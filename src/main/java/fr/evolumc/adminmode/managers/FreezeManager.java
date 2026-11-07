package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.utils.DiscordNotifier;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FreezeManager {

    public record FreezeData(String reason, long unfreezeAt) {}

    private final AdminMode plugin;
    private final Map<UUID, FreezeData> frozen = new HashMap<>();
    private int todayFreezeCount = 0;
    private BukkitTask autoUnfreezeTask;

    // ─── Persistence ──────────────────────────────────────────────────────────
    private final File persistFile;

    public FreezeManager(AdminMode plugin) {
        this.plugin = plugin;
        this.persistFile = new File(plugin.getDataFolder(), "freeze-persistent.yml");

        // Charger les gels persistants AVANT de démarrer la tâche
        load();

        // Tâche qui vérifie les dégels automatiques toutes les secondes
        autoUnfreezeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAutoUnfreeze, 20L, 20L);
    }

    // ─── Freeze avec raison + durée ──────────────────────────────────────────

    /**
     * Gèle un joueur avec une raison et une durée optionnelle.
     *
     * @param target  joueur à geler
     * @param admin   admin qui gèle (peut être null)
     * @param reason  raison affichée au joueur
     * @param seconds durée en secondes, 0 = permanent
     */
    public void freeze(Player target, Player admin, String reason, int seconds) {
        long unfreezeAt = seconds > 0 ? System.currentTimeMillis() + (seconds * 1000L) : 0;
        frozen.put(target.getUniqueId(), new FreezeData(reason, unfreezeAt));
        todayFreezeCount++;
        saveAsync();

        String msg = "§c§lVous avez été gelé" + (reason.isEmpty() ? "." : " pour : §f" + reason);
        if (seconds > 0) msg += " §c(durée : §f" + formatSeconds(seconds) + "§c)";
        target.sendMessage(msg);

        if (admin != null) {
            String adminMsg = plugin.getPrefix() + "§a" + target.getName() + " est maintenant gelé"
                    + (reason.isEmpty() ? "." : " (§7" + reason + "§a).");
            admin.sendMessage(adminMsg);
        }

        notifyDiscord(admin, target, true, reason, seconds);
    }

    /** Compatibilité avec l'ancien code sans raison/durée. */
    public void freeze(Player target, Player admin) {
        freeze(target, admin, "", 0);
    }

    // ─── Unfreeze ─────────────────────────────────────────────────────────────

    public void unfreeze(Player target, Player admin) {
        frozen.remove(target.getUniqueId());
        saveAsync();
        target.sendMessage("§a§lVous avez été dégelé.");
        if (admin != null) {
            admin.sendMessage(plugin.getPrefix() + "§a" + target.getName() + " a été dégelé.");
        }
        notifyDiscord(admin, target, false, "", 0);
    }

    /** Toggle freeze/unfreeze (sans raison/durée — compatibilité). */
    public boolean toggle(Player target, Player admin) {
        if (isFrozen(target.getUniqueId())) {
            unfreeze(target, admin);
            return false;
        } else {
            freeze(target, admin, "", 0);
            return true;
        }
    }

    /** Toggle avec raison + durée. */
    public boolean toggleFull(Player target, Player admin, String reason, int seconds) {
        if (isFrozen(target.getUniqueId())) {
            unfreeze(target, admin);
            return false;
        } else {
            freeze(target, admin, reason, seconds);
            return true;
        }
    }

    // ─── FreezeAll ────────────────────────────────────────────────────────────

    public void freezeAll(Player admin) {
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(admin)) continue;
            if (!isFrozen(p.getUniqueId())) {
                freeze(p, null, "FreezeAll", 0);
                count++;
            }
        }
        admin.sendMessage(plugin.getPrefix() + "§a§l" + count + " §ajoueur(s) gelé(s) en masse.");
        notifyDiscordBulk(admin, count);
    }

    public void unfreezeAll() {
        for (UUID uuid : new HashSet<>(frozen.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage("§a§lVous avez été dégelé (arrêt du serveur).");
            }
        }
        frozen.clear();
        saveAsync();
        if (autoUnfreezeTask != null) autoUnfreezeTask.cancel();
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public boolean isFrozen(UUID uuid) {
        return frozen.containsKey(uuid);
    }

    public Set<UUID> getFrozen() {
        return Collections.unmodifiableSet(frozen.keySet());
    }

    public FreezeData getFreezeData(UUID uuid) {
        return frozen.get(uuid);
    }

    // ─── Connexion d'un joueur gelé ───────────────────────────────────────────

    /**
     * Appelé par le listener onJoin pour rappeler à un joueur gelé son statut.
     * Les gels persistants survivent aux déconnexions/redémarrages.
     */
    public void onPlayerJoin(Player player) {
        FreezeData data = frozen.get(player.getUniqueId());
        if (data == null) return;

        // Vérifier si le gel a expiré pendant que le joueur était hors ligne
        if (data.unfreezeAt() > 0 && System.currentTimeMillis() >= data.unfreezeAt()) {
            frozen.remove(player.getUniqueId());
            saveAsync();
            return;
        }

        String msg = "§c§lVous êtes toujours gelé" + (data.reason().isEmpty() ? "." : " pour : §f" + data.reason());
        if (data.unfreezeAt() > 0) {
            long remaining = (data.unfreezeAt() - System.currentTimeMillis()) / 1000L;
            msg += " §c(temps restant : §f" + formatSeconds((int) remaining) + "§c)";
        }
        player.sendMessage(msg);
    }

    // ─── Auto-unfreeze tick ───────────────────────────────────────────────────

    private void tickAutoUnfreeze() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Map.Entry<UUID, FreezeData>> it = frozen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, FreezeData> entry = it.next();
            FreezeData data = entry.getValue();
            if (data.unfreezeAt() > 0 && now >= data.unfreezeAt()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    p.sendMessage("§a§lVous avez été dégelé automatiquement.");
                }
                plugin.getChatLogManager().addCommand(entry.getKey(), "[AUTO-UNFREEZE] durée écoulée");
                it.remove();
                changed = true;
            }
        }
        if (changed) saveAsync();
    }

    // ─── Persistence YAML ─────────────────────────────────────────────────────

    /**
     * Charge les gels persistants depuis le fichier YAML.
     * Appelé une fois au démarrage du manager.
     */
    private void load() {
        if (!persistFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(persistFile);
        long now = System.currentTimeMillis();
        for (String key : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String reason = cfg.getString(key + ".reason", "");
                long unfreezeAt = cfg.getLong(key + ".unfreeze-at", 0);
                // Ignorer les gels temporels expirés pendant l'arrêt du serveur
                if (unfreezeAt > 0 && now >= unfreezeAt) continue;
                frozen.put(uuid, new FreezeData(reason, unfreezeAt));
            } catch (IllegalArgumentException ignored) {}
        }
        if (!frozen.isEmpty()) {
            plugin.getLogger().info("[FreezeManager] " + frozen.size() + " gel(s) restauré(s) depuis freeze-persistent.yml.");
        }
    }

    /**
     * Sauvegarde l'état courant de façon asynchrone.
     */
    private void saveAsync() {
        // Snapshot sur le thread principal pour éviter la ConcurrentModificationException
        Map<UUID, FreezeData> snapshot = new HashMap<>(frozen);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<UUID, FreezeData> e : snapshot.entrySet()) {
                String key = e.getKey().toString();
                cfg.set(key + ".reason",     e.getValue().reason());
                cfg.set(key + ".unfreeze-at", e.getValue().unfreezeAt());
            }
            try {
                plugin.getDataFolder().mkdirs();
                cfg.save(persistFile);
            } catch (IOException ex) {
                plugin.getLogger().warning("[FreezeManager] Impossible de sauvegarder freeze-persistent.yml : " + ex.getMessage());
            }
        });
    }

    // ─── Discord ──────────────────────────────────────────────────────────────

    private void notifyDiscord(Player admin, Player target, boolean freezing, String reason, int seconds) {
        String webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        if (webhookUrl.isEmpty()) return;
        String adminName = admin != null ? admin.getName() : "Serveur";
        String action = freezing ? "❄ Joueur gelé" : "🔥 Joueur dégelé";
        String desc = "**Admin :** " + adminName + "\n**Cible :** " + target.getName();
        if (freezing && !reason.isEmpty()) desc += "\n**Raison :** " + reason;
        if (freezing && seconds > 0)      desc += "\n**Durée :** " + formatSeconds(seconds);
        int color = freezing ? 0x5599FF : 0xFF9900;
        DiscordNotifier.sendEmbed(plugin, webhookUrl, action, desc, color);
    }

    private void notifyDiscordBulk(Player admin, int count) {
        String webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        if (webhookUrl.isEmpty()) return;
        String desc = "**Admin :** " + admin.getName() + "\n**Joueurs gelés :** " + count;
        DiscordNotifier.sendEmbed(plugin, webhookUrl, "❄ FreezeAll déclenché", desc, 0x5599FF);
    }

    private String formatSeconds(int s) {
        if (s < 60)   return s + "s";
        if (s < 3600) return (s / 60) + "m" + (s % 60 > 0 ? (s % 60) + "s" : "");
        return (s / 3600) + "h" + ((s % 3600) / 60 > 0 ? ((s % 3600) / 60) + "m" : "");
    }

    public int getTodayFreezeCount()  { return todayFreezeCount; }
    public void resetTodayCount()     { todayFreezeCount = 0; }
}

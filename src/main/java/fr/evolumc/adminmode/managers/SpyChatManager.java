package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Gère le mode SpyChat : les admins qui l'activent reçoivent
 * tous les /msg, /tell, /r et messages chat privés interceptés.
 *
 * <p>L'état est persistant : les admins qui avaient activé le SpyChat
 * avant un redémarrage le retrouvent actif à leur prochaine connexion.</p>
 */
public class SpyChatManager {

    private final AdminMode plugin;
    /** Admins ayant activé l'écoute du SpyChat. */
    private final Set<UUID> listening = new HashSet<>();

    // ─── Persistence ─────────────────────────────────────────────────────────
    private final File persistFile;

    public SpyChatManager(AdminMode plugin) {
        this.plugin = plugin;
        this.persistFile = new File(plugin.getDataFolder(), "spychat-persistent.yml");
        load();
    }

    // ─── API publique ─────────────────────────────────────────────────────────

    public void enableListen(Player admin) {
        listening.add(admin.getUniqueId());
        saveAsync();
        admin.sendMessage(plugin.getPrefix() + "§aSpyChat §lACTIVÉ §a— vous interceptez les MP et commandes privées.");
    }

    public void disableListen(Player admin) {
        listening.remove(admin.getUniqueId());
        saveAsync();
        admin.sendMessage(plugin.getPrefix() + "§cSpyChat §lDÉSACTIVÉ§c.");
    }

    public boolean toggle(Player admin) {
        if (listening.contains(admin.getUniqueId())) {
            disableListen(admin);
            return false;
        } else {
            enableListen(admin);
            return true;
        }
    }

    public boolean isListening(UUID uuid) {
        return listening.contains(uuid);
    }

    public Set<UUID> getListeners() {
        return Collections.unmodifiableSet(listening);
    }

    /**
     * Appelé à la connexion d'un admin : si son SpyChat était actif,
     * le rétablir silencieusement et lui notifier.
     *
     * @param player le joueur qui vient de se connecter
     */
    public void onPlayerJoin(Player player) {
        if (!listening.contains(player.getUniqueId())) return;
        if (!player.hasPermission("adminmode.spychat")) {
            // Permission révoquée entre temps
            listening.remove(player.getUniqueId());
            saveAsync();
            return;
        }
        player.sendMessage(plugin.getPrefix() + "§8[§6SpyChat§8] §7Écoute §arestaurée §7(persistant).");
    }

    /**
     * Diffuse un message intercepté à tous les admins en écoute.
     *
     * @param sender   joueur qui a envoyé le message/commande
     * @param original texte original intercepté
     * @param type     type affiché ("MSG", "TELL", "REPLY", "CHAT")
     */
    public void broadcast(Player sender, String original, String type) {
        String formatted = plugin.getPrefix()
                + "§8[§6SpyChat§8] §7[§e" + type + "§7] §f" + sender.getName()
                + " §8→ §f" + original;

        for (UUID uuid : listening) {
            Player admin = plugin.getServer().getPlayer(uuid);
            if (admin != null && admin.isOnline() && !admin.equals(sender)) {
                admin.sendMessage(formatted);
            }
        }
    }

    // ─── Persistence YAML ─────────────────────────────────────────────────────

    private void load() {
        if (!persistFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(persistFile);
        for (String key : cfg.getKeys(false)) {
            try {
                if (cfg.getBoolean(key, false))
                    listening.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveAsync() {
        Set<UUID> snapshot = new HashSet<>(listening);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration cfg = new YamlConfiguration();
            for (UUID uuid : snapshot) {
                cfg.set(uuid.toString(), true);
            }
            try {
                plugin.getDataFolder().mkdirs();
                cfg.save(persistFile);
            } catch (IOException ex) {
                plugin.getLogger().warning("[SpyChatManager] Impossible de sauvegarder spychat-persistent.yml : " + ex.getMessage());
            }
        });
    }
}

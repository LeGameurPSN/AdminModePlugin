package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.utils.DiscordNotifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Détecte les comportements suspects côté commandes :
 *  - Spam de commandes (N commandes en X secondes)
 *  - Abus de /tp (téléportations répétées)
 *  - Commande identique répétée trop souvent
 *
 * Chaque seuil est configurable dans config.yml (suspicious-patterns.*).
 */
public class SuspiciousPatternManager {

    private final AdminMode plugin;

    // ─── Fenêtres glissantes ───────────────────────────────────────────────
    /** uuid → liste de timestamps des commandes récentes */
    private final Map<UUID, ArrayDeque<Long>> cmdTimestamps = new HashMap<>();
    /** uuid → (commande → timestamps) pour détecter les répétitions */
    private final Map<UUID, Map<String, ArrayDeque<Long>>> cmdRepeat = new HashMap<>();
    /** uuid → timestamps des /tp récents */
    private final Map<UUID, ArrayDeque<Long>> tpTimestamps = new HashMap<>();

    // ─── Cooldown d'alerte ─────────────────────────────────────────────────
    /** uuid → temps du dernier envoi d'alerte — pour éviter le flood */
    private final Map<UUID, Long> alertCooldown = new HashMap<>();
    private static final long ALERT_COOLDOWN_MS = 60_000L; // 1 min entre 2 alertes pour le même joueur

    private BukkitTask cleanupTask;

    public SuspiciousPatternManager(AdminMode plugin) {
        this.plugin = plugin;
        // Nettoyage des fenêtres glissantes toutes les minutes
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanup, 1200L, 1200L);
    }

    // ─── Entrée principale ─────────────────────────────────────────────────

    /**
     * À appeler depuis le listener de commandes pour chaque commande tapée.
     *
     * @param player  joueur qui a tapé la commande
     * @param command commande brute (ex: "/tp Steve")
     */
    public void recordCommand(Player player, String command) {
        if (!plugin.getConfig().getBoolean("suspicious-patterns.enabled", true)) return;

        UUID uuid = player.getUniqueId();
        long now  = System.currentTimeMillis();

        int windowSec     = plugin.getConfig().getInt("suspicious-patterns.window-seconds", 5);
        int spamThreshold = plugin.getConfig().getInt("suspicious-patterns.cmd-spam-threshold", 8);
        int tpThreshold   = plugin.getConfig().getInt("suspicious-patterns.tp-threshold", 5);
        int repeatThreshold = plugin.getConfig().getInt("suspicious-patterns.repeat-threshold", 4);
        long windowMs     = windowSec * 1000L;

        // ── Spam global ────────────────────────────────────────────────────
        ArrayDeque<Long> cmds = cmdTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        cmds.addLast(now);
        pruneOld(cmds, now, windowMs);
        if (cmds.size() >= spamThreshold) {
            alert(player, "SPAM_CMD",
                    "§c" + cmds.size() + " commandes en " + windowSec + "s",
                    "**Nb commandes :** " + cmds.size() + " en " + windowSec + "s\n**Dernière :** `" + command + "`");
        }

        // ── Abus /tp ───────────────────────────────────────────────────────
        String lower = command.toLowerCase();
        if (lower.startsWith("/tp ") || lower.startsWith("/teleport ")) {
            ArrayDeque<Long> tps = tpTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
            tps.addLast(now);
            pruneOld(tps, now, windowMs);
            if (tps.size() >= tpThreshold) {
                alert(player, "TP_ABUSE",
                        "§c" + tps.size() + " téléportations en " + windowSec + "s",
                        "**Nb /tp :** " + tps.size() + " en " + windowSec + "s\n**Commande :** `" + command + "`");
            }
        }

        // ── Répétition de la même commande ─────────────────────────────────
        String cmdKey = lower.split(" ")[0]; // just the base command
        Map<String, ArrayDeque<Long>> repeatMap = cmdRepeat.computeIfAbsent(uuid, k -> new HashMap<>());
        ArrayDeque<Long> repeats = repeatMap.computeIfAbsent(cmdKey, k -> new ArrayDeque<>());
        repeats.addLast(now);
        pruneOld(repeats, now, windowMs);
        if (repeats.size() >= repeatThreshold && repeats.size() > spamThreshold / 2) {
            alert(player, "CMD_REPEAT",
                    "§c" + cmdKey + " répétée " + repeats.size() + "× en " + windowSec + "s",
                    "**Commande :** `" + cmdKey + "`\n**Répétitions :** " + repeats.size() + " en " + windowSec + "s");
        }
    }

    // ─── Alerte ────────────────────────────────────────────────────────────

    private void alert(Player player, String type, String inGame, String discordDesc) {
        UUID uuid = player.getUniqueId();
        long now  = System.currentTimeMillis();

        // Cooldown — évite le flood d'alertes pour le même joueur
        Long last = alertCooldown.get(uuid);
        if (last != null && now - last < ALERT_COOLDOWN_MS) return;
        alertCooldown.put(uuid, now);

        // ── Message StaffChat en jeu ───────────────────────────────────────
        String msg = AdminMode.colorize("&8[&cPATTERN&8] &f" + player.getName()
                + " &8[" + type + "] &7" + inGame);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("staffchat.use"))
                staff.sendMessage(msg);
        }

        // ── Log surveillance si actif ──────────────────────────────────────
        plugin.getWatchListManager().logCommand(uuid, "[PATTERN:" + type + "] " + inGame);

        // ── Discord ────────────────────────────────────────────────────────
        String webhook = plugin.getConfig().getString("suspicious-patterns.discord-webhook",
                plugin.getConfig().getString("discord.webhook-url", ""));
        if (!webhook.isEmpty()) {
            String title = "⚠️ Pattern suspect — " + player.getName();
            String desc  = "**Joueur :** " + player.getName()
                    + "\n**Type :** " + type
                    + "\n" + discordDesc;
            DiscordNotifier.sendEmbed(plugin, webhook, title, desc, 0xFF6600);
        }
    }

    // ─── Utilitaires ───────────────────────────────────────────────────────

    private void pruneOld(ArrayDeque<Long> q, long now, long windowMs) {
        while (!q.isEmpty() && now - q.peekFirst() > windowMs) q.pollFirst();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        long maxAge = plugin.getConfig().getInt("suspicious-patterns.window-seconds", 10) * 2000L;
        cmdTimestamps.values().forEach(q -> pruneOld(q, now, maxAge));
        tpTimestamps.values().forEach(q -> pruneOld(q, now, maxAge));
        cmdRepeat.values().forEach(m -> m.values().forEach(q -> pruneOld(q, now, maxAge)));
        alertCooldown.entrySet().removeIf(e -> now - e.getValue() > ALERT_COOLDOWN_MS * 5);
    }

    public void stop() {
        if (cleanupTask != null) cleanupTask.cancel();
    }
}

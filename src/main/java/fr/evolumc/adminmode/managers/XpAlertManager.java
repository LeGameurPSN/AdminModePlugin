package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.utils.DiscordNotifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Surveille les gains d'XP anormaux.
 * Seuil configurable : xp-alert.threshold-per-second (défaut: 50 XP/s)
 */
public class XpAlertManager {

    private final AdminMode plugin;
    private final Map<UUID, Integer> lastXp = new HashMap<>();
    private final Map<UUID, Long> lastCheck = new HashMap<>();
    private final Map<UUID, Integer> alertCooldown = new HashMap<>();
    private BukkitTask task;

    public XpAlertManager(AdminMode plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int intervalTicks = 20; // check every second
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int threshold = plugin.getConfig().getInt("xp-alert.threshold-per-second", 50);

            // Purger les entrées des joueurs déconnectés pour éviter la fuite mémoire.
            // On construit l'ensemble des UUID en ligne une seule fois.
            java.util.Set<UUID> online = new java.util.HashSet<>();
            for (Player p : Bukkit.getOnlinePlayers()) online.add(p.getUniqueId());
            lastXp.keySet().retainAll(online);
            alertCooldown.keySet().retainAll(online);
            lastCheck.keySet().retainAll(online);

            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID uid = p.getUniqueId();
                int currentXp = p.getTotalExperience();
                if (!lastXp.containsKey(uid)) {
                    lastXp.put(uid, currentXp);
                    continue;
                }
                int gained = currentXp - lastXp.get(uid);
                lastXp.put(uid, currentXp);
                if (gained > threshold) {
                    int cd = alertCooldown.getOrDefault(uid, 0);
                    if (cd <= 0) {
                        String desc = "**Joueur :** " + p.getName()
                                + "\n**XP gagné :** " + gained + " en 1s"
                                + "\n**Seuil :** " + threshold
                                + "\n**XP total :** " + currentXp;
                        String webhook = plugin.getConfig().getString("xp-alert.discord-webhook", "");
                        DiscordNotifier.sendEmbed(plugin, webhook, "⚠️ Gain XP Suspect", desc, 0xFF0000);
                        // Alerter le staff en jeu
                        for (Player staff : Bukkit.getOnlinePlayers()) {
                            if (staff.hasPermission("staffchat.use")) {
                                staff.sendMessage(AdminMode.colorize("&8[&cXP ALERT&8] &f" + p.getName()
                                        + " &7a gagné &c+" + gained + " XP &7en 1 seconde !"));
                            }
                        }
                        alertCooldown.put(uid, 30); // cooldown 30s
                    } else {
                        alertCooldown.put(uid, cd - 1);
                    }
                } else {
                    alertCooldown.merge(uid, -1, (a, b) -> Math.max(0, a + b));
                }
            }
        }, 20L, intervalTicks);
    }

    public void stop() {
        if (task != null) task.cancel();
    }
}

package fr.evolumc.adminmode.listeners;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.managers.WatchListManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

public class WatchListListener implements Listener {

    private final AdminMode plugin;

    public WatchListListener(AdminMode plugin) { this.plugin = plugin; }

    /** Quand un joueur surveillé se reconnecte après un restart, on reprend lastLocation. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        WatchListManager wlm = plugin.getWatchListManager();
        if (wlm.isWatched(p.getUniqueId())) {
            WatchListManager.WatchEntry entry = wlm.getEntry(p.getUniqueId());
            if (entry != null) {
                entry.lastLocation = p.getLocation().clone();
                entry.addLog("RECONNEXION après restart — surveillance reprise.");
                wlm.persistFull(entry);
                // Alerter le staff
                for (Player staff : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (staff.hasPermission("staffchat.use")) {
                        staff.sendMessage(AdminMode.colorize(
                            "&8[&6WatchList&8] &f" + p.getName() + " &7s'est reconnecté — surveillance active."));
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (plugin.getWatchListManager().isWatched(p.getUniqueId())) {
            plugin.getWatchListManager().logCommand(p.getUniqueId(), event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        if (plugin.getWatchListManager().isWatched(p.getUniqueId())) {
            plugin.getWatchListManager().logChat(p.getUniqueId(), event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        WatchListManager wlm = plugin.getWatchListManager();
        if (!wlm.isWatched(p.getUniqueId())) return;
        WatchListManager.WatchEntry entry = wlm.getEntry(p.getUniqueId());
        if (entry == null || entry.lastLocation == null) return;
        Location to = event.getTo();
        if (to == null) return;
        // Déplacement anormal > 20 blocs en 1 tick
        if (entry.lastLocation.getWorld() != null
                && entry.lastLocation.getWorld().equals(to.getWorld())
                && entry.lastLocation.distanceSquared(to) > 400) {
            wlm.logMovement(p.getUniqueId(),
                String.format("Téléportation suspecte : %.1f blocs (%d,%d,%d → %d,%d,%d)",
                    Math.sqrt(entry.lastLocation.distanceSquared(to)),
                    entry.lastLocation.getBlockX(), entry.lastLocation.getBlockY(), entry.lastLocation.getBlockZ(),
                    to.getBlockX(), to.getBlockY(), to.getBlockZ()));
        }
        entry.lastLocation = to.clone();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        WatchListManager wlm = plugin.getWatchListManager();
        if (wlm.isWatched(p.getUniqueId())) {
            WatchListManager.WatchEntry entry = wlm.getEntry(p.getUniqueId());
            if (entry != null) {
                entry.addLog("DÉCONNEXION — session en pause (fichier conservé).");
                wlm.persistFull(entry);
                // On N'unwatche PAS → la session reste active pour la reconnexion
                // On envoie quand même un embed Discord de notification (pas de rapport final)
                String webhook = plugin.getConfig().getString("surveillance.discord-webhook", "");
                fr.evolumc.adminmode.utils.DiscordNotifier.sendEmbed(plugin, webhook,
                    "⏸ Surveillance — " + p.getName() + " déconnecté",
                    "**Session en pause.** Le fichier est conservé et la surveillance reprendra à la reconnexion.\n"
                    + "Pour clore la session, faites `/surveillance off " + p.getName() + "` (même hors ligne).",
                    0xE67E22);
            }
        }
        // OnlineDuration
        plugin.getOnlineDurationManager().onQuit(p);
    }
}

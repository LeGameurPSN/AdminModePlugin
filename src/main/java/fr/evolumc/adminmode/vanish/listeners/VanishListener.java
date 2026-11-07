package fr.evolumc.adminmode.vanish.listeners;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class VanishListener implements Listener {

    private final AdminMode plugin;

    public VanishListener(AdminMode plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getVanishManager().applyJoinStateTo(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getVanishManager().isVanished(player)) {
            // Pas de message de départ — déjà envoyé le fake quit à l'activation du vanish
            event.setQuitMessage(null);
            plugin.getVanishManager().unvanish(player, false);
        }
        if (plugin.getNoclipManager().isNoclipping(player)) {
            plugin.getNoclipManager().disableNoclip(player);
        }
    }

    /** Les mobs ne ciblent pas les joueurs en vanish */
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        Entity target = event.getTarget();
        if (target instanceof Player player) {
            if (plugin.getVanishManager().isVanished(player)) {
                event.setCancelled(true);
            }
        }
    }
}

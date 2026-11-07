package fr.evolumc.adminmode.listeners;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class CooldownListener implements Listener {

    private final AdminMode plugin;

    public CooldownListener(AdminMode plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        String cmd = event.getMessage().split(" ")[0].replace("/", "").toLowerCase();
        int remaining = plugin.getCooldownManager().getRemainingCooldown(p, cmd);
        if (remaining > 0) {
            event.setCancelled(true);
            p.sendMessage(AdminMode.colorize("&8[&cAdminMode&8] &cCommande en cooldown. Réessayez dans &f"
                + remaining + "s&c."));
            return;
        }
        // Enregistrer l'utilisation et les stats
        if (plugin.getConfig().contains("cooldowns." + cmd)) {
            plugin.getCooldownManager().setCooldown(p, cmd);
        }
        // Stats toujours enregistrées pour les commandes admin
        if (p.hasPermission("adminmode.use")) {
            plugin.getCommandStatsManager().record(cmd);
        }
    }
}

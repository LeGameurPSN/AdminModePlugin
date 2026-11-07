package fr.evolumc.adminmode.listeners;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class LockCommandListener implements Listener {

    private final AdminMode plugin;

    public LockCommandListener(AdminMode plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (p.isOp() || p.hasPermission("adminmode.lockcommand")) return;
        String cmd = event.getMessage().split(" ")[0].replace("/", "").toLowerCase();
        if (plugin.getLockCommandManager().isLocked(cmd)) {
            event.setCancelled(true);
            p.sendMessage(AdminMode.colorize("&8[&cAdminMode&8] &cCette commande est temporairement verrouillée."));
        }
    }
}

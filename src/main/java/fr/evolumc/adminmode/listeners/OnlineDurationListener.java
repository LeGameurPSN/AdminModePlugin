package fr.evolumc.adminmode.listeners;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;

public class OnlineDurationListener implements Listener {

    private final AdminMode plugin;

    public OnlineDurationListener(AdminMode plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getOnlineDurationManager().onJoin(event.getPlayer());
    }
}

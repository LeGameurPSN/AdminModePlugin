package fr.evolumc.adminmode.listeners;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.managers.StaffChatManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class StaffChatListener implements Listener {

    private final AdminMode plugin;
    private final StaffChatManager staffChatManager;

    public StaffChatListener(AdminMode plugin, StaffChatManager staffChatManager) {
        this.plugin = plugin;
        this.staffChatManager = staffChatManager;
    }

    /**
     * Intercepte les messages du chat.
     * Si le joueur a le StaffChat activé (toggle ON), son message est redirigé.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!staffChatManager.isInStaffChat(event.getPlayer())) return;

        // Annuler le message dans le chat normal
        event.setCancelled(true);

        // Envoyer dans le StaffChat (thread principal pour les sons Bukkit)
        String message = event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin,
                () -> staffChatManager.sendMessage(event.getPlayer(), message));
    }

    /**
     * Désactiver le toggle quand le joueur se déconnecte
     * (pas besoin de persister entre les sessions).
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        staffChatManager.disableOnQuit(event.getPlayer());
    }
}

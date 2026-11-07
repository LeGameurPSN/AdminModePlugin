package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.utils.DiscordNotifier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Option 15 — Mode maintenance.
 * Kick les joueurs non-admins et bloque les connexions.
 */
public class MaintenanceManager implements Listener {

    private final AdminMode plugin;
    private boolean maintenanceEnabled = false;
    private String customMessage;

    public MaintenanceManager(AdminMode plugin) {
        this.plugin = plugin;
        this.customMessage = AdminMode.colorize(
            plugin.getConfig().getString("maintenance.default-message",
                "&cLe serveur est en maintenance. Revenez plus tard !"));
    }

    public void enable(Player admin, String message) {
        maintenanceEnabled = true;
        if (message != null && !message.isEmpty()) {
            customMessage = AdminMode.colorize(message);
        }

        int kicked = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(admin)) continue;
            if (!p.hasPermission("adminmode.maintenance.bypass")) {
                p.kick(Component.text(customMessage));
                kicked++;
            }
        }
        admin.sendMessage(plugin.getPrefix() + "§aMaintenance §lACTIVÉE §a— §f" + kicked + " §ajoueur(s) expulsé(s).");
        plugin.getLogger().info("[AdminMode] Maintenance activée par " + admin.getName());

        String webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        String desc = "**Admin :** " + admin.getName()
                + "\n**Message :** " + customMessage
                + "\n**Joueurs expulsés :** " + kicked;
        DiscordNotifier.sendEmbed(plugin, webhookUrl, "🔧 Maintenance activée", desc, 0xFF4444);
    }

    public void disable(Player admin) {
        maintenanceEnabled = false;
        admin.sendMessage(plugin.getPrefix() + "§aMaintenance §lDÉSACTIVÉE §a— le serveur est de nouveau ouvert.");
        plugin.getLogger().info("[AdminMode] Maintenance désactivée par " + admin.getName());

        String webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        DiscordNotifier.sendEmbed(plugin, webhookUrl, "✅ Maintenance terminée",
                "**Admin :** " + admin.getName(), 0x44FF44);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!maintenanceEnabled) return;
        if (event.getPlayer().hasPermission("adminmode.maintenance.bypass")) return;
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, customMessage);
    }

    public boolean isEnabled() { return maintenanceEnabled; }
    public String getMessage() { return customMessage; }
}

package fr.evolumc.adminmode.listeners;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Option 1 — SpyChat : intercepte /msg /tell /r et les transmet aux admins en écoute.
 * Option 6 — Log toutes les commandes dans ChatLogManager (via addCommand).
 *
 * Note : le log du CHAT normal est déjà géré par ChatLogListener (Paper AsyncChatEvent).
 */
public class SpyChatListener implements Listener {

    private final AdminMode plugin;

    public SpyChatListener(AdminMode plugin) {
        this.plugin = plugin;
    }

    /** Intercepte les commandes : log + broadcast SpyChat si MP. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player sender = event.getPlayer();
        String cmd = event.getMessage(); // "/msg Notch Salut"

        // Log toutes les commandes (Option 6)
        plugin.getChatLogManager().addCommand(sender.getUniqueId(), cmd);

        // SpyChat : uniquement les commandes privées
        String lower = cmd.toLowerCase();
        if (lower.startsWith("/msg ")     || lower.startsWith("/tell ")
         || lower.startsWith("/r ")       || lower.startsWith("/reply ")
         || lower.startsWith("/w ")       || lower.startsWith("/whisper ")
         || lower.startsWith("/m ")) {
            plugin.getSpyChatManager().broadcast(sender, cmd, extractType(lower));
        }
    }

    private String extractType(String cmd) {
        if (cmd.startsWith("/msg"))     return "MSG";
        if (cmd.startsWith("/tell"))    return "TELL";
        if (cmd.startsWith("/r") || cmd.startsWith("/reply")) return "REPLY";
        if (cmd.startsWith("/w") || cmd.startsWith("/whisper")) return "WHISPER";
        return "MP";
    }
}

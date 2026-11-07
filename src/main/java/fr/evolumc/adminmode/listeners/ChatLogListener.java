package fr.evolumc.adminmode.listeners;

import fr.evolumc.adminmode.AdminMode;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatLogListener implements Listener {

    private final AdminMode plugin;

    public ChatLogListener(AdminMode plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getChatLogManager().addMessage(event.getPlayer(), plain);
    }
}

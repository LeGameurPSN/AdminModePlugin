package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class StaffChatManager {

    private final AdminMode plugin;

    /** Joueurs dont le chat est redirigé vers le StaffChat */
    private final Set<UUID> toggled = new HashSet<>();

    public StaffChatManager(AdminMode plugin) {
        this.plugin = plugin;
    }

    // ─── Toggle ───────────────────────────────────────────────────────────

    public boolean isInStaffChat(Player player) {
        return toggled.contains(player.getUniqueId());
    }

    public void enable(Player player) {
        toggled.add(player.getUniqueId());
    }

    public void disable(Player player) {
        toggled.remove(player.getUniqueId());
    }

    public void disableOnQuit(Player player) {
        toggled.remove(player.getUniqueId());
    }

    // ─── Envoi d'un message ───────────────────────────────────────────────

    /**
     * Envoie un message dans le StaffChat.
     * Seuls les joueurs ayant la permission staffchat.use le reçoivent.
     * Le message est aussi loggé dans la console.
     */
    public void sendMessage(Player sender, String message) {
        String formatted = buildFormat(sender.getName(), message);

        // Envoyer à tous les joueurs avec la permission
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("staffchat.use")) {
                online.sendMessage(formatted);
                online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.8f);
            }
        }

        // Log console
        Bukkit.getConsoleSender().sendMessage(formatted);
    }

    private String buildFormat(String playerName, String message) {
        String format = plugin.getConfig().getString(
                "staffchat.format",
                "&8[&bStaffChat&8] &7{player}&8: &f{message}"
        );
        return AdminMode.colorize(format
                .replace("{player}", playerName)
                .replace("{message}", message));
    }

    /**
     * Broadcast d'un message pré-formaté à tout le staff (sans le prefix staffchat).
     * Utilisé par ReportManager et d'autres systèmes internes.
     */
    public void broadcastToStaff(String message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("staffchat.use")) {
                online.sendMessage(message);
                online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    public Set<UUID> getToggled() {
        return toggled;
    }
}

package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /stafflist — affiche le staff connecté avec leur mode actif et leur durée de session.
 */
public class StaffListCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public StaffListCommand(AdminMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("staffchat.use")) {
            sender.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
            return true;
        }

        List<Player> staff = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("staffchat.use"))
                .collect(Collectors.toList());

        if (staff.isEmpty()) {
            sender.sendMessage(plugin.getPrefix() + "§7Aucun staff connecté.");
            return true;
        }

        sender.sendMessage(AdminMode.colorize("&8[&bStaff&8] &7" + staff.size() + " membre(s) en ligne :"));
        for (Player p : staff) {
            StringBuilder status = new StringBuilder();

            if (plugin.getAdminModeManager().isInAdminMode(p))
                status.append(" §c[ADMIN]");
            if (plugin.getVanishManager().isVanished(p))
                status.append(" §8[VANISH]");
            if (plugin.getSpectatorManager().isWatching(p.getUniqueId()))
                status.append(" §d[SPEC]");
            if (plugin.getStaffChatManager().isInStaffChat(p))
                status.append(" §b[SC]");
            if (plugin.getSpyChatManager().isListening(p.getUniqueId()))
                status.append(" §6[SPY]");

            long onlineSecs = plugin.getOnlineDurationManager().getTotalSeconds(p);
            String duration = formatDuration(onlineSecs);

            sender.sendMessage("  §f" + p.getName()
                    + "§7 — §f" + duration
                    + (status.length() > 0 ? status.toString() : " §a[EN LIGNE]"));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }

    private String formatDuration(long secs) {
        if (secs < 60)   return secs + "s";
        if (secs < 3600) return (secs / 60) + "m" + (secs % 60 > 0 ? (secs % 60) + "s" : "");
        return (secs / 3600) + "h" + ((secs % 3600) / 60 > 0 ? ((secs % 3600) / 60) + "m" : "");
    }
}

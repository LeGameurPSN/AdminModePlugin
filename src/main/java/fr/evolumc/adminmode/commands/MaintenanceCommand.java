package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Option 15 — /maintenance on [message] | off */
public class MaintenanceCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public MaintenanceCommand(AdminMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cJoueurs seulement.");
            return true;
        }
        if (!player.hasPermission("adminmode.maintenance")) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
            return true;
        }
        if (args.length < 1) {
            boolean current = plugin.getMaintenanceManager().isEnabled();
            player.sendMessage(plugin.getPrefix() + "§7Maintenance : " + (current ? "§aON" : "§cOFF"));
            player.sendMessage(plugin.getPrefix() + "§7Usage : §f/maintenance <on [message]|off>");
            return true;
        }
        if (args[0].equalsIgnoreCase("on")) {
            String msg = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
            plugin.getMaintenanceManager().enable(player, msg);
        } else if (args[0].equalsIgnoreCase("off")) {
            plugin.getMaintenanceManager().disable(player);
        } else {
            player.sendMessage(plugin.getPrefix() + "§7Usage : §f/maintenance <on|off>");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return Arrays.asList("on", "off");
        return Collections.emptyList();
    }
}

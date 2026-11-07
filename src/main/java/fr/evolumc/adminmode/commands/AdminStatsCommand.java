package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AdminStatsCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public AdminStatsCommand(AdminMode plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("adminmode.stats")) {
            sender.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission")); return true;
        }
        boolean session = args.length > 0 && args[0].equalsIgnoreCase("session");
        List<Map.Entry<String, Integer>> top = session
                ? plugin.getCommandStatsManager().getTopSession(10)
                : plugin.getCommandStatsManager().getTopTotal(10);

        sender.sendMessage(AdminMode.colorize("&8&m─────────────────────────────────────"));
        sender.sendMessage(AdminMode.colorize("&6  Top Commandes Admin &8(" + (session ? "session" : "global") + ")"));
        sender.sendMessage(AdminMode.colorize("&8&m─────────────────────────────────────"));
        if (top.isEmpty()) {
            sender.sendMessage(AdminMode.colorize("&7Aucune commande enregistrée."));
        } else {
            for (int i = 0; i < top.size(); i++) {
                sender.sendMessage(AdminMode.colorize(
                    "&7" + (i+1) + ". &f/" + top.get(i).getKey() + " &8— &a" + top.get(i).getValue() + "x"));
            }
        }
        if (!session) {
            sender.sendMessage(AdminMode.colorize("&8Utilisez &7/adminstats session &8pour la session actuelle."));
        }
        sender.sendMessage(AdminMode.colorize("&8&m─────────────────────────────────────"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                                @NotNull String l, @NotNull String[] a) {
        if (a.length == 1) return List.of("session");
        return Collections.emptyList();
    }
}

package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /freeze <joueur> [durée] [raison...]
 * /freezeall
 * Durée format : 30s, 5m, 2h
 */
public class FreezeCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public FreezeCommand(AdminMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur.");
            return true;
        }
        if (!player.hasPermission("adminmode.freeze")) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
            return true;
        }

        // /freezeall
        if (label.equalsIgnoreCase("freezeall")) {
            if (!player.hasPermission("adminmode.freezeall")) {
                player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
                return true;
            }
            plugin.getFreezeManager().freezeAll(player);
            return true;
        }

        // /freeze <joueur> [durée] [raison...]
        if (args.length < 1) {
            player.sendMessage(plugin.getPrefix() + "§7Usage : §f/freeze <joueur> [durée: 30s/5m/2h] [raison]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(plugin.getPrefix() + "§cJoueur introuvable : §f" + args[0]);
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(plugin.getPrefix() + "§cVous ne pouvez pas vous geler vous-même.");
            return true;
        }

        // Détecter durée optionnelle en args[1]
        int seconds = 0;
        int reasonStart = 1;
        if (args.length >= 2) {
            int parsed = parseDuration(args[1]);
            if (parsed >= 0) {
                seconds = parsed;
                reasonStart = 2;
            }
        }

        // Raison = reste des args
        String reason = args.length > reasonStart
                ? String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length))
                : "";

        plugin.getFreezeManager().toggleFull(target, player, reason, seconds);
        return true;
    }

    /** Parse "30s", "5m", "2h" → secondes. -1 si invalide. */
    private int parseDuration(String s) {
        if (s == null || s.isEmpty()) return -1;
        String lower = s.toLowerCase();
        try {
            if (lower.endsWith("s")) return Integer.parseInt(lower.substring(0, lower.length() - 1));
            if (lower.endsWith("m")) return Integer.parseInt(lower.substring(0, lower.length() - 1)) * 60;
            if (lower.endsWith("h")) return Integer.parseInt(lower.substring(0, lower.length() - 1)) * 3600;
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String label, @NotNull String[] args) {
        if (label.equalsIgnoreCase("freezeall")) return Collections.emptyList();
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(p))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) return Arrays.asList("30s", "1m", "5m", "10m", "1h");
        return Collections.emptyList();
    }
}

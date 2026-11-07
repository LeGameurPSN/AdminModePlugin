package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/** Option 3 — /track <joueur> : suivre la position via boussole + action bar. */
public class TrackCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public TrackCommand(AdminMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cJoueurs seulement.");
            return true;
        }
        if (!player.hasPermission("adminmode.track")) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
            return true;
        }
        // /track stop
        if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
            plugin.getSpyTrackManager().stopTrack(player);
            player.sendMessage(plugin.getPrefix() + "§cTrack arrêté.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.getPrefix() + "§7Usage : §f/track <joueur> §8| §f/track stop");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(plugin.getPrefix() + "§cJoueur introuvable : §f" + args[0]);
            return true;
        }
        plugin.getSpyTrackManager().startTrack(player, target);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            List<String> res = new ArrayList<>();
            res.add("stop");
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(p))
                    .forEach(res::add);
            return res;
        }
        return Collections.emptyList();
    }
}

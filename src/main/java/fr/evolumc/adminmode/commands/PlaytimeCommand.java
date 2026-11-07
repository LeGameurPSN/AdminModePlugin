package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class PlaytimeCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public PlaytimeCommand(AdminMode plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getPrefix() + "§7Usage : §f/playtime <joueur>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getPrefix() + "§cJoueur introuvable (doit être en ligne).");
            return true;
        }
        long secs = plugin.getOnlineDurationManager().getTotalSeconds(target);
        String time = plugin.getOnlineDurationManager().formatTime(secs);
        sender.sendMessage(plugin.getPrefix() + "§f" + target.getName() + " §7a joué §a" + time + " §7au total.");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                                @NotNull String l, @NotNull String[] a) {
        if (a.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(a[0].toLowerCase())).collect(Collectors.toList());
        return Collections.emptyList();
    }
}

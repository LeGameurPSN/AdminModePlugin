package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class VanishTpCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public VanishTpCommand(AdminMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur.");
            return true;
        }
        if (!player.hasPermission("adminmode.vanish")) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.getPrefix() + "§7Usage : §f/vanishtp <joueur>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(plugin.getPrefix() + "§cJoueur introuvable : §f" + args[0]);
            return true;
        }

        // 1. Activer le vanish si pas encore actif
        var vanishMgr = plugin.getVanishManager();
        if (!vanishMgr.isVanished(player)) {
            vanishMgr.vanish(player);
        }

        // 2. Téléporter
        player.teleport(target.getLocation());
        player.sendMessage(AdminMode.colorize("&8[&3ArcadiaVanish&8] &7Téléporté vers &c" + target.getName() + " &7en mode vanish."));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

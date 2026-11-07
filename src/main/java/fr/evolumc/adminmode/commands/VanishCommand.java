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

public class VanishCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public VanishCommand(AdminMode plugin) {
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

        // /vanish [joueur] — si arg fourni, toggle le vanish d'un autre joueur
        if (args.length >= 1) {
            if (!player.hasPermission("adminmode.vanish.others")) {
                player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(plugin.getPrefix() + "§cJoueur introuvable : §f" + args[0]);
                return true;
            }
            toggleVanish(target);
            player.sendMessage(AdminMode.colorize("&8[&3ArcadiaVanish&8] &7Vanish de &c" + target.getName() + " &7basculé."));
            return true;
        }

        // Toggle sur soi-même
        toggleVanish(player);
        return true;
    }

    private void toggleVanish(Player player) {
        var mgr = plugin.getVanishManager();
        if (mgr.isVanished(player)) {
            mgr.unvanish(player, false);
        } else {
            mgr.vanish(player);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("adminmode.vanish.others")) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

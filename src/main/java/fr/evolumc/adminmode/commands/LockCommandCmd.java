package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.*;

public class LockCommandCmd implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public LockCommandCmd(AdminMode plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("adminmode.lockcommand")) {
            sender.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission")); return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getPrefix() + "§7Usage : §f/lockcommand <commande|list|unlock <cmd>>");
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            Set<String> locked = plugin.getLockCommandManager().getLockedCommands();
            if (locked.isEmpty()) {
                sender.sendMessage(plugin.getPrefix() + "§7Aucune commande verrouillée.");
            } else {
                sender.sendMessage(plugin.getPrefix() + "§cCommandes verrouillées : §f" + String.join(", ", locked));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("unlock") && args.length >= 2) {
            boolean ok = plugin.getLockCommandManager().unlock(args[1]);
            sender.sendMessage(plugin.getPrefix() + (ok ? "§a/" + args[1] + " §7déverrouillée." : "§e/" + args[1] + " §7n'était pas verrouillée."));
            return true;
        }
        // Lock
        boolean ok = plugin.getLockCommandManager().lock(args[0]);
        sender.sendMessage(plugin.getPrefix() + (ok
            ? "§c/" + args[0] + " §7est maintenant §cverrouillée §7pour les non-OP."
            : "§e/" + args[0] + " §7était déjà verrouillée."));
        plugin.getCommandStatsManager().record("lockcommand");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                                @NotNull String l, @NotNull String[] a) {
        if (a.length == 1) return List.of("list", "unlock");
        return Collections.emptyList();
    }
}

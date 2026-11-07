package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.*;

public class QuickTpCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public QuickTpCommand(AdminMode plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommande joueur uniquement."); return true;
        }
        if (!player.hasPermission("adminmode.qtp")) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission")); return true;
        }
        plugin.getQuickTpManager().openGui(player);
        plugin.getCommandStatsManager().record("qtp");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

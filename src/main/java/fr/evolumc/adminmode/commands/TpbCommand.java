package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Option 13 — /tpb : retourner à la position d'avant la dernière TP admin. */
public class TpbCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public TpbCommand(AdminMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cJoueurs seulement.");
            return true;
        }
        if (!player.hasPermission("adminmode.use")) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
            return true;
        }
        if (!plugin.getAdminModeManager().isInAdminMode(player)) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("not-in-adminmode"));
            return true;
        }
        plugin.getAdminModeManager().teleportBack(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

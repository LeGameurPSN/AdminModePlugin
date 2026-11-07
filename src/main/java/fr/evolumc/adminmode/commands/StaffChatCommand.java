package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.managers.StaffChatManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class StaffChatCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;
    private final StaffChatManager staffChatManager;

    public StaffChatCommand(AdminMode plugin, StaffChatManager staffChatManager) {
        this.plugin = plugin;
        this.staffChatManager = staffChatManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur.");
            return true;
        }

        if (!player.hasPermission("staffchat.use")) {
            player.sendMessage(plugin.getPrefix() + scMsg("no-permission"));
            return true;
        }

        // /staffchat sans argument → toggle
        if (args.length == 0) {
            toggleStaffChat(player);
            return true;
        }

        // /staffchat on
        if (args[0].equalsIgnoreCase("on")) {
            if (staffChatManager.isInStaffChat(player)) {
                player.sendMessage(scPrefix() + "§eStaffChat déjà activé.");
            } else {
                staffChatManager.enable(player);
                player.sendMessage(scPrefix() + scMsg("toggle-on"));
            }
            return true;
        }

        // /staffchat off
        if (args[0].equalsIgnoreCase("off")) {
            if (!staffChatManager.isInStaffChat(player)) {
                player.sendMessage(scPrefix() + "§eStaffChat déjà désactivé.");
            } else {
                staffChatManager.disable(player);
                player.sendMessage(scPrefix() + scMsg("toggle-off"));
            }
            return true;
        }

        // /staffchat <message> → envoie un message one-shot sans toggle
        String message = String.join(" ", args);
        staffChatManager.sendMessage(player, message);
        return true;
    }

    private void toggleStaffChat(Player player) {
        if (staffChatManager.isInStaffChat(player)) {
            staffChatManager.disable(player);
            player.sendMessage(scPrefix() + scMsg("toggle-off"));
        } else {
            staffChatManager.enable(player);
            player.sendMessage(scPrefix() + scMsg("toggle-on"));
        }
    }

    private String scPrefix() {
        return AdminMode.colorize(plugin.getConfig().getString("staffchat.prefix", "&8[&bStaffChat&8] &r"));
    }

    private String scMsg(String key) {
        return AdminMode.colorize(plugin.getConfig().getString("staffchat." + key, "§cClé manquante : " + key));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("on", "off");
        }
        return List.of();
    }
}

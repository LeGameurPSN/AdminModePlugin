package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.managers.ChatLogManager;
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

public class ChatLogCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public ChatLogCommand(AdminMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur.");
            return true;
        }
        if (!player.hasPermission("adminmode.chatlog")) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.getPrefix() + "§7Usage : §f/chatlog <joueur> [nombre]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(plugin.getPrefix() + "§cJoueur introuvable ou hors ligne : §f" + args[0]);
            return true;
        }
        int count = 10;
        if (args.length >= 2) {
            try { count = Math.min(50, Math.max(1, Integer.parseInt(args[1]))); }
            catch (NumberFormatException ignored) {}
        }
        List<ChatLogManager.ChatEntry> logs = plugin.getChatLogManager().getLogs(target.getUniqueId(), count);
        player.sendMessage("§8§m                                        §r");
        player.sendMessage("§8[§cAdminMode§8] §7Chat log de §c" + target.getName() + " §7(§f" + logs.size() + " §7messages)");
        if (logs.isEmpty()) {
            player.sendMessage("§7Aucun message enregistré.");
        } else {
            for (ChatLogManager.ChatEntry entry : logs) {
                player.sendMessage("§8[" + entry.formattedTime() + "] §f" + entry.message());
            }
        }
        player.sendMessage("§8§m                                        §r");
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

package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.managers.ChatLogManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/** Option 6 — /cmdlog <joueur> [nombre] : voir les commandes tapées. */
public class CmdLogCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public CmdLogCommand(AdminMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("adminmode.chatlog")) {
            sender.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getPrefix() + "§7Usage : §f/cmdlog <joueur> [nombre]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getPrefix() + "§cJoueur introuvable (doit être en ligne).");
            return true;
        }
        int count = 20;
        if (args.length >= 2) {
            try { count = Math.max(1, Math.min(100, Integer.parseInt(args[1]))); }
            catch (NumberFormatException ignored) {}
        }
        List<ChatLogManager.ChatEntry> entries = plugin.getChatLogManager().getCmdLogs(target.getUniqueId(), count);
        sender.sendMessage("§8═══ §6CmdLog §7de §f" + target.getName() + " §8(" + entries.size() + " cmd) ═══");
        if (entries.isEmpty()) {
            sender.sendMessage("§7Aucune commande enregistrée.");
        } else {
            for (ChatLogManager.ChatEntry e : entries) {
                sender.sendMessage("§8[" + e.formattedTime() + "] §7" + e.message());
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).filter(n -> n.toLowerCase().startsWith(p))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

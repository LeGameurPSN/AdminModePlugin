package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.managers.WatchListManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class SurveillanceCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public SurveillanceCommand(AdminMode plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("adminmode.surveillance")) {
            sender.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission")); return true;
        }

        // /surveillance list
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            Map<UUID, WatchListManager.WatchEntry> all = plugin.getWatchListManager().getAll();
            if (all.isEmpty()) {
                sender.sendMessage(plugin.getPrefix() + "§7Aucun joueur sous surveillance.");
            } else {
                sender.sendMessage(AdminMode.colorize("&8[&6Surveillance&8] &7Joueurs surveillés :"));
                for (WatchListManager.WatchEntry e : all.values()) {
                    boolean online = Bukkit.getPlayer(e.uuid) != null;
                    sender.sendMessage(AdminMode.colorize("  &f" + e.playerName
                        + " &8— &7" + e.log.size() + " actions"
                        + (online ? " &a[EN LIGNE]" : " &c[HORS LIGNE]")));
                }
            }
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getPrefix() + "§7Usage : §f/surveillance <on|off|list> <joueur>");
            return true;
        }

        String action = args[0].toLowerCase();
        String targetName = args[1];
        WatchListManager wlm = plugin.getWatchListManager();

        if (action.equals("on")) {
            // On a besoin du joueur en ligne pour le snapshot initial
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                sender.sendMessage(plugin.getPrefix() + "§cJoueur introuvable ou hors ligne. §7La surveillance ne peut démarrer que sur un joueur connecté.");
                return true;
            }
            if (wlm.watch(target)) {
                sender.sendMessage(plugin.getPrefix() + "§a" + target.getName() + " §7est maintenant §asurveillé§7.");
                // ⚠ Ne PAS notifier la cible — la surveillance doit rester discrète
                plugin.getCommandStatsManager().record("surveillance");
            } else {
                sender.sendMessage(plugin.getPrefix() + "§e" + target.getName() + " §7est déjà sous surveillance.");
            }

        } else if (action.equals("off")) {
            // Chercher d'abord en ligne, sinon dans la watchlist (hors ligne)
            Player onlineTarget = Bukkit.getPlayerExact(targetName);
            UUID targetUuid = null;

            if (onlineTarget != null) {
                targetUuid = onlineTarget.getUniqueId();
            } else {
                // Chercher par nom dans la watchlist (joueur peut être hors ligne)
                for (Map.Entry<UUID, WatchListManager.WatchEntry> e : wlm.getAll().entrySet()) {
                    if (e.getValue().playerName.equalsIgnoreCase(targetName)) {
                        targetUuid = e.getKey(); break;
                    }
                }
            }

            if (targetUuid == null) {
                sender.sendMessage(plugin.getPrefix() + "§c" + targetName + " §7n'est pas sous surveillance (ou introuvable).");
                return true;
            }

            WatchListManager.WatchEntry entry = wlm.unwatch(targetUuid);
            if (entry != null) {
                sender.sendMessage(plugin.getPrefix() + "§cSurveillance arrêtée pour §f" + entry.playerName + "§7. Envoi du rapport Discord...");
                if (onlineTarget != null) {
                    plugin.getSessionReplayManager().generateAndSend(entry, onlineTarget);
                } else {
                    // Joueur hors ligne → rapport sans diff inventaire
                    plugin.getSessionReplayManager().generateAndSendOffline(entry);
                    sender.sendMessage(plugin.getPrefix() + "§e§oNote : joueur hors ligne, diff inventaire indisponible.");
                }
            } else {
                sender.sendMessage(plugin.getPrefix() + "§e" + targetName + " §7n'est pas sous surveillance.");
            }
        } else {
            sender.sendMessage(plugin.getPrefix() + "§7Usage : §f/surveillance <on|off|list> <joueur>");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return List.of("on", "off", "list");
        if (args.length == 2) {
            String input = args[0].toLowerCase();
            if (input.equals("off") || input.equals("list")) {
                // Compléter avec les joueurs sous surveillance
                return plugin.getWatchListManager().getAll().values().stream()
                        .map(e -> e.playerName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

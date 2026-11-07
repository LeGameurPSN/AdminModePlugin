package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.managers.AdminNoteManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Option 14 — /note <joueur> <texte>     → ajouter une note
 *             /note <joueur> list          → lister les notes
 *             /note <joueur> del <id>      → supprimer une note
 */
public class AdminNoteCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public AdminNoteCommand(AdminMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cJoueurs seulement.");
            return true;
        }
        if (!player.hasPermission("adminmode.note")) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getPrefix() + "§7Usage : §f/note <joueur> <texte|list|del <id>>");
            return true;
        }

        // Résoudre le joueur cible (online ou offline via UUID si possible)
        Player target = Bukkit.getPlayerExact(args[0]);
        UUID targetUUID = target != null ? target.getUniqueId() : null;
        if (targetUUID == null) {
            // Tenter offline player
            try {
                var offlinePlayer = Bukkit.getOfflinePlayer(args[0]);
                if (offlinePlayer.hasPlayedBefore()) targetUUID = offlinePlayer.getUniqueId();
            } catch (Exception ignored) {}
        }
        if (targetUUID == null) {
            player.sendMessage(plugin.getPrefix() + "§cJoueur inconnu : §f" + args[0]);
            return true;
        }
        String targetName = target != null ? target.getName() : args[0];

        // Sous-commandes
        if (args[1].equalsIgnoreCase("list")) {
            List<AdminNoteManager.Note> notes = plugin.getAdminNoteManager().getNotes(targetUUID);
            player.sendMessage("§8═══ §eNotes §7de §f" + targetName + " §8(" + notes.size() + ") ═══");
            if (notes.isEmpty()) {
                player.sendMessage("§7Aucune note.");
            } else {
                for (int i = 0; i < notes.size(); i++) {
                    AdminNoteManager.Note n = notes.get(i);
                    player.sendMessage("§8[§e" + i + "§8] §7[" + n.formattedTime() + "] §f" + n.author() + "§7: " + n.text());
                }
            }
            return true;
        }

        if (args[1].equalsIgnoreCase("del") || args[1].equalsIgnoreCase("delete")) {
            if (args.length < 3) {
                player.sendMessage(plugin.getPrefix() + "§7Usage : §f/note <joueur> del <id>");
                return true;
            }
            try {
                int idx = Integer.parseInt(args[2]);
                if (plugin.getAdminNoteManager().deleteNote(targetUUID, idx)) {
                    player.sendMessage(plugin.getPrefix() + "§aNote #" + idx + " supprimée.");
                } else {
                    player.sendMessage(plugin.getPrefix() + "§cNote introuvable.");
                }
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getPrefix() + "§cID invalide.");
            }
            return true;
        }

        // Ajout de note
        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        plugin.getAdminNoteManager().addNote(player, targetUUID, text);
        player.sendMessage(plugin.getPrefix() + "§aNote ajoutée sur §f" + targetName + "§a.");
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
        if (args.length == 2) return Arrays.asList("list", "del");
        return Collections.emptyList();
    }
}

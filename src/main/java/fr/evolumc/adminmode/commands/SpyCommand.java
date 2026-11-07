package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.utils.DiscordNotifier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /spy <joueur>        → ouvre inventaire + démarre spy temps réel (Option 1 & 2)
 * /spy chat            → toggle SpyChat (Option 1)
 * /track <joueur>      → suivre la position (Option 3)
 */
public class SpyCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public SpyCommand(AdminMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur.");
            return true;
        }
        if (!player.hasPermission("adminmode.spy")) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
            return true;
        }

        // /spy chat → toggle SpyChat
        if (args.length == 1 && args[0].equalsIgnoreCase("chat")) {
            plugin.getSpyChatManager().toggle(player);
            DiscordNotifier.logAction(plugin,
                "🕵 SpyChat toggled",
                "**Admin :** " + player.getName(),
                0xFFAA00);
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(plugin.getPrefix() + "§7Usage : §f/spy <joueur> §8| §f/spy chat");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(plugin.getPrefix() + "§cJoueur introuvable : §f" + args[0]);
            return true;
        }

        // Ouvre inventaire spy
        openSpyInventory(player, target);

        // Démarre spy temps réel (Option 2)
        plugin.getSpyTrackManager().startSpy(player, target);

        // Log Discord
        DiscordNotifier.logAction(plugin,
            "🔍 Spy inventaire",
            "**Admin :** " + player.getName() + "\n**Cible :** " + target.getName(),
            0xFFAA00);
        return true;
    }

    private void openSpyInventory(Player admin, Player target) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("Inventaire de " + target.getName()));

        ItemStack[] storage = target.getInventory().getStorageContents();
        for (int i = 0; i < Math.min(36, storage.length); i++) {
            if (storage[i] != null) inv.setItem(i, storage[i].clone());
        }
        ItemStack[] armor = target.getInventory().getArmorContents();
        if (armor.length >= 4) {
            if (armor[3] != null) inv.setItem(36, armor[3].clone());
            if (armor[2] != null) inv.setItem(37, armor[2].clone());
            if (armor[1] != null) inv.setItem(38, armor[1].clone());
            if (armor[0] != null) inv.setItem(39, armor[0].clone());
        }
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        if (pm != null) { pm.displayName(Component.text(" ")); pane.setItemMeta(pm); }
        inv.setItem(40, pane);
        ItemStack offHand = target.getInventory().getItemInOffHand();
        if (offHand.getType() != Material.AIR) inv.setItem(41, offHand.clone());
        for (int i = 42; i < 54; i++) inv.setItem(i, pane.clone());

        admin.openInventory(inv);
        admin.sendMessage(plugin.getPrefix() + "§7Inventaire de §c" + target.getName() + " §7(lecture seule) + spy temps réel actif.");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            completions.add("chat");
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(p))
                    .forEach(completions::add);
            return completions;
        }
        return Collections.emptyList();
    }
}

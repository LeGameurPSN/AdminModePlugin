package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.managers.HudPreferenceManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Option 9 — /adminmode hud ouvre un GUI pour activer/désactiver les éléments du HUD.
 * S'enregistre aussi comme Listener pour gérer les clics dans le GUI.
 */
public class HudGuiCommand implements CommandExecutor, TabCompleter, Listener {

    private final AdminMode plugin;
    private static final String GUI_TITLE = "⚙ Configuration HUD";

    public HudGuiCommand(AdminMode plugin) {
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
        openGui(player);
        return true;
    }

    public void openGui(Player player) {
        HudPreferenceManager prefs = plugin.getHudPreferenceManager();
        Inventory gui = Bukkit.createInventory(null, 27, Component.text(GUI_TITLE));
        HudPreferenceManager.HudElement[] elements = HudPreferenceManager.HudElement.values();

        // Slot 10 à 14 pour les 5 éléments (centré sur 9 cases)
        int[] slots = {10, 12, 14, 16, 20};

        for (int i = 0; i < elements.length; i++) {
            HudPreferenceManager.HudElement el = elements[i];
            boolean enabled = prefs.isEnabled(player.getUniqueId(), el);
            ItemStack item = new ItemStack(enabled ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text((enabled ? "§a✔ " : "§c✘ ") + el.getLabel()));
                meta.lore(List.of(
                        Component.text("§7Cliquez pour " + (enabled ? "§cdésactiver" : "§aactiver")),
                        Component.text("§8Élément : §f" + el.getDisplay())
                ));
                item.setItemMeta(meta);
            }
            int slot = i < slots.length ? slots[i] : 10 + i * 2;
            gui.setItem(slot, item);
        }

        // Bouton fermer
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        if (cm != null) { cm.displayName(Component.text("§cFermer")); close.setItemMeta(cm); }
        gui.setItem(22, close);

        player.openInventory(gui);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());
        if (!title.equals(GUI_TITLE)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Bouton fermer
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        // Identifier l'élément cliqué via le displayName
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(meta.displayName());
        // Supprimer le préfixe ✔/✘
        String clean = name.replace("✔ ", "").replace("✘ ", "").trim();

        for (HudPreferenceManager.HudElement el : HudPreferenceManager.HudElement.values()) {
            if (el.getLabel().equals(clean)) {
                plugin.getHudPreferenceManager().toggle(player.getUniqueId(), el);
                boolean now = plugin.getHudPreferenceManager().isEnabled(player.getUniqueId(), el);
                player.sendMessage(plugin.getPrefix() + "§7HUD §f" + el.getLabel()
                        + (now ? " §a→ Activé" : " §c→ Désactivé"));
                // Rouvrir pour voir l'état mis à jour
                Bukkit.getScheduler().runTaskLater(plugin, () -> openGui(player), 1L);
                return;
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

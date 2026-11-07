package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QuickTpManager implements Listener {

    private final AdminMode plugin;
    private final Map<UUID, String> adminGui = new HashMap<>(); // admin UUID → GUI title

    public QuickTpManager(AdminMode plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player admin) {
        var online = Bukkit.getOnlinePlayers();
        // On exclut l'admin lui-même de la liste affichée : calculer la taille
        // sur le nombre réel d'items à afficher, pas sur online.size().
        int displayCount = (int) online.stream()
                .filter(p -> !p.getUniqueId().equals(admin.getUniqueId()))
                .count();
        if (displayCount == 0) {
            admin.sendMessage(plugin.getPrefix() + "§7Aucun autre joueur en ligne.");
            return;
        }
        int size = Math.min(54, (int)(Math.ceil(displayCount / 9.0)) * 9);
        String title = "§8[§6QuickTP§8] §7Clic G: TP vers | Clic D: Amener ici";
        Inventory inv = Bukkit.createInventory(null, size, Component.text(title));
        adminGui.put(admin.getUniqueId(), title);

        for (Player p : online) {
            if (p.getUniqueId().equals(admin.getUniqueId())) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(p);
                meta.displayName(Component.text("§f" + p.getName()));
                meta.lore(java.util.List.of(
                    Component.text("§a▶ Clic gauche §7: TP vers " + p.getName()),
                    Component.text("§c◀ Clic droit §7: Amener " + p.getName() + " ici")
                ));
                head.setItemMeta(meta);
            }
            inv.addItem(head);
        }
        admin.openInventory(inv);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        String title = adminGui.get(admin.getUniqueId());
        if (title == null) return;
        String viewTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (!viewTitle.contains("QuickTP")) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
        SkullMeta meta = (SkullMeta) clicked.getItemMeta();
        if (meta == null || meta.getOwningPlayer() == null) return;
        Player target = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
        if (target == null) {
            admin.sendMessage(plugin.getPrefix() + "§cJoueur hors ligne.");
            admin.closeInventory();
            return;
        }
        admin.closeInventory();
        if (event.isLeftClick()) {
            admin.teleport(target.getLocation());
            admin.sendMessage(plugin.getPrefix() + "§7TP vers §f" + target.getName());
        } else if (event.isRightClick()) {
            target.teleport(admin.getLocation());
            admin.sendMessage(plugin.getPrefix() + "§f" + target.getName() + " §7amené vers vous.");
        }
        adminGui.remove(admin.getUniqueId());
    }
}

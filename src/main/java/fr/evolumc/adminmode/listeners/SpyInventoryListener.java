package fr.evolumc.adminmode.listeners;

import fr.evolumc.adminmode.AdminMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

/**
 * Bloque les clics dans l'inventaire spy.
 * Rafraîchit l'inventaire spy en temps réel quand la cible drop/ramasse des items.
 */
public class SpyInventoryListener implements Listener {

    private static final String SPY_TITLE_PREFIX = "Inventaire de ";
    private final AdminMode plugin;

    public SpyInventoryListener(AdminMode plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView() == null) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.startsWith(SPY_TITLE_PREFIX)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView() == null) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.startsWith(SPY_TITLE_PREFIX)) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player admin)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.startsWith(SPY_TITLE_PREFIX)) {
            if (plugin.getSpyTrackManager().isSpy(admin)) {
                plugin.getSpyTrackManager().stopSpy(admin);
                admin.sendMessage(plugin.getPrefix() + "§7Spy temps réel arrêté (inventaire fermé).");
            }
        }
    }

    /** Quand la cible drop un item → rafraîchit le spy inventaire de tous les admins qui la spient. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        refreshSpyFor(event.getPlayer());
    }

    /** Quand la cible ramasse un item → rafraîchit le spy inventaire. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshSpyFor(p), 1L);
    }

    private void refreshSpyFor(Player target) {
        UUID targetId = target.getUniqueId();
        for (Player admin : Bukkit.getOnlinePlayers()) {
            UUID spyTarget = plugin.getSpyTrackManager().getSpyTarget(admin.getUniqueId());
            if (!targetId.equals(spyTarget)) continue;
            // Vérifier que l'admin a bien un GUI spy ouvert
            if (admin.getOpenInventory() == null) continue;
            String title = PlainTextComponentSerializer.plainText()
                    .serialize(admin.getOpenInventory().title());
            if (!title.startsWith(SPY_TITLE_PREFIX)) continue;
            // Rafraîchir les contenus
            Inventory inv = admin.getOpenInventory().getTopInventory();
            // Clear et re-fill
            for (int i = 0; i < 42; i++) inv.setItem(i, null);
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
        }
    }
}

package fr.evolumc.adminmode.listeners;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.managers.AdminPanelManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Gère les clics dans le GUI AdminPanel et le menu d'actions.
 */
public class AdminPanelListener implements Listener {

    private final AdminMode plugin;

    public AdminPanelListener(AdminMode plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        String title = event.getView().getTitle();

        // ── Panel principal ────────────────────────────────────────────────
        if (title.equals(AdminPanelManager.PANEL_TITLE)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            int slot = event.getSlot();

            // Navigation
            if (slot == 45) { // ← Précédent
                int page = plugin.getAdminPanelManager().getCurrentPage(admin.getUniqueId());
                plugin.getAdminPanelManager().openPanel(admin, page - 1);
                return;
            }
            if (slot == 53) { // → Suivant
                int page = plugin.getAdminPanelManager().getCurrentPage(admin.getUniqueId());
                plugin.getAdminPanelManager().openPanel(admin, page + 1);
                return;
            }
            if (slot == 49) return; // Info, pas d'action

            // Clic sur une tête de joueur → ouvrir menu d'actions
            ItemStack item = event.getCurrentItem();
            if (item.getItemMeta() instanceof SkullMeta sm) {
                String targetName = sm.getOwningPlayer() != null
                        ? sm.getOwningPlayer().getName()
                        : item.getItemMeta().getDisplayName().replace("§f", "");
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    admin.sendMessage(plugin.getPrefix() + "§cCe joueur s'est déconnecté.");
                    plugin.getAdminPanelManager().openPanel(admin, plugin.getAdminPanelManager().getCurrentPage(admin.getUniqueId()));
                    return;
                }
                admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
                plugin.getAdminPanelManager().openActionMenu(admin, target);
            }
        }

        // ── Menu d'actions ────────────────────────────────────────────────
        else if (title.startsWith(AdminPanelManager.ACTION_TITLE)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            int slot = event.getSlot();

            String targetName = title.substring(AdminPanelManager.ACTION_TITLE.length());
            Player target = Bukkit.getPlayerExact(targetName);

            // ← Retour (slot 22)
            if (slot == 22) {
                int page = plugin.getAdminPanelManager().getCurrentPage(admin.getUniqueId());
                plugin.getAdminPanelManager().openPanel(admin, page);
                return;
            }

            if (target == null) {
                admin.sendMessage(plugin.getPrefix() + "§cCe joueur s'est déconnecté.");
                plugin.getAdminPanelManager().openPanel(admin, plugin.getAdminPanelManager().getCurrentPage(admin.getUniqueId()));
                return;
            }

            admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);

            switch (slot) {
                case 0 -> { // Téléporter vers
                    if (!admin.hasPermission("adminmode.tp")) { noPermMsg(admin); return; }
                    admin.teleport(target.getLocation());
                    admin.sendMessage(plugin.getPrefix() + "§7Téléporté vers §f" + target.getName() + "§7.");
                    admin.closeInventory();
                }
                case 1 -> { // Téléporter ici
                    if (!admin.hasPermission("adminmode.tp")) { noPermMsg(admin); return; }
                    target.teleport(admin.getLocation());
                    admin.sendMessage(plugin.getPrefix() + "§f" + target.getName() + " §7a été téléporté vers vous.");
                    admin.closeInventory();
                }
                case 2 -> { // Freeze toggle
                    if (!admin.hasPermission("adminmode.freeze")) { noPermMsg(admin); return; }
                    plugin.getFreezeManager().toggleFull(target, admin, "", 0);
                    // Rafraîchir le menu
                    Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getAdminPanelManager().openActionMenu(admin, target), 1L);
                }
                case 3 -> { // Surveillance toggle
                    if (!admin.hasPermission("adminmode.surveillance")) { noPermMsg(admin); return; }
                    var wlm = plugin.getWatchListManager();
                    if (wlm.isWatched(target.getUniqueId())) {
                        Bukkit.dispatchCommand(admin, "surveillance off " + target.getName());
                    } else {
                        wlm.watch(target);
                        admin.sendMessage(plugin.getPrefix() + "§a" + target.getName() + " §7est maintenant §asurveillé§7.");
                    }
                    Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getAdminPanelManager().openActionMenu(admin, target), 2L);
                }
                case 4 -> { // InvSee + snapshot Discord
                    if (!admin.hasPermission("adminmode.invsee")) { noPermMsg(admin); return; }
                    // Ouvrir l'inventaire du joueur ciblé
                    admin.openInventory(target.getInventory());
                    // Snapshot Discord en async
                    Bukkit.getScheduler().runTaskAsynchronously(plugin,
                            () -> plugin.getWatchListManager().sendInventorySnapshot(target, admin.getName()));
                }
                case 5 -> { // Notes
                    if (!admin.hasPermission("adminmode.note")) { noPermMsg(admin); return; }
                    admin.closeInventory();
                    Bukkit.dispatchCommand(admin, "note " + target.getName() + " list");
                }
                case 6 -> { // Report (tous peuvent reporter)
                    admin.closeInventory();
                    admin.sendMessage(plugin.getPrefix() + "§7Usage : §f/report " + target.getName() + " <raison>");
                }
                case 7 -> { // Kick
                    if (!admin.hasPermission("adminmode.kick")) { noPermMsg(admin); return; }
                    target.kickPlayer(AdminMode.colorize("&cVous avez été expulsé par un administrateur."));
                    admin.sendMessage(plugin.getPrefix() + "§f" + target.getName() + " §7a été expulsé.");
                    admin.closeInventory();
                }
            }
        }
    }

    private void noPermMsg(Player admin) {
        admin.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
    }
}

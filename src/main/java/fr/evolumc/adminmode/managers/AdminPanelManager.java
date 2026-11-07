package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * Gère l'interface graphique du panneau d'administration.
 *
 * Layout (54 slots = 6 rangées) :
 *  Rangées 0-4 : têtes des joueurs en ligne (5 × 9 = 45 slots max)
 *  Rangée 5    : pagination (◀ slot 45 / ▶ slot 53) + info (slot 49)
 *
 * Clic sur une tête → ouvre le menu d'actions du joueur (27 slots).
 */
public class AdminPanelManager {

    public static final String PANEL_TITLE  = "§8AdminPanel — Joueurs en ligne";
    public static final String ACTION_TITLE = "§8Actions — ";

    private static final int PAGE_SIZE = 45;

    private final AdminMode plugin;
    /** uuid admin → page courante du panel */
    private final Map<UUID, Integer> currentPage = new HashMap<>();

    public AdminPanelManager(AdminMode plugin) {
        this.plugin = plugin;
    }

    // ─── Panel principal ───────────────────────────────────────────────────

    public void openPanel(Player admin, int page) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.removeIf(p -> p.equals(admin)); // ne pas s'afficher soi-même
        int maxPage = Math.max(1, (int) Math.ceil(online.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, maxPage - 1));
        currentPage.put(admin.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(null, 54, PANEL_TITLE);

        // ── Têtes des joueurs ──────────────────────────────────────────────
        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, online.size());
        for (int i = start; i < end; i++) {
            Player target = online.get(i);
            inv.setItem(i - start, buildPlayerHead(target));
        }

        // ── Navigation ────────────────────────────────────────────────────
        if (page > 0)         inv.setItem(45, buildNavItem(Material.ARROW, "§aPrecedent", "§7Page " + page));
        if (page < maxPage-1) inv.setItem(53, buildNavItem(Material.ARROW, "§aSuivant",   "§7Page " + (page+2)));

        // ── Info centrale ──────────────────────────────────────────────────
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta  im   = info.getItemMeta();
        im.setDisplayName("§7" + online.size() + " joueur(s) en ligne");
        im.setLore(List.of("§8Page §f" + (page+1) + "§8/§f" + maxPage));
        info.setItemMeta(im);
        inv.setItem(49, info);

        admin.openInventory(inv);
    }

    private ItemStack buildPlayerHead(Player target) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) skull.getItemMeta();
        sm.setOwningPlayer(target);
        sm.setDisplayName("§f" + target.getName());

        boolean frozen  = plugin.getFreezeManager().isFrozen(target.getUniqueId());
        boolean watched = plugin.getWatchListManager().isWatched(target.getUniqueId());
        boolean admin   = plugin.getAdminModeManager().isInAdminMode(target);
        boolean vanish  = plugin.getVanishManager().isVanished(target);

        List<String> lore = new ArrayList<>();
        lore.add("§7Clic droit §8→ §fActions");
        lore.add("");
        if (frozen)  lore.add("§b❄ Gelé");
        if (watched) lore.add("§6👁 Sous surveillance");
        if (admin)   lore.add("§c⚡ Mode Admin");
        if (vanish)  lore.add("§8👻 Vanish");
        if (!frozen && !watched && !admin && !vanish) lore.add("§aEn ligne");
        sm.setLore(lore);
        skull.setItemMeta(sm);
        return skull;
    }

    private ItemStack buildNavItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  im   = item.getItemMeta();
        im.setDisplayName(name);
        im.setLore(List.of(lore));
        item.setItemMeta(im);
        return item;
    }

    // ─── Menu d'actions ────────────────────────────────────────────────────

    /**
     * Ouvre le menu d'actions pour un joueur ciblé.
     * Layout 27 slots :
     *   [0] Téléporter vers    [1] Téléporter ici
     *   [2] Freeze / Unfreeze  [3] Surveillance on/off
     *   [4] InvSee             [5] Note
     *   [6] Report             [7] Kick (si perm)
     *  [22] ← Retour
     */
    public void openActionMenu(Player admin, Player target) {
        Inventory inv = Bukkit.createInventory(null, 27, ACTION_TITLE + target.getName());

        boolean frozen  = plugin.getFreezeManager().isFrozen(target.getUniqueId());
        boolean watched = plugin.getWatchListManager().isWatched(target.getUniqueId());

        inv.setItem(0,  action(Material.ENDER_PEARL,     "§aTéléporter vers §f" + target.getName(), "§7Vous téléporte à lui", "adminmode.tp"));
        inv.setItem(1,  action(Material.LEAD,            "§aTéléporter ici",                        "§7L'amène à vous",       "adminmode.tp"));
        inv.setItem(2,  action(frozen ? Material.PACKED_ICE : Material.ICE,
                frozen ? "§bDégeler §f" + target.getName() : "§bGeler §f" + target.getName(),
                "§7Toggle freeze", "adminmode.freeze"));
        inv.setItem(3,  action(watched ? Material.REDSTONE : Material.COMPARATOR,
                watched ? "§cArrêter surveillance" : "§6Surveiller §f" + target.getName(),
                "§7Toggle surveillance", "adminmode.surveillance"));
        inv.setItem(4,  action(Material.CHEST,           "§eVoir l'inventaire",    "§7InvSee + snapshot Discord", "adminmode.invsee"));
        inv.setItem(5,  action(Material.BOOK,            "§7Voir les notes",       "§7Notes admin sur ce joueur", "adminmode.note"));
        inv.setItem(6,  action(Material.PAPER,           "§cSignaler",             "§7Ouvre un report",           null));
        inv.setItem(7,  action(Material.BARRIER,         "§cKick",                 "§7Expulse le joueur",         "adminmode.kick"));

        // Tête du joueur ciblé au centre
        inv.setItem(13, buildPlayerHead(target));

        // ← Retour
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta  bm   = back.getItemMeta();
        bm.setDisplayName("§7← Retour");
        back.setItemMeta(bm);
        inv.setItem(22, back);

        admin.openInventory(inv);
    }

    private ItemStack action(Material mat, String name, String lore, String permission) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  im   = item.getItemMeta();
        im.setDisplayName(name);
        im.setLore(permission == null
                ? List.of(lore)
                : List.of(lore, "§8Permission : §7" + permission));
        item.setItemMeta(im);
        return item;
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public int getCurrentPage(UUID uuid) {
        return currentPage.getOrDefault(uuid, 0);
    }
}

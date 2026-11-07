package fr.evolumc.adminmode.managers;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;

public class AdminData {

    // ─── État survival ─────────────────────────────────────────────────────
    private final Location savedLocation;
    private final GameMode savedGameMode;
    private final double savedHealth;
    private final int savedFoodLevel;
    private final float savedSaturation;
    private final int savedFireTicks;
    private final float savedFallDistance;
    private final boolean savedFlying;
    private final float savedFlySpeed;
    private final float savedWalkSpeed;

    // ─── Inventaire survival ───────────────────────────────────────────────
    private final ItemStack[] savedInventory;
    private final ItemStack[] savedArmor;
    private final ItemStack savedOffHand;
    private final int savedHeldItemSlot;

    // ─── XP survival ──────────────────────────────────────────────────────
    private final int savedLevel;
    private final float savedExp;
    private final int savedTotalExp;

    // ─── Potions survival ─────────────────────────────────────────────────
    private final Collection<PotionEffect> savedPotionEffects;
    /** Timestamp (ms) du moment où AdminData a été créé = entrée en admin mode */
    private final long savedAtMs = System.currentTimeMillis();

    // ─── Advancements survival ─────────────────────────────────────────────
    private final Map<String, Set<String>> savedAdvancements;

    // ─── Données admin persistantes ────────────────────────────────────────
    private Map<String, Set<String>> adminAdvancements = new HashMap<>();
    private ItemStack[] adminInventory = null;
    private ItemStack[] adminArmor = null;
    private ItemStack adminOffHand = null;
    private int adminHeldItemSlot = 0;

    public AdminData(Player player) {
        this.savedLocation        = player.getLocation().clone();
        this.savedGameMode        = player.getGameMode();
        this.savedHealth          = player.getHealth();
        this.savedFoodLevel       = player.getFoodLevel();
        this.savedSaturation      = player.getSaturation();
        this.savedFireTicks       = player.getFireTicks();
        this.savedFallDistance    = player.getFallDistance();
        this.savedFlying          = player.isFlying();
        this.savedFlySpeed        = player.getFlySpeed();
        this.savedWalkSpeed       = player.getWalkSpeed();
        this.savedInventory       = cloneContents(player.getInventory().getStorageContents());
        this.savedArmor           = cloneContents(player.getInventory().getArmorContents());
        ItemStack off             = player.getInventory().getItemInOffHand();
        this.savedOffHand         = off.getType().isAir() ? null : off.clone();
        this.savedHeldItemSlot    = player.getInventory().getHeldItemSlot();
        this.savedLevel           = player.getLevel();
        this.savedExp             = player.getExp();
        this.savedTotalExp        = player.getTotalExperience();
        this.savedPotionEffects   = new ArrayList<>(player.getActivePotionEffects());
        this.savedAdvancements    = captureAdvancements(player);
    }

    // ─── Utilitaires ──────────────────────────────────────────────────────

    private static ItemStack[] cloneContents(ItemStack[] source) {
        if (source == null) return new ItemStack[0];
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = (source[i] != null && !source[i].getType().isAir()) ? source[i].clone() : null;
        }
        return copy;
    }

    public static Map<String, Set<String>> captureAdvancements(Player player) {
        Map<String, Set<String>> map = new HashMap<>();
        Iterator<Advancement> it = org.bukkit.Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement adv = it.next();
            AdvancementProgress progress = player.getAdvancementProgress(adv);
            Set<String> awarded = new HashSet<>(progress.getAwardedCriteria());
            if (!awarded.isEmpty()) {
                map.put(adv.getKey().toString(), awarded);
            }
        }
        return map;
    }

    // ─── Getters survival ─────────────────────────────────────────────────

    public Location getSavedLocation()                           { return savedLocation.clone(); }
    public GameMode getSavedGameMode()                           { return savedGameMode; }
    public double getSavedHealth()                               { return savedHealth; }
    public int getSavedFoodLevel()                               { return savedFoodLevel; }
    public float getSavedSaturation()                            { return savedSaturation; }
    public int getSavedFireTicks()                               { return savedFireTicks; }
    public float getSavedFallDistance()                          { return savedFallDistance; }
    public boolean isSavedFlying()                               { return savedFlying; }
    public float getSavedFlySpeed()                              { return savedFlySpeed; }
    public float getSavedWalkSpeed()                             { return savedWalkSpeed; }
    public ItemStack[] getSavedInventory()                       { return cloneContents(savedInventory); }
    public ItemStack[] getSavedArmor()                           { return cloneContents(savedArmor); }
    public ItemStack getSavedOffHand()                           { return savedOffHand != null ? savedOffHand.clone() : null; }
    public int getSavedHeldItemSlot()                            { return savedHeldItemSlot; }
    public int getSavedLevel()                                   { return savedLevel; }
    public float getSavedExp()                                   { return savedExp; }
    public int getSavedTotalExp()                                { return savedTotalExp; }
    public Collection<PotionEffect> getSavedPotionEffects()      { return savedPotionEffects; }
    public long getSavedAtMs()                                   { return savedAtMs; }
    public Map<String, Set<String>> getSavedAdvancements()       { return savedAdvancements; }

    // ─── Inventaire admin ─────────────────────────────────────────────────

    public boolean hasAdminInventory() { return adminInventory != null; }

    public void saveAdminInventory(Player player) {
        adminInventory    = cloneContents(player.getInventory().getStorageContents());
        adminArmor        = cloneContents(player.getInventory().getArmorContents());
        ItemStack off     = player.getInventory().getItemInOffHand();
        adminOffHand      = off.getType().isAir() ? null : off.clone();
        adminHeldItemSlot = player.getInventory().getHeldItemSlot();
    }

    public void setAdminInventoryRaw(ItemStack[] inv, ItemStack[] armor, ItemStack offHand, int slot) {
        this.adminInventory    = cloneContents(inv);
        this.adminArmor        = cloneContents(armor);
        this.adminOffHand      = offHand != null ? offHand.clone() : null;
        this.adminHeldItemSlot = slot;
    }

    public ItemStack[] getAdminInventory()  { return cloneContents(adminInventory); }
    public ItemStack[] getAdminArmor()      { return cloneContents(adminArmor); }
    public ItemStack getAdminOffHand()      { return adminOffHand != null ? adminOffHand.clone() : null; }
    public int getAdminHeldItemSlot()       { return adminHeldItemSlot; }

    // ─── Advancements admin ───────────────────────────────────────────────

    public void saveAdminAdvancements(Player player)            { adminAdvancements = captureAdvancements(player); }
    public Map<String, Set<String>> getAdminAdvancements()      { return adminAdvancements; }
    public void setAdminAdvancements(Map<String, Set<String>> m){ this.adminAdvancements = m; }
}

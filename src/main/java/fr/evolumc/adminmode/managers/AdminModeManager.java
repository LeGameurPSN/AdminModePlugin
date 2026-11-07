package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.utils.DiscordNotifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.time.Duration;
import java.util.*;

public class AdminModeManager {

    private final AdminMode plugin;
    private final Map<UUID, AdminData> adminDataMap = new HashMap<>();
    private final Set<UUID> activeAdmins = new HashSet<>();
    private final Set<UUID> suppressingAdvancements = new HashSet<>();

    public AdminModeManager(AdminMode plugin) {
        this.plugin = plugin;
    }

    // ─── API publique ─────────────────────────────────────────────────────────

    public boolean isInAdminMode(Player player) {
        return activeAdmins.contains(player.getUniqueId());
    }

    public boolean isSuppressingAdvancements(Player player) {
        return suppressingAdvancements.contains(player.getUniqueId());
    }

    public void enable(Player player) {
        UUID uuid = player.getUniqueId();

        // Guard : empêche un double-appel (race condition ou commande rapide)
        // Sans ce guard, une 2e invocation remplacerait l'AdminData par un snapshot
        // de l'état admin (creative, inventaire vide) comme état "survival" → données perdues.
        if (activeAdmins.contains(uuid)) return;

        // 1. Créer ou récupérer l'AdminData
        AdminData data;
        if (adminDataMap.containsKey(uuid)) {
            AdminData old = adminDataMap.get(uuid);
            data = new AdminData(player);
            if (old.hasAdminInventory()) transferAdminData(old, data);
        } else {
            data = new AdminData(player);
        }
        adminDataMap.put(uuid, data);

        // 2. Vider l'inventaire survival
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);

        // 3. Retirer les effets de potions survival
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // 4. Passer en créatif
        player.setGameMode(GameMode.CREATIVE);

        // 5. Restaurer l'inventaire admin (si existant)
        if (data.hasAdminInventory()) restoreAdminInventory(player, data);

        // 6. Marquer actif — les advancements survival restent tels quels ;
        //    tout advancement obtenu en admin sera supprimé à la sortie.
        activeAdmins.add(uuid);

        // 7. Santé / faim max
        player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);

        // 9. Démarrer le compteur de session
        plugin.getSessionStatsManager().startSession(uuid);

        // 10. Message + broadcast
        player.sendMessage(plugin.getPrefix() + plugin.getMessage("activated"));
        broadcastIfNeeded(player, true);

        // 11. Titre d'écran
        player.showTitle(Title.title(
                Component.text("§cMODE ADMIN"),
                Component.text("§7Inventaire sauvegardé — advancements admin éphémères"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500))
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.5f);

        // 12. Proposer le vanish via message cliquable
        sendVanishPrompt(player);

        // 13. Notification Discord
        notifyDiscord(player, true);
    }

    public void disable(Player player) {
        UUID uuid = player.getUniqueId();
        AdminData data = adminDataMap.get(uuid);
        if (data == null) return;

        // Si l'admin est en spec furtif, le sortir d'abord
        if (plugin.getSpectatorManager().isWatching(uuid)) {
            plugin.getSpectatorManager().stopSpec(player);
        }

        // 1. Sauvegarder l'inventaire admin
        data.saveAdminInventory(player);

        // 2. Supprimer les advancements obtenus en admin (non sauvegardés volontairement)
        //    puis restaurer exactement les advancements survival sauvegardés à l'entrée.
        // 3. Basculer vers les advancements survival (diff précis : révoque l'excédent,
        //    restaure le manquant — ne touche pas aux critères déjà corrects)
        suppressingAdvancements.add(uuid);
        try {
            applyAdvancements(player, data.getSavedAdvancements());
        } finally {
            suppressingAdvancements.remove(uuid);
        }

        // 4. Vider l'inventaire admin
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);

        // 5. Retirer les effets de potions admin
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // 6. Dé-marquer actif AVANT le setGameMode — sinon onGameModeChange (priority HIGH)
        //    voit encore le joueur en admin mode et annule le passage en SURVIVAL/ADVENTURE.
        activeAdmins.remove(uuid);

        // 7. Restaurer le gamemode
        player.setGameMode(data.getSavedGameMode());

        // 8. Restaurer l'inventaire survival
        player.getInventory().setStorageContents(data.getSavedInventory());
        player.getInventory().setArmorContents(data.getSavedArmor());
        ItemStack savedOff = data.getSavedOffHand();
        player.getInventory().setItemInOffHand(savedOff != null ? savedOff : new ItemStack(Material.AIR));
        player.getInventory().setHeldItemSlot(data.getSavedHeldItemSlot());

        // 9. Restaurer XP
        if (plugin.getConfig().getBoolean("settings.save-experience", true)) {
            player.setLevel(data.getSavedLevel());
            player.setExp(data.getSavedExp());
            player.setTotalExperience(data.getSavedTotalExp());
        }

        // 10. Restaurer la santé / faim
        //     Math.max(1.0, ...) évite de tuer le joueur si la santé sauvegardée
        //     était aberrante (ex. snapshot pris juste avant une mort annulée).
        if (plugin.getConfig().getBoolean("settings.restore-health-food", true)) {
            double maxHp = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            player.setHealth(Math.min(Math.max(data.getSavedHealth(), 1.0), maxHp));
            player.setFoodLevel(data.getSavedFoodLevel());
            player.setSaturation(data.getSavedSaturation());
            player.setFireTicks(data.getSavedFireTicks());
            player.setFallDistance(data.getSavedFallDistance());
        }

        // 11. Restaurer les effets de potion en déduisant le temps passé en admin mode.
        //     Les durées PotionEffect sont en ticks (20 ticks = 1 s).
        //     Un effet dont la durée restante serait ≤ 0 n'est pas réappliqué.
        //     Les effets infinis (durée == -1 ou Integer.MAX_VALUE) sont toujours restaurés tels quels.
        if (plugin.getConfig().getBoolean("settings.save-potion-effects", true)) {
            int elapsedTicks = (int) ((System.currentTimeMillis() - data.getSavedAtMs()) / 50L);
            for (PotionEffect effect : data.getSavedPotionEffects()) {
                int originalDuration = effect.getDuration();
                // -1 et Integer.MAX_VALUE = effet permanent (beacon, etc.) → conserver
                if (originalDuration < 0 || originalDuration == Integer.MAX_VALUE) {
                    player.addPotionEffect(effect);
                    continue;
                }
                int remaining = originalDuration - elapsedTicks;
                if (remaining > 0) {
                    player.addPotionEffect(new PotionEffect(
                            effect.getType(),
                            remaining,
                            effect.getAmplifier(),
                            effect.isAmbient(),
                            effect.hasParticles(),
                            effect.hasIcon()
                    ));
                }
                // remaining ≤ 0 → l'effet aurait déjà expiré : on ne le restaure pas
            }
        }

        // 12. Restaurer le vol
        if (data.getSavedGameMode() == GameMode.SURVIVAL || data.getSavedGameMode() == GameMode.ADVENTURE) {
            player.setAllowFlight(false);
            player.setFlying(false);
        } else {
            player.setAllowFlight(data.isSavedFlying());
            player.setFlying(data.isSavedFlying());
        }
        player.setFlySpeed(data.getSavedFlySpeed());
        player.setWalkSpeed(data.getSavedWalkSpeed());

        // 13. Téléporter à la position d'activation
        player.teleport(data.getSavedLocation());

        // 14. Terminer le compteur de session
        plugin.getSessionStatsManager().endSession(uuid);

        // 15. Message + broadcast
        player.sendMessage(plugin.getPrefix() + plugin.getMessage("deactivated"));
        broadcastIfNeeded(player, false);

        // 16. Titre + son — skip si le joueur se déconnecte (onQuit appelle disable() ;
        //     showTitle/playSound sur un joueur offline génèrent des warnings Spigot/Paper).
        if (player.isOnline()) {
            player.showTitle(Title.title(
                    Component.text("§aSURVIE"),
                    Component.text("§7Position et inventaire restaurés"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500))
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
        }

        // 17. Notification Discord
        notifyDiscord(player, false);
    }

    /**
     * Téléporte l'admin à sa position d'activation sans désactiver le mode admin.
     */
    public void teleportBack(Player player) {
        UUID uuid = player.getUniqueId();
        AdminData data = adminDataMap.get(uuid);
        if (data == null) return;
        player.teleport(data.getSavedLocation());
        player.sendMessage(plugin.getPrefix() + "§7Téléporté à votre position d'activation.");
    }

    public void disableAllAdmins() {
        new HashSet<>(activeAdmins).forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) disable(p);
        });
    }

    // ─── Advancements ─────────────────────────────────────────────────────────

    /**
     * Remplace les advancements actuels du joueur par ceux de {@code target}.
     * Révoque uniquement les critères absents de target, octroie uniquement
     * ceux qui manquent — sans jamais toucher aux critères déjà corrects.
     * Utilisé à la sortie du mode admin pour restaurer exactement l'état survival
     * et effacer tout advancement obtenu pendant la session admin.
     */
    private void applyAdvancements(Player player, Map<String, Set<String>> target) {
        Iterator<Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement adv = it.next();
            String key = adv.getKey().toString();
            AdvancementProgress progress = player.getAdvancementProgress(adv);
            Set<String> wanted  = target.getOrDefault(key, Collections.emptySet());
            Set<String> current = new HashSet<>(progress.getAwardedCriteria());

            // Révoquer uniquement les critères excédentaires
            for (String criterion : current) {
                if (!wanted.contains(criterion)) {
                    progress.revokeCriteria(criterion);
                }
            }
            // Octroyer uniquement les critères manquants
            for (String criterion : wanted) {
                if (!current.contains(criterion)) {
                    progress.awardCriteria(criterion);
                }
            }
        }
    }

    // ─── Vanish prompt ────────────────────────────────────────────────────────

    private void sendVanishPrompt(Player player) {
        if (!player.hasPermission("adminmode.vanish")) return;

        Component oui = Component.text("[Oui]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("§aActiver le vanish")))
                .clickEvent(ClickEvent.runCommand("/vanish"));

        Component non = Component.text("[Non]")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("§cRester visible")))
                .clickEvent(ClickEvent.runCommand("/adminmode vanishno"));

        player.sendMessage(
                Component.text(AdminMode.colorize("&8[&3ArcadiaVanish&8] &7Voulez-vous passer en vanish ? "))
                        .append(oui)
                        .append(Component.text(" "))
                        .append(non)
        );
    }

    // ─── Discord ──────────────────────────────────────────────────────────────

    private void notifyDiscord(Player player, boolean enabled) {
        if (!plugin.getConfig().getBoolean("discord.notify-admin-toggle", false)) return;
        String webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        if (webhookUrl.isEmpty()) return;
        String title = enabled ? "🔴 Mode Admin activé" : "🟢 Mode Admin désactivé";
        String desc = "**" + player.getName() + "** " + (enabled ? "est entré" : "est sorti") + " du mode administrateur.";
        int color = enabled ? 0xFF4444 : 0x44FF44;
        DiscordNotifier.sendEmbed(plugin, webhookUrl, title, desc, color);
    }

    // ─── Helpers inventaire admin ─────────────────────────────────────────────

    private void restoreAdminInventory(Player player, AdminData data) {
        player.getInventory().setStorageContents(data.getAdminInventory());
        player.getInventory().setArmorContents(data.getAdminArmor());
        ItemStack off = data.getAdminOffHand();
        player.getInventory().setItemInOffHand(off != null ? off : new ItemStack(Material.AIR));
        player.getInventory().setHeldItemSlot(data.getAdminHeldItemSlot());
    }

    private void transferAdminData(AdminData source, AdminData target) {
        if (source.hasAdminInventory()) copyAdminInventoryBetween(source, target);
    }

    private void copyAdminInventoryBetween(AdminData source, AdminData dest) {
        dest.setAdminInventoryRaw(source.getAdminInventory(), source.getAdminArmor(),
                source.getAdminOffHand(), source.getAdminHeldItemSlot());
    }

    // ─── Broadcast ────────────────────────────────────────────────────────────

    private void broadcastIfNeeded(Player player, boolean enabled) {
        if (!plugin.getConfig().getBoolean("settings.broadcast-toggle", false)) return;
        String key = enabled ? "settings.broadcast-message-enable" : "settings.broadcast-message-disable";
        String msg = plugin.getConfig().getString(key, "").replace("{player}", player.getName());
        if (!msg.isEmpty()) Bukkit.broadcastMessage(AdminMode.colorize(msg));
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    public Map<UUID, AdminData> getAdminDataMap() { return adminDataMap; }
    public Set<UUID> getActiveAdmins() { return activeAdmins; }
}

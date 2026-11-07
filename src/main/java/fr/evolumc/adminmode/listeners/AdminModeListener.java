package fr.evolumc.adminmode.listeners;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

import java.io.File;
import java.io.IOException;

public class AdminModeListener implements Listener {

    private final AdminMode plugin;
    private final File staffChatFile;
    private YamlConfiguration staffChatData;

    public AdminModeListener(AdminMode plugin) {
        this.plugin = plugin;
        this.staffChatFile = new File(plugin.getDataFolder(), "staffchat-persistent.yml");
        if (!staffChatFile.exists()) {
            try { staffChatFile.createNewFile(); } catch (IOException ignored) {}
        }
        this.staffChatData = YamlConfiguration.loadConfiguration(staffChatFile);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Sauvegarder le temps de connexion avant toute autre opération
        plugin.getOnlineDurationManager().onQuit(player);
        plugin.getSpectatorManager().onTargetQuit(player.getUniqueId());
        // Sauvegarder l'état StaffChat persistant
        if (plugin.getStaffChatManager().isInStaffChat(player)) {
            staffChatData.set(player.getUniqueId().toString(), true);
        } else {
            staffChatData.set(player.getUniqueId().toString(), false);
        }
        saveStaffChatData();
        if (plugin.getAdminModeManager().isInAdminMode(player)) {
            plugin.getAdminModeManager().disable(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getAdminModeManager().getActiveAdmins().contains(player.getUniqueId())) {
            plugin.getAdminModeManager().disable(player);
        }
        // Restaurer StaffChat persistant
        if (staffChatData.getBoolean(player.getUniqueId().toString(), false)
                && player.hasPermission("staffchat.use")) {
            plugin.getStaffChatManager().enable(player);
            player.sendMessage(AdminMode.colorize("&8[&bStaffChat&8] &7StaffChat §brestauré &7(persistant)."));
        }

        // Restaurer SpyChat persistant (géré par SpyChatManager)
        plugin.getSpyChatManager().onPlayerJoin(player);

        // Rappeler le statut de gel (géré par FreezeManager — survive aux redémarrages)
        plugin.getFreezeManager().onPlayerJoin(player);

        // Notifier le staff des reports en attente (1 tick de délai pour que tous les messages de connexion soient envoyés d'abord)
        if (player.hasPermission("adminmode.report.manage")) {
            int open = plugin.getReportManager().getOpenCount();
            if (open > 0) {
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline())
                        player.sendMessage(AdminMode.colorize(
                                "&8[&cReports&8] &c" + open + " &7report(s) ouvert(s) — &f/report list"));
                }, 20L);
            }
        }

        // Note : onJoin pour OnlineDurationManager est géré par OnlineDurationListener
        // pour éviter le double appel (loginTimes écrase la vraie heure de connexion).
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (plugin.getAdminModeManager().isInAdminMode(player)) {
            event.setKeepInventory(true); event.getDrops().clear();
            event.setKeepLevel(true); event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (plugin.getAdminModeManager().isInAdminMode(player)) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && plugin.getAdminModeManager().isInAdminMode(player))
                    player.setGameMode(GameMode.CREATIVE);
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getAdminModeManager().isInAdminMode(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getAdminModeManager().isInAdminMode(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getAdminModeManager().isInAdminMode(player)) return;
        if (event.getNewGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
            player.sendMessage(plugin.getPrefix() + "§cDésactivez le mode admin avant de changer de gamemode.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        var manager = plugin.getAdminModeManager();
        if (manager.isInAdminMode(player) || manager.isSuppressingAdvancements(player))
            event.message(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent event) {}

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getAdminModeManager().isInAdminMode(player)) return;
        String cmd = event.getMessage().toLowerCase();
        if (cmd.startsWith("/gamemode") || cmd.startsWith("/gm ") ||
                cmd.equals("/gms") || cmd.startsWith("/gms ") ||
                cmd.equals("/gmc") || cmd.startsWith("/gmc ") ||
                cmd.equals("/gmsp") || cmd.startsWith("/gmsp ") ||
                cmd.equals("/gma") || cmd.startsWith("/gma ")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getPrefix() + "§cDésactivez le mode admin (§f/adminmode§c) avant de changer de gamemode.");
        }
    }

    private void saveStaffChatData() {
        // Copie de la config au moment de l'appel (main thread), écriture async
        // pour ne pas bloquer le thread principal sur un I/O disque.
        final YamlConfiguration snapshot = YamlConfiguration.loadConfiguration(staffChatFile);
        for (String key : staffChatData.getKeys(false)) {
            snapshot.set(key, staffChatData.get(key));
        }
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try { snapshot.save(staffChatFile); } catch (IOException e) {
                plugin.getLogger().warning("[StaffChatPersist] " + e.getMessage());
            }
        });
    }
}

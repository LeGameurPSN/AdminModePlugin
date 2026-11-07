package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class HUDManager {

    private final AdminMode plugin;
    private BukkitTask task;

    public HUDManager(AdminMode plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateHUD, 20L, 20L);
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
    }

    private void updateHUD() {
        AdminModeManager adminMgr   = plugin.getAdminModeManager();
        FreezeManager    freezeMgr  = plugin.getFreezeManager();
        SessionStatsManager statsMgr = plugin.getSessionStatsManager();
        HudPreferenceManager prefMgr = plugin.getHudPreferenceManager();

        int online        = Bukkit.getOnlinePlayers().size();
        int frozenCount   = freezeMgr.getFrozen().size();
        String tpsFormatted = formatTps(Bukkit.getServer().getTPS()[0]);

        for (UUID uuid : adminMgr.getActiveAdmins()) {
            Player admin = Bukkit.getPlayer(uuid);
            if (admin == null || !admin.isOnline()) continue;

            String sessionTime = "0s";
            Long start = statsMgr.getSessionStart(uuid);
            if (start != null) {
                long elapsed = System.currentTimeMillis() - start;
                sessionTime = statsMgr.formatDuration(elapsed);
            }

            boolean inVanish = plugin.getVanishManager().isVanished(admin);

            StringBuilder bar = new StringBuilder("§8[§cAdmin§8] ");

            if (prefMgr.isEnabled(uuid, HudPreferenceManager.HudElement.TPS)) {
                bar.append("§7TPS: ").append(tpsFormatted).append(" §8| ");
            }
            if (prefMgr.isEnabled(uuid, HudPreferenceManager.HudElement.ONLINE)) {
                bar.append("§7En ligne: §f").append(online).append(" §8| ");
            }
            if (prefMgr.isEnabled(uuid, HudPreferenceManager.HudElement.FROZEN)) {
                bar.append("§7Gelés: §c").append(frozenCount).append(" §8| ");
            }
            if (prefMgr.isEnabled(uuid, HudPreferenceManager.HudElement.SESSION)) {
                bar.append("§7Session: §e").append(sessionTime);
            }
            if (prefMgr.isEnabled(uuid, HudPreferenceManager.HudElement.VANISH) && inVanish) {
                bar.append(" §8| §7Vanish: §aON");
            }

            String result = bar.toString();
            if (result.endsWith("§8| ")) result = result.substring(0, result.length() - 4);

            admin.sendActionBar(Component.text(result));
        }
    }

    private String formatTps(double tps) {
        double clamped = Math.min(20.0, tps);
        String formatted = String.format("%.1f", clamped);
        if (clamped >= 18.0) return "§a" + formatted;
        if (clamped >= 15.0) return "§e" + formatted;
        return "§c" + formatted;
    }
}

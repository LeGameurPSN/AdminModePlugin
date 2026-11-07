package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.utils.DiscordNotifier;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Envoie un rapport quotidien sur Discord à 00h00.
 */
public class DailyReportManager {

    private final AdminMode plugin;
    private BukkitTask task;

    public DailyReportManager(AdminMode plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Calcul du délai jusqu'à minuit
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        long delaySeconds = ChronoUnit.SECONDS.between(now, midnight);
        long delayTicks = delaySeconds * 20L;
        long periodTicks = 24 * 60 * 60 * 20L; // 24h

        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sendReport, delayTicks, periodTicks);
        plugin.getLogger().info("[DailyReport] Prochain rapport dans " + (delaySeconds / 60) + " min.");
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void sendReport() {
        String webhook = plugin.getConfig().getString("daily-report.discord-webhook", "");
        if (webhook.isEmpty()) return;

        FreezeManager fm = plugin.getFreezeManager();
        CommandStatsManager csm = plugin.getCommandStatsManager();
        OnlineDurationManager odm = plugin.getOnlineDurationManager();

        StringBuilder desc = new StringBuilder();
        desc.append("**📊 Rapport du ").append(java.time.LocalDate.now().minusDays(1)).append("**\n\n");
        desc.append("**Freezes :** ").append(fm.getTodayFreezeCount()).append("\n");
        desc.append("**Joueurs connectés (actifs) :** ").append(Bukkit.getOfflinePlayers().length).append("\n");

        desc.append("\n**Top 5 commandes :**\n");
        List<Map.Entry<String, Integer>> top = csm.getTopTotal(5);
        for (int i = 0; i < top.size(); i++) {
            desc.append("`").append(i + 1).append(". /").append(top.get(i).getKey())
                .append(" — ").append(top.get(i).getValue()).append("x`\n");
        }

        boolean maintenance = plugin.getMaintenanceManager().isEnabled();
        desc.append("\n**Maintenance :** ").append(maintenance ? "🔴 Active" : "🟢 Inactive");

        // sendEmbedChunked découpe automatiquement si le rapport dépasse 1470 chars
        DiscordNotifier.sendEmbedChunked(plugin, webhook, "📋 Rapport Quotidien AdminMode", desc.toString(), 0x3498DB, 1470);
        // Reset daily stats
        csm.resetDaily();
        fm.resetTodayCount();
    }
}

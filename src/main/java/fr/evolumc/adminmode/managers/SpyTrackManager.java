package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Option 2 — Spy en temps réel : affiche en action bar ce que le joueur regardé fait
 * (bloc ciblé, vie, faim, position, direction) pendant que l'admin a l'inventaire ouvert OU via /spy.
 *
 * Option 3 — Track : met à jour la boussole de l'admin vers la cible toutes les secondes.
 */
public class SpyTrackManager {

    private final AdminMode plugin;

    // Option 2 : admin UUID → cible UUID + task
    private final Map<UUID, UUID> spyingTargets = new HashMap<>();
    private final Map<UUID, BukkitTask> spyTasks = new HashMap<>();

    // Option 3 : admin UUID → cible UUID + task
    private final Map<UUID, UUID> trackingTargets = new HashMap<>();
    private final Map<UUID, BukkitTask> trackTasks = new HashMap<>();

    public SpyTrackManager(AdminMode plugin) {
        this.plugin = plugin;
    }

    // ─── Option 2 : Spy temps réel ───────────────────────────────────────────

    public void startSpy(Player admin, Player target) {
        stopSpy(admin); // arrêter un spy précédent
        UUID adminId = admin.getUniqueId();
        UUID targetId = target.getUniqueId();
        spyingTargets.put(adminId, targetId);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player t = Bukkit.getPlayer(targetId);
            if (t == null || !t.isOnline()) {
                stopSpy(admin);
                admin.sendMessage(plugin.getPrefix() + "§c" + target.getName() + " s'est déconnecté, spy arrêté.");
                return;
            }
            if (!admin.isOnline()) {
                stopSpy(admin);
                return;
            }
            Location loc = t.getLocation();
            String blockLooking = getTargetBlock(t);
            String bar = "§8[§6SPY §f" + t.getName() + "§8] "
                    + "§7❤ §c" + String.format("%.1f", t.getHealth() / 2) + " "
                    + "§7🍗 §a" + t.getFoodLevel() + " "
                    + "§7📍 §f" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                    + " §8(§7" + directionName(loc.getYaw()) + "§8) "
                    + "§7🔲 §e" + blockLooking;
            admin.sendActionBar(Component.text(bar));
        }, 0L, 10L); // toutes les 0.5s

        spyTasks.put(adminId, task);
        admin.sendMessage(plugin.getPrefix() + "§aSpy temps réel sur §f" + target.getName() + " §aactivé.");
    }

    public void stopSpy(Player admin) {
        UUID adminId = admin.getUniqueId();
        BukkitTask t = spyTasks.remove(adminId);
        if (t != null) t.cancel();
        spyingTargets.remove(adminId);
    }

    public boolean isSpy(Player admin) {
        return spyingTargets.containsKey(admin.getUniqueId());
    }

    public UUID getSpyTarget(UUID adminId) {
        return spyingTargets.get(adminId);
    }

    // ─── Option 3 : Track position ───────────────────────────────────────────

    public void startTrack(Player admin, Player target) {
        stopTrack(admin);
        UUID adminId = admin.getUniqueId();
        UUID targetId = target.getUniqueId();
        trackingTargets.put(adminId, targetId);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player t = Bukkit.getPlayer(targetId);
            if (t == null || !t.isOnline()) {
                stopTrack(admin);
                admin.sendMessage(plugin.getPrefix() + "§c" + target.getName() + " s'est déconnecté, track arrêté.");
                return;
            }
            if (!admin.isOnline()) {
                stopTrack(admin);
                return;
            }
            // Met à jour la boussole
            if (admin.getWorld().equals(t.getWorld())) {
                admin.setCompassTarget(t.getLocation());
                double dist = admin.getLocation().distance(t.getLocation());
                double angle = admin.getLocation().toVector()
                        .subtract(t.getLocation().toVector())
                        .angle(admin.getLocation().getDirection()) * 180 / Math.PI;
                String relDir = angle < 0 ? "→" : "←";
                String bar = "§8[§dTRACK §f" + t.getName() + "§8] "
                        + "§7Distance: §b" + String.format("%.0f", dist) + "m "
                        + "§7Dir: §f" + relDir
                        + " §7(§e" + directionName(angleToTarget(admin.getLocation(), t.getLocation())) + "§7)";
                admin.sendActionBar(Component.text(bar));
            } else {
                admin.sendActionBar(Component.text("§8[§dTRACK§8] §e" + target.getName() + " §7est dans un autre monde : §c" + t.getWorld().getName()));
            }
        }, 0L, 20L); // toutes les secondes

        trackTasks.put(adminId, task);
        admin.sendMessage(plugin.getPrefix() + "§dTrack activé sur §f" + target.getName() + "§d. Votre boussole pointe vers lui.");
    }

    public void stopTrack(Player admin) {
        UUID adminId = admin.getUniqueId();
        BukkitTask t = trackTasks.remove(adminId);
        if (t != null) t.cancel();
        trackingTargets.remove(adminId);
        // Réinitialiser la boussole
        if (admin.isOnline() && admin.getWorld().getSpawnLocation() != null) {
            admin.setCompassTarget(admin.getWorld().getSpawnLocation());
        }
    }

    public boolean isTracking(Player admin) {
        return trackingTargets.containsKey(admin.getUniqueId());
    }

    public UUID getTrackTarget(UUID adminId) {
        return trackingTargets.get(adminId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String getTargetBlock(Player target) {
        try {
            var block = target.getTargetBlockExact(5);
            if (block == null) return "Air";
            return block.getType().name().toLowerCase().replace("_", " ");
        } catch (Exception e) {
            return "?";
        }
    }

    /** Calcule l'angle horizontal vers une cible depuis une origine. */
    private float angleToTarget(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private String directionName(float yaw) {
        yaw = ((yaw % 360) + 360) % 360;
        if (yaw < 22.5 || yaw >= 337.5) return "S";
        if (yaw < 67.5)  return "SO";
        if (yaw < 112.5) return "O";
        if (yaw < 157.5) return "NO";
        if (yaw < 202.5) return "N";
        if (yaw < 247.5) return "NE";
        if (yaw < 292.5) return "E";
        return "SE";
    }
}

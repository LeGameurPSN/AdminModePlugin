package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracker de temps de connexion par joueur (persistant JSON/YAML).
 */
public class OnlineDurationManager {

    private final AdminMode plugin;
    private final File dataFile;
    private YamlConfiguration data;
    private final Map<UUID, Long> loginTimes = new HashMap<>();

    public OnlineDurationManager(AdminMode plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "playtime.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void onJoin(Player p) {
        loginTimes.put(p.getUniqueId(), System.currentTimeMillis());
    }

    public void onQuit(Player p) {
        UUID uid = p.getUniqueId();
        if (!loginTimes.containsKey(uid)) return;
        long session = System.currentTimeMillis() - loginTimes.remove(uid);
        long total = data.getLong(uid + ".total", 0) + session;
        data.set(uid + ".total", total);
        data.set(uid + ".name", p.getName());
        save();
    }

    /** Retourne le temps total (en secondes) incluant la session actuelle. */
    public long getTotalSeconds(Player p) {
        long stored = data.getLong(p.getUniqueId() + ".total", 0);
        long sessionMs = 0;
        if (loginTimes.containsKey(p.getUniqueId())) {
            sessionMs = System.currentTimeMillis() - loginTimes.get(p.getUniqueId());
        }
        return (stored + sessionMs) / 1000;
    }

    public String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h + "h " + m + "m " + s + "s";
    }

    private void save() {
        // Prendre un snapshot des clés modifiées avant de passer en async
        // pour éviter une ConcurrentModificationException si un autre joueur
        // se déconnecte pendant l'écriture.
        final YamlConfiguration snapshot = new YamlConfiguration();
        for (String key : data.getKeys(true)) {
            snapshot.set(key, data.get(key));
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try { snapshot.save(dataFile); } catch (IOException e) {
                plugin.getLogger().warning("[OnlineDuration] Erreur sauvegarde : " + e.getMessage());
            }
        });
    }

    public Map<String, Long> getTopPlaytimes(int limit) {
        Map<String, Long> result = new HashMap<>();
        for (String key : data.getKeys(false)) {
            String name = data.getString(key + ".name", key);
            long total = data.getLong(key + ".total", 0);
            result.put(name, total / 1000);
        }
        return result;
    }
}

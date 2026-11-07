package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class CommandStatsManager {

    private final AdminMode plugin;
    private final File dataFile;
    private YamlConfiguration data;
    private final Map<String, Integer> sessionStats = new HashMap<>();

    public CommandStatsManager(AdminMode plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "command-stats.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void record(String command) {
        sessionStats.merge(command, 1, Integer::sum);
        int total = data.getInt("total." + command, 0) + 1;
        data.set("total." + command, total);
        if (total % 20 == 0) save();
    }

    public List<Map.Entry<String, Integer>> getTopSession(int limit) {
        return sessionStats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit).collect(Collectors.toList());
    }

    public List<Map.Entry<String, Integer>> getTopTotal(int limit) {
        Map<String, Integer> totalMap = new HashMap<>();
        ConfigurationSection sec = data.getConfigurationSection("total");
        if (sec != null) for (String k : sec.getKeys(false)) totalMap.put(k, sec.getInt(k, 0));
        return totalMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit).collect(Collectors.toList());
    }

    public void resetDaily() { data.set("total", null); save(); }

    private void save() {
        try { data.save(dataFile); } catch (IOException e) {
            plugin.getLogger().warning("[CommandStats] " + e.getMessage());
        }
    }
}

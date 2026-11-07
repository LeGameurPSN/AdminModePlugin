package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gère les cooldowns par commande et par groupe de permission.
 * Config :
 * cooldowns:
 *   freeze:
 *     adminmode.senior: 0
 *     adminmode.junior: 30
 *   surveillance:
 *     default: 10
 */
public class CooldownManager {

    private final AdminMode plugin;
    // commande → UUID → timestamp last use
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();

    public CooldownManager(AdminMode plugin) {
        this.plugin = plugin;
    }

    /**
     * @return secondes restantes, 0 si pas de cooldown.
     */
    public int getRemainingCooldown(Player player, String command) {
        int cd = getCooldownSeconds(player, command);
        if (cd <= 0) return 0;
        Map<UUID, Long> map = cooldowns.getOrDefault(command, new HashMap<>());
        Long last = map.get(player.getUniqueId());
        if (last == null) return 0;
        long elapsed = (System.currentTimeMillis() - last) / 1000;
        int remaining = (int) (cd - elapsed);
        return Math.max(0, remaining);
    }

    public boolean isOnCooldown(Player player, String command) {
        return getRemainingCooldown(player, command) > 0;
    }

    public void setCooldown(Player player, String command) {
        cooldowns.computeIfAbsent(command, k -> new HashMap<>())
                 .put(player.getUniqueId(), System.currentTimeMillis());
    }

    private int getCooldownSeconds(Player player, String command) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("cooldowns." + command);
        if (section == null) return 0;
        // Chercher la permission la plus précise
        for (String key : section.getKeys(false)) {
            if (!key.equals("default") && player.hasPermission(key)) {
                return section.getInt(key, 0);
            }
        }
        return section.getInt("default", 0);
    }
}

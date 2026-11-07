package fr.evolumc.adminmode.vanish.managers;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;

public class NoclipManager {

    private final AdminMode plugin;
    /** UUID → gamemode d'avant le noclip */
    private final Map<UUID, GameMode> previousGameMode = new HashMap<>();

    public NoclipManager(AdminMode plugin) {
        this.plugin = plugin;
    }

    public boolean isNoclipping(Player player) {
        return previousGameMode.containsKey(player.getUniqueId());
    }

    public void enableNoclip(Player player) {
        previousGameMode.put(player.getUniqueId(), player.getGameMode());
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(plugin.getPrefix() + "§aNoclip §lactivé §7(traversée des murs).");
    }

    public void disableNoclip(Player player) {
        GameMode prev = previousGameMode.remove(player.getUniqueId());
        if (prev != null) player.setGameMode(prev);
        else player.setGameMode(GameMode.SURVIVAL);
        player.sendMessage(plugin.getPrefix() + "§cNoclip §ldésactivé§c.");
    }

    public boolean toggle(Player player) {
        if (isNoclipping(player)) {
            disableNoclip(player);
            return false;
        } else {
            enableNoclip(player);
            return true;
        }
    }

    public void stopAll() {
        for (UUID uuid : new HashSet<>(previousGameMode.keySet())) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) disableNoclip(p);
        }
        previousGameMode.clear();
    }
}

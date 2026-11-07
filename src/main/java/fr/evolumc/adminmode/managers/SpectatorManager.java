package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;

public class SpectatorManager {

    private final AdminMode plugin;

    /** adminUUID → targetUUID */
    private final Map<UUID, UUID> watching = new HashMap<>();

    public SpectatorManager(AdminMode plugin) {
        this.plugin = plugin;
    }

    /**
     * Démarre le spectateur furtif : l'admin observe la cible en SPECTATOR.
     */
    public void startSpec(Player admin, Player target) {
        watching.put(admin.getUniqueId(), target.getUniqueId());
        admin.setGameMode(GameMode.SPECTATOR);
        admin.setSpectatorTarget(target);
        admin.sendMessage(plugin.getPrefix() + "§7Vous observez §c" + target.getName() + "§7. §8(/adminmode spec pour quitter)");
    }

    /**
     * Arrête le spectateur furtif et remet l'admin en créatif.
     */
    public void stopSpec(Player admin) {
        watching.remove(admin.getUniqueId());
        admin.setGameMode(GameMode.CREATIVE);
        admin.setSpectatorTarget(null);
        admin.sendMessage(plugin.getPrefix() + "§7Vous avez quitté le mode spectateur.");
    }

    public boolean isWatching(UUID adminUUID) {
        return watching.containsKey(adminUUID);
    }

    /**
     * @return UUID de la cible observée, ou null si l'admin n'est pas en spec.
     */
    public UUID getTarget(UUID adminUUID) {
        return watching.get(adminUUID);
    }

    /**
     * Appelé quand une cible se déconnecte :
     * tous les admins qui l'observaient sont sortis du mode spectateur.
     */
    public void onTargetQuit(UUID targetUUID) {
        List<UUID> affectedAdmins = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : watching.entrySet()) {
            if (entry.getValue().equals(targetUUID)) {
                affectedAdmins.add(entry.getKey());
            }
        }
        for (UUID adminUUID : affectedAdmins) {
            Player admin = Bukkit.getPlayer(adminUUID);
            if (admin != null && admin.isOnline()) {
                stopSpec(admin);
                admin.sendMessage(plugin.getPrefix() + "§eLa cible observée s'est déconnectée.");
            } else {
                watching.remove(adminUUID);
            }
        }
    }
}

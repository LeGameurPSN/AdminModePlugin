package fr.evolumc.adminmode.vanish.managers;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.vanish.integration.DiscordSrvBridge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class VanishManager {

    private final AdminMode plugin;
    private final Set<UUID> vanished = new HashSet<>();
    private final Map<UUID, Boolean> previousFlyState = new HashMap<>();

    public VanishManager(AdminMode plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    public void vanish(Player player) {
        UUID uuid = player.getUniqueId();
        previousFlyState.put(uuid, player.getAllowFlight());

        player.setAllowFlight(true);
        player.setFlying(true);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.hidePlayer(plugin, player);
        }

        player.setMetadata("vanished", new FixedMetadataValue(plugin, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));

        vanished.add(uuid);

        player.sendMessage(AdminMode.colorize("&8[&3ArcadiaVanish&8] &aVous êtes maintenant &linvisible &a(vol activé)."));
        broadcastFakeQuit(player);
    }

    public void unvanish(Player player, boolean teleportBack) {
        UUID uuid = player.getUniqueId();
        vanished.remove(uuid);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, player);
        }

        player.removeMetadata("vanished", plugin);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        Boolean prev = previousFlyState.getOrDefault(uuid, false);
        player.setAllowFlight(prev);
        if (!prev) player.setFlying(false);
        previousFlyState.remove(uuid);

        player.sendMessage(AdminMode.colorize("&8[&3ArcadiaVanish&8] &cVous êtes de nouveau &lvisible" +
                (teleportBack ? " &7(retour au point de départ)." : ".")));

        broadcastFakeJoin(player);
    }

    /**
     * Applique l'état de vanish au reconnect si le joueur était vanish.
     */
    public void applyJoinStateTo(Player player) {
        if (vanished.contains(player.getUniqueId())) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.hidePlayer(plugin, player);
                }
            }
            player.setMetadata("vanished", new FixedMetadataValue(plugin, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            player.setAllowFlight(true);
            player.setFlying(true);
        }
    }

    public void unvanishAll() {
        for (UUID uuid : new HashSet<>(vanished)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) unvanish(p, false);
        }
        vanished.clear();
    }

    private void broadcastFakeQuit(Player player) {
        String template = plugin.getConfig().getString("vanish.messages.player-quit",
                "&3[-] &o&6{player} &aa quitté le serveur !");
        broadcastFormatted(template, player);
        DiscordSrvBridge bridge = plugin.getDiscordSrvBridge();
        if (bridge != null) bridge.sendRealLeaveMessage(player);
    }

    private void broadcastFakeJoin(Player player) {
        String template = plugin.getConfig().getString("vanish.messages.player-join",
                "&3[+] &o&6{player} &aa rejoint le serveur !");
        broadcastFormatted(template, player);
    }

    private void broadcastFormatted(String template, Player player) {
        String msg = AdminMode.colorize(template.replace("{player}", player.getName()));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.sendMessage(msg);
            }
        }
    }

    public Set<UUID> getVanished() {
        return Collections.unmodifiableSet(vanished);
    }
}

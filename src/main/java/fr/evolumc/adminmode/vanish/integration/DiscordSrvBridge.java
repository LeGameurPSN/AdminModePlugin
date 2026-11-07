package fr.evolumc.adminmode.vanish.integration;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public class DiscordSrvBridge {

    private final Logger logger;
    private Object discordSRV; // instance DiscordSRV via reflection

    public DiscordSrvBridge(Logger logger) {
        this.logger = logger;
        try {
            Class<?> clazz = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            this.discordSRV = clazz.getMethod("getPlugin").invoke(null);
            logger.info("[ArcadiaVanish] DiscordSRV détecté, bridge activé.");
        } catch (Exception e) {
            this.discordSRV = null;
            logger.info("[ArcadiaVanish] DiscordSRV absent, bridge désactivé.");
        }
    }

    public boolean isAvailable() {
        return discordSRV != null;
    }

    /**
     * Envoie le vrai message de départ via DiscordSRV (pour que Discord voie la vraie déco).
     */
    public void sendRealLeaveMessage(Player player) {
        if (discordSRV == null) return;
        try {
            discordSRV.getClass()
                    .getMethod("updateDiscordChannelTopic", boolean.class)
                    .invoke(discordSRV, false);
        } catch (Exception ignored) {}
    }
}

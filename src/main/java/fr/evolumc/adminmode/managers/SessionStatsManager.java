package fr.evolumc.adminmode.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionStatsManager {

    /** Horodatage de début de la session en cours (null si pas en mode admin). */
    private final Map<UUID, Long> sessionStarts = new HashMap<>();

    /** Temps total accumulé en mode admin (en millisecondes). */
    private final Map<UUID, Long> totalMillis = new HashMap<>();

    /** Nombre de sessions admin. */
    private final Map<UUID, Integer> sessionCounts = new HashMap<>();

    /**
     * Démarre une session admin pour le joueur.
     */
    public void startSession(UUID uuid) {
        sessionStarts.put(uuid, System.currentTimeMillis());
        sessionCounts.merge(uuid, 1, Integer::sum);
    }

    /**
     * Termine la session admin et cumule le temps.
     */
    public void endSession(UUID uuid) {
        Long start = sessionStarts.remove(uuid);
        if (start == null) return;
        long elapsed = System.currentTimeMillis() - start;
        totalMillis.merge(uuid, elapsed, Long::sum);
    }

    /**
     * Retourne le temps total cumulé en millisecondes (sessions terminées uniquement).
     */
    public long getTotalMillis(UUID uuid) {
        return totalMillis.getOrDefault(uuid, 0L);
    }

    /**
     * Retourne le timestamp de début de la session en cours (en ms),
     * ou null si le joueur n'est pas en mode admin.
     */
    public Long getSessionStart(UUID uuid) {
        return sessionStarts.get(uuid);
    }

    /**
     * Retourne le nombre de sessions admin.
     */
    public int getSessionCount(UUID uuid) {
        return sessionCounts.getOrDefault(uuid, 0);
    }

    /**
     * Formate une durée en millisecondes en "Xh Ym Zs".
     */
    public String formatDuration(long millis) {
        long seconds = millis / 1000;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0 || h > 0) sb.append(m).append("m ");
        sb.append(s).append("s");
        return sb.toString().trim();
    }
}

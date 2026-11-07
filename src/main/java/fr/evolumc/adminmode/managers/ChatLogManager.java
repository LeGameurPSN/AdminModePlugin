package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Gère les logs de chat ET de commandes.
 * - En RAM : N dernières entrées par joueur (rapide à afficher)
 * - Sur disque : plugins/AdminMode/logs/<uuid>/<date>.log (persistant)
 */
public class ChatLogManager {

    public record ChatEntry(long timestamp, String type, String message) {
        private static final DateTimeFormatter FORMATTER =
                DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
        private static final DateTimeFormatter DATE_FMT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

        public String formattedTime() {
            return FORMATTER.format(Instant.ofEpochMilli(timestamp));
        }
        public String todayDate() {
            return DATE_FMT.format(Instant.ofEpochMilli(timestamp));
        }
    }

    private final AdminMode plugin;
    private final int maxLines;
    private final Map<UUID, ArrayDeque<ChatEntry>> logs = new HashMap<>();
    private final Path logsDir;

    public ChatLogManager(AdminMode plugin) {
        this.plugin = plugin;
        this.maxLines = plugin.getConfig().getInt("chatlog.max-lines", 50);
        this.logsDir = plugin.getDataFolder().toPath().resolve("logs");
        try {
            Files.createDirectories(logsDir);
        } catch (IOException e) {
            plugin.getLogger().warning("[ChatLog] Impossible de créer le dossier logs/ : " + e.getMessage());
        }
        scheduleLogRotation();
    }

    // ─── Ajout de messages chat ───────────────────────────────────────────────

    public void addMessage(Player player, String message) {
        add(player.getUniqueId(), "CHAT", message);
    }

    /** Enregistre une commande tapée par le joueur. */
    public void addCommand(UUID uuid, String command) {
        add(uuid, "CMD", command);
    }

    // ─── API interne ──────────────────────────────────────────────────────────

    private void add(UUID uuid, String type, String message) {
        ChatEntry entry = new ChatEntry(System.currentTimeMillis(), type, message);
        // RAM
        ArrayDeque<ChatEntry> deque = logs.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        deque.addLast(entry);
        while (deque.size() > maxLines) deque.pollFirst();
        // Disque — async pour ne pas bloquer le main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeToFile(uuid, entry));
    }

    // ─── Lecture ─────────────────────────────────────────────────────────────

    /**
     * Retourne les `count` dernières entrées (chat + commandes) depuis la RAM.
     */
    public List<ChatEntry> getLogs(UUID uuid, int count) {
        ArrayDeque<ChatEntry> deque = logs.get(uuid);
        if (deque == null || deque.isEmpty()) return Collections.emptyList();
        List<ChatEntry> all = new ArrayList<>(deque);
        int from = Math.max(0, all.size() - count);
        return all.subList(from, all.size());
    }

    /**
     * Retourne uniquement les commandes (type CMD) des N dernières entrées.
     */
    public List<ChatEntry> getCmdLogs(UUID uuid, int count) {
        ArrayDeque<ChatEntry> deque = logs.get(uuid);
        if (deque == null) return Collections.emptyList();
        List<ChatEntry> all = new ArrayList<>(deque);
        List<ChatEntry> cmds = new ArrayList<>();
        for (ChatEntry e : all) if ("CMD".equals(e.type())) cmds.add(e);
        int from = Math.max(0, cmds.size() - count);
        return cmds.subList(from, cmds.size());
    }

    // ─── Disque ───────────────────────────────────────────────────────────────

    private void writeToFile(UUID uuid, ChatEntry entry) {
        try {
            Path playerDir = logsDir.resolve(uuid.toString());
            Files.createDirectories(playerDir);
            Path logFile = playerDir.resolve(entry.todayDate() + ".log");
            String line = "[" + entry.formattedTime() + "] [" + entry.type() + "] " + entry.message() + "\n";
            Files.writeString(logFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("[ChatLog] Erreur écriture log : " + e.getMessage());
        }
    }

    // ─── Rotation des logs ────────────────────────────────────────────────────

    /**
     * Planifie une rotation quotidienne des logs.
     * Les fichiers .log de plus de {@code chatlog.archive-after-days} jours (défaut 30)
     * sont déplacés dans {@code logs/archive/} et non supprimés — traçabilité conservée.
     */
    private void scheduleLogRotation() {
        // Exécuter 5 minutes après le démarrage, puis toutes les 24h
        long delay  = 20L * 60L * 5L;
        long period = 20L * 60L * 60L * 24L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::rotateLogs, delay, period);
    }

    private void rotateLogs() {
        int keepDays = plugin.getConfig().getInt("chatlog.archive-after-days", 30);
        long cutoffMs = System.currentTimeMillis() - (keepDays * 86_400_000L);
        Path archiveDir = logsDir.resolve("archive");
        int moved = 0;
        try {
            Files.createDirectories(archiveDir);
            // Parcourir tous les sous-dossiers UUID
            try (DirectoryStream<Path> uuidDirs = Files.newDirectoryStream(logsDir)) {
                for (Path uuidDir : uuidDirs) {
                    if (!Files.isDirectory(uuidDir)) continue;
                    if (uuidDir.equals(archiveDir)) continue;
                    try (DirectoryStream<Path> logFiles = Files.newDirectoryStream(uuidDir, "*.log")) {
                        for (Path logFile : logFiles) {
                            BasicFileAttributes attrs = Files.readAttributes(logFile, BasicFileAttributes.class);
                            if (attrs.lastModifiedTime().toMillis() < cutoffMs) {
                                Path dest = archiveDir
                                        .resolve(uuidDir.getFileName())
                                        .resolve(logFile.getFileName());
                                Files.createDirectories(dest.getParent());
                                Files.move(logFile, dest, StandardCopyOption.REPLACE_EXISTING);
                                moved++;
                            }
                        }
                    } catch (IOException ignored) {}
                }
            }
            if (moved > 0)
                plugin.getLogger().info("[ChatLog] Rotation : " + moved + " fichier(s) archivé(s) (>" + keepDays + " jours).");
        } catch (IOException e) {
            plugin.getLogger().warning("[ChatLog] Erreur rotation : " + e.getMessage());
        }
    }
}

package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.utils.DiscordNotifier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Système de WatchList / Surveillance — PERSISTANT.
 *
 * Structure sur disque : plugins/AdminMode/surveillance/active/<uuid>.json
 * Format :
 * {
 *   "player": "Notch",
 *   "uuid": "...",
 *   "start": 1234567890,
 *   "items_before": {"DIAMOND": 3, ...},
 *   "log": ["[12:00:00] CMD: /tp", ...]
 * }
 *
 * À chaque log → append immédiat sur disque (résistant aux crashes/restarts).
 * Au restart → reprise automatique des sessions en cours.
 * Au /surveillance off ou déco → envoi Discord + suppression fichier.
 */
public class WatchListManager {

    // ─── WatchEntry ───────────────────────────────────────────────────────────

    public static class WatchEntry {
        public final String playerName;
        public final UUID uuid;
        public final long startTime;
        public final List<String> log = new ArrayList<>();
        public Location lastLocation;
        public Map<String, Integer> itemsBefore = new HashMap<>();

        /** Constructeur pour un nouveau joueur surveillé (en ligne). */
        public WatchEntry(Player p) {
            this.playerName = p.getName();
            this.uuid       = p.getUniqueId();
            this.startTime  = System.currentTimeMillis();
            this.lastLocation = p.getLocation().clone();
            snapshotInventory(p);
        }

        /** Constructeur pour une session restaurée depuis le fichier JSON. */
        public WatchEntry(String playerName, UUID uuid, long startTime,
                          Map<String, Integer> itemsBefore, List<String> log) {
            this.playerName  = playerName;
            this.uuid        = uuid;
            this.startTime   = startTime;
            this.itemsBefore = itemsBefore;
            this.log.addAll(log);
        }

        private void snapshotInventory(Player p) {
            for (ItemStack is : p.getInventory().getStorageContents()) {
                if (is != null) itemsBefore.merge(is.getType().name(), is.getAmount(), Integer::sum);
            }
        }

        public void addLog(String entry) {
            String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
            log.add("[" + ts + "] " + entry);
        }
    }

    // ─── Manager ──────────────────────────────────────────────────────────────

    private final AdminMode plugin;
    private final File activeDir;
    // ConcurrentHashMap : watched est lu/modifié depuis le main thread ET
    // depuis des tâches Bukkit async (appendLogLine, persistFull).
    private final Map<UUID, WatchEntry> watched = new ConcurrentHashMap<>();
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public WatchListManager(AdminMode plugin) {
        this.plugin    = plugin;
        this.activeDir = new File(plugin.getDataFolder(), "surveillance/active");
        activeDir.mkdirs();
        loadPersistedSessions();
        scheduleDailySummary();
    }

    // ─── API publique ─────────────────────────────────────────────────────────

    public boolean isWatched(UUID uuid)         { return watched.containsKey(uuid); }
    public WatchEntry getEntry(UUID uuid)        { return watched.get(uuid); }
    public Map<UUID, WatchEntry> getAll()        { return Collections.unmodifiableMap(watched); }

    /** Démarre une surveillance. Retourne false si déjà surveillé. */
    public boolean watch(Player p) {
        if (watched.containsKey(p.getUniqueId())) return false;
        WatchEntry entry = new WatchEntry(p);
        watched.put(p.getUniqueId(), entry);
        persistFull(entry);         // écriture initiale sur disque
        return true;
    }

    /** Arrête la surveillance, retire l'entrée ET supprime le fichier disque. */
    public WatchEntry unwatch(UUID uuid) {
        WatchEntry entry = watched.remove(uuid);
        if (entry != null) deleteFile(uuid);
        return entry;
    }

    /** Flush de toutes les sessions actives (appelé à onDisable). */
    public void flushAll() {
        for (WatchEntry e : watched.values()) persistFull(e);
    }

    // ─── Snapshot inventaire Discord ─────────────────────────────────────────

    /**
     * Envoie un snapshot textuel de l'inventaire du joueur dans le webhook Discord.
     * Appelé par InvSee ou AdminPanel.
     */
    public void sendInventorySnapshot(Player target, String requestedBy) {
        String webhook = plugin.getConfig().getString("surveillance.discord-webhook",
                plugin.getConfig().getString("discord.webhook-url", ""));
        if (webhook.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("**Inventaire de ").append(target.getName()).append("**\n");
        sb.append("*Demandé par : ").append(requestedBy).append("*\n\n");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack is : target.getInventory().getStorageContents()) {
            if (is != null) counts.merge(is.getType().name().toLowerCase(), is.getAmount(), Integer::sum);
        }
        if (counts.isEmpty()) {
            sb.append("*(inventaire vide)*");
        } else {
            counts.forEach((item, qty) -> sb.append("• ").append(item).append(" ×").append(qty).append("\n"));
        }
        // Armure
        sb.append("\n**Armure :** ");
        boolean anyArmor = false;
        for (ItemStack armor : target.getInventory().getArmorContents()) {
            if (armor != null) { sb.append(armor.getType().name().toLowerCase()).append(" "); anyArmor = true; }
        }
        if (!anyArmor) sb.append("*(aucune)*");

        String desc = sb.toString();
        // Tronquer à 2048 chars (limite Discord embed description)
        if (desc.length() > 2048) desc = desc.substring(0, 2045) + "…";

        final String finalDesc = desc;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                DiscordNotifier.sendEmbed(plugin, webhook,
                        "🎒 Snapshot inventaire — " + target.getName(), finalDesc, 0x5566FF));
    }

    // ─── Résumé quotidien à 00h00 ────────────────────────────────────────────

    /**
     * Programme un envoi Discord quotidien à minuit pour toutes les sessions
     * de surveillance actives. Chaque résumé est découpé en blocs de ≤ 1500 chars.
     */
    private void scheduleDailySummary() {
        String webhook = plugin.getConfig().getString("surveillance.discord-webhook",
                plugin.getConfig().getString("discord.webhook-url", ""));
        if (webhook.isEmpty()) return; // pas de webhook configuré

        // Calculer le délai jusqu'au prochain 00h00
        ZonedDateTime now       = ZonedDateTime.now();
        ZonedDateTime midnight  = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone());
        long delaySeconds       = Duration.between(now, midnight).getSeconds();
        long delayTicks         = delaySeconds * 20L;
        long periodTicks        = 24L * 3600L * 20L; // 24h en ticks

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (watched.isEmpty()) return;
            String wh = plugin.getConfig().getString("surveillance.discord-webhook",
                    plugin.getConfig().getString("discord.webhook-url", ""));
            if (wh.isEmpty()) return;

            for (WatchEntry e : new ArrayList<>(watched.values())) {
                sendDailySummaryFor(e, wh);
            }
        }, delayTicks, periodTicks);
    }

    private void sendDailySummaryFor(WatchEntry e, String webhook) {
        List<String> lines = new ArrayList<>(e.log);
        if (lines.isEmpty()) return;

        String header = "📋 Résumé quotidien — **" + e.playerName + "**\n"
                + "*Session démarrée le " + DATE_FMT.format(new Date(e.startTime)) + "*\n\n";

        // Découper le log en blocs de ≤ 1500 caractères
        List<String> chunks = splitIntoChunks(lines, 1500 - header.length());

        for (int i = 0; i < chunks.size(); i++) {
            String title = "📋 Surveillance " + e.playerName + (chunks.size() > 1 ? " (" + (i+1) + "/" + chunks.size() + ")" : "");
            String desc  = (i == 0 ? header : "") + chunks.get(i);
            final String d = desc;
            final String t = title;
            DiscordNotifier.sendEmbed(plugin, webhook, t, d, 0xFF6600);
            // Petit délai pour ne pas dépasser le rate limit Discord
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
    }

    private List<String> splitIntoChunks(List<String> lines, int maxChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder sb    = new StringBuilder();
        for (String line : lines) {
            String toAdd = "• " + line + "\n";
            if (sb.length() + toAdd.length() > maxChars && sb.length() > 0) {
                chunks.add(sb.toString());
                sb = new StringBuilder();
            }
            sb.append(toAdd);
        }
        if (sb.length() > 0) chunks.add(sb.toString());
        return chunks;
    }

    // ─── Log (append disque immédiat) ─────────────────────────────────────────

    public void logCommand(UUID uuid, String cmd) {
        WatchEntry e = watched.get(uuid);
        if (e == null) return;
        e.addLog("CMD: " + cmd);
        appendLogLine(uuid, e.log.get(e.log.size() - 1));
    }

    public void logChat(UUID uuid, String msg) {
        WatchEntry e = watched.get(uuid);
        if (e == null) return;
        e.addLog("CHAT: " + msg);
        appendLogLine(uuid, e.log.get(e.log.size() - 1));
    }

    public void logMovement(UUID uuid, String detail) {
        WatchEntry e = watched.get(uuid);
        if (e == null) return;
        e.addLog("MOVE: " + detail);
        appendLogLine(uuid, e.log.get(e.log.size() - 1));
    }

    // ─── Persistance ──────────────────────────────────────────────────────────

    /** Écrit/réécrit le fichier JSON complet pour cette entrée. */
    public void persistFull(WatchEntry e) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Files.writeString(fileFor(e.uuid).toPath(), toJson(e), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                plugin.getLogger().warning("[WatchList] Erreur écriture : " + ex.getMessage());
            }
        });
    }

    /**
     * Append une seule ligne de log dans le fichier JSON.
     * Stratégie : on insère la ligne avant le "]" final du tableau "log".
     * C'est rapide (raf seek) et évite de réécrire tout le fichier à chaque action.
     */
    private void appendLogLine(UUID uuid, String line) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            File f = fileFor(uuid);
            if (!f.exists()) { WatchEntry e = watched.get(uuid); if (e != null) persistFull(e); return; }
            try {
                // Lire, insérer avant la dernière ligne "]" du log, réécrire
                // On fait une réécriture complète async — simple et robuste
                WatchEntry e = watched.get(uuid);
                if (e != null) Files.writeString(f.toPath(), toJson(e), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                plugin.getLogger().warning("[WatchList] Erreur append : " + ex.getMessage());
            }
        });
    }

    /** Supprime le fichier de session active. */
    private void deleteFile(UUID uuid) {
        File f = fileFor(uuid);
        if (f.exists()) f.delete();
    }

    private File fileFor(UUID uuid) {
        return new File(activeDir, uuid.toString() + ".json");
    }

    // ─── Chargement au démarrage ──────────────────────────────────────────────

    /**
     * Recharge toutes les sessions actives depuis surveillance/active/*.json.
     * Les joueurs hors ligne sont gardés en mémoire — leur session reprend
     * automatiquement à leur reconnexion (WatchListListener.onJoin).
     */
    private void loadPersistedSessions() {
        File[] files = activeDir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) return;

        int loaded = 0;
        for (File f : files) {
            try {
                String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                WatchEntry entry = fromJson(content);
                if (entry != null) {
                    watched.put(entry.uuid, entry);
                    loaded++;
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("[WatchList] Impossible de lire " + f.getName() + " : " + ex.getMessage());
            }
        }
        if (loaded > 0) {
            plugin.getLogger().info("[WatchList] " + loaded + " session(s) de surveillance restaurée(s) après restart.");
        }
    }

    // ─── JSON ────────────────────────────────────────────────────────────────

    private String toJson(WatchEntry e) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"player\": \"").append(esc(e.playerName)).append("\",\n");
        sb.append("  \"uuid\": \"").append(e.uuid).append("\",\n");
        sb.append("  \"start\": ").append(e.startTime).append(",\n");
        sb.append("  \"start_date\": \"").append(DATE_FMT.format(new Date(e.startTime))).append("\",\n");
        sb.append("  \"items_before\": {");
        boolean first = true;
        for (Map.Entry<String, Integer> kv : e.itemsBefore.entrySet()) {
            if (!first) sb.append(", ");
            sb.append("\"").append(kv.getKey()).append("\": ").append(kv.getValue());
            first = false;
        }
        sb.append("},\n");
        sb.append("  \"log\": [\n");
        for (int i = 0; i < e.log.size(); i++) {
            sb.append("    \"").append(esc(e.log.get(i))).append("\"");
            if (i < e.log.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n}");
        return sb.toString();
    }

    /** Parser JSON minimal (pas de dépendance externe). */
    private WatchEntry fromJson(String json) {
        try {
            String playerName = extractString(json, "player");
            String uuidStr    = extractString(json, "uuid");
            long start        = extractLong(json, "start");
            Map<String, Integer> itemsBefore = extractItemsMap(json);
            List<String> log  = extractLogList(json);

            return new WatchEntry(playerName, UUID.fromString(uuidStr), start, itemsBefore, log);
        } catch (Exception e) {
            plugin.getLogger().warning("[WatchList] JSON invalide : " + e.getMessage());
            return null;
        }
    }

    private String extractString(String json, String key) {
        String search = "\"" + key + "\": \"";
        int start = json.indexOf(search) + search.length();
        int end   = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private long extractLong(String json, String key) {
        String search = "\"" + key + "\": ";
        int start = json.indexOf(search) + search.length();
        int end   = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("\n", start);
        return Long.parseLong(json.substring(start, end).trim());
    }

    private Map<String, Integer> extractItemsMap(String json) {
        Map<String, Integer> map = new HashMap<>();
        int start = json.indexOf("\"items_before\": {");
        if (start == -1) return map;
        start = json.indexOf("{", start) + 1;
        int end = json.indexOf("}", start);
        String block = json.substring(start, end).trim();
        if (block.isEmpty()) return map;
        for (String pair : block.split(",")) {
            String[] kv = pair.trim().split(":");
            if (kv.length == 2) {
                String k = kv[0].trim().replace("\"", "");
                int v    = Integer.parseInt(kv[1].trim());
                map.put(k, v);
            }
        }
        return map;
    }

    private List<String> extractLogList(String json) {
        List<String> list = new ArrayList<>();
        int start = json.indexOf("\"log\": [");
        if (start == -1) return list;
        start = json.indexOf("[", start) + 1;
        int end = json.lastIndexOf("]");
        String block = json.substring(start, end).trim();
        if (block.isEmpty()) return list;
        // Chaque ligne : "  \"...\""
        int pos = 0;
        while (pos < block.length()) {
            int q1 = block.indexOf("\"", pos);
            if (q1 == -1) break;
            int q2 = q1 + 1;
            // Chercher la fermeture en gérant les \" échappés
            while (q2 < block.length()) {
                if (block.charAt(q2) == '"' && block.charAt(q2 - 1) != '\\') break;
                q2++;
            }
            String entry = block.substring(q1 + 1, q2).replace("\\\"", "\"").replace("\\n", "\n");
            if (!entry.isEmpty()) list.add(entry);
            pos = q2 + 1;
        }
        return list;
    }

    private String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}

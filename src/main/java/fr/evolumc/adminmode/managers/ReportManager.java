package fr.evolumc.adminmode.managers;

import com.google.gson.*;
import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.utils.DiscordNotifier;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Système de reports joueurs — persistant.
 * Fichier : plugins/AdminMode/reports.json
 *
 * Chaque report possède un ID unique, un statut (OPEN/CLOSED) et
 * une référence à l'admin qui l'a fermé.
 */
public class ReportManager {

    // ─── Report ───────────────────────────────────────────────────────────────

    public enum Status { OPEN, CLOSED }

    public static class Report {
        private static final DateTimeFormatter FMT =
                DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

        public final int    id;
        public final String reporter;
        public final String target;
        public final String reason;
        public final long   timestamp;
        public volatile Status status   = Status.OPEN;
        public volatile String closedBy = null;

        public Report(int id, String reporter, String target, String reason, long timestamp) {
            this.id        = id;
            this.reporter  = reporter;
            this.target    = target;
            this.reason    = reason;
            this.timestamp = timestamp;
        }

        public String formattedTime() {
            return FMT.format(Instant.ofEpochMilli(timestamp));
        }
    }

    // ─── Constantes ───────────────────────────────────────────────────────────

    private static final long COOLDOWN_MS = 30_000L;
    public  static final int  PAGE_SIZE   = 5;

    // ─── État ─────────────────────────────────────────────────────────────────

    private final AdminMode plugin;
    private final Path reportsFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<Report> reports   = new ArrayList<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final AtomicInteger nextId   = new AtomicInteger(1);

    public ReportManager(AdminMode plugin) {
        this.plugin      = plugin;
        this.reportsFile = plugin.getDataFolder().toPath().resolve("reports.json");
        load();
    }

    // ─── Créer un report ─────────────────────────────────────────────────────

    public boolean report(Player reporter, Player target, String reason) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(reporter.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (now - last)) / 1000;
            reporter.sendMessage(plugin.getPrefix()
                    + "§cAttendez §f" + remaining + "s §cavant de re-signaler.");
            return false;
        }
        cooldowns.put(reporter.getUniqueId(), now);

        Report rep = new Report(nextId.getAndIncrement(),
                reporter.getName(), target.getName(), reason, now);
        reports.add(rep);
        saveAsync();

        plugin.getChatLogManager().addCommand(reporter.getUniqueId(),
                "/report " + target.getName() + " " + reason);

        // Staff chat in-game
        String staffMsg = plugin.getPrefix()
                + "§c⚠ REPORT §8[§f#" + rep.id + "§8] §f" + reporter.getName()
                + " §8→ §c" + target.getName()
                + " §8| §7" + reason;
        plugin.getStaffChatManager().broadcastToStaff(staffMsg);

        reporter.sendMessage(plugin.getPrefix()
                + "§aSignalement §f#" + rep.id + " §aenvoyé. Merci !");

        // Discord
        String webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        if (!webhookUrl.isEmpty()) {
            String desc = "**Reporter :** " + reporter.getName()
                    + "\n**Signalé :** " + target.getName()
                    + "\n**Raison :** " + reason
                    + "\n**ID :** #" + rep.id;
            DiscordNotifier.sendEmbed(plugin, webhookUrl, "🚩 Nouveau signalement", desc, 0xFF6600);
        }
        return true;
    }

    // ─── Gestion staff ────────────────────────────────────────────────────────

    public Optional<Report> getById(int id) {
        return reports.stream().filter(r -> r.id == id).findFirst();
    }

    /**
     * Ferme un report ouvert. Retourne false si introuvable ou déjà fermé.
     */
    public boolean close(int id, String adminName) {
        Optional<Report> opt = getById(id);
        if (opt.isEmpty() || opt.get().status == Status.CLOSED) return false;
        opt.get().status   = Status.CLOSED;
        opt.get().closedBy = adminName;
        saveAsync();
        return true;
    }

    public List<Report> getOpen()  {
        return reports.stream().filter(r -> r.status == Status.OPEN).toList();
    }
    public List<Report> getAll()   { return Collections.unmodifiableList(reports); }
    public int          getOpenCount() {
        return (int) reports.stream().filter(r -> r.status == Status.OPEN).count();
    }

    /** Retourne la page (1-indexed) des reports ouverts. */
    public List<Report> getOpenPage(int page) {
        List<Report> open = getOpen();
        int from = (page - 1) * PAGE_SIZE;
        if (from >= open.size() || from < 0) return Collections.emptyList();
        return open.subList(from, Math.min(from + PAGE_SIZE, open.size()));
    }

    public int getOpenPageCount() {
        return Math.max(1, (int) Math.ceil(getOpenCount() / (double) PAGE_SIZE));
    }

    // ─── Persistance JSON ────────────────────────────────────────────────────

    private void load() {
        if (!Files.exists(reportsFile)) return;
        try {
            String content = Files.readString(reportsFile, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            nextId.set(root.has("nextId") ? root.get("nextId").getAsInt() : 1);
            JsonArray arr = root.getAsJsonArray("reports");
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                Report r = new Report(
                        o.get("id").getAsInt(),
                        o.get("reporter").getAsString(),
                        o.get("target").getAsString(),
                        o.get("reason").getAsString(),
                        o.get("timestamp").getAsLong()
                );
                r.status   = Status.valueOf(o.get("status").getAsString());
                r.closedBy = o.has("closedBy") ? o.get("closedBy").getAsString() : null;
                reports.add(r);
                if (r.id >= nextId.get()) nextId.set(r.id + 1);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Reports] Erreur chargement : " + e.getMessage());
        }
    }

    private void saveAsync() {
        List<Report> snapshot = new ArrayList<>(reports);
        int currentNextId    = nextId.get();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            JsonArray arr = new JsonArray();
            for (Report r : snapshot) {
                JsonObject o = new JsonObject();
                o.addProperty("id",        r.id);
                o.addProperty("reporter",  r.reporter);
                o.addProperty("target",    r.target);
                o.addProperty("reason",    r.reason);
                o.addProperty("timestamp", r.timestamp);
                o.addProperty("status",    r.status.name());
                if (r.closedBy != null) o.addProperty("closedBy", r.closedBy);
                arr.add(o);
            }
            JsonObject root = new JsonObject();
            root.addProperty("nextId", currentNextId);
            root.add("reports", arr);
            try {
                Files.writeString(reportsFile, gson.toJson(root), StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().warning("[Reports] Erreur sauvegarde : " + e.getMessage());
            }
        });
    }
}

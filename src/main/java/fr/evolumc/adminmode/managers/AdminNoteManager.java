package fr.evolumc.adminmode.managers;

import com.google.gson.*;
import fr.evolumc.adminmode.AdminMode;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Option 14 — Notes admin sur les joueurs.
 * Stockées dans plugins/AdminMode/notes/<uuid>.json
 */
public class AdminNoteManager {

    public record Note(String author, String text, long timestamp) {
        private static final DateTimeFormatter FMT =
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
        public String formattedTime() { return FMT.format(Instant.ofEpochMilli(timestamp)); }
    }

    private final AdminMode plugin;
    private final Path notesDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    // Cache en mémoire : uuid cible → liste notes
    private final Map<UUID, List<Note>> cache = new HashMap<>();

    public AdminNoteManager(AdminMode plugin) {
        this.plugin = plugin;
        this.notesDir = plugin.getDataFolder().toPath().resolve("notes");
        try { Files.createDirectories(notesDir); } catch (IOException ignored) {}
    }

    public void addNote(Player admin, UUID targetUuid, String text) {
        List<Note> notes = load(targetUuid);
        notes.add(new Note(admin.getName(), text, System.currentTimeMillis()));
        save(targetUuid, notes);
        cache.put(targetUuid, notes);
    }

    public List<Note> getNotes(UUID targetUuid) {
        if (!cache.containsKey(targetUuid)) cache.put(targetUuid, load(targetUuid));
        return Collections.unmodifiableList(cache.get(targetUuid));
    }

    public boolean deleteNote(UUID targetUuid, int index) {
        List<Note> notes = new ArrayList<>(load(targetUuid));
        if (index < 0 || index >= notes.size()) return false;
        notes.remove(index);
        save(targetUuid, notes);
        cache.put(targetUuid, notes);
        return true;
    }

    // ─── Persistance JSON ──────────────────────────────────────────────────

    private List<Note> load(UUID uuid) {
        Path file = notesDir.resolve(uuid + ".json");
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
            List<Note> list = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                list.add(new Note(
                        o.get("author").getAsString(),
                        o.get("text").getAsString(),
                        o.get("timestamp").getAsLong()
                ));
            }
            return list;
        } catch (Exception e) {
            plugin.getLogger().warning("[AdminNotes] Erreur lecture " + uuid + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void save(UUID uuid, List<Note> notes) {
        Path file = notesDir.resolve(uuid + ".json");
        JsonArray arr = new JsonArray();
        for (Note n : notes) {
            JsonObject o = new JsonObject();
            o.addProperty("author", n.author());
            o.addProperty("text", n.text());
            o.addProperty("timestamp", n.timestamp());
            arr.add(o);
        }
        try {
            Files.writeString(file, gson.toJson(arr), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("[AdminNotes] Erreur écriture " + uuid + ": " + e.getMessage());
        }
    }
}

package fr.evolumc.adminmode.utils;

import fr.evolumc.adminmode.AdminMode;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilitaire Discord — envoie des embeds vers un webhook.
 * Toutes les actions AdminMode y sont loggées si le webhook est configuré.
 */
public class DiscordNotifier {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private DiscordNotifier() {}

    /** Point d'entrée principal : embed avec timestamp automatique. */
    public static void sendEmbed(AdminMode plugin, String webhookUrl, String title, String description, int color) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        String ts = TS_FMT.format(Instant.now());
        String fullDesc = description + "\n\n*" + ts + "*";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> post(plugin, webhookUrl, title, fullDesc, color));
    }

    /** Raccourci pour les actions AdminMode (lit le webhook depuis config). */
    public static void logAction(AdminMode plugin, String title, String description, int color) {
        String url = plugin.getConfig().getString("discord.webhook-url", "");
        sendEmbed(plugin, url, title, description, color);
    }

    // ─── HTTP ─────────────────────────────────────────────────────────────────

    private static void post(AdminMode plugin, String webhookUrl, String title, String description, int color) {
        try {
            String json = buildJson(title, description, color);
            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                plugin.getLogger().warning("[DiscordNotifier] HTTP " + code + " pour : " + title);
            }
            conn.disconnect();
        } catch (Exception e) {
            plugin.getLogger().warning("[DiscordNotifier] Erreur : " + e.getMessage());
        }
    }

    private static String buildJson(String title, String description, int color) {
        return "{\"embeds\":[{\"title\":\"" + esc(title) + "\",\"description\":\"" + esc(description) + "\",\"color\":" + color + "}]}";
    }

    private static String esc(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                   .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /**
     * Envoie un texte long en plusieurs embeds de max {@code maxChars} caractères,
     * avec 350 ms entre chaque envoi pour respecter le rate-limit Discord.
     * Utilisé par DailyReportManager et tout autre rapport potentiellement long.
     */
    public static void sendEmbedChunked(AdminMode plugin, String webhookUrl,
                                        String baseTitle, String fullText,
                                        int color, int maxChars) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        // Découpe en parties de maxChars
        List<String> parts = new ArrayList<>();
        int len = fullText.length();
        for (int start = 0; start < len; start += maxChars) {
            parts.add(fullText.substring(start, Math.min(start + maxChars, len)));
        }
        if (parts.isEmpty()) return;

        int total = parts.size();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            for (int i = 0; i < total; i++) {
                String title = total == 1 ? baseTitle : baseTitle + " [" + (i + 1) + "/" + total + "]";
                String ts = TS_FMT.format(Instant.now());
                String desc = parts.get(i) + "\n\n*" + ts + "*";
                post(plugin, webhookUrl, title, desc, color);
                if (i < total - 1) {
                    try { Thread.sleep(350); } catch (InterruptedException ignored) {}
                }
            }
        });
    }
}

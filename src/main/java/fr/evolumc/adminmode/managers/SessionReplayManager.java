package fr.evolumc.adminmode.managers;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.utils.DiscordNotifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Génère le rapport Discord d'une session de surveillance terminée.
 * Si le contenu dépasse 1500 caractères, il est découpé en plusieurs embeds
 * numérotés envoyés séquentiellement (délai 300ms entre chaque pour éviter le rate-limit).
 */
public class SessionReplayManager {

    // 1500 chars max visibles — on retire 30 pour laisser la place au timestamp
    // que sendEmbed ajoute automatiquement ("\n\n*dd/MM/yyyy HH:mm:ss*").
    private static final int MAX_PART_LENGTH = 1470;

    private final AdminMode plugin;

    public SessionReplayManager(AdminMode plugin) {
        this.plugin = plugin;
    }

    /** Joueur en ligne — diff inventaire disponible. */
    public void generateAndSend(WatchListManager.WatchEntry entry, Player player) {
        Map<String, Integer> itemsAfter = new HashMap<>();
        for (ItemStack is : player.getInventory().getStorageContents()) {
            if (is != null) itemsAfter.merge(is.getType().name(), is.getAmount(), Integer::sum);
        }
        sendReport(entry, itemsAfter, true);
    }

    /** Joueur hors ligne — diff inventaire impossible. */
    public void generateAndSendOffline(WatchListManager.WatchEntry entry) {
        sendReport(entry, null, false);
    }

    // ─── Core ────────────────────────────────────────────────────────────────

    private void sendReport(WatchListManager.WatchEntry entry,
                            Map<String, Integer> itemsAfter,
                            boolean canDiff) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<String> gained = new ArrayList<>();
                List<String> lost   = new ArrayList<>();

                if (canDiff && itemsAfter != null) {
                    Set<String> allItems = new HashSet<>();
                    allItems.addAll(entry.itemsBefore.keySet());
                    allItems.addAll(itemsAfter.keySet());
                    for (String item : allItems) {
                        int before = entry.itemsBefore.getOrDefault(item, 0);
                        int after  = itemsAfter.getOrDefault(item, 0);
                        int diff   = after - before;
                        if (diff > 0) gained.add("+" + diff + " " + item);
                        else if (diff < 0) lost.add(diff + " " + item);
                    }
                }

                String webhook = plugin.getConfig().getString("surveillance.discord-webhook", "");
                if (webhook.isEmpty()) return;

                List<String> parts = buildParts(entry, gained, lost, canDiff);
                int total = parts.size();
                String baseTitle = "📋 Session Replay — " + entry.playerName;

                for (int i = 0; i < total; i++) {
                    String title = total == 1
                            ? baseTitle
                            : baseTitle + "  [" + (i + 1) + "/" + total + "]";
                    DiscordNotifier.sendEmbed(plugin, webhook, title, parts.get(i), 0x9B59B6);
                    // Pause 350ms entre les envois pour respecter le rate-limit Discord
                    if (i < total - 1) {
                        try { Thread.sleep(350); } catch (InterruptedException ignored) {}
                    }
                }

            } catch (Exception e) {
                plugin.getLogger().warning("[SessionReplay] Erreur : " + e.getMessage());
            }
        });
    }

    // ─── Construction des parties ─────────────────────────────────────────────

    /**
     * Construit une liste de blocs de texte de max 1500 caractères chacun.
     *
     * Structure :
     *  - Partie 1 : en-tête (date, durée, nb actions) + autant de lignes de log que possible
     *  - Parties suivantes : suite du log
     *  - Dernière partie : diff inventaire (s'il y en a un)
     */
    private List<String> buildParts(WatchListManager.WatchEntry entry,
                                    List<String> gained, List<String> lost,
                                    boolean canDiff) {
        List<String> parts = new ArrayList<>();

        // ── En-tête (toujours sur la partie 1) ──────────────────────────────
        String startStr = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date(entry.startTime));
        String header = "**Début :** " + startStr + "\n"
                      + "**Durée :** " + formatDuration(System.currentTimeMillis() - entry.startTime) + "\n"
                      + "**Actions totales :** " + entry.log.size() + "\n\n"
                      + "**Journal :**\n";

        // ── Lignes de log ────────────────────────────────────────────────────
        // Chaque ligne est formatée : "`[HH:mm:ss] CMD: ...`\n"
        List<String> logLines = new ArrayList<>();
        for (String line : entry.log) {
            logLines.add("`" + line + "`\n");
        }

        // ── Bloc inventaire (ajouté à la fin) ────────────────────────────────
        StringBuilder invBlock = new StringBuilder();
        if (canDiff) {
            if (!gained.isEmpty()) invBlock.append("📦 **Items gagnés :** ").append(String.join(", ", gained)).append("\n");
            if (!lost.isEmpty())   invBlock.append("🗑 **Items perdus :** ").append(String.join(", ", lost)).append("\n");
            if (gained.isEmpty() && lost.isEmpty()) invBlock.append("✅ *Aucune variation d'inventaire.*\n");
        } else {
            invBlock.append("⚠️ *Diff inventaire indisponible (joueur hors ligne).*\n");
        }
        String invStr = invBlock.toString();

        // ── Remplissage des parties ───────────────────────────────────────────
        StringBuilder current = new StringBuilder(header);
        int logIdx = 0;

        while (logIdx < logLines.size()) {
            String line = logLines.get(logIdx);

            // Est-ce que la ligne tient dans la partie courante ?
            // On réserve de la place pour "... (N restants)" si nécessaire
            boolean isLastLine = (logIdx == logLines.size() - 1);
            String overflow = isLastLine ? "" : "*(... suite dans la partie suivante)*\n";
            int available = MAX_PART_LENGTH - current.length() - overflow.length();

            if (line.length() <= available) {
                current.append(line);
                logIdx++;
            } else {
                // La ligne ne rentre pas → clore la partie courante
                if (current.length() > header.length()) {
                    // Il y a déjà du contenu, on ferme
                    current.append("*(... suite dans la partie suivante)*\n");
                    parts.add(current.toString());
                    current = new StringBuilder("**Suite du journal :**\n");
                } else {
                    // La ligne elle-même est trop longue → la tronquer
                    // Guard : available peut être négatif si current + overflow > MAX_PART_LENGTH
                    int cutAt = Math.max(0, available - 5);
                    String truncated = (cutAt > 0 ? line.substring(0, cutAt) : "") + "…`\n";
                    current.append(truncated);
                    logIdx++;
                }
            }
        }

        // ── Dernière partie : reste du log + inventaire ───────────────────────
        // Ajouter le bloc inventaire à la fin de la dernière partie (si ça rentre)
        if (current.length() + invStr.length() + 1 <= MAX_PART_LENGTH) {
            // +1 pour le "\n" de séparation
            if (current.length() > 0 && !current.toString().endsWith("\n")) current.append("\n");
            current.append(invStr);
            parts.add(current.toString());
        } else {
            // Inventaire dans une partie séparée (pas de double \n : invStr commence sans \n)
            parts.add(current.toString());
            parts.add("**Diff inventaire :**\n" + invStr);
        }

        return parts;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String formatDuration(long ms) {
        long s = ms / 1000;
        long h = s / 3600; long m = (s % 3600) / 60; long sec = s % 60;
        if (h > 0) return h + "h " + m + "m " + sec + "s";
        return m + "m " + sec + "s";
    }
}

package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.managers.ReportManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * /report <joueur> <raison>         → signaler un joueur
 * /report list [page]               → lister les reports ouverts  (adminmode.report.manage)
 * /report close <id>                → fermer un report             (adminmode.report.manage)
 * /report info <id>                 → détails d'un report          (adminmode.report.manage)
 */
public class ReportCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public ReportCommand(AdminMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            sender.sendMessage(plugin.getPrefix() + "§7Usage : §f/report <joueur> <raison> §8| §f/report list §8| §f/report close <id>");
            return true;
        }

        String sub = args[0].toLowerCase();

        // ── Sous-commandes staff ──────────────────────────────────────────────
        if (sub.equals("list") || sub.equals("close") || sub.equals("info") || sub.equals("export")) {
            if (!sender.hasPermission("adminmode.report.manage")) {
                sender.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
                return true;
            }

            ReportManager mgr = plugin.getReportManager();

            switch (sub) {
                case "list" -> {
                    int page = 1;
                    if (args.length >= 2) {
                        try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
                    }
                    int maxPage = mgr.getOpenPageCount();
                    page = Math.max(1, Math.min(page, maxPage));
                    List<ReportManager.Report> pageReports = mgr.getOpenPage(page);

                    sender.sendMessage(AdminMode.colorize(
                            "&8[&6Reports&8] &7Reports ouverts §f(" + mgr.getOpenCount() + ")"
                            + " — Page §f" + page + "§7/§f" + maxPage));
                    if (pageReports.isEmpty()) {
                        sender.sendMessage("  §7Aucun report ouvert.");
                    } else {
                        for (ReportManager.Report r : pageReports) {
                            sender.sendMessage(AdminMode.colorize(
                                    "  &8#&f" + r.id
                                    + " &7" + r.formattedTime()
                                    + " &f" + r.reporter
                                    + " &8→ &c" + r.target
                                    + " &7| " + r.reason
                                    + "  &8[&a/report close " + r.id + "&8]"));
                        }
                    }
                }

                case "close" -> {
                    if (args.length < 2) {
                        sender.sendMessage(plugin.getPrefix() + "§7Usage : §f/report close <id>");
                        return true;
                    }
                    int id;
                    try { id = Integer.parseInt(args[1]); }
                    catch (NumberFormatException e) {
                        sender.sendMessage(plugin.getPrefix() + "§cID invalide.");
                        return true;
                    }
                    String adminName = sender instanceof Player p ? p.getName() : "Console";
                    if (mgr.close(id, adminName)) {
                        sender.sendMessage(plugin.getPrefix() + "§aReport §f#" + id + " §afermé.");
                        // Notifier le staff
                        plugin.getStaffChatManager().broadcastToStaff(
                                plugin.getPrefix() + "§7Report §f#" + id + " §7fermé par §f" + adminName + "§7.");
                    } else {
                        mgr.getById(id).ifPresentOrElse(
                                r -> sender.sendMessage(plugin.getPrefix() + "§eReport §f#" + id + " §eest déjà fermé."),
                                () -> sender.sendMessage(plugin.getPrefix() + "§cReport §f#" + id + " §cintrouvable.")
                        );
                    }
                }

                case "export" -> {
                    // Export CSV de tous les reports (ouverts + fermés)
                    List<ReportManager.Report> all = new ArrayList<>(mgr.getOpen());
                    // On ajoute aussi les fermés si ReportManager les expose ; sinon juste open
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            File dir = plugin.getDataFolder();
                            dir.mkdirs();
                            Path csv = dir.toPath().resolve("reports-export.csv");
                            StringBuilder sb = new StringBuilder("id,reporter,target,reason,timestamp,status,closedBy\n");
                            for (ReportManager.Report r : all) {
                                sb.append(r.id).append(",")
                                  .append(esc(r.reporter)).append(",")
                                  .append(esc(r.target)).append(",")
                                  .append(esc(r.reason)).append(",")
                                  .append(r.formattedTime()).append(",")
                                  .append(r.status).append(",")
                                  .append(r.closedBy != null ? esc(r.closedBy) : "").append("\n");
                            }
                            Files.writeString(csv, sb.toString(), StandardCharsets.UTF_8);
                            Bukkit.getScheduler().runTask(plugin, () ->
                                sender.sendMessage(plugin.getPrefix() + "§aExport CSV généré : §fplugins/AdminMode/reports-export.csv §7(" + all.size() + " reports)"));
                        } catch (IOException ex) {
                            Bukkit.getScheduler().runTask(plugin, () ->
                                sender.sendMessage(plugin.getPrefix() + "§cErreur lors de l'export : " + ex.getMessage()));
                        }
                    });
                }

                case "info" -> {
                    if (args.length < 2) {
                        sender.sendMessage(plugin.getPrefix() + "§7Usage : §f/report info <id>");
                        return true;
                    }
                    int id;
                    try { id = Integer.parseInt(args[1]); }
                    catch (NumberFormatException e) {
                        sender.sendMessage(plugin.getPrefix() + "§cID invalide.");
                        return true;
                    }
                    mgr.getById(id).ifPresentOrElse(r -> {
                        sender.sendMessage(AdminMode.colorize("&8[&6Report &f#" + r.id + "&8]"));
                        sender.sendMessage(AdminMode.colorize("  &7Reporter : &f" + r.reporter));
                        sender.sendMessage(AdminMode.colorize("  &7Signalé  : &c" + r.target));
                        sender.sendMessage(AdminMode.colorize("  &7Raison   : &f" + r.reason));
                        sender.sendMessage(AdminMode.colorize("  &7Date     : &f" + r.formattedTime()));
                        sender.sendMessage(AdminMode.colorize("  &7Statut   : "
                                + (r.status == ReportManager.Status.OPEN ? "&aOUVERT" : "&cFERMÉ par &f" + r.closedBy)));
                    }, () -> sender.sendMessage(plugin.getPrefix() + "§cReport §f#" + id + " §cintrouvable."));
                }
            }
            return true;
        }

        // ── /report <joueur> <raison> ─────────────────────────────────────────
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cJoueurs seulement.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getPrefix() + "§7Usage : §f/report <joueur> <raison>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(plugin.getPrefix() + "§cJoueur introuvable (doit être en ligne).");
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(plugin.getPrefix() + "§cVous ne pouvez pas vous signaler vous-même.");
            return true;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        plugin.getReportManager().report(player, target, reason);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(List.of("list", "close", "info"));
            Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(opts::add);
            String p = args[0].toLowerCase();
            return opts.stream().filter(n -> n.toLowerCase().startsWith(p)).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("close") || args[0].equalsIgnoreCase("info"))) {
            return plugin.getReportManager().getOpen().stream()
                    .map(r -> String.valueOf(r.id))
                    .filter(id -> id.startsWith(args[1]))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}

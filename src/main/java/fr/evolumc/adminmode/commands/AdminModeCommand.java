package fr.evolumc.adminmode.commands;

import fr.evolumc.adminmode.AdminMode;
import fr.evolumc.adminmode.managers.ChatLogManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class AdminModeCommand implements CommandExecutor, TabCompleter {

    private final AdminMode plugin;

    public AdminModeCommand(AdminMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur.");
            return true;
        }

        if (!player.hasPermission("adminmode.use")) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
            return true;
        }

        // ── Pas d'argument : toggle admin mode ──────────────────────────────
        if (args.length == 0) {
            var manager = plugin.getAdminModeManager();
            if (manager.isInAdminMode(player)) manager.disable(player);
            else manager.enable(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            // ── /adminmode vanishno (bouton [Non] du prompt vanish) ──────────
            case "vanishno" -> player.sendMessage(plugin.getPrefix() + "§7Vous restez visible.");

            // ── /adminmode reload ────────────────────────────────────────────
            case "reload" -> {
                if (!player.hasPermission("adminmode.use")) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
                    return true;
                }
                plugin.reloadConfig();
                player.sendMessage(plugin.getPrefix() + plugin.getMessage("reload"));
            }

            // ── /adminmode list ──────────────────────────────────────────────
            case "list" -> {
                Set<UUID> active = plugin.getAdminModeManager().getActiveAdmins();
                if (active.isEmpty()) {
                    player.sendMessage(plugin.getPrefix() + "§7Aucun admin en mode admin actuellement.");
                } else {
                    player.sendMessage(plugin.getPrefix() + "§7Admins actifs (§f" + active.size() + "§7) :");
                    for (UUID uuid : active) {
                        Player p = Bukkit.getPlayer(uuid);
                        String name = p != null ? p.getName() : uuid.toString();
                        boolean inVanish = p != null && plugin.getVanishManager().isVanished(p);
                        player.sendMessage("  §8▸ §c" + name + (inVanish ? " §7[vanish]" : ""));
                    }
                }
            }

            // ── /adminmode back ──────────────────────────────────────────────
            case "back" -> {
                if (!plugin.getAdminModeManager().isInAdminMode(player)) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessage("not-in-adminmode"));
                    return true;
                }
                plugin.getAdminModeManager().teleportBack(player);
            }

            // ── /adminmode fly [vitesse] ─────────────────────────────────────
            case "fly" -> {
                if (!plugin.getAdminModeManager().isInAdminMode(player)) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessage("not-in-adminmode"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getPrefix() + "§7Usage : §f/adminmode fly <0.1 - 1.0>");
                    return true;
                }
                try {
                    float speed = Float.parseFloat(args[1]);
                    if (speed < 0.1f || speed > 1.0f) {
                        player.sendMessage(plugin.getPrefix() + "§cVitesse invalide. Valeur entre 0.1 et 1.0.");
                        return true;
                    }
                    player.setFlySpeed(speed);
                    player.sendMessage(plugin.getPrefix() + "§7Vitesse de vol réglée à §e" + String.format("%.1f", speed) + "§7.");
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getPrefix() + "§cNombre invalide.");
                }
            }

            // ── /adminmode vanish ────────────────────────────────────────────
            case "vanish" -> {
                if (!player.hasPermission("adminmode.vanish")) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
                    return true;
                }
                var mgr = plugin.getVanishManager();
                if (mgr.isVanished(player)) mgr.unvanish(player, false);
                else mgr.vanish(player);
            }

            // ── /adminmode noclip ────────────────────────────────────────────
            case "noclip" -> {
                if (!player.hasPermission("adminmode.noclip")) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
                    return true;
                }
                plugin.getNoclipManager().toggle(player);
            }

            // ── /adminmode tpp <joueur> ──────────────────────────────────────
            case "tpp" -> {
                if (!player.hasPermission("adminmode.tpp")) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getPrefix() + "§7Usage : §f/adminmode tpp <joueur>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.getPrefix() + "§cJoueur introuvable : §f" + args[1]);
                    return true;
                }
                player.teleport(target.getLocation());
                player.sendMessage(plugin.getPrefix() + "§7Téléporté vers §c" + target.getName() + "§7 (silencieux).");
            }

            // ── /adminmode freeze <joueur> ───────────────────────────────────
            case "freeze" -> {
                if (!player.hasPermission("adminmode.freeze")) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getPrefix() + "§7Usage : §f/adminmode freeze <joueur>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.getPrefix() + "§cJoueur introuvable : §f" + args[1]);
                    return true;
                }
                if (target.equals(player)) {
                    player.sendMessage(plugin.getPrefix() + "§cVous ne pouvez pas vous geler vous-même.");
                    return true;
                }
                plugin.getFreezeManager().toggle(target, player);
            }

            // ── /adminmode spy <joueur> ──────────────────────────────────────
            case "spy" -> {
                if (!player.hasPermission("adminmode.spy")) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getPrefix() + "§7Usage : §f/adminmode spy <joueur>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.getPrefix() + "§cJoueur introuvable : §f" + args[1]);
                    return true;
                }
                openSpyInventory(player, target);
            }

            // ── /adminmode spec <joueur> ─────────────────────────────────────
            case "spec" -> {
                if (!player.hasPermission("adminmode.spec")) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
                    return true;
                }
                if (!plugin.getAdminModeManager().isInAdminMode(player)) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessage("not-in-adminmode"));
                    return true;
                }
                var specMgr = plugin.getSpectatorManager();
                if (specMgr.isWatching(player.getUniqueId())) {
                    specMgr.stopSpec(player);
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getPrefix() + "§7Usage : §f/adminmode spec <joueur>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.getPrefix() + "§cJoueur introuvable : §f" + args[1]);
                    return true;
                }
                if (target.equals(player)) {
                    player.sendMessage(plugin.getPrefix() + "§cVous ne pouvez pas vous observer vous-même.");
                    return true;
                }
                specMgr.startSpec(player, target);
            }

            // ── /adminmode chatlog <joueur> [nb] ─────────────────────────────
            case "chatlog" -> {
                if (!player.hasPermission("adminmode.chatlog")) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getPrefix() + "§7Usage : §f/adminmode chatlog <joueur> [nombre]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.getPrefix() + "§cJoueur introuvable ou hors ligne : §f" + args[1]);
                    return true;
                }
                int count = 10;
                if (args.length >= 3) {
                    try { count = Math.min(50, Math.max(1, Integer.parseInt(args[2]))); }
                    catch (NumberFormatException ignored) {}
                }
                List<ChatLogManager.ChatEntry> logs = plugin.getChatLogManager().getLogs(target.getUniqueId(), count);
                player.sendMessage("§8§m                                        §r");
                player.sendMessage("§8[§cAdminMode§8] §7Chat log de §c" + target.getName() + " §7(§f" + logs.size() + " §7messages)");
                if (logs.isEmpty()) player.sendMessage("§7Aucun message enregistré.");
                else for (ChatLogManager.ChatEntry e : logs) player.sendMessage("§8[" + e.formattedTime() + "] §f" + e.message());
                player.sendMessage("§8§m                                        §r");
            }

            // ── /adminmode stats [joueur] ────────────────────────────────────
            case "stats" -> {
                UUID targetUUID = player.getUniqueId();
                String targetName = player.getName();
                if (args.length >= 2) {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        player.sendMessage(plugin.getPrefix() + "§cJoueur introuvable : §f" + args[1]);
                        return true;
                    }
                    targetUUID = target.getUniqueId();
                    targetName = target.getName();
                }
                var statsMgr = plugin.getSessionStatsManager();
                int sessions = statsMgr.getSessionCount(targetUUID);
                long total = statsMgr.getTotalMillis(targetUUID);
                Long sessionStart = statsMgr.getSessionStart(targetUUID);
                String currentSession = sessionStart != null
                        ? statsMgr.formatDuration(System.currentTimeMillis() - sessionStart)
                        : "§7inactif";

                player.sendMessage("§8§m                                        §r");
                player.sendMessage("§8[§cAdminMode§8] §7Stats de §c" + targetName);
                player.sendMessage("§7Sessions : §f" + sessions);
                player.sendMessage("§7Temps total : §f" + statsMgr.formatDuration(total));
                player.sendMessage("§7Session actuelle : §e" + currentSession);
                player.sendMessage("§8§m                                        §r");
            }

            // ── /adminmode hud ────────────────────────────────────────────────
            case "hud" -> new fr.evolumc.adminmode.commands.HudGuiCommand(plugin).openGui(player);

            // ── /adminmode tpb ────────────────────────────────────────────────
            case "tpb" -> plugin.getAdminModeManager().teleportBack(player);

            default -> player.sendMessage(plugin.getPrefix() +
                    "§cSous-commande inconnue. §7Usage : §f/adminmode [back|fly|vanish|noclip|tpp|freeze|spy|spec|chatlog|stats|hud|tpb|list|reload]");
        }

        return true;
    }

    // ─── Spy inventory ────────────────────────────────────────────────────

    private void openSpyInventory(Player admin, Player target) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("Inventaire de " + target.getName()));

        ItemStack[] storage = target.getInventory().getStorageContents();
        for (int i = 0; i < Math.min(36, storage.length); i++) {
            if (storage[i] != null) inv.setItem(i, storage[i].clone());
        }

        ItemStack[] armor = target.getInventory().getArmorContents();
        if (armor.length >= 4) {
            if (armor[3] != null) inv.setItem(36, armor[3].clone());
            if (armor[2] != null) inv.setItem(37, armor[2].clone());
            if (armor[1] != null) inv.setItem(38, armor[1].clone());
            if (armor[0] != null) inv.setItem(39, armor[0].clone());
        }

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        if (pm != null) { pm.displayName(Component.text(" ")); pane.setItemMeta(pm); }
        inv.setItem(40, pane);

        ItemStack offHand = target.getInventory().getItemInOffHand();
        if (offHand.getType() != Material.AIR) inv.setItem(41, offHand.clone());
        for (int i = 42; i < 54; i++) inv.setItem(i, pane.clone());

        admin.openInventory(inv);
        admin.sendMessage(plugin.getPrefix() + "§7Inventaire de §c" + target.getName() + " §7(lecture seule)");
    }

    // ─── Tab Completer ────────────────────────────────────────────────────

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> subs = List.of("back", "fly", "vanish", "noclip", "tpp",
                    "freeze", "spy", "spec", "chatlog", "stats", "list", "reload");
            return subs.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String partial = args[1].toLowerCase();
            return switch (sub) {
                case "freeze", "spy", "spec", "chatlog", "stats", "tpp" ->
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(n -> n.toLowerCase().startsWith(partial))
                                .collect(Collectors.toList());
                case "fly" -> List.of("0.1", "0.2", "0.5", "1.0").stream()
                        .filter(s -> s.startsWith(partial)).collect(Collectors.toList());
                default -> Collections.emptyList();
            };
        }

        return Collections.emptyList();
    }
}

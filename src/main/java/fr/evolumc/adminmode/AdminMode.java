package fr.evolumc.adminmode;

import fr.evolumc.adminmode.commands.*;
import fr.evolumc.adminmode.listeners.*;
import fr.evolumc.adminmode.managers.*;
import fr.evolumc.adminmode.vanish.integration.DiscordSrvBridge;
import fr.evolumc.adminmode.vanish.listeners.VanishListener;
import fr.evolumc.adminmode.vanish.managers.NoclipManager;
import fr.evolumc.adminmode.vanish.managers.VanishManager;
import org.bukkit.plugin.java.JavaPlugin;

public class AdminMode extends JavaPlugin {

    private static AdminMode instance;

    // ─── Managers existants ───────────────────────────────────────────────
    private AdminModeManager    adminModeManager;
    private StaffChatManager    staffChatManager;
    private FreezeManager       freezeManager;
    private ChatLogManager      chatLogManager;
    private SessionStatsManager sessionStatsManager;
    private HUDManager          hudManager;
    private SpectatorManager    spectatorManager;
    private HudPreferenceManager hudPreferenceManager;
    private SpyChatManager      spyChatManager;
    private SpyTrackManager     spyTrackManager;
    private ReportManager       reportManager;
    private AdminNoteManager    adminNoteManager;
    private MaintenanceManager  maintenanceManager;

    // ─── Vanish ───────────────────────────────────────────────────────────
    private VanishManager       vanishManager;
    private NoclipManager       noclipManager;
    private DiscordSrvBridge    discordSrvBridge;

    // ─── Nouveaux managers v3 ─────────────────────────────────────────────
    private XpAlertManager       xpAlertManager;
    private WatchListManager     watchListManager;
    private SessionReplayManager sessionReplayManager;
    private QuickTpManager       quickTpManager;
    private CooldownManager      cooldownManager;
    private OnlineDurationManager onlineDurationManager;
    private CommandStatsManager  commandStatsManager;
    private DailyReportManager   dailyReportManager;
    private LockCommandManager   lockCommandManager;

    // ─── Managers v3.1 (surveillance passive + AdminPanel) ───────────────
    private SuspiciousPatternManager suspiciousPatternManager;
    private AdminPanelManager        adminPanelManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // ── Managers existants ────────────────────────────────────────────
        this.adminModeManager    = new AdminModeManager(this);
        this.staffChatManager    = new StaffChatManager(this);
        this.freezeManager       = new FreezeManager(this);
        this.chatLogManager      = new ChatLogManager(this);
        this.sessionStatsManager = new SessionStatsManager();
        this.spectatorManager    = new SpectatorManager(this);
        this.hudPreferenceManager = new HudPreferenceManager();
        this.hudManager          = new HUDManager(this);
        hudManager.start();


        this.spyChatManager     = new SpyChatManager(this);
        this.spyTrackManager    = new SpyTrackManager(this);
        this.reportManager      = new ReportManager(this);
        this.adminNoteManager   = new AdminNoteManager(this);
        this.maintenanceManager = new MaintenanceManager(this);

        // ── Vanish ────────────────────────────────────────────────────────
        this.discordSrvBridge = new DiscordSrvBridge(getLogger());
        this.vanishManager    = new VanishManager(this);
        this.noclipManager    = new NoclipManager(this);

        // ── Nouveaux managers v3 ──────────────────────────────────────────
        this.lockCommandManager    = new LockCommandManager(this);
        this.cooldownManager       = new CooldownManager(this);
        this.commandStatsManager   = new CommandStatsManager(this);
        this.onlineDurationManager = new OnlineDurationManager(this);
        this.watchListManager      = new WatchListManager(this);
        this.sessionReplayManager  = new SessionReplayManager(this);
        this.quickTpManager        = new QuickTpManager(this);
        this.xpAlertManager        = new XpAlertManager(this);
        xpAlertManager.start();
        this.dailyReportManager    = new DailyReportManager(this);
        dailyReportManager.start();

        // ── Managers v3.1 ─────────────────────────────────────────────────
        this.suspiciousPatternManager = new SuspiciousPatternManager(this);
        this.adminPanelManager        = new AdminPanelManager(this);

        // ── Commandes existantes ──────────────────────────────────────────
        AdminModeCommand amCmd = new AdminModeCommand(this);
        getCommand("adminmode").setExecutor(amCmd);
        getCommand("adminmode").setTabCompleter(amCmd);

        StaffChatCommand scCmd = new StaffChatCommand(this, staffChatManager);
        getCommand("staffchat").setExecutor(scCmd);
        getCommand("staffchat").setTabCompleter(scCmd);

        FreezeCommand   freezeCmd   = new FreezeCommand(this);
        SpyCommand      spyCmd      = new SpyCommand(this);
        SpecCommand     specCmd     = new SpecCommand(this);
        ChatLogCommand  chatlogCmd  = new ChatLogCommand(this);

        getCommand("freeze").setExecutor(freezeCmd);
        getCommand("freeze").setTabCompleter(freezeCmd);
        getCommand("freezeall").setExecutor(freezeCmd);
        getCommand("spy").setExecutor(spyCmd);
        getCommand("spy").setTabCompleter(spyCmd);
        getCommand("spec").setExecutor(specCmd);
        getCommand("spec").setTabCompleter(specCmd);
        getCommand("chatlog").setExecutor(chatlogCmd);
        getCommand("chatlog").setTabCompleter(chatlogCmd);

        TrackCommand     trackCmd   = new TrackCommand(this);
        getCommand("track").setExecutor(trackCmd);
        getCommand("track").setTabCompleter(trackCmd);

        CmdLogCommand    cmdlogCmd  = new CmdLogCommand(this);
        getCommand("cmdlog").setExecutor(cmdlogCmd);
        getCommand("cmdlog").setTabCompleter(cmdlogCmd);

        TpbCommand       tpbCmd     = new TpbCommand(this);
        getCommand("tpb").setExecutor(tpbCmd);
        getCommand("tpb").setTabCompleter(tpbCmd);

        ReportCommand    reportCmd  = new ReportCommand(this);
        getCommand("report").setExecutor(reportCmd);
        getCommand("report").setTabCompleter(reportCmd);

        AdminNoteCommand noteCmd    = new AdminNoteCommand(this);
        getCommand("note").setExecutor(noteCmd);
        getCommand("note").setTabCompleter(noteCmd);

        MaintenanceCommand maintCmd = new MaintenanceCommand(this);
        getCommand("maintenance").setExecutor(maintCmd);
        getCommand("maintenance").setTabCompleter(maintCmd);

        HudGuiCommand    hudGuiCmd  = new HudGuiCommand(this);
        getCommand("adminhud").setExecutor(hudGuiCmd);
        getCommand("adminhud").setTabCompleter(hudGuiCmd);

        VanishCommand    vanishCmd    = new VanishCommand(this);
        NoclipCommand    noclipCmd    = new NoclipCommand(this);
        TppCommand       tppCmd       = new TppCommand(this);
        VanishTpCommand  vanishTpCmd  = new VanishTpCommand(this);

        getCommand("vanish").setExecutor(vanishCmd);
        getCommand("vanish").setTabCompleter(vanishCmd);
        getCommand("noclip").setExecutor(noclipCmd);
        getCommand("tpp").setExecutor(tppCmd);
        getCommand("tpp").setTabCompleter(tppCmd);
        getCommand("vanishtp").setExecutor(vanishTpCmd);
        getCommand("vanishtp").setTabCompleter(vanishTpCmd);

        // ── Nouvelles commandes v3 ────────────────────────────────────────
        SurveillanceCommand  survCmd  = new SurveillanceCommand(this);
        getCommand("surveillance").setExecutor(survCmd);
        getCommand("surveillance").setTabCompleter(survCmd);

        QuickTpCommand  qtpCmd = new QuickTpCommand(this);
        getCommand("qtp").setExecutor(qtpCmd);
        getCommand("qtp").setTabCompleter(qtpCmd);

        AdminStatsCommand statsCmd = new AdminStatsCommand(this);
        getCommand("adminstats").setExecutor(statsCmd);
        getCommand("adminstats").setTabCompleter(statsCmd);

        PlaytimeCommand ptCmd = new PlaytimeCommand(this);
        getCommand("playtime").setExecutor(ptCmd);
        getCommand("playtime").setTabCompleter(ptCmd);

        LockCommandCmd lockCmd = new LockCommandCmd(this);
        getCommand("lockcommand").setExecutor(lockCmd);
        getCommand("lockcommand").setTabCompleter(lockCmd);

        // ── Commandes v3.1 ────────────────────────────────────────────────
        AdminPanelCommand panelCmd = new AdminPanelCommand(this);
        getCommand("adminpanel").setExecutor(panelCmd);
        getCommand("adminpanel").setTabCompleter(panelCmd);

        StaffListCommand staffListCmd = new StaffListCommand(this);
        getCommand("stafflist").setExecutor(staffListCmd);
        getCommand("stafflist").setTabCompleter(staffListCmd);

        // ── Listeners ─────────────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(new AdminModeListener(this), this);
        getServer().getPluginManager().registerEvents(new StaffChatListener(this, staffChatManager), this);
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatLogListener(this), this);
        getServer().getPluginManager().registerEvents(new SpyInventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new SpyChatListener(this), this);
        getServer().getPluginManager().registerEvents(new VanishListener(this), this);
        getServer().getPluginManager().registerEvents(maintenanceManager, this);
        getServer().getPluginManager().registerEvents(hudGuiCmd, this);
        // Nouveaux listeners v3
        getServer().getPluginManager().registerEvents(new WatchListListener(this), this);
        getServer().getPluginManager().registerEvents(new LockCommandListener(this), this);
        getServer().getPluginManager().registerEvents(new OnlineDurationListener(this), this);
        getServer().getPluginManager().registerEvents(new CooldownListener(this), this);
        getServer().getPluginManager().registerEvents(quickTpManager, this);
        // Listeners v3.1
        getServer().getPluginManager().registerEvents(new AdminPanelListener(this), this);

        getLogger().info("AdminMode v3.1 activé — SuspiciousPatterns, AdminPanel, StaffList, LogRotation, ReportExport !");
    }

    @Override
    public void onDisable() {
        if (hudManager         != null) hudManager.stop();
        if (freezeManager      != null) freezeManager.unfreezeAll();
        if (adminModeManager   != null) adminModeManager.disableAllAdmins();

        if (vanishManager      != null) vanishManager.unvanishAll();
        if (noclipManager      != null) noclipManager.stopAll();
        if (xpAlertManager     != null) xpAlertManager.stop();
        if (dailyReportManager != null) dailyReportManager.stop();
        // Flush toutes les sessions de surveillance sur disque avant arrêt
        if (watchListManager          != null) watchListManager.flushAll();
        if (suspiciousPatternManager  != null) suspiciousPatternManager.stop();
        getLogger().info("AdminMode désactivé — sessions de surveillance sauvegardées.");
    }

    // ─── Getters ──────────────────────────────────────────────────────────
    public static AdminMode getInstance()                           { return instance; }
    public AdminModeManager getAdminModeManager()                   { return adminModeManager; }
    public StaffChatManager getStaffChatManager()                   { return staffChatManager; }

    public FreezeManager getFreezeManager()                         { return freezeManager; }
    public ChatLogManager getChatLogManager()                       { return chatLogManager; }
    public SessionStatsManager getSessionStatsManager()             { return sessionStatsManager; }
    public HUDManager getHUDManager()                               { return hudManager; }
    public SpectatorManager getSpectatorManager()                   { return spectatorManager; }
    public HudPreferenceManager getHudPreferenceManager()           { return hudPreferenceManager; }
    public SpyChatManager getSpyChatManager()                       { return spyChatManager; }
    public SpyTrackManager getSpyTrackManager()                     { return spyTrackManager; }
    public ReportManager getReportManager()                         { return reportManager; }
    public AdminNoteManager getAdminNoteManager()                   { return adminNoteManager; }
    public MaintenanceManager getMaintenanceManager()               { return maintenanceManager; }
    public VanishManager getVanishManager()                         { return vanishManager; }
    public NoclipManager getNoclipManager()                         { return noclipManager; }
    public DiscordSrvBridge getDiscordSrvBridge()                   { return discordSrvBridge; }
    // v3
    public XpAlertManager getXpAlertManager()                      { return xpAlertManager; }
    public WatchListManager getWatchListManager()                   { return watchListManager; }
    public SessionReplayManager getSessionReplayManager()           { return sessionReplayManager; }
    public QuickTpManager getQuickTpManager()                       { return quickTpManager; }
    public CooldownManager getCooldownManager()                     { return cooldownManager; }
    public OnlineDurationManager getOnlineDurationManager()         { return onlineDurationManager; }
    public CommandStatsManager getCommandStatsManager()             { return commandStatsManager; }
    public DailyReportManager getDailyReportManager()               { return dailyReportManager; }
    public LockCommandManager getLockCommandManager()               { return lockCommandManager; }
    // v3.1
    public SuspiciousPatternManager getSuspiciousPatternManager()   { return suspiciousPatternManager; }
    public AdminPanelManager getAdminPanelManager()                 { return adminPanelManager; }

    public String getPrefix() {
        return colorize(getConfig().getString("messages.prefix", "&8[&cAdminMode&8] &r"));
    }

    public String getMessage(String key) {
        String msg = getConfig().getString("messages." + key, "&cMessage manquant : " + key);
        return colorize(msg);
    }

    public static String colorize(String text) {
        return text.replace("&", "§");
    }
}

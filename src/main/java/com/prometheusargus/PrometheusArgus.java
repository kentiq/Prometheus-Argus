package com.prometheusargus;

import com.prometheusargus.SpeedCheck;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.BanList;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.io.File;

import com.prometheusargus.mode.ModModeManager;
import com.prometheusargus.mode.ModModeListener;

public class PrometheusArgus extends JavaPlugin implements Listener, TabCompleter {

    private PlayerDataManager playerDataManager;
    private boolean globalDebugMode;
    private StaffGUIManager staffGUIManager;
    private SanctionHistoryManager sanctionHistoryManager;

    private FreezeManager freezeManager;
    private MuteManager muteManager;
    private PlayerNotesManager playerNotesManager;
    private ReportManager reportManager;
    private StaffChatManager staffChatManager;

    private ModModeManager modModeManager;
    private FileConfiguration modModeConfig;

    private int reachA_VlAlertLow, reachA_VlAlertMedium, reachA_VlAlertHigh, reachA_VlBanThreshold;
    private String reachA_BanReason;
    private int nofallA_VlAlertLow, nofallA_VlAlertMedium, nofallA_VlBanThreshold;
    private String nofallA_BanReason;
    private int flyA_VlAlertLow, flyA_VlAlertMedium, flyA_VlBanThreshold;
    private String flyA_BanReason;
    private int flyBGlide_VlAlertLow, flyBGlide_VlAlertMedium, flyBGlide_VlBanThreshold;
    private String flyBGlide_BanReason;
    private int knockbackA_VlAlertLow, knockbackA_VlAlertMedium, knockbackA_VlBanThreshold;
    private String knockbackA_BanReason;

    private int speedA_VlAlertLow, speedA_VlAlertMedium, speedA_VlAlertHigh, speedA_VlBanThresholdConfig;
    private String speedA_BanReasonConfig;
    private int speedB_VlAlertLow, speedB_VlAlertMedium, speedB_VlAlertHigh, speedB_VlBanThresholdConfig;
    private String speedB_BanReasonConfig;
    private int speedC_VlAlertLow, speedC_VlAlertMedium, speedC_VlAlertHigh, speedC_VlBanThresholdConfig;
    private String speedC_BanReasonConfig;
    private int speedD_VlAlertLow, speedD_VlAlertMedium, speedD_VlAlertHigh, speedD_VlBanThresholdConfig;
    private String speedD_BanReasonConfig;

    private Random random = new Random();
    private final SimpleDateFormat banExpirationFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss z", Locale.FRENCH);


    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        this.globalDebugMode = getConfig().getBoolean("global_debug_mode", false);
        if (globalDebugMode) getLogger().info("[Prometheus DEBUG] Global Debug Mode is ENABLED.");

        try {
            this.playerDataManager = new PlayerDataManager(this);
            this.sanctionHistoryManager = new SanctionHistoryManager(this);
            this.freezeManager = new FreezeManager(this);
            this.muteManager = new MuteManager(this);
            this.playerNotesManager = new PlayerNotesManager(this);
            this.reportManager = new ReportManager(this);
            this.staffChatManager = new StaffChatManager(this);
            this.staffGUIManager = new StaffGUIManager(this);
            if (this.staffGUIManager != null) {
                 new GUIListeners(this, staffGUIManager);
            } else {
                getLogger().severe("StaffGUIManager n'a pas pu être initialisé, GUIListeners non chargé.");
            }

            if (globalDebugMode) {
                getLogger().info("[Prometheus DEBUG] PlayerDataManager initialized: " + (playerDataManager != null));
                getLogger().info("[Prometheus DEBUG] SanctionHistoryManager initialized: " + (sanctionHistoryManager != null));
                getLogger().info("[Prometheus DEBUG] FreezeManager initialized: " + (freezeManager != null));
                getLogger().info("[Prometheus DEBUG] MuteManager initialized: " + (muteManager != null));
                getLogger().info("[Prometheus DEBUG] PlayerNotesManager initialized: " + (playerNotesManager != null));
                getLogger().info("[Prometheus DEBUG] ReportManager initialized: " + (reportManager != null));
                getLogger().info("[Prometheus DEBUG] StaffChatManager initialized: " + (staffChatManager != null));
                getLogger().info("[Prometheus DEBUG] StaffGUIManager initialized: " + (staffGUIManager != null));
            }
        } catch (Exception e) {
            getLogger().severe("UNE ERREUR CRITIQUE EST SURVENUE LORS DE L'INITIALISATION D'UN MANAGER !");
            e.printStackTrace();
            getLogger().severe("Prometheus Argus pourrait ne pas fonctionner correctement.");
        }

        if (getConfig().getBoolean("checks.reach_a.enabled", true)) new ReachCheckA(this);
        if (getConfig().getBoolean("checks.nofall_a.enabled", true)) new NoFallCheck_CustomFall(this);
        if (getConfig().getBoolean("checks.fly_a.enabled", true) || getConfig().getBoolean("checks.fly_b_glide.enabled", true)) {
            new FlyChecks(this);
        }
        if (getConfig().getBoolean("checks.knockback_a.enabled", true)) new KnockbackCheck(this);
        
        if (getConfig().getBoolean("checks.speed.enabled", true)) {
            new SpeedCheck(this); 
            if (globalDebugMode) getLogger().info("[Prometheus DEBUG] SpeedCheck initialized.");
        }

        getServer().getPluginManager().registerEvents(this, this);

        if (globalDebugMode) getLogger().info("[Prometheus DEBUG] Ensuring PlayerACData for already online players...");
        if (playerDataManager != null) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                initPlayerData(onlinePlayer);
            }
        } else {
             if (globalDebugMode) getLogger().warning("[Prometheus DEBUG] PlayerDataManager was null during onEnable, cannot init ACData for online players.");
        }

        getLogger().info("========================================");
        getLogger().info("Prometheus Argus Anti-Cheat");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("Statut: ACTIVÉ");
        getLogger().info("Développé par Kentiq.");
        getLogger().info("========================================");

        setupModMode();
    }

    private void setupModMode() {
        File modModeFile = new File(getDataFolder(), "modmode.yml");
        if (!modModeFile.exists()) {
            saveResource("modmode.yml", false);
        }
        
        modModeConfig = YamlConfiguration.loadConfiguration(modModeFile);
        
        modModeManager = new ModModeManager(this);
        
        getServer().getPluginManager().registerEvents(new ModModeListener(this, modModeManager), this);
        
        getLogger().info("[ModMode] Initialisé avec succès");
    }

    public ModModeManager getModModeManager() {
        return modModeManager;
    }

    public FileConfiguration getModModeConfig() {
        return modModeConfig;
    }

    private void initPlayerData(Player player) {
        if (playerDataManager == null) {
             if (globalDebugMode) getLogger().warning("[Prometheus DEBUG] PlayerDataManager is null in initPlayerData for " + player.getName());
            return;
        }
        PlayerACData acData = playerDataManager.getPlayerData(player);

        if (acData.getLastMoveLocation() == null && player.getLocation() != null) {
            acData.setLastMoveLocation(player.getLocation());
        }
        if (acData.getLastMoveTime() == 0) { 
            acData.setLastMoveTime(System.currentTimeMillis() - 50);
        }
        acData.setWasOnGroundSpeed(player.isOnGround());

        if (player.getLocation() != null) {
            if (acData.lastYPositionFly == 0.0 && acData.highestYReachedInAir == 0.0) {
                acData.lastYPositionFly = player.getLocation().getY();
                acData.highestYReachedInAir = player.getLocation().getY();
            }
            if (acData.lastOnGroundLocation == null && player.isOnGround()) {
                acData.lastOnGroundLocation = player.getLocation();
            }
        }
    }

    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public SanctionHistoryManager getSanctionHistoryManager() { return sanctionHistoryManager; }
    public FreezeManager getFreezeManager() { return freezeManager; }
    public MuteManager getMuteManager() { return muteManager; }
    public PlayerNotesManager getPlayerNotesManager() { return playerNotesManager; }
    public ReportManager getReportManager() { return reportManager; }
    public StaffChatManager getStaffChatManager() { return staffChatManager; }
    public StaffGUIManager getStaffGUIManager() { return staffGUIManager; }
    public boolean isGlobalDebugModeEnabled() { return globalDebugMode; }


    private void loadConfigValues() {
        reachA_VlAlertLow = getConfig().getInt("checks.reach_a.thresholds.low", 5);
        reachA_VlAlertMedium = getConfig().getInt("checks.reach_a.thresholds.medium", 10);
        reachA_VlAlertHigh = getConfig().getInt("checks.reach_a.thresholds.high", 15);
        reachA_VlBanThreshold = getConfig().getInt("checks.reach_a.ban.threshold", 20);
        reachA_BanReason = getConfig().getString("checks.reach_a.ban.reason", "&cPrometheus Argus: Tricherie détectée (Reach)");

        nofallA_VlAlertLow = getConfig().getInt("checks.nofall_a.thresholds.low", 8);
        nofallA_VlAlertMedium = getConfig().getInt("checks.nofall_a.thresholds.medium", 16);
        nofallA_VlBanThreshold = getConfig().getInt("checks.nofall_a.ban.threshold", 24);
        nofallA_BanReason = getConfig().getString("checks.nofall_a.ban.reason", "&cPrometheus Argus: Tricherie détectée (NoFall Calc.)");

        flyA_VlAlertLow = getConfig().getInt("checks.fly_a.thresholds.low", 8);
        flyA_VlAlertMedium = getConfig().getInt("checks.fly_a.thresholds.medium", 15);
        flyA_VlBanThreshold = getConfig().getInt("checks.fly_a.ban.threshold", 25);
        flyA_BanReason = getConfig().getString("checks.fly_a.ban.reason", "&cPrometheus Argus: Tricherie détectée (Fly - Mouvements anormaux)");

        flyBGlide_VlAlertLow = getConfig().getInt("checks.fly_b_glide.thresholds.low", 5);
        flyBGlide_VlAlertMedium = getConfig().getInt("checks.fly_b_glide.thresholds.medium", 10);
        flyBGlide_VlBanThreshold = getConfig().getInt("checks.fly_b_glide.ban.threshold", 15);
        flyBGlide_BanReason = getConfig().getString("checks.fly_b_glide.ban.reason", "&cPrometheus Argus: Tricherie détectée (Fly - Glide)");
        
        String kbPath = "checks.knockback_a.";
        knockbackA_VlAlertLow = getConfig().getInt(kbPath + "thresholds.low", 4);
        knockbackA_VlAlertMedium = getConfig().getInt(kbPath + "thresholds.medium", 8);
        knockbackA_VlBanThreshold = getConfig().getInt(kbPath + "ban.threshold", 12);
        knockbackA_BanReason = getConfig().getString(kbPath + "ban.reason", "&cPrometheus Argus: Tricherie détectée (Anti-Knockback)");

        speedA_VlAlertLow = getConfig().getInt("checks.speed_a.thresholds.low", 5);
        speedA_VlAlertMedium = getConfig().getInt("checks.speed_a.thresholds.medium", 10);
        speedA_VlAlertHigh = getConfig().getInt("checks.speed_a.thresholds.high", 15);
        speedA_VlBanThresholdConfig = getConfig().getInt("checks.speed_a.ban.threshold", 20);
        speedA_BanReasonConfig = getConfig().getString("checks.speed_a.ban.reason", "&cPrometheus Argus: Tricherie détectée (Speed A)");

        speedB_VlAlertLow = getConfig().getInt("checks.speed_b_jump.thresholds.low", 3);
        speedB_VlAlertMedium = getConfig().getInt("checks.speed_b_jump.thresholds.medium", 6);
        speedB_VlAlertHigh = getConfig().getInt("checks.speed_b_jump.thresholds.high", 8); 
        speedB_VlBanThresholdConfig = getConfig().getInt("checks.speed_b_jump.ban.threshold", 10);
        speedB_BanReasonConfig = getConfig().getString("checks.speed_b_jump.ban.reason", "&cPrometheus Argus: Tricherie détectée (Speed B - Saut)");

        speedC_VlAlertLow = getConfig().getInt("checks.speed_c_packet.thresholds.low", 5);
        speedC_VlAlertMedium = getConfig().getInt("checks.speed_c_packet.thresholds.medium", 10);
        speedC_VlAlertHigh = getConfig().getInt("checks.speed_c_packet.thresholds.high", 12);
        speedC_VlBanThresholdConfig = getConfig().getInt("checks.speed_c_packet.ban.threshold", 15);
        speedC_BanReasonConfig = getConfig().getString("checks.speed_c_packet.ban.reason", "&cPrometheus Argus: Tricherie détectée (Speed C - Paquets)");

        speedD_VlAlertLow = getConfig().getInt("checks.speed_d_ground.thresholds.low", 8);
        speedD_VlAlertMedium = getConfig().getInt("checks.speed_d_ground.thresholds.medium", 15);
        speedD_VlAlertHigh = getConfig().getInt("checks.speed_d_ground.thresholds.high", 20);
        speedD_VlBanThresholdConfig = getConfig().getInt("checks.speed_d_ground.ban.threshold", 25);
        speedD_BanReasonConfig = getConfig().getString("checks.speed_d_ground.ban.reason", "&cPrometheus Argus: Tricherie détectée (Speed D - Sol)");


        if (globalDebugMode || getConfig().getBoolean("debug_config_loading", false)) {
            getLogger().info("--- Prometheus Argus Configuration Loaded ---");
            getLogger().info("[Config] ReachA: LowVL=" + reachA_VlAlertLow + ", MedVL=" + reachA_VlAlertMedium + ", HighVL=" + reachA_VlAlertHigh + ", BanVL=" + reachA_VlBanThreshold);
            getLogger().info("[Config] NoFall (nofall_a): LowVL=" + nofallA_VlAlertLow + ", MedVL=" + nofallA_VlAlertMedium + ", BanVL=" + nofallA_VlBanThreshold);
            getLogger().info("[Config] FlyA (Main): LowVL=" + flyA_VlAlertLow + ", MedVL=" + flyA_VlAlertMedium + ", BanVL=" + flyA_VlBanThreshold);
            getLogger().info("[Config] FlyB_Glide: LowVL=" + flyBGlide_VlAlertLow + ", MedVL=" + flyBGlide_VlAlertMedium + ", BanVL=" + flyBGlide_VlBanThreshold);
            getLogger().info("[Config] KnockbackA: Enabled=" + getConfig().getBoolean(kbPath + "enabled", true) +
                             ", MinRatio=" + getConfig().getDouble(kbPath + "min_kb_ratio_taken", 0.20) +
                             ", Window=" + getConfig().getInt(kbPath + "check_ticks_window", 4) +
                             ", VLInc=" + getConfig().getInt(kbPath + "vl_increment", 2) +
                             ", LowVL=" + knockbackA_VlAlertLow + ", MedVL=" + knockbackA_VlAlertMedium + ", BanVL=" + knockbackA_VlBanThreshold);
            getLogger().info("[Config] SpeedA: VLs Low=" + speedA_VlAlertLow + ", Med=" + speedA_VlAlertMedium + ", High=" + speedA_VlAlertHigh + ", Ban=" + speedA_VlBanThresholdConfig);
            getLogger().info("[Config] SpeedB_Jump: VLs Low=" + speedB_VlAlertLow + ", Med=" + speedB_VlAlertMedium + ", High=" + speedB_VlAlertHigh + ", Ban=" + speedB_VlBanThresholdConfig);
            getLogger().info("[Config] SpeedC_Packet: VLs Low=" + speedC_VlAlertLow + ", Med=" + speedC_VlAlertMedium + ", High=" + speedC_VlAlertHigh + ", Ban=" + speedC_VlBanThresholdConfig);
            getLogger().info("[Config] SpeedD_Ground: VLs Low=" + speedD_VlAlertLow + ", Med=" + speedD_VlAlertMedium + ", High=" + speedD_VlAlertHigh + ", Ban=" + speedD_VlBanThresholdConfig);
            getLogger().info("--- End of Configuration ---");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Prometheus Argus Anti-Cheat est désactivé.");
    }

    public Date parseBanDuration(String timeStr) throws IllegalArgumentException {
        if (timeStr.equalsIgnoreCase("perm") || timeStr.equalsIgnoreCase("permanent") || timeStr.equals("0")) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        long value;
        String unitPart;
        Matcher matcher = Pattern.compile("^(\\d+)([a-zA-Z]+)$").matcher(timeStr);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Format de temps invalide. Utilisez <nombre><unité> (ex: 30d, 1mo, 2h) ou 'perm'.");
        }
        try {
            value = Long.parseLong(matcher.group(1));
            unitPart = matcher.group(2).toLowerCase();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Format de temps invalide: partie numérique incorrecte.");
        }
        if (value <= 0) {
             throw new IllegalArgumentException("La durée doit être positive (ex: 1d). Utilisez '0' ou 'perm' pour un ban permanent.");
        }
        switch (unitPart) {
            case "s": calendar.add(Calendar.SECOND, (int) value); break;
            case "m": calendar.add(Calendar.MINUTE, (int) value); break;
            case "h": calendar.add(Calendar.HOUR_OF_DAY, (int) value); break;
            case "d": calendar.add(Calendar.DAY_OF_MONTH, (int) value); break;
            case "w": calendar.add(Calendar.WEEK_OF_YEAR, (int) value); break;
            case "mo": calendar.add(Calendar.MONTH, (int) value); break;
            case "y": calendar.add(Calendar.YEAR, (int) value); break;
            default: throw new IllegalArgumentException("Unité de temps inconnue: '" + unitPart + "'. Unités valides: s, m, h, d, w, mo, y.");
        }
        return calendar.getTime();
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "=== Prometheus Argus - Aide ===");
        
        sendSingleHelpEntry(sender, "help", "Affiche ce message d'aide", "prometheusargus.command.base");
        sendSingleHelpEntry(sender, "reload", "Recharge la configuration", "prometheusargus.command.reload");
        
        sendSingleHelpEntry(sender, "ban <joueur> <temps> <raison>", "Bannir un joueur", "prometheusargus.command.ban");
        sendSingleHelpEntry(sender, "unban <joueur>", "Débannir un joueur", "prometheusargus.command.unban");
        sendSingleHelpEntry(sender, "kick <joueur> <raison>", "Expulser un joueur", "prometheusargus.command.kick");
        sendSingleHelpEntry(sender, "mute <joueur> <temps> <raison>", "Rendre muet un joueur", "prometheusargus.command.mute");
        sendSingleHelpEntry(sender, "unmute <joueur>", "Rendre la parole à un joueur", "prometheusargus.command.unmute");
        sendSingleHelpEntry(sender, "freeze <joueur>", "Geler un joueur", "prometheusargus.command.freeze");
        sendSingleHelpEntry(sender, "mod", "Activer/désactiver le mode modérateur", "prometheusargus.modmode.use");
        
        sendSingleHelpEntry(sender, "lookup <joueur>", "Voir les informations d'un joueur", "prometheusargus.command.lookup");
        sendSingleHelpEntry(sender, "history <joueur>", "Voir l'historique des sanctions", "prometheusargus.command.history");
        
        sendSingleHelpEntry(sender, "notes <joueur> add <note>", "Ajouter une note", "prometheusargus.notes.add");
        sendSingleHelpEntry(sender, "notes <joueur> view", "Voir les notes", "prometheusargus.notes.view");
        sendSingleHelpEntry(sender, "notes <joueur> remove <index>", "Supprimer une note", "prometheusargus.notes.remove");
        
        sendSingleHelpEntry(sender, "sc <message>", "Envoyer un message dans le staff chat", "prometheusargus.staffchat.use");
        sendSingleHelpEntry(sender, "sc", "Activer/désactiver le staff chat", "prometheusargus.staffchat.toggle");
        sendSingleHelpEntry(sender, "staff", "Ouvrir le menu staff", "prometheusargus.staffgui");
        sendSingleHelpEntry(sender, "observe", "Observer un joueur", "prometheusargus.staff.observe");
        sendSingleHelpEntry(sender, "test", "Tester un joueur", "prometheusargus.staff.test");
    }

    private void sendSingleHelpEntry(CommandSender sender, String command, String description, String permission, String... additionalInfo) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6/pa " + command + " &8- &e" + description));
        if (additionalInfo != null && additionalInfo.length > 0) {
            for (String info : additionalInfo) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "  " + info));
            }
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "  &7Permission: &b" + permission));
        sender.sendMessage(""); 
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (cmd.getName().equalsIgnoreCase("report")) {
            return handleReportPlayerCommand(sender, args);
        }

        if (!cmd.getName().equalsIgnoreCase("prometheusargus") && !cmd.getName().equalsIgnoreCase("pa")) {
            return false;
        }

        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("help"))) {
            if (!sender.hasPermission("prometheusargus.command.base")) { 
                sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser cette commande.");
                return true;
            }
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "staff":
                if (sender instanceof Player) {
                    if (staffGUIManager == null) {
                        getLogger().severe("StaffGUIManager is null! Command '/pa staff' cannot be executed.");
                        sender.sendMessage(ChatColor.RED + "Erreur interne: Le manager de GUI du staff n'est pas initialisé.");
                        return true;
                    }
                    if (sender.hasPermission("prometheusargus.staffgui")) staffGUIManager.openMainMenu((Player) sender);
                    else sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
                } else sender.sendMessage("Commande pour joueurs uniquement.");
                return true;
            case "observe": return handleObserveCommand(sender, subArgs);
            case "test": return handleTestCommand(sender, subArgs);
            case "ban": return handleBanCommand(sender, subArgs);
            case "unban": return handleUnbanCommand(sender, subArgs);
            case "kick": return handleKickCommand(sender, subArgs);
            case "lookup": return handleLookupCommand(sender, subArgs);
            case "history": return handleHistoryCommand(sender, subArgs);
            case "reload":
                if (!sender.hasPermission("prometheusargus.command.reload")) {
                    sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission."); 
                    return true;
                }
                reloadAllConfigs();
                sender.sendMessage(ChatColor.GREEN + "[PrometheusArgus] Toutes les configurations ont été rechargées.");
                getLogger().info("[PrometheusArgus] Configuration reloaded by " + sender.getName());
                return true;

            case "freeze":
            case "unfreeze": 
                return handleFreezeCommand(sender, subArgs);
            case "mute":
                return handleMuteCommand(sender, subArgs);
            case "unmute":
                return handleUnmuteCommand(sender, subArgs);
            case "notes":
                return handleNotesCommand(sender, subArgs);
            case "reports":
                 if (!(sender instanceof Player)) { sender.sendMessage("Commande pour joueurs uniquement."); return true; }
                 if (staffGUIManager == null) {
                    getLogger().severe("StaffGUIManager is null! Command '/pa reports' cannot be executed.");
                    sender.sendMessage(ChatColor.RED + "Erreur interne: Le manager de GUI du staff n'est pas initialisé.");
                    return true;
                }
                if (!sender.hasPermission("prometheusargus.reports.view")) {
                    sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de voir les reports."); return true;
                }
                int pageVal = 1;
                if (subArgs.length > 0) { try { pageVal = Integer.parseInt(subArgs[0]); } catch (NumberFormatException ignored) {} }
                if (pageVal < 1) pageVal = 1;
                staffGUIManager.openReportsMenu((Player) sender, pageVal);
                return true;
            case "sc":
            case "staffchat":
                return handleStaffChatCommand(sender, subArgs);
            case "mod":
                return handleModCommand(sender, args);

            default:
                sender.sendMessage(ChatColor.RED + "Sous-commande '" + args[0] + "' inconnue.");
                sender.sendMessage(ChatColor.YELLOW + "Utilisez " + ChatColor.GOLD + "/pa help" + ChatColor.YELLOW + " pour la liste.");
                return true;
        }
    }
    
    @SuppressWarnings("deprecation") 
    private boolean handleReportPlayerCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Cette commande ne peut être utilisée que par un joueur.");
            return true;
        }
        Player reporter = (Player) sender;
        if (!reporter.hasPermission("prometheusargus.report.use")) {
            reporter.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de signaler des joueurs.");
            return true;
        }
        if (args.length < 2) {
            reporter.sendMessage(ChatColor.RED + "Usage: /report <joueur> <raison>");
            return true;
        }
        OfflinePlayer reportedPlayer = Bukkit.getOfflinePlayer(args[0]);
        if ((!reportedPlayer.hasPlayedBefore() && !reportedPlayer.isOnline())) {
            Player onlineCheck = Bukkit.getPlayerExact(args[0]);
            if (onlineCheck == null) {
                 reporter.sendMessage(ChatColor.RED + "Joueur '" + args[0] + "' introuvable.");
                 return true;
            }
             reportedPlayer = onlineCheck;
        }
        
        if (reportedPlayer.getUniqueId().equals(reporter.getUniqueId())) {
            reporter.sendMessage(ChatColor.RED + "Vous ne pouvez pas vous signaler vous-même.");
            return true;
        }
        String reason = Arrays.stream(args, 1, args.length).collect(Collectors.joining(" "));
        if (reason.trim().isEmpty()) {
            reporter.sendMessage(ChatColor.RED + "Veuillez spécifier une raison pour votre signalement.");
            return true;
        }
        if (reportManager == null) {
            getLogger().severe("ReportManager is null! Command '/report' cannot be executed.");
            sender.sendMessage(ChatColor.RED + "Erreur interne: Le manager de signalements n'est pas initialisé.");
            return true;
        }
        reportManager.addReport(reporter, reportedPlayer, reason);
        return true;
    }
    
    private boolean handleObserveCommand(CommandSender sender, String[] subArgs) {
        if (!sender.hasPermission("prometheusargus.staff.observe")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission."); return true;
        }
        if (subArgs.length < 1) { 
            sender.sendMessage(ChatColor.RED + "Usage: /pa observe <joueur>"); return true;
        }
        Player targetObserve = Bukkit.getPlayerExact(subArgs[0]);
        if (targetObserve == null) {
            sender.sendMessage(ChatColor.RED + "Joueur '" + subArgs[0] + "' non trouvé."); return true;
        }
        sender.sendMessage(ChatColor.GREEN + "Observation activée pour " + targetObserve.getName() + " (simulation).");
        getLogger().info("[OBSERVE] " + sender.getName() + " observe " + targetObserve.getName());
        return true;
    }

    private boolean handleTestCommand(CommandSender sender, String[] subArgs) {
        if (!sender.hasPermission("prometheusargus.staff.test")) {
           sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission."); return true;
       }
       if (subArgs.length < 2) { 
           sender.sendMessage(ChatColor.RED + "Usage: /pa test <joueur> <type>"); return true;
       }
       Player targetTest = Bukkit.getPlayerExact(subArgs[0]);
       if (targetTest == null) {
           sender.sendMessage(ChatColor.RED + "Joueur '" + subArgs[0] + "' non trouvé."); return true;
       }
       sender.sendMessage(ChatColor.YELLOW + "Test '" + subArgs[1] + "' sur " + targetTest.getName() + " (simulation).");
       getLogger().info("[TEST] " + sender.getName() + " teste " + targetTest.getName() + " avec " + subArgs[1]);
       return true;
    }
    
    private boolean handleBanCommand(CommandSender sender, String[] subArgs) { 
        if (!sender.hasPermission("prometheusargus.command.ban")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission."); return true;
        }
        if (subArgs.length < 3) { 
            sender.sendMessage(ChatColor.RED + "Usage: /pa ban <pseudo> <temps> <raison>");
            sendSingleHelpEntry(sender, "ban <joueur> <temps> <raison>", "Bannit un joueur.", "prometheusargus.command.ban", "&7Temps: &f<N><U> (s,m,h,d,w,mo,y) ou 'perm'/'0'.");
            return true;
        }
        String targetName = subArgs[0];
        String timeStr = subArgs[1];
        String reason = Arrays.stream(subArgs, 2, subArgs.length).collect(Collectors.joining(" "));
        Date expirationDate;
        try { expirationDate = parseBanDuration(timeStr); } catch (IllegalArgumentException e) { sender.sendMessage(ChatColor.RED + "Erreur de format de temps: " + e.getMessage()); return true; }
        String banSource = (sender instanceof Player) ? sender.getName() : "Console";
        String formattedExpiration = (expirationDate == null) ? "Jamais (Permanent)" : banExpirationFormat.format(expirationDate);
        String kickMessageForBannedPlayer = ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.manual_ban.kick_format", "&c&lVOUS AVEZ ÉTÉ BANNI PAR %source%!\n&r \n&7Raison: &f%reason%\n&7Expire: &e%expiration%").replace("%reason%", reason).replace("%expiration%", formattedExpiration).replace("%source%", banSource));
        Bukkit.getBanList(BanList.Type.NAME).addBan(targetName, reason, expirationDate, banSource);
        if (sanctionHistoryManager == null) {
            getLogger().severe("SanctionHistoryManager is null! Cannot log ban.");
        } else {
            sanctionHistoryManager.addSanction(targetName, "ManualBan", reason, banSource, -1);
        }
        Player targetOnlinePlayer = Bukkit.getPlayerExact(targetName);
        if (targetOnlinePlayer != null && targetOnlinePlayer.isOnline()) {
            targetOnlinePlayer.kickPlayer(kickMessageForBannedPlayer);
            try {
                targetOnlinePlayer.playSound(targetOnlinePlayer.getLocation(), Sound.valueOf("GHAST_SCREAM"), 1.0f, 0.8f);
            } catch (NoSuchFieldError e) { /* Ignored for 1.8.8 compatibility */ }
        }
        String durationDisplay = (expirationDate == null) ? "Permanente" : timeStr;
        String broadcastMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.manual_ban.broadcast_format", "&c[PrometheusBan] &e%target% &7a été banni par &e%source%&7.\n&7Raison: &f%reason%\n&7Durée: &f%duration%").replace("%target%", targetName).replace("%source%", banSource).replace("%reason%", reason).replace("%duration%", durationDisplay));
        Bukkit.broadcastMessage(broadcastMessage);
        Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("prometheusargus.alerts")).forEach(p -> {
            try { p.playSound(p.getLocation(), Sound.valueOf("ENDERDRAGON_GROWL"), 0.7f, 1.2f); } catch (NoSuchFieldError e) { /* Ignored for 1.8.8 compatibility */ }
        });
        sender.sendMessage(ChatColor.GREEN + targetName + " a été banni avec succès. Durée: " + durationDisplay + ", Raison: " + reason);
        getLogger().info(sender.getName() + " a banni " + targetName + ". Durée: " + durationDisplay + ", Raison: " + reason);
        return true;
    }

    private boolean handleUnbanCommand(CommandSender sender, String[] subArgs) { 
        if (!sender.hasPermission("prometheusargus.command.unban")) { sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission."); return true;}
        if (subArgs.length < 1) { sender.sendMessage(ChatColor.RED + "Usage: /pa unban <pseudo>"); sendSingleHelpEntry(sender, "unban <joueur>", "Débannit un joueur.", "prometheusargus.command.unban"); return true; }
        String targetName = subArgs[0];
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        if (!banList.isBanned(targetName)) { sender.sendMessage(ChatColor.RED + "Le joueur " + targetName + " n'est pas banni."); return true; }
        banList.pardon(targetName);
        String unbanSource = (sender instanceof Player) ? sender.getName() : "Console";
        if (sanctionHistoryManager == null) {
            getLogger().severe("SanctionHistoryManager is null! Cannot log unban.");
        } else {
            sanctionHistoryManager.addSanction(targetName, "ManualUnban", "Débanni par " + unbanSource, unbanSource, -1);
        }
        String broadcastMessage = ChatColor.translateAlternateColorCodes('&',getConfig().getString("messages.manual_unban.broadcast_format", "&a[PrometheusUnban] &e%target% &7a été débanni par &e%source%&7.").replace("%target%", targetName).replace("%source%", unbanSource));
        Bukkit.broadcastMessage(broadcastMessage);
        Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("prometheusargus.alerts")).forEach(p -> {
            try { p.playSound(p.getLocation(), Sound.valueOf("LEVEL_UP"), 1.0f, 1.5f); } catch (NoSuchFieldError e) { /* Ignored for 1.8.8 compatibility */ }
        });
        sender.sendMessage(ChatColor.GREEN + targetName + " a été débanni avec succès.");
        getLogger().info(unbanSource + " a débanni " + targetName + ".");
        return true;
    }

    private boolean handleKickCommand(CommandSender sender, String[] subArgs) { 
        if(!sender.hasPermission("prometheusargus.command.kick")) { sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission."); return true; }
        if (subArgs.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /pa kick <pseudo> <raison>"); sendSingleHelpEntry(sender, "kick <joueur> <raison>", "Expulse un joueur du serveur.", "prometheusargus.command.kick"); return true;}
        String targetName = subArgs[0];
        String reason = Arrays.stream(subArgs, 1, subArgs.length).collect(Collectors.joining(" "));
        Player targetOnlinePlayer = Bukkit.getPlayerExact(targetName);
        if (targetOnlinePlayer == null || !targetOnlinePlayer.isOnline()) { sender.sendMessage(ChatColor.RED + "Le joueur " + targetName + " n'est pas en ligne."); return true;}
        String kickSource = (sender instanceof Player) ? sender.getName() : "Console";
        String kickMessageForPlayer = ChatColor.translateAlternateColorCodes('&',getConfig().getString("messages.manual_kick.kick_format", "&cVous avez été expulsé par %source%!\n&7Raison: &f%reason%").replace("%source%", kickSource).replace("%reason%", reason));
        targetOnlinePlayer.getWorld().playEffect(targetOnlinePlayer.getLocation().add(0,1,0), Effect.SMOKE, 4);
        try { targetOnlinePlayer.playSound(targetOnlinePlayer.getLocation(), Sound.valueOf("GHAST_FIREBALL"), 1.0f, 1.0f); } catch (NoSuchFieldError e) { /* Ignored for 1.8.8 compatibility */ }
        Bukkit.getScheduler().runTaskLater(this, () -> { if (targetOnlinePlayer.isOnline()) targetOnlinePlayer.kickPlayer(kickMessageForPlayer);}, 5L);
        if (sanctionHistoryManager == null) {
            getLogger().severe("SanctionHistoryManager is null! Cannot log kick.");
        } else {
            sanctionHistoryManager.addSanction(targetName, "ManualKick", reason, kickSource, -1);
        }
        String broadcastMessage = ChatColor.translateAlternateColorCodes('&',getConfig().getString("messages.manual_kick.broadcast_format", "&e[PrometheusKick] &e%target% &7a été expulsé par &e%source% &7(Raison: %reason%&7).").replace("%target%", targetName).replace("%source%", kickSource).replace("%reason%", reason));
        Bukkit.broadcastMessage(broadcastMessage);
        Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("prometheusargus.alerts")).forEach(p -> {
            try { p.playSound(p.getLocation(), Sound.valueOf("ITEM_BREAK"), 1.0f, 1.0f); } catch (NoSuchFieldError e) { /* Ignored for 1.8.8 compatibility */ }
        });
        sender.sendMessage(ChatColor.GREEN + targetName + " a été expulsé. Raison: " + reason);
        getLogger().info(kickSource + " a expulsé " + targetName + ". Raison: " + reason);
        return true;
    }

    @SuppressWarnings("deprecation") 
    private boolean handleLookupCommand(CommandSender sender, String[] subArgs) { 
        if (!sender.hasPermission("prometheusargus.command.lookup")) { sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission."); return true; }
        if (subArgs.length < 1) { sender.sendMessage(ChatColor.RED + "Usage: /pa lookup <pseudo>"); sendSingleHelpEntry(sender, "lookup <joueur>", "Affiche les informations d'un joueur.", "prometheusargus.command.lookup"); return true; }
        String targetName = subArgs[0];
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        if ((!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline())) {
             Player onlineCheck = Bukkit.getPlayerExact(targetName);
            if (onlineCheck == null) { sender.sendMessage(ChatColor.RED + "Le joueur " + targetName + " n'a jamais été vu ou n'est pas en ligne."); return true;}
            offlineTarget = onlineCheck;
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8&m------------&r &6&lLookup: " + offlineTarget.getName() + " &8&m------------"));
        sender.sendMessage(ChatColor.GRAY + "UUID: " + ChatColor.WHITE + offlineTarget.getUniqueId().toString());
        sender.sendMessage(ChatColor.GRAY + "En ligne: " + (offlineTarget.isOnline() ? ChatColor.GREEN + "Oui" : ChatColor.RED + "Non"));
        if (offlineTarget.isOnline()) {
            Player onlineTarget = offlineTarget.getPlayer();
            sender.sendMessage(ChatColor.GRAY + "Monde: " + ChatColor.WHITE + onlineTarget.getWorld().getName());
            sender.sendMessage(ChatColor.GRAY + "Position: " + ChatColor.WHITE + String.format(Locale.US, "%.1f, %.1f, %.1f", onlineTarget.getLocation().getX(), onlineTarget.getLocation().getY(), onlineTarget.getLocation().getZ()));
            sender.sendMessage(ChatColor.GRAY + "Gamemode: " + ChatColor.WHITE + onlineTarget.getGameMode().toString());
            try { 
                Object entityPlayer = onlineTarget.getClass().getMethod("getHandle").invoke(onlineTarget);
                int ping = (int) entityPlayer.getClass().getField("ping").get(entityPlayer); 
                sender.sendMessage(ChatColor.GRAY + "Ping: " + ChatColor.WHITE + ping + "ms"); 
            } catch (Exception e) { /* Ignored */ }
        }
        sender.sendMessage(ChatColor.GRAY + "Première connexion: " + ChatColor.WHITE + banExpirationFormat.format(new Date(offlineTarget.getFirstPlayed())));
        if (offlineTarget.getLastPlayed() > 0 && offlineTarget.getFirstPlayed() != offlineTarget.getLastPlayed()) { sender.sendMessage(ChatColor.GRAY + "Dernière connexion: " + ChatColor.WHITE + banExpirationFormat.format(new Date(offlineTarget.getLastPlayed()))); }
        
        org.bukkit.BanEntry banEntryBukkit = Bukkit.getBanList(BanList.Type.NAME).getBanEntry(offlineTarget.getName());
        if (banEntryBukkit != null) { sender.sendMessage(ChatColor.RED + "Statut de Bannissement:"); sender.sendMessage(ChatColor.GRAY + "  Banni: " + ChatColor.DARK_RED + "OUI"); sender.sendMessage(ChatColor.GRAY + "  Par: " + ChatColor.WHITE + banEntryBukkit.getSource()); sender.sendMessage(ChatColor.GRAY + "  Raison: " + ChatColor.WHITE + banEntryBukkit.getReason()); sender.sendMessage(ChatColor.GRAY + "  Depuis le: " + ChatColor.WHITE + banExpirationFormat.format(banEntryBukkit.getCreated())); sender.sendMessage(ChatColor.GRAY + "  Expire: " + ChatColor.WHITE + (banEntryBukkit.getExpiration() == null ? "Jamais" : banExpirationFormat.format(banEntryBukkit.getExpiration())));} 
        else { sender.sendMessage(ChatColor.GREEN + "Statut de Bannissement: Non banni");}
        
        if (muteManager == null) {
             sender.sendMessage(ChatColor.YELLOW + "Statut de Silence: Manager non initialisé.");
        } else {
            MuteManager.MuteEntry muteEntry = muteManager.getMuteEntry(offlineTarget.getUniqueId());
            if (muteEntry != null) { sender.sendMessage(ChatColor.GOLD + "Statut de Silence:"); sender.sendMessage(ChatColor.GRAY + "  Réduit au silence: " + ChatColor.YELLOW + "OUI"); sender.sendMessage(ChatColor.GRAY + "  Par: " + ChatColor.WHITE + muteEntry.getSource()); sender.sendMessage(ChatColor.GRAY + "  Raison: " + ChatColor.WHITE + muteEntry.getReason()); sender.sendMessage(ChatColor.GRAY + "  Depuis le: " + ChatColor.WHITE + banExpirationFormat.format(muteEntry.getCreated())); sender.sendMessage(ChatColor.GRAY + "  Expire: " + ChatColor.WHITE + (muteEntry.getExpires() == null ? "Jamais" : banExpirationFormat.format(muteEntry.getExpires())));}
            else { sender.sendMessage(ChatColor.GREEN + "Statut de Silence: Non réduit au silence"); }
        }

        if (offlineTarget.isOnline()) { 
            if (playerDataManager == null) {
                sender.sendMessage(ChatColor.YELLOW + "Niveaux de Violation: Manager non initialisé.");
            } else {
                PlayerACData acData = playerDataManager.getPlayerData(offlineTarget.getPlayer()); 
                if (acData != null) { 
                    Map<String, Integer> vls = acData.getAllViolationLevels(); 
                    if (!vls.isEmpty() && vls.values().stream().anyMatch(v -> v > 0)) { 
                        sender.sendMessage(ChatColor.GOLD + "Niveaux de Violation Actifs:"); 
                        vls.entrySet().stream().filter(e -> e.getValue() > 0).forEach(e -> sender.sendMessage(ChatColor.GRAY + "  - " + e.getKey() + ": " + ChatColor.RED + e.getValue())); 
                    } else { sender.sendMessage(ChatColor.GREEN + "Aucun niveau de violation actif."); }
                }
            }
        }
        
        if (sanctionHistoryManager == null) {
            sender.sendMessage(ChatColor.BLUE + "Dernières Sanctions: Manager non initialisé.");
        } else {
            List<SanctionHistoryManager.SanctionEntry> playerHistory = sanctionHistoryManager.getSanctionsForPlayer(offlineTarget.getName(), 3, 1);
            if (!playerHistory.isEmpty()) { sender.sendMessage(ChatColor.BLUE + "Dernières Sanctions Enregistrées (max 3):"); for (SanctionHistoryManager.SanctionEntry entryItem : playerHistory) { sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7- &f" + entryItem.date + " &7[&e" + entryItem.type + "&7 par &e" + entryItem.executor + "&7] Raison: &f" + entryItem.reason + (entryItem.vl != -1 ? " &7(VL: &c" + entryItem.vl + "&7)" : "")));}}
            else { sender.sendMessage(ChatColor.GRAY + "Aucune sanction enregistrée pour ce joueur.");}
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8&m----------------------------------------------------"));
        return true;
    }

    private boolean handleHistoryCommand(CommandSender sender, String[] subArgs) {
        if (!sender.hasPermission("prometheusargus.command.history")) { sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission."); return true; }
        if (subArgs.length < 1) { sender.sendMessage(ChatColor.RED + "Usage: /pa history <pseudo> [page]"); sendSingleHelpEntry(sender, "history <joueur> [page]", "Affiche l'historique des sanctions d'un joueur.", "prometheusargus.command.history"); return true; }
        String targetName = subArgs[0];
        int page = 1;
        if (subArgs.length > 1) { try { page = Integer.parseInt(subArgs[1]); if (page < 1) page = 1; } catch (NumberFormatException e) { sender.sendMessage(ChatColor.RED + "Le numéro de page '" + subArgs[1] + "' est invalide."); return true;}}
        int itemsPerPage = 8;

        if (sanctionHistoryManager == null) {
            getLogger().severe("SanctionHistoryManager is null! Command '/pa history' cannot be executed.");
            sender.sendMessage(ChatColor.RED + "Erreur interne: Le manager d'historique n'est pas initialisé.");
            return true;
        }

        List<SanctionHistoryManager.SanctionEntry> historyEntries = sanctionHistoryManager.getSanctionsForPlayer(targetName, itemsPerPage, page);
        if (historyEntries.isEmpty()) { if (page == 1) { sender.sendMessage(ChatColor.GRAY + "Aucune sanction trouvée pour " + ChatColor.WHITE + targetName + ChatColor.GRAY + "."); } else { sender.sendMessage(ChatColor.GRAY + "Aucune sanction trouvée pour " + ChatColor.WHITE + targetName + ChatColor.GRAY + " à la page " + page + ".");} return true;}
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8&m------&r &6Historique de " + targetName + " (Page " + page + ") &8&m------"));
        for (SanctionHistoryManager.SanctionEntry entry : historyEntries) { sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&f" + entry.date + "&7] &e" + entry.type + " &7par &e" + entry.executor + (entry.vl != -1 ? " &7(VL: &c" + entry.vl + "&7)" : "") + " &7Raison: &f" + entry.reason));}
        
        List<SanctionHistoryManager.SanctionEntry> allPlayerSanctions = sanctionHistoryManager.getSanctionsForPlayer(targetName, Integer.MAX_VALUE, 1);
        int totalPlayerSanctions = allPlayerSanctions.size();
        int totalPages = (int) Math.ceil((double) totalPlayerSanctions / itemsPerPage);

        if (totalPages > 1) {
            List<BaseComponent> pageComponents = new ArrayList<>();
            pageComponents.add(new TextComponent(ChatColor.GRAY + "Pages: ")); 

            int maxButtons = 7;
            int startPageNum = Math.max(1, page - (maxButtons / 2));
            int endPageNum = Math.min(totalPages, startPageNum + maxButtons - 1);
            if (endPageNum - startPageNum + 1 < maxButtons && startPageNum > 1) {
                startPageNum = Math.max(1, endPageNum - maxButtons + 1);
            }

            if (startPageNum > 1) {
                TextComponent first = new TextComponent("[1] .. ");
                first.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
                first.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pa history " + targetName + " " + 1));
                first.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.YELLOW + "Aller à la page 1").create()));
                pageComponents.add(first);
            }

            for (int i = startPageNum; i <= endPageNum; i++) {
                TextComponent pageNumText = new TextComponent("[" + i + "] ");
                if (i == page) {
                    pageNumText.setColor(net.md_5.bungee.api.ChatColor.GOLD);
                } else {
                    pageNumText.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
                    pageNumText.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pa history " + targetName + " " + i));
                    pageNumText.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.YELLOW + "Aller à la page " + i).create()));
                }
                pageComponents.add(pageNumText);
            }

            if (endPageNum < totalPages) {
                TextComponent last = new TextComponent(" .. [" + totalPages + "]");
                last.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
                last.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pa history " + targetName + " " + totalPages));
                last.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.YELLOW + "Aller à la page " + totalPages).create()));
                pageComponents.add(last);
            }

            if (sender instanceof Player) {
                ((Player) sender).spigot().sendMessage(pageComponents.toArray(new BaseComponent[0]));
            } else {
                StringBuilder consolePages = new StringBuilder(ChatColor.GRAY + "Pages: ");
                for (int i = 1; i <= totalPages; i++) {
                    consolePages.append(page == i ? ChatColor.GOLD : ChatColor.YELLOW).append("[").append(i).append("] ");
                }
                sender.sendMessage(consolePages.toString().trim());
            }
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8&m----------------------------------------------------"));
        return true;
    }

    private boolean handleFreezeCommand(CommandSender sender, String[] subArgs) {
        if (!(sender instanceof Player)) { 
            sender.sendMessage("Cette commande est pour les joueurs (staff)."); return true;
        }
        Player staff = (Player) sender;
        if (!staff.hasPermission("prometheusargus.command.freeze")) {
            staff.sendMessage(ChatColor.RED + "Vous n'avez pas la permission."); return true;
        }
        if (subArgs.length < 1) {
            staff.sendMessage(ChatColor.RED + "Usage: /pa freeze <joueur>");
            sendSingleHelpEntry(staff, "freeze <joueur>", "Gèle/Dégèle un joueur.", "prometheusargus.command.freeze");
            return true;
        }
        Player target = Bukkit.getPlayerExact(subArgs[0]);
        if (target == null) {
            staff.sendMessage(ChatColor.RED + "Joueur '" + subArgs[0] + "' non trouvé."); return true;
        }
        if (target.hasPermission("prometheusargus.bypass.freeze")) { 
            staff.sendMessage(ChatColor.RED + target.getName() + " ne peut pas être gelé (bypass).");
            return true;
        }
        if (freezeManager == null) {
            getLogger().severe("FreezeManager is null! Command '/pa freeze' cannot be executed.");
            sender.sendMessage(ChatColor.RED + "Erreur interne: Le manager de freeze n'est pas initialisé.");
            return true;
        }
        if (freezeManager.isFrozen(target)) {
            freezeManager.unfreezePlayer(target, staff);
        } else {
            freezeManager.freezePlayer(target, staff);
        }
        return true;
    }

    @SuppressWarnings("deprecation") 
    private boolean handleMuteCommand(CommandSender sender, String[] subArgs) {
        if (!sender.hasPermission("prometheusargus.command.mute")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission."); return true;
        }
        if (subArgs.length < 3) { 
            sender.sendMessage(ChatColor.RED + "Usage: /pa mute <joueur> <temps> <raison>");
            sendSingleHelpEntry(sender, "mute <joueur> <temps> <raison>", "Réduit un joueur au silence.", "prometheusargus.command.mute", "&7Temps: &f<N><U> ou 'perm'");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(subArgs[0]);
         if ((!target.hasPlayedBefore() && !target.isOnline())) {
            Player onlineCheck = Bukkit.getPlayerExact(subArgs[0]);
            if (onlineCheck == null) {
                sender.sendMessage(ChatColor.RED + "Joueur '" + subArgs[0] + "' introuvable."); return true;
            }
            target = onlineCheck;
        }
        String timeStr = subArgs[1];
        String reason = Arrays.stream(subArgs, 2, subArgs.length).collect(Collectors.joining(" "));
        
        Date expirationDate;
        try {
            expirationDate = parseBanDuration(timeStr); 
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Format de temps invalide: " + e.getMessage()); return true;
        }

        String muterName = (sender instanceof Player) ? sender.getName() : "Console";
        if (muteManager == null) {
            getLogger().severe("MuteManager is null! Command '/pa mute' cannot be executed.");
            sender.sendMessage(ChatColor.RED + "Erreur interne: Le manager de mute n'est pas initialisé.");
            return true;
        }
        muteManager.mutePlayer(target.getUniqueId(), target.getName(), reason, muterName, expirationDate);
        
        String durationDisplay = (expirationDate == null) ? "Permanent" : "jusqu'au " + banExpirationFormat.format(expirationDate);
        sender.sendMessage(ChatColor.GREEN + target.getName() + " a été réduit au silence " + durationDisplay + ". Raison: " + reason);
        
        String broadcastMessage = ChatColor.translateAlternateColorCodes('&',
            getConfig().getString("messages.mute.broadcast", "&e[PrometheusMute] &c%target% &7a été réduit au silence par &e%source% &7(%duration%). Raison: %reason%")
            .replace("%target%", target.getName())
            .replace("%source%", muterName)
            .replace("%duration%", (expirationDate == null) ? "Permanent" : timeStr)
            .replace("%reason%", reason)
        );
        Bukkit.broadcastMessage(broadcastMessage);
        return true;
    }
    
    @SuppressWarnings("deprecation") 
    private boolean handleUnmuteCommand(CommandSender sender, String[] subArgs) {
        if (!sender.hasPermission("prometheusargus.command.unmute")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission."); return true;
        }
        if (subArgs.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /pa unmute <joueur>");
            sendSingleHelpEntry(sender, "unmute <joueur>", "Lève le silence d'un joueur.", "prometheusargus.command.unmute");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(subArgs[0]);
         if ((!target.hasPlayedBefore() && !target.isOnline())) { 
            Player onlineCheck = Bukkit.getPlayerExact(subArgs[0]);
            boolean isMuted = muteManager != null && muteManager.getMuteEntry(target.getUniqueId()) != null;
            if (!isMuted && onlineCheck == null) {
                 sender.sendMessage(ChatColor.RED + "Joueur '" + subArgs[0] + "' introuvable ou non réduit au silence."); return true;
            }
            if (onlineCheck != null) target = onlineCheck;
            else if (!isMuted) {
                 sender.sendMessage(ChatColor.RED + "Joueur '" + subArgs[0] + "' introuvable ou non réduit au silence."); return true;
            }
        }

        String unmuterName = (sender instanceof Player) ? sender.getName() : "Console";
        if (muteManager == null) {
            getLogger().severe("MuteManager is null! Command '/pa unmute' cannot be executed.");
            sender.sendMessage(ChatColor.RED + "Erreur interne: Le manager de mute n'est pas initialisé.");
            return true;
        }
        if (muteManager.unmutePlayer(target.getUniqueId(), unmuterName)) {
            sender.sendMessage(ChatColor.GREEN + target.getName() + " n'est plus réduit au silence.");
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("messages.unmute.broadcast", "&a[PrometheusMute] Le silence de &e%target% &a été levé par &e%source%.")
                .replace("%target%", target.getName())
                .replace("%source%", unmuterName)
            ));
        } else {
            sender.sendMessage(ChatColor.RED + target.getName() + " n'est pas réduit au silence ou est introuvable.");
        }
        return true;
    }

    @SuppressWarnings("deprecation") 
    private boolean handleNotesCommand(CommandSender sender, String[] subArgs) {
        if (subArgs.length < 2) { 
            sendHelpMessage(sender); 
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(subArgs[0]);
        if ((!target.hasPlayedBefore() && !target.isOnline())) {
            Player onlineCheck = Bukkit.getPlayerExact(subArgs[0]);
             if (onlineCheck == null) {
                sender.sendMessage(ChatColor.RED + "Joueur '" + subArgs[0] + "' introuvable.");
                return true;
            }
            target = onlineCheck;
        }
        
        String action = subArgs[1].toLowerCase();
        String noteSourceName = (sender instanceof Player) ? sender.getName() : "Console";

        if (playerNotesManager == null) {
            getLogger().severe("PlayerNotesManager is null! Command '/pa notes' cannot be executed.");
            sender.sendMessage(ChatColor.RED + "Erreur interne: Le manager de notes n'est pas initialisé.");
            return true;
        }

        switch (action) {
            case "add":
                if (!sender.hasPermission("prometheusargus.notes.add")) {
                    sender.sendMessage(ChatColor.RED + "Permission refusée."); return true;
                }
                if (subArgs.length < 3) { 
                    sender.sendMessage(ChatColor.RED + "Usage: /pa notes " + target.getName() + " add <texte de la note>"); return true;
                }
                String noteText = Arrays.stream(subArgs, 2, subArgs.length).collect(Collectors.joining(" "));
                playerNotesManager.addNote(target.getUniqueId(), target.getName(), noteSourceName, noteText);
                sender.sendMessage(ChatColor.GREEN + "Note ajoutée pour " + target.getName() + ".");
                return true;

            case "view":
                if (!sender.hasPermission("prometheusargus.notes.view")) {
                    sender.sendMessage(ChatColor.RED + "Permission refusée."); return true;
                }
                int page = 1;
                if (subArgs.length > 2) { 
                    try { page = Integer.parseInt(subArgs[2]); } catch (NumberFormatException ignored) {}
                    if(page < 1) page = 1;
                }
                List<String> notes = playerNotesManager.getNotesForPlayer(target.getUniqueId(), 5, page); 
                if (notes.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Aucune note trouvée pour " + target.getName() + (page > 1 ? " à la page " + page : "") + ".");
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6--- Notes pour " + target.getName() + " (Page " + page + ") ---"));
                    notes.forEach(sender::sendMessage); 
                    
                    int totalNotes = playerNotesManager.getTotalNotesForPlayer(target.getUniqueId());
                    int totalPages = (int) Math.ceil((double) totalNotes / 5);
                    if (page < totalPages) {
                        sender.sendMessage(ChatColor.GRAY + "Voir page suivante: /pa notes " + target.getName() + " view " + (page + 1));
                    }
                }
                return true;
            
            case "remove":
            case "delete":
                if (!sender.hasPermission("prometheusargus.notes.remove")) {
                    sender.sendMessage(ChatColor.RED + "Permission refusée."); return true;
                }
                if (subArgs.length < 3) { 
                     sender.sendMessage(ChatColor.RED + "Usage: /pa notes " + target.getName() + " remove <ID_de_la_note>");
                     sender.sendMessage(ChatColor.GRAY + "(Utilisez '/pa notes " + target.getName() + " view' pour voir les IDs)");
                     return true;
                }
                try {
                    int noteId = Integer.parseInt(subArgs[2].replace("#", ""));
                    String removedNote = playerNotesManager.removeNoteByIndex(target.getUniqueId(), noteId, noteSourceName);
                    if (removedNote != null) {
                        sender.sendMessage(ChatColor.GREEN + "Note #" + noteId + " pour " + target.getName() + " supprimée.");
                        if (globalDebugMode) getLogger().info("Note removed: " + removedNote);
                    } else {
                        sender.sendMessage(ChatColor.RED + "ID de note invalide ou note non trouvée.");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "L'ID de la note doit être un nombre.");
                }
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Action de note inconnue: " + action + ". Utilisez add, view, ou remove.");
                return true;
        }
    }
    
    private boolean handleStaffChatCommand(CommandSender sender, String[] subArgs) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Le staff chat est réservé aux joueurs.");
            return true;
        }
        Player player = (Player) sender;

        if (staffChatManager == null) {
            getLogger().severe("StaffChatManager is null! Command '/pa sc' cannot be executed.");
            player.sendMessage(ChatColor.RED + "Erreur interne: Le manager de staff chat n'est pas initialisé.");
            return true;
        }

        if (subArgs.length == 0) {
            staffChatManager.toggleStaffChatMode(player);
        } else {
            String message = String.join(" ", subArgs);
            staffChatManager.sendStaffChatMessage(player, message);
        }
        return true;
    }

    private boolean handleModCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande ne peut être utilisée que par un joueur.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("prometheusargus.modmode.use")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser le mode modérateur.");
            return true;
        }

        if (modModeManager.isInModMode(player)) {
            modModeManager.disableModMode(player);
            player.sendMessage(ChatColor.RED + "Mode modérateur désactivé.");
        } else {
            modModeManager.enableModMode(player);
            player.sendMessage(ChatColor.GREEN + "Mode modérateur activé.");
        }

        return true;
    }

    public void flagPlayer(Player player, PlayerACData acDataInput, String checkNameInput, int vlIncrement, String debugInfo) {
        if (playerDataManager == null || sanctionHistoryManager == null) {
            getLogger().severe("Un manager critique est null! Flag non traité pour " + player.getName() + " Check: " + checkNameInput);
            return;
        }

        PlayerACData acData = acDataInput;
        final String checkName = checkNameInput.toLowerCase();

        if (acData == null) { 
            if (globalDebugMode) getLogger().warning("[Prometheus DEBUG] flagPlayer called with null PlayerACData for " + player.getName() + " on check " + checkName);
            return; 
        }
        
        if (player.hasPermission("prometheusargus.bypass") || 
            player.hasPermission("prometheusargus.bypass." + checkName) ||
            (checkName.startsWith("flya") && player.hasPermission("prometheusargus.bypass.flya")) || 
            (checkName.startsWith("flyb") && player.hasPermission("prometheusargus.bypass.flybglide")) ||
            (checkName.startsWith("speed") && player.hasPermission("prometheusargus.bypass.speed")) ||
            (checkName.equals("knockbacka") && player.hasPermission("prometheusargus.bypass.knockbacka")) ) {
            if (globalDebugMode) getLogger().info("[Prometheus DEBUG] Player " + player.getName() + " bypassed check " + checkName + " due to permission.");
            return;
        }
        
        acData.incrementViolationLevel(checkName, vlIncrement);
        final int currentSpecificVL = acData.getViolationLevel(checkName);
        int combinedFlyAVL = 0;
        if (checkName.startsWith("flya_")) {
            combinedFlyAVL += acData.getViolationLevel("flya_vertical");
            combinedFlyAVL += acData.getViolationLevel("flya_hover");
            combinedFlyAVL += acData.getViolationLevel("flya_invalidground");
        }

        if (globalDebugMode) {
            getLogger().info("[Prometheus DEBUG] Player " + player.getName() + " VL for " + checkName + " (" + checkNameInput + ") is now " + currentSpecificVL + (checkName.startsWith("flya_") ? " (Combined FlyA VL: " + combinedFlyAVL + ")" : ""));
        }

        String alertPrefix = "&8[&6PA-ALERT&8] ";
        ChatColor nameColor = ChatColor.GOLD;
        ChatColor checkColorStyle = ChatColor.DARK_RED;
        ChatColor vlColor = ChatColor.GOLD;
        Sound alertSoundStaff = null; 
        try { alertSoundStaff = Sound.valueOf("NOTE_PLING"); } catch (IllegalArgumentException e) { /*ignore*/ }
        float soundPitch = 1.0f;
        int vlForAlertStyle = checkName.startsWith("flya_") ? combinedFlyAVL : currentSpecificVL;

        if (checkName.equals("reacha")) {
            if (vlForAlertStyle >= reachA_VlAlertHigh) { alertPrefix = "&8[&4&lMAX&8] "; nameColor = ChatColor.RED; vlColor = ChatColor.DARK_RED; checkColorStyle = ChatColor.DARK_RED; soundPitch = 0.5f;}
            else if (vlForAlertStyle >= reachA_VlAlertMedium) { alertPrefix = "&8[&c&lMED&8] "; nameColor = ChatColor.RED; vlColor = ChatColor.RED; checkColorStyle = ChatColor.RED; soundPitch = 1.0f;}
            else if (vlForAlertStyle >= reachA_VlAlertLow) { alertPrefix = "&8[&eLOW&8] "; nameColor = ChatColor.YELLOW; vlColor = ChatColor.YELLOW; checkColorStyle = ChatColor.YELLOW; soundPitch = 1.5f;}
            else { alertPrefix = "&8[&7LOG&8] "; nameColor = ChatColor.GRAY; vlColor = ChatColor.GRAY; alertSoundStaff = null;}
        } else if (checkName.equals("nofallcustom")) { 
            if (vlForAlertStyle >= nofallA_VlAlertMedium) { alertPrefix = "&8[&c&lMED&8] "; nameColor = ChatColor.RED; vlColor = ChatColor.RED; checkColorStyle = ChatColor.RED; soundPitch = 0.8f;}
            else if (vlForAlertStyle >= nofallA_VlAlertLow) { alertPrefix = "&8[&eLOW&8] "; nameColor = ChatColor.YELLOW; vlColor = ChatColor.YELLOW; checkColorStyle = ChatColor.YELLOW; soundPitch = 1.2f;}
            else { alertPrefix = "&8[&7LOG&8] "; nameColor = ChatColor.GRAY; vlColor = ChatColor.GRAY; alertSoundStaff = null;}
        } else if (checkName.startsWith("flya_")) {
            if (vlForAlertStyle >= flyA_VlAlertMedium) { alertPrefix = "&8[&c&lFLY-MED&8] "; nameColor = ChatColor.RED; vlColor = ChatColor.RED; checkColorStyle = ChatColor.RED; soundPitch = 0.7f; }
            else if (vlForAlertStyle >= flyA_VlAlertLow) { alertPrefix = "&8[&eFLY-LOW&8] "; nameColor = ChatColor.YELLOW; vlColor = ChatColor.YELLOW; checkColorStyle = ChatColor.YELLOW; soundPitch = 1.1f; }
            else { alertPrefix = "&8[&7FLY-LOG&8] "; nameColor = ChatColor.GRAY; vlColor = ChatColor.GRAY; alertSoundStaff = null; }
        } else if (checkName.equals("flyb_glide")) {
             if (vlForAlertStyle >= flyBGlide_VlAlertMedium) { alertPrefix = "&8[&c&lGLIDE-MED&8] "; nameColor = ChatColor.RED; vlColor = ChatColor.RED; checkColorStyle = ChatColor.RED; soundPitch = 0.75f; }
             else if (vlForAlertStyle >= flyBGlide_VlAlertLow) { alertPrefix = "&8[&eGLIDE-LOW&8] "; nameColor = ChatColor.YELLOW; vlColor = ChatColor.YELLOW; checkColorStyle = ChatColor.YELLOW; soundPitch = 1.15f; }
             else { alertPrefix = "&8[&7GLIDE-LOG&8] "; nameColor = ChatColor.GRAY; vlColor = ChatColor.GRAY; alertSoundStaff = null; }
        } else if (checkName.equals("knockbacka")) {
            if (vlForAlertStyle >= knockbackA_VlAlertMedium) { alertPrefix = "&8[&4&lKB-MED&8] "; nameColor = ChatColor.DARK_RED; vlColor = ChatColor.DARK_RED; checkColorStyle = ChatColor.DARK_RED; soundPitch = 0.6f; }
            else if (vlForAlertStyle >= knockbackA_VlAlertLow) { alertPrefix = "&8[&cKB-LOW&8] "; nameColor = ChatColor.RED; vlColor = ChatColor.RED; checkColorStyle = ChatColor.RED; soundPitch = 1.0f; }
            else { alertPrefix = "&8[&7KB-LOG&8] "; nameColor = ChatColor.GRAY; vlColor = ChatColor.GRAY; alertSoundStaff = null; }
        } else if (checkName.equals("speeda")) {
            if (vlForAlertStyle >= speedA_VlAlertHigh) { alertPrefix = "&8[&4SPD-A-MAX&8] "; nameColor = ChatColor.RED; vlColor = ChatColor.DARK_RED; checkColorStyle = ChatColor.DARK_RED; soundPitch = 0.5f;}
            else if (vlForAlertStyle >= speedA_VlAlertMedium) { alertPrefix = "&8[&cSPD-A-MED&8] "; nameColor = ChatColor.RED; vlColor = ChatColor.RED; checkColorStyle = ChatColor.RED; soundPitch = 1.0f;}
            else if (vlForAlertStyle >= speedA_VlAlertLow) { alertPrefix = "&8[&eSPD-A-LOW&8] "; nameColor = ChatColor.YELLOW; vlColor = ChatColor.YELLOW; checkColorStyle = ChatColor.YELLOW; soundPitch = 1.5f;}
            else { alertPrefix = "&8[&7SPD-A-LOG&8] "; nameColor = ChatColor.GRAY; vlColor = ChatColor.GRAY; alertSoundStaff = null;}
        } else if (checkName.equals("speedb_jump")) {
            if (vlForAlertStyle >= speedB_VlAlertHigh) { alertPrefix = "&8[&4JUMP-MAX&8] "; nameColor = ChatColor.RED; vlColor = ChatColor.DARK_RED; checkColorStyle = ChatColor.DARK_RED; soundPitch = 0.6f;}
            else if (vlForAlertStyle >= speedB_VlAlertMedium) { alertPrefix = "&8[&cJUMP-MED&8] "; nameColor = ChatColor.RED; vlColor = ChatColor.RED; checkColorStyle = ChatColor.RED; soundPitch = 0.8f;}
            else if (vlForAlertStyle >= speedB_VlAlertLow) { alertPrefix = "&8[&eJUMP-LOW&8] "; nameColor = ChatColor.YELLOW; vlColor = ChatColor.YELLOW; checkColorStyle = ChatColor.YELLOW; soundPitch = 1.2f;}
            else { alertPrefix = "&8[&7JUMP-LOG&8] "; nameColor = ChatColor.GRAY; vlColor = ChatColor.GRAY; alertSoundStaff = null;}
        } else if (checkName.equals("speedc_packet")) {
             if (vlForAlertStyle >= speedC_VlAlertHigh) { alertPrefix = "&8[&4PKT-MAX&8] "; nameColor = ChatColor.RED; vlColor = ChatColor.DARK_RED; checkColorStyle = ChatColor.DARK_RED; soundPitch = 0.6f;}
            else if (vlForAlertStyle >= speedC_VlAlertMedium) { alertPrefix = "&8[&cPKT-MED&8] "; nameColor = ChatColor.RED; vlColor = ChatColor.RED; checkColorStyle = ChatColor.RED; soundPitch = 0.7f;}
            else if (vlForAlertStyle >= speedC_VlAlertLow) { alertPrefix = "&8[&ePKT-LOW&8] "; nameColor = ChatColor.YELLOW; vlColor = ChatColor.YELLOW; checkColorStyle = ChatColor.YELLOW; soundPitch = 1.1f;}
            else { alertPrefix = "&8[&7PKT-LOG&8] "; nameColor = ChatColor.GRAY; vlColor = ChatColor.GRAY; alertSoundStaff = null;}
        } else if (checkName.equals("speedd_ground")) {
            if (vlForAlertStyle >= speedD_VlAlertHigh) { alertPrefix = "&8[&4GROUND-MAX&8] "; nameColor = ChatColor.RED; vlColor = ChatColor.DARK_RED; checkColorStyle = ChatColor.DARK_RED; soundPitch = 0.65f;}
            else if (vlForAlertStyle >= speedD_VlAlertMedium) { alertPrefix = "&8[&cGROUND-MED&8] "; nameColor = ChatColor.RED; vlColor = ChatColor.RED; checkColorStyle = ChatColor.RED; soundPitch = 0.75f;}
            else if (vlForAlertStyle >= speedD_VlAlertLow) { alertPrefix = "&8[&eGROUND-LOW&8] "; nameColor = ChatColor.YELLOW; vlColor = ChatColor.YELLOW; checkColorStyle = ChatColor.YELLOW; soundPitch = 1.15f;}
            else { alertPrefix = "&8[&7GROUND-LOG&8] "; nameColor = ChatColor.GRAY; vlColor = ChatColor.GRAY; alertSoundStaff = null;}
        }

        TextComponent mainMessage = new TextComponent(ChatColor.translateAlternateColorCodes('&', alertPrefix));
        TextComponent playerNameComponent = new TextComponent(player.getName());
        playerNameComponent.setColor(nameColor.asBungee());
        playerNameComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.YELLOW + "Options pour " + player.getName()).create()));
        playerNameComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/pa lookup " + player.getName()));
        mainMessage.addExtra(playerNameComponent);
        mainMessage.addExtra(new TextComponent(ChatColor.DARK_GRAY + " a échoué "));
        TextComponent checkNameText = new TextComponent(checkNameInput);
        checkNameText.setColor(checkColorStyle.asBungee());
        checkNameText.setBold(true);
        mainMessage.addExtra(checkNameText);
        mainMessage.addExtra(new TextComponent(ChatColor.DARK_GRAY + " (VL: "));
        TextComponent vlText = new TextComponent(String.valueOf(currentSpecificVL));
        vlText.setColor(vlColor.asBungee());
        mainMessage.addExtra(vlText);
        if (checkName.startsWith("flya_")) {
            mainMessage.addExtra(new TextComponent(ChatColor.DARK_GRAY + ", FlyVL: "));
            TextComponent combinedFlyVLText = new TextComponent(String.valueOf(combinedFlyAVL));
            combinedFlyVLText.setColor(vlColor.asBungee());
            mainMessage.addExtra(combinedFlyVLText);
        }
        mainMessage.addExtra(new TextComponent(ChatColor.DARK_GRAY + ") "));
        TextComponent debugInfoText = new TextComponent("[" + debugInfo + "]");
        debugInfoText.setColor(net.md_5.bungee.api.ChatColor.DARK_GRAY);
        mainMessage.addExtra(debugInfoText);

        TextComponent tpButton = new TextComponent(" [TP]");
        tpButton.setColor(net.md_5.bungee.api.ChatColor.AQUA);
        tpButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.AQUA + "TP à " + player.getName()).create()));
        tpButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + player.getName()));
        mainMessage.addExtra(tpButton);

        TextComponent freezeButton = new TextComponent(" [FREEZE]");
        freezeButton.setColor(net.md_5.bungee.api.ChatColor.BLUE);
        freezeButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.BLUE + "Geler/Dégeler " + player.getName()).create()));
        freezeButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pa freeze " + player.getName()));
        mainMessage.addExtra(freezeButton);

        getLogger().warning(ChatColor.stripColor(mainMessage.toLegacyText()));

        final Sound finalAlertSoundStaff = alertSoundStaff; 
        final float finalSoundPitch = soundPitch; 
        for (Player onlineStaff : Bukkit.getOnlinePlayers()) {
             if (onlineStaff.hasPermission("prometheusargus.alerts")) {
                onlineStaff.spigot().sendMessage(mainMessage);
                if (finalAlertSoundStaff != null) {
                    try { onlineStaff.playSound(onlineStaff.getLocation(), finalAlertSoundStaff, 1.0f, finalSoundPitch); } 
                    catch (Exception ex) { if (globalDebugMode) getLogger().warning("Error playing alert sound: " + ex.getMessage()); }
                }
            }
        }
        
        String banReasonForThisFlag = null;
        boolean shouldBanPlayer = false;
        final String finalCheckCategoryForBanReset;

        if (checkName.equals("reacha") && currentSpecificVL >= reachA_VlBanThreshold) {
            shouldBanPlayer = true; banReasonForThisFlag = reachA_BanReason; finalCheckCategoryForBanReset = checkName;
        } else if (checkName.equals("nofallcustom") && currentSpecificVL >= nofallA_VlBanThreshold) {
            shouldBanPlayer = true; banReasonForThisFlag = nofallA_BanReason; finalCheckCategoryForBanReset = checkName;
        } else if (checkName.startsWith("flya_") && combinedFlyAVL >= flyA_VlBanThreshold) {
            shouldBanPlayer = true; banReasonForThisFlag = flyA_BanReason; finalCheckCategoryForBanReset = "flya_";
        } else if (checkName.equals("flyb_glide") && currentSpecificVL >= flyBGlide_VlBanThreshold) {
            shouldBanPlayer = true; banReasonForThisFlag = flyBGlide_BanReason; finalCheckCategoryForBanReset = checkName;
        } else if (checkName.equals("knockbacka") && currentSpecificVL >= knockbackA_VlBanThreshold) {
            shouldBanPlayer = true; banReasonForThisFlag = knockbackA_BanReason; finalCheckCategoryForBanReset = checkName;
        }
        else if (checkName.equals("speeda") && currentSpecificVL >= speedA_VlBanThresholdConfig) {
            shouldBanPlayer = true; banReasonForThisFlag = speedA_BanReasonConfig; finalCheckCategoryForBanReset = checkName;
        } else if (checkName.equals("speedb_jump") && currentSpecificVL >= speedB_VlBanThresholdConfig) {
            shouldBanPlayer = true; banReasonForThisFlag = speedB_BanReasonConfig; finalCheckCategoryForBanReset = checkName;
        } else if (checkName.equals("speedc_packet") && getConfig().getBoolean("checks.speed_c_packet.enabled", false) && currentSpecificVL >= speedC_VlBanThresholdConfig) {
            shouldBanPlayer = true; banReasonForThisFlag = speedC_BanReasonConfig; finalCheckCategoryForBanReset = checkName;
        } else if (checkName.equals("speedd_ground") && currentSpecificVL >= speedD_VlBanThresholdConfig) {
            shouldBanPlayer = true; banReasonForThisFlag = speedD_BanReasonConfig; finalCheckCategoryForBanReset = checkName;
        }
         else {
            finalCheckCategoryForBanReset = checkName;
        }

        if (shouldBanPlayer && banReasonForThisFlag != null) {
            final String finalBanReason = ChatColor.translateAlternateColorCodes('&', banReasonForThisFlag + " (Check: " + checkNameInput + " | VL: " + (checkName.startsWith("flya_") ? combinedFlyAVL : currentSpecificVL) + ")");
            final String playerNameToBan = player.getName();
            final UUID playerUUIDToReset = player.getUniqueId(); 
            final int finalVLForLog = checkName.startsWith("flya_") ? combinedFlyAVL : currentSpecificVL;
            final String finalCheckNameForHistoryDisplay = checkNameInput;

            if (sanctionHistoryManager != null) {
                sanctionHistoryManager.addSanction(playerNameToBan, "AutoBan:" + finalCheckNameForHistoryDisplay, finalBanReason, "PrometheusArgus", finalVLForLog);
            } else {
                getLogger().severe("SanctionHistoryManager is null! AutoBan not logged for " + playerNameToBan);
            }


            Bukkit.getScheduler().runTask(this, () -> {
                Player playerToKick = Bukkit.getPlayerExact(playerNameToBan);
                String banSourceName = "PrometheusArgus"; 
                Bukkit.getBanList(BanList.Type.NAME).addBan(playerNameToBan, finalBanReason, null, banSourceName); 
                
                String publicBanMessage = ChatColor.translateAlternateColorCodes('&',
                    getConfig().getString("messages.auto_ban.broadcast_format", 
                        "&8&m-----------------------------------------------------\n" +
                        "&c&lPROMETHEUS ARGUS A FRAPPÉ !\n" +
                        "&r \n" +
                        "&e%target% &7a été banni pour tricherie.\n" +
                        "&7(Check: &c%check%&7, Violations: &c%vl%&7)\n" +
                        "&r \n" +
                        "&7Que cela serve de leçon à tous les malandrins !\n" +
                        "&8&m-----------------------------------------------------")
                        .replace("%target%", playerNameToBan)
                        .replace("%check%", finalCheckNameForHistoryDisplay)
                        .replace("%vl%", String.valueOf(finalVLForLog))
                );
                Bukkit.broadcastMessage(publicBanMessage);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    try { p.playSound(p.getLocation(), Sound.valueOf("WITHER_SPAWN"), 1.0f, 0.8f); } 
                    catch (Exception ex) { if(globalDebugMode) getLogger().warning("Impossible de jouer le son du Wither pour le ban auto."); }
                }
                if (playerToKick != null && playerToKick.isOnline()) {
                    Location banLocation = playerToKick.getLocation(); 
                    Firework fw = (Firework) banLocation.getWorld().spawnEntity(banLocation.clone().add(0,1,0), EntityType.FIREWORK); 
                    FireworkMeta fwm = fw.getFireworkMeta(); 
                    fwm.setPower(1); 
                    Color randomColor1 = Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256)); 
                    Color randomColor2 = Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256)); 
                    fwm.addEffect(FireworkEffect.builder().with(org.bukkit.FireworkEffect.Type.BALL_LARGE).withColor(randomColor1, Color.BLACK).withFade(randomColor2).withFlicker().withTrail().build()); 
                    fw.setFireworkMeta(fwm); 
                    Bukkit.getScheduler().runTaskLater(this, fw::detonate, 2L);
                    
                    String autoKickMsg = ChatColor.translateAlternateColorCodes('&',
                        getConfig().getString("messages.auto_ban.kick_format",
                        "&c&lPROMETHEUS ARGUS VOUS A BANNI !\n" +
                        "&r \n" +
                        "&7Raison: &f%reason%\n" +
                        "&7Check: &c%check% &7(VL: &c%vl%&7)\n"+
                        "&7Expire: &eJamais (Permanent)")
                        .replace("%reason%", finalBanReason)
                        .replace("%check%", finalCheckNameForHistoryDisplay)
                        .replace("%vl%", String.valueOf(finalVLForLog))
                    );
                    playerToKick.kickPlayer(autoKickMsg);
                }
                
                getLogger().info("[Prometheus BAN] " + playerNameToBan + " a été banni automatiquement. " + ChatColor.stripColor(finalBanReason));
                
                if (playerDataManager != null) {
                    PlayerACData dataToReset = playerDataManager.getPlayerData(playerUUIDToReset); 
                    if (dataToReset != null) {
                        if (finalCheckCategoryForBanReset.equals("flya_")) {
                            dataToReset.resetViolationLevel("flya_vertical");
                            dataToReset.resetViolationLevel("flya_hover");
                            dataToReset.resetViolationLevel("flya_invalidground");
                        } else {
                            dataToReset.resetViolationLevel(finalCheckCategoryForBanReset);
                        }
                    } else {
                        if (globalDebugMode) getLogger().info("[Prometheus DEBUG] PlayerACData for " + playerNameToBan + " (UUID: " + playerUUIDToReset + ") was null during VL reset post-ban. Likely disconnected.");
                    }
                } else {
                     getLogger().severe("PlayerDataManager is null! Cannot reset VL for " + playerNameToBan + " post-ban.");
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        initPlayerData(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (playerDataManager != null) {
             if (globalDebugMode) getLogger().info("[Prometheus DEBUG] Player " + event.getPlayer().getName() + " quit. (ACData would be removed here if method exists)");
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (playerDataManager == null) return;
        PlayerACData acData = playerDataManager.getPlayerData(player);
        if (acData != null) {
            acData.recordTeleport();
            if (isGlobalDebugModeEnabled()) {
                getLogger().info("[DEBUG TP] Recorded teleport for " + player.getName() + " to " + event.getTo().toString());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerVelocityChange(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
         if (playerDataManager == null) return;
        PlayerACData acData = playerDataManager.getPlayerData(player);
        if (acData != null) {
            if (event.getVelocity().lengthSquared() > 0.001) { 
                acData.recordVelocityApplication();
                 if (isGlobalDebugModeEnabled()) {
                    getLogger().info("[DEBUG VELOCITY] Recorded velocity for " + player.getName() + " : " + event.getVelocity().toString());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player playerLoggingIn = event.getPlayer();
        org.bukkit.BanEntry banEntryBukkit = Bukkit.getBanList(BanList.Type.NAME).getBanEntry(playerLoggingIn.getName());
        org.bukkit.BanEntry banEntryToUse = banEntryBukkit; 

        if (banEntryToUse != null) {
            String source = banEntryToUse.getSource() != null ? banEntryToUse.getSource() : "Source inconnue";
            if (source.equalsIgnoreCase("CONSOLE")) source = "La Console Impitoyable";
            else if (source.equalsIgnoreCase("PrometheusArgus")) source = "Prometheus Argus"; 

            String reason = banEntryToUse.getReason() != null ? banEntryToUse.getReason() : "Vous êtes banni de ce serveur.";
            Date expiration = banEntryToUse.getExpiration();
            String expirationStr = (expiration == null) ? "Jamais (Permanent)" : banExpirationFormat.format(expiration); 
            
            String kickMessage = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("messages.login_ban_kick_format",
                "&c&lACCÈS REFUSÉ - VOUS AVEZ ÉTÉ BANNI !\n" +
                "&r \n" +
                "&7Pseudo: &e%player_name%\n" +
                "&7Raison: &f%reason%\n" +
                "&7Banni par: &e%source%\n" +
                "&7Expire le: &e%expiration%\n" +
                "&r \n" +
                "&6Si vous pensez que c'est une erreur, contactez un administrateur.")
                .replace("%player_name%", playerLoggingIn.getName())
                .replace("%reason%", reason)
                .replace("%source%", source)
                .replace("%expiration%", expirationStr)
            );
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kickMessage);
            if (globalDebugMode) getLogger().info("[Prometheus LOGIN] Joueur banni " + playerLoggingIn.getName() + " a tenté de se connecter. Kick avec message personnalisé.");
        }
    }

    private void saveDefaultConfig(String fileName) {
        File configFile = new File(getDataFolder(), fileName);
        if (!configFile.exists()) {
            saveResource(fileName, false);
        }
    }

    private void reloadAllConfigs() {
        reloadConfig();
        loadConfigValues();
        
        File modModeFile = new File(getDataFolder(), "modmode.yml");
        if (!modModeFile.exists()) {
            saveResource("modmode.yml", false);
        }
        modModeConfig = YamlConfiguration.loadConfiguration(modModeFile);
        getLogger().info("[ModMode] Configuration rechargée");
        getLogger().info("[ModMode] auto-vanish: " + modModeConfig.getBoolean("settings.auto-vanish"));
        getLogger().info("[ModMode] persist-mode: " + modModeConfig.getBoolean("settings.persist-mode"));
        getLogger().info("[ModMode] allow-chat: " + modModeConfig.getBoolean("settings.allow-chat"));
        
        File mutesFile = new File(getDataFolder(), "mutes.yml");
        if (!mutesFile.exists()) {
            saveResource("mutes.yml", false);
        }
        getLogger().info("[Config] mutes.yml rechargé");
        
        File reportsFile = new File(getDataFolder(), "reports.yml");
        if (!reportsFile.exists()) {
            saveResource("reports.yml", false);
        }
        getLogger().info("[Config] reports.yml rechargé");
        
        File notesFile = new File(getDataFolder(), "playernotes.yml");
        if (!notesFile.exists()) {
            saveResource("playernotes.yml", false);
        }
        getLogger().info("[Config] playernotes.yml rechargé");
        
        File historyFile = new File(getDataFolder(), "sanction_history.yml");
        if (!historyFile.exists()) {
            saveResource("sanction_history.yml", false);
        }
        getLogger().info("[Config] sanction_history.yml rechargé");
        
        muteManager.saveMutes();
        reportManager.saveReports();
        playerNotesManager.saveNotes();
        sanctionHistoryManager.saveHistory();
        
        getLogger().info("[Config] Tous les fichiers de configuration ont été rechargés");
        
        getLogger().info("[Config] modmode.yml - auto-vanish: " + modModeConfig.getBoolean("settings.auto-vanish"));
        getLogger().info("[Config] modmode.yml - persist-mode: " + modModeConfig.getBoolean("settings.persist-mode"));
        getLogger().info("[Config] modmode.yml - allow-chat: " + modModeConfig.getBoolean("settings.allow-chat"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (cmd.getName().equalsIgnoreCase("prometheusargus") || cmd.getName().equalsIgnoreCase("pa")) {
            if (args.length == 1) {
                completions.addAll(Arrays.asList(
                    "help", "reload", "ban", "unban", "kick", "mute", "unmute",
                    "freeze", "lookup", "history", "staff", "observe", "test",
                    "sc", "mod", "notes", "reports"
                ));
            } else if (args.length == 2) {
                String subCommand = args[0].toLowerCase();
                if (Arrays.asList("ban", "unban", "kick", "mute", "unmute", "freeze", 
                                "lookup", "history", "observe", "test", "notes").contains(subCommand)) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        completions.add(player.getName());
                    }
                } else if (subCommand.equals("reports")) {
                    completions.addAll(Arrays.asList("1", "2", "3", "4", "5"));
                }
            } else if (args.length == 3) {
                String subCommand = args[0].toLowerCase();
                if (subCommand.equals("ban") || subCommand.equals("mute")) {
                    completions.addAll(Arrays.asList("1h", "2h", "6h", "12h", "1d", "7d", "30d", "perm"));
                } else if (subCommand.equals("test")) {
                    completions.addAll(Arrays.asList("combat", "movement", "interaction", "inventory"));
                }
            }
        } else if (cmd.getName().equalsIgnoreCase("report")) {
            if (args.length == 1) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            } else if (args.length == 2) {
                completions.addAll(Arrays.asList(
                    "cheat", "hack", "xray", "speed", "fly", "killaura",
                    "spam", "insult", "troll", "other"
                ));
            }
        }

        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .collect(Collectors.toList());
    }
}
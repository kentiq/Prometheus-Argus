package com.prometheusargus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit; // <<< CORRECTION IMPORT AJOUTÉ <<<

public class ReportManager {

    private final PrometheusArgus plugin;
    private File reportsFile;
    private FileConfiguration reportsConfig;
    private final Map<String, ReportEntry> activeReports = new LinkedHashMap<>(); 
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRENCH); // Ce champ n'est pas utilisé pour l'instant, mais on le garde.

    public ReportManager(PrometheusArgus plugin) {
        this.plugin = plugin;
        setupReportsFile();
        loadReports();
    }

    private void setupReportsFile() {
        reportsFile = new File(plugin.getDataFolder(), "reports.yml");
        if (!reportsFile.exists()) {
            try {
                reportsFile.getParentFile().mkdirs();
                reportsFile.createNewFile();
                plugin.getLogger().info("Created reports.yml");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create reports.yml: " + e.getMessage());
            }
        }
        reportsConfig = YamlConfiguration.loadConfiguration(reportsFile);
        if (reportsConfig.getConfigurationSection("reports") == null) {
            reportsConfig.createSection("reports");
            saveReports();
        }
    }

    private void loadReports() {
        activeReports.clear();
        ConfigurationSection reportSection = reportsConfig.getConfigurationSection("reports");
        if (reportSection == null) return;

        List<String> reportIDs = new ArrayList<>(reportSection.getKeys(false));
        Collections.reverse(reportIDs); 

        for (String reportID : reportIDs) {
            try {
                String path = "reports." + reportID + ".";
                UUID reporterUUID = UUID.fromString(reportsConfig.getString(path + "reporterUUID"));
                String reporterName = reportsConfig.getString(path + "reporterName");
                UUID reportedUUID = UUID.fromString(reportsConfig.getString(path + "reportedUUID"));
                String reportedName = reportsConfig.getString(path + "reportedName");
                String reason = reportsConfig.getString(path + "reason");
                long timestamp = reportsConfig.getLong(path + "timestamp");
                boolean isOpen = reportsConfig.getBoolean(path + "isOpen", true); 

                if (reporterName == null || reportedName == null || reason == null) continue;

                if (isOpen) {
                    activeReports.put(reportID, new ReportEntry(reportID, reporterUUID, reporterName, reportedUUID, reportedName, reason, new Date(timestamp), isOpen));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not parse report entry with ID: " + reportID + " - " + e.getMessage());
            }
        }
        if (plugin.isGlobalDebugModeEnabled()) {
            plugin.getLogger().info("[ReportManager] Loaded " + activeReports.size() + " active reports.");
        }
    }

    public void saveReports() {
        for (Map.Entry<String, ReportEntry> entry : activeReports.entrySet()) {
            ReportEntry report = entry.getValue();
            String reportID = report.getReportID();
            String path = "reports." + reportID + ".";

            reportsConfig.set(path + "reporterUUID", report.getReporterUUID().toString());
            reportsConfig.set(path + "reporterName", report.getReporterName());
            reportsConfig.set(path + "reportedUUID", report.getReportedUUID().toString());
            reportsConfig.set(path + "reportedName", report.getReportedName());
            reportsConfig.set(path + "reason", report.getReason());
            reportsConfig.set(path + "timestamp", report.getTimestamp().getTime());
            reportsConfig.set(path + "isOpen", report.isOpen());
        }
        // Sauvegarder aussi les modifications des reports marqués comme fermés
        // Si un report a été fermé, son état `isOpen` est mis à false dans reportsConfig
        // avant d'appeler saveReports().
        // Cette boucle met à jour tous les reports actifs. Si un report est fermé et retiré de activeReports,
        // il ne sera pas mis à jour ici, mais sa valeur "isOpen: false" devrait déjà être dans reportsConfig.
        // Pour être sûr, on peut parcourir les clés de reportsConfig et s'assurer que ceux non dans activeReports sont bien marqués fermés,
        // mais loadReports() ne chargeant que les ouverts, cela devrait suffire pour la logique actuelle.

        try {
            reportsConfig.save(reportsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save reports.yml: " + e.getMessage());
        }
    }

    public String addReport(Player reporter, OfflinePlayer reportedPlayer, String reason) {
        for (ReportEntry existingReport : activeReports.values()) {
            if (existingReport.isOpen() &&
                existingReport.getReporterUUID().equals(reporter.getUniqueId()) &&
                existingReport.getReportedUUID().equals(reportedPlayer.getUniqueId()) &&
                (System.currentTimeMillis() - existingReport.getTimestamp().getTime()) < TimeUnit.MINUTES.toMillis(plugin.getConfig().getInt("reports.cooldown_minutes", 5))) {
                reporter.sendMessage(ChatColor.RED + "Vous avez déjà signalé ce joueur récemment."); // Message configurable
                return null;
            }
        }

        String reportID = UUID.randomUUID().toString().substring(0, 8); 
        ReportEntry newReport = new ReportEntry(reportID, reporter.getUniqueId(), reporter.getName(),
                reportedPlayer.getUniqueId(), reportedPlayer.getName(), reason, new Date(), true);
        
        // Pour que les nouveaux reports apparaissent en premier dans le GUI (qui utilise getOpenReports qui trie par date)
        // il est préférable d'ajouter puis de re-trier ou de s'assurer que la liste de base est bien ordonnée.
        // LinkedHashMap maintient l'ordre d'insertion.
        // Pour avoir les plus récents en "tête" de la map (important si on ne trie pas après) :
        Map<String, ReportEntry> tempMap = new LinkedHashMap<>();
        tempMap.put(reportID, newReport);
        tempMap.putAll(activeReports);
        activeReports.clear();
        activeReports.putAll(tempMap);
        
        saveReports();

        String staffAlert = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("messages.report.staff_alert",
            "&8[&cREPORT&8] &e%reporter% &7a signalé &c%reported% &7pour: &f%reason%")
            .replace("%reporter%", reporter.getName())
            .replace("%reported%", reportedPlayer.getName())
            .replace("%reason%", reason));

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("prometheusargus.reports.receive")) {
                staff.sendMessage(staffAlert);
                staff.playSound(staff.getLocation(), Sound.valueOf("ORB_PICKUP"), 1.0f, 1.2f); 
            }
        }
        
        reporter.sendMessage(ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("messages.report.report_sent", "&aVotre signalement contre %reported_player% a été envoyé. Merci.")
            .replace("%reported_player%", reportedPlayer.getName())
        ));
        if(plugin.isGlobalDebugModeEnabled()){
            plugin.getLogger().info("[ReportManager] New report ID " + reportID + " by " + reporter.getName() + " against " + reportedPlayer.getName());
        }
        return reportID;
    }

    public List<ReportEntry> getOpenReports() {
        return activeReports.values().stream()
                .filter(ReportEntry::isOpen)
                .sorted((r1, r2) -> r2.getTimestamp().compareTo(r1.getTimestamp())) 
                .collect(Collectors.toList());
    }
    
    public ReportEntry getReportById(String reportId) {
        // Il faut chercher dans activeReports et potentiellement dans le fichier pour les reports fermés si on veut les récupérer par ID
        // Pour l'instant, on ne cherche que dans les actifs (qui sont ouverts par défaut au chargement)
        return activeReports.get(reportId);
    }

    public boolean closeReport(String reportId, String staffName) {
        ReportEntry report = activeReports.get(reportId);
        if (report != null && report.isOpen()) {
            report.setOpen(false); 
            // Mettre à jour directement dans le fichier config
            reportsConfig.set("reports." + reportId + ".isOpen", false);
            reportsConfig.set("reports." + reportId + ".closedBy", staffName);
            reportsConfig.set("reports." + reportId + ".closedTimestamp", System.currentTimeMillis());
            saveReports(); // Sauvegarder le changement dans le fichier

            // Optionnel : retirer de activeReports pour qu'il ne soit plus dans la liste des "actifs" en mémoire jusqu'au prochain reload
            // activeReports.remove(reportId); // Mais loadReports() ne rechargera que les "isOpen=true"

            if(plugin.isGlobalDebugModeEnabled()){
                plugin.getLogger().info("[ReportManager] Report ID " + reportId + " closed by " + staffName);
            }
            return true;
        }
        return false;
    }

    public static class ReportEntry {
        private final String reportID;
        private final UUID reporterUUID;
        private final String reporterName;
        private final UUID reportedUUID;
        private final String reportedName;
        private final String reason;
        private final Date timestamp;
        private boolean isOpen;

        public ReportEntry(String reportID, UUID reporterUUID, String reporterName, UUID reportedUUID, String reportedName, String reason, Date timestamp, boolean isOpen) {
            this.reportID = reportID;
            this.reporterUUID = reporterUUID;
            this.reporterName = reporterName;
            this.reportedUUID = reportedUUID;
            this.reportedName = reportedName;
            this.reason = reason;
            this.timestamp = timestamp;
            this.isOpen = isOpen;
        }

        public String getReportID() { return reportID; }
        public UUID getReporterUUID() { return reporterUUID; }
        public String getReporterName() { return reporterName; }
        public UUID getReportedUUID() { return reportedUUID; }
        public String getReportedName() { return reportedName; }
        public String getReason() { return reason; }
        public Date getTimestamp() { return timestamp; }
        public boolean isOpen() { return isOpen; }
        public void setOpen(boolean open) { isOpen = open; }

        // Utiliser le SimpleDateFormat passé en argument ou celui de la classe externe
        public String getFormattedTimestamp(SimpleDateFormat sdf) {
            return sdf.format(timestamp);
        }
    }
}
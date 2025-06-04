package com.prometheusargus;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale; 
import java.util.stream.Collectors;

public class SanctionHistoryManager {

    private final PrometheusArgus plugin;
    private File historyFile;
    private FileConfiguration historyConfig;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRENCH);

    public SanctionHistoryManager(PrometheusArgus plugin) {
        this.plugin = plugin;
        setupHistoryFile();
    }

    private void setupHistoryFile() {
        historyFile = new File(plugin.getDataFolder(), "sanction_history.yml");
        if (!historyFile.exists()) {
            try {
                historyFile.getParentFile().mkdirs();
                if (historyFile.createNewFile()) {
                     plugin.getLogger().info("Created sanction_history.yml");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create sanction_history.yml: " + e.getMessage());
            }
        }
        historyConfig = YamlConfiguration.loadConfiguration(historyFile);
        if (!historyConfig.isList("history")) {
            historyConfig.set("history", new ArrayList<String>());
            saveHistory();
        }
    }

    public void addSanction(String playerName, String type, String reason, String staffExecutor, int vlAtSanction) { 
        List<String> history = historyConfig.getStringList("history");
        
        String cleanReason = ChatColor.stripColor(reason);
        if (cleanReason.length() > 100) {
            cleanReason = cleanReason.substring(0, 97) + "...";
        }

        String entry = String.format("%s - Joueur: %s - Type: %s - Par: %s - %sRaison: %s",
                dateFormat.format(new Date()),
                playerName,
                type, 
                staffExecutor,
                (vlAtSanction != -1 ? "VL: " + vlAtSanction + " - " : ""), 
                cleanReason 
        );
        history.add(0, entry); 

        int maxEntries = plugin.getConfig().getInt("history.max_entries", 500);
        while (history.size() > maxEntries && maxEntries > 0) {
            history.remove(history.size() - 1);
        }

        historyConfig.set("history", history);
        saveHistory();
        if (plugin.isGlobalDebugModeEnabled()) {
            plugin.getLogger().info("[History] Added sanction: " + entry);
        }
    }

    public List<String> getRawHistory() {
        return historyConfig.getStringList("history");
    }
    
    public int getTotalSanctions() {
        return getRawHistory().size();
    }

    public List<String> getFormattedHistory(int itemsPerPage, int pageNum) {
        return getPaginatedAndFormattedEntries(getRawHistory(), itemsPerPage, pageNum);
    }

    public List<SanctionEntry> getSanctionsForPlayer(String playerName, int itemsPerPage, int pageNum) {
        List<String> playerHistoryRaw = getRawHistory().stream()
            .filter(entry -> {
                String lowerEntry = entry.toLowerCase();
                String searchKey = "joueur: " + playerName.toLowerCase();
                return lowerEntry.contains(searchKey);
            })
            .collect(Collectors.toList());

        return getPaginatedSanctionEntries(playerHistoryRaw, itemsPerPage, pageNum);
    }
    
    private List<SanctionEntry> getPaginatedSanctionEntries(List<String> entries, int itemsPerPage, int pageNum) {
        if (itemsPerPage <= 0) itemsPerPage = 10; 
        if (pageNum <= 0) pageNum = 1;

        int totalEntries = entries.size();
        int startIndex = (pageNum - 1) * itemsPerPage;
        
        if (startIndex >= totalEntries && totalEntries > 0) {
            return new ArrayList<>(); 
        }
        if (startIndex < 0) startIndex = 0;
        
        int endIndex = Math.min(startIndex + itemsPerPage, totalEntries);
        
        if (startIndex >= endIndex) return new ArrayList<>();

        return entries.subList(startIndex, endIndex).stream()
                .map(SanctionEntry::fromString) 
                .filter(java.util.Objects::nonNull) 
                .collect(Collectors.toList());
    }
    
    private List<String> getPaginatedAndFormattedEntries(List<String> allEntries, int itemsPerPage, int pageNum) {
        int totalEntries = allEntries.size();
        
        if (itemsPerPage <= 0) itemsPerPage = 45; 
        if (pageNum <= 0) pageNum = 1;

        int startIndex = (pageNum - 1) * itemsPerPage;
        
        if (startIndex >= totalEntries && totalEntries > 0) {
             return new ArrayList<>();
        }
        if (startIndex < 0) startIndex = 0;
        
        int endIndex = Math.min(startIndex + itemsPerPage, totalEntries);
        
        if (startIndex >= endIndex) return new ArrayList<>();

        return allEntries.subList(startIndex, endIndex).stream()
                .map(entry -> ChatColor.GRAY + entry) 
                .collect(Collectors.toList());
    }

    public void saveHistory() {
        try {
            historyConfig.save(historyFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save sanction_history.yml: " + e.getMessage());
        }
    }

    public static class SanctionEntry {
        public final String date;
        public final String playerName;
        public final String type;
        public final String executor;
        public final String reason;
        public final int vl;
        public final String rawEntry;

        public SanctionEntry(String date, String playerName, String type, String executor, String reason, int vl, String rawEntry) {
            this.date = date;
            this.playerName = playerName;
            this.type = type;
            this.executor = executor;
            this.reason = reason;
            this.vl = vl;
            this.rawEntry = rawEntry;
        }

        public static SanctionEntry fromString(String entryStr) {
            try {
                String[] partsDateTimeAndRest = entryStr.split(" - Joueur: ", 2);
                if (partsDateTimeAndRest.length < 2) return null;
                String date = partsDateTimeAndRest[0];
                
                String[] partsPlayerAndRest = partsDateTimeAndRest[1].split(" - Type: ", 2);
                if (partsPlayerAndRest.length < 2) return null;
                String playerName = partsPlayerAndRest[0];
                
                String[] partsTypeAndRest = partsPlayerAndRest[1].split(" - Par: ", 2);
                if (partsTypeAndRest.length < 2) return null;
                String type = partsTypeAndRest[0];
                
                String[] partsExecutorAndRest = partsTypeAndRest[1].split(" - ", 2); 
                if (partsExecutorAndRest.length < 2) return null;
                String executor = partsExecutorAndRest[0];
                
                String reasonPart = partsExecutorAndRest[1];
                int vl = -1;
                String reason;

                if (reasonPart.startsWith("VL: ")) {
                    String[] vlAndReasonParts = reasonPart.substring(4).split(" - Raison: ", 2);
                    if (vlAndReasonParts.length < 2) return null;
                    vl = Integer.parseInt(vlAndReasonParts[0]);
                    reason = vlAndReasonParts[1];
                } else if (reasonPart.startsWith("Raison: ")) {
                    reason = reasonPart.substring(8);
                } else { 
                    return null; 
                }
                
                return new SanctionEntry(date, playerName, type, executor, reason, vl, entryStr);
            } catch (Exception e) {
                return null; 
            }
        }
    }
}
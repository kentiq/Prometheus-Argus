package com.prometheusargus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StaffGUIManager {

    private final PrometheusArgus plugin;
    public static final String MAIN_MENU_TITLE = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Prometheus - Menu Staff";
    public static final String PLAYER_LIST_TITLE = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Prometheus - Joueurs";
    public static final String PLAYER_INFO_TITLE_PREFIX = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Prometheus - Infos "; 
    public static final String SANCTION_HISTORY_TITLE = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Prometheus - Historique";
    public static final String REPORTS_LIST_TITLE = ChatColor.DARK_RED + "" + ChatColor.BOLD + "Prometheus - Signalements";

    private final SimpleDateFormat guiDateFormat = new SimpleDateFormat("dd/MM HH:mm", Locale.FRENCH);


    public StaffGUIManager(PrometheusArgus plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player staffPlayer) {
        Inventory mainMenu = Bukkit.createInventory(null, 27, MAIN_MENU_TITLE);

        mainMenu.setItem(10, createGUIItem(Material.PAPER, ChatColor.GREEN + "Liste des Joueurs en Ligne",
                Arrays.asList(ChatColor.GRAY + "Voir et inspecter les joueurs.")));
        
        mainMenu.setItem(12, createGUIItem(Material.EMPTY_MAP, ChatColor.RED + "Signalements en Attente",
                Arrays.asList(ChatColor.GRAY + "Voir les signalements des joueurs.")));

        mainMenu.setItem(14, createGUIItem(Material.BOOK_AND_QUILL, ChatColor.GOLD + "Historique des Sanctions",
                Arrays.asList(ChatColor.GRAY + "Consulter les sanctions enregistrées.")));

        mainMenu.setItem(16, createGUIItem(Material.COMPASS, ChatColor.AQUA + "Statistiques Anti-Cheat",
                Arrays.asList(ChatColor.GRAY + "Voir les statistiques de détection.")));
        
        ItemStack placeholder = createGUIItem(Material.STAINED_GLASS_PANE, " ", null, (short) 7); 
        for (int i = 0; i < mainMenu.getSize(); i++) {
            if (mainMenu.getItem(i) == null) {
                if (i < 9 || i > 17 || (i % 9 == 0) || (i % 9 == 8) || i == 11 || i == 13 || i == 15) { 
                     mainMenu.setItem(i, placeholder);
                }
            }
        }
        mainMenu.setItem(22, createGUIItem(Material.BARRIER, ChatColor.RED + "Fermer", null)); 

        staffPlayer.openInventory(mainMenu);
    }

    public void openPlayerListMenu(Player staffPlayer, int page) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());

        int playersPerPage = 45; 
        int totalPages = Math.max(1, (int) Math.ceil((double) onlinePlayers.size() / playersPerPage));
        page = Math.max(1, Math.min(page, totalPages));

        Inventory playerListMenu = Bukkit.createInventory(null, 54, PLAYER_LIST_TITLE + ChatColor.GRAY + " (Page " + page + "/" + totalPages + ")");

        int startIndex = (page - 1) * playersPerPage;
        for (int i = 0; i < playersPerPage; i++) {
            int playerIndex = startIndex + i;
            if (playerIndex < onlinePlayers.size()) {
                Player targetPlayer = onlinePlayers.get(playerIndex);
                PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(targetPlayer);
                
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.DARK_GRAY + "--------------------");
                if (acData != null) {
                    int totalVL = acData.getAllViolationLevels().values().stream().mapToInt(Integer::intValue).sum();
                    ChatColor vlColor = ChatColor.GREEN;
                    if (totalVL > 5) vlColor = ChatColor.YELLOW;
                    if (totalVL > 15) vlColor = ChatColor.RED;
                    if (totalVL > 25) vlColor = ChatColor.DARK_RED;
                    lore.add(ChatColor.GRAY + "Niveau de Menace: " + vlColor + totalVL);
                } else {
                    lore.add(ChatColor.GRAY + "Niveau de Menace: " + ChatColor.WHITE + "N/A");
                }
                lore.add(ChatColor.DARK_GRAY + "--------------------");
                lore.add(ChatColor.YELLOW + "Clic Gauche: Inspecter");
                lore.add(ChatColor.AQUA + "Clic Droit: Téléporter (Admin)");

                playerListMenu.setItem(i, createPlayerHeadItem(targetPlayer.getName(), ChatColor.WHITE + targetPlayer.getName(), lore));
            } else {
                break; 
            }
        }

        if (page > 1) {
            playerListMenu.setItem(45, createGUIItem(Material.ARROW, ChatColor.YELLOW + "◄ Page Précédente (" + (page - 1) + ")", null));
        } else {
            playerListMenu.setItem(45, createGUIItem(Material.STAINED_GLASS_PANE, " ", null, (short)7));
        }
        if (page < totalPages) {
            playerListMenu.setItem(53, createGUIItem(Material.ARROW, ChatColor.YELLOW + "Page Suivante (" + (page + 1) + ") ►", null));
        } else {
             playerListMenu.setItem(53, createGUIItem(Material.STAINED_GLASS_PANE, " ", null, (short)7));
        }
        playerListMenu.setItem(49, createGUIItem(Material.BARRIER, ChatColor.RED + "Retour au Menu Principal", null));

        staffPlayer.openInventory(playerListMenu);
    }


    public void openPlayerInfoMenu(Player staffPlayer, Player targetPlayer) {
        PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(targetPlayer);
        if (acData == null) {
            staffPlayer.sendMessage(ChatColor.RED + "Impossible de récupérer les données pour " + targetPlayer.getName());
            plugin.getStaffGUIManager().openPlayerListMenu(staffPlayer, 1); 
            return;
        }

        Inventory playerInfoMenu = Bukkit.createInventory(null, 54, PLAYER_INFO_TITLE_PREFIX + targetPlayer.getName());

        playerInfoMenu.setItem(4, createPlayerHeadItem(targetPlayer.getName(), ChatColor.GOLD + targetPlayer.getName(), Arrays.asList(
                ChatColor.GRAY + "Ping: " + getPing(targetPlayer) + "ms",
                ChatColor.GRAY + "Monde: " + targetPlayer.getWorld().getName(),
                ChatColor.GRAY + "Position: " + String.format("%.1f, %.1f, %.1f", targetPlayer.getLocation().getX(), targetPlayer.getLocation().getY(), targetPlayer.getLocation().getZ()),
                ChatColor.GRAY + "Gamemode: " + targetPlayer.getGameMode().toString()
        )));
        
        playerInfoMenu.setItem(0, createGUIItem(Material.ARROW, ChatColor.YELLOW + "Retour à la Liste des Joueurs", null));

        int slot = 18; 
        Map<String, Integer> violations = acData.getAllViolationLevels();
        if (violations.isEmpty() || violations.values().stream().allMatch(v -> v == 0)) {
            playerInfoMenu.setItem(slot, createGUIItem(Material.GLASS_BOTTLE, ChatColor.GREEN + "Aucune violation active", null));
        } else {
            for (Map.Entry<String, Integer> entry : violations.entrySet()) {
                if (entry.getValue() > 0) { 
                    if (slot >= 26) { 
                         if (slot == 26) playerInfoMenu.setItem(slot, createGUIItem(Material.PAPER, ChatColor.GRAY+"(Plus...)", null)); 
                    }
                     if (slot >= 35) { 
                         if (slot == 35 && violations.size() > (26-18+1) + (35-27+1) ) playerInfoMenu.setItem(slot, createGUIItem(Material.PAPER, ChatColor.GRAY+"(Encore plus...)", null));
                         break; 
                    }
                    playerInfoMenu.setItem(slot++, createGUIItem(Material.PAPER, ChatColor.YELLOW + entry.getKey() + ": " + ChatColor.RED + entry.getValue(), null));
                }
            }
        }
        
        playerInfoMenu.setItem(47, createGUIItem(Material.EYE_OF_ENDER, ChatColor.AQUA + "Observer " + targetPlayer.getName(),
                Arrays.asList(ChatColor.GRAY + "(Activer logs debug ciblés)", ChatColor.GOLD + "Exécuter: /pa observe " + targetPlayer.getName())));
        playerInfoMenu.setItem(48, createGUIItem(Material.TNT, ChatColor.RED + "Tester le Joueur",
                Arrays.asList(ChatColor.GRAY + "(Ex: faux dégâts, etc.)", ChatColor.DARK_RED + "Attention: Peut être intrusif!", ChatColor.GOLD + "Ex: /pa test " + targetPlayer.getName() + " nofall")));
        playerInfoMenu.setItem(50, createGUIItem(Material.IRON_AXE, ChatColor.DARK_RED + "Sanctionner (Commandes)",
                Arrays.asList(ChatColor.GRAY + "Utilisez les commandes de votre", ChatColor.GRAY + "plugin de sanctions habituel.")));
        playerInfoMenu.setItem(51, createGUIItem(Material.FEATHER, ChatColor.LIGHT_PURPLE + "Vanish (Si plugin de Vanish)",
                Arrays.asList(ChatColor.GRAY + "Exécuter: /vanish")));
        
        ItemStack placeholder = createGUIItem(Material.STAINED_GLASS_PANE, " ", null, (short) 15); 
        for (int i = 0; i < playerInfoMenu.getSize(); i++) {
            if (playerInfoMenu.getItem(i) == null) {
                playerInfoMenu.setItem(i, placeholder);
            }
        }
        playerInfoMenu.setItem(4, playerInfoMenu.getItem(4)); 
        playerInfoMenu.setItem(0, playerInfoMenu.getItem(0)); 
        if(playerInfoMenu.getItem(47) != null && playerInfoMenu.getItem(47).getType() != Material.STAINED_GLASS_PANE) playerInfoMenu.setItem(47, playerInfoMenu.getItem(47));
        if(playerInfoMenu.getItem(48) != null && playerInfoMenu.getItem(48).getType() != Material.STAINED_GLASS_PANE) playerInfoMenu.setItem(48, playerInfoMenu.getItem(48));
        if(playerInfoMenu.getItem(50) != null && playerInfoMenu.getItem(50).getType() != Material.STAINED_GLASS_PANE) playerInfoMenu.setItem(50, playerInfoMenu.getItem(50));
        if(playerInfoMenu.getItem(51) != null && playerInfoMenu.getItem(51).getType() != Material.STAINED_GLASS_PANE) playerInfoMenu.setItem(51, playerInfoMenu.getItem(51));

        staffPlayer.openInventory(playerInfoMenu);
    }
    
    public void openSanctionHistoryMenu(Player staffPlayer, int page) {
        List<String> historyEntries = plugin.getSanctionHistoryManager().getFormattedHistory(45, page);
        int totalSanctions = plugin.getSanctionHistoryManager().getTotalSanctions();
        int itemsPerPage = 45;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalSanctions / itemsPerPage));
        page = Math.max(1, Math.min(page, totalPages));

        Inventory historyMenu = Bukkit.createInventory(null, 54, SANCTION_HISTORY_TITLE + ChatColor.GRAY + " (Page " + page + "/" + totalPages + ")");

        if (historyEntries.isEmpty()) {
            historyMenu.setItem(22, createGUIItem(Material.BARRIER, ChatColor.RED + "Aucune sanction enregistrée.", null));
        } else {
            for (int i = 0; i < historyEntries.size(); i++) {
                historyMenu.setItem(i, createGUIItem(Material.PAPER, historyEntries.get(i), null));
            }
        }

        if (page > 1) {
            historyMenu.setItem(45, createGUIItem(Material.ARROW, ChatColor.YELLOW + "◄ Page Précédente (" + (page - 1) + ")", null));
        } else {
             historyMenu.setItem(45, createGUIItem(Material.STAINED_GLASS_PANE, " ", null, (short)7));
        }
        if (page < totalPages) {
            historyMenu.setItem(53, createGUIItem(Material.ARROW, ChatColor.YELLOW + "Page Suivante (" + (page + 1) + ") ►", null));
        } else {
            historyMenu.setItem(53, createGUIItem(Material.STAINED_GLASS_PANE, " ", null, (short)7));
        }
        historyMenu.setItem(49, createGUIItem(Material.BARRIER, ChatColor.RED + "Retour au Menu Principal", null));

        staffPlayer.openInventory(historyMenu);
    }

    public void openReportsMenu(Player staffPlayer, int page) {
        List<ReportManager.ReportEntry> openReports = plugin.getReportManager().getOpenReports();
        
        int reportsPerPage = 45;
        int totalReports = openReports.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalReports / reportsPerPage));
        page = Math.max(1, Math.min(page, totalPages));

        Inventory reportsMenu = Bukkit.createInventory(null, 54, REPORTS_LIST_TITLE + ChatColor.GRAY + " (Page " + page + "/" + totalPages + ")");

        if (openReports.isEmpty()) {
            reportsMenu.setItem(22, createGUIItem(Material.BARRIER, ChatColor.GREEN + "Aucun signalement en attente.", null));
        } else {
            int startIndex = (page - 1) * reportsPerPage;
            for (int i = 0; i < reportsPerPage; i++) {
                int reportIndex = startIndex + i;
                if (reportIndex < totalReports) {
                    ReportManager.ReportEntry report = openReports.get(reportIndex);
                    
                    OfflinePlayer reportedOfflinePlayer = Bukkit.getOfflinePlayer(report.getReportedUUID());
                    String reportedPlayerNameDisplay = reportedOfflinePlayer.getName() != null ? reportedOfflinePlayer.getName() : report.getReportedName();

                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Signalé par: " + ChatColor.YELLOW + report.getReporterName());
                    lore.add(ChatColor.GRAY + "Joueur signalé: " + ChatColor.RED + reportedPlayerNameDisplay);
                    lore.add(ChatColor.GRAY + "Date: " + ChatColor.AQUA + report.getFormattedTimestamp(guiDateFormat));
                    lore.add(ChatColor.DARK_GRAY + "--------------------");
                    lore.add(ChatColor.WHITE + "Raison: ");
                    String reason = report.getReason();
                    final int MAX_LINE_LENGTH = 35;
                    while (reason.length() > MAX_LINE_LENGTH) {
                        int breakPoint = reason.lastIndexOf(' ', MAX_LINE_LENGTH);
                        if (breakPoint == -1) breakPoint = MAX_LINE_LENGTH;
                        lore.add(ChatColor.GRAY + "  " + reason.substring(0, breakPoint));
                        reason = reason.substring(breakPoint).trim();
                    }
                    lore.add(ChatColor.GRAY + "  " + reason);
                    lore.add(ChatColor.DARK_GRAY + "--------------------");
                    lore.add(ChatColor.YELLOW + "Clic Gauche: Se TP au joueur signalé (si en ligne)");
                    lore.add(ChatColor.GREEN + "Clic Droit: Marquer comme traité (fermer)");
                    lore.add(ChatColor.AQUA + "Clic Molette: Voir profil du signalé");
                    lore.add(ChatColor.DARK_GRAY + "ID: " + report.getReportID());


                    ItemStack reportItem;
                    if (reportedOfflinePlayer.hasPlayedBefore() || reportedOfflinePlayer.isOnline()) {
                        reportItem = createPlayerHeadItem(reportedPlayerNameDisplay, ChatColor.GOLD + "Report: " + reportedPlayerNameDisplay, lore);
                    } else {
                        reportItem = createGUIItem(Material.PAPER, ChatColor.GOLD + "Report: " + reportedPlayerNameDisplay, lore);
                    }
                    reportsMenu.setItem(i, reportItem);
                } else {
                    break; 
                }
            }
        }

        if (page > 1) {
            reportsMenu.setItem(45, createGUIItem(Material.ARROW, ChatColor.YELLOW + "◄ Page Précédente (" + (page - 1) + ")", null));
        } else {
            reportsMenu.setItem(45, createGUIItem(Material.STAINED_GLASS_PANE, " ", null, (short)7));
        }
        if (page < totalPages) {
            reportsMenu.setItem(53, createGUIItem(Material.ARROW, ChatColor.YELLOW + "Page Suivante (" + (page + 1) + ") ►", null));
        } else {
            reportsMenu.setItem(53, createGUIItem(Material.STAINED_GLASS_PANE, " ", null, (short)7));
        }
        reportsMenu.setItem(49, createGUIItem(Material.BARRIER, ChatColor.RED + "Retour au Menu Principal", null));

        staffPlayer.openInventory(reportsMenu);
    }


    public static ItemStack createGUIItem(Material material, String name, List<String> lore, short durability) {
        ItemStack item = new ItemStack(material, 1, durability);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    public static ItemStack createGUIItem(Material material, String name, List<String> lore) {
        return createGUIItem(material, name, lore, (short) 0);
    }

    public static ItemStack createPlayerHeadItem(String playerName, String name, List<String> lore) {
        ItemStack playerHead = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwner(playerName);
            skullMeta.setDisplayName(name);
            if (lore != null) {
                skullMeta.setLore(lore);
            }
            playerHead.setItemMeta(skullMeta);
        }
        return playerHead;
    }

    private int getPing(Player player) {
        try {
            Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);
            return (int) entityPlayer.getClass().getField("ping").get(entityPlayer);
        } catch (Exception e) {
            return -1;
        }
    }
}
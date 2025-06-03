package com.prometheusargus; // Package de base

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer; // AJOUT
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta; // AJOUT si manquant, mais devrait y être

import java.util.List; // AJOUT

public class GUIListeners implements Listener {

    private final PrometheusArgus plugin;
    private final StaffGUIManager guiManager;

    public GUIListeners(PrometheusArgus plugin, StaffGUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player staffPlayer = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getInventory();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedInventory == null || clickedItem == null || clickedItem.getType() == Material.AIR) {
             // Annuler si on clique dans un slot vide de nos GUIs spécifiques
            if (clickedInventory != null && 
                (clickedInventory.getTitle().equals(StaffGUIManager.MAIN_MENU_TITLE) ||
                 clickedInventory.getTitle().startsWith(StaffGUIManager.PLAYER_LIST_TITLE) ||
                 clickedInventory.getTitle().startsWith(StaffGUIManager.PLAYER_INFO_TITLE_PREFIX) ||
                 clickedInventory.getTitle().startsWith(StaffGUIManager.SANCTION_HISTORY_TITLE) ||
                 clickedInventory.getTitle().startsWith(StaffGUIManager.REPORTS_LIST_TITLE) )) { // AJOUT REPORTS_LIST_TITLE
                event.setCancelled(true);
            }
            return;
        }
        
        // Vérifier si l'item a un nom seulement s'il n'est pas un placeholder qu'on veut quand même annuler
        if (!clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            // Si c'est un item sans nom (comme une vitre de placeholder avec nom vide) dans nos GUIs, on annule aussi
             if (clickedInventory.getTitle().equals(StaffGUIManager.MAIN_MENU_TITLE) ||
                 clickedInventory.getTitle().startsWith(StaffGUIManager.PLAYER_LIST_TITLE) ||
                 clickedInventory.getTitle().startsWith(StaffGUIManager.PLAYER_INFO_TITLE_PREFIX) ||
                 clickedInventory.getTitle().startsWith(StaffGUIManager.SANCTION_HISTORY_TITLE) ||
                 clickedInventory.getTitle().startsWith(StaffGUIManager.REPORTS_LIST_TITLE) ) {
                event.setCancelled(true);
            }
            return;
        }


        String inventoryTitle = clickedInventory.getTitle();
        String itemDisplayName = clickedItem.getItemMeta().getDisplayName();


        if (inventoryTitle.equals(StaffGUIManager.MAIN_MENU_TITLE)) {
            event.setCancelled(true);
            if (itemDisplayName.contains("Liste des Joueurs")) {
                guiManager.openPlayerListMenu(staffPlayer, 1);
            } else if (itemDisplayName.contains("Signalements en Attente")) { // <<< NOUVELLE CONDITION
                guiManager.openReportsMenu(staffPlayer, 1);
            } else if (itemDisplayName.contains("Historique des Sanctions")) {
                guiManager.openSanctionHistoryMenu(staffPlayer, 1);
            } else if (itemDisplayName.contains("Statistiques")) {
                staffPlayer.sendMessage(ChatColor.YELLOW + "Fonctionnalité de statistiques à implémenter.");
                staffPlayer.closeInventory();
            } else if (itemDisplayName.contains("Fermer")) {
                staffPlayer.closeInventory();
            }
            return;
        }

        if (inventoryTitle.startsWith(StaffGUIManager.PLAYER_LIST_TITLE)) {
            // ... (Logique existante pour PLAYER_LIST_TITLE - INCHANGÉE) ...
            event.setCancelled(true);
            int currentPage = 1;
            try {
                String titlePart = inventoryTitle.substring(StaffGUIManager.PLAYER_LIST_TITLE.length()); 
                if (titlePart.contains("Page ") && titlePart.contains("/")) {
                    String pageStr = titlePart.substring(titlePart.indexOf("Page ") + 5, titlePart.indexOf("/")).trim();
                    currentPage = Integer.parseInt(pageStr);
                }
            } catch (Exception ignored) {
                 if(plugin.isGlobalDebugModeEnabled()) plugin.getLogger().warning("Could not parse page from PlayerList GUI title: " + inventoryTitle);
            }

            if (clickedItem.getType() == Material.SKULL_ITEM) {
                String targetPlayerName = ChatColor.stripColor(itemDisplayName);
                Player targetPlayer = Bukkit.getPlayerExact(targetPlayerName);
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    if (event.getClick() == ClickType.LEFT) {
                        guiManager.openPlayerInfoMenu(staffPlayer, targetPlayer);
                    } else if (event.getClick() == ClickType.RIGHT && staffPlayer.hasPermission("prometheusargus.staff.teleport")) {
                        staffPlayer.teleport(targetPlayer);
                        staffPlayer.sendMessage(ChatColor.GREEN + "Téléporté à " + targetPlayer.getName());
                        staffPlayer.closeInventory();
                    }
                } else {
                    staffPlayer.sendMessage(ChatColor.RED + "Le joueur " + targetPlayerName + " n'est plus en ligne.");
                    guiManager.openPlayerListMenu(staffPlayer, currentPage); 
                }
            } else if (clickedItem.getType() == Material.ARROW) {
                if (itemDisplayName.contains("Précédente")) {
                    guiManager.openPlayerListMenu(staffPlayer, currentPage - 1);
                } else if (itemDisplayName.contains("Suivante")) {
                    guiManager.openPlayerListMenu(staffPlayer, currentPage + 1);
                }
            } else if (itemDisplayName.contains("Retour au Menu Principal")) {
                 guiManager.openMainMenu(staffPlayer);
            }
            return;
        }
        
        if (inventoryTitle.startsWith(StaffGUIManager.PLAYER_INFO_TITLE_PREFIX)) {
            // ... (Logique existante pour PLAYER_INFO_TITLE_PREFIX - INCHANGÉE) ...
            event.setCancelled(true);
            String targetPlayerNameFromTitle = inventoryTitle.substring(StaffGUIManager.PLAYER_INFO_TITLE_PREFIX.length()).trim();

            if (itemDisplayName.contains("Observer")) {
                 staffPlayer.performCommand("pa observe " + targetPlayerNameFromTitle);
                 staffPlayer.closeInventory();
            } else if (itemDisplayName.contains("Tester le Joueur")) {
                 staffPlayer.sendMessage(ChatColor.GOLD + "Utilisez: /pa test " + targetPlayerNameFromTitle + " <type_test>");
                 staffPlayer.closeInventory();
            } else if (itemDisplayName.contains("Sanctionner (Commandes)")) {
                 staffPlayer.sendMessage(ChatColor.GOLD + "Utilisez les commandes de sanction: /ban, /kick, /mute " + targetPlayerNameFromTitle);
                 staffPlayer.closeInventory();
            } else if (itemDisplayName.contains("Vanish")) {
                 staffPlayer.performCommand("vanish"); 
                 staffPlayer.closeInventory();
            } else if (itemDisplayName.contains("Retour à la Liste des Joueurs")) { 
                guiManager.openPlayerListMenu(staffPlayer, 1);
            }
            return;
        }

        if (inventoryTitle.startsWith(StaffGUIManager.SANCTION_HISTORY_TITLE)) {
            // ... (Logique existante pour SANCTION_HISTORY_TITLE - INCHANGÉE) ...
             event.setCancelled(true);
            int currentPage = 1;
            try {
                String titlePart = inventoryTitle.substring(StaffGUIManager.SANCTION_HISTORY_TITLE.length());
                if (titlePart.contains("Page ") && titlePart.contains("/")) {
                    String pageStr = titlePart.substring(titlePart.indexOf("Page ") + 5, titlePart.indexOf("/")).trim();
                    currentPage = Integer.parseInt(pageStr);
                }
            } catch (Exception ignored) {
                if(plugin.isGlobalDebugModeEnabled()) plugin.getLogger().warning("Could not parse page from SanctionHistory GUI title: " + inventoryTitle);
            }

            if (clickedItem.getType() == Material.ARROW) {
                if (itemDisplayName.contains("Précédente")) {
                    guiManager.openSanctionHistoryMenu(staffPlayer, currentPage - 1);
                } else if (itemDisplayName.contains("Suivante")) {
                    guiManager.openSanctionHistoryMenu(staffPlayer, currentPage + 1);
                }
            } else if (itemDisplayName.contains("Retour au Menu Principal")) {
                 guiManager.openMainMenu(staffPlayer);
            }
            return;
        }

        // >>> NOUVELLE LOGIQUE POUR LE GUI DES REPORTS <<<
        if (inventoryTitle.startsWith(StaffGUIManager.REPORTS_LIST_TITLE)) {
            event.setCancelled(true);
            int currentPage = 1; // Default page
             try {
                String titlePart = inventoryTitle.substring(StaffGUIManager.REPORTS_LIST_TITLE.length()); 
                if (titlePart.contains("Page ") && titlePart.contains("/")) {
                    String pageStr = titlePart.substring(titlePart.indexOf("Page ") + 5, titlePart.indexOf("/")).trim();
                    currentPage = Integer.parseInt(pageStr);
                }
            } catch (Exception ignored) {
                 if(plugin.isGlobalDebugModeEnabled()) plugin.getLogger().warning("Could not parse page from ReportsList GUI title: " + inventoryTitle);
            }

            if (clickedItem.getType() == Material.SKULL_ITEM || clickedItem.getType() == Material.PAPER && clickedItem.getItemMeta().hasLore()) { // Item de report (tête ou papier)
                List<String> lore = clickedItem.getItemMeta().getLore();
                String reportId = null;
                String reportedPlayerName = null;

                for (String line : lore) {
                    String strippedLine = ChatColor.stripColor(line);
                    if (strippedLine.startsWith("ID: ")) {
                        reportId = strippedLine.substring(4);
                    }
                    if (strippedLine.startsWith("Joueur signalé: ")) {
                        reportedPlayerName = strippedLine.substring("Joueur signalé: ".length());
                    }
                }

                if (reportedPlayerName == null) { // Failsafe, essayer de prendre le nom de l'item
                    reportedPlayerName = ChatColor.stripColor(itemDisplayName).replace("Report: ", "");
                }
                
                OfflinePlayer reportedOfflinePlayer = Bukkit.getOfflinePlayer(reportedPlayerName); // Nécessaire pour UUID

                if (event.getClick() == ClickType.LEFT) { // Se TP au joueur signalé (si en ligne)
                    if (reportedOfflinePlayer.isOnline()) {
                        Player targetOnline = reportedOfflinePlayer.getPlayer();
                        if (staffPlayer.hasPermission("prometheusargus.staff.teleport")) { // Utiliser une permission existante ou créer une nouvelle
                            staffPlayer.teleport(targetOnline);
                            staffPlayer.sendMessage(ChatColor.GREEN + "Téléporté à " + targetOnline.getName() + " (signalé).");
                            staffPlayer.closeInventory();
                        } else {
                            staffPlayer.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de vous téléporter.");
                        }
                    } else {
                        staffPlayer.sendMessage(ChatColor.RED + "Le joueur signalé " + reportedPlayerName + " n'est pas en ligne.");
                    }
                } else if (event.getClick() == ClickType.RIGHT) { // Marquer comme traité
                    if (reportId != null && staffPlayer.hasPermission("prometheusargus.reports.manage")) {
                        boolean closed = plugin.getReportManager().closeReport(reportId, staffPlayer.getName());
                        if (closed) {
                            staffPlayer.sendMessage(ChatColor.GREEN + "Signalement ID " + reportId + " marqué comme traité.");
                            guiManager.openReportsMenu(staffPlayer, currentPage); // Rafraîchir le GUI
                        } else {
                            staffPlayer.sendMessage(ChatColor.RED + "Impossible de fermer le signalement ID " + reportId + " (déjà traité ou introuvable).");
                        }
                    } else if (reportId == null) {
                        staffPlayer.sendMessage(ChatColor.RED + "Impossible de trouver l'ID du signalement.");
                    } else {
                         staffPlayer.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de gérer les signalements.");
                    }
                } else if (event.getClick() == ClickType.MIDDLE) { // Voir profil du signalé
                    staffPlayer.performCommand("pa lookup " + reportedPlayerName);
                    staffPlayer.closeInventory();
                }

            } else if (clickedItem.getType() == Material.ARROW) {
                if (itemDisplayName.contains("Précédente")) {
                    guiManager.openReportsMenu(staffPlayer, currentPage - 1);
                } else if (itemDisplayName.contains("Suivante")) {
                    guiManager.openReportsMenu(staffPlayer, currentPage + 1);
                }
            } else if (itemDisplayName.contains("Retour au Menu Principal")) {
                guiManager.openMainMenu(staffPlayer);
            }
            return;
        }
    }
}
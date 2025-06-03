package com.prometheusargus.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SanctionsGUIListener implements Listener {
    private static final Map<UUID, Player> targetPlayers = new HashMap<>();

    public static void setTargetPlayer(Player moderator, Player target) {
        targetPlayers.put(moderator.getUniqueId(), target);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        if (!event.getView().getTitle().equals(SanctionsGUI.getTitle())) return;
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        Player target = targetPlayers.get(player.getUniqueId());
        if (target == null) return;

        switch (clickedItem.getType()) {
            case BARRIER:
                if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.RED + "Kick")) {
                    // TODO: Implémenter la logique de kick
                    player.sendMessage(ChatColor.RED + "Fonctionnalité de kick à implémenter");
                } else if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.RED + "Fermer")) {
                    player.closeInventory();
                }
                break;

            case PAPER:
                if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.YELLOW + "Mute")) {
                    // TODO: Implémenter la logique de mute
                    player.sendMessage(ChatColor.YELLOW + "Fonctionnalité de mute à implémenter");
                }
                break;

            case IRON_DOOR:
                if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.GOLD + "Ban Temporaire")) {
                    // TODO: Implémenter la logique de ban temporaire
                    player.sendMessage(ChatColor.GOLD + "Fonctionnalité de ban temporaire à implémenter");
                }
                break;

            case BEDROCK:
                if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.DARK_RED + "Ban Permanent")) {
                    // TODO: Implémenter la logique de ban permanent
                    player.sendMessage(ChatColor.DARK_RED + "Fonctionnalité de ban permanent à implémenter");
                }
                break;

            case BOOK:
                if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.GREEN + "Templates")) {
                    // TODO: Implémenter la logique des templates
                    player.sendMessage(ChatColor.GREEN + "Fonctionnalité des templates à implémenter");
                }
                break;

            case WATCH:
                if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.AQUA + "Historique")) {
                    // TODO: Implémenter la logique de l'historique
                    player.sendMessage(ChatColor.AQUA + "Fonctionnalité de l'historique à implémenter");
                }
                break;

            case BOOK_AND_QUILL:
                if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.LIGHT_PURPLE + "Notes")) {
                    // TODO: Implémenter la logique des notes
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "Fonctionnalité des notes à implémenter");
                }
                break;

            case SKULL_ITEM:
                // TODO: Implémenter la logique pour voir l'historique détaillé
                player.sendMessage(ChatColor.YELLOW + "Historique détaillé à implémenter");
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        
        if (event.getView().getTitle().equals(SanctionsGUI.getTitle())) {
            targetPlayers.remove(player.getUniqueId());
        }
    }
} 
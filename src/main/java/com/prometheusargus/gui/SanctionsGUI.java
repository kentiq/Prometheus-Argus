package com.prometheusargus.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class SanctionsGUI {
    private static final String GUI_TITLE = ChatColor.DARK_RED + "Menu de Sanctions";
    private static final int GUI_SIZE = 54;

    public static String getTitle() {
        return GUI_TITLE;
    }

    public static void openSanctionsGUI(Player moderator, Player target) {
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        ItemStack targetHead = createPlayerHead(
            target.getName(),
            ChatColor.YELLOW + "Joueur: " + ChatColor.WHITE + target.getName(),
            Arrays.asList(
                ChatColor.GRAY + "UUID: " + target.getUniqueId(),
                ChatColor.GRAY + "IP: " + target.getAddress().getAddress().getHostAddress(),
                "",
                ChatColor.YELLOW + "Statistiques:",
                ChatColor.GRAY + "• Temps de jeu: " + getPlayTime(target),
                ChatColor.GRAY + "• Dernière connexion: " + getLastLogin(target),
                "",
                ChatColor.RED + "Cliquez pour voir l'historique"
            )
        );
        gui.setItem(4, targetHead);

        gui.setItem(19, createSanctionItem(
            Material.BARRIER,
            ChatColor.RED + "Kick",
            Arrays.asList(
                ChatColor.GRAY + "Expulse le joueur du serveur",
                "",
                ChatColor.YELLOW + "Cliquez pour kick"
            )
        ));

        gui.setItem(21, createSanctionItem(
            Material.PAPER,
            ChatColor.YELLOW + "Mute",
            Arrays.asList(
                ChatColor.GRAY + "Empêche le joueur de parler",
                "",
                ChatColor.YELLOW + "Options:",
                ChatColor.GRAY + "• 1 heure",
                ChatColor.GRAY + "• 1 jour",
                ChatColor.GRAY + "• 1 semaine",
                ChatColor.GRAY + "• Permanent",
                "",
                ChatColor.YELLOW + "Cliquez pour mute"
            )
        ));

        gui.setItem(23, createSanctionItem(
            Material.IRON_DOOR,
            ChatColor.GOLD + "Ban Temporaire",
            Arrays.asList(
                ChatColor.GRAY + "Bannit le joueur temporairement",
                "",
                ChatColor.YELLOW + "Options:",
                ChatColor.GRAY + "• 1 jour",
                ChatColor.GRAY + "• 1 semaine",
                ChatColor.GRAY + "• 1 mois",
                "",
                ChatColor.YELLOW + "Cliquez pour ban"
            )
        ));

        gui.setItem(25, createSanctionItem(
            Material.BEDROCK,
            ChatColor.DARK_RED + "Ban Permanent",
            Arrays.asList(
                ChatColor.GRAY + "Bannit le joueur définitivement",
                "",
                ChatColor.RED + "Attention: Action irréversible",
                "",
                ChatColor.YELLOW + "Cliquez pour ban permanent"
            )
        ));

        gui.setItem(37, createSanctionItem(
            Material.BOOK,
            ChatColor.GREEN + "Templates",
            Arrays.asList(
                ChatColor.GRAY + "Sanctions prédéfinies",
                "",
                ChatColor.YELLOW + "Templates disponibles:",
                ChatColor.GRAY + "• Spam",
                ChatColor.GRAY + "• Insultes",
                ChatColor.GRAY + "• Cheat",
                ChatColor.GRAY + "• Publicité",
                "",
                ChatColor.YELLOW + "Cliquez pour voir les templates"
            )
        ));

        gui.setItem(39, createSanctionItem(
            Material.WATCH,
            ChatColor.AQUA + "Historique",
            Arrays.asList(
                ChatColor.GRAY + "Voir l'historique des sanctions",
                "",
                ChatColor.YELLOW + "Informations:",
                ChatColor.GRAY + "• Sanctions précédentes",
                ChatColor.GRAY + "• Modérateurs",
                ChatColor.GRAY + "• Raisons",
                "",
                ChatColor.YELLOW + "Cliquez pour voir l'historique"
            )
        ));

        gui.setItem(41, createSanctionItem(
            Material.BOOK_AND_QUILL,
            ChatColor.LIGHT_PURPLE + "Notes",
            Arrays.asList(
                ChatColor.GRAY + "Ajouter des notes sur le joueur",
                "",
                ChatColor.YELLOW + "Fonctionnalités:",
                ChatColor.GRAY + "• Ajouter une note",
                ChatColor.GRAY + "• Voir les notes",
                ChatColor.GRAY + "• Modifier les notes",
                "",
                ChatColor.YELLOW + "Cliquez pour gérer les notes"
            )
        ));

        gui.setItem(49, createSanctionItem(
            Material.BARRIER,
            ChatColor.RED + "Fermer",
            Arrays.asList(
                ChatColor.GRAY + "Ferme le menu de sanctions",
                "",
                ChatColor.YELLOW + "Cliquez pour fermer"
            )
        ));

        moderator.openInventory(gui);
    }

    private static ItemStack createSanctionItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createPlayerHead(String playerName, String displayName, List<String> lore) {
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwner(playerName);
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private static String getPlayTime(Player player) {
        return "Non disponible";
    }

    private static String getLastLogin(Player player) {
        return "Maintenant";
    }
} 
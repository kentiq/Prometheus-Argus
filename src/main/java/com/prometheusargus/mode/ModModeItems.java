package com.prometheusargus.mode;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ModModeItems {
    private static final String PREFIX = ChatColor.DARK_RED + "" + ChatColor.BOLD + "[Prometheus] " + ChatColor.RESET;

    public static void giveModItems(Player player) {
        player.getInventory().clear();
        
        ItemStack freezeStick = createModItem(
            Material.BLAZE_ROD,
            ChatColor.RED + "Bâton de Freeze",
            Arrays.asList(
                ChatColor.GRAY + "Clic gauche pour geler un joueur",
                ChatColor.GRAY + "Clic droit pour dégeler un joueur",
                "",
                ChatColor.YELLOW + "Effets:",
                ChatColor.GRAY + "• Immobilise le joueur",
                ChatColor.GRAY + "• Désactive les interactions",
                ChatColor.GRAY + "• Empêche la déconnexion",
                "",
                ChatColor.GOLD + "Cooldown: 3 secondes"
            ),
            true
        );
        
        ItemStack teleportStick = createModItem(
            Material.ENDER_PEARL,
            ChatColor.AQUA + "Bâton de Téléportation",
            Arrays.asList(
                ChatColor.GRAY + "Clic gauche pour se téléporter",
                ChatColor.GRAY + "Clic droit pour téléporter un joueur à vous",
                "",
                ChatColor.YELLOW + "Effets:",
                ChatColor.GRAY + "• Téléportation instantanée",
                ChatColor.GRAY + "• Protection contre les dégâts",
                ChatColor.GRAY + "• Particules de téléportation",
                "",
                ChatColor.GOLD + "Cooldown: 2 secondes"
            ),
            true
        );
        
        ItemStack inspectStick = createModItem(
            Material.STICK,
            ChatColor.YELLOW + "Bâton d'Inspection",
            Arrays.asList(
                ChatColor.GRAY + "Clic gauche pour inspecter un joueur",
                ChatColor.GRAY + "Clic droit pour voir l'inventaire",
                "",
                ChatColor.YELLOW + "Fonctionnalités:",
                ChatColor.GRAY + "• Voir les effets actifs",
                ChatColor.GRAY + "• Voir les enchantements",
                ChatColor.GRAY + "• Voir les statistiques",
                "",
                ChatColor.GOLD + "Cooldown: 1 seconde"
            ),
            true
        );
        
        ItemStack punishStick = createModItem(
            Material.BONE,
            ChatColor.LIGHT_PURPLE + "Bâton de Punition",
            Arrays.asList(
                ChatColor.GRAY + "Clic gauche pour ouvrir le menu de sanctions",
                ChatColor.GRAY + "Clic droit pour ban rapide",
                "",
                ChatColor.YELLOW + "Sanctions disponibles:",
                ChatColor.GRAY + "• Kick",
                ChatColor.GRAY + "• Ban temporaire",
                ChatColor.GRAY + "• Ban permanent",
                ChatColor.GRAY + "• Mute",
                "",
                ChatColor.GOLD + "Cooldown: 5 secondes"
            ),
            true
        );
        
        ItemStack monitorStick = createModItem(
            Material.EMERALD,
            ChatColor.GREEN + "Bâton de Surveillance",
            Arrays.asList(
                ChatColor.GRAY + "Clic gauche pour suivre un joueur",
                ChatColor.GRAY + "Clic droit pour arrêter de suivre",
                "",
                ChatColor.YELLOW + "Fonctionnalités:",
                ChatColor.GRAY + "• Vue à la première personne",
                ChatColor.GRAY + "• Voir les actions du joueur",
                ChatColor.GRAY + "• Détection des mouvements suspects",
                "",
                ChatColor.GOLD + "Cooldown: 2 secondes"
            ),
            true
        );
        
        ItemStack recallStick = createModItem(
            Material.GOLDEN_CARROT,
            ChatColor.GOLD + "Bâton de Rappel",
            Arrays.asList(
                ChatColor.GRAY + "Clic gauche pour sauvegarder une position",
                ChatColor.GRAY + "Clic droit pour retourner à la position sauvegardée",
                "",
                ChatColor.YELLOW + "Fonctionnalités:",
                ChatColor.GRAY + "• Sauvegarde de position",
                ChatColor.GRAY + "• Retour sécurisé",
                ChatColor.GRAY + "• Particules de téléportation",
                "",
                ChatColor.GOLD + "Cooldown: 1 seconde"
            ),
            true
        );

        ItemStack sanctionsMenu = createModItem(
            Material.BOOK,
            ChatColor.DARK_RED + "Menu de Sanctions",
            Arrays.asList(
                ChatColor.GRAY + "Clic pour ouvrir le menu de sanctions",
                "",
                ChatColor.YELLOW + "Fonctionnalités:",
                ChatColor.GRAY + "• Sanctions prédéfinies",
                ChatColor.GRAY + "• Historique des sanctions",
                ChatColor.GRAY + "• Templates personnalisés",
                "",
                ChatColor.GOLD + "Pas de cooldown"
            ),
            false
        );
        
        player.getInventory().setItem(0, freezeStick);
        player.getInventory().setItem(1, teleportStick);
        player.getInventory().setItem(2, inspectStick);
        player.getInventory().setItem(3, punishStick);
        player.getInventory().setItem(4, monitorStick);
        player.getInventory().setItem(5, recallStick);
        player.getInventory().setItem(8, sanctionsMenu);

        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
    }

    public static void removeModItems(Player player) {
        player.getInventory().clear();
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    private static ItemStack createModItem(Material material, String name, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            if (glow) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createPlayerHead(String playerName, String displayName, List<String> lore) {
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
} 
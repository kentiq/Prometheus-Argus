package com.prometheusargus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Date;
import org.bukkit.BanList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FreezeManager implements Listener {

    private final PrometheusArgus plugin;
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Map<UUID, Location> originalLocations = new HashMap<>();
    private final Map<UUID, GameMode> originalGameModes = new HashMap<>();

    public FreezeManager(PrometheusArgus plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isFrozen(Player player) {
        return frozenPlayers.contains(player.getUniqueId());
    }

    public void freezePlayer(Player target, Player staff) {
        if (isFrozen(target)) {
            staff.sendMessage(ChatColor.RED + target.getName() + " est déjà gelé.");
            return;
        }

        frozenPlayers.add(target.getUniqueId());
        originalLocations.put(target.getUniqueId(), target.getLocation().clone());
        originalGameModes.put(target.getUniqueId(), target.getGameMode());

        target.setGameMode(GameMode.ADVENTURE);
        target.setWalkSpeed(0.0f);
        target.setFlySpeed(0.0f);
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 128, false, false)); 
        target.setAllowFlight(false); 
        target.setFoodLevel(20); 
        target.setHealth(target.getMaxHealth()); 

        String freezeMessage = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.freeze.target_message",
                "&c&lVOUS AVEZ ÉTÉ GELÉ !\n&eNe vous déconnectez pas, sous peine de sanction.\n&eUn membre du staff va vous contacter."));
        
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (isFrozen(target) && target.isOnline()) {
                target.sendMessage(freezeMessage);
                target.playSound(target.getLocation(), org.bukkit.Sound.valueOf("NOTE_BASS_GUITAR"), 1f, 0.5f);
            }
        }, 0L, 60L); 

        staff.sendMessage(ChatColor.GREEN + target.getName() + " a été gelé. Utilisez /pa unfreeze " + target.getName() + " pour le dégeler.");
        plugin.getLogger().info("[Freeze] " + staff.getName() + " a gelé " + target.getName());
        
        plugin.getSanctionHistoryManager().addSanction(
            target.getName(), 
            "Freeze", 
            "Gelé par " + staff.getName(), 
            staff.getName(), 
            -1
        );
    }

    public void unfreezePlayer(Player target, Player staff) {
        if (!isFrozen(target)) {
            if (staff != null) {
                 staff.sendMessage(ChatColor.RED + target.getName() + " n'est pas gelé.");
            }
            return;
        }

        frozenPlayers.remove(target.getUniqueId());
        
        target.setWalkSpeed(0.2f); 
        target.setFlySpeed(0.1f);  
        target.removePotionEffect(PotionEffectType.JUMP);
        if (originalGameModes.containsKey(target.getUniqueId())) {
            target.setGameMode(originalGameModes.get(target.getUniqueId()));
            originalGameModes.remove(target.getUniqueId());
        } else {
            target.setGameMode(plugin.getServer().getDefaultGameMode());
        }
        if (target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR) {
            target.setAllowFlight(true);
        }

        if (originalLocations.containsKey(target.getUniqueId())) {
            target.teleport(originalLocations.get(target.getUniqueId()));
            originalLocations.remove(target.getUniqueId());
        }

        target.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.unfreeze.target_message",
                "&a&lVOUS AVEZ ÉTÉ DÉGELÉ.\n&eVous pouvez à nouveau bouger.")));
        target.playSound(target.getLocation(), org.bukkit.Sound.valueOf("LEVEL_UP"), 1f, 1.5f);

        String actionSource = (staff != null) ? staff.getName() : "Système";
        String actionDetail = (staff != null) ? "Dégelé par " + staff.getName() : "Auto-dégelé (déconnexion)";

        if (staff != null) {
            staff.sendMessage(ChatColor.GREEN + target.getName() + " a été dégelé.");
        }
        plugin.getLogger().info("[Freeze] " + actionSource + " a dégelé " + target.getName() + (staff == null ? " (déconnexion)" : ""));
         plugin.getSanctionHistoryManager().addSanction(
            target.getName(), 
            "Unfreeze", 
            actionDetail, 
            actionSource, 
            -1
        );
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (isFrozen(event.getPlayer())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
                Location originalLoc = originalLocations.getOrDefault(event.getPlayer().getUniqueId(), from);
                event.getPlayer().teleport(originalLoc.setDirection(to.getDirection())); 
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isFrozen(player)) {
            String command = event.getMessage().toLowerCase().split(" ")[0];
            Set<String> allowedCommands = new HashSet<>(plugin.getConfig().getStringList("freeze.allowed_commands"));
            if (!allowedCommands.contains(command.startsWith("/") ? command : "/" + command)) {
                player.sendMessage(ChatColor.RED + "Vous ne pouvez pas utiliser cette commande tant que vous êtes gelé.");
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isFrozen(player)) {
            if (plugin.getConfig().getBoolean("freeze.punish_on_disconnect", true)) {
                String banner = "PrometheusArgus (Freeze)";
                String reason = plugin.getConfig().getString("freeze.disconnect_ban_reason", "Déconnexion pendant un gel (Freeze)");
                Date expiration = null; 
                String timeStr = plugin.getConfig().getString("freeze.disconnect_ban_duration", "perm");
                if (!timeStr.equalsIgnoreCase("perm")) {
                    try {
                        expiration = plugin.parseBanDuration(timeStr); 
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Format de durée invalide pour freeze.disconnect_ban_duration: " + timeStr + ". Ban permanent appliqué.");
                    }
                }
                
                Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), reason, expiration, banner);
                plugin.getSanctionHistoryManager().addSanction(player.getName(), "AutoBan:FreezeDisconnect", reason, banner, -1);
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.freeze.disconnect_broadcast",
                    "&c[PrometheusFreeze] &e%player% &7s'est déconnecté en étant gelé et a été &4&lBANNI&7.")
                    .replace("%player%", player.getName())));
                plugin.getLogger().info(player.getName() + " s'est déconnecté en étant gelé et a été banni.");
            }
            unfreezePlayer(player, null); 
        }
    }
}
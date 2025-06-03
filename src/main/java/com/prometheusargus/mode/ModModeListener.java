package com.prometheusargus.mode;

import com.prometheusargus.PrometheusArgus;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModModeListener implements Listener {
    private final PrometheusArgus plugin;
    private final ModModeManager modModeManager;
    private final ModModeItemHandler itemHandler;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> cooldownProgress = new HashMap<>();

    public ModModeListener(PrometheusArgus plugin, ModModeManager modModeManager) {
        this.plugin = plugin;
        this.modModeManager = modModeManager;
        this.itemHandler = new ModModeItemHandler(plugin, modModeManager);
    }

    private boolean isOnCooldown(Player player, String itemName) {
        UUID playerId = player.getUniqueId();
        if (!cooldowns.containsKey(playerId)) {
            cooldowns.put(playerId, new HashMap<>());
            cooldownProgress.put(playerId, new HashMap<>());
            return false;
        }

        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (!playerCooldowns.containsKey(itemName)) {
            return false;
        }

        long lastUse = playerCooldowns.get(itemName);
        int cooldownSeconds = getCooldownForItem(itemName);
        long timeLeft = (lastUse + (cooldownSeconds * 1000)) - System.currentTimeMillis();
        
        if (timeLeft > 0) {
            // Mettre à jour la progression
            int progress = (int) ((cooldownSeconds * 1000 - timeLeft) * 100 / (cooldownSeconds * 1000));
            cooldownProgress.get(playerId).put(itemName, progress);
            return true;
        }
        
        return false;
    }

    private void setCooldown(Player player, String itemName) {
        UUID playerId = player.getUniqueId();
        if (!cooldowns.containsKey(playerId)) {
            cooldowns.put(playerId, new HashMap<>());
            cooldownProgress.put(playerId, new HashMap<>());
        }
        cooldowns.get(playerId).put(itemName, System.currentTimeMillis());
        cooldownProgress.get(playerId).put(itemName, 0);
    }

    private int getCooldownForItem(String itemName) {
        String configPath = "items." + itemName.toLowerCase().replace(" ", "-") + ".cooldown";
        return plugin.getModModeConfig().getInt(configPath, 1);
    }

    private String getMessage(String path) {
        String message = plugin.getModModeConfig().getString("messages." + path, "");
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private String getCooldownMessage(Player player, String itemName) {
        UUID playerId = player.getUniqueId();
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        Map<String, Integer> playerProgress = cooldownProgress.get(playerId);
        
        if (!playerCooldowns.containsKey(itemName) || !playerProgress.containsKey(itemName)) {
            return "";
        }

        long lastUse = playerCooldowns.get(itemName);
        int cooldownSeconds = getCooldownForItem(itemName);
        long timeLeft = (lastUse + (cooldownSeconds * 1000)) - System.currentTimeMillis();
        
        if (timeLeft <= 0) {
            return "";
        }

        int progress = playerProgress.get(itemName);
        String progressBar = createProgressBar(progress);
        
        return ChatColor.translateAlternateColorCodes('&', 
            String.format("&cCooldown: &e%d.%ds &7[%s]", 
                timeLeft / 1000, 
                (timeLeft % 1000) / 100,
                progressBar
            )
        );
    }

    private String createProgressBar(int progress) {
        int totalBars = 10;
        int filledBars = (progress * totalBars) / 100;
        
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                bar.append("&a█");
            } else {
                bar.append("&7█");
            }
        }
        
        return bar.toString();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!modModeManager.isInModMode(player)) {
            return;
        }

        if (!plugin.getModModeConfig().getBoolean("settings.allow-interactions", false)) {
            event.setCancelled(true);
        }

        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        String itemName = item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : "";
        if (isOnCooldown(player, itemName)) {
            String message = getCooldownMessage(player, itemName);
            if (!message.isEmpty()) {
                player.sendMessage(message);
            }
            event.setCancelled(true);
            return;
        }

        Player target = getTargetedPlayer(player);
        boolean isLeftClick = event.getAction().name().contains("LEFT_CLICK");

        if (itemName.contains("Freeze")) {
            itemHandler.handleFreezeStick(player, target, isLeftClick);
        } else if (itemName.contains("Téléportation")) {
            itemHandler.handleTeleportStick(player, target, isLeftClick);
        } else if (itemName.contains("Inspection")) {
            itemHandler.handleInspectStick(player, target, isLeftClick);
        } else if (itemName.contains("Punition")) {
            itemHandler.handlePunishStick(player, target, isLeftClick);
        } else if (itemName.contains("Surveillance")) {
            itemHandler.handleMonitorStick(player, target, isLeftClick);
        } else if (itemName.contains("Rappel")) {
            itemHandler.handleRecallStick(player, isLeftClick);
        }

        setCooldown(player, itemName);
    }

    private Player getTargetedPlayer(Player player) {
        Player target = null;
        double maxDistance = 5.0; // Distance maximale pour cibler un joueur

        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            if (onlinePlayer != player && onlinePlayer.getWorld() == player.getWorld()) {
                double distance = onlinePlayer.getLocation().distance(player.getLocation());
                if (distance <= maxDistance) {
                    target = onlinePlayer;
                    maxDistance = distance;
                }
            }
        }

        return target;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        // On ne bloque plus les mouvements en mode modérateur
        // Le joueur peut se déplacer et regarder autour de lui librement
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (modModeManager.isInModMode(player) && !plugin.getModModeConfig().getBoolean("settings.allow-commands", false)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas utiliser de commandes en mode modérateur.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (modModeManager.isInModMode(player) && !plugin.getModModeConfig().getBoolean("settings.allow-chat", false)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas parler en mode modérateur.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (modModeManager.isInModMode(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        if (modModeManager.isInModMode(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (modModeManager.isInModMode(player)) {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL && !plugin.getModModeConfig().getBoolean("settings.allow-fall-damage", false)) {
                    event.setCancelled(true);
                } else if (!plugin.getModModeConfig().getBoolean("settings.allow-damage", false)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (modModeManager.isInModMode(player) && !plugin.getModModeConfig().getBoolean("settings.allow-pvp", false)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (modModeManager.isInModMode(player) && !plugin.getModModeConfig().getBoolean("settings.allow-hunger", false)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player) {
            Player player = (Player) event.getTarget();
            if (modModeManager.isInModMode(player) && !plugin.getModModeConfig().getBoolean("settings.allow-mob-targeting", false)) {
                event.setCancelled(true);
            }
        }
    }
} 
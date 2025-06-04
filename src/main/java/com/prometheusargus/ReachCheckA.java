package com.prometheusargus;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.logging.Level;

public class ReachCheckA implements Listener {

    private final PrometheusArgus plugin;
    private final double MAX_REACH_DISTANCE_SQUARED;
    private final double PLAYER_HITBOX_RADIUS_APPROX = 0.4;
    private final boolean DEBUG_MODE;

    public ReachCheckA(PrometheusArgus plugin) {
        this.plugin = plugin;
        
        this.DEBUG_MODE = plugin.getConfig().getBoolean("checks.reach_a.debug_mode", true);
        double maxReach = plugin.getConfig().getDouble("checks.reach_a.max_distance", 4.2);
        
        if (DEBUG_MODE) {
            plugin.getLogger().info("[ReachCheckA DEBUG] Initializing ReachCheckA...");
            plugin.getLogger().info("[ReachCheckA DEBUG] Max reach distance from config: " + maxReach + " (Squared: " + Math.pow(maxReach, 2) + ")");
            plugin.getLogger().info("[ReachCheckA DEBUG] PLAYER_HITBOX_RADIUS_APPROX: " + PLAYER_HITBOX_RADIUS_APPROX);
        }
        
        this.MAX_REACH_DISTANCE_SQUARED = Math.pow(maxReach, 2);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        if (DEBUG_MODE) {
            plugin.getLogger().info("[ReachCheckA DEBUG] Listener registered.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (DEBUG_MODE) plugin.getLogger().info("[ReachCheckA DEBUG] EntityDamageByEntityEvent triggered.");

        if (!(event.getDamager() instanceof Player)) {
            if (DEBUG_MODE) plugin.getLogger().info("[ReachCheckA DEBUG] Damager is not a Player (" + event.getDamager().getType() + "). Exiting.");
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            if (DEBUG_MODE) plugin.getLogger().info("[ReachCheckA DEBUG] Target is not a LivingEntity (" + event.getEntity().getType() + "). Exiting.");
            return;
        }

        Player damager = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();

        if (DEBUG_MODE) plugin.getLogger().info("[ReachCheckA DEBUG] Damager: " + damager.getName() + ", Target: " + target.getName() + " (Type: " + target.getType() + ")");

        if (damager.getGameMode() == GameMode.CREATIVE || damager.getGameMode() == GameMode.SPECTATOR) {
            if (DEBUG_MODE) plugin.getLogger().info("[ReachCheckA DEBUG] Damager " + damager.getName() + " is in Creative/Spectator mode. Exiting.");
            return;
        }
        if (damager.equals(target)) {
            if (DEBUG_MODE) plugin.getLogger().info("[ReachCheckA DEBUG] Damager " + damager.getName() + " attacked self. Exiting.");
            return;
        }

        Location damagerEyeLocation = damager.getEyeLocation();
        Location targetLocation = target.getLocation();

        if (DEBUG_MODE) {
            plugin.getLogger().info("[ReachCheckA DEBUG] DamagerEyeLoc: X=" + String.format("%.2f",damagerEyeLocation.getX()) + ", Y=" + String.format("%.2f",damagerEyeLocation.getY()) + ", Z=" + String.format("%.2f",damagerEyeLocation.getZ()));
            plugin.getLogger().info("[ReachCheckA DEBUG] TargetBaseLoc: X=" + String.format("%.2f",targetLocation.getX()) + ", Y=" + String.format("%.2f",targetLocation.getY()) + ", Z=" + String.format("%.2f",targetLocation.getZ()) + ", TargetEyeHeight: " + String.format("%.2f", target.getEyeHeight()));
        }

        double dx = damagerEyeLocation.getX() - targetLocation.getX();
        double dz = damagerEyeLocation.getZ() - targetLocation.getZ();
        double horizontalDistanceSquared = (dx * dx) + (dz * dz);
        double horizontalDistance = Math.sqrt(horizontalDistanceSquared);

        double effectiveHorizontalDistance = horizontalDistance - PLAYER_HITBOX_RADIUS_APPROX;
        if (effectiveHorizontalDistance < 0) effectiveHorizontalDistance = 0;

        double targetCenterY = targetLocation.getY() + (target.getEyeHeight() / 2.0);
        double dy = damagerEyeLocation.getY() - targetCenterY;

        double totalDistanceSquared = (effectiveHorizontalDistance * effectiveHorizontalDistance) + (dy * dy);
        double totalDistance = Math.sqrt(totalDistanceSquared);

        if (DEBUG_MODE) {
            plugin.getLogger().info(String.format(
                "[ReachCheckA DEBUG] Calculated values for %s -> %s: HorizDist: %.2f, EffHorizDist: %.2f, dY: %.2f, TargetCenterY: %.2f, TotalDist: %.2f (Squared: %.2f), MAX_REACH_SQ: %.2f",
                damager.getName(), target.getName(),
                horizontalDistance, effectiveHorizontalDistance, dy, targetCenterY,
                totalDistance, totalDistanceSquared, MAX_REACH_DISTANCE_SQUARED
            ));
        }

        if (totalDistanceSquared > MAX_REACH_DISTANCE_SQUARED) {
            if (DEBUG_MODE) plugin.getLogger().info("[ReachCheckA DEBUG] REACH DETECTED for " + damager.getName() + "! Distance violation.");
            PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(damager);
            if (acData != null) {
                 plugin.flagPlayer(damager, acData,
                        "ReachA",
                        1,
                        "Dist: " + String.format("%.2f", totalDistance) +
                        " (Max: " + String.format("%.2f", Math.sqrt(MAX_REACH_DISTANCE_SQUARED)) + ")" +
                        " T: " + target.getType()
                );
            } else {
                if (DEBUG_MODE) plugin.getLogger().warning("[ReachCheckA DEBUG] PlayerACData was null for " + damager.getName() + " during flag.");
            }
        } else {
            if (DEBUG_MODE) plugin.getLogger().info("[ReachCheckA DEBUG] Reach within limits for " + damager.getName() + ".");
        }
        
        PlayerACData damagerData = plugin.getPlayerDataManager().getPlayerData(damager);
        if(damagerData != null){
            damagerData.lastAttackTime = System.currentTimeMillis();
        } else {
             if (DEBUG_MODE) plugin.getLogger().warning("[ReachCheckA DEBUG] PlayerACData was null for " + damager.getName() + " when updating lastAttackTime.");
        }
    }
    
    @SuppressWarnings("unused")
    private int getPing(Player player) {
        try {
            Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);
            return (int) entityPlayer.getClass().getField("ping").get(entityPlayer);
        } catch (Exception e) {
            if (DEBUG_MODE) plugin.getLogger().log(Level.WARNING, "[ReachCheckA DEBUG] Failed to get ping for " + player.getName(), e);
            return 0; 
        }
    }
}
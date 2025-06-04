package com.prometheusargus;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class NoFallCheck_CustomFall implements Listener {

    private final PrometheusArgus plugin;
    private final boolean DEBUG_MODE;
    private final double MIN_FALL_DISTANCE_TO_FLAG;

    private static final Set<Material> SAFE_LANDING_BLOCKS = new HashSet<>(Arrays.asList(
            Material.WATER, Material.STATIONARY_WATER, Material.LAVA, Material.STATIONARY_LAVA,
            Material.WEB, Material.SLIME_BLOCK, Material.HAY_BLOCK,
            Material.BED, Material.BED_BLOCK, Material.LADDER, Material.VINE
    ));
     private static final Set<Material> NON_SOLID_FOR_GROUND_CHECK = new HashSet<>(Arrays.asList(
            Material.AIR, Material.WATER, Material.STATIONARY_WATER, Material.LAVA, Material.STATIONARY_LAVA,
            Material.LADDER, Material.VINE, Material.WEB, Material.CARPET, Material.LONG_GRASS, Material.RED_ROSE, Material.YELLOW_FLOWER
    ));

    public NoFallCheck_CustomFall(PrometheusArgus plugin) {
        this.plugin = plugin;
        this.DEBUG_MODE = plugin.getConfig().getBoolean("checks.nofall_a.debug_mode", true);
        this.MIN_FALL_DISTANCE_TO_FLAG = plugin.getConfig().getDouble("checks.nofall_a.min_fall_distance_check", 5.0);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (DEBUG_MODE) plugin.getLogger().info("[NoFallCustom DEBUG] Initialized. MinFallToFlag: " + MIN_FALL_DISTANCE_TO_FLAG);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR || player.getAllowFlight()) {
            PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(player);
            if (acData != null) acData.resetNoFallData();
            return;
        }

        PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(player);
        if (acData == null) return;

        Location to = event.getTo();
        Location from = event.getFrom();

        Block blockBelow = to.clone().subtract(0, 0.1, 0).getBlock();
        boolean isOnSolidGround = player.isOnGround() && !NON_SOLID_FOR_GROUND_CHECK.contains(blockBelow.getType()) && blockBelow.getType().isSolid();
        
        if (player.isInsideVehicle() || isOnLadderOrVine(player) || player.getLocation().getBlock().isLiquid()) {
            if (DEBUG_MODE && acData.ticksInAir > 0) plugin.getLogger().info("[NoFallCustom DEBUG] " + player.getName() + " in vehicle/ladder/liquid. Resetting fall data.");
            acData.resetNoFallData();
            acData.lastOnGroundLocation = player.getLocation();
            return;
        }

        if (isOnSolidGround) {
            if (acData.ticksInAir > 1) { 
                if (DEBUG_MODE) plugin.getLogger().info("[NoFallCustom DEBUG] " + player.getName() + " landed on solid. CustomFallDist: " + String.format("%.2f",acData.verticalFallDistanceCounter) + ", ServerReportedFall: " + String.format("%.2f", player.getFallDistance()));

                if (acData.verticalFallDistanceCounter >= MIN_FALL_DISTANCE_TO_FLAG) {
                    Block landingBlock = blockBelow; 
                    if (isSafeLanding(player, landingBlock)) {
                        if (DEBUG_MODE) plugin.getLogger().info("[NoFallCustom DEBUG] " + player.getName() + " landed on a safe block: " + landingBlock.getType());
                    } else {
                        final double fallDistAtLanding = acData.verticalFallDistanceCounter;
                        final String playerNameForTask = player.getName();

                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            Player p = plugin.getServer().getPlayerExact(playerNameForTask);
                            if (p == null) return;
                            PlayerACData currentAcData = plugin.getPlayerDataManager().getPlayerData(p);
                            if (currentAcData == null) return;

                            if (!currentAcData.justTookFallDamage && p.getFallDistance() > 3.0) { 
                                if (DEBUG_MODE) plugin.getLogger().warning("[NoFallCustom DEBUG] " + p.getName() + " NOFALL DETECTED! CustomFall: " + String.format("%.2f", fallDistAtLanding) + ", ServerFall: " + String.format("%.2f", p.getFallDistance()) + ", No damage event received.");
                                plugin.flagPlayer(p, currentAcData,
                                        "NoFallCustom",
                                        plugin.getConfig().getInt("checks.nofall_a.vl_increment", 8),
                                        "No damage. CustomDist: " + String.format("%.2f", fallDistAtLanding) + " ServerDist: " + String.format("%.2f", p.getFallDistance())
                                );
                            } else {
                                if (DEBUG_MODE) { /* ... logs ... */ }
                            }
                            currentAcData.justTookFallDamage = false;
                        }, 2L); 
                    }
                }
            }
            acData.resetNoFallData();
            acData.lastOnGroundLocation = player.getLocation();
        } else {
            acData.ticksInAir++;
            if (acData.lastOnGroundLocation != null && from.getY() > to.getY()) { 
                acData.verticalFallDistanceCounter += (from.getY() - to.getY());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        Player player = (Player) event.getEntity();
        PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(player);
        if (acData == null) return;

        if (DEBUG_MODE) plugin.getLogger().info("[NoFallCustom DEBUG] " + player.getName() + " received FALL damage. Amount: " + event.getDamage() + ". CustomFallDist was: " + String.format("%.2f",acData.verticalFallDistanceCounter));
        acData.justTookFallDamage = true; 
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(player);
        if (acData == null) return;

        if (DEBUG_MODE) plugin.getLogger().info("[NoFallCustom DEBUG] " + player.getName() + " teleported, resetting all fall data. Cause: " + event.getCause());
        acData.resetNoFallData();
        acData.lastOnGroundLocation = event.getTo(); 
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){ 
        PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(event.getPlayer());
        if(acData != null){
            acData.resetNoFallData();
        }
    }

    private boolean isSafeLanding(Player player, Block landingBlock) {
        if (SAFE_LANDING_BLOCKS.contains(landingBlock.getType())) return true;
        if ((landingBlock.getType() == Material.LAVA || landingBlock.getType() == Material.STATIONARY_LAVA) &&
            player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) return true;
        return false;
    }
    
    private boolean isOnLadderOrVine(Player player) {
        Material type = player.getLocation().getBlock().getType();
        return type == Material.LADDER || type == Material.VINE;
    }
}
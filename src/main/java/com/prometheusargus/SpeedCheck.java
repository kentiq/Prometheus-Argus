package com.prometheusargus;

import com.prometheusargus.PlayerACData;
import com.prometheusargus.PrometheusArgus;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class SpeedCheck implements Listener {

    private final PrometheusArgus plugin;
    private final Set<Material> climbableBlocks = new HashSet<>();

    private double speedA_baseMaxSpeedPerTick;
    private double speedA_sprintMultiplier;
    private double speedA_sneakMultiplier;
    private double speedA_potionMultiplierPerLevel;
    private double speedA_jumpBoostLeniency;
    private double speedA_airLeniency;
    private double speedA_iceMultiplier;
    private double speedA_webDivisor;

    private double speedB_maxNormalJumpHeight;
    private double speedB_potionJumpMultiplierPerLevel;

    private double speedC_packetSpeedThreshold;
    private double speedC_maxTheoreticalSpeed;

    private int speedD_groundLeniencyTicks;


    public SpeedCheck(PrometheusArgus plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        loadConfigValuesFromMainPlugin();

        climbableBlocks.add(Material.LADDER);
        climbableBlocks.add(Material.VINE);
    }

    private void loadConfigValuesFromMainPlugin() {
        speedA_baseMaxSpeedPerTick = plugin.getConfig().getDouble("checks.speed_a.base_max_speed_per_tick", 0.288);
        speedA_sprintMultiplier = plugin.getConfig().getDouble("checks.speed_a.sprint_multiplier", 1.3);
        speedA_sneakMultiplier = plugin.getConfig().getDouble("checks.speed_a.sneak_multiplier", 0.42);
        speedA_potionMultiplierPerLevel = plugin.getConfig().getDouble("checks.speed_a.potion_multiplier_per_level", 0.20);
        speedA_jumpBoostLeniency = plugin.getConfig().getDouble("checks.speed_a.jump_boost_leniency", 1.25);
        speedA_airLeniency = plugin.getConfig().getDouble("checks.speed_a.air_leniency", 1.15);
        speedA_iceMultiplier = plugin.getConfig().getDouble("checks.speed_a.ice_multiplier", 1.8);
        speedA_webDivisor = plugin.getConfig().getDouble("checks.speed_a.web_divisor", 4.0);

        speedB_maxNormalJumpHeight = plugin.getConfig().getDouble("checks.speed_b_jump.max_normal_jump_height", 1.253);
        speedB_potionJumpMultiplierPerLevel = plugin.getConfig().getDouble("checks.speed_b_jump.potion_jump_multiplier_per_level", 0.5);

        speedC_packetSpeedThreshold = plugin.getConfig().getDouble("checks.speed_c_packet.packet_speed_threshold", 1.5);
        speedC_maxTheoreticalSpeed = plugin.getConfig().getDouble("checks.speed_c_packet.max_theoretical_speed_bps", 7.5);

        speedD_groundLeniencyTicks = plugin.getConfig().getInt("checks.speed_d_ground.leniency_ticks", 5);
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPlayerDataManager() == null) return;
        PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(player);

        if (acData == null || player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE ||
            player.isFlying() || player.getAllowFlight() || player.isInsideVehicle() || player.isDead()) {
            if (acData != null && event.getTo() != null) acData.setLastMoveLocation(event.getTo());
            return;
        }

        if (player.hasPermission("prometheusargus.bypass.speed")) {
             if (acData != null && event.getTo() != null) acData.setLastMoveLocation(event.getTo());
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        long currentTime = System.currentTimeMillis();

        if (acData.getLastMoveLocation() == null) {
            acData.setLastMoveLocation(from);
            acData.setLastMoveTime(currentTime - 50);
            acData.setWasOnGroundSpeed(player.isOnGround());
            return;
        }
        
        Location actualLastLocation = acData.getLastMoveLocation();

        if (actualLastLocation.getX() == to.getX() && actualLastLocation.getY() == to.getY() && actualLastLocation.getZ() == to.getZ()) {
            acData.setWasOnGroundSpeed(player.isOnGround());
            return;
        }
        
        if (acData.hasBeenTeleportedRecently(currentTime, 1500) ||
            acData.wasVelocityAppliedRecently(currentTime, 1000)) {
            acData.setLastMoveLocation(to);
            acData.setLastMoveTime(currentTime);
            acData.setWasOnGroundSpeed(player.isOnGround());
            return;
        }
        
        checkSpeedA_D(player, acData, actualLastLocation, to);
        checkSpeedB_Jump(player, acData, actualLastLocation, to);
        if (plugin.getConfig().getBoolean("checks.speed_c_packet.enabled", false)) {
            checkSpeedC_Packet(player, acData, actualLastLocation, to, currentTime);
        }

        acData.setLastMoveLocation(to);
        acData.setLastMoveTime(currentTime);
        acData.setWasOnGroundSpeed(isOnGroundCustom(player));
        acData.setLastYDiffSpeed(to.getY() - actualLastLocation.getY());
    }

    @SuppressWarnings("deprecation")
    private void checkSpeedA_D(Player player, PlayerACData acData, Location from, Location to) {
        double deltaX = to.getX() - from.getX();
        double deltaZ = to.getZ() - from.getZ();
        double distanceHorizontalSq = deltaX * deltaX + deltaZ * deltaZ;

        if (distanceHorizontalSq == 0) return;
        double distanceHorizontal = Math.sqrt(distanceHorizontalSq);

        double maxAllowedSpeed = speedA_baseMaxSpeedPerTick;
        boolean currentTickIsOnGround = isOnGroundCustom(player);

        if (player.isSprinting()) {
            maxAllowedSpeed *= speedA_sprintMultiplier;
        } else if (player.isSneaking()) {
            maxAllowedSpeed *= speedA_sneakMultiplier;
        }

        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (effect.getType().equals(PotionEffectType.SPEED)) {
                    maxAllowedSpeed *= (1.0 + (speedA_potionMultiplierPerLevel * (effect.getAmplifier() + 1)));
                    break;
                }
            }
        }
        if (player.hasPotionEffect(PotionEffectType.SLOW)) {
             for (PotionEffect effect : player.getActivePotionEffects()) {
                if (effect.getType().equals(PotionEffectType.SLOW)) {
                    maxAllowedSpeed *= (1.0 - (0.15 * (effect.getAmplifier() + 1)));
                    break;
                }
            }
        }

        if (!currentTickIsOnGround && acData.wasOnGroundSpeed()) {
            maxAllowedSpeed *= speedA_jumpBoostLeniency;
        } else if (!currentTickIsOnGround) {
            maxAllowedSpeed *= speedA_airLeniency;
            acData.onLadderOrVine = isClimbing(player);
            if (acData.onLadderOrVine) maxAllowedSpeed *= 1.5;
        }
        
        Material blockBelow = player.getLocation().getBlock().getRelative(BlockFace.DOWN).getType();
        Material blockAtFeet = player.getLocation().getBlock().getType();

        if (blockBelow == Material.ICE || blockBelow == Material.PACKED_ICE) {
            maxAllowedSpeed *= speedA_iceMultiplier;
        }
        if (blockAtFeet == Material.WEB) {
            maxAllowedSpeed /= speedA_webDivisor;
        }
        if (blockBelow == Material.SOUL_SAND) {
            maxAllowedSpeed *= 0.4;
        }
        
        Block playerBlock = player.getLocation().getBlock();
        if (playerBlock.isLiquid() || player.getEyeLocation().getBlock().isLiquid()) {
             boolean hasDepthStrider = false;
             if (player.getInventory().getBoots() != null && player.getInventory().getBoots().containsEnchantment(Enchantment.DEPTH_STRIDER)) {
                 hasDepthStrider = true;
                 int dsLevel = player.getInventory().getBoots().getEnchantmentLevel(Enchantment.DEPTH_STRIDER);
                 if (dsLevel >= 3) maxAllowedSpeed *= 2.0; 
                 else if (dsLevel == 2) maxAllowedSpeed *= 1.6;
                 else if (dsLevel == 1) maxAllowedSpeed *= 1.3;
             }
             if (!hasDepthStrider) maxAllowedSpeed *= 0.7; 
        }


        maxAllowedSpeed += 0.005;

        if (distanceHorizontal > maxAllowedSpeed) {
            String debugInfo = String.format("H-Dist: %.3f (Max: %.3f) OnG: %b Sprint: %b Sneak: %b AirTSpeed: %d Blw:%s",
                    distanceHorizontal, maxAllowedSpeed - 0.005, currentTickIsOnGround, player.isSprinting(), player.isSneaking(), acData.getAirTicksSpeed(), blockBelow.name());
            plugin.flagPlayer(player, acData, "SpeedA", 1, debugInfo);
        }

        if (currentTickIsOnGround && acData.getGroundTicks() > speedD_groundLeniencyTicks) {
            double maxStrictGroundSpeed = speedA_baseMaxSpeedPerTick;
            if (player.isSprinting()) maxStrictGroundSpeed *= speedA_sprintMultiplier;
            if (player.hasPotionEffect(PotionEffectType.SPEED)) {
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    if (effect.getType().equals(PotionEffectType.SPEED)) {
                        maxStrictGroundSpeed *= (1.0 + (speedA_potionMultiplierPerLevel * (effect.getAmplifier() + 1)));
                        break;
                    }
                }
            }
            if (blockBelow == Material.ICE || blockBelow == Material.PACKED_ICE) maxStrictGroundSpeed *= speedA_iceMultiplier;
            
            maxStrictGroundSpeed += 0.002;
            if (distanceHorizontal > maxStrictGroundSpeed) {
                 String debugInfo = String.format("Ground-Dist: %.3f (StrictMax: %.3f) GTicks: %d",
                    distanceHorizontal, maxStrictGroundSpeed-0.002, acData.getGroundTicks());
                plugin.flagPlayer(player, acData, "SpeedD_Ground", 1, debugInfo);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void checkSpeedB_Jump(Player player, PlayerACData acData, Location from, Location to) {
        double deltaY = to.getY() - from.getY();

        if (deltaY <= 0.0001 || !acData.wasOnGroundSpeed() || isOnGroundCustom(player)) {
            return;
        }
        acData.onLadderOrVine = isClimbing(player);
        if (acData.onLadderOrVine) return;
        
        Material blockBelowFrom = from.getBlock().getRelative(BlockFace.DOWN).getType();
        if (blockBelowFrom == Material.SLIME_BLOCK) {
            return;
        }

        double maxJumpHeight = speedB_maxNormalJumpHeight;
        if (player.hasPotionEffect(PotionEffectType.JUMP)) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (effect.getType().equals(PotionEffectType.JUMP)) {
                    maxJumpHeight += (effect.getAmplifier() + 1) * speedB_potionJumpMultiplierPerLevel;
                    break;
                }
            }
        }
        maxJumpHeight += 0.05;

        if (deltaY > acData.getLastYDiffSpeed() && deltaY > 0.42 && deltaY > maxJumpHeight ) {
             String debugInfo = String.format("JumpY: %.3f (MaxHeight: %.3f) Pot: %b",
                    deltaY, maxJumpHeight-0.05, player.hasPotionEffect(PotionEffectType.JUMP));
            plugin.flagPlayer(player, acData, "SpeedB_Jump", 1, debugInfo);
        }
    }

    private void checkSpeedC_Packet(Player player, PlayerACData acData, Location from, Location to, long currentTime) {
        long timeDiff = currentTime - acData.getLastMoveTime();
        if (timeDiff <= 1) timeDiff = 50;

        double distance3D = from.distance(to);
        double packetSpeedBPS = (distance3D * 1000.0) / timeDiff;

        if (packetSpeedBPS > speedC_maxTheoreticalSpeed * speedC_packetSpeedThreshold) {
            acData.addToPacketSpeedBuffer(packetSpeedBPS - (speedC_maxTheoreticalSpeed * speedC_packetSpeedThreshold));
        } else {
            acData.addToPacketSpeedBuffer(-speedC_maxTheoreticalSpeed * 0.1);
        }

        if (acData.getPacketSpeedBuffer() > speedC_maxTheoreticalSpeed * 2.5) {
             acData.incrementPacketSpeedViolations();
             acData.resetPacketSpeedBuffer();
            if (acData.getPacketSpeedViolations() > 2) {
                String debugInfo = String.format("PacketSpeed: %.2fbps (Buffer>%.2f)", packetSpeedBPS, speedC_maxTheoreticalSpeed * 2.5);
                plugin.flagPlayer(player, acData, "SpeedC_Packet", 2, debugInfo);
                acData.resetPacketSpeedViolations();
            }
        } else if (acData.getPacketSpeedViolations() > 0 && isOnGroundCustom(player)) {
            acData.resetPacketSpeedViolations();
        }
    }
    
    private boolean isClimbing(Player player) {
        Material currentBlock = player.getLocation().getBlock().getType();
        return climbableBlocks.contains(currentBlock);
    }
    
    @SuppressWarnings("deprecation")
    private boolean isOnGroundCustom(Player player) {
        if (player.isOnGround()) return true;

        Location loc = player.getLocation();
        double r = 0.29;
        double yOffset = -0.015;
        
        for (double xOffset : new double[]{-r, r}) {
            for (double zOffset : new double[]{-r, r}) {
                Block block = loc.clone().add(xOffset, yOffset, zOffset).getBlock();
                if (isConsideredSolidForGround(block.getType())) {
                    return true;
                }
            }
        }
        Block centerBlock = loc.clone().add(0, yOffset, 0).getBlock();
        if (isConsideredSolidForGround(centerBlock.getType())) {
            return true;
        }

        Block blockDirectlyBelow = loc.clone().add(0, -0.1, 0).getBlock();
         if (isConsideredSolidForGround(blockDirectlyBelow.getType())) {
             return true;
         }
        return false;
    }

    private boolean isConsideredSolidForGround(Material material) {
        if (!material.isSolid() || material == Material.AIR || material.name().contains("SIGN") || material.name().contains("PLATE") ||
            material == Material.TRAP_DOOR || material == Material.IRON_TRAPDOOR || material.name().contains("FENCE") ||
            material.name().contains("WALL") || material == Material.LADDER || material == Material.VINE ||
            material.name().contains("CARPET") || material.name().contains("SLAB") && !material.name().contains("DOUBLE_SLAB") /* dalles simples */) {
            return false;
        }
        return true;
    }
}
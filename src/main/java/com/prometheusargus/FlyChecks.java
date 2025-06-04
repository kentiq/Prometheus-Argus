package com.prometheusargus;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class FlyChecks implements Listener {

    private final PrometheusArgus plugin;
    private final boolean DEBUG_MODE;

    private final double MAX_VERTICAL_GAIN_PER_TICK;
    private final double MAX_VERTICAL_GAIN_JUMP;
    private final int MAX_TICKS_HOVERING;
    private final double MIN_Y_CHANGE_TO_RESET_HOVER;
    private final int INVALID_GROUND_BUFFER_THRESHOLD;
    private final int VL_INCREMENT_VERTICAL;
    private final int VL_INCREMENT_HOVER;
    private final int VL_INCREMENT_INVALID_GROUND;

    private final double MIN_GLIDE_DELTA_Y;
    private final double MAX_GLIDE_DELTA_Y;
    private final int MIN_TICKS_IN_AIR_FOR_GLIDE;
    private final int GLIDE_BUFFER_THRESHOLD;
    private final int VL_INCREMENT_GLIDE;

    private static final Set<Material> CLIMBABLE_BLOCKS = new HashSet<>();
    static {
        CLIMBABLE_BLOCKS.add(Material.LADDER);
        CLIMBABLE_BLOCKS.add(Material.VINE);
    }

    private static PotionEffectType SLOW_FALLING_EFFECT_TYPE = null;
    private static Method IS_GLIDING_METHOD = null;
    private static boolean REFLECTION_INITIALIZED = false;

    public FlyChecks(PrometheusArgus plugin) {
        this.plugin = plugin;
        String flyA_path = "checks.fly_a.";
        String flyB_path = "checks.fly_b_glide.";

        this.DEBUG_MODE = plugin.getConfig().getBoolean(flyA_path + "debug_mode", false) || plugin.getConfig().getBoolean(flyB_path + "debug_mode", false);

        this.MAX_VERTICAL_GAIN_PER_TICK = plugin.getConfig().getDouble(flyA_path + "max_vertical_gain_tick", 0.52);
        this.MAX_VERTICAL_GAIN_JUMP = plugin.getConfig().getDouble(flyA_path + "max_vertical_gain_jump", 1.35);
        this.MAX_TICKS_HOVERING = plugin.getConfig().getInt(flyA_path + "max_ticks_hovering", 25);
        this.MIN_Y_CHANGE_TO_RESET_HOVER = plugin.getConfig().getDouble(flyA_path + "min_y_change_reset_hover", -0.0785);
        this.INVALID_GROUND_BUFFER_THRESHOLD = plugin.getConfig().getInt(flyA_path + "invalid_ground_buffer_threshold", 5);
        this.VL_INCREMENT_VERTICAL = plugin.getConfig().getInt(flyA_path + "vl_increment_vertical", 3);
        this.VL_INCREMENT_HOVER = plugin.getConfig().getInt(flyA_path + "vl_increment_hover", 2);
        this.VL_INCREMENT_INVALID_GROUND = plugin.getConfig().getInt(flyA_path + "vl_increment_invalid_ground", 2);

        this.MIN_GLIDE_DELTA_Y = plugin.getConfig().getDouble(flyB_path + "min_glide_delta_y", -0.150);
        this.MAX_GLIDE_DELTA_Y = plugin.getConfig().getDouble(flyB_path + "max_glide_delta_y", -0.001);
        this.MIN_TICKS_IN_AIR_FOR_GLIDE = plugin.getConfig().getInt(flyB_path + "min_ticks_in_air_for_glide", 15);
        this.GLIDE_BUFFER_THRESHOLD = plugin.getConfig().getInt(flyB_path + "glide_buffer_threshold", 4);
        this.VL_INCREMENT_GLIDE = plugin.getConfig().getInt(flyB_path + "vl_increment", 4);

        initializeReflection();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (DEBUG_MODE) plugin.getLogger().info("[FlyChecks DEBUG] Initialized.");
    }

    private void initializeReflection() {
        if (REFLECTION_INITIALIZED) return;
        try {
            SLOW_FALLING_EFFECT_TYPE = PotionEffectType.getByName("SLOW_FALLING");
            if (SLOW_FALLING_EFFECT_TYPE == null && DEBUG_MODE) {
                plugin.getLogger().info("[FlyChecks DEBUG] PotionEffectType.SLOW_FALLING not found (likely pre-1.13 server).");
            }
        } catch (Throwable t) {
            if (DEBUG_MODE) plugin.getLogger().log(Level.WARNING, "[FlyChecks DEBUG] Error resolving PotionEffectType.SLOW_FALLING.", t);
        }

        try {
            IS_GLIDING_METHOD = Player.class.getMethod("isGliding");
            if (DEBUG_MODE) plugin.getLogger().info("[FlyChecks DEBUG] Player.isGliding() method found.");
        } catch (NoSuchMethodException e) {
            if (DEBUG_MODE) plugin.getLogger().info("[FlyChecks DEBUG] Player.isGliding() method not found (likely pre-1.9 server).");
        } catch (Throwable t) {
            if (DEBUG_MODE) plugin.getLogger().log(Level.WARNING, "[FlyChecks DEBUG] Error resolving Player.isGliding() method.", t);
        }
        REFLECTION_INITIALIZED = true;
    }

    private boolean hasSlowFallingEffect(Player player) {
        if (SLOW_FALLING_EFFECT_TYPE == null) {
            return false;
        }
        return player.hasPotionEffect(SLOW_FALLING_EFFECT_TYPE);
    }

    private boolean isPlayerGliding(Player player) {
        if (IS_GLIDING_METHOD == null) {
            return false;
        }
        try {
            return (boolean) IS_GLIDING_METHOD.invoke(player);
        } catch (Exception e) {
            if (DEBUG_MODE) plugin.getLogger().log(Level.WARNING, "[FlyChecks DEBUG] Error invoking isGliding() method.", e);
            return false;
        }
    }

    private boolean isInWeb(Player player) {
        return player.getLocation().getBlock().getType() == Material.WEB;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR || player.getAllowFlight()) {
            PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(player);
            if (acData != null) acData.resetFlyAbilityData(player.getLocation());
            return;
        }

        PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(player);
        if (acData == null) return;

        Location to = event.getTo();
        Location from = event.getFrom();

        if (from.getWorld().equals(to.getWorld()) &&
            from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            if (from.getPitch() == to.getPitch() && from.getYaw() == to.getYaw()){
                acData.lastYPositionFly = to.getY();
                return;
            }
        }

        Block blockBelow = to.getBlock().getRelative(BlockFace.DOWN);
        boolean isServerOnGround = player.isOnGround();
        boolean isNearSolidGround = isNearGround(player, blockBelow);
        boolean isInLiquid = player.getLocation().getBlock().isLiquid() || blockBelow.isLiquid();
        acData.onLadderOrVine = isOnClimbable(player);

        boolean hasSlowFalling = hasSlowFallingEffect(player);
        boolean isElytraGliding = isPlayerGliding(player);
        boolean isInsideWeb = isInWeb(player);

        if (isNearSolidGround || isInLiquid || acData.onLadderOrVine || player.isInsideVehicle() || hasRecentlyBouncedOnSlime(acData) || isInsideWeb) {
            if (DEBUG_MODE && acData.flyAbilityTicksInAir > 0) {
                String reason = isNearSolidGround ? "NearGround" : isInLiquid ? "InLiquid" : acData.onLadderOrVine ? "Climbable" : player.isInsideVehicle() ? "Vehicle" : hasRecentlyBouncedOnSlime(acData) ? "Slime" : "InWeb";
                plugin.getLogger().info("[FlyChecks DEBUG] " + player.getName() + " on support ("+reason+"). Resetting fly data. InAirFor: " + acData.flyAbilityTicksInAir + "t");
            }
            acData.resetFlyAbilityData(to);
            acData.lastOnGroundLocation = to;
            return;
        }

        if (isServerOnGround && !isNearSolidGround && !isInLiquid && !acData.onLadderOrVine && !player.isInsideVehicle() && !isInsideWeb) {
            double yDiffForGroundCheck = to.getY() - from.getY();
            if (acData.flyAbilityTicksInAir > 2 || yDiffForGroundCheck > 0.03126 ) {
                acData.invalidGroundStateVLBuffer++;
                if (acData.invalidGroundStateVLBuffer >= INVALID_GROUND_BUFFER_THRESHOLD) {
                    if (DEBUG_MODE) plugin.getLogger().warning("[FlyChecks DEBUG] " + player.getName() + " INVALID GROUND STATE! ClientOnGround=true, ServerCalcOnGround=false. Buffer: " + acData.invalidGroundStateVLBuffer);
                    plugin.flagPlayer(player, acData, "FlyA_InvalidGround", VL_INCREMENT_INVALID_GROUND, "Spoofed Ground? dY: " + String.format("%.3f",yDiffForGroundCheck));
                    acData.invalidGroundStateVLBuffer = 0;
                }
            }
        } else if (isNearSolidGround || isInLiquid || acData.onLadderOrVine) {
            acData.invalidGroundStateVLBuffer = 0;
        }

        acData.flyAbilityTicksInAir++;
        double deltaY = to.getY() - from.getY();

        double maxAllowedYGainThisTick = (acData.flyAbilityTicksInAir == 1 && acData.lastOnGroundLocation != null) ? MAX_VERTICAL_GAIN_JUMP : MAX_VERTICAL_GAIN_PER_TICK;

        if (player.hasPotionEffect(PotionEffectType.JUMP) && acData.flyAbilityTicksInAir <= 2) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (effect.getType().equals(PotionEffectType.JUMP)) {
                    maxAllowedYGainThisTick += (effect.getAmplifier() + 1) * 0.42;
                    if (DEBUG_MODE) plugin.getLogger().info("[FlyChecks DEBUG] " + player.getName() + " Jump Boost! New maxAllowedYGain: " + String.format("%.4f", maxAllowedYGainThisTick));
                    break;
                }
            }
        }

        if (deltaY > maxAllowedYGainThisTick && deltaY > 0.0157 && !isAscendingNearBlock(player) && !isElytraGliding) {
            if (DEBUG_MODE) plugin.getLogger().warning("[FlyChecks DEBUG] " + player.getName() + " ANOMALOUS VERTICAL GAIN! dY: " + String.format("%.4f", deltaY) + ", Max: " + String.format("%.4f", maxAllowedYGainThisTick) + ", InAir: " + acData.flyAbilityTicksInAir);
            plugin.flagPlayer(player, acData, "FlyA_Vertical", VL_INCREMENT_VERTICAL, "VertGain: " + String.format("%.3f", deltaY));
        }

        if (!hasSlowFalling && !isElytraGliding) {
            if (deltaY >= MIN_Y_CHANGE_TO_RESET_HOVER) {
                acData.ticksHovering++;
            } else {
                acData.ticksHovering = 0;
            }
            acData.highestYReachedInAir = Math.max(acData.highestYReachedInAir, to.getY());

            if (acData.ticksHovering > MAX_TICKS_HOVERING) {
                if (DEBUG_MODE) plugin.getLogger().warning("[FlyChecks DEBUG] " + player.getName() + " HOVERING! HoverTicks: " + acData.ticksHovering + ", dY: " + String.format("%.4f",deltaY));
                plugin.flagPlayer(player, acData, "FlyA_Hover", VL_INCREMENT_HOVER, "Hover: " + acData.ticksHovering + "t, dY: " + String.format("%.3f",deltaY));
                acData.ticksHovering = 0;
            }
        } else {
            acData.ticksHovering = 0;
            if (DEBUG_MODE && (hasSlowFalling || isElytraGliding)) plugin.getLogger().info("[FlyChecks DEBUG] " + player.getName() + " has SlowFalling/Elytra, resetting hover ticks. dY: " + String.format("%.4f",deltaY));
        }

        if (!hasSlowFalling && !isElytraGliding && deltaY < 0 && acData.flyAbilityTicksInAir > MIN_TICKS_IN_AIR_FOR_GLIDE) {
            if (deltaY > MIN_GLIDE_DELTA_Y && deltaY < MAX_GLIDE_DELTA_Y) {
                acData.glideVLBuffer++;
                if (acData.glideVLBuffer >= GLIDE_BUFFER_THRESHOLD) {
                    if (DEBUG_MODE) plugin.getLogger().warning("[FlyChecks DEBUG] " + player.getName() + " GLIDING! dY: " + String.format("%.4f", deltaY) + " for " + acData.glideVLBuffer + " checks. InAir: " + acData.flyAbilityTicksInAir);
                    plugin.flagPlayer(player, acData, "FlyB_Glide", VL_INCREMENT_GLIDE, "Glide: dY " + String.format("%.3f", deltaY) + " for " + acData.glideVLBuffer + "t");
                    acData.glideVLBuffer = 0;
                }
            } else {
                acData.glideVLBuffer = Math.max(0, acData.glideVLBuffer - 1);
            }
        } else if (deltaY >=0 || hasSlowFalling || isElytraGliding) {
            if (DEBUG_MODE && acData.glideVLBuffer > 0 && (hasSlowFalling || isElytraGliding)) plugin.getLogger().info("[FlyChecks DEBUG] " + player.getName() + " has SlowFalling/Elytra or not descending, resetting glide buffer.");
            acData.glideVLBuffer = 0;
        }

        acData.lastYPositionFly = to.getY();
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(event.getPlayer());
        if (acData != null) {
            if (DEBUG_MODE) plugin.getLogger().info("[FlyChecks DEBUG] " + event.getPlayer().getName() + " teleported. Resetting fly data. Cause: " + event.getCause());
            acData.resetFlyAbilityData(event.getTo());
            if (event.getPlayer().isOnGround() || isNearGround(event.getPlayer(), event.getTo().getBlock().getRelative(BlockFace.DOWN))) {
                 acData.lastOnGroundLocation = event.getTo();
            } else {
                 acData.lastOnGroundLocation = null;
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamageBySlime(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Player player = (Player) event.getEntity();
            Block blockBelow = event.getEntity().getLocation().getBlock().getRelative(BlockFace.DOWN);
            if (blockBelow.getType() == Material.SLIME_BLOCK) {
                PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(player);
                if (acData != null) {
                    acData.lastSlimeBounceTime = System.currentTimeMillis();
                    if (DEBUG_MODE) plugin.getLogger().info("[FlyChecks DEBUG] " + player.getName() + " bounced on slime block (detected via fall damage event).");
                    acData.flyAbilityTicksInAir = 0;
                    acData.ticksHovering = 0;
                    acData.glideVLBuffer = 0;
                    acData.lastOnGroundLocation = player.getLocation();
                }
            }
        }
    }

    private boolean hasRecentlyBouncedOnSlime(PlayerACData acData) {
        return (System.currentTimeMillis() - acData.lastSlimeBounceTime) < 1500;
    }

    private boolean isOnClimbable(Player player) {
        Material typeInPlayer = player.getLocation().getBlock().getType();
        Material typeAtEye = player.getEyeLocation().getBlock().getType();
        return CLIMBABLE_BLOCKS.contains(typeInPlayer) || CLIMBABLE_BLOCKS.contains(typeAtEye);
    }

    @SuppressWarnings("deprecation")
    private boolean isNearGround(Player player, Block blockBelow) {
        if (player.isOnGround()) return true;

        if (blockBelow.getType().isSolid() && !blockBelow.isLiquid()) {
            double playerFeetY = player.getLocation().getY();
            double blockTopY = blockBelow.getY() + 1.0;
            return (playerFeetY - blockTopY) < 0.20 && (playerFeetY - blockTopY) >= -0.016;
        }
        return false;
    }

    private boolean isAscendingNearBlock(Player player){
        Location loc = player.getLocation();
        BlockFace[] horizontalFaces = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.NORTH_EAST, BlockFace.NORTH_WEST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST
        };
        for(BlockFace face : horizontalFaces){
            Block feetRelative = loc.getBlock().getRelative(face);
            Block headRelative = loc.clone().add(0,1,0).getBlock().getRelative(face);
            if (feetRelative.getType().isSolid() || headRelative.getType().isSolid()){
                return true;
            }
        }
        return false;
    }
}
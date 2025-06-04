package com.prometheusargus;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class KnockbackCheck implements Listener {

    private final PrometheusArgus plugin;
    private final boolean DEBUG_MODE;

    private final double MIN_EXPECTED_KB_XZ_TO_CHECK;
    private final double MIN_KB_RATIO_TAKEN;
    private final int KB_CHECK_TICKS_WINDOW;
    private final int VL_INCREMENT;
    private final double KNOCKBACK_BASE_STRENGTH = 0.4;
    private final double KNOCKBACK_ENCHANT_MODIFIER = 0.5;

    public KnockbackCheck(PrometheusArgus plugin) {
        this.plugin = plugin;
        String path = "checks.knockback_a.";

        this.DEBUG_MODE = plugin.getConfig().getBoolean(path + "debug_mode", false);
        this.MIN_EXPECTED_KB_XZ_TO_CHECK = plugin.getConfig().getDouble(path + "min_expected_kb_to_check", 0.05);
        this.MIN_KB_RATIO_TAKEN = plugin.getConfig().getDouble(path + "min_kb_ratio_taken", 0.20);
        this.KB_CHECK_TICKS_WINDOW = plugin.getConfig().getInt(path + "check_ticks_window", 4);
        this.VL_INCREMENT = plugin.getConfig().getInt(path + "vl_increment", 2);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (DEBUG_MODE) plugin.getLogger().info("[KnockbackCheck DEBUG] Initialized. MinRatio: " + MIN_KB_RATIO_TAKEN + ", Window: " + KB_CHECK_TICKS_WINDOW + "t");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(victim);

        if (acData == null || victim.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.SPECTATOR || victim.getAllowFlight() || victim.isInsideVehicle()) {
            if (acData != null) acData.resetKnockbackData();
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();
        switch (cause) {
            case FALL:
            case DROWNING:
            case SUFFOCATION:
            case STARVATION:
            case POISON:
            case WITHER:
            case FIRE_TICK:
            case VOID:
                return;
            default:
                break;
        }
        if (isPost1_9() && cause.name().equals("FLY_INTO_WALL")) {
            return;
        }
        
        if (victim.getLocation().getBlock().isLiquid() || victim.getLocation().getBlock().getType() == Material.WEB || isObstructed(victim, event)) {
            if (DEBUG_MODE) plugin.getLogger().info("[KnockbackCheck DEBUG] " + victim.getName() + " obstructed or in fluid/web. KB check may be lenient or skipped.");
        }

        acData.lastDamageTime = System.currentTimeMillis();
        acData.ticksSinceLastDamage = 0;
        acData.locationAtDamage = victim.getLocation().clone();
        acData.tookSignificantKnockback = false;

        double kbHorizontalStrength = KNOCKBACK_BASE_STRENGTH;
        Vector damageDirection = new Vector(0, 0, 0);

        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent edbeEvent = (EntityDamageByEntityEvent) event;
            Entity damager = edbeEvent.getDamager();
            
            if (damager instanceof Arrow) {
                Arrow arrow = (Arrow) damager;
                if (arrow.getShooter() instanceof Entity) {
                    damager = (Entity) arrow.getShooter();
                }
            }

            if(damager != victim) {
                damageDirection = victim.getLocation().toVector().subtract(damager.getLocation().toVector());
                damageDirection.setY(0);
                if (damageDirection.lengthSquared() > 0.001) {
                    damageDirection.normalize();
                } else { 
                    damageDirection = victim.getLocation().getDirection().multiply(-1).setY(0).normalize();
                    if (damageDirection.lengthSquared() == 0) damageDirection = new Vector(1,0,0);
                }
            }

            if (damager instanceof Player) {
                Player attacker = (Player) damager;
                ItemStack itemInHand = attacker.getItemInHand();
                if (itemInHand != null && itemInHand.getType() != Material.AIR && itemInHand.containsEnchantment(Enchantment.KNOCKBACK)) {
                    int kbLevel = itemInHand.getEnchantmentLevel(Enchantment.KNOCKBACK);
                    kbHorizontalStrength += kbLevel * KNOCKBACK_ENCHANT_MODIFIER;
                    if (DEBUG_MODE) plugin.getLogger().info("[KnockbackCheck DEBUG] Attacker " + attacker.getName() + " has KB " + kbLevel + ". KB Strength: " + kbHorizontalStrength);
                }
            }
        } else { 
            kbHorizontalStrength *= 0.25; 
            damageDirection = victim.getLocation().getDirection().multiply(-1).setY(0).normalize();
             if (damageDirection.lengthSquared() == 0) damageDirection = new Vector(1,0,0);
        }

        acData.expectedKnockbackXZ = kbHorizontalStrength;
        acData.expectedKnockbackDirection = damageDirection;

        if (DEBUG_MODE) {
            plugin.getLogger().info(String.format("[KnockbackCheck DEBUG] %s damaged by %s. ExpectedKB_XZ: %.3f, Dir: (%.2f, %.2f)",
                    victim.getName(), event.getCause().name(), acData.expectedKnockbackXZ,
                    acData.expectedKnockbackDirection.getX(), acData.expectedKnockbackDirection.getZ()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(player);

        if (acData == null || acData.lastDamageTime == 0 || acData.locationAtDamage == null || acData.expectedKnockbackDirection == null) {
            return;
        }

        if (acData.tookSignificantKnockback) {
            return;
        }

        long timeSinceDamageMs = System.currentTimeMillis() - acData.lastDamageTime;
        int currentTickAfterDamage = (int) (timeSinceDamageMs / 50);

        if (currentTickAfterDamage < 1 && event.getFrom().distanceSquared(acData.locationAtDamage) < 0.001) {
             if (DEBUG_MODE && timeSinceDamageMs < 50) plugin.getLogger().info("[KnockbackCheck DEBUG] " + player.getName() + " Move event too soon after damage or no movement ("+currentTickAfterDamage+"t), waiting for next tick.");
            return;
        }
        
        acData.ticksSinceLastDamage = currentTickAfterDamage;

        if (acData.ticksSinceLastDamage > KB_CHECK_TICKS_WINDOW) {
            if (!acData.tookSignificantKnockback && acData.expectedKnockbackXZ >= MIN_EXPECTED_KB_XZ_TO_CHECK) {
                if (DEBUG_MODE) plugin.getLogger().warning("[KnockbackCheck DEBUG] " + player.getName() + " FAILED KB CHECK. No sufficient KB after " + acData.ticksSinceLastDamage + " ticks. Expected XZ: " + String.format("%.3f", acData.expectedKnockbackXZ));
                plugin.flagPlayer(player, acData, "KnockbackA", VL_INCREMENT,
                        "NoKB. Exp ~" + String.format("%.2f", acData.expectedKnockbackXZ) + " (Win: " + acData.ticksSinceLastDamage + "t)"
                );
            } else if (acData.tookSignificantKnockback && DEBUG_MODE){
                 plugin.getLogger().info("[KnockbackCheck DEBUG] " + player.getName() + " PASSED KB CHECK within window. KB detected.");
            }
            acData.resetKnockbackData();
            return;
        }

        Vector movementSinceDamage = event.getTo().toVector().subtract(acData.locationAtDamage.toVector());
        Vector movementXZSinceDamage = new Vector(movementSinceDamage.getX(), 0, movementSinceDamage.getZ());

        double actualDistanceMovedXZ = movementXZSinceDamage.length();
        double movementInExpectedDir = 0;

        if (acData.expectedKnockbackDirection.lengthSquared() > 0.001 && movementXZSinceDamage.lengthSquared() > 0.001) {
            movementInExpectedDir = movementXZSinceDamage.dot(acData.expectedKnockbackDirection);
        }

        if (DEBUG_MODE) {
            plugin.getLogger().info(String.format("[KnockbackCheck DEBUG] %s Move | Tick: %d | ActualMovedXZ: %.4f | MovedInExpDir: %.4f | ExpectedKB_XZ: %.3f",
                    player.getName(), acData.ticksSinceLastDamage, actualDistanceMovedXZ, movementInExpectedDir, acData.expectedKnockbackXZ));
        }

        if (movementInExpectedDir >= (acData.expectedKnockbackXZ * MIN_KB_RATIO_TAKEN)) {
            acData.tookSignificantKnockback = true;
            if (DEBUG_MODE) plugin.getLogger().info("[KnockbackCheck DEBUG] " + player.getName() + " took significant knockback this tick. Moved in dir: " + String.format("%.3f", movementInExpectedDir));
        }
    }

    private boolean isObstructed(Player player, EntityDamageEvent event) {
        Vector kbDir;
        if (event instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
             if (damager instanceof Arrow && ((Arrow) damager).getShooter() instanceof Entity) {
                damager = (Entity) ((Arrow) damager).getShooter();
            }
             if (damager == player) return false;
            kbDir = player.getLocation().toVector().subtract(damager.getLocation().toVector()).setY(0);
        } else {
            return false;
        }

        if (kbDir.lengthSquared() < 0.001) return false;
        kbDir.normalize();

        Location playerLoc = player.getLocation();

        for (double dist = 0.1; dist <= 0.6; dist += 0.25) {
            Location checkLocFeet = playerLoc.clone().add(kbDir.clone().multiply(dist));
            Location checkLocHead = playerLoc.clone().add(0, player.getEyeHeight() * 0.8, 0).add(kbDir.clone().multiply(dist));

            if (isSolidBlockAt(checkLocFeet.getBlock()) || isSolidBlockAt(checkLocHead.getBlock())) {
                if (DEBUG_MODE) plugin.getLogger().info("[KnockbackCheck DEBUG] " + player.getName() + " obstructed by block at feet: " + checkLocFeet.getBlock().getType() + " or head: " + checkLocHead.getBlock().getType() + " in KB direction.");
                return true;
            }
        }
        return false;
    }

    private boolean isSolidBlockAt(Block block){
        return block.getType().isSolid() && 
               block.getType() != Material.SIGN_POST && 
               block.getType() != Material.WALL_SIGN &&
               block.getType() != Material.AIR;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(event.getPlayer());
        if (acData != null) {
            acData.resetKnockbackData();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        PlayerACData acData = plugin.getPlayerDataManager().getPlayerData(event.getPlayer());
        if (acData != null) {
            if (DEBUG_MODE && acData.lastDamageTime != 0) plugin.getLogger().info("[KnockbackCheck DEBUG] " + event.getPlayer().getName() + " teleported, resetting KB data.");
            acData.resetKnockbackData();
        }
    }
    
    private boolean isPost1_9() {
        try {
            return Material.getMaterial("ELYTRA") != null;
        } catch (Throwable e) { 
            return false;
        }
    }
}
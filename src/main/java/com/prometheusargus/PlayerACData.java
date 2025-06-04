package com.prometheusargus;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerACData {

    private final UUID playerUUID;
    private final Map<String, Integer> violationLevels;
    public long lastAttackTime;

    public double verticalFallDistanceCounter;
    public int ticksInAir;
    public boolean justTookFallDamage;

    public int flyAbilityTicksInAir;
    public double lastYPositionFly;
    public int ticksHovering;
    public double highestYReachedInAir;
    public long lastSlimeBounceTime;
    public boolean onLadderOrVine;
    public Location lastOnGroundLocation;
    public int invalidGroundStateVLBuffer;
    public int glideVLBuffer;

    public long lastDamageTime;
    public double expectedKnockbackXZ;
    public Vector expectedKnockbackDirection;
    public int ticksSinceLastDamage;
    public Location locationAtDamage;
    public boolean tookSignificantKnockback;

    private Location lastMoveLocation;
    private long lastMoveTime;
    private long lastTeleportTime = 0;
    private boolean wasOnGround = true;
    private long lastVelocityApplicationTime = 0;
    private int airTicksSpeed = 0;
    private int groundTicks = 0;
    private double lastYDiffSpeed = 0;

    private double packetSpeedBuffer = 0.0;
    private int packetSpeedViolations = 0;


    public PlayerACData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.violationLevels = new HashMap<>();
        this.lastAttackTime = 0;

        this.verticalFallDistanceCounter = 0.0;
        this.ticksInAir = 0;
        this.justTookFallDamage = false;

        this.flyAbilityTicksInAir = 0;
        this.lastYPositionFly = 0.0;
        this.ticksHovering = 0;
        this.highestYReachedInAir = 0.0;
        this.lastSlimeBounceTime = 0L;
        this.onLadderOrVine = false;
        this.invalidGroundStateVLBuffer = 0;
        this.glideVLBuffer = 0;
        
        resetKnockbackData();

        this.lastMoveTime = System.currentTimeMillis();
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(playerUUID);
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public int getViolationLevel(String checkName) {
        return violationLevels.getOrDefault(checkName.toLowerCase(), 0);
    }

    public void incrementViolationLevel(String checkName, int amount) {
        String lowerCheckName = checkName.toLowerCase();
        violationLevels.put(lowerCheckName, getViolationLevel(lowerCheckName) + amount);
    }

    public void decrementViolationLevel(String checkName, int amount) {
        String lowerCheckName = checkName.toLowerCase();
        int currentVL = getViolationLevel(lowerCheckName);
        violationLevels.put(lowerCheckName, Math.max(0, currentVL - amount));
    }

    public void resetViolationLevel(String checkName) {
        violationLevels.remove(checkName.toLowerCase());
    }

    public Map<String, Integer> getAllViolationLevels() {
        return new HashMap<>(violationLevels);
    }

    public void resetNoFallData() {
        this.verticalFallDistanceCounter = 0.0;
        this.ticksInAir = 0;
        this.justTookFallDamage = false;
    }
    
    public void resetFlyAbilityData(Location currentGroundOrResetLocation) {
        this.flyAbilityTicksInAir = 0;
        Player p = getPlayer();
        Location referenceLocation = currentGroundOrResetLocation;
        if (referenceLocation == null) {
            referenceLocation = (p != null && p.isOnline()) ? p.getLocation() : this.lastOnGroundLocation;
        }

        if (referenceLocation != null) {
            this.lastYPositionFly = referenceLocation.getY();
            this.highestYReachedInAir = referenceLocation.getY();
        } else { 
            this.lastYPositionFly = (p != null && p.isOnline() ? p.getLocation().getY() : 0); 
            this.highestYReachedInAir = (p != null && p.isOnline() ? p.getLocation().getY() : 0);
        }
        this.ticksHovering = 0;
        this.invalidGroundStateVLBuffer = 0;
        this.glideVLBuffer = 0;
    }

    public void resetKnockbackData() {
        this.lastDamageTime = 0;
        this.expectedKnockbackXZ = 0.0;
        this.expectedKnockbackDirection = null;
        this.ticksSinceLastDamage = 0;
        this.locationAtDamage = null;
        this.tookSignificantKnockback = false;
    }
    
    public Location getLastMoveLocation() {
        return lastMoveLocation;
    }

    public void setLastMoveLocation(Location lastMoveLocation) {
        this.lastMoveLocation = lastMoveLocation;
    }

    public long getLastMoveTime() {
        return lastMoveTime;
    }

    public void setLastMoveTime(long lastMoveTime) {
        this.lastMoveTime = lastMoveTime;
    }

    public void recordTeleport() {
        this.lastTeleportTime = System.currentTimeMillis();
        this.airTicksSpeed = 0;
        this.groundTicks = 0;
        Player p = getPlayer();
        if (p != null && p.isOnline()) {
            this.lastMoveLocation = p.getLocation();
        } else {
            this.lastMoveLocation = null;
        }
        this.packetSpeedBuffer = 0.0;
        this.packetSpeedViolations = 0;
        resetFlyAbilityData(p != null && p.isOnline() ? p.getLocation() : null);
    }

    public boolean hasBeenTeleportedRecently(long currentTime, long gracePeriodMillis) {
        return (currentTime - lastTeleportTime) < gracePeriodMillis;
    }

    public boolean wasOnGroundSpeed() {
        return wasOnGround;
    }

    public void setWasOnGroundSpeed(boolean currentIsOnGround) {
        this.wasOnGround = currentIsOnGround;
        if (currentIsOnGround) {
            airTicksSpeed = 0;
            groundTicks++;
        } else {
            groundTicks = 0;
            airTicksSpeed++;
        }
    }

    public void recordVelocityApplication() {
        this.lastVelocityApplicationTime = System.currentTimeMillis();
    }

    public boolean wasVelocityAppliedRecently(long currentTime, long gracePeriodMillis) {
        return (currentTime - lastVelocityApplicationTime) < gracePeriodMillis;
    }

    public int getAirTicksSpeed() {
        return airTicksSpeed;
    }

    public int getGroundTicks() {
        return groundTicks;
    }
    
    public double getLastYDiffSpeed() {
        return lastYDiffSpeed;
    }

    public void setLastYDiffSpeed(double lastYDiffSpeed) {
        this.lastYDiffSpeed = lastYDiffSpeed;
    }

    public double getPacketSpeedBuffer() {
        return packetSpeedBuffer;
    }

    public void addToPacketSpeedBuffer(double amount) {
        this.packetSpeedBuffer += amount;
        if (this.packetSpeedBuffer < 0) this.packetSpeedBuffer = 0;
    }

    public void resetPacketSpeedBuffer() {
        this.packetSpeedBuffer = 0.0;
    }

    public int getPacketSpeedViolations() {
        return packetSpeedViolations;
    }

    public void incrementPacketSpeedViolations() {
        this.packetSpeedViolations++;
    }

    public void resetPacketSpeedViolations() {
        this.packetSpeedViolations = 0;
    }
}
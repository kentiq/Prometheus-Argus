package com.prometheusargus;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MuteManager implements Listener {

    private final PrometheusArgus plugin;
    private File muteFile;
    private FileConfiguration muteConfig;
    private final Map<UUID, MuteEntry> activeMutes = new HashMap<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss z", Locale.FRENCH);

    public MuteManager(PrometheusArgus plugin) {
        this.plugin = plugin;
        setupMuteFile();
        loadMutes();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void setupMuteFile() {
        muteFile = new File(plugin.getDataFolder(), "mutes.yml");
        if (!muteFile.exists()) {
            plugin.saveResource("mutes.yml", false);
        }
        muteConfig = YamlConfiguration.loadConfiguration(muteFile);
    }

    private void loadMutes() {
        activeMutes.clear();
        if (muteConfig.getConfigurationSection("mutes") == null) {
            muteConfig.createSection("mutes");
            saveMutes();
            return;
        }
        for (String uuidStr : muteConfig.getConfigurationSection("mutes").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String path = "mutes." + uuidStr + ".";
                String playerName = muteConfig.getString(path + "playerName");
                String reason = muteConfig.getString(path + "reason");
                String source = muteConfig.getString(path + "source");
                long createdMillis = muteConfig.getLong(path + "created");
                long expiresMillis = muteConfig.getLong(path + "expires");

                if (playerName == null) continue;

                Date created = new Date(createdMillis);
                Date expires = (expiresMillis == 0) ? null : new Date(expiresMillis);

                if (expires != null && expires.before(new Date())) {
                    muteConfig.set("mutes." + uuidStr, null);
                    continue;
                }
                activeMutes.put(uuid, new MuteEntry(playerName, uuid, reason, source, created, expires));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Could not parse UUID for mute entry: " + uuidStr);
            }
        }
        saveMutes();
        if (plugin.isGlobalDebugModeEnabled()) {
            plugin.getLogger().info("[MuteManager] Loaded " + activeMutes.size() + " active mutes.");
        }
    }

    public void saveMutes() {
        muteConfig.set("mutes", null); 
        muteConfig.createSection("mutes");

        for (Map.Entry<UUID, MuteEntry> entry : activeMutes.entrySet()) {
            MuteEntry mute = entry.getValue();
            if (mute.getExpires() != null && mute.getExpires().before(new Date())) {
                continue;
            }
            String uuidStr = mute.getTargetUUID().toString();
            String path = "mutes." + uuidStr + ".";
            muteConfig.set(path + "playerName", mute.getTargetName());
            muteConfig.set(path + "reason", mute.getReason());
            muteConfig.set(path + "source", mute.getSource());
            muteConfig.set(path + "created", mute.getCreated().getTime());
            muteConfig.set(path + "expires", (mute.getExpires() == null) ? 0 : mute.getExpires().getTime());
        }
        try {
            muteConfig.save(muteFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save mutes.yml: " + e.getMessage());
        }
    }

    public boolean isMuted(UUID playerUUID) {
        MuteEntry mute = activeMutes.get(playerUUID);
        if (mute != null) {
            if (mute.getExpires() == null || mute.getExpires().after(new Date())) {
                return true;
            } else {
                activeMutes.remove(playerUUID);
                muteConfig.set("mutes." + playerUUID.toString(), null);
                saveMutes();
                return false;
            }
        }
        return false;
    }
    
    public MuteEntry getMuteEntry(UUID playerUUID) {
        if (isMuted(playerUUID)) {
            return activeMutes.get(playerUUID);
        }
        return null;
    }


    public void mutePlayer(UUID targetUUID, String targetName, String reason, String source, Date expirationDate) {
        MuteEntry mute = new MuteEntry(targetName, targetUUID, reason, source, new Date(), expirationDate);
        activeMutes.put(targetUUID, mute);
        saveMutes();
        
        plugin.getSanctionHistoryManager().addSanction(
            targetName, 
            "Mute", 
            reason + (expirationDate == null ? " (Permanent)" : " (Expire: " + dateFormat.format(expirationDate) + ")"), 
            source, 
            -1
        );

        Player targetOnline = Bukkit.getPlayer(targetUUID);
        if (targetOnline != null) {
            String expirationStr = (expirationDate == null) ? "permanent" : "jusqu'au " + dateFormat.format(expirationDate);
            targetOnline.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("messages.mute.target_muted", 
                "&cVous avez été réduit au silence par %source% pour : %reason%. Votre silence est %expiration_type%.")
                .replace("%source%", source)
                .replace("%reason%", reason)
                .replace("%expiration_type%", expirationStr)
            ));
        }
        if (plugin.isGlobalDebugModeEnabled()) {
            plugin.getLogger().info("[MuteManager] Muted " + targetName + " (UUID: " + targetUUID + "). Reason: " + reason + ". Expires: " + (expirationDate == null ? "Permanent" : expirationDate));
        }
    }

    public boolean unmutePlayer(UUID targetUUID, String source) {
        if (activeMutes.containsKey(targetUUID)) {
            MuteEntry mute = activeMutes.remove(targetUUID);
            muteConfig.set("mutes." + targetUUID.toString(), null);
            saveMutes();

            plugin.getSanctionHistoryManager().addSanction(
                mute.getTargetName(),
                "Unmute", 
                "Silence levé par " + source, 
                source, 
                -1
            );

            Player targetOnline = Bukkit.getPlayer(targetUUID);
            if (targetOnline != null) {
                 targetOnline.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.unmute.target_unmuted",
                    "&aVotre silence a été levé par %source%. Vous pouvez à nouveau parler.")
                    .replace("%source%", source)
                ));
            }
            if (plugin.isGlobalDebugModeEnabled()) {
                 plugin.getLogger().info("[MuteManager] Unmuted " + mute.getTargetName() + " (UUID: " + targetUUID + ") by " + source);
            }
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (isMuted(player.getUniqueId())) {
            MuteEntry mute = getMuteEntry(player.getUniqueId());
            if (mute != null) {
                String timeLeft = "Permanent";
                if (mute.getExpires() != null) {
                    long durationMillis = mute.getExpires().getTime() - System.currentTimeMillis();
                    if (durationMillis > 0) {
                        timeLeft = formatDuration(durationMillis);
                    } else {
                        unmutePlayer(player.getUniqueId(), "Système (Auto-Expiration)");
                        return;
                    }
                }
                
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.mute.cannot_chat",
                    "&cVous êtes réduit au silence. Raison: %reason%. Temps restant: %time_left%.")
                    .replace("%reason%", mute.getReason())
                    .replace("%time_left%", timeLeft)
                ));
                event.setCancelled(true);
            }
        }
    }
    
    private String formatDuration(long millis) {
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("j ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }


    public static class MuteEntry {
        private final String targetName;
        private final UUID targetUUID;
        private final String reason;
        private final String source;
        private final Date created;
        private final Date expires;

        public MuteEntry(String targetName, UUID targetUUID, String reason, String source, Date created, Date expires) {
            this.targetName = targetName;
            this.targetUUID = targetUUID;
            this.reason = reason;
            this.source = source;
            this.created = created;
            this.expires = expires;
        }

        public String getTargetName() { return targetName; }
        public UUID getTargetUUID() { return targetUUID; }
        public String getReason() { return reason; }
        public String getSource() { return source; }
        public Date getCreated() { return created; }
        public Date getExpires() { return expires; }
    }
}
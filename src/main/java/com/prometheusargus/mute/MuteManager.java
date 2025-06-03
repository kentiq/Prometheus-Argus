package com.prometheusargus.mute;

import com.prometheusargus.PrometheusArgus;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MuteManager {

    private final PrometheusArgus plugin;
    private YamlConfiguration config;

    public MuteManager(PrometheusArgus plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        File configFile = new File(plugin.getDataFolder(), "mutes.yml");
        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
            if (plugin.isGlobalDebugModeEnabled()) {
                plugin.getLogger().info("[Mute] Configuration rechargée");
                plugin.getLogger().info("[Mute] default-duration: " + config.getString("settings.default-duration", "1h"));
                plugin.getLogger().info("[Mute] max-duration: " + config.getString("settings.max-duration", "30d"));
            }
        }
    }
} 
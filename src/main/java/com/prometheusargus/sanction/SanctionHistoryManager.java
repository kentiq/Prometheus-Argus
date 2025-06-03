package com.prometheusargus.sanction;

import com.prometheusargus.PrometheusArgus;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class SanctionHistoryManager {

    private final PrometheusArgus plugin;
    private YamlConfiguration config;

    public SanctionHistoryManager(PrometheusArgus plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        File configFile = new File(plugin.getDataFolder(), "sanction_history.yml");
        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
            if (plugin.isGlobalDebugModeEnabled()) {
                plugin.getLogger().info("[Sanction] Configuration rechargée");
                plugin.getLogger().info("[Sanction] max-history: " + config.getInt("settings.max-history", 50));
                plugin.getLogger().info("[Sanction] save-duration: " + config.getInt("settings.save-duration", 30));
            }
        }
    }
} 
package com.prometheusargus.report;

import com.prometheusargus.PrometheusArgus;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ReportManager {

    private final PrometheusArgus plugin;
    private YamlConfiguration config;

    public ReportManager(PrometheusArgus plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        File configFile = new File(plugin.getDataFolder(), "reports.yml");
        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
            if (plugin.isGlobalDebugModeEnabled()) {
                plugin.getLogger().info("[Report] Configuration rechargée");
                plugin.getLogger().info("[Report] max-reports: " + config.getInt("settings.max-reports", 5));
                plugin.getLogger().info("[Report] cooldown: " + config.getInt("settings.cooldown", 300));
            }
        }
    }
} 
package com.prometheusargus.notes;

import com.prometheusargus.PrometheusArgus;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class PlayerNotesManager {

    private final PrometheusArgus plugin;
    private YamlConfiguration config;

    public PlayerNotesManager(PrometheusArgus plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        File configFile = new File(plugin.getDataFolder(), "playernotes.yml");
        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
            if (plugin.isGlobalDebugModeEnabled()) {
                plugin.getLogger().info("[Notes] Configuration rechargée");
                plugin.getLogger().info("[Notes] max-notes: " + config.getInt("settings.max-notes", 10));
                plugin.getLogger().info("[Notes] max-note-length: " + config.getInt("settings.max-note-length", 200));
            }
        }
    }
} 
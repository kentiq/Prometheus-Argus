package com.prometheusargus;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager implements Listener {

    private final PrometheusArgus plugin;
    private final Map<UUID, PlayerACData> playerDataMap;

    public PlayerDataManager(PrometheusArgus plugin) {
        this.plugin = plugin;
        this.playerDataMap = new HashMap<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (plugin.isGlobalDebugModeEnabled()) { // Utiliser le getter pour globalDebugMode
            plugin.getLogger().info("[PlayerDataManager DEBUG] Initialized and events registered.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Crée ou récupère les données. Si computeIfAbsent est utilisé dans getPlayerData,
        // cette ligne assure juste que le log de join est présent.
        playerDataMap.put(player.getUniqueId(), new PlayerACData(player.getUniqueId()));
        if (plugin.isGlobalDebugModeEnabled()) {
            plugin.getLogger().info("[PlayerDataManager DEBUG] PlayerACData initialized for JOINING player: " + player.getName());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        playerDataMap.remove(player.getUniqueId());
        if (plugin.isGlobalDebugModeEnabled()) {
            plugin.getLogger().info("[PlayerDataManager DEBUG] PlayerACData REMOVED for quitting player: " + player.getName());
        }
    }

    /**
     * Récupère les données PlayerACData pour un joueur.
     * Si les données n'existent pas (ex: après un /reload pour un joueur déjà en ligne),
     * elles sont créées à la volée.
     * @param player Le joueur
     * @return Les PlayerACData du joueur.
     */
    public PlayerACData getPlayerData(Player player) {
        return playerDataMap.computeIfAbsent(player.getUniqueId(), k -> {
            if (plugin.isGlobalDebugModeEnabled()) {
                plugin.getLogger().info("[PlayerDataManager DEBUG] PlayerACData CREATED ON-DEMAND for: " + player.getName() + " (Likely due to reload or plugin start with player already online)");
            }
            return new PlayerACData(k);
        });
    }

    // Cette méthode est moins utilisée directement par les checks, mais peut servir.
    public PlayerACData getPlayerData(UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline()) {
            return getPlayerData(player); // Utilise la logique computeIfAbsent
        }
        // Si le joueur est hors ligne, on retourne ce qu'on a, ou null.
        return playerDataMap.get(uuid);
    }
}
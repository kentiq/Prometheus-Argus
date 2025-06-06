package com.prometheusargus.mode;

import com.prometheusargus.PrometheusArgus;
import com.prometheusargus.gui.SanctionsGUI;
import com.prometheusargus.gui.SanctionsGUIListener;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.ChatColor;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ModModeManager implements Listener {
    private final PrometheusArgus plugin;
    private final Set<UUID> modModePlayers;
    private final Set<UUID> vanishedPlayers;
    private final Map<UUID, GameMode> previousGameModes;
    private final Map<UUID, ItemStack[]> previousInventories;
    private final Map<UUID, Long> vanishTimestamps;
    private final Map<UUID, Boolean> modModePlayersMap;
    private final Map<UUID, Long> cooldowns;
    private static final long COOLDOWN_DURATION = 3000; // 3 secondes en millisecondes
    private YamlConfiguration config;

    public ModModeManager(PrometheusArgus plugin) {
        this.plugin = plugin;
        this.modModePlayers = new HashSet<>();
        this.vanishedPlayers = new HashSet<>();
        this.previousGameModes = new HashMap<>();
        this.previousInventories = new HashMap<>();
        this.vanishTimestamps = new HashMap<>();
        this.modModePlayersMap = new HashMap<>();
        this.cooldowns = new HashMap<>();
    }

    public boolean isInModMode(Player player) {
        return modModePlayersMap.getOrDefault(player.getUniqueId(), false);
    }

    public boolean isVanished(Player player) {
        return vanishedPlayers.contains(player.getUniqueId());
    }

    public void enableModMode(Player player) {
        // Sauvegarder l'état précédent
        previousGameModes.put(player.getUniqueId(), player.getGameMode());
        previousInventories.put(player.getUniqueId(), player.getInventory().getContents());
        
        // Activer le mode modérateur
        modModePlayers.add(player.getUniqueId());
        modModePlayersMap.put(player.getUniqueId(), true);
        
        // Appliquer les effets
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        
        // Appliquer le vanish
        if (plugin.getModModeConfig().getBoolean("settings.auto-vanish", true)) {
            vanishedPlayers.add(player.getUniqueId());
        }
        
        // Donner les items
        ModModeItems.giveModItems(player);
        
        // Effets visuels et sonores
        player.playSound(player.getLocation(), "LEVEL_UP", 1.0f, 1.0f);
        
        // Title
        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendTitle(
                    org.bukkit.ChatColor.DARK_RED + "" + org.bukkit.ChatColor.BOLD + "[Prometheus Argus]",
                    org.bukkit.ChatColor.GREEN + "Mode modérateur activé"
                );
            }
        }.runTaskLater(plugin, 1L);
        
        // Notification au staff
        String message = plugin.getModModeConfig().getString("messages.staff-notification", "&e%player% &7a activé le mode modérateur")
            .replace("%player%", player.getName())
            .replace("%action%", "activé");
        plugin.getServer().getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("prometheusargus.modmode.notify"))
            .forEach(p -> p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', message)));
    }

    public void disableModMode(Player player) {
        // Désactiver le mode modérateur
        modModePlayers.remove(player.getUniqueId());
        modModePlayersMap.remove(player.getUniqueId());
        vanishedPlayers.remove(player.getUniqueId());
        
        // Restaurer l'état précédent
        GameMode previousMode = previousGameModes.getOrDefault(player.getUniqueId(), GameMode.SURVIVAL);
        player.setGameMode(previousMode);
        previousGameModes.remove(player.getUniqueId());
        
        // Restaurer l'inventaire
        ItemStack[] previousInventory = previousInventories.get(player.getUniqueId());
        if (previousInventory != null) {
            player.getInventory().setContents(previousInventory);
            previousInventories.remove(player.getUniqueId());
        }
        
        // Retirer les effets
        player.setAllowFlight(false);
        player.setFlying(false);
        
        // Effets visuels et sonores
        player.playSound(player.getLocation(), "ANVIL_BREAK", 1.0f, 1.0f);
        
        // Title
        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendTitle(
                    org.bukkit.ChatColor.DARK_RED + "" + org.bukkit.ChatColor.BOLD + "[Prometheus Argus]",
                    org.bukkit.ChatColor.RED + "Mode modérateur désactivé"
                );
            }
        }.runTaskLater(plugin, 1L);
        
        // Notification au staff
        String message = plugin.getModModeConfig().getString("messages.staff-notification", "&e%player% &7a désactivé le mode modérateur")
            .replace("%player%", player.getName())
            .replace("%action%", "désactivé");
        plugin.getServer().getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("prometheusargus.modmode.notify"))
            .forEach(p -> p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', message)));
    }

    public void handlePlayerJoin(Player player) {
        if (plugin.getModModeConfig().getBoolean("settings.persist-mode", true)) {
            if (modModePlayers.contains(player.getUniqueId())) {
                enableModMode(player);
            }
        }
    }

    public void handlePlayerQuit(Player player) {
        // Ne pas retirer le joueur des listes pour la persistance
        // Les listes seront nettoyées si nécessaire lors de la désactivation du mode
    }

    public void reloadConfig() {
        File configFile = new File(plugin.getDataFolder(), "modmode.yml");
        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
            if (plugin.isGlobalDebugModeEnabled()) {
                plugin.getLogger().info("[ModMode] Configuration rechargée");
                plugin.getLogger().info("[ModMode] auto-vanish: " + config.getBoolean("settings.auto-vanish", true));
                plugin.getLogger().info("[ModMode] persist-mode: " + config.getBoolean("settings.persist-mode", true));
                plugin.getLogger().info("[ModMode] allow-chat: " + config.getBoolean("settings.allow-chat", false));
            }
        }
    }

    public void toggleVanish(Player player) {
        if (isVanished(player)) {
            disableVanish(player);
        } else {
            enableVanish(player);
        }
    }

    private void enableVanish(Player player) {
        vanishedPlayers.add(player.getUniqueId());
        vanishTimestamps.put(player.getUniqueId(), System.currentTimeMillis());
        
        // Cacher le joueur
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.hasPermission("prometheusargus.modmode.see")) {
                onlinePlayer.hidePlayer(player);
            }
        }
        
        // Effets visuels
        player.playSound(player.getLocation(), "LEVEL_UP", 1.0f, 1.0f);
        player.sendTitle(
            ChatColor.DARK_RED + "" + ChatColor.BOLD + "[Prometheus Argus]",
            ChatColor.GREEN + "Vanish activé"
        );
        
        // Notification au staff
        String message = plugin.getModModeConfig().getString("messages.vanish-notification", "&e%player% &7est maintenant en vanish")
            .replace("%player%", player.getName());
        notifyStaff(message);
    }

    private void disableVanish(Player player) {
        vanishedPlayers.remove(player.getUniqueId());
        vanishTimestamps.remove(player.getUniqueId());
        
        // Montrer le joueur
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.showPlayer(player);
        }
        
        // Effets visuels
        player.playSound(player.getLocation(), "ANVIL_BREAK", 1.0f, 1.0f);
        player.sendTitle(
            ChatColor.DARK_RED + "" + ChatColor.BOLD + "[Prometheus Argus]",
            ChatColor.RED + "Vanish désactivé"
        );
        
        // Notification au staff
        String message = plugin.getModModeConfig().getString("messages.vanish-notification", "&e%player% &7n'est plus en vanish")
            .replace("%player%", player.getName());
        notifyStaff(message);
    }

    private void notifyStaff(String message) {
        String coloredMessage = ChatColor.translateAlternateColorCodes('&', message);
        plugin.getServer().getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("prometheusargus.modmode.notify"))
            .forEach(p -> p.sendMessage(coloredMessage));
    }

    public long getVanishDuration(Player player) {
        if (!isVanished(player)) return 0;
        Long timestamp = vanishTimestamps.get(player.getUniqueId());
        return timestamp != null ? System.currentTimeMillis() - timestamp : 0;
    }

    public String getFormattedVanishDuration(Player player) {
        long duration = getVanishDuration(player);
        if (duration == 0) return "0s";
        
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isInModMode(player)) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        if (isOnCooldown(player)) {
            player.sendMessage(ChatColor.RED + "Veuillez patienter avant d'utiliser à nouveau cet item");
            event.setCancelled(true);
            return;
        }

        switch (item.getType()) {
            case BLAZE_ROD: // Bâton de Freeze
                // TODO: Implémenter la logique de freeze
                setCooldown(player);
                break;

            case ENDER_PEARL: // Bâton de Téléportation
                // TODO: Implémenter la logique de téléportation
                setCooldown(player);
                break;

            case STICK: // Bâton d'Inspection
                // TODO: Implémenter la logique d'inspection
                setCooldown(player);
                break;

            case BONE: // Bâton de Punition
                // TODO: Implémenter la logique de punition
                setCooldown(player);
                break;

            case EMERALD: // Bâton de Surveillance
                // TODO: Implémenter la logique de surveillance
                setCooldown(player);
                break;

            case GOLDEN_CARROT: // Bâton de Rappel
                // TODO: Implémenter la logique de rappel
                setCooldown(player);
                break;

            case BOOK: // Menu de Sanctions
                event.setCancelled(true);
                // Le menu de sanctions sera géré par le GUI
                break;
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!isInModMode(player)) return;

        if (!(event.getRightClicked() instanceof Player)) return;
        Player target = (Player) event.getRightClicked();

        ItemStack item = player.getItemInHand();
        if (item == null) return;

        if (isOnCooldown(player)) {
            player.sendMessage(ChatColor.RED + "Veuillez patienter avant d'utiliser à nouveau cet item");
            event.setCancelled(true);
            return;
        }

        switch (item.getType()) {
            case BLAZE_ROD: // Bâton de Freeze
                // TODO: Implémenter la logique de freeze
                setCooldown(player);
                break;

            case ENDER_PEARL: // Bâton de Téléportation
                // TODO: Implémenter la logique de téléportation
                setCooldown(player);
                break;

            case STICK: // Bâton d'Inspection
                // TODO: Implémenter la logique d'inspection
                setCooldown(player);
                break;

            case BONE: // Bâton de Punition
                event.setCancelled(true);
                SanctionsGUIListener.setTargetPlayer(player, target);
                SanctionsGUI.openSanctionsGUI(player, target);
                break;

            case EMERALD: // Bâton de Surveillance
                // TODO: Implémenter la logique de surveillance
                setCooldown(player);
                break;
        }
    }

    private boolean isOnCooldown(Player player) {
        Long lastUse = cooldowns.get(player.getUniqueId());
        if (lastUse == null) return false;
        return System.currentTimeMillis() - lastUse < COOLDOWN_DURATION;
    }

    private void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }
} 
package com.prometheusargus.mode;

import com.prometheusargus.PrometheusArgus;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModModeItemHandler {
    private final PrometheusArgus plugin;
    private final ModModeManager modModeManager;
    private final Map<UUID, Location> savedLocations = new HashMap<>();

    public ModModeItemHandler(PrometheusArgus plugin, ModModeManager modModeManager) {
        this.plugin = plugin;
        this.modModeManager = modModeManager;
    }

    public void handleFreezeStick(Player staff, Player target, boolean isLeftClick) {
        if (target == null) {
            staff.sendMessage(ChatColor.RED + "Aucun joueur ciblé.");
            return;
        }

        if (isLeftClick) {
            plugin.getFreezeManager().freezePlayer(target, staff);
        } else {
            plugin.getFreezeManager().unfreezePlayer(target, staff);
        }
    }

    public void handleTeleportStick(Player staff, Player target, boolean isLeftClick) {
        if (isLeftClick) {
            // Téléportation du staff
            staff.teleport(staff.getLocation());
            staff.sendMessage(ChatColor.GREEN + "Vous vous êtes téléporté.");
        } else if (target != null) {
            // Téléportation du joueur ciblé vers le staff
            target.teleport(staff.getLocation());
            staff.sendMessage(ChatColor.GREEN + target.getName() + " a été téléporté à vous.");
            target.sendMessage(ChatColor.YELLOW + "Vous avez été téléporté par " + staff.getName());
        } else {
            staff.sendMessage(ChatColor.RED + "Aucun joueur ciblé.");
        }
    }

    public void handleInspectStick(Player staff, Player target, boolean isLeftClick) {
        if (target == null) {
            staff.sendMessage(ChatColor.RED + "Aucun joueur ciblé.");
            return;
        }

        if (isLeftClick) {
            // Inspection du joueur
            staff.sendMessage(ChatColor.GREEN + "=== Informations sur " + target.getName() + " ===");
            staff.sendMessage(ChatColor.YELLOW + "Gamemode: " + target.getGameMode().name());
            staff.sendMessage(ChatColor.YELLOW + "Santé: " + target.getHealth() + "/" + target.getMaxHealth());
            staff.sendMessage(ChatColor.YELLOW + "Nourriture: " + target.getFoodLevel() + "/20");
        } else {
            // Inspection de l'inventaire
            staff.openInventory(target.getInventory());
        }
    }

    public void handlePunishStick(Player staff, Player target, boolean isLeftClick) {
        if (target == null) {
            staff.sendMessage(ChatColor.RED + "Aucun joueur ciblé.");
            return;
        }

        if (isLeftClick) {
            // Kick
            String reason = "Kick par " + staff.getName();
            target.kickPlayer(ChatColor.RED + "Vous avez été kick par " + staff.getName() + "\nRaison: " + reason);
            staff.sendMessage(ChatColor.GREEN + target.getName() + " a été kick.");
        } else {
            // Ban
            String reason = "Ban par " + staff.getName();
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(target.getName(), reason, null, staff.getName());
            target.kickPlayer(ChatColor.RED + "Vous avez été banni par " + staff.getName() + "\nRaison: " + reason);
            staff.sendMessage(ChatColor.GREEN + target.getName() + " a été banni.");
        }
    }

    public void handleMonitorStick(Player staff, Player target, boolean isLeftClick) {
        if (isLeftClick) {
            if (target == null) {
                staff.sendMessage(ChatColor.RED + "Aucun joueur ciblé.");
                return;
            }

            // Vérifier si le joueur est en vanish
            if (modModeManager.isVanished(target)) {
                staff.sendMessage(ChatColor.RED + "Ce joueur est en vanish et ne peut pas être surveillé.");
                return;
            }

            // Téléporter le staff derrière le joueur avec une distance de sécurité
            Location targetLoc = target.getLocation();
            Vector direction = targetLoc.getDirection().multiply(-2); // 2 blocs derrière
            Location teleportLoc = targetLoc.add(direction);
            teleportLoc.setY(targetLoc.getY()); // Garder la même hauteur
            
            // Vérifier si la position est sûre
            if (isSafeLocation(teleportLoc)) {
                staff.teleport(teleportLoc);
                staff.sendMessage(ChatColor.GREEN + "Vous suivez maintenant " + target.getName());
                
                // Démarrer une tâche de suivi
                startMonitoringTask(staff, target);
            } else {
                staff.sendMessage(ChatColor.RED + "Impossible de se téléporter à une position sûre.");
            }
        } else {
            // Arrêter le suivi
            stopMonitoringTask(staff);
            staff.sendMessage(ChatColor.YELLOW + "Vous ne suivez plus personne.");
        }
    }

    private boolean isSafeLocation(Location loc) {
        Block block = loc.getBlock();
        Block blockAbove = loc.getBlock().getRelative(0, 1, 0);
        Block blockBelow = loc.getBlock().getRelative(0, -1, 0);
        
        return !block.getType().isSolid() && 
               !blockAbove.getType().isSolid() && 
               blockBelow.getType().isSolid();
    }

    private final Map<UUID, Integer> monitoringTasks = new HashMap<>();

    private void startMonitoringTask(Player staff, Player target) {
        // Arrêter toute tâche existante
        stopMonitoringTask(staff);
        
        // Démarrer une nouvelle tâche
        int taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!staff.isOnline() || !target.isOnline()) {
                stopMonitoringTask(staff);
                return;
            }

            // Vérifier si le staff est toujours en mode modération
            if (!modModeManager.isInModMode(staff)) {
                stopMonitoringTask(staff);
                return;
            }

            // Vérifier si le joueur est toujours en vanish
            if (modModeManager.isVanished(target)) {
                staff.sendMessage(ChatColor.RED + "Le joueur est maintenant en vanish, arrêt du suivi.");
                stopMonitoringTask(staff);
                return;
            }

            // Téléporter le staff derrière le joueur
            Location targetLoc = target.getLocation();
            Vector direction = targetLoc.getDirection().multiply(-2);
            Location teleportLoc = targetLoc.add(direction);
            teleportLoc.setY(targetLoc.getY());
            
            if (isSafeLocation(teleportLoc)) {
                staff.teleport(teleportLoc);
            }
        }, 0L, 5L); // Mise à jour toutes les 5 ticks (0.25 secondes)

        monitoringTasks.put(staff.getUniqueId(), taskId);
    }

    private void stopMonitoringTask(Player staff) {
        Integer taskId = monitoringTasks.remove(staff.getUniqueId());
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    public void handleRecallStick(Player staff, boolean isLeftClick) {
        if (isLeftClick) {
            // Sauvegarder la position
            savedLocations.put(staff.getUniqueId(), staff.getLocation());
            staff.sendMessage(ChatColor.GREEN + "Position sauvegardée.");
        } else {
            // Retourner à la position sauvegardée
            Location savedLoc = savedLocations.get(staff.getUniqueId());
            if (savedLoc != null) {
                staff.teleport(savedLoc);
                staff.sendMessage(ChatColor.GREEN + "Retour à la position sauvegardée.");
            } else {
                staff.sendMessage(ChatColor.RED + "Aucune position sauvegardée.");
            }
        }
    }
} 
package com.prometheusargus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class StaffChatManager implements Listener {

    private final PrometheusArgus plugin;
    private final Set<UUID> staffChatToggled = new HashSet<>();
    private final String staffChatPermission = "prometheusargus.staffchat.use";
    private final String staffChatReceivePermission = "prometheusargus.staffchat.receive"; // Peut être identique à .use ou séparé

    public StaffChatManager(PrometheusArgus plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Vérifie si un joueur a activé le mode staff chat permanent.
     * @param player Le joueur.
     * @return true si le mode est activé, false sinon.
     */
    public boolean isInStaffChatMode(Player player) {
        return staffChatToggled.contains(player.getUniqueId());
    }

    /**
     * Active ou désactive le mode staff chat permanent pour un joueur.
     * @param player Le joueur.
     * @return true si le mode est maintenant activé, false s'il est désactivé.
     */
    public boolean toggleStaffChatMode(Player player) {
        if (!player.hasPermission(staffChatPermission)) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser le staff chat.");
            return false;
        }

        UUID playerUUID = player.getUniqueId();
        if (staffChatToggled.contains(playerUUID)) {
            staffChatToggled.remove(playerUUID);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.staffchat.toggle_off", "&eStaffChat désactivé. Vos messages sont maintenant publics.")));
            return false;
        } else {
            staffChatToggled.add(playerUUID);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.staffchat.toggle_on", "&aStaffChat activé. Vos messages sont maintenant privés au staff.")));
            return true;
        }
    }

    /**
     * Envoie un message dans le staff chat.
     * @param sender Le joueur qui envoie le message.
     * @param message Le message à envoyer.
     */
    public void sendStaffChatMessage(Player sender, String message) {
        if (!sender.hasPermission(staffChatPermission)) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'envoyer des messages dans le staff chat.");
            return;
        }

        String format = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.staffchat.format", "&8[&cStaff&8] &e%player%&7: &f%message%")
                .replace("%player%", sender.getDisplayName()) // Utiliser getDisplayName() pour les préfixes/suffixes
                .replace("%message%", message));

        plugin.getLogger().info(ChatColor.stripColor("[StaffChat] " + sender.getName() + ": " + message)); // Log en console

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission(staffChatReceivePermission)) {
                onlinePlayer.sendMessage(format);
                // Jouer un son discret pour les destinataires
                onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.valueOf("SUCCESSFUL_HIT"), 0.5f, 1.5f); // Son léger et aigu
            }
        }
    }

    /**
     * Intercepte les messages des joueurs qui ont activé le staff chat permanent.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true) // NORMAL pour permettre à d'autres plugins de formater avant si besoin
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (isInStaffChatMode(player)) {
            if (!player.hasPermission(staffChatPermission)) { // Double check permission au cas où
                staffChatToggled.remove(player.getUniqueId()); // Corriger l'état si la perm a été retirée
                return; // Laisser le message passer en chat public
            }
            event.setCancelled(true); // Annuler le message dans le chat public
            sendStaffChatMessage(player, event.getMessage());
        }
    }
}
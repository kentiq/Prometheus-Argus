package com.prometheusargus;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PlayerNotesManager {

    private final PrometheusArgus plugin;
    private File notesFile;
    private FileConfiguration notesConfig;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH);

    public PlayerNotesManager(PrometheusArgus plugin) {
        this.plugin = plugin;
        setupNotesFile();
    }

    private void setupNotesFile() {
        notesFile = new File(plugin.getDataFolder(), "playernotes.yml");
        if (!notesFile.exists()) {
            try {
                notesFile.getParentFile().mkdirs();
                notesFile.createNewFile();
                plugin.getLogger().info("Created playernotes.yml");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create playernotes.yml: " + e.getMessage());
            }
        }
        notesConfig = YamlConfiguration.loadConfiguration(notesFile);
    }

    public void saveNotes() {
        try {
            notesConfig.save(notesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save playernotes.yml: " + e.getMessage());
        }
    }

    /**
     * Ajoute une note pour un joueur.
     * @param targetUUID UUID du joueur ciblé.
     * @param targetName Nom du joueur ciblé (pour référence, non utilisé comme clé primaire).
     * @param staffName Nom du staff qui ajoute la note.
     * @param noteText Le contenu de la note.
     * @return true si la note a été ajoutée, false sinon.
     */
    public boolean addNote(UUID targetUUID, String targetName, String staffName, String noteText) {
        List<String> notes = notesConfig.getStringList("notes." + targetUUID.toString() + ".entries");
        if (notes == null) {
            notes = new ArrayList<>();
        }
        
        String formattedNote = String.format("[%s par %s] %s",
                dateFormat.format(new Date()),
                staffName,
                ChatColor.stripColor(noteText)
        );
        notes.add(0, formattedNote);

        int maxNotesPerPlayer = plugin.getConfig().getInt("notes.max_per_player", 20);
        while (notes.size() > maxNotesPerPlayer && maxNotesPerPlayer > 0) {
            notes.remove(notes.size() - 1);
        }

        notesConfig.set("notes." + targetUUID.toString() + ".playerName", targetName);
        notesConfig.set("notes." + targetUUID.toString() + ".entries", notes);
        saveNotes();
        
        if(plugin.isGlobalDebugModeEnabled()){
            plugin.getLogger().info("[NotesManager] Added note for " + targetName + " by " + staffName);
        }
        return true;
    }

    /**
     * Récupère les notes pour un joueur, paginées.
     * @param targetUUID UUID du joueur.
     * @param itemsPerPage Nombre de notes par page.
     * @param pageNum Numéro de la page (commence à 1).
     * @return Une liste de chaînes représentant les notes formatées pour la page, ou une liste vide.
     */
    public List<String> getNotesForPlayer(UUID targetUUID, int itemsPerPage, int pageNum) {
        List<String> allNotes = notesConfig.getStringList("notes." + targetUUID.toString() + ".entries");
        if (allNotes == null || allNotes.isEmpty()) {
            return new ArrayList<>();
        }

        if (itemsPerPage <= 0) itemsPerPage = 5;
        if (pageNum <= 0) pageNum = 1;

        int totalEntries = allNotes.size();
        int startIndex = (pageNum - 1) * itemsPerPage;

        if (startIndex >= totalEntries) {
            return new ArrayList<>();
        }
        
        int endIndex = Math.min(startIndex + itemsPerPage, totalEntries);

        List<String> pageNotes = new ArrayList<>();
        for (int i = startIndex; i < endIndex; i++) {
            pageNotes.add(ChatColor.GOLD + "#" + (totalEntries - i) + " " + ChatColor.GRAY + allNotes.get(i));
        }
        return pageNotes;
    }
    
    /**
     * Récupère le nombre total de notes pour un joueur.
     * @param targetUUID L'UUID du joueur.
     * @return Le nombre total de notes.
     */
    public int getTotalNotesForPlayer(UUID targetUUID) {
        List<String> allNotes = notesConfig.getStringList("notes." + targetUUID.toString() + ".entries");
        return allNotes == null ? 0 : allNotes.size();
    }


    /**
     * Supprime une note spécifique pour un joueur en utilisant son index (1-based, le plus récent est #1).
     * @param targetUUID UUID du joueur.
     * @param noteIndex L'index de la note à supprimer (1 pour la plus récente).
     * @param staffName Nom du staff qui supprime la note.
     * @return La note supprimée ou null si l'index est invalide ou si aucune note n'a été trouvée.
     */
    public String removeNoteByIndex(UUID targetUUID, int noteIndex, String staffName) {
        List<String> notes = notesConfig.getStringList("notes." + targetUUID.toString() + ".entries");
        if (notes == null || notes.isEmpty()) {
            return null;
        }


        int listIndexToRemove = notes.size() - noteIndex;

        if (listIndexToRemove < 0 || listIndexToRemove >= notes.size()) {
            return null;
        }

        String removedNote = notes.remove(listIndexToRemove);
        notesConfig.set("notes." + targetUUID.toString() + ".entries", notes);
        saveNotes();
        
        if(plugin.isGlobalDebugModeEnabled()){
            plugin.getLogger().info("[NotesManager] " + staffName + " removed note (index " + noteIndex + ") for player UUID " + targetUUID.toString() + ": " + removedNote);
        }
        
        return removedNote;
    }
    
    /**
     * Retourne le nom du joueur associé à un UUID, tel que stocké dans le fichier de notes.
     * Utile si le joueur a changé de nom.
     * @param targetUUID L'UUID du joueur.
     * @return Le dernier nom connu du joueur, ou null.
     */
    public String getPlayerNameForNotes(UUID targetUUID) {
        return notesConfig.getString("notes." + targetUUID.toString() + ".playerName");
    }
}
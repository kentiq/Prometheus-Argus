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
                notesFile.createNewFile(); // Crée un fichier vide s'il n'existe pas
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
                ChatColor.stripColor(noteText) // On stocke le texte brut
        );
        notes.add(0, formattedNote); // Ajouter au début pour les plus récentes en premier

        // Optionnel: Limiter le nombre de notes par joueur
        int maxNotesPerPlayer = plugin.getConfig().getInt("notes.max_per_player", 20);
        while (notes.size() > maxNotesPerPlayer && maxNotesPerPlayer > 0) {
            notes.remove(notes.size() - 1);
        }

        notesConfig.set("notes." + targetUUID.toString() + ".playerName", targetName); // Stocker le dernier nom connu
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

        if (itemsPerPage <= 0) itemsPerPage = 5; // Un nombre raisonnable pour l'affichage chat
        if (pageNum <= 0) pageNum = 1;

        int totalEntries = allNotes.size();
        int startIndex = (pageNum - 1) * itemsPerPage;

        if (startIndex >= totalEntries) {
            return new ArrayList<>(); // Page vide
        }
        
        int endIndex = Math.min(startIndex + itemsPerPage, totalEntries);

        List<String> pageNotes = new ArrayList<>();
        for (int i = startIndex; i < endIndex; i++) {
            // Ajouter un numéro d'index global à la note pour faciliter la suppression
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

        // L'index est 1-based et correspond à l'affichage (plus récent = #1).
        // Dans la liste, l'index 0 est le plus récent.
        // Donc, une note avec l'index affiché 'k' est à notes.get(k-1) si on compte du plus ancien au plus récent.
        // Mais nos notes sont stockées avec la plus récente à l'index 0.
        // Si on affiche #1 comme la plus récente, et qu'elle est à l'index 0 de la liste:
        // Index dans la liste = noteIndex - 1 (si noteIndex est 1-based et #1 est le plus récent)
        // NON, c'est plus simple: l'index affiché #N (où N est le nombre total de notes - i de la boucle getNotes)
        // Donc pour supprimer la note #ID, il faut trouver l'élément qui correspond à ID = totalNotes - list_index
        // list_index = totalNotes - ID

        int listIndexToRemove = notes.size() - noteIndex; // Convertit l'index public (1-based, #1 = plus ancienne si affichée dans l'ordre)
                                                       // ou (1-based, #1 = plus récente si affichée dans l'ordre inversé)
                                                       // Ici, on stocke le plus récent à l'index 0.
                                                       // L'affichage donne #total - i, donc #1 est le dernier (plus ancien).
                                                       // Si l'utilisateur veut supprimer #1 (le plus ancien), il faut supprimer notes.get(notes.size() - 1)
                                                       // Si l'utilisateur veut supprimer #N (le N-ième plus ancien), il faut supprimer notes.get(notes.size() - N)
                                                       // C'est équivalent à l'index (notes.size() - noteIndex)

        if (listIndexToRemove < 0 || listIndexToRemove >= notes.size()) {
            return null; // Index invalide
        }

        String removedNote = notes.remove(listIndexToRemove);
        notesConfig.set("notes." + targetUUID.toString() + ".entries", notes);
        saveNotes();
        
        // Log optionnel de la suppression
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
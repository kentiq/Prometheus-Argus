# Changelog de Prometheus Argus

## [1.7-ALPHA] - 2025-03-06

### Ajouté
- Nouvelle classe `ModModeItemHandler` pour une meilleure gestion des items de modération
- Système de cooldowns pour les items de modération
- Messages personnalisés pour les interactions avec les items
- Intégration complète des paramètres de configuration du mode modérateur

### Modifié
- Amélioration de la gestion des items de modération
- Refonte du système de détection des joueurs ciblés
- Optimisation de la gestion des événements en mode modérateur

### Corrigé
- Correction du bug de mouvement en mode modérateur (plus de rollback de position)
- Les modérateurs peuvent maintenant se déplacer librement
- Meilleure gestion des interactions avec le monde selon la configuration
- Correction des problèmes de synchronisation des événements

### Configuration
- Ajout de nouveaux paramètres dans `modmode.yml` :
  - `allow-chat` : Contrôle l'accès au chat
  - `allow-commands` : Contrôle l'utilisation des commandes
  - `allow-interactions` : Contrôle les interactions avec le monde
  - `allow-damage` : Contrôle la prise de dégâts
  - `allow-pvp` : Contrôle les combats PvP
  - `allow-fall-damage` : Contrôle les dégâts de chute
  - `allow-hunger` : Contrôle la faim
  - `allow-mob-targeting` : Contrôle le ciblage par les mobs

### Notes
- Cette version apporte une refonte majeure du mode modérateur
- Les modérateurs ont maintenant plus de flexibilité dans leurs actions
- Le système est plus configurable et plus stable

## [1.6-ALPHA] - 2025-03-06

### Ajout
- Nouveau mode modérateur (`/pa mod`)
  - Items de modération (Freeze, Téléportation, Inspection, etc.)
  - Persistance entre les sessions
  - Auto-vanish intégré
  - Restrictions de gameplay
  - Sauvegarde et restauration de l'inventaire
  - Sauvegarde et restauration du GameMode
  - Notifications visuelles (titles) et sonores
  - Nouvelle section "Condition de passement" dans la configuration


### Correction
- Correction du bug de mouvement dans le mode modérateur
- Correction de la perte d'items lors de la désactivation du mode modérateur
- Amélioration des effets visuels et sonores

### Changement
- Passage du GameMode SPECTATOR à CREATIVE pour le mode modérateur
- Amélioration des messages de notification
- Optimisation du code pour une meilleure performance
- Mise à jour des permissions pour inclure les nouvelles fonctionnalités de modération
- Amélioration de la gestion des configurations pour les différents managers

## [1.5-ALPHA] - 2025-03-06

### Ajout
- Système de notes joueur
- Système de signalements
- Interface graphique staff
- Commandes de modération avancées

### Correction
- Divers bugs mineurs
- Amélioration des performances

### Changement
- Refonte du système de permissions
- Amélioration des messages
- Optimisation du code 
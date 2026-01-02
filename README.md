================================================================
                     PROJET SGBD (Mini-SGBD)
================================================================
Auteur(s) : NOEL Samuel; BEN NAJEM Brahim; DENAGNON Marwan
Langage   : Java
Date      :  Décembre 2025
================================================================

### 1. DESCRIPTION
Ce projet implémente un Système de Gestion de Base de Données (SGBD)
relationnel simplifié. Il gère la persistance des données sur disque,
la gestion des pages (BufferManager), et un moteur SQL basique.

### 2. STRUCTURE DU PROJET
- /src : Code source Java (.java).
- /bin : Classes compilées (.class) - généré automatiquement.
- *.sh : Scripts d'automatisation.

### 3. PRÉREQUIS
- Java installé (JDK).
- Terminal bash.
- Droits d'exécution : chmod +x *.sh

### 4. DONNEES
Pour le respect des consignes de rendu, les fichiers de données volumineux (.csv) peuvent avoir été retirés de l'archive. 
AVANT de lancer les tests ou les scénarios, veuillez vous assurer que les fichiers suivants sont présents à la RACINE du projet :
- R.csv, S.csv, T.csv, V.csv, B.csv
(Ces fichiers sont indispensables car le moteur de commande et les suites de tests les appellent par leurs noms respectifs).

### 5. COMPILATION
Avant toute exécution, compilez le projet :

    ./compile.sh

### 6. LANCEMENT DES TESTS
Pour vérifier le bon fonctionnement technique (DiskManager, etc.) :

    ./test.sh

(Le script exécutera la suite de tests complète).

### 7. LANCEMENT DE L'APPLICATION
Le SGBD nécessite obligatoirement deux fichiers en arguments :
1. Un fichier de configuration (ex: config.txt)
2. Un fichier de scénario/commandes

Commande :
    ./app.sh [CheminConfig] < [CheminScenario]

Exemple d'utilisation :
    ./app.sh config.txt < commandes.sql

Note : Assurez-vous que ces fichiers existent avant de lancer la commande.

### 8 NOTES IMPORTANTES (Vigilance Évaluateur)
- Nettoyage : L'archive a été vidée de tout fichier binaire généré (dossier BinData, catalogue.db). Le système les recréera proprement lors du premier lancement.
- Persistance : Pour garantir que les données sont bien écrites sur le disque (Flush des buffers), utilisez toujours la commande 'EXIT' pour quitter le programme.
- Flexibilité des noms : Le SGBD est générique. Si vous ajoutez vos propres fichiers .csv, assurez-vous de fournir le nom exact dans la commande IMPORT ou APPEND entre parenthèses, ex: APPEND INTO MaTable ALLRECORDS (MonFichier.csv).
- Le script app.sh est configuré avec 2 Go de RAM alloués (-Xmx2g) pour supporter le chargement de fichiers volumineux (Scénarios Big Data).

================================================================# Projet-BDDA
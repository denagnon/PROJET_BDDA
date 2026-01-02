#!/bin/bash
# Lancement du SGBD
# Lancement du SGBD avec 2Go de RAM pour le BigData
# Usage attendu : ./app.sh <FichierConfig> <FichierScenario>
# Exemple : ./app.sh config.txt commande_scenario.txt
java -Xmx2g -cp bin sgbd.Main "$@"

#!/bin/bash
# Lancement du SGBD
# Usage attendu : ./app.sh <FichierConfig> <FichierScenario>
# Exemple : ./app.sh config.txt commande_scenario.txt
java -cp bin sgbd.Main "$@"
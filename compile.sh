#!/bin/bash
mkdir -p bin

# La commande magique 'find' cherche tous les fichiers .java dans tous les sous-dossiers
javac -d bin -sourcepath src $(find src -name "*.java")

echo "Compilation terminee."


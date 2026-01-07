#!/bin/bash
# Lance le SGBD avec seulement 64Mo de RAM pour prouver le Pipelining
echo "[DEMO] Lancement Big Data (RAM limitée à 64Mo)..."
java -Xmx64m -cp bin sgbd.Main config.txt < test_bigdata.txt
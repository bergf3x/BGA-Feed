#!/bin/bash
# Probelauf: Uebernehmen grau bis geaendert (Aktualisierung, Einstellungen, Unterseite, Ort)
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UI="python3 tools/ui.py"
log() { echo "$*" | tee -a $R; }
shot() { adb exec-out screencap -p > $OUT/$1.png; log "Screenshot $1"; }
tapw() { $UI tap "$1" ${3:-0} | tee -a $R; sleep ${2:-1.5}; }
adb install -r -g app.apk | tail -1 | tee -a $R
adb logcat -c
adb shell am start -W -n $ACT >/dev/null
sleep 25
tapw "Nachrichten" 1.5; tapw "Beides" 1.5; tapw "Hinzufügen" 3
tapw "Add to home screen" 3; tapw "Add automatically" 3; tapw "Hinzufügen" 3
adb shell input keyevent KEYCODE_HOME; sleep 20
log "== 1 Aktualisierung unveraendert (grau) =="
tapw "Einstellungen" 3; tapw "Aktualisierung" 3; shot v1_grau
log "== 2 Intervall geaendert (hell) =="
tapw "15 Min" 2 0; sleep 1; shot v2_hell
log "== 3 zurueck auf alten Wert (wieder grau) =="
tapw "30 Min" 2 0; sleep 1; shot v3_wieder_grau
tapw "Abbrechen" 2
log "== 4 Einstellungen Hauptseite: grau, dann Unterseite Darstellung =="
shot v4_main_grau
tapw "Darstellung" 3; shot v5_unterseite_grau
tapw "Kompakt" 2; sleep 1; shot v6_unterseite_hell
$UI texts | head -40 | tee -a $R
log "== Ende =="

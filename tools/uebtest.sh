#!/bin/bash
# Probelauf: Aktualisierung mit Uebernehmen/Abbrechen/Ausgeglichen, Ort-Auswahl mit Uebernehmen
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UI="python3 tools/ui.py"
log() { echo "$*" | tee -a $R; }
shot() { adb exec-out screencap -p > $OUT/$1.png; log "Screenshot $1"; }
tapw() { $UI tap "$1" ${3:-0} | tee -a $R; sleep ${2:-1.5}; }
alarm() { adb shell dumpsys alarm | grep -B1 -A4 "$PKG" | grep -E "ELAPSED|RTC|type|bgawidget" | head -4 | tee -a $R; }
adb install -r -g app.apk | tail -1 | tee -a $R
adb logcat -c
adb shell am start -W -n $ACT >/dev/null
sleep 25
tapw "Nachrichten" 1.5; tapw "Beides" 1.5; tapw "Hinzufügen" 3
tapw "Add to home screen" 3; tapw "Add automatically" 3; tapw "Hinzufügen" 3
adb shell input keyevent KEYCODE_HOME; sleep 20
log "== 1 Aktualisierung oeffnen =="
tapw "Einstellungen" 3; tapw "Aktualisierung" 3; shot u1_start
log "== 2 Ausgeglichen waehlen, noch NICHT uebernehmen =="
tapw "Ausgeglichen" 2; shot u2_ausgeglichen
tapw "Abbrechen" 2
adb shell input keyevent KEYCODE_HOME; sleep 2
log "Wecker nach Abbrechen (Stand wie vorher):"; alarm
log "== 3 Erneut: Manuell + Uebernehmen, dann Ausgeglichen + Uebernehmen =="
tapw "Einstellungen" 3; tapw "Aktualisierung" 3
tapw "Manuell" 1.5 0; tapw "Manuell" 1.5 1; shot u3_manuell
tapw "Übernehmen" 2; adb shell input keyevent KEYCODE_HOME; sleep 3
log "Wecker nach Manuell (keiner):"; alarm
tapw "Einstellungen" 3; tapw "Aktualisierung" 3; tapw "Ausgeglichen" 2; shot u4_ausgeglichen2
tapw "Übernehmen" 2; adb shell input keyevent KEYCODE_HOME; sleep 3
log "Wecker nach Ausgeglichen (3 Std):"; alarm
log "== 4 Wetter-Ort =="
tapw "Berchtesgaden" 3; shot u5_ort
$UI texts | head -30 | tee -a $R
log "== Ende =="

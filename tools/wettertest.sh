#!/bin/bash
# Probelauf 1.10: Wetter-Rubrik, Vorschau-Fenster einer Karte, Einstellungen Wetter-Karten
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UI="python3 tools/ui.py"
log() { echo "$*" | tee -a $R; }
shot() { adb exec-out screencap -p > $OUT/$1.png; log "Screenshot $1"; }
tapw() { $UI tap "$1" ${3:-0} | tee -a $R; sleep ${2:-1.5}; }
adb install -r -g app.apk | tail -1 | tee -a $R
adb shell am start -W -n $ACT >/dev/null; sleep 12
shot w0_start
$UI texts | head -30 | tee -a $R
tapw "Wetter" 2
shot w1_menue
$UI texts | head -30 | tee -a $R
tapw "Bergwetter" 1
shot w1b_menue_gewaehlt
tapw "Übernehmen" 4
shot w2_bergwetter
$UI texts | head -30 | tee -a $R
tapw "Bergbericht" 3
shot w3_vorschau
$UI texts | head -20 | tee -a $R
adb shell input keyevent KEYCODE_BACK; sleep 1.5
tapw "Wetter" 2
tapw "Bergwetter" 1
tapw "Region" 1
tapw "Übernehmen" 4
shot w4_region
$UI texts | head -30 | tee -a $R
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell am start -W -n $ACT >/dev/null; sleep 4
tapw "Einstellungen" 3
tapw "Wetter-Karten" 3
shot w5_einstellungen
$UI texts | head -40 | tee -a $R
adb shell input swipe 540 1900 540 700 400; sleep 1
shot w6_einstellungen_unten
$UI texts | head -40 | tee -a $R

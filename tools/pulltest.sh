#!/bin/bash
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UI="python3 tools/ui.py"
log() { echo "$*" | tee -a $R; }
shot() { adb exec-out screencap -p > $OUT/$1.png; log "Screenshot $1"; }
adb install -r -g app.apk | tail -1 | tee -a $R
adb logcat -c
adb shell am start -W -n $ACT | grep TotalTime | tee -a $R
sleep 45; shot 1_start
# langsamer Zug mit Halten (motionevent: down, move, Screenshot, up)
adb shell input motionevent DOWN 540 1500
for y in 1540 1580 1620 1660 1700; do adb shell input motionevent MOVE 540 $y; done
sleep 0.5; shot 2_halb_gezogen
for y in 1760 1820 1900 1980 2060 2140; do adb shell input motionevent MOVE 540 $y; done
sleep 0.5; shot 3_loslassen_bereit
adb shell input motionevent UP 540 2140
sleep 0.4; shot 4_laedt
for i in $(seq 1 30); do sleep 2; $UI has "Aktualisiert" | grep -q gefunden && break; done
shot 5_fertig
sleep 3; shot 6_zurueck
# kurzer Zug unter der Schwelle
adb shell input motionevent DOWN 540 1500
for y in 1530 1560 1590; do adb shell input motionevent MOVE 540 $y; done
adb shell input motionevent UP 540 1590
sleep 1; shot 7_kurz
# normales Scrollen weiter moeglich
adb shell input swipe 540 2000 540 900 300; sleep 1; shot 8_scroll
adb logcat -d | grep -E "FATAL EXCEPTION" -A8 | head -20 | tee -a $R
log "== Ende =="

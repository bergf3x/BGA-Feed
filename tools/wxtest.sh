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
sleep 50; shot 1_oben
adb shell input swipe 540 2100 540 1900 700; sleep 1.5; shot 2a_etwas
adb shell input swipe 540 2100 540 1800 600; sleep 1.5; shot 2b_mehr
adb shell input swipe 540 2100 540 1800 600; sleep 1.5; shot 2c_noch_mehr
for i in 1 2 3; do adb shell input swipe 540 2000 540 900 300; sleep 0.6; done; sleep 1; shot 3_unten
$UI texts | head -20 > $OUT/3_unten.txt
for i in $(seq 1 12); do adb shell input swipe 540 900 540 2100 200; sleep 0.4; done; sleep 1.5; shot 4_wieder_oben
adb shell input swipe 540 1600 540 1750 700; sleep 1.5; shot 4b_etwas_zurueck
for i in 1 2 3; do adb shell input swipe 540 2000 540 900 300; sleep 0.6; done; sleep 1
$UI tap "Wetter Berchtesgaden, 2°"; $UI tap "Wetter Berchtesgaden" | tee -a $R; sleep 2; shot 5_zeile_angetippt
adb logcat -d | grep -E "FATAL|AndroidRuntime" | head -10 | tee -a $R
log "== Ende =="

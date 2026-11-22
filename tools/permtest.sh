#!/bin/bash
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UI="python3 tools/ui.py"
log() { echo "$*" | tee -a $R; }
shot() { adb exec-out screencap -p > $OUT/$1.png; log "Screenshot $1"; }
tapw() { $UI tap "$1" | tee -a $R; sleep ${2:-1.5}; }
adb install -r app.apk | tail -1 | tee -a $R
adb logcat -c
adb shell am start -W -n $ACT | grep TotalTime | tee -a $R
sleep 30
shot 1_start
tapw "Nachrichten" 1.5; shot 2_aufgeklappt
tapw "Beides" 1.5; shot 3_beides
adb shell input swipe 540 1500 540 1100 400; sleep 1; shot 4_berechtigungen
$UI texts | head -40 | tee -a $R
tapw "Erlauben" 2; shot 5_systemabfrage
$UI tap "Allow" | tee -a $R; sleep 3; shot 6_nach_erlauben
adb shell dumpsys package $PKG | grep -i "POST_NOTIFICATIONS" | tee -a $R
adb shell am force-stop $PKG; adb shell am start -W -n $ACT >/dev/null; sleep 8; shot 7_neustart
$UI texts | grep -E "offen|Benachrichtigungen|Akku" | tee -a $R
adb logcat -d | grep -E "FATAL EXCEPTION" -A8 | head -30 | tee -a $R
log "== Ende =="

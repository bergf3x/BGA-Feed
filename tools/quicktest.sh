#!/bin/bash
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UI="python3 tools/ui.py"
log() { echo "$*" | tee -a $R; }
shot() { adb exec-out screencap -p > $OUT/$1.png; log "Screenshot $1"; }
tapw() { $UI tap "$1" | tee -a $R; sleep ${2:-1.5}; }
adb install -r -g app.apk | tail -1 | tee -a $R
adb logcat -c
adb shell am start -W -n $ACT | grep TotalTime | tee -a $R
sleep 35
tapw "Einstellungen" 2; tapw "Nachrichten" 2
for n in "BGLand24" "ORF Salzburg"; do tapw "$n" 0.8; done
tapw "Fertig" 1.5; tapw "Speichern" 2
adb shell am start -n $ACT >/dev/null; sleep 1; tapw "Aktualisieren" 25
tapw "Zeitungen ▾" 2; shot 1_zeitungen; tapw "Schließen" 1.5
tapw "Einstellungen" 2; adb shell input swipe 540 1600 540 900 300; sleep 1
tapw "Über die App" 2; tapw "Technisches Protokoll" 1.5; shot 2_protokoll; $UI texts | grep -E ":[0-9][0-9]  " | tee -a $R
tapw "Neuigkeiten" 1.5; shot 3_neuigkeiten; tapw "Schließen" 1.5
adb shell monkey -p $PKG --throttle 100 --pct-syskeys 0 -s 99 -v 2000 > $OUT/monkey.txt 2>&1
grep -E "Events injected|CRASH|ANR" $OUT/monkey.txt | tee -a $R
adb logcat -d | grep -E "FATAL EXCEPTION" -A6 | grep -v uiautomator | head -20 | tee -a $R
log "== Ende =="

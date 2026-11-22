#!/bin/bash
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UI="python3 tools/ui.py"
log() { echo "$*" | tee -a $R; }
shot() { adb exec-out screencap -p > $OUT/$1.png; log "Screenshot $1"; }
tapw() { $UI tap "$1" | tee -a $R; sleep ${2:-1.5}; }
wb() { python3 tools/wbounds.py $PKG; }
adb install -r -g app.apk | tail -1 | tee -a $R
adb logcat -c
adb shell am start -W -n $ACT | grep TotalTime | tee -a $R
sleep 35
tapw "Hinzufügen" 3
shot 0_pin_dialog
$UI texts | head -12 | tee -a $R
tapw "Add to home screen" 3; tapw "Add automatically" 3; tapw "Hinzufügen" 3
adb shell input keyevent KEYCODE_HOME; sleep 4
shot 1_home_start
B=$(wb); log "Widget: $B"
read X1 Y1 X2 Y2 <<< "$B"
if [ "$X2" != "0" ]; then
  CX=$(( (X1+X2)/2 ))
  for step in 1 2 3; do
    # lange druecken -> Rahmen zum Groesse aendern, dann unteren Griff nach unten ziehen
    B=$(wb); read X1 Y1 X2 Y2 <<< "$B"; CY=$(( (Y1+Y2)/2 ))
    adb shell input swipe $CX $CY $CX $CY 1200; sleep 1.5
    adb shell input keyevent KEYCODE_BACK; sleep 0.5
    adb shell input swipe $CX $CY $CX $CY 1200; sleep 2
    shot ${step}_resize_frame
    B=$(wb); read X1 Y1 X2 Y2 <<< "$B"
    adb shell input swipe $CX $((Y2+2)) $CX $((Y2+330)) 1500; sleep 2
    adb shell input keyevent KEYCODE_HOME; sleep 4
    B=$(wb); log "Nach Vergroessern $step: $B"
    shot ${step}_groesse
  done
  # Aufklappen im groessten Zustand nicht moeglich; einmal klein machen fuer Pfeil-Test
fi
$UI texts | head -30 | tee -a $R
adb logcat -d | grep -E "FATAL EXCEPTION|Error inflating|Couldn't add widget|RemoteViews" -A5 | head -30 | tee -a $R
log "== Ende =="

#!/bin/bash
# Probelauf Widget: Intervalle, Manuell, Wecker, Abrufzaehler, Groessenstufen, Aufklapp-Pfeil
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UI="python3 tools/ui.py"
log() { echo "$*" | tee -a $R; }
shot() { adb exec-out screencap -p > $OUT/$1.png; log "Screenshot $1"; }
tapw() { $UI tap "$1" ${3:-0} | tee -a $R; sleep ${2:-1.5}; }
wb() { python3 tools/wbounds.py $PKG; }
alarm() { adb shell dumpsys alarm | grep -B1 -A4 "$PKG" | grep -E "ELAPSED|RTC|type|bgawidget" | head -6 | tee -a $R; }
stat() { adb logcat -d -s BGAstat:I | tail -${1:-6} | tee -a $R; }
adb install -r -g app.apk | tail -1 | tee -a $R
adb logcat -c
adb shell am start -W -n $ACT | grep TotalTime | tee -a $R
sleep 30
log "== 1 Widget 'Beides' ablegen =="
tapw "Nachrichten" 1.5; tapw "Beides" 1.5; tapw "Hinzufügen" 3
tapw "Add to home screen" 3; tapw "Add automatically" 3; tapw "Hinzufügen" 3
adb shell input keyevent KEYCODE_HOME; sleep 25
shot 1_start; log "Widget: $(wb)"
stat 4
log "== 2 Wecker bei Automatik (30 Min, ohne Benachrichtigungen: ELAPSED_REALTIME ohne WAKEUP) =="
alarm
log "== 3 Pfeil: Wetter aufklappen =="
tapw "Wetter aufklappen" 3; shot 2_aufgeklappt
tapw "Wetter aufklappen" 3; shot 3_zugeklappt
log "== 4 Aktualisieren zweimal: zweiter Abruf soll weniger Daten laden (304) =="
adb shell am broadcast -a de.bergf3x.bgawidget.REFRESH -n $PKG/de.bergf3x.bgawidget.WidgetProvider >/dev/null; sleep 25
adb shell am broadcast -a de.bergf3x.bgawidget.REFRESH -n $PKG/de.bergf3x.bgawidget.WidgetProvider >/dev/null; sleep 25
stat 8
log "== 5 Reiterwechsel darf nichts laden =="
N1=$(adb logcat -d -s BGAstat:I | grep -c fetchAll); log "fetchAll vorher: $N1"
tapw "Alle" 4; tapw "Alle" 4
N2=$(adb logcat -d -s BGAstat:I | grep -c fetchAll); log "fetchAll nachher: $N2"
log "== 6 Einstellungen > Aktualisierung =="
tapw "Einstellungen" 3; shot 4_menue; tapw "Aktualisierung" 3; shot 5_auto_warnung
tapw "Manuell" 1.5 0; tapw "Manuell" 1.5 1; shot 6_manuell
tapw "Schließen" 2
adb shell input keyevent KEYCODE_HOME; sleep 3
log "== 7 Manuell: kein Wecker mehr =="
alarm
N3=$(adb logcat -d -s BGAstat:I | grep -c "Abruf automatisch"); log "automatische Abrufe bisher: $N3"
log "== 8 Manuell: Aktualisieren-Knopf holt trotzdem =="
adb shell am broadcast -a de.bergf3x.bgawidget.REFRESH -n $PKG/de.bergf3x.bgawidget.WidgetProvider >/dev/null; sleep 25
stat 3
log "== 9 Wieder Automatik 15 Min: Wecker kommt zurueck =="
tapw "Einstellungen" 3; tapw "Aktualisierung" 3; tapw "15 Min" 2 0; tapw "1 Std" 2 1; shot 7_auto_15_1std
tapw "Schließen" 2; adb shell input keyevent KEYCODE_HOME; sleep 3
alarm
log "== 10 Groessenstufen: Widget vergroessern =="
for step in 1 2; do
  B=$(wb); read X1 Y1 X2 Y2 <<< "$B"; CX=$(( (X1+X2)/2 )); CY=$(( (Y1+Y2)/2 ))
  adb shell input swipe $CX $CY $CX $CY 1200; sleep 1.5
  adb shell input keyevent KEYCODE_BACK; sleep 0.5
  adb shell input swipe $CX $CY $CX $CY 1200; sleep 2
  adb shell input swipe $CX $Y2 $CX $((Y2+280)) 1800; sleep 2
  adb shell input keyevent KEYCODE_HOME; sleep 5
  log "Groesse $step: $(wb)"; shot 8_groesse_$step
done
$UI texts | head -40 | tee -a $R
adb logcat -d | grep -E "FATAL EXCEPTION|Error inflating" -A6 | head -30 | tee -a $R
log "== Ende =="

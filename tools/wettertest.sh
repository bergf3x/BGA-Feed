#!/bin/bash
# Probelauf 1.10 Variante B: untere Leiste, Wetter-Bereich, Vorschau, Gemerkt, Einstellungen, Widget
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UI="python3 tools/ui.py"
log() { echo "$*" | tee -a $R; }
shot() { adb exec-out screencap -p > $OUT/$1.png; log "Screenshot $1"; }
tapw() { $UI tap "$1" ${3:-0} | tee -a $R; sleep ${2:-1.5}; }
adb install -r -g app.apk | tail -1 | tee -a $R
adb shell am start -W -n $ACT >/dev/null; sleep 14
shot a1_start
$UI texts | head -30 | tee -a $R
tapw "Wetter" 3
shot a2_wetter_berg
$UI texts | head -30 | tee -a $R
tapw "Region" 3
shot a3_wetter_region
tapw "Bergwetter" 3
$UI tapin "Bergbericht" 0 | tee -a $R; sleep 3
shot a4_vorschau
$UI texts | head -20 | tee -a $R
adb shell input keyevent KEYCODE_BACK; sleep 1.5
tapw "Gemerkt" 3
shot a5_gemerkt
adb shell input keyevent KEYCODE_BACK; sleep 1.5
tapw "News" 3
shot a6_news
tapw "Einstellungen" 3
shot a7_einstellungen
$UI texts | head -40 | tee -a $R
tapw "Wetter" 3
shot a8_einst_wetter
adb shell input swipe 540 1900 540 700 400; sleep 1
shot a9_einst_wetter_unten
$UI texts | head -40 | tee -a $R
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
log "== Widget =="
adb shell am start -W -n $ACT >/dev/null; sleep 3
tapw "Hinzufügen" 3
tapw "Add to home screen" 3; tapw "Add automatically" 3; tapw "Hinzufügen" 3
adb shell input keyevent KEYCODE_HOME; sleep 10
shot c1_widget_klein
B=$(python3 tools/wbounds.py $PKG); log "Widget: $B"
read X1 Y1 X2 Y2 <<< "$B"
if [ "$X2" != "0" ]; then
  CX=$(( (X1+X2)/2 ))
  for step in 1 2 3; do
    B=$(python3 tools/wbounds.py $PKG); read X1 Y1 X2 Y2 <<< "$B"; CY=$(( (Y1+Y2)/2 ))
    adb shell input swipe $CX $CY $CX $CY 1200; sleep 1.5
    adb shell input keyevent KEYCODE_BACK; sleep 0.5
    adb shell input swipe $CX $CY $CX $CY 1200; sleep 2
    adb shell input swipe $CX $((Y2+2)) $CX $((Y2+330)) 1500; sleep 2
    adb shell input keyevent KEYCODE_HOME; sleep 6
  done
fi
sleep 6
shot c2_widget_gross
log "Widget: $(python3 tools/wbounds.py $PKG)"
$UI texts | grep -E "News|Wetter|Gemerkt" | head -8 | tee -a $R
tapw "Wetter" 3
sleep 3
shot c3_widget_wetter
log "== Ende =="

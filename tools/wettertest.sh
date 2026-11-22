#!/bin/bash
# Probelauf: hoehere Leiste, Gemerkt als Liste mit Vorschaubild
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UI="python3 tools/ui.py"
log() { echo "$*" | tee -a $R; }
shot() { adb exec-out screencap -p > $OUT/$1.png; log "Screenshot $1"; }
tapw() { $UI tap "$1" ${3:-0} | tee -a $R; sleep ${2:-1.5}; }
adb install -r -g app.apk | tail -1 | tee -a $R
adb shell am start -W -n $ACT >/dev/null; sleep 40
shot s1_news
for n in 0 1 2; do
  $UI tapin " · Anzeiger" 0 | tee -a $R; sleep 3
  if [ $n = 0 ]; then shot s2_vorschau; fi
  tapw "Merken" 1.5
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
  adb shell input swipe 540 1900 540 1500 300; sleep 1
done
tapw "Gemerkt" 3
shot s3_gemerkt
$UI texts | head -30 | tee -a $R
tapw "Wetter" 3
shot s4_wetter
tapw "News" 3
shot s5_news_zurueck
log "== Ende =="

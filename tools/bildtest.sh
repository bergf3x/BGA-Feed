#!/bin/bash
# Probelauf 1.09: Vorschaubilder je Quelle, Vorschau-Fenster, Quellen-Waechter, Einsortierung
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UI="python3 tools/ui.py"
log() { echo "$*" | tee -a $R; }
shot() { adb exec-out screencap -p > $OUT/$1.png; log "Screenshot $1"; }
tapw() { $UI tap "$1" ${3:-0} | tee -a $R; sleep ${2:-1.5}; }
adb install -r -g app.apk | tail -1 | tee -a $R
adb shell setprop log.tag.BGAclass DEBUG
adb shell setprop log.tag.BGAimg INFO
adb logcat -c
adb shell am start -W -n $ACT >/dev/null
sleep 70
shot b1_liste
log "== Bilder je Host (Logcat BGAimg) =="
adb logcat -d -s BGAimg:I > $OUT/img.txt
log "Fehlschlaege: $(grep -c 'nicht geladen' $OUT/img.txt)"
grep 'nicht geladen' $OUT/img.txt | sed -E 's/.*(HTTP [0-9]+|[A-Za-z.]*Exception[^ ]*|zu klein|kein Bild|zu gross).* (https?:\/\/[^/]+).*/\1 \2/' | sort | uniq -c | sort -rn | head -10 | tee -a $R
log "Speicher: $(adb shell run-as $PKG sh -c 'ls cache/img | wc -l' 2>/dev/null) Bilddateien, $(adb shell run-as $PKG sh -c 'du -sk cache/img' 2>/dev/null)"
log "== ImageViews auf dem Bildschirm =="
$UI imgs | tee -a $R
log "== Vorschau-Fenster je Quelle (Stichproben) =="
i=0
for SRC in "Anzeiger" "Tagblatt" "BGLand24" "chiemgau24" "ORF" "Merkur" "BR24" "Landratsamt" "Nationalpark"; do
  i=$((i+1)); found=0
  for try in 1 2 3 4 5 6 7 8; do
    out=$($UI tapin " · $SRC" 0)
    echo "$out" | tee -a $R
    if echo "$out" | grep -q getippt; then found=1; break; fi
    adb shell input swipe 540 1900 540 700 400; sleep 1
  done
  if [ $found = 1 ]; then sleep 2.5; shot b2_vorschau_$i; $UI texts | head -12 | tee -a $R; adb shell input keyevent KEYCODE_BACK; sleep 1.5; fi
done
log "== Einsortierung (Auszug aus Logcat BGAclass) =="
adb logcat -d -s BGAclass:D | sed -E 's/^.*BGAclass *: //' | sort -u > $OUT/class.txt
wc -l < $OUT/class.txt | tee -a $R
cut -d' ' -f1 $OUT/class.txt | sed -E 's#^([^/]*/[^/]*).*#\1#' | sort | uniq -c | sort -rn | head -40 | tee -a $R
for T in "Deutschland & Welt/Leute" "Deutschland & Welt/Blaulicht" "Deutschland & Welt/Wirtschaft" "Deutschland & Welt/Wissen & Digital" "Deutschland & Welt/Politik & Gesellschaft"; do
  log "-- $T (bis 8 Beispiele)"; grep -F "$T <-" $OUT/class.txt | head -8 | cut -c1-190 | tee -a $R
done
log "-- Blaulicht (alle Regionen, 8 Beispiele)"; grep "/Blaulicht <-" $OUT/class.txt | head -8 | cut -c1-190 | tee -a $R
log "== Quellen-Waechter (Info) =="
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell am start -W -n $ACT >/dev/null; sleep 4
tapw "Einstellungen" 3; tapw "Über die App" 3; tapw "Quellen" 2; shot b3_quellen
$UI texts | grep -E "●|○" | tee -a $R
log "== Widget mit Bildern =="
adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_HOME; sleep 2
log "== Ende =="

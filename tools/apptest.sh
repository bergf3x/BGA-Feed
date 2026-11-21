#!/bin/bash
# Alltagstest der Test-App im Emulator. Ergebnisse in out/ (Screenshots, Logs, report.txt).
set +e
PKG=de.bergf3x.bgawidget.test
OUT=out
mkdir -p $OUT
R=$OUT/report.txt
UI="python3 tools/ui.py"
log() { echo "$*" | tee -a $R; }
shot() { adb exec-out screencap -p > $OUT/$1.png; log "Screenshot $1"; }
appmem() { adb shell dumpsys meminfo $PKG | grep -E "TOTAL PSS|TOTAL:" | head -1 | tr -s ' '; }
crashes() { adb logcat -d -b crash | grep -c "$PKG"; }

log "== Installation =="
adb install -r -g app.apk | tail -1 | tee -a $R
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb logcat -c

log "== Kaltstart (erster Start, ohne Daten) =="
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.MainActivity | grep -E "TotalTime|WaitTime" | tee -a $R
sleep 3; shot 01_erststart
log "Warte 75 s auf ersten Abruf aller Quellen ..."
for i in $(seq 1 15); do sleep 5; if $UI has "Lade Nachrichten" | grep -q gefunden; then :; else break; fi; done
sleep 20; shot 02_nach_abruf
log "Speicher nach Abruf: $(appmem)"

log "== Quellen einschalten (alle) über Einstellungen -> Quellen & Themen =="
$UI tap "Einstellungen" | tee -a $R; sleep 2; shot 03_einstellungen
$UI tap "Quellen & Themen" | tee -a $R; sleep 2; shot 04_quellen
$UI texts > $OUT/quellen_texts.txt
for n in "BGLand24" "chiemgau24" "ORF Salzburg" "Traunsteiner Tagblatt" "Landratsamt BGL" "Nationalpark Berchtesgaden"; do
  $UI tap "$n" | tee -a $R; sleep 1
done
shot 05_quellen_an
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 2

log "== Aktualisieren (alle Quellen direkt) =="
T0=$(date +%s)
$UI tap "Aktualisieren" | tee -a $R
for i in $(seq 1 30); do sleep 3; if $UI has "Lade Nachrichten" | grep -q gefunden; then :; else break; fi; done
log "Aktualisierung sichtbar fertig nach ca. $(( $(date +%s) - T0 )) s"
sleep 15; shot 06_nach_aktualisieren
log "Speicher: $(appmem)"

log "== Direktabruf-Protokoll der App =="
adb shell run-as $PKG cat shared_prefs/direct.xml | sed 's/&#10;/\n/g;s/&quot;/"/g' | grep -vE "state_|^<" | tail -40 | tee -a $R
adb shell run-as $PKG ls -l files/feeds | tee -a $R

log "== Scroll-Flüssigkeit (Nachrichtenliste) =="
adb shell dumpsys gfxinfo $PKG reset >/dev/null
for i in $(seq 1 8); do adb shell input swipe 540 1900 540 700 250; sleep 0.4; done
for i in $(seq 1 8); do adb shell input swipe 540 700 540 1900 250; sleep 0.4; done
adb shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile" | tee -a $R

log "== Themenmenü: Region =="
$UI tap "Region" | tee -a $R; sleep 2; shot 07_menue_region
$UI texts > $OUT/menue_region_texts.txt
$UI tap "BGLand24" | tee -a $R; sleep 1; shot 08_menue_bgland_offen
$UI tap "Berchtesgaden" | tee -a $R; sleep 1
$UI tap "Rupertiwinkel" | tee -a $R; sleep 1; shot 09_menue_zwei_gewaehlt
$UI tap "chiemgau24" | tee -a $R; sleep 1; shot 10_menue_chiemgau_offen
$UI tap "Schließen" | tee -a $R; sleep 2; shot 11_liste_mehrfachauswahl
$UI texts > $OUT/liste_mehrfach_texts.txt
log "== Themenmenü: Sport =="
$UI tap "Sport" | tee -a $R; sleep 2; shot 12_menue_sport
$UI tap "Schließen"; sleep 1
$UI tap "Alle" | tee -a $R; sleep 2

log "== Unterseiten =="
for n in "Live Webcams" "Suche" "Gemerkt"; do
  adb shell dumpsys gfxinfo $PKG reset >/dev/null
  $UI tap "$n" | tee -a $R; sleep 4; shot "13_$(echo $n | tr ' ' '_')"
  adb shell input keyevent KEYCODE_BACK; sleep 2
done
$UI tap "Einstellungen"; sleep 2; $UI tap "Info" | tee -a $R; sleep 2; shot 14_info
$UI texts > $OUT/info_texts.txt
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1

log "== Drehen (Neuaufbau der Ansicht) =="
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1; sleep 3; shot 15_quer
adb shell settings put system user_rotation 0; sleep 3

log "== Offline (WLAN + Daten aus) =="
adb shell svc wifi disable; adb shell svc data disable; sleep 3
$UI tap "Aktualisieren" | tee -a $R; sleep 25; shot 16_offline
$UI texts | head -30 > $OUT/offline_texts.txt
adb shell svc wifi enable; adb shell svc data enable; sleep 8

log "== Hintergrund / Planung =="
adb shell dumpsys jobscheduler | grep -A3 "$PKG" | head -20 | tee -a $R
adb shell dumpsys alarm | grep -B1 -A2 "$PKG" | head -20 | tee -a $R

log "== Belastungstest (Monkey, 2500 zufällige Eingaben) =="
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 2
adb shell monkey -p $PKG --throttle 120 --pct-syskeys 0 --pct-appswitch 5 -s 4242 -v 2500 > $OUT/monkey.txt 2>&1
grep -E "Events injected|CRASH|ANR|Monkey aborted" $OUT/monkey.txt | tee -a $R
sleep 3
log "Speicher nach Monkey: $(appmem)"

log "== Kaltstart mit vorhandenen Daten (3x) =="
for i in 1 2 3; do
  adb shell am force-stop $PKG; sleep 1
  adb shell am start -W -n $PKG/.MainActivity | grep -E "TotalTime" | tee -a $R
  sleep 3
done

log "== Abstürze / ANR / Fehler im Log =="
adb logcat -d > $OUT/logcat.txt
log "Crash-Puffer Einträge der App: $(crashes)"
grep -nE "FATAL EXCEPTION|ANR in $PKG|Process: $PKG" $OUT/logcat.txt | head -20 | tee -a $R
grep -nE "AndroidRuntime" -A12 $OUT/logcat.txt | grep -m1 -A12 "FATAL" | tee -a $R
grep -cE "StrictMode|Skipped [0-9]+ frames" $OUT/logcat.txt | sed 's/^/StrictMode\/Skipped-Frames Meldungen: /' | tee -a $R
grep -E "Skipped [0-9]+ frames" $OUT/logcat.txt | head -5 | tee -a $R
log "== Ende =="

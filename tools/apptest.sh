#!/bin/bash
# Alltagstest der Test-App im Emulator. Ergebnisse in out/ (Screenshots, Logs, report.txt).
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
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
adb shell am start -W -n $ACT | grep -E "TotalTime|WaitTime" | tee -a $R
sleep 3; shot 01_erststart
log "Warte 75 s auf ersten Abruf aller Quellen ..."
for i in $(seq 1 15); do sleep 5; if $UI has "Lade Nachrichten" | grep -q gefunden; then :; else break; fi; done
sleep 20; shot 02_nach_abruf
log "Speicher nach Abruf: $(appmem)"
adb shell dumpsys meminfo $PKG | grep -E "Java Heap|Native Heap|Graphics|Views:|Activities:" | tr -s ' ' | tee -a $R

log "== Quellen einschalten (alle) über Einstellungen -> Quellen & Themen =="
$UI tap "Einstellungen" | tee -a $R; sleep 2; shot 03_einstellungen
$UI tap "Quellen & Themen" | tee -a $R; sleep 2; shot 04_quellen
$UI texts > $OUT/quellen_texts.txt
for n in "BGLand24" "chiemgau24" "ORF Salzburg" "Traunsteiner Tagblatt" "Landratsamt BGL"; do
  $UI tap "$n" | tee -a $R; sleep 1
done
adb shell input swipe 540 1800 540 900 300; sleep 1
$UI tap "Nationalpark Berchtesgaden" | tee -a $R; sleep 1
shot 05_quellen_an
$UI texts > $OUT/quellen_texts_nach.txt
$UI tap "Fertig" | tee -a $R; sleep 1
$UI tap "Speichern" | tee -a $R; sleep 2
adb shell run-as $PKG cat shared_prefs/bga_widget.xml | grep -A12 'name="fd_' | head -14 | tee -a $R
adb shell am start -n $ACT >/dev/null; sleep 2

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

log "== App-Daten BGLand24: Region Berchtesgaden/Rupertiwinkel (Datum | Abo | Titelanfang) =="
adb shell run-as $PKG cat files/feeds/bgland24.xml > $OUT/app_bgland24.xml
python3 - <<'PY' | tee -a $R
import re
x=open("out/app_bgland24.xml",encoding="utf-8").read()
items=re.findall(r"<item>(.*?)</item>",x,re.S)
print("Items gesamt", len(items), "davon Abo", sum("🔒" in i for i in items))
for it in items:
    tp=re.search(r"<topic>(.*?)</topic>",it).group(1)
    if tp in ("Region/Berchtesgaden","Region/Rupertiwinkel"):
        t=re.search(r"<title>(.*?)</title>",it).group(1)
        print(tp[7:10], re.search(r"<pubDate>(.*?)</pubDate>",it).group(1)[5:22], "ABO" if "🔒" in t else "   ", t.replace("🔒 ","")[:40])
PY
adb shell run-as $PKG cat shared_prefs/bga_widget.xml | grep -E "plus|tp_" | tee -a $R
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
adb shell am start -n $ACT >/dev/null; sleep 2
adb shell monkey -p $PKG --throttle 120 --pct-syskeys 0 --pct-appswitch 5 -s 4242 -v 2500 > $OUT/monkey.txt 2>&1
grep -E "Events injected|CRASH|ANR|Monkey aborted" $OUT/monkey.txt | tee -a $R
sleep 3
log "Speicher nach Monkey: $(appmem)"
adb shell dumpsys meminfo $PKG > $OUT/meminfo_nach_monkey.txt
grep -E "Java Heap|Native Heap|Graphics|Views:|Activities:|ViewRootImpl|Bitmaps" $OUT/meminfo_nach_monkey.txt | tr -s ' ' | tee -a $R

log "== Reihenfolge der obersten Meldungen (Zeitangaben) =="
adb shell am start -n $ACT >/dev/null; sleep 2; $UI tap "Alle" >/dev/null; sleep 2
$UI texts | grep -E "vor |heute|gestern" | head -12 | tee -a $R
log "== Kaltstart mit vorhandenen Daten (3x) =="
for i in 1 2 3; do
  adb shell am force-stop $PKG; sleep 1
  adb shell am start -W -n $ACT | grep -E "TotalTime" | tee -a $R
  sleep 3
done

log "== Abstürze / ANR / Fehler im Log =="
adb logcat -d > $OUT/logcat.txt
log "Crash-Puffer Einträge der App: $(crashes)"
grep -nE "FATAL EXCEPTION|ANR in $PKG|Process: $PKG" $OUT/logcat.txt | head -20 | tee -a $R
grep -nE "AndroidRuntime" -A12 $OUT/logcat.txt | grep -m1 -A12 "FATAL" | tee -a $R
APID=$(adb shell pidof $PKG | tr -d '\r')
log "App-Prozess: $APID"
grep -E "Skipped [0-9]+ frames" $OUT/logcat.txt | awk -v p="$APID" '$3==p' | tee $OUT/skipped_app.txt | tail -8 | tee -a $R
log "Skipped-Frames-Meldungen der App (letzter Prozess): $(wc -l < $OUT/skipped_app.txt)"
grep -E " E [A-Za-z]*: | W System.err" $OUT/logcat.txt | awk -v p="$APID" '$3==p' | head -15 | tee -a $R
log "== Ende =="

#!/bin/bash
# Gesamttest vor Version 1.00: Funktion, Stabilitaet, Performance, Aktualitaet, grosse Schrift, Hell.
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UI="python3 tools/ui.py"
log() { echo "$*" | tee -a $R; }
shot() { adb exec-out screencap -p > $OUT/$1.png; log "Screenshot $1"; }
appmem() { adb shell dumpsys meminfo $PKG | grep -E "TOTAL PSS|TOTAL:" | head -1 | tr -s ' '; }
tapw() { $UI tap "$1" | tee -a $R; sleep ${2:-1.5}; }

log "== Installation =="
adb install -r -g app.apk | tail -1 | tee -a $R
adb logcat -c
log "== Kaltstart =="
adb shell am start -W -n $ACT | grep -E "TotalTime" | tee -a $R
sleep 40; shot 01_start
log "Speicher: $(appmem)"

log "== Einstellungen: Hauptmenü =="
tapw "Einstellungen" 2; shot 02_einstellungen; $UI texts > $OUT/02_einstellungen.txt
tapw "Nachrichten" 2; shot 03_nachrichten; $UI texts > $OUT/03_nachrichten.txt
for n in "BGLand24" "chiemgau24" "ORF Salzburg" "Traunsteiner Tagblatt" "Landratsamt BGL"; do tapw "$n" 0.8; done
adb shell input swipe 540 1700 540 900 300; sleep 1
for n in "Nationalpark Berchtesgaden" "BR24" "Merkur"; do tapw "$n" 0.8; done
shot 04_quellen_an; $UI texts > $OUT/04_quellen_an.txt
tapw "Themen ausblenden" 2; shot 05_themen_ausblenden; $UI texts > $OUT/05_themen.txt
tapw "Fertig" 1.5; tapw "Fertig" 1.5
tapw "Benachrichtigungen" 2; shot 06_benachrichtigungen; tapw "Fertig" 1.5
tapw "Wetter" 2; shot 07_wetter; tapw "Fertig" 1.5
tapw "Darstellung" 2; shot 08_darstellung; adb shell input swipe 540 1700 540 900 300; sleep 1; shot 08b_darstellung_unten; tapw "Fertig" 1.5
tapw "Widgets" 2; shot 09_widgets; $UI texts > $OUT/09_widgets.txt; tapw "Schließen" 2
$UI texts > $OUT/09b_nach_widgets.txt
adb shell input swipe 540 1600 540 900 300; sleep 1; shot 09c_einstellungen_unten
tapw "Über die App" 2; tapw "Neuigkeiten" 1.5; shot 10_ueber; tapw "Technisches Protokoll" 1.5; shot 10b_protokoll; tapw "Schließen" 2; tapw "Speichern" 2
adb shell run-as $PKG cat shared_prefs/bga_widget.xml | grep -E 'name="fd_' -A11 | head -12 | tee -a $R

log "== Abruf aller Quellen (Ziehen zum Aktualisieren) =="
adb shell am start -n $ACT >/dev/null; sleep 2
T0=$(date +%s)
adb shell input motionevent DOWN 540 1500
for y in 1560 1640 1720 1800 1880 1960 2040 2120 2160; do adb shell input motionevent MOVE 540 $y; done
adb shell input motionevent UP 540 2160
sleep 0.5; shot 11_laedt
for i in $(seq 1 60); do sleep 2; T=$($UI texts); echo "$T" | grep -qE "Aktualisieren …|Aktualisiere …" || break; done
log "Abruf sichtbar fertig nach $(( $(date +%s) - T0 )) s"
echo "$T" | head -8 >> $R
sleep 8; shot 12_nach_abruf
log "Speicher: $(appmem)"

log "== Protokoll Direktabruf =="
adb shell run-as $PKG cat shared_prefs/direct.xml | sed 's/&#10;/\n/g;s/&quot;/"/g' | grep -E "Artikel direkt|FEHLER" | tail -14 | tee -a $R

log "== Aktualität: neueste Meldung je Quelle (App-Daten) =="
mkdir -p $OUT/feeds
for f in $(adb shell run-as $PKG ls files/feeds | tr -d '\r'); do adb shell run-as $PKG cat files/feeds/$f > $OUT/feeds/$f; done
python3 - <<'PY' | tee -a $R
import os, re, email.utils, datetime
now = datetime.datetime.now(datetime.timezone.utc)
for f in sorted(os.listdir("out/feeds")):
    x = open("out/feeds/" + f, encoding="utf-8", errors="ignore").read()
    items = re.findall(r"<item>(.*?)</item>", x, re.S)
    ds = []
    for it in items:
        m = re.search(r"<pubDate>(.*?)</pubDate>", it)
        try: ds.append(email.utils.parsedate_to_datetime(m.group(1)))
        except Exception: pass
    titles = [re.sub(r"<!\[CDATA\[|\]\]>", "", t) for t in re.findall(r"<title>(.*?)</title>", x)[1:4]]
    if ds:
        age = (now - max(ds)).total_seconds() / 3600
        print(f"{f:22} {len(items):4} Meldungen, neueste vor {age:5.1f} Std | {titles[0][:60] if titles else ''}")
    else:
        print(f"{f:22} {len(items):4} Meldungen, ohne Datum")
PY

log "== Zweiter Abruf (kommt Neues, keine Dubletten) =="
sleep 60
adb shell am start -n $ACT >/dev/null; sleep 1
$UI tap "Aktualisieren" >/dev/null
for i in $(seq 1 40); do sleep 2; $UI texts | grep -q "Aktualisiere" || break; done
adb shell run-as $PKG cat shared_prefs/direct.xml | sed 's/&#10;/\n/g;s/&quot;/"/g' | grep -E "Artikel direkt" | tail -9 | tee -a $R

log "== Reiter =="
for chip in "Zeitungen ▾" "BGL ▾" "Deutschland & Welt ▾"; do
  tapw "$chip" 2; shot "13_$(echo $chip | tr -cd 'A-Za-z')"; tapw "Schließen" 1.5
done
tapw "Alle" 1.5

log "== Scrollen + Bildrate =="
adb shell dumpsys gfxinfo $PKG reset >/dev/null
for i in $(seq 1 8); do adb shell input swipe 540 1900 540 700 250; sleep 0.4; done
shot 14_gescrollt
for i in $(seq 1 10); do adb shell input swipe 540 700 540 1900 250; sleep 0.4; done
adb shell dumpsys gfxinfo $PKG | grep -E "Total frames|Janky|50th|90th" | head -4 | tee -a $R

log "== Offline =="
adb shell svc wifi disable; adb shell svc data disable; sleep 3
$UI tap "Aktualisieren" >/dev/null; sleep 30; shot 15_offline
$UI texts | grep -E "Verbindung|Offline" | head -3 | tee -a $R
adb shell svc wifi enable; adb shell svc data enable; sleep 8

log "== Große Schrift (130 %) =="
adb shell settings put system font_scale 1.3; sleep 2
adb shell am force-stop $PKG; adb shell am start -n $ACT >/dev/null; sleep 6; shot 16_grosse_schrift
tapw "Einstellungen" 2; shot 16b_einstellungen_gross; adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell settings put system font_scale 1.0; sleep 1

log "== Hell-Design =="
adb shell am force-stop $PKG; adb shell am start -n $ACT >/dev/null; sleep 4
tapw "Einstellungen" 2; tapw "Darstellung" 2; tapw "Hell" 1; tapw "Fertig" 1.5; tapw "Speichern" 2
adb shell am force-stop $PKG; adb shell am start -n $ACT >/dev/null; sleep 5; shot 17_hell
tapw "Einstellungen" 2; shot 17b_hell_einstellungen; tapw "Nachrichten" 2; shot 17c_hell_nachrichten
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
tapw "Einstellungen" 2; tapw "Darstellung" 2; tapw "Dunkel" 1; tapw "Fertig" 1.5; tapw "Speichern" 2

log "== Drehen =="
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1; sleep 3; shot 18_quer
adb shell settings put system user_rotation 0; sleep 3

log "== Belastung (Monkey 5000) =="
adb shell am start -n $ACT >/dev/null; sleep 2
adb shell monkey -p $PKG --throttle 100 --pct-syskeys 0 --pct-appswitch 3 -s 777 -v 5000 > $OUT/monkey.txt 2>&1
grep -E "Events injected|CRASH|ANR|Monkey aborted" $OUT/monkey.txt | tee -a $R
log "Speicher nach Monkey: $(appmem)"

log "== Neustart-Zeiten =="
for i in 1 2 3; do adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $ACT | grep TotalTime | tee -a $R; sleep 3; done

log "== Abstürze =="
adb logcat -d > $OUT/logcat.txt
grep -nE "FATAL EXCEPTION|ANR in $PKG" -A12 $OUT/logcat.txt | head -40 | tee -a $R
log "== Ende =="

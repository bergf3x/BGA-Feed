#!/bin/bash
# Kurztest Themenmenü: alle Reiter öffnen/schließen, Regionen auf-/zuklappen, Klappbereiche in "Alle".
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UI="python3 tools/ui.py"
log() { echo "$*" | tee -a $R; }
shot() { adb exec-out screencap -p > $OUT/$1.png; log "Screenshot $1"; }
count() { $UI texts | grep -c "$1"; }

adb install -r -g app.apk | tail -1 | tee -a $R
adb logcat -c
adb shell am start -W -n $ACT | grep TotalTime | tee -a $R
sleep 5
log "== alle Quellen einschalten =="
$UI tap "Einstellungen"; sleep 2; $UI tap "Quellen & Themen"; sleep 2
for n in "BGLand24" "chiemgau24" "ORF Salzburg" "Traunsteiner Tagblatt" "Landratsamt BGL"; do $UI tap "$n" | tee -a $R; sleep 1; done
adb shell input swipe 540 1800 540 900 300; sleep 1
for n in "Nationalpark Berchtesgaden" "BR24"; do $UI tap "$n" | tee -a $R; sleep 1; done
$UI texts > $OUT/quellen_texts.txt
$UI tap "Fertig"; sleep 1; $UI tap "Speichern"; sleep 2
adb shell am start -n $ACT >/dev/null; sleep 2
$UI tap "Aktualisieren" | tee -a $R
for i in $(seq 1 30); do sleep 3; $UI has "Lade Nachrichten" | grep -q gefunden || break; done
sleep 15; shot 01_alle
$UI texts > $OUT/01_alle.txt

for chip in "BGL ▾" "Außerhalb ▾" "Deutschland & Welt ▾"; do
  tag=$(echo "$chip" | tr -cd 'A-Za-z')
  log "== Reiter $chip =="
  $UI tap "$chip" | tee -a $R; sleep 2; shot "m_${tag}_0"
  $UI texts > "$OUT/m_${tag}_0.txt"
  # jede aufklappbare Region öffnen, prüfen, wieder schließen
  grep "aufklappen" "$OUT/m_${tag}_0.txt" | awk -F'|' '{print $3}' | sed 's/^ *//;s/ *$//' | sed 's/, .*//' > $OUT/regions.txt
  i=0
  IFS=$'\n'; for reg in $(cat $OUT/regions.txt); do unset IFS
    [ -z "$reg" ] && continue; i=$((i+1))
    $UI tap "$reg" >/dev/null; sleep 1
    if $UI texts | grep -q "$reg.*zuklappen"; then log "  $reg: öffnet OK"; else log "  $reg: ÖFFNET NICHT"; fi
    shot "m_${tag}_${i}"; $UI texts > "$OUT/m_${tag}_${i}.txt"
    $UI tap "$reg" >/dev/null; sleep 1
    if $UI texts | grep -q "$reg.*aufklappen"; then log "  $reg: schließt OK"; else log "  $reg: SCHLIESST NICHT"; fi
  done; unset IFS
  # Einzeleintrag wählen (erste Rubrik der ersten Region) und Schließen
  first=$(head -1 $OUT/regions.txt)
  if [ -n "$first" ]; then $UI tap "$first" >/dev/null; sleep 1; fi
  $UI tap "Schließen" | tee -a $R; sleep 2
  if $UI texts | grep -q "| Schließen |"; then log "  Blase bleibt offen!"; else log "  Blase geschlossen OK"; fi
  shot "m_${tag}_liste"
done

log "== Alle: Klappbereiche =="
$UI tap "Alle" | tee -a $R; sleep 2
for area in "Außerhalb" "Deutschland & Welt"; do
  for k in $(seq 1 80); do
    $UI texts | grep -q "$area, aufklappen" && break
    adb shell input swipe 540 2000 540 1700 400; sleep 0.8
  done
  $UI tap "$area, aufklappen" | tee -a $R; sleep 2
  if $UI texts | grep -q "$area, zuklappen"; then log "  $area: öffnet OK"; else log "  $area: ÖFFNET NICHT"; fi
  shot "f_$(echo $area | tr -cd 'A-Za-z')_offen"
  $UI tap "$area, zuklappen" | tee -a $R; sleep 2
  if $UI texts | grep -q "$area, aufklappen"; then log "  $area: schließt OK"; else log "  $area: SCHLIESST NICHT"; fi
done

log "== Abstürze =="
adb logcat -d > $OUT/logcat.txt
grep -nE "FATAL EXCEPTION|ANR in $PKG" -A12 $OUT/logcat.txt | head -30 | tee -a $R
log "== Ende =="

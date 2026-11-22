#!/bin/bash
# Welche Anfragen kostet ein Abruf, und welche Server antworten bedingt (304)?
set +e
PKG=de.bergf3x.bgawidget.test
ACT=$PKG/de.bergf3x.bgawidget.MainActivity
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
adb install -r -g app.apk | tail -1 | tee -a $R
adb shell setprop log.tag.BGAnet DEBUG
adb logcat -c
adb shell am start -W -n $ACT >/dev/null
sleep 60
adb logcat -d -s BGAnet:D > $OUT/net1.txt
adb logcat -c
adb shell am broadcast -a de.bergf3x.bgawidget.REFRESH -n $PKG/de.bergf3x.bgawidget.WidgetProvider >/dev/null
sleep 45
adb logcat -d -s BGAnet:D > $OUT/net2.txt
adb logcat -d -s BGAstat:I | tee -a $R
for f in net1 net2; do
  echo "== $f: Anfragen je Host (Anzahl, Bytes, mit ETag, mit Last-Modified, bedingt gesendet) ==" | tee -a $R
  sed -E 's/^.*BGAnet *: //' $OUT/$f.txt | awk '{split($5,u,"/"); h=u[3]; n[h]++; b[h]+=$2; if($3=="etag=true")e[h]++; if($4=="lm=true")l[h]++; if($5=="cond=true"||$4=="cond=true")c[h]++} END{for(h in n) print n[h], b[h], e[h]+0, l[h]+0, c[h]+0, h}' | sort -rn | tee -a $R
done
log() { echo "$*" | tee -a $R; }
log "== Ende =="

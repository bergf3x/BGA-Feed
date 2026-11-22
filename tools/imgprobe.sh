#!/bin/bash
set +e
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UA="BGAWidget/2.3 (privates Nachrichten-Widget, Android)"
get() { curl -sL -m 40 -A "$UA" --compressed -o /tmp/p.html "$1"; }
get "https://www.berchtesgadener-anzeiger.de/region-und-lokal/lokales-berchtesgadener-land.html"
echo "=== BGA Artikel-Links mit img (Auszug) ===" | tee -a $R
python3 - <<'PY' | tee -a out/report.txt
import re
h=open('/tmp/p.html',encoding='utf-8',errors='ignore').read()
ms=list(re.finditer(r'<a\b[^>]*?href="[^"]*_arid,\d+\.html"[^>]*>.*?</a>',h,re.S))
print("Artikel-Anker:",len(ms),"mit <img>:",sum('<img' in m.group(0) for m in ms))
for m in ms[:3]:
    print(re.sub(r'\s+',' ',m.group(0))[:700]); print('--')
# Bilder ausserhalb der Anker nahe Artikeln
print("cms_media/pic-Bilder:",len(re.findall(r'<img[^>]+(?:cms_media|/pic/)[^>]*>',h)))
PY
get "https://www.traunsteiner-tagblatt.de/region/landkreis-traunstein.html"
echo "=== TT ===" | tee -a $R
python3 - <<'PY' | tee -a out/report.txt
import re
h=open('/tmp/p.html',encoding='utf-8',errors='ignore').read()
ms=list(re.finditer(r'<a\b[^>]*?href="[^"]*_arid,\d+\.html"[^>]*>.*?</a>',h,re.S))
print("Artikel-Anker:",len(ms),"mit <img>:",sum('<img' in m.group(0) for m in ms))
for m in ms[:2]:
    print(re.sub(r'\s+',' ',m.group(0))[:600]); print('--')
PY
get "https://www.lra-bgl.de/t/presse/pressemitteilungen/"
echo "=== LRA ===" | tee -a $R
python3 - <<'PY' | tee -a out/report.txt
import re
h=open('/tmp/p.html',encoding='utf-8',errors='ignore').read()
i=h.find('pressemitteilungen/')
for m in list(re.finditer(r'<(?:li|article|div)[^>]*class="[^"]*(?:news|press|teaser|item|result)[^"]*"[^>]*>.{0,600}',h,re.S))[:3]:
    print(re.sub(r'\s+',' ',m.group(0))[:600]); print('--')
print("img mit /media|/files|/upload:",re.findall(r'<img[^>]+(?:media|files|upload|presse)[^>]*>',h)[:3])
PY
get "https://www.nationalpark-berchtesgaden.bayern.de/service/presse/pressemitteilungen/index.htm"
echo "=== NPV ===" | tee -a $R
python3 - <<'PY' | tee -a out/report.txt
import re
h=open('/tmp/p.html',encoding='utf-8',errors='ignore').read()
imgs=re.findall(r'<img[^>]+>',h)
print([re.sub(r'\s+',' ',i)[:160] for i in imgs if 'pressemitteilungen' in i or 'presse' in i][:4])
i=h.find('Pressemitteilung')
print(re.sub(r'\s+',' ',h[h.find('<h2'):h.find('<h2')+700]))
PY
echo "== Ende ==" | tee -a $R

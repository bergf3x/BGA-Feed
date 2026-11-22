#!/bin/bash
# Welche Quelle liefert Bild-Adressen? (nur Auszuege, kein Seiteninhalt)
set +e
OUT=out; mkdir -p $OUT; R=$OUT/report.txt
UA="BGAWidget/2.3 (privates Nachrichten-Widget, Android)"
probe() {
  name="$1"; url="$2"
  echo "=== $name $url" | tee -a $R
  curl -sL -m 40 -A "$UA" -H "Accept-Encoding: gzip" --compressed -w "HTTP %{http_code} %{size_download}B\n" -o /tmp/p.html "$url" | tee -a $R
  echo "-- img-Tags (erste 4):" | tee -a $R
  grep -o -i -E '<img[^>]{0,260}>' /tmp/p.html | head -4 | tee -a $R
  echo "-- enclosure/media/og (erste 4):" | tee -a $R
  grep -o -i -E '<(enclosure|media:content|media:thumbnail)[^>]{0,200}>|property="og:image"[^>]{0,200}>' /tmp/p.html | head -4 | tee -a $R
  echo "-- anzahl img: $(grep -o -i '<img' /tmp/p.html | wc -l)  enclosure/media: $(grep -o -i -E '<(enclosure|media:content|media:thumbnail)' /tmp/p.html | wc -l)" | tee -a $R
}
probe BGA "https://www.berchtesgadener-anzeiger.de/region-und-lokal/lokales-berchtesgadener-land.html"
probe TT "https://www.traunsteiner-tagblatt.de/region/landkreis-traunstein.html"
probe BGLand24-RSS "https://www.bgland24.de/bgland/region-berchtesgaden/rssfeed.rdf"
probe Chiemgau24-RSS "https://www.chiemgau24.de/chiemgau/rssfeed.rdf"
probe ORF "https://rss.orf.at/salzburg.xml"
probe LRA "https://www.lra-bgl.de/t/presse/pressemitteilungen/"
probe NPV "https://www.nationalpark-berchtesgaden.bayern.de/service/presse/pressemitteilungen/index.htm"
probe BR24 "https://feeds.br24.de/rss/nachrichten"
probe Merkur "https://www.merkur.de/bayern/rssfeed.rdf"
echo "== Ende ==" | tee -a $R

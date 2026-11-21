"""Prueft alle Quellen der App: Erreichbarkeit, Ladezeit, Groesse, Anzahl Meldungen, Alter der neuesten Meldung.
Vergleicht ausserdem die GitHub-Feeds (Ersatzweg) mit der Live-Quelle. Ausgabe nur Kennzahlen, keine Inhalte."""
import json
import re
import sys
import time
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path

import requests

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import scrape  # noqa: E402

UA = "BGAWidget/2.3 (privates Nachrichten-Widget, Android)"
NOW = datetime.now(timezone.utc)


def get(url):
    t0 = time.time()
    try:
        r = requests.get(url, headers={"User-Agent": UA}, timeout=25)
        return r.status_code, round((time.time() - t0) * 1000), len(r.content), r
    except Exception as e:
        return f"Fehler {type(e).__name__}", round((time.time() - t0) * 1000), 0, None


def age(iso):
    if not iso:
        return None
    try:
        d = datetime.fromisoformat(iso.replace("Z", "+00:00"))
        return round((NOW - d).total_seconds() / 60)
    except Exception:
        return None


def fmt_age(m):
    if m is None:
        return "–"
    return f"{m} min" if m < 120 else f"{m / 60:.1f} h"


PROBES = [
    # Seiten, die der Direktabruf der App laedt
    ("BA Startseite Region", "https://www.berchtesgadener-anzeiger.de/region-und-lokal/lokales-berchtesgadener-land.html"),
    ("BA Nachrichten", "https://www.berchtesgadener-anzeiger.de/nachrichten.html"),
    ("BA Sport", "https://www.berchtesgadener-anzeiger.de/sport.html"),
    ("TT Landkreis Traunstein", "https://www.traunsteiner-tagblatt.de/region/landkreis-traunstein.html"),
    ("TT Berchtesgadener Land", "https://www.traunsteiner-tagblatt.de/region/berchtesgadener-land.html"),
    ("BGLand24 RSS Berchtesgaden", "https://www.bgland24.de/bgland/region-berchtesgaden/rssfeed.rdf"),
    ("BGLand24 RSS Bad Reichenhall", "https://www.bgland24.de/bgland/region-bad-reichenhall/rssfeed.rdf"),
    ("BGLand24 RSS Rupertiwinkel", "https://www.bgland24.de/bgland/rupertiwinkel/rssfeed.rdf"),
    ("BGLand24 RSS Salzburg", "https://www.bgland24.de/bgland/region-salzburg/rssfeed.rdf"),
    ("chiemgau24 RSS Lokales", "https://www.chiemgau24.de/chiemgau/rssfeed.rdf"),
    ("ORF Salzburg RSS", "https://rss.orf.at/salzburg.xml"),
    ("BR24 RSS", "https://feeds.br24.de/rss/nachrichten"),
    ("Landratsamt Presse", "https://www.lra-bgl.de/t/presse/pressemitteilungen/"),
    ("Nationalpark Presse", "https://www.nationalpark-berchtesgaden.bayern.de/service/presse/pressemitteilungen/index.htm"),
    ("GitHub-Feed (raw)", "https://raw.githubusercontent.com/bergf3x/BGA-Feed/main/feeds.json"),
]


def newest_rss(r):
    best = None
    for it in scrape._rss_items(r.content, art=False):
        a = age(it.get("date"))
        if a is not None and (best is None or a < best):
            best = a
    return best


def newest_ba(r):
    ids = [int(x) for x in re.findall(r"_arid,(\d+)\.html", r.text)]
    return max(ids) if ids else None


def main():
    rows = []
    print("== Erreichbarkeit und Ladezeit (vom GitHub-Server aus) ==")
    for name, url in PROBES:
        st, ms, size, r = get(url)
        extra = ""
        if r is not None and r.ok:
            if url.endswith((".rdf", ".xml")) or "rss" in url:
                n = len(scrape._rss_items(r.content, art=False))
                extra = f"{n} Meldungen, neueste vor {fmt_age(newest_rss(r))}"
            elif "_arid" in r.text:
                extra = f"neueste Artikel-ID {newest_ba(r)}"
        rows.append(dict(name=name, status=st, ms=ms, kb=round(size / 1024), extra=extra))
        print(f"{name:32} | HTTP {st} | {ms:5} ms | {size / 1024:6.0f} KB | {extra}")

    print("\n== GitHub-Feeds (Ersatzweg): Alter der neuesten Meldung ==")
    root = Path(__file__).resolve().parent.parent
    cat = json.loads((root / "feeds.json").read_text(encoding="utf-8"))
    for f in cat["feeds"]:
        p = root / f["url"].rsplit("/", 1)[-1]
        if not p.exists():
            print(f"{f['name']:28} | Datei fehlt")
            continue
        x = p.read_text(encoding="utf-8")
        lb = re.search(r"<lastBuildDate>(.*?)</lastBuildDate>", x)
        built = parsedate_to_datetime(lb.group(1)) if lb else None
        dates = [parsedate_to_datetime(d) for d in re.findall(r"<pubDate>(.*?)</pubDate>", x)]
        n = len(re.findall(r"<item>", x))
        newest = max(dates) if dates else None
        print(f"{f['name']:28} | {n:3} Meldungen | Feed gebaut vor {fmt_age(round((NOW - built).total_seconds() / 60)) if built else '–'}"
              f" | neueste Meldung vor {fmt_age(round((NOW - newest).total_seconds() / 60)) if newest else '–'}"
              f" | Themen {len(f['topics'])}")

    print("\n== Vergleich Live-Quelle gegen GitHub-Feed ==")
    for site in scrape.SITES:
        t0 = time.time()
        try:
            live = scrape.fetch_site(site)
        except Exception as e:
            print(f"{site['name']:28} | Live-Abruf Fehler {type(e).__name__}")
            continue
        sec = round(time.time() - t0, 1)
        p = root / site["feed"]
        feed_urls = set(re.findall(r"<link>(.*?)</link>", p.read_text(encoding="utf-8"))) if p.exists() else set()
        live_urls = [it["url"] for it in live]
        missing = [u for u in live_urls if u.replace("&", "&amp;") not in feed_urls and u not in feed_urls]
        print(f"{site['name']:28} | live {len(live_urls):3} Meldungen in {sec:5} s | davon im GitHub-Feed fehlend: {len(missing)}")


if __name__ == "__main__":
    main()

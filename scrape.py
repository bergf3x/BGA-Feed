#!/usr/bin/env python3
"""Erzeugt aus den Lokalseiten des Berchtesgadener Anzeigers einen RSS-Feed.

Nutzung:
  python scrape.py                 # live abrufen, feed.xml schreiben
  python scrape.py --html datei    # statt Abruf eine lokale HTML-Datei parsen (Test)

Die Seite bietet keine Zeitangaben bei den Lokalartikeln. Als Veroeffentlichungszeit
wird deshalb der Zeitpunkt genommen, an dem ein Artikel zum ersten Mal gesehen wurde
(gespeichert in state.json). Sortiert wird nach der Artikel-ID (hoeher = neuer).
"""
import argparse
import json
import re
import sys
from datetime import datetime, timezone
from email.utils import format_datetime
from pathlib import Path
from xml.sax.saxutils import escape

BASE = "https://www.berchtesgadener-anzeiger.de"
SOURCES = [
    "/region-und-lokal/lokales-berchtesgadener-land.html",
]
UA = "bga-feed/1.0 (privater Feed-Generator, 1 Abruf alle 30 Min.)"
ARID = re.compile(r"_arid,(\d+)\.html")
MAX_ITEMS = 60
HERE = Path(__file__).parent


def parse(html):
    from bs4 import BeautifulSoup

    soup = BeautifulSoup(html, "html.parser")
    items = {}
    for a in soup.find_all("a", href=ARID):
        href = a["href"]
        m = ARID.search(href)
        if not m:
            continue
        arid = int(m.group(1))
        url = href if href.startswith("http") else BASE + href
        parts = list(a.stripped_strings)
        h = a.find(["h2", "h3"])
        title = h.get_text(" ", strip=True) if h else ""
        teaser = next(
            (p.get_text(" ", strip=True) for p in a.find_all("p") if not p.find("img")), ""
        )
        category = ""
        if title and title in parts:
            idx = parts.index(title)
            category = parts[idx - 1] if idx > 0 else ""
        if not title and parts:
            title = max(parts, key=len)
        if not title:
            continue
        plus = bool(a.find("img", alt=re.compile(r"Plus", re.I)))
        prev = items.get(arid)
        # Der gleiche Artikel taucht mehrfach auf; die Variante mit Teaser gewinnt.
        if prev and len(prev["teaser"]) >= len(teaser):
            continue
        items[arid] = dict(
            arid=arid, url=url, title=title, teaser=teaser, category=category, plus=plus
        )
    return list(items.values())


def fetch():
    import requests

    found = {}
    for path in SOURCES:
        r = requests.get(BASE + path, headers={"User-Agent": UA}, timeout=30)
        r.raise_for_status()
        for it in parse(r.text):
            found[it["arid"]] = it
    return list(found.values())


def build(items, state, now):
    state_items = state.setdefault("items", {})
    for it in items:
        key = str(it["arid"])
        if key not in state_items:
            state_items[key] = {**it, "first_seen": now.isoformat()}
        else:
            state_items[key].update({k: it[k] for k in ("title", "teaser", "category", "plus")})
    ordered = sorted(state_items.values(), key=lambda x: x["arid"], reverse=True)[:MAX_ITEMS]
    state["items"] = {str(x["arid"]): x for x in ordered}

    out = []
    for it in ordered:
        label = f"[{it['category']}] " if it["category"] else ""
        title = label + it["title"] + (" (Plus)" if it["plus"] else "")
        pub = format_datetime(datetime.fromisoformat(it["first_seen"]))
        out.append(
            "    <item>\n"
            f"      <title>{escape(title)}</title>\n"
            f"      <link>{escape(it['url'])}</link>\n"
            f"      <guid isPermaLink=\"false\">bga-{it['arid']}</guid>\n"
            f"      <pubDate>{pub}</pubDate>\n"
            f"      <category>{escape(it['category'])}</category>\n"
            f"      <description>{escape(it['teaser'])}</description>\n"
            "    </item>"
        )
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<rss version="2.0">\n  <channel>\n'
        "    <title>Berchtesgadener Anzeiger (inoffiziell, Lokales)</title>\n"
        f"    <link>{BASE}/region-und-lokal/lokales-berchtesgadener-land.html</link>\n"
        "    <description>Neueste lokale Meldungen, automatisch erzeugt. Nur Titel und Anrisstext.</description>\n"
        "    <language>de-de</language>\n"
        f"    <lastBuildDate>{format_datetime(now)}</lastBuildDate>\n"
        + "\n".join(out)
        + "\n  </channel>\n</rss>\n"
    )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--html", help="lokale HTML-Datei statt Live-Abruf")
    ap.add_argument("--out", default=str(HERE / "feed.xml"))
    ap.add_argument("--state", default=str(HERE / "state.json"))
    args = ap.parse_args()

    items = parse(Path(args.html).read_text(encoding="utf-8")) if args.html else fetch()
    if not items:
        sys.exit("Keine Artikel gefunden - Seitenstruktur geaendert? Feed bleibt unveraendert.")

    sp = Path(args.state)
    state = json.loads(sp.read_text(encoding="utf-8")) if sp.exists() else {}
    now = datetime.now(timezone.utc).replace(microsecond=0)
    xml = build(items, state, now)
    Path(args.out).write_text(xml, encoding="utf-8")
    sp.write_text(json.dumps(state, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(items)} Artikel gelesen, {len(state['items'])} im Feed -> {args.out}")


if __name__ == "__main__":
    main()

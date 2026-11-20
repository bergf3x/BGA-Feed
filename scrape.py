#!/usr/bin/env python3
"""Erzeugt aus Nachrichtenseiten ohne eigenen RSS-Feed private RSS-Feeds.

Nutzung:
  python scrape.py                          # alle Seiten live abrufen
  python scrape.py --site alpin --html f    # statt Abruf eine lokale HTML-Datei parsen (Test)

Ausgabe je Seite: ein Feed (bga -> feed.xml, alpin -> feeds/alpin.xml) plus feeds.json
(Katalog fuer das Widget: Name, Feed-URL, vorhandene Themen).

Die Seiten zeigen bei den Artikeln keine Zeitangaben. Als Veroeffentlichungszeit wird der
Zeitpunkt genommen, an dem ein Artikel zum ersten Mal gesehen wurde (state-Dateien).
Jeder Artikel bekommt ein Thema (<topic>), nach dem das Widget filtern kann.
"""
import argparse
import json
import re
import sys
from datetime import datetime, timezone
from email.utils import format_datetime
from pathlib import Path
from xml.sax.saxutils import escape

HERE = Path(__file__).parent
UA = "bga-feed/1.1 (privater Feed-Generator, 1 Abruf alle 30 Min.)"
RAW = "https://raw.githubusercontent.com/bergf3x/BGA-Feed/main/"
MAX_ITEMS = 200

BGA_ARID = re.compile(r"_arid,(\d+)\.html")
ALP_ID = re.compile(r"/(\d{4,9})/(?:artikel|galerie|produkttest|tour)_")
SKIP_PART = re.compile(r"[\d:,.\s]*(Std\.?|Hm|km|Min\.?)?")
HAS_WORD = re.compile(r"[A-Za-zÄÖÜäöüß]{3,}")


def _clean_parts(a):
    return [p for p in a.stripped_strings if HAS_WORD.search(p) and not SKIP_PART.fullmatch(p)]


# ---------------------------------------------------------------- Berchtesgadener Anzeiger
PLUS = re.compile(r"plus", re.I)


def is_plus(a):
    """Plus+-Kennzeichnung: Bild (alt/src/class) oder Element mit passender CSS-Klasse."""
    for img in a.find_all("img"):
        if PLUS.search(" ".join([img.get("alt", ""), img.get("src", ""), " ".join(img.get("class", []))])):
            return True
    for el in a.find_all(True):
        if any(PLUS.search(c) for c in el.get("class", [])):
            return True
    return False


def parse_bga(html, base):
    from bs4 import BeautifulSoup

    soup = BeautifulSoup(html, "html.parser")
    items = {}
    for a in soup.find_all("a", href=BGA_ARID):
        href = a["href"]
        m = BGA_ARID.search(href)
        if not m:
            continue
        sid = int(m.group(1))
        url = href if href.startswith("http") else base + href
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
        plus = is_plus(a)
        prev = items.get(sid)
        if prev:
            # Der gleiche Artikel taucht mehrfach auf (mit/ohne Bild); Plus gilt, sobald
            # eine Variante es zeigt, und die Variante mit Teaser gewinnt.
            plus = plus or prev["plus"]
            if len(prev["teaser"]) >= len(teaser):
                prev["plus"] = plus
                continue
        items[sid] = dict(
            sid=sid, url=url, title=title, teaser=teaser, category=category, plus=plus
        )
    return list(items.values())


def bga_topic(item, source_topic):
    # Teaser mit Kicker "Regionalsport BGD/TS" gehoeren immer zu "Sport aus der Region"
    if source_topic.startswith("Sport/"):
        return source_topic
    if item["category"].lower().startswith("regionalsport"):
        return "Sport/Sport aus der Region"
    return source_topic


# ---------------------------------------------------------------- ALPIN
ALP_TOPICS = [
    ("/home/news/", "News"),
    ("/touren/bergbericht/", "Bergbericht"),
    ("/touren/", "Touren"),
    ("/tests-produkte/", "Tests & Produkte"),
    ("/sicher-am-berg/", "Sicherheit & Technik"),
    ("/home/interviews/", "Interviews"),
    ("/home/historisches/", "Historie"),
    ("/home/olaf-klaert-das-schon/", "Ratgeber"),
    ("/foto-video/", "Foto & Video"),
    ("/alpin-plus/", "ALPIN+"),
]


def alp_topic(path):
    for prefix, name in ALP_TOPICS:
        if path.startswith(prefix):
            return name
    return "Sonstiges"


def parse_alpin(html, base):
    from bs4 import BeautifulSoup

    soup = BeautifulSoup(html, "html.parser")
    items = {}
    for art in soup.find_all("article"):
        a = art.find("a", href=ALP_ID)
        if not a:
            continue  # Anzeigen, Taboola, Platzhalter
        href = a["href"]
        sid = int(ALP_ID.search(href).group(1))
        if sid in items:
            continue
        path = href.replace(base, "")
        url = href if href.startswith("http") else base + href
        h1 = a.find("h1")
        h2 = a.find("h2")
        if h1:  # grosser Aufmacher: h1 = Titel, h2 = Unterzeile
            title = h1.get_text(" ", strip=True)
            category = h2.get_text(" ", strip=True) if h2 else ""
            teaser = ""
        elif h2:
            title = h2.get_text(" ", strip=True)
            parts = _clean_parts(a)
            category = teaser = ""
            if title in parts:
                idx = parts.index(title)
                category = parts[idx - 1] if idx > 0 else ""
                teaser = parts[idx + 1] if idx + 1 < len(parts) else ""
        else:
            continue
        title = re.sub(r"\s+", " ", title).strip()
        if not title:
            continue
        items[sid] = dict(
            sid=sid, url=url, title=title, teaser=teaser, category=category, plus=False,
            topic=alp_topic(path),
        )
    return list(items.values())


# ---------------------------------------------------------------- Seiten-Konfiguration
SITES = [
    dict(
        id="bga",
        name="Berchtesgadener Anzeiger",
        base="https://www.berchtesgadener-anzeiger.de",
        link="/region-und-lokal/lokales-berchtesgadener-land.html",
        feed="feed.xml",
        state="state.json",
        # Reihenfolge: spaeter genannte Seiten ueberschreiben das Thema frueherer
        # Reihenfolge: allgemein -> speziell; spaeter genannte Seiten ueberschreiben das Thema
        sources=[
            ("/region-und-lokal/lokales-berchtesgadener-land.html", "Region/Berchtesgadener Land"),
            ("/region-und-lokal/lokalnachrichten-landkreis-traunstein.html", "Region/Landkreis Traunstein"),
            ("/region-und-lokal/bergsteiger-und-naturfreunde.html", "Region/Bergsteiger & Naturfreunde"),
            ("/nachrichten.html", "Nachrichten/Deutschland & Welt"),
            ("/nachrichten/wirtschaft.html", "Nachrichten/Wirtschaft"),
            ("/nachrichten/wissenschaft.html", "Nachrichten/Wissenschaft"),
            ("/nachrichten/computer.html", "Nachrichten/Digital"),
            ("/nachrichten/boulevard.html", "Nachrichten/Boulevard"),
            ("/kultur.html", "Kultur"),
            ("/sport.html", "Sport"),
            ("/sport/fussball.html", "Sport/Fußball"),
            ("/sport/wintersport.html", "Sport/Wintersport"),
            ("/sport/sport-aus-der-region.html", "Sport/Sport aus der Region"),
        ],
        topics=[
            "Region", "Region/Berchtesgadener Land", "Region/Landkreis Traunstein",
            "Region/Bergsteiger & Naturfreunde",
            "Nachrichten", "Nachrichten/Deutschland & Welt", "Nachrichten/Wirtschaft",
            "Nachrichten/Wissenschaft", "Nachrichten/Digital", "Nachrichten/Boulevard",
            "Sport", "Sport/Sport aus der Region", "Sport/Fußball", "Sport/Wintersport",
            "Kultur",
        ],
        parse=parse_bga,
        retopic=bga_topic,
        order="id",
    ),
    dict(
        id="alpin",
        name="ALPIN",
        base="https://www.alpin.de",
        link="/",
        feed="feeds/alpin.xml",
        state="state-alpin.json",
        sources=[("/", "")],
        topics=[t for _, t in ALP_TOPICS] + ["Sonstiges"],
        parse=parse_alpin,
        retopic=None,
        order="seen",
        # alpin.de sperrt GitHub-Server (403). Das Widget liest die Seite deshalb direkt
        # auf dem Handy; hier wird nur der Katalogeintrag erzeugt.
        direct=True,
    ),
]


def fetch_site(site):
    import requests

    found = {}
    for path, topic in site["sources"]:
        try:
            r = requests.get(site["base"] + path, headers={"User-Agent": UA}, timeout=30)
            r.raise_for_status()
        except Exception as e:  # eine Teilseite darf ausfallen
            print(f"[{site['id']}] {path}: {e}", file=sys.stderr)
            continue
        for it in site["parse"](r.text, site["base"]):
            it.setdefault("topic", topic)
            if site["retopic"]:
                it["topic"] = site["retopic"](it, topic)
            old = found.get(it["sid"])
            if old and old.get("plus"):
                it["plus"] = True  # Plus-Kennzeichen geht nicht verloren, wenn eine andere Seite es nicht zeigt
            found[it["sid"]] = it
    return list(found.values())


def sort_key(site):
    if site["order"] == "id":
        return lambda x: (x["sid"],)
    # ALPIN: nach Erstsichtung, bei Gleichstand nach ID (Tour-IDs sind riesig -> hinten)
    return lambda x: (x["first_seen"], x["sid"] if x["sid"] < 10_000_000 else 0)


def build(site, items, state, now):
    state_items = state.setdefault("items", {})
    for it in items:
        key = str(it["sid"])
        if key not in state_items:
            state_items[key] = {**it, "first_seen": now.isoformat()}
        else:
            state_items[key].update({k: it[k] for k in ("title", "teaser", "category", "topic")})
            state_items[key]["plus"] = bool(state_items[key].get("plus") or it.get("plus"))
    ordered = sorted(state_items.values(), key=sort_key(site), reverse=True)[:MAX_ITEMS]
    state["items"] = {str(x["sid"]): x for x in ordered}

    out = []
    for it in ordered:
        title = ("🔒 " if it.get("plus") else "") + it["title"]
        pub = format_datetime(datetime.fromisoformat(it["first_seen"]))
        out.append(
            "    <item>\n"
            f"      <title>{escape(title)}</title>\n"
            f"      <link>{escape(it['url'])}</link>\n"
            f"      <guid isPermaLink=\"false\">{site['id']}-{it['sid']}</guid>\n"
            f"      <pubDate>{pub}</pubDate>\n"
            f"      <category>{escape(it.get('category', ''))}</category>\n"
            f"      <topic>{escape(it.get('topic') or 'Region')}</topic>\n"
            f"      <description>{escape(it.get('teaser', ''))}</description>\n"
            "    </item>"
        )
    xml = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<rss version="2.0">\n  <channel>\n'
        f"    <title>{escape(site['name'])} (inoffiziell)</title>\n"
        f"    <link>{escape(site['base'] + site['link'])}</link>\n"
        "    <description>Neueste Meldungen, automatisch erzeugt. Nur Titel und Anrisstext.</description>\n"
        "    <language>de-de</language>\n"
        f"    <lastBuildDate>{format_datetime(now)}</lastBuildDate>\n"
        + "\n".join(out)
        + "\n  </channel>\n</rss>\n"
    )
    present = {it.get("topic") or "Region" for it in ordered}
    topics = [
        t for t in site["topics"]
        if t in present or any(p.startswith(t + "/") for p in present)
    ]
    return xml, topics


def run_site(site, now, html_file=None, out=None, state_file=None):
    if html_file:
        items = site["parse"](Path(html_file).read_text(encoding="utf-8"), site["base"])
        for it in items:
            it.setdefault("topic", site["sources"][0][1])
            if site["retopic"]:
                it["topic"] = site["retopic"](it, it["topic"])
    else:
        items = fetch_site(site)
    if not items:
        print(f"[{site['id']}] Keine Artikel gefunden - Feed bleibt unveraendert.", file=sys.stderr)
        return None
    sp = Path(state_file or HERE / site["state"])
    state = json.loads(sp.read_text(encoding="utf-8")) if sp.exists() else {}
    # alte Statedateien (vor Version 1.1) hatten "arid" statt "sid"
    for v in state.get("items", {}).values():
        if v.get("topic") == "Regionalsport":
            v["topic"] = "Sport/Sport aus der Region"
        if "sid" not in v and "arid" in v:
            v["sid"] = v.pop("arid")
    xml, topics = build(site, items, state, now)
    op = Path(out or HERE / site["feed"])
    op.parent.mkdir(parents=True, exist_ok=True)
    op.write_text(xml, encoding="utf-8")
    sp.write_text(json.dumps(state, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"[{site['id']}] {len(items)} Artikel gelesen, {len(state['items'])} im Feed -> {op}")
    return topics


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--site", help="nur diese Seite (id)")
    ap.add_argument("--html", help="lokale HTML-Datei statt Live-Abruf (mit --site)")
    ap.add_argument("--out")
    ap.add_argument("--state")
    args = ap.parse_args()

    now = datetime.now(timezone.utc).replace(microsecond=0)
    catalog_path = HERE / "feeds.json"
    old = json.loads(catalog_path.read_text(encoding="utf-8")) if catalog_path.exists() else {}
    old_topics = {f["id"]: f["topics"] for f in old.get("feeds", [])}

    ok = 0
    catalog = []
    for site in SITES:
        if args.site and site["id"] != args.site:
            continue
        if site.get("direct"):
            catalog.append(dict(id=site["id"], name=site["name"], url="",
                                direct=site["base"] + site["link"], topics=site["topics"]))
            continue
        topics = run_site(site, now, args.html, args.out, args.state)
        if topics is not None:
            ok += 1
        else:
            topics = old_topics.get(site["id"], [])
        catalog.append(
            dict(id=site["id"], name=site["name"], url=RAW + site["feed"], topics=topics)
        )
    if not ok:
        sys.exit("Keine Seite lieferte Artikel - Seitenstruktur geaendert?")
    if not args.site:
        catalog_path.write_text(
            json.dumps({"feeds": catalog}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
        )


if __name__ == "__main__":
    main()

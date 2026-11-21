#!/usr/bin/env python3
"""Erzeugt aus Nachrichtenseiten ohne eigenen RSS-Feed private RSS-Feeds (derzeit: Berchtesgadener Anzeiger).

Nutzung:
  python scrape.py                          # alle Seiten live abrufen
  python scrape.py --site bga --html f    # statt Abruf eine lokale HTML-Datei parsen (Test)

Ausgabe je Seite: ein Feed (bga -> feed.xml) plus feeds.json
(Katalog fuer das Widget: Name, Feed-URL, vorhandene Themen).

Die Seiten zeigen bei den Artikeln keine Zeitangaben. Als Veroeffentlichungszeit wird der
Zeitpunkt genommen, an dem ein Artikel zum ersten Mal gesehen wurde (state-Dateien).
Jeder Artikel bekommt ein Thema (<topic>), nach dem das Widget filtern kann.
"""
import argparse
import json
import re
import sys
import zlib
from datetime import datetime, timezone
from email.utils import format_datetime
from pathlib import Path
from xml.sax.saxutils import escape

HERE = Path(__file__).parent
UA = "bga-feed/1.1 (privater Feed-Generator, 1 Abruf alle 30 Min.)"
RAW = "https://raw.githubusercontent.com/bergf3x/BGA-Feed/main/"
MAX_ITEMS = 400
MAX_PER_TOPIC = 40  # damit auch kleine Themen nicht von grossen verdraengt werden

BGA_ARID = re.compile(r"_arid,(\d+)\.html")


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


# ---------------------------------------------------------------- bgland24.de
def _clean(t):
    import html as _h
    return re.sub(r"\s+", " ", _h.unescape(re.sub(r"<[^>]+>", " ", t or ""))).strip()


def _bg_wp(base):
    """WordPress-Schnittstelle (falls vorhanden)."""
    import requests

    h = {"User-Agent": UA}
    r = requests.get(
        base + "/wp-json/wp/v2/posts",
        params={"per_page": 50, "_fields": "id,link,title,excerpt,date_gmt,categories"},
        headers=h, timeout=30,
    )
    r.raise_for_status()
    posts = r.json()
    cats = {}
    try:
        c = requests.get(base + "/wp-json/wp/v2/categories", params={"per_page": 100, "_fields": "id,name"}, headers=h, timeout=30)
        cats = {x["id"]: _clean(x["name"]) for x in c.json()}
    except Exception:
        pass
    out = []
    for p in posts:
        names = [cats[i] for i in p.get("categories", []) if i in cats and cats[i].lower() != "allgemein"]
        out.append(dict(
            sid=int(p["id"]), url=p["link"], title=_clean(p["title"]["rendered"]),
            teaser=_clean(p["excerpt"]["rendered"])[:300], category=names[0] if names else "",
            topic=names[0] if names else "Region", plus=False,
            date=(p.get("date_gmt") or "") + "+00:00" if p.get("date_gmt") else "",
        ))
    return out


def _bg_rss(base, html):
    """Feed-Autodiscovery auf der Startseite."""
    import requests
    from xml.etree import ElementTree as ET

    m = re.search(r'<link[^>]+type="application/(?:rss|atom)\+xml"[^>]*href="([^"]+)"', html, re.I)
    if not m:
        return []
    url = m.group(1)
    if url.startswith("/"):
        url = base + url
    r = requests.get(url, headers={"User-Agent": UA}, timeout=30)
    r.raise_for_status()
    root = ET.fromstring(r.content)
    out = []
    for it in root.iter("item"):
        link = (it.findtext("link") or "").strip()
        if not link:
            continue
        out.append(dict(
            sid=zlib.crc32(link.encode()), url=link, title=_clean(it.findtext("title")),
            teaser=_clean(it.findtext("description"))[:300], category=_clean(it.findtext("category")),
            topic="Region", plus=False, date="",
        ))
    return out


BG_ART = re.compile(r"-\d{6,}\.html$")
BG_SECTIONS = [
    "/bgland/region-berchtesgaden/", "/bgland/region-bad-reichenhall/", "/bgland/rupertiwinkel/",
    "/bgland/region-traunstein/", "/bgland/region-salzburg/", "/bgland/", "/",
]


def _rss_items(xml_bytes, art=True):
    """RSS 2.0 / RDF / Atom tolerant lesen: Titel, Link, Datum, Anriss."""
    from email.utils import parsedate_to_datetime

    txt = xml_bytes.decode("utf-8", "replace")
    out = []
    for blk in re.findall(r"<(?:item|entry)\b[^>]*>(.*?)</(?:item|entry)>", txt, re.S):
        def tag(name):
            m = re.search(rf"<{name}\b[^>]*>(.*?)</{name}>", blk, re.S)
            v = m.group(1) if m else ""
            v = re.sub(r"^\s*<!\[CDATA\[(.*?)\]\]>\s*$", r"\1", v, flags=re.S)
            return v.strip()
        link = tag("link") or (re.search(r'<link[^>]+href="([^"]+)"', blk) or [None, ""])[1]
        link = link.split("#")[0].strip()
        if not link or (art and not BG_ART.search(link.split("?")[0])):
            continue
        date = ""
        raw = tag("pubDate") or tag("dc:date") or tag("published") or tag("updated")
        if raw:
            try:
                d = parsedate_to_datetime(raw) if "," in raw or raw[:3].isalpha() else datetime.fromisoformat(raw.replace("Z", "+00:00"))
                if d.tzinfo is None:
                    d = d.replace(tzinfo=timezone.utc)
                date = d.astimezone(timezone.utc).isoformat()
            except Exception:
                pass
        out.append(dict(
            sid=zlib.crc32(link.encode()), url=link, title=_clean(tag("title")),
            teaser=_clean(tag("description") or tag("summary"))[:300], category=_clean(tag("category")),
            topic="Region", plus=False, date=date,
        ))
    return [x for x in out if len(x["title"]) >= 10]


def _bg_sections(base, sections=None, tag="bgland24"):
    """Rubrik-Feeds und -Seiten der Region; je Rubrik nur Kennzahlen ins Protokoll."""
    import requests

    found = {}
    for sec in sections or BG_SECTIONS:
        n_rss = n_html = 0
        try:
            r = requests.get(base + sec + "rssfeed.rdf", headers={"User-Agent": UA}, timeout=20)
            st = r.status_code
            if r.ok:
                for it in _rss_items(r.content):
                    it.setdefault("sec", sec)
                    found.setdefault(it["url"], it)
                    n_rss += 1
        except Exception as e:
            st = f"Fehler {type(e).__name__}"
        if n_rss == 0 and sec != "/":
            try:
                h = requests.get(base + sec, headers={"User-Agent": UA}, timeout=20)
                if h.ok:
                    for it in _bg_html(base, h.text):
                        found.setdefault(it["url"], it)
                        n_html += 1
            except Exception:
                pass
        print(f"[{tag}] Rubrik {sec}: RSS {st} -> {n_rss}, Seite -> {n_html}")
    return list(found.values())


def _bg_html(base, html):
    """Notnagel: Artikellinks der Startseite."""
    from bs4 import BeautifulSoup
    from urllib.parse import urljoin, urlparse

    host = urlparse(base).netloc.replace("www.", "")
    skip = ("/category/", "/tag/", "/author/", "/page/", "/impressum", "/datenschutz", "/kontakt", "/wp-", "/feed", "/tags/", "/kategorie")
    soup = BeautifulSoup(html, "html.parser")
    seen, out = set(), []
    for a in soup.find_all("a", href=True):
        url = urljoin(base + "/", a["href"]).split("#")[0]
        pu = urlparse(url)
        if pu.netloc.replace("www.", "") != host or any(x in pu.path for x in skip):
            continue
        slug = pu.path.rstrip("/").rsplit("/", 1)[-1]
        title = _clean(a.get_text(" "))
        h = a.find(["h1", "h2", "h3", "h4"])
        if h:
            title = _clean(h.get_text(" "))
        if url in seen or len(slug) < 15 or "-" not in slug or len(title) < 20 or not BG_ART.search(pu.path):
            continue
        seen.add(url)
        out.append(dict(sid=zlib.crc32(url.encode()), url=url, title=title, teaser="", category="", topic="Region", plus=False, date=""))
    return out


def bg_topic(url):
    """Thema aus dem Adresspfad ableiten, z. B. /bgland/region-berchtesgaden/... -> Region/Berchtesgaden."""
    from urllib.parse import urlparse

    seg = [x for x in urlparse(url).path.split("/") if x]

    def nice(x):
        x = re.sub(r"^(region|landkreis)-", "", x)
        return " ".join(w.capitalize() for w in x.split("-"))

    if not seg:
        return "Region"
    top = seg[0]
    if top in ("bgland", "lokal", "lokales", "chiemgau"):
        return "Region/" + nice(seg[1]) if len(seg) > 2 else "Region"
    if top in ("welt", "deutschland", "politik", "wirtschaft"):
        return "Nachrichten/" + nice(top)
    if top in ("sport", "wintersport"):
        return "Sport/Wintersport" if top == "wintersport" else "Sport"
    if top == "bayern" and len(seg) > 2:
        return "Bayern/" + nice(seg[1]) if seg[1].startswith("landkreis") else "Bayern"
    if top in ("leben", "stars"):
        return "Nachrichten/" + nice(top)
    return nice(top)


BG_SAMPLE = []
BG_PROBE = []  # Diagnose-URLs (leer = aus). Bezahl-Erkennung: JSON "paid":1 / "paywall":"hard" in der Artikelseite


def bg_signals(html):
    """Paywall-Kennzeichen einer Artikelseite."""
    return dict(
        free_false=bool(re.search(r'"isAccessibleForFree"\s*:\s*"?false"?', html, re.I)),
        paywall_cls=bool(re.search(r'class="[^"]*(paywall|premium)[^"]*"', html, re.I)),
        plus_text=bool(re.search(r"BGLand24\s*Plus|Plus-Artikel|mit Plus|Plus\+", html)),
        paid=bool(re.search(r'"paid"\s*:\s*1\b', html) or re.search(r'"paywall"\s*:\s*"hard"', html)),
    )


def bg_enrich(it):
    """Echte Veroeffentlichungszeit und Paywall-Kennzeichen aus der Artikelseite."""
    import requests
    from datetime import timezone as _tz
    from zoneinfo import ZoneInfo

    r = requests.get(it["url"], headers={"User-Agent": UA}, timeout=15)
    r.raise_for_status()
    sig = bg_signals(r.text)
    if sig["paid"] or sig["free_false"]:
        it["plus"] = True
    it["pc"] = 2
    m = (re.search(r'article:published_time"\s+content="([^"]+)"', r.text)
         or re.search(r'"datePublished"\s*:\s*"([^"]+)"', r.text)
         or re.search(r'<time[^>]+datetime="([^"]+)"', r.text))
    if not m:
        return
    d = datetime.fromisoformat(m.group(1).replace("Z", "+00:00"))
    if d.tzinfo is None:
        d = d.replace(tzinfo=ZoneInfo("Europe/Berlin"))
    it["date"] = d.astimezone(_tz.utc).isoformat()


def bg_probe():
    """Diagnose: welche Paywall-Signale zeigen bekannte Bezahl-Artikel? (nur Kennzahlen im Log)"""
    import requests

    for u in BG_PROBE + (BG_SAMPLE or []):
        try:
            r = requests.get(u, headers={"User-Agent": UA}, timeout=15)
            classes = sorted(set(re.findall(r'class="([^"]*(?:plus|paywall|premium|abo|locked)[^"]*)"', r.text, re.I)))[:8]
            t = r.text
            extra = dict(
                accessible_any=len(re.findall(r"isAccessibleForFree", t)),
                word_Plus=len(re.findall(r"\bPlus\b", t)),
                data_attrs=sorted(set(re.findall(r'(data-[a-z-]*(?:paid|premium|plus|paywall|access|lock)[a-z-]*)=', t, re.I)))[:8],
                meta=sorted(set(re.findall(r'<meta[^>]+(?:name|property)="([^"]*(?:paid|premium|plus|paywall|access|content_tier|tier)[^"]*)"', t, re.I)))[:8],
                json_keys=sorted(set(re.findall(r'"(\w*(?:paid|premium|paywall|access|plus|tier)\w*)"\s*:', t, re.I)))[:12],
            )
            extra["paid_vals"] = re.findall(r'"paid"\s*:\s*([^,}\s]{1,12})', t)[:4]
            extra["paywall_vals"] = re.findall(r'"paywall"\s*:\s*("[^"]{0,30}"|[^,}\s]{1,12})', t)[:4]
            print(f"[bgland24] probe2 {extra}")
            print(f"[bgland24] probe HTTP {r.status_code}, {len(r.text)} Zeichen, Signale {bg_signals(r.text)}, Klassen {classes}")
        except Exception as e:
            print(f"[bgland24] probe Fehler: {e}", file=sys.stderr)

def fetch_bgland(site):
    items = _fetch_bgland(site)
    for it in items:
        it["topic"] = bg_topic(it["url"])
    return items


def _fetch_bgland(site):
    import requests

    base = site["base"]
    for name, fn in (("wp-json", lambda: _bg_wp(base)),):
        try:
            items = fn()
            if items:
                print(f"[{site['id']}] Quelle: {name}")
                return items
        except Exception as e:
            print(f"[{site['id']}] {name}: {e}", file=sys.stderr)
    class _Empty:
        text = ""
    try:
        r = requests.get(base + "/", headers={"User-Agent": UA}, timeout=30)
        r.raise_for_status()
    except Exception as e:
        print(f"[{site['id']}] Startseite: {e}", file=sys.stderr)
        r = _Empty()
    items = {}
    try:
        for it in _bg_sections(base):
            items[it["url"]] = it
    except Exception as e:
        print(f"[{site['id']}] Rubriken: {e}", file=sys.stderr)
    try:
        for it in _bg_html(base, r.text):
            items.setdefault(it["url"], it)
    except Exception as e:
        print(f"[{site['id']}] startseite: {e}", file=sys.stderr)
    print(f"[{site['id']}] Quelle: Rubriken + Startseite, {len(items)} Artikel")
    return list(items.values())


# ---------------------------------------------------------------- Seiten-Konfiguration
LOCAL_RE = re.compile(r'href="(?:https?://(?:www\.)?chiemgau24\.de)?(/(?:lokal|lokales)/[a-z0-9-]+/)"')


def fetch_chiemgau(site):
    """chiemgau24.de (Ippen wie BGLand24): Lokalteil /chiemgau/, Unterrubriken aus den Artikel-Adressen ableiten,
    je Unterrubrik RSS. Thema kommt aus der Adresse (unbekannte Rubriken liefern den allgemeinen Feed). Nur Regionales."""
    import requests

    base = site["base"]
    subs = []
    try:
        r = requests.get(base + "/chiemgau/rssfeed.rdf", headers={"User-Agent": UA}, timeout=20)
        for it in _rss_items(r.content):
            seg = [x for x in urlparse_path(it["url"]).split("/") if x]
            if len(seg) > 2 and seg[0] == "chiemgau" and seg[1] not in subs:
                subs.append(seg[1])
    except Exception as e:
        print(f"[{site['id']}] /chiemgau/: {e}", file=sys.stderr)
    secs = ["/chiemgau/"] + ["/chiemgau/" + x + "/" for x in subs[:10]]
    items = _bg_sections(base, secs, site["id"])
    for it in items:
        it["topic"] = bg_topic(it["url"])
    return [it for it in items if it["topic"].startswith("Region")]


def urlparse_path(u):
    from urllib.parse import urlparse
    return urlparse(u).path


def fetch_rss_site(site):
    """Reiner RSS-Feed (z. B. ORF Salzburg): alle Meldungen in ein festes Thema."""
    import requests

    r = requests.get(site["rss"], headers={"User-Agent": UA}, timeout=30)
    r.raise_for_status()
    items = _rss_items(r.content, art=False)
    for it in items:
        it["topic"] = site["topic"]
    print(f"[{site['id']}] RSS {r.status_code} -> {len(items)}")
    return items


# ---------------------------------------------------------------- Traunsteiner Tagblatt, Landratsamt, Nationalpark, BR24
def tt_topic(item, source_topic):
    """Traunsteiner Tagblatt: Thema aus der Adresse (Landkreis Traunstein / Berchtesgadener Land)."""
    u = item["url"]
    if "/region/berchtesgadener-land" in u:
        return "Region/Berchtesgadener Land"
    if "/region/landkreis-traunstein" in u:
        return "Region/Landkreis Traunstein"
    return bga_topic(item, source_topic)


DE_MON = {"jan": 1, "feb": 2, "mär": 3, "mae": 3, "mar": 3, "apr": 4, "mai": 5, "jun": 6, "jul": 7,
          "aug": 8, "sep": 9, "okt": 10, "nov": 11, "dez": 12}


def _de_date(d, m, y, hh=12, mm=0):
    from zoneinfo import ZoneInfo
    return datetime(int(y), int(m), int(d), int(hh), int(mm), tzinfo=ZoneInfo("Europe/Berlin")).astimezone(timezone.utc).isoformat()


def fetch_lra(site):
    """Pressemitteilungen des Landratsamts BGL (Liste mit Datum, Titel, Anriss)."""
    import requests

    r = requests.get(site["base"] + "/t/presse/pressemitteilungen/", headers={"User-Agent": UA}, timeout=30)
    r.raise_for_status()
    html = r.text
    out, seen = [], set()
    for m in re.finditer(r'<a[^>]+href="(/t/presse/pressemitteilungen/detail/news/[^"#?]+)"[^>]*>(.*?)</a>', html, re.S):
        href, title = m.group(1), _clean(m.group(2))
        if href in seen or len(title) < 8:
            continue
        seen.add(href)
        before = html[max(0, m.start() - 1500):m.start()]
        dm = list(re.finditer(r"(\d{1,2})\.\s*([A-Za-zäÄ]{3})[a-zä]*\.?\s*(\d{4})", before))
        date = ""
        if dm:
            d, mon, y = dm[-1].groups()
            mo = DE_MON.get(mon.lower()[:3])
            if mo:
                date = _de_date(d, mo, y)
        after = html[m.end():m.end() + 1500]
        pm = re.search(r"<p[^>]*>(.*?)</p>", after, re.S)
        url = site["base"] + href
        out.append(dict(sid=zlib.crc32(url.encode()), url=url, title=title, teaser=_clean(pm.group(1))[:300] if pm else "",
                        category="", topic=site["topic"], plus=False, date=date))
    print(f"[{site['id']}] Liste -> {len(out)}")
    return out


def fetch_npv(site):
    """Pressemitteilungen des Nationalparks (Tabelle Datum | Titel)."""
    import requests

    page = site["base"] + "/service/presse/pressemitteilungen/"
    r = requests.get(page + "index.htm", headers={"User-Agent": UA}, timeout=30)
    r.raise_for_status()
    out = []
    for m in re.finditer(r"(\d{2})\.(\d{2})\.(\d{4})\s*</td>\s*<td[^>]*>\s*<a[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>", r.text, re.S):
        d, mo, y, href, title = m.groups()
        url = href if href.startswith("http") else page + href.lstrip("./")
        url = url.replace("&amp;", "&")
        out.append(dict(sid=zlib.crc32(url.encode()), url=url, title=_clean(title), teaser="", category="",
                        topic=site["topic"], plus=False, date=_de_date(d, mo, y)))
    print(f"[{site['id']}] Tabelle -> {len(out)}")
    return out


BR_PLACES = re.compile(
    r"Berchtesgaden|Bad Reichenhall|Reichenhall|Freilassing|Laufen|Bischofswiesen|Ramsau|Schönau|Königssee|"
    r"Marktschellenberg|Piding|Ainring|Teisendorf|Bayerisch Gmain|Schneizlreuth|Saaldorf|Surheim|Anger\b|"
    r"Traunstein|Traunreut|Trostberg|Ruhpolding|Inzell|Reit im Winkl|Chiemsee|Chiemgau|Rupertiwinkel|Waging|"
    r"Tittmoning|Siegsdorf|Grassau|Übersee|Watzmann|Untersberg|Kehlstein|Obersalzberg|Jenner|Hochkalter|Salzach")


def fetch_br(site):
    """BR24: offizieller Feed, nur Meldungen mit Ortsbezug Berchtesgadener Land / Chiemgau."""
    import requests

    r = requests.get(site["rss"], headers={"User-Agent": UA}, timeout=30)
    r.raise_for_status()
    items = [it for it in _rss_items(r.content, art=False) if BR_PLACES.search(it["title"] + " " + it["teaser"])]
    for it in items:
        it["topic"] = site["topic"]
    print(f"[{site['id']}] RSS {r.status_code} -> {len(items)} mit Ortsbezug")
    return items


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
            ("/region-und-lokal/lokalnachrichten-landkreis-traunstein.html", "Region/Landkreis Traunstein", "/startseite_artikel"),
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
        id="bgland24",
        name="BGLand24",
        base="https://www.bgland24.de",
        link="/",
        feed="feed_bgland24.xml",
        state="state_bgland24.json",
        sources=[("/", "Region")],
        topics=None,  # dynamisch aus den Kategorien der Artikel
        parse=None,
        retopic=None,
        fetch=fetch_bgland,
        enrich=bg_enrich,
        order="seen",
    ),
    dict(
        id="chiemgau24",
        name="chiemgau24",
        base="https://www.chiemgau24.de",
        link="/",
        feed="feed_chiemgau24.xml",
        state="state_chiemgau24.json",
        sources=[("/", "Region")],
        topics=None,
        parse=None,
        retopic=None,
        fetch=fetch_chiemgau,
        enrich=bg_enrich,
        order="seen",
        ippen=True,
        only_region=True,
    ),
    dict(
        id="orf_salzburg",
        name="ORF Salzburg",
        base="https://salzburg.orf.at",
        link="/",
        rss="https://rss.orf.at/salzburg.xml",
        topic="Region/Salzburg",
        feed="feed_orf_salzburg.xml",
        state="state_orf_salzburg.json",
        sources=[("/", "Region/Salzburg")],
        topics=None,
        parse=None,
        retopic=None,
        fetch=fetch_rss_site,
        order="seen",
    ),
    dict(
        id="tt",
        name="Traunsteiner Tagblatt",
        base="https://www.traunsteiner-tagblatt.de",
        link="/region/landkreis-traunstein.html",
        feed="feed_tt.xml",
        state="state_tt.json",
        sources=[
            ("/region/landkreis-traunstein.html", "Region/Landkreis Traunstein", "/region/"),
            ("/region/berchtesgadener-land.html", "Region/Berchtesgadener Land", "/region/"),
            ("/sport/sport-aus-der-region.html", "Sport/Sport aus der Region", "/sport/"),
            ("/region/kultur.html", "Kultur", "/region/kultur"),
        ],
        topics=None,
        parse=parse_bga,
        retopic=tt_topic,
        order="id",
    ),
    dict(
        id="lra_bgl",
        name="Landratsamt BGL",
        base="https://www.lra-bgl.de",
        link="/t/presse/pressemitteilungen/",
        topic="Region/Landratsamt",
        feed="feed_lra_bgl.xml",
        state="state_lra_bgl.json",
        sources=[("/", "Region/Landratsamt")],
        topics=None,
        parse=None,
        retopic=None,
        fetch=fetch_lra,
        order="seen",
    ),
    dict(
        id="npv",
        name="Nationalpark Berchtesgaden",
        base="https://www.nationalpark-berchtesgaden.bayern.de",
        link="/service/presse/pressemitteilungen/index.htm",
        topic="Region/Nationalpark",
        feed="feed_npv.xml",
        state="state_npv.json",
        sources=[("/", "Region/Nationalpark")],
        topics=None,
        parse=None,
        retopic=None,
        fetch=fetch_npv,
        order="seen",
    ),
    dict(
        id="br24",
        name="BR24",
        base="https://www.br.de",
        link="/nachrichten/",
        rss="https://feeds.br24.de/rss/nachrichten",
        topic="Region/BGL & Chiemgau",
        feed="feed_br24.xml",
        state="state_br24.json",
        sources=[("/", "Region/BGL & Chiemgau")],
        topics=None,
        parse=None,
        retopic=None,
        fetch=fetch_br,
        order="seen",
    ),
]


def fetch_site(site):
    import requests

    if site.get("fetch"):
        return site["fetch"](site)

    found = {}
    for src in site["sources"]:
        path, topic = src[0], src[1]
        only = src[2] if len(src) > 2 else ""  # nur Artikel, deren URL dies enthaelt (Seite mischt mehrere Rubriken)
        try:
            r = requests.get(site["base"] + path, headers={"User-Agent": UA}, timeout=30)
            r.raise_for_status()
        except Exception as e:  # eine Teilseite darf ausfallen
            print(f"[{site['id']}] {path}: {e}", file=sys.stderr)
            continue
        for it in site["parse"](r.text, site["base"]):
            if only and only not in it["url"]:
                continue
            it.setdefault("topic", topic)
            if site["retopic"]:
                it["topic"] = site["retopic"](it, topic)
            old = found.get(it["sid"])
            if old:
                if old.get("plus"):
                    it["plus"] = True  # Plus-Kennzeichen geht nicht verloren
                # Artikel tauchen auf fremden Rubrikseiten wieder auf (Seitenleisten): innerhalb
                # derselben Hauptrubrik gewinnt die speziellere (spaetere) Seite, sonst die erste.
                if old["topic"].split("/")[0] != it["topic"].split("/")[0]:
                    it["topic"] = old["topic"]
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
            state_items[key] = {**it, "first_seen": it.get("date") or now.isoformat()}
        else:
            state_items[key].update({k: it[k] for k in ("title", "teaser", "category", "topic")})
            state_items[key]["plus"] = bool(state_items[key].get("plus") or it.get("plus"))
            if it.get("pc"):
                state_items[key]["pc"] = it["pc"]
    if site["id"] == "bgland24" or site.get("ippen"):  # alte Statedaten auf das aktuelle Themenschema bringen
        for k in [k for k, v in state_items.items() if not BG_ART.search(v["url"].split("?")[0])]:
            del state_items[k]  # Menue-/Rubrik-Links sind keine Artikel
        for v in state_items.values():
            v["topic"] = bg_topic(v["url"])
        if site.get("only_region"):
            for k in [k for k, v in state_items.items() if not v["topic"].startswith("Region")]:
                del state_items[k]
    ordered, per_topic = [], {}
    for x in sorted(state_items.values(), key=sort_key(site), reverse=True):
        t = x.get("topic") or "Region"
        per_topic[t] = per_topic.get(t, 0) + 1
        if per_topic[t] <= MAX_PER_TOPIC and len(ordered) < MAX_ITEMS:
            ordered.append(x)
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
    rank = ["Region", "Bayern", "Sport", "Nachrichten"]
    dyn = sorted(
        present | {p.split("/")[0] for p in present},
        key=lambda t: (rank.index(t.split("/")[0]) if t.split("/")[0] in rank else 99, t),
    )
    topics = [
        t for t in (site["topics"] or dyn)
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
    if site.get("enrich"):
        known = state.get("items", {})
        for v in known.values():
            if v.get("pc") != 2:
                v["plus"] = False  # frueher falsch/ungeprueft erkannt: neu pruefen
        n = 0
        for it in items:
            if not it["url"].endswith(".html") or (str(it["sid"]) in known and known[str(it["sid"])].get("pc") == 2) or n >= 40:
                continue
            n += 1
            try:
                site["enrich"](it)
            except Exception as e:  # ohne Zeit: dann gilt "zuerst gesehen"
                print(f"[{site['id']}] Zeit fuer {it['url']}: {e}", file=sys.stderr)
    if site["id"] == "bgland24":
        if BG_PROBE:
            bg_probe()
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
        topics = run_site(site, now, args.html, args.out, args.state)
        if topics is not None:
            ok += 1
        else:
            topics = old_topics.get(site["id"], [])
            if not topics:
                continue  # Quelle lieferte noch nie Artikel: nicht im Katalog anbieten
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

"""Quellen-Bewertung: Status, Feed?, Anzahl, neuestes/aeltestes Datum, Treffer Bergthemen, robots-Hinweis."""
import re, sys, requests, html
from email.utils import parsedate_to_datetime
from urllib.parse import urlparse
UA = "BGAWidget/2.3 (privates Nachrichten-Widget, Android)"
BERG = re.compile(r"Berg(wacht|rettung|unfall|steiger|tour|not)|Alpin|Lawine|abgestürzt|Absturz|Wanderer|Kletter|Skitour|Hubschrauber|Christoph|Gipfel|Hütte|Klettersteig|Bergsteig|Notarzthubschrauber|Martin \d|Rega|Gletscher", re.I)
seen_robots = {}
for u in sys.argv[1:]:
    print(f"\n### {u}")
    host = urlparse(u).netloc
    if host not in seen_robots:
        try:
            rb = requests.get(f"https://{host}/robots.txt", headers={"User-Agent": UA}, timeout=15).text
            blk = re.search(r"User-agent:\s*\*\s*\n((?:(?!User-agent).*\n?)*)", rb, re.I)
            rules = [l.strip() for l in (blk.group(1) if blk else "").splitlines() if l.strip().lower().startswith("disallow")]
            note = "VERBOT" if any(l.lower().replace(" ", "") == "disallow:/" for l in rules) else "ok"
            if re.search(r"automat|text.?and.?data|44b", rb, re.I): note += " +TDM/Automat-Hinweis"
            seen_robots[host] = note
        except Exception as e:
            seen_robots[host] = f"? {e.__class__.__name__}"
    print("  robots:", seen_robots[host])
    try:
        r = requests.get(u, headers={"User-Agent": UA, "Accept-Language": "de-DE"}, timeout=25)
    except Exception as e:
        print("  FEHLER", e.__class__.__name__); continue
    t = r.text
    items = [m.group(0) for m in re.finditer(r"<(item|entry)\b.*?</\1>", t, re.S)]
    print(f"  HTTP {r.status_code}, {len(t)//1024} KB, Items {len(items)}")
    if not items:
        for x in sorted(set(re.findall(r'href="([^"]*(?:rss|feed|\.xml|atom)[^"]*)"', t, re.I)))[:8]: print("  F:", x)
        continue
    dates, hits, sample = [], 0, []
    for it in items:
        ti = html.unescape(re.sub(r"<!\[CDATA\[|\]\]>", "", (re.search(r"<title[^>]*>(.*?)</title>", it, re.S) or [None, ""])[1])).strip()
        de = (re.search(r"<description[^>]*>(.*?)</description>|<summary[^>]*>(.*?)</summary>", it, re.S))
        d = re.search(r"<pubDate>(.*?)</pubDate>|<dc:date>(.*?)</dc:date>|<updated>(.*?)</updated>|<published>(.*?)</published>", it, re.S)
        if d:
            s = next(g for g in d.groups() if g)
            try:
                dates.append(parsedate_to_datetime(s.strip()) if "," in s else __import__("datetime").datetime.fromisoformat(s.strip().replace("Z", "+00:00")))
            except Exception: pass
        if BERG.search(ti + " " + (de.group(0) if de else "")):
            hits += 1
            if len(sample) < 3: sample.append(ti[:90])
    if dates:
        print(f"  neueste {max(dates):%d.%m. %H:%M}, älteste {min(dates):%d.%m. %H:%M}")
    print(f"  Bergthemen: {hits}/{len(items)}")
    for s in sample: print("   •", s)

"""Einmalige Erreichbarkeits-Prüfung (wenige Abrufe): Status, Größe, erste Titel."""
import re, sys, requests
UA = "Mozilla/5.0 (Linux; Android 15; 25113PN0EG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"
for u in sys.argv[1:]:
    try:
        r = requests.get(u, headers={"User-Agent": UA, "Accept-Language": "de-DE"}, timeout=25)
        t = r.text
        titles = re.findall(r"<title>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</title>", t)[:6]
        links = sorted(set(re.findall(r'href="([^"]*artikel_[^"]*|[^"]*-\d{6,}\.html)"', t)))[:8]
        print(f"\n### {u}\n{r.status_code} {len(t)} Zeichen, Typ {r.headers.get('content-type')}")
        for x in titles: print("  T:", x[:110])
        for x in links: print("  L:", x[:140])
        if u.endswith("robots.txt"): print(t[:1600])
        if "LINKS" in u or u.endswith("/lokales/"):
            print("  Bereiche:", sorted(set(re.findall(r'href="(?:https:)?//www\.merkur\.de(/lokales/[a-z0-9-]+/)"', t)))[:60])
        if "rss" in u or "feed" in u:
            print("  Items:", t.count("<item"))
            for d in re.findall(r"<dc:date>(.*?)</dc:date>|<pubDate>(.*?)</pubDate>", t)[:3]: print("  D:", d)
    except Exception as e:
        print(f"\n### {u}\nFEHLER {e}")

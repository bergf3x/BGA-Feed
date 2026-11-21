"""Sucht frei lizenzierte Landschaftsfotos auf Wikimedia Commons und laedt Vorschaubilder (1280 px) mit Lizenzdaten."""
import json, re, sys, requests
from pathlib import Path
UA = "BGL-Wetter-News/1.0 (private app; github.com/bergf3x)"
QUERIES = {
    "watzmann": "Watzmann Berchtesgaden",
    "koenigssee": "Königssee St. Bartholomä",
    "hintersee": "Hintersee Ramsau Hochkalter",
}
out = Path("cand"); out.mkdir(exist_ok=True)
meta = {}
for key, q in QUERIES.items():
    r = requests.get("https://commons.wikimedia.org/w/api.php", headers={"User-Agent": UA}, params=dict(
        action="query", generator="search", gsrsearch=q + " filetype:bitmap", gsrnamespace=6, gsrlimit=30,
        prop="imageinfo", iiprop="url|extmetadata|size", iiurlwidth=1280, format="json"), timeout=30).json()
    pages = sorted(r.get("query", {}).get("pages", {}).values(), key=lambda p: p.get("index", 99))
    n = 0
    for p in pages:
        ii = p["imageinfo"][0]; em = ii.get("extmetadata", {})
        lic = em.get("LicenseShortName", {}).get("value", "")
        if ii["width"] < 1600 or ii["width"] < ii["height"] * 1.3: continue
        if not re.search(r"CC BY|CC0|Public domain|PD", lic, re.I): continue
        if "NC" in lic or "ND" in lic: continue
        artist = re.sub(r"<[^>]+>", "", em.get("Artist", {}).get("value", "")).strip()
        n += 1
        name = f"{key}_{n}.jpg"
        (out / name).write_bytes(requests.get(ii["thumburl"], headers={"User-Agent": UA}, timeout=60).content)
        meta[name] = dict(title=p["title"], license=lic, artist=artist, page=ii.get("descriptionurl"))
        print(name, lic, artist, p["title"])
        if n >= 5: break
(out / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=1))

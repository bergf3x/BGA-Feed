"""Kleiner UI-Helfer fuer den Emulator-Test: Element per Text/Beschreibung finden und antippen.
Aufruf: python3 ui.py tap "Aktualisieren"   |   python3 ui.py has "Keine Artikel"   |   python3 ui.py texts"""
import re
import subprocess
import sys
import time


def dump():
    for _ in range(3):
        subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True, timeout=30)
        r = subprocess.run(["adb", "shell", "cat", "/sdcard/ui.xml"], capture_output=True, text=True, timeout=30)
        if "<hierarchy" in r.stdout:
            return r.stdout
        time.sleep(1)
    return ""


NODE = re.compile(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')


def nodes():
    for m in NODE.finditer(dump()):
        t, d, x1, y1, x2, y2 = m.groups()
        yield t, d, (int(x1) + int(x2)) // 2, (int(y1) + int(y2)) // 2


def main():
    cmd, arg = sys.argv[1], (sys.argv[2] if len(sys.argv) > 2 else "")
    if cmd == "texts":
        for t, d, x, y in nodes():
            if t or d:
                print(f"{y:5} | {t[:70]} | {d[:50]}")
        return
    for t, d, x, y in nodes():
        if arg and (arg == t or arg == d or arg in d or t.startswith(arg)):
            if cmd == "tap":
                subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)])
                print(f"getippt: {arg} @ {x},{y}")
            else:
                print(f"gefunden: {arg}")
            return
    print(f"NICHT GEFUNDEN: {arg}")
    sys.exit(0)


if __name__ == "__main__":
    main()

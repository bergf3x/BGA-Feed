"""Gibt die Umrisse des Widgets auf dem Startbildschirm aus: x1 y1 x2 y2 (Vereinigung aller Knoten des Pakets)."""
import re, subprocess, sys, time
pkg = sys.argv[1]
for _ in range(3):
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True, timeout=30)
    x = subprocess.run(["adb", "shell", "cat", "/sdcard/ui.xml"], capture_output=True, text=True, timeout=30).stdout
    if "<hierarchy" in x:
        break
    time.sleep(1)
b = [tuple(map(int, m.groups())) for m in re.finditer(r'package="' + re.escape(pkg) + r'"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)]
if not b:
    print("0 0 0 0"); sys.exit(0)
print(min(v[0] for v in b), min(v[1] for v in b), max(v[2] for v in b), max(v[3] for v in b))

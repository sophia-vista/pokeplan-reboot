#!/usr/bin/env python3
"""Rasterise a few Pixelarticons (MIT, https://github.com/halfmage/pixelarticons) into assets/pixelarticons/<name>.png.

Each is a 24x24 white glyph on transparency, tinted at draw time (composeApp `Glyph`). Used for simple controls (arrows,
edit, copy, trash...) where a word would be too long; the Pokémon item icons stay the main icons.
Usage: python3 -I tools/sprites/fetch_pixelarticons.py [path to a pixelarticons clone]
(clones the pinned commit when no path is given)."""
import os, re, subprocess, sys, tempfile
from PIL import Image, ImageChops, ImageDraw

COMMIT = "c9ecc2da61afde85dbe539025e3d931c6152ad75"
NAMES = ["arrow-left", "arrow-right", "arrow-up", "arrow-down", "pencil", "copy", "trash", "plus", "minus", "close",
         "check", "play", "pause", "reload", "undo", "search", "star", "stop", "chevron-left", "chevron-right",
         "chevron-up", "chevron-down", "more-horizontal", "flag", "repeat", "clipboard",
         "volume-2", "bell", "sun", "moon", "user", "cloud", "home", "sliders", "layout", "grid-2x2-2", "hand", "pointer",
         "drag-and-drop", "eye", "thermometer", "calendar", "clock", "battery", "map", "heart", "zap", "settings-cog",
         "cursor-minimal", "power", "shield", "download", "upload", "info-box", "lock", "vibrate", "languages", "globe", "eye-off", "map-pin", "music", "battery-low", "users", "battery-medium", "battery-full"]
SS = 8

def subpaths(d):
    toks = re.findall(r"[MmHhVvLlZz]|-?\d*\.?\d+", d)
    i, x, y, sx, sy, cmd, cur = 0, 0.0, 0.0, 0.0, 0.0, None, []
    out = []
    def num():
        nonlocal i
        v = float(toks[i]); i += 1; return v
    while i < len(toks):
        if re.match(r"[A-Za-z]", toks[i]):
            cmd = toks[i]; i += 1
            if cmd in "Zz":
                if cur: out.append(cur); cur = []
                x, y = sx, sy
                continue
        if cmd in "Mm":
            if cur: out.append(cur)
            a, b = num(), num()
            x, y = (a, b) if cmd == "M" else (x + a, y + b)
            sx, sy, cur = x, y, [(x, y)]
            cmd = "L" if cmd == "M" else "l"
            continue
        if cmd in "Hh": a = num(); x = a if cmd == "H" else x + a
        elif cmd in "Vv": a = num(); y = a if cmd == "V" else y + a
        elif cmd in "Ll":
            a, b = num(), num(); x, y = (a, b) if cmd == "L" else (x + a, y + b)
        else: raise SystemExit(f"unsupported command {cmd}")
        cur.append((x, y))
    if cur: out.append(cur)
    return out

def raster(svg):
    d = " ".join(re.findall(r' d="([^"]*)"', svg))
    big = 24 * SS
    # even-odd: XOR every sub-polygon, which is how these icons cut their holes
    mask = Image.new("1", (big, big), 0)
    for poly in subpaths(d):
        m = Image.new("1", (big, big), 0)
        ImageDraw.Draw(m).polygon([(px * SS, py * SS) for px, py in poly], fill=1)
        mask = ImageChops.logical_xor(mask, m)
    alpha = mask.convert("L").resize((24, 24), Image.NEAREST)
    img = Image.new("RGBA", (24, 24), (255, 255, 255, 0))
    img.putalpha(alpha)
    for yy in range(24):
        for xx in range(24):
            if alpha.getpixel((xx, yy)): img.putpixel((xx, yy), (255, 255, 255, 255))
    return img

def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else None
    if repo is None:
        repo = tempfile.mkdtemp(prefix="pixelarticons-")
        subprocess.check_call(["git", "clone", "-q", "https://github.com/halfmage/pixelarticons", repo])
        subprocess.check_call(["git", "-C", repo, "checkout", "-q", COMMIT])
    out = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "pixelarticons")
    os.makedirs(out, exist_ok=True)
    for n in NAMES:
        raster(open(os.path.join(repo, "svg", n + ".svg")).read()).save(os.path.join(out, n + ".png"))
    print(len(NAMES), "icons written to", os.path.normpath(out))

main()

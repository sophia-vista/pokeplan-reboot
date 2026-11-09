#!/usr/bin/env python3
"""Inspects the weather sheets PokéLife draws (assets/weather) and the owner's rips they were cut from (assets/weather/source): sizes,
frame grids, and for every frame its bounding box, opaque pixel count, distinct colours and whether it repeats another frame.

Usage: python3 -I tools/weather/inspect_sheets.py [out dir]   (with an out dir, every frame is also written there as a PNG)
Prints a markdown table; the cut grids are those of tools/sprites/cut_weather.py. Needs Pillow.
"""
import os
import sys

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
DIR = os.path.join(ROOT, 'assets/weather')
# name -> (frame width, frame height, columns, rows, frames used)
SHEETS = {
    'rain': (240, 160, 5, 13, 61),
    'snow': (64, 32, 4, 4, 16),
    'snow-dungeon': (32, 32, 4, 4, 16),
    'blizzard': (32, 32, 4, 4, 16),
    'sand-1': (120, 32, 1, 1, 1),
    'sand-2': (120, 32, 1, 1, 1),
    'clouds': (720, 408, 1, 1, 1),
}


def frames(name):
    w, h, cols, rows, used = SHEETS[name]
    sheet = Image.open(os.path.join(DIR, name + '.png')).convert('RGBA')
    for i in range(used):
        yield i, sheet.crop(((i % cols) * w, (i // cols) * h, (i % cols + 1) * w, (i // cols + 1) * h))


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else None
    if out:
        os.makedirs(out, exist_ok=True)
    print('| sheet | file size | frame | frames | opaque px (min-max) | semi-transparent px | colours | bbox of frame 0 | repeated frames |')
    print('|---|---|---|---|---|---|---|---|---|')
    for name, (w, h, cols, rows, used) in SHEETS.items():
        counts, semi, seen, repeats, colours = [], 0, {}, [], set()
        bbox0 = None
        for i, fr in frames(name):
            data = fr.tobytes()
            if data in seen:
                repeats.append((i, seen[data]))
            seen.setdefault(data, i)
            px = fr.load()
            n = 0
            for y in range(h):
                for x in range(w):
                    r, g, b, a = px[x, y]
                    if a:
                        n += 1
                        colours.add((r, g, b))
                        if a < 255:
                            semi += 1
            counts.append(n)
            if i == 0:
                bbox0 = fr.getchannel('A').getbbox()
            if out:
                fr.save(os.path.join(out, f'{name}-{i:02d}.png'))
        size = Image.open(os.path.join(DIR, name + '.png')).size
        print(f'| {name} ({size[0]}x{size[1]}) | {os.path.getsize(os.path.join(DIR, name + ".png"))} B | {w}x{h} | {used} | {min(counts)}-{max(counts)} | {semi} | {len(colours)} | {bbox0} | {repeats or "none"} |')


if __name__ == '__main__':
    main()

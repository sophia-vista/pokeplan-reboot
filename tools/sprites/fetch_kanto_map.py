#!/usr/bin/env python3
"""Builds assets/world/map-kanto.png, the Kanto region map of Pokémon FireRed and LeafGreen, from the game's own tiles.

Usage: python3 -I tools/sprites/fetch_kanto_map.py   (needs Pillow)

The pret/pokefirered decompilation (pinned commit below) keeps the region map as a tile sheet (region_map.png with the palettes
in region_map.pal) and a 30 x 20 tilemap of the land, sea and towns (kanto.bin). This draws it, fills the transparent pixels around
the town squares and the blank Sevii Islands inset with the sea's own colours (row by row, as the sea is striped), and crops the map
itself (24 x 16 tiles, 192 x 128 pixels), without the game's frame or title bar. Nothing is drawn by hand. Private use only (Nintendo / Game Freak art, as the
rest of assets/world); listed in docs/assets.md.
"""
import io
import os
import struct
import urllib.request

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'assets/world/map-kanto.png')
# Pinned for reproducibility. To update: git ls-remote https://github.com/pret/pokefirered refs/heads/master
COMMIT = 'ce6406d8c2eaf46d543c8a2606de0c3cb1951982'
BASE = f'https://raw.githubusercontent.com/pret/pokefirered/{COMMIT}/graphics/region_map/'
W, H = 30, 20
# The map itself inside the 30 x 20 screen, in tiles: left, top, width, height.
CROP = (3, 2, 24, 16)


def get(name):
    with urllib.request.urlopen(BASE + name, timeout=60) as r:
        return r.read()


def draw(canvas, tiles, tilemap, colours, transparent_zero):
    """Paints [tilemap] onto [canvas]; palette index 0 is left alone when [transparent_zero]."""
    tw = tiles.size[0] // 8
    px = tiles.load()
    out = canvas.load()
    entries = struct.unpack('<%dH' % (len(tilemap) // 2), tilemap)
    for i, e in enumerate(entries[:W * H]):
        t, hflip, vflip, pal = e & 0x3ff, (e >> 10) & 1, (e >> 11) & 1, (e >> 12) & 15
        tx, ty = (t % tw) * 8, (t // tw) * 8
        for y in range(8):
            for x in range(8):
                idx = px[tx + (7 - x if hflip else x), ty + (7 - y if vflip else y)]
                if idx == 0 and transparent_zero:
                    continue
                n = pal * 16 + idx
                out[(i % W) * 8 + x, (i // W) * 8 + y] = colours[n] if n < len(colours) else (0, 0, 0)  # the blank Sevii inset


def main():
    lines = get('region_map.pal').decode().split('\n')
    colours = [tuple(map(int, l.split())) for l in lines[3:3 + int(lines[2])]]
    tiles = Image.open(io.BytesIO(get('region_map.png')))
    key = (255, 0, 255)
    canvas = Image.new('RGB', (W * 8, H * 8), key)
    draw(canvas, tiles, get('kanto.bin'), colours, True)
    left, top, w, h = CROP
    out = canvas.crop((left * 8, top * 8, (left + w) * 8, (top + h) * 8))
    px = out.load()
    # The blank inset is open sea; the sea is striped and its column at the right edge of the crop is open water in every row.
    sea = [px[out.width - 1, y] for y in range(out.height)]
    for y in range(out.height):
        for x in range(out.width):
            if px[x, y] == (0, 0, 0):
                px[x, y] = sea[y]
    # The transparent corners of the town squares take the colour of what is around them, a ring at a time.
    todo = {(x, y) for y in range(out.height) for x in range(out.width) if px[x, y] == key}
    while todo:
        fill = {}
        for x, y in todo:
            near = [px[x + dx, y + dy] for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1))
                    if 0 <= x + dx < out.width and 0 <= y + dy < out.height and (x + dx, y + dy) not in todo]
            if near:
                fill[(x, y)] = max(set(near), key=near.count)
        for (x, y), c in fill.items():
            px[x, y] = c
        todo -= set(fill)
    out.save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Builds assets/world/map-hoenn.png, the Hoenn region map of Pokémon Emerald's PokéNav, from the game's own tiles.

Usage: python3 -I tools/sprites/fetch_hoenn_map.py   (needs Pillow)

The pret/pokeemerald decompilation (pinned commit below) keeps the region map as a sheet of 233 8-bit tiles (map.png, whose palette
indices start at 112 because the game loads its 32 colours into palette slot 7), the 32 colours (map.pal) and a 64 x 64 affine
tilemap (map.bin, one byte per tile). This puts them together and crops the map out of the 512 x 512 background (256 x 152 pixels:
the land, routes, towns and cities, and the sea around them). Nothing is drawn by hand. Private use only (Nintendo / Game Freak art,
as the rest of assets/world); listed in docs/assets.md.
"""
import io
import os
import urllib.request

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'assets/world/map-hoenn.png')
# Pinned for reproducibility. To update: git ls-remote https://github.com/pret/pokeemerald refs/heads/master
COMMIT = '731ad5bfd6e6f265508d0efcca0ba42f9dcf5881'
BASE = f'https://raw.githubusercontent.com/pret/pokeemerald/{COMMIT}/graphics/pokenav/region_map/'
SIDE = 64
FIRST_COLOUR = 112
CROP = (0, 0, 256, 152)


def get(name):
    with urllib.request.urlopen(BASE + name, timeout=60) as r:
        return r.read()


def main():
    lines = get('map.pal').decode().split('\n')
    colours = [tuple(map(int, l.split())) for l in lines[3:3 + int(lines[2])]]
    tiles = Image.open(io.BytesIO(get('map.png')))
    tilemap = get('map.bin')
    per_row = tiles.size[0] // 8
    px = tiles.load()
    out = Image.new('RGB', (SIDE * 8, SIDE * 8))
    for i, t in enumerate(tilemap[:SIDE * SIDE]):
        tx, ty = (t % per_row) * 8, (t // per_row) * 8
        for y in range(8):
            for x in range(8):
                n = px[tx + x, ty + y] - FIRST_COLOUR
                out.putpixel(((i % SIDE) * 8 + x, (i // SIDE) * 8 + y), colours[n] if 0 <= n < len(colours) else colours[0])
    out.crop(CROP).save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Builds assets/world/map-johto.png, the Johto map of Pokémon Crystal's Pokégear, from the game's own tiles.

Usage: python3 -I tools/sprites/fetch_johto_map.py   (needs Pillow)

The pret/pokecrystal disassembly (pinned commit below) keeps the town map as 48 tiles (town_map.png, four greys), a 20 x 18 tilemap
(johto.bin, one byte per tile) and the colours of each tile (town_map_palette_map.asm and pokegear.pal, in the Game Boy Color's
5-bit RGB). This puts them together as Crystal shows them and crops the map inside its frame (18 x 16 tiles, 144 x 128 pixels).
Nothing is drawn by hand. Private use only (Nintendo / Game Freak art, as the rest of assets/world); listed in docs/assets.md.
"""
import io
import os
import re
import urllib.request

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'assets/world/map-johto.png')
# Pinned for reproducibility. To update: git ls-remote https://github.com/pret/pokecrystal refs/heads/master
COMMIT = '3c813b0b4d82b39bf62185aceffb76fd6026c70b'
BASE = f'https://raw.githubusercontent.com/pret/pokecrystal/{COMMIT}/gfx/pokegear/'
W, H = 20, 18
PALETTES = ['BORDER', 'EARTH', 'MOUNTAIN', 'CITY', 'POI', 'POI_MTN']
GREY_TO_INDEX = {255: 0, 170: 1, 85: 2, 0: 3}


def get(name):
    with urllib.request.urlopen(BASE + name, timeout=60) as r:
        return r.read()


def main():
    tiles = Image.open(io.BytesIO(get('town_map.png')))
    tilemap = get('johto.bin')
    colours = [tuple(int(v) * 255 // 31 for v in m) for m in re.findall(r'RGB (\d+), (\d+), (\d+)', get('pokegear.pal').decode())]
    palette = {name: colours[i * 4:i * 4 + 4] for i, name in enumerate(PALETTES)}
    rows = [l for l in get('town_map_palette_map.asm').decode().split('\n') if l.strip().startswith('townmappals')][:6]
    tile_palette = [n.strip() for row in rows for n in row.strip()[len('townmappals'):].split(',')]
    px = tiles.load()
    out = Image.new('RGB', (W * 8, H * 8))
    for i, t in enumerate(tilemap[:W * H]):
        if t >= len(tile_palette):
            continue
        p = palette[tile_palette[t]]
        tx, ty = (t % 16) * 8, (t // 16) * 8
        for y in range(8):
            for x in range(8):
                out.putpixel(((i % W) * 8 + x, (i // W) * 8 + y), p[GREY_TO_INDEX.get(px[tx + x, ty + y], 0)])
    out.crop((8, 8, 8 + 144, 8 + 128)).save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()

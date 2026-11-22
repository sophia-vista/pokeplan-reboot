#!/usr/bin/env python3
import io
import os
import re
import urllib.request

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'assets/world/map-johto.png')
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

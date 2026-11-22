#!/usr/bin/env python3
import io
import os
import struct
import urllib.request

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'assets/world/map-sinnoh.png')
COMMIT = 'c6689696d5fb51a644239702cb556eb52012a46a'
BASE = f'https://raw.githubusercontent.com/pret/pokeplatinum/{COMMIT}/res/graphics/town_map/'
BRIGHTEN = 2.4
CROP = (20, 90, 500, 490)


def get(name):
    with urllib.request.urlopen(BASE + name, timeout=60) as r:
        return r.read()


def main():
    lines = get('bottom_screen_map.pal').decode().split('\n')
    colours = [tuple(map(int, l.split())) for l in lines[3:3 + int(lines[2])]]
    tiles = Image.open(io.BytesIO(get('bottom_screen_map_tiles.png')))
    px = tiles.load()
    per_row = tiles.size[0] // 8
    data = get('bottom_screen_region_map_tilemap.NSCR')
    i = data.index(b'NRCS')
    n = struct.unpack('<I', data[i + 16:i + 20])[0]
    entries = struct.unpack('<%dH' % (n // 2), data[i + 20:i + 20 + n])
    out = Image.new('RGB', (512, 512))
    for k, e in enumerate(entries[:4096]):
        tile, hflip, vflip, bank = e & 0x3FF, (e >> 10) & 1, (e >> 11) & 1, e >> 12
        block, within = k // 1024, k % 1024
        cx, cy = (block % 2) * 32 + within % 32, (block // 2) * 32 + within // 32
        tx, ty = (tile % per_row) * 8, (tile // per_row) * 8
        for y in range(8):
            for x in range(8):
                v = px[tx + (7 - x if hflip else x), ty + (7 - y if vflip else y)] // 17
                c = colours[bank * 16 + v]
                out.putpixel((cx * 8 + x, cy * 8 + y), tuple(min(255, int(ch * BRIGHTEN)) for ch in c))
    crop = out.crop(CROP)
    brown = (151, 107, 55)
    cp = crop.load()
    for y in range(crop.height):
        for x in range(crop.width):
            r, g, b = cp[x, y]
            if r > 230 and 120 < g < 190 and b < 40:
                cp[x, y] = brown
    crop.resize((crop.width // 2, crop.height // 2), Image.NEAREST).save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()

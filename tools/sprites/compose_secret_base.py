#!/usr/bin/env python3
"""Puts together the Secret Base rooms the app draws (REP-053, docs/pokelife MODULES/HOME.md 9.5) from the pret/pokeemerald art that
`fetch_pret.py` put in assets/hoenn/: for each of the six looks, the size-1 room's floor and walls (the game's own layout, `map.bin`, drawn from the
tileset's metatiles), and every decoration sprite with its see-through colour made transparent.

Writes assets/hoenn/room/<look>.png (176 x 144, 11 x 9 cells of 16 px) and assets/hoenn/room/decorations/<id>.png (24 x 24, RGBA).
Nothing is drawn by hand: the pixels are the game's tiles and sprites, only assembled. Needs Pillow. Skips files that exist.

Usage: python3 -I tools/sprites/compose_secret_base.py
"""
import os
import struct

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
HOENN = os.path.join(ROOT, 'assets/hoenn')
OUT = os.path.join(HOENN, 'room')
TILE = 16
# look -> (layout, secondary tileset). Same table as RoomLook in base-core.
LOOKS = {
    'tree': ('SecretBase_Tree1', 'tree'),
    'shrub': ('SecretBase_Shrub1', 'shrub'),
    'red_cave': ('SecretBase_RedCave1', 'red_cave'),
    'brown_cave': ('SecretBase_BrownCave1', 'brown_cave'),
    'blue_cave': ('SecretBase_BlueCave1', 'blue_cave'),
    'yellow_cave': ('SecretBase_YellowCave1', 'yellow_cave'),
}


def palette(path):
    lines = [l for l in open(path).read().splitlines()[3:] if l.strip()]
    return [tuple(int(v) for v in l.split()) for l in lines]


def metatiles(path):
    data = open(path, 'rb').read()
    return [struct.unpack_from('<8H', data, i * 16) for i in range(len(data) // 16)]


def indices(path):
    img = Image.open(path)
    assert img.mode == 'P', path
    return img.width, list(img.getdata())


def floor(layout, tileset):
    primary = os.path.join(HOENN, 'secret-base/primary')
    secondary = os.path.join(HOENN, 'secret-base/secondary', tileset)
    palettes = [palette(os.path.join(primary if n < 6 else secondary, 'palettes/%02d.pal' % n)) for n in range(16)]
    pw, ptiles = indices(os.path.join(primary, 'tiles.png'))
    sw, stiles = indices(os.path.join(secondary, 'tiles.png'))
    pmeta = metatiles(os.path.join(primary, 'metatiles.bin'))
    smeta = metatiles(os.path.join(HOENN, 'secret-base/secondary/metatiles.bin'))
    width, height = [int(v) for v in next(l for l in open(os.path.join(HOENN, 'secret-base/layouts.tsv')).read().splitlines() if l.startswith(layout + '\t')).split('\t')[1:3]]
    cells = struct.unpack('<%dH' % (width * height), open(os.path.join(HOENN, 'secret-base/layouts', layout, 'map.bin'), 'rb').read())
    out = Image.new('RGBA', (width * TILE, height * TILE), (0, 0, 0, 0))
    px = out.load()
    for n, cell in enumerate(cells):
        meta_id = cell & 0x3FF
        meta = pmeta[meta_id] if meta_id < 512 else smeta[meta_id - 512]
        for layer in range(2):
            for quarter in range(4):
                entry = meta[layer * 4 + quarter]
                tile, flip_x, flip_y, pal = entry & 0x3FF, entry >> 10 & 1, entry >> 11 & 1, entry >> 12
                sheet, sheet_w, index = (ptiles, pw, tile) if tile < 512 else (stiles, sw, tile - 512)
                sx, sy = index % (sheet_w // 8) * 8, index // (sheet_w // 8) * 8
                for y in range(8):
                    for x in range(8):
                        colour = sheet[(sy + (7 - y if flip_y else y)) * sheet_w + sx + (7 - x if flip_x else x)]
                        if layer == 1 and colour == 0:
                            continue  # colour 0 of the top layer is see-through
                        r, g, b = palettes[pal][colour]
                        px[n % width * TILE + quarter % 2 * 8 + x, n // width * TILE + quarter // 2 * 8 + y] = (r, g, b, 255)
    return out


def decoration(path):
    img = Image.open(path)
    assert img.mode == 'P', path
    rgba = img.convert('RGBA')
    data = list(img.getdata())
    rgba.putdata([(0, 0, 0, 0) if i == 0 else p for i, p in zip(data, rgba.getdata())])  # palette colour 0 is the see-through colour
    return rgba


def main():
    os.makedirs(os.path.join(OUT, 'decorations'), exist_ok=True)
    for look, (layout, tileset) in LOOKS.items():
        target = os.path.join(OUT, look + '.png')
        if not os.path.exists(target):
            floor(layout, tileset).save(target)
            print('wrote', target)
    src = os.path.join(HOENN, 'decorations')
    for name in sorted(os.listdir(src)):
        if name.endswith('.png') and name != 'put_away_cursor.png':
            target = os.path.join(OUT, 'decorations', name)
            if not os.path.exists(target):
                decoration(os.path.join(src, name)).save(target)
    print('decorations:', len(os.listdir(os.path.join(OUT, 'decorations'))))


if __name__ == '__main__':
    main()

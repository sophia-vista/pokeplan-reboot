#!/usr/bin/env python3
import glob
import os
from collections import Counter

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SRC = os.path.join(ROOT, 'assets/tiles/sheets')
OUT = os.path.join(ROOT, 'assets/tiles')
TEAL = (0, 128, 128, 255)
LINE = (128, 255, 255)
MAGENTA = (255, 0, 255)
TILE = 24


def pixels(t):
    return [t.getpixel((x, y)) for y in range(t.height) for x in range(t.width)]


WALLS, GROUND, ALT1, ALT2 = 3, 12, 15, 18


def origin(im):
    w, h = im.size
    px = im.convert('RGB').load()
    xs = [x for x in range(w) if sum(px[x, y] == LINE for y in range(160, min(h, 700))) > 300]
    ys = [y for y in range(h) if sum(px[x, y] == LINE for x in range(8, 600)) > 300]
    return xs[0] + 1, ys[0] + 1


def tile(im, o, c, r):
    x, y = o[0] + 25 * c, o[1] + 25 * r
    return im.crop((x, y, x + TILE, y + TILE))


def empty(t):
    return all(p == TEAL for p in pixels(t))


def clean(t):
    colors = Counter(p for p in pixels(t) if p[:3] != MAGENTA)
    fill = colors.most_common(1)[0][0]
    t = t.copy()
    t.putdata([fill if p[:3] == MAGENTA else p for p in pixels(t)])
    return t


def find_water(im, o):
    for c in range(21, 34, 3):
        block = [tile(im, o, c + i, r) for r in range(3) for i in range(3)]
        if im.width < o[0] + 25 * (c + 3) or any(empty(t) for t in block):
            continue
        if all(sum(p[:3] == MAGENTA for p in pixels(t)) < TILE * TILE * 0.4 for t in block):
            return c
    return None


def cut(path):
    im = Image.open(path).convert('RGBA')
    o = origin(im)
    walls = [tile(im, o, WALLS + c, r) for r in range(3) for c in range(3)]
    wc = find_water(im, o)
    if wc is None:
        return None
    water = [tile(im, o, wc + c, r) for r in range(3) for c in range(3)]
    ground = tile(im, o, GROUND + 1, 1)
    if any(empty(t) for t in walls + water + [ground]):
        return None
    if any(p[:3] == MAGENTA for t in walls for p in pixels(t)):
        return None
    alts = [tile(im, o, ALT1 + c, 1) for c in range(3)], [tile(im, o, ALT2 + c, 1) for c in range(3)]
    alt = [next((t for t in group if not empty(t)), ground) for group in alts]
    atlas = Image.new('RGBA', (6 * TILE, 4 * TILE), (0, 0, 0, 0))
    for i, t in enumerate(walls):
        atlas.paste(t, ((i % 3) * TILE, (i // 3) * TILE))
    for i, t in enumerate(water):
        atlas.paste(clean(t), ((3 + i % 3) * TILE, (i // 3) * TILE))
    for i, t in enumerate([ground] + alt):
        atlas.paste(t, (i * TILE, 3 * TILE))
    return atlas


def main():
    done = []
    for path in sorted(glob.glob(os.path.join(SRC, '*.png'))):
        name = os.path.splitext(os.path.basename(path))[0]
        atlas = cut(path)
        if atlas is None:
            print('skipped', name)
            continue
        atlas.save(os.path.join(OUT, name + '.png'), optimize=True)
        done.append(name)
    print(len(done), 'atlases:', ' '.join(done))


if __name__ == '__main__':
    main()

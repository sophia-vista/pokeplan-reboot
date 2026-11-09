#!/usr/bin/env python3
"""Cuts the Mystery Dungeon weather effects out of assets/weather/source (the owner's rips of the Red Rescue Team sheets)
into assets/weather/<name>.png, transparent where the sheet's panel colour (90, 90, 120) was.

Usage: python3 -I tools/sprites/cut_weather.py

rain    5 x 13 grid of 240 x 160 frames (61 used): rain streaks and splashes, play in order and tile
snow    4 x 4 grid of 64 x 32 frames, snow-dungeon and blizzard 4 x 4 grids of 32 x 32: tiles that repeat across the screen
sand    two 120 x 32 textures, one above the other, that scroll sideways
clouds  the 720 x 408 shadow of drifting clouds in the game's own colour (82, 115, 189: the 5-bit colour 10, 14, 23 of W01.bpl), blended in at the game's 4/16 when drawn
Needs Pillow.
"""
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
DIR = os.path.join(ROOT, 'assets/weather')
PANEL = (90, 90, 120)


def clear(image):
    image = image.convert('RGBA')
    px = image.load()
    for y in range(image.height):
        for x in range(image.width):
            if px[x, y][:3] == PANEL:
                px[x, y] = (0, 0, 0, 0)
    return image


def grid(sheet, xs, ys, w, h):
    out = Image.new('RGBA', (w * len(xs), h * len(ys)), (0, 0, 0, 0))
    for r, y in enumerate(ys):
        for c, x in enumerate(xs):
            out.paste(clear(sheet.crop((x, y, x + w, y + h))), (c * w, r * h))
    return out


def main():
    misc = Image.open(os.path.join(DIR, 'source/misc.png')).convert('RGBA')
    rain = Image.open(os.path.join(DIR, 'source/rain.png')).convert('RGBA')
    # Rain: 13 rows of 5 frames from (8, 8), 248 px and 168 px apart; the last row holds one.
    rain_xs = [8 + 248 * i for i in range(5)]
    rain_ys = [8 + 168 * i for i in range(13)]
    sheet = grid(rain, rain_xs, rain_ys, 240, 160)
    sheet.crop((0, 0, 240 * 5, 160 * 13)).save(os.path.join(DIR, 'rain.png'))
    rows = [440, 480, 520, 560]
    grid(misc, [8, 80, 152, 224], rows, 64, 32).save(os.path.join(DIR, 'snow.png'))
    grid(misc, [304, 344, 384, 424], rows, 32, 32).save(os.path.join(DIR, 'blizzard.png'))
    grid(misc, [472, 512, 552, 592], rows, 32, 32).save(os.path.join(DIR, 'snow-dungeon.png'))
    grid(misc, [8], [608], 120, 32).save(os.path.join(DIR, 'sand-1.png'))
    grid(misc, [136], [608], 120, 32).save(os.path.join(DIR, 'sand-2.png'))
    clouds = misc.crop((8, 16, 728, 424)).convert('RGBA')
    px = clouds.load()
    for y in range(clouds.height):
        for x in range(clouds.width):
            r, g, b, _ = px[x, y]
            px[x, y] = (82, 115, 189, 255) if (r, g, b) == (82, 115, 189) else (0, 0, 0, 0)
    clouds.save(os.path.join(DIR, 'clouds.png'))
    print('done')


if __name__ == '__main__':
    main()

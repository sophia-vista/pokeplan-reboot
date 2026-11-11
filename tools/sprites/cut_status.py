#!/usr/bin/env python3
"""Cuts the Mystery Dungeon status icons out of assets/status/sheet.png (the owner's rip of the status sheet) into one
horizontal strip per icon, assets/status/<name>.png, each frame a 16 x 16 cell on a transparent background.

Usage: python3 -I tools/sprites/cut_status.py

The sheet's teal background (0, 120, 144) becomes transparent. Needs Pillow.
"""
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
DIR = os.path.join(ROOT, 'assets/status')
CELL = 16
LEFT = 104  # the palettes sit to the left of the icons
# name -> (top y of the row on the sheet, frames[, cell size])
ROWS = {
    'alert': (32, 9),  # "!"
    'question': (47, 10),  # "?"
    'cross': (64, 9),  # an X
    'sleep': (232, 9),  # Zz, growing
    'skull': (250, 8),
    # The stat icons, kept for the type-flavoured reactions of the buddy (a flame for Fire, a fist for Fighting...).
    'flame': (112, 7),
    'shield': (96, 13),
    'fist': (176, 8),
    'ice': (144, 6, 32),  # a block of ice, 32 px cells
}


# The second sheet, with icons of other sizes: name -> (cell size, [the box of each frame on the sheet]).
EXTRA = {
    'zap': (24, [(22, 22, 42, 50), (42, 20, 68, 50)]),  # the yellow flash of a shock
    'sweat': (16, [(188, 34, 198, 50), (197, 34, 206, 52)]),  # the blue drop of a nervous one
    'twinkle': (24, [(5, 76, 26, 98), (24, 76, 46, 98), (44, 76, 70, 98)]),  # a white sparkle, for something nice
}


def extra():
    sheet = Image.open(os.path.join(DIR, 'sheet2.png')).convert('RGBA')
    bg = sheet.getpixel((sheet.width - 1, 0))
    for name, (cell, boxes) in EXTRA.items():
        strip = Image.new('RGBA', (cell * len(boxes), cell), (0, 0, 0, 0))
        for i, box in enumerate(boxes):
            part = sheet.crop(box)
            px = part.load()
            for y in range(part.height):
                for x in range(part.width):
                    if px[x, y] == bg:
                        px[x, y] = (0, 0, 0, 0)
            trimmed = part.crop(part.getbbox() or (0, 0, 1, 1))
            strip.paste(trimmed, (i * cell + (cell - trimmed.width) // 2, (cell - trimmed.height) // 2))
        strip.save(os.path.join(DIR, f'{name}.png'))
        print(name, len(boxes))


def main():
    extra()
    sheet = Image.open(os.path.join(DIR, 'sheet.png')).convert('RGBA')
    bg = sheet.getpixel((sheet.width - 1, 0))
    for name, (top, frames, *size) in ROWS.items():
        side = size[0] if size else CELL
        strip = Image.new('RGBA', (side * frames, side), (0, 0, 0, 0))
        for i in range(frames):
            cell = sheet.crop((LEFT + i * side, top, LEFT + (i + 1) * side, top + side))
            px = cell.load()
            for y in range(side):
                for x in range(side):
                    if px[x, y] == bg:
                        px[x, y] = (0, 0, 0, 0)
            strip.paste(cell, (i * side, 0))
        strip.save(os.path.join(DIR, f'{name}.png'))
        print(name, frames)


if __name__ == '__main__':
    main()

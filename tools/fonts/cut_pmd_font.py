#!/usr/bin/env python3
"""Measures the glyphs of assets/fonts/pmd-dialogue.png, the Pokemon dialogue bitmap font (white letters with a black drop
shadow, ripped by John2k4 from Game Freak's sprites) and writes assets/fonts/pmd-dialogue.tsv:
`char <TAB> x <TAB> y <TAB> width <TAB> height <TAB> dy`, the glyph's box in the sheet and its distance below (positive) or
above (negative) the baseline of its row's capital letters, so letters sit on one line. Only the sheet's own pixels are used.

Usage: python3 -I tools/fonts/cut_pmd_font.py
"""
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SHEET = os.path.join(ROOT, 'assets/fonts/pmd-dialogue.png')
OUT = os.path.join(ROOT, 'assets/fonts/pmd-dialogue.tsv')
COLUMNS = 13
# The rows of the sheet, top to bottom, 13 glyphs each ('_' is not a letter here: the last cell of row 5 is the underline).
ROWS = [
    ('abcdefghijklm', 0),
    ('nopqrstuvwxyz', 1),
    ('ABCDEFGHIJKLM', 2),
    ('NOPQRSTUVWXYZ', 3),
    ('1234567890:+-', 4),
    (',.¡!¿?‘’“”♂♀_', 5),
    ('àáâãäåæçèéêëì', 6),
]
# Which glyph of each row sits on the baseline (its bottom edge is the baseline), so descenders hang below it.
BASE = {0: 'a', 1: 'n', 2: 'A', 3: 'N', 4: '1', 5: '?', 6: 'à'}


def bands(im):
    w, h = im.size
    rows = [any(im.getpixel((x, y))[3] for x in range(w)) for y in range(h)]
    out, s = [], None
    for y, v in enumerate(rows + [False]):
        if v and s is None:
            s = y
        if not v and s is not None:
            out.append((s, y - 1))
            s = None
    return out


def main():
    im = Image.open(SHEET).convert('RGBA')
    w = im.width
    band = bands(im)
    cell = w / COLUMNS
    lines = []
    for chars, r in ROWS:
        # The accent rows come after a gap: bands 6 and on are the ones at index 6 (a to m accents) ... see the sheet.
        y0, y1 = band[r] if r < 6 else band[6]
        boxes = {}
        for i, ch in enumerate(chars):
            xs = range(int(i * cell), int((i + 1) * cell))
            pts = [(x, y) for x in xs for y in range(y0, y1 + 1) if x < w and im.getpixel((x, y))[3]]
            if not pts:
                continue
            boxes[ch] = (min(p[0] for p in pts), min(p[1] for p in pts), max(p[0] for p in pts), max(p[1] for p in pts))
        base = boxes[BASE[r]][3] + 1
        for ch, (x0, ty, x1, by) in boxes.items():
            lines.append(f'{ch}\t{x0}\t{ty}\t{x1 - x0 + 1}\t{by - ty + 1}\t{ty - base}')
    with open(OUT, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(lines) + '\n')
    print(len(lines), 'glyphs')


if __name__ == '__main__':
    main()

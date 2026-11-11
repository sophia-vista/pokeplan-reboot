"""Gives the zero of the Pokémon DP font (hexparrot/pokemon-assets, MIT) a bar through its middle so it is not mistaken for an 8
at small sizes (design review, 11 November 2026). Run from assets/fonts on the original files:
    pip install fonttools && python3 -I ../../tools/fonts/patch_zero.py
The regular zero gets a 3 pixel centre bar; the bold zero (a 2 pixel hole) gets a 3 pixel staircase. One art pixel = 157 font units."""
from fontTools.ttLib import TTFont
from fontTools.ttLib.tables._g_l_y_f import GlyphCoordinates

U = 157

def patch(path, cells):
    t = TTFont(path)
    g = t['glyf']['zero']
    coords, ends, flags = list(g.coordinates), list(g.endPtsOfContours), list(g.flags)
    for c, r in cells:
        x, y = c * U, r * U
        coords += [(x, y), (x, y + U), (x + U, y + U), (x + U, y)]  # clockwise = filled
        flags += [1] * 4
        ends.append(len(coords) - 1)
    g.coordinates, g.flags, g.endPtsOfContours, g.numberOfContours = GlyphCoordinates(coords), flags, ends, len(ends)
    g.recalcBounds(t['glyf'])
    t.save(path)

patch('pokemon_dp.ttf', [(2, 3), (2, 4), (2, 5)])
patch('pokemon_dp_bold.ttf', [(3, 3), (2, 4), (3, 5)])


# The peso sign (U+20B1), which the font lacks: a P with two bars through its bowl (regular) or stubs left of its stem (bold).
import copy
from fontTools.ttLib.tables._g_l_y_f import Glyph


def add_peso(path, bars, stubs, advance):
    t = TTFont(path)
    p = t['glyf'][t.getBestCmap()[ord('P')]]
    g = Glyph()
    coords = [(x + U, y) for x, y in p.coordinates]  # one pixel right, room for the stubs
    ends, flags = list(p.endPtsOfContours), list(p.flags)
    for x0, x1, y in bars + stubs:
        coords += [(x0 + U, y), (x0 + U, y + U), (x1 + U, y + U), (x1 + U, y)]
        flags += [1] * 4
        ends.append(len(coords) - 1)
    g.numberOfContours, g.coordinates, g.flags, g.endPtsOfContours = len(ends), GlyphCoordinates(coords), flags, ends
    g.program = copy.deepcopy(p.program)
    order = t.getGlyphOrder() + ['peso']
    t.setGlyphOrder(order)
    t['glyf'].glyphs['peso'] = g
    t['glyf'].glyphOrder = order
    g.recalcBounds(t['glyf'])
    t['hmtx']['peso'] = (advance, g.xMin)
    for table in t['cmap'].tables:
        if table.isUnicode():
            table.cmap[0x20B1] = 'peso'
    t.save(path)


add_peso('pokemon_dp.ttf', bars=[(157, 628, 1099), (157, 628, 785)], stubs=[(-157, 0, 1099), (-157, 0, 785)], advance=1099)
add_peso('pokemon_dp_bold.ttf', bars=[(314, 628, 785)], stubs=[(-157, 0, 785), (-157, 0, 1099)], advance=1256)

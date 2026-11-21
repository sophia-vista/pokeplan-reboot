from fontTools.ttLib import TTFont
from fontTools.ttLib.tables._g_l_y_f import GlyphCoordinates

U = 157

def patch(path, cells):
    t = TTFont(path)
    g = t['glyf']['zero']
    coords, ends, flags = list(g.coordinates), list(g.endPtsOfContours), list(g.flags)
    for c, r in cells:
        x, y = c * U, r * U
        coords += [(x, y), (x, y + U), (x + U, y + U), (x + U, y)]
        flags += [1] * 4
        ends.append(len(coords) - 1)
    g.coordinates, g.flags, g.endPtsOfContours, g.numberOfContours = GlyphCoordinates(coords), flags, ends, len(ends)
    g.recalcBounds(t['glyf'])
    t.save(path)

patch('pokemon_dp.ttf', [(2, 3), (2, 4), (2, 5)])
patch('pokemon_dp_bold.ttf', [(3, 3), (2, 4), (3, 5)])


import copy
from fontTools.ttLib.tables._g_l_y_f import Glyph


def add_peso(path, bars, stubs, advance):
    t = TTFont(path)
    p = t['glyf'][t.getBestCmap()[ord('P')]]
    g = Glyph()
    coords = [(x + U, y) for x, y in p.coordinates]
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

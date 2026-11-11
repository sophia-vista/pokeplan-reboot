#!/usr/bin/env python3
"""Gives a Mystery Dungeon form that SpriteCollab has no shiny for a shiny made from the form's real still sprites.

Usage: python3 -I tools/sprites/derive_shiny.py [--dry-run]

assets/sprites holds each form's regular and shiny sprite (PokeAPI/sprites), drawn pixel for pixel on the same shapes. For a
form whose assets/pmd sheets have no `<id>-shiny.png`, this reads which of the regular sprite's colours the shiny changes and by
how much (hue, saturation, value), then recolours the form's own PMD sheets (every folder in assets/pmd, and its portrait strip)
the same way: each pixel takes the change of the nearest colour of the regular still sprite, and a pixel that is nearest to a
colour the shiny leaves alone (the outline, a belly, the eyes) stays as it is. Only recolouring of real pixels: no pixel is drawn.
A form whose shiny still is the regular one is left without a shiny. The sheet tables (pmd.tsv, sleep.tsv...) and portraits.tsv
get their shiny flag set, and assets/pmd/derived-shiny.tsv lists `id <TAB> folders` for the forms that got one, so a real
SpriteCollab shiny (a later fetch_pmd.py run) can replace it. The credits are the regular sprite's artists', unchanged.

Needs Pillow. Run it after fetch_pmd.py: a fetch writes the real shinies and the tables afresh, so delete derived-shiny.tsv before a re-run and only what is still missing is derived again.
"""
import colorsys
import io
import os
import sys

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
PMD = os.path.join(ROOT, 'assets/pmd')
STILLS = os.path.join(ROOT, 'assets/sprites')
# folder -> table name (the idle sheets are the folder itself)
FOLDERS = ['', 'sleep', 'eat', 'hop', 'walk-down', 'walk-side', 'walk-up', 'idle-side', 'idle-up', 'rotate', 'wake', 'deep-breath', 'pose']
# The furthest a pixel may be from a colour of the regular still (redmean distance) to take its change.
NEAREST = 110.0
# A colour counts as changed when its shiny is this far from it, or this far round the hue circle (degrees).
CHANGED = 48.0
HUE_CHANGED = 12.0


def distance(a, b):
    rm = (a[0] + b[0]) / 2
    dr, dg, db = a[0] - b[0], a[1] - b[1], a[2] - b[2]
    return ((2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db) ** 0.5


def hsv(c):
    return colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)


def hue_gap(a, b):
    """The distance round the hue circle, in degrees (0 to 180)."""
    return abs((a - b + 0.5) % 1.0 - 0.5) * 360


# Below these a colour is a grey, a white or a near-black: its hue means nothing, so it is matched by its lightness alone.
GREY_SATURATION = 0.18
DARK_VALUE = 0.2
# The furthest round the hue circle a pixel may be from the colour of the regular still it takes its change from.
HUE_REACH = 32.0


def chromatic(h_s_v):
    return h_s_v[1] > GREY_SATURATION and h_s_v[2] > DARK_VALUE


def anchors(fid):
    """The regular still's colours with what the shiny does to each, or None without a usable pair or when the shiny looks the same.

    A list of (h, s, v, change) of the regular colours, change being None when the shiny keeps the colour, else the
    (hue turn, saturation difference, value ratio) that turns it into the shiny's.
    """
    regular, shiny = os.path.join(STILLS, f'{fid}.png'), os.path.join(STILLS, f'{fid}-shiny.png')
    if not (os.path.exists(regular) and os.path.exists(shiny)):
        return None
    a, b = Image.open(regular).convert('RGBA'), Image.open(shiny).convert('RGBA')
    if a.size != b.size:
        return None
    flat = lambda im: im.get_flattened_data() if hasattr(im, 'get_flattened_data') else im.getdata()
    pairs = {}
    for pa, pb in zip(flat(a), flat(b)):
        if pa[3] > 0 and pb[3] > 0:
            pairs.setdefault(pa[:3], {}).setdefault(pb[:3], 0)
            pairs[pa[:3]][pb[:3]] += 1
    out, changed = [], 0
    for ca, targets in pairs.items():
        cb = max(targets, key=targets.get)
        ha, sa, va = hsv(ca)
        hb, sb, vb = hsv(cb)
        dh = (hb - ha + 0.5) % 1.0 - 0.5
        moved = distance(ca, cb) > CHANGED or (abs(dh) * 360 > HUE_CHANGED and sa > GREY_SATURATION and sb > GREY_SATURATION)
        if moved:
            changed += 1
            out.append((ha, sa, va, (dh, sb - sa, vb / va if va > 0.05 else 1.0, hb)))
        else:
            out.append((ha, sa, va, None))
    return out if changed else None


def recolour_map(colours, table):
    """{colour: recoloured colour} for the distinct RGB [colours] of a sheet."""
    found = {}
    for c in colours:
        h, s, v = hsv(c)
        best, near = None, 1e9
        for anchor in table:
            ah, as_, av, _ = anchor
            if chromatic((h, s, v)) != chromatic((ah, as_, av)):
                continue
            # Chromatic colours are matched by hue first, then how pale and how light; greys by lightness (and a little by tint).
            d = (hue_gap(h, ah) + 70 * abs(s - as_) + 70 * abs(v - av)) if chromatic((h, s, v)) else (200 * abs(v - av) + 40 * abs(s - as_))
            if d < near:
                best, near = anchor, d
        if best is None or best[3] is None or (chromatic((h, s, v)) and hue_gap(h, best[0]) > HUE_REACH):
            found[c] = c
            continue
        dh, ds, vr, hb = best[3]
        if chromatic((h, s, v)):
            h = (h + dh) % 1.0
        else:
            # A grey the shiny tints (a white that goes lilac): take the shiny's hue, and as much saturation as it gained.
            h = hb
        s = min(1.0, max(0.0, s + ds))
        v = min(1.0, v * vr)
        r, g, b = colorsys.hsv_to_rgb(h, s, v)
        found[c] = (round(r * 255), round(g * 255), round(b * 255))
    return found


def recolour(path, table):
    image = Image.open(path).convert('RGBA')
    colours = {p[:3] for p in image.getdata() if p[3] > 0}
    mapping = recolour_map(colours, table)
    out = Image.new('RGBA', image.size)
    out.putdata([(*mapping[p[:3]], p[3]) if p[3] > 0 else p for p in image.getdata()])
    if out.getcolors(maxcolors=256) is not None:
        out = out.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    buf = io.BytesIO()
    out.save(buf, 'PNG', optimize=True)
    return buf.getvalue()


def read_table(path):
    with open(path, encoding='utf-8') as fh:
        return [line.rstrip('\n').split('\t') for line in fh if line.strip()]


def main():
    dry = '--dry-run' in sys.argv[1:]
    derived = {}
    tables = {}
    for sub in FOLDERS:
        name = 'pmd' if sub == '' else sub
        path = os.path.join(PMD, sub, f'{name}.tsv')
        if os.path.exists(path):
            tables[sub] = (path, read_table(path))
    portraits_path = os.path.join(PMD, 'portraits', 'portraits.tsv')
    portraits = read_table(portraits_path)
    todo = {}
    # Only a form whose idle sheet has no real shiny gets its other sheets derived (a form with a real idle shiny and a missing one
    # for another row, because the frames differ, keeps showing the real one there as before: the app falls back to its idle).
    plain = {int(r[0]) for r in tables[''][1] if r[5] == '0'}
    for sub, (_, rows) in tables.items():
        for r in rows:
            if r[5] == '0' and int(r[0]) in plain:
                todo.setdefault(int(r[0]), []).append(sub)
    for r in portraits:
        if r[2] == '0':
            todo.setdefault(int(r[0]), []).append('portraits')
    skipped = []
    for fid in sorted(todo):
        table = anchors(fid)
        if table is None:
            skipped.append(fid)
            continue
        for sub in todo[fid]:
            src = os.path.join(PMD, sub, f'{fid}.png')
            if not os.path.exists(src):
                continue
            if not dry:
                with open(os.path.join(PMD, sub, f'{fid}-shiny.png'), 'wb') as fh:
                    fh.write(recolour(src, table))
            derived.setdefault(fid, []).append(sub or 'idle')
            if sub == 'portraits':
                for r in portraits:
                    if int(r[0]) == fid:
                        r[2] = '1'
            else:
                for r in tables[sub][1]:
                    if int(r[0]) == fid:
                        r[5] = '1'
    print(f'{len(derived)} forms got a derived shiny, {len(skipped)} have no usable still pair: {skipped}', file=sys.stderr)
    if dry:
        return
    for sub, (path, rows) in tables.items():
        with open(path, 'w', encoding='utf-8') as fh:
            for r in rows:
                fh.write('\t'.join(r) + '\n')
    with open(portraits_path, 'w', encoding='utf-8') as fh:
        for r in portraits:
            fh.write('\t'.join(r) + '\n')
    # A form derived by an earlier run keeps its line (its flags are set now, so this run does not see it as missing a shiny).
    listed = os.path.join(PMD, 'derived-shiny.tsv')
    if os.path.exists(listed):
        for fid, subs in read_table(listed):
            merged = dict.fromkeys(derived.get(int(fid), []) + subs.split(','))
            derived[int(fid)] = list(merged)
    with open(listed, 'w', encoding='utf-8') as fh:
        for fid in sorted(derived):
            fh.write(f'{fid}\t{",".join(derived[fid])}\n')


if __name__ == '__main__':
    main()

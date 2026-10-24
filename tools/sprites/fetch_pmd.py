#!/usr/bin/env python3
"""Downloads the Pokemon Mystery Dungeon style sprites of the PMD SpriteCollab (https://github.com/PMDCollab/SpriteCollab)
and turns each one's Idle animation, facing the viewer, into a sprite sheet the app plays on every platform.

Usage: python3 -I tools/sprites/fetch_pmd.py

Needs Pillow (pip install pillow) and assets/sprites/manifest.tsv (tools/sprites/fetch.py), which names every form id.

SpriteCollab holds the official Mystery Dungeon sprites (Chunsoft) and community ones drawn in the same style, under
CC BY-NC 4.0: non-commercial use with credit. For every form id with a sprite it writes assets/pmd/<id>.png (the frames of
the first, front-facing row of Idle-Anim.png side by side, or Walk when there is no Idle) and <id>-shiny.png when SpriteCollab
has the shiny. Every frame is cut to the box all frames of the row fit in, so the Pokemon fills the frame without jitter.
assets/pmd/pmd.tsv lists `id <TAB> frames <TAB> frame width <TAB> frame height <TAB> comma separated frame times in
milliseconds <TAB> shiny (0 or 1)` (the format of assets/animated/animated.tsv), and assets/pmd/credits.tsv lists
`id <TAB> artists` for the credit the licence asks for. Only the sprites' own pixels are copied; nothing is drawn.
Forms match SpriteCollab's form names (rattata-alola is Alola, charizard-mega-x is Mega_X); recolours, alternates and
cutscene versions are skipped. A run downloads and writes everything again.
"""
import concurrent.futures
import csv
import io
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'assets/pmd')
MANIFEST = os.path.join(ROOT, 'assets/sprites/manifest.tsv')
# Pinned, so a run gives the same files; move it forward to take in new sprites.
COMMIT = 'cf6c95f4684398ca29c1ac69fd4895ed03c07e5b'
BASE = f'https://raw.githubusercontent.com/PMDCollab/SpriteCollab/{COMMIT}/'
# Mystery Dungeon games run at 60 frames a second; AnimData durations count those frames.
FRAME_MS = 1000 / 60


def download(url):
    """The bytes, or None if the file does not exist."""
    for attempt in range(6):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if e.code not in (429, 500, 502, 503):
                raise
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(2 ** attempt)
    raise RuntimeError('giving up on ' + url)


def norm(s):
    return re.sub('[^a-z0-9]', '', s.lower())


def targets(tracker):
    """(form id, SpriteCollab folder, tracker node) for every form of ours that SpriteCollab has a sprite for."""
    with open(MANIFEST, encoding='utf-8') as fh:
        rows = [(int(r[0]), r[1]) for r in csv.reader(fh, delimiter='\t') if r]
    species = {fid: ident for fid, ident in rows if fid < 10000}
    out = []
    for fid, ident in rows:
        if fid < 10000:
            sid, suffix = fid, ''
        else:
            # The species is the longest species identifier the form's identifier starts with.
            base = max((s for s, i in species.items() if ident.startswith(i + '-')), key=lambda s: len(species[s]), default=None)
            if base is None:
                continue
            sid, suffix = base, ident[len(species[base]) + 1:]
        node = tracker.get(f'{sid:04d}')
        if node is None:
            continue
        folder = f'{sid:04d}'
        if suffix:
            match = [(k, v) for k, v in node.get('subgroups', {}).items() if k != '0000' and norm(v['name']) == norm(suffix)]
            if not match:
                continue
            key, node = match[0]
            folder += '/' + key
        if node.get('sprite_complete', 0) > 0:
            out.append((fid, folder, node))
    return out


def front_row(folder):
    """(frames, frame times in ms) of the front-facing row of the Idle animation (Walk if there is none), or None."""
    xml = download(BASE + f'sprite/{folder}/AnimData.xml')
    if xml is None:
        return None
    anims = {a.findtext('Name'): a for a in ET.fromstring(xml).find('Anims')}

    def resolve(name):
        a = anims.get(name)
        seen = set()
        while a is not None and a.findtext('CopyOf') and name not in seen:
            seen.add(name)
            name = a.findtext('CopyOf')
            a = anims.get(name)
        return name, a

    for wanted in ('Idle', 'Walk'):
        name, anim = resolve(wanted)
        if anim is None or anim.find('Durations') is None:
            continue
        data = download(BASE + f'sprite/{folder}/{name}-Anim.png')
        if data is None:
            continue
        w, h = int(anim.findtext('FrameWidth')), int(anim.findtext('FrameHeight'))
        times = [max(1, round(int(d.text) * FRAME_MS)) for d in anim.find('Durations')]
        image = Image.open(io.BytesIO(data)).convert('RGBA')
        # Row 0 faces down, towards the viewer.
        frames = [image.crop((i * w, 0, (i + 1) * w, h)) for i in range(min(len(times), image.width // w))]
        if frames:
            return frames, times[:len(frames)]
    return None


def sheet(frames, box=None):
    """(PNG bytes, frame width, frame height, box): the frames cut to [box] (by default the box they all fit in), side by side."""
    if box is None:
        boxes = [f.getbbox() for f in frames if f.getbbox()]
        if not boxes:
            return None
        box = (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))
    cut = [f.crop(box) for f in frames]
    w, h = cut[0].size
    strip = Image.new('RGBA', (w * len(cut), h), (0, 0, 0, 0))
    for i, f in enumerate(cut):
        strip.paste(f, (i * w, 0))
    if strip.getcolors(maxcolors=256) is not None:
        strip = strip.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    out = io.BytesIO()
    strip.save(out, 'PNG', optimize=True)
    return out.getvalue(), w, h, box


def credit(node, names):
    ids = [node['sprite_credit']['primary']] + node['sprite_credit'].get('secondary', [])
    return ', '.join(dict.fromkeys(names.get(i, i.strip('<@!>')) for i in ids if i))


def fetch_one(target, names):
    fid, folder, node = target
    row = front_row(folder)
    made = row and sheet(row[0])
    if not made:
        return fid, None
    png, w, h, box = made
    with open(os.path.join(OUT, f'{fid}.png'), 'wb') as fh:
        fh.write(png)
    shiny = node.get('subgroups', {}).get('0000', {}).get('subgroups', {}).get('0001')
    has_shiny = False
    if shiny and shiny.get('sprite_complete', 0) > 0:
        shiny_row = front_row(folder + '/0000/0001')
        # The shiny is a recolour of the same frames: cut it the same way, and use it only if it has the same frames.
        same = shiny_row and len(shiny_row[0]) == len(row[0]) and shiny_row[0][0].size == row[0][0].size
        shiny_made = same and sheet(shiny_row[0], box)
        if shiny_made:
            with open(os.path.join(OUT, f'{fid}-shiny.png'), 'wb') as fh:
                fh.write(shiny_made[0])
            has_shiny = True
    return fid, (len(row[0]), w, h, row[1], has_shiny, credit(node, names))


def main():
    os.makedirs(OUT, exist_ok=True)
    tracker = json.loads(download(BASE + 'tracker.json'))
    names = {}
    for line in download(BASE + 'credit_names.txt').decode('utf-8').splitlines()[1:]:
        parts = line.split('\t')
        if len(parts) >= 2:
            names[parts[1]] = parts[0]
    todo = targets(tracker)
    print(f'{len(todo)} forms have a SpriteCollab sprite', file=sys.stderr)
    results = {}
    with concurrent.futures.ThreadPoolExecutor(8) as pool:
        for n, (fid, info) in enumerate(pool.map(lambda t: fetch_one(t, names), todo), 1):
            if info:
                results[fid] = info
            if n % 100 == 0:
                print(f'{n}/{len(todo)}', file=sys.stderr)
    with open(os.path.join(OUT, 'pmd.tsv'), 'w', encoding='utf-8') as fh:
        for fid in sorted(results):
            frames, w, h, times, shiny, _ = results[fid]
            fh.write(f'{fid}\t{frames}\t{w}\t{h}\t{",".join(map(str, times))}\t{int(shiny)}\n')
    with open(os.path.join(OUT, 'credits.tsv'), 'w', encoding='utf-8') as fh:
        for fid in sorted(results):
            fh.write(f'{fid}\t{results[fid][5]}\n')
    print(f'wrote {len(results)} sheets ({sum(r[4] for r in results.values())} with a shiny)', file=sys.stderr)


if __name__ == '__main__':
    main()

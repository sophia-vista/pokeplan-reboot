#!/usr/bin/env python3
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
COMMIT = 'cf6c95f4684398ca29c1ac69fd4895ed03c07e5b'
BASE = f'https://raw.githubusercontent.com/PMDCollab/SpriteCollab/{COMMIT}/'
FRAME_MS = 1000 / 60
ANIMS = {'': ('Idle', 'Walk'), 'sleep': ('Sleep',), 'eat': ('Eat',), 'hop': ('Hop',)}
WALK = {'walk-down': 0, 'walk-side': 2, 'walk-up': 4}
ROWS = {**WALK, 'idle-side': 2, 'idle-up': 4}
EXTRA = {'idle-side': ('Idle',), 'idle-up': ('Idle',), 'rotate': ('Rotate',), 'wake': ('Wake',), 'deep-breath': ('DeepBreath',), 'pose': ('Pose',)}
MODE = next((a for a in sys.argv[1:] if a in ('walk', 'extra', 'faces')), None)
WALK_ONLY = MODE == 'walk'
if MODE == 'walk':
    ANIMS = {sub: ('Walk',) for sub in WALK}
elif MODE == 'extra':
    ANIMS = dict(EXTRA)
elif MODE == 'faces':
    ANIMS = {}
else:
    ANIMS = {**ANIMS, **{sub: ('Walk',) for sub in WALK}}
PORTRAITS = MODE in (None, 'faces')
EMOTIONS = ('Normal', 'Happy', 'Joyous', 'Inspired', 'Determined', 'Surprised',
            'Sad', 'Angry', 'Worried', 'Teary-Eyed', 'Pain', 'Sigh', 'Dizzy', 'Stunned')
PORTRAIT = 40


def download(url):
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
    with open(MANIFEST, encoding='utf-8') as fh:
        rows = [(int(r[0]), r[1]) for r in csv.reader(fh, delimiter='\t') if r]
    species = {fid: ident for fid, ident in rows if fid < 10000}
    out = []
    for fid, ident in rows:
        if fid < 10000:
            sid, suffix = fid, ''
        else:
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
        if node.get('sprite_complete', 0) > 0 or node.get('portrait_complete', 0) > 0:
            out.append((fid, folder, node))
    return out


def anim_data(folder):
    xml = download(BASE + f'sprite/{folder}/AnimData.xml')
    if xml is None:
        return None
    return {a.findtext('Name'): a for a in ET.fromstring(xml).find('Anims')}


def front_row(folder, anims, wanted, row=0):

    def resolve(name):
        a = anims.get(name)
        seen = set()
        while a is not None and a.findtext('CopyOf') and name not in seen:
            seen.add(name)
            name = a.findtext('CopyOf')
            a = anims.get(name)
        return name, a

    for want in wanted:
        name, anim = resolve(want)
        if anim is None or anim.find('Durations') is None:
            continue
        data = download(BASE + f'sprite/{folder}/{name}-Anim.png')
        if data is None:
            continue
        w, h = int(anim.findtext('FrameWidth')), int(anim.findtext('FrameHeight'))
        times = [max(1, round(int(d.text) * FRAME_MS)) for d in anim.find('Durations')]
        image = Image.open(io.BytesIO(data)).convert('RGBA')
        if image.height < (row + 1) * h:
            continue
        frames = [image.crop((i * w, row * h, (i + 1) * w, (row + 1) * h)) for i in range(min(len(times), image.width // w))]
        if frames:
            return frames, times[:len(frames)]
    return None


def sheet(frames, box=None):
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


def credit(node, names, kind='sprite'):
    ids = [node[f'{kind}_credit']['primary']] + node[f'{kind}_credit'].get('secondary', [])
    named = ', '.join(dict.fromkeys(n for n in (names.get(i, i.strip('<@!>')).strip() for i in ids if i) if n))
    return named or ', '.join(dict.fromkeys(f'SpriteCollab contributor {i.strip("<@!>")}' for i in ids if i))


def save(png, sub, name):
    with open(os.path.join(OUT, sub, f'{name}.png'), 'wb') as fh:
        fh.write(png)


def fetch_anims(folder, node, fid, names):
    anims = anim_data(folder) if ANIMS and node.get('sprite_complete', 0) > 0 else None
    if anims is None:
        return {}, None
    shiny = node.get('subgroups', {}).get('0000', {}).get('subgroups', {}).get('0001')
    shiny_anims = anim_data(folder + '/0000/0001') if shiny and shiny.get('sprite_complete', 0) > 0 else None
    out = {}
    for sub, wanted in ANIMS.items():
        row = front_row(folder, anims, wanted, ROWS.get(sub, 0))
        made = row and sheet(row[0])
        if not made:
            if sub == '':
                return {}, None
            continue
        png, w, h, box = made
        save(png, sub, fid)
        has_shiny = False
        shiny_row = shiny_anims and front_row(folder + '/0000/0001', shiny_anims, wanted, ROWS.get(sub, 0))
        same = shiny_row and len(shiny_row[0]) == len(row[0]) and shiny_row[0][0].size == row[0][0].size
        shiny_made = same and sheet(shiny_row[0], box)
        if shiny_made:
            save(shiny_made[0], sub, f'{fid}-shiny')
            has_shiny = True
        out[sub] = (len(row[0]), w, h, row[1], has_shiny)
    return out, credit(node, names)


def portrait_strip(folder, emotions):
    faces = []
    for emotion in emotions:
        data = download(BASE + f'portrait/{folder}/{emotion}.png')
        if data is None:
            continue
        image = Image.open(io.BytesIO(data)).convert('RGBA')
        if image.size == (PORTRAIT, PORTRAIT):
            faces.append((emotion, image))
    if not faces or faces[0][0] != 'Normal':
        return None
    strip = Image.new('RGBA', (PORTRAIT * len(faces), PORTRAIT), (0, 0, 0, 0))
    for i, (_, f) in enumerate(faces):
        strip.paste(f, (i * PORTRAIT, 0))
    if strip.getcolors(maxcolors=256) is not None:
        strip = strip.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    out = io.BytesIO()
    strip.save(out, 'PNG', optimize=True)
    return out.getvalue(), [e for e, _ in faces]


def fetch_portraits(folder, node, fid, names):
    if node.get('portrait_complete', 0) <= 0:
        return None
    made = portrait_strip(folder, EMOTIONS)
    if not made:
        return None
    save(made[0], 'portraits', fid)
    shiny = node.get('subgroups', {}).get('0000', {}).get('subgroups', {}).get('0001')
    has_shiny = False
    shiny_faces = None
    if shiny and shiny.get('portrait_complete', 0) > 0:
        shiny_made = portrait_strip(folder + '/0000/0001', EMOTIONS)
        if shiny_made:
            save(shiny_made[0], 'portraits', f'{fid}-shiny')
            has_shiny = True
            if shiny_made[1] != made[1]:
                shiny_faces = shiny_made[1]
    return made[1], has_shiny, credit(node, names, 'portrait'), shiny_faces


def fetch_one(target, names):
    fid, folder, node = target
    anims, artists = fetch_anims(folder, node, fid, names)
    return fid, anims, artists, fetch_portraits(folder, node, fid, names) if PORTRAITS else None


def write_table(sub, rows):
    name = 'pmd' if sub == '' else sub
    with open(os.path.join(OUT, sub, f'{name}.tsv'), 'w', encoding='utf-8') as fh:
        for fid in sorted(rows):
            frames, w, h, times, shiny = rows[fid]
            fh.write(f'{fid}\t{frames}\t{w}\t{h}\t{",".join(map(str, times))}\t{int(shiny)}\n')


def main():
    for sub in list(ANIMS) + (['portraits'] if PORTRAITS else []):
        os.makedirs(os.path.join(OUT, sub), exist_ok=True)
    tracker = json.loads(download(BASE + 'tracker.json'))
    names = {}
    for line in download(BASE + 'credit_names.txt').decode('utf-8').splitlines()[1:]:
        parts = line.split('\t')
        if len(parts) >= 2:
            names[parts[1]] = parts[0]
    todo = targets(tracker)
    print(f'{len(todo)} forms have a SpriteCollab sprite or portrait', file=sys.stderr)
    tables = {sub: {} for sub in ANIMS}
    credits, portraits = {}, {}
    with concurrent.futures.ThreadPoolExecutor(8) as pool:
        for n, (fid, anims, artists, faces) in enumerate(pool.map(lambda t: fetch_one(t, names), todo), 1):
            for sub, info in anims.items():
                tables[sub][fid] = info
            if artists:
                credits[fid] = artists
            if faces:
                portraits[fid] = faces
            if n % 100 == 0:
                print(f'{n}/{len(todo)}', file=sys.stderr)
    for sub, rows in tables.items():
        write_table(sub, rows)
        print(f'{sub or "idle"}: {len(rows)} sheets ({sum(r[4] for r in rows.values())} with a shiny)', file=sys.stderr)
    if PORTRAITS:
        with open(os.path.join(OUT, 'portraits', 'portraits.tsv'), 'w', encoding='utf-8') as fh:
            for fid in sorted(portraits):
                emotions, shiny, artists, shiny_faces = portraits[fid]
                fh.write(f'{fid}\t{",".join(emotions)}\t{int(shiny)}\t{artists}' + (f'\t{",".join(shiny_faces)}' if shiny_faces else '') + '\n')
    if MODE is None:
        with open(os.path.join(OUT, 'credits.tsv'), 'w', encoding='utf-8') as fh:
            for fid in sorted(credits):
                fh.write(f'{fid}\t{credits[fid]}\n')
    print(f'portraits: {len(portraits)} strips ({sum(p[1] for p in portraits.values())} with a shiny)', file=sys.stderr)


if __name__ == '__main__':
    main()

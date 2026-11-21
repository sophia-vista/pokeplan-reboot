#!/usr/bin/env python3
import argparse
import csv
import hashlib
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ASSETS = os.path.join(ROOT, 'assets', 'pmd')
MANIFEST_OUT = os.path.join(ROOT, 'docs', 'ASSETS', 'PMD_SPRITE_MANIFEST.json')

REPOS = {
    'SpriteCollab': 'https://github.com/PMDCollab/SpriteCollab',
    'CustomSpriteCollab': 'https://github.com/audinowho/CustomSpriteCollab',
    'RawAsset': 'https://github.com/PMDCollab/RawAsset',
    'lxsmnsyc': 'https://github.com/lxsmnsyc/SpriteCollab',
    'sylvainpolletvillard': 'https://github.com/sylvainpolletvillard/SpriteCollab',
    'PokeBattle': 'https://github.com/GustavoGFV/PokeBattle',
    'GameDataPacks': 'https://github.com/PokemonWorkshop/GameDataPacks',
    'pokemonAutoChess': 'https://github.com/keldaanCommunity/pokemonAutoChess',
}
SPECIES = {
    514: 'Simisear', 516: 'Simipour', 520: 'Tranquill', 522: 'Blitzle', 523: 'Zebstrika', 538: 'Throh', 558: 'Crustle', 564: 'Tirtouga',
    565: 'Carracosta', 591: 'Amoonguss', 592: 'Frillish', 593: 'Jellicent', 616: 'Shelmet', 618: 'Stunfisk', 626: 'Bouffalant',
    668: 'Pyroar', 683: 'Aromatisse', 732: 'Trumbeak', 733: 'Toucannon', 735: 'Gumshoos', 756: 'Shiinotic', 765: 'Oranguru',
    837: 'Rolycoly', 838: 'Carkol', 839: 'Coalossal', 865: "Sirfetch'd", 866: 'Mr. Rime', 868: 'Milcery', 870: 'Falinks',
    874: 'Stonjourner', 878: 'Cufant', 879: 'Copperajah', 883: 'Arctovish', 893: 'Zarude', 917: 'Tarountula', 929: 'Dolliv',
    931: 'Squawkabilly', 942: 'Maschiff', 943: 'Mabosstiff', 944: 'Shroodle', 945: 'Grafaiai', 947: 'Brambleghast', 948: 'Toedscool',
    949: 'Toedscruel', 950: 'Klawf', 954: 'Rabsca', 956: 'Espathra', 962: 'Bombirdier', 968: 'Orthworm', 973: 'Flamigo',
    986: 'Brute Bonnet', 990: 'Iron Treads', 993: 'Iron Jugulis', 999: 'Gimmighoul', 1001: 'Wo-Chien', 1002: 'Chien-Pao',
    1003: 'Ting-Lu', 1008: 'Miraidon', 1014: 'Okidogi', 1020: 'Gouging Fire', 1021: 'Raging Bolt', 1022: 'Iron Boulder',
    1023: 'Iron Crown', 1025: 'Pecharunt',
}
WANTED = ('Idle', 'Walk', 'Attack', 'Hurt', 'Sleep', 'Faint')
OUR_FORMS = {10263: (999, '0001', 'Roaming'), 10268: (1008, '0001', 'Low Power Mode')}

LEADS = {
    'fork_sheets': {
        'repository': 'lxsmnsyc/SpriteCollab (branch master, path compact/<region>/<dex>/0000)',
        'kind': 'repacked SpriteCollab-format sheets (sheet.json + frames.bin + regular.png), built by the fork maintainer',
        'species': {
            514: 'regular coat; credit recorded as Discord id 298217315294773251, no name; dated 2026-09-26',
            516: 'regular coat; the sheet records no credit',
            520: 'regular coat; credit Pokejavi. (compact/EDITS.md: built from the artist zip sprite-0520.zip, credits.txt added by the maintainer)',
            558: 'regular coat; credit JFain (same note, sprite-0558.zip)',
            592: 'regular + female coats; credit Pokejavi.',
            616: 'regular coat; credit Pokejavi. (sprite-0616.zip)',
            626: 'regular coat; credit Pokejavi. per compact/EDITS.md (sprite-0626.zip); the sheet itself records no credit',
        },
        'why_pending': "The fork's own notes say these are 'not in the collection yet': they were never approved upstream, and the "
                       'CC BY-NC 4.0 credit was added by the fork maintainer, not shown by the artist.',
    },
    'sylvain_fork': {
        'repository': 'sylvainpolletvillard/SpriteCollab (master) sprite/0520: a full SpriteCollab-format folder for Tranquill',
        'species': {520: 'folder with AnimData.xml and 14 animation sheets; no credits.txt in the tree'},
    },
    'auto_chess': {
        'repository': 'keldaanCommunity/pokemonAutoChess app/public/src/assets/pokemons/<dex>.png + .json (texture atlases)',
        'kind': 'frames cut from PMD sheets and packed per Pokémon (anim names Attack, Hop, Hurt, Idle, Shoot, Sleep, Walk, 8 directions)',
        'species': {520: 'Tranquill', 558: 'Crustle', 868: 'Milcery', 870: 'Falinks', 874: 'Stonjourner', 883: 'Arctovish',
                    917: 'Tarountula', 929: 'Dolliv', 962: 'Bombirdier', 968: 'Orthworm', 999: 'Gimmighoul', 1014: 'Okidogi',
                    1025: 'Pecharunt'},
        'only_here': (520, 558, 1014),
        'why_pending': 'Code is GPL-3; the repository states no licence or artist for these atlases (pokemons/credits.json has no '
                       'entry for them). Frame timings are not stored.',
    },
    'pokebattle': {
        'repository': 'GustavoGFV/PokeBattle src/data/PokemonSprites/Bouffalant',
        'species': {626: 'AnimData.xml (13 animations) but only Walk, Attack, Faint sheets and a portrait; no artist named'},
    },
    'darkusshadow': {
        'repository': 'https://www.deviantart.com/darkusshadow (pages 967776690 and 967779547, published 2023-06-18)',
        'kind': 'ALTERNATIVE_STYLE: Paldea overworld sprite sheets, not PMD; download needs a DeviantArt login',
        'species': [917, 929, 931, 943, 944, 945, 947, 948, 949, 950, 954, 956, 962, 973],
        'note': "Named in the pages' credits text. Maschiff and every DLC species are not named. The artist says 'free for everyone "
                "to use in their Pokémon projects, please give credits'. The sheet files were not inspected.",
    },
}


def sh(*args, cwd=None):
    return subprocess.run(args, cwd=cwd, capture_output=True, check=False)


def ensure_clone(cache, name, url):
    path = os.path.join(cache, name)
    if not os.path.isdir(path):
        os.makedirs(cache, exist_ok=True)
        r = sh('git', 'clone', '-q', '--depth', '1', '--filter=blob:none', '--no-checkout', url, path)
        if r.returncode:
            raise RuntimeError(f'cannot clone {url}: {r.stderr.decode()[:200]}')
    return path


def tree(repo):
    return sh('git', '-C', repo, 'ls-tree', '-r', '--name-only', 'HEAD').stdout.decode().splitlines()


def blob(repo, path):
    return sh('git', '-C', repo, 'show', f'HEAD:{path}').stdout


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def parse_anim_data(xml):
    root = ET.fromstring(xml)
    anims = {a.findtext('Name'): a for a in root.iter('Anim')}

    def resolve(name, seen=()):
        a = anims.get(name)
        if a is not None and a.findtext('CopyOf') and name not in seen:
            return resolve(a.findtext('CopyOf'), seen + (name,))
        return name, a

    out = {}
    for name in anims:
        real, a = resolve(name)
        if a is None or a.findtext('FrameWidth') is None:
            out[name] = (real, None, None, [])
        else:
            out[name] = (real, int(a.findtext('FrameWidth')), int(a.findtext('FrameHeight')), [int(d.text) for d in a.iter('Duration')])
    return out


def sprite_folders(paths, dex):
    prefix = f'sprite/{dex:04d}/'
    folders = defaultdict(list)
    for p in paths:
        if p.startswith(prefix):
            rest = p[len(prefix):]
            folder, _, name = rest.rpartition('/')
            folders[folder].append(name)
    return {f: files for f, files in folders.items() if 'AnimData.xml' in files}


def inspect(repo, paths, dex):
    out = {}
    for folder, files in sorted(sprite_folders(paths, dex).items()):
        base = f'sprite/{dex:04d}/{folder + "/" if folder else ""}'
        xml = blob(repo, base + 'AnimData.xml')
        anims = parse_anim_data(xml)
        sheets = {n[:-9] for n in files if n.endswith('-Anim.png')}
        present = {a: (a in anims and anims[a][0] in sheets) for a in WANTED}
        out[folder] = {'animations': sorted(anims), 'with_sheets': sorted(sheets), 'wanted': present,
                       'copy_of': {n: v[0] for n, v in anims.items() if v[0] != n}, 'anim_data_sha256': sha256(xml),
                       'path': base}
    return out


def local_sheets(fid):
    out = []
    for sub in ('', 'sleep', 'hop', 'eat', 'walk-down', 'walk-side', 'walk-up'):
        for suffix in ('', '-shiny'):
            p = os.path.join(ASSETS, sub, f'{fid}{suffix}.png')
            if os.path.exists(p):
                out.append({'path': os.path.relpath(p, ROOT), 'sha256': sha256(open(p, 'rb').read())})
    return out


def duplicates():
    seen = defaultdict(list)
    for dirpath, _, names in os.walk(ASSETS):
        for n in names:
            if n.endswith('.png'):
                p = os.path.join(dirpath, n)
                seen[sha256(open(p, 'rb').read())].append(os.path.relpath(p, ROOT))
    return [v for v in seen.values() if len(v) > 1]


def classify(dex, official, in_assets):
    root = official.get('')
    pending = dex in LEADS['fork_sheets']['species'] or dex in LEADS['auto_chess']['only_here']
    if root:
        wanted = root['wanted']
        need = ('Idle', 'Walk', 'Attack', 'Hurt', 'Sleep')
        return ('complete_pmd' if all(wanted[a] for a in need) else 'partial_pmd'), ''
    if official:
        return 'partial_pmd', 'only alternate forms are drawn upstream (no regular coat); the default form is not available'
    if pending:
        return 'permission_pending', 'exists only outside the official collection'
    return 'portrait_only', 'SpriteCollab has portraits but no character sprite folder'


def build(cache, quick=False):
    repos = {n: ensure_clone(cache, n.replace('/', '_'), url) for n, url in REPOS.items() if n in ('SpriteCollab', 'RawAsset')}
    sc = repos['SpriteCollab']
    paths = tree(sc)
    head = sh('git', '-C', sc, 'rev-parse', 'HEAD').stdout.decode().strip()
    pin = open(os.path.join(ROOT, 'tools', 'sprites', 'fetch_pmd_new.py'), encoding='utf-8').read()
    pinned = re.search(r"COMMIT = '([0-9a-f]{40})'", pin)[1]
    portraits = {int(r[0]) for r in csv.reader(open(os.path.join(ASSETS, 'portraits', 'portraits.tsv'), encoding='utf-8'), delimiter='\t') if r}
    ours = {int(r[0]) for r in csv.reader(open(os.path.join(ASSETS, 'pmd.tsv'), encoding='utf-8'), delimiter='\t') if r}
    records = []
    for dex, name in sorted(SPECIES.items()):
        official = inspect(sc, paths, dex)
        cls, notes = classify(dex, official, dex in ours)
        rec = {
            'national_dex': dex, 'species': name, 'asset_type': 'pmd_sprite', 'status': cls,
            'source_repository': REPOS['SpriteCollab'] if official else None,
            'source_url': f'{REPOS["SpriteCollab"]}/tree/master/sprite/{dex:04d}' if official else None,
            'contributor': None, 'license': 'CC BY-NC 4.0' if official else None,
            'permission_status': 'verified_cc_by_nc_4' if official else 'unknown',
            'animations': {a.lower(): bool(official.get('', {}).get('wanted', {}).get(a)) for a in WANTED},
            'forms': [], 'files': [], 'sha256': [], 'notes': notes, 'portrait_available': dex in portraits,
            'integrated_in_pokelife': dex in ours,
            'upstream_folders': {f or '(root)': {k: v for k, v in info.items() if k != 'path'} for f, info in official.items()},
        }
        if dex in ours:
            rec['files'] = local_sheets(dex)
            rec['sha256'] = [f['sha256'] for f in rec['files']]
        for fid, (d, folder, label) in OUR_FORMS.items():
            if d == dex and folder in official:
                info = official[folder]
                rec['forms'].append({'form_id': fid, 'name': label, 'folder': folder, 'animations': info['animations'],
                                     'wanted': info['wanted'], 'integrated_in_pokelife': fid in ours,
                                     'files': local_sheets(fid)})
        for folder, info in official.items():
            if folder not in ('', '0000/0001') and re.fullmatch(r'\d{4}', folder) and not any(f['folder'] == folder for f in rec['forms']):
                rec['forms'].append({'form_id': None, 'name': f'upstream form {folder}', 'folder': folder,
                                     'animations': info['animations'], 'wanted': info['wanted'], 'integrated_in_pokelife': False})
        for key in ('fork_sheets', 'sylvain_fork', 'auto_chess', 'pokebattle'):
            lead = LEADS[key]
            where = lead['species']
            if dex in where:
                rec.setdefault('other_sources', []).append({'repository': lead['repository'], 'detail': where[dex] if isinstance(where, dict) else where,
                                                            'files_inspected': key in ('fork_sheets', 'sylvain_fork', 'auto_chess')})
        if dex in LEADS['darkusshadow']['species']:
            rec.setdefault('other_sources', []).append({'repository': LEADS['darkusshadow']['repository'], 'detail': LEADS['darkusshadow']['kind'],
                                                        'files_inspected': False})
        records.append(rec)
    return {'generated_from': {'SpriteCollab_master': head, 'fetch_pmd_new_commit': pinned, 'repositories': REPOS},
            'classes': ['complete_pmd', 'partial_pmd', 'pmd_static', 'alternative_style', 'portrait_only', 'missing', 'permission_pending'],
            'leads': LEADS, 'duplicates_in_assets_pmd': duplicates(), 'records': records}


def report(m):
    count = defaultdict(list)
    for r in m['records']:
        count[r['status']].append(r['species'])
    lines = [f"{k}: {len(v)}  {', '.join(v)}" for k, v in sorted(count.items())]
    integrated = [r['species'] for r in m['records'] if r['integrated_in_pokelife']]
    lines.append(f'integrated in PokéLife: {len(integrated)}')
    return '\n'.join(lines)


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--cache', default=os.path.expanduser('~/.cache/pokelife-pmd-audit'))
    p.add_argument('--write', action='store_true', help=f'write {os.path.relpath(MANIFEST_OUT, ROOT)} (never overwrites without this)')
    p.add_argument('--out', default=MANIFEST_OUT)
    args = p.parse_args()
    m = build(args.cache)
    print(report(m))
    if args.write:
        os.makedirs(os.path.dirname(args.out), exist_ok=True)
        with open(args.out, 'w', encoding='utf-8') as fh:
            json.dump(m, fh, indent=2, ensure_ascii=False)
            fh.write('\n')
        print('wrote', os.path.relpath(args.out, ROOT))
    else:
        print('(dry run: pass --write to write the manifest)')


if __name__ == '__main__':
    main()

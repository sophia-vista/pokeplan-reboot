#!/usr/bin/env python3
import concurrent.futures
import csv
import os
import sys
import time
import urllib.error
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
CACHE = os.path.join(ROOT, 'tools/species/cache')
OUT = os.path.join(ROOT, 'assets/sprites')
SPRITES_COMMIT = '7a7d66b1620e646f69afd48dc1273da9033ede9c'
BASE = f'https://raw.githubusercontent.com/PokeAPI/sprites/{SPRITES_COMMIT}/sprites/pokemon/'
ALL_VARIANTS = [('front', '{id}.png', '{id}.png'), ('shiny', 'shiny/{id}.png', '{id}-shiny.png'),
                ('back', 'back/{id}.png', '{id}-back.png'), ('back-shiny', 'back/shiny/{id}.png', '{id}-back-shiny.png')]
VARIANTS = ALL_VARIANTS if '--with-back' in sys.argv else ALL_VARIANTS[:2]


def download(url, path):
    for attempt in range(6):
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                data = r.read()
            with open(path, 'wb') as fh:
                fh.write(data)
            return True
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return False
            if e.code not in (429, 500, 502, 503):
                raise
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(2 ** attempt)
    raise RuntimeError('giving up on ' + url)


def pokemon_rows():
    path = os.path.join(CACHE, 'pokemon.csv')
    if not os.path.exists(path):
        sys.exit('Run tools/species/generate.py first; it downloads the dataset into tools/species/cache.')
    with open(path, encoding='utf-8', newline='') as fh:
        rows = [(int(r['id']), r['identifier']) for r in csv.DictReader(fh)]
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'species', 'custom_forms.txt'), encoding='utf-8') as fh:
        rows += [(int(l.split('|')[0]), l.split('|')[1]) for l in fh if l.strip() and not l.startswith('#')]
    return rows


def fetch_one(pid):
    found = []
    for name, remote, local in VARIANTS:
        path = os.path.join(OUT, local.format(id=pid))
        if os.path.exists(path) and os.path.getsize(path) > 0:
            found.append(name)
        elif download(BASE + remote.format(id=pid), path):
            found.append(name)
    return pid, found


def main():
    os.makedirs(OUT, exist_ok=True)
    rows = pokemon_rows()
    if '--check' in sys.argv:
        missing = 0
        with open(os.path.join(OUT, 'manifest.tsv'), encoding='utf-8') as fh:
            for line in fh:
                pid, _, variants = line.rstrip('\n').split('\t')
                for name, _, local in VARIANTS:
                    if name in variants.split(',') and not os.path.exists(os.path.join(OUT, local.format(id=pid))):
                        print('missing', local.format(id=pid))
                        missing += 1
        sys.exit(1 if missing else 0)
    results = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
        for pid, found in pool.map(fetch_one, [pid for pid, _ in rows]):
            results[pid] = found
    with open(os.path.join(OUT, 'manifest.tsv'), 'w', encoding='utf-8') as fh:
        for pid, ident in rows:
            fh.write(f'{pid}\t{ident}\t{",".join(results[pid])}\n')
    total = sum(len(v) for v in results.values())
    no_front = [ident for pid, ident in rows if 'front' not in results[pid]]
    print(f'{len(rows)} forms, {total} sprites; no front sprite: {len(no_front)} {no_front[:15]}')


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Downloads item icons and type plates from the public PokeAPI/sprites repository into assets/items and assets/types.

Usage: python3 -I tools/sprites/fetch_items.py

The icons are the real game art: the app's bag items, plus the items used as tab icons. Files are named by the app's item
id (assets/items/<app id>.png); where the app's id is not a PokeAPI name (super-candy, link-cord, lures...) a mapping below
picks the closest real item. Writes assets/items/manifest.tsv (app id, PokeAPI name). Existing files are skipped.
"""
import csv
import os
import sys
import urllib.error
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
CACHE = os.path.join(ROOT, 'tools/species/cache')
OUT = os.path.join(ROOT, 'assets/items')
SPRITES_COMMIT = '7a7d66b1620e646f69afd48dc1273da9033ede9c'  # same pin as fetch.py
BASE = f'https://raw.githubusercontent.com/PokeAPI/sprites/{SPRITES_COMMIT}/sprites/items/'

TYPES = ['normal', 'fighting', 'flying', 'poison', 'ground', 'rock', 'bug', 'ghost', 'steel', 'fire', 'water', 'grass',
         'electric', 'psychic', 'ice', 'dragon', 'dark', 'fairy']

# App item id -> the real item whose icon stands for it.
MAPPED = {
    'super-candy': 'exp-candy-xl',
    'link-cord': 'linking-cord',
    'nature-mint': 'adamant-mint',
    # The five flavours, each as the berry that has it in the games.
    'berry-spicy': 'figy-berry', 'berry-dry': 'wiki-berry', 'berry-sweet': 'mago-berry',
    'berry-bitter': 'aguav-berry', 'berry-sour': 'iapapa-berry',
}
MAPPED.update({f'lure-{t}': f'{t}-gem' for t in TYPES})
MAPPED.update({f'tera-shard-{t}': f'{t}-tera-shard' for t in TYPES})

# Items whose id is already the PokeAPI name.
SAME = ['rare-candy', 'ability-capsule', 'ability-patch', 'bottle-cap', 'dynamax-band',
        'hp-up', 'protein', 'iron', 'calcium', 'zinc', 'carbos',
        'oval-charm', 'lucky-egg', 'star-piece', 'scope-lens', 'shiny-charm', 'leftovers', 'amulet-coin',
        # Tab icons: Home, Modules, Pokémon, Me; Plan's Planner and Focus.
        'town-map', 'explorer-kit', 'poke-ball', 'member-card', 'journal', 'mystery-egg',
        # Module cards: Care, its calm break and skills.
        'soothe-bell', 'lunar-wing', 'mental-herb',
        # Menu entries inside the tabs.
        'great-ball', 'storage-key', 'seal-bag', 'master-ball', 'oaks-letter', 'exp-share', 'heart-scale', 'gb-sounds',
        'oaks-parcel', 'timer-ball', 'premier-ball', 'friend-ball']


def read_csv(name):
    with open(os.path.join(CACHE, name), encoding='utf-8', newline='') as fh:
        return list(csv.DictReader(fh))


def main():
    if not os.path.exists(os.path.join(CACHE, 'items.csv')):
        sys.exit('Run tools/species/generate.py first; it downloads the dataset into tools/species/cache.')
    names = {r['id']: r['identifier'] for r in read_csv('items.csv')}
    wanted = {i: i for i in SAME}
    wanted.update(MAPPED)
    # Evolution items (stones and the like) come from the evolution data.
    for r in read_csv('pokemon_evolution.csv'):
        if r['trigger_item_id']:
            ident = names[r['trigger_item_id']]
            wanted[ident] = ident
    os.makedirs(OUT, exist_ok=True)
    missing = []
    for app_id, real in sorted(wanted.items()):
        path = os.path.join(OUT, app_id + '.png')
        if os.path.exists(path) and os.path.getsize(path) > 0:
            continue
        try:
            with urllib.request.urlopen(BASE + real + '.png', timeout=30) as r:
                data = r.read()
            with open(path, 'wb') as fh:
                fh.write(data)
        except urllib.error.HTTPError as e:
            if e.code != 404:
                raise
            missing.append(real)
    with open(os.path.join(OUT, 'manifest.tsv'), 'w', encoding='utf-8') as fh:
        for app_id, real in sorted(wanted.items()):
            if real not in missing:
                fh.write(f'{app_id}\t{real}\n')
    print(f'{len(wanted) - len(missing)} icons, missing: {missing}')


def fetch_types():
    """The 18 type name plates (Sword and Shield style) into assets/types/<type>.png."""
    out = os.path.join(ROOT, 'assets/types')
    os.makedirs(out, exist_ok=True)
    base = f'https://raw.githubusercontent.com/PokeAPI/sprites/{SPRITES_COMMIT}/sprites/types/generation-viii/sword-shield/'
    count = 0
    for r in read_csv('types.csv'):
        tid = int(r['id'])
        if tid > 18:
            continue
        path = os.path.join(out, r['identifier'] + '.png')
        if os.path.exists(path) and os.path.getsize(path) > 0:
            count += 1
            continue
        with urllib.request.urlopen(base + f'{tid}.png', timeout=30) as resp:
            data = resp.read()
        with open(path, 'wb') as fh:
            fh.write(data)
        count += 1
    print(f'{count} type plates')


if __name__ == '__main__':
    main()
    fetch_types()

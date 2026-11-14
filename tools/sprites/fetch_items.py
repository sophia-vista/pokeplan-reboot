#!/usr/bin/env python3
"""Downloads item icons and type plates from the public PokeAPI/sprites repository into assets/items and assets/types.

Usage: python3 -I tools/sprites/fetch_items.py

The icons are the real game art: the app's bag items, plus the items used as tab icons. Files are named by the app's item
id (assets/items/<app id>.png); where the app's id is not a PokeAPI name (super-candy, link-cord, lures...) a mapping below
picks the closest real item. Writes assets/items/manifest.tsv (app id, PokeAPI name). Existing files are skipped.

`python3 -I tools/sprites/fetch_items.py extras` fetches only the REP-053 extras (needs no species cache): every
`*-berry.png` and `*-mail.png` into assets/items under their PokeAPI names (listed in assets/items/berries-mail.tsv; the build
does not bundle them until a module uses them) and the Gym and Contest badges `sprites/badges/N.png` into assets/badges
(manifest.tsv there). A plain run does these too.
"""
import csv
import hashlib
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
    # The everyday candy is the Exp. Candy of the games (its id stays rare-candy: saves and the ledger name it).
    'rare-candy': 'exp-candy-m',
    'link-cord': 'linking-cord',
    'nature-mint': 'adamant-mint',
    # The five flavours, each as the berry that has it in the games.
    'berry-spicy': 'figy-berry', 'berry-dry': 'wiki-berry', 'berry-sweet': 'mago-berry',
    'berry-bitter': 'aguav-berry', 'berry-sour': 'iapapa-berry',
}
MAPPED.update({f'lure-{t}': f'{t}-gem' for t in TYPES})
MAPPED.update({f'tera-shard-{t}': f'{t}-tera-shard' for t in TYPES})

# Items whose id is already the PokeAPI name.
SAME = ['ability-capsule', 'ability-patch', 'bottle-cap', 'dynamax-band',
        'hp-up', 'protein', 'iron', 'calcium', 'zinc', 'carbos',
        'oval-charm', 'lucky-egg', 'star-piece', 'scope-lens', 'shiny-charm', 'leftovers', 'amulet-coin',
        # Tab icons: Home, Modules, Pokémon, Me; Plan's Planner and Focus.
        'town-map', 'explorer-kit', 'poke-ball', 'member-card', 'journal', 'mystery-egg',
        # Module cards: Care, its calm break and skills.
        'soothe-bell', 'lunar-wing', 'mental-herb',
        # Menu entries inside the tabs.
        'great-ball', 'storage-key', 'seal-bag', 'master-ball', 'oaks-letter', 'exp-share', 'heart-scale', 'gb-sounds',
        'oaks-parcel', 'timer-ball', 'premier-ball', 'friend-ball',
        # Module cards: Heal's Today, Activity and Hydration.
        'potion', 'x-speed', 'fresh-water',
        'everstone', 'oval-stone', 'razor-claw', 'razor-fang', 'strawberry-sweet', 'gimmighoul-coin']


BERRIES = """aguav apicot aspear babiri belue bluk charti cheri chesto chilan chople coba colbur cornn custap durin enigma figy
ganlon golden-nanab golden-pinap golden-razz grepa haban hondew hopo iapapa jaboca kasib kebia kee kelpsy lansat leppa liechi
lum mago magost maranga micle nanab nomel occa oran pamtre passho payapa pecha persim petaya pinap pomeg qualot rabuta rawst
razz rindo roseli rowap salac shuca silver-nanab silver-pinap silver-razz sitrus spelon starf tamato tanga wacan watmel wepear
wiki yache""".split()
MAILS = """air bead bloom brick bubble dream fab favored flame glitter grass greet harbor heart inquiry like mech mosaic orange
reply retro rsvp shadow snow space steel thanks tropic tunnel wave wood""".split()
BADGES = range(1, 78)  # sprites/badges/1.png .. 77.png at the pinned commit


def download(url, path):
    """Fetch url to path unless it is already there; return (sha256, bytes written now)."""
    if not (os.path.exists(path) and os.path.getsize(path) > 0):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with urllib.request.urlopen(url, timeout=30) as r:
            data = r.read()
        with open(path, 'wb') as fh:
            fh.write(data)
    with open(path, 'rb') as fh:
        return hashlib.sha256(fh.read()).hexdigest()


def fetch_extras():
    """Every Berry and Mail icon (assets/items) and the badges (assets/badges); each folder gets a tsv receipt."""
    rows = []
    for name in [f'{b}-berry' for b in BERRIES] + [f'{m}-mail' for m in MAILS]:
        sha = download(BASE + name + '.png', os.path.join(OUT, name + '.png'))
        rows.append(f'{name}\t{BASE}{name}.png\t{sha}')
    with open(os.path.join(OUT, 'berries-mail.tsv'), 'w', encoding='utf-8') as fh:
        fh.write(f'# PokeAPI/sprites @ {SPRITES_COMMIT}\nname\turl\tsha256\n' + '\n'.join(rows) + '\n')
    badge_base = BASE.replace('/items/', '/badges/')
    badges = []
    for n in BADGES:
        sha = download(f'{badge_base}{n}.png', os.path.join(ROOT, 'assets/badges', f'{n}.png'))
        badges.append(f'{n}\t{badge_base}{n}.png\t{sha}')
    with open(os.path.join(ROOT, 'assets/badges/manifest.tsv'), 'w', encoding='utf-8') as fh:
        fh.write(f'# PokeAPI/sprites @ {SPRITES_COMMIT}\nid\turl\tsha256\n' + '\n'.join(badges) + '\n')
    print(f'{len(rows)} berry and mail icons, {len(badges)} badges')


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
    if sys.argv[1:] == ['extras']:
        fetch_extras()
    else:
        main()
        fetch_types()
        fetch_extras()

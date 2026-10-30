#!/usr/bin/env python3
"""Downloads real Pokemon scene backgrounds (the battle backdrops of Pokemon Showdown) into assets/scenes/<name>.png.

Usage: python3 -I tools/sprites/fetch_scenes.py

These are the region scenes (Plan's town, Care's meadow) and the type scenes of the Pokédex entries (see BuddyScene in
app-core). Existing files are skipped, so it can be re-run.
"""
import os
import sys
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'assets/scenes')
# Pinned for reproducibility. To update: git ls-remote https://github.com/smogon/pokemon-showdown-client refs/heads/master
COMMIT = 'ab4b77b8f3a5712e8fbe03ce106ef2b70a874fdb'
BASE = f'https://raw.githubusercontent.com/smogon/pokemon-showdown-client/{COMMIT}/play.pokemonshowdown.com/fx/bg-'
SCENES = ['city', 'meadow', 'forest', 'route', 'mountain', 'volcanocave', 'beach', 'desert', 'thunderplains', 'icecave', 'dampcave']


def main():
    os.makedirs(OUT, exist_ok=True)
    for name in SCENES:
        path = os.path.join(OUT, f'{name}.png')
        if os.path.exists(path) and os.path.getsize(path) > 0:
            continue
        with urllib.request.urlopen(f'{BASE}{name}.png', timeout=30) as r, open(path, 'wb') as fh:
            fh.write(r.read())
    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print(f'{len(SCENES)} scenes, {size / 1e3:.0f} KB')


if __name__ == '__main__':
    sys.exit(main())

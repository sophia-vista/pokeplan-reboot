#!/usr/bin/env python3
"""Adds the PMD SpriteCollab sprites that were drawn after the commit `fetch_pmd.py` is pinned to, for a short list of forms,
without re-fetching (or changing) anything else.

Why a second script: SpriteCollab's `tracker.json` still marks most of these species as "no sprite" (sprite_complete 0) although
their folders hold approved animation sheets, and `fetch_pmd.py` trusts the tracker. Here the folders are the evidence: a form is
listed in FORMS below only after its `AnimData.xml` and sheets were inspected (docs/ASSETS/PMD_SPRITE_ACQUISITION.md).

Usage: python3 -I tools/sprites/fetch_pmd_new.py [--commit SHA] [--dry-run]

For each form it writes the same files as `fetch_pmd.py` (assets/pmd/<form id>.png and -shiny.png, the Sleep, Eat, Hop and Walk
rows into their folders, and the rows of pmd.tsv, sleep.tsv and so on) and adds the artists to credits.tsv. Existing rows of other
forms are kept; a form already in the tables is NOT overwritten unless --replace is given. Only the sprites' own pixels are copied.
SpriteCollab sprites are CC BY-NC 4.0: non-commercial use, with credit.
"""
import argparse
import csv
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import fetch_pmd as base  # noqa: E402  (its helpers; its main() is not run)

# The SpriteCollab master commit these forms were inspected at (2026-11-08, "Update credits.").
COMMIT = '709f2e32ae4b21d11628883fd2356bb2d5f75244'

# our form id -> (species folder, the form's own folder under sprite/<dex>/ or '' for the species' regular form, name)
# Shiny coats live in `<folder>/0000/0001` for the regular form and `<folder>/0001` for a numbered form (checked in the tree).
FORMS = {
    593: ('0593', '', 'Jellicent (male)'), 618: ('0618', '', 'Stunfisk'), 683: ('0683', '', 'Aromatisse'), 733: ('0733', '', 'Toucannon'),
    837: ('0837', '', 'Rolycoly'), 865: ('0865', '', "Sirfetch'd"), 868: ('0868', '', 'Milcery'), 874: ('0874', '', 'Stonjourner'),
    878: ('0878', '', 'Cufant'), 879: ('0879', '', 'Copperajah'), 883: ('0883', '', 'Arctovish'), 917: ('0917', '', 'Tarountula'),
    929: ('0929', '', 'Dolliv'), 945: ('0945', '', 'Grafaiai'), 948: ('0948', '', 'Toedscool'), 962: ('0962', '', 'Bombirdier'),
    968: ('0968', '', 'Orthworm'), 990: ('0990', '', 'Iron Treads'), 1003: ('1003', '', 'Ting-Lu'), 1020: ('1020', '', 'Gouging Fire'),
    1021: ('1021', '', 'Raging Bolt'), 1025: ('1025', '', 'Pecharunt'),
    10263: ('0999', '0001', 'Gimmighoul (Roaming)'), 10268: ('1008', '0001', 'Miraidon (Low Power Mode)'),
}


def read_table(sub):
    name = 'pmd' if sub == '' else sub
    path = os.path.join(base.OUT, sub, f'{name}.tsv')
    rows = {}
    if os.path.exists(path):
        with open(path, encoding='utf-8') as fh:
            for r in csv.reader(fh, delimiter='\t'):
                if r:
                    rows[int(r[0])] = (int(r[1]), int(r[2]), int(r[3]), [int(x) for x in r[4].split(',')], bool(int(r[5])))
    return rows


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--commit', default=COMMIT)
    p.add_argument('--dry-run', action='store_true')
    p.add_argument('--replace', action='store_true', help='overwrite forms that are already in the tables')
    args = p.parse_args()
    base.BASE = f'https://raw.githubusercontent.com/PMDCollab/SpriteCollab/{args.commit}/'
    tracker = json.loads(base.download(base.BASE + 'tracker.json'))
    names = {}
    for line in base.download(base.BASE + 'credit_names.txt').decode('utf-8').splitlines()[1:]:
        parts = line.split('\t')
        if len(parts) >= 2:
            names[parts[1]] = parts[0]
    for sub in base.ANIMS:
        os.makedirs(os.path.join(base.OUT, sub), exist_ok=True)
    tables = {sub: read_table(sub) for sub in base.ANIMS}
    credits = {}
    cpath = os.path.join(base.OUT, 'credits.tsv')
    if os.path.exists(cpath):
        with open(cpath, encoding='utf-8') as fh:
            for line in fh.read().splitlines():
                k, _, v = line.partition('\t')
                credits[int(k)] = v
    added = []
    for fid, (species, form, label) in sorted(FORMS.items()):
        if fid in tables[''] and not args.replace:
            print(f'{fid} {label}: already in the tables, kept (use --replace)')
            continue
        folder = f'{species}/{form}' if form else species
        shiny_folder = f'{folder}/0001' if form else f'{folder}/0000/0001'
        anims = base.anim_data(folder)
        if anims is None:
            print(f'{fid} {label}: no AnimData at {folder}, skipped', file=sys.stderr)
            continue
        shiny_anims = base.anim_data(shiny_folder)
        found = {}
        for sub, wanted in base.ANIMS.items():
            row = base.front_row(folder, anims, wanted, base.WALK.get(sub, 0))
            made = row and base.sheet(row[0])
            if not made:
                continue
            png, w, h, box = made
            shiny_row = shiny_anims and base.front_row(shiny_folder, shiny_anims, wanted, base.WALK.get(sub, 0))
            same = shiny_row and len(shiny_row[0]) == len(row[0]) and shiny_row[0][0].size == row[0][0].size
            shiny_made = same and base.sheet(shiny_row[0], box)
            if not args.dry_run:
                base.save(png, sub, fid)
                if shiny_made:
                    base.save(shiny_made[0], sub, f'{fid}-shiny')
            found[sub] = (len(row[0]), w, h, row[1], bool(shiny_made))
        if '' not in found:
            print(f'{fid} {label}: no Idle or Walk row, skipped', file=sys.stderr)
            continue
        node = tracker.get(species, {})
        if form:
            node = node.get('subgroups', {}).get(form, node)
        for sub, info in found.items():
            tables[sub][fid] = info
        artists = base.credit(node, names) if node.get('sprite_credit') else ''
        if artists:
            credits[fid] = artists
        added.append(fid)
        print(f'{fid} {label}: {", ".join(k or "idle" for k in found)}; shiny idle: {found[""][4]}; artists: {artists or "(none in the tracker)"}')
    if args.dry_run:
        return
    for sub, rows in tables.items():
        base.write_table(sub, rows)
    with open(cpath, 'w', encoding='utf-8') as fh:
        for fid in sorted(credits):
            fh.write(f'{fid}\t{credits[fid]}\n')
    print(f'{len(added)} forms added at SpriteCollab {args.commit[:7]}')


if __name__ == '__main__':
    main()

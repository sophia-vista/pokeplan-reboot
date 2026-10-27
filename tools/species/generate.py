#!/usr/bin/env python3
"""Generates the species data for :pokemon-core from the public PokeAPI CSV dataset.

Usage: python3 -I tools/species/generate.py [cache_dir]

Downloads the CSV files it needs (once) into the cache directory, then writes
pokemon-core/src/commonMain/kotlin/app/pokeplan/core/dex/generated/DexData.kt.
Only plain facts are written here. Rules (rarity, hatch pools) live in Kotlin
so they are tested and tunable.
"""
import collections
import csv
import os
import re
import sys
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
# Pinned to one commit so the generated data is reproducible (and CI does not break when upstream changes).
# To update: git ls-remote https://github.com/PokeAPI/pokeapi refs/heads/master, change the SHA, regenerate, review the diff.
POKEAPI_COMMIT = '6fd69dfe90465ba96534f6816fc817737d1f97f6'
BASE = f'https://raw.githubusercontent.com/PokeAPI/pokeapi/{POKEAPI_COMMIT}/data/v2/csv/'
FILES = ['pokemon', 'pokemon_species', 'pokemon_species_names', 'pokemon_stats', 'pokemon_types', 'types',
         'pokemon_abilities', 'abilities', 'ability_names', 'pokemon_evolution', 'pokemon_forms',
         'pokedexes', 'pokemon_dex_numbers', 'items', 'moves', 'locations', 'pokemon_species_flavor_text']
OUT = os.path.join(ROOT, 'pokemon-core/src/commonMain/kotlin/app/pokeplan/core/dex/generated/DexData.kt')
# Module regions, hand-curated (docs/region-curation-prompt.md): Region entry -> list file. A missing file is a region
# not curated yet, which gets no row (an empty dex). A species is in at most one module region.
MODULE_REGIONS = {'PLAN': 'plan', 'CARE': 'care', 'HOUSEHOLD': 'household'}
REGION_LIST_DIR = os.path.join(ROOT, 'tools/species/regions')
CHUNK = 28000
KIND_RANK = {'LEVEL': 0, 'STONE': 1, 'FRIENDSHIP': 2, 'LEVEL_TIME': 3, 'GENDER': 4, 'TRADE': 5, 'RANDOM': 6, 'TRIAL': 7}

# Regional dexes (PokeAPI pokedex ids) merged into one list per region, in order.
REGION_DEXES = {
    'KANTO': [2], 'JOHTO': [7], 'HOENN': [15], 'SINNOH': [6, 30], 'UNOVA': [9],
    'KALOS': [12, 13, 14], 'ALOLA': [21], 'GALAR': [27, 28, 29], 'PALDEA': [31, 32, 33],
}
TYPES18 = ['normal', 'fighting', 'flying', 'poison', 'ground', 'rock', 'bug', 'ghost', 'steel', 'fire',
           'water', 'grass', 'electric', 'psychic', 'ice', 'dragon', 'dark', 'fairy']


def fetch(cache):
    os.makedirs(cache, exist_ok=True)
    for f in FILES:
        path = os.path.join(cache, f + '.csv')
        if not os.path.exists(path) or os.path.getsize(path) == 0:
            print('downloading', f)
            urllib.request.urlretrieve(BASE + f + '.csv', path)


def rows(cache, f):
    with open(os.path.join(cache, f + '.csv'), encoding='utf-8', newline='') as fh:
        return list(csv.DictReader(fh))


def pretty(identifier):
    return ' '.join(w.capitalize() for w in identifier.split('-'))


def clean(text):
    assert '|' not in text and '\n' not in text and '"' not in text and '$' not in text, text
    return text


def form_kind(ident, is_default, battle_only, mega):
    if is_default:
        return 'DEFAULT'
    if mega or re.search(r'-(mega|mega-x|mega-y|primal)$', ident):
        return 'MEGA'
    if ident.endswith('-gmax') or ident.endswith('-eternamax'):
        return 'GMAX'
    if battle_only:
        return 'ALTERNATE'
    if re.search(r'-(alola|galar|hisui|paldea)$', ident) or ident.startswith('tauros-paldea-'):
        return 'REGIONAL'
    if ident.endswith('-female'):
        return 'GENDER'
    return 'ALTERNATE'


def main():
    cache = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'tools/species/cache')
    fetch(cache)
    R = lambda f: rows(cache, f)

    species = {int(r['id']): r for r in R('pokemon_species')}
    names = {int(r['pokemon_species_id']): r['name'] for r in R('pokemon_species_names') if r['local_language_id'] == '9'}
    genus = {int(r['pokemon_species_id']): r['genus'] for r in R('pokemon_species_names') if r['local_language_id'] == '9'}
    pokemon = {int(r['id']): r for r in R('pokemon')}
    forms_by_pokemon = collections.defaultdict(list)
    for r in R('pokemon_forms'):
        forms_by_pokemon[int(r['pokemon_id'])].append(r)
    form_to_pokemon = {int(r['id']): int(r['pokemon_id']) for r in R('pokemon_forms')}

    type_ident = {int(r['id']): r['identifier'] for r in R('types')}
    stats = collections.defaultdict(dict)
    for r in R('pokemon_stats'):
        stats[int(r['pokemon_id'])][int(r['stat_id'])] = int(r['base_stat'])
    types = collections.defaultdict(list)
    for r in sorted(R('pokemon_types'), key=lambda r: (int(r['pokemon_id']), int(r['slot']))):
        t = type_ident[int(r['type_id'])]
        if t in TYPES18:
            types[int(r['pokemon_id'])].append(t)
    ability_ident = {int(r['id']): r['identifier'] for r in R('abilities')}
    ability_name = {int(r['ability_id']): r['name'] for r in R('ability_names') if r['local_language_id'] == '9'}
    aname = lambda i: clean(ability_name.get(i) or pretty(ability_ident[i]))
    normal = collections.defaultdict(list)
    hidden = {}
    for r in sorted(R('pokemon_abilities'), key=lambda r: (int(r['pokemon_id']), int(r['slot']))):
        pid, aid = int(r['pokemon_id']), int(r['ability_id'])
        if r['is_hidden'] == '1':
            hidden[pid] = aid
        elif aid not in normal[pid]:
            normal[pid].append(aid)
    item_ident = {int(r['id']): r['identifier'] for r in R('items')}
    move_ident = {int(r['id']): r['identifier'] for r in R('moves')}
    loc_ident = {int(r['id']): r['identifier'] for r in R('locations')}

    # ---- species
    species_lines = []
    default_pokemon = {}
    for pid, r in pokemon.items():
        if r['is_default'] == '1':
            default_pokemon[int(r['species_id'])] = pid
    for sid in sorted(species):
        r = species[sid]
        dp = default_pokemon[sid]
        cosmetic = [f['form_identifier'] for f in forms_by_pokemon[dp]
                    if f['is_default'] == '0' and f['is_battle_only'] == '0' and f['is_mega'] == '0' and f['form_identifier']]
        gender = r['gender_rate']
        species_lines.append('|'.join([
            str(sid), r['identifier'], clean(names[sid]), r['generation_id'], r['evolves_from_species_id'] or '-',
            gender, r['is_baby'], r['is_legendary'], r['is_mythical'], ','.join(cosmetic) or '-',
            clean(genus.get(sid) or '') or '-', pokemon[dp]['height'], pokemon[dp]['weight']]))

    # ---- forms (every pokemon row)
    form_lines = []
    for pid in sorted(pokemon):
        r = pokemon[pid]
        sid = int(r['species_id'])
        ident = r['identifier']
        frs = forms_by_pokemon[pid]
        battle_only = bool(frs) and all(f['is_battle_only'] == '1' for f in frs)
        mega = any(f['is_mega'] == '1' for f in frs)
        kind = form_kind(ident, r['is_default'] == '1', battle_only, mega)
        sname = names[sid]
        sident = species[sid]['identifier']
        rest = ident[len(sident) + 1:] if ident.startswith(sident + '-') else ''
        if kind == 'DEFAULT':
            name = sname
        elif rest in ('alola', 'galar', 'hisui', 'paldea'):
            name = {'alola': 'Alolan', 'galar': 'Galarian', 'hisui': 'Hisuian', 'paldea': 'Paldean'}[rest] + ' ' + sname
        elif kind == 'MEGA':
            name = ('Primal ' if rest == 'primal' else 'Mega ') + sname + (' ' + rest[-1].upper() if rest in ('mega-x', 'mega-y') else '')
        elif kind == 'GMAX':
            name = ('Eternamax ' if rest == 'eternamax' else 'Gigantamax ') + sname
        else:
            name = f'{sname} ({pretty(rest) if rest else "alt"})'
        st = stats[pid]
        assert len(st) >= 6, ident
        statstr = ','.join(str(st[i]) for i in range(1, 7))
        abil = ','.join(aname(a) for a in normal[pid]) or '-'
        hid = aname(hidden[pid]) if pid in hidden else '-'
        form_lines.append('|'.join([str(pid), ident, str(sid), kind, clean(name),
                                    ','.join(types[pid]) or '-', statstr, abil, hid]))

    # ---- evolutions
    evo_best = {}
    for r in R('pokemon_evolution'):
        trig = int(r['evolution_trigger_id'])
        to_sid = int(r['evolved_species_id'])
        from_sid = int(species[to_sid]['evolves_from_species_id'])
        level = r['minimum_level'] or '-'
        item = item_ident.get(int(r['trigger_item_id'])) if r['trigger_item_id'] else None
        held = item_ident.get(int(r['held_item_id'])) if r['held_item_id'] else None
        tod = r['time_of_day'] or '-'
        odd_time = tod not in ('-', 'day', 'night', 'dusk')  # e.g. full-moon: not a time of day, so a trial
        gender = {'1': 'F', '2': 'M'}.get(r['gender_id'], '-')
        friend = r['minimum_happiness'] or '-'
        extras = []
        for col, label in [('location_id', 'location'), ('known_move_id', 'move'), ('known_move_type_id', 'move-type'),
                           ('minimum_beauty', 'beauty'), ('minimum_affection', 'affection'),
                           ('relative_physical_stats', 'physical-stats'), ('party_species_id', 'party-species'),
                           ('party_type_id', 'party-type'), ('trade_species_id', 'trade-species'),
                           ('needs_overworld_rain', 'rain'), ('turn_upside_down', 'upside-down'),
                           ('needs_multiplayer', 'multiplayer'), ('near_special_rock', 'special-rock'),
                           ('region_id', 'region'), ('used_move_id', 'used-move'), ('minimum_move_count', 'move-count'),
                           ('minimum_steps', 'steps'), ('minimum_damage_taken', 'damage'), ('nature_bitmask', 'nature'),
                           ('condition_expression', 'condition')]:
            v = r[col]
            if v and v != '0':
                if col == 'location_id':
                    v = loc_ident.get(int(v), v)
                elif col in ('known_move_id', 'used_move_id'):
                    v = move_ident.get(int(v), v)
                elif col in ('known_move_type_id', 'party_type_id'):
                    v = type_ident.get(int(v), v)
                extras.append(f'{label}={v}' if v != '1' else label)
        if odd_time:
            extras.append('time=' + tod)
            tod = '-'
        if r['percentage_chance']:
            kind = 'RANDOM'
        elif trig == 1:
            if extras or (held and True):
                kind = 'TRIAL'
            elif gender != '-':
                kind = 'GENDER'
            elif friend != '-':
                kind = 'FRIENDSHIP'
            elif tod != '-' and level != '-':
                kind = 'LEVEL_TIME'
            elif level != '-':
                kind = 'LEVEL'
            elif tod != '-':
                kind = 'LEVEL_TIME'
            else:
                kind = 'TRIAL'
        elif trig == 2:
            kind = 'TRADE'
        elif trig == 3:
            kind = 'STONE'
        else:
            kind = 'TRIAL'
            extras.insert(0, 'trigger=' + {4: 'shed', 5: 'spin', 6: 'tower-of-darkness', 7: 'tower-of-waters',
                                           8: 'three-critical-hits', 9: 'take-damage', 10: 'in-battle-level-up',
                                           11: 'agile-style-move', 12: 'strong-style-move', 13: 'recoil-damage',
                                           14: 'use-move', 15: 'three-defeated-bisharp', 16: 'gimmighoul-coins',
                                           17: 'meltan-candies'}.get(trig, 'other'))
        req_form = pokemon[form_to_pokemon[int(r['required_pokemon_form_id'])]]['identifier'] if r['required_pokemon_form_id'] else '-'
        to_form = pokemon[form_to_pokemon[int(r['evolved_pokemon_form_id'])]]['identifier'] if r['evolved_pokemon_form_id'] else '-'
        chance = r['percentage_chance'] or '-'
        key = (from_sid, to_sid, req_form, to_form)
        row = [str(from_sid), str(to_sid), kind, level, item or '-', held or '-', tod, gender,
               friend, req_form, to_form, chance, ';'.join(extras) or '-']
        # The dataset has one row per version / location; keep the simplest valid trigger per evolution.
        if key not in evo_best or KIND_RANK[kind] < KIND_RANK[evo_best[key][2]]:
            evo_best[key] = row
    evo_lines = ['|'.join(row) for row in evo_best.values()]

    # ---- regions
    dex_numbers = collections.defaultdict(list)
    for r in R('pokemon_dex_numbers'):
        dex_numbers[int(r['pokedex_id'])].append((int(r['pokedex_number']), int(r['species_id'])))
    region_lines = []
    for region, dexes in REGION_DEXES.items():
        seen, order = set(), []
        for d in dexes:
            for _, sid in sorted(dex_numbers[d]):
                if sid not in seen:
                    seen.add(sid)
                    order.append(sid)
        region_lines.append(region + '|' + ','.join(map(str, order)))
    # Module regions: the curated lists in tools/species/regions
    owner = {}
    for i, (region, file) in enumerate(MODULE_REGIONS.items()):
        path = os.path.join(REGION_LIST_DIR, file + '.txt')
        if not os.path.exists(path):
            continue
        ids = read_region_list(path, species, names)
        for sid in ids:
            if sid in owner:
                sys.exit(f'{path}: {names[sid]} is already in the {owner[sid]} region; a species is in at most one module region')
            owner[sid] = region
        region_lines.insert(i, region + '|' + ','.join(map(str, ids)))
    # Species whose Hisuian form is the one found in the Hisui dex (used to pick the variant in Sinnoh)
    region_lines.append('HISUI|' + ','.join(str(sid) for _, sid in sorted(dex_numbers[30])))

    # ---- Pokedex text: the most recent game's English entry for each species
    latest = {}
    for r in R('pokemon_species_flavor_text'):
        if r['language_id'] != '9':
            continue
        sid, version = int(r['species_id']), int(r['version_id'])
        if sid not in latest or version > latest[sid][0]:
            latest[sid] = (version, r['flavor_text'])
    entry_lines = []
    for sid in sorted(species):
        text = re.sub(r'\s+', ' ', latest[sid][1].replace('\u00ad', '')).strip()
        assert '|' not in text and '$' not in text and '"""' not in text, text
        entry_lines.append(f'{sid}|{text}')

    write(species_lines, form_lines, evo_lines, region_lines, entry_lines)
    print(len(species_lines), 'species,', len(form_lines), 'forms,', len(evo_lines), 'evolutions,', len(region_lines), 'regions')


def read_region_list(path, species, names):
    import difflib
    by_name = {v.lower(): k for k, v in names.items()}
    by_ident = {r['identifier']: k for k, r in species.items()}
    found, seen = [], set()
    with open(path, encoding='utf-8') as fh:
        for number, raw in enumerate(fh, 1):
            line = raw.strip()
            if not line or line.startswith('#'):
                continue
            sid = by_name.get(line.lower()) or by_ident.get(line.lower())
            if sid is None:
                close = difflib.get_close_matches(line.lower(), list(by_name) + list(by_ident), n=3)
                sys.exit(f'{path}:{number}: no species called "{line}". Did you mean: {", ".join(dict.fromkeys(close)) or "?"}')
            if sid in seen:
                sys.exit(f'{path}:{number}: "{line}" is listed twice')
            seen.add(sid)
            found.append(sid)
    if len(found) < 30:
        sys.exit(f'{path}: only {len(found)} species; need at least 30')
    special = [names[s] for s in found if species[s]['is_legendary'] == '1' or species[s]['is_mythical'] == '1']
    if special:
        print(f'note: legendary/mythical in {os.path.basename(path)} (quest rewards, not eggs):', ', '.join(special))
    return found


def chunks(lines):
    out, cur, size = [], [], 0
    for line in lines:
        if size + len(line) + 1 > CHUNK and cur:
            out.append(cur)
            cur, size = [], 0
        cur.append(line)
        size += len(line) + 1
    if cur:
        out.append(cur)
    return out


def write(species_lines, form_lines, evo_lines, region_lines, entry_lines):
    parts = ['// Generated by tools/species/generate.py from the PokeAPI CSV dataset. Do not edit by hand;',
             '// change the generator (or the rules in Kotlin) and run it again.',
             'package app.pokeplan.core.dex.generated', '']
    for name, lines in [('SPECIES', species_lines), ('FORMS', form_lines), ('EVOLUTIONS', evo_lines), ('REGIONS', region_lines), ('ENTRIES', entry_lines)]:
        cs = chunks(lines)
        parts.append(f'internal val DEX_{name}: List<String> = listOf(')
        for c in cs:
            parts.append('    """' + '\n'.join(c) + '\n""",')
        parts.append(')')
        parts.append('')
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(parts))


if __name__ == '__main__':
    main()

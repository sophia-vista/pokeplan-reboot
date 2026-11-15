#!/usr/bin/env python3
"""Fetch the Generation 3 (Hoenn) art the module places use, from pret/pokeemerald.

REP-053 (docs/pokelife/FEATURES/POKEMON_PLACES_IDEAS.md). Rule 4.0 of ASSETS_AND_AUDIO.md: a life module's rooms, furniture
and items are mainline art, mostly Generation 3. pret/pokeemerald commits the game's graphics and map data as plain files,
so no ROM is needed; everything comes through raw.githubusercontent.com at a pinned commit.

Output (`assets/hoenn/`):
  decorations/<name>.png       the 43 Secret Base decoration sprites (indexed PNG, palette inside), plus brendan.pal,
                               may.pal and decoration_menu.pal (the 16-colour JASC palettes of the player dolls and the menu)
  secret-base/primary/         the shared tileset: tiles.png (greyscale indices, drawn with palettes/NN.pal), unknown_tiles.png,
                               metatiles.bin, metatile_attributes.bin, palettes/00.pal .. 15.pal
  secret-base/secondary/       metatiles.bin and metatile_attributes.bin shared by the five rooms, and for each of blue_cave,
                               brown_cave, red_cave, yellow_cave, shrub and tree: tiles.png, unused_tiles.png, palettes/NN.pal
  secret-base/layouts/<Name>/  map.bin and border.bin of the 24 room layouts (six rooms, four sizes) (u16 metatile id | collision << 10 | elevation << 12
                               per cell), and secret-base/layouts.tsv (name, size, tilesets)
  berry-trees/<name>.png       the 32 Berry tree growth sheets (sprout, dirt pile and the 30 trees; six 16 x 32 frames side by side)
  manifest.tsv                 every file: path, source URL, sha256 (the receipt)

Existing files are skipped; downloads are cached in tools/sprites/cache-pret (git-ignored). Nintendo / Game Freak art, private
use only like the rest of assets/ (docs/assets.md).

Usage: python3 -I tools/sprites/fetch_pret.py

The PokeAPI berry and mail icons and the badges are fetched by `python3 -I tools/sprites/fetch_items.py extras`.
"""
import hashlib
import json
import sys
import urllib.request
from pathlib import Path

REV = "9103e529c2fbdd7add4a1ff3350c6c1f92112c42"  # pret/pokeemerald master on 14 November 2026, pinned
RAW = f"https://raw.githubusercontent.com/pret/pokeemerald/{REV}/"
HERE = Path(__file__).resolve().parent
OUT = HERE.parents[1] / "assets" / "hoenn"
CACHE = HERE / "cache-pret"

DECORATIONS = """attract_mat big_plant blastoise_doll blue_brick blue_tent breakable_door brick_desk camp_desk charizard_doll
colorful_plant comfort_desk fire_blast_mat fissure_mat glass_ornament gorgeous_plant hard_desk heavy_desk lapras_doll
powder_snow_mat pretty_desk pretty_flowers put_away_cursor ragged_desk red_brick red_plant red_tent regice_doll regirock_doll
registeel_doll rhydon_doll sand_ornament slide snorlax_doll solid_board spikes_mat stand surf_mat thunder_mat tire
tropical_plant venusaur_doll wailmer_doll yellow_brick""".split()
DECORATION_PALETTES = ["brendan", "may", "decoration_menu"]
BERRY_TREES = """aguav aspear cheri chesto cornn dirt_pile durin figy grepa hondew iapapa kelpsy lansat leppa liechi lum mago
nomel oran pamtre pecha persim pomeg rabuta rawst razz sitrus spelon sprout tamato wepear wiki""".split()
ROOMS = ["blue_cave", "brown_cave", "red_cave", "yellow_cave", "shrub", "tree"]
PALETTES = [f"palettes/{i:02d}.pal" for i in range(16)]


def get(rel):
    path = CACHE / rel.replace("/", "__")
    if not path.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        path.write_bytes(urllib.request.urlopen(urllib.request.Request(RAW + rel), timeout=60).read())
    return path.read_bytes()


def main():
    wanted = {}  # path under assets/hoenn -> repo path

    for name in DECORATIONS:
        wanted[f"decorations/{name}.png"] = f"graphics/decorations/{name}.png"
    for name in DECORATION_PALETTES:
        wanted[f"decorations/{name}.pal"] = f"graphics/decorations/{name}.pal"
    for name in BERRY_TREES:
        wanted[f"berry-trees/{name}.png"] = f"graphics/object_events/pics/berry_trees/{name}.png"

    primary = "data/tilesets/primary/secret_base/"
    for name in ["tiles.png", "unknown_tiles.png", "metatiles.bin", "metatile_attributes.bin"] + PALETTES:
        wanted[f"secret-base/primary/{name}"] = primary + name
    secondary = "data/tilesets/secondary/secret_base/"
    for name in ["metatiles.bin", "metatile_attributes.bin"]:
        wanted[f"secret-base/secondary/{name}"] = secondary + name
    for room in ROOMS:
        for name in ["tiles.png", "unused_tiles.png"] + PALETTES:
            wanted[f"secret-base/secondary/{room}/{name}"] = f"{secondary}{room}/{name}"

    layouts = [l for l in json.loads(get("data/layouts/layouts.json"))["layouts"]
               if l["name"].startswith("SecretBase_")]
    rows = ["name\twidth\theight\tprimary\tsecondary"]
    for l in layouts:
        folder = l["name"].removesuffix("_Layout")
        for kind in ("map", "border"):
            wanted[f"secret-base/layouts/{folder}/{kind}.bin"] = f"data/layouts/{folder}/{kind}.bin"
        rows.append(f"{folder}\t{l['width']}\t{l['height']}\t{l['primary_tileset']}\t{l['secondary_tileset']}")
    assert len(layouts) == 24, len(layouts)

    receipt, written = [], 0
    for rel, repo_path in sorted(wanted.items()):
        target = OUT / rel
        if not (target.exists() and target.stat().st_size > 0):
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(get(repo_path))
            written += 1
        receipt.append(f"{rel}\t{RAW}{repo_path}\t{hashlib.sha256(target.read_bytes()).hexdigest()}")
    (OUT / "secret-base").mkdir(parents=True, exist_ok=True)
    (OUT / "secret-base" / "layouts.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    (OUT / "manifest.tsv").write_text(
        f"# pret/pokeemerald @ {REV}\npath\turl\tsha256\n" + "\n".join(receipt) + "\n", encoding="utf-8")
    print(f"{len(wanted)} files in assets/hoenn ({written} downloaded now), {len(layouts)} room layouts")


if __name__ == "__main__":
    sys.exit(main())

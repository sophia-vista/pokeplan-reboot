#!/usr/bin/env python3
"""Render the ground maps of Pokémon Mystery Dungeon: Explorers of Sky (USA) from the ROM.

The ROM is not in the repo: pass one whose SHA-1 is 5fa96ca8d8dd6405d6cd2bad73ed68bc73a9d152 (the one
pret/pmd-sky builds as pmdsky.us.nds). The game lists its maps in MAP_BG/bg_list.dat (each with a palette
`bpl`, tile set `bpc`, layout `bma` and up to eight tile animations `bpa`); this reads that list and
renders every map with SkyTemple's file-format library, one PNG each: the layers composited, the first
palette frame, the first tile-animation frame, no collision or debug overlays.

Needs `pip install ndspy skytemple-files "pillow<11"` (SkyTemple's Rust palette code breaks with Pillow 11
and newer). Writes assets/sky-maps/<name>.png and maps.tsv (name, files, size, layers, animated).
Nintendo / Chunsoft art: private use only. Format reference: SkyTemple (GPL-3), used as a library here;
nothing of it is copied into the repo.

Usage: python3 tools/sprites/extract_sky_maps.py ROM.nds [--out DIR]
"""
import argparse
import hashlib
import sys
from pathlib import Path

import ndspy.rom
from PIL import Image
from skytemple_files.common.types.file_types import FileType

ROM_SHA1 = "5fa96ca8d8dd6405d6cd2bad73ed68bc73a9d152"
OUT = Path(__file__).resolve().parents[2] / "assets" / "sky-maps"


def main():
    p = argparse.ArgumentParser()
    p.add_argument("rom")
    p.add_argument("--out", default=str(OUT))
    args = p.parse_args()
    data = Path(args.rom).read_bytes()
    if hashlib.sha1(data).hexdigest() != ROM_SHA1:
        sys.exit("Not the Explorers of Sky (USA) ROM (SHA-1 mismatch).")
    rom = ndspy.rom.NintendoDSRom(data)
    get = lambda name: rom.getFileByName("MAP_BG/" + name.lower())  # the list is upper case, the files lower
    levels = FileType.BG_LIST_DAT.deserialize(get("bg_list.dat")).level
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    rows, skipped = [], []
    for level in levels:
        name = level.bma_name
        if name.startswith("S99"):  # the seizure warning and the ratings screen, not maps
            continue
        try:
            bpl = FileType.BPL.deserialize(get(level.bpl_name + ".bpl"))
            bpc = FileType.BPC.deserialize(get(level.bpc_name + ".bpc"))
            bma = FileType.BMA.deserialize(get(level.bma_name + ".bma"))
            bpas = []
            for n in level.bpa_names:
                bpas.append(FileType.BPA.deserialize(get(n + ".bpa")) if n and n != "NULL" else None)
            bpas += [None] * (8 - len(bpas))
            layers = bma.to_pil(bpc, bpl, bpas, False, False, False, single_frame=True)
            base = Image.new("RGBA", layers[0].size, (0, 0, 0, 255))
            for layer in layers:
                base = Image.alpha_composite(base, layer.convert("RGBA"))
            if base.getcolors(256) is not None:  # lossless: most maps have at most 256 colours, which a palette PNG stores in a third of the space
                base = base.convert("RGB").quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
            base.save(out / f"{name}.png", optimize=True)
            rows.append((name, level.bpl_name, level.bpc_name, level.bma_name, base.width, base.height, len(layers),
                         sum(1 for b in bpas if b)))
            print(name, base.size, flush=True)
        except Exception as e:  # a few entries are placeholders or unusual
            skipped.append((name, repr(e)))
            print("SKIP", name, repr(e)[:100], file=sys.stderr)
    with open(out / "maps.tsv", "w", encoding="utf-8") as f:
        f.write("name\tbpl\tbpc\tbma\twidth\theight\tlayers\tbpa_slots\n")
        for r in rows:
            f.write("\t".join(map(str, r)) + "\n")
    print(len(rows), "maps,", len(skipped), "skipped")


if __name__ == "__main__":
    main()

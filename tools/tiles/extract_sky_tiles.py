#!/usr/bin/env python3
"""Extract the dungeon tilesets of Pokémon Mystery Dungeon: Explorers of Sky (USA) from the ROM.

The ROM is not in the repo: pass one whose SHA-1 is 5fa96ca8d8dd6405d6cd2bad73ed68bc73a9d152
(pret/pmd-sky's pmdsky.us.nds). DUNGEON/dungeon.bin packs, per tileset N, `dungeonN.dpc` (chunks of 3 x 3
tiles), `.dpci` (the tile graphics), `.dpl` (palettes) and `.dma` (the autotile rules: for a wall, water or
floor tile and which of its eight neighbours are the same, three chunk variants). They are read with
SkyTemple's file-format library (`pip install ndspy skytemple-files "pillow<11"`).

For each tileset with rules (170 of the 199) it writes assets/tiles/ex-NNN.png in the layout of the other
atlases (tools/tiles/cut_tiles.py): 6 x 4 tiles of 24 x 24, the wall block (columns 0-2, rows 0-2), the
water block (columns 3-5), and the floor tile with its two alternates (row 3, columns 0-2). The chunks are
chosen by the game's own rules, so the corners, edges and middles are the game's. The first palette
frame is used (the water's sparkle colours are not animated). Nintendo / Chunsoft art: private use only.

Beside each it writes assets/tiles/ex-NNN-auto.png, the full autotile set the launcher's dungeon floor (REP-052) draws
walls and water of any shape with: 47 columns, one per way a square can touch its eight neighbours once the
corners that cannot show are dropped (a diagonal counts only when both squares beside it are the same; the order is
[blob_masks]), and six rows: the wall's three variants, then the water's three, each chunk the game's own pick for
that neighbourhood. The bits are N 1, NE 2, E 4, SE 8, S 16, SW 32, W 64, NW 128 (set: the neighbour is the same).

Usage: python3 tools/tiles/extract_sky_tiles.py ROM.nds [--sheets DIR]   (--sheets also saves every chunk of each tileset)
"""
import argparse
import hashlib
import sys
from pathlib import Path

import ndspy.rom
from PIL import Image
from skytemple_files.common.types.file_types import FileType
from skytemple_files.common.util import get_binary_from_rom, get_ppmdu_config_for_rom
from skytemple_files.hardcoded.dungeons import HardcodedDungeons
from skytemple_files.graphics.dma.protocol import DmaType
from skytemple_files.graphics.dma.util import get_tile_neighbors

ROM_SHA1 = "5fa96ca8d8dd6405d6cd2bad73ed68bc73a9d152"
OUT = Path(__file__).resolve().parents[2] / "assets" / "tiles"
TILE = 24

# Where each neighbour sits in a 3 x 3 grid around the middle square, by bit (see the docstring).
NEIGHBOURS = {1: (1, 0), 2: (2, 0), 4: (2, 1), 8: (2, 2), 16: (1, 2), 32: (0, 2), 64: (0, 1), 128: (0, 0)}


def blob_masks():
    """The 47 neighbourhoods an autotile can tell apart, ascending: a diagonal bit is kept only when both squares beside it are set."""
    def reduce(m):
        for d, a, b in ((2, 1, 4), (8, 4, 16), (32, 16, 64), (128, 64, 1)):
            if not (m & a and m & b):
                m &= ~d
        return m
    return sorted({reduce(m) for m in range(256)})


def auto_atlas(dma, chunk):
    """The full autotile set of a tileset: 47 columns ([blob_masks]) by six rows (wall variants 0-2, water variants 0-2)."""
    masks = blob_masks()
    out = Image.new("RGB", (TILE * len(masks), TILE * 6))
    for k, ctype in enumerate((DmaType.WALL, DmaType.WATER)):
        for col, m in enumerate(masks):
            grid = [[0] * 3 for _ in range(3)]
            grid[1][1] = 1
            for bit, (x, y) in NEIGHBOURS.items():
                if m & bit:
                    grid[y][x] = 1
            for v, idx in enumerate(dma.get(ctype, get_tile_neighbors(grid, 1, 1, True))[:3]):
                out.paste(chunk(idx), (col * TILE, (k * 3 + v) * TILE))
    return out


def main():
    p = argparse.ArgumentParser()
    p.add_argument("rom")
    p.add_argument("--sheets")
    args = p.parse_args()
    data = Path(args.rom).read_bytes()
    if hashlib.sha1(data).hexdigest() != ROM_SHA1:
        sys.exit("Not the Explorers of Sky (USA) ROM (SHA-1 mismatch).")
    rom = ndspy.rom.NintendoDSRom(data)
    cfg = get_ppmdu_config_for_rom(rom)
    pack = FileType.DUNGEON_BIN.deserialize(rom.getFileByName("DUNGEON/dungeon.bin"), cfg)
    OUT.mkdir(parents=True, exist_ok=True)
    rows, seen, alias = [], {}, {}
    for n in range(1, 199):
        try:
            dma = pack.get(f"dungeon{n}.dma")
            dpc, dpci, dpl = pack.get(f"dungeon{n}.dpc"), pack.get(f"dungeon{n}.dpci"), pack.get(f"dungeon{n}.dpl")
        except Exception:
            continue  # no autotile rules: not a floor tileset
        chunks = dpc.chunks_to_pil(dpci, dpl.palettes, 1).convert("RGB")

        def chunk(i):
            return chunks.crop((0, i * TILE, TILE, i * TILE + TILE))

        atlas = Image.new("RGB", (TILE * 6, TILE * 4))
        for kind, ctype, x0 in (("wall", DmaType.WALL, 0), ("water", DmaType.WATER, 3)):
            grid = [[0] * 5 for _ in range(5)]
            for y in range(1, 4):
                for x in range(1, 4):
                    grid[y][x] = 1
            for y in range(3):
                for x in range(3):
                    atlas.paste(chunk(dma.get(ctype, get_tile_neighbors(grid, x + 1, y + 1, True))[0]), ((x0 + x) * TILE, y * TILE))
        for v, idx in enumerate(dma.get(DmaType.FLOOR, 0xFF)):
            atlas.paste(chunk(idx), (v * TILE, 3 * TILE))
        digest = hashlib.sha1(atlas.tobytes()).hexdigest()
        if digest in seen:  # the game ships many placeholder copies of one tileset; keep the first
            alias[n] = seen[digest]
            print("tileset", n, "is the same as", seen[digest])
            (OUT / f"ex-{n:03d}.png").unlink(missing_ok=True)
            (OUT / f"ex-{n:03d}-auto.png").unlink(missing_ok=True)
            continue
        seen[digest] = n
        atlas.save(OUT / f"ex-{n:03d}.png", optimize=True)
        auto = auto_atlas(dma, chunk)
        # Lossless in a palette (a tileset has far fewer than 256 colours): a quarter of the size of an RGB file.
        if len(auto.getcolors(256) or []) > 0:
            auto = auto.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
        auto.save(OUT / f"ex-{n:03d}-auto.png", optimize=True)
        rows.append((n, len(dpl.palettes), chunks.height // TILE))
        if args.sheets:
            Path(args.sheets).mkdir(parents=True, exist_ok=True)
            cols = 16
            sheet = Image.new("RGB", (cols * TILE, ((chunks.height // TILE) + cols - 1) // cols * TILE))
            for i in range(chunks.height // TILE):
                sheet.paste(chunk(i), (i % cols * TILE, i // cols * TILE))
            sheet.save(Path(args.sheets) / f"ex-{n:03d}-chunks.png")
        print("tileset", n, flush=True)
    # Which dungeons use each tileset: the game's dungeon list (arm9) gives each dungeon's floors in mappa_s.bin,
    # and each floor layout names its tileset (ids of 170 and up are the pit and boss floors, which have none here).
    uses = {}
    mappa = FileType.MAPPA_BIN.deserialize(rom.getFileByName("BALANCE/mappa_s.bin"))
    names = {d.id: d.name for d in cfg.dungeon_data.dungeons}
    for did, d in enumerate(HardcodedDungeons.get_dungeon_list(get_binary_from_rom(rom, cfg.bin_sections.arm9), cfg)):
        if d.mappa_index >= len(mappa.floor_lists) or did == 0:
            continue
        for floor in mappa.floor_lists[d.mappa_index][d.start_after:d.start_after + d.number_floors]:
            ts = alias.get(floor.layout.tileset_id, floor.layout.tileset_id)
            if ts < 170 and did in names:
                uses.setdefault(ts, set()).add(names[did])
    with open(OUT / "ex-tilesets.tsv", "w", encoding="utf-8") as f:
        f.write("tileset\tpalettes\tchunks\tdungeons\n")
        for r in rows:
            f.write("\t".join(map(str, r)) + "\t" + "; ".join(sorted(uses.get(r[0], []))) + "\n")
    print(len(rows), "tilesets")


if __name__ == "__main__":
    main()

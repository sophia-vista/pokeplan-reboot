#!/usr/bin/env python3
"""Fetch and render the Pokémon Mystery Dungeon: Red Rescue Team ground maps.

pret/pmd-red commits the game's map data (`data/map_bg/*.bpl|bpc|bma|bpa`, extracted
from the ROM by the decompilation) and the table that names each map's files
(`src/map_files_table.c`). Both are reachable through raw.githubusercontent.com, so no
ROM is needed here. Every entry of the table is decoded to one PNG: the Friend Areas
(the 57 places of the Friend Area map), the towns, the guild and the other ground maps.

Output (`assets/rescue-maps/`):
  <MAP_FILE_ID>.png     the map with its first palette frame and first tile-animation frame
  maps.tsv              id, bpl, bpc, bma, width, height, layers, palette cycles, bpa slots
  sources.tsv           each downloaded file, its URL and sha256 (the receipt)

Format reference: pret/pmd-red `src/ground_bg.c` and `src/map_files_table.c`. This is an
independent decoder; it does not rip from a ROM. Pokémon Mystery Dungeon art belongs to
Nintendo / Chunsoft / The Pokémon Company: private use only (docs/assets.md).

Needs Pillow. Usage:  python3 -I tools/sprites/fetch_rescue_maps.py [--only FRIEND_AREA]
Downloads are cached in tools/sprites/cache-rescue-maps (git-ignored).
"""
import argparse
import hashlib
import re
import struct
import sys
import urllib.error
import urllib.request
from pathlib import Path

from PIL import Image

REV = "013475aa04f5be3191e5527c186d9bfceae7cae0"  # pret/pmd-red, pinned
RAW = f"https://raw.githubusercontent.com/pret/pmd-red/{REV}/"
HERE = Path(__file__).resolve().parent
OUT = HERE.parents[1] / "assets" / "rescue-maps"
CACHE = HERE / "cache-rescue-maps"


def fetch(rel):
    path = CACHE / rel.replace("/", "__")
    if not path.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        request = urllib.request.Request(RAW + rel)
        path.write_bytes(urllib.request.urlopen(request, timeout=60).read())
    return path.read_bytes()


def nibbles(raw):
    return bytes(v for b in raw for v in (b & 15, b >> 4))


def parse_table(text):
    """Every `[MAP_FILE_ID_X] = { .bplFileName ... }` entry: id -> (bpl, bpc, bma, [bpa])."""
    entries = {}
    pattern = re.compile(
        r"\[MAP_FILE_ID_(\w+)\]\s*=\s*\{\s*\.bplFileName\s*=\s*(\"\w+\"|NULL),\s*"
        r"\.bpcFileName\s*=\s*(\"\w+\"|NULL),\s*\.bmaFileName\s*=\s*(\"\w+\"|NULL),\s*"
        r"\.bpaFileNames\s*=\s*\{([^}]*)\}")
    for m in pattern.finditer(text):
        name = lambda s: None if s == "NULL" else s.strip('"')
        bpas = [name(x.strip()) for x in m[5].split(",") if x.strip()]
        entries[m[1]] = (name(m[2]), name(m[3]), name(m[4]), bpas)
    return entries


def palettes(bpl):
    """BPL: u16 banks, u16 animated; per bank 15 colours (RGBA bytes) after colour 0; then
    cycle specs (ticks, frames) per bank and the frames. Returns (banks, {bank: (ticks, frames)})."""
    count, animated = struct.unpack_from("<2H", bpl)

    def colours(offset):
        out = [(0, 0, 0, 255)]
        for p in range(offset, offset + 60, 4):
            r, g, b = (((v >> 3) << 3) | (v >> 5) for v in bpl[p:p + 3])
            out.append((r, g, b, 255))
        return out

    banks = [colours(4 + i * 60) for i in range(count)]
    cycles, offset = {}, 4 + count * 60
    if animated:
        specs = [struct.unpack_from("<2H", bpl, offset + i * 4) for i in range(count)]
        offset += count * 4
        for bank, (ticks, frames) in enumerate(specs):
            if frames:
                cycles[bank] = (ticks, [colours(offset + i * 60) for i in range(frames)])
                offset += frames * 60
    return banks, cycles


def chunk_rows(bma, pos, wc, hc):
    """One BMA tile-layer: run-length rows of 12-bit chunk ids, each row XORed with the row above."""
    rows, previous = [], [0] * ((wc + 1) // 2 * 2)
    for _ in range(hc):
        row = []
        while len(row) < wc:
            cmd = bma[pos]
            pos += 1
            if cmd >= 192:
                for _ in range(cmd - 191):
                    v = int.from_bytes(bma[pos:pos + 3], "little")
                    pos += 3
                    row.extend((v & 4095, v >> 12))
            elif cmd >= 128:
                v = int.from_bytes(bma[pos:pos + 3], "little")
                pos += 3
                row.extend((v & 4095, v >> 12) * (cmd - 127))
            else:
                row.extend([0, 0] * (cmd + 1))
        row = [v ^ previous[i] for i, v in enumerate(row)]
        rows.append(row[:wc])
        previous = row
    return rows


def render(bpl, bpc, bma, bpas):
    """Palette-index tile image composed from the BMA layers; returns (image, layer count)."""
    cw, ch, ntiles, *rest = struct.unpack_from("<8H", bpc)
    slots, nchunks = rest[:4], rest[4]
    static = [bytes(64)] + [nibbles(bpc[16 + i * 32:48 + i * 32]) for i in range(ntiles - 1)]
    off = 16 + (ntiles - 1) * 32
    chunks = [(0,) * (cw * ch)] + [
        struct.unpack_from("<" + "H" * (cw * ch), bpc, off + i * cw * ch * 2) for i in range(nchunks - 1)]
    wt, ht, tw, th, wc, hc, nl, _data, _collision = struct.unpack_from("<6B3H", bma)
    if (tw, th) != (cw, ch):
        raise ValueError("chunk size differs between BPC and BMA")
    pos, layers = 12, []
    for _ in range(nl):
        layers.append(chunk_rows(bma, pos, wc, hc))
        # advance past the layer: chunk_rows does not return pos, so re-walk it
        pos = skip_layer(bma, pos, wc, hc)
    tiles = list(static)
    for i, n in enumerate(slots):  # animated tile slots follow the static tiles; draw frame 0
        if not n:
            continue
        raw = bpas[i] if i < len(bpas) else None
        if raw:
            frames = struct.unpack_from("<h", raw, 2)[0]
            first = 4 + 4 * frames
            tiles += [nibbles(raw[first + t * 32:first + (t + 1) * 32]) for t in range(n)]
        else:
            tiles += [bytes(64)] * n
    image = Image.new("P", (wt * 8, ht * 8))
    layered = len(layers) > 1
    for rows in reversed(layers):  # layer 0 is on top
        for cy, row in enumerate(rows):
            for cx, chunk in enumerate(row):
                for t, entry in enumerate(chunks[chunk] if chunk < len(chunks) else ()):
                    idx = entry & 1023
                    raw = tiles[idx] if idx < len(tiles) else bytes(64)
                    tile = Image.frombytes("P", (8, 8), raw)
                    mask = Image.frombytes("L", (8, 8), bytes(255 if v else 0 for v in raw)) if layered else None
                    for flag, flip in ((1024, Image.Transpose.FLIP_LEFT_RIGHT), (2048, Image.Transpose.FLIP_TOP_BOTTOM)):
                        if entry & flag:
                            tile = tile.transpose(flip)
                            if mask:
                                mask = mask.transpose(flip)
                    bank = entry >> 12
                    tile = tile.point([v + bank * 16 if v else 0 for v in range(256)])
                    image.paste(tile, (cx * cw * 8 + t % cw * 8, cy * ch * 8 + t // cw * 8), mask)
    return image, nl


def skip_layer(bma, pos, wc, hc):
    for _ in range(hc):
        got = 0
        while got < wc:
            cmd = bma[pos]
            pos += 1
            if cmd >= 192:
                pos += 3 * (cmd - 191)
                got += 2 * (cmd - 191)
            elif cmd >= 128:
                pos += 3
                got += 2 * (cmd - 127)
            else:
                got += 2 * (cmd + 1)
    return pos


def to_rgba(indices, banks):
    flat = [c for bank in banks for c in bank]
    return Image.frombytes("RGBA", indices.size, bytes(v for i in indices.tobytes() for v in (flat[i] if i < len(flat) else (0, 0, 0, 255))))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", help="substring of a MAP_FILE_ID, e.g. FRIEND_AREA")
    args = parser.parse_args()
    table = parse_table(fetch("src/map_files_table.c").decode("utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)
    rows, sources, failed = [], {}, []
    for ident, (bpl_n, bpc_n, bma_n, bpa_n) in sorted(table.items()):
        if args.only and args.only not in ident:
            continue
        if not (bpl_n and bpc_n and bma_n):
            continue
        try:
            files = {}
            for kind, name in (("bpl", bpl_n), ("bpc", bpc_n), ("bma", bma_n)):
                rel = f"data/map_bg/{name}.{kind}"
                files[kind] = fetch(rel)
                sources[rel] = hashlib.sha256(files[kind]).hexdigest()
            bpas = []
            for name in bpa_n:
                if not name:
                    bpas.append(None)
                    continue
                rel = f"data/map_bg/{name}.bpa"
                raw = fetch(rel)
                sources[rel] = hashlib.sha256(raw).hexdigest()
                bpas.append(raw)
            banks, cycles = palettes(files["bpl"])
            for bank, (_ticks, frames) in cycles.items():
                banks[bank] = frames[0]  # the game loads frame 0 first
            indices, nl = render(files["bpl"], files["bpc"], files["bma"], bpas)
            to_rgba(indices, banks).save(OUT / f"{ident}.png", optimize=True)
            rows.append((ident, bpl_n, bpc_n, bma_n, indices.size[0], indices.size[1], nl, len(cycles),
                         sum(1 for b in bpas if b)))
            print(ident, indices.size, flush=True)
        except (ValueError, struct.error, IndexError, urllib.error.URLError, AssertionError) as e:
            failed.append((ident, repr(e)))
            print("SKIP", ident, repr(e), file=sys.stderr)
    with open(OUT / "maps.tsv", "w", encoding="utf-8") as f:
        f.write("id\tbpl\tbpc\tbma\twidth\theight\tlayers\tpalette_cycles\tbpa_slots\n")
        for r in rows:
            f.write("\t".join(map(str, r)) + "\n")
    with open(OUT / "sources.tsv", "w", encoding="utf-8") as f:
        f.write(f"# pret/pmd-red @ {REV}\nurl\tsha256\n")
        for rel, digest in sorted(sources.items()):
            f.write(f"{RAW}{rel}\t{digest}\n")
    print(f"{len(rows)} maps written, {len(failed)} skipped")
    for ident, why in failed:
        print("  skipped", ident, why)


if __name__ == "__main__":
    main()

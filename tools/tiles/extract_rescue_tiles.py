#!/usr/bin/env python3
import argparse
import hashlib
import re
import struct
import sys
from pathlib import Path

from PIL import Image

ROM_SHA1 = "9f4cfc5b5f4859d17169a485462e977c7aac2b89"
ARCHIVE = 0x3B0000
OUT = Path(__file__).resolve().parents[2] / "assets" / "tiles"
TILESET_FILE = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 16, 23, 24, 25, 20, 23, 28,
                29, 30, 31, 32, 33, 28, 35, 36, 37, 30, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54,
                55, 56, 45, 45, 59, 60, 48, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79]
TILE = 24


class Archive:
    def __init__(self, data):
        self.d = data
        self.files = {}
        count, table = struct.unpack_from("<II", data, ARCHIVE + 8)
        table &= 0x1FFFFFF
        for i in range(count):
            name, ptr = struct.unpack_from("<II", data, table + 8 * i)
            self.files[self.cstr(name)] = ptr & 0x1FFFFFF

    def cstr(self, ptr):
        ptr &= 0x1FFFFFF
        return self.d[ptr:self.d.index(b"\0", ptr)].decode("ascii")

    def get(self, name):
        o = self.files[name]
        return o + 0x10 if self.d[o:o + 4] in (b"SIRO", b"SIR0") else o


def decompress_at(d, o):
    src = d[o:o + 0x20000]
    length = src[5] + (src[6] << 8)
    if src[:4] == b"AT4P":
        total = src[0x10] + (src[0x11] << 8)
        i = 0x12
    elif src[:4] == b"AT3P":
        total, i = 0, 0x10
    else:
        raise ValueError("not an AT file")
    flags = [src[7 + k] + 3 for k in range(9)]
    out = bytearray()
    cmd_bit, current = 8, 0
    src = d[o:o + max(length, 0x20) + 8]
    while i < length:
        if total and len(out) >= total:
            break
        if cmd_bit == 8:
            current = src[i]
            i += 1
            cmd_bit = 0
        if (current & 0x80) == 0:
            command = (src[i] >> 4) + 3
            tmp = (src[i] & 0xF) << 8
            for k, special in enumerate((0x1F, 0x1E, 0x1D, 0x1C, 0x1B, 0x1A, 0x19, 0x18, 0x17)):
                if command == flags[k]:
                    command = special
                    break
            if command >= 0x17:
                c = src[i] & 0xF
                i += 1
                nib = lambda v: v & 0xF
                pairs = {
                    0x1F: (c, c, c, c), 0x1E: (c, c + 1, c + 1, c + 1), 0x1D: (c, c - 1, c, c),
                    0x1C: (c, c, c - 1, c), 0x1B: (c, c, c, c - 1), 0x1A: (c, c - 1, c - 1, c - 1),
                    0x19: (c, c + 1, c, c), 0x18: (c, c, c + 1, c), 0x17: (c, c, c, c + 1)}[command]
                out.append((nib(pairs[0]) << 4) | nib(pairs[1]))
                out.append((nib(pairs[2]) << 4) | nib(pairs[3]))
            else:
                tmp += src[i + 1]
                i += 2
                tmp += len(out) - 0x1000
                for _ in range(command):
                    out.append(out[tmp])
                    tmp += 1
        else:
            out.append(src[i])
            i += 1
        cmd_bit += 1
        current = (current << 1) & 0xFF
    return bytes(out)


def palette(arc, tileset):
    o = arc.get(f"b{tileset:02d}pal")
    colors = [tuple(arc.d[o + 4 * k:o + 4 * k + 3]) for k in range(160)]
    colors[0] = (0, 0, 0)
    try:
        ptr = arc.files[f"b{tileset:02d}canm"]
        table = struct.unpack_from("<I", arc.d, ptr + 4)[0] & 0x1FFFFFF
        for i in range(32):
            entry = struct.unpack_from("<I", arc.d, table + 4 * i)[0] & 0x1FFFFFF
            count = struct.unpack_from("<H", arc.d, entry)[0]
            colors.append(tuple(arc.d[entry + 4:entry + 7]) if count else (0, 0, 0))
    except (struct.error, ValueError):
        colors += [(0, 0, 0)] * 32
    return colors


def render_chunk(chunk, tiles, colors):
    img = Image.new("RGB", (TILE, TILE))
    for n, entry in enumerate(chunk):
        idx, hflip, vflip, bank = entry & 0x3FF, entry & 0x400, entry & 0x800, entry >> 12
        raw = tiles[idx * 32:idx * 32 + 32]
        pixels = [v for b in raw for v in (b & 15, b >> 4)]
        if len(pixels) < 64:
            pixels += [0] * (64 - len(pixels))
        tile = Image.new("RGB", (8, 8))
        for p, v in enumerate(pixels):
            tile.putpixel((p % 8, p // 8), colors[bank * 16 + v] if bank * 16 + v < len(colors) else (0, 0, 0))
        if hflip:
            tile = tile.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        if vflip:
            tile = tile.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        img.paste(tile, (n % 3 * 8, n // 3 * 8))
    return img


def mask(grid, x, y):
    def t(dx, dy):
        yy, xx = y + dy, x + dx
        return grid[yy][xx] if 0 <= yy < len(grid) and 0 <= xx < len(grid[0]) else 0
    around = [t(0, 1), t(1, 1), t(1, 0), t(1, -1), t(0, -1), t(-1, -1), t(-1, 0), t(-1, 1)]
    me = grid[y][x]
    if me in (1, 2, 3):
        r = 0xFF
        for bit, v in enumerate(around):
            if (v == 0) if me == 1 else (v != me):
                r &= ~(1 << bit)
        return (r & 0xFF) | {1: 0x200, 2: 0x100, 3: 0x100}[me]
    r = 0
    for bit, v in enumerate(around):
        if v == 0:
            r |= 1 << bit
    return r


def atlas(cex, chunks, tiles, colors):
    pick = lambda m, v=0: render_chunk(chunks[cex[v + m * 3]], tiles, colors)
    out = Image.new("RGB", (TILE * 6, TILE * 4))
    wall = [[1] * 5 for _ in range(5)]
    for y in range(1, 4):
        for x in range(1, 4):
            wall[y][x] = 0
    pond = [[1] * 5 for _ in range(5)]
    for y in range(1, 4):
        for x in range(1, 4):
            pond[y][x] = 2
    for r in range(3):
        for c in range(3):
            out.paste(pick(mask(wall, c + 1, r + 1)), (c * TILE, r * TILE))
            out.paste(pick(mask(pond, c + 1, r + 1)), ((c + 3) * TILE, r * TILE))
    ground = [[1] * 3 for _ in range(3)]
    for v in range(3):
        out.paste(pick(mask(ground, 1, 1), v), (v * TILE, 3 * TILE))
    return out


def dungeons(pret):
    uses = {}
    for path in sorted(Path(pret, "data/dungeon").glob("*/main_data.inc")):
        name = re.sub(r"(?<!^)(?=[A-Z])", " ", path.parent.name)
        for line in path.read_text().splitlines():
            m = re.match(r"\s*\.byte\s+(.*)", line)
            if m:
                row = [int(v, 16) for v in m[1].split(",")]
                if len(row) >= 3:
                    uses.setdefault(row[2], set()).add(name)
    return uses


def main():
    p = argparse.ArgumentParser()
    p.add_argument("rom")
    p.add_argument("--pret")
    p.add_argument("--sheets")
    args = p.parse_args()
    data = Path(args.rom).read_bytes()
    if hashlib.sha1(data).hexdigest() != ROM_SHA1:
        sys.exit("Not the Red Rescue Team (USA) 1.0 ROM (SHA-1 mismatch).")
    arc = Archive(data)
    uses = dungeons(args.pret) if args.pret else {}
    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    for ts in range(0x40):
        fid = TILESET_FILE[ts]
        try:
            tiles = decompress_at(data, arc.get(f"b{fid:02d}fon"))
            cel = decompress_at(data, arc.get(f"b{fid:02d}cel"))
            cex = decompress_at(data, arc.get(f"b{fid:02d}cex"))
            colors = palette(arc, ts)
        except (KeyError, ValueError, IndexError) as e:
            print("skip tileset", ts, repr(e))
            continue
        chunks = [struct.unpack_from("<9H", cel, 18 * i) for i in range(len(cel) // 18)]
        img = atlas(cex, chunks, tiles, colors)
        img.save(OUT / f"rr-{ts:02d}.png", optimize=True)
        rows.append((ts, fid, len(tiles) // 32, len(chunks), "; ".join(sorted(uses.get(ts, [])))))
        if args.sheets:
            Path(args.sheets).mkdir(parents=True, exist_ok=True)
            sheet = Image.new("RGB", (TILE * 25, TILE * 10))
            for n, ch in enumerate(chunks[:250]):
                sheet.paste(render_chunk(ch, tiles, colors), (n % 25 * TILE, n // 25 * TILE))
            sheet.save(Path(args.sheets) / f"rr-{ts:02d}-chunks.png")
        print("tileset", ts, "file", fid, len(tiles) // 32, "tiles", len(chunks), "chunks", flush=True)
    with open(OUT / "rr-tilesets.tsv", "w", encoding="utf-8") as f:
        f.write("tileset\tfile_id\ttiles\tchunks\tdungeons\n")
        for r in rows:
            f.write("\t".join(map(str, r)) + "\n")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Decodes a Red Rescue Team ground background (the .bpl palette, .bpc tiles and chunks, .bma map and .bpa tile animation files of
pret/pmd-red `data/map_bg`) into images, following the decompilation's own code (src/ground_bg.c: sub_80A2FBC, CopyBpcTilesToVram,
_UncompressCell, BmaLayerNrlDecompressor, sub_80A4764). Used to check the weather art PokéLife draws against the game's own data.

Usage: python3 -I tools/weather/decode_bg.py <pmd-red checkout> <out dir>
Writes, for each weather background, its first frame as <name>.png and the tile animation as a strip <name>-anim.png, and prints the header facts.
Needs Pillow.
"""
import os
import struct
import sys

from PIL import Image

# gMapFilesTable entries the weather kinds of src/ground_weather.c use (kind -> bpa files by slot), see docs/pokelife/evidence/WEATHER-original.md.
WEATHER_BGS = {
    'W01': [], 'W03P01': ['W03P011'], 'W03P02': ['W03P021'], 'W03P03': ['W03P031'], 'W04': ['W041'], 'W05': [], 'W06': [],
}


def u16s(b, off, n):
    return list(struct.unpack_from('<%dH' % n, b, off))


class Bg:
    def __init__(self, folder, name, bpa_names):
        read = lambda suffix: open(os.path.join(folder, 'data/map_bg', name + suffix), 'rb').read()
        bpl = read('.bpl')
        self.num_palettes, self.has_pal_anim = bpl[0], bpl[2]
        rgb = bpl[4:]
        # 15 colours of 4 bytes (r, g, b, unused) per palette; colour 0 is transparent.
        self.palettes = [[(0, 0, 0, 0)] + [tuple(rgb[p * 60 + i * 4: p * 60 + i * 4 + 3]) + (255,) for i in range(15)] for p in range(self.num_palettes)]
        bpc = read('c.bpc')
        h = u16s(bpc, 0, 8)
        self.chunk_w, self.chunk_h, self.num_tiles, self.slot_tiles, self.num_chunks = h[0], h[1], h[2], h[3:7], h[7]
        # Tile 0 is blank and not stored; then numTiles - 1 tiles of 32 bytes (4 bits a pixel); then the chunk definitions.
        off = 16
        self.tiles = [bytes(32)] + [bpc[off + 32 * i: off + 32 * (i + 1)] for i in range(self.num_tiles - 1)]
        off += 32 * (self.num_tiles - 1)
        n = self.chunk_w * self.chunk_h
        self.chunks = [[0] * n] + [u16s(bpc, off + 2 * n * i, n) for i in range(self.num_chunks - 1)]
        bma = read('m.bma')
        self.map_w_tiles, self.map_h_tiles, self.tiling_w, self.tiling_h, self.map_w, self.map_h, self.layers = bma[:7]
        self.chunk_map = self.nrl(bma, 12)
        self.bpas = []
        for bn in bpa_names:
            b = open(os.path.join(folder, 'data/map_bg', bn + '.bpa'), 'rb').read()
            per, frames = b[0], struct.unpack_from('<h', b, 2)[0]
            durations = list(struct.unpack_from('<%di' % frames, b, 4))
            data = 4 + 4 * frames
            self.bpas.append({'name': bn, 'tiles': per, 'frames': frames, 'durations': durations,
                              'data': [[b[data + 32 * (f * per + t): data + 32 * (f * per + t + 1)] for t in range(per)] for f in range(frames)]})

    def nrl(self, bma, src):
        """BmaLayerNrlDecompressor: rows of mapWidthChunks chunk numbers; the first row is plain, the others change the row above (xor), as the code does."""
        rows = []
        for j in range(self.map_h):
            above = rows[j - 1] if j else None
            row = []
            while len(row) < self.map_w:
                cmd = bma[src]
                src += 1
                if cmd >= 0xC0:
                    vals = []
                    for _ in range(cmd - 0xBF):
                        v = bma[src] | bma[src + 1] << 8 | bma[src + 2] << 16
                        src += 3
                        vals += [v & 0xFFF, (v >> 12) & 0xFFF]
                elif cmd >= 0x80:
                    v = bma[src] | bma[src + 1] << 8 | bma[src + 2] << 16
                    src += 3
                    vals = [v & 0xFFF, (v >> 12) & 0xFFF] * (cmd - 0x7F)
                else:
                    vals = None
                    count = (cmd + 1) * 2
                if vals is not None:
                    count = len(vals)
                for i in range(count):
                    p = len(row)
                    prev = above[p] if above is not None and p < len(above) else 0
                    if vals is None:
                        row.append(prev if above is not None else 0)
                    else:
                        row.append((prev ^ vals[i]) if above is not None else vals[i])
            rows.append(row[:self.map_w])
        return rows

    def tile_for(self, index, frames):
        """The 32 bytes of local tile [index]: static tiles, then the animated slots in order (frame [frames][slot] of each)."""
        if index < self.num_tiles:
            return self.tiles[index]
        index -= self.num_tiles
        for slot, count in enumerate(self.slot_tiles):
            if index < count:
                if slot < len(self.bpas):
                    bpa = self.bpas[slot]
                    return bpa['data'][frames[slot] % bpa['frames']][index]
                return bytes(32)
            index -= count
        return bytes(32)

    def render(self, frames=(0, 0)):
        w, h = self.map_w * self.chunk_w * 8, self.map_h * self.chunk_h * 8
        im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        px = im.load()
        for cy, row in enumerate(self.chunk_map):
            for cx, chunk in enumerate(row):
                entries = self.chunks[chunk] if chunk < len(self.chunks) else [0] * (self.chunk_w * self.chunk_h)
                for ty in range(self.chunk_h):
                    for tx in range(self.chunk_w):
                        e = entries[ty * self.chunk_w + tx]
                        data = self.tile_for(e & 0x3FF, frames)
                        pal = self.palettes[min((e >> 12) & 0xF, len(self.palettes) - 1)] if self.palettes else None
                        if pal is None or not any(data):
                            continue
                        for y in range(8):
                            for x in range(8):
                                byte = data[y * 4 + x // 2]
                                c = (byte >> 4) if x & 1 else (byte & 0xF)
                                if c == 0:
                                    continue
                                sx = 7 - x if e & 0x400 else x
                                sy = 7 - y if e & 0x800 else y
                                px[(cx * self.chunk_w + tx) * 8 + sx, (cy * self.chunk_h + ty) * 8 + sy] = pal[c]
        return im


def main():
    folder, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    for name, bpas in WEATHER_BGS.items():
        bg = Bg(folder, name, bpas)
        print(name, 'palettes', bg.num_palettes, 'palAnim', bg.has_pal_anim, 'chunk', bg.chunk_w, 'x', bg.chunk_h, 'static tiles', bg.num_tiles,
              'bpa slot tiles', bg.slot_tiles, 'chunks', bg.num_chunks, 'map chunks', bg.map_w, 'x', bg.map_h, '=', bg.map_w * bg.chunk_w * 8, 'x', bg.map_h * bg.chunk_h * 8, 'px',
              'bpa', [(b['name'], b['tiles'], b['frames'], sorted(set(b['durations']))) for b in bg.bpas])
        bg.render().save(os.path.join(out, name + '.png'))
        if bg.bpas:
            frames = bg.bpas[0]['frames']
            first = bg.render((0, 0))
            crop = (0, 0, min(first.width, 240), min(first.height, 160))
            strip = Image.new('RGBA', (240 * frames, 160), (0, 0, 0, 0))
            for f in range(frames):
                strip.paste(bg.render((f, f)).crop(crop), (240 * f, 0))
            strip.save(os.path.join(out, name + '-anim.png'))


if __name__ == '__main__':
    main()

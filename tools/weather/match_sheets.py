#!/usr/bin/env python3
"""Finds which of the game's weather layers each of PokéLife's snow and blizzard sheets is, by comparing the shape of every opaque pixel of a frame with the same-sized
window of every tile-animation frame of the layers decoded from the game's files (tools/weather/decode_bg.py).

Usage: python3 -I tools/weather/match_sheets.py <pmd-red checkout>
Prints, for frames 0 and 5 of each sheet, the layer, the animation frame and the offset of the exact match. Needs Pillow and numpy.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import decode_bg  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SHEETS = {'snow': (64, 32, 4), 'snow-dungeon': (32, 32, 4), 'blizzard': (32, 32, 4)}
LAYERS = {'W03P01': ['W03P011'], 'W03P02': ['W03P021'], 'W03P03': ['W03P031'], 'W04': ['W041']}


def main():
    folder = sys.argv[1]
    decoded = {}
    for name, bpas in LAYERS.items():
        bg = decode_bg.Bg(folder, name, bpas)
        decoded[name] = [np.array(bg.render((f, f)).crop((0, 0, 192, 192)))[:, :, 3] > 0 for f in range(16)]
    for sheet, (w, h, cols) in SHEETS.items():
        im = Image.open(os.path.join(ROOT, 'assets/weather', sheet + '.png'))
        for f in (0, 5):
            frame = np.array(im.crop(((f % cols) * w, (f // cols) * h, (f % cols + 1) * w, (f // cols + 1) * h)))[:, :, 3] > 0
            found = None
            for layer, frames in decoded.items():
                for g, mask in enumerate(frames):
                    for dy in range(0, 192 - h + 1):
                        for dx in range(0, 192 - w + 1):
                            if (mask[dy:dy + h, dx:dx + w] == frame).all():
                                found = (layer, g, dx, dy)
                                break
                        if found:
                            break
                    if found:
                        break
                if found:
                    break
            print(f'{sheet} frame {f}: {int(frame.sum())} opaque px -> layer/frame/x/y {found}')


if __name__ == '__main__':
    main()

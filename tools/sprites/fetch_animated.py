#!/usr/bin/env python3
"""Downloads the real animated Pokemon sprites (Black/White) from the public PokeAPI/sprites repository and turns each
GIF into a sprite sheet the app can play on every platform.

Usage: python3 -I tools/sprites/fetch_animated.py

Needs Pillow (pip install pillow) for the GIF to PNG step only; the app itself needs no new library.

For every Pokemon id that has an animated front sprite it writes assets/animated/<id>.png (all frames side by side,
left to right) and, when the shiny colours differ, <id>-shiny.png. assets/animated/animated.tsv lists
`id <TAB> frames <TAB> frame width <TAB> frame height <TAB> comma separated frame times in milliseconds <TAB> shiny (0 or 1)`.
Only the sprites are converted (the frames are the originals); nothing is drawn. Existing files are skipped.
Species above 649 have no Black/White animation, so the app shows their still sprite.
"""
import concurrent.futures
import io
import os
import sys
import time
import urllib.error
import urllib.request

from PIL import Image, ImageSequence

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'assets/animated')
# Pinned to the same commit as tools/sprites/fetch.py.
SPRITES_COMMIT = '7a7d66b1620e646f69afd48dc1273da9033ede9c'
BASE = f'https://raw.githubusercontent.com/PokeAPI/sprites/{SPRITES_COMMIT}/sprites/pokemon/versions/generation-v/black-white/animated/'
LAST_ID = 649


def download(url):
    """The bytes, or None if the sprite does not exist."""
    for attempt in range(6):
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if e.code not in (429, 500, 502, 503):
                raise
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(2 ** attempt)
    raise RuntimeError('giving up on ' + url)


def sheet(data):
    """(PNG bytes of all frames side by side, frame count, width, height, frame times in ms)."""
    gif = Image.open(io.BytesIO(data))
    frames, times = [], []
    for frame in ImageSequence.Iterator(gif):
        frames.append(frame.convert('RGBA'))
        # GIF times are in 10 ms steps; browsers treat anything under 20 ms as 100 ms, so do the same.
        ms = frame.info.get('duration', 100)
        times.append(100 if ms < 20 else ms)
    w, h = frames[0].size
    strip = Image.new('RGBA', (w * len(frames), h), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        strip.paste(f, (i * w, 0))
    # Sprites use a handful of colours: store them as an indexed PNG when that loses nothing (about a third of the size).
    colours = strip.getcolors(maxcolors=256)
    if colours is not None:
        strip = strip.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    out = io.BytesIO()
    strip.save(out, 'PNG', optimize=True)
    return out.getvalue(), len(frames), w, h, times


def fetch_one(pid):
    normal = os.path.join(OUT, f'{pid}.png')
    data = download(BASE + f'{pid}.gif')
    if data is None:
        return pid, None
    png, frames, w, h, times = sheet(data)
    with open(normal, 'wb') as fh:
        fh.write(png)
    shiny_data = download(BASE + f'shiny/{pid}.gif')
    has_shiny = shiny_data is not None and shiny_data != data
    if has_shiny:
        shiny_png = sheet(shiny_data)[0]
        with open(os.path.join(OUT, f'{pid}-shiny.png'), 'wb') as fh:
            fh.write(shiny_png)
    return pid, (frames, w, h, times, has_shiny)


def main():
    os.makedirs(OUT, exist_ok=True)
    results = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
        for pid, info in pool.map(fetch_one, range(1, LAST_ID + 1)):
            if info:
                results[pid] = info
    with open(os.path.join(OUT, 'animated.tsv'), 'w', encoding='utf-8') as fh:
        for pid in sorted(results):
            frames, w, h, times, shiny = results[pid]
            fh.write(f'{pid}\t{frames}\t{w}\t{h}\t{",".join(map(str, times))}\t{int(shiny)}\n')
    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print(f'{len(results)} animated sprites, {size / 1e6:.1f} MB')


if __name__ == '__main__':
    sys.exit(main())

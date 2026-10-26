#!/usr/bin/env python3
"""Downloads a few real game sound effects from hexparrot/pokemon-assets into assets/sfx/<name>.ogg.

Usage: python3 -I tools/sprites/fetch_sfx.py

Each one is converted to a small mono Ogg Vorbis file with ffmpeg, so Android and the web play it like the cries.
Existing files are skipped, so it can be re-run.
"""
import os
import subprocess
import sys
import tempfile
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'assets/sfx')
# Pinned for reproducibility, the same package as the display font. To update:
# git ls-remote https://github.com/hexparrot/pokemon-assets refs/heads/master
COMMIT = '2386d4308f6995d9539a5c15c1256c22025385e4'
BASE = f'https://raw.githubusercontent.com/hexparrot/pokemon-assets/{COMMIT}/assets/audio/'
# Our name -> the file in the package. Names match the Sfx enum in composeApp (Cries.kt).
SOUNDS = {
    'select': 'sfx/select.wav',
    'jump': 'sfx/jump.wav',
    'heal': 'sfx/heal.wav',
    'escape': 'sfx/escape.wav',
    'pokemon-center': 'music/pc-healing.ogg',
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, source in SOUNDS.items():
        path = os.path.join(OUT, f'{name}.ogg')
        if os.path.exists(path) and os.path.getsize(path) > 0:
            continue
        with tempfile.TemporaryDirectory() as tmp:
            raw = os.path.join(tmp, os.path.basename(source))
            with urllib.request.urlopen(BASE + source, timeout=30) as r, open(raw, 'wb') as fh:
                fh.write(r.read())
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', raw, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '4', path], check=True)
    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print(f'{len(SOUNDS)} sounds, {size / 1e3:.0f} KB')


if __name__ == '__main__':
    sys.exit(main())

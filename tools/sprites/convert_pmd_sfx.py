#!/usr/bin/env python3
"""Cuts the UI sounds from the Pokémon Mystery Dungeon: Red Rescue Team sound-effects dump into assets/sfx/<name>.ogg.

Usage: python3 -I tools/sprites/convert_pmd_sfx.py <folder with songNNN.mp3>

The dump is 478 unlabelled songNNN.mp3 files (not in the repo; the owner has the zip). SOUNDS maps our name (the Sfx enum in
composeApp, Cries.kt) to the song number: 301, 302, 303, 331, 200 to 204, 207, 332, 333, 337, 342, 344, 356, 406, 421, 455, 457, 466, 468, 470, 471, 473 and 516 were named by the owner, the rest were picked by their
length and pitch contour and may need swapping after a listen. Each becomes a small mono Ogg Vorbis file, peak-normalised
so the quiet ones are not lost. Needs ffmpeg and numpy.
"""
import glob
import os
import subprocess
import sys

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'assets/sfx')
SOUNDS = {
    'cursor': 301,  # cursor, switches and the letter scroller's tick
    'confirm': 302,
    'back': 303,
    'error': 331,
    'drawer-open': 330,  # rising
    'drawer-close': 337,  # fall
    'long-press': 354,
    'pin': 306,
    'unpin': 342,  # status down
    'hide': 333,  # escape
    'move-pickup': 307,
    'move-drop': 343,
    'uninstall-warn': 405,
    'focus-lock': 404,
    'habit-tick': 311,
    'focus-start': 414,
    'focus-end': 200,  # default success
    'egg-get': 478,
    'level-up': 201,  # shares the rank-up clip; 216 was a guess and is the dungeon wind sound (assets/pmd-sfx/README.md)
    'rank-up': 201,  # super success
    'candy': 332,  # money
    'item-get': 203,  # got something good
    'unlock': 204,  # unlocked something
    'calm-start': 353,
    # Emotes, for the buddy's reactions.
    'damage': 344,
    'surprise': 356,  # mild surprise
    'confusion-status': 406,
    'quick-escape': 421,
    'shock': 455,
    'urgent-shock': 457,
    'curious': 466,  # curious / suspicion
    'confused': 468,
    'sad': 470,  # "oh." (the teardrop)
    'mad': 471,
    'great-shock': 473,
    'eating': 516,  # a berry given to the buddy
    'calm-end': 202,  # mellow success
    'reminder': 207,
    'timer-done': 207,
}
PEAK = 0.7
MAX_GAIN = 4.0


def main(folder):
    os.makedirs(OUT, exist_ok=True)
    for name, number in SOUNDS.items():
        found = glob.glob(os.path.join(folder, '**', f'song{number}.mp3'), recursive=True)
        if not found:
            sys.exit(f'song{number}.mp3 not found in {folder}')
        raw = subprocess.run(['ffmpeg', '-v', 'quiet', '-i', found[0], '-f', 'f32le', '-ac', '1', '-ar', '22050', '-'],
                             capture_output=True, check=True).stdout
        peak = float(np.abs(np.frombuffer(raw, dtype=np.float32)).max())
        gain = min(MAX_GAIN, PEAK / peak) if peak > 0 else 1.0
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', found[0], '-ac', '1', '-ar', '22050', '-af', f'volume={gain:.3f}',
                        '-c:a', 'libvorbis', '-q:a', '3', os.path.join(OUT, f'{name}.ogg')], check=True)
        print(f'{name}: song{number}, gain {gain:.2f}')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])

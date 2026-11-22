#!/usr/bin/env python3
import glob
import os
import subprocess
import sys

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'assets/sfx')
SOUNDS = {
    'cursor': 301,
    'confirm': 302,
    'back': 303,
    'error': 331,
    'drawer-open': 330,
    'drawer-close': 337,
    'long-press': 354,
    'pin': 306,
    'unpin': 342,
    'hide': 333,
    'move-pickup': 307,
    'move-drop': 343,
    'uninstall-warn': 405,
    'focus-lock': 404,
    'habit-tick': 311,
    'focus-start': 414,
    'focus-end': 200,
    'egg-get': 478,
    'level-up': 201,
    'rank-up': 201,
    'candy': 332,
    'item-get': 203,
    'unlock': 204,
    'calm-start': 353,
    'damage': 344,
    'surprise': 356,
    'confusion-status': 406,
    'quick-escape': 421,
    'shock': 455,
    'urgent-shock': 457,
    'curious': 466,
    'confused': 468,
    'sad': 470,
    'mad': 471,
    'great-shock': 473,
    'eating': 516,
    'calm-end': 202,
    'reminder': 207,
    'timer-done': 207,
}
PEAK = 0.7
MAX_GAIN = 4.0

# The song ids the owner named by ear. Every other mapping was picked by the clip's length and pitch contour and still needs a
# listen before it is trusted (assets/sfx/provenance.tsv, written by every run, says which is which).
OWNER_NAMED = {
    301, 302, 303, 331, 200, 201, 202, 203, 204, 207, 332, 333, 337, 342, 344, 356, 406, 421, 455, 457, 466, 468, 470, 471, 473, 516,
}


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
    with open(os.path.join(OUT, 'provenance.tsv'), 'w', encoding='utf-8') as f:
        f.write('sound\tsong\tprovenance\n')
        for name, number in SOUNDS.items():
            f.write(f'{name}\t{number}\t{"owner-named" if number in OWNER_NAMED else "guessed"}\n')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])

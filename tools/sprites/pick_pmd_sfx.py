#!/usr/bin/env python3
import os
import subprocess
import sys

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SRC = os.path.join(ROOT, 'assets/pmd-sfx')
OUT = os.path.join(ROOT, 'assets/sfx')
PICKS = {'recruit': 206, 'stairs-up': 340, 'stairs-down': 341, 'learn': 312}
PEAK = 0.7
MAX_GAIN = 4.0


def main():
    for name, number in PICKS.items():
        src = os.path.join(SRC, f'{number}.ogg')
        raw = subprocess.run(['ffmpeg', '-v', 'quiet', '-i', src, '-f', 'f32le', '-ac', '1', '-ar', '22050', '-'], capture_output=True, check=True).stdout
        peak = float(np.abs(np.frombuffer(raw, dtype=np.float32)).max())
        gain = min(MAX_GAIN, PEAK / peak) if peak > 0 else 1.0
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', src, '-ac', '1', '-ar', '22050', '-af', f'volume={gain:.3f}', '-c:a', 'libvorbis', '-q:a', '3', os.path.join(OUT, f'{name}.ogg')], check=True)
        print(f'{name}: {number}, gain {gain:.2f}')


if __name__ == '__main__':
    sys.exit(main())

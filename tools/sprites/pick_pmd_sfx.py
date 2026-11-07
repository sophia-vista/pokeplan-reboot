#!/usr/bin/env python3
"""Copies a few Red Rescue Team sound effects from assets/pmd-sfx (the renders of the game's own sound ids) into assets/sfx/<name>.ogg.

Usage: python3 -I tools/sprites/pick_pmd_sfx.py

Each id is chosen for an event the game itself plays it for (assets/pmd-sfx/triggers.tsv, read from pret/pmd-red's source), not by guess:
  206  a monster joins the team (`MonsterJoinSequence_Async`, `HandleSpecialEntityJoinSequence`)      -> recruit: a Pokémon arrives or joins a party
  340  stairs, the ascending sound                                                                    -> stairs-up: a team comes back from an expedition
  341  stairs, the descending sound                                                                   -> stairs-down: a team goes out on an expedition
  312  a monster learns a new move (read by hand in dungeon_leveling.c)                               -> learn: a skill trained or practised
They are renders, an approximation of the GBA sound engine, and nobody has listened to them yet (assets/pmd-sfx/README.md): swap an id here if one sounds wrong.
Peak-normalised like the clips cut by convert_pmd_sfx.py. Needs ffmpeg and numpy.
"""
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

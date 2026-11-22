#!/usr/bin/env python3
import os
import re
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SRC = os.path.join(ROOT, 'assets/sky-sfx')
OUT = os.path.join(ROOT, 'assets/sfx')
TARGET_RMS_DB = -15.0
LIMIT_DB = -1.0
MAXLEN = 4.5

SOUNDS = {
    'story-rumble': 'jingle-rumbling-louder-sfx',
    'story-rumble-loud': 'jingle-rumbling-loudest-sfx',
    'story-wind': 'jingle-wind-sfx-2',
    'story-wind-high': 'jingle-wind-sfx-3',
    'story-flood': 'jingle-flood-sfx',
    'story-electricity': 'jingle-electricity-sfx-1',
    'story-electricity-long': 'jingle-electricity-sfx-2',
    'story-fire': 'jingle-fire-crackling-sfx-1',
    'story-hot-water': 'jingle-running-hot-water-sfx',
    'story-ominous': 'jingle-ominous-string-note-sfx',
    'story-ringing': 'jingle-single-tone-ringing-sfx',
    'story-vision': 'jingle-waterfall-cave-vision-sfx',
    'story-temporal': 'jingle-temporal-pinnacle-sfx',
    'story-humming': 'jingle-humming-sfx',
    'story-stoneship': 'jingle-rainbow-stoneship-sfx',
    'story-team-charm': 'jingle-team-charm-jingle',
    'story-beach-dusk': 'jingle-on-the-beach-at-dusk',
}


def run(args):
    return subprocess.run(args, capture_output=True, text=True)


def rms_db(path, filt=''):
    af = (filt + ',' if filt else '') + 'astats=metadata=0'
    out = run(['ffmpeg', '-nostats', '-i', path, '-af', af, '-f', 'null', '-']).stderr
    m = re.findall(r'RMS level dB:\s*(-?[\d.]+|-inf)', out)
    return float(m[-1]) if m and m[-1] != '-inf' else -60.0


def duration(path):
    return float(run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', path]).stdout.strip() or 0)


def main():
    if not os.path.isdir(SRC):
        sys.exit('assets/sky-sfx is missing')
    for name, clip in SOUNDS.items():
        src = os.path.join(SRC, clip + '.ogg')
        if not os.path.exists(src):
            print('missing', clip)
            continue
        length = min(duration(src), MAXLEN)
        fade = min(0.6, length / 3)
        cut = f'atrim=0:{length:.3f},afade=t=out:st={length - fade:.3f}:d={fade:.3f}'
        gain = TARGET_RMS_DB - rms_db(src, cut)
        gain = max(min(gain, 24.0), -6.0)
        dst = os.path.join(OUT, name + '.ogg')
        r = run(['ffmpeg', '-y', '-v', 'error', '-i', src, '-af', f'{cut},volume={gain:.2f}dB,alimiter=limit={10 ** (LIMIT_DB / 20):.3f}:level=disabled',
                 '-ac', '1', '-c:a', 'libvorbis', '-q:a', '3', dst])
        if r.returncode:
            print('failed', name, r.stderr[-200:])
            continue
        print(f'{name}: {length:.1f}s, +{gain:.1f} dB, RMS now {rms_db(dst):.1f} dB')


if __name__ == '__main__':
    main()

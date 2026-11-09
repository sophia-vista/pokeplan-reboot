#!/usr/bin/env python3
"""Writes the dungeon's weather colour ramps (the `colvec` file of pret/pmd-red, data/dungeon/colvec.inc) to assets/weather/ramps.tsv.

In the game every weather has a table of 256 entries for each of red, green and blue (src/weather.c, src/bg_palette_buffer.c): a colour of a
background palette goes through it per channel, `out = ramp[channel value] * brightness / 256` with the brightness at its full 31, as 5 bits.
On a change of weather the dungeon's ramp moves to the new table by at most 10 a frame (up to 64 frames: about a second).

Usage: python3 -I tools/weather/extract_ramps.py <pmd-red checkout>
Output: one line per weather and channel, `NAME<TAB>r|g|b<TAB>256 comma-separated values`. The weathers are the game's order (WeatherID).
"""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
NAMES = ['CLEAR', 'SUNNY', 'SANDSTORM', 'CLOUDY', 'RAIN', 'HAIL', 'FOG', 'SNOW']


def main():
    text = open(os.path.join(sys.argv[1], 'data/dungeon/colvec.inc'), encoding='utf-8').read()
    text = text[text.index('colvec:\n') + len('colvec:\n'):]
    data = bytes(int(x, 16) for x in re.findall(r'0x([0-9a-f]{2})', text))
    assert len(data) == 8 * 256 * 4, len(data)
    lines = []
    for w, name in enumerate(NAMES):
        for ch, c in enumerate('rgb'):
            lines.append(f'{name}\t{c}\t' + ','.join(str(data[(w * 256 + i) * 4 + ch]) for i in range(256)))
    with open(os.path.join(ROOT, 'assets/weather/ramps.tsv'), 'w', encoding='utf-8', newline='\n') as fh:
        fh.write('\n'.join(lines) + '\n')
    print('wrote', len(lines), 'ramps')


if __name__ == '__main__':
    main()

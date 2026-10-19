#!/usr/bin/env python3
"""Downloads the real Pokemon cries from the public PokeAPI/cries repository into assets/cries/<species id>.ogg.

Usage: python3 -I tools/sprites/fetch_cries.py

One cry per species (National Dex number 1 to 1025), the latest ones. Existing files are skipped, so it can be re-run.
"""
import concurrent.futures
import os
import sys
import time
import urllib.error
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'assets/cries')
# Pinned for reproducibility. To update: git ls-remote https://github.com/PokeAPI/cries refs/heads/main
CRIES_COMMIT = 'b3a9044bb839b5e3152aaa536c7b463a0fcf0140'
BASE = f'https://raw.githubusercontent.com/PokeAPI/cries/{CRIES_COMMIT}/cries/pokemon/latest/'
LAST_ID = 1025


def fetch_one(pid):
    path = os.path.join(OUT, f'{pid}.ogg')
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return pid, True
    for attempt in range(6):
        try:
            with urllib.request.urlopen(BASE + f'{pid}.ogg', timeout=30) as r:
                data = r.read()
            with open(path, 'wb') as fh:
                fh.write(data)
            return pid, True
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return pid, False
            if e.code not in (429, 500, 502, 503):
                raise
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(2 ** attempt)
    raise RuntimeError(f'giving up on {pid}')


def main():
    os.makedirs(OUT, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
        results = dict(pool.map(fetch_one, range(1, LAST_ID + 1)))
    missing = [pid for pid, ok in results.items() if not ok]
    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print(f'{len(results) - len(missing)} cries, {size / 1e6:.1f} MB; missing: {missing[:20]}')


if __name__ == '__main__':
    sys.exit(main())

#!/usr/bin/env python3
"""Write the `update.json` for a version's own release, with the patches that sit beside it.

The app reads the rolling "latest" release only to learn which version is newest, then reads this manifest from the
version's own release (`releases/download/vX.Y.Z/update.json`) because the builds the updater installs are the
versioned APKs, and the patches are made from those. A patch is named `patch-<from>-to-<to>.patch.gz`; its `fromBuild`
comes from the older release's own `update.json` (0 when that release predates it, in which case the app matches by
version only and checks the rebuilt APK's SHA-256 anyway).

Usage (the release workflow runs this):
    python3 tools/update/write_release_manifest.py --version 0.233.0 --build 350 \
        --commit abc1234 --date 2026-11-23 --apk versioned/pokeplan-v0.233.0.apk \
        --patches 'patches/*.patch.gz' --old-dir old --out versioned/update.json
"""
from __future__ import annotations

import argparse
import glob
import hashlib
import json
import os
import re
import sys

PATCH_NAME = re.compile(r"^patch-(\d+\.\d+\.\d+)-to-(\d+\.\d+\.\d+)\.patch\.gz$")


def sha256(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--version", required=True)
    parser.add_argument("--build", required=True, type=int)
    parser.add_argument("--commit", default="")
    parser.add_argument("--date", default="")
    parser.add_argument("--apk", required=True, help="the versioned APK this manifest describes")
    parser.add_argument("--patches", default="", help="glob of the patch files beside it")
    parser.add_argument("--old-dir", default="old", help="where the older releases' update.json files were downloaded")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    patches = []
    for path in sorted(glob.glob(args.patches)):
        match = PATCH_NAME.match(os.path.basename(path))
        if match is None or match.group(2) != args.version:
            continue
        from_version = match.group(1)
        from_build = 0
        old_manifest = os.path.join(args.old_dir, f"v{from_version}", "update.json")
        if os.path.isfile(old_manifest):
            try:
                from_build = int(json.load(open(old_manifest, encoding="utf-8")).get("build") or 0)
            except (ValueError, OSError):
                from_build = 0
        patches.append(
            {
                "from": from_version,
                "fromBuild": max(from_build, 0),
                "asset": os.path.basename(path),
                "size": os.path.getsize(path),
                "sha256": sha256(path),
            }
        )

    manifest = {
        "version": args.version,
        "build": args.build,
        "apk": os.path.basename(args.apk),
        "size": os.path.getsize(args.apk),
        "sha256": sha256(args.apk),
        "commit": args.commit,
        "date": args.date,
        "patches": patches,
    }
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(manifest, f, separators=(",", ":"))
        f.write("\n")
    print(f"{args.out}: v{args.version} build {args.build}, {len(patches)} patch(es)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

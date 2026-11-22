#!/usr/bin/env python3
"""Rebuild the tiny APK pair and patch the update-patch test applies.

The files are committed under `composeApp/src/jvmTest/resources/update/` so `UpdatePatchTest` can replay a patch made by
`tools/update/make_patch.py` (the two pieces must agree on the format). Run this after changing the format:

    python3 tools/update/make_fixture.py
"""
from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "composeApp", "src", "jvmTest", "resources", "update")


def entry(z: zipfile.ZipFile, name: str, data: bytes) -> None:
    z.writestr(name, data)


def main() -> int:
    os.makedirs(OUT, exist_ok=True)
    old_apk = os.path.join(OUT, "old.apk")
    new_apk = os.path.join(OUT, "new.apk")
    patch = os.path.join(OUT, "update.patch.gz")
    with zipfile.ZipFile(old_apk, "w", zipfile.ZIP_DEFLATED) as z:
        entry(z, "AndroidManifest.xml", b"<manifest>old</manifest>")
        entry(z, "classes.dex", b"dex " * 2000)
        entry(z, "assets/sprite-a.png", bytes(range(256)) * 40)
        entry(z, "assets/sprite-b.png", bytes(range(255, -1, -1)) * 40)
        entry(z, "assets/unchanged.png", b"same bytes" * 500)
    with zipfile.ZipFile(new_apk, "w", zipfile.ZIP_DEFLATED) as z:
        entry(z, "AndroidManifest.xml", b"<manifest>new</manifest>")
        entry(z, "classes.dex", b"dex " * 2000 + b"a new class")
        entry(z, "assets/sprite-a.png", bytes(range(256)) * 40)
        entry(z, "assets/sprite-b.png", bytes(range(255, -1, -1)) * 40 + b"changed")
        entry(z, "assets/unchanged.png", b"same bytes" * 500)
        entry(z, "assets/added.png", b"new asset" * 100)
    subprocess.run(
        [sys.executable, os.path.join(HERE, "make_patch.py"), "--from", old_apk, "--to", new_apk, "--out", patch],
        check=True,
    )
    print(f"fixture written to {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

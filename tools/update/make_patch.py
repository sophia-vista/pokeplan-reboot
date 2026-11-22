#!/usr/bin/env python3
"""Build a small update patch between two signed APKs of the same app.

The in-app updater downloads one of these patches instead of the whole ~116 MB APK: the patch reconstructs the new,
signed APK byte for byte from the copy installed on the phone, so no signing key ever leaves the build machine.

How it is small: an APK is a ZIP whose entries are stored one after another, and an update leaves almost every entry
(about 30,000 sprites, cries, maps, fonts) unchanged. The patch lists each unchanged entry as a COPY from the old APK
and ships only the changed entries (mostly the dex files), the APK signing block and the ZIP central directory as
literal bytes. That is about 4 to 20 MB instead of 116 MB. The stream is gzipped on top.

Patch format (after gunzip), big-endian:

    "POKEPATCH1\\n"        11-byte magic
    u64  target_size       size of the new APK
    32B  target_sha256     SHA-256 of the new APK
    u32  op_count
    then, in target order:
        0x01 COPY           u64 source_offset, u32 length   bytes from the old APK
        0x02 LITERAL        u32 length, length bytes        bytes carried in the patch

Usage:
    python3 tools/update/make_patch.py --from old.apk --to new.apk --out patch.patch.gz
    python3 tools/update/make_patch.py --from old.apk --to new.apk --out patch.patch.gz --quiet

Exit status is 0 only when the patch was written and replaying it reproduces the new APK exactly (size and SHA-256).
The updater applies the patch with `app.pokeplan.ui.updates.applyUpdatePatch` and checks the manifest's SHA-256 again
before it opens the installer, so a damaged patch or a different installed APK falls back to the full download.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import os
import struct
import sys
import zipfile

MAGIC = b"POKEPATCH1\n"
COPY = 0x01
LITERAL = 0x02


def local_segments(apk: str) -> list[tuple[str, int, int]]:
    """(name, local-header offset, local header + compressed data length) for every entry, in file order."""
    segments: list[tuple[str, int, int]] = []
    with open(apk, "rb") as f, zipfile.ZipFile(apk) as z:
        for entry in z.infolist():
            f.seek(entry.header_offset)
            header = f.read(30)
            if header[:4] != b"PK\x03\x04":
                raise SystemExit(f"{apk}: {entry.filename} does not start with a local file header")
            name_length, extra_length = struct.unpack("<HH", header[26:30])
            segments.append((entry.filename, entry.header_offset, 30 + name_length + extra_length + entry.compress_size))
    segments.sort(key=lambda s: s[1])
    return segments


def same_bytes(a: str, a_offset: int, b: str, b_offset: int, length: int) -> bool:
    with open(a, "rb") as fa, open(b, "rb") as fb:
        fa.seek(a_offset)
        fb.seek(b_offset)
        while length > 0:
            chunk = min(length, 1 << 20)
            if fa.read(chunk) != fb.read(chunk):
                return False
            length -= chunk
    return True


def build_ops(old: str, new: str) -> tuple[list[tuple], int, int]:
    """COPY/LITERAL ops in target order; returns (ops, literal bytes, copy ops)."""
    old_segments = {name: (offset, length) for name, offset, length in local_segments(old)}
    new_segments = local_segments(new)
    new_size = os.path.getsize(new)
    end_of_entries = max((offset + length for _, offset, length in new_segments), default=0)
    ops: list[tuple] = []
    literal = bytearray()
    copied = 0

    def flush_literal() -> None:
        nonlocal literal
        if literal:
            ops.append((LITERAL, bytes(literal)))
            literal = bytearray()

    with open(new, "rb") as fnew:
        position = 0
        for name, offset, length in new_segments:
            if offset > position:
                fnew.seek(position)
                literal += fnew.read(offset - position)
                flush_literal()
            fnew.seek(offset)
            new_bytes = fnew.read(length)
            old_segment = old_segments.get(name)
            if old_segment is not None and old_segment[1] == length and same_bytes(old, old_segment[0], new, offset, length):
                flush_literal()
                ops.append((COPY, old_segment[0], length))
                copied += length
            else:
                literal += new_bytes
                flush_literal()
            position = offset + length
        # The APK signing block, the central directory and the end record all change with the entries.
        if new_size > max(position, end_of_entries):
            fnew.seek(position if position >= end_of_entries else end_of_entries)
            literal += fnew.read(new_size - (position if position >= end_of_entries else end_of_entries))
            flush_literal()
    return ops, sum(len(op[1]) for op in ops if op[0] == LITERAL), copied


def write_patch(new: str, ops: list[tuple], out: str, level: int = 9) -> None:
    digest = hashlib.sha256()
    with open(new, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    with open(out, "wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, compresslevel=level) as f:
        f.write(MAGIC)
        f.write(struct.pack(">Q", os.path.getsize(new)))
        f.write(digest.digest())
        f.write(struct.pack(">I", len(ops)))
        for op in ops:
            if op[0] == COPY:
                f.write(bytes([COPY]) + struct.pack(">QI", op[1], op[2]))
            else:
                data = op[1]
                f.write(bytes([LITERAL]) + struct.pack(">I", len(data)) + data)


def replay_patch(old: str, patch: str) -> tuple[int, bytes]:
    """Apply the patch to the old APK; returns its size and SHA-256, for the self-check."""
    sha = hashlib.sha256()
    size = 0
    with gzip.open(patch, "rb") as f, open(old, "rb") as f_old:
        if f.read(len(MAGIC)) != MAGIC:
            raise SystemExit("the patch has no POKEPATCH1 header")
        target_size = struct.unpack(">Q", f.read(8))[0]
        f.read(32)  # target SHA-256, re-read below from the new APK
        count = struct.unpack(">I", f.read(4))[0]
        for _ in range(count):
            kind = f.read(1)
            if kind == bytes([COPY]):
                source_offset, length = struct.unpack(">QI", f.read(12))
                f_old.seek(source_offset)
                data = f_old.read(length)
                if len(data) != length:
                    raise SystemExit("the patch copies past the end of the old APK")
            elif kind == bytes([LITERAL]):
                (length,) = struct.unpack(">I", f.read(4))
                data = f.read(length)
                if len(data) != length:
                    raise SystemExit("the patch ends inside a literal")
            else:
                raise SystemExit(f"unknown patch op {kind!r}")
            sha.update(data)
            size += len(data)
        if size != target_size:
            raise SystemExit(f"the patch rebuilds {size} bytes, expected {target_size}")
    return size, sha.digest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--from", dest="old", required=True, help="the APK already on the phone")
    parser.add_argument("--to", dest="new", required=True, help="the signed APK the patch must rebuild")
    parser.add_argument("--out", required=True, help="where to write the .patch.gz")
    parser.add_argument("--quiet", action="store_true", help="only errors")
    args = parser.parse_args()

    for path in (args.old, args.new):
        if not os.path.isfile(path):
            raise SystemExit(f"no such file: {path}")
    ops, literal_bytes, copied_bytes = build_ops(args.old, args.new)
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    write_patch(args.new, ops, args.out)

    size, digest = replay_patch(args.old, args.out)
    with open(args.new, "rb") as f:
        target_sha = hashlib.sha256(f.read()).digest()
    if digest != target_sha:
        raise SystemExit("the patch does not reproduce the new APK")
    if not args.quiet:
        old_size = os.path.getsize(args.old)
        patch_size = os.path.getsize(args.out)
        print(
            f"{os.path.basename(args.old)} -> {os.path.basename(args.new)}: "
            f"{patch_size / 1e6:.2f} MB patch for a {os.path.getsize(args.new) / 1e6:.1f} MB APK "
            f"({len(ops)} ops, {copied_bytes / 1e6:.1f} MB copied, {literal_bytes / 1e6:.1f} MB carried; "
            f"old was {old_size / 1e6:.1f} MB); replay verified"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
import contextlib
import hashlib
import io
import struct
import sys
from pathlib import Path

import ndspy.rom
from PIL import Image
from skytemple_files.common.types.file_types import FileType
from skytemple_files.graphics.effect_wan import sheets
from skytemple_files.graphics.effect_wan.model import WanFile, mergeWithBasePalette

ROM_SHA1 = "5fa96ca8d8dd6405d6cd2bad73ed68bc73a9d152"
ROOT = Path(__file__).resolve().parents[2]
RIP = ROOT / "rip" / "sky" / "effects"
TILES = ROOT / "assets" / "tiles"

TRAPS = {"spin": 8, "gust": 7, "slow": 10}
TRAP_ANIMATION_INFO = 0xAFD0
EFFECT_ANIMATION_INFO = 0xFAAC
LONGEST_FRAME = 30
ANCHOR = (256, 512)


def load(pack, i):
    s = FileType.SIR0.deserialize(pack[i])
    w = WanFile()
    with contextlib.redirect_stdout(io.StringIO()):
        w.ImportWan(s.content, s.content_pointer_offsets, s.data_pointer)
    return w


def render(w, seq):
    if any(fr.frmIndex >= len(w.frameData) for fr in seq):
        return None
    box = (10000, 10000, -1, -1)
    for fr in seq:
        for cfg in w.frameData[fr.frmIndex]:
            r = sheets.GetPieceRect(w, cfg)
            if r:
                box = sheets.CombineExtents(box, r)
    if box[2] < 0:
        return None
    box = sheets.roundUpBox(box)
    size = (box[2] - box[0], box[3] - box[1])
    frames = [sheets.GenerateFrame(w, w.frameData[fr.frmIndex], box) or Image.new("RGBA", size) for fr in seq]
    return sheets.CombineFramesIntoAnim(frames), size, (ANCHOR[0] - box[0], ANCHOR[1] - box[1]), [fr.duration for fr in seq]


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    data = Path(sys.argv[1]).read_bytes()
    if hashlib.sha1(data).hexdigest() != ROM_SHA1:
        sys.exit("Not the Explorers of Sky (USA) ROM (SHA-1 mismatch).")
    rom = ndspy.rom.NintendoDSRom(data)
    pack = FileType.BIN_PACK.deserialize(rom.getFileByName("EFFECT/effect.bin"))
    base = load(pack, 292)
    RIP.mkdir(parents=True, exist_ok=True)
    rows = []
    rendered = {}
    for f in range(268):
        try:
            w = load(pack, f)
        except Exception:
            continue
        if getattr(w, "animData", None) is None or getattr(w, "imgType", 0) == 3:
            continue
        mergeWithBasePalette(w, base.customPalette)
        for a, seq in enumerate(w.animData):
            if not seq:
                continue
            try:
                got = render(w, seq)
            except Exception:
                continue
            if got is None:
                continue
            strip, size, origin, durations = got
            strip.save(RIP / f"fx-{f:03d}-{a:02d}.png", optimize=True)
            rendered[(f, a)] = got
            rows.append(f"{f}\t{a}\t{len(durations)}\t{size[0]}\t{size[1]}\t{origin[0]}\t{origin[1]}\t{','.join(map(str, durations))}")
    (RIP / "effects.tsv").write_text("file\tanimation\tframes\twidth\theight\torigin_x\torigin_y\tticks\n" + "\n".join(rows) + "\n", encoding="utf-8")

    overlay = rom.loadArm9Overlays([10])[10].data
    trap_rows = []
    for name, trap in TRAPS.items():
        anim = struct.unpack_from("<H", overlay, TRAP_ANIMATION_INFO + 2 * trap)[0]
        kind, file, _, animation, sound, _ = struct.unpack_from("<6i", overlay, EFFECT_ANIMATION_INFO + 28 * anim)
        strip, size, origin, durations = rendered[(file, animation)]
        strip.save(TILES / f"trap-{name}.png", optimize=True)
        ticks = [min(d, LONGEST_FRAME) for d in durations]
        trap_rows.append(f"{name}\t{file}\t{animation}\t{len(ticks)}\t{size[0]}\t{size[1]}\t{origin[0]}\t{origin[1]}\t{','.join(map(str, ticks))}\t{sound}")
    (TILES / "trap-fx.tsv").write_text("trap\tfile\tanimation\tframes\twidth\theight\torigin_x\torigin_y\tticks\tsound\n" + "\n".join(trap_rows) + "\n", encoding="utf-8")
    print(len(rows), "animations;", len(trap_rows), "trap effects")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Map Red Rescue Team sound ids (assets/pmd-sfx/NNN.ogg) to the game events that play them.

The decompilation pret/pmd-red names no sound effect: every call passes a bare number. So the
mapping is built from where the game actually uses each number:

  move      the per-move table `gUnknown_80BDBC4` in src/effect_data.c (`.soundEffectId`), plus the
            per-species overrides in `gUnknown_80C183C`; 997 (STOP_SOUND_EFFECT) means silent.
  menu      `sMenuSoundEffects` in src/music_util.c (MENU_SFX_ACCEPT, BACK, ...).
  call      every PlaySoundEffect / PlaySound / PlayFanfareSE / PlaySoundWithVolume (and the two
            dungeon helpers that take an id) with a literal id: the file and enclosing function.
  effect    the 448 effect animations in `gUnknown_80B9CC4` carry a sound id (`.unk10`, the 600s).
            Moves (`.unk2`, `.unk4`, `.unk6`), items (`gUnknown_80CE37C`) and traps
            (`sTrapEffectTable`) point at those animations, so each gets the sounds of its animation.
  script    the story scripts' FANFARE_PLAY / FANFARE_PLAY2 commands in src/data/ground/*: the
            script file (map code, e.g. a04p04) and the script array that plays it.

`confidence` is `table` when the game reads the id from a data table that names its event (move,
menu), and `call-site` when only a function or script name is known: those are leads for a human
label (the unnamed `sub_80xxxxxx` functions say nothing), not definitions. A sound id with no
row is one the decompilation never reaches with a literal (it may be played through a computed
id or from the ROM's own data); it is reported, not guessed.

Usage: python3 -I tools/sprites/build_sfx_catalog.py /path/to/pret/pmd-red
Writes assets/pmd-sfx/triggers.tsv (id, kind, event, source, confidence) and prints coverage.
"""
import collections
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SFX = HERE.parents[1] / "assets" / "pmd-sfx"
STOP = 997  # STOP_SOUND_EFFECT in include/constants/bg_music.h


def num(s):
    return STOP if s == "STOP_SOUND_EFFECT" else int(s, 0)


def moves(root):
    text = (root / "src/effect_data.c").read_text(errors="replace")
    start = text.index("gUnknown_80BDBC4[NUM_MOVE_IDS]")
    entry = re.compile(r"\[(MOVE_\w+)\]\s*=\s*\{(.*?)\},?\s*$", re.M)
    table = text[start:text.index("};", start)]
    mv = {}
    for m in entry.finditer(table):
        f = dict(re.findall(r"\.(\w+)\s*=\s*([\w-]+)", m[2]))
        mv[m[1]] = (num(f["soundEffectId"]), int(f["unk1C"]), int(f["unk20"]))
    s0 = text.index("gUnknown_80C183C[4336]")
    sp = {}
    for m in re.finditer(r"\[(\d+)\]\s*=\s*\{\.species\s*=\s*(\w+).*?\.soundEffectId\s*=\s*([\w-]+)\}", text[s0:]):
        sp[int(m[1])] = (m[2], num(m[3]))
    rows = []
    for name, (sid, count, first) in mv.items():
        label = name[5:].replace("_", " ").title()
        if sid != STOP:
            rows.append((sid, "move", f"{label}: the move's sound", "effect_data.c gUnknown_80BDBC4", "table"))
        users = collections.defaultdict(list)
        for i in range(first, first + count):
            if i in sp and sp[i][1] != STOP:
                users[sp[i][1]].append(sp[i][0][8:].title())
        for sid2, who in users.items():
            more = f" and {len(who) - 4} more" if len(who) > 4 else ""
            rows.append((sid2, "move", f"{label}: used by {', '.join(who[:4])}{more}",
                         "effect_data.c gUnknown_80C183C", "table"))
    return rows


def menu(root):
    text = (root / "src/music_util.c").read_text(errors="replace")
    body = text[text.index("sMenuSoundEffects"):]
    body = body[:body.index("};")]
    return [(int(n), "menu", f"Menu sound: {k[9:].lower()}", "music_util.c sMenuSoundEffects", "table")
            for k, n in re.findall(r"\[(MENU_SFX_\w+)\]\s*=\s*(\d+)", body)]


def effects(root):
    text = (root / "src/effect_data.c").read_text(errors="replace")
    a = text.index("gUnknown_80B9CC4[448]")
    sound = {}
    for m in re.finditer(r"\[(\d+)\]\s*=\s*\{[^}]*\.unk10\s*=\s*(-?\d+)", text[a:text.index("};", a)]):
        sound[int(m[1])] = int(m[2])
    rows = []

    def add(idx, kind, event, source):
        sid = sound.get(idx, -1)
        if sid >= 0 and sid != STOP:
            rows.append((sid, kind, f"{event} (effect animation #{idx})", source, "table"))

    b = text.index("gUnknown_80BDBC4[NUM_MOVE_IDS]")
    for m in re.finditer(r"\[(MOVE_\w+)\]\s*=\s*\{(.*?)\},?\s*$", text[b:text.index("};", b)], re.M):
        f = dict(re.findall(r"\.(\w+)\s*=\s*(-?\w+)", m[2]))
        label = m[1][5:].replace("_", " ").title()
        for field in ("unk2", "unk4", "unk6"):
            if int(f.get(field, 0)):
                add(int(f[field]), "move", f"Move {label}: animation", "effect_data.c gUnknown_80BDBC4")
    c = text.index("gUnknown_80CE37C[NUMBER_OF_ITEM_IDS]")
    for m in re.finditer(r"\[(ITEM_\w+)\s*\]\s*=\s*\{\s*\.unk0\s*=\s*(\d+),\s*\.unk2\s*=\s*(\d+)", text[c:text.index("};", c)]):
        label = m[1][5:].replace("_", " ").title()
        for k in (m[2], m[3]):
            if int(k):
                add(int(k), "item", f"Item {label}: use animation", "effect_data.c gUnknown_80CE37C")
    d = text.index("sTrapEffectTable[NUM_TRAPS]")
    for m in re.finditer(r"\[(TRAP_\w+)\s*\]\s*=\s*(\d+)", text[d:text.index("};", d)]):
        if int(m[2]):
            add(int(m[2]), "trap", f"Trap {m[1][5:].replace('_', ' ').title()}: triggered", "effect_data.c sTrapEffectTable")
    return rows


CALL = re.compile(r"\b(PlaySoundEffect|PlaySound|PlayFanfareSE|PlaySoundWithVolume|sub_80421C0|sub_80421EC)\s*\("
                  r"(?:[^,()]*,\s*)?(0x[0-9a-fA-F]+|\d+)\s*[,)]")


def enclosing(lines, i):
    for j in range(i, -1, -1):
        if re.match(r"^[A-Za-z_]", lines[j]) and "(" in lines[j] and not lines[j].rstrip().endswith(";"):
            m = re.search(r"(\w+)\s*\(", lines[j])
            return m[1] if m else "?"
    return "?"


def calls(root):
    rows = []
    for path in sorted((root / "src").rglob("*.c")):
        lines = path.read_text(errors="replace").splitlines()
        for i, line in enumerate(lines):
            m = CALL.search(line)
            if m:
                sid = int(m[2], 0)
                if sid != STOP:
                    rows.append((sid, "call", f"{path.name}: {enclosing(lines, i)}",
                                 f"{path.relative_to(root)}:{i + 1}", "call-site"))
    return rows


def scripts(root):
    rows = []
    for path in sorted((root / "src/data/ground").glob("*.h")):
        lines = path.read_text(errors="replace").splitlines()
        symbol = "?"
        for i, line in enumerate(lines):
            m = re.match(r"^(?:static\s+)?const\s+\w+\s+(\w+)\[\]", line)
            if m:
                symbol = m[1]
            m = re.search(r"FANFARE_PLAY2?\((\d+)\)", line)
            if m and int(m[1]) != STOP:
                rows.append((int(m[1]), "script", f"story script {path.stem.replace('ground_data_', '').replace('_station', '')}: {symbol}",
                             f"{path.relative_to(root)}:{i + 1}", "call-site"))
    return rows


def manual():
    """Ids the decompilation picks with a ternary or a small array, read from the code by hand."""
    src = "dungeon_8041AD0.c"
    rows = [(340, "dungeon", "Stairs: the ascending sound", f"{src} PlayStairsSound (IsStairDirectionUp)", "call-site"),
            (341, "dungeon", "Stairs: the descending sound", f"{src} PlayStairsSound", "call-site")]
    rows.append((312, "dungeon", "A monster learns a new move (both call sites in dungeon_leveling.c read: the sound plays "
                 "just before the 'learned' message, not on level-up)", "dungeon_leveling.c:612,679", "read"))
    for a0, ids in (("other weather/tileset wind", (0xD8, 0xD9, 0xD9, 0x173)), ("wind type 4", (0xDA, 0xDB, 0xDB, 0x174))):
        for stage, sid in enumerate(ids):
            rows.append((sid, "dungeon", f"Dungeon wind ({a0}), stage {stage}", f"{src} sub_80426C8, called from dungeon_wind.c",
                         "call-site"))
    return rows


def main():
    root = Path(sys.argv[1])
    rows = moves(root) + effects(root) + menu(root) + calls(root) + scripts(root) + manual()
    merged = collections.OrderedDict()
    for sid, kind, event, source, conf in sorted(rows, key=lambda r: (r[0], r[1], r[2])):
        key = (sid, kind, event)
        if key in merged:
            merged[key][0] += 1
        else:
            merged[key] = [1, source, conf]
    have = {int(p.stem) for p in SFX.glob("*.ogg")}
    with open(SFX / "triggers.tsv", "w", encoding="utf-8") as f:
        f.write("id\tkind\tevent\tsites\tfirst_source\tconfidence\n")
        for (sid, kind, event), (n, source, conf) in merged.items():
            f.write(f"{sid}\t{kind}\t{event}\t{n}\t{source}\t{conf}\n")
    mapped = {sid for sid, _, _ in merged}
    print(len(merged), "rows;", len(mapped & have), "of", len(have), "rendered sounds have a trigger;",
          len(mapped - have), "ids referenced but not rendered:", sorted(mapped - have)[:20])
    print("unmapped sound ids:", len(have - mapped))


if __name__ == "__main__":
    main()

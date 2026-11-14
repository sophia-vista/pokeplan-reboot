#!/usr/bin/env python3
"""Rip Pokémon Mystery Dungeon: Explorers of Sky (USA) into readable files, for reference and future work (owner, 14 November 2026).

The ROM is not in the repo: pass one whose SHA-1 is 5fa96ca8d8dd6405d6cd2bad73ed68bc73a9d152. Everything is decoded with
SkyTemple's file-format library (`pip install ndspy skytemple-files "pillow<11"`) into rip/sky/ (nothing there ships in the app):

- text/strings.tsv: every string of MESSAGE/text_e.str, with the name of the block it is in (Pokémon, moves, items, dungeons,
  menus, dialogue, help...);
- scripts/<map>/<name>.exps: every cutscene and ground script (SCRIPT/**.ssb) decompiled to ExplorerScript, the language
  SkyTemple edits them in; dialogue/<map>/<name>.txt: only what is said in each, in order, with who says it where the script
  names them; dialogue/index.tsv: every script with its number of lines (the main story is the m01a.. to m25a.. scripts);
- data/: the game's tables as TSV: Pokémon (BALANCE/monster.md), moves (waza_p.bin), items (item_p.bin), dungeon floors (mappa_s.bin);
- portraits/<NNNN>.png: each Pokémon's portraits (FONT/kaomado.kao), one 40 x 40 per emotion in a row, missing ones left empty;
- backgrounds/: the full-screen pictures (BACK, TOP, DUNGEON .bgp); textures/: the WTE textures (GROUND, FONT, DUNGEON).
The effects are rendered by tools/sprites/extract_sky_effects.py (rip/sky/effects), the ground maps by
tools/sprites/extract_sky_maps.py (assets/sky-maps), the dungeon tilesets by tools/tiles/extract_sky_tiles.py, the sound by
tools/sprites/extract_sky_audio.py. Not ripped: the Pokémon sprites (MONSTER/*.bin; the PMD SpriteCollab sprites the app uses are
these, redrawn and extended), the ground object sprites (GROUND/*.wan), music (approximate renders only, see extract_sky_audio.py).
Nintendo / Chunsoft art and text: private use only.

Usage: python3 tools/rip/rip_sky.py ROM.nds [--only text,scripts,story,data,portraits,backgrounds,textures] (story reads the dialogue scripts writes)
"""
import argparse
import contextlib
import hashlib
import io
import re
from enum import Enum
from pathlib import Path

import ndspy.rom
from PIL import Image
from skytemple_files.common.types.file_types import FileType
from skytemple_files.common.util import get_ppmdu_config_for_rom

ROM_SHA1 = "5fa96ca8d8dd6405d6cd2bad73ed68bc73a9d152"
OUT = Path(__file__).resolve().parents[2] / "rip" / "sky"
PARTS = ["text", "scripts", "story", "data", "portraits", "backgrounds", "textures"]


def files(rom, prefix=""):
    def walk(folder, at=""):
        for n in folder.files:
            yield at + n
        for n, f in folder.folders:
            yield from walk(f, at + n + "/")
    return [n for n in walk(rom.filenames) if n.startswith(prefix)]


def tsv(path, header, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    clean = lambda v: str(v.name if isinstance(v, Enum) else v).replace("\t", " ").replace("\n", "\\n")
    path.write_text("\t".join(header) + "\n" + "".join("\t".join(clean(v) for v in r) + "\n" for r in rows), encoding="utf-8")


def fields(obj):
    """The plain fields of a SkyTemple record (numbers, text, flags, enums), in a stable order."""
    out = {}
    for k, v in sorted(vars(obj).items()):
        if k.startswith("_"):
            continue
        if isinstance(v, (int, str, bool, float, Enum)):
            out[k] = v
        elif isinstance(v, (list, tuple)) and all(isinstance(x, (int, bool, str)) for x in v) and len(v) <= 32:
            out[k] = ",".join(map(str, v))
    return out


def rows_of(records, names=None):
    keys = []
    for r in records:
        for k in fields(r):
            if k not in keys:
                keys.append(k)
    header = (["index", "name"] if names else ["index"]) + keys
    rows = []
    for i, r in enumerate(records):
        f = fields(r)
        rows.append([i] + ([names(i)] if names else []) + [f.get(k, "") for k in keys])
    return header, rows


def strings(rom, cfg):
    text = FileType.STR.deserialize(rom.getFileByName("MESSAGE/text_e.str")).strings
    blocks = sorted(cfg.string_index_data.string_blocks.items(), key=lambda kv: kv[1].begin)
    def block_of(i):
        name = ""
        for n, b in blocks:
            if b.begin <= i < b.end:
                name = n
        return name
    return text, blocks, block_of


def rip_text(rom, cfg):
    text, _, block_of = strings(rom, cfg)
    tsv(OUT / "text" / "strings.tsv", ["index", "block", "text"], [(i, block_of(i), s) for i, s in enumerate(text)])
    return f"{len(text)} strings"


SPEAKER = re.compile(r"message_(SetFace\w*|SetActor\w*|ResetActor|ResetFace)\(([^)]*)\)")
SAYING = re.compile(r"(message_\w+)\(")
ENGLISH = re.compile(r'english="((?:[^"\\]|\\.)*)"')


def transcript(source):
    """The lines said in an ExplorerScript, in order, with the actor the script last named for the message box."""
    lines, who = [], ""
    current = None
    for raw in source.splitlines():
        m = SPEAKER.search(raw)
        if m:
            args = [a.strip() for a in m.group(2).split(",")]
            who = "" if m.group(1).startswith("Reset") else next((a for a in args if a.startswith(("ACTOR_", "OBJECT_"))), who)
        s = SAYING.search(raw)
        if s and not s.group(1).startswith(("message_SetFace", "message_SetActor", "message_Reset", "message_Close", "message_SetWaitMode", "message_Key")):
            current = s.group(1).removeprefix("message_")
        e = ENGLISH.search(raw)
        if e and current:
            said = re.sub(r"\[[^\]]*\]", "", e.group(1)).replace("\\n", " ").strip()
            line = f"{(who.removeprefix('ACTOR_') or '-')}\t{current}\t{said}"
            # The same line in each branch of a choice (one per partner or hero) is said once.
            if said and (not lines or lines[-1] != line):
                lines.append(line)
    return lines


def rip_scripts(rom, cfg):
    done, index = 0, []
    for name in files(rom, "SCRIPT/"):
        if not name.endswith(".ssb"):
            continue
        rel = Path(name).relative_to("SCRIPT")
        try:
            with contextlib.redirect_stdout(io.StringIO()):
                source, _ = FileType.SSB.deserialize(rom.getFileByName(name), cfg).to_explorerscript()
        except Exception as e:
            index.append((str(rel), "error: " + str(e)[:80], 0))
            continue
        out = OUT / "scripts" / rel.with_suffix(".exps")
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(source, encoding="utf-8")
        said = transcript(source)
        if said:
            d = OUT / "dialogue" / rel.with_suffix(".txt")
            d.parent.mkdir(parents=True, exist_ok=True)
            d.write_text("speaker\tkind\tline\n" + "\n".join(said) + "\n", encoding="utf-8")
        index.append((str(rel), "ok", len(said)))
        done += 1
    tsv(OUT / "dialogue" / "index.tsv", ["script", "state", "lines"], sorted(index))
    return f"{done} scripts"


def rip_data(rom, cfg):
    text, blocks, _ = strings(rom, cfg)
    block = dict(blocks)
    def named(b):
        bl = block.get(b)
        return (lambda i: text[bl.begin + i] if bl and bl.begin + i < bl.end else "") if bl else None
    md = FileType.MD.deserialize(rom.getFileByName("BALANCE/monster.md"))
    header, rows = rows_of(md.entries, named("Pokemon Names"))
    tsv(OUT / "data" / "pokemon.tsv", header, rows)
    from skytemple_files.data.waza_p.handler import WazaPHandler
    from skytemple_files.data.item_p.handler import ItemPHandler
    moves = WazaPHandler.deserialize(rom.getFileByName("BALANCE/waza_p.bin")).moves
    header, rows = rows_of(moves, named("Move Names"))
    tsv(OUT / "data" / "moves.tsv", header, rows)
    items = ItemPHandler.deserialize(rom.getFileByName("BALANCE/item_p.bin")).item_list
    header, rows = rows_of(items, named("Item Names"))
    tsv(OUT / "data" / "items.tsv", header, rows)
    mappa = FileType.MAPPA_BIN.deserialize(rom.getFileByName("BALANCE/mappa_s.bin"))
    floors = []
    for li, fl in enumerate(mappa.floor_lists):
        for fi, floor in enumerate(fl):
            f = fields(floor.layout)
            floors.append({"list": li, "floor": fi + 1, **f})
    keys = []
    for f in floors:
        for k in f:
            if k not in keys:
                keys.append(k)
    tsv(OUT / "data" / "floors.tsv", keys, [[f.get(k, "") for k in keys] for f in floors])
    dungeon_names = named("Dungeon Names (Main)")
    if dungeon_names:
        tsv(OUT / "data" / "dungeons.tsv", ["index", "name"], [(i, dungeon_names(i)) for i in range(block["Dungeon Names (Main)"].end - block["Dungeon Names (Main)"].begin)])
    return f"{len(md.entries)} Pokémon, {len(moves)} moves, {len(items)} items, {len(floors)} floors"


SAID = {"Talk", "Narration", "Monologue", "Mail", "Notice", "Explanation", "SpecialTalk", "ImitationSound"}


def rip_story(rom, cfg):
    """The main story in order: the scenario scripts m<chapter><letter><scene> (m01a0101 is the storm at sea), only what is said (no menus)."""
    story = sorted((p for p in (OUT / "dialogue").rglob("m[0-9][0-9][a-z]*.txt") if not p.stem.startswith("m00")), key=lambda p: (p.stem, p.parent.name))
    lines = 0
    with open(OUT / "dialogue" / "story.txt", "w", encoding="utf-8") as f:
        for p in story:
            said = [l.split("\t", 2) for l in p.read_text(encoding="utf-8").splitlines()[1:]]
            said = [(who, line) for who, kind, line in said if kind in SAID]
            if not said:
                continue
            f.write(f"\n== {p.stem} ({p.parent.name}) ==\n")
            for who, line in said:
                f.write(f"{who}: {line}\n")
                lines += 1
    return f"{lines} lines of story"


def rip_portraits(rom, cfg):
    kao = FileType.KAO.deserialize(rom.getFileByName("FONT/kaomado.kao"))
    done = 0
    (OUT / "portraits").mkdir(parents=True, exist_ok=True)
    for i in range(kao.n_entries()):
        faces = [kao.get(i, s) for s in range(40)]
        if not any(faces):
            continue
        last = max(s for s, f in enumerate(faces) if f) + 1
        strip = Image.new("RGBA", (40 * last, 40))
        for s in range(last):
            if faces[s]:
                strip.paste(faces[s].get().convert("RGBA"), (40 * s, 0))
        strip.save(OUT / "portraits" / f"{i:04d}.png", optimize=True)
        done += 1
    return f"{done} Pokémon's portraits"


def rip_backgrounds(rom, cfg):
    done = 0
    for name in files(rom):
        if not name.lower().endswith(".bgp"):
            continue
        out = OUT / "backgrounds" / (name.replace("/", "_").rsplit(".", 1)[0] + ".png")
        out.parent.mkdir(parents=True, exist_ok=True)
        try:
            FileType.BGP.deserialize(rom.getFileByName(name)).to_pil().save(out, optimize=True)
            done += 1
        except Exception:
            pass
    return f"{done} backgrounds"


def rip_textures(rom, cfg):
    done = 0
    for name in files(rom):
        if not name.lower().endswith(".wte"):
            continue
        out = OUT / "textures" / (name.replace("/", "_").rsplit(".", 1)[0] + ".png")
        out.parent.mkdir(parents=True, exist_ok=True)
        try:
            wte = FileType.WTE.deserialize(rom.getFileByName(name))
            if not wte.has_image():
                continue
            image = wte.to_pil_canvas(0) if hasattr(wte, "to_pil_canvas") else wte.to_pil(0)
            image.save(out, optimize=True)
            done += 1
        except Exception:
            pass
    return f"{done} textures"


def main():
    p = argparse.ArgumentParser()
    p.add_argument("rom")
    p.add_argument("--only", default=",".join(PARTS))
    args = p.parse_args()
    data = Path(args.rom).read_bytes()
    if hashlib.sha1(data).hexdigest() != ROM_SHA1:
        raise SystemExit("Not the Explorers of Sky (USA) ROM (SHA-1 mismatch).")
    rom = ndspy.rom.NintendoDSRom(data)
    cfg = get_ppmdu_config_for_rom(rom)
    for part in args.only.split(","):
        print(part, globals()["rip_" + part](rom, cfg), flush=True)


if __name__ == "__main__":
    main()

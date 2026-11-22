#!/usr/bin/env python3
import hashlib
import sys
from pathlib import Path

import ndspy.rom
from PIL import Image
from skytemple_files.common.types.file_types import FileType
from skytemple_files.common.util import get_ppmdu_config_for_rom
from skytemple_files.data.item_p.handler import ItemPHandler

ROM_SHA1 = "5fa96ca8d8dd6405d6cd2bad73ed68bc73a9d152"
OUT = Path(__file__).resolve().parents[2] / "assets" / "tiles"

FLOOR_ITEMS = [
    ("berry-spicy", "Cheri Berry"), ("berry-sour", "Sitrus Berry"), ("berry-sweet", "Pecha Berry"),
    ("berry-dry", "Chesto Berry"), ("berry-bitter", "Rawst Berry"),
    ("hp-up", "Protein"), ("protein", "Protein"), ("iron", "Iron"), ("calcium", "Calcium"), ("zinc", "Zinc"), ("carbos", "Protein"),
    ("poke", "Poké"),
]


def clear(img):
    img = img.convert("P") if img.mode != "P" else img
    rgba = img.convert("RGBA")
    px, src = rgba.load(), img.load()
    for y in range(img.height):
        for x in range(img.width):
            if src[x, y] % 16 == 0:
                px[x, y] = (0, 0, 0, 0)
    return rgba


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    data = Path(sys.argv[1]).read_bytes()
    if hashlib.sha1(data).hexdigest() != ROM_SHA1:
        sys.exit("Not the Explorers of Sky (USA) ROM (SHA-1 mismatch).")
    rom = ndspy.rom.NintendoDSRom(data)
    cfg = get_ppmdu_config_for_rom(rom)
    pack = FileType.DUNGEON_BIN.deserialize(rom.getFileByName("DUNGEON/dungeon.bin"), cfg)
    OUT.mkdir(parents=True, exist_ok=True)

    traps = pack.get("traps.trp.img")
    sheet = Image.new("RGBA", (24 * len(traps.sprites), 24))
    for i in range(len(traps.sprites)):
        sheet.paste(clear(traps.to_pil(i, 0)), (i * 24, 0))
    sheet.save(OUT / "floor-traps.png", optimize=True)

    items = pack.get("items.itm.img")
    table = ItemPHandler.deserialize(rom.getFileByName("BALANCE/item_p.bin")).item_list
    strings = FileType.STR.deserialize(rom.getFileByName("MESSAGE/text_e.str")).strings
    block = cfg.string_index_data.string_blocks["Item Names"]
    names = {n: i for i, n in reversed(list(enumerate(strings[block.begin:block.end])))}
    sheet = Image.new("RGBA", (16 * len(FLOOR_ITEMS), 16))
    rows = []
    for col, (ours, theirs) in enumerate(FLOOR_ITEMS):
        entry = table[names[theirs]]
        sheet.paste(clear(items.to_pil(entry.sprite, entry.palette)), (col * 16, 0))
        rows.append(f"{ours}\t{col}\t{theirs}\t{entry.sprite}\t{entry.palette}")
    sheet.save(OUT / "floor-items.png", optimize=True)
    (OUT / "floor-items.tsv").write_text("item\tcolumn\tsky_item\tsprite\tpalette\n" + "\n".join(rows) + "\n", encoding="utf-8")
    print(len(traps.sprites), "traps,", len(FLOOR_ITEMS), "items")


if __name__ == "__main__":
    main()

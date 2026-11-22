# Red Rescue Team sound effects

`NNN.ogg` is song-table index NNN of Pokémon Mystery Dungeon: Red Rescue Team (USA), which is the
id the game itself passes to its sound functions (and the number of the `songNNN` files in the
Sounds Resource dump). `sfx.tsv` has each one's player and length; `triggers.tsv` says what the
game plays it for. Rendered by `tools/sprites/extract_rescue_audio.py`, mapped by
`tools/sprites/build_sfx_catalog.py` (needs a pret/pmd-red checkout). Private use only.

## Two separate accuracy questions

1. **Which id is which event?** Answered by the game's own code, below.
2. **Does the rendered clip sound like the game?** Not verified. The renderer approximates the
   GBA sound engine (no vibrato, reverb or pan; synthesised square/wave/noise channels), and
   nobody has listened to the output yet. Compare against the Sounds Resource clips by ear.

## How triggers were found

pret/pmd-red names no sound effect; every call passes a bare number. The catalog reads where
the numbers come from:

| kind | source | confidence |
|---|---|---|
| `move` | `gUnknown_80BDBC4` / `gUnknown_80C183C` (sound per move, per species) and the move's effect animations | `table`: the game reads the id from a table keyed by the move's name |
| `item`, `trap` | item and trap effect-animation tables, each animation carrying a sound id | `table` |
| `menu` | `sMenuSoundEffects` (accept, back, navigate, sort, toggle, info) | `table` |
| `call` | every literal `PlaySound`, `PlaySoundEffect`, `PlayFanfareSE`, `PlaySoundWithVolume` call: file and function | `call-site`: where it plays, not what it means. `sub_80xxxxxx` names say nothing |
| `script` | story scripts' `FANFARE_PLAY(2)`: the map code (e.g. `a04p04`) and script | `call-site` |
| `dungeon` | a few ids chosen by a ternary or small array (stairs, wind) | `call-site` |
| 312 | read by hand in `dungeon_leveling.c` | `read` |

Coverage of the 496 sounds: **310** have a table-derived or hand-read trigger, **110** only a
call site (a lead, not a definition), **76** none (no literal reaches them: they may be played
through a computed id or by data this pass does not parse, or never). Ids `0` and `997` are the
engine's "no sound" and are skipped.

## Audit of the sounds already named in `convert_pmd_sfx.py`

Checked against the code (the dump numbering equals these ids):

- **Confirmed by tables:** 301 navigate, 302 accept, 303 back (also "fail"), 306 sort, 307 toggle,
  344 the default move-hit sound (damage).
- **Consistent with the call sites:** 200, 201 (mission reward), 202, 203, 204 (a monster
  joining), 331 (item pick-up), 332 (money: bank, shops), 333 (giving, placing, taking items).
- **Contradicted:** `level-up` is mapped to 216, but 216 is the dungeon wind sound (stage 0).
  312, which looks like a level-up cue, is the "learned a new move" sound. The real level-up cue
  is not among the literal calls.
- **No trigger found, so unverified:** 311, 405, 414 (and the guesses 342, 343, 353, 354, 356,
  406, 421, 455, 457, 466, 468, 470, 471, 473, 478, 516 that only reach scripts, moves or
  cutscenes). Treat as "sounds plausible", not "is the cue for".

## What the app's UI sounds map to, and how sure that is

`assets/sfx/provenance.tsv` lists every sound the app uses (`SOUNDS` in `tools/sprites/convert_pmd_sfx.py`), the Red
Rescue Team song id it comes from, and whether the owner named that id by ear (`owner-named`) or it was picked by the
clip's length and pitch contour and still needs a listen (`guessed`). The script rewrites the file on every run, so the
two can never drift apart. A `guessed` row is a lead, not a definition: swap it only after hearing the clip.

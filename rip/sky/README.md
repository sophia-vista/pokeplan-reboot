# Explorers of Sky, ripped

Pokémon Mystery Dungeon: Explorers of Sky (USA), decoded into readable files from the owner's ROM (SHA-1 `5fa96ca8…`; not in the repo),
for reference and future work: story writing, rules, art and effects to draw on (owner, 14 November 2026). Nothing here ships in the app; when
something is used, it is cut into `assets/` by its own tool and listed in `docs/assets.md`. Nintendo / Chunsoft / Spike Chunsoft art and text:
private use only.

Re-make it with `python3 tools/rip/rip_sky.py ROM.nds` and `python3 tools/sprites/extract_sky_effects.py ROM.nds`, in a venv with
`pip install ndspy skytemple-files "pillow<11"`.

**Do not read these folders whole** (about 52 MB in 7,800 files, mostly scripts): grep them, then open the file you need.

| Folder | What | How to use it |
|---|---|---|
| `text/strings.tsv` | All 18,451 strings of the game's text file, with their block (Pokémon names and categories, moves, items and their descriptions, dungeons, menus, help, the Pokémon's lines) | `grep -i "oran berry" rip/sky/text/strings.tsv`; `awk -F'\t' '$2=="Move Names"'` |
| `dialogue/story.txt` | The main scenario's dialogue in order (the `m<chapter>…` scripts; `m01a0101` is the storm at sea), only what is said (no menus), `speaker: line`. The other script families (`um`, `us`, `n`, `s`, `enter`...) are the game's other scenes: `dialogue/index.tsv` lists them all | Read a chapter: `grep -n "== m05" rip/sky/dialogue/story.txt`, then read that range |
| `dialogue/<map>/<script>.txt` | What is said in every script that has dialogue: speaker (the actor the script names, `-` for the hero's partner or none), kind (`Talk`, `Mail`, `Narration`, `Menu`...), line; repeated branches said once | `index.tsv` lists every script with its number of lines |
| `scripts/<map>/<script>.exps` | Every cutscene and ground script decompiled to ExplorerScript (SkyTemple's language): moves, camera, sounds, flags, branches | The full staging of a scene, when the dialogue is not enough |
| `data/` | The game's tables: `pokemon.tsv` (monster.md: types, stats, abilities, evolutions, sizes), `moves.tsv`, `items.tsv`, `floors.tsv` (every dungeon floor's layout: structure, tileset, music, weather, rooms, traps, items), `dungeons.tsv` | Columns are SkyTemple's field names |
| `portraits/NNNN.png` | Each Pokémon's portraits from the game (40 × 40, one per emotion in the game's order; a missing one is empty) | Index = the Pokémon's number in `data/pokemon.tsv` |
| `effects/fx-FFF-AA.png` | All 1,020 effect animations (moves, statuses, traps, cutscenes), one strip each; `effects.tsv` has frames, size, anchor and each frame's ticks (1/60 s) | `TRAP_ANIMATION_INFO` and `EFFECT_ANIMATION_INFO` in overlay 10 say which a trap or move plays |
| `backgrounds/`, `textures/` | The full-screen pictures (title, menus, dungeon screens) and the WTE textures | |

Elsewhere: the ground maps are `assets/sky-maps/`, the dungeon tilesets `assets/tiles/ex-*`, the sound effects `assets/sky-sfx/`
(and `tools/sprites/extract_sky_audio.py` renders the rest, approximately). Not ripped: the Pokémon sprites (the PMD SpriteCollab
sprites the app uses already are these, redrawn and extended), the ground objects' sprites (`GROUND/*.wan`), the music as files.

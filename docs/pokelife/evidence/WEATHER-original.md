# Weather effects: PokéLife against Red Rescue Team (owner request, 9 November 2026)

What the original does, how it was found, what PokéLife did differently and what was changed. Everything here can be reproduced with the tools in `tools/weather/`
(they need a checkout of `pret/pmd-red`, cloned read-only through the git proxy; no ROM is used: the decompilation carries the game's graphics data).

## 1. The sheets (step 1)

The owner's rips are `assets/weather/source/misc.png` (736 x 648, RGBA: cloud shadows, three 16-frame tile animations, two sandstorm textures) and `rain.png` (1248 x 2192, indexed:
61 frames of 240 x 160). `tools/sprites/cut_weather.py` cuts them (transparent where the sheet's panel colour was); `tools/weather/inspect_sheets.py` reports every sheet and frame
(`inspect_sheets.py <dir>` also writes each frame as a PNG):

| sheet | file size | frame | frames | opaque px (min-max) | semi-transparent px | colours | bbox of frame 0 | repeated frames |
|---|---|---|---|---|---|---|---|---|
| rain (1200x2080) | 33281 B | 240x160 | 61 | 15-266 | 0 | 6 | (24, 0, 29, 15) | none |
| snow (256x128) | 1494 B | 64x32 | 16 | 84-84 | 0 | 5 | (3, 0, 59, 32) | none |
| snow-dungeon (128x128) | 824 B | 32x32 | 16 | 42-42 | 0 | 5 | (2, 0, 24, 30) | none |
| blizzard (128x128) | 1358 B | 32x32 | 16 | 77-77 | 0 | 4 | (0, 2, 32, 32) | none |
| sand-1 (120x32) | 1665 B | 120x32 | 1 | 3840-3840 | 0 | 6 | (0, 0, 120, 32) | none |
| sand-2 (120x32) | 1613 B | 120x32 | 1 | 3840-3840 | 0 | 6 | (0, 0, 120, 32) | none |
| clouds (720x408) | 5472 B | 720x408 | 1 | 43548-43548 | 0 | 1 | (25, 15, 713, 393) | none |

- **The sandstorm sheets had the wrong rows.** The textures sit at y 608 to 639 of `misc.png`; the cut started at y 600, so 8 rows of the sheet's caption ("Sandstorm") and its dark
  background came with them and showed as repeating text and stripes over the town (reported from a phone, 9 November). Fixed in `cut_weather.py`; the other sheets cut identically.
- Sheet colours are the game's 5-bit colours at `v * 255 / 31` (the cloud blue 82, 115, 189 is the palette's 87, 119, 191 = 5-bit 10, 14, 23).
- The sandstorm textures are fully opaque (3,840 of 3,840 pixels, 6 colours): they only work blended.
- **The rain sheet is a shower, not a loop.** Its frames hold 15 opaque pixels at frame 0, 266 at frame 41 and 23 at frame 60: drops start, build up and fade. PokéLife plays frames 0 to 60 round
  and round, so the rain swells and dies every 4 seconds. The loop points of the game's rain effect are not in the files read so far (open item below).

## 2. The game's weather code (step 2)

There are two systems. **The ground screens** (Pokémon Square and the other towns) are `src/ground_weather.c`: a script variable `WEATHER_KIND` (-1 none, 0 to 15) picks one background layer and a
blend; this is what the launcher's town imitates. **The dungeon** (`src/weather.c`) has eight weathers (`WeatherID`: clear, sunny, sandstorm, cloudy, rain, hail, fog, snow), each an effect animation
(effect ids 331, 239, 16 or 440, 20 and 223 and 425 and 426 in `src/dungeon_8041AD0.c`, the `efob`/`efbg` files of `data/effects`) plus a colour ramp (section 3).

### Ground weather kinds (`GroundWeather_Select`, table `gUnknown_811E5F4`, `gMapFilesTable`)

| kind | background (`data/map_bg`) | tile animation (`.bpa`) | blend (`BLDALPHA`) | motion each tick |
|---|---|---|---|---|
| 0 to 8 | T01P07W, H01P01W, H02P01W, H02P02W, H02P03W, H07P04W, H17P01W, H19P01W, H29P03W | | 0x1010 for 0, 3, 4, 5, 8; 0x808 for 7; 0x1000 for 1, 2, 6 | pinned to the camera |
| 9 | W01 (1152 x 888): the cloud shadows | none | 0x40c | x +1 every 2 ticks, y +1 every 4 ticks |
| 10 | W03P01 (960 x 720): **snow** | W03P011: 16 frames, 16 tiles, duration 4 | 0x1000, wraps | pinned |
| 11 | W03P02: snow, denser | W03P021: duration 2 | 0x1000, wraps | pinned |
| 12 | W03P03: **blizzard** | W03P031: duration 1 | 0x1000, wraps | pinned |
| 13 | W04 (1440 x 960): sparse flecks | W041: 6 tiles, duration 6 | 0x1000, wraps | pinned |
| 14 | W05 (480 x 264): grey diagonal streaks on black, palette-animated | none | 0x1010 | x +1 every 4 ticks |
| 15 | W06 (336 x 240): grey texture | none | 0x40c | x +1 every 2 ticks |

- **Motion.** `sub_80A60D8` adds to counters each tick and moves the layer by a quarter of them (`unk4 += 2; x += unk4/4; unk4 &= 3`): kind 9 is 0.5 px a tick right and 0.25 up (30 and 15 px a second), kind 14 0.25 and kind 15 0.5.
  The layer's position is the camera's minus the scroll, so content moves right and up.
- **Blend.** `gUnknown_2026E4E` is `BLDALPHA` as `SetBldAlphaReg((v & 0x1F00) >> 8, v & 0x1F)`: the high byte is the weather layer's weight (EVA, BG1 is the first target), the low byte the picture
  below (EVB). So 0x40c is **4 parts of the layer and 12 of the scene (a quarter)**; 0x1010 adds the layer at full strength (black adds nothing: the rain streaks); 0x1000 shows the layer opaque where it
  has a pixel and nothing of the scene beneath it (the flakes); 0x808 is half and half.
- **Playback.** A tile animation's frame lasts its duration plus one tick: `if (unk4-- <= 0) { advance; unk4 = duration }` in `sub_80A4764`. At the GBA's 59.7275 Hz that is **83.7 ms a frame for snow
  (duration 4, 5 ticks), 33.5 ms for the blizzard (2 ticks)**, 50 ms for W03P02 and 117 ms for W04. All of them have 16 frames. The layers wrap round (`wrapAround` set by `sub_80A3BB0(.., 1)`), so a
  small tile repeats over the whole screen.

### The decoder and the match (`tools/weather/decode_bg.py`, `match_sheets.py`)

`decode_bg.py` rebuilds the layers from the game's `.bpl` palette, `.bpc` tiles and chunks, `.bma` map and `.bpa` animation files, following `sub_80A2FBC`, `CopyBpcTilesToVram`, `_UncompressCell` and the
NRL decompressor `BmaLayerNrlDecompressor`. `match_sheets.py` then compares every frame of PokéLife's sheets with the decoded layers' frames, pixel for pixel:

| PokéLife sheet | is | rip frame f = animation frame |
|---|---|---|
| snow (64 x 32) | W03P01 (kind 10) | f + 2 |
| snow-dungeon (32 x 32) | W03P01, a 32 x 32 window of the same layer | f |
| blizzard (32 x 32) | W03P03 (kind 12) | f + 9 |
| clouds | W01 (kind 9), same blue | |
| rain (240 x 160, 61 frames) | **not a ground layer**: the dungeon's rain effect (effect 16) | |
| sand-1 and sand-2 (120 x 32) | **no ground layer matches** (the ground's W05 and W06 are grey); the dungeon's sandstorm effect (239) | |

## 3. The colour ramps (step 3)

`data/dungeon/colvec.inc` (the file `colvec` of the dungeon archive) holds, for each of the 8 weathers, a table of 256 entries for each of red, green and blue (`tools/weather/extract_ramps.py` writes
`assets/weather/ramps.tsv`). Every colour of a tile palette goes through it (`SetBGPaletteBufferColorRGB`): `out5 = ramp[channel] * brightness / 256 & 0x1F` with the game's 8-bit palette value `v5 * 8 + 7`
and the brightness at its full 31. On a change of weather the dungeon's ramp moves to the new table by at most 10 a frame (up to 64 frames: about a second). The clear ramp is the identity (`8k + 7`).
What each does to a mid grey and to black, in 8 bits:

| weather | black becomes (r, g, b) | white becomes | look |
|---|---|---|---|
| clear | 0, 0, 0 | 255 | none |
| sunny | 15, 15, 15 | 255 | blacks lifted: bright |
| sandstorm | 63, 63, 63 | 255, 255, 207 | red and green lifted, blue crushed to 207: yellow-brown |
| cloudy | 31, 31, 63 | 231, 231, 255 | blacks lifted, reds and greens held back: cool and dull |
| rain | 0, 0, 39 | 239 | reds and greens sunk, blue lifted: dark blue |
| hail | 0, 15, 31 | 199, 223, 239 | cold |
| fog | 87, 87, 87 | 239 | flat grey: blacks up to 87, whites down to 239 |
| snow | 0, 23, 39 | 231 | cool |

It is a lookup, not a tint: a straight line through a ramp is off by up to 31 of 255, so the town recolours its tiles through the table itself (`WeatherGrade.kt`).
PokéLife had none of this: it drew a tinted rectangle (sand) or a white one (blizzard) over a town that kept its clear colours.

## 4. What was wrong and what changed (steps 4 and 5)

| | before | now (from the game) |
|---|---|---|
| snow frames | 220 ms | 5 ticks = 83.7 ms, 16 frames |
| blizzard frames | 80 ms, plus a white veil at 10% | 2 ticks = 33.5 ms, no veil (the flakes are opaque) |
| snow, blizzard compositing | | opaque where there is a flake (EVA 16, EVB 0) |
| clouds | black shadows at 16%, 6 px/s right | the game's blue (82, 115, 189) at 4/16 = 25%, 30 px/s right and 15 px/s up, whole game pixels, wrapping |
| sandstorm | tint rectangle at 16%, textures at 40% and 30%, 18 and 9 px/s | no tint; both textures at 25% (the game's texture blend), moving 0.5 and 0.25 px a tick like the two layers that scroll (kinds 15 and 14); whole game pixels |
| sandstorm sheet | included the caption rows | cut from the right rows (the bug from the phone) |
| dungeon-tile towns | clear colours under every weather | each tile colour through the weather's own ramp |
| rain | 61 frames round and round at 70 ms | unchanged: the timing and loop points are not known (below) |

The positions and the scale were already right: a layer is the game's pixels at a whole-number scale without smoothing, repeated to cover the screen (the game wraps its layers the same way), and
the snow and blizzard layers are fixed to the screen like the game's, which pins them to the camera.

## 5. Open

- **Rain** (dungeon effect 16, `efob` objects): decode the effect's animation (frame durations, loop points) from `data/effects` and the effect tables; until then the shower's start and end repeat.
- **Sandstorm art**: the brown textures belong to the dungeon's sandstorm effect (239); their scroll speeds and blend are taken from the ground layers that do scroll. Decoding effect 239 would settle them.
- **Gradual change.** The game takes about a second to move to a new ramp; the town changes at once.
- The ground kinds 0 to 8 (map-specific layers), the dungeon's sunny and fog weathers and the town's weather transitions are not used.

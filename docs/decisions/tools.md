# Decision records: tools

Moved here from code comments in the comment audit (21 November 2026). Each entry is a decision, rule or constraint that
the code alone does not show. Entries are grouped by source file; a line saying "Documented in" means the owning doc
already holds the rule and the entry only points to it.

## `tools/fonts/cut_pmd_font.py`

### cut_pmd_font.py

Documented in `docs/assets.md Art and font sources`.

### pmd-dialogue sheet rows

In tools/fonts/cut_pmd_font.py the ROWS table lists the glyphs of pmd-dialogue.png from top to bottom, 13 per row. The underscore is not a letter there: the last cell of row 5 holds the underline.

### pmd-dialogue baseline glyph

In tools/fonts/cut_pmd_font.py the BASE table names, per sheet row, the glyph whose bottom edge is the row's baseline, so descenders in the row hang below it and every letter sits on one line.

### pmd-dialogue accent rows

In tools/fonts/cut_pmd_font.py rows 6 and up of the dialogue font sheet (the accented letters) come after a gap and are all measured inside the sheet's band 6, not in a band of their own.

## `tools/fonts/patch_zero.py`

### Pokémon DP font zero patch

tools/fonts/patch_zero.py gives the zero of the Pokémon DP font (hexparrot/pokemon-assets, MIT) a bar so it is not mistaken for an 8 at small sizes (design review, 11 November 2026). It runs from assets/fonts on the original files and needs fonttools. The regular zero gets a 3 pixel centre bar, the bold zero (a 2 pixel hole) a 3 pixel staircase; one art pixel is 157 font units.

### patch_zero.py contour winding

In tools/fonts/patch_zero.py the added rectangles are written clockwise because TrueType glyph contours are filled when clockwise.

### Peso sign in the DP font

The Pokémon DP font has no peso sign (U+20B1), so tools/fonts/patch_zero.py builds one from a P: two bars through the bowl in the regular weight, stubs left of the stem in the bold weight.

### patch_zero.py peso shift

In tools/fonts/patch_zero.py add_peso moves the copied P one art pixel to the right so the stubs of the bold peso have room left of the stem.

## `tools/icon/monochrome.py`

### Themed launcher icon layer

tools/icon/monochrome.py builds the Android monochrome (themed) icon layer from the adaptive icon foreground. Android tints that layer with one colour and uses only its alpha, so the cream egg would become a solid blob; the script keeps the egg outline, check and stopwatch dial opaque and clears the light fill. It only changes alpha; no pixel is drawn.

## `tools/link/generate_bazi.py`

### generate_bazi.py

Documented in `docs/code-map.md link-core`.

### BaZi jie names

In tools/link/generate_bazi.py each of the twelve jie is listed with both names the lunar_python library uses for it: a pinyin-style key for some and the Chinese name itself for others.

### BaZi jie table

In tools/link/generate_bazi.py the jie tables of neighbouring lunar years repeat names, so they are collected in a list of (name, Solar) pairs, not a dict, which would drop entries.

### BaZi jie table years

A Gregorian year spans two lunar years' jie tables, so tools/link/generate_bazi.py reads the tables of the year before, the year and the year after.

## `tools/rip/rip_sky.py`

### rip_sky.py

Documented in `docs/assets.md Explorers of Sky maps`.

### rip_sky dialogue dedupe

In tools/rip/rip_sky.py the same line often appears in each branch of a choice (one per partner or hero); it is written to the dialogue file once.

### Main story scripts

In the Explorers of Sky rip the main story is the scenario scripts named m<chapter><letter><scene> (m01a0101 is the storm at sea); tools/rip/rip_sky.py collects only what is said in them, in order, into story.txt, skipping the m00 scripts.

## `tools/species/generate.py`

### Dex generator

tools/species/generate.py writes the Dex tables (species, forms, evolutions, regions, entries, moves, learnsets) as JSON, one array row per line, into pokemon-core's dex resources in the column order DexParser.kt reads. It writes only plain PokeAPI facts; rules such as rarity and hatch pools live in Kotlin so they are tested and tunable.

### PokeAPI CSV pin

tools/species/generate.py reads the PokeAPI CSV data at one pinned commit so the generated Dex is reproducible and CI does not break when upstream changes. To update, take the new SHA from git ls-remote of PokeAPI/pokeapi master, regenerate and review the diff.

### Module regions

Module regions (PLAN, CARE, HEAL, MINT, BASE, LINK) are hand-curated lists in tools/species/regions (see docs/region-curation-prompt.md). A missing list file means the region is not curated yet and gets no row, so its dex is empty. A species belongs to at most one module region.

### Odd evolution time conditions

In tools/species/generate.py an evolution whose time condition is not day, night or dusk (for example full moon) is not a time of day, so it is stored as a TRIAL evolution.

### Evolution trigger choice

PokeAPI has one evolution row per version and location; tools/species/generate.py keeps the simplest valid trigger per evolution, ranked by KIND_RANK (LEVEL, STONE, FRIENDSHIP, LEVEL_TIME, GENDER, TRADE, RANDOM, TRIAL).

### HISUI region row

The HISUI region row in the generated Dex lists the species of the Hisui regional dex; it is used to pick the Hisuian form variant for species shown in Sinnoh.

### Learnset generation

Learnsets are one row per Pokémon row that has moves, taken from the newest version group in which it learns anything, as level-up (move@level), egg, tutor and machine lists; moves of a non-standard type are left out.

### Dex JSON column order

The columns of each generated Dex JSON table are defined by DexParser.kt in pokemon-core; tools/species/generate.py must write them in that order.

### Dex species row columns

A species row is: id, identifier, name, generation, evolves-from, female eighths, baby, legendary, mythical, cosmetic forms, genus, height, weight.

### Dex forms row columns

A forms row is: id, identifier, species id, kind, name, types, six base stats, abilities, hidden ability.

## `tools/sprites/build_sfx_catalog.py`

### build_sfx_catalog.py

Documented in `docs/assets.md Art and font sources`.

### STOP_SOUND_EFFECT id

In pret/pmd-red the constant STOP_SOUND_EFFECT (997, in include/constants/bg_music.h) means silence; tools/sprites/build_sfx_catalog.py maps it to 997 so a move with no sound is recognised.

### Hand-read sound ids

Sound ids that the decompilation picks with a ternary or a small array cannot be scraped by pattern, so tools/sprites/build_sfx_catalog.py lists them by hand, read from the code, as call-site rows.

## `tools/sprites/compose_secret_base.py`

### compose_secret_base.py

Documented in `docs/assets.md Hoenn module art (REP-053)`.

### Secret Base looks table

The LOOKS table in tools/sprites/compose_secret_base.py (look to layout and secondary tileset) must match RoomLook in base-core, which names the same six looks.

### Secret Base top layer transparency

In the Secret Base room composer, colour 0 of a top-layer tile is see-through, as on the GBA.

### Decoration sprite transparency

In decoration sprites, palette colour 0 is the see-through colour, so compose_secret_base.py turns it transparent.

## `tools/sprites/convert_pmd_sfx.py`

### Sfx cursor

In convert_pmd_sfx.py the 'cursor' sound (Red Rescue Team 301) also serves switches and the letter scroller's tick.

### Sfx drawer-open

The 'drawer-open' UI sound is Red Rescue Team 330, a rising sound.

### Sfx drawer-close

The 'drawer-close' UI sound is Red Rescue Team 337, a falling sound.

### Sfx unpin

The 'unpin' UI sound is Red Rescue Team 342, the game's status-down sound.

### Sfx hide

The 'hide' UI sound is Red Rescue Team 333, the game's escape sound.

### Sfx focus-end

The 'focus-end' UI sound is Red Rescue Team 200, the game's default success sound.

### Sfx level-up

The 'level-up' sound shares the rank-up clip (Red Rescue Team 201). Id 216 was tried first, but it is the dungeon wind sound (see assets/pmd-sfx/README.md).

### Sfx rank-up

The 'rank-up' sound is Red Rescue Team 201, the game's super success sound.

### Sfx candy

The 'candy' sound is Red Rescue Team 332, the game's money sound.

### Sfx item-get

The 'item-get' sound is Red Rescue Team 203, the game's got-something-good sound.

### Sfx unlock

The 'unlock' sound is Red Rescue Team 204, the game's unlocked-something sound.

### Buddy emote sounds

The sounds from 'damage' to 'great-shock' in convert_pmd_sfx.py are the emotes for the buddy's reactions.

### Sfx surprise

The 'surprise' sound is Red Rescue Team 356, the game's mild surprise.

### Sfx curious

The 'curious' sound is Red Rescue Team 466, the game's curious or suspicious sound.

### Sfx sad

The 'sad' sound is Red Rescue Team 470, the game's 'oh.' (the teardrop).

### Sfx eating

The 'eating' sound is Red Rescue Team 516, played when a berry is given to the buddy.

### Sfx calm-end

The 'calm-end' sound is Red Rescue Team 202, the game's mellow success sound.

## `tools/sprites/convert_story_sfx.py`

### convert_story_sfx.py

Documented in `docs/assets.md Art and font sources`.

### Story sfx MAXLEN

MAXLEN in convert_story_sfx.py is in seconds: a longer story clip is cut to it with a short fade-out.

### Story sfx names

Keys of SOUNDS in convert_story_sfx.py are the names of the Sfx enum in composeApp (Cries.kt); values are the clip names in assets/sky-sfx.

## `tools/sprites/cut_status.py`

### cut_status.py

Documented in `docs/assets.md Art and font sources`.

### Status sheet LEFT offset

In the status sheet LEFT (104) is the x where the icons start; the palettes sit to its left.

### Status sheet ROWS

ROWS in cut_status.py maps an icon name to (top y of its row on the sheet, number of frames[, cell size]).

### Status stat icons

The stat icons (flame, shield, fist, ...) are kept for the buddy's type-flavoured reactions, such as a flame for Fire and a fist for Fighting.

### Status sheet EXTRA

EXTRA in cut_status.py reads the second status sheet, which has icons of other sizes: name to (cell size, the box of each frame on the sheet).

## `tools/sprites/cut_weather.py`

### cut_weather.py

Documented in `docs/assets.md Weather`.

### Rain sheet grid

The rain sheet is 13 rows of 5 frames starting at (8, 8), 248 px apart horizontally and 168 px vertically; the last row holds one frame (61 used).

## `tools/sprites/derive_shiny.py`

### derive_shiny.py

Documented in `docs/assets.md Mystery Dungeon sprites (preferred)`.

### Derived shiny folders

FOLDERS in derive_shiny.py lists the sheet folders under assets/pmd, mapped to table names; the idle sheets are the folder itself (the empty name).

### Derived shiny NEAREST

NEAREST (110.0) is the largest redmean colour distance at which a sheet pixel still takes the change of the nearest colour of the regular still sprite.

### Derived shiny CHANGED

A colour of the regular still counts as changed in its shiny when it moves by at least CHANGED (48.0) in colour distance or HUE_CHANGED (12.0) degrees round the hue circle.

### Derived shiny greys

Below GREY_SATURATION (0.18) saturation or DARK_VALUE (0.2) value a colour is a grey, white or near-black; its hue means nothing, so it is matched by lightness alone.

### Derived shiny HUE_REACH

HUE_REACH (32 degrees) is the furthest round the hue circle a pixel may be from the regular still's colour it takes its change from.

### Derived shiny colour table

The colour table of a form is a list of (h, s, v, change) of the regular still's colours, with change None when the shiny keeps the colour, else (hue turn, saturation difference, value ratio); None for the whole form when there is no usable pair or the shiny looks the same.

### Derived shiny colour matching

Chromatic colours are matched first by hue, then by how pale and how light; greys by lightness and a little by tint. The weights are in derive_shiny.py's distance expression.

### Derived shiny grey tint

A grey the shiny tints (a white that goes lilac) takes the shiny's hue and as much saturation as it gained.

### Derived shiny scope

Only a form whose idle sheet has no real shiny gets its other sheets derived. A form with a real idle shiny but a missing one for another sheet (frames differ) keeps showing the real one there; the app falls back to the idle shiny.

### derived-shiny.tsv rerun

A form derived by an earlier run keeps its line in assets/pmd/derived-shiny.tsv: its flags are set by then, so a rerun would not see it as missing a shiny.

## `tools/sprites/extract_rescue_audio.py`

### gSongTable address

The song table (gSongTable) of Red Rescue Team (USA) is at ROM address 0x9E866BC, taken from pret/pmd-red sound/song_table.inc.

### Red Rescue Team song count

The song table has 941 slots: 588 real songs and 353 empty_song slots.

### m4a engine frame rate

FRAME_HZ (59.7275) is the GBA's vblank rate; m4a envelopes and tempo run on it.

### m4a 4-bit delta compression

The engine's 4-bit delta compression stores samples in 33-byte blocks of 64 samples each.

### m4a voicegroup layout

A voicegroup is 128 voices of 12 bytes each.

### m4a active note record

An active note in extract_rescue_audio.py is a list [start_sample, remaining_ticks or None, key, vel, vol_at_start, voice, ...].

### m4a finished note record

A finished note in extract_rescue_audio.py is a dict with start, end, key, vel, voice, bend, tune and keysh.

### m4a loop handling

When a track jumps back (a loop), the renderer stops that track after one pass: one pass is enough for a clip.

### m4a unknown commands

The renderer skips one byte on an unknown m4a command and continues.

### m4a track volume automation

Track volume changes are applied as a piecewise-constant gain between the recorded volume points.

## `tools/sprites/extract_sky_audio.py`

### DSE envelope times

DUR in extract_sky_audio.py is the DSE envelope time table from ppmdu's dse_common.cpp (Duration_Lookup_Table, in ms times the split's multiplier; for a multiplier of 0, Duration_Lookup_Table_NullMulti in ms).

### DSE pause durations

DELAY gives the duration of the pause events 0x80 to 0x8F in ticks, at 48 ticks per quarter note.

### DSE chunk format

A DSE chunk has a 16-byte header (label, two params, data length) followed by the data, padded to 4-byte alignment.

### DSE IMA ADPCM samples

A DSE IMA ADPCM sample has a 4-byte preamble followed by the nibbles.

### DSE trk chunk header

A trk chunk starts with four bytes: track id, channel id and two unknown bytes; the events follow.

### DSE parse_sequence result

The sequence parser returns [(tpqn, [track events])] for a .smd (one song) or a .sed (one sound effect per entry of its table).

### DSE .sed table

The offset table of a .sed file ends at its first zero entry.

### DSE file kinds

A sound-effect file (.sed) holds many sounds and a song file (.smd) holds one, so the output file name carries the index of the sound.

## `tools/sprites/extract_sky_effects.py`

### extract_sky_effects.py

Documented in `docs/assets.md Art and font sources`.

### Town trap effects

TRAPS names the traps of the town (FloorTrap in launcher/DungeonFloor.kt) by their game number. The others have no effect of their own (Warp, Trip, Wonder Tile) or one SkyTemple cannot draw (Slumber's is of the other kind).

### TRAP_ANIMATION_INFO

TRAP_ANIMATION_INFO (0xAFD0 in overlay 10 of the USA ROM) is a table of one u16 effect animation id per trap (26 traps).

### EFFECT_ANIMATION_INFO

EFFECT_ANIMATION_INFO (0xFAAC in overlay 10 of the USA ROM) holds 28-byte entries: kind, file, an unknown field, animation, sound and more.

### Effect frame cap

LONGEST_FRAME (30 ticks) caps a frame that the game holds forever (it waits for the next step); it is shown for half a second.

### Effect anchor

ANCHOR (256, 512) is where SkyTemple's frame coordinates put an effect's anchor; every effect is centred on x 256.

## `tools/sprites/extract_sky_maps.py`

### extract_sky_maps.py

Documented in `docs/assets.md Explorers of Sky maps`.

### MAP_BG name case

In the Explorers of Sky ROM the names in bg_list.dat are upper case while the MAP_BG files are lower case, so the script lower-cases names to open them.

### Non-map S99 entries

Entries starting with S99 in the Explorers of Sky map list are the seizure warning and the ratings screen, not maps, and are skipped.

### Palette PNG for maps

Maps with at most 256 colours are stored as palette PNGs, which is lossless and a third of the size.

## `tools/sprites/fetch.py`

### fetch.py

Documented in `docs/assets.md Art and font sources`.

### PokeAPI sprites pin

tools/sprites/fetch.py reads PokeAPI/sprites at one pinned commit for reproducibility. To update, take the new SHA from git ls-remote of PokeAPI/sprites master.

### Back sprites

The app has no battles, so back sprites are not fetched unless --with-back is given; they double the size of assets/sprites.

### Custom forms in sprite manifest

Forms PokeAPI lacks (tools/species/custom_forms.txt) have no PokeAPI sprite, so the manifest lists them with no variants.

## `tools/sprites/fetch_animated.py`

### fetch_animated.py

Documented in `docs/assets.md Animated sprites`.

### Animated sprites pin

SPRITES_COMMIT in fetch_animated.py must equal the one in tools/sprites/fetch.py so stills and animations come from the same PokeAPI/sprites snapshot.

### GIF frame times

GIF frame times come in 10 ms steps, and browsers treat anything under 20 ms as 100 ms, so fetch_animated.py does the same.

### Indexed PNG for sprite sheets

A sprite sheet is stored as an indexed PNG when that loses nothing, because sprites use few colours (about a third of the size).

## `tools/sprites/fetch_cries.py`

### fetch_cries.py

Documented in `docs/assets.md Cries`.

### Cries pin

Documented in `docs/assets.md Cries`.

## `tools/sprites/fetch_hoenn_map.py`

### Hoenn region map

tools/sprites/fetch_hoenn_map.py builds assets/world/map-hoenn.png (256 x 152) from Pokémon Emerald's PokéNav region map in pret/pokeemerald: a sheet of 233 8-bit tiles (palette indices start at 112), 32 colours and a 64 x 64 affine tilemap, cropped from the 512 x 512 background. Nothing is drawn by hand; private use only (Nintendo / Game Freak art).

### Hoenn map pin

fetch_hoenn_map.py reads pret/pokeemerald at pinned commit 731ad5b for reproducibility; to update take the new SHA from git ls-remote of pret/pokeemerald master.

## `tools/sprites/fetch_items.py`

### fetch_items.py

Documented in `docs/assets.md Badges`.

### Item icons pin

SPRITES_COMMIT in fetch_items.py must equal the one in fetch.py so all PokeAPI/sprites art comes from one snapshot.

### Item icon mapping

MAPPED in fetch_items.py maps an app item id that is not a PokeAPI name to the real item whose icon stands for it.

### Rare Candy icon

The everyday candy is shown with the Exp. Candy icon of the games, but its id stays rare-candy because saves and the ledger name it.

### Berry flavour icons

The five flavour berries use the berry that has that flavour in the games: Figy spicy, Wiki dry, Mago sweet, Aguav bitter, Iapapa sour.

### Tab icon items

The first group in SAME holds the icons of the tabs: Home, Modules, Pokémon, Me, and Plan's Planner and Focus.

### Care card icon items

The next group in SAME holds the icons of Care's module cards, its calm break and skills.

### Menu icon items

The next group in SAME holds the icons of menu entries inside the tabs.

### Heal card icon items

The next group in SAME holds the icons of Heal's module cards: Today, Activity and Hydration.

### Evolution item icons

The last group in SAME holds what a Pokémon holds to evolve (the Kecleon Shop's shelf), taken from the evolution data's held items.

### Badge pictures

Badge pictures are sprites/badges/1.png to 77.png at the pinned PokeAPI/sprites commit.

### Evolution item fetch

Stones and similar evolution items are fetched from the evolution data's trigger items, not from a hand list.

## `tools/sprites/fetch_johto_map.py`

### Johto region map

tools/sprites/fetch_johto_map.py builds assets/world/map-johto.png from Pokémon Crystal's Pokégear town map in pret/pokecrystal: 48 tiles (four greys), a 20 x 18 tilemap and per-tile colours (town_map_palette_map.asm and pokegear.pal in Game Boy Color 5-bit RGB), cropped to the map inside its frame (18 x 16 tiles, 144 x 128 px). Nothing is drawn by hand; private use only.

### Johto map pin

fetch_johto_map.py reads pret/pokecrystal at pinned commit 3c813b0 for reproducibility; to update take the new SHA from git ls-remote of pret/pokecrystal master.

## `tools/sprites/fetch_kanto_map.py`

### fetch_kanto_map.py

Documented in `docs/assets.md Kanto map`.

### Kanto map pin

fetch_kanto_map.py reads pret/pokefirered at pinned commit ce6406d for reproducibility; to update take the new SHA from git ls-remote of pret/pokefirered master.

### Kanto map crop

CROP in fetch_kanto_map.py is the map inside the 30 x 20 tile screen: left 3, top 2, width 24, height 16 tiles.

### Kanto map Sevii inset

Tiles past the palette in the Kanto tilemap are the blank Sevii Islands inset; they are painted black first and later filled with the sea's colours.

### Kanto map sea fill

The blank Sevii inset is open sea. The sea is striped, and its column at the right edge of the crop is open water in every row, so each row takes that colour.

### Kanto map town corners

The transparent corners of the town squares take the colour of what is around them, filled a ring at a time.

## `tools/sprites/fetch_pixelarticons.py`

### fetch_pixelarticons.py

Documented in `docs/assets.md Art and font sources`.

### Pixelarticon fill rule

Pixelarticons cut their holes by even-odd fill, so the rasteriser XORs every sub-polygon.

## `tools/sprites/fetch_pmd.py`

### fetch_pmd.py

Documented in `docs/assets.md Mystery Dungeon sprites (preferred)`.

### SpriteCollab pin

Documented in `docs/assets.md Mystery Dungeon sprites (preferred)`.

### AnimData frame duration

Mystery Dungeon games run at 60 frames a second and AnimData durations count those frames, so one unit is 1000/60 ms.

### PMD ANIMS table

ANIMS lists the animations kept, by output folder ('' is assets/pmd itself), each with the AnimData names to try in order.

### SpriteCollab direction rows

SpriteCollab's rows run Down, DownRight, Right, UpRight, Up, UpLeft, Left, DownLeft; the Walk rows kept are Down (0), Right (2) and Up (4).

### PMD idle rows

The Idle rows facing the side and up are kept the same way as Walk; the front-facing one is the idle sheet itself.

### PMD extra animations

The later animations let a Pokémon that stops walking keep facing the way it went, and add one-offs for a spin, waking up, a deep breath and a pose.

### PMD EMOTIONS order

EMOTIONS fixes the order portraits sit in a strip; new faces are added at the end so a strip is read by the place in its own list. Normal comes first and a form without it gets no strip.

### PMD form to species

The species of a form is the longest species identifier its identifier starts with (rattata-alola belongs to rattata).

### SpriteCollab row 0

Row 0 of a SpriteCollab sheet faces down, towards the viewer.

### SpriteCollab credits

Blank credit names are left out rather than shown empty, unless that leaves no credit at all (a form drawn only by a contributor with no name on file); then the contributor's id is the credit, so the licence's credit is never empty.

### PMD shiny sheet

A shiny is a recolour of the same frames, so it is cut the same way and used only if it has the same frames as the regular one.

### PMD shiny portrait strip

The shiny portrait strip keeps its own faces: the app picks a face by its place in whichever strip it draws, and reads the shiny strip's list from the fifth column of portraits.tsv when it differs from the regular one.

## `tools/sprites/fetch_pmd_new.py`

### fetch_pmd_new.py

Documented in `docs/ASSETS/PMD_SPRITE_ACQUISITION.md Pinned SpriteCollab commit`.

### fetch_pmd_new COMMIT

Documented in `docs/ASSETS/PMD_SPRITE_ACQUISITION.md Pinned SpriteCollab commit`.

### fetch_pmd_new FORMS

FORMS maps our form id to (species folder, the form's own folder under sprite/<dex>/ or '' for the species' regular form, name). Shiny coats live in <folder>/0000/0001 for the regular form and <folder>/0001 for a numbered form.

## `tools/sprites/fetch_pret.py`

### fetch_pret.py

Documented in `docs/assets.md Hoenn module art (REP-053)`.

### Hoenn art pin

Documented in `docs/assets.md Hoenn module art (REP-053)`.

## `tools/sprites/fetch_rescue_maps.py`

### fetch_rescue_maps.py

Documented in `docs/assets.md Art and font sources`.

### pmd-red BPL format

A BPL palette file holds u16 banks and u16 animated, then per bank 15 colours (RGBA bytes) after colour 0, then cycle specs (ticks, frames) per bank and the frames.

### pmd-red BMA layer format

A BMA tile layer is run-length coded rows of 12-bit chunk ids, each row XORed with the row above.

### BPC animated tile slots

In a BPC file the animated tile slots follow the static tiles; the render draws frame 0 of each.

### BMA layer order

Layer 0 of a rescue map is on top, so layers are painted in reverse order.

### Palette cycle start

The game loads frame 0 of a palette cycle first, so the render uses it as the base colours.

## `tools/sprites/fetch_scenes.py`

### fetch_scenes.py

Documented in `docs/assets.md Scenes`.

### Scenes pin

fetch_scenes.py reads smogon/pokemon-showdown-client at a pinned commit for reproducibility; to update take the new SHA from git ls-remote of its master.

## `tools/sprites/fetch_sfx.py`

### fetch_sfx.py

Documented in `docs/assets.md Sound effects`.

### Sfx pin

Documented in `docs/assets.md Sound effects`.

### Sfx names

Keys of SOUNDS in fetch_sfx.py must match the names of the Sfx enum in composeApp (Cries.kt); values are files in the hexparrot/pokemon-assets audio package.

### Evolution sfx

The 'evolution' sound is the opening of the games' evolution music: the track is three minutes, and the first 6.5 seconds, faded out, carry the 'What? it is evolving' glow.

### Sfx TRIM

TRIM in fetch_sfx.py lists the only sounds that are cut short, as (start, length) in seconds.

## `tools/sprites/fetch_sinnoh_map.py`

### fetch_sinnoh_map.py

Documented in `docs/assets.md Sinnoh Town Map`.

## `tools/sprites/pick_pmd_sfx.py`

### pick_pmd_sfx.py

Documented in `docs/assets.md Art and font sources`.

## `tools/sprites/pmd_audit.py`

### pmd_audit.py

Documented in `docs/ASSETS/PMD_SPRITE_ACQUISITION.md Pinned SpriteCollab commit`.

### pmd_audit OUR_FORMS

OUR_FORMS maps our form id to (species, SpriteCollab folder under sprite/<dex>/, label) for the forms assets/pmd holds or could hold (see fetch_pmd_new.py).

### pmd_audit LEADS

Documented in `docs/ASSETS/PMD_SPRITE_ACQUISITION.md Pinned SpriteCollab commit`.

### pmd_audit upstream forms

Forms that have sprites upstream but no form in the app are kept out of the app and only listed in the audit record.

## `tools/tiles/cut_tiles.py`

### cut_tiles.py

Documented in `docs/assets.md Art and font sources`.

### Tileset sheet columns

In a Spriters Resource tileset sheet the columns are: legend 0-2, walls 3-5, wall alternates 6-11, ground 12-14, ground alternates 15-20, then the unused ground (one or two blocks) and the water, found as the first block of nine whole tiles that is not sparkle magenta.

## `tools/tiles/extract_rescue_tiles.py`

### extract_rescue_tiles.py

Documented in `docs/assets.md Art and font sources`.

### TILESET_FILE

TILESET_FILE maps each Red Rescue Team tileset to the fon/cel/cex file id it uses (gUnknown_8108EC0 in pret/pmd-red src/dungeon_info.c).

### Dungeon archive file start

A file in the Red Rescue Team dungeon archive starts 0x10 bytes after a SIRO/SIR0 header when it has one, else at its offset, as the game reads it.

### AT4PX decompression

decompress_at is a port of DecompressAT in pret/pmd-red src/decompress_at.c and handles AT4PX and AT3PX.

### AT decompress slice

The initial 0x20000 byte slice can be shorter than a big compressed file, so the decompressor re-slices exactly the length it needs.

### Dungeon palette index 0

Palette index 0 of a dungeon tileset is the black backdrop; the file's colour k is palette index k (pret/pmd-red src/dungeon_vram.c sub_803E874).

### Dungeon palette animation

Animated colours: palette index 160 + i takes the first frame of entry i of the tileset's canm file.

### Dungeon autotile mask

The autotile mask of a cell follows the game's sub_80498A8 in pret/pmd-red src/dungeon_map_access.c, with terrain 0 wall, 1 ground, 2 water.

### Floor tileset byte

FloorProperties.tileset is byte 2 of each 28-byte row of a dungeon's floor data in pret/pmd-red.

## `tools/tiles/extract_sky_floor.py`

### extract_sky_floor.py

Documented in `docs/assets.md Art and font sources`.

### FLOOR_ITEMS

FLOOR_ITEMS maps our Bag items to the Explorers of Sky item drawn for them. Berries go by flavour (Cheri spicy, Sitrus sour, Pecha sweet, Chesto dry, Rawst bitter); the game has one bottle for its vitamins, so all six of ours use it; Poké is Poké.

## `tools/tiles/extract_sky_tiles.py`

### extract_sky_tiles.py

Documented in `docs/assets.md Explorers of Sky maps`.

### Autotile neighbourhoods

An autotile can tell 47 neighbourhoods apart: a diagonal neighbour bit is kept only when both squares beside it are set.

### Sky tilesets without autotile rules

A tileset without autotile rules (dma) is not a floor tileset and is skipped.

### Sky tileset aliases

The game ships many placeholder copies of one tileset; the script keeps the first and records the others as aliases of it.

### Autotile palette PNG

A tileset has far fewer than 256 colours, so its autotile PNG is saved as a palette image, losslessly, at a quarter of the size of RGB.

### Dungeons using each tileset

To find which dungeons use each tileset, the game's dungeon list (arm9) gives each dungeon's floors in mappa_s.bin and each floor layout names its tileset; ids of 170 and up are the pit and boss floors, which have none here.

## `tools/weather/decode_bg.py`

### decode_bg.py

tools/weather/decode_bg.py decodes Red Rescue Team ground backgrounds (.bpl palette, .bpc tiles and chunks, .bma map, .bpa tile animation in pret/pmd-red data/map_bg) following the decompilation's own code in src/ground_bg.c, to check the weather art against the game's data.

### WEATHER_BGS

WEATHER_BGS lists the gMapFilesTable entries the weather kinds of pret/pmd-red src/ground_weather.c use (kind to bpa files by slot); see docs/pokelife/FEATURES/LAUNCHER.md section 4.4.

### BPL palette layout

Each BPL palette is 15 colours of 4 bytes (r, g, b, unused); colour 0 is transparent.

### BPC tile layout

In a BPC file tile 0 is blank and not stored; then numTiles - 1 tiles of 32 bytes (4 bits a pixel); then the chunk definitions.

### BmaLayerNrlDecompressor

A BMA layer is rows of mapWidthChunks chunk numbers: the first row is plain and the others change the row above (xor), as the game's code does.

## `tools/weather/extract_ramps.py`

### Weather colour ramps

tools/weather/extract_ramps.py writes the dungeon weather colour ramps (colvec, data/dungeon/colvec.inc of pret/pmd-red) to assets/weather/ramps.tsv. Each weather has a 256-entry table per channel; a colour goes through it per channel as out = ramp[value] * brightness / 256, with brightness at its full 31 (5 bits). On a weather change the ramp moves to the new table by at most 10 a frame, up to 64 frames (about a second).

## `tools/weather/inspect_sheets.py`

### inspect_sheets.py

tools/weather/inspect_sheets.py inspects the weather sheets in assets/weather and the owner's rips in assets/weather/source: sizes, frame grids, and per frame the bounding box, opaque pixel count, distinct colours and whether it repeats another frame. It prints a markdown table; the cut grids are those of tools/sprites/cut_weather.py.

### inspect_sheets SHEETS

SHEETS maps a weather sheet name to (frame width, frame height, columns, rows, frames used).

## `tools/weather/match_sheets.py`

### match_sheets.py

tools/weather/match_sheets.py finds which of the game's weather layers each of the snow and blizzard sheets is, by comparing the shape of every opaque pixel of a frame with the same-sized window of every tile-animation frame of the layers decoded by tools/weather/decode_bg.py. It prints, for frames 0 and 5 of each sheet, the layer, animation frame and offset of the exact match.

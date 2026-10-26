---
name: game-assets
description: Where the real game assets come from and how to fetch or regenerate them: the Dex data generator, sprites, animated sprites, cries, scenes, item icons, PC box wallpapers and the Home Region curation kit. Use before running or changing tools/species or tools/sprites, adding or replacing anything in assets/, or regenerating DexData.kt.
---

# Game assets

The art rule in `CLAUDE.md` always applies: real game assets only. Sources and licences are in `docs/assets.md`.

## Token care
- `DexData.kt` is generated and about 340 KB: never read it whole; grep for the species or form you need. Never edit it by hand: change `generate.py` and regenerate (CI fails if regenerating changes anything).
- `assets/sprites`, `assets/animated` and `assets/cries` hold thousands of files: never list them; grep `manifest.tsv` or `animated.tsv`, or count with `ls | wc -l`. Open a sprite image only when you must see it.
- The fetch scripts download a lot: run them only when the assets have to change, and keep `tools/species/cache` (git-ignored) between runs.

## Dex data and assets
- `tools/species/generate.py`: stdlib Python that regenerates `DexData.kt` from the public PokeAPI CSVs. Run `python3 -I tools/species/generate.py`; it caches downloads in `tools/species/cache` (git-ignored).
- `assets/sprites/`: Pokémon sprites (96×96 PNG, `<formId>.png` and `<formId>-shiny.png`, plus `manifest.tsv`) from the public PokeAPI/sprites repo, fetched with `python3 -I tools/sprites/fetch.py` (needs `tools/species/cache`, so run the species generator first). Form ids match `FormInfo.id`.
- `assets/animated/` and `assets/cries/`: the real Black/White animated sprites (Gen 1 to 5, `python3 -I tools/sprites/fetch_animated.py` converts the PokeAPI GIFs to sprite sheets plus `animated.tsv`; needs Pillow for that step only) and the real cries of species 1 to 1025 (`python3 -I tools/sprites/fetch_cries.py`). `AnimatedSprite` plays a sheet (still `Sprite` for species without one), `CryPlayer` plays a cry (Android MediaPlayer, web audio element, none on desktop); both honour Sound and Reduce motion.
- `assets/sfx/`: a few real game sound effects (select, jump, heal, escape, the Pokémon Center jingle) from hexparrot/pokemon-assets, as mono Ogg (`python3 -I tools/sprites/fetch_sfx.py`, needs ffmpeg). Each is an `Sfx` value played by `CryPlayer.effect` beside the cry player, so a cry and an effect never cut each other off.
- `assets/pmd/`: the preferred Pokémon sprites, Mystery Dungeon style from the PMD SpriteCollab (pinned commit; `python3 -I tools/sprites/fetch_pmd.py`, needs Pillow and `assets/sprites/manifest.tsv`). Sheets of the front-facing Idle row in the `animated.tsv` format (`pmd.tsv`) plus `credits.tsv`; the Sleep, Eat and Hop rows in `pmd/sleep`, `pmd/eat`, `pmd/hop` (each with its own `.tsv`, played by `AnimatedSprite`'s `action`); and `pmd/portraits` (one 40 px strip per form of the faces in `fetch_pmd.py`'s `EMOTIONS`, listed with their artists in `portraits.tsv`, drawn by `Portrait`). `AnimatedSprite`, `Sprite` and the widget art try `pmd` first, then `animated`, then `sprites`. A full run takes about 25 minutes. CC BY-NC 4.0: each Pokédex entry credits the artists.
- `assets/scenes/`: real Pokémon scene backdrops (Showdown, `python3 -I tools/sprites/fetch_scenes.py`) for the buddy's place on Home and in the widget; `BuddyScene` (app-core) picks one from the buddy's type and dims it at night.
- `assets/items/`: real item icons from PokeAPI/sprites (`python3 -I tools/sprites/fetch_items.py`), used for the tab icons and the bag.
- `assets/backgrounds/`: Black/White PC box wallpapers from PKHeX, tiled behind each tab by `PixelBackground` (see `docs/assets.md` for sources and licences).

## Home Region curation
- `docs/home-region-curation-prompt.md` and `docs/home-region-candidates.md`: the kit for curating the Home Region (`tools/species/home-region.txt`) in a separate chat. Regenerate the candidates table with `./gradlew :pokemon-core:curationReport`.

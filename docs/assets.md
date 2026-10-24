# Art and font sources

All art is real game assets, never generated (the owner's rule). This is a private project; check each source's terms before any public release.

| What | Where it is | Source |
|---|---|---|
| Pokémon sprites (front and shiny) | `assets/sprites/` | PokeAPI/sprites, `tools/sprites/fetch.py` |
| Item icons (bag, tab icons) | `assets/items/` | PokeAPI/sprites, `tools/sprites/fetch_items.py` |
| Mystery Dungeon sprites (preferred) | `assets/pmd/` | The [PMD SpriteCollab](https://github.com/PMDCollab/SpriteCollab) at commit `cf6c95f`: the official Pokémon Mystery Dungeon sprites (Chunsoft) and community sprites in the same style, for 961 species and 113 forms (916 with a shiny). `tools/sprites/fetch_pmd.py` keeps the front-facing row of each Idle animation (Walk where there is none), cut to the frames' box, as sprite sheets plus `pmd.tsv`; `credits.tsv` names each sprite's artists. **CC BY-NC 4.0: non-commercial use, with credit** (the Pokémon page shows it). The app uses them wherever a Pokémon is drawn and falls back to the rows below. |
| Animated sprites | `assets/animated/` | Pokémon Black/White animated GIFs from PokeAPI/sprites (same pinned commit), converted frame by frame to sprite-sheet PNGs by `tools/sprites/fetch_animated.py`. Gen 1 to 5 only. Game Freak's art; private use only. |
| Cries | `assets/cries/` | The latest cries from [PokeAPI/cries](https://github.com/PokeAPI/cries) (pinned commit), `tools/sprites/fetch_cries.py`. Game Freak's audio; private use only. |
| Scenes | `assets/scenes/` | Battle backdrops (meadow, forest, route, mountain, volcano cave, beach, desert, thunder plains, ice cave, damp cave) from [smogon/pokemon-showdown-client](https://github.com/smogon/pokemon-showdown-client) `play.pokemonshowdown.com/fx` (pinned commit), `tools/sprites/fetch_scenes.py`. Game Freak style art by the Showdown artists; private use only. |
| Display font (titles, buttons, numbers) | `assets/fonts/pokemon_dp.ttf`, `pokemon_dp_bold.ttf` | `Pokemon-DP` and `Pokemon-DP-Bold` from [hexparrot/pokemon-assets](https://github.com/hexparrot/pokemon-assets) (a fork of itsjavi/pokemon-assets, MIT for the package; the fonts are recreations of the games' fonts). Licence file next to them. |
| Backgrounds | `assets/backgrounds/bw01, bw07, bw08, bw11, bw12, bw15.png` | The Pokémon Black/White PC box wallpapers (01, 07, 08, 11, 12, 15), from [kwsch/PKHeX](https://github.com/kwsch/PKHeX) `PKHeX.Drawing.Misc/Resources/img/box/bw` at commit `bfe89c7`. PKHeX is GPL-3 (licence next to them); the art is Game Freak's. Private use only. Tiled, mirrored and drawn at a whole-number scale. |
| Body font | `assets/fonts/nunito_regular.ttf`, `nunito_semibold.ttf`, `nunito_bold.ttf` | Static instances of Nunito (Google Fonts, SIL OFL 1.1), cut from the variable font with fontTools. Licence in `LICENSE-nunito-OFL.txt`. |

The same package also has GBC, GBA and DS fonts, menu sprites, ribbons, marks, type badges (SVG) and sound effects, if we want more.

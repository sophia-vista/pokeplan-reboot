# Pokémon influence in the modules: open ideas and places (proposals)

**Status:** proposals. **An idea is not a rule.** It becomes one only when the owner approves it and it is written into the owning module file (`MODULES/*.md`) or `POKEMON_ECOSYSTEM.md`. Backlog: REP-053. Built so far: the Secret Base room (`MODULES/BASE.md` 9.5) and goal badges with the Hall of Fame (`MODULES/PLAN.md` 9.1); only what is still open is listed here.

Each module already has its own way to get Pokémon (Focus and Task Eggs, Care Play, Field Research, Ledger Research, Habitat Discovery, Postcard Trail, Exploration Finds). This file is about what Pokémon and the games' world do in a module *after* that.

## 1. Rules every idea has to keep

- **One place only** (`POKEMON_ECOSYSTEM.md` 2.2): a place below shows the module's own party. It is never a new slot a Pokémon is assigned to.
- **No upkeep loop** (8.4): nothing wilts, decays, gets hungry or asks to be visited.
- **Privacy** (13): a life module still pays through a minimal event with no figure, name or private word. MINT ideas count actions, never amounts.
- **GEAR stays visual** (GEAR.md section 6): no progression, items or rewards.
- **Real assets only**, per `ASSETS_AND_AUDIO.md` 4.0: Pokémon are Mystery Dungeon first; a module's places, rooms and items are mainline art, mostly Generation 3; Mystery Dungeon places and tilesets are for WORLD and the launcher only (the team base, Pelipper Post Office, Friend Areas, the Duskull and Felicity banks, Chansey's Day Care and the Sky hot spring stay WORLD's). An idea with no pixel art is dropped or changed, not drawn.
- Specified elsewhere, so not repeated here: adapted abilities, special evolutions, Ribbons and Marks, held items (`POKEMON_ECOSYSTEM.md` 8.5 and 20.1).

## 2. Where the art can come from

Fetched already: the Hoenn Secret Base decorations, tilesets, layouts and Berry tree sheets in `assets/hoenn/`, the Berry and Mail icons in `assets/items`, the badges in `assets/badges` (Gradle does not bundle the Berry and Mail icons yet).

| Source | What it has | ROM needed | State |
|---|---|---|---|
| `pret/pokeemerald` | Pokéblock art, Mail papers, object sprites, the tilesets | No | Verified; the tiles for the chunk 7 rooms are not extracted yet |
| `pret/pokecrystal` | Mom's sprite, Mail borders | No | Verified. The bedroom decorations (bed, carpet, poster, dolls) are not checked |
| `pret/pokeplatinum` | About 4,900 PNGs: Poffin case art, a Pokétch graphics folder | No | Folder verified; which Pokétch app screens are there is not checked |
| `pret/pokeheartgold` | About 3,400 PNGs, the Pokéwalker course data | No for those | The Pokéwalker route pictures were not found as PNGs; they may need the owner's ROM |
| Black/White (Gen 5) | 2D menus and icons only | Owner's ROM | No public decompilation with art; a decoder for the 2D formats would be new work |

Limits that shape the ideas:

- **Gen 1 to 3 rooms are tiles, not pictures.** A room is put together from a tileset and a layout by a small tool (the Secret Base render in `composeApp/src/jvmTest/.../HoennRoom.kt` does it), or laid out by the app from the decoration sprites.
- **Gen 4 and 5 maps and furniture are 3D.** Only their 2D art (menus, icons, sprites) is usable, so Amity Square, the Underground bases, Join Avenue and the Villa are out.
- Each asset used gets its line in `docs/assets.md`. All of it is Nintendo art, on the same private-use footing as the Sky rip.

## 3. Ideas still open

| # | Module | Idea (game) | What happens | Art | Verdict |
|---|---|---|---|---|---|
| A2 | BASE | Berry plots (Gen 3 soil) | Upkeep done waters a plot; berries grow in real hours and go to the Bag. They never wilt | pokeemerald trees, PokeAPI icons | Buildable (chunk 4) |
| A4 | LINK | Mail (Gen 2 to 4) | A party Pokémon carries a follow-up as a Mail item; finishing it delivers the letter. The paper is a cosmetic choice | PokeAPI icons, pret papers | Buildable |
| A5 | MINT | Gimmighoul coins | One coin per paid Mint action; 999 evolve Gimmighoul into Gholdengo. The coin exists as an item (Kecleon Shop, Paldea floors); a coin per paid Mint action is not built | In `assets/items` | Buildable |
| A6 | MINT | Mom's savings (Gold/Silver) | Reconciling and reviewing fill a jar; Mom sometimes sends a decoration or an item | pokecrystal sprite, the decorations | Chunk 6 |
| A7 | CARE | Cooking | Berries are cooked with the Care party; each recipe fills a small dex. One moment, no hunger | Curry is 3D only: use Poffins (pokeplatinum) or Pokéblocks (pokeemerald) | Buildable as Poffins or Pokéblocks |
| A8 | GEAR | Pokétch apps, Rotom forms | Tools drawn as Pokétch apps; a Rotom helper changes form per tool. Visual only | pokeplatinum folder, SpriteCollab Rotom | Likely; app screens not checked |
| A9 | HEAL | Stroll (Pokéwalker) | Steps become Watts for the Heal lead; Watts open named routes with their own finds | Route pictures not found | Uncertain |
| A10 | HEAL | Sleep styles (Pokémon Sleep) | The Heal party is shown asleep on a finished sleep record | SpriteCollab has one sleep animation per species | Keep the sleeping party; drop the Sleep Dex |

Rejected: Friend Safari for LINK (LINK.md forbids turning real people into something to collect); the Game Corner for MINT (gambling beside real money records).

## 4. Places still open

A place is a real location from the games that a module opens onto or keeps a corner of. The module's party stands in it; the records stay ordinary rows. Each is a room drawn with the Secret Base render's approach, behind its module's existing screen.

| # | Module | Place (game) | What it is for | Verdict |
|---|---|---|---|---|
| P2 | BASE | The player's bedroom (Johto) | An alternative or second room: the bed, carpet, poster and dolls Mom buys (A6). pokecrystal art | Check the art first |
| P3 | PLAN | Day Care (Hoenn Route 117) | Where Focus and Task Eggs wait. The Day-Care Man stands outside when one is ready | Strong fit with Eggs |
| P5 | PLAN | Trainers' School (Rustboro) | The weekly review, at the blackboard | Small; only if reviews want a scene |
| P6 | CARE | Hot spring (Lavaridge) | Care's quiet place: the party soaks while a routine is done | Good fit |
| P7 | CARE | Berry Blender or Poffin house | A7's kitchen | With A7 |
| P8 | HEAL | Pokémon Center | The counter where an activity is logged; the heal jingle is in `assets/sfx` | The most recognisable place |
| P9 | HEAL | Safari Zone or National Park | Where steps are walked (with A9, or on its own as the walk scene) | After P8 |
| P10 | HEAL | Professor's lab | Field Research's home: the research notes and the discovery | Small |
| P11 | MINT | Poké Mart counter | Mint's counter for reconciling and review (the mainline games have no bank) | Small; P12 may be enough |
| P12 | MINT | Department Store (Lilycove) | The shop where Poké buys decorations, as its rooftop sale does in Hoenn; a cosmetic sink for Poké | Chunk 6 |
| P13 | LINK | Pokémon Center upstairs (Union Room, Cable Club) | Link's home: Mail (A4) and the Postcard Trail leave from here, and a trade evolution is done here with a Link Cord | Buildable |
| P15 | WORLD | Friend Areas (Rescue Team) | Stay WORLD's (the "Friend Area camps" WORLD.md lists for later); not a view of the PC | WORLD's to schedule |
| P16 | Me | Trainer Card | The profile as the games' card: name, rank, badges, time played. pokeemerald art, not checked | Small |
| P17 | GEAR | Radio Tower, Pokétch Company | A header picture only | Low value |

Not possible with real art: Amity Square, the Sinnoh Underground, Join Avenue, the Resort Area Villa (all 3D).

## 5. Suggested order

Chunk 4 (A2), then P8 and P3, which reuse the Secret Base room tool; A6 and P12 once decorations exist (chunk 6); A5 at any time.

## 6. Decisions

- **Art**: rule 4.0 of `ASSETS_AND_AUDIO.md`. Places in modules are mainline rooms; the Mystery Dungeon banks and Friend Areas stay with WORLD.
- **Decorations are earned three ways**: from BASE upkeep, bought with Poké at the Department Store (P12), and as Mom's gifts for MINT actions (A6).
- **Module rooms are drawn at 5x on a phone, and at 4x when the screen is narrower than about 1000 px** (whole-number scales only), flush with the dialogue frame (padding equal to the frame: 8 dp at the sides, 6 dp above and below). `ScreenshotTest.secretBaseRoom` renders it.
- **A Generation 3 room with Mystery Dungeon Pokémon in it is acceptable.** Both are drawn at the same pixel size, so one room pixel is one sprite pixel.
- **Decorations use the 24 px sprites in `assets/hoenn/decorations/`** (palette colour 0 is the see-through colour).

Still not decided: every number (how many chores a decoration takes, prices, how often Mom sends one), and whether each open idea is wanted at all. An idea is approved only when it is written into its owning module file.

## 7. Build plan (chunks for the next sessions)

Chunks 1 to 3 (the art fetch, the scale test, the Secret Base room) and 5 (goal badges) are built; 4, 6 and 7 are open. One chunk per session. Each starts with `.claude/hooks/claim.sh "REP-053 <chunk>"` and ends by updating the REP-053 row. **A chunk that adds a rule writes it into the owning spec first** (`MODULES/BASE.md`, `MODULES/MINT.md`), with its numbers marked *(tune)*, and asks the owner the numbered questions before building.

| # | Chunk | What to do | Done when |
|---|---|---|---|
| 4 | Berry plots (A2) | Spec in `MODULES/BASE.md`: plots, real-hour growth, no wilting, berries to the Bag. Check `docs/economy.md` for what berries already do and where they come from, so the plot does not flood it; re-run `:plan-core:simulateEconomy` if a number changes what is paid | `:base-core:jvmTest :app-core:jvmTest :data:jvmTest` green (a numbered `.sqm` migration and `databases/N.db` if a table is added), screenshots in both palettes |
| 6 | Department Store and Mom (P12, A6) | Spec in `MODULES/MINT.md` and `BASE.md`: the shop sells decorations for Poké; Mom's jar fills from the two paid MINT actions (counted, never amounts; `MintEvent` stays as it is) and sends a decoration through `GameStore.grantDecoration` | As 4 |
| 7 | The other places (P3 Day Care, P8 Pokémon Center, P6 hot spring, P13 Union Room) | One at a time, each asked of the owner first | Per place |

Not scheduled: A4 Mail, A5 Gimmighoul coins, A7 cooking, A8 Pokétch, A9 Stroll, A10 sleeping party. Ask the owner before starting any of them.

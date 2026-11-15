# Pokémon influence in the modules: ideas and places (proposals)

**Status:** proposals only, written 14 November 2026 from the owner's question "further Pokémon integration ideas for the modules, like Secret Bases for BASE". **Nothing here is decided or specified.** An idea becomes a rule only when the owner approves it and it is written into the owning module file (`MODULES/*.md`) or `POKEMON_ECOSYSTEM.md`. Backlog: REP-053.

Each module already has its own way to get Pokémon (Focus and Task Eggs, Care Play, Field Research, Ledger Research, Habitat Discovery, Postcard Trail, Exploration Finds). This file is about what Pokémon and the games' world do in a module *after* that.

## 1. Rules every idea has to keep

- **One place only** (`POKEMON_ECOSYSTEM.md` 2.2): a place below shows the module's own party. It is never a new slot a Pokémon is assigned to.
- **No upkeep loop** (8.4): nothing wilts, decays, gets hungry or asks to be visited.
- **Privacy** (13): a life module still pays through a minimal event with no figure, name or private word. MINT ideas count actions, never amounts.
- **GEAR stays visual** (GEAR.md 23): no progression, items or rewards.
- **Real assets only**: every idea names its source in section 2. An idea with no pixel art is dropped or changed, not drawn.
- **Which art goes where** (`ASSETS_AND_AUDIO.md` 4.0, owner, 14 November 2026): Pokémon sprites and portraits are Mystery Dungeon first; Mystery Dungeon places and tilesets are for WORLD and the launcher only; a module's places, rooms and items are mainline art, mostly Generation 3. The tables below follow it.
- Already specified elsewhere, so not repeated here: adapted abilities (Pickup, Honey Gather...), special evolutions done with the phone or the step counter, Ribbons and Marks, held items (`POKEMON_ECOSYSTEM.md` 8.5 and 20.1).

## 2. Where the art can come from

Checked on 14 November 2026 against the public repositories (file lists, not every image).

| Source | What it has | ROM needed | State |
|---|---|---|---|
| `assets/rescue-maps/` (in the repo) | 245 Red Rescue Team maps as whole pictures: 57 Friend Areas, Pelipper Post Office (outside and inside), Makuhita Dojo, Pokémon Square, Whiscash Pond, the team bases | No | Already fetched. **WORLD and the launcher only** |
| `assets/sky-maps/` (in the repo) | 440 Explorers of Sky maps, by map code | Owner's ROM, already ripped | Already cut. **WORLD and the launcher only** |
| PokeAPI/sprites | `badges/` (Gym badges), every Berry icon, every Mail icon, Gimmighoul Coin | No | Verified. Only 6 berries and the coin are in `assets/items` today |
| `pret/pokeemerald` | 43 Secret Base decoration sprites (dolls, mats, desks, plants, tents, bricks), the Secret Base tilesets (per cave, tree and shrub type), 32 Berry tree growth sheets, Pokéblock art, Mail papers, object sprites | No | Verified |
| `pret/pokecrystal` | Mom's sprite, Mail borders | No | Verified. The bedroom decorations (bed, carpet, poster, dolls) are not checked yet |
| `pret/pokeplatinum` | About 4,900 PNGs: Poffin case art, a Pokétch graphics folder | No | Folder verified; which Pokétch app screens are there is not checked |
| `pret/pokeheartgold` | About 3,400 PNGs, the Pokéwalker course data | No for those | The Pokéwalker route pictures were not found as PNGs; they may need the owner's ROM |
| Black/White (Gen 5) | 2D menus and icons only | Owner's ROM | No public decompilation with art. `ndspy` (already used by `tools/rip/rip_sky.py`) unpacks it; a decoder for the 2D formats would be new work |

Limits that shape the ideas:

- **Gen 1 to 3 rooms are tiles, not pictures.** A Hoenn room has to be put together from a tileset and a layout by a small tool (as `tools/tiles/` does for dungeons), or laid out by the app from the decoration sprites.
- **Gen 4 and 5 maps and furniture are 3D.** Only their 2D art (menus, icons, sprites) is usable, so Amity Square, the Underground bases, Join Avenue and the Villa are out as places.
- **Two drawing scales.** Mainline overworld art uses 16-pixel tiles; the SpriteCollab Pokémon are Mystery Dungeon scale. Every module place mixes the two (rule 4.0), so a test render of one room comes first, to settle the scale and where the Pokémon stand.
- All of it is Nintendo art, on the same private-use footing as the Sky rip. One `tools/sprites/fetch_pret.py` with pinned commits would cover Gens 1 to 4, and each asset used gets its line in `docs/assets.md`.

## 3. Ideas, round one

| # | Module | Idea (game) | What happens | Art | Verdict |
|---|---|---|---|---|---|
| A1 | BASE | Secret Base room (Hoenn) | Base's party lives in a furnished room. Chores and maintenance earn decorations, the user places them. The lead's type picks the spot: tree, shrub or cave | pokeemerald decorations and tilesets | Buildable; needs the scale test |
| A2 | BASE | Berry plots (Gen 3 soil) | Upkeep done waters a plot; berries grow in real hours and go to the Bag. They never wilt | pokeemerald trees, PokeAPI icons | Buildable |
| A3 | PLAN | Gym badges for goals | A goal's milestones are badges; a finished goal is a Hall of Fame entry with the Plan party that was there | PokeAPI badges | Buildable, one fetch |
| A4 | LINK | Mail (Gen 2 to 4) | A party Pokémon carries a follow-up as a Mail item; finishing it delivers the letter. The paper is a cosmetic choice | PokeAPI icons, pret papers | Buildable |
| A5 | MINT | Gimmighoul coins | One coin per paid Mint action; 999 evolve Gimmighoul into Gholdengo (the canonical rule) | In `assets/items` | Buildable now |
| A6 | MINT | Mom's savings (Gold/Silver) | Reconciling and reviewing fill a jar; Mom sometimes sends a decoration or an item | pokecrystal sprite, the A1 decorations | Buildable, after A1 |
| A7 | CARE | Cooking | Berries are cooked with the Care party; each recipe fills a small dex. One moment, no hunger | Curry is 3D only: use Poffins (pokeplatinum) or Pokéblocks (pokeemerald) | Buildable as Poffins or Pokéblocks |
| A8 | GEAR | Pokétch apps, Rotom forms | Tools drawn as Pokétch apps; a Rotom helper changes form per tool. Visual only | pokeplatinum folder, SpriteCollab Rotom | Likely; app screens not checked |
| A9 | HEAL | Stroll (Pokéwalker) | Steps become Watts for the Heal lead; Watts open named routes with their own finds | Route pictures not found | Uncertain |
| A10 | HEAL | Sleep styles (Pokémon Sleep) | The Heal party is shown asleep on a finished sleep record | Pokémon Sleep has no pixel art; SpriteCollab has one sleep animation per species | Keep the sleeping party; drop the Sleep Dex (nothing to collect) |

Rejected: Friend Safari for LINK (LINK.md forbids turning real people into something to collect); the Game Corner for MINT (gambling beside real money records).

## 4. Places, round two

A place is a real location from the games that a module opens onto or keeps a corner of, the way BASE can be a Secret Base. The module's party stands in it; the records stay ordinary rows.

| # | Module | Place (game) | What it is for | Art | Verdict |
|---|---|---|---|---|---|
| P1 | BASE | Secret Base (Hoenn) | A1: the room, with the berry plot (A2) outside it | pokeemerald | First choice |
| P2 | BASE | The player's bedroom (Johto) | An alternative or second room: the bed, carpet, poster and dolls Mom buys (A6) | pokecrystal, not checked | Check the art first |
| P3 | PLAN | Day Care (Hoenn Route 117) | Where Focus and Task Eggs wait. The Day-Care Man stands outside when one is ready, as in the games | pokeemerald tiles and sprites | Strong fit with Eggs |
| P4 | PLAN | Gym and Hall of Fame | A3: the badge case, and the room finished goals are recorded in | PokeAPI badges, pokeemerald Hall of Fame art (not checked) | Buildable |
| P5 | PLAN | Trainers' School (Rustboro) | The weekly review, at the blackboard | pokeemerald tiles | Small; only if reviews want a scene |
| P6 | CARE | Hot spring (Lavaridge) | Care's quiet place: the party soaks while a routine is done. Visitors can turn up here | pokeemerald tiles | Good fit |
| P7 | CARE | Berry Blender or Poffin house | A7's kitchen | pokeemerald, pokeplatinum | With A7 |
| P8 | HEAL | Pokémon Center | The counter where an activity is logged; the heal jingle is already in `assets/sfx` | pokeemerald tiles | The most recognisable place in the list |
| P9 | HEAL | Safari Zone or National Park | Where steps are walked (with A9 if its art is found, or on its own as the walk scene) | pokeemerald tiles | After P8 |
| P10 | HEAL | Professor's lab | Field Research's home: the research notes and the discovery | pokeemerald tiles | Small |
| P11 | MINT | Poké Mart counter | Mint's counter for reconciling and review. The mainline games have no bank, and the Mystery Dungeon banks stay with WORLD (rule 4.0) | pokeemerald tiles | Small; P12 may be enough on its own |
| P12 | MINT | Department Store (Lilycove) | The shop where Poké buys decorations, as its rooftop sale does in Hoenn. Gives Poké a cosmetic sink | pokeemerald tiles | With A1 |
| P13 | LINK | Pokémon Center upstairs (Union Room, Cable Club) | Link's home: Mail (A4) and the Postcard Trail leave from here, and a trade evolution is done here with a Link Cord. (The Pelipper Post Office is Mystery Dungeon, so WORLD's.) | pokeemerald tiles | Buildable |
| P15 | WORLD | Friend Areas (Rescue Team) | Stay WORLD's, as the "Friend Area camps" WORLD.md already lists for later; not a view of the PC (rule 4.0) | 57 maps in the repo | WORLD's to schedule |
| P16 | Me | Trainer Card | The profile as the games' card: name, rank, badges (A3), time played | pokeemerald trainer card art (not checked) | Small |
| P17 | GEAR | Radio Tower, Pokétch Company | A header picture only | pokecrystal or pokeplatinum | Low value |

Left to WORLD and the launcher by rule 4.0: the team base, Pelipper Post Office, Friend Areas, the Duskull and Felicity banks, Chansey's Day Care, the Sky hot spring.

Not possible with real art: Amity Square, the Sinnoh Underground, Join Avenue, the Resort Area Villa (all 3D).

## 5. Suggested order

1. **Test render** of P1: a Hoenn Secret Base room with the decorations and the Base party's SpriteCollab sprites, both palettes. It settles the scale for every module place.
2. **One fetch script** for pret (pinned commits) and the missing PokeAPI folders (badges, berries, mail), then A1, A2, A3.
3. P8 (Pokémon Center for HEAL) and P3 (Day Care for PLAN), which reuse the room tool from step 1.
4. A6 and P12 (Mom and the Department Store) once decorations exist; A5 at any time.

## 6. Decisions

- *(Owner, 14 November 2026)* Art: rule 4.0 of `ASSETS_AND_AUDIO.md`. Places in modules are mainline rooms; the Mystery Dungeon banks and Friend Areas stay with WORLD.
- *(Owner, 14 November 2026)* **Decorations are earned three ways**: from BASE upkeep, bought with Poké at the Department Store (P12), and as Mom's gifts for MINT actions (A6).
- *(Owner, 15 November 2026)* **Module rooms are drawn at 5x on a phone, and at 4x when the screen is narrower than about 1000 px** (a 176 px Secret Base room is 880 px at 5x, 704 px at 4x; whole-number scales only), flush with the dialogue frame (padding equal to the frame: 8 dp at the sides, 6 dp above and below). The test render settled it (`ScreenshotTest.secretBaseRoom`, chunk 2).
- *(Owner, 15 November 2026)* **A Generation 3 room with Mystery Dungeon Pokémon in it is acceptable** (rule 4.0 already puts Pokémon in PMD; the room is mainline). Both are drawn at the same pixel size, so one room pixel is one sprite pixel.
- *(Owner, 15 November 2026)* **Decorations use the 24 px sprites in `assets/hoenn/decorations/`** (palette colour 0 is the see-through colour); the in-room tiles of the tileset are not extracted.
- *(Default applied, not answered)* Order of work: section 5.

Still not decided: every number (how many chores a decoration takes, prices, how often Mom sends one), and whether each idea beyond A1 to A3 is wanted at all. An idea is approved only when it is written into its owning module file.

## 7. Build plan (chunks for the next sessions)

One chunk per session, in this order. Each starts with `.claude/hooks/claim.sh "REP-053 <chunk>"` and ends by updating this section's Status column and the REP-053 row. **A chunk that adds a rule writes it into the owning spec first** (`MODULES/BASE.md` for BASE, `MODULES/PLAN.md`, `MODULES/MINT.md`), with its numbers marked *(tune)*, and asks the owner the numbered questions before building. Chunks 1 and 2 add no rule and can be built straight away.

| # | Chunk | What to do | Done when | Status |
|---|---|---|---|---|
| 1 | Fetch the art | `tools/sprites/fetch_pret.py` in the style of `fetch_rescue_maps.py` (stdlib Python, pinned commits, skips existing files, writes a `manifest.tsv`): from `pret/pokeemerald`, `graphics/decorations/*.png` and their `.pal` where a sprite is indexed, `data/tilesets/primary/secret_base` and `data/tilesets/secondary/secret_base/*`, `graphics/object_events/pics/berry_trees/*.png`, into `assets/hoenn/` (`decorations/`, `secret-base/`, `berry-trees/`). Extend `fetch_items.py` (or a sibling) for every `*-berry.png` and `*-mail.png` into `assets/items`, and PokeAPI `sprites/badges/` into `assets/badges/`. Add each to `docs/assets.md` and the `game-assets` skill. Check the PNGs open with correct colours (pret PNGs are indexed; some need their `.pal`). | The files are in `assets/` with manifests and sources listed. No app code | **Done** (0.194.0): `assets/hoenn/` (256 files incl. the 24 room layouts, `layouts.tsv`), 105 Berry and Mail icons in `assets/items`, 77 badges in `assets/badges`; Gradle does not bundle the Berry and Mail icons yet. The decoration PNGs are 24 px menu sprites (palette colour 0 is the see-through green); the in-room look of a decoration is a metatile in the same tileset, not extracted |
| 2 | Test render | A `ScreenshotTest` case (not a shipped screen) that draws one Secret Base room: a floor and walls from the tileset, six decorations, and six SpriteCollab Pokémon (`assets/pmd`) standing in it, in both palettes and at two integer scales for the room (2x and 3x). Send both PNGs to the owner and ask which scale reads right and whether the mix is acceptable. Room layout: read `data/layouts/SecretBase_*/map.bin` and the metatiles from pokeemerald, or lay the floor out in code from the tiles if that is much simpler; say which was done | The owner has seen the render and answered | **Done** (0.194.0): the owner chose the scale and the mix (section 6). Room: `SecretBase_Tree1` laid out from `map.bin` and `metatiles.bin` (`composeApp/src/jvmTest/.../HoennRoom.kt`), decoration sprites and PMD Idle frames drawn at one pixel size with the room, shown at a phone's 1080 px width: `./gradlew :composeApp:jvmTest --tests "*ScreenshotTest.secretBaseRoom"` writes `secretbase-{2x,3x,4x,5x}-{light,dark}.png` (the room flush with the dialogue frame) |
| 3 | Secret Base room (A1, P1) | Spec in `MODULES/BASE.md` first: what a decoration is, the three ways it is earned (section 6), the room grid, that the room shows Base's party and is not a place. Then `base-core` (decorations owned and placed, pure rules, tests), `BaseStore` in `app-core` (earning through the existing chore and maintenance payments; a new reward needs `RewardRules.VERSION` and `docs/economy.md`), the tables in `data/` (a numbered `.sqm` migration and `databases/N.db`), and the screen in `composeApp` (`screens/Base.kt`) as a new menu card | Spec approved, `:base-core:jvmTest :app-core:jvmTest :data:jvmTest` green, screenshots in both palettes | Next: spec and questions first |
| 4 | Berry plots (A2) | Spec in `MODULES/BASE.md`: plots, real-hour growth, no wilting, berries to the Bag. Check `docs/economy.md` for what berries already do and where they come from, so the plot does not flood it; re-run `:plan-core:simulateEconomy` if a number changes what is paid | As 3 | After 3 |
| 5 | Badges for goals (A3, P4) | Spec in `MODULES/PLAN.md`: a goal's milestones (`plan-core` `Goal.kt`) show as badges, a finished goal is a Hall of Fame row with the Plan party of that moment. Display only unless the owner asks for a reward | As 3, without `base-core` | **Done** (0.197.0): `MODULES/PLAN.md` section 9.1, `GoalRules.badgeCase` and `hallOfFame`, `Goal.hallOfFameParty`, the Goals view; the owner took the four defaults (a badge per milestone, an assigned set, a party snapshot, on the goal card) |
| 6 | Department Store and Mom (P12, A6) | Spec in `MODULES/MINT.md` and `BASE.md`: the shop sells decorations for Poké; Mom's jar fills from the two paid MINT actions (counted, never amounts; `MintEvent` stays as it is) and sends a decoration | As 3 | After 3 |
| 7 | The other places (P3 Day Care, P8 Pokémon Center, P6 hot spring, P13 Union Room) | Each is a room drawn with chunk 2's tool, behind its module's existing screen. One at a time, each asked of the owner first | Per place | After 3 |

Not scheduled: A4 Mail, A5 Gimmighoul coins, A7 cooking, A8 Pokétch, A9 Stroll, A10 sleeping party. Ask the owner before starting any of them.

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

## 6. Questions for the owner

Answered by rule 4.0 (14 November 2026): places in modules are mainline rooms, not Mystery Dungeon maps; the banks and the Friend Areas stay with WORLD.

1. **How are decorations earned?** A: from BASE upkeep only. B: upkeep, plus bought with Poké at the Department Store (P12). C: B plus Mom's gifts for MINT actions (A6). *Recommended: C, since it ties MINT and BASE together and gives Poké a sink.*
2. **Which first?** A: the order in section 5. B: Secret Base only. C: another pick. *Recommended: A.*

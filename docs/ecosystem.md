# The PokéPlan ecosystem

How PokéPlan grows from one productivity app into a small family of life tools that share one world, without the tools
getting in each other's way. This refines the first draft (`pokelife_pokemon_progression_spec.md`, from ChatGPT) against
what is already built. `docs/design.md` stays the source of truth for the game rules of what exists today; this
document is the plan for what comes next. Items marked **(proposed)** are my defaults and wait for the owner's yes.

## 1. Principles

1. **Companion first.** The buddy is a presence, not a chore: no hunger, hygiene or affection meters, no sentences, only
   small reactions (an emoji, a hop, a cry) to things that really happened.
2. **Real progress first.** Every tool works fully without the game. The game only reacts to work the user already meant to do.
3. **One world, separate tools.** Share identity, collection, items and the buddy; never share domain data.
4. **No punishment for being human.** A missed day never costs levels, items, friendship or Pokémon. (One deliberate
   exception, kept small and explained: a failed Deep Focus session loses its egg.)
5. **Private by design.** What crosses from a tool into the world is minimal and contains no text or health values.
6. **Transparent economy.** Every reward source, cap and use is written down; earned items never expire or vanish.
7. **Accessible.** Sound, motion and celebrations are controllable.

## 2. Three layers

```
 Domain (one per module)        Effort events              World (shared)
 ───────────────────────        ─────────────              ──────────────
 Plan   tasks, habits, focus  ┐                            trainer rank
 Care   routines, mood        ├──►  { app, kind, tier,  ──► collection, eggs
 Grow   goals, journal        │       minutes, day, key }   inventory, bonds
 Home   recipes, closet       ┘     (no text, no values)     achievements, buddy
 own tables, own privacy                                     versioned rules + caps
```

- **Domain** is what you did. Each module owns its tables and its export and delete. Today that is the `plan.*` schema.
- **Effort events** are the only interface. Today: `EffortEvent` (a focus session) and `AchievementEvent` (a finished piece
  of work of tier 1 to 5) in `pokemon-core`. An event carries the app, the kind, a tier, minutes if relevant, the local day
  and an idempotency key. **Never** a title, a note, a mood value or a health number.
- **World** is what the game gives back: trainer rank, the collection, the bag, eggs, bonds, achievements and the buddy,
  computed by the shared reward engine under versioned rules (`RewardRules.VERSION`, recorded on every ledger row; every source, sink and cap is in `docs/economy.md`). Today: `platform.*` plus the engine in `pokemon-core`
  (the same Kotlin runs on the server, compiled to JavaScript, so the server stays authoritative).

Because modules only meet in the world, adding one never changes another.

## 3. One app now, separate-ready

**Ship as one app with modules** (turn each on or off in Settings; each module is a section of the **Modules** tab, while
Home, Pokémon and Me hold the shared world, 0.33.0), because the shared buddy and collection work offline
for free, features like sign-in, widgets and notifications are built once, and friends install one thing. Keep a hard
module boundary now (own core library, own schema, events only) so a module can become its own app later.

**Split a module out only when** (a) it needs sensitive permissions people may not want next to a task list (health data,
journal), or (b) people want it without the rest, or (c) it is a different kind of game.

**Standalone games.** A Mystery Dungeon style idle or expedition game is case (c) and a natural separate app: it reads the
shared collection and sends effort-like events back (an expedition returning with items). Voidpet Dungeon is the reference
for that genre; from its [store listings](https://www.bluestacks.com/campaign/com.voidpet.dungeon/en) it is a turn-based
tactical RPG with 1000+ levels and 200+ creatures, so the "idle" version would be our own, much smaller take. Late, optional. **Its world is the games' regions** (Kanto to
Paldea), which fold into it from PokéPlan (owner's decision, see "Module regions" in section 4).

## 4. Modules

| Module | Real-world job | What it owns | What it grows in the world |
|---|---|---|---|
| **Plan** (built) | Time and tasks: calendar, tasks, habits, focus, review | `plan.*` | Eggs (focus), Rare Candies (levels), items, rank |
| **Care** (next) | Body and mind: self-care routines, calm breaks, mood check-ins; later health summaries | `care.*`, on-device first | Bond (friendship), berries, camp comfort (cosmetic) |
| **Grow** | Learning: goals, practice, journal, reflection | `grow.*` | Dex discoveries, evolution trials, forms |
| **Home** | Household: recipes, meal plan, grocery list, closet | `home.*` | Berries or curry (cosmetic); mostly creates Plan tasks |

Each tool grows a different thing, so a player is drawn to all of them but none is required. Home modules have no game loop of
their own: a meal plan or laundry day creates **Plan tasks**, and finishing the task is the one canonical reward source.

### What the reference apps teach

- **Google Calendar + TickTick → Plan.** Missing: a day and week **timeline with time-blocking**, Google Calendar
  import or sync, reminders (Android notifications), tags and lists. This is the largest gap in Plan.
- **Voidpet Garden → Care.** [Voidpet Garden](https://ixd.prattsi.org/2025/02/design-critique-voidpet-garden-ios-app/) is a
  self-care app around a pet collection: mindfulness, gratitude, emotion naming, mood tracking, and pets that prefer
  different kinds of affection. Our `BondStyle` (comfort, presence, gifts, balanced, from nature) is the same idea, so the
  buddy's reactions can follow it.
- **Huawei Health → Care, later** (section 6).
- **Paprika and a wardrobe app → Home.** Photo-heavy data apps with no natural game loop; build last.

### Module regions *(decided by the owner, October 2026)*

Every life module has its own custom region: a hand-curated list of species in the style of the Plan Region (the first one),
built with the same kit (`docs/region-curation-prompt.md`, `./gradlew :pokemon-core:curationReport`). The rules in
section 10, "Decided (owner)" item 1 still hold: a region is flavour and holds no domain data.

- **Names: "<Module> Region".** Plan Region, Care Region, Grow Region, Home Region. **The old Home Region became the
  Plan Region** (it holds the lake trio, Plan's patrons), and the name "Home Region" passes to the household module.
  In code: `Region.PLAN` keeps the stored name `HOME` (saves, `plan.focus_sessions` and the reward function store it
  by name), and the household module's region is `Region.HOUSEHOLD`. A module region (`isModule`) needs no rank and stays
  hidden until its module ships (`released`); its list is `tools/species/regions/<region>.txt`.
- **No overlap.** A species is in at most one module region. When a species clearly belongs to a module, the Plan Region
  gives it up; owned Pokémon are kept, and only the dex counts change. **The Plan Region gives up nine** (owner): Swablu,
  Altaria, Cottonee and Whimsicott go to Care; Caterpie, Larvesta, Volcarona, Riolu and Lucario go to Grow. Each leaves
  Plan's list (`tools/species/regions/plan.txt`) in the same change that adds its new region, so no species is ever left
  without one; Caterpie's line (Metapod, Butterfree) moves with it.
- **All module regions are open by default** (owner): each one is open to every player, from the first day, as soon as its
  module ships, with no rank gate and no need to use the module. (Today's rank chain for the game regions is unchanged
  until they leave.)
- **Size: about 150 species each**, like the Plan Region (about 2 months per dex), with at least one hatchable line
  in every rarity tier.
- **The game regions (Kanto to Paldea) fold into the dungeon game** (section 3, "Standalone games"): its world is the real
  regions, explored with the shared collection. **They leave PokéPlan when that game ships** (owner); until then they stay
  as they are now. Nothing is taken away from players who have them (*(proposed)* their caught Pokémon and dex progress
  move with the collection, which is already shared). Once they leave, trainer rank no longer opens regions in PokéPlan
  and only reveals features; their legendary quests move with them.
- Modules without a game loop of their own get no region: the Poketch utilities pay nothing (section 8), and the dungeon
  game uses the game regions.

**Draft themes** *(patrons accepted by the owner for now; species lists are drafts for curation; patrons are
legendary-class or mythical and earned by quest)*:

| Region | Main types | Patrons | Signature species (draft) | Super Rare line |
|---|---|---|---|---|
| **Plan** (the old Home Region, 167) | everyday route Pokémon | Uxie, Mesprit, Azelf (as built) | as curated | as curated |
| **Care** (curated, 143) | Fairy, Grass, Water, Normal | Cresselia (good dreams, sleep), Shaymin (the Gracidea is given in thanks: gratitude) | Happiny line, Audino, Comfey, Alomomola, Swablu line, Munchlax line, Munna line, Cottonee line, Spritzee line, Hatenna line, Indeedee, Snom line, and the full list in `tools/species/regions/care.txt` | Goomy line, and Slakoth's (rest) |
| **Grow** | Psychic, Fighting, Bug | Kubfu and Urshifu (it evolves by passing a trial, like Grow's evolution trials) | Abra line, Slowpoke line, Natu line, Smeargle, Magikarp line, Caterpie line, Larvesta line, Riolu line, Machop line, Mienfoo line, Elgyem line, Unown (letters for the journal) | Beldum line |
| **Home** | Normal, Fire, Grass, Fairy | Ogerpon (Hoopa and Magearna are already in Plan's list) | Rotom (the appliance forms), Miltank, Swirlix line, Milcery line, Applin line, Sinistea line, Lechonk line, Fidough line, Smoliv line, Tandemaus line, Sewaddle line (sewing, for the closet), Furfrou, Klefki (keys) | Frigibax line |

Care's list is curated (`Region.CARE`, 0.32.0): 143 species in 64 hatchable lines (20 Common, 23 Uncommon, 19 Rare, 2 Super
Rare) plus the two patrons, open to everyone since the Care screens shipped (0.34.0). Only 12 lines are Super Rare and 6 were free, so each new
region takes two: Goomy and Slakoth for Care, leaving Beldum and Jangmo-o for Grow and Frigibax and Duraludon for Home.
Cresselia's legendary quest still counts the Sinnoh dex until Care's own patron quests are written. Grow's and Home's
lists are curated when their modules start.

## 5. Interconnection

- **Trainer rank:** every module contributes, capped **per module per day (proposed)** so none dominates; a "balanced day"
  can give a small cosmetic nod, never a penalty.
- **One buddy** on Home and in the widget reacts to events from any module.
- **Bond history, not a meter:** a "your journey together" timeline of hatches, level-ups, evolutions and milestones,
  privacy-safe. Friendship only ever goes up.
- **Weekly Route (later):** an optional cross-module journey with capped contributions and cosmetic rewards.
- **Evolution paths map to modules:** level (Plan), friendship (Care), trials (Grow). Existing rules already have all three.
- **Duplicates:** the same real action never rewards twice. A routine shown in two modules has one canonical source record.

## 6. Care module v1 (done in 0.41.0)

**Built (0.25.0, no screens yet):** `care-core` (routines, calm breaks, mood check-ins, prompts, trends, `CareRules`), the
Care events in the shared engine (`RewardEngine.care`, rules version 2), `CareStore` in `app-core` with its own save, apart
from Plan's, paying through the `World` interface with a per-day cap on the world's side, and the opt-in cloud copy in
`care.saves`. Rewards and caps are in `docs/economy.md`. **Screens (0.34.0), after journalling and DBT (owner):** Care is a
module in the Modules tab (`screens/Care.kt`), on by default and switched off in Settings → Modules (only hidden: its save
stays). Today's routines sit on its menu; then Routines (an optional time and days, put away, delete), Calm break (1 or 2
minutes, a breathing circle that stands still with Reduce motion, the buddy asleep in a scene), **Diary card** (one a day,
after the DBT diary card: six feelings rated 0 to 5, urges the user chooses to track, the skills used, a note), **Skills**
(the four DBT groups, Mindfulness, Distress tolerance, Emotion regulation and Interpersonal effectiveness, with 20
well-known skills as short how-tos in our own words and "I practised this"; paced breathing opens the calm break; a line
that it is a reminder, not therapy), **Journal** (free entries any time, today's prompt as an optional start, six preset
tags, search) and Trends (7-day counts, best week, each feeling by week; no streaks). Its save is `care.json`
(`pokeplan.care` on the web); Settings → Your data exports and erases it, and erasing the device erases it too. The Care
Region opened with it. **Opt-in sync (0.38.0):** Settings → Modules → "Care copy in the cloud", off by default and shown
only to a signed-in player; `CareSync` (app-core `cloud`) uploads `care.json` through the reward function (`care-pull`,
`care-push`, `care-delete`) to `care.saves` with its own version, apart from the Plan save, and asks which to keep when two
devices both changed. The device's visitor seed and its sync state are never uploaded; turning it off, or erasing Care's
data, deletes the copy.
**Routine reminders (0.39.0):** on Android, a routine with a time reminds once at that time on
its days while it is not done (`CareReminders` in app-core, `RoutineReminders` in androidApp), with Done (ticked and paid
as on the screen) and "In 30 min"; never repeated, not moved by Plan's quiet hours or rest days, held until the end of a
focus session; Settings → Modules → "Routine reminders" (`CareSettings.reminders`). **Care widget (0.40.0):** today's
routines with a tick (ticked and paid as on the screen), the done ones below, and whether today's diary card is saved, in
the meadow scene; tapping opens Care. **Care v1 is complete** with the visitor's liked berry (0.41.0, under Befriending).

**Goal:** a calm self-care companion in the spirit of Voidpet Garden, with the buddy, that stays useful with no game at all.

- **Routines:** user-defined (drink water, stretch, take a walk, wind down), with a time or none, ticked off from a
  widget or notification. Reminders are gentle and can be dismissed; no streak-shaming, only rolling trends and personal bests.
- **Calm breaks:** the 1 to 2 minute breathing moment from the design doc, also available on its own.
- **Diary card** *(replaces the mood check-in, owner, 0.34.0)*: after the DBT diary card, feelings rated 0 to 5, optional
  urges, the skills used, a note. Private; a trend view over weeks.
- **Skills library** *(owner, 0.34.0)*: DBT's four skill groups as short reminders; practising one is doing, so it earns.
- **Journal** *(replaces the reflection prompts, owner, 0.34.0)*: free entries; the gratitude and reflection prompts are an
  optional, skippable starting point.
- **Buddy:** reacts with an emoji chosen from its type and bond style, rests during a calm break, never comments on mood.
  *(0.42.0)* Care's menu opens on Care's buddy (`GameStore.careBuddy`: Care's lead, else the Plan buddy) in the meadow, the
  same `BuddyStage` as Home: pet it (`pet(id)`, the shared daily cap), give it a berry, and it celebrates only Care's own
  moments (`buddyEvent(care = true)`) with its cry, portrait and hop. *(0.43.0)* Below it, Care's whole party sits in six equal
  slots with its perks (`CarePartyPanel`, the same panel as Plan's page): tap one for its cry and a hop, hold it to open it.
  Visitors idle with their own animations and cry when tapped or visited; a loved action plays the cry, a treat the healing
  chime, a new friend the Pokémon Center tune. The calm-break buddy wakes with a stretch and its cry. Ticks, practised
  skills and a saved diary card play the select blip. Sounds follow the Sound setting, motion Reduce motion.

**Rewards, with the privacy rule built in:** *entering sensitive information earns nothing* (diary cards, journal entries,
notes, health values). **Doing** earns: a finished routine (tier 1, capped per day), a calm break (minutes), a practised
skill (3 a day), and a weekly "kept a rhythm" milestone.
Rewards are bond, berries and cosmetics, plus a small capped share of trainer rank.

**Data:** `care.*` tables with row-level security, **on-device by default**; cloud sync is opt-in and excluded from friends
and leaderboards. Export and delete cover it.

**Events out:** `routine_completed`, `calm_break_completed`, `skill_practised`, `weekly_rhythm`. No other field (not even
which skill).

### Befriending (built in 0.35.0)

*Owner's answers, October 2026.* Care gains a Pokémon source that is about bond, not volume. Entering sensitive information
still earns nothing, and a visitor never reads a domain (only the *kind* of Care action).

- **Visitors.** Each day the Care scene offers **2 or 3 random visitors** from the Care Region pool (Common to Rare; Super
  Rare and the patrons stay quest-only). The type pool tilts a little with the kinds of Care action done lately (calm
  breaks lean Water and Fairy, routines Normal, skills Grass), and coming back after a break offers a fuller choice. No
  catch rolls, no balls, no battles.
- **Befriending.** The user picks one visitor and **spends the day's befriend** on it: **one befriend per day**. The visitor
  warms up on days that have at least one paid Care action (a berry it likes adds a bonus point); about 4 to 5 points make
  it a friend. A visitor not visited for a few days wanders off, with no penalty and no streak shown.
- **Outcome.** Usually it **leaves an Egg** that hatches through the normal path (Dex pacing unchanged); with a **rare
  chance it joins directly** (owned, high starting friendship). Both are an encounter method for the "best two methods" rule
  (a nature bias and a min IV floor), and both go in the **same PC and Pokédex** as every other Pokémon.
- **Limits** *(tune)*: one befriend a day, a daily total shared with Care's world-side cap, one result per visitor (the
  idempotency key), and the direct-join chance small enough that Care's Pokémon income stays below Plan's.
- **As built.** *Spending the befriend* is one tap on a visitor and warms it by a point; it needs at least one paid Care action
  that day, so a quiet day simply waits. Four points make a friend (`Visitors.FRIEND_POINTS`), so one friend takes at least 4 days.
  Unvisited warming visitors stay 3 days (`WANDER_DAYS`) and then wander off; the day's unpicked visitors leave at midnight. A
  break of 3 days or more brings 3 visitors instead of 2. The egg hatches at once, like a Task Egg, as the visitor's own species
  (the player chose it; until 0.45.0, below), into Care's party if there is room, else the PC. **Visitor luck** (Flying, Psychic, Ghost in Care's party)
  raises a visitor's chance of arriving one rarity higher, the egg's IV floor (up to +6) and the direct-join chance (up to +5
  percentage points). **The liked berry (0.41.0):** each species likes one of the five berry flavours
(`Visitors.likedBerry`, the same every time, found by trying). "Offer a treat" can give a berry from the shared bag (spent
at once through `World.spendBerry`); its liked flavour adds one bonus day once per visitor (`Visitors.BERRY_BONUS`,
`Visitor.berryBonus`), so a friend takes 3 days at the quickest instead of 4.
- **Visits (0.36.0, replaces the one-tap befriend).** Tapping a visitor starts a **visit** of 3 actions from five kinds of care, named
  after Chapman's love languages (words, time, gift, service, touch). Each visitor has a seeded **favourite** (`Visitor.favorite`)
  found by trying: the favourite gives 2 warmth, any other kind 1, one already used in the visit 0. After the 1st and 2nd action it
  may make a **bid** (look, nudge, show; Gottman's bids for connection) and **noticing** it adds 1. A visit of `WARM_VISIT` (4)
  warmth is a day; a quieter one is not, with no penalty. Every kind warms because the evidence that matching a person's love
  language helps is weak (Impett and colleagues, *Current Directions in Psychological Science*); variety and responsiveness
  matter more. Rewards, caps and the one-a-day limit are as before. The copy never claims a mental-health benefit.
  `Visitors.act`, `Visitors.notice`, `CareStore.startVisit/visitAct/visitNotice/finishVisit`. **0.37.0:** the visitor stands in
  a meadow scene and reacts with its own SpriteCollab animations (hop for a loved action, eat for a treat; none with Reduce motion);
  a pat asks for a spot (head, back, chin), a species' liked spot (`Visitors.likedSpot`) warms +1 once a visit; five bid kinds;
  `CareData.met` keeps every species cared for (shown as a quiet "Met" row). Not built: more care kinds beyond the five.
- **Offline.** Visitors are seeded per device and per day (`CareData.visitorSeed`, with the day), so the same device sees the same
  visitors all day, and the offer is saved the first time it is seen. The egg's dice come from the oldest server-issued egg ticket
  when the device has one (it is spent with the befriend and reported on the next sync like a focus ticket), and from the device
  without; the direct-join roll is part of the same dice. The day's cap is the key `befriend-<epochDay>`. The server does not run
  Care events yet, so nothing here needs a server change.
- **Numbers** go in `RewardRules` and `docs/economy.md` with the build (a rules version bump), then `simulateEconomy`.
- **More with visitors (0.45.0, owner, replaces one befriend a day and the visitor's own egg).**
  - *A visit for every visitor:* each visitor can have **one visit a day** (`Visitor.lastVisitDay`, `CareStore.canVisit`), each
    warming on its own, once some Care action has paid that day; one visit is open at a time. New visitors come only while
    the meadow has room for `Visitors.MAX_VISITORS` (4), so friends come at most 4 a day (`GameStore.BEFRIEND_DAILY_CAP`) and
    in practice about one a day for someone who visits all of them.
  - *The friend's gift:* usually an **egg of its family's first stage**, Day Care style (`Dex.lineRoot`: a Pikachu leaves a Pichu
    egg; a first stage leaves its own kind), hatched at once as before; **10%** of the time (`BEFRIEND_DIRECT_JOIN_PERCENT`,
    up to +5 with Visitor luck) the visitor itself joins with friendship 150 (`BefriendResult.joined`). Rules version 11.
  - *Care done with the visitor, not with buttons* (`VisitorScene`): pick a kind of care, then do it in the meadow. A pat lands
    where you tap the visitor (its head, under its chin, or its back), a treat or berry is dragged to it (or tapped), sitting
    together is a press held on it for a moment, tidying its spot is a few swipes across the meadow, and kind words are picked
    from three warm lines. A bid is answered by tapping the visitor. A counter shows how far the visit is. Every gesture has a
    screen-reader action, and Reduce motion keeps the visitor still.
  - *Free play* with any visitor, any time (Play on its row): a pat, a treat (never a berry) or a game of tag where it bounds to
    where the meadow is tapped. Play warms nothing and pays nothing.
  - *In the meadow:* the two warmest visitors stand beside Care's buddy on its stage (`StageGuest`); a tap is a pat, with its cry,
    a hop and a heart.

### Health integration (after v1, optional)

The owner uses a **Huawei Watch 4 Pro** with Huawei Health on a **Samsung Galaxy S26 Ultra**, and wants **historical**
data at **no cost**. Researched October 2026:

| Route | Free | History | Effort | Verdict |
|---|---|---|---|---|
| **Huawei data export** (HUAWEI ID → Privacy Centre → Request your data → Health) | yes | **all of it** | M | **Use first** |
| Health Connect via the Health Sync bridge app | no (one-time fee, about £2.69) | only after paying | S | optional, ongoing sync |
| Health Connect with the phone's own Samsung Health steps | yes | 30 days, more with the history permission | S | phone steps only, not the watch |
| Huawei Health Kit (developer API) | account and approval needed | at most 1 year before authorisation | L | skip |
| Gadgetbridge (open source) | yes | from pairing on | M | skip: replaces Huawei Health on the watch |

- **Huawei data export.** Huawei verifies the account, prepares a password-protected zip and emails a link that stays valid
  21 days (reports range from minutes to a week); the request asks for a file-size split and a password
  ([Huawei help](https://consumer.huawei.com/en/support/content/en-us00746223/),
  [Android Authority](https://www.androidauthority.com/how-to-sync-huawei-health-with-strava-3182027/)). Users report
  JSON files with steps, sleep and heart rate plus spreadsheets describing the fields
  ([GitHub issue](https://github.com/aricooperdavis/Huawei-TCX-Converter/issues/19)); the format is not documented, so
  the importer needs a real export to build against.
- **No official Huawei Health → Health Connect sync** exists ([FitMesh, 2026](https://www.fitmesh.fit/en/blog/huawei-health-health-connect-sync)).
  [Health Sync](https://healthsync.app/f-a-q/) bridges it (needs HMS Core on a non-Huawei phone, about a week's trial, then a small
  one-time or six-monthly fee; data from before install syncs only once paid).
- **Health Connect** reads only the 30 days before the app's first permission unless the user grants
  `PERMISSION_READ_HEALTH_DATA_HISTORY` (device-dependent, check `FEATURE_READ_HEALTH_DATA_HISTORY`)
  ([Android docs](https://developer.android.com/health-and-fitness/health-connect/read-data)).
- **Health Kit** needs a Huawei developer project, an approval per app, and a separate scope to read data from before
  authorisation, capped at one year ([Huawei docs](https://developer.huawei.com/consumer/en/doc/development/HMSCore-Guides/historydata-open-0000001209921350)).
- **Gadgetbridge** supports the Watch 4 Pro since 0.81.0 ([release notes](https://gadgetbridge.org/blog/release-0_81_0/)), but the
  watch then pairs with Gadgetbridge instead of Huawei Health, so Huawei's own features and cloud history are lost.

**Plan.** (1) An **"Import Huawei Health export"** screen: pick the zip, enter its password, parse daily step totals and
sleep summaries **on the device**; re-import any time to top up (imports are idempotent by day). (2) Later and optional, a
Health Connect reader for anything else on the phone (Samsung Health steps, or Huawei data if the owner buys Health
Sync). The rules from section 6 hold: the data never leaves the device or reaches friends and leaderboards, the game
rewards only movement or rest milestones ("a calm night" as a cosmetic Sleep-style collectible), never the values, and
never shames low numbers. **Needed from the owner:** request the export and share the zip's file list (names only) so
the parser matches the real format.

## 7. Reconciling the first draft with what exists

| The draft says | Today | Decision |
|---|---|---|
| Avoid EV/IV style competitive stats | Natures, IVs, EVs, perks (capped +25% per bucket, Guardians +15%) | Keep (owner's choice); show the numbers; add nothing more |
| Pick one berry purpose | Berry gives friendship, is a Lure ingredient | Berries are a **Care/bond item** *(proposed)*; Lures stay a separate craft |
| Pokémon XP recipient must be explicit | Buddy gets friendship and EVs; candies go to any Pokémon the user picks | Keep; document it in the reward table |
| Streaks optional, never punishing | Streak feeds a perk and the buddy mood | Keep optional; never lose items or levels |
| No punishment | A failed focus session loses its egg | Keep as the one explained exception *(proposed)* |
| Module data stays in its module | The cloud save is the Plan document, task titles included | Done (0.24.1): it is in `plan.saves`, without the game state; `platform` keeps game state only. A new module keeps its own save in its own schema |
| Versioned reward rules and idempotency | Reward engine, `apply_outcome` with idempotency key | Done (0.24.1): `RewardRules.VERSION` in `pokemon-core` is recorded on every `platform.reward_ledger` row (`rules_version`) and in the function's GET; regenerating the golden vector with new outcomes refuses the old version |
| Export and delete span everything | Settings → Your data: export the whole save as JSON; delete the account (the reward function deletes the auth user and every table cascades from it, checked by `supabase/tests/account.test.sql` across all uuid columns) | Done for `platform` and `plan`; a new schema must reference `platform.profiles` with `on delete cascade` or the SQL test fails |
| No paid randomization | Eggs are free and earned | Keep; no gacha, no paid currency |

## 8. Roadmap (revisited)

1. **Validate on a phone** (ongoing): fixes so far are the widget freeze and pet animation (0.3.1, 0.6.1), Rare Candy on every task with a Super Candy bonus (0.4.0), real evolution levels with a Medium Fast style level curve (0.5.0), and Pokédex sprites that keep loading through Next and no grey flash on tapping a Pokémon (0.19.4). From the same round: entries in a scene for their type (0.19.5) and a form dex with shinies (0.20.0). Gaps found in an audit (October 2026) and closed: party perks shown and all applied, held items, item uses, training plans dropped, a quick-then-slow rank curve with a real feature reveal (0.26.0 to 0.28.0); task categories, notes, deadlines, repeats, search and filters (0.29.0); Android notifications (0.30.0); Pick for me, mini-quests, Just 5 minutes, rest days, welcome back (0.31.0). Still open from it: shop and Lures (coins have no use), Mega Stone and Ability Patch sources, a Legendary Egg hatch, the agenda widget, the mission board, contests, perfect dex and Hall of Fame, date-range filter and custom repeats, the Care screens.
2. ~~Parked todos~~ (done in 0.2.0).
3. **Finish Plan.** Done: reminders as notifications with quiet hours, focus and rest days (0.6.0); a time on tasks and snooze until tomorrow (0.7.0); export, delete account, erase device (0.8.0). The day timeline with tap-to-place time-blocking (0.9.0). Egg tickets for offline hatching (0.10.0). PMD SpriteCollab sprites (0.11.0, owner's priority). Calendar events on the day timeline, Google Calendar through the phone's calendars (0.12.0). Dragging and resizing blocks, and a week timeline (0.14.0). PokéPlan's own events on the calendar, never paid (0.15.0). Seen, caught and owned in the Pokédex (0.16.0). Focus rules: a locked screen and calls handled on Android, the rules on screen, focused and paused time apart (0.16.1). Me metrics defined, with 7 / 30 / 90 day ranges and goal progress apart (0.17.0). Accessibility pass: contrast, 48 dp targets, roles and labels (0.17.1). Desktop and web are deprioritised (they keep compiling; no features only for them, see Platforms in design.md). Lists after TickTick, the Inbox being the tasks in no list (0.21.0); a Month view, multi-day bars in the Week and all-day own events in colours (0.22.0); recurring own events, edited as a series and moved or deleted one at a time (0.24.0). **Next:** check the brief's accessibility checklist on a phone (TalkBack, large text; ui-ux-brief.md section 9, the owner's to do on a device); then item 5.
4. ~~**Platform tidy before a second module**~~ (done in 0.24.1): the cloud save is in `plan.saves`, the reward rules are versioned, and `docs/economy.md` lists every source, sink and cap. To do by the owner: `supabase db push`, then run the Deploy the reward function workflow.
5. **Care v1** (section 6), as a module in the same app with its own schema. Rules, save and schema done (0.25.0); Care Region curated (0.32.0); the Modules tab (0.33.0); the screens, the Settings switch and the Care Region released (0.34.0); opt-in sync (0.38.0); routine reminders (0.39.0); the Care widget (0.40.0); the visitor's liked berry (0.41.0). **Care v1 is done**; next item 6. Since: Care's Pokémon feel (0.42.0), aligned Plan and Care pages (0.43.0), a visit a day for every visitor, care by gesture, free play and the family egg (0.45.0).
6. **PokéPlan Launcher** *(moved up by the owner, October 2026: right after Care v1)*: PokéPlan as an optional Android home screen, after Niagara and Smart Launcher ("PokéPlan Launcher" below).
7. **Charm pass:** hatch and evolution ceremonies, region themes, the Oak sprite, music.
8. **Grow**, then **health integration** (the Huawei export importer first, see section 6), then **Home** modules.
9. **PokéLife extras** (PokéLife is the app's name from 0.47.0, section 11): Poketch-style utilities with helper Pokémon ("Poketch utilities and helper Pokémon" below) and a live wallpaper with the buddy in its scene (the launcher can show it once it exists).
10. **The October 2026 audit** (section 11): a generous economy and Care Eggs (0.46.0); the PokéLife name; screen transitions; Home as an overview of every region with each region's lead (0.49.0); regional looks (0.50.0: Plan a town, Care a meadow); then the Pokémon detail, Party and Pokédex passes.
11. **Later:** weekly contests, trading, co-op focus, Secret Base, photo mode, and the standalone dungeon game.

### Ideas parked

- **Personality quiz** *(owner, 0.31.0)*: dropped from PokéPlan's onboarding (a starter picker is enough there). It may suit another module, for example Care (a gentle "how are you, really" check that suggests a buddy or a routine) or the standalone dungeon game, where the Mystery Dungeon quiz is at home.

- **More Poketch-style utilities** *(owner's idea, from Diamond and Pearl)*: after the v1 tools below, other candidates are a digital clock and calendar, a counter, a stopwatch and a countdown (tied to Focus), a step or water-intake counter (a Care module hook), a memo pad, a day-care or "buddy check" screen. Each one gets a helper the same way as below.

### Poketch utilities and helper Pokémon *(proposed, owner's idea)*

Handy tools that live in the app and as widgets, each one a "Poketch app" the player switches between with a button, like the watch in Diamond and Pearl. Each tool has **helper Pokémon** known for that skill (Ampharos for the flashlight). It stays plain utility: no text from the buddy, real game assets for icons, and nothing a tool records is read by any other module.

**v1 tools and their helpers.** A Pokémon is compatible with a tool if it is in the tool's **signature line OR it learns the tool's move** (in any game, by any method). There is no type fallback.

| Tool | Signature line | Move |
|---|---|---|
| Flashlight | Mareep, Flaaffy, Ampharos (the Olivine lighthouse) | Flash |
| Calculator and unit converter (one tool, two pages) | Porygon, Porygon2, Porygon-Z | Conversion |
| Currency converter | Meowth, Persian, Perrserker (all regional forms) | Pay Day |
| Picker (pick one from a short list you type) | Cleffa, Clefairy, Clefable | Metronome |
| Coin toss | Gimmighoul (both forms), Gholdengo | Make It Rain |

**The tool always works; only the helper is locked.** Every tool works from the first day with no helper (it shows a real item icon instead), because a tool you need right now, like a flashlight at night, must never sit behind a game. A helper is flavour: its sprite in the tool and its widget, and one emoji reaction when the tool is used. Using a tool pays nothing (no candies, items, eggs, rank points or effort events), because taps on a flashlight or a coin would be farmed.

**Assigning.** Any owned Pokémon that is compatible, in the party or the PC, can be a tool's helper; one Pokémon may help several tools. Guardians are not helpers (their role stays separate). If a helper is transferred, the tool goes back to its loaner or to no helper.

**Ranger request, when you own no compatible Pokémon.** A Pokémon Ranger-style capture minigame lends the tool a helper:
- Capture Styler loops: draw circles around the moving sprite (a mouse drag on desktop and web). The loaner is the signature line's iconic member (Ampharos, Porygon, Meowth, Clefairy, Gimmighoul). Its base Speed sets how fast it moves and its rarity sets the number of loops, 2 to 4 *(tune)*.
- Gentle by design: failing just means trying again, with no cooldown or cost. With Reduce motion, the sprite moves slowly, or the player can tap in rhythm instead of drawing loops.
- The loop line is UI chrome drawn in code and the Pokémon is its real sprite, so the art rule holds. It is not a battle.
- **A loan, not a catch.** The loaner helps that one tool only. It never enters the party, the PC or the Dex and changes no seen or caught state, so it cannot become a free egg that skips the Dex pacing.
- **The loan lasts until you own a compatible Pokémon** (hatched, evolved or already owned). Then that Pokémon takes over the tool (signature line first, then the highest friendship), and the loaner goes home with one goodbye emote. The player can change the helper afterwards.

**Platform and privacy notes.**
- Flashlight: on Android, the camera torch (`CameraManager.setTorchMode`, no camera permission needed); on web and desktop, a full-white screen.
- Currency converter: needs exchange rates. The app fetches daily rates from a free rates service and caches them; offline, the tool shows the last rates and their date. Only the rates are fetched; no user data is sent.
- Picker: the list stays on the device and is never synced or sent anywhere.
- Widgets: a flashlight toggle and a coin toss are the natural first ones.

**Data and where it lives.**
- `pokemon-core` has no moves today. The Dex generator would pull from PokeAPI only the species that learn the five moves above (not full learnsets), so the generated data stays small. Compatibility (signature line or move) is a Pokémon-side rule, so it goes in `pokemon-core`; the tools themselves belong to the app.
- Helpers and loans are cosmetic, so they are client-side data in the save (like `AppData.favorites`), not part of the server's game state.

**Decided (owner).**
- No friendship tick for using a tool: helpers stay pure flavour, and no tool pays anything.
- Exchange rates come from a free rates service called by the app (no Supabase function). Which service is picked when the tool is built.

### PokéPlan Launcher *(proposed, owner's idea)*

PokéPlan can also be the phone's home screen, in the spirit of **Niagara** (one clean list, the apps you use most) and
**Smart Launcher** (apps sorted into categories by themselves, widgets, personalization). Android only; desktop and web
do not get it.

- **One APK, opt-in.** A `LauncherActivity` in `androidApp` with the `HOME` and `DEFAULT` intent categories. Nothing
  changes until the user picks PokéPlan as the home app in Android's settings (the launcher offers a button that opens
  them); the normal app icon and `MainActivity` stay as they are. One APK keeps a single `save.json` and `GameStore`, which
  a second APK could not read.
- **Home page (Niagara).** The buddy in its scene (the live wallpaper later), today's tasks with a tick, a Focus button,
  and a short pinned list of apps. No dock clutter, no badges.
- **App list.** Swipe up for every app A to Z with a letter scroller and search (`LauncherApps`, which also covers work
  profiles). Long press: pin, hide, app info, uninstall.
- **Categories (Smart Launcher).** Apps sorted by the category they declare (`ApplicationInfo.category`, Android 8, our
  minSdk), with a fallback "Other" and the user able to move an app. Each category card uses a real item icon, as on the
  menus (`screens/Menu.kt`).
- **Widgets.** Hosts any app's widgets (`AppWidgetHost`), PokéPlan's Buddy, Today's tasks and Focus first among them.
- **Personalization.** PC box wallpapers, region themes and `DayBackground`; the buddy on the home page is any party
  Pokémon. App icons are each app's own (no icon packs drawn by us), so the art rule holds.
- **Pays nothing.** Opening apps, pinning or sorting earns no candies, items, eggs, rank points or effort events, like the
  Poketch tools. The launcher only shows and starts what already pays.
- **Reliability first.** A home screen must never crash or lag: v1 is the app list, categories and widgets; the game parts
  come in once that is solid. Its data (pins, hidden apps, categories, layout) is client-side, on the device only, never
  synced (it describes the user's installed apps).

**Decided (owner, October 2026).**
- **Strict focus.** During a focus session, pressing Home keeps the Focus screen on: it is not leaving the app and starts no
  grace period. There is no separate "focus home" that hides apps; opening another app still starts the 10 second grace
  period as today.
- **Care on the home page.** When Care is on, today's routines (with a tick) and today's diary card sit on the home page.

## 9. Open questions

- Per-module daily rank caps, or one shared cap?
- Is the failed-focus egg loss acceptable, or should it soften?
- Do you want Care's cloud sync at all, or strictly on-device?

## 10. Game layer

Reconciles an outside draft (the "Game Layer Spec") with what is built. **Shiny odds and the Guardian rules stay exactly as
they are in docs/design.md** *(owner's decision)*: shiny is about 1 in 192 per egg (1 in 128 until rules version 12 made eggs more frequent, so shinies per player stay as rare), and Guardians are legendary-class and
mythical Pokémon in rank-opened slots with +10% signature perks added on top of the ordinary cap (up to +15% in a bucket
from Guardians). Nothing below changes them.

### Already true, no work

- Guardians are legendary and mythical only; they cannot be the buddy, join the party or be transferred (so no migration).
- Every legendary and mythical has a quest; Uxie, Mesprit and Azelf are the lake trio, written around knowledge, emotion and willpower, with the buckets in design.md.
- A granted legendary gets one ordinary shiny roll when it is created and is never rerolled; there is no re-encounter.
- Seen and caught states, the IV judge, mints, Bottle Caps and the Ability Capsule exist.

### Adopt when the module that needs it ships

- **Encounter methods as egg variants.** The way an egg was earned biases the roll (a min IV floor, a nature bias, a type pool). All numbers live in the versioned rules and `docs/economy.md`; no battles, balls or catch rates. Only the **best two** methods apply to one egg; every method has a daily cap; one egg per source record (the existing idempotency key).
- **Return after a break** is one of the best methods: coming back is rewarded, never punished. It needs a daily cap so it cannot be farmed.
- **Dex as journal.** An entry shows the date and the *kind* of action that produced it, never a title, note or value.
- **Badges** as cosmetic trophies for a module's own milestone, and an optional quarterly or annual review ceremony. They never gate levels, items or domain access.
- **Legendary patrons per module** (one Guardian-style legendary per module, earned by an arc), added one at a time after Care v1 and only where the link is obvious (the lake trio sets the bar).
- A **legendary IV floor** of three perfect stats is a small change in `Hatching.create` for quest-granted legendaries *(proposed, not built)*.
- An **Oak intro**: the starter picker plus one question (calm, focused or curious) that picks the first egg's type pool. Charm pass.

### Not adopted

| Idea | Why |
|---|---|
| Shiny odds 1/4096 and a Shiny Charm | Shiny stays about 1 in 192 per egg, about as rare per player as 1 in 128 was |
| One Guardian slot with a single +15% effect inside the cap | Guardians stay as designed |
| Party slots 2 to 6 as cosmetic only | Non-buddy party members keep their 25% perk share |
| Steps, Oval Charm and step-based eggs | Eggs hatch from focus time; there are no steps |
| Any-Pokémon-as-Guardian, life-stat mapping, domain-reading perks, deployment fatigue, soft-reset shiny hunting, wild battles | Break the principles or belong to the later standalone game |
| Legendary trading | Legendaries are already untransferable |

### Decided (owner)

1. **Regions are custom and tied to modules, for flavour.** A module's region is a hand-curated list of species made for that module, in the same way as the Plan Region (`tools/species/regions/plan.txt`), not a copy of a game region. Its theme, patron legendaries and egg type pool fit the module (the lake trio suit Plan, so Plan's list is where they belong; the other lists are curated module by module, and Care's before Care v1 ships). The game regions (Kanto and the rest) stay as the ordinary collectable dexes until they fold into the dungeon game. Names, sizes, the no-overlap rule and the draft themes: "Module regions" in section 4. It is flavour, not ownership: a custom region holds no domain data, and *(proposed)* it is **not gated by using the module**, so nobody has to use a module they do not want to finish a dex. The same limits as every egg method apply (best two methods, daily caps). Curation uses the existing kit (`docs/region-curation-prompt.md`, `./gradlew :pokemon-core:curationReport`).
2. **Legendaries and mythicals count towards regional dex completion.** A region is complete only with its legendary-class and mythical species, which are earned through quests, not eggs. Consequences to handle when the completion rewards are built: the "completes the Plan dex at about day 67" pacing in docs/design.md covers the hatchable species only, so completion also waits on the quests (rank-gated, long); the Dex should show those pages as "earned by quest" so they never read as missing eggs; and the economy simulation should report both "hatchable done" and "fully complete".
3. **Plan arcs stay as built:** one quest per legendary. For future modules, arcs should add up to **one cohesive but small and quick story** (see below).
4. **Darkrai is out.**
5. **A full party per module** *(October 2026)*. Each module has its own party of six, and its party's perks apply to that
   module's rewards only, instead of one party shared by every module. The spec is in docs/design.md, "Parties per module" (a Pokémon in one party at a time, a lead per party, shared
   Guardians, Care's own perk buckets); not built yet.
   **Refined (owner):** effectively **many parties, one per region/module, but one shared PC and one shared Pokédex**. A
   Pokémon can be in a module's party while staying in the one collection; Care's party is where befriended Pokémon start.
6. **Care obtains Pokémon by befriending** (section 6, "Befriending"): 2 or 3 random visitors a day, one befriend a day,
   usually an Egg, rarely a direct join; shared PC and Dex.

### Story direction (future)

Keep it light: a few short beats, never a cutscene, never a wall of text. One shared thread runs through every module's arc so they read as one world rather than separate checklists. Each arc is quick (a handful of beats, tied to the effort the quest already asks for) and each beat is one or two lines with the patron's sprite and the usual reaction emoji. The story reacts to real effort only and never reads a domain (no task titles, notes or values), like every other event. The lake trio's arcs (knowledge, emotion, willpower) are the template; the thread that joins modules is still to be written, and should be agreed before the second module's arc ships.

## 11. Product direction from the October 2026 audit *(owner's decisions)*

An outside audit of the whole app (product, UX, visual design, the Pokémon side) asked the owner to settle a few questions. The answers:

1. **Each module is its own Pokémon region** (already decided, section 4), and **every region gives Pokémon at a comparable rate**: several a day in each, not one region far ahead. Care Eggs (0.46.0) brought Care level with Plan; Grow and Home need their own way to give eggs when they ship.
2. **Easy to fill, hard to perfect.** Completing a Pokédex should be easy, with plenty of Pokémon and Rare Candies a day. What stays hard is a good version of a Pokémon: a shiny, high IVs, the right nature. Loosen pacing and caps where they slow collecting; never make shinies or IVs easier to make up for it (rules version 12, docs/economy.md).
3. **A lead per region.** Each region keeps its own lead (Plan's buddy, Care's lead, later Grow's and Home's), with no single global buddy. Home shows every region's lead, not only Plan's.
4. **The app is renamed PokéLife.** PokéPlan becomes the name of the Plan module. Code packages (`app.pokeplan.*`), the repository, the Android application id and the save keep their names, so installs, saves and sync carry on.
5. **All of the audit's areas are wanted**, in this order: docs (this section), the economy (done), the name, screen transitions, a Home that is an overview of every region, regional looks, then the Pokémon detail, Party, PC and Pokédex passes.

What the audit asked for in navigation and look, adopted as the brief for those passes:

- **Transitions:** a crossfade between bottom tabs; a short directional slide into a region or a detail and back out; a crossfade between sections of a collection; about 160 to 240 ms for ordinary navigation, longer only for real game moments (hatch, evolution). All of it follows Reduce motion. Switching screens keeps scroll position and selection where it can, and Back behaves the same everywhere.
- **Home** is a world overview: the trainer, every region with its lead and its one most useful next step, recent events and discoveries. It must not read as Plan's dashboard; Plan's planner and focus stay inside Plan.
- **Three visual layers:** the region's world (scene, sprites, its own palette and motif: Plan a town or guild board, Care a meadow, Grow a training ground, Home a house), the functional layer (lists, forms, settings: readable first, Nunito for body text, the DS font only for titles, ranks and numbers, red used sparingly), and game moments (hatch, evolution, rewards: richer, but brief and never on every small action).
- **The Pokémon detail** reads in this order: identity (sprite, number, name, types, level), the specimen (nature, ability, gender, shiny, IVs), growth (EXP, friendship, stats), evolution, its role (party, region, favourite), and where it came from.
- **Kept as they are:** the no-chores companion (no hunger, decay or missed-day penalties), Care's privacy (mood and journal entries never pay or reach the world), real game assets only, and the shared event architecture (module activity, a minimal event, the shared world, the reward rules).


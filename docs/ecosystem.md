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
  computed by the shared reward engine from a versioned rules table. Today: `platform.*` plus the engine in `pokemon-core`
  (the same Kotlin runs on the server, compiled to JavaScript, so the server stays authoritative).

Because modules only meet in the world, adding one never changes another.

## 3. One app now, separate-ready

**Ship as one app with modules** (turn each on or off in Settings), because the shared buddy and collection work offline
for free, features like sign-in, widgets and notifications are built once, and friends install one thing. Keep a hard
module boundary now (own core library, own schema, events only) so a module can become its own app later.

**Split a module out only when** (a) it needs sensitive permissions people may not want next to a task list (health data,
journal), or (b) people want it without the rest, or (c) it is a different kind of game.

**Standalone games.** A Mystery Dungeon style idle or expedition game is case (c) and a natural separate app: it reads the
shared collection and sends effort-like events back (an expedition returning with items). Voidpet Dungeon is the reference
for that genre; from its [store listings](https://www.bluestacks.com/campaign/com.voidpet.dungeon/en) it is a turn-based
tactical RPG with 1000+ levels and 200+ creatures, so the "idle" version would be our own, much smaller take. Late, optional.

## 4. Modules

| Module | Real-world job | What it owns | What it grows in the world |
|---|---|---|---|
| **Plan** (built) | Time and tasks: calendar, tasks, habits, focus, review | `plan.*` | Eggs (focus), Rare Candies and EVs (levels), rank |
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

## 5. Interconnection

- **Trainer rank:** every module contributes, capped **per module per day (proposed)** so none dominates; a "balanced day"
  can give a small cosmetic nod, never a penalty.
- **One buddy** on Home and in the widget reacts to events from any module.
- **Bond history, not a meter:** a "your journey together" timeline of hatches, level-ups, evolutions and milestones,
  privacy-safe. Friendship only ever goes up.
- **Weekly Route (later):** an optional cross-module journey with capped contributions and cosmetic rewards.
- **Evolution paths map to modules:** level (Plan), friendship (Care), trials (Grow). Existing rules already have all three.
- **Duplicates:** the same real action never rewards twice. A routine shown in two modules has one canonical source record.

## 6. Care module v1 (build this next)

**Goal:** a calm self-care companion in the spirit of Voidpet Garden, with the buddy, that stays useful with no game at all.

- **Routines:** user-defined (drink water, stretch, take a walk, wind down), with a time or none, ticked off from a
  widget or notification. Reminders are gentle and can be dismissed; no streak-shaming, only rolling trends and personal bests.
- **Calm breaks:** the 1 to 2 minute breathing moment from the design doc, also available on its own.
- **Mood check-in:** an emoji scale, an optional note, optional tags. Private; a trend view over weeks.
- **Gratitude and reflection prompts:** optional, one at a time, skippable.
- **Buddy:** reacts with an emoji chosen from its type and bond style, rests during a calm break, never comments on mood.

**Rewards, with the privacy rule built in:** *entering sensitive information earns nothing* (mood, notes, health values).
**Doing** earns: a finished routine (tier 1, capped per day), a calm break (minutes), and a weekly "kept a rhythm" milestone.
Rewards are bond, berries and cosmetics, plus a small capped share of trainer rank.

**Data:** `care.*` tables with row-level security, **on-device by default**; cloud sync is opt-in and excluded from friends
and leaderboards. Export and delete cover it.

**Events out:** `routine_completed`, `calm_break_completed`, `weekly_rhythm`. No other field.

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
| Module data stays in its module | `platform.cloud_saves` holds the **whole** Plan document, task titles included | **Move it to a Plan-owned save** (`plan.saves`) *(proposed)*; `platform` keeps game state only |
| Versioned reward rules and idempotency | Reward engine, `apply_outcome` with idempotency key | Add a version number to the rules table and the reward ledger |
| Export and delete span everything | Settings → Your data: export the whole save as JSON; delete the account (the reward function deletes the auth user and every table cascades from it, checked by `supabase/tests/account.test.sql` across all uuid columns) | Done for `platform` and `plan`; a new schema must reference `platform.profiles` with `on delete cascade` or the SQL test fails |
| No paid randomization | Eggs are free and earned | Keep; no gacha, no paid currency |

## 8. Roadmap (revisited)

1. **Validate on a phone** (ongoing): fixes so far are the widget freeze and pet animation (0.3.1, 0.6.1), Rare Candy on every task with a Super Candy bonus (0.4.0), and real evolution levels with a Medium Fast style level curve (0.5.0).
2. ~~Parked todos~~ (done in 0.2.0).
3. **Finish Plan.** Done: reminders as notifications with quiet hours, focus and rest days (0.6.0); a time on tasks and snooze until tomorrow (0.7.0); export, delete account, erase device (0.8.0). The day timeline with tap-to-place time-blocking (0.9.0). Egg tickets for offline hatching (0.10.0). **Next:** Google Calendar import, dragging and resizing blocks on the timeline (and a week timeline), and the brief follow-ups (Focus rules, Dex seen and owned, Me metrics, accessibility).
4. **Platform tidy before a second module:** move the cloud save to `plan.saves`, version the reward rules, document sources, sinks and caps.
5. **Care v1** (section 6), as a module in the same app with its own schema.
6. **Charm pass:** hatch and evolution ceremonies, region themes, the Oak sprite, music.
7. **Grow**, then **health integration** (the Huawei export importer first, see section 6), then **Home** modules.
8. **PokéLife extras** (the owner's name for the whole family): Poketch-style utilities with helper Pokémon ("Poketch utilities and helper Pokémon" below), a live wallpaper with the buddy in its scene, then a Pokémon-themed launcher as the endgame.
9. **Later:** weekly contests, trading, co-op focus, Secret Base, photo mode, and the standalone dungeon game.

### Ideas parked

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

## 9. Open questions

- Per-module daily rank caps, or one shared cap?
- Is the failed-focus egg loss acceptable, or should it soften?
- Do you want Care's cloud sync at all, or strictly on-device?

## 10. Game layer

Reconciles an outside draft (the "Game Layer Spec") with what is built. **Shiny odds and the Guardian rules stay exactly as
they are in docs/design.md** *(owner's decision)*: shiny is about 1 in 128 per egg, and Guardians are legendary-class and
mythical Pokémon in rank-opened slots with +10% signature perks added on top of the ordinary cap (up to +15% in a bucket
from Guardians). Nothing below changes them.

### Already true, no work

- Guardians are legendary and mythical only; they cannot be the buddy, join the party or be transferred (so no migration).
- Every legendary and mythical has a quest; Uxie, Mesprit and Azelf are the lake trio, written around knowledge, emotion and willpower, with the buckets in design.md.
- A granted legendary gets one ordinary shiny roll when it is created and is never rerolled; there is no re-encounter.
- Seen and caught states, the IV judge, mints, Bottle Caps and the Ability Capsule exist.

### Adopt when the module that needs it ships

- **Encounter methods as egg variants.** The way an egg was earned biases the roll (a min IV floor, a nature bias, a type pool). All numbers live in the versioned rules table; no battles, balls or catch rates. Only the **best two** methods apply to one egg; every method has a daily cap; one egg per source record (the existing idempotency key).
- **Return after a break** is one of the best methods: coming back is rewarded, never punished. It needs a daily cap so it cannot be farmed.
- **Dex as journal.** An entry shows the date and the *kind* of action that produced it, never a title, note or value.
- **Badges** as cosmetic trophies for a module's own milestone, and an optional quarterly or annual review ceremony. They never gate levels, items or domain access.
- **Legendary patrons per module** (one Guardian-style legendary per module, earned by an arc), added one at a time after Care v1 and only where the link is obvious (the lake trio sets the bar).
- A **legendary IV floor** of three perfect stats is a small change in `Hatching.create` for quest-granted legendaries *(proposed, not built)*.
- An **Oak intro**: the starter picker plus one question (calm, focused or curious) that picks the first egg's type pool. Charm pass.

### Not adopted

| Idea | Why |
|---|---|
| Shiny odds 1/4096 and a Shiny Charm | Shiny stays about 1 in 128 |
| One Guardian slot with a single +15% effect inside the cap | Guardians stay as designed |
| Party slots 2 to 6 as cosmetic only | Non-buddy party members keep their 25% perk share |
| Steps, Oval Charm and step-based eggs | Eggs hatch from focus time; there are no steps |
| Any-Pokémon-as-Guardian, life-stat mapping, domain-reading perks, deployment fatigue, soft-reset shiny hunting, wild battles | Break the principles or belong to the later standalone game |
| Legendary trading | Legendaries are already untransferable |

### Decided (owner)

1. **Regions are custom and tied to modules, for flavour.** A module's region is a hand-curated list of species made for that module, in the same way as the Home Region (`tools/species/home-region.txt`), not a copy of a game region. Its theme, patron legendaries and egg type pool fit the module (the lake trio suit Plan, so Plan's list is where they belong; the other lists are curated module by module, and Care's before Care v1 ships). The game regions (Kanto and the rest) stay as the ordinary collectable dexes. It is flavour, not ownership: a custom region holds no domain data, and *(proposed)* it is **not gated by using the module**, so nobody has to use a module they do not want to finish a dex. The same limits as every egg method apply (best two methods, daily caps). Curation uses the existing kit (`docs/home-region-curation-prompt.md`, `./gradlew :pokemon-core:curationReport`).
2. **Legendaries and mythicals count towards regional dex completion.** A region is complete only with its legendary-class and mythical species, which are earned through quests, not eggs. Consequences to handle when the completion rewards are built: the "completes the Home dex at about day 67" pacing in docs/design.md covers the hatchable species only, so completion also waits on the quests (rank-gated, long); the Dex should show those pages as "earned by quest" so they never read as missing eggs; and the economy simulation should report both "hatchable done" and "fully complete".
3. **Plan arcs stay as built:** one quest per legendary. For future modules, arcs should add up to **one cohesive but small and quick story** (see below).
4. **Darkrai is out.**

### Story direction (future)

Keep it light: a few short beats, never a cutscene, never a wall of text. One shared thread runs through every module's arc so they read as one world rather than separate checklists. Each arc is quick (a handful of beats, tied to the effort the quest already asks for) and each beat is one or two lines with the patron's sprite and the usual reaction emoji. The story reacts to real effort only and never reads a domain (no task titles, notes or values), like every other event. The lake trio's arcs (knowledge, emotion, willpower) are the template; the thread that joins modules is still to be written, and should be agreed before the second module's arc ships.


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

The owner uses a **Huawei Watch 4 Pro** with Huawei Health. What I could find, and what is **unverified**:
- No confirmed direct route from Huawei Health into Android's Health Connect; community guides describe a bridge app such
  as Health Sync, with partial sleep coverage ([FitMesh](https://www.fitmesh.fit/en/blog/huawei-health-health-connect-sync)).
- Huawei's own Health Kit needs a Huawei Developer project and an access application that may ask for company and privacy
  details ([Thryve's summary](https://www.thryve.health/huawei-health-kit-api-integration)); heavy for a private project.
- Huawei Health's own data export (CSV or JSON) exists per several guides but its menu paths disagree.

Plan: first check on the phone whether Health Connect lists Huawei Health or a bridge as a data source. If so, read-only
steps and sleep **summaries** stay on the device, and the game only rewards optional movement or rest milestones
("a calm night" as a cosmetic Sleep-style collectible), never the data itself, never shaming low numbers.
Otherwise stay with manual check-ins.

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
| Export and delete span everything | Planned for v1 | Define per schema before Care ships |
| No paid randomization | Eggs are free and earned | Keep; no gacha, no paid currency |

## 8. Roadmap (revisited)

1. **Validate on a phone** (now): the build, sign-in and sync, widgets, cries.
2. **Parked todos:** nav bar colour, sound and music sliders, wider emoji reactions by type and nature, widget refresh when the buddy changes.
3. **Finish Plan:** timeline and time-blocking, Google Calendar import, Android notifications, the brief follow-ups (Focus rules, Dex seen and owned, bag grouping, Me metrics, help, accessibility), account export and delete, egg tickets for offline hatching.
4. **Platform tidy before a second module:** move the cloud save to `plan.saves`, version the reward rules, document sources, sinks and caps.
5. **Care v1** (section 6), as a module in the same app with its own schema.
6. **Charm pass:** hatch and evolution ceremonies, region themes, the Oak sprite, music.
7. **Grow**, then **health integration**, then **Home** modules.
8. **Later:** weekly contests, trading, co-op focus, Secret Base, photo mode, and the standalone dungeon game.

## 9. Open questions

- Per-module daily rank caps, or one shared cap?
- Is the failed-focus egg loss acceptable, or should it soften?
- Do you want Care's cloud sync at all, or strictly on-device?
- What does Health Connect show on your phone? (Settings → Health Connect → App permissions / Data sources.)

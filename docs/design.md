# PokéPlan design

Product and game design for the multiplatform rewrite. Numbers marked *(tune)* are starting values to be settled by simulation before release. Everything not marked is decided.

## Pillars

1. **Strict focus.** Leaving the app during a session fails the egg. No loopholes.
2. **More tasks, more productive.** The economy rewards finishing many real tasks, not gaming one big one.
3. **Your partner matters.** A well-raised older Pokémon stays worth using; new hatches never make it obsolete.
4. **Nostalgic pixel.** Gen 3 to 5 look and feel (GBA/DS era), cute and readable.

## Core loop

```
        focus session ──► egg hatches ──► new Pokémon (stats, nature, ability, shiny?)
             ▲                                   │ duplicates ──► transfer ──► Rare Candies
             │                                   ▼
   buddy perks (type / ability)  ◄────  partner raised with candies, items, EV training
             ▲                                   ▲
             └──────── finished tasks ───────────┘  (candies, items, EVs by task category)
```

Focus time brings new Pokémon. Finished tasks make Pokémon stronger. The buddy's perks feed back into both.

## Focus

- Minimum session 5 minutes; Deep Focus (motion-based on phones) boosts egg odds by one band (see `Egg` in `:shared`).
- **Strict mode, always on.** Leaving the app (backgrounding it on Android, losing window focus on desktop, hiding the tab on web) starts a **10 second grace period**. Returning in time keeps the session; otherwise the egg is lost and the streak is hit. No pause button.
- Open risk: a phone call or a system dialog counts as leaving. Plan: treat an incoming call as a free pause. Decide before building.
- Sweet spot is built in: Super Rares per focus-hour peak at about 60 minutes.

## Tasks

- Priority 1 to 5; **priority 5 pays the most**. Reward tables are already ported (`CandyRewards`).
- **Minimum task age:** a task only pays when it has existed for at least *(tune: 15 minutes)* before being completed. Younger tasks complete without a reward. There is no daily cap, so many real tasks always pay.
- On-time bonus: finishing before the due date adds a bonus *(tune)*.
- Subtasks pay a small fixed amount each *(tune)*. Watch that splitting a task into many tiny subtasks does not become the new exploit; the minimum age applies to subtasks too.
- **Categories train stats** (see EVs): Health → HP, Work → Attack, Chores → Defense, School → Sp. Atk, Hobby → Sp. Def, Social → Speed. Leisure and Others: the player picks.

## Pokémon

Each owned Pokémon has: species, level 1 to 100, nature, ability, 6 IVs, 6 EVs, held item, friendship, shiny flag, nickname.

- **Stats** use the Gen 3+ formula:
  - HP = ⌊(2·Base + IV + ⌊EV/4⌋) · Level / 100⌋ + Level + 10
  - Other = ⌊(⌊(2·Base + IV + ⌊EV/4⌋) · Level / 100⌋ + 5) · Nature⌋, with nature ×1.1 / ×0.9
  - IV 0 to 31 rolled at hatch. EV 0 to 252 per stat, 510 total, gained from finished tasks by category.
- **Natures:** the existing 25. Each raises one stat and lowers another (five are neutral).
- **Abilities:** each species has one or two; one is rolled at hatch. Abilities give buddy perks (below).
- **Shiny:** rolled at hatch, base chance *(tune: about 1 in 512)*, with a small bonus for longer sessions. Shinies get a distinct sprite palette and a hatch animation.
- **Levelling:** 10 Rare Candies per level (as now). A Pokémon more than 5 levels below your partner needs half the candies *(tune)*, so new hatches catch up without outshining the partner.
- **Evolution:** Super Candy at the species' evolution level (as now). Evolution stones for the species that use them.

### Items (so a mid partner is not overshadowed)

| Item | Effect | Source |
|---|---|---|
| Rare Candy, Super Candy | level up, evolve | task rewards |
| Vitamins (HP Up, Protein, Iron, Calcium, Zinc, Carbos) | +10 EV in one stat | task drops, streak rewards |
| Nature Mint | change the nature | rare drop, badge reward |
| Ability Capsule | swap to the other ability | rare drop |
| Bottle Cap | raise one IV to 31 *(tune)* | rare drop, Pokédex milestones |
| Held items (e.g. Leftovers, Choice Band style) | one small buddy perk | drops, shop |
| Evolution stones | evolve stone species | drops |

Items are earned by finishing tasks and keeping streaks. They are not sold for real money.

### Duplicates → candy

- Transfer any Pokémon you do not need for Rare Candies. Starting yields by rarity *(tune)*: Common 5, Uncommon 8, Rare 15, Super Rare 40. Perfect-IV or shiny ones show a warning first.
- You cannot transfer the last one of a species you have not registered, nor your buddy.
- This is the sink for hatches, so the PC never needs to be a graveyard.

## Buddy and perks

One party Pokémon is the **buddy**. It lives on the home screen and widget, reacts to the day (sleeps during focus, cheers when a task is done, droops when idle), and gives perks that scale with its level and friendship.

- **Type perk** (one per type, from the buddy's primary type; second type gives half strength *(tune)*). Starting mapping:

| Perk bucket | Types | Effect |
|---|---|---|
| Egg luck | Normal, Flying, Fairy | better rarity odds on hatches |
| Candy yield | Fighting, Rock, Ground, Steel | more Rare Candies from tasks |
| Super Candy chance | Psychic, Dragon, Ghost | higher chance of Super Candy |
| Item finder | Grass, Bug, Dark | chance of an item drop per task |
| Shiny hunter | Electric, Ice | small shiny chance bonus |
| Streak keeper | Water, Fire, Poison | one missed day forgiven per week |

- **Ability perk:** one extra, from the buddy's ability. Real abilities as flavour, data driven. Examples: Pickup (extra item chance), Early Bird (bonus for the first task before 9am), Technician (bonus for 5 to 20 minute sessions), Magic Guard (a failed session spares the streak once a week), Guts (bonus on priority 5 tasks), Synchronize (+EV on the task's stat).
- **Stats matter:** perk strength scales with the relevant stat (e.g. Attack for candy yield) and nature, so raising and training a partner pays.
- **Caps:** total bonus from perks and held item stays below +25% *(tune)* so no single buddy breaks the economy.

## Retention

- Daily streak (focus or a finished task), weekly goals, and a Pokédex with seen/caught, region completion rewards and a trainer card.
- Badges for milestones (tasks finished, focus hours), shown as gym badges.
- Later: friends see your buddy and party; co-op focus sessions; trading.

## Look and feel

- Gen 3 to 5 pixel art: GBA/DS-era text boxes, menus and sprites. Animated front/back sprites for the buddy.
- Hatch, level-up and evolution are full ceremonies (egg wobble, text box lines, cry, flash), skippable.
- Crisp scaling: nearest-neighbour, integer scale, no blur.
- Fonts: pixel font for headings only; body text stays a readable font.
- Sound: cries, short jingles; a mute toggle.
- Accessibility: content descriptions on all images, text scaling, colour-blind safe type chips.

## Android widgets

1. **Today's tasks:** checklist, tap to complete.
2. **Buddy + streak (2×2).**
3. **Agenda:** upcoming tasks, optional read-only device calendar events.
4. **Start focus:** one tap, starts a default session.

Local-first: the app keeps a local database and syncs to Supabase, so widgets and offline use work. Completions from the widget queue and the server grants the reward on sync; the app shows what was earned next time it opens.

## Platforms

Android (full, plus widgets), desktop and web (same game, no widgets). Focus enforcement is looser on desktop and web (window focus or tab visibility only).

## Backend (Supabase)

Server-authoritative for everything that grants value: hatching, candies, items, transfers, shiny rolls. Clients send intents ("finished task X", "hatch session Y") and the server validates and rolls. Row-level security so a user only reads and writes their own rows. Species, base stats, abilities and perk tables are data shipped with the app and mirrored on the server.

## Data to add

- Per-species base stats and abilities for the 150 species, generated at build time from a public dataset (needs the build environment to reach it).
- Ability → perk table, type → perk table, item table, all as plain data with tests.

## Milestones

1. **Shared core (done in #2):** eggs, candy, levels, evolution, Pokédex.
2. **Pokémon depth:** IVs, EVs, natures with stats, abilities, shiny, items, transfer yields, perk calculation. Unit tested in `:shared`.
3. **UI shell:** desktop and web screens (needs `dl.google.com` allowed in the environment).
4. **Backend:** Supabase schema, RLS, reward functions, sync.
5. **Android app and widgets** (needs the Android SDK).
6. **Online:** friends, co-op focus, trading.

## Risks

- **IP:** names, sprites and cries are Nintendo / Game Freak / The Pokémon Company property. Fine for private use; a public release or store listing would likely be taken down. Keep species swappable data and consider an original-creature skin.
- **Strict focus** can frustrate users with legitimate interruptions. Calls exempt; revisit after use.
- **Balance:** perks, items and transfer yields compound. Simulate a few months of play before fixing numbers.
- **Scope:** stats, items, perks and widgets are a lot. Milestone 2 should stay behind a clean interface so the UI work can start in parallel.

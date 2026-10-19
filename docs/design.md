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
             └──────── finished tasks ───────────┘  (candies, items, EVs via training plan)
```

Focus time brings new Pokémon. Finished tasks make Pokémon stronger. The buddy's perks feed back into both.

## Focus

- Minimum session 5 minutes; Deep Focus (motion-based on phones) boosts egg odds by one band (see `Egg` in `:shared`).
- **Strict mode, always on.** Leaving the app (backgrounding it on Android, losing window focus on desktop, hiding the tab on web) starts a **10 second grace period**. Returning in time keeps the session; otherwise the egg is lost and the streak is hit. No pause button.
- **Phone calls pause the timer** (incoming or ongoing); it resumes when the call ends.
- **One emergency pause per day**, up to 2 minutes, started by the player before leaving. Anything else that takes the player out of the app fails the egg after the grace period.
- Sweet spot is built in: Super Rares per focus-hour peak at about 60 minutes.

## Tasks

- Priority 1 to 5; **priority 5 pays the most**. Reward tables are already ported (`CandyRewards`).
- **Minimum task age:** a one-off task only pays when it has existed for at least *(tune: 15 minutes)* before being completed, counted from creation. Younger tasks complete without a reward. There is no daily cap, so many real tasks always pay.
- **Recurring tasks are exempt:** their instances are created ahead of time by the schedule, so they pay when their day comes.
- On-time bonus: finishing before the due date adds a bonus *(tune)*. **Overdue tasks pay the normal reward**, just without the bonus, so old tasks get finished rather than abandoned.
- **Subtasks** pay a flat 1 Rare Candy each *(tune)*, for up to 5 subtasks per task. The parent pays in full when it is finished. The minimum age applies to subtasks too.
- **Undo:** completing a task shows its reward and grants it after a 10 second undo window; undo cancels it. After that completion is final.
- **Categories** are for organising only and never change rewards. Six, replacing the old eight (School, Work, Hobby, Leisure, Chores, Health, Social, Others, where Hobby/Leisure overlapped and Others was a dumping ground):

| Category | Covers |
|---|---|
| Work | job, projects, admin |
| School | classes, homework, exams |
| Home | chores, errands, bills |
| Health | exercise, sleep, appointments |
| Social | friends, family, events |
| Personal | hobbies, leisure, anything else (the default) |

  Each has a colour used on the calendar and widgets. A weekly recap shows how the week split across categories.

## Planner (v1)

In scope for v1: tasks with priority, category, due date/time and reminders; **recurring tasks** (daily, weekly, monthly, custom); **subtasks**; an **own calendar view** (month and week, tasks by due date) that also feeds the agenda widget; and **read-only import of device calendar events** on Android (needs the calendar permission; shown on the calendar and agenda, never paid as tasks).

## Pokémon

Each owned Pokémon has: species, level 1 to 100, nature, ability, 6 IVs, 6 EVs, held item, friendship, shiny flag, nickname.

- **Stats** use the Gen 3+ formula:
  - HP = ⌊(2·Base + IV + ⌊EV/4⌋) · Level / 100⌋ + Level + 10
  - Other = ⌊(⌊(2·Base + IV + ⌊EV/4⌋) · Level / 100⌋ + 5) · Nature⌋, with nature ×1.1 / ×0.9
  - IV 0 to 31 rolled at hatch. EV 0 to 252 per stat, 510 total, gained from finished tasks through the Pokémon's training plan (below).
- **Natures:** the existing 25. Each raises one stat and lowers another (five are neutral).
- **Abilities:** each species has one or two; one is rolled at hatch. Abilities give buddy perks (below).
- **Shiny:** rolled at hatch, base chance **1 in 128** per egg (about a month at ~5 eggs a day), with a small bonus for longer sessions. Shinies get a distinct sprite palette and a hatch animation.
- **Levelling:** 10 Rare Candies per level (as now). A Pokémon more than 5 levels below your partner needs half the candies *(tune)*, so new hatches catch up without outshining the partner.
- **Evolution:** Super Candy at the species' evolution level (as now). Evolution stones for the species that use them.

### Training plan

Stat training is the player's choice, not tied to task type. Each Pokémon has a **training plan**: up to 2 focus stats, changeable at any time. When a task pays out:

- The buddy gains EVs in its focus stats, split evenly. Amount = the task's priority *(tune)*, so a priority 5 task gives 5 EVs.
- The rest of the party gains half *(tune)*, so a raised team is possible.
- No plan set: no EVs. The same minimum task age applies, so there is nothing to farm.

At about 5 tasks a day this fills the 510 EV budget in a few weeks; Vitamins are the shortcut.

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

**Sources (both):** common items drop at random from paid tasks (chance by priority) and come as streak, badge and Pokédex milestone rewards. Rare items (Nature Mint, Ability Capsule, Bottle Cap, stones) are bought in a shop with **coins** earned from play. **Coins** are earned from paid tasks (by priority), completed focus sessions (by minutes), and transferring duplicates, plus streak and badge bonuses. Amounts to be tuned. Nothing is sold for real money.

### Duplicates → candy

- Transfer any Pokémon you do not need for Rare Candies. Starting yields by rarity *(tune)*: Common 5, Uncommon 8, Rare 15, Super Rare 40. Perfect-IV or shiny ones show a warning first.
- You cannot transfer the last one of a species you have not registered, nor your buddy.
- This is the sink for hatches, so the PC never needs to be a graveyard.

## Roster

**Every species through Gen 9 (about 1,025) with all alternate forms**: regional (Alolan, Galarian, Hisuian, Paldean), gender differences, Mega / Gigantamax, and cosmetic forms (Unown, Vivillon, Alcremie, etc.). The 150-species list in `:shared` is a placeholder that will be replaced by generated data.

- **Mega / Gigantamax are a temporary focus form.** A buddy holding its Mega Stone (or Dynamax Band for G-Max species) shows that form for the length of a focus session, with a bonus to that session's perk *(tune)*, then reverts. The stones are reusable key items, one per species, from the shop or rare drops.
- **Cosmetic forms are a side collection** with their own progress bar and badge. 100% Pokédex means one of each species plus regional forms; cosmetic forms still appear when hatching.
- **Rarity by rule** from game data, with one tunable table: evolution stage, base stat total, and legendary / mythical status (legendaries and mythicals are Super Rare, babies and unevolved forms lean Common, final evolutions lean Rare). Hand overrides are not planned.
- **Art:** the Pokémon Showdown sprite sets (animated Gen 5 style where available, their sets for Gen 6 to 9 otherwise). Fetch and bundle them at build time rather than loading from Showdown at runtime, so the app does not depend on their server. The same IP caveat as the rest of the project applies (see Risks), and Showdown's own terms should be checked before any public release.

## Buddy and perks

The buddy can be **swapped freely at any time**. Perks apply when a reward is earned, so a player can pick an Egg luck buddy before a session. Friendship is per Pokémon, which gives a reason to stick with one.

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

- **Ability perk:** one extra, from the buddy's ability. Real abilities as flavour, data driven. Examples: Pickup (extra item chance), Early Bird (bonus for the first task before 9am), Technician (bonus for 5 to 20 minute sessions), Magic Guard (a failed session spares the streak once a week), Guts (bonus on priority 5 tasks), Synchronize (+25% EV gain in the buddy's focus stats).
- **Stats matter:** perk strength scales with the relevant stat (e.g. Attack for candy yield) and nature, so raising and training a partner pays.
- **Caps:** total bonus from perks and held item stays below +25% *(tune)* so no single buddy breaks the economy.

## Retention

- Daily streak: kept alive by one paid task **or** one completed focus session that day, weekly goals, and a Pokédex with seen/caught, region completion rewards and a trainer card.
- Badges for milestones (tasks finished, focus hours), shown as gym badges.
- **First online release:** friends and profiles (view a friend's buddy, party and trainer card) and **weekly friends-only leaderboards** (focus minutes, tasks finished, resetting weekly). Trading and co-op focus come later.

## Look and feel

- Gen 3 to 5 pixel art: GBA/DS-era text boxes, menus and sprites. Animated front/back sprites for the buddy.
- Hatch, level-up and evolution are full ceremonies (egg wobble, text box lines, cry, flash), skippable.
- Crisp scaling: nearest-neighbour, integer scale, no blur.
- Fonts: pixel font for headings only; body text stays a readable font.
- Sound: cries, short jingles; a mute toggle.
- Accessibility: content descriptions on all images, text scaling, colour-blind safe type chips.

## Screens and flows (proposal, to confirm)

Navigation is five bottom tabs: **Home / Planner / Focus / Collection / Me**.

- **Home:** the animated buddy with mood and a speech bubble; tap to pet (friendship, small daily cap). Streak flame, today's top tasks (tap to complete), a "Start focus" button at the default length, small candy and coin counters.
- **Planner:** list and calendar views, category filter, quick-add sheet (title, priority, due, then "more" for category, recurrence, subtasks, reminder). Completing a task opens a reward sheet with the 10 second undo.
- **Focus:** presets (15 / 25 / 45 / 60) plus custom, Deep Focus toggle, a short reminder of the strict rules, the egg during the session, the emergency pause button. On finish: hatch ceremony, then a summary card with actions (set as buddy, keep, transfer).
- **Collection:** Party (6), PC boxes (30 each), Pokédex (National Dex, seen / caught, forms tab, cosmetic-forms collection), Bag. The Pokémon detail screen shows stats, nature, ability, an IV "judge" (qualitative, like the games), training plan, held item, and actions: feed Rare Candy, evolve, transfer, nickname, set as buddy.
- **Me:** trainer card (badges, streak, totals), shop, friends and leaderboards (once online), settings (reminders, sound, theme, account linking, data export).
- **Onboarding:** title → start as guest → choose a starter from the starter set → name the trainer → create a first task (tutorial) → first focus session (tutorial).
- **Offline:** everything works offline. Because the server grants rewards, offline completions show as "pending" and resolve on sync; a focus session's start and end times are sent and validated for plausibility.

## Android widgets

1. **Today's tasks:** checklist, tap to complete.
2. **Buddy + streak (2×2).**
3. **Agenda:** upcoming tasks, optional read-only device calendar events.
4. **Start focus:** one tap, starts a default session.

Local-first: the app keeps a local database and syncs to Supabase, so widgets and offline use work. Completions from the widget queue and the server grants the reward on sync; the app shows what was earned next time it opens.

## Platforms

Android (full, plus widgets), desktop and web (same game, no widgets). Focus enforcement is looser on desktop and web (window focus or tab visibility only).

## Backend (Supabase)

**Accounts:** players start as a guest on the device (Supabase anonymous sign-in) and can later link an email or Google account to sync and keep progress. No passwords are stored in the app.

Server-authoritative for everything that grants value: hatching, candies, items, transfers, shiny rolls. Clients send intents ("finished task X", "hatch session Y") and the server validates and rolls. Row-level security so a user only reads and writes their own rows. Species, base stats, abilities and perk tables are data shipped with the app and mirrored on the server.

## Data to add

- Per-species base stats, abilities, types, evolutions and forms for **every species through Gen 9**, generated at build time from a public dataset (needs the build environment to reach it). Dex numbers move to the National Dex (the current 150 use custom numbers).
- A rarity for every species, assigned by rule (see Roster).
- Ability → perk table, type → perk table, item table, all as plain data with tests.

## Milestones

1. **Shared core (done in #2):** eggs, candy, levels, evolution, Pokédex.
2. **Species data pipeline:** generate species, base stats, abilities, forms, evolutions and rarity (by rule) for Gen 1 to 9 from a public dataset, plus the sprite fetch step. Replaces the 150-species placeholder; National Dex numbers.
3. **Pokémon depth:** IVs, EVs, natures with stats, abilities, shiny, items, transfer yields, perk calculation. Unit tested in `:shared`.
4. **UI shell:** desktop and web screens (needs `dl.google.com` allowed in the environment).
5. **Backend:** Supabase schema, RLS, reward functions, sync.
6. **Android app and widgets** (needs the Android SDK).
7. **Online:** friends and weekly leaderboards first; then trading and co-op focus.

## Risks

- **IP:** names, sprites and cries are Nintendo / Game Freak / The Pokémon Company property. Fine for private use; a public release or store listing would likely be taken down. Keep species swappable data and consider an original-creature skin.
- **Strict focus** can frustrate users with legitimate interruptions. Calls exempt; revisit after use.
- **Balance:** perks, items and transfer yields compound. Simulate a few months of play before fixing numbers.
- **Scope:** stats, items, perks and widgets are a lot. Milestone 2 should stay behind a clean interface so the UI work can start in parallel.

## Open questions

Being settled in rounds before any Pokémon-depth code is written. Decided: everything above not marked *(tune)* or *(proposal)*.

- **To confirm:** the Screens and flows proposal.
- **To tune by simulation before release:** rarity rule parameters across ~1,025 species, coin amounts, transfer yields, perk magnitudes and cap, catch-up discount, minimum task age, Mega bonus.
- **To check:** Showdown sprite terms and build-time availability, whether the build environment can reach the data sources, Android permission flow for calendar import.

# PokéLife Product Bible

**Status:** Authoritative product-wide specification  
**Last reconciled:** 13 November 2026 (later decisions live in their owning module and feature documents)

> This document records product-wide decisions and boundaries. It is not an implementation-status report. Detailed behavior belongs in the canonical module, feature, and architecture specifications listed below.

## 1. Product definition

PokéLife is an Android-first, local-first personal operating system built around a living Pokémon world. It combines practical life-management modules with a shared Pokémon collection and a PMD-inspired home environment.

**Core promise:** make everyday life easier to manage, and make managing everyday life more meaningful through Pokémon.

PokéLife must remain useful when the user ignores game systems. Pokémon must be integral to the experience without obstructing real-life tasks.

## 2. Non-negotiable product principles

1. **Utility first, Pokémon throughout.** Every life module must work without Pokémon rewards.
2. **One owner, one source of truth.** Every canonical record, shared service, and reward-bearing action has one authoritative owner.
3. **More is less.** Prioritize the primary action, remove redundant labels and cards, and reveal detail when useful.
4. **Low-friction and forgiving.** Fast capture, sensible defaults, easy resumption, and no punishment for absence, illness, interruption, or missed days.
5. **Honest automation.** Use reliable, documented integrations; provide manual or export/import alternatives; require confirmation for consequential actions.
6. **Local-first and private.** Core functionality works offline. No mandatory account, PokéLife cloud synchronization, advertising, telemetry, or subscription.
7. **Canonical Pokémon identity.** Preserve official species, forms, evolutions, types, moves, abilities, and lore where practical. Adapt mechanics only when required and document the adaptation.
8. **Reliability before novelty.** Empty/error states, process death, offline use, accessibility, migration, recovery, and performance are part of completion.
9. **Permanent, generous progression.** No time-limited Pokémon, expiring essential rewards, attendance penalties, decay, or excessive grinding.
10. **No scope by imitation.** Add a feature only when it solves a real user problem and fits the approved information architecture.

## 3. Product boundaries

- Android is the primary and only supported product platform (owner decision, 6 November 2026). The repository's desktop and web targets are development conveniences (screenshot tests, fast iteration): they may stay as long as keeping them compiling is cheap, they are not release targets, they gate nothing, and no Android design, storage or UX choice may bend to keep them working.
- PokéLife is local-first and offline-capable. Optional provider access does not become a core dependency. Local-only has priority (owner decision, 6 November 2026): there are no Supabase backend, accounts, cloud sync, friends board or server-side reward function, and none is to be built.
- No financial transfers, messages, or other consequential external actions occur without explicit user confirmation.
- Health, finance, relationship, journal, and household content remain within their owning domains. Shared progression receives only explicitly approved minimal, non-sensitive event metadata.
- No game mechanic may make real-life utility conditional on game participation or currency.

### Owner decisions of 13 November 2026 (answering the product review)

- **Personal use only.** PokéLife is built for the owner's own phone and is never published (no Play Store, no public release). Work that exists only for a public release is dropped: a protected release key (the committed key may stay), store and Play-policy justifications such as `QUERY_ALL_PACKAGES`, store listings. Licence duties still apply (CC BY-NC credits, REP-015), and so does everything that protects the owner's data.
- **Automated tests over manual checks.** Section 9's "actual-device verification" is met by automated tests that exercise the Android code (Robolectric on the JVM where it can, instrumented tests only where it cannot), real screenshot comparison in both palettes and at large text, and simulated process death, kill and corruption. No session hands the owner a manual checklist; the owner reports defects and possible optimisations as they come up in daily use.
- **Module priorities.** PLAN, CARE and WORLD are used daily and get depth first. MINT, HEAL, BASE and GEAR are to reach the level of a specialist app in their category. LINK is a private archive the player visits to record and look back on their people, memories and eras; it stays low-pressure and never nags.
- **BASE is single-user.** No household sharing or shared lists between people.
- **English only.** No localisation.
- **A watch is wanted** if one can be supported: a feasibility check first, against the owner's actual watch (the Huawei Watch Fit 4 Pro, which is not Wear OS; `ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` section 5.2), with the phone's notification actions on the watch as the baseline.
- **Lean tests.** A test exists to guard something that matters: a rule that pays, moves or changes a Pokémon, privacy and sealing, saving, migration and recovery, and the Android behaviour REP-033 covers. No test for a constant, a label or copy, a feature that was removed, or the absence of something removed, and no copy of a test per module when the modules share the code path. A test that no longer guards anything is deleted with the code it tested.
- **BASE** is the household module, taking Hoenn's Secret Bases as its Pokémon inspiration (its look and words borrow from them; its scene is a secret base under the trees). "Home" means only the Home tab. WORLD's destination is **Town**. Code uses `base-core`, `app.pokeplan.base`, `Base*`; stored names stay (the `home_*` tables, save keys, ledger ids, and the `Region.HOUSEHOLD`, `Module.HOME`, `Place.HOUSEHOLD` and `ReminderModule.HOME` constants). The module's specification is `MODULES/BASE.md`.

## 4. Canonical module map and ownership

| Module | Product responsibility | Canonical data owner |
|---|---|---|
| PLAN | Tasks, projects/lists, planning recurrence, goals, milestones, focus sessions, native planning events | PLAN |
| CARE | Self-care routines, reflection, Diary Cards, journal, mood/context check-ins, coping tools, Care Play and visitor state | CARE |
| HEAL | Activity, workouts, nutrition, hydration, sleep/recovery, measurements, personal health records and imports | HEAL |
| MINT | Accounts, balance snapshots, transactions, transfers, budgets, savings targets, bills and financial reports | MINT; provider remains authoritative for provider-originated facts |
| BASE | Chores, household inventory, supplies, assets, maintenance, warranties and household reference | BASE |
| LINK | Contacts, relationship notes, memories and eras, important dates and reminders | LINK |
| GEAR | Utility-specific settings and records for calculators, converters, timers, clocks, sensors and device tools | GEAR |
| WORLD | Expeditions, dungeons, missions, campaigns, story, settlement, Exploration Finds and Exploration Rank events | WORLD |

WORLD is the Pokémon adventure; HEAL owns physical health; BASE owns household management; LINK owns relationships; goals, projects and milestones belong to PLAN and reflection to CARE. The eight modules above are the complete set.

### Cross-module ownership rules

- A linked record is a reference or read-only projection, not a second canonical copy.
- CARE owns self-care routines; PLAN does not absorb them into planning recurrence.
- BASE owns a chore completion when a PLAN task is merely linked to that chore; the action must not pay twice.
- MINT owns bill and payment state. A linked PLAN task does not prove that a bill was paid.
- LINK owns a follow-up until the user explicitly converts it into a PLAN task. Conversion preserves one canonical completion and reward owner.
- GEAR may calculate or display information but does not take ownership of another module's records.
- External providers remain authoritative for their original facts; PokéLife stores only permitted local snapshots/imports with provenance.

## 5. Shared Pokémon and reward principles

`FEATURES/POKEMON_ECOSYSTEM.md` is the sole detailed authority for canonical species/forms, owned Pokémon instances, collection/storage, Pokédex, module parties, WORLD expedition teams, buddy, individual progression, evolution, Eggs, recruitment, Bag, Poké, reward ledger, and acquisition contracts.

Product-wide invariants:

- One canonical owned instance per Pokémon; parties and WORLD teams reference owned IDs and never create copies.
- A Pokémon is in **one place only** (owner decisions of 6, 7 and 8 November 2026; detail in `FEATURES/POKEMON_ECOSYSTEM.md` section 2.2): the **global buddy** (the main companion, cosmetic); a **module party** per applicable life module, **one lead plus five members, whose bonuses apply only to that module**; a module's **Guardian slot**; a **WORLD expedition team**; a **person's team in LINK** (up to six; the **Me** entry shows LINK's own party as that person's team, not a second roster); a **GEAR helper** (visual only, tool and menu icons); or the **PC**. Every assignment takes the Pokémon from its old place.
- One Bag and one universal currency: Poké.
- Held items are persistent, reusable, non-consumable equipment and never lifetime single-use: equipping or triggering an effect never consumes, charges, breaks or removes the item; encounter-scoped trigger limits reset for later eligible encounters. Runtime effect state is separate from inventory. Consumables such as evolution stones and vitamins use explicit atomic consumption rules.
- Every obtainable item has a documented acquisition source and enters the shared Bag through the shared reward/inventory ledger; no module mutates inventory directly.
- Source modules own eligibility for their activity; the shared Pokémon/economy service owns idempotent reward settlement and canonical reward creation.
- WORLD is the eligible source of ordinary Exploration XP events; the shared progression service owns Exploration Rank and ordinary Exploration XP (WORLD.md 14.3). Life-module activities do not advance expedition floors or Exploration Rank.
- Pokémon progression and acquisition should be generous and permanent. No breeding, required trading, paid Pokémon, seasonal exclusives, or punitive attendance loops.
- The initial World buddy can be selected from all official starters, including regional starter variants. Any owned Pokémon can later become the buddy without resetting story progress.

### Owner decisions of 11 November 2026 (answering the design review)

- **Parties:** six per module stay; the role section of a Pokémon is simplified instead (one "Move to..." place picker rather than a button per role).
- **Candies:** Exp. Candy (XS to XL) is the everyday wage and Rare Candy the occasional prize; Super Candy stays as the evolution item (level evolutions keep their Super Candy cost for now). Re-balancing changes payouts, so bump `RewardRules.VERSION` and update `docs/economy.md`.
- **Shiny odds** stay 1 in 128, and the module-named Pokédexes stay as they are.
- **Growth rates, base friendship** (everything starts at 70) and evolution at the level itself remain open (REP-048).

### Expanded Pokémon ecosystem scope

The approved staged expansion includes persistent held-item loadouts and effects, item acquisition/shop/crafting sources, abilities and Hidden Abilities, natures/Mints, moves and mastery, evolution items, training, Poké Ball identity, Ribbons/Marks/research, Mega/Z/Dynamax/Tera systems, Pokémon Jobs, contests, Pokéathlon, berries/cooking/camps, fossils, raids/outbreaks and Ranger-style field interactions. Shared ownership and detailed mechanics are specified in `FEATURES/POKEMON_ECOSYSTEM.md`; WORLD owns their player-facing gameplay where appropriate. These are staged features, not a claim of current implementation.

## 6. World and launcher

WORLD is a PMD-inspired idle/adventure game spanning PMD worlds, mainline regions, and eventually selected Pokémon Ranger regions. There are no humans. The opening uses Mythical Pokémon as catalysts for the player and buddy's adventure; regional campaigns are authored adaptations/echoes, not copies of existing plots. Rescue Team and Explorers remain distinct campaign families. All regions are accessible from day one at entry-level depth; deeper content unlocks through relevant progression.

`FEATURES/LAUNCHER.md` owns the in-app living home environment and optional Android default-home replacement. The launcher owns scene/layout preferences, app organization, and presentation state only; it does not own module records, Pokémon ownership, buddy selection, reward rules, or reminder scheduling.

## 7. Visual and content direction

- Use one PMD-inspired visual grammar with presentation intensity appropriate to each context.
- Utility modules use compact, readable, low-clutter surfaces; WORLD and the launcher may use richer scene presentation.
- Do not let pixel art, animation, sound, or dialogue obstruct practical actions.
- Preserve crisp pixel rendering and authored, character-consistent dialogue.
- Accessibility, scalable typography, TalkBack, reduced motion, contrast, and large touch targets apply across the product.

Owner decisions of 11 November 2026:
- **Identity: both.** The player is a **trainer** in every life module (name, trainer rank, the Oak-style welcome stay, and the missing Oak portrait is still needed). **WORLD uses teams** (the expedition team, Rescue/Explorers framing, no humans staged) with its own Normal to Diamond ladder. The two ladders need clearly different names and places so one screen never shows both as "Rank".
- **Numbers font:** try the PMD dialogue font (`pmd-dialogue`; Latin letters, digits and a few marks) for numbers (focus minutes, money, quantities, the calculator) and check that the zero reads as a zero, then decide how far it spreads. Per `FEATURES/DESIGN_SYSTEM.md` 5.3 it stays out of long reading, dense data tables and user-entered text, so money tables and notes keep a plain face. Try it before patching the DP font's zero.
- **Focus follows Forest:** strict failure and the 10 s grace stay (Deep Focus); add an allow list of apps usable without penalty (Usage Access, or a simpler "calls and these apps" list, decided against the permission policy in `ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md`), an ongoing notification and a break cycle; a stopwatch mode stays optional. The 10 s grace with a notification shade or a call needs a device check.

`FEATURES/DESIGN_SYSTEM.md` owns tokens, reusable components, geometry, typography, interaction states, accessibility, and shared presentation rules. `FEATURES/ASSETS_AND_AUDIO.md` owns asset resolution, manifests, provenance, licensing, rendering metadata, audio selection, and validation. Module documents define only their contextual use and justified adaptations.

## 8. Integrations, privacy, and data

- Permissions are requested just in time, in context, and only for an implemented capability.
- Permission denial must not disable unrelated or manual workflows.
- Prefer official APIs and documented platform interfaces. Do not scrape credentials, bypass security, or depend on private/undocumented interfaces.
- Calendar events retain external calendar ownership unless a user explicitly creates a native PLAN event.
- Health imports retain source, timestamp, units, provenance, and freshness. Huawei Watch Fit access is feasibility-dependent and must be verified on the actual device/firmware before being advertised.
- Philippine financial provider integrations must be described honestly. Notification parsing or manual import is not a bank API and does not establish a live balance connection.
- No background location tracking, location history, location-derived Pokémon encounters, SMS reading, or broad notification access in the baseline.
- Private CARE, HEAL, MINT, BASE, and LINK content never enters WORLD or shared reward presentation. Only the specific minimal event contracts approved in the owning module and Pokémon specification may cross that boundary.

`ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` owns cross-module Android permissions, provider adapters, integration capability status, privacy/security boundaries, and consent. `ARCHITECTURE/DATA_AND_MIGRATIONS.md` owns persistence, transactions, IDs, backup/restore, schema migration, and recovery. `ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` Section 9 owns shared reminder scheduling, quiet hours, focus deferral, delivery, action routing, recovery, and reminder privacy.

## 9. Definition of done

A capability is not complete because it is specified, has a screen, or has code present. It must have:
- one explicit data owner and source of truth;
- working offline behavior where applicable;
- empty, loading, success, error, denied-permission, and interruption states;
- persistence across process death and app updates;
- safe migration, backup, restore, and destructive-action behavior;
- automated tests for domain rules, edge cases, and migrations;
- accessibility and dark-mode review;
- acceptable performance on target hardware;
- successful end-to-end workflow verification and, where relevant, actual-device verification;
- updated authoritative documentation and changelog.

Do not maintain a separate global decision register; approved decisions are recorded in their authoritative document.

## 10. Decision precedence

1. Latest explicit owner-approved decision, incorporated into its authoritative document.
2. This Product Bible for product-wide rules.
3. The relevant module or feature specification for detailed behavior.
4. Architecture specifications for shared technical contracts and implementation foundations.
5. Implementation Guide for baseline evidence, current implementation status, dependencies, and roadmap.
6. Existing code, source archives, README files, and historical documents as evidence, not product authority.

A genuine conflict with an approved decision must be surfaced for approval; do not silently resolve it by changing product behavior.

## 11. Canonical documentation inventory

The authoritative documentation root is `docs/pokelife/` (paths below are relative to it). `README.md` there explains how it relates to the repository's older documents.

```text
docs/pokelife/
  README.md  # orientation only; not a specification
  PRODUCT_BIBLE.md
  IMPLEMENTATION_GUIDE.md
  IMPLEMENTATION_BACKLOG.md  # active delivery plan; not product rules
  MODULES/
    PLAN.md
    CARE.md
    HEAL.md
    MINT.md
    BASE.md
    LINK.md
    GEAR.md
    WORLD.md
  FEATURES/
    LAUNCHER.md
    POKEMON_ECOSYSTEM.md
    ASSETS_AND_AUDIO.md
    DESIGN_SYSTEM.md
  ARCHITECTURE/
    DATA_AND_MIGRATIONS.md
    INTEGRATIONS_AND_PRIVACY.md
    TEST_STRATEGY.md
```

Each document under this directory is a canonical authority for its stated domain. The Pokémon ecosystem specification now includes the formerly separate expansion catalogue and mechanics. Shared reminder policy is consolidated into Section 9 of `ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md`. The implementation backlog remains a separate, traceable delivery artifact and does not define product behavior. `TEST_STRATEGY.md` is the authoritative cross-layer testing strategy.

Also in the folder: `evidence/` (dated reviews and audits, evidence only) and `FEATURES/POKEMON_PLACES_IDEAS.md` (proposals for what Pokémon do inside the modules, tracked as REP-053; an idea binds only once it is written into the owning module or ecosystem file).

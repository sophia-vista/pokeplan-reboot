# PokéLife — CARE Module

**Version:** 1.4  
**Status:** Owner-approved target specification  
**Decision status:** Owner-approved requirements incorporated directly below  
**Baseline reviewed:** Source archive `0.123.0`  
**Last consolidated:** 5 November 2026

> This document defines the intended CARE experience. It is not a claim that every feature is already implemented. The Product Bible remains the product-wide authority; this file is the single detailed source of truth for CARE. The Implementation Guide records current implementation status and delivery work. Approved decisions are incorporated into the relevant requirements below; there is no separate CARE decision register. The shared visual source of truth is `../FEATURES/DESIGN_SYSTEM.md`; CARE must follow it and may define only the adaptations specific to CARE. Shared reminder scheduling, delivery, global quiet hours, focus deferral and recovery are defined in `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`; this document defines only CARE-owned reminder sources, preferences and action semantics.

## 1. Purpose and boundaries

CARE is a private space for reflection, emotional self-care, routines, and practical coping tools. It should be useful without Pokémon rewards and should not turn self-care into a performance system.

CARE owns:
- Routines and self-care actions.
- Calm / grounding tools.
- Diary Cards and mood/context check-ins.
- Journals, notes, prompts and personal reflections.
- DBT/CBT reference material, skills and guided exercises.
- CARE history and trends.
- CARE's Pokémon interaction experience (Care Play and the Care Garden/Meadow).
- CARE Egg progress and visitor relationship/recruitment events, using shared Pokémon and Bag services.

CARE does not own HEAL activity, fitness/workouts, nutrition, hydration, sleep, body measurements or personal health records. HEAL is the authoritative owner of physical-health records and is specified in `../MODULES/HEAL.md`. CARE may link to a HEAL record only through an explicit user action; it must not copy health measurements or create a competing health log. CARE does not own a second Pokémon collection, party, Bag, currency or independent egg-hatching system. It uses the shared Pokémon collection, inventory and egg progression.

## 2. Visual presentation

CARE uses the shared PMD dialogue-box visual grammar defined in
`../FEATURES/DESIGN_SYSTEM.md`. It must not introduce a separate CARE
design language. Its presentation can be calmer and more expressive than
PLAN, but shared typography, spacing, padding, pixel border treatment,
button styles, surface tokens, interaction states and accessibility
remain consistent.

- Use compact primary/secondary dialogue-box surfaces for Today actions,
  routine summaries, check-in status, prompts and concise feedback.
- Use a more expressive dialogue-box treatment for buddy/visitor
  interactions and authored Pokémon responses where this improves the
  experience; do not turn the whole CARE landing into a chat transcript.
- Diary Card and Journal prioritize writing comfort, legibility and
  usable content width. Their input areas must not inherit cramped game
  dialogue typography or fixed speech-bubble dimensions.
- Calm tools use restrained panels and clear controls; animation,
  decorative effects and sound remain optional and subordinate to the
  exercise.
- Care Garden/Meadow retains its scene-first composition. Contextual
  Pokémon interaction panels, visitor details and progress feedback use
  shared dialogue-box components rather than bespoke visual chrome.
- Keep routine rows, history, trends and skills content scannable; do
  not wrap every row, metric or paragraph in its own dialogue box.
- All CARE surfaces support light/dark themes, system text scaling,
  TalkBack, minimum touch targets and reduced motion. Sensitive content
  must remain readable without relying on color or decorative cues.

## 3. CARE landing and navigation

The CARE landing is a calm, action-oriented entry point. It must not compete with the Home dashboard or become a catalogue of every feature.

Primary areas:
1. **Today** — next useful action, today's routines, concise completion progress, and check-in status.
2. **Calm** — quick grounding, breathing and coping tools.
3. **Reflect** — one destination with two distinct modes: Diary Card (structured check-in) and Journal (free-form writing).
4. **Care Garden** — prominent entry to the Pokémon interaction environment; it is a companion experience, not another task dashboard.

Skills library and History (trends, prior diary cards and journal archive) are secondary destinations. Settings remain in CARE settings. The buddy should be present contextually, not as a large competing dashboard card. Visitor/party management must not crowd the daily-care workflow.

### 3.1 Today and routines

- Make the next useful action obvious without explanatory paragraphs.
- Show a compact routine checklist and quiet daily reward progress where applicable (for example, `2/5 today` if the existing reward rule remains in force).
- Completion gives immediate, compact feedback. Support undo and skip where appropriate.
- Routine reminders are optional and individually configured; no time is required to create a routine. Scheduling and delivery use `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.
- Do not use shame copy, broken streaks, missed-day penalties, aggressive reminders or compulsory check-ins.
- Preserve routine history and allow safe archiving/restoration.

### 3.2 Calm

Provide short, accessible tools that can be entered and exited quickly. Activities should not require a mood disclosure or journal entry. Keep the tool itself distinct from diary tracking and avoid presenting the module as diagnosis or treatment.

### 3.3 Reflect

Diary Card and Journal remain separate data concepts and records while sharing one Reflect destination.

- **Diary Card:** structured, optional daily check-in for feelings, urges, skills used and optional note. Use a compact form with collapsible optional sections and one save action. An independently configurable, optional once-daily reminder may be offered; shared scheduling and delivery follow `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.
- **Journal:** free writing, prompts, tags and past entries. Search, edit, delete, export and linking should be straightforward.
- A saved-state indicator and one clear action should make today's check-in status visible.
- No streak or penalty for missing a check-in.
- Never inspect or reward the emotion values, urge ratings, note content, positivity, or amount of personal disclosure. If a completion incentive is used, it may only recognize the act of saving a check-in with a fixed small increment; the contents are not inputs to rewards.

### 3.4 Privacy boundary

Diary entries, journal text, mood data, urges, therapy-related content, skills responses and other sensitive personal records remain private CARE data. They must never be sent to World, the launcher, other modules, a shared profile, analytics, or the Pokémon reward engine. Only explicitly documented non-sensitive activity metadata may cross into shared progression. No content-derived rewards. Local-first storage and export/backup follow the Product Bible's privacy and data rules.

## 4. Care Play

Care Play is the Pokémon relationship interaction system. It should feel like spending time with Pokémon, not maintaining virtual pets or completing chores.

### 4.1 Pokémon participation

- The buddy is always accessible and is the primary companion.
- Current visitors can be interacted with while visiting.
- Other owned Pokémon may be invited from the shared collection without changing CARE party membership.
- No Pokémon requires daily attention. Leaving a Pokémon alone has no negative consequence.
- Interactions are freely repeatable.
- The first meaningful interaction with each Pokémon per day may grant the normal progression reward. Repeated interactions remain available for unique reactions, dialogue and enjoyment, but grant reduced or no additional progression.
- There is no daily interaction quota, forced schedule or missed-day penalty.

### 4.2 Entry and activity discovery

Use a hybrid entry model:
- Optional Pokémon/activity suggestions based on personality or context.
- An activity browser for deliberate selection.
- Optional “Surprise me” selection.

Suggestions never expire and ignoring them has no consequence. Do not force a single prescribed activity.

### 4.3 Individual preferences

Each Pokémon may have one primary and one secondary preferred affection/interaction style. Preferences are individual to that Pokémon, not automatically shared by all members of a species.

- Preferences begin undiscovered.
- Discover them naturally through Care Play; use subtle behavioral reactions, occasional dialogue or hints.
- Show only discovered preferences on the Pokémon profile.
- Discovery is gradual and optional; users do not need to test every activity.
- Preferred interactions produce a more meaningful response and may improve relationship progression, but other interactions remain valid and rewarding.
- No wrong answers, negative affection, compulsory discovery or repetitive testing.
- Preferences remain stable unless an authored evolution/story event explicitly changes them.

### 4.4 Activity design standard

- A varied set of short activities, generally 30–90 seconds.
- Activities map to interaction types such as grooming, toy play, puzzles, gift/treat selection, encouragement, gentle touch and shared quiet time.
- Simple touch, matching, observation and light timing; one-handed and interruption-friendly.
- No failure states, lives, punitive scores or difficult controls.
- Allow quick exit and safe resume where useful.
- Use species-specific reactions and dialogue only where supported by available content.
- Reuse existing visitor interaction categories and scene mechanics where they meet this standard; extend them rather than rebuilding without cause.

### 4.5 Rewards and sensitive-data isolation

- Bond and relationship development belongs to Pokémon relationship state.
- Meaningful Care Play interactions contribute to Care Egg progress.
- Visitor interactions may advance that visitor's recruitment relationship.
- CARE grants no CARE module XP and introduces no new currency.
- Use existing shared items/reward services only; do not create duplicate Bag, currency or ownership.
- Journal, diary, mood and other sensitive data never contribute to rewards.

## 5. Care Garden (working name: Meadow)

The Meadow is a dedicated, fixed interaction scene for the buddy and visitors. It is not the Android launcher and does not simulate free roaming.

### 5.1 Scene and layout

- One illustrated garden scene fitted to the available screen.
- No scrolling, panning or camera movement.
- Buddy has a consistent central position.
- Up to four regular visitors occupy designated surrounding positions.
- Assign visitor positions dynamically from a fixed set of spots; the scene composition remains recognizable at every visitor count.
- Pokémon do not freely roam, pathfind or autonomously move between activity areas.
- Available idle animations, occasional expression changes and simple reactions provide restrained ambient life.
- No hunger, fatigue, boredom, needs, schedules, simulated routines, resource farming or maintenance chores.
- No neglect mechanics, affection decay or time-based penalties.
- Environmental effects remain subtle and must not compete with Pokémon or interaction targets.

### 5.2 Controls and gestures

Keep persistent controls minimal:
- Back.
- Small visitor-queue indicator.
- Compact Care Egg progress indicator.
- No permanent activity toolbar.

Pokémon gestures:
- **Tap:** immediately triggers a lightweight, context-sensitive reaction using basic behavior/state and available assets. It is not necessarily a progression activity.
- **Long press:** opens a compact Pokémon interface with identity, discovered preferences, available activities, and visitor relationship progress when applicable.
- Provide an accessible alternative to long press (for example, a visible/selectable action exposed through TalkBack or a contextual control).
- Tap reactions have a short per-Pokémon cooldown. Repeated taps during cooldown must not restart full animations or repeatedly play cries; a subtle response is acceptable. Long press remains available during cooldown.
- Do not connect ordinary tap reactions to discovered affection preferences; keep that system separate and avoid extra state complexity.

### 5.3 Ambient reactions

Tap reactions can reflect simple states (sleeping, playful, relaxed, hungry when a treat is offered, etc.), but do not build a complex behavioral simulation. Prefer a small number of reusable reactions and graceful fallbacks.

### 5.4 Customization

Meadow customization is deferred and is not a current priority. Use the existing environment and assets. Do not implement furniture placement, decoration inventories, theme shops or a building system as part of the initial Meadow. Revisit only after core interactions and usability are complete.

## 6. Visitor system

Visitors are a permanent, low-pressure recruitment and relationship loop. They are not daily chores or expiring invitations.

### 6.1 Arrival, capacity and queue

- Visitors arrive naturally over time; multiple may arrive on one day.
- Arrival frequency is independent of routines, Diary Cards, mood check-ins and other personal-care activity.
- Maximum of four regular visitors at once, in addition to the buddy.
- New visitors enter a queue when all four slots are occupied.
- Queued visitors do not expire or disappear. When a slot opens, the next queued visitor may enter.
- Do not automatically replace or displace existing visitors.
- No arrival countdowns, attendance requirements or missed-day penalties.
- Queue display is visible but unobtrusive.

### 6.2 Visitor selection and individuality

- Select visitors from a varied, weighted species pool.
- Common and uncommon species can appear; rare species appear less often but do not require grinding or streaks.
- Previously encountered visitors may return.
- Seasonal or environment-themed visitors may exist only as permanent content, never as limited-time exclusives.
- Generate individual visitor identity/personality/preferences. Do not treat one visitor's favorite action as a permanent species-wide preference.
- The selection system should support a generous roster without forcing a fixed daily quota.

### 6.3 Befriending and relationship progress

- A visitor can be interacted with directly; the user does not need to complete another CARE activity first.
- Preserve and refine the existing visitor interaction scene, five existing interaction categories (kind words, sitting together, offering treats, tidying, gentle touch), species reactions, berry handling, cries and feedback where assets support them.
- Preserve the spirit of the existing warm-visit relationship/recruitment system, but replace the rigid three-action daily visit and relationship-day quota with flexible meaningful interactions.
- Meaningful interactions advance persistent visitor relationship progress and may contribute to Care Egg progress.
- Relationship progress never resets because of absence, app inactivity or a missed day.
- No daily attendance quota, expiring visit, forced interaction count or prerequisite routine completion.
- Favorite interactions can provide a stronger positive response or more efficient relationship progress, but no other interaction is a wrong choice.

### 6.4 Retention, departure and dismissal

- Visitors remain until befriended or manually dismissed.
- No three-day departure rule, countdown or automatic loss of relationship progress.
- New visitors arrive only when a slot is available; existing visitors are not automatically replaced.
- Dismissing a visitor does not revoke any Pokémon, egg or other permanent reward already earned.
- Dismissal should be deliberate and recoverable where practical; do not make it a routine maintenance action.

### 6.5 Recruitment reward

When a visitor is befriended, resolve one of two outcomes:

1. **Default outcome — visitor egg:** the visitor leaves an egg that hatches into that visitor species' first evolutionary stage.
2. **Rare outcome — direct recruitment:** the actual visiting Pokémon joins the shared collection directly, at a notably higher starting level with stronger stats / a more developed starting build.

Rules:
- Every successful befriending permanently rewards a Pokémon, either through the egg or direct recruitment.
- Eggs remain the normal outcome; direct recruitment is a rare bonus.
- Visitor rarity modestly increases the chance of direct recruitment, but does not guarantee it. Common visitors can also join directly.
- Direct recruitment is not required for completing any collection or progression goal.
- Use shared Pokémon ownership and Bag systems; no CARE-only collection.

### 6.6 Egg hatching and inheritance

- Visitor eggs enter the shared Pokémon Egg collection and use the shared hatching/progression system.
- No real-time countdown, expiry or forced hatching schedule.
- Egg species is the visitor's first evolutionary stage.
- Visitor eggs may lightly inherit selected characteristics from the visitor: personality/nature influence, potential stat strengths, and a rare special trait or cosmetic variation.
- Inheritance is probabilistic and light; it does not determine the hatchling's whole build. The hatchling receives its own individual traits.
- No breeding interface, pair selection, breeding optimization or repeatable breeding loop is introduced. This is a special visitor gift/recruitment outcome, not a general breeding system.
- Eggs and hatchlings retain a small origin record: visitor species and individual identity, befriending date, inherited traits where applicable, and a short origin note. Preserve the link after hatching.

### 6.7 Departure and return visits

- After befriending, the visitor leaves the Meadow following the recruitment scene, whether the outcome is an egg or direct recruitment.
- The visitor slot becomes available for the next queued visitor.
- The visitor's identity and relationship history remain represented by the egg origin record or recruited Pokémon record.
- Previously befriended visitors may occasionally return as familiar faces.
- Returning visitors are social/reunion appearances, not regular recruitment candidates; they do not occupy the four regular visitor slots and cannot award duplicate eggs or recruitment.
- Reunion interactions may include unique dialogue, reactions and references to the original visit and relationship history.
- No separate keepsake collection, memento album or keepsake inventory.

## 7. Asset and animation policy

PMD asset coverage is uneven by species, form, action and expression. The implementation must inspect actual availability and must not assume universal animation support.

Priority:
1. Existing PMD SpriteCollab overworld sprites, animations and portraits.
2. Existing compatible mainline/PokeAPI/Showdown assets as a fallback where PMD coverage is absent.
3. Existing project effects and sound effects for small feedback where appropriate.
4. Simple authored UI effects only where necessary; Pixelarticons remain UI glyphs, not Pokémon or environmental-art substitutes.

Implementation requirements:
- Build or use a per-species/form asset manifest describing available idle, sleep, hop, walk, eat, portrait expressions, cries and other supported reactions.
- Reuse available idle, sleep, hop, eating and directional sprites; do not fabricate missing animation frames or infer that all Pokémon support the same action.
- Graceful fallback order: closest available sprite animation; sprite nudge/scale or small positional reaction; portrait expression; subtle generic effect; static sprite. Select the fallback that best preserves pixel-art consistency.
- Do not play a cry for every tap. Use sound selectively and respect sound/reduced-motion settings.
- Preserve nearest-neighbor scaling, integer scaling where practical, stable sprite anchors and consistent pixel grid.
- Reuse the existing visitor scene, five interaction categories, sprite reactions, berry handling and sound mappings where they pass review. Audit sound mappings by listening rather than assuming enum names are correct.
- Do not create new backgrounds or decoration packs for the initial Meadow. Existing world/scene/tileset assets are sufficient to prototype; customization is deferred.
- Track source, author, license, attribution, modification and distribution permissions for every asset. Existing asset audit notes include non-commercial/private-use restrictions for some sources; do not assume that current assets are cleared for public or commercial distribution.

## 8. Current implementation reuse and refactor direction

The reviewed 0.123.0 baseline already has a substantial CARE foundation: routines, calm breaks, Diary Card, Skills, Journal, Trends, reminders, visitor interaction scenes, visitor relationship/recruitment, Care Egg progress, and CARE rewards.

Preserve and audit before replacing:
- Existing CARE screens, records, reminders and history.
- Existing visitor interaction scene and five interaction categories.
- Existing species reactions, berry handling, interaction prompts, visitor favorites and free-play actions.
- Existing visitor relationship/recruitment and Care Egg plumbing.
- Existing shared Pokémon, Bag and egg-hatching services.

Refactor gaps:
- Remove the visitor unlock prerequisite that requires completing a paid CARE activity.
- Remove one-visit-per-day and rigid three-distinct-actions-per-visit requirements.
- Remove three-day visitor departure and relationship-progress reset.
- Replace daily relationship-day attendance with persistent flexible meaningful-interaction progress.
- Replace visitor random favorite as a proxy for permanent Pokémon preferences; keep per-visitor favorites distinct from persistent individual preference data.
- Add buddy/owned-Pokémon Care Play access and the fixed Meadow scene.
- Ensure visitor queue, four-slot capacity, returning befriended visitors and the egg/direct-recruitment outcome are consistent with shared collection state.
- Audit existing reward formulas; do not invent new XP or currency. CARE grants no module XP.

Do not claim a feature is complete because a screen, model or asset exists. Verify end-to-end behavior, persistence, migration, tests and device presentation.

## 9. Data and event ownership

CARE owns private records and the visitor relationship state. Shared Pokémon services own actual Pokémon ownership, Bag items, eggs, hatching and global progression. A CARE event may emit only the minimum non-sensitive event metadata needed for the approved reward outcome.

Suggested conceptual records (adapt to the final architecture; avoid duplicate stores):
- `CareRoutine` and completion history.
- `DiaryCard` and `JournalEntry` as separate record types.
- `CarePlayPreference` keyed to an individual Pokémon ID, with primary/secondary preference and discovery state.
- `CareVisitor` with unique visitor ID, species/form, generated individual traits, arrival/queue state, relationship progress, favorite interaction and befriended/dismissed state.
- `VisitorOrigin` attached to the visitor egg and hatchling.
- `CareGardenState` for fixed spot assignment and queue order; presentation state must be reconstructible and not become a second Pokémon ownership store.

Use stable IDs and idempotent reward events. Do not derive rewards from private content. Persist visitor progress and queue state across app restarts and migrations. Define deterministic handling for duplicate visitor generation, a visitor species already owned, app restore, and dismissed visitors.

## 10. Acceptance criteria

### CARE utility and privacy
- CARE landing presents Today, Calm and Reflect clearly, with Care Garden accessible without displacing core care actions.
- Diary Card and Journal remain distinct, searchable/editable/exportable records.
- Routine and diary reminders are optional and non-nagging.
- Sensitive CARE content never enters shared progression or World.
- Missed days do not break streaks, remove rewards or harm Pokémon relationships.

### Care Play and Meadow
- Buddy, visitors and invited owned Pokémon can participate without changing CARE party membership.
- Activities can be selected through suggestions, browser or Surprise me.
- A user can complete or leave an activity in 30–90 seconds without losing state unexpectedly.
- The Meadow is one fixed screen, with central buddy and up to four designated visitor spots; no free roaming, camera motion or scrolling.
- Tap and long-press behavior is reliable, accessible and does not conflict with cooldowns.
- Missing PMD animations never cause broken sprites or fabricated frame sequences.
- Reduce Motion, sound settings, dark mode, enlarged text and TalkBack are supported.

### Visitors and rewards
- Visitors can arrive independently of CARE task/check-in completion.
- Capacity is four regular visitors plus buddy; queue entries persist and do not expire.
- Visitor relationship progress survives absence, process death and app restart.
- Befriending always results in a visitor egg or direct recruitment.
- Direct recruitment is rare; rarity only modestly affects odds.
- Visitor eggs hatch through the shared system, retain origin and use light inheritance.
- Befriended visitors can return without occupying regular slots or duplicating rewards.
- Dismissal never revokes permanent rewards.

### Engineering and QA
- Add unit tests for visitor selection, queue order, capacity, relationship progress, dismissal, recruitment odds, egg metadata/inheritance and return-visit duplicate prevention.
- Add migration tests for old visitor state, including visitors previously subject to three-day departure and relationship-day progress.
- Test process death, offline use, backup/restore, clock/time-zone changes and duplicate event delivery.
- Validate scene fit and touch targets on target Android device and common aspect ratios.
- Profile sprite decoding, animation lifecycle, memory and battery; stop animation work when the Meadow is not visible.
- Maintain asset provenance/licensing metadata and verify public-distribution rights before release.

## 11. Explicit non-goals

## Shared data architecture
CARE owns its routines, diary cards, journal entries, check-ins and completion history. Shared persistence, transaction boundaries, backup/restore, deletion, migration and recovery follow `../ARCHITECTURE/DATA_AND_MIGRATIONS.md`; CARE must not create a parallel save store or expose private reflection content through generic events.

- Free-roaming Pokémon in the Care Garden (free roaming belongs to the launcher).
- A pet-needs simulator: hunger, fatigue, boredom, neglect or maintenance.
- Visitor attendance streaks, expiring invitations, automatic departure or relationship decay.
- Daily interaction quotas or mandatory Care Play.
- CARE module XP or a new CARE currency.
- Content-derived rewards from mood, diary, journal or therapy-related records.
- General Pokémon breeding or breeding optimization.
- Meadow furniture placement, decoration inventory, theme shop or customization in the current priority scope.
- A separate visitor egg/hatching implementation.
- A separate keepsake inventory or album.

## Shared Pokémon ecosystem contract

CARE's acquisition identity is **Care Play and visitor befriending**, including persistent Care Eggs and the approved rare direct-recruitment outcome. CARE owns visitor eligibility, interaction and relationship lifecycle; the shared Pokémon ecosystem owns Egg persistence/hatching, canonical owned instances, global collection, party references, Bag/Poké and idempotent reward settlement. CARE has its own independent party. CARE's private journal, Diary Card, mood, urges, skills responses and therapy-related content must never enter Pokémon progression or reward events. See `../FEATURES/POKEMON_ECOSYSTEM.md`.

---

## Testing reference

CARE routine and journal persistence, reminder behavior, privacy isolation, interrupted completion and reward ownership must follow [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md). Private journal/mood content must not appear in shared events, logs or notifications.

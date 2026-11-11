# PokéLife LINK Module Specification

**Version:** 1.0  
**Status:** Specified / approved  
**Module:** LINK  
**Product Bible:** `../PRODUCT_BIBLE.md`, §§3–5  
**Shared visual authority:** `../FEATURES/DESIGN_SYSTEM.md`  
**Shared reminder authority:** `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`  
**Shared Pokémon/economy authority:** canonical shared Pokémon and economy specification  
**Implementation roadmap:** `../IMPLEMENTATION_GUIDE.md`, Phase 8

> LINK is a private, manual-first relationship organizer. It helps the user remember people and commitments without turning relationships into a performance metric.

---

## 1. Purpose and product identity

LINK is the personal relationship and social-life module. It provides one calm, dependable place to keep contact details, useful context, important dates, personal notes and follow-ups.

**Core principle:** Remember what matters. Make it easier to stay connected. Never gamify the people in the user's life.

LINK must be useful as a private organizer even when all Pokémon progression is disabled. It must not create pressure to contact people, reward contact frequency, or judge the quality of a relationship.

### 1.1 Goals

- Keep important people and their relevant details easy to find.
- Preserve useful context the user chooses to record.
- Remember birthdays, anniversaries and other meaningful dates.
- Capture follow-ups and commitments without creating a second task manager.
- Support quick manual entry and optional, permission-controlled device-contact import.
- Offer optional, low-pressure Pokémon discovery through the Postcard Trail.

### 1.2 Explicit non-goals

LINK is not:
- A messaging, calling, email or social-media client.
- A social network, CRM, sales pipeline or contact-enrichment service.
- A relationship-quality, intimacy, popularity or responsiveness tracker.
- A location tracker, call/SMS monitor, social-media scraper or activity surveillance tool.
- An automatic message sender or an AI that independently contacts people.
- A second calendar, task manager, notes app or reminder scheduler.
- A place to store passwords, payment credentials or unnecessary sensitive information.

No contact is ever contacted, notified or messaged by PokéLife.

---

## 2. Information architecture

LINK has four closely related destinations. Keep navigation shallow; notes and follow-up history should also be visible in the relevant person's profile.

### 2.1 People

The main directory of manually created and optionally imported contacts.

- Search by name, alias, organization or user-entered searchable detail.
- Optional user-defined relationship category, such as family, friend, colleague, service provider or custom.
- Optional groups/tags for organization. Avoid mandatory categorization.
- Sort by name or recently updated; do not rank by importance or engagement.
- Favorite/pin is a convenience for directory access, not a relationship score.
- Support contacts with only a name; phone number, email and other fields are optional.
- Duplicate detection should suggest possible matches without silently merging records.

### 2.2 Person profile

A profile is the central place for one person's LINK-owned information.

**Overview**
- Name and optional preferred name.
- Optional phone, email, organization, role, address and other user-entered fields.
- Relationship category and tags.
- Important dates and upcoming date reminders.
- Next open follow-up, if one exists.
- Source/provenance for imported fields where relevant.

**Notes**
- Free-form relationship context and dated notes.
- User-controlled tags/search.
- Notes are private to LINK and are never included in notifications or Pokémon progression.

**Follow-ups**
- Open and completed follow-ups associated with this person.
- Completion history is shown as a factual record, not a score or streak.
- A follow-up may be linked to a PLAN task when the user explicitly chooses to convert it.

Do not display inferred relationship health, contact frequency, response time, or activity charts.

### 2.3 Important Dates

A compact list of dates associated with people or optionally recorded independently.

Supported date types:
- Birthday.
- Anniversary.
- Personal milestone.
- Custom date.

Each date supports:
- Title/type and month/day.
- Optional year, including unknown year.
- Annual recurrence or one-time occurrence.
- Optional associated person.
- Optional advance reminder preference.
- Optional private note.

A date without a known year remains valid. Leap-day recurrence must have a documented, predictable rule. Deleting a person must not silently delete an independently meaningful date; ask whether to retain or remove linked dates when necessary.

LINK stores date records. Shared reminder scheduling and notification delivery remain owned by `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`. LINK does not automatically create Google Calendar or Android Calendar events.

### 2.4 Follow-ups

Follow-ups are lightweight reminders of something the user intends to do or revisit.

Fields:
- Required title.
- Optional linked person.
- Optional due date/time.
- Optional context note.
- Optional reminder preference.
- Status: Upcoming, Due, Completed or Cancelled.
- Created, updated and completed timestamps.

Behavior:
- Quick-add requires only a title.
- A follow-up may exist without a linked person.
- Due dates are optional; undated follow-ups remain in the list.
- Completing a follow-up records completion once and preserves its history.
- Reopening a completed follow-up is allowed but must not pay a second Pokémon reward.
- Cancelled follow-ups do not earn rewards.
- Overdue items are presented neutrally; no shame language, escalating warnings or negative game consequences.
- No automatic follow-ups are generated from contact imports, dates, messages or call history.
- Do not nag the user to contact someone or imply that a relationship is deteriorating.

A follow-up can be converted to a PLAN task through an explicit user action. Conversion must preserve a stable cross-reference and avoid duplicate ownership:
- Before conversion, LINK owns the follow-up and its status.
- After conversion, PLAN owns the actionable task, scheduling and task completion.
- LINK retains the person association and relationship context, and displays the linked PLAN task status.
- A single completion must not be recorded as two independent real-world actions or paid twice.
- Deleting or unlinking either side must follow an explicit, recoverable link policy; it must not silently delete the other module's record.

### 2.5 Search and organization

- One search entry point for people, notes, dates and follow-ups.
- Filters for category, tags and follow-up status.
- No automatic categorization based on message content or inferred identity.
- Search indexes remain local and are rebuilt safely after migration.

---

## 3. Primary workflows

### 3.1 Add a person

1. Create a person manually with a name.
2. Add only the details that are useful.
3. Optionally assign a category, tag or favorite.
4. Optionally add a date or note.
5. Save without requiring a phone number, email or device-contact permission.

### 3.2 Capture context

1. Open a person.
2. Add a short note or dated context entry.
3. Save immediately with minimal required fields.
4. Find it later through the profile or LINK search.

Do not force the user to choose a note type or fill a structured form for ordinary context.

### 3.3 Remember an important date

1. Add a date from a person's profile or the Dates destination.
2. Choose a date type and month/day.
3. Add a year only when known.
4. Choose annual or one-time behavior.
5. Optionally configure a reminder through the shared reminder feature.

### 3.4 Capture a follow-up

1. Enter a short title.
2. Optionally associate a person, due date/time and context.
3. Optionally configure a reminder.
4. Complete or cancel it when appropriate.
5. Optionally convert it to a PLAN task if it needs fuller scheduling or execution management.

### 3.5 Import device contacts

1. User explicitly chooses Import contacts.
2. Explain the Android Contacts permission and the fields PokéLife will read.
3. Request permission only at this point.
4. Let the user select individual contacts or a clearly explained import scope.
5. Preview proposed additions and matches before writing LINK records.
6. Let the user confirm, skip or resolve uncertain duplicates.
7. Preserve imported-field provenance and any user-curated LINK-only information.

Import is never a prerequisite for using LINK.

---

## 4. Data ownership and relationships

LINK owns the following canonical records:

| Record | LINK responsibility |
|---|---|
| Contact | Identity and user-selected contact details |
| Relationship category/tag | User-defined organization |
| Relationship note | Private user-authored context |
| Important date | Date, recurrence and association |
| Follow-up | Lightweight commitment until explicitly converted |
| Contact source mapping | Import provenance and reconciliation |
| Postcard Trail state | LINK's optional acquisition progress and idempotency references |

**As built (0.144.0 Plan's half, 0.145.0 LINK's half, REP-014).** `GameStore.convertFollowUp(followUpId, title, planned, due)` makes the PLAN task for a converted follow-up (one task per follow-up id; `TaskLink(LINK_FOLLOW_UP, id)`), after which PLAN owns its completion and pays it once by Plan's rules (paid once, so a reopen never pays again); `GameStore.linkedTask(LINK_FOLLOW_UP, id)` gives LINK the task and its state to display; `unlinkTask` leaves a normal PLAN task. LINK must not pay for a follow-up that has a linked task: `FollowUp.converted` is set when the follow-up is handed over (`LinkStore.convertToPlan`) and never cleared, so unlinking or deleting the task does not make LINK pay; while the task exists LINK refuses to finish or cancel the follow-up (`LinkStore.IN_PLAN`) and a task finished in PLAN is mirrored as completed without a reward (`syncFromPlan`). `TaskLinksTest` covers it.

### 4.1 Cross-module boundaries

| Capability | Owner | LINK relationship |
|---|---|---|
| Full tasks, projects, calendar and task execution | PLAN | Explicit conversion/link only |
| Shared reminder scheduling and delivery | `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` | LINK supplies source records and preferences |
| Global Pokémon collection, Pokédex, Bag, Poké and reward ledger | Shared Pokémon/economy services | LINK emits eligible abstract progression events |
| Journal, mood and self-care records | CARE | No access |
| Health/activity/medical records | HEAL | No access |
| Household and service records | HOME | May reference a LINK contact only through an explicit link |
| Messaging/calls/social feeds | External apps | No ingestion or monitoring |

LINK must not copy a PLAN task into a second independently editable task. A linked representation is a reference with clear navigation and status provenance.

### 4.2 Reward event boundary

The shared reward service receives only the minimum event data needed to enforce LINK's acquisition policy, such as an opaque follow-up ID, policy version, module identifier and completion timestamp. It must not receive:
- Contact names or identifiers.
- Phone numbers, email addresses or addresses.
- Relationship categories or tags.
- Note text or follow-up titles/context.
- The identity of the person associated with a follow-up.

The event is evaluated inside the LINK/reward boundary. Other modules and WORLD receive only a generic reward result.

---

## 5. Reminders and notifications

Shared scheduling, quiet hours, notification permissions, delivery, recovery and action routing are defined only in `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.

LINK owns:
- Which important dates and follow-ups may request reminders.
- Source-record references and LINK-specific reminder preferences.
- What opening a reminder should navigate to.
- Whether a reminder is enabled, snoozed or dismissed at the LINK source level.

Suggested date presets may include the day of, one day before, three days before and one week before. These are user choices, not mandatory defaults. Follow-up reminders are opt-in. Avoid duplicate reminders when a follow-up has been converted to a PLAN task; PLAN becomes the reminder source after conversion.

Notification privacy:
- Generic lock-screen text by default, such as “You have an upcoming reminder.”
- Do not expose contact names, date types, notes or follow-up context unless the user explicitly enables more detailed previews.
- Dismissing a reminder never changes a relationship record or causes a reward penalty.

---

## 6. Integrations and permissions

### 6.1 Android Contacts

Device-contact integration is optional and Android-specific.

- Manual LINK operation requires no contacts permission.
- Request permission only after an explicit import action.
- Import only user-selected contacts/fields and explain the scope before confirmation.
- No continuous background synchronization in the initial release.
- No automatic upload, enrichment, reverse lookup or contact scraping.
- Re-import occurs only when the user requests it.
- Preserve LINK-authored fields when imported data changes.
- Show conflicts and let the user choose which value to retain.
- Revoking permission stops future imports but does not delete already imported LINK records.
- Store a source mapping sufficient for reconciliation without treating the device provider as LINK's permanent source of truth.

Do not add permissions for call logs, SMS, notification access, accessibility scraping, location or social-media accounts.

### 6.2 Other integrations

No messaging, email, social-media, CRM, cloud address-book or external contact-enrichment integration is required. Any future integration needs a separate privacy and feasibility review and explicit user authorization.

---

## 7. Privacy, security and data lifecycle

Relationship context can be sensitive even when it is not formally classified as health or financial data.

- Store LINK records locally in the approved persistence architecture.
- No mandatory account, cloud profile, PokéLife cloud sync, advertising, profiling or telemetry containing LINK data.
- Do not transmit LINK content to third parties.
- Protect local data and exports using the shared security, encryption and backup architecture.
- Never place raw contact details or note text in logs, crash reports or analytics.
- Do not expose relationship notes, contact details or follow-up context to WORLD, launcher surfaces or unrelated modules.
- Do not use contact data to infer sensitive traits or relationship quality.
- Provide export of all LINK records and source/provenance metadata in a documented format.
- Support deletion of individual records, selected imports and all LINK data.
- Deleting LINK data must not delete PLAN tasks, unrelated module records or shared Pokémon.
- Deleting a contact must provide a clear choice for linked dates/follow-ups and must not silently destroy independent records.
- Backups and restores must preserve stable IDs and cross-module links.

---

## 8. Offline behavior

Core LINK workflows work entirely offline.

| Capability | Offline behavior |
|---|---|
| People directory and profiles | Fully available from local records |
| Notes and search | Fully available |
| Important dates | Fully available |
| Follow-ups | Fully available |
| Local reminder scheduling | Uses shared local reminder infrastructure |
| PLAN links | Available from local module data |
| Device-contact import | Available only when Android Contacts provider and permission are available |
| Pokémon reward resolution | Uses local shared services; safely queues or retries only if the architecture requires it |

No online lookup is needed to add, edit, search or export LINK records. Import failure must not affect existing LINK data.

---

## 9. Optional Pokémon acquisition: Postcard Trail

LINK's optional acquisition identity is **Postcard Trail**. It evokes correspondence and places remembered without turning real people into collectible objectives.

### 9.1 User control

- Postcard Trail is a LINK-specific optional progression feature.
- It is enabled as a visible, user-controllable feature; the user can disable it at any time.
- Disabling it stops new LINK progression events without affecting contacts, notes, dates, follow-ups or reminders.
- Previously recruited Pokémon and earned rewards remain permanently owned.
- The setting can be changed without losing LINK data or other module progression.

### 9.2 Eligible activity

Only a user-completed, eligible LINK follow-up can contribute progress.

- Each distinct follow-up completion can contribute at most once.
- Reopening, editing, rescheduling, duplicating or re-importing a record cannot generate another event.
- Cancelling a follow-up does not contribute progress.
- Follow-ups converted to PLAN tasks use one canonical completion event; the LINK and PLAN representations cannot both pay.
- The event must not include the person's identity or any follow-up text.

Explicitly ineligible:
- Adding or importing contacts.
- Editing contact details, categories, tags or favorites.
- Writing or viewing notes.
- Adding or editing important dates.
- Opening a profile or searching contacts.
- Contact frequency, call/message history, response time or social-media activity.
- Any inferred or automatically observed interaction.

### 9.3 Discovery loop

- Eligible completions fill a Postcard Trail progress track.
- A completed track grants a postcard-themed discovery/encounter opportunity.
- The encounter recruits through the shared Pokémon acquisition and collection services.
- Initial balancing target: three distinct eligible follow-up completions per encounter opportunity, subject to shared economy simulation and cross-module reward calibration.
- Encounter rarity and reward generosity are balanced centrally; LINK must not hard-code an independent rarity economy.
- Progress is persistent, non-expiring and never decays.
- No daily quota, streak, penalty, loss for inactivity, timed event or limited-time species.
- Duplicate species remain valid individual collection entries under shared duplicate rules.

### 9.4 Ethical boundary

The feature rewards the user's act of completing a self-chosen reminder, not the person, relationship, emotional closeness or outcome. It must not encourage unnecessary contact, manufacture follow-ups, reward repeated low-value tasks or pressure the user to maintain a social routine. Keep the practical LINK workflow fully useful with Postcard Trail disabled.

---

## 10. Visual presentation

LINK follows the shared PMD dialogue-box visual system in `../FEATURES/DESIGN_SYSTEM.md` and uses the shared utility presentation intensity.

- Use shared dialogue-box/panel components for concise profile context, important date summaries, follow-up actions and Postcard Trail discovery feedback.
- Use compact native-feeling contact rows and efficient lists for the People directory.
- Do not wrap every contact, note, date or follow-up in a separate card.
- Keep profile information scannable; reveal secondary fields on demand.
- Use typography, spacing and subtle separators to establish hierarchy rather than extra decoration.
- Pokémon art is limited to restrained, contextual Postcard Trail moments; do not decorate every contact or imply that contacts are Pokémon.
- Do not use relationship meters, hearts, social scores, streak rings, leaderboards or urgency colors.
- Support light/dark themes, dynamic text scaling, TalkBack, minimum 48 dp touch targets, reduced motion and responsive widths.

The design-system document owns reusable component rules, typography, spacing, borders, states and accessibility. LINK may define content-specific layouts but cannot fork shared component anatomy or global tokens.

---

## 11. Existing implementation audit and reuse policy

The reviewed source archive `0.123.0` has no first-class LINK module, contact/relationship domain models, device-contact import adapter or LINK permission flow. The archive includes legacy reminder infrastructure, but LINK must use the shared reminder contract rather than creating another scheduler. Existing code is evidence, not authority; the target architecture and this specification govern implementation.

### 11.1 Reuse

- Shared module shell, navigation, local persistence, encryption, backup/export and migration foundations after architecture review.
- Shared reminder scheduler and delivery through LINK source adapters.
- Shared Pokémon collection, acquisition and reward services.
- Shared design-system components after audit against `FEATURES/DESIGN_SYSTEM.md`.
- Android Contacts provider only through a permission-aware platform adapter.

### 11.2 Build

Create a LINK domain with independent models, repositories, use cases, local persistence, search indexing, import reconciliation, Postcard Trail state and tests. Keep Android Contacts APIs in the Android platform layer; common LINK domain logic must not depend on Android framework classes or Compose.

### 11.3 Avoid or retire

- Do not add LINK records to PLAN or CARE persistence.
- Do not use one unstructured global JSON object as the long-term data model.
- Do not build a second reminder scheduler.
- Do not request broad contacts access at app startup.
- Do not add call/SMS monitoring, social scraping or background contact synchronization.
- Do not use Supabase or other cloud pathways.
- Do not create a separate Pokémon collection, currency, Bag or reward ledger.
- Do not reward contact creation, note-taking, imports or passive interaction data.

---

## 12. Implementation phases and exit gates

LINK follows Phase 8 in the dependency-first Implementation Guide. HOME and LINK can share architecture work but remain separate domains and acceptance gates.

### Phase 1 — LINK domain and persistence

- Define Contact, RelationshipNote, ImportantDate, FollowUp and ContactSource models.
- Define stable IDs, timestamps, provenance, deletion and migration behavior.
- Establish repositories and local persistence in the approved architecture.
- Define cross-module reference contracts and the minimal reward event schema.

**Exit gate:** every record has one owner; records survive process death and migrations; no dependency on PLAN/CARE storage.

### Phase 2 — Manual contacts and profiles

- People directory, quick add, profile overview and editing.
- Optional categories, tags, favorites and duplicate suggestions.
- Empty states and safe deletion.

**Exit gate:** manual LINK use is complete without permissions or internet.

### Phase 3 — Notes and search

- Relationship notes and dated context.
- Unified local search and filters.
- Export and deletion behavior.

**Exit gate:** notes remain private and searchable; search index can be rebuilt without data loss.

### Phase 4 — Important dates

- Date types, annual/one-time recurrence and unknown-year support.
- Leap-day and time-zone behavior.
- LINK reminder source integration.

**Exit gate:** date recurrence is deterministic; no duplicate calendar ownership or reminders.

### Phase 5 — Follow-ups and reminders

- Quick capture, due dates, statuses and completion history.
- Shared reminder integration and privacy-safe notification content.
- PLAN conversion/link contract.

**Exit gate:** reminders recover after reboot/process death; conversion creates no duplicate task, completion or reward.

### Phase 6 — Optional device-contact import

- Explicit permission flow and import preview.
- User-selected import, duplicate reconciliation and field provenance.
- Re-import and permission-revocation behavior.

**Exit gate:** no silent merge or overwrite; manual LINK remains fully usable when permission is denied.

### Phase 7 — Postcard Trail

- User setting and opt-out behavior.
- Eligible completion event policy and idempotency.
- Encounter/recruitment integration with shared Pokémon services.
- Economy simulation and anti-farming tests.

**Exit gate:** only eligible user-completed follow-ups contribute; no personal contact data leaves LINK; disabled progression does not affect utility.

### Phase 8 — Polish and acceptance

- Visual refinement and shared component audit.
- Accessibility, responsive layout and dark mode.
- Large-directory/search performance.
- Backup, restore, migration, deletion and real-device permission testing.

**Exit gate:** all core workflows pass product, privacy, offline, reliability and accessibility acceptance.

---

## 13. Acceptance criteria

### Product utility
- A person can be created with only a name.
- Notes, dates and follow-ups can be captured with minimal required fields.
- Search finds relevant people and LINK-owned content.
- Overdue follow-ups are neutral and do not create shame or penalties.
- LINK remains useful with Postcard Trail disabled.

### Ownership and reliability
- LINK is the canonical owner of contacts, relationship notes, important dates and unconverted follow-ups.
- PLAN conversion uses stable references and does not duplicate tasks or completion events.
- Shared reminders are used; no second scheduler exists.
- Recurrence handles leap days and time-zone changes predictably.
- Records survive process death, reboot, app update, backup and restore.
- Deletion and import conflicts are explicit and recoverable.

### Privacy and integrations
- Manual use requires no Android Contacts permission.
- Permission is requested only for an explicit import.
- No call/SMS/social monitoring, scraping, automatic messaging or cloud transmission exists.
- Notes and contact details never reach WORLD, launcher or progression events.
- Imported fields retain provenance and cannot silently overwrite user-curated fields.
- Export and deletion cover LINK data and respect cross-module ownership.

### Pokémon progression
- Postcard Trail is optional and can be disabled without disabling LINK.
- Only distinct eligible follow-up completions contribute.
- Contact creation, imports, notes, dates and passive interaction never contribute.
- Reopening, editing, migration, duplicate imports or PLAN linkage cannot double-pay.
- No streak loss, decay, expiring reward, daily quota or relationship score exists.
- Recruitment uses the shared collection and reward ledger.

### Accessibility and presentation
- Shared PMD dialogue-box language is consistent without excessive framing.
- TalkBack, dynamic text, contrast, touch targets, reduced motion and dark mode pass review.
- Contact lists and profiles remain responsive with large local datasets.

---

## 14. Documentation alignment

| Document | Authoritative responsibility |
|---|---|
| `../PRODUCT_BIBLE.md` | Product-wide module map, ownership, privacy and shared Pokémon rules |
| `../IMPLEMENTATION_GUIDE.md` | Baseline implementation status, dependency order and delivery roadmap |
| `../MODULES/PLAN.md` | Tasks, calendar and execution after explicit follow-up conversion |
| `../MODULES/CARE.md` | Private reflection and self-care; no LINK record ownership |
| `../MODULES/HOME.md` | Household records; may reference a LINK-owned service contact |
| `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` | Shared reminder scheduling, policy, delivery and recovery |
| `../FEATURES/DESIGN_SYSTEM.md` | Shared PMD dialogue-box visual language and reusable components |
| Shared Pokémon/economy specification | Collection, reward ledger, acquisition contracts and economy balance |
| Future `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` | Permission and sensitive-data architecture |
| `../ARCHITECTURE/DATA_AND_MIGRATIONS.md` | Shared persistence, transaction, encryption boundary, export, backup, restore and migration contracts |

This document is the single detailed requirements source for LINK. Shared scheduling, visual rules, Pokémon ownership and technical foundations remain in their respective canonical documents; do not duplicate their implementation rules here.

## 15. As built (0.145.0, REP-014): the manual slice

*Built and tested on the JVM, compiled for Android; **not run on a device**. No reminder, device-contact import or widget was started.*

**What exists.** A menu with an overview on top and four destinations. **Overview**: only what needs attention (follow-ups that are due, with a tick; dates in the next 14 days), quick-add, and one search over people, notes, dates and follow-ups. **People** (2.1, 2.2): a directory sorted by name or by what changed last (never ranked), filters for Pinned and relationship words, a person needs only a name, a profile with Overview, Notes (dated lines, newest first) and Follow-ups, an edit form, duplicate suggestions by name, phone digits or email (suggested, never merged), and a delete that asks what to do with their dates and follow-ups. **Dates** (2.3): birthday, anniversary, milestone or custom, day and month with an optional year, every year or once, an optional person; the next time it comes with "turns N" when the year is known. **29 February is observed on 28 February in a year without one** (`LinkRules.observed`). **Follow-ups** (2.4): title only, optional person, day and note; Open and History; done, cancelled, reopened. A late one is just "Due since…", no escalation. **Make it a Plan task** (2.4, on the follow-up's options, through `GameStore.convertFollowUp`): from then on PLAN owns the task and its reward, Link shows "In Plan" with no tick, Link never pays for that follow-up (`GameStore.link` refuses it), and "Stop linking to Plan" lets it go. **Settings**: Postcard Trail on or off, categories, export and erase by kind.

**Postcard Trail (section 9), as built** *(numbers are *(tune)* starting values; `RewardRules.VERSION` 20, `docs/economy.md`)*:
- **On by default** (every acquisition method is), off in Settings without touching a record. Only one thing pays: a **follow-up finished** (2 Rare Candies; at most 3 paid a day). Adding or importing people, notes, dates, tags and favourites, editing, rescheduling, cancelling and reopening never pay.
- **Idempotent by follow-up.** The event id is `link:followup:<follow-up id>`, the game remembers it (`AppData.linkPaid`, newest 1000) and the follow-up keeps `paid`, so reopening and finishing again, a restore or a replay pays once. Nothing is clawed back by a reopen.
- **What leaves LINK** is exactly `LinkEvent`: id, module ("link"), an opaque `ref`, the broad kind (`followup_completed`), the time and the policy version. `LinkStoreTest` and `LinkTablesTest` check that no name, number, email, organization, tag, note, date or follow-up title reaches the event, the shared save, the reward log or the shared tables.
- A payment is candies, a little friendship for Link's lead, a trainer point and a point on the track; **3 points (three finished follow-ups) find a Postcard Egg** from the **Link Region** (`tools/species/regions/link.txt`) into Link's party. Link's party perks (Candy finder: Normal, Flying, Water, Grass, Bug, Ground; Friendship gain: Fairy, Fighting, Electric, Psychic, Fire, Dragon; capped at +25%) only change what Postcard Trail pays.
- **First-run tutorial** (POKEMON_ECOSYSTEM.md 10.2.1): a 5-second postcard gives one Link Region Pokémon into Link's party, leading it, once per save. A new Pokémon arrives as "Postcard Trail delivered…" in a scene of its own (the route).

**Data (section 4).** `link_*` tables from the start (schema 10): `link_person`, `link_note`, `link_date`, `link_followup` (each row `key, position, day, json`) and `link_state` (settings); `LinkTables` reads, exports or erases one family alone. Link is in the daily backup and the backup file; a backup made before Link leaves Link alone. Not sealed (no owner decision), like Home.

**Reminders (5), built in 0.143.0:** off until the user turns Link's reminders on, then per date (the day of, or 1, 3 or 7 days before) and per follow-up (on its day); one nudge each, generic lock-screen text, quiet hours and focus sessions respected; a follow-up handed to Plan is Plan's to remind about.

**Audit changes (0.170.0):** the directory and search also find a person by phone (however spaced), email and address; a person's profile can add a date for them (3.3); deleting a person offers Undo; the directory shows 40 people at a time; a person can get a new category from their own form (Settings no longer has a Categories box with chips that did nothing); a cancelled follow-up shows the day it was cancelled; a follow-up PLAN finished is reopened in PLAN (Link follows its task both ways, unpaid); and "due" counts on Home and Modules leave out follow-ups Plan owns, as the Overview already did.

**Not built, not offered:** importing device contacts and the Contacts permission (6.1), a link from HOME to a contact, contact source mapping and provenance, widgets, and a passphrase-sealed backup.

## Shared Pokémon ecosystem contract

LINK's acquisition identity is **Postcard Trail**, based on distinct eligible follow-up completions. LINK owns follow-up eligibility; the shared Pokémon ecosystem owns canonical reward settlement, Egg/owned-Pokémon records, Pokédex and inventory. LINK has an independent party referencing shared owned Pokémon IDs. If a follow-up is converted into a PLAN task, one canonical completion event and one reward owner must be preserved. Contact identifiers, personal notes, message content and social activity are excluded from reward events. See `../FEATURES/POKEMON_ECOSYSTEM.md`.

---

## Testing reference

LINK record ownership, follow-up workflows, reminder routing, interruption recovery, duplicate-action prevention and privacy boundaries must be tested under [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md).

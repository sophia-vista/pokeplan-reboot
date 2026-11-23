# PokéLife LINK Module Specification

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

LINK has three primary destinations (owner, 23 November 2026; `../FEATURES/DESIGN_SYSTEM.md` 6.7): **People**, **Dates** and **Moments**, with **Follow-ups** and **Settings** as secondary places after them in the same Sections key. Keep navigation shallow; notes and follow-up history should also be visible in the relevant person's profile. Moments is planned and not built, so it is not listed until it exists (2.7).

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

The profile also has a **Profile** tab after Overview (about, mind and stars: section 2.6).

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

### 2.6 Profile, slambook and birth details

A person's profile is also a **slambook page**: the details the user chooses to write down, plus readings that are worked out from a birth date the user gave. The user has one card of their own, **Me**, pinned first in the directory, with the same page.

- **Me.** One person marked `isMe`, created from People ("Set up my profile"); asking again never makes a second. It is an ordinary person (it can be deleted, exported, erased) and nothing about it is special to rewards.
- **About.** Socials (a platform and a handle or link, as typed), likes, dislikes, and slambook answers: a tap-to-answer list of classic questions (nickname, favourite colour, food, song, Pokémon, hobby, dream job, motto, pet peeve, best memory with me, ...) plus the user's own questions. All optional, all searchable.
- **Mind.** The result of a test the user took elsewhere, typed in, never inferred: MBTI type, Enneagram type and wing, love languages (first and second), attachment style, DISC, Big Five (lower, middle or higher per trait). Each label has a plain-language reading, shown when it is tapped. LINK offers no test and scores nothing.
- **Stars.** Worked out on the phone from the person's Birthday date (one record, shared with Dates and its reminder) and an optional birth time, UTC offset and birthplace. Nothing is stored except what the user typed.
  - **Western natal chart:** Sun, Moon, Mercury to Pluto and the mean North Node, retrograde marks, aspects (conjunction, sextile, square, trine, opposition within orb), and with a birth time and place the rising sign, midheaven and whole-sign houses. Accurate to about a third of a degree (JPL mean elements for 1800 to 2050 and a low-precision Moon), so a body on a sign line may really be in the next sign; the page says so. With no birth time, a body that changes sign that day is shown as "A or B". With a month and day but no year, only the newspaper sun sign is shown.
  - **Chinese:** zodiac animal and element (the year turns at Chinese New Year), the **Four Pillars (BaZi)** with the Day Master and a five-element count (the year turns at Lichun, the month at each *jie* solar term, the day at midnight; the late Rat hour, 23:00 to 24:00, takes the next day's stem and keeps the birth day's pillar; the clock time as written, no true-solar-time correction, compared with the solar terms on the Beijing clock), and the **Kua (Ming Gua) number** with its group and four lucky directions (needs the user to choose the male or female formula). Supported for 1902 to 2098; outside that the Chinese readings are not shown rather than guessed. The tables are generated by `tools/link/generate_bazi.py`.
  - **Numerology:** the Life Path number, keeping the master numbers 11, 22 and 33.
- **Readings are for fun.** Every reading is fixed text in a traditional spirit with a visible "for fun and conversation, not advice or a verdict" line. LINK never scores, ranks or compares people by them, never judges a relationship with them (section 1.2), and never uses them in a notification, a search ranking or a reward. A profile is private LINK data under section 7: it is in the People export and erase, and no figure, handle or word of it reaches `LinkEvent` or any other module.

### 2.7 Moments (planned)

One timeline of the events worth remembering with the people in them: a meeting, a trip, a meal, a milestone. A moment carries a date (and optionally a time and a place), the people it was with, a free note and an optional picture the user chooses; it appears on the relevant person's profile and in the timeline, and it is private LINK data under section 7. It never scores, never reminds and never pays on its own. No record, store or screen is built yet, so LINK lists People and Dates only until it exists.

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

**Conversion rules (built).** `GameStore.convertFollowUp` makes one PLAN task per follow-up id (`TaskLink(LINK_FOLLOW_UP, id)`); PLAN then owns its completion and pays it once by Plan's rules, and `GameStore.linkedTask` gives LINK the task and its state to display. `unlinkTask` leaves a normal PLAN task. `FollowUp.converted` is set on handover and never cleared, so unlinking or deleting the task never makes LINK pay; while the task exists LINK refuses to finish or cancel the follow-up, and a task finished in PLAN is mirrored as completed without a reward.

### 4.1 Cross-module boundaries

| Capability | Owner | LINK relationship |
|---|---|---|
| Full tasks, projects, calendar and task execution | PLAN | Explicit conversion/link only |
| Shared reminder scheduling and delivery | `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` | LINK supplies source records and preferences |
| Global Pokémon collection, Pokédex, Bag, Poké and reward ledger | Shared Pokémon/economy services | LINK emits eligible abstract progression events |
| Journal, mood and self-care records | CARE | No access |
| Health/activity/medical records | HEAL | No access |
| Household and service records | BASE | May reference a LINK contact only through an explicit link |
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

**Choosing a reminder is the opt-in:** the first reminder chosen on a date, in the Add-a-date form, in an edit or in the contacts import, turns Link's master reminder switch on; nothing is on before that, and Settings can turn them all off again. Suggested date presets may include the day of, one day before, three days before and one week before. These are user choices, not mandatory defaults. Follow-up reminders are opt-in and fire on the follow-up's day; each reminder is one nudge, with quiet hours and focus sessions respected. Avoid duplicate reminders when a follow-up has been converted to a PLAN task; PLAN becomes the reminder source after conversion.

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
- No daily quota on progress, collection or attendance, no streak, penalty, loss for inactivity, timed event or limited-time species. The only caps are anti-farming ones on payments: at most 3 paid follow-ups a day and at most 5 Safari catches a day in total (9.5; `economy.md`).
- Duplicate species remain valid individual collection entries under shared duplicate rules.

### 9.4 Ethical boundary

The feature rewards the user's act of completing a self-chosen reminder, not the person, relationship, emotional closeness or outcome. It must not encourage unnecessary contact, manufacture follow-ups, reward repeated low-value tasks or pressure the user to maintain a social routine. Keep the practical LINK workflow fully useful with Postcard Trail disabled.

### 9.5 Friend Safari (built in 0.225.0; owner decisions, 20 and 21 November 2026)

A second optional acquisition for the slambook: each person in LINK (the Me card too) has a **Friend Safari**, a small encounter pool of Pokémon only that person's card opens. Decided:

- **What decides a person's Safari:** the person's opaque id only, as a stable seed (like the real Friend Safari's friend code): LINK hands the shared world a hash of the record key (`LinkRules.safariRef`), never the key, a name, a birthday, a personality or a chart, so nothing private is read and nothing private reaches the reward boundary. The event is `SafariEvent` (id, opaque `ref`, the capped people count, time, policy), a type of its own: it never goes through `LinkEvent`, so a visit cannot also pay Postcard Trail's candies, Poké or track points.
- **The more people, the more spread:** the pool has a fixed order of slots decided by the seed alone (slot rarities Common, Uncommon, Uncommon, Rare, Rare, all of the Link Region); LINK's people count unlocks more slots (2 at the start, one more for every 6 people, at most 5), so adding people only appends slots and never reshuffles the ones a person already has, and with more slots each visit meets any one species less often (more species, each rarer). The count that spreads the pools excludes the Me card and is capped (20 *(tune)*), so made-up contacts buy nothing past it.
- **Entry is free (owner: "free entry. trust").** No ticket and no ball. **One catch a day for each person (owner, 21 November 2026)**: a visit meets one slot of that person's pool (species not caught yet six times as likely, like an egg) and catches it directly, no egg and no minigame, into Link's party (else the PC), at the ordinary shiny chance. Because that alone would make a pile of added people worth a catch each a day, **at most 5 Safari catches a day in all** *(tune; added by the build, owner to confirm)*. Adding or importing people therefore still pays nothing by itself; only visiting a Safari can.
- **What a visit pays:** the Pokémon (and the usual new-species candies, if it is a new species) and nothing else: no Poké, no trainer point, no track point, no item.
- Safari recruits through the shared Pokémon acquisition services, balanced centrally; LINK hard-codes no rarity economy of its own. Postcard Trail stays as it is. Off with Postcard Trail's switch or its own (Settings → "Friend Safari on each person"), and LINK stays fully useful without it. Same ethical boundary as 9.4: the reward is for opening a Safari, never for a person, a relationship or contact.
- **Presentation (as built):** a Friend Safari box at the end of a person's screen shows the slots (a species not met yet is a Friend Ball and a question mark) and a Visit key; once visited it says "Come back tomorrow." A new Pokémon arrives in a scene of its own ("A Friend Safari had a ..."). A Safari backdrop would use mainline assets, not PMD places (`ASSETS_AND_AUDIO.md` 4.0); none is drawn yet.

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

## 11. Reuse policy

Existing code is evidence, not authority. LINK reuses the shared module shell, local persistence, encryption, backup/export and migration foundations, the shared reminder scheduler (through LINK source adapters), the shared Pokémon collection, acquisition and reward services, the shared design-system components, and the Android Contacts provider only through a permission-aware platform adapter. LINK has its own domain (models, repositories, persistence, search, import reconciliation, Postcard Trail state); Android Contacts APIs stay in the Android platform layer and common LINK logic depends on no Android or Compose class.

### 11.1 Avoid

- Do not add LINK records to PLAN or CARE persistence.
- Do not use one unstructured global JSON object as the long-term data model.
- Do not build a second reminder scheduler.
- Do not request broad contacts access at app startup.
- Do not add call/SMS monitoring, social scraping or background contact synchronization.
- Do not add cloud pathways.
- Do not create a separate Pokémon collection, currency, Bag or reward ledger.
- Do not reward contact creation, note-taking, imports or passive interaction data.

---

## 12. Remaining work

Not built: a chooser for fields that changed on re-import (today only empty fields are filled and nothing is overwritten), a link from BASE to a contact, upcoming dates on the launcher, widgets, sealing Link's rows (a passphrase-sealed backup already ships and carries Link in clear), and the open question of "keep in touch" (D8). **Exit gate:** no silent merge or overwrite; manual LINK remains fully usable when permission is denied. Real-device testing of the whole module is also still to do (`../IMPLEMENTATION_BACKLOG.md`).

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
- No streak loss, decay, expiring reward, relationship score or quota on progress exists; the anti-farming caps on payments in 9.5 are the only limits.
- Recruitment uses the shared collection and reward ledger.

### Accessibility and presentation
- Shared PMD dialogue-box language is consistent without excessive framing.
- TalkBack, dynamic text, contrast, touch targets, reduced motion and dark mode pass review.
- Contact lists and profiles remain responsive with large local datasets.

---

## 14. Documentation alignment

This document is the single detailed requirements source for LINK. Product-wide rules: `../PRODUCT_BIBLE.md`; delivery order: `../IMPLEMENTATION_GUIDE.md`; PLAN conversion target: `PLAN.md`; BASE may reference a LINK-owned service contact: `BASE.md`; shared reminders and permissions: `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`; shared persistence, encryption boundary, export, backup and migration: `../ARCHITECTURE/DATA_AND_MIGRATIONS.md`; visual language: `../FEATURES/DESIGN_SYSTEM.md`; Pokémon and economy: `../FEATURES/POKEMON_ECOSYSTEM.md`.

## 15. As built: the manual slice

*Compiled for Android; not yet run on a device.*

A page with an overview on top and, behind the header's Sections key, four places (People, Dates, Follow-ups, Settings). **Overview**: only what needs attention (follow-ups that are due, with a tick; dates in the next 14 days), quick-add, and one search over people, notes, dates and follow-ups. **People** (2.1, 2.2): a directory sorted by name or by what changed last (never ranked), filters for Pinned and relationship words, a person needs only a name, a profile with Overview, Profile (2.6), Notes (dated lines, newest first) and Follow-ups, duplicate suggestions by name, phone digits or email (suggested, never merged), and a delete (with Undo) that asks what to do with their dates and follow-ups. The directory and search also find a person by phone (however spaced), email and address, and show 40 people at a time. **Dates** (2.3): birthday, anniversary, milestone or custom, day and month with an optional year, every year or once, an optional person; the next time comes with "turns N" when the year is known. **29 February is observed on 28 February in a year without one** (`LinkRules.observed`). **Follow-ups** (2.4): title only, optional person, day and note; Open and History; done, cancelled (shows its day), reopened. A late one is just "Due since…", no escalation. **Make it a Plan task** (on the follow-up's options): PLAN owns the task and its reward, Link shows "In Plan" with no tick, and "Stop linking to Plan" lets it go; a follow-up PLAN finished is reopened in PLAN (Link follows its task both ways, unpaid); "due" counts on Home and Modules leave out follow-ups Plan owns. **Settings**: Postcard Trail on or off, export and erase by kind; a person gets a new category from their own form.

**Postcard Trail (section 9)** *(numbers are *(tune)* starting values; `docs/economy.md`)*:
- **On by default** (every acquisition method is), off in Settings without touching a record. Only one thing pays: a **follow-up finished** (2 Rare Candies; at most 3 paid a day). Adding or importing people, notes, dates, tags and favourites, editing, rescheduling, cancelling and reopening never pay.
- **Idempotent by follow-up.** The event id is `link:followup:<follow-up id>`, the game remembers it (`AppData.linkPaid`, newest 1000) and the follow-up keeps `paid`, so reopening and finishing again, a restore or a replay pays once. Nothing is clawed back by a reopen.
- **What leaves LINK** is exactly `LinkEvent`: id, module ("link"), an opaque `ref`, the broad kind (`followup_completed`), the time and the policy version. No name, number, email, organization, tag, note, date or follow-up title may reach the event, the shared save, the reward log or the shared tables.
- A payment is candies, a little friendship for Link's lead, a trainer point and a point on the track; **3 points (three finished follow-ups) find a Postcard Egg** from the **Link Region** (`tools/species/regions/link.txt`) into Link's party. Link's party perks (Candy finder: Normal, Flying, Water, Grass, Bug, Ground; Friendship gain: Fairy, Fighting, Electric, Psychic, Fire, Dragon; capped at +25%) only change what Postcard Trail pays.
- **First Pokémon** (POKEMON_ECOSYSTEM.md 10.2.1): the first person, date or follow-up gives one Common egg of the Link Region that hatches into Link's party, leading it, once per save. A new Pokémon arrives as "Postcard Trail delivered…" in a scene of its own (the route).

**Data (section 4).** `link_*` tables: `link_person`, `link_note`, `link_date`, `link_followup` (each row `key, position, day, json`) and `link_state` (settings); `LinkTables` reads, exports or erases one family alone. Link is in the daily backup and the backup file; a backup made before Link leaves Link alone. Not sealed (no owner decision), like Base.

**Contacts import:** Android only (`ContactSource`, null elsewhere so the button is not offered; `AndroidContacts` reads names, phones, emails, companies and birthdays in one query). People → "Import contacts" says what is read, then asks `READ_CONTACTS`; denied, manual use is untouched. A pick list (search, 40 at a time, nothing preselected) marks rows New, Already in Link (same `Person.sourceId`) or Maybe already in Link (name, a phone or an email; starts unselected, and when picked fills only the matched person's empty fields). A reminder is chosen (No, On the day, 1, 3 or a week before; no default), then "Import N" makes one save, one result line and Undo. A birthday becomes a Birthday date unless the person has one; a second import changes nothing; empty phone, email and organization are filled, nothing is overwritten. Contacts with no name are counted and shown. Nothing is paid and nothing reaches `LinkEvent`. **Reminder in one step:** the Add-a-date form has "Remind me" keys, and the first reminder turns Link's reminders on. Known gap: the notification permission is asked only when a reminder is within 4 days.

**Profile, slambook and birth details (section 2.6):** People → "Set up my profile" makes the Me card; every profile has a Profile tab (About, Mind, Stars) after Overview, so Notes is tab 3 and Follow-ups tab 4. `Person.isMe` and `Person.profile` live in the person's JSON (no schema step); `link-core`: `Profile`, `ProfileKit`, `Insights`, `Sky` (natal chart), `BaZi` and `BaZiTables` (Four Pillars, zodiac, Kua, Life Path), `Birthplaces`, `LoreWestern`, `LoreChinese`, `LorePersonality`; `LinkStore.createMe`, `updateProfile`, `setBirthday`. Verified against `ephem` (planets, Moon, ascendant) and `lunar_python` (pillars, New Year) in `BirthChartTest`. The birth date is the person's Birthday date. Nothing is paid and nothing reaches `LinkEvent`.

**A screen for each person, a profile card and Friend Safari (0.225.0), as built:** a person opens on a screen of their own inside People (header "Profile", or "My profile" for the Me card, back to the directory), no longer an accordion under their row; a search hit, a new person and "Set up my profile" open it too. It starts with a **profile card**: the Pokémon the user chose as their picture in the app's ordinary portrait frame (a Friend Ball when none; "Choose" or "Change" opens a picker of the Pokémon the player has caught, or a search of every species), the whole name wrapped, role and organization, and tags for Me, the relationship word and Pinned. Then the dialogue box of tabs (Overview, Profile in two rows, Notes, Follow-ups), then the **Friend Safari** box (9.5). `Person.avatar` (a species id, 0 for none) lives in the person's JSON, so there is no schema step; the picture is cosmetic and feeds nothing. `LinkSettings.safari` (on by default) is the Safari's own switch. Friend Safari: `SafariEvent`, `SafariRules` and `RewardEngine.safariVisit` in pokemon-core, `GameStore.safari` and `LinkStore.visitSafari`; `RewardRules.VERSION` 33.

`Person.sourceId` (the contact's lookup key, default empty) lives in the person's JSON, so there is no schema step.

## Shared Pokémon ecosystem contract

LINK's acquisition identity is **Postcard Trail**, based on distinct eligible follow-up completions. LINK owns follow-up eligibility; the shared Pokémon ecosystem owns canonical reward settlement, Egg/owned-Pokémon records, Pokédex and inventory. LINK has an independent party referencing shared owned Pokémon IDs. If a follow-up is converted into a PLAN task, one canonical completion event and one reward owner must be preserved. Contact identifiers, personal notes, message content and social activity are excluded from reward events. See `../FEATURES/POKEMON_ECOSYSTEM.md`.

---

## Testing reference

LINK record ownership, follow-up workflows, reminder routing, interruption recovery, duplicate-action prevention and privacy boundaries must be tested under [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md).

**People list as cards and a random picture picker (owner, 23 November 2026).** The directory shows each person as a small card of their own (their Pokémon picture, the name, the relationship word and organization, a star when pinned) instead of a plain row; a person with no picture shows the Friend Ball. The picture picker opens on a **random draw of Pokémon from the whole Dex** (36 at a time) with a Shuffle key, and "My Pokémon" switches to the ones the player has caught; searching by name still lists matches. Cosmetic only.

**Friend Safari asks before it visits (owner, 23 November 2026).** A visit uses one of the day's five catches, so the Visit key now asks "Use one of today's N visits? It catches one Pokémon." with Visit now and Not now; nothing visits on its own when PokéLife opens (no code path does: `LinkStore.visitSafari` has the one caller, the key).

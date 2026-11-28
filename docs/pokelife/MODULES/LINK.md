# PokéLife LINK Module Specification

**Status:** Specified / approved (archive direction, owner, 25 November 2026)  
**Module:** LINK  
**Product Bible:** `../PRODUCT_BIBLE.md`, §§3–5  
**Shared visual authority:** `../FEATURES/DESIGN_SYSTEM.md`  
**Shared reminder authority:** `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`  
**Shared photo-source and permission authority:** `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md`  
**Shared Pokémon/economy authority:** canonical shared Pokémon and economy specification (`../FEATURES/POKEMON_ECOSYSTEM.md` and `docs/economy.md`)  
**Implementation roadmap:** `../IMPLEMENTATION_GUIDE.md`, Phase 8

> LINK is a private personal archive: the player, their people, their relationships and the history they share, kept in one calm place. It helps the player record and look back on a life without turning relationships or memories into a performance metric.

---

## 1. Purpose and product identity

LINK is the personal archive and social-life module of PokéLife. It documents the player, the people in their life, how those people are connected, and the history they share: contact details and context, memories and life eras with their photos, important dates and reminders, follow-ups, and the slambook pages the player chooses to keep.

**Archive direction (owner, 25 November 2026):** LINK is "a private personal archive documenting you, your people, your relationships and your shared history". It is a place the player visits to record and to look back, not a list to maintain.

**Core principle:** Remember what matters. Make it easier to look back than to keep up. Never gamify the people in the player's life.

LINK must be useful as a private archive even when all Pokémon progression is disabled. It must not create pressure to contact people, reward contact frequency, judge the quality of a relationship, or ask the player to document days. Recording is optional, no entry is ever late, and nothing decays when the player is away.

### 1.1 Goals

- Keep the important people and their details easy to find.
- Record how the player's people are connected to the player and to each other.
- Keep memories, life eras and photos as a private history that can be browsed and rediscovered.
- Remember birthdays, anniversaries and personal milestones, with reminders the player chooses.
- Capture light follow-ups and commitments without creating a second task manager.
- Combine the separate views of a life (people, photos, dates, history) so looking back takes few steps.
- Support quick manual entry, optional permission-controlled device-contact import, and an optional read-only OneDrive photo source.
- Offer optional, low-pressure Pokémon discovery through the Postcard Trail and Friend Safari; a per-person Pokémon direction is specified, not built (9.6).

### 1.2 Explicit non-goals

LINK is not:
- A messaging, calling, email or social-media client.
- A social network, CRM, sales pipeline or contact-enrichment service.
- A relationship-quality, intimacy, popularity or responsiveness tracker.
- A location tracker, call/SMS monitor, social-media scraper or activity surveillance tool.
- An automatic message sender or an AI that independently contacts people.
- A second calendar, task manager, notes app or reminder scheduler.
- A cloud drive or photo editor; LINK never modifies, moves or deletes a file in OneDrive.
- A place to store passwords, payment credentials or unnecessary sensitive information.
- A system that requires documentation streaks, daily logging, missed-day penalties or an account for the player's own LINK data.

No contact is ever contacted, notified or messaged by PokéLife. No photo is identified, named or tagged automatically: there is no face recognition, no inferred relationship and no background photo scanning.

**OneDrive exception (owner, 25 November 2026).** The app is local-only, with no mandatory account, sync or server, and LINK's own records and photos follow that. The optional Microsoft OneDrive photo source (6.2) is a deliberate, narrow exception: a Microsoft account can be connected only to read the photos the player chooses to add to a memory; they then appear on that person's page by association. It is optional and read-only, it never writes, moves or deletes anything in OneDrive, and no LINK record is uploaded to Microsoft. The exact permission, scope and token rules are owned by `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` (section 8.2A); this document states only the behaviour and the privacy rules.

---

## 2. Information architecture

LINK has three primary destinations (owner, 25 November 2026; `../FEATURES/DESIGN_SYSTEM.md` 6.7): **People**, **Memories** and **Reminders**. **Settings** and the less-used set-up places (contacts import, the optional OneDrive source, export and erase) sit after them in the same Sections key. Keep navigation shallow: a person page and the link a memory makes to its people and eras should reach the relevant records without a detour, and anything the player is unlikely to need sits one step further in.

The three places read one archive: a memory belongs to people and eras; a person's page gathers their reminders, memories and photos; search spans all of it (2.5).

### 2.1 People

The main directory of manually created and optionally imported contacts, and the place the player's people are kept.

- Search by name, alias, organization or user-entered searchable detail.
- **Predefined relationship categories** (owner, 25 November 2026): one optional main category per person, chosen from Family, Friends, Colleagues, Work, School and Services. A category is a relationship word, not a free-form label; words already on old saves (Friend, Colleague, Service provider) keep showing rather than being guessed away, and the 0.243.0 one-time repair that gave the category back from a group stays. The player's own words belong in groups.
- **Customizable groups** (owner, 24 November 2026; kept by the 25 November 2026 direction): a person can be in any number of groups; the player makes, renames and deletes them, and deleting a group deletes nobody. A group sits beside the main category and never replaces it; "Me" is not a group. Avoid mandatory categorization.
- Sort by name, recently updated, **My order** or **group** (owner, 24 November 2026), and the directory **starts** sorted by group (owner, 25 November 2026); name, recent and My order remain. In My order the player moves a person up or down among the people shown (a swap with the neighbour on screen; the Me person stays first), and it is only the player's own arrangement, never a score or a ranking, and it never pays. By group, each person is listed once, under the heading of their first group in the player's group order, people in no group last under "No group", like MINT's account groups (MINT.md 4.3); My order and name are unchanged, and Me stays first in every sort. Do not rank by importance or engagement.
- Favorite/pin is a convenience for directory access, not a relationship score.
- Support contacts with only a name; phone number, email and other fields are optional.
- Duplicate detection should suggest possible matches without silently merging records.
- Each person keeps their Friend Safari and encounter mechanics as they are (9.5); the directory does not change them.

### 2.2 Person profile

A profile is the central place for one person's LINK-owned information and the history the archive holds about them.

**Overview**
- Name and optional preferred name.
- Optional phone, email, organization, role, address and other user-entered fields.
- Main category, groups and tags (2.1).
- Socials, likes and dislikes, and the slambook answers the player chose to write (2.6).
- The next important date or open follow-up, and the last recorded meetup (2.4).
- Source/provenance for imported fields where relevant.

**Notes**
- Free-form relationship context and dated notes.
- User-controlled tags/search.
- Notes are private to LINK and are never included in notifications or Pokémon progression.

**Slambook**
- The person's **Profile** tab after Overview: About, Mind and Stars (2.6), including the slambook question set for their relationship category.
- Personality information the player typed in from tests taken elsewhere (MBTI, Enneagram, DISC, Big Five and the rest of 2.6); never inferred.

**History**
- The memories the person is in, newest first, with their photos and eras.
- Their important dates and follow-ups (2.4), and the last recorded meetup.
- Photo gallery (built, 0.245.0): every photo from the memories that include this person, filled by association and never copied into a second gallery; the Overview shows a strip of up to eight of them. A photo assigned to the person alone is not built.

**Connections (built 0.267.0)**
- A person can be connected to other people the player recorded: family, friends, colleagues and the player's own words for the connection.
- A connection is an explicit record between two people with its label; it is shown on both profiles, can be edited or removed, and is never inferred from names, photos, messages or shared details.
- Deleting a person removes that person's connections and changes nobody else's profile.
- Built (0.267.0): `PersonConnection(id, aId, bId, label, createdMillis)` has no direction, and its one word is shown on both profiles. A connection links two different existing people; the word is up to 30 characters (`CONNECTION_LABEL_MAX`); the same word twice for one pair is refused, ignoring case, while a second different word is allowed. The suggestions are Family, Partner, Friend, Colleague, Neighbour and Classmate, and any other word is the player's own. The person Overview has a Connections section after Photos: a row opens the other profile, Change edits the word or removes it (with Undo), and Connect to someone picks a person. No counts, scores or inferred links. Connections live in the `connections` list of `link_state` JSON (no schema step), travel in the People export and are erased with People.

**Reorganised profile (partly built 0.271.0; the rest specified, not built; connections themselves are built, see above).** The archive direction groups the profile so the everyday view stays short: overview and contact first, then slambook, then history (memories, dates, meetups, follow-ups), then photos and connections. Navigation is simplified, and secondary detail is revealed on demand rather than every field being shown at once (10).

Built (0.271.0, REP-084 remainder): the person Overview shows "Last met" (the latest memory with a full date) and "Show all N" for memories beyond the first four. Still not built: simpler tab navigation (Overview, Profile, Notes and Follow-ups remain two rows), grouping of the contact, slambook and history sections, secondary detail on demand, and connections in search.

Do not display inferred relationship health, contact frequency, response time, or activity charts.

### 2.3 Memories and Eras

**Core built (0.244.0; owner, 25 November 2026), local photos and the Gallery are built (0.245.0), and the optional OneDrive source is built on Android (0.247.0).** The memory and era records exist: the timeline, the chapters, their partial dates, the associations and On This Day are in the app. Local photos are kept with their memories and the Gallery is the third browsing mode (0.245.0); the OneDrive source adds a photo to a memory as a normal local photo on Android, and desktop and web have no source. The archive direction turns the old planned "Moments" timeline into this record: the events and everyday experiences worth keeping, organised into life chapters and browsed by timeline, photo gallery or chapter.

**Moments (memories).** A memory is one recorded event, gathering, trip, milestone or everyday experience. It carries:
- A title and a note the player writes; one of the two is enough, and a memory with neither is refused.
- A date as precisely as the player knows it: a year, a month, a day, any of them, or none, with an "Around then" mark for a memory the player only remembers approximately. A month needs its year and a day needs its month; the year range (1000 to 2200) and leap days are checked, and a fully blank date is unknown rather than guessed.
- An optional place and an optional time, as typed.
- Any number of people, including Me and people with no category; deleting a person takes them out of every memory that names them and never deletes the memory.
- Any number of eras, which may overlap; a memory may belong to none, one or several.
- Up to nine local photos, each with its own caption; photo-first entries are the normal way to record one (below).

**Eras.** An era is a life chapter the player creates by hand, such as a school period, a place they lived or a relationship chapter. It carries:
- A title (required) and an optional note.
- Flexible start and end dates with the same partial-date editor: a year, a month or a day, any of them or none, approximate if the player says so; either end may be open (an era that is still going), and eras may overlap freely. A start after its end is refused.
- The memories associated with it. Eras are never inferred from dates, locations or the player's records; they are only what the player makes.
- Deleting an era removes the association and never deletes a memory; deleting a memory never deletes an era.

**Photos (local photos built, 0.245.0; the OneDrive source is built on Android, 0.247.0).** Memories are photo-first: the player can start from photos and add the words later, or start with words and add photos. A memory keeps up to 9 local photos, each with its own caption (up to 200 characters); the memory itself has a description. A photo is picked through the platform picture picker (the Android Photo Picker on Android), which downscales a large picture to fit; on Android several can be taken at once from the system gallery picker ("Add photos") or from the system file picker ("From files": any documents provider the phone has, including a cloud app's folder, with no account or sign-in inside LINK and no extra permission). LINK accepts PNG, JPEG and WebP and stores the picture once in the Link document as standard base64 with no `data:` prefix, at most 1,400,000 base64 characters (the same budget as MINT's receive QR), and the base64 must decode. A photo belongs to its memory: the form shows it as a 96 dp thumbnail with Caption and Remove (Undo), deleting the memory deletes its photos, the "Memories and eras" export/erase kind includes them, and a person's gallery gathers the photos of the memories that name them. A photo can also come from the optional OneDrive source on Android (6.2): it is downloaded once and kept as a normal local memory photo, and a photo is stored once, with each place pointing to it rather than holding a duplicate.

**Browsing modes.** The same records can be browsed three ways; Timeline and Chapters are built (0.244.0) and the Gallery is built for local photos (0.245.0), with the optional OneDrive source adding to it on Android (6.2):
- **Chronological timeline:** memories newest first, a heading for each year and memories with no known year last under "No year"; each row shows its date, place and people, and tapping it opens the **Memory Detail** (below); Edit, with Delete and Undo, is a separate key there.
- **Photo gallery:** a grid of the stored photos, newest added first, each tile labelled with its memory's title and its caption or date; tapping one opens it full size with its caption and Close, and a person's page shows a strip of their photos. The full-size view is the shared photo viewer (swipe, zoom, rotate, Open memory key; `FEATURES/DESIGN_SYSTEM.md` 6.8A, built 0.266.0).
- **Life chapters:** the eras, newest first by the chapter's end date (or its start when it has no end) and undated chapters last, each showing its range and memory count and opening to the memories in it, for looking back at a chapter as a whole.

Dates read as "14 Mar 2024", "Mar 2024", "2024", "Around 2024" or "Unknown date"; an era's range reads "2020 - 2024", "2020 - now", "Until 2024" or "No dates" (a plain hyphen, because the DP font has no en dash).

**Person-specific collections.** A person's page gathers their memories, dates, follow-ups, last recorded meetup and photo gallery from the associations already recorded; the collection is filled by association, not by copying records into a second list. The Overview lists up to four of the person's memories and an "Add a memory" that preselects that person; the last recorded meetup is the latest of those memories that knows a full date (2.4).

**Memory Detail (owner, 27 November 2026 — Sprint 2).** Every memory entry opens the same read-first page: the memory's name, its date, place and time, the note, the photos (tapping one opens it full size), the people (each opens that person), the chapters (each opens the Chapters view on that chapter) and the PLAN events it is attached to (6.3). **Edit** is one key on that page and opens the memory form; Back returns to where the player came from. Timeline rows, a chapter's memories, On this day, a person's Memories and a photo's full-size view (an **Open memory** key) all open it, and so does a search result. Nothing on the page writes.

**On This Day and historical discovery.** The "On this day" box at the top of Memories is built (0.244.0): it gathers the memories the player recorded on this calendar day in earlier years. Historical discovery is browsing by year, era, person and photo gallery; LINK suggests nothing from outside the player's own records and ranks no memory by engagement. It all works entirely on the phone from local records.

Memories and eras never score, rank or remind on their own. They pay nothing on their own today; the owner direction for extra Pokémon progression through memories and meaningful milestones is a future reward source to be specified in the shared ecosystem and the economy (9.6).

### 2.4 Reminders

One destination (owner, 25 November 2026) replaces the separate Dates and Follow-ups places. It keeps the same records and rules, gathered as reminders, and every record is still reachable from the person it belongs to.

**Important dates.** A compact list of dates associated with people or optionally recorded independently.
- Supported date types: birthday, anniversary, personal milestone, custom date.
- Each date supports: title/type and month/day; optional year, including an unknown year; annual recurrence or one-time occurrence; optional associated person; optional advance reminder preference; optional private note.
- A date without a known year remains valid. Leap-day recurrence keeps the documented, predictable rule; 29 February is observed on 28 February in a year without one.
- Deleting a person must not silently delete an independently meaningful date; ask whether to retain or remove linked dates when necessary.

**Follow-ups.** Lightweight reminders of something the player intends to do or revisit.
- Fields: required title; optional linked person; optional due date/time; optional context note; optional reminder preference; status Upcoming, Due, Completed or Cancelled; created, updated and completed timestamps.
- A follow-up may exist without a linked person, and undated follow-ups remain in the list.
- Completing a follow-up records completion once and preserves its history; reopening is allowed but never pays a second Pokémon reward; cancelled follow-ups do not earn rewards.
- Overdue items are presented neutrally: no shame language, escalating warnings or negative game consequences.
- No automatic follow-ups are generated from contact imports, dates, memories, messages, photos or call history; do not nag the player to contact someone or imply a relationship is deteriorating.

**Last recorded meetup.** A person's last recorded meetup is the latest of their memories that knows its full date (`LinkRules.metUpOf`); there is no separate meetup record and nothing is inferred from messages, calls, location or photos. It is a fact the player recorded and is never a target, a streak or an automatic reminder; the Reminders place shows it when REP-087 is built.

**Consolidated upcoming reminders.** One dashboard lists the next birthdays, anniversaries, personal milestones and follow-ups together, with what and who they are about and the reminder setting; the overview shows only what needs attention rather than a full calendar.

**Widgets.** Birthday and important-date widgets show the next dates from the local records, opt-in like the app's other widgets, with generic text by default and hidden when LINK is locked; a widget reads the local store and never needs a network or an account.

**PLAN integration (owner, 24 November 2026; stays).** LINK does not automatically create Google Calendar or Android Calendar events. Dates and open follow-ups with a due day (not ones made a PLAN task) appear in PLAN's calendar as read-only all-day entries in LINK's accent through the integration layer (`../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` 8.1A). Nothing is written to the phone, nothing pays, they never reach reminders, the launcher or widgets, and LINK locked hides them. A Me > Settings switch each for Dates and Follow-ups (both on). A person can also be tied to a PLAN event (field "People", at most 12), stored only in the integration document; linking a PLAN event to a memory is a separate archive link (6.3).

Follow-up conversion to PLAN keeps one completion and one owner:
- Before conversion, LINK owns the follow-up and its status.
- After conversion, PLAN owns the actionable task, scheduling and task completion.
- LINK retains the person association and relationship context, and displays the linked PLAN task status.
- A single completion must not be recorded as two independent real-world actions or paid twice.
- Deleting or unlinking either side follows an explicit, recoverable link policy; it must not silently delete the other module's record.
- After conversion PLAN is the reminder source; LINK avoids a duplicate reminder.

### 2.5 Unified search and organization

- One search entry point across people, memories, eras, reminders, notes, slambook answers and photo captions. **Built (Sprint 2, 27 November 2026): search on LINK's main screen opens by the snappy pull-down only, with no Search key in the header (owner, 28 November 2026: the key was removed) over people, notes, memories (title, note, place, people), chapters, photo captions, dates and follow-ups; results are grouped by kind under a heading, show a line of context and open the exact record (a memory opens its Memory Detail, a chapter the Chapters view, a photo caption its memory). Search also shows whenever any of those records exist, including a memory-only archive.** Slambook answers and the filters below are not built.
- Filters by record kind, relationship category, group, person, era, year and reminder status; search results respect LINK's lock setting.
- No automatic categorization or tagging based on message content, photo content or inferred identity.
- Search indexes remain local and are rebuilt safely after migration; the archive is searchable offline.

### 2.6 Profile, slambook and birth details

A person's profile is also a **slambook page**: the details the player chooses to write down, plus readings that are worked out from a birth date the player gave. The player has one card of their own, **Me**, pinned first in the directory, with the same page.

- **Me.** One person marked `isMe`, created from People ("Set up my profile"); asking again never makes a second. It is an ordinary person (it can be deleted, exported, erased) and nothing about it is special to rewards.
- **About.** Socials (a platform and a handle or link, as typed), likes, dislikes, and slambook answers: a tap-to-answer list of classic questions (nickname, favourite colour, food, song, Pokémon, hobby, dream job, motto, pet peeve, best memory with me, ...). All optional, all searchable.
- **Question sets per relationship category** (owner, 25 November 2026): the classic questions are organised into sets, one per predefined category (Family, Friends, Colleagues, Work, School, Services), plus a general set for a person without a category. The player can customise a set: add their own questions, choose which ones to show and reorder them. Removing a question from a set never deletes an answer already written, and customising one set changes no other person's page.
  - Built (0.271.0, REP-085): `QuestionSet(category, questions)` in `LinkSettings.questionSets` (JSON in the LINK document, no schema step, defaults empty). Starting sets: Family, Friends, Work, School and Services are shorter subsets of the 16 classic questions; any other or blank category uses the general set of all 16. Colleagues share Work's set (no separate Colleagues category exists). The player's own set for a category (matched ignoring case) replaces its starting set: reorder, hide and show again, add an own question, or go back to the starting set, in LINK Settings. The slambook's Add a question offers the person's category set. Hiding or removing a question never deletes an answer already written, and customising one set changes no other. Sets are settings, not records: not in the export or the erase kinds.
- **Mind.** The result of a test the player took elsewhere, typed in, never inferred: MBTI type, Enneagram type and wing, love languages (first and second), attachment style, DISC, Big Five (lower, middle or higher per trait). Each label has a plain-language reading, shown when it is tapped. LINK offers no test and scores nothing.
- **Stars.** Worked out on the phone from the person's Birthday date (one record, now held in Reminders, with its reminder) and an optional birth time, UTC offset and birthplace. Nothing is stored except what the player typed.
  - **Western natal chart:** Sun, Moon, Mercury to Pluto and the mean North Node, retrograde marks, aspects (conjunction, sextile, square, trine, opposition within orb), and with a birth time and place the rising sign, midheaven and whole-sign houses. Accurate to about a third of a degree (JPL mean elements for 1800 to 2050 and a low-precision Moon), so a body on a sign line may really be in the next sign; the page says so. With no birth time, a body that changes sign that day is shown as "A or B". With a month and day but no year, only the newspaper sun sign is shown.
  - **Chinese:** zodiac animal and element (the year turns at Chinese New Year), the **Four Pillars (BaZi)** with the Day Master and a five-element count (the year turns at Lichun, the month at each *jie* solar term, the day at midnight; the late Rat hour, 23:00 to 24:00, takes the next day's stem and keeps the birth day's pillar; the clock time as written, no true-solar-time correction, compared with the solar terms on the Beijing clock), and the **Kua (Ming Gua) number** with its group and four lucky directions (needs the player to choose the male or female formula). Supported for 1902 to 2098; outside that the Chinese readings are not shown rather than guessed. The tables are generated by `tools/link/generate_bazi.py`.
  - **Numerology:** the Life Path number, keeping the master numbers 11, 22 and 33.
- **Readings are for fun.** Every reading is fixed text in a traditional spirit with a visible "for fun and conversation, not advice or a verdict" line. LINK never scores, ranks or compares people by them, never judges a relationship with them (section 1.2), and never uses them in a notification, a search ranking or a reward. A profile is private LINK data under section 7: it is in the People export and erase, and no figure, handle or word of it reaches `LinkEvent` or any other module.

---

## 3. Primary workflows

### 3.1 Add a memory

1. Choose Add a memory in Memories, on a person's page (which preselects that person), from **Memory in Home's Quick actions or in LINK's own Quick actions box** (owner, 27 November 2026; both open Memories with the form already open), or after a calendar event (3.4).
2. Write a title, a note, set a date or add a photo; **any one of the four is enough** (owner's Sprint 1 list, 27 November 2026), so a photo-only or date-only moment can be kept now and finished later. A place or a time alone is not enough. A memory without a title is called by its note's first line, else its date, else "Photo memory" or "Untitled memory" (`LinkRules.memoryLabel`).
3. Set the date with the Year, Month and Day fields, leaving unknown parts blank, and turn on "Around then" for a memory the player only remembers approximately. A month needs its year and a day needs its month.
4. Optionally add the place and the time.
5. Choose any number of people and chapters with the People and Chapters chips.
6. Save; find it later in the timeline, the chapters or the person's page.

### 3.2 Create an era

1. Choose Add an era in the Chapters view of Memories.
2. Give it a title and an optional note.
3. Set its start and end with the same Year/Month/Day editor; either end may be open or unknown.
4. Save, then add memories to it from the memory form's Chapters chips.
5. Edit or delete the era at any time; deleting it never deletes a memory.

### 3.3 Find photos (local picker or OneDrive)

1. Open a memory's form (or add a memory).
2. On Android, choose Add a photo for one picture from the Android Photo Picker, Add photos to take several at once from the system gallery picker, or From files to take several from the system file picker (OpenMultipleDocuments for any documents provider the phone has, including a cloud app's folder; the provider's own sign-in happens outside PokéLife and LINK asks for no account). PNG, JPEG and WebP are accepted, and each picture is downscaled to fit the budget.
3. The photos appear in the form as 96 dp thumbnails; add or edit their captions (up to 200 characters), or Remove one.
4. **Photo changes are staged until Save** (owner's Sprint 1 list, 27 November 2026): added photos, removed photos and caption edits wait in the form, and Save applies them with the text in one write (`LinkStore.saveMemory`), all or nothing; Cancel discards every pending change, for a new memory and an existing one alike. A refusal (a picture too big, nothing left in the memory) changes nothing. Adding a photo from OneDrive or from a gallery tile is its own explicit action and writes straight away.
5. Find the photos in the memory's row ("1 photo" or "N photos"), in the Gallery pill, and on the pages of the people the memory names.
6. The optional OneDrive path on Android (6.2) is built: connect, browse folders and photos, choose one photo, **choose the memory yourself** (no memory is preselected; the list shows each memory's name, date and people, and the add key stays off until one is tapped) and Add to this memory; it is downloaded and kept as a normal local photo. Multi-select on OneDrive is not built yet.

### 3.4 Link a PLAN event to a memory

1. After a scheduled PLAN calendar event, choose **Save as Memory** on the event.
2. The memory form opens with the event's title and day already filled, and the people already linked to the event (at most 12) offered; the player can change everything.
3. Save the memory. LINK records a reference to the PLAN event in the integration layer; it does not create a second calendar event.
4. Or attach an existing memory to a PLAN event from the memory's page.
5. Edit or delete the PLAN event, or the memory, without changing the other; a broken link is detached explicitly, never by deleting a record silently.

### 3.5 Add a reminder

1. Add an important date from Reminders or from a person's page.
2. Choose the date type and the day and month; add a year only when known.
3. Choose annual or one-time behaviour.
4. Optionally choose an advance reminder through the shared reminder feature.
5. Add a private note if wanted; find it later in Reminders and on the person's page.

### 3.6 Capture a follow-up

1. Enter a short title.
2. Optionally associate a person, due date/time and context.
3. Optionally configure a reminder.
4. Complete or cancel it when appropriate; overdue stays neutral.
5. Optionally convert it to a PLAN task if it needs fuller scheduling or execution management (2.4).

### 3.7 Add a person

1. Create a person manually with a name.
2. Add only the details that are useful.
3. Optionally assign a predefined main category, a group, a tag or a favorite.
4. Optionally add a connection to another person, a slambook detail, a note or a photo.
5. Save without requiring a phone number, email or device-contact permission.

### 3.8 Capture context

1. Open a person.
2. Add a short note or dated context entry.
3. Save immediately with minimal required fields.
4. Find it later through the profile or LINK search.

Do not force the player to choose a note type or fill a structured form for ordinary context.

### 3.9 Import device contacts

1. The player explicitly chooses Import contacts.
2. Explain the Android Contacts permission and the fields PokéLife will read.
3. Request permission only at this point.
4. Let the player select individual contacts or a clearly explained import scope.
5. Preview proposed additions and matches before writing LINK records.
6. Let the player confirm, skip or resolve uncertain duplicates.
7. Preserve imported-field provenance and any user-curated LINK-only information.

Import is never a prerequisite for using LINK.

---

## 4. Data ownership and relationships

LINK owns the following canonical records:

| Record | LINK responsibility |
|---|---|
| Contact | Identity and player-selected contact details |
| Relationship category | The predefined relationship word; one optional per person |
| Group | Player-defined organization; any number per person |
| Relationship note | Private player-authored context |
| Person connection | How two people are connected, as the player records it |
| Memory | Event or experience record with its flexible date, people, eras and photos |
| Era | Player-created life chapter with flexible start and end dates |
| Photo (local) | Image files and thumbnails LINK stores or caches, with captions and associations |
| Important date | Date, recurrence and association (shown in Reminders) |
| Follow-up | Lightweight commitment until explicitly converted (shown in Reminders) |
| Last recorded meetup | The day the player recorded meeting a person |
| Contact source mapping | Import provenance and reconciliation |
| OneDrive photo | Read once when the player adds it to a memory; the original stays Microsoft's and the added picture is LINK's local photo |
| PLAN memory link | A reference in the integration document; PLAN owns the event |
| Person-team assignment | References to shared owned Pokémon; the place and the Me exception are in `../FEATURES/POKEMON_ECOSYSTEM.md` 2.2.2A and the passive EXP waits for the economy (9.6) |
| Postcard Trail and Safari state | LINK's optional acquisition progress and idempotency references |

**Conversion rules (built).** `GameStore.convertFollowUp` makes one PLAN task per follow-up id (`TaskLink(LINK_FOLLOW_UP, id)`); PLAN then owns its completion and pays it once by PLAN's rules, and `GameStore.linkedTask` gives LINK the task and its state to display. `unlinkTask` leaves a normal PLAN task. `FollowUp.converted` is set on handover and never cleared, so unlinking or deleting the task never makes LINK pay; while the task exists LINK refuses to finish or cancel the follow-up, and a task finished in PLAN is mirrored as completed without a reward.

**Memories, eras and photos (owner, 25 November 2026; built 0.244.0-0.245.0).** LINK owns the memory and era records, the captions and the associations, and the local photo files it stores. A photo added from the optional OneDrive source (6.2) is downloaded once and kept as a normal local photo; the original stays in OneDrive and LINK keeps no OneDrive reference record. Deleting an era removes only its associations. Deleting a memory removes its associations and the local copies of photos that only it used (a photo still associated elsewhere stays), and never touches the OneDrive original.

**PLAN memory links (owner, 25 November 2026; built 0.263.0).** A memory may reference a PLAN calendar event by a stable id through the integration layer, the same way the event "People" links work. PLAN owns the event and its edits and deletes; LINK owns the memory. No calendar record is duplicated, and breaking the link is explicit and recoverable: it never deletes the memory or the event silently.

**Person-team assignments (owner direction, 25 November 2026; specified, not built).** A person's Pokémon team references shared owned Pokémon; LINK never keeps a second collection and never pays outside the linked event boundary. The placement (a place of its own, with LINK's party serving as the Me entry's team) and the capacity of six are decided in `../FEATURES/POKEMON_ECOSYSTEM.md` 2.2.2A; the passive EXP and the memory/milestone progression are to be specified in the economy before build (9.6).

### 4.1 Cross-module boundaries

| Capability | Owner | LINK relationship |
|---|---|---|
| Full tasks, projects, calendar and task execution | PLAN | Explicit conversion/link only |
| PLAN calendar events | PLAN | A memory may reference one through the integration layer; LINK never creates or edits a calendar event |
| Shared reminder scheduling and delivery | `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` | LINK supplies source records and preferences |
| Global Pokémon collection, Pokédex, Bag, Poké and reward ledger | Shared Pokémon/economy services | LINK emits eligible abstract progression events; a person team references shared owned Pokémon |
| Memories, eras, captions and local photos | LINK | Canonical owner; no other module reads the private content |
| OneDrive photos | Microsoft and the player | Read only when the player adds one to a memory; the added copy is LINK's local photo, and LINK never writes, moves or deletes a file there |
| Journal, mood and self-care records | CARE | No access |
| Health/activity/medical records | HEAL | No access |
| Household and service records | BASE | May reference a LINK contact only through an explicit link |
| Messaging/calls/social feeds | External apps | No ingestion or monitoring |

LINK must not copy a PLAN task into a second independently editable task, and must not copy a PLAN event into a second editable calendar record. A linked representation is a reference with clear navigation and status provenance.

### 4.2 Reward event boundary

The shared reward service receives only the minimum event data needed to enforce LINK's acquisition policy, such as an opaque follow-up ID, policy version, module identifier and completion timestamp. It must not receive:
- Contact names or identifiers.
- Phone numbers, email addresses or addresses.
- Relationship categories, groups or connections.
- Note text, follow-up titles/context or memory text.
- Photo data, file names, captions, OneDrive references or tokens, or image thumbnails.
- The identity of the person associated with a follow-up, a memory or a milestone.

The event is evaluated inside the LINK/reward boundary. Other modules and WORLD receive only a generic reward result. The future progression through memories and meaningful milestones (9.6) uses the same opaque boundary and no figure, name, caption or photo crosses it.

---

## 5. Reminders and notifications

Shared scheduling, quiet hours, notification permissions, delivery, recovery and action routing are defined only in `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.

LINK owns:
- Which important dates and follow-ups may request reminders.
- Source-record references and LINK-specific reminder preferences.
- What opening a reminder should navigate to (Reminders, the person, or the linked record).
- Whether a reminder is enabled, snoozed or dismissed at the LINK source level.

**Choosing a reminder is the opt-in:** the first reminder chosen on a date, in the date form, in an edit or in the contacts import, turns LINK's master reminder switch on; nothing is on before that, and Settings can turn them all off again. Suggested date presets may include the day of, one day before, three days before and one week before. These are player choices, not mandatory defaults. Follow-up reminders are opt-in and fire on the follow-up's day; each reminder is one nudge, with quiet hours and focus sessions respected. A follow-up converted to a PLAN task leaves PLAN as the reminder source, and LINK avoids a duplicate.

**Consolidated upcoming reminders.** Reminders is the one place that shows what is coming: the next birthdays, anniversaries, personal milestones and follow-ups together, soonest first by day, with what and who they are about. The place does not create a second scheduler; it reads the same source records and preferences.

**Widgets.** Birthday and important-date widgets are part of the shared widget feature, refreshed from the local store like the app's other widgets and opt-in; they show the next dates with generic text by default and follow LINK's lock setting.

**Last recorded meetup.** The date the player recorded meeting a person is shown as a fact. It never produces an automatic reminder, a "days since" counter, a streak or any nudge to meet again; the player can set a follow-up if they want one.

Notification privacy:
- Generic lock-screen text by default, such as "You have an upcoming reminder."
- Do not expose contact names, date types, notes, memory text, captions or follow-up context unless the player explicitly enables more detailed previews.
- Dismissing a reminder never changes a relationship record or causes a reward penalty.

---

## 6. Integrations and permissions

### 6.1 Android Contacts

Device-contact integration is optional and Android-specific.

- Manual LINK operation requires no contacts permission.
- Request permission only after an explicit import action.
- Import only player-selected contacts/fields and explain the scope before confirmation.
- No continuous background synchronization in the initial release.
- No automatic upload, enrichment, reverse lookup or contact scraping.
- Re-import occurs only when the player requests it.
- Preserve LINK-authored fields when imported data changes.
- Show conflicts and let the player choose which value to retain.
- Revoking permission stops future imports but does not delete already imported LINK records.
- Store a source mapping sufficient for reconciliation without treating the device provider as LINK's permanent source of truth.

Do not add permissions for call logs, SMS, notification access, accessibility scraping, location or social-media accounts.

### 6.2 Microsoft OneDrive (optional read-only photo source; owner, 25 November 2026; built 0.247.0)

An optional photo source for the archive. The connection and the first photo flow are built on Android (0.247.0); desktop and web have no source and hide the place.

- **Optional and off until connected.** LINK is fully usable with no Microsoft account. The OneDrive place sits in LINK's Sections menu between Follow-ups and Settings and exists only where the platform has a source; nothing is read before the player connects. A build with no app registration says "OneDrive isn't set up on this build." and opens no sign-in page.
- **Sign-in and registration.** The connection is the OAuth 2.0 authorization code flow with PKCE (S256) opened in the phone's browser; Microsoft redirects to `pokeplan-onedrive://auth`, which the app handles on launch and on a new intent. Scopes are exactly `Files.Read offline_access`: read-only, never a write scope, and no client secret is used or stored. The client id is compiled from the `onedriveClientId` Gradle property or the `ONEDRIVE_CLIENT_ID` environment variable into `BuildConfig.ONEDRIVE_CLIENT_ID`; the release workflow and the `apk`-labelled CI build pass it from the repository secret. **Owner set-up:** register a Microsoft Entra app as a public client supporting personal accounts, with the redirect URI `pokeplan-onedrive://auth` and delegated Graph `Files.Read`, and set the client id for releases.
- **Read-only reads.** LINK lists folder children (folders first, then photos by name), asks Graph for a photo's thumbnail, and downloads a full photo only when the player adds it; all are HTTPS Graph GETs. Limits: a folder listing up to 4 MB, a thumbnail up to 2 MB and a full photo up to 20 MB (refused over). It never creates, uploads, edits, moves, renames or deletes a file in OneDrive, and it never changes a folder.
- **Adding one photo.** The player browses folders, taps a photo, chooses one of their memories and chooses Add to this memory; the picture is downloaded, encoded as standard base64 and added to that memory as a normal local memory photo (the same path as the Photo Picker, REP-082). It is therefore exported, backed up and erased with the rest of LINK, and the OneDrive original is never modified. The added copy obeys the same limits as any memory photo (up to 9 per memory, 1,400,000 base64 characters and a 200-character caption), so a photo over that is refused with a message. Single-photo selection only for now; multi-select and adding to an era or a person directly are not built.
- **Never modifies the source.** There is no write, move, rename or delete path to OneDrive anywhere in LINK. Disconnecting never deletes a file there.
- **Tokens.** Access, refresh and expiry are sealed with an Android Keystore AES-256-GCM key (alias `pokeplan-onedrive`) into `filesDir/onedrive-tokens.bin` (temporary write, then rename); they are never in the save, an export, a backup or a log, and disconnect deletes the file. A refresh happens on demand and rotates the refresh token; a sign-in Microsoft has refused is marked stale and the place asks to connect again.
- **States and failure.** The place shows not set up, connect, connecting, connected, needs sign-in and unavailable, and a failed read shows a short Microsoft/Graph message. Offline or on an error the archive keeps every record and every photo already added, and manual LINK use never depends on OneDrive. A failed sign-in, an expired token or a revoked permission never damages LINK data.
- **No LINK data uploaded.** OneDrive is used only to read photos the player picks. No contact, note, memory text, caption, reminder or other LINK record is sent to Microsoft, and no sync, server or LINK account exists.
- **Not built.** Multi-select, cache management and clearing, offline copies beyond the added local photos, sharing and brightness controls, the Microsoft provider due diligence and a run on a device. The exact permission, scope and token rules are owned by `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` section 8.2A.

### 6.3 PLAN (calendar events and memories)

The existing LINK-PLAN integration stays: dates and open follow-ups appear as read-only all-day entries in PLAN's calendar, and people can be tied to a PLAN event (2.4).

The archive direction adds a memory link (built 0.263.0, owner, 25 November 2026):
- After a scheduled PLAN calendar event, **Save as Memory** creates a LINK memory pre-filled from the event's title, day and linked people.
- An existing memory can be attached to a PLAN event instead; the link is a reference stored in the integration layer (`app.pokeplan.app.integrations`), not a duplicated calendar record.
- A memory and its PLAN event are independent: editing or deleting the event never edits or deletes the memory, and deleting the memory never deletes the event. Breaking the link is explicit and recoverable.
- Nothing here pays; a memory is not a reward input today (9.2, 9.6).

### 6.4 Other integrations

No messaging, email, social-media, CRM, cloud address-book or external contact-enrichment integration is required. The optional read-only OneDrive photo source in 6.2 is the one cloud exception and is used only for photos the player picks. Any future integration needs a separate privacy and feasibility review and explicit player authorization.

---

## 7. Privacy, security and data lifecycle

Relationship context, memories and photos can be sensitive even when they are not formally classified as health or financial data.

- Store LINK records locally in the approved persistence architecture.
- No mandatory account, cloud profile, PokéLife cloud sync, advertising, profiling or telemetry containing LINK data.
- Do not transmit LINK content to third parties. The only network read is the optional OneDrive photo read in 6.2, made only for the account the player connected and the photos they browse or pick.
- Protect local data and exports using the shared security, encryption and backup architecture.
- Never place raw contact details, note text, memory text, captions or photo paths in logs, crash reports or analytics.
- Do not expose relationship notes, contact details, memory content, photos, captions or follow-up context to WORLD, launcher surfaces or unrelated modules.
- Do not use contact or photo data to infer sensitive traits, relationship quality or who is in a photo; there is no face recognition and no automatic tagging.
- OneDrive tokens are sealed with an Android Keystore key (alias `pokeplan-onedrive`) in `filesDir/onedrive-tokens.bin`, never appear in the save, a LINK export or a backup, are never logged, and are removed when the player disconnects or revokes access; LINK requests read-only Graph scopes only.
- A photo added from OneDrive is a normal local memory photo, so deleting the memory removes it; browsing thumbnails are held in memory only, and neither ever touches the OneDrive original.
- Provide export of all LINK records and source/provenance metadata in a documented format; local photos and their captions travel with the export under the shared export rules, while OneDrive originals and OneDrive tokens do not.
- Backups and restores must preserve stable IDs, associations and cross-module links; a restored archive keeps the records and the photos already added, and the OneDrive place reads nothing until the account is connected again.
- Support deletion of individual records, selected imports and all LINK data.
- Deleting LINK data must not delete PLAN tasks, unrelated module records, shared Pokémon or any OneDrive file.
- Deleting a contact must provide a clear choice for linked dates, follow-ups, connections, memories and photos, and must not silently destroy independent records.
- Deleting an era removes associations only; deleting a memory removes its associations and only the local photo copies that belong to it. Neither deletes a person, a date, an important photo associated elsewhere or a OneDrive original.

---

## 8. Offline behavior

Core LINK workflows work entirely offline.

| Capability | Offline behavior |
|---|---|
| People directory and profiles | Fully available from local records |
| Notes and search | Fully available |
| Memories, eras and their associations | Fully available |
| Photos stored locally (including ones added from OneDrive) | Fully available |
| OneDrive folder/photo browsing | Unavailable without a network; nothing is cached on the phone, the place says OneDrive is unavailable, and photos already added stay in the archive |
| Reminders (dates, follow-ups, last meetup) | Fully available |
| Local reminder scheduling | Uses shared local reminder infrastructure |
| PLAN links | Available from local module data |
| Device-contact import | Available only when Android Contacts provider and permission are available |
| Pokémon reward resolution | Uses local shared services; safely queues or retries only if the architecture requires it |

No online lookup is needed to add, edit, search or export LINK records. Import or OneDrive failure must not affect existing LINK data.

---

## 9. Optional Pokémon acquisition: Postcard Trail

LINK's optional acquisition identity is **Postcard Trail**. It evokes correspondence and places remembered without turning real people into collectible objectives.

### 9.1 User control

- Postcard Trail is a LINK-specific optional progression feature.
- It is enabled as a visible, player-controllable feature; the player can disable it at any time.
- Disabling it stops new LINK progression events without affecting contacts, notes, dates, memories, photos, follow-ups or reminders.
- Previously recruited Pokémon and earned rewards remain permanently owned.
- The setting can be changed without losing LINK data or other module progression.

### 9.2 Eligible activity

Only a player-completed, eligible LINK follow-up can contribute progress.

- Each distinct follow-up completion can contribute at most once.
- Reopening, editing, rescheduling, duplicating or re-importing a record cannot generate another event.
- Cancelling a follow-up does not contribute progress.
- Follow-ups converted to PLAN tasks use one canonical completion event; the LINK and PLAN representations cannot both pay.
- The event must not include the person's identity or any follow-up text.
- The future memory and milestone progression in 9.6 is a separate, specified-only source; until it is specified in the shared ecosystem and the economy, memories and eras pay nothing.

Explicitly ineligible today:
- Adding or importing contacts.
- Editing contact details, categories, groups, tags or favorites.
- Writing, browsing or viewing notes.
- Creating, editing or browsing memories, eras or photos.
- Adding or editing important dates or recording a meetup.
- Opening a profile or searching the archive.
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

The feature rewards the player's act of completing a self-chosen reminder, not the person, relationship, emotional closeness or outcome. It must not encourage unnecessary contact, manufacture follow-ups, reward repeated low-value tasks, pressure the player to maintain a social routine or require the player to document memories for progress. Keep the practical LINK workflow fully useful with Postcard Trail disabled.

### 9.5 Friend Safari (built in 0.225.0; owner decisions, 20 and 21 November 2026)

A second optional acquisition for the slambook: each person in LINK (the Me card too) has a **Friend Safari**, a small encounter pool of Pokémon only that person's card opens. Decided:

- **What decides a person's Safari:** the person's opaque id only, as a stable seed (like the real Friend Safari's friend code): LINK hands the shared world a hash of the record key (`LinkRules.safariRef`), never the key, a name, a birthday, a personality or a chart, so nothing private is read and nothing private reaches the reward boundary. The event is `SafariEvent` (id, opaque `ref`, the capped people count, time, policy), a type of its own: it never goes through `LinkEvent`, so a visit cannot also pay Postcard Trail's candies, Poké or track points.
- **The more people, the more spread:** the pool has a fixed order of slots decided by the seed alone (slot rarities Common, Uncommon, Uncommon, Rare, Rare, all of the Link Region); LINK's people count unlocks more slots (2 at the start, one more for every 6 people, at most 5), so adding people only appends slots and never reshuffles the ones a person already has, and with more slots each visit meets any one species less often (more species, each rarer). The count that spreads the pools excludes the Me card and is capped (20 *(tune)*), so made-up contacts buy nothing past it.
- **Entry is free (owner: "free entry. trust").** No ticket and no ball. **One catch a day for each person (owner, 21 November 2026)**: a visit meets one slot of that person's pool (species not caught yet six times as likely, like an egg) and catches it directly, no egg and no minigame, into Link's party (else the PC), at the ordinary shiny chance. Because that alone would make a pile of added people worth a catch each a day, **at most 5 Safari catches a day in all** *(tune; added by the build, owner to confirm)*. Adding or importing people therefore still pays nothing by itself; only visiting a Safari can.
- **What a visit pays:** the Pokémon (and the usual new-species candies, if it is a new species) and nothing else: no Poké, no trainer point, no track point, no item.
- Safari recruits through the shared Pokémon acquisition services, balanced centrally; LINK hard-codes no rarity economy of its own. Postcard Trail stays as it is. Off with Postcard Trail's switch or its own (Settings → "Friend Safari on each person"), and LINK stays fully useful without it. Same ethical boundary as 9.4: the reward is for opening a Safari, never for a person, a relationship or contact.
- **Presentation (as built):** a Friend Safari box at the end of a person's screen shows the slots (a species not met yet is a Friend Ball and a question mark) and a Visit key; once visited it says "Come back tomorrow." A new Pokémon arrives in a scene of its own ("A Friend Safari had a ..."). A Safari backdrop would use mainline assets, not PMD places (`ASSETS_AND_AUDIO.md` 4.0); none is drawn yet.

### 9.6 Person Pokémon teams (owner direction, 25 November 2026; specified, not built)

The owner wants each person to be able to hold a small Pokémon team of up to six, and LINK's own party also to serve as the player's team on the **Me** entry. The placement of the place is settled in `../FEATURES/POKEMON_ECOSYSTEM.md` 2.2.2A; nothing here is built, and no rate is fixed by this document.

- **Up to six Pokémon per person.** The player assigns shared owned Pokémon to a person, and that person's page shows them.
- **Me's team is LINK's party.** Because Me also has an entry in People, the Me page shows LINK's own party (one lead and five) as that person's team: the same six Pokémon, not a second roster and not a second placement.
- **Passive EXP over time.** An assigned Pokémon gains EXP passively while it stays with the person, without the player having to log anything.
- **Extra progression through memories and milestones.** Recording memories and reaching meaningful milestones with a person is the extra way the direction wants that person's Pokémon to progress; this is a reward source of its own, not part of Postcard Trail.
- **Canonical evolution and friendship.** Species evolution, friendship and the other canonical progression rules are adapted for this source, not reinvented.
- **No pressure.** No decay, no missed-day penalty and no mandatory documentation: a person left unvisited and a memory never recorded take nothing away.

**Still to be specified before build (owner to confirm the economy numbers; *(tune)* placeholders only).** The passive EXP and memory/milestone progression and their numbers are to be specified in the economy (`docs/economy.md`, `RewardRules.VERSION`) and balanced with a simulation before any build. The place itself, the capacity of six and the Me exception are decided in `../FEATURES/POKEMON_ECOSYSTEM.md` 2.2.2A; LINK keeps no second Pokémon collection and no shipped behaviour changes until the place is built.

---

## 10. Visual presentation

LINK follows the shared PMD dialogue-box visual system in `../FEATURES/DESIGN_SYSTEM.md` and uses the shared utility presentation intensity.

- Use shared dialogue-box/panel components for profile context, memory entries, reminder summaries and Postcard Trail discovery feedback.
- **Photo-first layouts (the Gallery is built, 0.245.0):** the gallery leads with photos, captions and a clear date, with the text available without competing with the image; the timeline and the memory form keep the words first.
- **Three browsing modes** (2.3): the chronological timeline, the photo gallery and the life chapters share one set of records and one visual language, so switching mode never changes the data.
- **Profile grouping and simpler navigation (specified, not built; the connections section is built, 0.267.0):** the profile is grouped into short sections (overview, slambook, history, photos, connections), with secondary fields revealed on demand, and the three primary places are reached from the header's Sections key rather than a row of keys on the page.
- Use compact native-feeling contact rows and efficient lists for the People directory.
- Do not wrap every contact, note, memory, date or follow-up in a separate card.
- Keep profile information scannable; reveal secondary fields on demand.
- Use typography, spacing and subtle separators to establish hierarchy rather than extra decoration.
- Pokémon art is limited to restrained, contextual Postcard Trail and Friend Safari moments; do not decorate every contact or imply that contacts are Pokémon.
- Do not use relationship meters, hearts, social scores, streak rings, leaderboards or urgency colors; do not show a "days since" count.
- Support light/dark themes, dynamic text scaling, TalkBack, minimum 48 dp touch targets, reduced motion and responsive widths, including photo grids at large text.

The design-system document owns reusable component rules, typography, spacing, borders, states and accessibility. LINK may define content-specific layouts but cannot fork shared component anatomy or global tokens.

---

## 11. Reuse policy

Existing code is evidence, not authority. LINK reuses the shared module shell, local persistence, encryption, backup/export and migration foundations, the shared reminder scheduler (through LINK source adapters), the shared Pokémon collection, acquisition and reward services, the shared design-system components, the Android Contacts provider only through a permission-aware platform adapter, and (for the optional photo source) a Microsoft Graph adapter in the Android platform layer that exposes only read operations. LINK has its own domain (models, repositories, persistence, search, import reconciliation, memory and era records, photo association, Postcard Trail state); Android Contacts, Microsoft Graph and other platform APIs stay in the Android platform layer and common LINK logic depends on no Android or Compose class.

### 11.1 Avoid

- Do not add LINK records to PLAN or CARE persistence.
- Do not use one unstructured global JSON object as the long-term data model.
- Do not build a second reminder scheduler.
- Do not request broad contacts access at app startup.
- Do not add call/SMS monitoring, social scraping or background contact synchronization.
- Do not add cloud pathways except the optional read-only OneDrive photo source in 6.2; never add a write path, a background sync or an upload of LINK data.
- Do not copy a PLAN calendar event into a second editable record.
- Do not create a separate Pokémon collection, currency, Bag or reward ledger, and do not invent evolution or friendship rules.
- Do not reward contact creation, note-taking, imports, photo browsing, memory creation or passive interaction data before the ecosystem and economy specify the memory/milestone source.
- Do not identify, tag or group people in photos automatically.

---

## 12. Remaining work

The archive direction (owner, 25 November 2026) is the build order below. The core of item 1 shipped in 0.244.0 and the local photos of item 2 in 0.245.0; everything else is **specified, not built** today. Nothing in this document is committed to a version.

1. **Memories and Eras (core built 0.244.0).** The memory and era records, flexible dates (exact, approximate, month-only, year-only, unknown, open era ends), multiple people and multiple overlapping eras, the chronological timeline and chapters browsing, On This Day and the person-specific history collection are built as the core. The photo gallery came with item 2 (0.245.0).
2. **Local photo galleries (built 0.245.0).** Photos stored with their memories, up to 9 per memory with captions, 96 dp thumbnails, the Gallery mode with 128 dp tiles and a full-size viewer, the person photos strip, and the photos in the Memories export/erase and deleted with their memory. A dedicated photo store/cache and the backup archive for large collections are still open (REP-083).
3. **OneDrive photo source (partly built 0.247.0).** The Android connection (OAuth PKCE, read-only), folder browsing, thumbnails, disconnect and adding one photo to a memory are built (6.2). Open: multi-select, cache management/clear and offline copies, the Microsoft provider review, and a run on a device.
4. **Person connections and the profile reorganisation (partly built 0.267.0).** Connections between people (family, friends, colleagues, the player's own words) are built (2.2). Open: the grouped, shorter profile UI with navigation simplification and secondary detail on demand, connections in search, and a connections section in the export UI beyond the People kind.
5. **Per-person Pokémon teams (specified, not built).** Up to six Pokémon per person, passive EXP, and extra progression through memories and milestones; the placement and the Me exception are decided in `../FEATURES/POKEMON_ECOSYSTEM.md` 2.2.2A, and the build waits on the economy numbers (9.6).
6. **Reminders consolidation (specified, not built).** One Reminders place replacing the separate Dates and Follow-ups places, the last recorded meetup, the consolidated upcoming dashboard and the birthday/important-date widgets.
7. **PLAN memory linking (built 0.263.0).** "Save as Memory" after a scheduled event, attaching an existing memory to a PLAN event, and the independent, recoverable link (6.3).

Also still open from before the archive direction:

- A chooser for fields that changed on re-import (today only empty fields are filled and nothing is overwritten).
- A link from BASE to a contact.
- Upcoming dates on the launcher, and widgets (now covered by 2.4 when built).
- Sealing Link's rows (a passphrase-sealed backup already ships and carries Link in clear).
- The open question of "keep in touch" (D8).
- Groups imported from phone contacts, and linked people shown on the event in the calendar rows (0.240.0).
- The OneDrive photo cache and offline copies beyond the first add-one-photo flow (6.2; open).

**Exit gate:** no silent merge or overwrite; manual LINK remains fully usable when permission is denied or OneDrive is not connected. Real-device testing of the whole module is also still to do (`../IMPLEMENTATION_BACKLOG.md`).

---

## 13. Acceptance criteria

### Product utility
- A person can be created with only a name.
- Notes, dates and follow-ups can be captured with minimal required fields.
- A memory can be saved with only what the player knows (a photo, a title or a date), and a memory or era date can be exact, approximate, month-only, year-only or unknown.
- An era has flexible start and end dates, may be open-ended, may overlap another era, and never deletes a memory.
- A memory can include several people and several overlapping eras.
- A memory shows multiple photos, each with a caption, and the three browsing modes (timeline, photo gallery, life chapters) show the same records.
- On This Day and historical discovery work offline from local records and suggest nothing from outside the archive.
- Search finds people, memories, eras and reminders; overdue follow-ups are neutral and do not create shame or penalties.
- LINK remains useful with Postcard Trail and Friend Safari disabled and with OneDrive not connected.

### Ownership and reliability
- LINK is the canonical owner of contacts, relationship notes, connections, memories, eras, local photos, important dates and unconverted follow-ups.
- A memory's links to PLAN events are references in the integration layer; no calendar record is duplicated.
- When built, person-team assignments reference shared owned Pokémon and never create a second collection.
- Recurrence handles leap days and time-zone changes predictably; flexible archive dates are never guessed.
- Associations are stored once; deleting an era changes no person, date or photo, and deleting a memory changes no person or date and only removes the local photo copies that belong to it.
- Records, associations and local photos survive process death, reboot, app update, backup and restore.
- Deletion and import conflicts are explicit and recoverable.

### Privacy and integrations
- Manual use requires no Android Contacts permission and no Microsoft account.
- Contacts permission is requested only for an explicit import; OneDrive is read only after the player connects it.
- OneDrive is read-only: no write, move, rename or delete path exists, and disconnecting never touches a file there.
- OneDrive tokens are sealed, never logged, never in a LINK export or backup, and removed on disconnect or revoke.
- No LINK record is uploaded to Microsoft; the only network read is the OneDrive photo read the player asks for.
- There is no face recognition, automatic tagging or inferred identity, relationship or quality from photos or contacts.
- No call/SMS/social monitoring, scraping, automatic messaging or cloud transmission beyond that OneDrive read exists.
- Notes, contact details, memories, captions and photos never reach WORLD, the launcher or progression events.
- Imported fields retain provenance and cannot silently overwrite user-curated fields.
- Export and deletion cover LINK data, including local photos, and respect cross-module ownership; OneDrive originals are never deleted by LINK.

### Pokémon progression
- Postcard Trail and Friend Safari are optional and can be disabled without disabling LINK.
- Only distinct eligible follow-up completions contribute to Postcard Trail and only a Safari visit pays a Safari catch.
- Memories, eras, photos and recorded meetups pay nothing until the memory/milestone source is specified in the ecosystem and economy.
- Person teams are not built until the passive-EXP and memory/milestone numbers are specified; the placement, the capacity of six and the Me exception are decided in `../FEATURES/POKEMON_ECOSYSTEM.md` 2.2.2A.
- Contact creation, imports, notes, dates, photo browsing and passive interaction never contribute.
- Reopening, editing, migration, duplicate imports or PLAN linkage cannot double-pay.
- No streak loss, decay, expiring reward, relationship score or quota on progress exists; the anti-farming caps on payments in 9.5 are the only limits.
- Recruitment and rewards use the shared collection and reward ledger.

### Accessibility and presentation
- Shared PMD dialogue-box language is consistent without excessive framing.
- The three browsing modes and photo-first layouts remain usable at large text and with TalkBack, with captions and descriptions reachable without the image.
- TalkBack, dynamic text, contrast, touch targets, reduced motion and dark mode pass review.
- Contact lists, memory lists and photo galleries remain responsive with large local archives.

---

## 14. Documentation alignment

This document is the single detailed requirements source for LINK. Product-wide rules: `../PRODUCT_BIBLE.md`; delivery order: `../IMPLEMENTATION_GUIDE.md`; PLAN conversion and calendar events: `PLAN.md`; PLAN memory links: the in-app integration layer (`app.pokeplan.app.integrations`) and `PLAN.md` 8.1A; BASE may reference a LINK-owned service contact: `BASE.md`; shared reminders and permissions, including the OneDrive account, scopes and token rules: `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` (reminder section 9; in-app integrations section 8.1A; OneDrive photos section 8.2A); shared persistence, encryption boundary, export, backup and migration (including photo files): `../ARCHITECTURE/DATA_AND_MIGRATIONS.md`; visual language: `../FEATURES/DESIGN_SYSTEM.md`; Pokémon and economy, including the person-team placement (2.2.2A) and the memory/milestone progression to be specified: `../FEATURES/POKEMON_ECOSYSTEM.md` and `docs/economy.md`.

## 15. As built: the manual slice

*Compiled for Android; not yet run on a device.*

A page with an overview on top and, behind the header's Sections key, five places (People, Memories, Dates, Follow-ups, Settings). The archive direction consolidates Dates and Follow-ups into one **Reminders** place (specified, not built, 2.4); today they are still two places. **Overview**: only what needs attention (follow-ups that are due, with a tick; dates in the next 14 days), quick-add, and one search over people, notes, dates and follow-ups. **People** (2.1, 2.2): a directory that starts sorted by group (owner, 25 November 2026), with name, recent and My order still available (never ranked; By group shows a heading per group and "No group" last), filters for Pinned and main category (group chips filter by group), a person needs only a name, a profile with Overview, Profile (2.6), Notes (dated lines, newest first) and Follow-ups, duplicate suggestions by name, phone digits or email (suggested, never merged), and a delete (with Undo) that asks what to do with their dates and follow-ups. The directory and search also find a person by phone (however spaced), email and address, and show 40 people at a time. **Dates** (2.4): birthday, anniversary, milestone or custom, day and month with an optional year, every year or once, an optional person; the next time comes with "turns N" when the year is known. **29 February is observed on 28 February in a year without one** (`LinkRules.observed`). **Follow-ups** (2.4): title only, optional person, day and note; Open and History; done, cancelled (shows its day), reopened. A late one is just "Due since…", no escalation. **Make it a Plan task** (on the follow-up's options): PLAN owns the task and its reward, Link shows "In Plan" with no tick, and "Stop linking to Plan" lets it go; a follow-up PLAN finished is reopened in PLAN (Link follows its task both ways, unpaid); "due" counts on Home and Modules leave out follow-ups Plan owns. **Settings**: Postcard Trail on or off, export and erase by kind; a person gets a new category from their own form (the archive direction narrows categories to the predefined set, 2.1). **Main category, groups, My order and the PLAN integration (0.240.0 and 0.243.0, owner decisions of 24 November 2026; JVM tests only, not run on a device):** a person has one optional main category and is in any number of groups (2.1; made, renamed and deleted in People; deleting one deletes nobody); `Person.category` lives in the person's JSON, so there is no schema step, and `LinkRules.STARTING_CATEGORIES` is Family, Friends, Work, School and Services. The 0.240.0 build had turned the category into groups; on first open this version gives the category back once to people a group had replaced it for (`LinkRules.withCategoriesBack`, `LinkSettings.categoriesRestored`: a person with no category who is in exactly one group named like an old category word gets that word back, and every group stays), and People adds By group beside name, recent and My order (move up or down, Me stays first; never a score, never pays): each person once, under their first group's heading in the player's group order, people in no group last under "No group". The calendar overlay and event People links are in 2.4 and live in `IntegrationStore` (`app.pokeplan.app.integrations`), not in `Person`. Open: groups import from phone contacts, showing linked people on the event in the calendar rows.

**Memories and Eras (0.244.0, core; chapter order 0.244.1).** Memories and Chapters are a place of their own in LINK's Sections menu, between People and Dates ("Memories", with a count or "add one"). Behind one row of pills, **Timeline** lists memories newest first with a year heading per group and no known year last under "No year", each row showing the date, place and people; tapping a row edits it in place with Delete and Undo, and "Add a memory" opens the form (title, note, a Year/Month/Day editor with "Around then", place, time, and People/Chapters chips). **Chapters** lists the eras newest first by their latest date (an undated chapter last) with their range and memory count, opens one to its memories, and adds or edits an era with the same date editor, a Save, and Delete with Undo; deleting an era keeps its memories. An **"On this day"** box appears at the top of Memories when earlier-year memories share today's month and day. A person's Overview lists up to four of their memories and an "Add a memory" that preselects that person. **Split dates:** a `PartialDate` has an optional year, month and day plus an approximate flag; a month needs its year, a day needs its month, the year range and leap days are checked, a blank date is unknown, and the labels are "14 Mar 2024", "Mar 2024", "2024", "Around 2024", "Unknown date", and an era's "2020 - 2024", "2020 - now", "Until 2024" or "No dates". Deleting a person takes them out of the memories that name them and leaves the memory; the last recorded meetup is the latest memory with a full date (`LinkRules.metUpOf`; Reminders shows it when REP-087 is built). **Export and erase:** a new "Memories and eras" kind (`LinkKinds.MEMORIES`) exports and erases the two lists; erasing people removes them from memories, and erasing memories leaves people. **Storage:** the records are JSON fields on `LinkData` and ride in the existing `link_state` row like settings, so there is no new table and no schema step. **Not built at this point:** person connections, PLAN event-memory links, unified search over memories and the Reminders consolidation; none of them pays, and nothing here changes the reward boundary. JVM tests only, not run on a device.

**Photos and Gallery (0.245.0).** A memory keeps up to 9 photos, each with a caption of up to 200 characters, added with the platform picture picker (the Android Photo Picker on Android; PNG, JPEG or WebP, downscaled to fit). The form shows them as 96 dp thumbnails with Caption and Remove (Undo), and a new memory collects the picked photos and attaches them when Save makes it; list rows say "1 photo" or "N photos". The photo keys are Add a photo (the single system picker), Add photos (Android's multi-select gallery picker) and From files (the system file picker, OpenMultipleDocuments, for any documents provider the phone has including a cloud app's folder: no account or sign-in inside LINK, and no extra permission); every picture follows the same 9-photo, PNG/JPEG/WebP, 1,400,000-base64-character and 200-character-caption rules as the single picker. The Memories pills are **Timeline | Chapters | Gallery**: Gallery is a grid of 128 dp tiles, newest added first, each labelled with its memory's title and its caption or date, and tapping a tile opens the full-size picture with its caption and Close. A person's Overview adds a "Photos" strip of up to 8 thumbnails from their memories, opening the same viewer. A photo is a `MemoryPhoto` record in `LinkData.photos` (standard base64 PNG, JPEG or WebP, no `data:` prefix, at most 1,400,000 characters, the same budget as MINT's receive QR) and rides in the `link_state` row as JSON, so there is no new table and no schema step. The "Memories and eras" export/erase kind includes the photos; deleting a memory deletes its photos, erasing memories erases them, and erasing people leaves them. No share or brightness control yet, and the OneDrive source is a separate place (0.247.0). JVM tests only, not run on a device.

**OneDrive (Android, 0.247.0).** A OneDrive place in LINK's Sections menu (between Follow-ups and Settings) exists only where `AppModel.oneDrive` is set; desktop and web have no source, and the place and its Jump-to entry are hidden. The connection is OAuth 2.0 authorization code + PKCE (S256, a 64-byte verifier and a random state) in the phone's browser to `https://login.microsoftonline.com/common/oauth2/v2.0/authorize`, redirecting to `pokeplan-onedrive://auth` (scheme `pokeplan-onedrive`, host `auth`, handled on cold start in `MainActivity.onCreate` and in `onNewIntent`); the token endpoints and Graph are HTTPS `HttpsURLConnection` calls with no new library, and the scope is exactly `Files.Read offline_access`. The client id comes from the `onedriveClientId` Gradle property or `ONEDRIVE_CLIENT_ID` into `BuildConfig.ONEDRIVE_CLIENT_ID` (empty means "OneDrive isn't set up on this build."); the release workflow and the `apk`-labelled CI build pass the `ONEDRIVE_CLIENT_ID` secret. Access, refresh and expiry are sealed with an Android Keystore AES-256-GCM key (alias `pokeplan-onedrive`) into `filesDir/onedrive-tokens.bin` by temporary write and rename, never in the save, an export, a backup or a log; disconnect deletes the file, a refresh is on demand and rotates the token, and a stale sign-in asks to connect again. The place shows Connect / Connect again / Disconnect and the states (not set up, disconnected, connecting, connected, needs sign-in, unavailable) with a short Microsoft/Graph message on failure. The folder browser shows folders first then photos by name, with Up back to the root, folder item counts and 112 dp thumbnails (fetched once per tile, at most 2 MB); tapping a photo opens a panel that picks one of the player's memories and Add to this memory downloads it (at most 20 MB, refused over), base64-encodes it and adds it as a normal memory photo, so it is exported, backed up and erased with the rest of LINK while the OneDrive original is untouched; the added copy obeys the per-memory photo limits (9 photos, 1,400,000 base64 characters, a 200-character caption). All Graph calls are read-only GETs (a folder listing at most 4 MB); there is no write path, no background scan and no folder watch. Not built: multi-select, cache management and clearing, offline copies beyond the added local photos, sharing and brightness controls, the Microsoft provider review and a device run. **Owner set-up:** register a Microsoft Entra app (public client, personal accounts supported, redirect URI `pokeplan-onedrive://auth`, delegated `Files.Read`), add the `ONEDRIVE_CLIENT_ID` repository secret, or pass `-PonedriveClientId=…` or `ONEDRIVE_CLIENT_ID` when building locally. JVM tests only and `:androidApp:compileDebugKotlin`; not run on a device.

**Postcard Trail (section 9)** *(numbers are *(tune)* starting values; `docs/economy.md`)*:
- **On by default** (every acquisition method is), off in Settings without touching a record. Only one thing pays: a **follow-up finished** (2 Rare Candies; at most 3 paid a day). Adding or importing people, notes, dates, memories, tags and favourites, editing, rescheduling, cancelling and reopening never pay.
- **Idempotent by follow-up.** The event id is `link:followup:<follow-up id>`, the game remembers it (`AppData.linkPaid`, newest 1000) and the follow-up keeps `paid`, so reopening and finishing again, a restore or a replay pays once. Nothing is clawed back by a reopen.
- **What leaves LINK** is exactly `LinkEvent`: id, module ("link"), an opaque `ref`, the broad kind (`followup_completed`), the time and the policy version. No name, number, email, organization, tag, note, date, memory, caption, photo or follow-up title may reach the event, the shared save, the reward log or the shared tables.
- A payment is candies, a little friendship for Link's lead, a trainer point and a point on the track; **3 points (three finished follow-ups) find a Postcard Egg** from the **Link Region** (`tools/species/regions/link.txt`) into Link's party. Link's party perks (Candy finder: Normal, Flying, Water, Grass, Bug, Ground; Friendship gain: Fairy, Fighting, Electric, Psychic, Fire, Dragon; capped at +25%) only change what Postcard Trail pays.
- **First Pokémon** (POKEMON_ECOSYSTEM.md 10.2.1): the first person, date or follow-up gives one Common egg of the Link Region that hatches into Link's party, leading it, once per save. A new Pokémon arrives as "Postcard Trail delivered…" in a scene of its own (the route).

**Data (section 4).** `link_*` tables: `link_person`, `link_note`, `link_date`, `link_followup` (each row `key, position, day, json`) and `link_state` (settings); `LinkTables` reads, exports or erases one family alone. Link is in the daily backup and the backup file; a backup made before Link leaves Link alone. Not sealed (no owner decision), like Base. Memories, eras and their photos ride in the `link_state` row as JSON like settings, so this adds no table and no migration; a dedicated photo store/cache and the backup archive for large photo collections remain open with the rest of REP-083. The OneDrive tokens are not in this save at all: they live in a sealed file outside it (6.2, 7).

**Contacts import:** Android only (`ContactSource`, null elsewhere so the button is not offered; `AndroidContacts` reads names, phones, emails, companies and birthdays in one query). People → "Import contacts" says what is read, then asks `READ_CONTACTS`; denied, manual use is untouched. A pick list (search, 40 at a time, nothing preselected) marks rows New, Already in Link (same `Person.sourceId`) or Maybe already in Link (name, a phone or an email; starts unselected, and when picked fills only the matched person's empty fields). A reminder is chosen (No, On the day, 1, 3 or a week before; no default), then "Import N" makes one save, one result line and Undo. A birthday becomes a Birthday date unless the person has one; a second import changes nothing; empty phone, email and organization are filled, nothing is overwritten. Contacts with no name are counted and shown. Nothing is paid and nothing reaches `LinkEvent`. **Reminder in one step:** the Add-a-date form has "Remind me" keys, and the first reminder turns Link's reminders on. Known gap: the notification permission is asked only when a reminder is within 4 days.

**Profile, slambook and birth details (section 2.6):** People → "Set up my profile" makes the Me card; every profile has a Profile tab (About, Mind, Stars) after Overview, so Notes is tab 3 and Follow-ups tab 4. `Person.isMe` and `Person.profile` live in the person's JSON (no schema step); `link-core`: `Profile`, `ProfileKit`, `Insights`, `Sky` (natal chart), `BaZi` and `BaZiTables` (Four Pillars, zodiac, Kua, Life Path), `Birthplaces`, `LoreWestern`, `LoreChinese`, `LorePersonality`; `LinkStore.createMe`, `updateProfile`, `setBirthday`. Verified against `ephem` (planets, Moon, ascendant) and `lunar_python` (pillars, New Year) in `BirthChartTest`. The birth date is the person's Birthday date. Nothing is paid and nothing reaches `LinkEvent`. The slambook question sets per category are specified, not built; connections between people are built (0.267.0, 2.2).

**A screen for each person, a profile card and Friend Safari (0.225.0), as built:** a person opens on a screen of their own inside People (header "Profile", or "My profile" for the Me card, back to the directory), no longer an accordion under their row; a search hit, a new person and "Set up my profile" open it too. It starts with a **profile card**: the Pokémon the player chose as their picture in the app's ordinary portrait frame (a Friend Ball when none; "Choose" or "Change" opens a picker of the Pokémon the player has caught, or a search of every species), the whole name wrapped, role and organization, and tags for Me, the relationship word and Pinned. Then the dialogue box of tabs (Overview, Profile in two rows, Notes, Follow-ups), then the **Friend Safari** box (9.5). `Person.avatar` (a species id, 0 for none) lives in the person's JSON, so there is no schema step; the picture is cosmetic and feeds nothing. `LinkSettings.safari` (on by default) is the Safari's own switch. Friend Safari: `SafariEvent`, `SafariRules` and `RewardEngine.safariVisit` in pokemon-core, `GameStore.safari` and `LinkStore.visitSafari`; `RewardRules.VERSION` 33. The archive direction keeps Friend Safari and the encounter mechanics exactly as they are (9.5).

`Person.sourceId` (the contact's lookup key, default empty) lives in the person's JSON, so there is no schema step.

**Specified next, not built (archive direction, owner, 25 November 2026).** The Memories and Eras core is built (0.244.0), local photos and the Gallery are built (0.245.0) and OneDrive's connection and first photo flow are built (0.247.0); no record, store or screen exists yet for the rest, and none is a reward input today:
- **OneDrive, the rest:** multi-select, cache management/clear and offline copies, the Microsoft provider review and a run on a device (6.2).
- **The profile reorganisation** (2.2; person connections themselves are built, 0.267.0; connections in search and the export UI are open).
- **Per-person Pokémon teams:** up to six Pokémon per person, passive EXP and memory/milestone progression; the placement and the Me exception are in `../FEATURES/POKEMON_ECOSYSTEM.md` 2.2.2A, and the build waits on the economy numbers (9.6).
- **Reminders consolidation:** one Reminders place for dates, follow-ups and the last recorded meetup, the upcoming dashboard and the date widgets (2.4).
- **PLAN memory linking (built 0.263.0):** Save as Memory after a scheduled event and attaching an existing memory to an event (6.3).

Nothing above changes Postcard Trail, Friend Safari, the reward-event boundary or the existing `link_*` tables.

## Shared Pokémon ecosystem contract

LINK's acquisition identity is **Postcard Trail**, based on distinct eligible follow-up completions. LINK owns follow-up eligibility; the shared Pokémon ecosystem owns canonical reward settlement, Egg/owned-Pokémon records, Pokédex and inventory. LINK has an independent party referencing shared owned Pokémon IDs. If a follow-up is converted into a PLAN task, one canonical completion event and one reward owner must be preserved. The owner direction for per-person Pokémon teams, their passive EXP and the extra progression through memories and milestones is specified, not built: the place, the capacity of six and the Me exception (LINK's party is the Me entry's team) are decided in `../FEATURES/POKEMON_ECOSYSTEM.md` 2.2.2A, the passive EXP and milestone numbers are to be specified in `docs/economy.md` before build (9.6), and no shipped behaviour changes until then. Contact identifiers, personal notes, memory text, captions, photos, message content and social activity are excluded from reward events. See `../FEATURES/POKEMON_ECOSYSTEM.md`.

---

## Testing reference

LINK record ownership, follow-up and reminder workflows, memory and era dates and associations, photo and OneDrive failure behaviour, interruption recovery, duplicate-action prevention and privacy boundaries must be tested under [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md).

**People list as cards and a random picture picker (owner, 23 November 2026).** The directory shows each person as a small card of their own (their Pokémon picture, the name, the relationship word and organization, a star when pinned) instead of a plain row; a person with no picture shows the Friend Ball. The picture picker opens on a **random draw of Pokémon from the whole Dex** (36 at a time) with a Shuffle key, and "My Pokémon" switches to the ones the player has caught; searching by name still lists matches. Cosmetic only.

**Friend Safari asks before it visits (owner, 23 November 2026).** A visit uses one of the day's five catches, so the Visit key now asks "Use one of today's N visits? It catches one Pokémon." with Visit now and Not now; nothing visits on its own when PokéLife opens (no code path does: `LinkStore.visitSafari` has the one caller, the key).

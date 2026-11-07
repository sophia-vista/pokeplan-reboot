# REP-014, sixth slice: LINK, manual first (0.145.0)

**Status:** built and tested on the JVM, compiled for desktop and web (the Android app is compiled by the gate); **not run on a device**. No reminder, contact import or PLAN link was started.
**Owner brief (8 November 2026):** "continue with next chunk of items" (the backlog's next item after HOME: LINK). Built on the HOME branch, which was not yet merged, so this slice stacks on it (schema 10, rules version 20, version 0.145.0); the HOME branch first took in `main` (the Mint follow-ups, version 0.142.0), which made HOME 0.143.0.

## 1. What was chosen from the spec, and why
`MODULES/LINK.md` section 12 puts the domain and persistence first, then manual contacts, notes and search, important dates, follow-ups and reminders, optional device-contact import and Postcard Trail. Built: the domain and persistence, people and profiles, notes, search, important dates, follow-ups and Postcard Trail. Not built: reminders (they need a source adapter in the shared scheduler, a separate piece), device-contact import (needs the Contacts permission and a preview flow, section 6.1) and the PLAN conversion (2.4).

## 2. Decisions I made that the owner may reverse
1. *Postcard Trail on by default* (every acquisition method starts on), off in Settings without touching a record.
2. *Numbers:* a follow-up pays 2 Rare Candies and 1 track point, 3 points fill the track (LINK.md 9.3 asks for three), at most 3 paid a day so making and ticking small items is never a way to earn (9.4). Slower than Home on purpose: a follow-up is a self-chosen reminder, not a chore.
3. *29 February is observed on 28 February* in a year without one (2.3 asks for a documented, predictable rule).
4. *A reopened follow-up keeps its `paid` flag*, so finishing it again pays nothing; cancelling never pays and a cancelled follow-up is reopened before it can be finished.
5. *Deleting a person* deletes their notes and asks, with two switches, whether their dates and follow-ups are kept (the person taken off) or deleted (section 7).
6. *Duplicates are suggested, never merged,* by name or preferred name, phone digits (at least 7) or email.
7. *The directory sorts by name or by recent change only;* "Pinned" is a filter for finding, not a ranking. No "last contacted", no meter, no frequency.
8. *Not encrypted.* Like Home (no owner decision for LINK); it can be sealed the same way as Mint later.
9. *The Link Region is released with the module* (`Region.LINK`), so the Pokédex gains a Link tab for everyone.

## 3. Tests
`LinkRulesTest` (dates, the leap-day rule, follow-up state and order, payment limits, search, duplicates, directory), `LinkStoreTest` (payment once, the event and the shared save hold no private word, the track and the egg, tutorial, deletion choices, export and erase), `LinkTablesTest` (activation, round trip, changed rows only, one kind erased alone, backup), `LinkRewardTest` (the golden vector is regenerated for version 20), screenshots of the menu, the empty menu with the tutorial, dark, every section at 130% text and the arrival scene.

## 4. Not done / next
Device checks; reminders for dates and follow-ups through the shared scheduler; device-contact import; PLAN conversion; sealing LINK if the owner asks; then the typed repositories per module (`DATA_AND_MIGRATIONS.md` section 7.3.0).

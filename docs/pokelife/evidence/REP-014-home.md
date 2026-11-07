# REP-014, fifth slice: HOME, manual first (0.142.0)

**Status:** built and tested on the JVM, compiled for desktop, web and Android; **not run on a device**. No reminder, attachment, import or PLAN link was started.
**Owner brief (8 November 2026):** "work on next pending item" (the backlog's next item after MINT: HOME, then LINK).

## 1. What was chosen from the spec, and why
`MODULES/HOME.md` section 13 puts chores, supplies, basic assets and maintenance, the overview and Habitat Discovery in Phase 1, and warranties, reference and search in Phase 2. Built: all of Phase 1 except reminders, and from Phase 2 warranties, the reference library and search. Not built, documented in `MODULES/HOME.md` section 16: reminders, attachments, PLAN links, widgets, barcode, import.

## 2. Decisions I made that the owner may reverse
1. *Durable inventory is the asset.* HOME.md 4.4 says a durable item may be an asset "avoiding duplicate asset identity"; there is one record for both.
2. *No backlog.* A repeating chore shows its latest occurrence up to today only; older missed ones are not listed (4.2).
3. *Undo does not claw back; payment is once per occurrence* (9.2 "idempotent reversal policy"). Caps: 5 chores and 2 pieces of maintenance a day *(tune)* so a job split into many cannot farm it.
4. *Habitat Discovery on by default* (every method starts on), off in Settings.
5. *Numbers:* chore 2 candies, maintenance 4, meter 5 points (1 and 2). Faster than Care and Heal, about Plan's pace, because chores are frequent and small.
6. *Not encrypted.* Mint's encryption was a specific owner decision; HOME follows the shared policy and uses Heal's plain tables. It can be sealed the same way later.
7. *Quantity in thousandths of a unit* (exact, parsed from "1.5" or "1,5").
8. *Starting areas* are offered once with stable ids and can be renamed, archived or deleted.

## 3. Tests
`HomeRulesTest` (58, schedules checked against a brute-force scan), `HomeStoreTest` (29: payment once, undo, caps, privacy of the event, quantity rules, export and erase), `HomeTablesTest` (10), `HomeRewardTest` (golden vector regenerated for version 19).

## 4. Not done / next
Device checks; reminders through the shared scheduler; attachments; PLAN links; LINK, then the typed repositories per module.

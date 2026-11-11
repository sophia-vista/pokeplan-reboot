# LINK audit (0.171.0): findings, comparison with relationship-manager apps, and what is left

**Status:** what is listed as fixed is built and tested on the JVM (`LinkRulesTest`, `LinkStoreTest`) and compiled for Android and the web; **not run on a device**. No reward number changed. The comparison with other apps is from the author's knowledge of Android Contacts, Monica, Dex, Clay and Covve, not from looking them up in this session; check it before relying on a detail. The open items are the owner's to decide; none was changed.

## 1. Fixed in 0.171.0 (PR 298)
- Directory and search matched name, alias, organization, role, category and tags only. They now also match phone (digits, however spaced, three or more), email and address (LINK.md 2.1 "user-entered searchable detail").
- A profile could not add a date (LINK.md 3.3 allows it). It has "Add a date" now, with the person preset.
- Deleting a person (and their notes) was permanent. It offers Undo.
- A search hit that opened Notes set the first tab of every profile opened afterwards. Only the jumped person gets it.
- A cancelled follow-up showed today in History. It shows the day it was cancelled.
- A follow-up PLAN finished could be reopened in Link and bounced back at once. Reopen is refused while PLAN owns the task, and `syncFromPlan` also reopens the follow-up when PLAN takes the task back to open. Neither pays.
- Home and the Modules card counted follow-ups handed to Plan as "due"; the Overview did not. All three use `LinkStore.dueNow()`.
- The directory composed every row at once. It shows 40 at a time, and an open profile is always in view.
- Settings → Categories had chips that did nothing. A new category is added from the person's form (`LinkStore.addCategory` is unchanged); the Settings box is gone.

## 2. Compared with relationship-manager apps
**Complete?** Has: people with name, alias, phone, email, organization, role, address, one category, tags and a pin; dated notes; birthdays, anniversaries and other dates with opt-in reminders; follow-ups with an optional person, day and reminder; one search; JSON export and erase; a hand-over to Plan. Missing, in order of impact: device-contact import (specified in 6.1, not built); a "keep in touch every N weeks" cadence; more than one phone or email; photos; vCard or CSV export; a widget; recurring follow-ups. Cadence, interaction timelines and "last contacted" are left out **on purpose** (LINK.md 1.2 and 2.2), and they are the main selling point of Monica, Dex and Clay.

**Necessary?** Core: people, notes, dates, follow-ups, search, reminders. Heavier than a notebook needs: category plus tags plus pin; seven export and erase buttons; a follow-up system beside Plan tasks with a conversion between them. Call / Message / Email buttons only open the dialer (no permission) but sit uneasily with the "not a calling or messaging client" non-goal. Postcard Trail is separate and optional.

**Replaces?** Android Contacts: no (no import, one phone, no photo). Monica, Dex or Clay: no (no import, cadence or timeline). A birthday calendar plus a social to-do list: yes, once the data is in.

**Usable day to day?** Good: a person needs only a name, nothing scores or nags, quick-add is fast. Frictions: every person is typed by hand (the main blocker for more than a couple of dozen people); a birthday reminder takes Settings → master switch → Dates → open the date → pick a lead time, and the per-date chips are hidden until the master switch is on; Home shows only due follow-ups, never an upcoming birthday; the Overview sits below the Pokémon stage, reward box and tutorial card; a date is 12 month chips plus number fields; quick-add of a follow-up cannot take a day (about four taps for "tomorrow").

## 3. Left open (owner's decisions)
1. **Reminders, one opt-in not two.** Picking a lead on a date, or "Remind me on the day" on a follow-up, switches Link's reminders on; the master stays as a pause. It changes a specified opt-in (LINK.md 5), so it needs the owner. Recommended first.
2. **Device-contact import (6.1) and its permission.** The adoption blocker; the next chunk after 1.
3. **Upcoming dates on Home** (REP-022).
4. **LINK is not sealed.** Phones, emails and notes are plain in the database and in the daily backup, while LINK.md 7 says to protect local data and exports with the shared encryption architecture. Needs an owner decision (MINT and HEAL are sealed).
5. **Over-built pieces:** collapse the per-kind export and erase buttons to one export and one erase; the `tag` filter of `LinkRules.directory` has no UI; ask whether Link needs its own follow-ups or whether a Plan task with a person would do.
6. **Menu order:** the Overview and Search above the Pokémon stage.
7. **Entry:** a date picker for dates (a year may be unknown); a day field on follow-up quick-add.
8. **Categories** a user added cannot be removed or renamed.
9. **Plan mirror:** `syncFromPlan` runs only while the Link screen is open, so a follow-up finished in Plan shows as open in Link until Link is opened (counts are unaffected: `dueNow` leaves Plan-owned ones out).
10. **Search:** results are capped at 8 with no "more", and several notes of one person give several rows.

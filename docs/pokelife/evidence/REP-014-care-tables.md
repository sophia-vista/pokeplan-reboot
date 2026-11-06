# REP-014, first slice: CARE on its own tables (0.132.0)

**Status:** built and tested on the JVM and compiled for Android; not yet run on a device.
**Owner decisions:** CARE first, with the CARE spec fixes in the same change (6 November 2026).

## 1. What changed

### Storage (schema 3 to 4)

CARE's records moved out of the `care` row of `live_document` into tables, one family per kind of record so a private kind can be read, exported or erased on its own:

| Table | Holds |
|---|---|
| `care_routine`, `care_routine_completion` | routines; the days each was done, one row each |
| `care_diary_card`, `care_journal_entry` | the two private record types, apart from each other |
| `care_calm_break`, `care_skill_practice` | calm breaks and practised skills |
| `care_visitor`, `care_dismissed_visitor` | visitors (meadow then queue, by `position`) and the ones sent away |
| `care_state` | every single-value field: settings, the visit in progress, the journal draft, skipped prompts, rhythm weeks, the visitor seed, met, and any field a newer build adds |

**Design decision (deviation from "one column per field").** A record table has the columns the app asks by (`key`, `position`, `day`) and the record itself as JSON in `json`. The typed model (`CareData`) stays the one definition of a record, so there is no second field mapping to drift from it, `:data` stays independent of `care-core`, and a field this build does not know is kept instead of dropped. The cost is that SQL cannot filter on a field inside `json`; nothing asks it to yet. When a screen needs such a query, the column is added with a migration.

**How it fits under the rules.** `CareStore` is unchanged in shape: it still loads and saves one `CareData` document through `Storage`. `CareTables` (in `:data`) takes that JSON apart on save and puts it back together on load, and a save writes only the rows that changed (a routine ticked is one `care_routine_completion` row, a draft typed is one `care_state` row). `LiveDocuments` routes `care` through the tables once `moveCareToTables` has run, so reads, the daily backup (`snapshot`), restore (`replaceAll`) and `names` all still see one `care` document. The backup still writes `care.json` as it always did, assembled from the tables.

**Activation** (`LiveDocuments.moveCareToTables`, called by `LiveStore.get` after the live documents are active): in one transaction the document row is split into rows, read back, compared with the original (empty lists and an empty `completions` array are not told apart), the marker `care_store=tables` is set and the old row is kept as `aside:care:tables:<millis>`; any failure rolls everything back and the document row stays the live Care save, tried again at the next start. A fresh install has no document and only gets the marker. After activation nothing writes the row: one authority.

**Rollback.** A build older than 0.132.0 refuses the schema-4 file (`NewerSchemaException`, file untouched). The way back is the newest backup in `filesDir/backups/`, whose `care.json` is the document in the old shape, and the kept-aside row.

### Visitor rules (`MODULES/CARE.md` section 6; numbers in section 6.8)

Removed: the paid-Care prerequisite, one visit a day per visitor, the fixed three actions, the three-day departure, the day-counted progress. Added: arrivals independent of care, a four-slot meadow and a six-place queue, persistent warmth (16 makes a friend), a visit that can be left at any time, a three-hour rest, dismissal that can be undone, and a ready friend that waits when the world's daily limit of friends is reached. Old saves: warm days become 4 progress each, visitors get ids.

## 2. Evidence

- `CareTablesTest` (data, JVM): the 0.123.0 Care fixture moves in and reads back equal; every kind of record round trips and unknown fields survive; a save writes only changed rows (counted with triggers); a fresh install; a move that fails leaves the document as the live save; restore from a snapshot keeps the replaced save aside; the tables survive reopening the file.
- `CareStoreOnTablesTest` (data, JVM): the real `CareStore` runs on the tables through `LiveDocuments`, the file is closed and reopened, and the data is equal field for field; erasing the journal rows leaves the diary and routines.
- `CareStoreTest` (app-core): arrival without care, warmth kept at once, rest, befriending in two or three visits, repeats and bids, the liked spot, the liked berry once, capacity and queue over 60 days, absence changing nothing, dismissal keeping what was earned and asking back, a ready friend that waits, an old save's visitors migrated.
- `verifyCommonMainDataDbMigration` passes with `databases/4.db` committed.

## 3. Not done (needs a device, or later)

- Run on a phone: first start migrates Care, the visitors screen, a restart, the backup folder holds a valid `care.json`. Debug builds' DB check has a new line, "Care tables", that moves a document in, writes a change, reads it back and checks the backup snapshot sees it.
- The rest of REP-008 (restore screen, user-chosen backup, staged import, low storage, kill during write, MINT encryption).
- PLAN onto its own tables next, then the shared Pokémon, Bag and ledger tables as the live authority.
- Spec items not built: returning befriended visitors, Care Play for the buddy and owned Pokémon, the fixed Meadow, light egg inheritance with an origin note.

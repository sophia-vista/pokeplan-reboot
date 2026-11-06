# REP-008: the local data foundation (first slice)

**Evidence for:** `ARCHITECTURE/DATA_AND_MIGRATIONS.md` sections 4, 7, 8, 11 and 12; release gates A and B.  
**Done:** 6 November 2026 in the `:data` module (the renamed `db-spike`; Android and JVM targets; SQLDelight 2.4.1).  
**Status of the evidence:** 20 tests on the JVM with real SQLite files and the five committed 0.123.0 saves; the Android target compiles. **Nothing ran on a device** (the owner deferred device checks until a release, `REP-007-gate-a-review.md` section 5), and **the app does not use the module**: the JSON save is still the only live store, so there is no dual write.

## 1. What exists

| Piece | Where | Proven by |
|---|---|---|
| Schema version 1: owned Pokémon (with IV/EV/level/friendship `CHECK`s), module parties (six slots, lead in slot 1, unique per party), global buddy, Guardians, Dex history, progress, one Bag, one Poké balance, reward events, an append-only ledger, outbox, migration history, import warnings, the legacy sidecar | `data/src/commonMain/sqldelight/.../Data.sq` | `DataStoreTest` |
| Foreign keys on every connection, read back when the store opens (a connection without them is refused); the Android driver sets them in `onConfigure` | `DataStore.open`, `JvmDatabase`, `AndroidDatabase` | `aConnectionWithoutForeignKeysIsRefused`, `theOwnershipRulesHoldBecauseForeignKeysAreOn` |
| `schemaOutputDirectory` set, `databases/1.db` committed, `verifyMigrations` on, and `jvmTest` depends on `verifyCommonMainDataDbMigration`: a schema edit without a numbered migration now fails (checked by adding a column on purpose) | `data/build.gradle.kts` | the build itself |
| Schema versioning in `PRAGMA user_version`: created, migrated step by step in one transaction, or refused when newer (a newer file is not touched) and refused when it has tables but no version | `DataStore.prepareSchema` | `aNewerSchemaIsLeftAloneAndAnOlderOneIsCreatedFresh` |
| One transaction coordinator; repositories for the Pokémon side and the economy (idempotent `settle`, compensating `reverse`, `reconcile` of balances against the ledger; the ledger is append-only even against raw SQL, by trigger) | `DataStore`, `PokemonRepository`, `EconomyRepository` | `settlingTheSameEventTwicePaysOnce`, `aSpendThatCannotBeCoveredChangesNothing`, `aFailureInTheMiddleOfATransactionLeavesNothingBehind`, `aReversalIsACompensatingEntryAndHappensOnce`, `theLedgerCannotBeEditedOrEmptiedEvenByRawSql` |
| `LegacyJsonImporter`: reads `save.json` and `care.json` as text (never writes them), one transaction, identity = SHA-256 of both inputs, idempotent, refuses a different save, self-verifies counts, balances, the Bag, the Dex, party slots, `integrity_check` and `foreign_key_check` before committing | `LegacyJsonImporter` | `LegacyJsonImporterTest` |
| Backup: `VACUUM INTO` plus a version stamp, JVM only | `JvmDatabase.kt` | `aBackupIsAConsistentCopyThatOpensAndPassesTheChecks` |

## 2. How the import maps the 0.123.0 save

- **Shared Pokémon and economy side** goes into the tables: every owned Pokémon field by field (the favourite flag and the origin are columns; any field the importer does not know goes to the Pokémon's `extra_json`), parties, the Guardians, the five Dex sets, egg meter and trainer points.
- **Parties:** each Pokémon with `inParty` goes into the party of its `partyId`, in the order it was listed. The old PLAN buddy becomes slot 1 of PLAN's party and the starting global buddy; the old `careLeadId` becomes slot 1 of CARE's party. A party over six keeps its first six and records a warning for each other member (they stay owned). This is the data half of REP-009.
- **Poké and the Bag:** `coins + WorldData.poke` becomes one opening-balance ledger event (`legacy:<migrationId>:opening-balance`, policy `legacy-0.123.0`) with both sources, the save schema and the migration id in its metadata; the Bag is `PlayerState.inventory` plus `WorldData.items`, summed per item. The engine never runs, so nothing is paid twice, and the reward log is kept (in the sidecar) for display only.
- **Everything else** (tasks, lists, series, habits, goals, focus, settings, quests, GEAR, WORLD without `poke` and `items`, the reward log, and all of `care.json`) is stored as raw JSON in `legacy_sidecar`, one row per legacy field, and a test proves that every top-level field of the fixture is either mapped or in the sidecar. A field of the player record that the importer does not handle goes to `player.<field>`.
- **Not imported on purpose:** the signed-in account and its session (`AppData.account`) and Care's cloud-copy state (`CareData.cloud`); each records a warning.

## 3. Decisions the work settled

1. The importer reads JSON, it does not deserialize into the app's classes. It has no dependency on `app-core`, so nothing can be dropped by a model that does not know a field, and the legacy model can be retired with the cloud path. The tests use `app-core` to compare against what the app itself reads.
2. SQLite on Android 8 is 3.18: the schema uses no upsert, and `VACUUM INTO` (3.27) is not in common code. The Android backup (WAL checkpoint then copy under the single writer) is device work.
3. The module ids are upper case (`PLAN`, `CARE`, ...); the legacy ids `plan` and `care` are mapped by the importer.
4. A manual `BEGIN` over a file-backed JDBC driver is unsound (the driver may hand the next statement another connection); the store uses the driver's own transaction.

## 4. Not done (REP-008 stays Partial)

| Missing | Why / when |
|---|---|
| Wiring the database into the app, the explicit user-facing import, the activation marker and cutover | Needs the device checks first; Gate B |
| Staged-file import with an atomic swap, restoring from a backup, moving a corrupt database aside whole, the low-storage path | File operations that belong with the Android platform layer and need a device; the pure parts (opening, refusing, verifying) are done |
| Android backup snapshot and WAL settings | Device checks |
| Tables for PLAN, CARE, WORLD, GEAR | Each module's refactor (REP-014); until then its fields live in the sidecar |
| `reminder_request`, `attachment`, `external_source` | REP-011 and the HEAL, MINT, HOME and LINK work |
| A reward policy that uses the ledger for live events | REP-010 |


## Device self-check (0.128.0)

The owner chose not to schedule formal device checks ("no need to test, just go", 6 November 2026). So the checks can run themselves without any setup, debug builds carry a second launcher icon, **DB check** (`androidApp/src/debug`, `DeviceSelfCheck` in `:data` androidMain). It runs on a throwaway file, never `pokelife.db` or the save: SQLite version, foreign keys enforced, journal mode, transaction rollback, a refused orphan row, 5000 inserts timed, checkpoint then backup copy then reopen, and a reopen after close. **Nothing has been run on a device yet**: tapping the icon once on the phone is the evidence. Not covered: low storage, the widgets' access to the database, MINT encryption, a kill during a write. The database stays out of the live path until the owner has seen the report and says to cut over.

**Result (6 November 2026, owner's phone, build 217):** every line of the DB check passed. This is the first on-device evidence for the Android driver, foreign keys, journal mode, rollback, the backup copy and reopen. Still unproven: low storage, the widgets' access, MINT encryption, a kill during a write.

## Cutover (0.129.0)

Decision (owner, 6 November 2026: "yes, as one PR"): the database becomes the live store now, in this shape, because the normalized schema has no tables for Plan, Care, World, GEAR or settings and writing them all is the work of REP-014.

- **What is live:** `live_document` (schema v2, migration `1.sqm`, `databases/2.db`) holds two JSON documents, `save` (`AppData`) and `care` (`CareData`), exactly as the app serialises them. Each save is one transaction. This is the single authority once the marker `live_store = database` is set: no dual write.
- **Activation** (`LiveDocuments.activate`, called on every start, a no-op once active): in one transaction copy `save.json` and `care.json` in, read each back and require it byte-equal, set the marker. A failure rolls everything back and the files stay the live store. A fresh install activates with no documents.
- **Rollback copy:** the legacy files are never written after activation. The daily backup (`LiveBackup`, `filesDir/backups/<millis>/save.json, care.json, DONE`, newest 3) uses the same format, so putting its two files back in `filesDir` and installing an older build returns to the JSON app.
- **Corruption** (open or `integrity_check`/`foreign_key_check` fails with a corrupt-file error): the whole database (and `-wal`, `-shm`) is renamed `*.corrupt-<millis>`, never deleted; the newest backup is restored (the marker is set from it), else the legacy files are imported. A database from a newer build is left untouched. Once the database has been live (flag file `live.active`), a failed open does **not** fall back to the stale files: the storage refuses, so nothing is overwritten.
- **Restore:** `LiveBackup.restoreNewest` keeps the documents it replaces as `aside:<name>:<millis>` in the same transaction, so a restore can be undone. There is no screen for it yet.
- **Proven:** 8 new JVM tests (activation once, fresh install, reopen, a failed activation rolls back, a version 1 file migrates, backup due/keep-3/restore/aside, an unfinished backup is never used); DB check on a device covers activation, backup and restore on a throwaway file. **Not yet proven on the owner's phone: the real first-start import of the real save.** The legacy files make that reversible.
- **Not done:** a restore screen, backup to a user-chosen place, encryption, the low-storage path, a kill during a write (SQLite's own journal covers it, untested), desktop and web (they keep JSON), and the module tables (REP-014).

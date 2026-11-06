# REP-007: Gate A review (ownership, transactions and idempotency, rollback)

**Evidence for:** `IMPLEMENTATION_GUIDE.md` section 5, Gate A, and `ARCHITECTURE/DATA_AND_MIGRATIONS.md` sections 3 to 8, 11 to 13.  
**Done:** 6 November 2026, as a document review against the 0.123.0 code, the REP-003 inventory and the REP-006 spike. No product behaviour changes here; the decisions below are the contract REP-008 to REP-010 build to.  
**Status:** Gate A **passes for the data foundation (REP-008) and the shared Pokémon and economy slice (REP-009, REP-010)**. It does not yet pass for the modules that do not exist (HEAL, MINT, HOME, LINK): they have no code to review, and each gets its own ownership check when its specification is implemented.

## 1. Gate A checklist

| Gate A criterion (guide, section 5) | Result | Where it is settled |
|---|---|---|
| All module records have one owner | Met for the eight modules on paper (`DATA_AND_MIGRATIONS.md` section 5). In 0.123.0 code, three records have two writers, listed in section 2 below; REP-008 to REP-010 remove them | Section 2 |
| Shared Pokémon, reminders, integration registry and database services each have one authority | Pokémon and economy: one `data` module (section 2.2). Reminders and integrations: still specification only; REP-011 owns them and nothing in REP-008 touches them | Section 2 |
| Test strategy, migration design, transaction and idempotency model, backup and restore, and rollback are documented | Test strategy and migration design already exist; the transaction model, idempotency keys and rollback are made concrete here | Sections 3 and 4 |
| No module implementation starts on a competing store or shadow entity model | Holds: REP-008 adds no second writer. The database is built and tested, and the JSON save stays the only live store until Gate B (section 4) | Section 4 |

## 2. Ownership contracts

### 2.1 Where 0.123.0 has two writers

| Record | Writers today | Single owner after the work | Backlog |
|---|---|---|---|
| Poké balance | `PlayerState.coins` (PLAN) and `WorldData.poke` (WORLD) | Shared economy: one `currency_balance` row, changed only through the ledger | REP-010 |
| Bag | `PlayerState.inventory` and `WorldData.items` (a staging inventory) | Shared economy: one `bag_stack` table; `WorldData.items` is reconciled into it | REP-010 (the staging reconciliation is recorded there; the tables exist from REP-008) |
| Party membership | `OwnedPokemon.inParty` and `partyId` on the Pokémon itself, plus `PlayerState.careLeadId` and `buddyId` (PLAN's buddy) | `module_party` rows (module, slot, owned id) and one `buddy_selection` row; the Pokémon carries no party flag | REP-009 |
| A CARE completion and its reward | `CareStore` saves `care.json`, then calls `World`, which saves `save.json` (two commits) | One transaction: the completion in CARE's tables and the reward in the shared ledger | After REP-008, with the CARE migration (REP-014); the interim is documented in section 3.5 |

### 2.2 Who may write what (the `data` module)

One Gradle module, `:data` (the renamed `db-spike`, Android and JVM targets), holds the database and the repositories. Its rules:

1. **Table groups have an owner and a repository interface.** A caller outside the owner may call the owner's interface or the shared reward service; it never receives a table handle. `SpikeDb`-style generated queries stay `internal` to `:data`.
2. **Shared tables** (`owned_pokemon`, `pokemon_history`, `dex_history`, `module_party`, `buddy_selection`, `bag_stack`, `currency_balance`, `reward_event`, `reward_ledger_entry`, `outbox_event`, `processed_event`, `migration_history`, `app_meta`) are written only by the **shared Pokémon and economy service**. A module asks for an acquisition, a grant or a spend; it does not insert `owned_pokemon` or change a balance (`POKEMON_ECOSYSTEM.md` sections 11 and 12).
3. **Module tables** (PLAN, CARE, WORLD, GEAR and later modules) are written only by that module's repository. Until a module is migrated, its whole legacy document lives in the **legacy sidecar** table, keyed by legacy path, and is written only by the importer (section 3.6). There is no half-migrated state in which a field is both a column and in the sidecar.
4. **A module repository calls the transaction coordinator, never `SqlDriver`.** The coordinator hands the callback the repositories it needs; a service called inside it joins the open transaction.
5. **Projections are views or rebuilt queries** (the Dex counts, the rank, the party cards). The data layer keeps no second editable copy.

### 2.3 Android entry points

The widgets, the reminder receivers and the notification actions all run in the app's single process: the manifest sets no `android:process` on any receiver, service or provider (checked in `androidApp/src/main/AndroidManifest.xml`). So the spec's single-writer rule (`DATA_AND_MIGRATIONS.md` section 4.3) is met without multi-process SQLite: they call the same repositories through one process-wide database handle. A change that moves any of them into another process must first re-open this decision.

## 3. Transaction and idempotency model

### 3.1 One coordinator, one writer

`Transactor.run { ... }` wraps SQLDelight's `transactionWithResult`. Exactly one database connection exists per process, and `PRAGMA foreign_keys = ON` is set when it is opened and **verified by reading it back** (the spike showed the rules fail silently without it: `REP-006` finding 1). The connection factory fails to open if the read-back is not `1`. WAL and the durability setting are a device question and wait for the device checks (section 5).

### 3.2 Nothing is reported before it commits

A repository method returns the committed result. In-memory observable state is updated from that result only. A failure rolls the whole transaction back and surfaces as an error value; the UI is not told "saved". This closes the `SavedDocument.write()` window (the value is set before `storage.save()`).

### 3.3 Reward-bearing operations

Follow `DATA_AND_MIGRATIONS.md` section 8.2 and `POKEMON_ECOSYSTEM.md` section 12.3 unchanged. What REP-008 pins down:

| Decision | Rule |
|---|---|
| Event identity | `reward_event.event_id` is the primary key and equals the idempotency key; the key is `<sourceModule>:<sourceRecordId>:<eventKind>` plus `:<occurrenceId>` where the product allows a new reward for a new occurrence (a recurring task's date, a routine's day). Never a timestamp, a title or a random id |
| A retry | An insert of an existing `event_id` is detected before any side effect; the repository returns the recorded settlement and changes nothing |
| Ledger | `reward_ledger_entry` is append-only: the repository exposes no update or delete. A reversal is a new entry whose `reverses_entry_id` points at the original |
| Balances | `currency_balance` and `bag_stack.quantity` change in the same transaction as the ledger entry, with `CHECK (>= 0)`. A spend that would go below zero fails the whole transaction. A debug check recomputes both from the ledger and compares |
| Policy version | `reward_event.policy_version` stores `RewardRules.VERSION` for the event. Events written by the importer use the policy `legacy-0.123.0` and never re-run the engine |
| Outbox | Inserted in the same transaction (`outbox_event`); consumers record `processed_event(consumer, event_id)` with a unique key. A failed consumer never rolls back the user action |

### 3.4 What undo means

Undoing a real-life action before settlement removes it without a reward. After settlement it appends a compensating ledger event; it never reuses the original key and never lets the repeated action pay twice (`POKEMON_ECOSYSTEM.md` section 12.5). 0.123.0's 10-second undo window for a completed task (`AppData.pending`) stays a PLAN rule, stored with the task.

### 3.5 CARE and the shared reward, before and after

CARE's two commits (section 2.1) are closed in two steps. REP-008 gives the shared reward service an idempotent entry point; the existing `CareStore` is **not** changed in REP-008 (it keeps `care.json`). When CARE moves onto the database (REP-014), its completion and its reward become one transaction with the key `care:<routineId>:completion:<date>`. Until then the legacy window stays and is recorded as a known risk, not hidden: a crash between the two saves can leave a completion unpaid, which the next open of the app already tolerates.

### 3.6 Import (the one-time legacy migration)

The importer is idempotent by construction:

1. It reads `save.json` and `care.json` **as text** and never writes to them.
2. It parses into typed intermediate records, resolving cross-file references after both parse (`DATA_AND_MIGRATIONS.md` section 12.4). A missing optional reference is a warning row, not an invented record.
3. It writes everything in **one transaction** into a staged database file: shared rows, the opening-balance ledger entry, and the sidecar rows for every legacy field that has no table yet (raw JSON, keyed by legacy path and record id, never dropped).
4. It records `migration_history(id, source_checksum, schema_from, schema_to, status, counts)`. The id is a hash of the two inputs, so the same inputs cannot import twice; a changed save is a different migration and is rejected until the marker is cleared deliberately.
5. It validates before it reports success: owned Pokémon count, Poké (coins + World Poké, section 3.7), per item quantity, candy totals, Dex sets, party rosters and buddy against the source, and `PRAGMA foreign_key_check` and `integrity_check` clean.
6. Reward history (`rewardLog`) goes to the sidecar for display. It never creates `reward_event` rows that pay, and it never re-credits a balance.

### 3.7 Currency migration

`newPoké = legacyCoins + legacyWorldPoké` (`POKEMON_ECOSYSTEM.md` section 11.2). The importer writes **one** ledger entry, `legacy:<migrationId>:opening-balance:poke`, of that sum, with both source values, the source save schema and the migration id in the entry's metadata. Absent values count as zero. The economy rules themselves (what each source pays after migration, `RewardRules.VERSION`) change in REP-010, not in the importer.

## 4. Rollback strategy

| Stage | Live store | Rollback |
|---|---|---|
| REP-008 shipped (importer and repositories exist) | **JSON, unchanged.** The database file is created only by an explicit import call and by tests; the app does not open it | Nothing to roll back: delete the staged database; the JSON was never touched |
| Import run in a build (Gate B rehearsal) | JSON | The import writes a staged file and renames it only after the validation in section 3.6 passes; any failure leaves the previous state, and the staged file is removed. The JSON files are read-only to the importer |
| Cutover (Gate B passes) | Database | Keep `save.json` and `care.json` untouched and read-only for the retention window. The marker `migration_history.status = 'complete'` is the only thing that switches the app to the database. To roll back: ship the previous build and delete the marker (the JSON is still current as of the cutover); changes made after cutover are not in the JSON, which is why a verified backup is taken at cutover and the window is long |
| After the retention window | Database | Retire the JSON code and files (REP-012 retires the cloud path at the same gate) |

Rules that follow:

1. **No dual writes**, ever. Before cutover only the JSON is written; after it only the database. There is no mode that writes both.
2. A **newer schema opened by an older build** fails to open with a message and writes nothing (`DATA_AND_MIGRATIONS.md` section 11.2).
3. A **corrupt database** is moved aside whole (database, WAL and SHM) before anything is created, never replaced by a blank one (section 13.2).
4. Every numbered schema migration is checked by SQLDelight's migration verification against the committed previous-version `.db` file (REP-008 turns this on; in the spike it checked nothing).

## 5. Decisions and open items for the owner

### Decided in this review

- **Technology:** SQLDelight on SQLite stands (`DATA_AND_MIGRATIONS.md` section 4.1); `db-spike` becomes `:data`. Room is not introduced.
- **Desktop and web** keep the JSON `GameStore` as development conveniences and never open the database (Bible, section 3).
- **IDs:** every legacy id is kept as the primary key (`TEXT`); no id is regenerated.
- **Time:** instants are epoch milliseconds; legacy local dates stay as ISO text inside the sidecar until their module is migrated, so no local date is read as UTC.

### Owner decisions recorded today

- **On-device database checks:** deferred until a release is planned, by the owner's choice. The consequence is stated plainly so it is not forgotten: until they run, nothing about the Android driver's runtime (WAL, the backup snapshot, restore, low-storage failure, the widgets' access, encryption of MINT data) is established, **the database cannot become the live store, and Gate B and Gate E cannot pass**. REP-008 to REP-010 are therefore built and tested on the JVM only.
- **Protected release key (REP-005):** needed only before any public release. The shared test key stays in use while the repository and the APKs are private. It does mean that, if the signature ever changes, the app must be uninstalled and its data would be lost: take a verified backup before rotating.

### Still open (not blocking REP-008)

1. Encryption of MINT data at rest (SQLCipher or file level): decide when MINT starts.
2. Whether the archive format of `DATA_AND_MIGRATIONS.md` section 10.2 needs the optional password encryption in the first backup release.
3. The CARE and PLAN row-level schemas: designed with the refactor of each module (REP-014), not in advance.

## 6. What this changes in the documents

- `IMPLEMENTATION_BACKLOG.md`: REP-007 done; the owner decisions above recorded against REP-005 and REP-006.
- `IMPLEMENTATION_GUIDE.md`: the unresolved-issues list points here.
- No specification changes: nothing above contradicts `DATA_AND_MIGRATIONS.md` or `POKEMON_ECOSYSTEM.md`.

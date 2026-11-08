# REP-014, third slice: the shared Pokémon, Bag and ledger tables are the live authority (0.135.0)

**Status:** built and tested on the JVM (`:data:jvmTest`, `:app-core:jvmTest`, the rest of `jvmTest`), compiled for web and Android (debug APK assembled); **not run on a device** (the Android DB check, `DeviceSelfCheck`, has a new line for it, to be run by the owner).
**Owner request (7 November 2026):** make the shared Pokémon, Bag and ledger tables the live authority with the same recipe as CARE and PLAN; decide and document whether the app keeps reading one `AppData` through the split or moves to the typed repositories (prefer the split, say what the repositories still need); make the ledger the only writer of the balance and drop World's working copy; keep one place per Pokémon in the new tables.

## 1. Decision: the split, not the typed repositories (for now)

The app keeps reading and writing one `AppData`; `LiveDocuments` now splits a save three ways (PLAN's part, the player's part, the rest) and puts them back together on a read. Reasons: the reward engine, `GameStore` and every screen are written against `AppData` and tested that way (GameStore alone has hundreds of tests); PLAN already proved the recipe; the typed repositories cannot be adopted by one module at a time while the Pokémon list is also read by PLAN, CARE, WORLD, GEAR and the widgets. Written down in `DATA_AND_MIGRATIONS.md` section 7.3.0, with what the repositories still need: a `GameStore` that asks for one Pokémon, party or balance; reward sites that settle their **real** event; the WORLD team and GEAR helper places moved into `pokemon_place`; `pokemon_history` and held-item references; World's rules taking the balance as an input.

## 2. Schema 6 (`5.sqm`, `databases/6.db`)

| Change | Why |
|---|---|
| `owned_pokemon` + `position`, `origin_json` | the list order a party's member order follows; the origin exactly as the save has it (`origin_how` and `origin_region` stay as lookup columns) |
| `pokemon_place` replaces `module_party`, `buddy_selection`, `guardian_slot` | **one row at most per Pokémon**: `owned_id` is the primary key; `UNIQUE (kind, ref, slot)` gives exactly one buddy and one Pokémon per slot; a `CHECK` ties each kind to its shape; no row is the PC. The migration carries old rows over by the app's priority (Guardian, buddy, party) and drops the other places of a Pokémon the old tables had in several (tested from a copy of `5.db`) |
| `pokemon_state` | the player's single values with no table: the party leads, the roles version, any field a newer build adds, the favourites and origins of Pokémon that are gone |

`dex_history`, `player_progress`, `bag_stack`, `currency_balance`, `reward_event`, `reward_ledger_entry` are unchanged. `verifyCommonMainDataDbMigration` passes (5 to 6).

## 3. What moves and how

`PokemonTables` (same style as `PlanTables`, JSON in and out so `:data` does not depend on `pokemon-core`): the player (`pokemon`, `buddyId`, `guardians`, `inventory`, `coins`, `eggMeter`, `trainerPoints`, `caught`, the leads, the roles version), `favorites`, `origins`, `shinySpecies`, `caughtForms`, `shinyForms`, `seenSpecies`. The rest of the save (settings, World, GEAR, quests, the reward log, counters, `pokeMigration`, `placesVersion`) stays in the `save` row.

**Activation (`LiveDocuments.movePokemonToTables`, called after `movePlanToTables` on every start, a no-op once done):** one transaction: (1) the save goes through `LiveDocuments.normalizer` (`GameStore.normalizeDocument`, set by `LiveStore`): the app's own load check, so a save from before the Poké merge, the one-place rule or the habit merge is brought up to them exactly as loading would; (2) the old save is kept whole as `aside:save:pokemon:<millis>`; (3) rows are written, the balances as **one opening-balance event** (`live:save:opening-balance`, source `MIGRATION`, the Poké merge's two source values in its metadata), nothing else paid, nothing in the outbox; (4) marker `pokemon_store=tables`; (5) the whole save is read back and compared with the checked one (`LiveDocuments.canonical`: a set is a set, a Bag entry of zero is no stack) and the ledger reconciles. A difference, a Pokémon in two places, a buddy or Guardian that is not owned, or non-empty tables (a legacy import) roll everything back; the `save` row stays the live save and it is tried again at the next start.

**Writes:** a diff against the rows (`PokemonTablesTest.aWriteChangesOnlyTheRowsThatChanged` counts with triggers): the same save writes nothing; one level-up is one `owned_pokemon` row; +5 Poké is one `currency_balance` update, one event, one ledger entry and nothing else; two Rare Candies is one `bag_stack` row, one event, one entry. The `save` row is rewritten only when its text changed. **Reads:** the balances and the Bag come from the tables. **Backup and restore:** the daily backup assembles `save.json` from all three parts; `replaceAll` passes a restored save through the same check and split (an old 0.123.0 save with a buddy that is also in a party restores without loss; without the check a save the tables cannot represent is refused and the current save kept).

## 4. Poké

The ledger is the only writer of `currency_balance` and `bag_stack` (`EconomyRepository.settle`, idempotent, append-only; update and delete of a ledger entry are refused by triggers). The save's `coins` and `inventory` are not stored: a live write settles the difference since the last write as one event (`save:<n>`, source `APP`, kind `net-change`). That is coarse by design (it records how much, not why); the per-source events arrive as the reward sites move onto `settle`. A save that would take the balance below zero is refused whole (tested: the other changes in the same save roll back too).

**World's working copy (replaced in 0.145.0 by `pokeEarned`, which the same update drains into the coins; see CHANGELOG):** `WorldData.poke` is `@Transient`: never stored, set from the balance when a save loads (`migratePoke`) and folded back after each action (`foldPoke`, unchanged), because World's pure rules (`claimMission`, `upgrade`, `claimResearch`, `buyCandy`, `train`, the offline simulation and floor events) still spend and earn through it. An old save's stored `world.poke` is read once into `legacyPoke` for the 1:1 merge (`pokeMigration` keeps both source values) and is never written (`EncodeDefault.NEVER`); the World screen shows `player.coins`. **Not done:** removing the in-memory field needs World's rules to take the balance as an input and report what they earned; that touches about 150 lines of `world-core` and its tests and the economy simulator, and was left for the World step of REP-014.

No number changed, so `RewardRules.VERSION` stays 16 and `:plan-core:simulateEconomy` and `:world-core:simulateWorldEconomy` were not re-run.

## 5. One place per Pokémon

In the table: the primary key (a second row for a Pokémon cannot be inserted), the unique slot, the single buddy; the repository's `setParty`, `setBuddy` and `setGuardians` each take the Pokémon from its old place and close up the slots left behind (`PokemonTablesTest.aPokemonHasOnePlaceAndTheTableCannotHoldTwo`). Across the tables and the `save` row: a write that names a Pokémon as buddy and in a party, as Guardian and buddy, on a World team (or two) and in the table, or as a Pokégear helper and in the table is refused before any row is written (`aSaveThatPutsAPokemonInTwoPlacesIsRefusedWholeBeforeAnythingIsWritten`). The two places that stay with their modules (World teams, Pokégear helpers) are therefore checked rather than constrained; moving them into `pokemon_place` is on the repository list in section 1. A Pokémon World gives you (a Guardian included) arrives with no place, that is in the PC.

The legacy importer also keeps the rule: the old buddy leaves Plan's party, and the import reports a `place_conflict` warning for each.

## 6. Tests

`PokemonTablesTest` (17): the four 0.123.0 fixtures move in and read back equal (also through `snapshot`), the aside copies, the row holds neither PLAN's nor the player's, counts of Pokémon, Bag and balance; the move pays nothing (one opening event, ledger reconciles, empty outbox) and a second move is a no-op; a fresh install; a move that cannot be proved (a buddy also in a party, no normalizer) leaves the save row as the live save with nothing written; a read-back that differs is not accepted; rows touched per write (above); the ledger as only writer; every field of the player incl. unknown ones, orphan favourites and origins, a visitor origin; collection and party order across a release, an append and an insert in front; one place (table, repository, save); dangling buddy or Guardian; a backup snapshot restoring through the split; an old backup checked by the app first; a write that fails halfway leaving every table as it was; the migration from a copy of `5.db`. Existing: `BackupRestoreTest` (restore and the write cut short), `PlanTablesTest`, `CareTablesTest`, `LiveDocumentsTest`, `LegacyJsonImporterTest` (two expectations changed: one place only), `DataStoreTest`; app-core: the Poké merge test now builds its old save with `legacyPoke` and asserts the field is never written again.

## 7. Known costs and not done

- A write reads all Pokémon rows to diff against them (a few hundred to a thousand rows); not measured on a device.
- The Android DB check line is new and has **not been run on a device**.
- Not done (the owner's list): the streak reminder and weekly recap source adapter; a place label on the party and PC screens; REP-008 device runs, the archive and MINT encryption.
- Not done (next in REP-014): HEAL (manual first); the typed repositories per module (section 1); World's rules taking the balance as an input.
- Asked in the session and not built: a guaranteed source for Nature Mints and Bottle Caps. Both exist (a Mint from every milestone of a goal done, both as rare reward drops from the Training items rank); a deterministic source (for example a World shop price) changes what the economy pays and belongs with its own `RewardRules.VERSION` bump and both simulations.

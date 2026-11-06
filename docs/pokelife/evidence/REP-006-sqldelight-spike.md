# REP-006: SQLDelight/SQLite feasibility spike (Android driver)

**Evidence for:** `ARCHITECTURE/DATA_AND_MIGRATIONS.md` section 4 and release gate A.  
**Done:** 6 November 2026, in the `db-spike` module (a small Kotlin Multiplatform library with an Android and a JVM target), on the project's own toolchain: Kotlin 2.4.21, AGP 9.4.1 (the `com.android.kotlin.multiplatform.library` plugin), Gradle 9.8.1, SQLDelight 2.4.1.  
**Status of the evidence:** built and tested on the JVM with real SQLite; the Android target compiles. **Nothing ran on an Android device or emulator.**

## 1. Questions and answers

| Question | Answer | Evidence |
|---|---|---|
| Does the SQLDelight Gradle plugin work with this project's Kotlin and AGP 9 KMP library plugin? | Yes, with no workaround | `:db-spike:jvmTest` and `:db-spike:compileAndroidMain` both succeed on a clean configuration |
| Does the Android driver resolve and compile? | Yes | `:db-spike:compileAndroidMain` builds with `app.cash.sqldelight:android-driver` |
| Does generated typed code replace hand-written SQL? | Yes: `SpikeDb`, `SpikeQueries` with typed rows | `SpikeDbTest.theGeneratedSchemaCreatesAndFeedsTypedQueries` |
| Can reward settlement be idempotent in one transaction? | Yes: a repeated `event_id` violates the ledger's primary key, and the transaction rolls back the balance change with it | `aSecondSettlementOfTheSameEventRollsTheWholeTransactionBack` |
| Does an interrupted transaction leave nothing behind? | Yes | `aFailureInTheMiddleOfATransactionLeavesNothingBehind` |
| Does the database enforce the ownership rules the code could forget? | Yes: a seventh party slot, an unknown owned id and the same Pokémon twice in one party are refused; the same Pokémon in two modules' parties is allowed; releasing a Pokémon removes it from every party (foreign keys on) | `constraintsHoldWhereTheCodeCouldForgetThem` |
| Does a numbered migration from a version-1 file keep its rows? | Yes (`1.sqm` adds a column and a table; the balance and Pokémon survive) | `aVersionOneFileMigratesToVersionTwoAndKeepsItsRows` |

The spike's schema is a small slice of the target model (owned Pokémon, module party, Bag stack, Poké balance, reward ledger). It is not the final schema.

## 2. Findings that matter for the design

1. **Foreign keys are off by default in SQLite.** The tests only pass the party and release rules because the connection sets `foreign_keys=true` (checked: with it set to false, `constraintsHoldWhereTheCodeCouldForgetThem` fails because an unknown owned id is accepted). The spec already requires verifying this on every connection (`DATA_AND_MIGRATIONS.md` section 4.2); the real driver setup must do it and a test must prove it.
2. **The migration verification task is not yet meaningful.** `verifyCommonMainSpikeDbMigration` exists and passes, but it checks nothing until `schemaOutputDirectory` is configured and the version `.db` files are committed. That has to be set up with the real schema so every future migration is checked against the previous version.
3. **The party-size rule can live in the schema** (`CHECK (slot BETWEEN 1 AND 6)`), which matches the six-slot party (`POKEMON_ECOSYSTEM.md` section 2.2). Rules that depend on other rows (for example module-specific eligibility) still belong in the domain layer.
4. **Room is not needed.** The spec asked to compare Room only if SQLDelight harmed maintainability or Android reliability. Nothing found does; the decision in `DATA_AND_MIGRATIONS.md` section 4.1 stands, subject to the unverified points below.

## 3. Not established

- Behaviour on a real device or emulator: the Android driver's runtime, WAL mode, the backup snapshot and restore, and low-storage failures.
- The widgets' access (spec section 4.3): whether widgets can go through the same repository layer in one process, or need multi-process SQLite.
- Performance with a heavy save (the 70-day fixture is about 170 KB of JSON; load and write times are unmeasured).
- Encryption of MINT data at rest (`INTEGRATIONS_AND_PRIVACY.md` section 11): SQLCipher or file-level encryption was not tried.
- The `wasmJs` target: the spike has none, by decision (Android is the only product platform).
- Importing a real JSON save into tables (the importer in `DATA_AND_MIGRATIONS.md` section 12), which is REP-008's work, using the fixtures from REP-003.

## 4. Recommendation

Proceed with SQLDelight on SQLite for REP-007 and REP-008. Before relying on it for the release gate, run the Android-only checks in section 3 on a device (a small instrumented test in `androidApp` or this module). `db-spike` became the `:data` module in REP-008 (the spike's schema and tests were replaced by the real ones); it is not wired into the app.

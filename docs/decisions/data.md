# Decision records: data

Moved here from code comments in the comment audit (21 November 2026). Each entry is a decision, rule or constraint that
the code alone does not show. Entries are grouped by source file; a line saying "Documented in" means the owning doc
already holds the rule and the entry only points to it.

## `data/build.gradle.kts`

### data module overview

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 4.5 Database rules`.

### Committed schema versions for verifyMigrations

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 4.5 Database rules`.

### jvmTest depends on migration verification

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 4.5 Database rules`.

## `data/src/androidMain/kotlin/app/pokeplan/data/AndroidDatabase.kt`

### AndroidDatabase foreign keys

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 4.5 Database rules`.

### AndroidDatabase.onCorruption throws

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12.6 Cutover and retirement`.

## `data/src/androidMain/kotlin/app/pokeplan/data/KeystoreMintKey.kt`

### KeystoreMintKey

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### KeystoreHealKey

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### KeystoreBackupKey

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### KeystoreOffPhoneKey

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.7 Automatic backup off the phone (REP-034)`.

## `data/src/commonMain/kotlin/app/pokeplan/data/BackupBundle.kt`

### BackupBundle staged restore

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.5.1 What exists`.

### BackupBundle.KNOWN documents

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.5.1 What exists`.

### BackupBundle.portable

`BackupBundle.portable` re-seals Mint and Heal under a passphrase for a portable backup. Each document arrives sealed with this phone's key (looked up by document name in `devices`) and is opened first; if one cannot be opened here (another phone's key, or no key on this run) the whole result is null, because the phone cannot honestly re-seal what it cannot read. A document that is not sealed (an older backup's, or Heal on a phone whose key was unavailable) is sealed under the passphrase as it is; a backup with neither is returned unchanged. The passphrase is validated by `PassphraseSealer.refusal` before the call.

### BackupBundle.unlock

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

## `data/src/commonMain/kotlin/app/pokeplan/data/BackupRetention.kt`

### BackupRetention policy

`BackupRetention` keeps the newest automatic backup of each of the last 7 days that have one and, of those taken before that, the newest of each of the last 4 weeks that have one (weeks start on Monday): at most eleven copies. Counting days and weeks that have a backup, not calendar days, means a phone that was off for a while keeps what it has instead of dropping it for being old. Days are the player's days, via the time zone offset.

### BackupRetention week boundary

1 January 1970 was a Thursday, so `BackupRetention` shifts the day number by three before dividing by seven to make a week begin on Monday.

## `data/src/commonMain/kotlin/app/pokeplan/data/BaseTables.kt`

### BaseTables

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4.1 Record tables`.

### BaseTables.activate

`BaseTables.activate` starts BASE in its tables: the document (null on a fresh install) is split into rows, every row is read back and the assembled document is compared with the given one; the active marker is set only when they are equal, all in one transaction, so a difference leaves the database exactly as it was. Already active does nothing.

## `data/src/commonMain/kotlin/app/pokeplan/data/CareTables.kt`

### CareTables

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4.1 Record tables`.

### CareTables.activate

`CareTables.activate` moves Care into its tables: the document (null on a fresh install) is split into rows, every row is read back and the assembled document is compared with the given one; the active marker is set only when they are equal, all in one transaction, so a difference leaves the database exactly as it was. Already active does nothing.

### CareTables.canonical

`CareTables.canonical` leaves out what a read back cannot tell apart, so the post-write comparison is not fooled: an empty list of records is not stored (nothing to put in a table), a routine never done has an empty `completions` array or none and comes back as the same thing either way, and the days of a routine are a set so their order does not matter.

## `data/src/commonMain/kotlin/app/pokeplan/data/DataStore.kt`

### NewerSchemaException

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 11.2 Migration rules`.

### DataStore.open checks foreign keys and schema

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 4.5 Database rules`.

### DataStore.mintCipher

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### DataStore.healCipher

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### Use the driver's own transaction

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 4.5 Database rules`.

## `data/src/commonMain/kotlin/app/pokeplan/data/EconomyRepository.kt`

### RewardEventInput.eventId

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 8.3 Idempotency keys`.

### RewardEventInput.outboxJson

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 8.5 Transactional outbox`.

### EconomyRepository balances change only with a ledger entry

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 8.7 Settlement rules`.

### EconomyRepository.settle

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 8.7 Settlement rules`.

### EconomyRepository.reverse

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 8.7 Settlement rules`.

## `data/src/commonMain/kotlin/app/pokeplan/data/HealTables.kt`

### HealTables

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### HealTables.isSealed marker

The Heal sealed marker (`META_HEAL_SEALED`) is set once by `HealTables.seal` and is never cleared, not even by `HealTables.clear`, so a sealed Heal stays sealed after an erase.

### Heal with no key refuses to write

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### HealTables.locked never throws

`HealTables.locked` reports whether any stored Heal row is sealed and cannot be opened with this phone's key (a database copied from another phone, or no key on this run). It is always false while the rows are in clear. It never throws so it is safe inside a transaction: an exception inside a nested transaction would roll the outer one back.

### HealTables.snapshot

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### HealTables.keepLockedAside

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### HealTables.seal

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### HealTables.activate

`HealTables.activate` starts HEAL in its tables: the document (null on a fresh install) is split into rows, every row is read back and the assembled document is compared with the given one; the active marker is set only when they are equal, all in one transaction, so a difference leaves the database exactly as it was. Already active does nothing.

## `data/src/commonMain/kotlin/app/pokeplan/data/LinkTables.kt`

### LinkTables

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4.1 Record tables`.

### LinkTables.activate

`LinkTables.activate` starts LINK in its tables: the document (null on a fresh install) is split into rows, every row is read back and the assembled document is compared with the given one; the active marker is set only when they are equal, all in one transaction, so a difference leaves the database exactly as it was. Already active does nothing.

## `data/src/commonMain/kotlin/app/pokeplan/data/LiveDocuments.kt`

### LiveDocuments no dual write

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12.6 Cutover and retirement`.

### LiveDocuments.normalizer

`LiveDocuments.normalizer` is the app's own check of a save (what `GameStore` does to every save it loads: numbers back in range, the Poké merge, one place per Pokémon, habits merged), `(document, nowMillis) -> document`. The app sets it so `:data` does not depend on the app model. It runs only on a save that did not come from the app's own writes: the one being moved into the Pokémon tables and a restored backup. Without it those documents are taken as they are and a save the tables cannot represent is refused.

### LiveDocuments.movePlanToTables

`LiveDocuments.movePlanToTables` moves PLAN's part of the save from its row into PLAN's tables once: the rows are written and read back equal to the document, the marker is set, the whole old row is kept under `aside:save:plan:<millis>` (never deleted) and the `save` row keeps only what is not PLAN's, all in one transaction so a failure leaves the save row as the live save. It does nothing once done, so it can run on every start.

### LiveDocuments.movePokemonToTables

`LiveDocuments.movePokemonToTables` (schema 6, REP-014) moves the player's side of the save into the shared Pokémon, Bag and ledger tables once, after PLAN's move. The save first passes through `normalizer`, then is written as rows; the balances become one opening-balance event and nothing is paid. The whole save is read back and compared with the normalized one, the marker is set, the old save is kept under `aside:save:pokemon:<millis>` (never deleted) and the `save` row keeps only what is neither PLAN's nor the player's, all in one transaction: any difference, or a save the tables cannot represent, rolls everything back and the rows stay the live save. It does nothing once done.

### LiveDocuments.moveCareToTables

`LiveDocuments.moveCareToTables` moves the Care document from its row into Care's tables once: the rows are written and read back equal to the document, the marker is set and the old row is kept under `aside:care:tables:<millis>` (never deleted), all in one transaction, so a failure leaves the document row as the live Care save. It does nothing once done, so it can run on every start.

### LiveDocuments.moveHealToTables

`LiveDocuments.moveHealToTables` starts HEAL in its tables once. HEAL has no earlier home, so on a normal start only the marker is set and every later `write("heal")` goes to the `heal_*` rows. A `heal` document row that already exists (a backup restored before this ran) is imported, read back equal and kept aside under `aside:heal:tables:<millis>`. A failure leaves the database as it was and is tried again at the next start.

### LiveDocuments.moveMintToTables

`LiveDocuments.moveMintToTables` starts MINT in its tables once, exactly as `moveHealToTables` does for HEAL: nothing to move on a normal start, a `mint` document row left by a restore is imported, read back equal and kept aside under `aside:mint:tables:<millis>`. A failure leaves the database as it was.

### LiveDocuments.moveLinkToTables

`LiveDocuments.moveLinkToTables` starts LINK in its tables once, exactly as `moveBaseToTables` does for BASE: nothing to move on a normal start, a `link` document row left by a restore is imported, read back equal and kept aside under `aside:link:tables:<millis>`. A failure leaves the database as it was.

### LiveDocuments.moveBaseToTables

`LiveDocuments.moveBaseToTables` starts BASE in its tables once, as `moveHealToTables` does for HEAL: nothing to move on a normal start, a `home` document row left by a restore is imported, read back equal and kept aside under `aside:home:tables:<millis>`. A failure leaves the database as it was.

### LiveDocuments.keepAside

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12.6 Cutover and retirement`.

### LiveDocuments.asideText sealing

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### LiveDocuments.eraseKeptCopiesAndHolds

A full-device erase, after the stores have written their blank state, also removes every copy kept aside by a migration or a restore (they hold the old documents whole) and every held reminder (`LiveDocuments.eraseKeptCopiesAndHolds`, one transaction). It leaves the live documents alone (the stores own those) and the daily backup files (the platform deletes those).

### LiveDocuments.snapshot seals Heal

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### LiveDocuments.snapshot seals Mint

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### LiveDocuments.replaceAll

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.5.1 What exists`.

### Restore of a document sealed with another key

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### Restore passes the save through the app's check

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4.1 Record tables`.

### LiveDocuments.activate

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12.6 Cutover and retirement`.

## `data/src/commonMain/kotlin/app/pokeplan/data/MintTables.kt`

### MintTables

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### MintTables codec

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### MintTables.locked never throws

`MintTables.locked` reports whether any stored Mint row cannot be opened with this phone's key (a database copied from another phone, or a lost key). It never throws so it is safe inside a transaction: an exception inside a nested transaction would roll the outer one back.

### Mint with no key fails closed

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### MintTables.sealedSnapshot

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### MintTables.keepLockedAside

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

### MintTables.activate

`MintTables.activate` starts MINT in its tables: the document (null on a fresh install) is split into rows, every row is read back and the assembled document is compared with the given one; the active marker is set only when they are equal, all in one transaction, so a difference leaves the database exactly as it was. Already active does nothing. It first calls `cipher()` so it refuses to start without the key.

### MintTables.activate needs the key

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1 Sealing of Mint and Heal`.

## `data/src/commonMain/kotlin/app/pokeplan/data/PassphraseSealer.kt`

### PassphraseSealer portable backup

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1`.

## `data/src/commonMain/kotlin/app/pokeplan/data/PlanTables.kt`

### PlanTables split of the save

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4.1`.

### Tables.write transaction contract

`PlanTables.write` (and the other `*Tables.write` methods built on `RecordRows`) must only be called inside one of the store's transactions: it diffs and changes rows one by one and relies on the caller's transaction for atomicity.

### PLAN activation verified by read-back

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12.6`.

### plan_series keys and day

A series record has no id of its own: the key is the series id carried by its `template`, and its day column is the start of its recurrence rule, or its creation when it has none.

### plan_state single values

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4`.

### PlanTables.canonical

`PlanTables.canonical` drops what a read back cannot tell apart before a save is compared with what was read: an empty list of records or days is not stored and comes back as the model's own empty default, and a day set is a set, so its order is ignored.

## `data/src/commonMain/kotlin/app/pokeplan/data/PokemonRepository.kt`

### One place per Pokemon in PokemonRepository

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2`.

### Releasing a Pokemon keeps its Dex history

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4.1`.

### PokemonRepository.placeOf null

`PokemonRepository.placeOf` returns null for a Pokémon in the PC, but also for one on a WORLD expedition team or helping a GEAR tool, because those modules keep their own records of that place and the `pokemon_place` table has no row for it.

### setParty takes the Pokemon from its old place

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4.1`.

## `data/src/commonMain/kotlin/app/pokeplan/data/PokemonTables.kt`

### PokemonTables split and ledger authority

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.3.0`.

### PokemonTables.write contract

`PokemonTables.write` must run inside a transaction and takes `rest`, the other part of the same save, so it can refuse a Pokémon that is in two places (a World team or GEAR helper in `rest` plus a row here) before anything is written. Only rows that changed are touched, and the balance and the Bag move through one ledger event.

### Legacy global Guardian list in a restored save

A module's Guardian slot is that module's index in `GUARDIAN_MODULES` (since 0.158.0). A save that still has the old global Guardian list, for example a restored backup from an older build, has it read into the free module slots in order, which are the slots the old rows had.

### Ledger notes settled before the net change

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.3.0`.

### Ledger note that cannot be applied

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.3.0`.

### PokemonTables.begin

`PokemonTables.begin` marks the player's side as living in the tables after `write` has put the save in as an opening balance. It refuses tables that already hold Pokémon or ledger rows, because those are a legacy import and not this save; the caller reads the save back and compares it before the transaction ends.

### PokemonTables.clear keeps the ledger

`PokemonTables.clear` erases the Pokémon, their places, the Dex and the single values in one transaction but not the reward ledger, which is append-only history.

### pokemon_place write order

In `writePlaces`, every place that moved or went away is removed before the new ones are inserted, so a slot that another Pokémon takes over is free when it is written (`pokemon_place` has a unique slot per kind and ref).

### Pokemon fields without a column

A Pokémon field without a column is kept whole in `extra_json`; so is the `partyId` of a Pokémon that is not in a party. It means nothing there, but it must come back unchanged on a read.

### Collection positions

Positions in `owned_pokemon` are kept as they are so a change touches only its own row; they are renumbered from 1 only when an insert in the middle or a reorder makes the old numbers disagree with the list.

### pokemon_state single values

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4.1`.

### Net change ledger event

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 8.4`.

### PokemonTables.canonical

`PokemonTables.canonical` drops what a read back cannot tell apart, so a save can be checked for coming back equal: a set is a set (its order is not kept) and a Bag entry of zero is not a stack.

### Canonical Guardians

In the canonical form the Guardians are per module: the old global list counts as the first free modules in order, and an empty list or map is the same as none, so an old save and the same save written per module compare equal.

## `data/src/commonMain/kotlin/app/pokeplan/data/RecordRows.kt`

### RecordRows diff writes

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4.1`.

### Sealed rows keep no date column

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1`.

## `data/src/commonMain/kotlin/app/pokeplan/data/Records.kt`

### Legacy ids kept as primary keys

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 4.5`.

### OwnedPokemonRecord.position

An `OwnedPokemonRecord.position` of 0 asks `PokemonRepository.add` to put the Pokémon last in the collection's list; otherwise it is the place in the order the Pokémon were owned.

### Modules that keep a party

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2`.

## `data/src/commonMain/kotlin/app/pokeplan/data/ReminderRepository.kt`

### Held reminders keep opaque references

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.10.1`.

### Reminder persistence

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.10.1`.

### Dropping old holds

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.3`.

## `data/src/commonMain/kotlin/app/pokeplan/data/RowCipher.kt`

### RowCipher sealing

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1`.

### MintLockedException

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1`.

### HealLockedException

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1`.

## `data/src/commonMain/kotlin/app/pokeplan/data/Sha256.kt`

### Sha256 in plain Kotlin

`Sha256` is a plain Kotlin implementation because the common source set has no hash function; it gives an import its identity and checksum.

## `data/src/commonMain/sqldelight/app/pokeplan/data/Data.sq`

### Legacy ids kept as primary keys

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 4.5`.

### Global favourite flag

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 6.4`.

### owned_pokemon origin columns

`origin_how` and `origin_region` are the lookup columns of a Pokémon's origin, the way it joined the collection; both are null for Pokémon from before the origin was recorded.

### extra_json keeps unknown fields

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4.1`.

### owned_pokemon position and origin_json

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4.1`.

### pokemon_place one row per Pokemon

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4.1`.

### dex_history

The Dex history (species and forms ever seen, caught or owned as a shiny) survives a transfer, so a Pokémon leaving the collection never takes its Dex entry with it (`POKEMON_ECOSYSTEM.md` section 5).

### Reward event idempotency key

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 8.3`.

### Append-only reward ledger

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 8.4`.

### reminder_hold only holds what cannot be rebuilt

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.8`.

### CARE record tables

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4.1`.

### care_dismissed_visitor

Visitors the player sent away are kept in their own table so that a dismissal can be undone; visitors in the meadow and queue are stored in arrival order.

### PLAN record tables

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4`.

### HEAL record tables

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4`.

### heal_steps and readings

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4`.

### MINT record tables

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.4`.

### Ledger is the only writer of balance and Bag

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 8.4`.

### No upsert on Android 8

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 4.5`.

### home_room placements

Documented in `docs/pokelife/MODULES/BASE.md 9.5.6`.

## `data/src/commonMain/sqldelight/migrations/10.sqm`

### heal_steps sealed with Heal

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1`.

## `data/src/commonMain/sqldelight/migrations/5.sqm`

### Migration 5 pokemon_place carry-over

Migration 5 to 6 carries the old party, buddy and Guardian rows into `pokemon_place` by the app's own priority (Guardian, then buddy, then party), so a Pokémon the old tables had in two places keeps the first and is dropped from the others.

## `data/src/javaMain/kotlin/app/pokeplan/data/AesGcmCipher.kt`

### AesGcmCipher

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1`.

## `data/src/javaMain/kotlin/app/pokeplan/data/LiveBackup.kt`

### LiveBackup folder layout

A `LiveBackup` backup is one folder per backup (`<millis>/save.json`, `care.json`, ...) with a `DONE` marker written last, so a folder cut short is never used. It reads the database in one transaction so it is consistent, files are sealed when a cipher is given, and with `requireSeal` no backup is taken at all while there is no cipher, rather than written in clear.

### LiveBackup.hasRoomFor

`LiveBackup.hasRoomFor` asks for twice the backup's size plus a margin, because a file is written beside the copy it replaces; a backup is skipped, never half written, when there is not that much room.

### Backup documents stay sealed

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1`.

### Restore keeps replaced documents aside

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.6`.

### LiveBackup.refreshAfterSealing

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md Sealing an install whose Heal is in clear`.

### LiveBackup.restoreNewest

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md Cipher and keys (documents kept aside)`.

### LiveBackup.restoreNewest key fallback

LiveBackup.restoreNewest restores the newest backup that can be opened: a backup sealed with a Keystore key this phone no longer has is passed over in favour of the one before it, so a lost key does not block a restore.

### LiveBackup.SEALED

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md Cipher and keys (BK1: prefix)`.

## `data/src/javaMain/kotlin/app/pokeplan/data/OffPhoneBackup.kt`

### BackupFolder.write contract

BackupFolder.write always creates a whole new file and the name never exists yet; OffPhoneBackup relies on this so a failed copy never overwrites or touches an earlier one.

### OffPhoneBackup

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.7 Automatic backup off the phone`.

### OffPhoneBackup.run device ciphers

OffPhoneBackup.run takes the phone's Mint and Heal ciphers (their rows are stored sealed to this phone) and opens them to seal again under the passphrase. If BackupBundle.portable returns null, one of them cannot be opened here, and nothing is written.

### OffPhoneBackup.EVERY

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.7 When`.

### OffPhoneBackup.STALE

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.7 Status`.

## `data/src/javaMain/kotlin/app/pokeplan/data/Pbkdf2Sealer.kt`

### Pbkdf2Sealer text format and passphrase normalisation

Pbkdf2Sealer texts are `p1.<salt>.<iterations>.<iv, ciphertext and tag>` with URL-safe Base64 parts (so none contains a dot). The passphrase is NFKC-normalised first, so the same words typed on another keyboard open the same backup. The context string is AES-GCM associated data, so a text sealed for another use never opens here. PBKDF2WithHmacSHA256 and AES-256-GCM are available on every supported Android (API 26 and later) and on the JVM.

### Pbkdf2Sealer iteration cap on open

The iteration count is read from the file being opened, so Pbkdf2Sealer refuses a count above MAX_ITERATIONS (and below 1) instead of running it: a crafted backup file must not be able to make the phone work for minutes.

### Pbkdf2Sealer.ITERATIONS

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md Portable (passphrase) backup`.

## `data/src/jvmMain/kotlin/app/pokeplan/data/JvmDatabase.kt`

### DataStore.backupTo

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md SQLite on Android 8 is 3.18`.

### DataStore.backupTo user_version

VACUUM INTO does not carry PRAGMA user_version over to the copy, so DataStore.backupTo stamps the current schema version on it; otherwise the copy would not open as the schema it is.

## `data/src/jvmTest/kotlin/app/pokeplan/data/LiveDocumentsTest.kt`

### Released schema 1 file has no user_version

The committed SQLDelight schema dump (databases/1.db) carries no PRAGMA user_version, while a released database had it set to 1. Tests that open it as a released database must set user_version = 1 themselves.

## `data/src/jvmTest/kotlin/app/pokeplan/data/MintTablesTest.kt`

### MintTablesTest write counting

With progression on, a logged Mint record also marks itself paid, which is a second write. The write-count test turns progression off so it counts plain saves only.

## `data/src/jvmTest/kotlin/app/pokeplan/data/PokemonTablesTest.kt`

### Poke balance is the ledger sum

The Poke balance equals the sum of the ledger entries, and each change is an event of its own that is never edited or removed. The opening balance is its own event.

### Pokemon places when old tables hold several

When old tables put a Pokemon in several places, the first place by priority wins (Guardian, then buddy, then party) and the others are closed up behind it so the one-place rule holds.

## `data/src/jvmTest/kotlin/app/pokeplan/data/PortableBackupTest.kt`

### PortableBackupTest clear-text check

The clear-text check searches for "PHP" with its JSON quotes: three letters can turn up by chance inside any base64 text, the quotes cannot, so the check does not fail at random.

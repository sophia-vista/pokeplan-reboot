# REP-014, fourth slice: MINT, manual first, and arrival scenes by module (0.140.0)

**Status:** built and tested on the JVM, compiled for desktop, web and Android; **not run on a device**. No provider, statement import or bank connection was started.
**Owner brief (8 November 2026):** the next REP-014 item (MINT, whose placeholder colour was reserved), and "improve Pokémon acquisition scenes: all are egg, that was created mainly for Plan focus eggs; can reuse but not too much, whatever makes sense for the module".

## 1. What was chosen from the spec, and why

`MODULES/MINT.md` section 18 puts the financial foundation, accounts and transactions, then budgets and bills first (P0), and import, reconciliation and providers later. Built: **accounts** (4), **transactions** (5), **budgets** (7.1, 7.2), **bills** (8) with the recurrence rules, the **overview** (3.2), a **review** that holds the month's reports (9), **reconciliation** done by hand (5.8) and the **weekly review**, **hide amounts** (3.3) and the **Ledger Research** progression (16). Not built, documented in `MODULES/MINT.md` section 22: providers, import, reminders, splits, rollover, savings targets, foreign currency, the access gate, encryption.

## 2. Ledger Research and the payout

Spec: MINT.md sections 16 and 22, `POKEMON_ECOSYSTEM.md` 10.2.1, `docs/economy.md` (version 18).

| Rule | Value *(tune)* | Where |
|---|---|---|
| Default | **on** (owner: every acquisition method starts on), off in Settings keeps every record | `MintSettings.progression` |
| Reconciled account pays | the account has real transactions; once per account a day; 2 a day | `MintRules.reconcilePays` |
| Weekly review pays | an open account and a transaction exist; once a week | `MintRules.reviewPays` |
| Pays | reconciled: 3 Rare Candies, 1 friendship step, 1 trainer point, 2 meter points; review: 5 candies, 1 step, 1 point, 3 meter points; 4 points = a Ledger Egg | `RewardEngine.mint`, `mintEgg` |
| Never pays | spending, income, transfers, balances, budgets, bills, empty accounts, repeats | no event kind exists for them |
| Idempotency | event id `mint:<kind>:<record id>` kept in `AppData.mintPaid` (newest 1000); the record keeps `paid` | `GameStore.mint`, `MintStore` |
| Daily and weekly caps (second check) | 2 a day, 1 a week | `GameStore.MINT_CAPS` |

`RewardRules.VERSION` 17 to 18; `golden.json` regenerated, with a Mint vector. `simulateEconomy` was not re-run (it plays only Plan; the new source cannot change Plan's numbers).

**What leaves MINT.** `MintEvent(id, kind, nowMillis, ref, module = "mint")`. Proofs: `MintRewardTest.theEventCarriesNothingButTheContract` (the exact JSON), `MintStoreTest.reconcilingPaysOnceAndTheEventCarriesNothingButTheContract` (an amount, an account name, a payee and a currency are searched for in the event, the shared save and the reward log) and `MintTablesTest.noAmountOrNameOfMintEverReachesTheSharedTables` (the shared tables and the ledger).

## 3. Storage (schema 7 to 8)

`7.sqm` and `databases/8.db`: eight record tables and `mint_state`, `key, position, day, json` like Heal's. `LiveDocuments.moveMintToTables` runs on every start (Android `LiveStore`); a `mint` row left by a restore is imported, read back equal and kept aside. `PokemonTables` knows Mint's lead. `MintTablesTest` (11): fresh start, import, an activation that cannot finish, a reopened file field for field with exact integer amounts, only changed rows written, one kind erased alone, the shared tables never holding Mint's words or figures, backup snapshot and older-backup restore, the bundle, export equals table family, party and lead through the shared Pokémon tables.

## 4. Arrival scenes

Before: every new Pokémon was shown as an egg rocking and hatching (`HatchScene`), made for Plan's focus eggs. Now `Acquisition` (EGG, ARRIVAL, FIELD, LEDGER) is read from `Origin.How` (the cutscene) or the reward-log source (`describe`, Home's Recently). EGG keeps the scene for Plan's focus and task eggs and a Care visitor's gift (CARE.md says an egg). ARRIVAL (Care, including its tutorial): the Pokémon walks in from the left over the meadow with its own SpriteCollab walk animation and settles. FIELD (Heal): it runs in from the right along the route. LEDGER (Mint): a Star Piece glints in the cave and the Pokémon rises from the floor. All use the module's real battle scene and sprites, the shared flash for a shiny, and Reduce motion goes straight to the Pokémon. `RewardTextTest` and screenshots cover them. HOME and LINK pick theirs when built.

## 5. Decisions I made that the owner may reverse

1. *Ledger Research is on by default* (MINT.md 16.4 says it can be disabled; it pays only abstract events with no figure). The alternative is Heal's opt-in.
2. *Numbers*: 3 and 5 candies, 2 and 3 meter points, 2 reconciles a day, 1 review a week; all *(tune)*.
3. *Amounts are shown as `PHP 1,250.50`*, the currency code rather than a symbol (no font glyph needed, never ambiguous).
4. *No foreign-currency transfers and no exchange rates yet*: a transfer needs both accounts in one currency; nothing is converted anywhere.
5. *Mint's Pokémon icon and cave scene*: the amulet coin and `dampcave`; the Mint Region content is the 0.139.0 curation.
6. *Care's `CARE_EGG` origin label* became "Arrived through Care" (it was "Hatched from a Care Egg"), since Care's eggs now arrive as friends; a visitor's gift still hatches from an egg.

## 6. Encryption: decision and what was built

The owner delegated the choice ("you make best decision for encryption", 8 November 2026). Decision: **encrypt Mint's rows at rest with an Android Keystore key, and seal every copy of Mint that leaves its tables.** Reasons: (1) `allowBackup` is true, so Android's cloud backup copies the app's files; sealed rows make that copy useless to anyone but this phone, which matches "no financial record leaves the local domain" (MINT.md 13.1); (2) a Keystore key needs no password to remember and no prompt, so Mint opens like every other module (the access gate of 13.2 stays a separate, later feature); (3) SQLCipher would encrypt the whole database but adds a native library, a new SQLDelight driver and a migration of the live file, none of which can be verified here, and it would also lock the game's own data to a key the player cannot move; (4) a passphrase for everything would mean typing it on every start.

Built: `RowCipher` (seal and open; sealed text starts `m1.`), `AesGcmCipher` (AES-256-GCM, provider-chosen random IV, a context string as associated data), `KeystoreMintKey` (Android), `DataStore.mintCipher`, `RecordRows.Codec` (rows compared by what they say, not by their ciphertext, so a save still writes only the rows that changed), `MintTables` (seals records and settings, empties the `day` column, `locked()`, `readOrNull()`, `sealedSnapshot()`, sealed rows set aside before an empty Mint can overwrite them), `LiveDocuments` (snapshot, restore, `keepAside` and the restored-row aside all sealed; a sealed backup that does not open is kept aside and changes nothing), and `BackupBundle` (a sealed Mint document passes the checks). `MintTables` refuse to start without a key. Tests (`MintTablesTest`, 15): rows hold no figure, name, currency, date or setting in clear; the snapshot, a backup bundle and the asides are sealed; a restore on the same phone works; a database or backup from another key is locked, not wiped, and the sealed copy is kept; no key, no start.

**Limits, said plainly.** A backup of Mint opens only on a phone with the key: a new phone restored from the player's own backup file or from Android's cloud backup has Mint sealed and empty (the sealed rows are kept aside, not lost, but cannot be opened there). The way to move Mint between phones today is Mint's own Export, a readable file. The next step is a passphrase-sealed backup (PBKDF2 into the same AES-GCM) that opens anywhere. Sealing hides records from a copy of the files; it does not hide them from the running app, from a rooted phone while Mint is open, or from a screenshot. Not exercised on a device: Keystore key creation, a phone with no hardware-backed keystore, and the cloud-restore path.

## 7. Not done / next

- A passphrase-sealed portable backup of Mint; the access gate; bill reminders through the shared scheduler; import; providers; splits, rollover, savings targets.
- Device checks for everything above, including the Keystore key and the new scenes' timing on a phone.
- HOME and LINK, then the typed repositories per module.

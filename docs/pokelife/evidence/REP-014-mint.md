# REP-014, fourth slice: MINT, manual first, and arrival scenes by module (0.140.0)

**Status:** built and tested on the JVM, compiled for desktop, web and Android; **not run on a device**. No provider, statement import or bank connection was started.
**Owner brief (8 November 2026):** the next REP-014 item (MINT, whose placeholder colour was reserved), and "improve Pokémon acquisition scenes: all are egg, that was created mainly for Plan focus eggs; can reuse but not too much, whatever makes sense for the module".

## 1. What was chosen from the spec, and why

`MODULES/MINT.md` section 18 puts the financial foundation, accounts and transactions, then budgets and bills first (P0), and import, reconciliation and providers later. Built: **accounts** (4), **transactions** (5), **budgets** (7.1, 7.2), **bills** (8) with the recurrence rules, the **overview** (3.2), a **review** that holds the month's reports (9), **reconciliation** done by hand (5.8) and the **weekly review**, **hide amounts** (3.3) and the **Ledger Research** progression (16). Not built, documented in `MODULES/MINT.md` section 22: providers, import, reminders, splits, rollover, savings targets, foreign currency, the access gate, encryption.

## 2. Ledger Research and the payout

Spec: MINT.md sections 16 and 22, `POKEMON_ECOSYSTEM.md` 10.2.1, `docs/economy.md` (version 18).

| Rule | Value *(tune)* | Where |
|---|---|---|
| Opt-in | **on by default** (Mint's spec says the player can disable it; Heal's says opt-in), off keeps every record | `MintSettings.progression` |
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

## 6. Not done / next

- **Encryption (owner decision needed).** `DATA_AND_MIGRATIONS.md` 10.4 leaves the choice for MINT's first sensitive table. Nothing is encrypted: Mint's tables and every backup file are plain text inside the app's private storage, as Care's journal and Heal's records are. Options: SQLCipher for the whole database, or a Keystore-wrapped key for Mint's rows and for backup files. Either changes the backup and restore path and needs a device.
- Bill reminders through the shared scheduler, the access gate, import, providers, splits, rollover, savings targets.
- Device checks for everything above, including the new scenes' timing on a phone.
- HOME and LINK, then the typed repositories per module.

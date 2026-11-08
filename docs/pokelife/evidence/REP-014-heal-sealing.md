# REP-014, Heal sealed with a Keystore key (0.152.0)

**Status:** built and tested on the JVM, compiled for Android; **not run on a device** (the Keystore path, the one-time move of a real install and the passphrase backup are the phone checks in section 6).
**Owner decision 6 (8 November 2026), roadmap item 8 of `IMPLEMENTATION_GUIDE.md` section 3.2:** "HEAL encryption: put in the roadmap", i.e. seal `heal_*` rows and Heal's backup copy with the existing cipher. `HEAL.md` section 14 asks for "the shared encryption and backup architecture for sensitive local data and exports".

## 1. What is sealed

| Where | Before | Now |
|---|---|---|
| The rows of `heal_activity`, `heal_hydration`, `heal_sleep`, `heal_measurement`, `heal_goal` and `heal_state` | JSON in clear, the day beside it in `day` | AES-256-GCM text (`m1.`, the same `RowCipher` as Mint), `day` empty: the table shows no measurement, time, note or date |
| `snapshot()` of the live documents (the daily backup in `filesDir/backups/` and "Save a copy") | Heal's JSON in clear | Heal sealed whole with the phone's Heal key |
| Documents kept aside (`aside:heal:*`: a migration, a restore, a document that could not be read) | in clear | sealed (the copies already made are sealed by the migration) |
| A passphrase backup ("Save a copy that opens on any phone") | Heal in clear in the file | Heal sealed under the passphrase, with Mint, so one passphrase opens both |

Heal has **its own key** (`KeystoreHealKey`, alias `pokelife.heal.v1`, associated data `pokelife-heal`), not Mint's: one module's key can be lost without the other, and a text sealed for one never opens as the other. The key is made inside the Android Keystore, non-exportable, with no screen-lock prompt (Mint's access gate is a separate feature and Heal has none).

## 2. The move of an install that has Heal in clear

Heal has been in clear since 0.136.0, so unlike Mint it needs a migration. `HealTables.seal(nowMillis)` runs at every start and does nothing once done:

1. in one transaction it rewrites every row of the six tables as sealed text (day emptied), seals every plaintext `aside:heal:*` copy, sets the marker `heal_sealed` (`app_meta`), reads the whole document back with the key and compares it with what was there, and checks that no row is left in clear. Any difference rolls it all back and Heal stays as it was; it is tried again at the next start;
2. `scrub()` then runs `VACUUM` and a truncating WAL checkpoint, so the free pages and the log do not keep the old clear rows (best effort, outside any transaction);
3. `LiveBackup.refreshAfterSealing` takes a fresh backup (Heal sealed in it) and deletes the older daily copies that still hold Heal in clear. If the new one cannot be written (no room) the old ones stay and rotate out as usual.

A fresh install is sealed from its first row: `seal` has nothing to rewrite and only sets the marker. **No schema change** (the marker is an `app_meta` key; `databases/10.db` stays the latest).

## 3. Failure modes (the same stance as Mint's, one difference)

| Case | Result |
|---|---|
| No key on this run, Heal **not yet sealed** (the Keystore failed on an old install) | Heal keeps working in clear for that run and is sealed at the first start with the key. This is the one difference from Mint, which refuses to start without a key: Heal already exists in clear on phones, and locking the player out of their records would be worse |
| No key on this run, Heal **sealed** | Fail closed: `healStorage` is memory only for the run, nothing is read or written (`HealLockedException`), the rows are untouched and open again at the next start with the key |
| The database was copied to another phone (the key opens none of it) | Heal reads as empty; the first write sets the old rows aside, still sealed, as `aside:heal:locked:<hash>`, then starts empty (Mint's rule) |
| A backup sealed with another phone's key is restored | It changes nothing: it is kept as `aside:heal:locked:<millis>`, sealed. The same for a Heal sealed when this run has no key |
| A backup from an older build (Heal in clear) is restored | It is written and sealed by the table's own write |
| Recovery from a damaged database | `LiveStore.openOrRecover` now gives the new database both keys **before** it restores the newest backup (it did not, so a sealed Mint or Heal in that backup would have been kept aside as locked instead of restored) |
| A passphrase backup is made on a phone that cannot open Heal (another phone's key, or no key) | Refused with a note, as for Mint; a Heal that is not sealed is sealed under the passphrase as it is |

## 4. What is still in clear, honestly

- Android's own cloud backup of the app's files (`allowBackup="true"`, owner decision 8: left for now) now carries Heal only as text this phone's key opens. It still carries Plan, Care, Home and Link in clear.
- Settings and the notification text: the hydration reminder says the same words for everyone and holds an opaque slot id (unchanged).
- The Pokémon, Bag, Poké balance and ledger hold no health figure (a `HealEvent` carries only `{id, kind, time, ref}`; unchanged).
- A phone with the screen unlocked can still open Heal; nothing here is an access gate.
- `scrub` cannot reach flash blocks the phone's storage keeps after a delete; it removes what SQLite itself kept.

## 5. Tests

`HealSealingTest` (15): a clear install is sealed once, reads back equal and shows nothing in any stored text; the kept-aside copies are sealed; no key leaves Heal in clear and working; a sealed Heal with no key reads as locked and writes nothing; another phone's key sets the old rows aside sealed before any write; a snapshot and a backup file hold no Heal in clear and restore on the same phone with the old Heal kept aside sealed; a backup from another phone's key is kept and changes nothing; an older build's clear backup is sealed on restore; a passphrase backup opens Heal on another phone only with the passphrase, and Mint and Heal share one; Heal that cannot be opened here cannot be made portable; the daily copies made before the seal are replaced by a sealed one; a fresh install is sealed from its first row (export and erase by kind still work); a sealing that cannot finish leaves the rows in clear; the sealed file reopens and no longer contains the note. `PortableBackupTest` follows the new `portable(documents, devices, ...)`. The 25 existing `HealTablesTest` cases run unchanged (the unsealed path).

## 6. To check on a phone

1. Update an install that already has Heal records: they are all still there; `adb`-free check: Settings → Backups → Save a copy, open the file in a text editor, search for a note you wrote in Heal (not found).
2. Settings → Backups → "Save a copy that opens on any phone" with a passphrase; restore it on a second phone (or after an erase) with the passphrase: Heal and Mint come back.
3. Clear the app's Keystore entry is not possible without a reinstall; reinstall with the Android cloud backup on to see the locked case: Heal reads as empty and `aside:heal:locked:*` holds the old rows.
4. The first start after the update takes a moment longer (the move, `VACUUM` and a new backup).

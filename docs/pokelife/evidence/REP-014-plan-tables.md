# REP-014, second slice: PLAN on its own tables, Habits merged, one place per Pokémon, CARE finished, backups (0.133.0)

**Status:** built and tested on the JVM, compiled for desktop, web and Android (debug APK assembled); **not run on a device**.
**Owner decisions (7 November 2026):** do the PLAN storage and the PLAN spec refactors in one session; do REP-008's remainder; REP-009's "one Pokémon in several parties" is wrong and is replaced by **one Pokémon, one place**; finish all CARE items.

## 1. PLAN storage (schema 4 to 5)

PLAN shares the `AppData` document with the player, so unlike CARE it cannot own a whole document. `LiveDocuments` splits every `save` into PLAN's fields (`PlanTables.PLAN_FIELDS`) and the rest, and reassembles on a read:

| Table | Holds |
|---|---|
| `plan_task`, `plan_list`, `plan_event`, `plan_series`, `plan_goal`, `plan_review`, `plan_focus_record` | `key, position, day, json` (the typed model stays the one definition of a record; unknown fields are kept) |
| `plan_day` | the rest days and the active days, one row each |
| `plan_state` | the running focus session, how it ended, completions waiting out their undo, the pick, the emergency pauses, the legacy habits and the time they were merged |

Everything else stays in the `save` row (player, Pokémon, Bag, settings, World, GEAR, quests, counters). A write touches only rows that changed (`PlanTablesTest` counts them with triggers). Activation (`movePlanToTables`, called after Care's on every start): one transaction, write, read back, compare with the original (empty lists and the order of a day set are not told apart), marker `plan_store=tables`, the whole old row kept as `aside:save:plan:<millis>`; any difference rolls everything back and the row stays the live save. The daily backup still assembles `save.json` from both, and restore goes through the same split. `RecordRows` is the row diff shared with `CareTables` (Care's tests still pass unchanged).

Known cost: a write reads the PLAN rows it diffs against (a few thousand rows is a few hundred KB); a deleted record shifts the positions of those after it, so those rows are rewritten too. Not measured on a device. PLAN's settings (inside `AppData.settings`) and the focus alerts are not moved.

## 2. Habit merge (PLAN.md section 11.2)

See PLAN.md 11.2 "As built". `GameStore.mergeHabits` runs in `tidy` on every load: habits become frequency series (same id, creation day, completion days, payment day), `legacyHabits` keeps the originals. `GameStoreTest.habitsOfAnOlderSaveBecomeRecurringTasks...` proves: ids and goal links kept, no coins or log entries from the merge, loading again changes nothing, yesterday's payment is not paid again, the first tick of a new day pays once. One documented exception to the spec's table: chosen weekdays stay a frequency target.

## 3. One Pokémon, one place (REP-009, replaced)

Spec first (POKEMON_ECOSYSTEM.md 2.2, 2.2.2, 2.2.3, 2.3, 2.4, 6.2, 6.3; GEAR.md 6), then `Places` (derived place, `vacate`, `resolve`) and `GameStore.moveTo`: `setBuddy`, `setInParty`, `slotGuardian`, `worldAssign`, `assignGearHelper` each take the Pokémon from its old place in the same save; a team out on an expedition cannot be moved. `PlacesTest` and the reworked `GameStoreTest`, `WorldStoreTest`, `GearTest`, `PerksStoreTest` cover it.

**Decisions I made that the owner may reverse:**
1. *Priority when a save has a Pokémon in two places:* Guardian, World team, party, helper, buddy (function beats decoration, so rewards do not drop). The usual case is the starter, who was both buddy and Plan's lead: it stays in Plan's party and the buddy becomes the best Pokémon in the PC, or is empty if there is none.
2. *New games:* the starter is the buddy only; Plan's party starts empty and the first hatched Pokémon joins it (so the starter no longer earns Plan friendship).
3. *Assigning moves, it does not refuse* (the lists of free Pokémon for a World team show the PC only). The Plan-party-never-empty rule stays for taking a member out directly.
4. *Pokégear:* no automatic helper from the collection; a tool without a chosen helper shows its icon or its Ranger loaner. A Pokémon helps one tool.
5. *Quick send* skips Guardian species.

Not done: a place label on the party/PC screens (the store has `placeOf`; the screens do not show it), and the Pokémon pickers for World teams still list by the old filter only through `worldIdle()`.

## 4. CARE (CARE.md 6.8 "Built in 0.133.0")

Reunions, egg inheritance with the origin note, Care Play and the Meadow; tests in `CareExtrasTest` (inheritance is stable, light and repeatable; a friend returns, takes no place and pays nothing; preferences are individual, stable and discovered only by showing; time pays once a day per Pokémon and repeats pay nothing). `RewardRules.VERSION` 16 (Care Play only). `simulateEconomy` was **not** re-run: it plays only Plan, and the new source is capped at 6 Rare Candies a day. Not built: the cosmetic-variation trait of inheritance (needs art), a Care Play activity per species, TalkBack and reduced-motion review on a device, and the visual check of the Meadow and Care Play screens (no screenshot test was added).

## 5. REP-008 remainder

Done and tested on the JVM: backup bundle file (`BackupBundle`), staged validation before any change, restore of a chosen file or an automatic backup in one transaction with the replaced save kept aside and a mid-swap failure rolling back whole, the list of backups, the low-storage check (a backup is skipped, a save that cannot be written rolls back and is reported), and a write cut short leaving every table at the last commit (`BackupRestoreTest`). Android: `AndroidBackups` (document pickers), Settings → Backups. **Not done:** a real process kill on a device and a real low-storage run (both unproven off the JVM), the database snapshot archive with attachments and checksums of section 10.2, archive encryption, and **MINT encryption** (MINT does not exist yet; the choice of SQLCipher or per-table encryption belongs with its first sensitive table).

## 6. Not done / next

- The streak reminder and weekly recap still need a source adapter in the shared reminders.
- Shared Pokémon, Bag and ledger tables as the live authority; HEAL (manual first).
- Device checks for everything above (the owner skips them and will raise issues).

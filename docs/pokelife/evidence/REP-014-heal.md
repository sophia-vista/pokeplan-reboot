# REP-014, third slice: HEAL, manual first (0.136.0)

**Status:** built and tested on the JVM, compiled for desktop, web and Android (debug APK assembled); **not run on a device**. No provider, watch or Health Connect work was started: those stay behind the capability checks `MODULES/HEAL.md` section 10 names.
**Owner brief (7 November 2026):** build HEAL, manual entry first; its own `heal-core` and `HealStore`; payout only through a minimal event; own tables from the start; module menu, party, first-run tutorial, reminders, privacy.

## 1. What was chosen from the spec, and why

`MODULES/HEAL.md` section 18 puts manual activity and fitness first (phase 1) and nutrition and sleep second (phase 2). Built: **activity** (3), **hydration** (5.4), **sleep** (6, manual and finished nights only), **body measurements** (7) and **basic goals** (9), with the Today card (2.2) and the Settings page. Reasons: they are the P0/P1 items that need no provider, no food database (5.2 says licensing must be evaluated first) and no clinical judgment, and each is a single quick form. **Not built** (documented, not hidden): the exercise library, workout sessions and templates (4: a logger with sets, rest timers and interruption recovery is its own session), meals and nutrients (5.1 to 5.3), personal health records (8: medication, symptoms, appointments, notes), History and charts (2.3), a customisable Today (2.2), every import and provider (10), medication/appointment/goal-check-in reminders (13). HEAL.md section 22 says so in the same words.

## 2. Field Research and the payout

Spec first: HEAL.md sections 12 and 22, `POKEMON_ECOSYSTEM.md` section 10.2.1 (the HEAL row), `docs/economy.md` (version history 17, two source rows, caps, an income note).

| Rule | Value *(tune)* | Where |
|---|---|---|
| Opt-in | off by default; off keeps every record | `HealSettings.fieldResearch` |
| Activity pays | a new record, at least 10 min, ended within 12 h and not in the future, 3 a day, manual source only | `HealRules.activityPays` |
| Drink pays | the day's first, logged now, 1 a day | `HealRules.participationPays` |
| Pays | activity 4 Rare Candies + berry + 1 friendship step (Heal's lead) + 1 trainer point + 2 meter points; drink 1 candy + 1 trainer point + 1 point; 4 points = a Field Egg | `RewardEngine.heal`, `healEgg` |
| Never pays | sleep, measurements, goals, notes, imports, edits, backfills | by construction: there is no event kind for them |
| Idempotency | event id `heal:<kind>:<record id>` remembered in `AppData.healPaid` (newest 1000); the record also keeps `paid`; edits keep it and never pay | `GameStore.heal`, `HealStore` |
| Daily caps (second check) | 3 and 1, per kind, by day counter | `GameStore.HEAL_DAILY_CAP` |

`RewardRules.VERSION` 16 to 17; `golden.json` regenerated (`UPDATE_GOLDEN=1`) and now also holds a HEAL vector; `GoldenVectorTest` guards the outcome change with the version bump. `simulateEconomy` was **not** re-run: it plays only Plan and the new source cannot change Plan's numbers (the same reasoning the Care entries in `economy.md` use). The pacing note in `economy.md` (at most 1.75 Field Eggs a day, 13 candies on a full day) is arithmetic, not a simulation.

**What leaves HEAL.** `HealEvent(id, kind, nowMillis, ref, module = "heal")`. `HealStoreTest.theEventLeavingHealCarriesNothingButTheContract` logs a run with distance 7000 and the note "secret", a drink of 500 ml named "water", and asserts that none of those values appear in the event JSON or in the shared save. The reward log says only "Heal: Field Research". The `ref` is the record's random key and means nothing outside HEAL.

**Heal Region.** `Region.HEAL`, `tools/species/regions/heal.txt` (64 lines: 13 Common, 23 Uncommon, 26 Rare, 2 Super Rare (Jangmo-o, Frigibax); sporty, outdoor and energetic; none was in Plan's or Care's list, the generator checks). Every rarity has a pool (`HealRewardTest`). It opens with the module (module regions rank 0), shows as a Pokédex tab and a "Counts for the Heal Region Pokédex" line. Content choice, which the owner may change. `DexData.kt` regenerated: one line changed.

**Heal's party.** `PartyIds.HEAL`, `PlayerState.healLeadId` / `healLead`, `HealPerks` (Friendship gain: Fighting, Normal, Fire, Electric, Flying, Dragon; Berry finder: Water, Grass, Ground, Ice, Bug, Fairy; 25% cap, lead in full, the rest a quarter), `Places.leads` keeps the lead, `GameStore.setHealLead`, a "To Heal party" / "Make Heal lead" row on the Pokémon page, a Heal group on the party screen, `HealPartyPanel` in the module. One place only is unchanged: joining takes the Pokémon from where it was (`HealStoreTest.theWalkDoesNotMoveTheBuddy...`).

**Tutorial.** `Tutorials.HEAL_WALK`, `tutorialOffered(id, careBreaks, healActivities)`, `completeTutorial` (Heal Region Common egg into Heal's party, leading it), `TutorialCard` with a walk wording; the row added to `POKEMON_ECOSYSTEM.md` 10.2.1. `HealStoreTest` proves: once only, no meter point, no `healPaid`, no `activeDays`, no HEAL record, the next real activity still pays; a player with a logged activity is not offered it.

## 3. Storage (schema 6 to 7)

`6.sqm` and `databases/7.db`: `heal_activity`, `heal_hydration`, `heal_sleep`, `heal_measurement`, `heal_goal` (key, position, day, json) and `heal_state`. Unlike Care and Plan there is no earlier document to move: `LiveDocuments.moveHealToTables` sets the marker (and, only if a `heal` row of `live_document` exists because a backup was restored first or the database could not be opened on an earlier run and `heal.json` was used, imports it, reads it back equal, and keeps the row aside as `aside:heal:tables:<millis>`). `LiveStore` (Android) runs it on every start after Plan's. `LiveDocuments.read/write/names/snapshot` route `heal` to `HealTables` once active, so the daily backup and the backup file (`BackupBundle.KNOWN`) cover it; `replaceAll` leaves `heal` alone when the backup has none (an older backup never wipes health data). `HealTables` can read one kind (`readKind`), erase one kind (`erase`) or all (`clear`) without touching any table outside `heal_*`.

Tests (`HealTablesTest`, 9): fresh start; import of a restored row; an activation that cannot finish leaves the database as it was (trigger on the marker); a reopened file returns the whole `HealData` field for field; a save writes only the rows that changed (trigger-counted: one drink is one row, a setting is one row, an edit is one row); erasing one kind leaves the others and the game's `save` row byte-for-byte equal; backup snapshot carries `heal`, an older backup leaves it, a backup that has it brings it back and keeps the replaced one aside; the bundle knows `heal` and refuses unknown names; export of one kind equals that table family. `HealStoreTest` (17) and `HealRulesTest` (8) cover rules, caps, privacy, erase and export; `HealRemindersTest` (6) the reminders; `HealRewardTest` (8) the engine in `pokemon-core`.

**Sealed later (0.152.0, `REP-014-heal-sealing.md`):** the rows, the backups and the kept-aside copies are now sealed with a Keystore key; the clear-text paragraph that followed here described 0.136.0 to 0.150.0. Per-kind export of a *backup* is still not done. The Android `heal.json` fallback is only for a database that never opens.

## 4. Reminders

`HealReminders` / `HealReminderAdapter` (app-core) are a shared-reminder source for **hydration only**, off until a time is added (max 3). The request holds an opaque slot (`hyd-1000`); the words are fixed ("A moment for a drink?", "Only if you would like one."); the notification is `VISIBILITY_PRIVATE`; the only action is a 30-minute snooze (`ReminderSnooze` gives HEAL Care's), there is no "done"; the global quiet hours and focus deferral apply (`HealRemindersTest`); a reminder is dropped for a day on which the player's own target is already met. `SharedReminders` registers the adapter and presents it (`HydrationReminders`, channel `heal-hydration`, low importance); `MainActivity.healChanged` re-plans when the times, the target or today's total change. Not run on a device.

## 5. Screens

`screens/Heal.kt`: `Module.HEAL` (item icon `potion`, `RegionLook.HEAL`, route scene), `HealMenu` (reward box, tutorial, Today card, big cards with real item icons `x-speed`, `fresh-water`, `moon-stone`, `scope-lens`, `star-piece`, `storage-key`, the Field Research line, the party panel, the privacy line), and the six sections. Empty states for every list, validation messages in words (`Check.Refused`), delete and erase ask first, every number is "not logged" rather than 0, no score, no colour judgment. Light and dark come from the shared theme tokens; touch targets are the shared `PixelKey`/`TabButton`/`ToggleRow`. Three item icons were added to `assets/items` through `tools/sprites/fetch_items.py` (`potion`, `x-speed`, `fresh-water`, PokeAPI/sprites). `ScreenshotTest`: `healMenu`, `healMenuEmptyShowsTheTutorialAndNoScore`, `healMenuDark`, `healActivity`, `largeTextHeal` (menu and every section at 130% text). **Not reviewed with TalkBack or reduced motion on a device**; the screens have no animation of their own (the tutorial's bar is the shared one).

## 6. Decisions I made that the owner may reverse

1. *Field Research is opt-in* (HEAL.md 12.4 and the acceptance criteria), so a new player's first activity pays nothing until they turn it on; the walk tutorial is how they meet it. The alternative (on by default for activity) contradicts "Field Research ... is opt-in".
2. *Heal Region content*: the 64 lines above.
3. *Numbers*: 4 candies, 3 sessions a day, 10 minutes, 12 hours, a 4-point meter; all *(tune)*.
4. *`Region.HEAL` is released with the module*, so the Pokédex gains a Heal tab for everyone, with the Heal Region's lines uncaught.
5. *HEAL has no switch in Settings* (Care's `careModule` is forced on too).
6. *Hydration reminders are the only reminder source*: the others need the records that were not built.

## 7. Not done / next

- Device checks for everything above (the owner skips them and will raise issues): the hydration notification, the database activation on a real upgrade from schema 5, the Heal screens under TalkBack.
- Encryption of the sensitive tables and backups (MINT's session).
- The rest of HEAL: workouts, nutrition, personal health records, History and charts, provider adapters, medication/appointment reminders.
- Still open from earlier: the shared Pokémon, Bag and ledger tables as the live authority; the streak reminder and weekly recap source adapter; a place label on the party and PC screens; REP-008 device runs and archive.

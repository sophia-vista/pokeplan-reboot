# REP-014, sixth slice: cross-module links, Home reminders, real ledger events (0.144.0)

**Status:** built and tested on the JVM, compiled for desktop, web and Android; **not run on a device**.
**Owner brief (7 November 2026):** "do these": cross-module links (HOME chore to PLAN task, LINK follow-up to PLAN task; one completion and one reward owner each), Home reminders through the shared scheduler (chores, maintenance, low stock, warranty), typed repositories per module (`DATA_AND_MIGRATIONS.md` 7.3.0).

## 1. What was built
1. **Links.** `Task.link: TaskLink?` (`LinkKind.HOME_CHORE`, `LINK_FOLLOW_UP`; `LinkKind.ownsCompletion`). `TaskRules.paysOnCompletion` and the subtask rule return false for a task another module owns, so PLAN cannot pay for it. `GameStore` holds the `LinkOwner`s: `completeTask`, `reopen`, `wontDo`, `undoCompletion` on a HOME-linked task call HOME's `complete`, `undo`, `skip` (`HomeLinkOwner` over `HomeStore`), and `syncLinks()` (on every Home change and on `refresh()`) makes the task follow the chore (title, day, state, next occurrence, gone). `linkChore`, `unlinkTask`, `linkedTask`. `convertFollowUp` is PLAN's half of the LINK conversion: a normal PLAN task with a `LINK_FOLLOW_UP` reference, one per follow-up.
2. **Home reminders.** `HomeSettings.reminders` (off), `reminderHour`, `maintenanceLeadDays`, `warrantyLeadDays`, `reminderNames`; `remind` on `Chore`, `MaintenanceSchedule`, `Supply`, `Warranty` (all off). `HomeReminders.upcoming/stillWanted/text`, `HomeReminderAdapter` registered with `SharedReminders`, `UpkeepReminders` (the notification, channel `home-upkeep`), `onHomeChanged` in `App()` and `MainActivity.homeChanged`. Screens: Home → Settings → Reminders, and a "Remind me" button on a chore, maintenance schedule, supply with a threshold and warranty with an end date.
3. **Real ledger events.** `AppData.ledgerNotes` (`LedgerNote`), written by `GameStore.heal/mint/home` through `noted(...)`; `PokemonTables.settleNotes` settles each under its own event id before the net change. 

## 2. Decisions I made that the owner may reverse
1. *A HOME link is a projection owned by HOME; a LINK conversion hands ownership to PLAN.* The two cases in the Bible differ, so `LinkKind.ownsCompletion` says which. HOME's chore is never copied: the task keeps the chore id and the occurrence only; its title is overwritten by sync.
2. *One task per chore, always the current occurrence* (not one per occurrence), so a weekly chore never piles up tasks and the task needs no series.
3. *Removing the link deletes the projection* (restorable like any deleted task); there is no "convert to a PLAN-owned task" action yet, because HOME.md 2.1 only requires the explanation if it is offered.
4. *Reminders are per record and off by default, with a master switch;* the chore "Done" action is allowed from a notification (it is the user's act and the chore's own completion), maintenance, restocking and warranties are not.
5. *A low supply asks once,* at the first reminder hour after the last quantity change (or its creation), so editing its name does not ask again.
6. *Care's reward has no event id,* so its payments still reach the ledger as the net change of one save.

## 3. Not done
- **LINK itself** (all eight phases of `MODULES/LINK.md`): the follow-up record, its screens, its reminders and Postcard Trail. `convertFollowUp` and `linkedTask` are ready for it; MINT bill state links, a PLAN shopping task for a supply (HOME.md 4.3) and the "Reminders for LINK" half are not built.
- **Typed repositories per module** are not finished: only the second item of the list in `DATA_AND_MIGRATIONS.md` 7.3.0 (real events) is, for HEAL, MINT and HOME. Still open: a `GameStore` that asks the repositories for one Pokémon, one party or one balance; PLAN's and Care's reward sites (task completion, Care's moments) settling real events; the WORLD team and GEAR helper places in `pokemon_place`; `pokemon_history` and held-item references; World's rules taking the balance as an input. `app-core` also targets web, which has no SQLite, so a typed repository port has to live behind an interface there.
- Device checks (Android notification buttons, quiet hours with a Home reminder, a widget ticking a linked task).

## 4. Tests
`TaskLinksTest` (13), `HomeRemindersTest` (7), `PokemonTablesTest` (+2).

# REP-014, seventh slice: Link reminders, and the streak reminder and weekly recap on the shared scheduler (0.148.0)

**Status:** built and tested on the JVM; the composeApp and the Android app are compiled by the gate; **not run on a device**. No reward number changed (`RewardRules.VERSION` stays 20).
**Owner brief (8 November 2026):** "continue with next chunk", then "bigger chunk welcome". The backlog's open reminder items after Home's were Link (dates and follow-ups) and Plan's streak reminder and weekly recap; both are done here, on one branch.

## 1. Link reminders (`MODULES/LINK.md` section 5)
- **Records.** `ImportantDate.remindDays: Int?` (null is off; the presets are `LinkRules.REMIND_PRESETS` = 0, 1, 3 and 7 days before, the day itself included), `FollowUp.remind: Boolean`, and `LinkSettings.reminders` (the master switch, **off**), `reminderHour` (9) and `reminderNames` (off). Old saves load with all of it off (`LinkRemindersTest`); no schema change, because the rows keep their whole JSON.
- **Rules.** `LinkReminders.upcoming/stillWanted/text` and `LinkReminderAdapter` (`app-core/.../LinkReminders.kt`). A date asks for its next occurrence only, on the observed day (29 February is the 28th in a year without one) minus its lead; a one-time date asks nothing once it has passed. A follow-up asks once, on its day, only while it is open, has a day, is not overdue and **was not handed to Plan** (`converted`: Plan is then the reminder source, LINK.md section 5, and `setFollowUpRemind` refuses to switch it on). Nothing overdue asks, nothing repeats, no backlog is rebuilt; a request that fires after the date it was made for has passed is stale and dropped.
- **Actions.** A follow-up's notification offers "Done" (the follow-up's own completion, which pays Postcard Trail once like ticking it in Link) and "Tomorrow"; a date's offers only "Tomorrow". Dismissing or snoozing changes no record and costs nothing.
- **Privacy.** The request holds an opaque record id, the kind and a day. The lock screen, and the text when the user has not allowed more, is always "You have an upcoming reminder". With "Name it" on, the unlocked text is the date's title with when ("Mum's birthday, tomorrow") or "Follow up: title"; never a person, a note or a date's type.
- **App.** Settings → Reminders in Link (master switch, hour, "Name it"); a "Remind me" row of chips on an existing date; "Remind me on the day" on an open follow-up with a day. Android: `LinkNotices` (channel `link-notices`, private lock-screen version), `linkStore`, `LinkReminderAdapter` in `SharedReminders`, `onLinkChanged` in `App()` and `MainActivity.linkChanged`.

## 2. The streak reminder and the weekly recap on the shared scheduler (`INTEGRATIONS_AND_PRIVACY.md` section 9)
- `PlanReminderAdapter` now also answers for two nudges, given a clock (`KIND_STREAK`, `KIND_RECAP`; without one it behaves as before). A streak request is made for each of the next three days at the chosen hour, named by its streak day; the recap for the next Sunday at 18:00, named by its date. **A request inside quiet hours is not made**, as before; when it fires, `isStillWanted` runs `Nudges.streakAtRisk` / `weeklyRecap` (a new `quietApplies = false`, because the shared policy already moved delivery out of quiet hours and a focus session) and refuses one whose day has gone by, so a nudge held until the next morning is dropped instead of nagging. No repeats, no actions.
- The words, the channel and the notification ids are unchanged (`AppAlerts.show`). The separate alarms and the "later" alarms for a focus session are gone: `AppAlerts.Plan` no longer has `streak` and `recap`, `schedule` cancels any alarm of the four old kinds an older version set, and one that still fires does nothing. Focus deferral is now the shared policy's.
- **The nightly refresh.** The planner looks three days ahead and was re-run only when the app opened or an alarm fired, so a "a week before" birthday could have been missed. `SharedReminders.setDailyRefresh` sets one inexact alarm a night (04:00 local) whose only job is to plan everything again (`ACTION_REFRESH`). This also helps Home's lead times (maintenance up to 14 days, warranties up to 90).

## 3. Decisions I made that the owner may reverse
1. *Reminders off by default, per record,* as Home's: the master switch is off and each date and follow-up is switched on by itself. Presets for dates follow LINK.md section 5 ("day of, one day, three days, one week").
2. *A date's reminder cannot be set while adding it,* only on an existing date (Home's pattern: the choice is small and the add form stays short).
3. *The generic lock-screen text is one sentence for both kinds,* the spec's own example, so even the kind (date or follow-up) is not shown.
4. *The streak reminder still skips a quiet-hours hour* instead of moving to the morning, so an old setting behaves as it did.
5. *The nightly refresh is inexact* (`setAndAllowWhileIdle`) and at 04:00, away from anything the user set.

## 4. Tests
`LinkRemindersTest` (10: both switches, presets, passed and one-time dates, 29 February, follow-up rules and the Plan hand-over, stale after an edit, delete or switch, a late request, no names without the user's yes, Done once, quiet hours and an old save) and `PlanNudgeRemindersTest` (6: the requests, quiet hours, the day a streak request belongs to, an empty week, focus deferral, no repeat or action); `ReminderCoordinatorTest` and `NudgesTest` still pass unchanged apart from a named argument.

## 5. Not done / next
Device checks (the alarms, the nightly refresh, the lock-screen text); a date's reminder while adding it; device-contact import for Link; then the typed repositories per module (`DATA_AND_MIGRATIONS.md` section 7.3.0) and GEAR completion.

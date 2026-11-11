# PLAN module audit at 0.167.2

**Date:** 11 November 2026. **Scope:** PLAN only (Plan's page, Planner, Calendar, Targets, Lists, Focus, Progress, Goals, Review, Plan settings), against `MODULES/PLAN.md` and `FEATURES/DESIGN_SYSTEM.md` 5.1 and 12A.
**Method:** every PLAN screen file and the store actions behind it were read; the `ScreenshotTest` renders of the PLAN screens were looked at in both palettes. Nothing was run on a phone, so touch feel, the keyboard and drag on the hours are judged from code only.
**Status:** evidence, not authority. A rule changes in `MODULES/PLAN.md` first.

This goes deeper on one module than `DESIGN-REVIEW-0.165.1.md`. Findings that review already has are marked **(DR)** and not argued again. The audit was made on 0.167.2; the review's first batches merged as 0.168.0 while it was written, and items they changed are marked **(0.168.0)**: re-check those on a current build.

## 1. Verdict

PLAN's engine is ahead of its screens. Quick add with a parsed preview, the recurrence rules, the undo window on completion, the day timeline with drag and resize, and Today's "From before" with one "Move to today" are all good and should be kept as they are.

The weak points are, in order:

1. **Dates.** A task can be planned only for today, tomorrow or in 7 days from its own card, and the calendar reaches 13 days ahead and never back. Anything further needs typed quick-add text.
2. **Add-only records.** Targets, goals, milestones and steps can be made but not renamed, removed or undone.
3. **Depth.** The daily screen (Today) is three levels down, a target four, and Plan's page shows its first task below the fold.
4. **Six small defects** that make buttons do nothing or lose data silently (section 2).

## 2. Defects (wrong today, small to fix)

| # | What happens | Where |
|---|---|---|
| D1 | **"Add a task" does nothing in four empty states.** Next 7 days, Done, Targets and All tasks set `addTaskRequested`, but only Today, Inbox and a list have a quick-add box to receive it. The flag stays set, so the keyboard then opens by itself on the Planner menu when the player goes back. In Targets the label is also wrong (it should add a target, and the field is right above it: `addAbove` exists for this). | `Planner.kt:176`, `Frequency.kt:120`, `AllTasks.kt:83` |
| D2 | **"No date" also deletes the deadline.** The chip under "Move to" clears `planned`, `plannedTime` and `due`, though the deadline is edited on another tab and the label says nothing about it. | `Planner.kt:552` |
| D3 | **A blank title on Save keeps the old title and says "Saved."** (DR) | `Planner.kt:623` |
| D4 | **Unsaved edits are lost without a word.** The Edit tab holds title, notes, difficulty, category, deadline and estimate until Save; switching to When or Steps, or collapsing the card, drops them. The When tab saves at once, so the two tabs of one card follow opposite rules. | `Planner.kt:598-630` |
| D5 | **"Won't do" has no undo and the task disappears from every list.** Delete shows the undo strip; Won't do does not. Done lists only `DONE`, so the task can be found again only in All tasks with Status set to Any. | `Planner.kt:633`, `Planner.kt:108` |
| D6 | **Stale direction.** Progress says "Add one under Me, Goals"; Goals is in Plan's menu. | `Growth.kt:141` |

Also wrong against the design rules (section 7 has the visual list): the Targets add key and the calendar's "today" marker are red, and three screens carry explanatory captions.

## 3. Navigation and information hierarchy

**3.1 Two menus before any task.** The path to Today is Modules, Plan, Planner, Today: four taps, three levels. Plan's menu (Planner, Focus, Progress, Goals, Review, Settings) leads to the Planner's menu (Today, Calendar, Inbox, Next 7 days, Targets, Done, All tasks, Lists). The spec (3) asks for a small, stable structure with **Today as the default landing view** and five destinations: Today, Inbox, Upcoming, Lists, Focus.

Recommendation: one menu. Opening Plan lands on Today (quick add, From before, Today, This evening, today's targets, a compact Focus entry). Its menu cards are Inbox, Upcoming, Lists, Calendar, Focus, and one "More" card for Progress, Goals, Review, Done, All tasks and Settings. This keeps the card menu rule (`Menu.kt`) and removes a level.

**3.2 Plan's page shows no task on the first screen.** In `lead-light-plan.png` the stage ends at about 500 px and the party panel at about 1,180 px of a 1,080 px screen; "Today" starts below the fold. (DR finding 2; the compact stage shipped in 0.168.0.) The party panel should also fold to one row here: the party is managed on the Pokémon tab and its perks line is a caption.

**3.3 The same information is shown three ways.** Today on Plan's page (`NextUp` plus two rows plus "N more in the Planner"), Today in the Planner, and the Today tile's count. If 3.1 is taken, Plan's page list goes and `NextUp` ("Focus on this", "Pick for me") moves to the top of Today, where it is currently missing: **"Pick for me" and "Focus on this" exist only on Plan's page, not on the Today list or a task card.**

**3.4 Redundant or overlapping destinations.**
- **Inbox** is "every open task in no list, whatever its date" (`TaskViews.inbox`). For a player with no lists, Inbox, All tasks and the sum of the dated views are the same set, and planning a task never empties the Inbox. The spec (3.2) means captured and unprocessed. Recommendation: Inbox is no list **and** no date; a dated task with no list simply lives in Today or Upcoming.
- **Next 7 days** and the Calendar's Week ("Next 7 days" is its subtitle) show the same span. Keep the list as Upcoming, drop the 7-day cap (group "Later" as lists already do through `byWhen`), and put a Calendar key in its header, as the spec suggests ("Calendar should be accessible from Today or Upcoming").
- **Done** and **All tasks with Status: Done** overlap. Done can become a filter preset of All tasks, and show "Won't do" too (D5).
- **Review** has a card that says "done this week" for six days in seven and opens a one-line screen. Fold it into Progress as a card that appears when a review is ready, keeping the badge.
- **Plan settings** has three controls; they are also reachable from Me, Settings. A key in the Focus setup ("Usual length") and the calendar's own header ("Calendars") would retire the card.

**3.5 Targets are hidden.** A daily target is four levels down (Modules, Plan, Planner, Targets), appears on no Today list, on Plan's page, on the Home tab or in a widget. The spec (3.1 item 5) lists "recurring tasks due today" on Today, and (5) says there is no separate Habits section. Recommendation: targets not yet met for the period show as a group on Today with the same check key; the Targets screen stays for managing them.

**3.6 Names.** The enum says `DAY`, the tile "Calendar"; `UPCOMING` is "Next 7 days"; the screenshot test still calls Targets "habits". User-facing terms drift too: "Steps" (card) and "subtasks" (spec), "Targets" and "habits" in Review's summary ("Read: 3 days"). (DR lists the app-wide pairs.)

## 4. Task management

**4.1 The card.** Tapping a row opens three tabs in place (When, Steps, Edit). It works, but the When tab is five stacked chip groups, about 1,000 px tall in `task-card-when-light.png`, inside a list; the task below is pushed off the screen and the card's own Done key scrolls away.
- Lead with the two things done most: the day and the list. Put Time, Remind me and Repeat behind one "Time and repeat" row that shows its current value ("9:00, 10 min before, weekly") and opens on tap.
- The line "C: Medium, Personal" under the title restates the badge and is a caption; drop it, or show the category dot on the row instead.
- A finished task shows only Steps; it cannot be deleted or have its notes read unless it had notes. Give it Reopen and Delete.

**4.2 Priority and rank.** Every row leads with a large rank letter (E to A). It is difficulty and reward tier, not urgency, and it is the heaviest thing on the row, heavier than the title. The spec (4.4, 10.1) wants None, Low, Medium, High priority that "must not visually overpower title/date information", and the completion control first. (DR) Recommendation: the check key moves to the left as the row's first element (the spec's order: completion control, title, date); rank becomes a small tag in the meta line; priority, if added, is a thin left edge in the row, the way the timeline blocks already carry category colour.

**4.3 Steps.** A step cannot be unticked, renamed, removed or reordered, and the cap of 5 is silent: the field just disappears. (DR) "Step done" is a text button where every other completion is the check key.

**4.4 Category against list.** A task has a category (six fixed, coloured) and a list (user-made, coloured). Both appear as colour on the same row and both are filters. Category drives the timeline colour and Progress; list drives grouping. That is two overlapping grouping systems with two palettes. Recommendation: let a list carry a default category, so filing a task in "Thesis" sets School, and show only the list's dot on rows.

**4.5 Missing.** Manual order, bulk actions (select several, move or finish), duplicate, a task to event conversion (spec 3.2), and task linking to a goal from the task card (the store has `unlinkTask`, the card has no goal row). (DR for the first two.) Estimates step by 5 minutes from 0 with no presets; 15, 30, 45, 60 chips would match the focus lengths.

**4.6 Quick add.** Strong. Gaps: the draft is not kept (DR; spec 4.6); the parser's syntax (`#List`, `!B`, `30m`, "tonight") is discoverable only by guessing, so the first empty Today could show one example as placeholder text ("Call mum tomorrow 5pm"); and on the Planner menu a task added goes to the Inbox with no sign of where it went (say "Added to Inbox" in the existing toast line).

**4.7 Lists.** Fine for lists. The spec's project half (3.5: status, target date, milestones, progress) is not there, and Goals duplicate part of it (see 6.3). Deleting a list had no confirm (the undo strip covers lists and events since 0.168.0). "Edit list" is a text key at the top of the list above quick add; it belongs in the header's corner.

## 5. Scheduling and the calendar

**5.1 Picking a day is the module's largest friction.**
- From a card: Today, Tomorrow, "Next week", No date. No other day can be chosen.
- "Next week" is today plus 7 days, not next Monday, and after choosing it nothing on the tab says which date it became.
- The deadline: three presets, then "-1 day / +1 day" nudges. The 28th from the 11th is 17 taps. A deadline's time is always 23:59 and cannot be set.
- A goal's target date nudges by 7 days.
- Quick add's chips are Today, Tomorrow, Weekend, No date: a third vocabulary ("Weekend" here, "Next week" on the card).

Recommendation: one shared day picker (a month grid in a dialogue box, pixel keys, with the preset chips above it) used for Move to, Deadline, quick add and goals. `PixelDatePicker` (`screens/DatePicker.kt`, 0.168.0) is already used by Heal, Mint, Home and Link; PLAN is the one dated module that does not use it, and should take it rather than a second one. One preset set everywhere: Today, Tomorrow, This weekend, Next week (Monday), Pick a day, No date.

**5.2 The calendar's window.** Day view: today to 13 days ahead (`DAYS_AHEAD`), never yesterday. Week view: exactly two pages ("Next 7 days" and "The week after"), starting on today rather than on the week's first day, so the Week ignores the "Week starts on Monday" setting that Month obeys. Month view scrolls freely but "Open day" works only inside the 14 days. So an event next month can be seen in Month and not opened, and last week cannot be looked at at all.
Recommendation: Day and Week page freely both ways (repeating tasks beyond the generated 14 days can show as ghost occurrences or simply not at all); Week starts on the set first day.

**5.3 Time entry.** Four fixed times (9, 12, 15, 19) and 15-minute nudges; 7:30 is six taps from 9:00. Reminders have four fixed offsets, counted from a time that defaults to 9:00 when the task has none, which the note states but few will expect. A typed or wheel time in the same picker as 5.1 fixes both.

**5.4 The tray.** "To plan" sits above the hours and scrolls sideways, which is right. Its hint line ("Hold one and drag it to an hour, or tap it, then an hour.") is shown every time, for ever; show it until the first successful placement. In `planner-day.png` three overlapping blocks at 9:00 clip their titles to "Call the dent…"; a tap on a clipped block should show its full title in the picked panel (it does for tasks; check events).

**5.5 Day load.** "3 h 45 min planned · 11 h 45 min awake" assumes 8:00 to 22:00 for everyone and counts every task without an estimate as 30 minutes. Either make the waking window a Plan setting or drop "awake" and show only planned time.

**5.6 Repeats.** Four presets (Daily, Weekdays, Weekly, Monthly). The engine can do every N weeks, chosen weekdays and yearly; the card cannot, and shows "A custom repeat. Choose one above to replace it." The spec's scopes (5.6: this one, this and future, all) are not offered: moving one occurrence or editing its title has no stated scope. At least chosen weekdays (the Targets form already has the weekday chips) and a visible "this one only" note on a moved occurrence.

**5.7 Targets.** A target can be added, ticked and unticked for today only. It cannot be renamed, changed, paused, archived or deleted (no store action exists), and yesterday cannot be ticked late. The row has no history; a small 7-cell week strip in pixel cells would replace the text "2/3, 3 times a week, Health". Difficulty cannot be set (always C).

## 6. Focus, Progress, Goals, Review

**6.1 Focus setup.** In `focus-setup.png` the egg stage takes the first 250 px, "Working on" is below the fold, and a "How it works" box of four bullets is shown on every visit. Start is pinned, which is right, but it is an outlined key of the same weight as "Just 5 minutes" under it; the primary action should be the one filled key.
- Order: Working on (the linked task is the reason most sessions start), length, Deep Focus, then Start. Shrink the stage to a strip.
- "Working on" offers only the first four of today's tasks (DR); it needs "another task" opening the Today list.
- "How it works" moves to Help or shows until the first finished session.
- "Hatching in: Plan Region" is a caption; drop it or make it the stage's label.

**6.2 Focus running and after.** The running view does not name the task being worked on. "Give up" ends the session and loses the egg in one tap with no confirm, next to Pause; strictness is the rule (spec 6.3), an accidental tap is not. There is no session history (spec 6.4) and no break or next-session offer after a hatch; the end screen offers only "Meet" and "OK". The ongoing notification, break cycle and allow list are already decided in the review (DR item 9).

**6.3 Goals.** Add-only: a goal cannot be renamed, re-dated, put on hold, archived or deleted, and a milestone cannot be renamed, removed, reordered or unticked, though ticking one pays a Super Candy with no undo window (tasks get one). Finished goals stay in the list for ever at full size. The empty state is a paragraph, against the "title and one action" rule. Goals and Lists overlap as "a group of work with progress": the spec's model (3.5, 9) is a list that can be a project, and a goal that may link to one. Recommendation: do the edit and archive actions first; then let a goal link to a list so its progress is that list's tasks, rather than building projects as a third thing.

**6.4 Progress.** The only charts in the app, and clear. Each card opens with a two-line caption explaining what the chart is ("The last 30 days, today included: …", "Minutes from finished sessions only; …"); these are the captions the design rules remove. Move the definitions to Help. The six-week bars have no baseline or scale, so a week of 1 and a week of 0 look alike (4 dp against 0); give zero no bar.

**6.5 Review.** One screen, once a week, then a dead end ("This week's review is done. See you next week!"). The note typed there is saved and never shown again anywhere. "N not finished yet" does not list them, yet the toggle carries them all to tomorrow in one go. Recommendation: list the unfinished tasks with per-task Move, Later and Won't do; show past reviews under Progress.

## 7. Visual consistency

Checked against DESIGN_SYSTEM 5.1 and 12A.

| Issue | Where | Fix |
|---|---|---|
| Red used as chrome | Targets add key (`Frequency.kt:87`); today's date cell in the day strip and Month (`DayPlan.kt:467`, `MonthView.kt:178`); event delete keys use `Pixel.Red` where task and list delete use `Pixel.Danger` | Plan accent for the add key and for "today"; one danger colour for every delete. The red "now" line on the hours is a calendar convention and can stay if the owner agrees. |
| Chips ignore the module accent | Every `TabButton` in PLAN is blue on amber frames (`Tabs.kt:38` default) | Fixed in 0.168.0 (`TabButton` takes the frame tone); check PLAN's cards in a fresh screenshot. |
| Captions that restate the screen | Card line "C: Medium, Personal"; "Hatching in"; the tray hint; "How it works"; Progress card notes; the Goals paragraph; repeat notes ("Every day, starting with its day.") | Remove, or show once. |
| Sections outside a dialogue box | Task groups, list rows and the calendar panel are plain bordered panels (`Pixel.Panel` plus a hairline), while quick add and empty states are dialogue boxes | Decide one: the spec (10.1) allows rows without a card each, but the group panel should be the shared box so a screen does not mix two frames. |
| Row centre line | `NextUp` stacks the check key alone on its own line under the meta row | Put the check key on the title's row, as `TaskRow` does. |
| Primary action weight | Focus "Start" outlined (6.1); "Save" on the Edit tab outlined green | One filled key for the screen's primary action. |
| Zero reads as eight | "60 min", "9:00", "10 min before" | DR finding 1 (0.168.0 changed the zero; check these three). |
| Menu tiles | Tiles are an icon, a count and a title, which follows the rule. The seventh tile leaves a hole in the grid; All tasks is the only grey one | Resolved by 3.1; otherwise make search a field at the top instead of a tile. |
| Month cells | Task chips clip mid-word ("Finish you", "Pay the r") | Ellipsis, or dots with a count for tasks and bars only for events. |
| Dark palette | `dark-planner.png` and the dark task card hold up; the Edit tab's Save and the bottom keys are low contrast (green outline on navy) | Covered by the filled primary key. |

## 8. What to keep

Quick add and its "Understood" line. The undo window on completion and the undo strip on delete. Today's grouping and "Move to today". The day timeline with drag, resize and 15-minute snap, and the tray. Lists with colour and a real item icon. Read-only phone calendars. "Just 5 minutes" and the retry after a cold egg. No streak on a card until there is one. Empty states with the sleeping buddy.

## 9. Suggested order

**A. Fixes (one small PR):** D1 to D6; red off the Targets key and the today marker; one delete colour; a confirm (or a 3-second hold) on Give up; name the task on the running focus view.

**B. Dates (one PR):** `PixelDatePicker` plus a time picker for Move to, Deadline, quick add, goals and reminders; one preset vocabulary; the calendar pages freely and Week starts on the set day.

**C. Edit what can be added:** targets (rename, change, archive, delete, tick yesterday), goals and milestones (rename, re-date, archive, delete, untick inside an undo window), steps (untick, rename, remove), finished tasks (delete). Store actions and tests first, then the screens.

**D. Structure (needs the owner's yes, then `MODULES/PLAN.md` section 3 first):** Plan opens on Today; one menu; targets on Today; Inbox means no list and no date; Upcoming without the 7-day cap; Review and Done folded in.

**E. Card and row:** check key first, rank as a tag, the When tab folded to day and list with a "Time and repeat" row, edits saved as they are made (or a kept draft with a "not saved" mark).

**F. Depth already in the review's list:** priority, manual order, bulk actions, focus notification, breaks and history, projects through goal to list links.

## 10. Owner decisions needed

1. **Does Plan open on Today** with a single menu (3.1), or keep the lead stage page first?
2. **Inbox:** no list and no date (the spec's meaning), or keep TickTick's "no list"?
3. **Rank on the row:** keep the large letter first, or check key first with rank as a small tag (4.2)? This also decides where priority would go.
4. **Category and list:** keep both on every task, or let the list set the category (4.4)?
5. **The red "now" line and red "today"** on the calendar: allowed as a calendar convention, or Plan's accent?
6. **Goals and projects:** link a goal to a list (6.3), or build projects separately as the spec's 3.5 describes?

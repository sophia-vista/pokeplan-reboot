# Changelog

Versions are `MAJOR.MINOR.PATCH` ([semantic versioning](https://semver.org)), kept in the `VERSION` file. While the app is
below 1.0, a new feature bumps the minor number and a fix bumps the patch number. Bump `VERSION` in the pull request that
ships something; the first merge with a new number publishes that version's own GitHub release (the rolling "Latest build"
release always has the newest merge).

## 0.16.1

- Focus keeps your egg when you lock the screen or a call comes in, as it always should have: put the phone face down and the timer runs on, and a call pauses the timer until you come back. Leaving the app or moving the phone in Deep Focus now counts on every tab, not only on Focus. The Focus screen spells out the rules, and the timer shows the minutes focused, with paused minutes apart.

## 0.16.0

- The Pokédex tells you where each Pokémon stands: **Owned** (in your party or PC now, with a Poké Ball on it), **Caught** (yours once, since transferred), **Seen** (a friend's buddy or a quest's Pokémon, still a grey shape but named when you tap it), or ??? for ones not found yet. Each tile says so in words, and screen readers read the number, name and state.

## 0.15.0

- Your own events: New event on Planner → Calendar adds an appointment or a block of time that isn't a task, like a lesson or lunch. Give it a name, tap an hour (or nudge it by 15 minutes) and Add. Events sit on the hours in teal, drag and resize like tasks, and count towards how full the day is; tap one to rename it, change its length or delete it. They never pay, and they sync with the rest of your save but stay in PokéPlan.

## 0.14.0

- Drag to plan: Planner → Day is now Planner → Calendar, with Day and Week. Hold a task (or just drag it with a mouse) and drop it on an hour to plan it there, drag a task on the hours to move it, or drag its lower edge to change how long it takes. Everything snaps to 15 minutes and the page scrolls as you drag. Week shows the next seven days side by side, so tasks can be dragged between days; tap a day's name to open it. Tapping still works too, and a picked task now has Shorter and Longer.

## 0.13.0

- More of the Mystery Dungeon look: your buddy curls up and sleeps on Home and the widget while a focus session runs, hops when you pet it and munches a berry you give it, all with its own SpriteCollab animations (the few that don't have one hop as before). Its portrait shows how it feels: happy in the corner of Home when a task is done, overjoyed at a hatch, awed by a shiny, determined beside the Focus timer and surprised if you leave. Each Pokémon's page shows its portrait too. The artists' credits moved to the Pokédex (tap a caught species), in smaller, fainter text.

## 0.12.0

- Your calendar on the Day timeline (Android): Me → Settings → Calendars lets PokéPlan read the phone's calendars, Google Calendar included, and you tick which to show. Their events sit on the hours next to your tasks in the calendar's colour, all-day ones above, and the time they take counts in how full the day is. They're read-only and never pay.

## 0.11.0

- A new look for every Pokémon: the Pokémon Mystery Dungeon style sprites of the PMD SpriteCollab, idling in place on Home, the widget and each Pokémon's page and standing still in lists, the Pokédex and the PC (shinies included). The few forms SpriteCollab doesn't have yet keep their old sprite. Each Pokémon's page credits the sprite's artists.

## 0.10.0

- Egg tickets: when you are signed in, your phone keeps a stock of 20 eggs the server has already rolled the dice for, so a focus egg hatches the same way with or without a connection and failing a session never rerolls one. Each sync reports the tickets you used and tops the stock back up; if you run out offline, eggs still hatch as before. (Needs the new database migration and the reward function deployed.)

## 0.9.0

- Planner → Day: a timeline of your hours. Tasks without a time (and the inbox) wait at the top; tap one, then tap an hour to plan it there, and fine-tune with +15 and -15. Tap a task on the hours to move it or take its time away. Blocks show the category's icon and last as long as the estimate (30 minutes without one), tasks at the same time sit side by side, a red line marks now, and a line above says how full the day is. Earlier and Later switch days, up to two weeks ahead.

## 0.8.1

- A cleaner interface: four text sizes instead of seven (none below 12 sp, widgets included), no coloured strips on the left of cards, nested corners that line up, commas instead of "·" separators, and shorter copy with the lines that only repeated a title or a button removed. The Music slider, which did nothing yet, is hidden until music ships; "Haptics" is now "Vibration".

## 0.8.0

- Settings → Your data: export everything as a JSON file (the share sheet on Android, Downloads on desktop, a download on the web), delete your account and its cloud data for good, or erase this device and start over. Both deletions ask twice.

## 0.7.0

- Tasks have a time: open one and pick At 9:00, 12:00, 15:00 or 19:00 and nudge it by 15 minutes; the card shows the time and whether a reminder is set.
- A reminder can be snoozed until tomorrow at 9:00 as well as for an hour.

## 0.6.1

- The Buddy widget keeps its idle animation while it reacts to a pet or a berry (it used to freeze on one picture until the emoji went away), and a still buddy (Reduce motion, during focus) stands on its shadow instead of floating above it.

## 0.6.0

- Task reminders: open a task with a day and pick up to three under Remind me (at the time, 10 minutes, an hour or a day before). The notification has Done and Snooze 1 hour. Reminders wait during quiet hours (22:00 to 07:00 by default, Settings), a focus session and rest days, and are set again after a restart. Android asks once for permission to notify.

## 0.5.0

- Pokémon evolve at their real game level (Charmander at 16, Charmeleon at 36) instead of half of it. Levels cost Rare Candies on a curve like the games' experience: 2 a level at first, more at higher levels (8 at 30, 18 at 50, 67 at 99). Pokémon that already evolved stay evolved.

## 0.4.0

- Every finished task now pays Rare Candies; a Super Candy bonus comes on top (10% for rank E up to 40% for rank A, raised by the Super Candy chance perk) instead of replacing them.

## 0.3.1

- Fix: the Buddy widget froze on a still picture after a pet or a berry (and the Today and Focus widgets could show old data for a while). Glance only recomposes a widget that is still showing, so the widgets now load their data again on every update.

## 0.3.0

- Dark mode: Settings has Match phone (the default), Light and Dark; the dithered page background turns deep navy and the Android navigation bar icons follow.
- The PC boxes have a search box and a Sort and filters panel (newest, level, dex number, name, friendship, IVs; type, shiny, starred).
- A Help screen under Me with tap-to-expand questions and answers.
- A shiny buddy glints on Home (four small stars that flash one at a time; off with Reduce motion).
- The Today and Focus widgets use the buddy's scene instead of the box wallpapers.

## 0.2.0

- The Focus widget shows a live countdown; the Today widget has a "+ Add" button; long-press the app icon for "Add task" and "Start focus" shortcuts; the app is single-instance so a widget tap or shortcut never opens a second copy.
- Quick-add chips (Today, Tomorrow, Weekend, No date) on the task box; the bag is grouped by kind; a cloud-save indicator on Home; a one-time "What's new" card after an update.
- Fix: the widget buddy could stay frozen on a still picture after a tap. An alarm now brings the animation back.
- A pixel-dithered page background with a light grain whose colours follow the time of day (dawn, morning, afternoon, dusk, night), always light enough to read.
- Shiny set: a shiny hatch shows a 🌟 reaction and "A shiny X hatched!", shinies are marked in the Party and PC lists, the Pokédex counts and marks the species you have ever found as a shiny (kept after a transfer), and the odds are explained in the Dex.
- Star a Pokémon to lock it against transfer.
- Haptics: the pet tick now uses the phone's vibrator directly (the earlier one followed the system touch-feedback setting and often did nothing) and has a Settings switch.
- The screen stays on while a focus session runs.

- Pokédex: the Home Region and every region are numbered from 1 in their own order; a new National tab (by generation) uses the real National Dex numbers; each list shows caught and shiny counts.
- The buddy's level chip opens its detail screen (app and widget); a light vibration on a pet; the same emoji never repeats twice in a row.
- The scene darkens slowly through dusk and lightens through dawn (in the app and the widget) instead of switching at 8 pm and 6 am.
- The egg on the Focus setup screen wobbles gently; friendlier empty states for party, PC, bag, Guardians, quests and friends.
- Large system text: the tab bar scales up to 1.25x, rank badges keep their size, task titles wrap to four lines.

- System bars match the app: the red header runs under the status bar and the white tab bar under the navigation bar.
- Sound effects and Music volume sliders replace the Sound toggle (music itself comes later).
- The buddy's pet and berry reactions are a wider set of emoji picked from its bond style, type and the time of day.
- The Buddy widget refreshes when the buddy, its level, a berry, a focus session or today's tasks change.

## 0.1.0

The first private build.

- Planner with difficulty ranks E to A, habits, goals, weekly review and rest days; today's tasks, inbox and recurrence.
- Focus timer that hatches an egg (Deep Focus on Android), with strict rules and calm breaks.
- Pokémon: party, PC, Pokédex, bag, items, natures, abilities, shiny, friendship, evolution and Guardians (legendaries and mythicals with quests).
- Buddy on Home: animated Black/White sprites, cries, petting and berries; Android widgets (Buddy, Today, Focus).
- Cloud save with an account, friends, a weekly leaderboard and daily gifts (Supabase), and the server-side reward function.
- Original app icon, wallpapers, Pokémon DP and Nunito fonts.

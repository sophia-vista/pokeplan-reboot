# Changelog

Versions are `MAJOR.MINOR.PATCH` ([semantic versioning](https://semver.org)), kept in the `VERSION` file. While the app is
below 1.0, a new feature bumps the minor number and a fix bumps the patch number. Bump `VERSION` in the pull request that
ships something; the first merge with a new number publishes that version's own GitHub release (the rolling "Latest build"
release always has the newest merge).

## 0.8.0

- Settings → Your data: export everything as a JSON file (the share sheet on Android, Downloads on desktop, a download on the web), delete your account and its cloud data for good, or erase this device and start over. Both deletions ask twice.

## 0.7.0

- Tasks have a time: open one and pick At 9:00, 12:00, 15:00 or 19:00 and nudge it by 15 minutes; the card shows the time and whether a reminder is set.
- A reminder can be snoozed until tomorrow at 9:00 as well as for an hour.

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

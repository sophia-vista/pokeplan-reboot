# Changelog

Versions are `MAJOR.MINOR.PATCH` ([semantic versioning](https://semver.org)), kept in the `VERSION` file. While the app is
below 1.0, a new feature bumps the minor number and a fix bumps the patch number. Bump `VERSION` in the pull request that
ships something; the first merge with a new number publishes that version's own GitHub release (the rolling "Latest build"
release always has the newest merge).

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

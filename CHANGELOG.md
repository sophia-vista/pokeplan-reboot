# Changelog

Versions are `MAJOR.MINOR.PATCH` ([semantic versioning](https://semver.org)), kept in the `VERSION` file. While the app is
below 1.0, a new feature bumps the minor number and a fix bumps the patch number. Bump `VERSION` in the pull request that
ships something; the first merge with a new number publishes that version's own GitHub release (the rolling "Latest build"
release always has the newest merge).

## 0.34.0

- Care, the second module, under Modules, inspired by journalling and DBT: small routines you tick off each day, a 1 or 2 minute calm break with your buddy asleep beside you, a daily diary card (how strongly you felt six feelings, any urges you choose to track, the skills you used), a library of DBT skills (Mindfulness, Distress tolerance, Emotion regulation, Interpersonal effectiveness) with short how-tos, and a journal with a question for each day if you want one. Gentle trends, no streaks. Routines, calm breaks and practised skills earn friendship and berries; a diary card or a journal entry never earns anything, and everything stays on this device. Care is on for everyone and can be switched off in Me, Settings, Modules.
- Reward rules version 7: practising a skill pays friendship (doubled for Presence natures) and a berry, up to 3 skills a day.
- The Care Region opens to everyone: 143 gentle Pokémon to hatch, from the first day, with no rank needed.

## 0.33.0

- A new Modules tab, ready for Care and the modules after it. The tabs are now Home, Modules, Pokémon and Me: Home, Pokémon and Me hold what every module shares (your buddy, party, PC, Pokédex, Bag, trainer card, friends and settings), and each module has its own place under Modules. Plan is the first: Planner, Focus, Growth, Goals and Review are under Modules, Plan. The Modules tab remembers what you had open, and widgets, shortcuts and reminders still open the Planner and Focus directly.

## 0.32.0

- The Care Region's dex is chosen: 143 gentle, soothing Pokémon (Chansey, Snorlax, Munna, Comfey, Milotic, Wooloo and more), with Cresselia and Shaymin as its patrons and Goomy and Slakoth as its Super Rare lines. It opens to everyone when the Care screens arrive. Swablu, Altaria, Cottonee and Whimsicott move there from the Plan Region; the ones you already have stay yours.

## 0.31.1

- The Home Region is now the Plan Region, the first of the module regions. Your saves, dex progress and focus history carry over unchanged. The name "Home Region" will belong to the household module's own region later.

## 0.31.0

- Pick for me on Home's Next up card: it picks one of today's tasks for you, and finishing that one pays 10% more candies.
- Daily mini-quests on Home (from Apprentice): three small things a day, like a glass of water or a stretch. A berry each, one more for all three, and one reroll. "Open the task you've been avoiding" starts 5 minutes of focus on your oldest task.
- Just 5 minutes on the Focus screen, for when starting is the hard part.
- Tasks you focused on show how long they took against your estimate, and Growth tells you how your estimates run.
- Rest today: mark a rest day from Home's progress card; your streak waits for you.
- Welcome back: after a week or more away, a gift of Rare Candies and berries, and no scolding.
- The streak day now ends at 4am, so a late night still counts for the day before. Finished work warms your buddy's friendship up to 10 times a day. Reward rules version 6.

## 0.30.0

- Notifications on Android that the design promised. Focus sessions of 15 minutes or more send a quiet "halfway there" note, and every session sends a "time is up" note; neither counts as leaving the app, and both are cancelled if the session fails or is given up. In the evening (20:00 by default) a gentle streak reminder appears if nothing has counted yet today and you have a streak going. On Sunday evening a weekly recap lists tasks finished, focus time and your top category. A task reminder you do not answer comes back up to 3 times, 10 minutes apart, then stops, and reminders now offer a 10 minute snooze (Done, 10 min and 1 hour: Android shows three buttons, so "Tomorrow" left the notification). Each one can be switched off under Settings, Notifications and week.

## 0.29.0

- Editing a task now also sets its category, its notes and its deadline (a day, with Today, Tomorrow, Next week and a day-by-day nudge, or none).
- A task can repeat: daily, on weekdays, weekly or monthly. Pick Repeat in a task's options, or in More options when you add one. Repeating tasks say so in their line, and Never stops the repeat.
- Planner has a new tile, All tasks: search titles and notes, and filter by status, category, list and difficulty.
- New habits can be on chosen days, a number of times a week or a number of times a month (not only every day), and have a category.
- New goals can have a category and a target date, both shown on the goal.
- The weekly review shows where the week's finished tasks went, by category.
- Fix: a repeating task's later days pay only once their day comes, so ticking ahead earns nothing extra.

## 0.28.0

- Trainer ranks come quickly at first and slow down later: Apprentice after a day or two of play (it took over two weeks), Ace in under two weeks, and each rank after that takes longer than the last. Nobody loses a rank.
- Each rank now opens something, shown once on Home in a "Rank up" card: evolution, the Bag and goals at Apprentice; held items, party perks, Growth and coins at Trainer; training items and stats at Ace; Guardians and legendary quests at Veteran. Day one stays simple: tasks, focus, your buddy and your collection. Reward rules version 5. Online: push the new migration (`rank_curve`) and redeploy the reward function.

## 0.27.0

- Training plans are gone: no more choosing focus stats. Finished tasks no longer add EVs; Vitamins from the Bag are how a Pokémon's stats (and the perks they power) grow. Synchronize now gives a little Super Candy chance. Reward rules version 4. Online: push the new migration (`no_training_plans`) and redeploy the reward function.

## 0.26.0

- Party perks you can see: the Party screen shows each perk's total and, opened, where every part comes from (your buddy's types and ability, its held item, the rest of the party, Guardians). Each Pokémon's page says what it gives as your buddy and in the party.
- Held items work: give one from the Bag or a Pokémon's page, swap or take it back. Its perk applies while your buddy holds it, and items like a Metal Coat (now found from tasks) let trade evolutions happen.
- Nature Mint, Bottle Cap, Ability Capsule and Tera Shards can be used from the Bag.
- Magic Guard now keeps its promise: once a week, a failed focus session of 5 minutes or more still hatches its egg. A strong Streak keeper perk forgives a second missed day a week. Reward rules version 3.

## 0.25.0

- Behind the scenes, the Care module's first part (no screens yet): self-care routines, 1 to 2 minute calm breaks, private mood check-ins with an optional note and tags, gratitude and reflection prompts, and rolling trends with personal bests instead of streaks. Care keeps its own save, apart from Plan's. Doing earns: a routine grows your buddy's friendship, a calm break adds a berry, and care on 4 days of a week ("kept a rhythm") gives 3 berries, each with a little trainer rank, capped each day. A mood, a note or a reflection never earns anything. Reward rules version 2. Online, Care's copy is opt-in and only yours (`care.saves`); push the new migration to add it.

## 0.24.2

- Fix: undoing a habit and ticking it again the same day no longer pays twice. A habit now remembers the last day it paid, apart from the days it was done.

## 0.24.1

- Behind the scenes, before a second module: the cloud save is now Plan's own (`plan.saves`), so the shared game tables hold only the game; every reward the server records says which version of the reward rules made it (rules version 1); and `docs/economy.md` lists every reward source, use and cap. Nothing changes in the app. To take effect online, push the new migration, then redeploy the reward function.

## 0.24.0

- Your own events can repeat: daily, on weekdays, weekly, every 2 weeks, monthly or yearly, timed or all day ("Gym" every Monday, "Home" every weekend). Pick it under Repeats in New event, or tap an event to change it. A repeating event never ends unless you set an end, then ‹ › moves the end one occurrence at a time. Changes in the event panel are made to every one; dragging one, or tapping another hour, moves just that one. Delete asks whether to delete this one or all. Changing how a later one repeats leaves the earlier ones as they were.

## 0.23.0

- More Pokémon sounds. The buddy cries when Home celebrates a real event (a task done, a hatch, a shiny) and when you make a Pokémon your buddy. Five real game sound effects (from the same package as the display font, in `assets/sfx`) each mark one kind of moment: the menu select blip when a task is ticked off, a hop for each rock of a hatching egg, the healing chime when a berry or item is given, the run-away sound when a Pokémon is transferred, and the Pokémon Center jingle after an evolution's cry. They follow Sound and its volume in Settings, play beside a cry without cutting it off, and are silent on desktop as the cries are.

## 0.22.0

- A Month view in the Calendar (Day / Week / Month), after TickTick and Google Calendar: events over several days are one solid bar across them, one-day all-day events are short bars, and timed events and tasks sit on their day. Tap a day to see its agenda under the grid, and Open day to plan its hours. In the Week, all-day events now run across their days as one bar too.
- Your own events can be all day, over one day or several ("Home" for a weekend, "WFH" for a day): pick All day in New event, then its first and last day. Every event can have a colour. Tap an event's bar to change it.

## 0.21.0

- Lists in the Planner, after TickTick. Make a list for each part of your life (a name, a colour and a real item icon) from the new Lists section under the menu's tiles. The Inbox is now every task that isn't in a list, whatever its date. A list and the Inbox group their tasks by when they are: From before, Today, Tomorrow, Next 7 days, Later and No date. Add straight into a list, type `#Groceries` in any quick-add, or move a task from its options. In Today and Next 7 days (was Soon), each task shows its list's colour and name. Lists can be renamed, recoloured, archived or deleted (their tasks go back to the Inbox). The Calendar's "To plan" card now calls tasks with no date "No date".

## 0.20.0

- The Pokédex tracks forms. Each caught Pokémon's entry lists all its forms (regional variants, female forms, Megas, Gigantamax and more) with a Regular / ✨ Shiny switch. Forms you have had are in colour, the rest are black shapes. Tap one you have had to see it in the scene with its own types, abilities and stats. A form counts once you have had a Pokémon in it (a Mega while it holds its Mega Stone, a female form for a female) and stays counted after a transfer. Gigantamax and other special forms can't be found yet, so they are faded and don't count. Each region's list shows how many of its forms you have.

## 0.19.5

- Each caught Pokémon's Pokédex entry now shows it in a real scene for its type, the same ones buddies get on Home: Water Pokémon on the beach, Grass and Bug in the forest, Electric on the thunder plains, and so on. Species you have only seen keep the plain wallpaper, so the scene never gives their type away.

## 0.19.4

- Pokémon in the Pokédex no longer lose their animation (or show nothing) after you page through entries quickly with Next: a sprite whose loading was cut short is no longer remembered as missing. While an entry's animation loads, its space stays empty for a moment instead of flashing the still sprite in another art style.
- Tapping a Pokémon's sprite to hear its cry (on its page and in the Pokédex), a Pokédex tile, an evolution in an entry or a PC tile no longer flashes a grey highlight over it.

## 0.19.3

- The Planner's menu and lists are redesigned after well-regarded to-do apps (Apple Reminders' smart lists, Things' Today and Logbook, Todoist's Upcoming). The menu is one "Add a task" field (the day chips show up once you type) above six smaller tiles that each show their count, so all of them fit on one screen. Today shows how the day is going with a thin bar, then the tasks from before with a "Move to today" button, today's tasks, and "This evening". Soon is grouped by day and Done by the day you finished each task. Rows in a group share one panel and no longer repeat the date the group is already on.

## 0.19.2

- Your buddy no longer shrinks for a moment when you pet it or give it a berry: its hop and bite play at its usual size. The scene on Home is a little taller so the whole jump fits. Tapping a Pokémon in your party no longer flashes the row.

## 0.19.1

- The Calendar in the Planner is redesigned after well-regarded calendar apps (Fantastical's day ticker, Apple and Google Calendar's grid, Structured's inbox). The day or week and two arrows sit on one line, Day and Week are one switch, and a "Today" button appears once you move away. A day opens with a strip of the week's days, a dot under each day that has plans. Tasks still waiting for an hour share one "To plan" card, each a chip with its icon that scrolls sideways, so the hours start much higher up. The hours sit on their own panel with labels on the lines, a faint half-hour line, a red now-line with a dot, and all-day events in a band at the top. Blocks have a coloured strip on the left, a bolder title and their start and end time; in a week they show their title on one line when there is room, never broken letter by letter.

## 0.19.0

- Easier to use, after a review of every screen against the Laws of UX (`docs/laws-of-ux-review.md`). Back now sits at the top left of every page, with the page it returns to written under the title. Settings are switches you can tap anywhere on the row, sorted into small groups (Focus, Sound and touch, Look, Reminders and week). Undo for a finished or deleted task now floats just above the tabs, near your thumb, on every tab, so nothing on the page jumps when it appears. Focus keeps a "Start 25 min" button pinned at the bottom, picks the task to work on with radio rows, keeps Give up quiet, and after a hatch offers "Meet" the new Pokémon. The keyboard's Done key adds a task, a step, a habit, a goal or a milestone. Overdue tasks are marked in calm amber instead of red. Empty lists show their item icon, goals say how many milestones are done, the Bag shows a Use button on items you can use, Transfer and removing a friend stay quiet until you confirm, and the berry button on Home is easier to hit.

## 0.18.0

- Eggs hatch like in the games: "Oh?", the egg rocks three times, harder each time, a flash, and your new Pokémon is there and calls out, with its category ("the Seed Pokémon"). Shinies flash gold, twice. It plays once for every new egg, from a focus session, a Task Egg or a task ticked on the widget; tap or Skip to jump ahead, and Reduce motion goes straight to the Pokémon.
- Evolving plays its own scene: "What? Charmander is evolving!", the Pokémon turns white and flickers between its old and new shape, faster and faster, then a flash and "Congratulations!".
- Each Pokédex species has its own page: its category, height, weight and entry, its types, abilities (the hidden one marked), base stats, and its evolution line with how each step works in PokéPlan (Lv. 16 and a Super Candy, a Fire Stone, a Link Cord...). Tap its sprite to hear its cry, a member of its line to open that one, or Previous and Next to flip through the list. The list itself has a completion bar and All, Caught and Not caught filters, and keeps its place when you come back.

## 0.17.1

- Easier to read and tap: green and blue text is a little darker so it stands out on cards (and red a little lighter in dark mode), every button is at least 48 dp tall, and the starter picker, PC tiles and the opening dialogue tell screen readers what they are and what a tap does.

## 0.17.0

- Clearer numbers under Me: the trainer card says which totals are all time and how the streak counts. Growth shows the last 7, 30 or 90 days, with each area's finished tasks and focus minutes beside its share (quiet areas are listed on one line instead of as empty bars), weekly bars for tasks and for focus with the week under each bar, and your goals' progress kept apart from tasks and time. Weekly charts follow your week start setting.

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

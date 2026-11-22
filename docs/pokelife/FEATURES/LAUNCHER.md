# PokéLife Launcher

**Version:** 1.0  
**Status:** Approved specification  
**Scope:** PMD-inspired in-app home environment and optional Android default-home replacement  
**Approved:** 5 November 2026

> This document is the authoritative specification for the PokéLife home environment and optional Android launcher. It defines target behavior, not a claim that every capability is already implemented. The Product Bible owns product-wide principles; the Implementation Guide tracks source status and delivery.

## 1. Purpose and product direction

PokéLife Launcher is a persistent, interactive Pokémon Mystery Dungeon-inspired living environment that can also serve as the user's Android home screen. It must feel like arriving in a familiar Pokémon settlement, not opening a dashboard decorated with pixel art.

The launcher has two equally important responsibilities:

1. **Immersion:** a coherent environment inhabited by the user's Pokémon, with a recognizable buddy and restrained ambient activity.
2. **Utility:** immediate, dependable access to applications, PokéLife modules, search, widgets and system navigation.

The user must never need to walk through the environment, wait for an animation, interact with a Pokémon or open a game-like menu to perform an ordinary phone action. The launcher is optional; PokéLife remains fully usable as a conventional application when another Android launcher is selected.

### Non-negotiable principles

- Utility first, Pokémon throughout; visual immersion never obstructs an action.
- More is less: avoid dashboard cards, redundant labels, fixed docks and excessive controls.
- Local-first, offline-capable and no mandatory account or network dependency.
- No needy-pet loop, relationship meter, compulsory interaction or punishment for absence.
- One source of truth for every Pokémon and module record.
- Preserve useful existing launcher behavior and assets; refactor rather than discard without evidence.
- Reliability, accessibility, lifecycle correctness and performance are release requirements.

## 2. Scope and boundaries

The launcher owns its scene configuration, layout, visual simulation state, launcher preferences, app organization and presentation projections. It does not own module business records, Pokémon ownership, party membership, buddy selection, reward rules or reminder scheduling.

The launcher is not:

- A replacement for WORLD's adventure, expedition or settlement systems.
- A second task manager, calendar, journal, finance system or health tracker.
- A notification-listener product that reads unrelated applications' notifications.
- A permanent background game process.
- A requirement for using PokéLife.

## 3. Existing launcher: what to preserve

Preserve where reliable (refactor rather than discard; record behavior, dependencies and migration needs before removing a component): the four-column grid and multiple home pages, movable and resizable widgets, Android AppWidget hosting, town scenes and weather presentation, the app list, drawer, folders and app pinning, gesture handling and launcher settings, and the battery-saver and animation controls. Their implementation is in `composeApp/.../ui/launcher/` and `androidApp/.../widgets/`; the grid's rules are in `docs/launcher-widget-grid.md`.

Asset priority: PMD Pokémon PNGs, portrait strips, cries, sound effects and town, guild, map and environment art first; suitable mainline sprites second; Pixelarticons for interface glyphs only. Respect source licensing and redistribution restrictions.

Widget measurement: a widget is measured against the dimensions its grid cells actually allocate, not a coarse size (section 11.5). Open: verify clipping, reflow and unused space with several providers and sizes on a physical device. Source-level behavior is not proof of device behavior; build verification, lifecycle testing and device profiling remain required.

## 4. PMD living environment

### 4.1 Scene concept and hierarchy

The default home is PokéLife's own recognizable settlement. It should contain a central landmark, a small number of identifiable facilities, connected paths, natural scenery, depth layers and locations suitable for Pokémon to rest or gather. It may evoke PMD's visual language but must not copy an existing game's town layout wholesale.

Visual hierarchy:

1. Environment: terrain, buildings, paths, sky and scenery.
2. Inhabitants: Pokémon sprites and restrained environmental behavior.
3. Functional landmarks: contextual shortcuts to modules and shared areas.
4. Personal surfaces: widgets, folders and shortcuts.
5. System controls: status/navigation bars and launcher overlays.

Buildings and scenery should remain visually legible without permanent labels. Labels appear on focus, selection, onboarding or where accessibility requires them. Avoid large dashboard headers, permanent card rows, fixed docks, excessive floating buttons and constant animation.

### 4.2 Continuous pages and viewport

Multiple home pages form one continuous environment. Horizontal page movement shifts the viewport through a finite scene or connected scene regions. Background layers maintain spatial continuity; Pokémon retain logical world positions; widgets and folders remain anchored to logical positions. A bounded parallax effect is optional and must be disabled by Reduced Motion. Infinite procedural generation is out of scope; a town of a dungeon's tiles is one finite floor laid out each day from the game's own tiles (section 4.5).

### 4.3 Environment presets and atmosphere

Provide a polished default scene before adding many variants. Support a small curated set of built-in environments, day/night presentation based on local time, optional weather presentation, manual atmospheric overrides, seasonal appearance options and optional ambient effects (e.g. rain, leaves or particles). Weather and seasonal effects are cosmetic and must never affect rewards, availability, deadlines or progression.

### 4.4 Weather layers (from Red Rescue Team's own code)

The town's weather imitates the original game's ground-screen weather (pret/pmd-red `src/ground_weather.c`); the numbers live in `GbaWeather.kt`, the layers are rebuilt from the game's files by `tools/weather/decode_bg.py` (needs a read-only checkout of `pret/pmd-red`; no ROM is used), and the per-weather colour lookup is `WeatherGrade.kt` (from `assets/weather/ramps.tsv`, written by `tools/weather/extract_ramps.py`). Weather is cosmetic (section 4.3).

- **Tick:** the game runs at 59.7275 Hz; all timings below count ticks. A tile-animation frame lasts its duration plus one tick; all tile animations have 16 frames and wrap, so a small tile repeats over the screen. Layers are drawn at a whole-number scale without smoothing, and snow and blizzard are fixed to the screen (pinned to the camera).
- **Snow** (W03P01): 5 ticks (83.7 ms) a frame; **blizzard** (W03P03): 2 ticks (33.5 ms) a frame, no veil. Both composite opaque where a flake has a pixel and show nothing of the scene beneath it (`BLDALPHA` 0x1000).
- **Clouds** (W01, kind 9): the game's blue (82, 115, 189) at 4/16 = 25% over the scene (`BLDALPHA` 0x40c, `TEXTURE_ALPHA`), moving 0.5 px a tick right and 0.25 px a tick up (30 and 15 px a second), in whole game pixels, wrapping. Kind 15 (grey texture, W06) is also a quarter, moving right 0.5 px a tick; kind 14 (streaks, W05) moves right 0.25 px a tick.
- **Sandstorm:** no tint; both textures (`sand-1`, `sand-2`, 120 x 32, opaque, so they only work blended) at 25%, scrolling 0.5 and 0.25 px a tick like the two layers that scroll. Their art belongs to the dungeon's sandstorm effect (239); speed and blend are borrowed from the ground layers.
- **Rain:** the 61-frame sheet (240 x 160) is the dungeon's rain effect (16), a shower that builds up (15 opaque pixels at frame 0, 266 at frame 41, 23 at frame 60) and fades; it plays frames 0 to 60 round and round at 70 ms. The game's real frame durations and loop points are not decoded.
- **Colour ramps:** for dungeon-tile towns each tile colour goes through the weather's own ramp (`out5 = ramp[channel] * brightness / 256 & 0x1F` on the 8-bit value `v5 * 8 + 7`), a lookup and not a tint (a straight line is off by up to 31 of 255). Clear is the identity. What each does to black: sunny lifts to 15; sandstorm to 63 with blue crushed (white becomes 255, 255, 207: yellow-brown); cloudy to 31, 31, 63 and white to 231, 231, 255; rain sinks red and green and lifts blue (0, 0, 39); hail 0, 15, 31; fog flattens to grey (black 87, white 239); snow 0, 23, 39 and white 231.
- **Open:** decode the rain (effect 16) and sandstorm (239) effect animations; the game takes about a second to move to a new ramp while the town changes at once; ground kinds 0 to 8 (map-specific layers), the dungeon's sunny and fog weathers and weather transitions are not used.

### 4.5 The dungeon floor (REP-052)

A town of a dungeon's tiles is a Mystery Dungeon floor that the app lays out from the game's own tiles: the code arranges real tiles, it never draws any (`DungeonFloor` in `launcher/DungeonFloor.kt`). Scenes (whole pictures) keep their ground band.

- **Layout:** rooms joined by corridors, walls everywhere else, as on the games' floors, across every page as one floor. Rooms are big: two columns a page with two rooms each (three on a tall screen), each most of its column's width and four to ten squares tall, and now and then one as wide as the page; a room may sit anywhere down its column, two squares of wall from the next. Corridors join every room to its nearest (so every floor square reaches every other), with a few more for loops. Some rooms hold a pond and some wall holds water, from the tileset's own water. The floor's edges are wall. Of a few tries, a room takes the place that shows the most floor no widget covers, so the rooms fill the open bands between the widgets; a square counts as under a widget if any of it is.
- **A new floor each day:** the seed is the tileset and the local date, so the floor is the same all day; Launcher settings → Town → New layout gives another one for the same day (the finds stay the same, see below).
- **Shapes:** with a tileset's full autotile set (`assets/tiles/ex-NNN-auto.png`: the 47 neighbourhoods a square can have, the game's three variants of each, cut by `tools/tiles/extract_sky_tiles.py`) walls and water take any shape, with ragged room edges and irregular ponds. A tileset with only the 3 x 3 block (the Red Rescue Team ones, until their full sets are cut) keeps rectangles and walls at least two squares thick.
- **Walking:** a Pokémon walks only on the floor, along a path over its squares. Water types swim and Flying types fly over water; only Ghost types pass through walls, as in the games. Widgets are soft walls: nobody stops, rests or meets under a widget, and a path goes round one when it can; it passes behind only where there is no other way (a widget row as wide as the screen).
- **Stairs:** each floor has the game's stairs. A Guardian that arrives comes up them; one that leaves goes down them.
- **Traps (cosmetic only):** a few of the games' gentle traps lie hidden on the floor: Warp, Spin, Slumber, Gust, Slow, Trip and Poison. Any trap a Pokémon steps on springs, every time, as in the games; it is then shown for the day, and the Pokémon step round the traps they know about when there is another way, and never stop on one. On Home each plays the game's sound, and the traps that have an effect of their own in the game play it at the Pokémon's feet (Spin's swirl, Gust's wind, Slow's falling light: `assets/tiles/trap-*.png`, the game's `TRAP_ANIMATION_INFO` table, cut by `tools/sprites/extract_sky_effects.py`; Slumber's is of a kind not yet drawn, so it keeps the sleep icon). What it does is for show: it warps the Pokémon to another room, spins it and leaves it **confused** (about 25 seconds: a question mark over its head, and now and then it stumbles a square to the side and finds its way again), sends it to sleep for a moment, blows it away in a random direction until it meets a wall or another Pokémon (at most ten squares, as the games' blown-away effect does), slows it for 20 seconds, trips it, or **poisons** it (about 30 seconds: a skull over its head, and it walks at 70% pace). A status wears off by itself with a twinkle (`Condition` in `TownFloor.kt`). A Pokémon is only ever warped to, or started on, a square it can walk away from (no lake in the rock, no hollow in a wall), and nobody picks a square where it would overlap another Pokémon (standing there or walking to it), while the floor has room: the room follows each sprite's frame size (about two squares between ordinary Pokémon, three or more for a Wailord or a Moltres, and a big one covers the squares behind it, not those in front: `TownLife.overlap`); what each does next is never scripted: no two Pokémon act out the same moments, and a crowd standing together has one meeting at a time. **Pokémon are walls to each other** (`DungeonFloor.path(blocked)`): a walking Pokémon goes round the others, one square at a time, and only where there is no way round does it swap places with the one in the way (the other walks to the square it left, as the games' team members trade places); a sleeping Pokémon or one stopped for a meeting is never swapped with. A Wonder Tile is always shown and gives a twinkle. No reward, no harm, nothing in Reduce motion. Tiles from `assets/tiles/floor-traps.png` (`tools/tiles/extract_sky_floor.py`).
- **Finds:** `TownFinds.PER_DAY` (3) things lie on the floor each day: berries, vitamins or a little Poké (`docs/economy.md`, `TownFinds`). They come from the date, not the layout, so a new layout moves them and never makes more. A tap picks one up into the Bag (or adds its Poké), with the game's pick-up sound; so does a Pokémon that walks onto one, as a team picks up items in the games, and now and then one heads for a find it has spotted. Either way it rises from the floor with its name and is gone until tomorrow; the Pokémon only walk while the home screen shows, and the sound plays only on Home. Never under a widget. Pictures from `assets/tiles/floor-items.png`, the game's own floor pictures for those items.
- **Sky:** none yet. Outdoor tilesets would have a sky strip above the top wall only once a matching Mystery Dungeon sky is found among real assets (REP-052 open item); a sky from another game would not match.
- **Ghosts and walls:** a Ghost type takes a short cut through walls only rarely (12% of long legs), fades while inside, and never rests or sleeps in a wall; one caught in a wall walks out or is set on the floor. Trap effects are not cancelled by the trap's own status icon.

## 5. Main navigation and app access

### 5.1 Hybrid navigation

Provide both world navigation and direct navigation. Tapping a landmark opens its associated destination; a compact global navigation surface provides direct access from every page. Neither route depends on the other. No in-world walking or loading animation may gate access.

Suggested landmark mapping (visual shortcuts only):

| Landmark | Destination |
|---|---|
| Central town landmark | PokéLife home/overview |
| Plan-associated facility | PLAN |
| Care-associated environment | CARE |
| Health-associated facility | HEAL |
| Finance-associated facility | MINT |
| Household facility | BASE |
| Connection facility | LINK |
| Utility/equipment area | GEAR |
| Expedition/guild entrance | WORLD |
| Pokémon gathering point | Collection, party and PC |
| Me/settings access | Profile and settings |

The final art and mapping should align with approved module identities; these landmarks do not create separate module implementations.

### 5.2 Direct controls

Provide app drawer, global search, global module navigation, Pokémon collection access, settings, configurable shortcuts and predictable Android back behavior. The navigation overlay should be compact and should not become another dashboard.

### 5.3 App drawer

Support installed applications, search, alphabetical ordering, optional categories (Games, Messages and calls, Social, Music, video and photos, Productivity, Money, Health and fitness, Shopping, Maps and travel, News, Tools and system, and Other, taken from each app's declared Android category), user-created folders, automatic categorization with manual overrides, hidden applications, pinning and application information/uninstall routes where Android permits. Usage-based suggestions are optional and require explicit opt-in if they depend on Usage Access. External applications remain first-class; PokéLife must not make its own apps artificially privileged.

### 5.4 Folders and shortcuts

Folders open in a compact floating panel, support rename/reorder/add/remove and close by outside tap or Back. Preserve existing folder behavior where sound. Support Android app shortcuts when provided. Built-in shortcuts may include Start/Resume Focus, Capture Task, Care Check-in, favorite utility, Pokémon collection and Resume WORLD. Route every action through its owning module service.

## 6. Roaming Pokémon and environmental interactions

### 6.1 Population and priority

Visible inhabitants are projections of the canonical owned-Pokémon collection. Priority order:

1. Selected global buddy.
2. The Guardian: at most one Guardian walks the town, however many the player has (one per module). It is the buddy if the buddy is a Guardian; otherwise the Guardians take turns, one a day, in order of level (then id), so the town changes without the player doing anything. When the Guardian changes, the old one walks off the edge of Home and the new one walks in (nothing happens in Reduce motion: it is just a change). The others stay in their modules.
3. User-favorited Pokémon.
4. Other eligible owned Pokémon according to rotation settings or an explicitly selected launcher display roster.

The shown Guardian is a presence, not a crowd-pleaser: it stands and looks far longer than the others, roams short and rarely, and takes no part in the town's pairwise meetings. A passing Pokémon may glance at it (stops and turns its head; the Guardian is not held), and once every few minutes one is startled and the Guardian sparkles back. Next to that, the shown Guardian is noticed: a Pokémon that walks close to it stops, turns and is startled (and the Guardian answers with a sparkle), once in a while per Pokémon, never in Reduce motion. The town weather has a "Guardian's weather" choice: the shown Guardian brings its own (Kyogre and Suicune rain, Articuno snow, Kyurem and Regice a blizzard, Regirock and Landorus a sandstorm, Lugia and Rayquaza cloud shadows; none of its own means a clear sky). "Live forecast" stays the default. Now and then one Pokémon in the town chirps (its own cry, quiet and short, with a hop; the Guardian rarely and lower), only while the town is in sight on Home, never in Reduce motion, battery saver, a call or with Sound off, and it can be switched off in the launcher's settings.

There is no global active party. Module parties are independent; the launcher must not infer or merge them into one. WORLD expedition teams are also not a launcher party.

A configurable population ceiling may target 24 visible Pokémon, but the default should be substantially smaller and selected through device profiling. The visible population is a presentation subset; hiding or rotating a Pokémon never removes or modifies its collection record.

### 6.2 Behavior model

Use a lightweight, bounded state machine with appropriate states such as idle, walk, rest, sleep, play, explore, interact and species-specific environmental behavior. Behavior must respect species capabilities, available assets, terrain, sprite direction, nearby objects, path boundaries and collision rules. Do not give every species an identical generic animation set.

Behaviours in the town today (all cosmetic, built only from the sprite sheets' own animations and the status icons): idle variation (standing, turning to look the other way, grazing with the eat animation, a hop); spells of rest and of pottering about (a two-state rhythm, so a few walk while most rest and it changes, each Pokémon a little more or less restless by nature): resting, it stands, looks about, grazes or shifts a step; restless, it walks two to four connected legs as a correlated random walk (it keeps roughly to its way with gentle turns, bends back towards a place it likes the further it strays, the buddy near the middle of Home, leans along the back and front of the ground, keeps a little room from the others and now and then sets off across the town), each leg a gentle curve that eases in and out, at a pace of its own; how lively the town is, 1 (sleepy) to 10 (restless, default 4, calm), is a launcher setting that sets how often they set off and how long they rest; staggered bedtimes, with a hop, a stretch and a bite of breakfast on waking; surprise (a Pokémon next to one that is tapped, or to a Guardian that has just arrived, may be startled); weather felt through status icons (a twinkle for a Pokémon at home in the weather, a drop of sweat for one that suffers in it, by type); a tap that hops, hops happily, turns about, or, tapped over and over, is flustered. Greetings, playing together, following, investigating objects, hiding, species-specific patterns and the Guardian's arrival and departure scenes wait for the capability registry.

Town additions: a soft shadow under every Pokémon; status icons centred on one line above the head; meetings on a floor happen on their own squares with up to one square between (Chebyshev distance 2 or less). Species quirks (`TownQuirks.kt`, cosmetic): always asleep (Snorlax, Slakoth, Slaking, Abra, Komala, Munna, Musharna, Morelull), rooted (Metapod, Kakuna, Cascoon, Silcoon, Sudowoodo, Ferroseed, Ferrothorn, Sandygast, Palossand), disguised as another species until tapped and back after a minute (Mew, Ditto, Zorua, Zoroark), slow at x0.5 (Shuckle, Slowpoke line, Torkoal, Carbink), fast at x1.9 (Vigoroth, Ninjask, Varoom, Revavroom, Zebstrika, Doduo, Dodrio, Cyclizar, Cinderace, Arcanine, Talonflame, Pikachu and clones), erratic (Spinda: 30% stumble a step, 8% confused), timid (Ralts, Wimpod: flee from anyone within 2 squares). Every Guardian shows its type's stat icon with a hop every 25 to 60 seconds.

### 6.3 Stable placement

Persist stable logical scene/location identity, position and display eligibility for a visible owned Pokémon. Transient animation frames and interpolation do not need persistence. On relaunch, restore coherent positions. Rotation should be deterministic or seeded to avoid constant reshuffling. Avoid saving every animation frame.

### 6.4 Interactions

Water Pokémon may rest near water; Flying Pokémon may perch or fly; compatible Pokémon may play; small Pokémon may investigate nearby objects; inhabitants may gather near landmarks. These are cosmetic. They must not generate rewards, require a response, create relationship scores, trigger reminders or impose maintenance obligations.

Tapping a Pokémon produces a brief nonverbal reaction (look, wave, hop, expression or suitable short animation). A secondary action may open its collection profile. Do not show unsolicited text dialogue or persistent speech bubbles outside WORLD.

## 7. Buddy presence

The buddy is the user's selected Pokémon from the shared ecosystem. The user is not represented by a human or Pokémon avatar. The buddy receives highest visibility priority, has a recognizable preferred area, participates in the same autonomous behavior system and updates immediately when buddy selection changes. It should feel like an inhabitant rather than a fixed mascot overlay.

The buddy may follow a preferred route, rest, interact with nearby Pokémon, approach a landmark or react when tapped. It must not initiate unsolicited dialogue, demand attention or create a separate affection/relationship mechanic. The launcher reads the canonical buddy selection and never creates its own buddy record.

## 8. Widgets, folders and quick actions

### 8.1 Presentation

Built-in PokéLife widgets use the shared Design System's PMD dialogue-box components and tokens. They may resemble dialogue panels, notice boards or signs, but not every interface element should be a speech bubble. App icons remain icons; buildings remain scenery; Pokémon remain sprites; search uses an input surface; settings use shared utility components.

Support compact, medium and large layouts where meaningful, individual configuration, consistent typography/borders/spacing, predictable truncation, accessible touch targets, light/dark appearance and tested contrast. External Android widgets retain provider rendering unless the provider supports customization; do not force them into incompatible dialogue-box styling.

### 8.2 Initial widget catalog

| Widget | Content | Actions |
|---|---|---|
| Clock | Time/date | Open clock |
| Calendar | Upcoming events | Open event/calendar |
| Focus | Timer/session state | Start, pause, resume, return |
| Quick Tasks | Today's tasks | Capture, complete, open |
| Care | Selected check-in/routine status | Open CARE |
| Health | Selected activity summary | Open HEAL |
| Finance | Selected balance/summary | Open MINT |
| Base | Selected household status | Open BASE |
| Buddy | Selected Pokémon | Open collection |
| Party | Active Pokémon | Open party |
| World | Expedition status | Open/claim when supported |
| Gear | Favorite utilities | Launch selected tool |
| Notifications | Selected reminder summary | Open destination |

Not every widget needs every size. Sensitive information is hidden by default and requires explicit user configuration to display.

### 8.3 Picker and editing

Organize widget picker by module and utility category. Show name, preview, supported sizes and a short description. Configure before or immediately after placement. Preserve move/resize functionality, but anchor elements to logical scene positions rather than raw screen coordinates. Long-press opens contextual editing; drag repositions; resize handles appear only while editing; removal is explicit. No mandatory global edit mode.

### 8.4 Quick actions

Provide a compact quick-action surface through a configurable gesture and visible alternative. Suggested actions: search, app drawer, global module navigation, quick task capture, Focus, Pokémon collection and settings. Do not recreate Android Quick Settings; Wi-Fi, Bluetooth and brightness remain Android responsibilities. An optional Android Quick Settings tile is a separate future enhancement only if it provides a genuinely useful action.

## 9. Notifications and contextual information

`../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` remains the sole authority for scheduling, quiet hours, focus deferral, delivery, recovery and action routing. The launcher displays selected PokéLife notification/reminder information, pending indicators and contextual quick actions; it does not schedule duplicate reminders.

Possible contextual information includes next calendar event, active Focus session, pending tasks, upcoming routine, expedition completion, selected module status, time/date and optional weather. Display only what the user chooses. Do not turn every module metric into a permanent indicator.

Respect Android notification permission and lock-screen privacy. Do not expose diary details, health details or financial amounts by default. Do not request notification-listener access to reproduce notifications from unrelated applications. The launcher uses PokéLife-owned reminder records and supported Android surfaces only.

## 10. Customization and settings

Settings are organized by intent and use sensible defaults.

- **Appearance:** environment preset, scenery, day/night, weather, seasonal appearance, ambient effects, Pokémon/buddy visibility, sprite scale where supported, widget and icon appearance.
- **Layout:** page count/arrangement, grid dimensions, widget placement, viewport behavior, folders and shortcuts.
- **Pokémon:** visible population ceiling, buddy/party/favorite priority, rotation, per-Pokémon visibility and ambient behavior density.
- **Gestures:** app drawer, search, global navigation, quick actions, page navigation, widget editing and back behavior.
- **Sound/touch:** ambient cries, footsteps, environmental and interaction SFX, haptics and independent volume controls.
- **Performance:** Automatic, Balanced and Minimal modes, with only a small number of advanced controls.
- **Backup/reset:** include launcher configuration in supported local backup/export; provide reset layout, appearance and all launcher preferences.

Resetting launcher preferences must never delete apps, owned Pokémon, module records or the PokéLife save. Avoid exposing complex tuning unless it produces a meaningful visible result.

Launcher settings hold choice lists, not cycling lines (town size, weather, temperature, battery saver, every gesture), switches for on/off lines, and no Sound section (global Settings owns sound). Date format and 12-hour clock live in General Settings, "Date and time". "Clear pinned apps and custom icons" and "Reset launcher settings" ask first; reset keeps pins, icons, hidden, moved, folders, launch counts and the weather place.

## 11. Android default-launcher integration

### 11.1 Optional Home role

Register the appropriate Android `HOME` intent filter and let the user select PokéLife through Android's default Home app flow. Never automatically replace the default launcher. Do not interfere with One UI Home or third-party launchers. The user must be able to restore the previous launcher through Android settings. The regular PokéLife app remains usable independently of the selected launcher.

### 11.2 System integration

Support Home button/home gesture, Back, status/navigation bars, system insets, edge-to-edge layout, display cutouts, rotation policy, app launch/task switching, keyboard/text input, system theme and accessibility settings. Do not attempt to replace Android system UI.

### 11.3 Permissions and access

| Capability | Access | Timing/policy |
|---|---|---|
| Default launcher | Android Home selection | User explicitly selects |
| Installed apps | Package visibility/launcher queries | No runtime dialog; use narrowest compliant mechanism |
| External widgets | AppWidget binding | When adding a widget |
| Notifications | Android notification permission | When enabling reminders/notifications |
| Calendar | Calendar provider access if implemented | When enabling calendar integration |
| Weather | Network | Only when enabling online weather |
| Usage suggestions | Usage Access special access | Only when enabling usage-based sorting |
| Location weather | Location permission | Only when explicitly enabled |
| Notification listener | Special access | Not required; avoid for core launcher |

Review `QUERY_ALL_PACKAGES` policy and distribution eligibility before using it. Optional permission denial must not block launcher operation; explain value before requesting and avoid repeated prompts after denial.

### 11.4 Default gestures

- Horizontal swipe: move between environment pages.
- Swipe up: app drawer (on the open town; the right-edge drag stays, but Android's own back swipe also lives on that edge).
- Swipe down: configured quick-access surface.
- Long-press element: contextual editing.
- Tap landmark: destination.
- Tap Pokémon: nonverbal reaction.
- Tap app icon: launch application.

Resolve gesture conflicts through predictable touch regions and provide visible alternatives for users who cannot or do not wish to use gestures.

Swipe up or down reach is 64 dp. Extra triggers: swipe up or down with two fingers, press and hold empty space. Jobs: Open folders, Open widgets, Edit the home screen, Go to the next page, Pull down notifications (`EXPAND_STATUS_BAR`, used only for this).

### 11.5 Android widget host

Keep the AppWidgetHost infrastructure to this contract:

1. Pass actual allocated width and height in dp through the entire hosting pipeline.
2. Respect provider minimum dimensions and supported resize modes.
3. Re-measure host after placement/resizing and parent constraint changes.
4. Handle provider configuration activities.
5. Handle provider removal, package uninstall/disable and widget ID cleanup.
6. Recover host state after process recreation.
7. Test multiple providers and sizes on physical devices.
8. Prevent clipping, stale measurements and unexpected empty space.

Built-in PokéLife widgets and Android external widgets may share placement concepts but are different rendering technologies.

## 12. Performance, battery, memory and lifecycle

### 12.1 Rendering and assets

Separate static environment layers, dynamic Pokémon sprites, widget surfaces, hit regions, overlays and environmental effects. Cache or reuse unchanged layers; a clock or widget value update must not redraw the entire scene unnecessarily. Use nearest-neighbor scaling for pixel sprites, avoid unintended bilinear interpolation and fractional scaling that blurs edges, preserve sprite baseline/ground positioning, directional frames, tile alignment and transparency. Bound bitmap/sprite caches, reuse decoded assets, load scene assets on demand and avoid decoding every animation simultaneously.

Recommended draw order: sky/distant background; distant scenery; buildings/static structures; ground/paths; Pokémon and interactive objects; foreground scenery; widgets/interface surfaces; navigation/interaction overlays. Determine occlusion from scene depth rather than arbitrary draw order.

### 12.2 Animation and audio

Animate visible sprites only, use bounded animation rates, pause offscreen pages where possible, reduce effects in battery saver and stop animation/audio when hidden or backgrounded. Use state transitions rather than polling. No permanent background service for roaming. Audio is optional, must not trigger on every frame, must limit overlapping cries, respect silent mode/volume settings and stop immediately when backgrounded.

### 12.3 Lifecycle expectations

| State | Expected behavior |
|---|---|
| Launcher visible | Render scene and active animations |
| Page offscreen | Reduce/pause nonessential animation |
| Another app foregrounded | Stop scene animation and ambient audio |
| Screen off | Suspend visual activity |
| Battery saver | Reduce rendering and refresh work |
| Process killed | Restore configuration and stable scene state |
| Device reboot | Recover without manual repair |

Consolidate the AUTO/OFF/ON battery saver into Automatic, Balanced and Minimal user-facing modes. Avoid background wake locks and per-frame persistence. Weather refresh and scheduled data updates follow shared background-work policies.

### 12.4 Performance targets

App drawer and navigation must feel immediate; interaction must not exhibit perceptible lag due to ambient animation; memory use must remain bounded during extended sessions; there must be no unnecessary background wake locks or animation while hidden. Establish numeric frame-time, memory and startup budgets through profiling on the supported device baseline rather than assuming emulator performance.

### 12.5 Crash-loop safe mode

Android restarts a crashed launcher at once, so a start-up crash would loop. `CrashGuard` keeps the last ten crash times in its own preferences and treats **a third crash within a minute** as a loop (`CrashLedger.LIMIT` 2, `WINDOW_MILLIS` 60 s). The next start then opens **safe mode** instead of the scene: a plain list of the installed apps, the line "PokéLife stopped three times in a minute, so it started plain. Your apps still open from here.", a **Copy crash log** key and **Try again**, which forgets the recorded crashes so the following start is a normal one. Safe mode opens no save, draws no widgets or roaming, and pays nothing. One crash, or crashes spread further apart, changes nothing. The log holds the time, the app version, the thread name and the exception; it must never carry module content, account names, amounts or any other private text.

## 13. Accessibility

All functional elements expose meaningful accessible labels, logical traversal order, selected/focused state and supported actions. Pokémon expose species or nickname when navigated; decorative details do not create hundreds of meaningless screen-reader stops.

Provide a conventional navigation mode with named destinations, module list, search, app drawer, Pokémon access, widgets, folders and settings without requiring map navigation. Support system font scaling, high contrast, Reduced Motion, reduced transparency/effects, color-independent state, adequate touch targets and readable dialogue panels. Sound never communicates essential information exclusively.

Reduced Motion disables or simplifies continuous roaming, parallax, ambient particles and decorative transitions. Gesture actions always have an accessible alternative.

## 14. Offline behavior

| Feature | Offline behavior |
|---|---|
| Environment and roaming | Fully available |
| Buddy | Fully available from local shared data |
| Module navigation | Fully available |
| App drawer | Uses installed application list |
| Built-in widgets | Cached/local module data |
| External widgets | Provider-dependent |
| Calendar | Locally accessible calendar data |
| Weather | Cached forecast with timestamp or manual appearance |
| Search | Local apps and supported PokéLife destinations |
| Settings | Fully available |
| Reminders | Local shared scheduling/delivery, subject to Android conditions |

No cloud account, remote server or internet connection is required to render the environment. If a provider is unavailable, retain its widget with a useful unavailable/stale state rather than crashing or silently deleting it.

## 15. Shared Pokémon and module integration

### 15.1 Pokémon contract

Read canonical species/form data, owned Pokémon instances, module-party references where explicitly requested, global buddy selection, favorites, display eligibility and asset references from `POKEMON_ECOSYSTEM.md`. Use owned instance IDs to identify individuals; species IDs alone cannot distinguish duplicates.

The launcher must not create owned Pokémon, award XP, change levels/evolution, deliver rewards (the one exception is the dungeon floor's daily finds, section 4.5), modify party/buddy selection independently or maintain affection. It only projects the shared data into the scene.

### 15.2 Module projections and actions

The launcher reads module-owned data through shared read models or application interfaces and sends actions to the owning module service. It does not reproduce business rules.

| Module | Permitted launcher projection/action examples |
|---|---|
| PLAN | Tasks, Focus state, selected calendar information; capture/complete/start Focus through PLAN |
| CARE | Non-sensitive check-in/routine status; open CARE; never expose private diary content by default |
| HEAL | User-selected activity summary; open HEAL |
| MINT | Privacy-controlled finance summary; hidden values respected; open MINT |
| BASE | Selected household status and shortcuts; open BASE |
| LINK | Selected connection shortcuts; open LINK |
| GEAR | Favorite utilities; launch selected tool |
| WORLD | Expedition status and permitted completion/claim actions through WORLD |

Example: task completion calls PLAN's operation; Focus calls PLAN's service; Care check-in navigates to CARE; expedition claim invokes WORLD's reward flow. No launcher action may bypass source validation or reward idempotency.

### 15.3 Refresh and privacy

Use observable local data sources for in-app updates. Update only affected widget projections; do not poll continuously or refresh the entire scene for a small change. Use cached values on resume. Apply privacy/visibility settings at every presentation boundary. CARE private content, sensitive HEAL details and MINT financial information must not leak into unrelated surfaces or logs.

If a module is disabled, unavailable or unimplemented, its shortcut behaves consistently with the app shell and does not fabricate data.

## 16. Pixel-art animation and PMD dialogue consistency

Use reusable animation definitions with frame duration, looping/one-shot behavior, direction, interruption and completion semantics. Prefer authentic source frames over artificial stretching/rotation. Asset fallback: PMD sprite/animation; appropriate mainline or supported alternative; static species sprite if no animation exists. Never substitute missing Pokémon artwork with generic Pixelarticons.

The shared Design System owns dialogue-box anatomy, tokens, typography, borders and accessibility. Launcher may adapt shared components for widgets, contextual labels, folders, search/navigation overlays, status panels and confirmations. Maintain consistent padding, contrast, focus and pressed states; avoid nested dialogue boxes and gratuitous portraits. App icons, scenery, sprites and search inputs retain their appropriate visual form.

## 17. Empty states, onboarding and recovery

### 17.1 Onboarding

First launch opens into a calm, populated environment without requiring extensive configuration. A short optional flow may: confirm/select a buddy if available; choose an initial environment; explain core gestures; show app drawer/direct module access; offer widget placement; and separately offer Android default-home setup. Do not require widget, gesture, Pokémon or page configuration before use.

### 17.2 Empty and unavailable states

- No Pokémon: keep scenery intact; offer collection/intro route; never create placeholder owned Pokémon.
- No buddy: show environment without invented companion and provide buddy-selection route.
- No widgets: environment remains complete; offer contextual add-widget action without permanent empty containers.
- Permission denied: explain affected optional feature and preserve all other functionality.
- App uninstalled/disabled: mark pinned shortcut unavailable and offer removal/replacement; do not crash or erase unrelated layout.
- Provider offline: show stale/unavailable state and retain placement.

### 17.3 Recovery

Restore last valid configuration; validate scene/widget references; repair missing assets/destinations; fall back to a safe default layout if necessary; preserve shared Pokémon and module records. Launcher reset never resets the PokéLife save.

## 18. Acceptance criteria

### Experience and navigation
- [ ] Default home is a recognizable PMD-inspired living environment, not a conventional dashboard.
- [ ] Multiple pages preserve scene continuity.
- [ ] Direct module navigation works without in-world travel.
- [ ] App drawer is available by gesture and visible control.
- [ ] Search covers installed apps and supported PokéLife destinations.
- [ ] No fixed dock is required; external apps remain easy to launch.

### Pokémon
- [ ] Buddy follows canonical global selection; no global active party is assumed. Favorites and the optional launcher display roster follow the documented priority.
- [ ] Visible Pokémon retain stable positions and configurable population.
- [ ] Species/terrain influence compatible behavior.
- [ ] Tap produces brief nonverbal reaction.
- [ ] No relationship meter, compulsory interaction or duplicated reward logic.

### Widgets
- [ ] Built-in widgets have tested size variants.
- [ ] External widgets measure against actual allocated dimensions.
- [ ] No clipping or unexpected empty space.
- [ ] Widget configuration survives process recreation.
- [ ] Folders behave reliably and widgets respect privacy settings.

### Android integration
- [ ] User can select PokéLife as default launcher and restore previous launcher.
- [ ] Home, Back, system gestures, safe areas and system bars behave correctly.
- [ ] No mandatory sensitive permission; optional denial degrades gracefully.

### Reliability, accessibility and offline
- [ ] Animation/audio stop when hidden; battery saver reduces work.
- [ ] Sprite/icon caches are bounded; no continuous background roaming service.
- [ ] Scene configuration survives process death, rotation and display changes.
- [ ] Screen readers, alternative navigation, Reduced Motion and text scaling work.
- [ ] Core launcher works offline and provider failures do not crash it.

### Visual quality
- [ ] Pixel sprites retain crisp edges and correct depth/ground alignment.
- [ ] Dialogue panels use shared Design System tokens.
- [ ] Scenery does not compete with widgets; day/night remains readable.
- [ ] Empty states remain coherent.

## 19. Implementation order

Navigation and widget correctness precede advanced animation. Do not replace `LauncherScreen.kt` or the existing renderer wholesale before mapping reusable behavior and dependencies. Delivery status is `../IMPLEMENTATION_GUIDE.md`'s and the backlog's.

## 20. Documentation ownership and cross-references

- `../PRODUCT_BIBLE.md`: product-wide launcher purpose and principles.
- `../IMPLEMENTATION_GUIDE.md`: current implementation status, source audit and delivery roadmap.
- `DESIGN_SYSTEM.md`: shared dialogue-box components, visual tokens and accessibility.
- `POKEMON_ECOSYSTEM.md`: canonical species/forms, owned instances, party and buddy.
- Section 9 of `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md`: scheduling, quiet hours, delivery, recovery and action routing.
- `../ARCHITECTURE/DATA_AND_MIGRATIONS.md`: launcher configuration persistence and ownership.
- `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md`: Android permissions, system integration and privacy boundaries.
- `../ARCHITECTURE/TEST_STRATEGY.md`: domain, lifecycle, widget, offline and device acceptance tests.
- `../MODULES/*.md`: module-owned data and actions.

This document owns launcher requirements. Other documents should link here rather than duplicate detailed launcher behavior. Changes to shared Pokémon, reminders, privacy, persistence or design contracts must be reconciled with their respective authoritative documents.

---

## Testing reference

Launcher lifecycle, home-role selection/fallback, widget hosting/resizing, roaming scene performance, animation suspension, accessibility and physical-device acceptance are defined in [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md). Emulator coverage does not replace launcher acceptance on the target device.

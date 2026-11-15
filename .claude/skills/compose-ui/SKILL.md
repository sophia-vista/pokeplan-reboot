---
name: compose-ui
description: How the PokéLife Compose UI and Android app are organised and what the buddy, DayBackground and art rule require. Use before changing composeApp or androidApp screens, menus, the buddy, backgrounds, widgets or screenshots.
---

# Compose UI and Android app

Moved verbatim from CLAUDE.md (13 November 2026 token trim). Also read `docs/pokelife/FEATURES/DESIGN_SYSTEM.md` 5.1 and 12A and load `spacing-system` before laying out a screen.

- `composeApp/`: the shared Compose UI (`app.pokeplan.ui`; `App()` is the entry; one package per module (`plan/`, `care/`, `heal/`, `mint/`, `base/`, `link/`, `world/`, `gear/`, `pokemon/`), shared screens in `screens/`, the theme and house components in `theme/`; `app-core` and `androidApp` are grouped the same way, see `docs/code-map.md`). The bottom tabs are Home / Modules / Pokémon / Me; a module with several sections opens on a menu of cards (`MenuGrid` in `screens/Menu.kt`), and sections that hold records (the Planner's views, the Pokémon tab, World's Journal, Me) are rows with counts (`MenuList`), never tab pills. `ScreenshotTest` renders screens to `composeApp/build/screenshots/*.png`; `jvmTest` skips it unless tests are named (`./gradlew :composeApp:jvmTest --tests "*ScreenshotTest.collection*"`, render the screens you changed) or `-Pscreenshots` renders them all (CI passes it).
- `androidApp/`: the thin Android application (reminders through `reminders/SharedReminders.kt`, Glance widgets in `widgets/`, the database wiring in `LiveStore.kt`). Widgets and the app share one save; the app calls `GameStore.reload()` when it comes back to the front.
- The buddy never chats: no permanent speech bubble, needs or mood meter; level and progress sit small in a corner, and one reaction (like a game emote) happens only for a real event or a tap (`GameStore.buddyEvent()`, an egg hatched or a payout in the last few minutes, celebrated once): a Mystery Dungeon status icon for a tap (`ReactionIcon`: a sparkle, a flame for a Fire type, a heart...; no emoji), its SpriteCollab portrait for an event. It sleeps during focus and hops or eats with its own SpriteCollab animations (`SpriteAction`); `Portrait` and `SpeakerBox` show its face beside text.
- `DayBackground` (composeApp) is the default page behind every screen (`AppBackdrop` swaps in a real scene or dungeon town under a veil when Settings → Look → Background asks): an ordered-dither gradient with a light grain, drawn in code as UI chrome (not art) and tinted by the time of day; screens must not paint their own opaque background over it.

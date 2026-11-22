# Navigation audit (21 November 2026)

The owner asked for a pass over navigation on every screen: the hierarchy, contextual navigation, Back (the key and the
system gesture), the patterns screens share, and the bottom tabs. This file records what was checked, what the rules are,
what was found, what was fixed in the same session, and what is still open. The code is the evidence here; the rules live
in `../FEATURES/DESIGN_SYSTEM.md` 5.7 and 6.7, and those two sections gained the rules this pass settled.

## The map

Four bottom tabs (the only global navigation). A wide window moves them to a side rail; the behaviour is the same.

```
Home ────────────── HomeScreen: trainer header, region cards (open a module in one tap),
                    "Also today" rows, Quick log, mini quests, recent
Modules ─────────── Modules list: "Jump to" search, Gear key, one row per module
  └─ module ─────── daily view: lead, overview, Quick actions, party; header = Back + Sections
       └─ section ─ SectionScaffold: header Back, system Back
            └─ page ─ a facility in the Town, a Journal page, a task card, a calendar panel
Pokémon ─────────── menu (Party, PC, Pokédex, Bag, Mart, Quests)
  └─ section ────── SectionScaffold
       ├─ detail ── PokemonDetail (own header and Back key)
       └─ entry ─── DexEntryScreen (own header and Back key)
Me ──────────────── menu (Stats, Settings, Help)
  ├─ section ────── SectionScaffold (Settings pages, Stats, Help)
  │    └─ page ──── Plan progress, Care trends (from Stats), a Settings page (from Settings)
  └─ ...
Overlays ────────── Gear (hub → tool), a cutscene, a module's screen lock
Launcher ────────── the optional Android home surface, a separate state machine (REP-045)
```

## The rules the screens share

- A root tab has no Back key; the system Back leaves the app (Home, Modules list, Pokémon menu, Me menu).
- A module's daily view carries Back (to the Modules list) and the Sections key. A section carries Back (to the daily
  view), and the system Back does the same.
- A page opened inside a section goes up one level first: its own list, then the section, then the module.
- The Pokégear covers whichever tab is open; Back closes a tool to the hub, and the hub closes the Pokégear.
- Dialogs and sheets (a Sections sheet, a breakdown, a QR viewer) close on Back by themselves.

## Findings

| # | Finding | Where | State |
|---|---|---|---|
| N1 | The Pokémon detail drew a Back key, but no screen registered the system Back, so Back left the app. | `pokemon/PokemonDetail.kt` | Fixed |
| N2 | In the Town and the Journal, the header's Back left World entirely while the system Back and the on-page "Back to the …" key closed only that page: three ways back, two destinations. | `world/World.kt` | Fixed |
| N3 | Switching tabs dropped the Pokémon and Me tabs to their menus (a section, an open Pokémon, a Pokédex entry were lost), while the Modules tab kept its module and section. The same state was also lost when the app was put away. | `pokemon/Collection.kt`, `screens/Me.kt` | Fixed |
| N4 | A widget, shortcut or notification opened its screen behind the open Pokégear. | `App.kt` (`open`) | Fixed |
| N5 | A kind left by an earlier Mint quick action (say, Transfer) pre-filled the next entry reached from outside Mint. | `App.kt` (`open`) | Fixed |
| N15 | The Pokémon tab read a jump (Home's buddy chip, a widget's Pokémon, the Party shortcut) only at its first composition, so while the tab was already open the jump did nothing; the same one-shot also lost to a saved place. | `App.kt`, `pokemon/Collection.kt` | Fixed |
| N6 | Care has one header and one Back for five places, so Back inside a skill guide or a Care Play scene leaves the place instead of closing the guide or the scene. | `care/Care.kt`, `care/CareDream.kt` | Fixed |
| N7 | Plan's calendar panels (a new event's draft, a picked task, an event) and an expanded task card are pages inside the section, but Back leaves the section and discards the draft. | `plan/DayPlan.kt`, `plan/Planner.kt` | Fixed |
| N8 | The intro (onboarding) is full-screen with no Back; Back exits the app. | `screens/Onboarding.kt` | Fixed |
| N9 | Tapping the active Modules tab returns to the module list; tapping the active Pokémon or Me tab does nothing. (A second tap is the usual "go to this tab's first screen".) | `App.kt` | Fixed |
| N10 | Opening a Pokémon from a module switches to the Pokémon tab; Back then goes to the Pokémon menu, not to the module the player came from. The module is still open in the Modules tab, but nothing says so. | `App.kt`, module screens | Fixed |
| N11 | Modules mix two section patterns: Care builds its own header and Back, Plan mixes `SectionScaffold` and its own header, the other five use `SectionScaffold`. They look the same, so this is only a maintenance risk. | `care/`, `plan/` | Open, cleanup |
| N12 | The launcher keeps about fifteen navigation flags in one composable; a recreation loses them. | `launcher/LauncherScreen.kt` | Out of scope (REP-045) |
| N13 | Navigation state lives in `AppModel`, which is not saved: after process death the app returns to the saved tab's root, even though `rememberSaveable` now keeps the Pokémon, Me and World pages. | `App.kt` | Open |
| N14 | `townPageToOpen` is a test-only global in production code (the screenshot test sets it to render each Town page). | `world/World.kt` | Open, cleanup |

## What changed

- **N1:** `PokemonDetail` registers `BackHandler` with the same action as its Back key.
- **N2:** `SectionScaffold` now knows the page one level below it. A page registers itself with `UpTo { … }`; the header's
  Back key and the system Back both run it, then the section's Back once the page has gone. The Town and the Journal use it
  instead of a Back handler of their own, so the three ways back agree. The "Back to the …" keys stay (they are explicit).
- **N3:** the Pokémon tab (section, open Pokémon, Pokédex entry) and Me (section) and World's Town and Journal pages keep
  their place with `rememberSaveable`, so a tab switch and the app being put away both find them where they were. What is
  not saved there: the PC sort and filters, which start clean (they belong to the section, which is closed when you leave).
- **N4:** `open()` closes the Pokégear for every place except Gear.
- **N5:** `open(Place.MINT)` clears the one-shot entry kind and target account.
- **N15:** the Pokémon tab reads the one-shot jump itself (`LaunchedEffect` on `openPokemonId` and `openParty`), so it works
  whether the tab is open or not, and a fresh jump wins over a saved place. `App` no longer clears the flags from outside.
- **N6 and N7:** Care and Plan use the same up-slot as a section: a skill's guide and Care's Play view and Pokémon panel in
  Dream, and Plan's calendar draft/panels and an open task card, register themselves with `UpTo`, so Back closes that page
  first. Care keeps its own header for its five places (see N11) but now shares the one Back rule.
- **N8:** the intro's Back goes to the step before (the greeting line by line) and leaves the app only on the very first
  line.
- **N9:** a second tap on the Pokémon or Me tab returns it to its menu, as on Modules; the shell counts the taps, and a
  place restored after a tab switch is not reset by the restore.
- **N10:** a Pokémon opened from Home or a module returns there on Back; a plain visit to the Pokémon tab keeps the usual
  Back to its own list.
- Docs: `DESIGN_SYSTEM.md` 6.7 gained the two rules ("a page inside a section is one level up before the section is left"
  and "every tab keeps its place"). Tests: `BackChainTest` (a tab switch keeps the Me and Pokémon places; the Town's Back
  goes to the town, then out of World) and `NavigationTest` (the Pokégear and the Mint kind).

## What is still open

1. **N13, process death:** the navigation state lives in `AppModel`, which is not saved, so Android killing the app in the
   background returns to the saved tab's root even though the pages inside a tab are now saved with `rememberSaveable`.
   Fixing it means saving the module and section names with the tab (a `rememberSaveable` navigation store the model reads
   and writes, or saving them with the save); it is a session of its own and matters less than the Back and place fixes.
2. **N11, two header patterns:** Care builds one header for its five places and Plan mixes `SectionScaffold` with its own
   header. They look the same, so this is a maintenance risk, not a behaviour bug; folding them into `SectionScaffold`
   takes a scaffold that leaves the page scroll to the screen, which is a small redesign of how a section changes page.
3. **N12, the launcher:** its own navigation state machine stays as it is (REP-045 covers it).
4. **N14, a test hook in production code:** `townPageToOpen` is a global the screenshot test sets to render each Town page;
   giving `WorldScreen` a `startTownPage` parameter would remove it, a cleanup for the next World session.

Not touched here: the Android back gesture's predictive animation (the Compose `NavigationBackHandler` already provides
it) and the launcher's own navigation.

# World: audit against the improvement plan

1 November 2026, at version 0.101.0. This is the audit the improvement plan (`pokelife-world-improvement-plan.md`, section 16) asks for before any broad change. It reports what the code does, not what the documents say. No game rule was changed and no existing owner decision is reopened. Read with `docs/world-module.md` (the module in full).

Method: read `world-core`, `GameStore`'s `world*` functions, `WorldAlerts`, `composeApp/.../screens/World.kt` and `WorldArt.kt`, the Android widget and alarm code, the tests, and rendered the World screens (`ScreenshotTest`) at normal and 130% text.

---

## 1. Map of the current architecture and screen flow

### 1.1 Code

| Layer | Where | What it holds |
|---|---|---|
| Rules, no UI | `world-core` (JVM and wasmJs) | `WorldData` (the save), `Dungeons` + `KantoDungeons`/`JohtoDungeons`/`HoennDungeons` (data), `WorldLayer`, `Story`/`KantoStory`/`JohtoStory`/`HoennStory`, `Legends`, `Missions`, `Progression` (skills, facilities, research, Café, shop, Dojo, Swap Shop), `WorldSim` (the deterministic simulation), `WorldGuide` (next step), `Rank` |
| Store | `app-core` `GameStore` | `worldSettle`, `worldAssign`, `worldStart`, `worldQuickSend`, `worldRecall`, `worldRecruit`, `worldClaimItems`, mission, facility, research, Café, shop, swap, Dojo and treasure functions; `worldGuardian`, `worldLegends`, `worldPaid` (private) |
| Alerts | `app-core` `WorldAlerts`; `androidApp` `AppAlerts` | Forecast of the next finished job and the one alarm |
| UI | `composeApp` `screens/World.kt` (about 700 lines), `WorldArt.kt` (map picture, tile boxes), `AppModel.worldSection`, `AppModel.worldLayer` | All World screens |
| Surfaces | `androidApp` `WorldWidget`; `Place.WORLD`; Settings (World animation, job alert) | Widget, shortcuts and notifications |

Persistence: one JSON document, `AppData.world` (`WorldData`), written by `GameStore` through `Storage`; client-side, not part of the server's game state, so World pays through the shared Bag and the reward log, never the server reward engine. Every World field has a default, which is how old saves load.

### 1.2 Screen flow

```
Modules -> World  (WorldScreen; settles every 3 s while open and once on opening)
 └─ Landing (WorldMenu): header "Rank: X"
      ├─ first time: "Start here" card with "Send my strongest into Tiny Woods"
      │  after that: "Next up" card (WorldGuide, HOME layer only) with "Send my strongest"
      ├─ banner: "N of 36 dungeons open"
      ├─ menu grid of eight entries:
      │    Map | Missions | Skills | Base | Expeditions | Camp | Finds and Bag | Settings
      └─ RankCard (rank bar, the story of the layer last viewed on the Map, Poké)
 Map           layer switch (4 buttons) -> map picture + markers -> LegendsCard -> one card per dungeon (Send buttons)
 Missions      the board (3 held), jobs by state
 Skills        three skills, exact effect text
 Base          Camp and Lab upgrades, Café, Kecleon Shop, Swap Shop, Dojo, research milestones
 Expeditions   one card per team slot (choose team, send/recall, floor time), the last 8 journal lines
 Camp          yard of recruited Pokémon
 Finds and Bag Finds waiting (recruit), treasure boxes, the Bag items World holds, World's reward-history entries
 Settings      scrolling floors, job alert
```

Back handling: each section has Back and the system back gesture (`ScreenSwitch`); `BackChainTest` covers the module back chains in general (World's own sections are not named in it).

---

## 2. What already satisfies the plan

| Plan item | State |
|---|---|
| Owner decisions in section 2 | All followed in code: journeys advance only through teams; no eggs; regional tables; Guardians; layers open from day one with per-dungeon gates. |
| Offline model (14.2) | Deterministic, seeded by team, expedition and floor; 12 h cap per open; a clock set back does not freeze (`ClockAndDayTest`); no streaks or missed-day penalties. |
| Shared state (14.3) | Recruits go through `grantPokemon` (shared collection, once); items go through the Bag; `worldPaid` writes the shared reward log; no parallel inventory. |
| Rank (8.1) | Progress bar and "x of y to next rank" exist. |
| Skills (8.2) | The effect text is the real formula (one line per skill). |
| Dungeon facts (5.5) | A dungeon card shows rank, floors, favoured types, deepest floor, clears, why-locked (first reason only) and Send actions. |
| Strongest team (6.2) | `worldQuickSend`. Pokémon already assigned elsewhere are excluded from the picker. |
| Team speed shown (6.3, in part) | "A floor takes about N min" per team on the Expeditions card and "N of M like this place" on the Map card. |
| Facility text (10.x, in part) | Exact bonuses are written in the facility lines. |
| Job limit (9.4) | Up to 3 held; kept. |
| Guardian overlap (11.3, World side) | A legendary already owned from Plan's quest is not given twice (this change), and the World quest counts. |
| Accessibility baseline (13.5) | Screenshot tests render every World section at normal and 130% text; Reduce Motion is respected for tile boxes and animation. |
| Widget and alarm (14.4) | Exist and are tested in logic (`WorldAlerts`, forecast tested against a real settle). |

---

## 3. Gaps, bugs and mismatches between the documents and the code

### 3.1 Likely bugs and mismatches (to confirm with the owner before changing)

1. **The team picker shows Guardians.** `WorldTeamsView` builds its list from `player.pokemon` unfiltered, while `worldQuickSend` deliberately leaves Guardians out and Guardians cannot join the party. `WorldData.assign` and `GameStore.worldAssign` check only that the Pokémon exists. So a Guardian (any legendary or mythical) can be put on a team by hand but never by "Send my strongest". Which is intended is unstated. (Plan 2 says Guardians are a separate role; the safest fix is to exclude them everywhere.)
2. **The picker lists at most 40 Pokémon, strongest first** (`take(40)`). A player with a large collection cannot pick weaker Pokémon (for a type match) and there is no search, filter or favourite-type view.
3. **"Ability contribution" is not part of team speed.** The plan's 6.3 text says speed includes an ability contribution. In code, abilities raise *skill levels* (more berries, more finds, more Poké), up to 3 levels, and never change floor time. Floor time is only `base / (1 + power/100)` with power = levels, +20% for each favoured-type member, then the 10% Temporal Mastery and the legendary percentages. The UI must say that exactly; it must not claim abilities speed floors up.
4. **The landing's "Next up" is for the home layer only.** `WorldGuide.nextStep(world)` is called with its default layer, so a player working in Kanto, Johto or Hoenn is told about the home layer's dungeons. The banner counts all 36 dungeons as one list.
5. **Locked dungeons show only one reason.** The dungeon card's `when` chain stops at the first failing requirement (story shut, then rank, then chapter). The plan (5.5) wants each shown separately, and the progress toward the chapter ("3 of 5 different dungeons cleared").
6. **Map state is colour only.** The marker states are yellow, green and grey (open, cleared, locked). There is a text summary for screen readers, but nothing pairs the colour with a shape or label, and there is no "exploring" or "Guardian" state. (13.5, 5.2.)
7. **Marker positions on the three region maps are read by eye** and not verified on a device (open decision in the plan, section 17).
8. **The job alarm and notification have never run on a physical device** (section 17). The widget refresh path is likewise untested on device.
9. **The Plan side of the legendary overlap is not done.** World no longer gives a second copy, but Plan's quest list still shows and lets the player claim a legendary World already gave (`claimQuest` grants without checking ownership). Cross-module wording (11.3) is also missing in both.
10. **The legendary boosts are invisible in play.** `LegendsCard` lists quests but not what each Pokémon gives, where it applies or whether it is active. Guardian screens do not mention World boosts. Stacking is additive per layer with the global Temporal Mastery separate; the cap question (section 17 of the plan) is open and nothing in code caps World's own percentages except a 50% ceiling on the speed-up.
11. **The Camp screen does not show the Camp's effect** (exact treasure bonus) in the way the plan asks, and the Lab's bonus is not tied to research notes.

### 3.2 Gaps against the plan (nothing exists yet)

- Five-pillar navigation. Today the landing is an eight-entry menu of equal weight (Map, Missions, Skills, Base, Expeditions, Camp, Finds and Bag, Settings) plus a rank card; there is no Atlas/Expeditions/Guild/Base/Journal grouping.
- **Journal.** Only `WorldData.journal` exists: at most 40 short text lines (`JournalLine(at, text)`), 8 shown on the Expeditions screen. No typed history, no per-dungeon or per-team record, no discoveries view, nothing durable beyond 40 lines.
- **Expedition overview of all teams** in one place with status labels and "ready to review". The Expeditions screen is one card per slot; the landing shows no active team.
- **Expedition report.** `WorldSim.settle` returns a `WorldReport` (events per team) and the store writes the reward log, but the UI shows no report on return: only journal lines and a reward-history line. Guaranteed, milestone and chance rewards are not separated.
- **Expedition preview** (expected floors, reward categories, milestones) and a **power explanation**.
- **Team names, presets, badges, history, roles.**
- **Finds on the landing.** They are reachable only through "Finds and Bag" (the menu entry carries a count badge).
- **Atlas places and routes,** place types, fog of war, regional Guild jobs, chapter structure beyond title + summary, Guardian journal, regional research notebook (research counts species globally, not per region), rank-up presentation and the "what the next rank unlocks" line.
- **Floor events and move-based exploration** (the Dex has moves since 0.96.0; nothing uses them).
- **World save version.** `WorldData` has no schema version; compatibility rests on default values. New persisted structures should carry one (plan 14.1).

### 3.3 Documentation mismatches

- `docs/world.md`'s implementation-status table (0.89.0) is out of date (it lists nine dungeons and one Guardian). `docs/world-module.md` is the current description.
- The plan's description of the landing as "every system an equal card" is accurate; its description of a "Next up" card is only true for the home layer (see 3.1, item 4).
- `docs/ecosystem.md` says each region's legendary is "a quest, not a Guardian reward" in an older decision; the later decisions (Guardian = main legendary) are in force and recorded in decision 4 and 8 of the same document.

---

## 4. Checklist results (plan section 16)

| Item | Result |
|---|---|
| Locate screens, navigation, entry points | Done: section 1. |
| `AppData.world`, serializers | `WorldData` is `@Serializable` with defaults; `JournalLine`, `Expedition`, `WorldTeam`, `Find`, `MissionProgress`. |
| Expedition time and offline formula | `WorldSim.floorMillis`; seeded floor rolls; verified by tests and the economy report. |
| 12-hour cap, clock rollback | Cap `MAX_AWAY_MILLIS`; rollback resets `settledTo` to now (test: `aClockSetBackDoesNotFreezeTheClimb`). |
| Team power, favoured types, abilities | Verified (3.1, item 3). |
| Floor probabilities, milestones | Berry 70%, treasure 6%, find 3%, milestones by dungeon. |
| Recruitment and duplicate prevention | A find is removed once recruited (`withoutFind`); the Pokémon is a new individual (duplicates of a species are allowed by design). A legendary quest never gives a second copy since this change. |
| Rank, skill XP | `ExplorerRank` points; skills `30 x level^2`. |
| Job limits, repeats, forecasts | 3 held; periods 14 days (most), 7 (weekly), 1 (daily); `jobsDoneAt` forecast. |
| Facility and purchase calculations | `Facility.cost`, `candyPrice`, Café, Dojo constants. |
| Capture current screens | Rendered by `ScreenshotTest` (world-*.png, world-xl-*.png, world-map-kanto/johto/hoenn). |
| Small screen, 130% text, Reduce Motion | Rendered; nothing clipped in the current sections. |
| Physical device checks | **Not done:** map markers, job alarm, widget refresh, launcher page changes. |
| Reliability tests | Close/reopen, long offline, clock changes, shared Bag and log entries, old-save loading: covered by `WorldTest`, `FinishTest`, `ClockAndDayTest`, `WorldStoreTest`, `SaveCompatibilityTest`. Interrupted reward collection and recruiting an already assigned Pokémon: not specifically tested. |

---

## 5. File-by-file plan for Phase 1 (cohesion and usability)

Phase 1 is presentation and small derived data; it changes no formula and no reward. Order matters: each step ships and tests on its own.

1. **Landing as expedition headquarters** (plan 4.2). `screens/World.kt` `WorldMenu`: order the screen as rank, one primary action, active team cards, ready jobs, Finds waiting, story/Guardian line, then a compact shortcuts row. Keep the eight sections reachable through the shortcuts and a "More" entry (Skills, Camp, Settings) so nothing is removed. New small composables in a new file `screens/WorldHome.kt` (`PrimaryAction`, `TeamStrip`, `FindsStrip`) to stop `World.kt` growing. Needs: a pure function `WorldGuide.primaryAction(data, layer)` in `world-core` (returns what the screen shows: continue, prepare a team, choose a destination, review finds), tested.
2. **Next step per layer.** Pass the layer on view to `WorldGuide.nextStep`; remember the last layer on the landing. `World.kt`, test in `world-core` (`KantoTest` style).
3. **Atlas states and dungeon detail** (5.2, 5.5). `WorldArt.kt` `MapMarker`/`MarkerState` gain `EXPLORING` and `GUARDIAN`; draw a shape per state (ring, check, lock, flag), keep colour as a second cue. `WorldMapView`: a dungeon detail sheet; locked reasons each on their own line with progress (uses `Story.chaptersOf(layer)` and `dungeonsCleared`). An accessible list alternative already exists (the dungeon cards); keep it as the list view of the Atlas.
4. **Expedition overview** (6.1). New composable `ExpeditionOverview` (all teams, destination, floor n of m, status, Recall). `WorldTeamsView` uses it at the top. Status text is derived from `WorldData` only (no new field).
5. **Team picking and power explanation** (6.2, 6.3). Replace `take(40)` with a scrollable, searchable list (favoured-type filter, "free only"); exclude Guardians (3.1 item 1, after the owner confirms); add a "How fast" block built from `WorldSim.power` and `suitability`: combined levels, favoured members, ability skill levels (labelled as skills, not speed), the Temporal Mastery and legend percentages, resulting minutes per floor. Saved presets are Phase 2 (they need a field).
6. **Expedition report and Finds visibility** (6.6). `WorldPage`: after a settle with events, show a dismissible report card (floors, XP, Poké, berries, boxes, milestone rewards, new Finds with a link to recruit). Source: the `WorldReport` that `worldSettle` already returns, kept in `AppModel` until dismissed (not saved; the reward log stays canonical). Landing shows the Finds count and a "Recruit" action.
7. **Rank, skills and legend clarity** (8.1, 8.2, 11.2). Rank card lists what the next rank opens (from `ExplorerRank.teamSlots` and dungeon `needs`); `LegendsCard` shows the bonus text, where it applies and whether the Pokémon is owned. The text comes from `LegendQuest` data (add a `bonusText` there and test it matches the numbers).
8. **Mission board status and previews** (9.4). `WorldMissionsView`: state per job (available, active, done, unavailable with the reason), reward preview, repeat wait. Data exists in `Missions`.
9. **Accessibility pass** (13.5). Run the 130% screenshots on every new composable; keep touch targets at `Spacing.Target`.
10. **Device checks** (owner): the three region maps' markers, the job alarm, the widget, the launcher page changes.

Phase 1 files touched: `world-core` `WorldGuide.kt`, `Legends.kt` (bonus text); `composeApp` `screens/World.kt`, `WorldArt.kt`, new `WorldHome.kt`, `AppModel` (`lastReport`); tests in `world-core` and `ScreenshotTest`.

---

## 6. Data migration requirements

- **Phase 1:** none. No persisted field changes. (`lastReport` is in memory only.)
- **Phase 2 (when built):** new fields with defaults, in `WorldData`: team names and accents (`WorldTeam.name`, `colour`), saved presets, a typed expedition history (replacing the 40-line text journal with entries such as `JournalEntry(kind, at, place, text, rewards)`; keep reading old `JournalLine`s and show them as plain notes), per-region discovered/recruited sets for the notebook, regional jobs state (`missionsDone` and `missionClaimedDay` already keyed by job id). Add a **`worldVersion`** integer now with default 1 so later changes can migrate; never rename or remove existing fields (`guardianDone`, `guardianGranted`, `layersDone`, `legends`, `clears`, `deepest`).
- **Phase 3:** floor events must be derived from the seed (team, expedition, floor) so a settle is identical however it is split; any choice the player makes later is stored as its own record, never as a change to a past floor. The event's "default" outcome is what happens if the player never chooses.

## 7. Test plan

| Phase | New tests |
|---|---|
| 1 | `WorldGuide.primaryAction` and per-layer `nextStep` (world-core); the report is built from the real `WorldReport` and does not touch the Bag or log (app-core: a settle then a dismiss changes nothing); legend bonus text matches `Legends` numbers; team picker excludes Guardians and lists more than 40; locked-reason lines for each failing requirement; screenshots at normal and 130% text for the landing, overview, report, dungeon detail, picker, mission states, each marker state. |
| 2 | Save round-trip and old-save loading for each new field (`SaveCompatibilityTest`); journal entries written once per event and never twice on re-settle; presets survive transfer (a transferred Pokémon leaves a preset); regional jobs per layer; Guardian journal reflects owned/placed; Plan quest shows "already given" and does not grant. |
| 3 | A settle split in different ways (one call, many calls, with a clock rollback) gives identical event outcomes; no event removes a reward or Pokémon; an unanswered choice resolves to its default; essential progress never needs a specific rare Pokémon; the economy simulation covers every new mechanic. |
| 4 | Each new layer: the data tests already used for Kanto/Johto/Hoenn (species in the regional dex, no legendaries in wild tables, one Guardian dungeon, chapters in range, legends point at their layer) plus a `simulateWorldEconomy` run. |
| Always | `./gradlew jvmTest compileKotlinWasmJs :androidApp:compileDebugKotlin`; BackChainTest for every new back path. |

## 8. Open decisions this audit cannot settle (as in the plan, section 17)

1. Should Guardians be excluded from teams everywhere (3.1, item 1)?
2. Stacking and a cap for World's legendary boosts, and whether the global Temporal Mastery stacks with them (it does today).
3. What Plan's quest list should show for a legendary World has already given.
4. Which module receives each side legendary.
5. Whether team roles are descriptive only or carry bonuses.
6. Which floor events fit the offline simulation (proposal: events resolved from the seed with a "default" outcome, choices stored later).
7. How places, routes and dungeon entrances fit the data model (proposal: a `Place` list per layer with a `dungeonId?`, separate from `DungeonDef`).
8. Physical-device results for the markers, alarm and widget.

Do not start Phase 1 step 5 (the picker's Guardian filter) before decision 1.

---

## 9. Owner's answers (1 November 2026) and what was done

1. **Guardians may join a team when idle** (not on a team and not slotted as a Guardian). Done: `GameStore.worldIdle()`; the picker and "Send my strongest" use it; `worldAssign` refuses a slotted Guardian; `slotGuardian` refuses one that is on a team (nothing is moved behind the player's back).
2. **The picker shows the 20 strongest idle Pokémon** (plus the team's own members and whatever is picked, so they can be taken off). Done.
3. **Abilities are not part of floor speed.** Agreed; the "how fast" explanation (Phase 1, step 5) will say so.
4. **The landing's "Next up" follows the layer on show**, with the layer buttons on the landing. Done.
5. **Every unmet requirement is listed on a locked dungeon**, with how far along it is. Done: `WorldGuide.lockReasons`.
6. **Markers use shapes as well as colour** (diamond, circle with a tick, square, circle with an arrow, red ring for a Guardian), with a legend line under the map and a screen-reader summary that counts the explored ones. Done.
7. **Plan has no quest for a legendary World gives** (option A, owner): done species by species (`Legends.coveredSpecies`, applied in `Quests.all`); the rest keep theirs until a layer gives them.
8. **Device checks** (map markers, job alarm, widget, launcher pages): with the owner.

**Phase 1 progress (0.104.0).** Done: idle Guardians and the 20-strongest picker (1, 2), the landing's next step per layer (4), every lock reason (5), shaped markers (6), the all-teams overview, the "how fast" explanation (3) and the legendary bonus text. Still to do in Phase 1: the landing redesign around one primary action, the expedition report on return, the rank card's "what the next rank opens", the mission board's states and previews, and the device checks.

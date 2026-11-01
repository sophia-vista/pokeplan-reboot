# Pokégear: code audit and plan (1 November 2026)

The facts about what Pokégear is in the code at 0.105.0, the discrepancies with the documents, what Phase 0 of the owner's utility-suite specification (`pokelife_pokegear_audit_claude_code.md`, sections 0 to 22) changed in 0.106.0, and the plan for Phase 1. The specification's rules apply: utilities pay nothing, no permission before the tool that needs it, common logic in `commonMain`, saves and serialized names preserved, nothing private in the cloud.

## 1. What is built (verified in the code)

| Tool | Where | Capability | Gaps |
|---|---|---|---|
| Flashlight | `screens/GearTools.kt` `FlashlightTool`, `ui/Torch.kt`, `androidApp/AndroidTorch.kt` | Android torch through `setTorchMode`; a white full-screen light elsewhere | no brightness, colour, red or night mode, no SOS, no widget or tile |
| Calculator | `PocketCalculator`, `ui/launcher/Calculator.kt` | four operations one at a time (no precedence), decimal, sign, clear | no expression editor, parentheses, percent, scientific, history, memory |
| Units | `UnitConverterPage`, `ui/gear/Units.kt` | length, mass, volume, speed, temperature (about 25 units) | no search, favourites, swap, precision, history, custom units; ten categories short of an everyday set |
| Currency | `CurrencyTool`, `ui/gear/Rates.kt` | Frankfurter daily reference rates, USD base, cached last set | USD to EUR default, codes only, one pair, no board, chart, alert, fees, manual rate |
| Picker | `PickerTool`, `GameStore.setPickerOptions` | up to 30 strings, random choice | no edit, reorder, weights, history |
| Coin toss | `CoinTool` | heads or tails | no dice, history, announcement |
| Helpers | `screens/Gear.kt`, `pokemon-core/.../Gear.kt` | signature-line helper, Ranger loan, reaction | large against the utility; moves not used |

**No Pokégear timer or stopwatch exists** (`GearTool` has five entries). The Built note in `docs/ecosystem.md` already said five tools; its roadmap item 8 names the timer and stopwatch as planned. Corrected wording is now in section "Pokégear" of that file. The same note said the persistent top-bar entry was not built; it is (the button at the top of every tab), and it said the Dex had no moves; it has had them since 0.96.0.

## 2. Verified findings and what Phase 0 did

1. **P0, privacy. Confirmed and fixed.** `AppData.pickerOptions` is documented "never synced or sent anywhere", but `GameStore.cloudDocument()` filtered only `account`, `eggTickets` and `shownCalendars` (`DEVICE_ONLY`), so a signed-in player's Picker list (and the rate cache) went to the cloud with the rest of the save. Now: `pickerOptions` and `gearRates` are in `DEVICE_ONLY` (never in the cloud document), `cloudPart()` ignores them when deciding what is unsynced, and `adoptCloud` keeps this device's copy, also against an older cloud document that still carries them. "Export my data" still includes the Picker list (it is the player's own data) and leaves out the rate cache. Tested both ways (`GameStoreTest`). **What remains:** copies already uploaded earlier stay in the player's cloud save until the next upload replaces the document; no migration of local data was needed (the field and its name are unchanged).
2. **Rate cache local-only. Done** (same change). Public reference data, fetched again on demand.
3. **Currency client. Hardened.** The tool made a new `HttpClient` on every entry and never closed it: now one client for as long as the tool is open, closed on leaving, with connect, request and socket timeouts and a check of the HTTP status. `parseRates` now validates and normalises: the base is read (it assumed USD), every rate must be a finite number above zero, codes must be three capital letters, the date must be a real-looking date, and rates of another base are turned into dollars (refused when the dollar is missing). `GearRates` gained `fetchedAtMillis` and `source` (defaults, so old caches load). The tool shows "Rates of DATE, fetched N ago, from SOURCE. Reference rates, not what a bank or money changer gives." A failed fetch says why and never touches the cached rates. Tested with a mock engine.
4. **Flashlight lifecycle. Fixed.** `AndroidTorch` used the first flash camera and never listened: now every flash camera is tried in turn, a `TorchCallback` keeps the state true (another app or the system switching the light shows in the tool at once; a camera taken by another app is treated as unavailable), the tool and the activity turn off a light this app turned on (`Torch.release`, called when the tool is left and in `onStop`), and one `AndroidTorch` serves the activity. The screen-light fallback (colour, brightness, SOS) is Phase 1.
5. **Capability layer. Added, no tools.** `ui/gear/Capabilities.kt` (common): `Capability`, `PermissionNeed`, `CapabilityStatus`, `DeviceCapabilities`, `NoCapabilities` for desktop and web (everything says it is not available there). `androidApp/AndroidCapabilities` reads what the phone really has (sensors from `SensorManager`, features from the package manager, the flash from the camera service) and whether a permission is already granted. It starts no sensor, opens no camera and asks for no permission. Reached as `AppModel.capabilities`.
6. **Docs match the code.** `docs/ecosystem.md` corrected (section "Pokégear"); this file added.

Not done in Phase 0: the optional move of private tool data into a separate store (the `DEVICE_ONLY` list is the smaller, safe change; a separate store is worth doing when custom units, history and recordings arrive).

## 3. Decisions (the specification's defaults, section 20; none has been changed by the owner)

1. Build common and high-value tools deeply, offline first; leave medical and professional functions out.
2. Free and ad-free, no Pro tier.
3. Currency: PHP is the default home currency, user-configurable, with favourite and travel sets.
4. All user-created utility data (picker lists, custom units, histories, recordings) local-only unless the player opts into backup or sync.
5. First major release: a strong scientific calculator and everyday calculators; graphing and symbolic maths later.
6. Huawei Watch Fit 4 Pro: a feasibility experiment after the phone tools (the watch runs HarmonyOS, not Wear OS).
7. Helper Pokémon: compact, collapsible, never taking the working area.

## 4. Plan for Phase 1 (core utility quality), one change at a time

Each item follows the specification's template (user problem, current behaviour with file and symbol, proposed behaviour, files, permissions, schema and privacy impact, reward impact none, tests, acceptance criteria) before it is built.

1. **Pokégear home and tool shell:** search (offline, synonyms), favourites, recents, categories; a shared tool page (title, favourite toggle, compact helper, status line, copy and share). Files: `screens/Gear.kt`, new `ui/gear/ToolCatalog.kt` (tool metadata and search, common). Schema: favourites and recents are device-only (`DEVICE_ONLY`).
2. **Calculator engine:** one shared expression engine (precedence, parentheses, percent, history, memory) in `commonMain`, with the launcher's keypad kept and regression tests (`2 + 3 × 4 = 14`). Files: `ui/launcher/Calculator.kt`, new `ui/gear/CalcEngine.kt`.
3. **Unit catalogue:** a data-driven catalogue with stable ids, aliases, affine units, search, favourites, swap, precision, and the missing everyday categories; cooking volume to mass only with an ingredient density. Files: `ui/gear/Units.kt`, a new catalogue file.
4. **Currency:** PHP home default, search by name or code, swap, a multi-currency board, a manual rate and a fee-aware "what it really costs" mode (cash, card, ATM, transfer), minor-unit formatting (JPY has none), rate freshness (done in Phase 0). Files: `ui/gear/Rates.kt`, `CurrencyTool`.
5. **Timer and stopwatch:** a general timer (several, labelled, notification actions, survives process death) and a stopwatch (monotonic clock, laps), reward-neutral; a timer started from Focus stays Plan's. Needs the Android notification permission asked at first use. Files: new `ui/gear/Timers.kt` (common state and maths), an Android adapter.
6. **Picker and dice:** edit, reorder, weights, history, dice, a disclaimer that it is not for security or gambling.
7. **Flashlight:** screen light mode (brightness, colour, red), SOS and strobe only on request with a warning, a widget.

Exit criteria for the phase are in section 17 of the specification: common jobs faster than another app, every tool with empty, loading, error and offline states, no reward side effects, all pure logic tested.

## 5. Questions for the owner that this audit does not settle

The seven decisions above are assumed from the specification's defaults. Beyond them: whether the first Phase 1 change should be the Pokégear home and shell (recommended, because it gives every later tool a place) or the calculator; and whether `pokelife://gear/<tool>` deep links should come in Phase 1 or with the widgets.

## 6. Phase 1 status (0.107.0)

Done, each with tests on its pure logic and a screenshot at normal and 130% text:

1. **Pokégear home and tool shell:** `ToolCatalog` (categories, descriptions, synonyms, typo-tolerant search, favourites and recents first), the home (search, favourites, used lately, groups), a shared top line with a star, the helper as a small strip ("Show" opens the full bar; the choice is kept).
2. **Calculator:** `CalcEngine` (precedence, brackets, unary minus, percent as a shop calculator reads it, factorial, functions, degrees or radians, errors that say what is wrong, 12 significant digits so `0.1 + 0.2` is 0.3), memory, an opt-in history, copy. The old `Calculator` keypad class is no longer used by any screen (the launcher never used it; only Pokégear did); it stays until Phase 4's "remove dead code after reference checks".
3. **Units:** `UnitCatalog` (16 kinds, over 110 units, stable ids `kind:symbol`, affine and nonlinear conversions, units that are easy to mistake carry a note, no volume-to-weight conversion at all), search, swap, every unit at once, starred pairs, decimals.
4. **Currency:** `Currencies` (names, aliases, minor units), `Travel.estimate` (reference estimate, the rate offered, the fees, apart), a home currency (PHP), a board, a manual rate.
5. **Timer and stopwatch:** `GearTimers` (pure rules on the saved data), saved timers, an Android alarm (`AppAlerts.Kind.TIMER`) and a notification with Stop and +1 min (`TimerActionReceiver`, each acts only on timers still ringing). Reward-neutral. The stopwatch stores its start in clock time and never counts a clock set back as time lost; a monotonic source across process death is not available to common code, so across a restart it uses the clock.
6. **Picker and dice:** `Randomizer` (weighted choice, several without repeats, the last pick left out, dice up to 20 of 2 to 1000 sides with a modifier), editing in place, reordering, an opt-in history, a line that this is not secure randomness. The flashlight's screen light (four colours, brightness by dimming the picture) and an SOS signal that exists only on request, with a warning, on the torch, never with Reduce motion on, never faster than one change in 250 ms, for at most five minutes.

**Limits to know:** screen brightness is the picture dimmed, not the phone's brightness (that needs an Android window call); the notification asks for permission the first time a timer is started (as the reminders do); the rate service gives daily reference rates only (no history, charts or alerts: a second provider would be needed); the world clock and time zone converter, the everyday calculators (tax, tip, loan, dates) and unit conversions by ingredient are Phase 2. Phase 1 has not been run on a device.

# GEAR — Practical Utilities and Device Tools

**Version:** 1.0  
**Status:** Specified / approved  
**Product owner:** PokéLife  
**Canonical scope:** GEAR tool catalog, tool behavior, local state, device capability use

> This document defines the approved GEAR product requirements. It does not claim that the existing implementation already meets them. The Product Bible defines product-wide principles; the Implementation Guide tracks implementation status and delivery order; the shared design system and reminder feature own cross-module behavior.

## 1. Purpose and product boundaries

GEAR is PokéLife's practical utility suite: a fast, dependable collection of calculators, converters, time tools, device utilities, sensors and lightweight randomizers. Its standard is not simply to contain many tools. Each shipped tool should be credible for its intended job, easy to reach, accurate about its limitations and useful without Pokémon systems.

**Primary outcomes**
- Complete common utility tasks quickly, without opening several unrelated apps.
- Offer specialist depth when it materially improves the task, while keeping the default interface compact.
- Work offline wherever the function permits; clearly identify network-derived or stale information.
- Use Android hardware only when needed, with contextual permissions and graceful fallbacks.
- Provide consistent search, favorites, recents, input behavior, copy/share and lifecycle handling.

**Non-goals**
- GEAR is not a life-management module, financial ledger, health tracker, task manager, general note system or replacement for PLAN, MINT, HEAL, BASE or LINK.
- It does not own Pokémon acquisition, collection, friendship, leveling, rewards, items, currency or module progression.
- It does not require an account, cloud service, subscription, advertising, telemetry or network access for local tools.
- It must not inflate the catalog with redundant or low-value features merely to increase tool count.
- It must not imply professional accuracy for phone sensors or replace dedicated calibrated instruments.

## 2. Core principles

1. **Utility first.** A tool must remain fully usable when the helper sprite is hidden and when the user ignores every game system.
2. **Dedicated-app quality.** Each tool must be evaluated against a strong dedicated free app for the same task. Implement useful depth, not a superficial imitation.
3. **Fast by default, deep on demand.** Put the most common action first. Reveal advanced options through tabs, secondary screens, contextual menus or progressive disclosure.
4. **One source of truth.** Each tool owns only the records and settings necessary for its function. Shared services own global navigation, permissions, reminder delivery, backup and design primitives.
5. **Honest data.** Display units, assumptions, source, timestamp, freshness, precision and limitations where they affect interpretation.
6. **Offline-first.** Calculations, local lists, timers, saved preferences and cached data continue to work offline where technically possible.
7. **No punishment.** No streaks, expiry, attendance requirements, missed-day penalties or pressure to use a tool.
8. **Inspect before replacing.** Preserve proven behavior and data formats where they meet requirements. Refactor gaps; do not rewrite an existing tool without tracing UI, state, persistence, platform adapters, lifecycle and tests.

## 3. Information architecture

GEAR retains five broad catalog groups while allowing search and favorites to be the fastest entry points:

| Group | Tools |
|---|---|
| Calculate | Calculator; Everyday Calculators |
| Convert | Currency Converter; Unit Converter |
| Time | Timer; Stopwatch; World Clock |
| Light | Flashlight / Screen Light |
| Choose & Measure | Picker; Coin & Dice; Compass; Level; Barometer; Device Check; Waypoints; Sound Meter; QR; Network Check |

The current baseline contains 18 tools. This specification preserves that coherent core and upgrades the tools before considering additions. Catalog taxonomy may be simplified in the UI, but search must find every tool regardless of group.

### 3.1 GEAR home

The landing screen prioritizes:
1. Search across tool names, aliases and relevant task terms.
2. Favorites, with user-controlled ordering.
3. Recently used tools.
4. A compact set of useful quick tools, informed by actual use rather than game rewards.
5. Categories and the complete searchable catalog.

Requirements:
- Search is local, fast, typo-tolerant where practical and available offline.
- Favorites and recents are distinct. Recents are chronological and can be cleared; favorites persist until changed.
- The complete catalog remains one clear action away.
- Avoid large promotional cards, redundant descriptions and a permanent helper carousel.
- Support compact grid and list presentation if both can be maintained without duplicated navigation state.
- Empty favorites, no search results and first-use states provide a concise next action.

## 4. Shared tool interaction contract

Every tool page follows the same interaction expectations:
- Clear title and primary task area; one obvious primary action.
- Inputs support keyboard, paste, selection, editing and locale-appropriate formatting.
- Results can be copied; share/export is available when it adds value.
- Back navigation preserves in-progress state when safe and expected.
- Destructive actions (clear history, delete saved lists, remove a configured item) are deliberate and recoverable where practical.
- Loading, empty, offline, stale, error, unavailable hardware, denied permission and interrupted states are explicit where applicable.
- Long-running work and sensor subscriptions stop when the tool is no longer active unless a documented user-started background behavior requires otherwise.
- Tool preferences and histories are local-only. They are included in user-controlled local backup/export according to the data policy, but never silently uploaded or synchronized.
- No utility action emits a Pokémon progression or reward event.

### 4.1 Shared platform behavior

- Request camera, microphone, location and other permissions only at the point of use. Explain the immediate purpose and provide a no-permission fallback when one exists.
- Keep Android APIs in platform-specific adapters. Common domain logic must not depend on Android classes.
- Handle app backgrounding, rotation, multi-window where applicable, process death, reboot and OS notification restrictions explicitly.
- Use monotonic time for elapsed-time measurement; wall-clock changes must not corrupt timer or stopwatch duration.
- Respect locale, time zone, 12/24-hour preference, currency minor units, decimal separators and digit grouping.
- Support TalkBack, visible focus, dynamic text scaling, reduced motion and minimum touch targets. Do not make gestures the only way to perform an action.
- Do not keep sensors, microphone, camera, torch or high-frequency location sampling active after the user leaves the relevant function.

## 5. Tool specifications

### 5.1 Calculator

**Purpose:** reliable expression calculator with a simple default surface and optional scientific depth.

**Core behavior**
- Parse editable mathematical expressions with precedence, parentheses, unary operators, percent semantics that are documented, constants and clear syntax errors.
- Support standard arithmetic, powers, roots, fractions, reciprocal, absolute value and sign operations.
- Scientific mode provides trigonometric and inverse-trigonometric functions, logarithms, exponentials and common constants. Degree/radian mode is visible and persistent.
- Support memory controls (MC, MR, M+, M−), expression/result history, copy result and reuse/edit previous expressions.
- Editing supports cursor placement, selection and correction without forcing the user to clear and re-enter the whole expression.
- Display precision consistently; avoid silently rounding intermediate calculations in ways that change the result.
- Provide understandable errors for division by zero, invalid domains, malformed expressions and overflow.

**Presentation:** simple keypad and expression/result view by default. Scientific controls and history are secondary, not a dense permanent scientific keyboard.

### 5.2 Everyday Calculators

A compact collection of task-oriented calculators with explicit assumptions and editable inputs:
- Percentage, percent change, reverse percentage and percentage-point difference.
- Discount, tax/VAT inclusive and exclusive, tip and bill split with custom shares.
- Unit price and package comparison with normalized units.
- Date difference, date arithmetic and time arithmetic.
- Ratio, proportion and recipe scaling.
- Fuel/trip cost and consumption estimates.
- Loan/installment and compound-savings estimates, with rate, period, compounding and fee assumptions visible.

Rules:
- Each calculator has a short, task-specific input flow and immediate result.
- Keep units and assumptions beside the result; do not present estimates as guaranteed quotes.
- Formula breakdown is optional and secondary.
- Inputs are editable after calculation; reset affects only the active calculator.
- Avoid duplicating MINT budgeting, transaction or bill-record ownership. GEAR calculates estimates; MINT owns actual finances.

### 5.3 Currency Converter — Currency Converter Pro direction

**Purpose:** a multi-currency workspace for quick conversion, travel comparison and reference-rate inspection. It is a flagship GEAR tool, not a basic two-field pair converter.

**Multi-row currency board**
- PHP is the initial home/reference currency. The user can change the home currency.
- Initial suggested watchlist: USD, EUR, JPY, CNY, KRW, SGD, HKD and GBP; all rows are editable and the list is customizable.
- Editing any currency row makes that row the active source amount and recalculates every other row immediately. The user is not restricted to editing a single designated base row.
- A single active input currency/value is the calculation source of truth. Other rows are derived from normalized reference rates to avoid cumulative rounding drift.
- Add, remove, search, reorder, pin and favorite currencies. Search by currency name, ISO code, symbol, country and common alias.
- Support a broad maintained fiat-currency catalog and currency-specific minor-unit formatting. Do not mix cryptoassets, metals or loyalty points into the standard fiat list.
- Swap active and target currencies where a pair view is used; retain the multi-row board as the primary experience.
- Provide clear empty, unavailable-rate and unsupported-currency states.

**Rate integrity and data freshness**
- Distinguish daily reference rates, live/market quotes, manually entered rates and cached rates. Never label a daily reference rate as a live exchange quote.
- Display provider/source, rate date, retrieval time, rate type and freshness. Show a stale/offline indicator when appropriate.
- Cache the last valid rates for offline use. Offline calculations remain available using cached or manually entered rates, with the age of the rate visible.
- Failed refreshes must not erase the last valid rate set. A failed or missing provider response must never be replaced with a fabricated rate.
- Historical charts and date-specific lookup should support 1W, 1M, 3M, 6M, 1Y and 5Y ranges when the chosen provider has reliable data. If unavailable, omit or clearly disable the affected range rather than implying full history.
- Manual rate entry is supported for a user-provided quote. Store its source label and timestamp; distinguish it visually from provider rates.
- Preserve compatibility with the existing `GearRates` persisted format through an explicit adapter/migration. Do not discard existing cached rates or watchlist preferences.

**Calculation input**
- The amount field accepts arithmetic expressions (for example, `1200 / 3 + 50`) using the shared expression engine where feasible.
- Preserve the unrounded internal amount; apply display rounding according to the destination currency's minor units and user precision preference.
- Clearly distinguish rate precision from displayed amount precision.

**Travel and transaction costs**
- A collapsible Travel / actual-cost section supports manual money-changer quotes, card foreign-transaction fees, ATM fixed and percentage fees, transfer fees and spread/markup.
- Show the effective amount received or effective home-currency cost after entered fees, with assumptions itemized.
- Compare reference conversion with the user's entered quote without implying that PokéLife can see a bank's actual applied rate.
- Keep travel costs as temporary calculations or locally saved presets. MINT remains the owner of actual transactions and account balances.

**Personalization**
- Local favorites, watchlists, named presets and recent conversions are supported.
- Saved presets retain currencies, amounts and user-entered fee assumptions; refreshing a rate must not silently change the saved assumptions.
- Rate alerts are deferred until a reliable source, background policy and notification design are proven. No continuous polling or battery-heavy background service.

### 5.4 Unit Converter

**Purpose:** fast conversion across a broad, reliable catalog.

**Core behavior**
- Searchable categories for length, area, volume, mass, temperature, speed, time, pressure, energy, power, force, torque, data storage, data transfer rate, frequency, angle and density; extend only when a well-defined use case exists.
- Search by unit name, symbol, abbreviation and common alias.
- Fast source/target swap, favorites, recent conversions and user precision settings.
- Correct SI, US customary and Imperial distinctions; distinguish bits from bytes and decimal from binary storage units.
- Correctly handle affine conversions such as temperature and any nonlinear units that are included.
- Volume-to-mass conversion is not offered as a universal conversion. If ingredient-specific density is added later, it must require an explicit ingredient/density and label the assumption.
- Display the conversion formula or factor on demand, not permanently.

### 5.5 Timer

**Purpose:** reliable one-shot and multi-timer countdowns.

**Core behavior**
- Multiple simultaneous timers with custom labels, quick presets and recent durations.
- Start, pause, resume, reset, cancel and add-time actions.
- Persistent timer state survives app backgrounding and process death; after reboot, reconstruct remaining time from a stored deadline and a documented clock strategy.
- Use monotonic elapsed time while the process is alive; handle wall-clock and time-zone changes without producing negative or unexpectedly extended durations.
- Completion notification provides useful actions such as stop/dismiss and restart where Android supports them. Sound/vibration behavior is configurable and respects system settings.
- Explain notification/alarm permission limitations and provide a visible in-app fallback when exact background delivery cannot be guaranteed.
- Optional interval timer (work/rest rounds) may be included after the core timer is reliable. It must not become a separate workout or habit system.
- Shared notification delivery and scheduling must follow `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`; GEAR owns timer duration, labels and timer state, not a parallel notification framework.

### 5.6 Stopwatch

**Core behavior**
- Accurate elapsed-time measurement using a monotonic clock.
- Start, pause, resume, reset and lap/split capture.
- Show current elapsed time and useful lap comparisons (best, worst, average and difference) without cluttering the default view.
- Preserve a running session through normal backgrounding and process death using a monotonic/wall-clock reconciliation strategy; document reboot behavior.
- Copy or share a lap table when useful. History is optional and locally controlled.
- Do not use wall-clock timestamps as the sole source for elapsed duration.

### 5.7 World Clock

**Core behavior**
- Search cities/time zones and pin a user-defined set of locations.
- Show local time, date, day-of-week and day difference clearly.
- Editing the time in one location converts it to all pinned locations, including date rollover.
- Support meeting-time comparison across selected cities and shareable time suggestions.
- Respect daylight-saving transitions and historical/future zone-rule changes through platform time-zone data.
- A day/night indicator is optional and must not replace readable date/time information.
- City search is offline after the time-zone database and saved city list are available; no location permission is needed for manually selected cities.

### 5.8 Flashlight / Screen Light

**Torch mode**
- Use the Android camera torch when supported and available. Show actual hardware capability and current torch state.
- Provide on/off and intensity control only where the hardware/API supports intensity. Never display a working brightness slider if only binary torch is available.
- Optional SOS/strobe modes require explicit user start, prominent stop control and automatic stop on exit, lifecycle loss or thermal warning.
- Handle camera contention, permission denial, unavailable flash, thermal restrictions and battery considerations honestly.

**Screen light mode**
- Full-screen light with white, warm, red and amber options, adjustable in-app brightness and simple presets.
- Clearly distinguish app-screen brightness from Android system brightness; do not claim to change system brightness unless a supported system action is actually performed.
- Provide an obvious tap/Back exit and prevent accidental lock-in.
- Restore prior screen/window settings on exit and stop any active pattern immediately.

### 5.9 Picker

**Core behavior**
- Create, rename, edit, reorder, duplicate and delete local lists.
- Support bulk entry, newline paste, duplicate detection and optional grouping.
- Optional weighted choices and draw-without-replacement mode, with weights and remaining-item count visible.
- Allow repeated picks, remove selected item, undo, reset and local history.
- Named saved lists are local-only and can be exported/imported by user action.
- Clear empty-list, one-item and all-items-removed behavior.
- Random selection is for convenience, not security or cryptographic use.
- Personal lists must remain local and never uploaded or synchronized. Preserve current local-only semantics and add regression tests around backup, restore and migration.

### 5.10 Coin & Dice

- Coin toss with configurable side labels where appropriate.
- Standard dice, multiple dice, common polyhedral dice and custom-sided dice.
- Random number generation with range, count and repeat options.
- Optional roll history, haptic feedback and restrained animation.
- Results must be announced accessibly; reduced-motion mode must not impair result clarity.
- This is a casual randomizer, not a certified or cryptographically secure random source.

### 5.11 Compass

- Show compass rose and numeric heading.
- Distinguish magnetic north from true north; only show true-north correction when location/declination data is available and permission is granted.
- Provide calibration guidance and a confidence/accuracy state. Explain magnetic interference and sensor limitations.
- Do not fabricate heading when sensor is absent or unreliable. Provide a clear unavailable state.
- Optional coordinates/altitude are secondary and require the appropriate location/sensor capability.

### 5.12 Level

- Bubble-level presentation for horizontal and vertical alignment.
- Numeric pitch/roll angle and a clear zero/calibrate action.
- Stable near-zero behavior with sensible filtering; calibration state is visible.
- Support portrait and landscape orientations and avoid unnecessary animation.
- Label phone-based measurement as approximate and dependent on device placement/sensor calibration.

### 5.13 Barometer

- Display pressure in common units with sensor availability and sampling time.
- Optional local history graph with sensible sampling only while the tool is active or while an explicitly enabled low-power sampling policy is in effect.
- Distinguish raw atmospheric pressure from weather interpretation. Do not claim a weather forecast from a single pressure reading.
- Altitude estimation is available only with a valid reference pressure and clearly stated assumptions; it is not a surveying instrument.
- Show unavailable/unsupported hardware states without inventing values.

### 5.14 Device Check

- Show only information the platform reliably exposes: Android/app version, device model where available, battery/charging state, supported battery temperature/health fields, storage, screen resolution/density and capability support.
- Offer focused diagnostics for touch/display, sensors, camera/flash and network where relevant.
- Sensor/camera tests run only when the user starts them and stop immediately on exit.
- Do not imply invasive hardware diagnostics, hidden system access, or exact battery-health measurement when Android does not expose it.
- Keep the screen a diagnostic/reference tool, not an always-on device monitor.

### 5.15 Waypoints

- Capture coordinates on demand with timestamp, accuracy and source metadata.
- Allow a user-defined name and optional note; organize, edit and delete saved waypoints.
- Display coordinates in a readable format and allow copy in common decimal/DMS formats.
- Open a waypoint in an external map/navigation app through an explicit user action.
- Optional export/import is local and user-controlled.
- No continuous background tracking, route history, geofencing or location collection when the tool is closed.
- Request location permission only when capturing a location; manual coordinate entry remains available.

### 5.16 Sound Meter

- Provide an estimated sound level from the microphone with live reading, min/max/average and a short history graph/peak hold where feasible.
- Label readings as phone-microphone estimates, not calibrated professional dB measurements.
- Allow a user calibration offset but explain that it does not replace a calibrated reference microphone.
- Optional frequency/spectrum display is deferred until measurement quality and device performance justify it.
- Show a clear microphone-active indicator and stop recording immediately when the user stops or leaves the tool.
- No background listening, recording archive or transmission of audio. Do not persist raw audio.

### 5.17 QR & Barcode

- Scan supported QR codes and common barcode formats using the camera; support scanning from an image when platform/library capability permits.
- Generate QR codes for plain text, URLs, Wi-Fi credentials, contact details and other common payloads with clear validation.
- Copy/share generated content and save generated images when requested.
- Provide a safe preview before opening links or acting on a payload. Never auto-open scanned URLs or automatically join Wi-Fi networks.
- Warn on suspicious schemes or potentially dangerous content; do not execute arbitrary intents or scripts from scanned data.
- Optional local scan history/favorites are user-controlled and can be disabled/cleared.
- Camera permission is requested only on scan; manual QR generation does not require it.

### 5.18 Network Check

- Distinguish Wi-Fi/mobile connection state, local network connection and actual internet reachability.
- Where supported, show latency, packet loss and DNS resolution results with target, timestamp and test method.
- Display IP/network details only when useful and safe; do not expose sensitive network information unnecessarily.
- Do not label a ping/reachability check as a speed test.
- Add throughput testing only if a reliable endpoint, data-use disclosure, cancellation, timeout and clear measurement methodology are available. Never run repeated high-volume tests in the background.
- Explain that captive portals, VPNs, firewalls and provider routing can affect results.

## 6. Helper Pokémon — visual-only policy

Helper Pokémon are an optional visual identity element, not a GEAR system of progression or capability.

- Helper Pokémon are the fourth Pokémon role (`FEATURES/POKEMON_ECOSYSTEM.md` section 2.2): aesthetics only. If a helper is selected/available for a tool, its compact sprite is used as the tool icon in the GEAR catalog and the GEAR menu, and may appear in the tool header when it fits.
- If no suitable helper sprite is available, use the normal tool/item icon. Never show an empty helper placeholder.
- The sprite must not displace the tool name, reduce touch targets, occupy the working area or obscure results.
- A helper is one of the Pokémon places (`FEATURES/POKEMON_ECOSYSTEM.md` section 2.2): a Pokémon helps **one** tool and is then in no party, not the buddy and not on a WORLD team; choosing it moves it from where it was. Nothing picks a helper automatically from the collection (that would take a Pokémon out of the PC unasked): a tool without a chosen helper shows its normal icon.
- Helper selection affects presentation only. It does not alter calculations, permissions, tool behavior, unlocks, availability or outcomes.
- No helper recruitment, acquisition, loans, eligibility challenge, friendship, levels, XP, rewards, collection records or helper-specific quests are part of GEAR; a helper is only a visual preference.
- Small reactions may occur after a real user action when an appropriate asset exists. Do not trigger reactions on recomposition, polling, every keystroke or routine screen redraw.
- Respect reduced motion and sound settings. Static sprite is the default fallback.

## 7. Optional Ranger-style capture minigame

**Not built.** The code has no capture minigame; this is an idea only. If it is ever made: short rounds of touch-drawn capture loops around moving Pokémon (finger and stylus, cancellation-safe, deterministic timing independent of frame rate, loop stopped when hidden), as pure entertainment.

**Strict boundaries**
- No Pokémon recruitment, collection additions, eggs, items, XP, friendship, levels, currency, unlocks, quests, achievements or module progression. No reward ledger events. No advantage in World or any life module.
- It can be played, ignored or removed without affecting GEAR or PokéLife utility workflows; difficulty never gates tools or content.

## 8. Data ownership and relationships

GEAR owns only utility-specific local state, including:
- Favorites, recents and catalog presentation preferences.
- Calculator history/memory preferences if history is enabled.
- Currency watchlist, home currency, rate cache, manual quotes, presets and conversion history.
- Unit favorites and precision preferences.
- Timer definitions/running timer state and optional local history.
- Stopwatch session/lap history when the user chooses to retain it.
- World clock city list.
- Picker lists and weights.
- Saved waypoints and notes.
- Optional local QR history/favorites and sensor preferences.

Ownership boundaries:
- MINT owns accounts, balances, transactions, budgets, bills and financial records. GEAR may calculate or display a conversion without writing MINT records.
- PLAN owns tasks, focus sessions and planning routines. GEAR timer may be opened from a PLAN action, but the owning workflow must preserve PLAN's session/reward semantics and must not create a competing timer record.
- HEAL owns workouts and health history. An optional GEAR interval timer is a generic utility and does not write HEAL workout records.
- BASE, LINK and CARE own their respective records; GEAR does not duplicate them.
- Shared reminders own notification scheduling/delivery policy. GEAR supplies timer identity, deadline, labels and completion action semantics according to `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.
- Android platform adapters own hardware API access; GEAR domain logic owns tool behavior and interprets capability results.
- Shared design system owns reusable UI components and tokens; GEAR defines only tool-specific layout adaptations.

All GEAR records and preferences are local-only. They must not be uploaded, synchronized, sent to telemetry or shared with social systems. Local backup/export behavior is user-controlled and documented. Preserve local-only state boundaries through migrations and test that nothing GEAR keeps can leak into a shared payload.

## 9. Privacy, permissions and offline behavior

| Capability | Permission / network policy | Offline behavior |
|---|---|---|
| Calculator, everyday calculators, unit converter, picker, coin/dice | No runtime permission; no network | Fully offline |
| Currency | Network only for rate refresh/history; no financial account access | Cached/manual rates and calculations; freshness visible |
| Timer/stopwatch | Notifications/alarms as required by Android version and user settings | Core timing local; background delivery may be OS-limited |
| World clock | No location permission for manually chosen cities | Saved city/time-zone data available offline |
| Flashlight | Torch capability/permission as required by platform; screen light needs no camera permission | Fully local |
| Compass/level/barometer | Sensor availability; location only for true-north correction or optional location fields | Sensor readings local; no network required for basic use |
| Device check | Query only platform-exposed information; no broad permissions | Local |
| Waypoints | Location permission only on explicit capture; manual entry supported | Manual/saved waypoint use offline; new fix depends on location provider |
| Sound meter | Microphone permission on explicit start | Local estimate; no audio saved or transmitted |
| QR scan | Camera permission on explicit scan; generation requires none | Local scan/generation where libraries are bundled |
| Network check | Network state and test endpoints; disclose data use for throughput tests | Local connection details; internet checks unavailable offline |

Additional rules:
- No raw microphone audio is stored or transmitted.
- QR payloads, picker lists, waypoints, timer labels/history and conversion presets remain on device unless explicitly exported by the user.
- Do not collect location continuously or in the background.
- Network-dependent providers must be documented and replaceable; do not hard-code credentials in the client.
- No tool may silently upload user input, location, QR contents, lists or measurements.

## 10. Visual presentation

GEAR uses the **utility presentation** defined by `../FEATURES/DESIGN_SYSTEM.md` (from `MODULES/GEAR.md`). PMD dialogue-box framing is used selectively for prompts, concise status, result summaries and confirmations; it must not force calculator keypads, currency boards, sensor readings, charts or data tables into speech-bubble layouts.

- Use shared fields, buttons, rows, tabs, dialogs, sheets, feedback states and semantic tokens.
- Keep values, units and primary controls legible. Dense specialist tools use structured rows, charts and compact sections rather than giant cards.
- Use the helper-sprite rule in Section 6; tool icon fallback remains the normal tool/item icon.
- Pokémon art never blocks input, consumes the work area or competes with results.
- No continuous decorative motion. Animations are functional and respect reduced motion.
- Verify light/dark themes, enlarged text, TalkBack, keyboard behavior, focus, 48 dp targets and narrow/wide layouts.
- Preserve crisp nearest-neighbor pixel rendering and asset provenance for all helper sprites and item icons.

## 11. Implementation and migration requirements

**Reuse-first audit**
- Trace each of the 18 tools end-to-end: UI → state → persistence → common domain logic → platform adapter → permissions → lifecycle → tests.
- Preserve stable serialized names and existing `GearRates` compatibility. Add explicit migrations/adapters for schema changes.
- Review the current currency multi-row board and manual rate implementation before redesign. Extend it into the multi-row editable Currency Converter Pro workspace; do not throw away the existing board without evidence.
- Verify the `GameStore.DEVICE_ONLY` list (the cached rates, the Picker's list and what the tools remember stay on the device; the "Export my data" file leaves out only the rates). Current behavior must be proven by tests, not assumed.
- Do not rely on code presence as evidence of release readiness.

**Suggested delivery order**
- **P0 — correctness and privacy:** data ownership, local-only serialization, migrations, arithmetic correctness, currency rate integrity, timer lifecycle/notification policy, permission boundaries, sensor lifecycle and QR safety.
- **P1 — core tool quality:** GEAR home/catalog; calculator; Currency Converter Pro; unit converter; timer; stopwatch; flashlight; picker and coin/dice.
- **P2 — specialist tools:** everyday calculators; world clock; compass/level/barometer; device check; waypoints; sound meter; QR; network check.
- **P3 — optional polish:** shortcuts/widgets, additional tool candidates only after the 18-tool core meets acceptance.

Do not start P3 additions while P0 data/privacy or P1 core-tool quality is failing.

## 12. Acceptance criteria and QA

GEAR is accepted only when all applicable criteria pass:

### Product and catalog
- Search, favorites, recents and categories work offline and remain consistent after restart.
- All 18 tools are discoverable, and the catalog contains no duplicate or abandoned entry.
- Each tool's primary task is understandable without helper Pokémon.
- No GEAR action awards Pokémon, items, currency, XP, friendship, rank or progression.

### Correctness and resilience
- Calculator and everyday-calculator functions have unit tests for normal values, edge cases, invalid input, precision, rounding and locale.
- Currency conversion tests cover all-row editing, active-source switching, rate normalization, missing/stale/manual rates, minor units, fee calculations, expression inputs, cache fallback and legacy `GearRates` migration.
- Unit conversion tests cover affine units, bits/bytes, SI/US/Imperial distinctions, precision and invalid dimensional conversions.
- Timer tests cover multiple timers, pause/resume, add time, process death, reboot, clock changes, notification denial and duplicate completion delivery.
- Stopwatch tests verify monotonic elapsed time and lap calculations across backgrounding and wall-clock changes.
- Sensor tests cover missing hardware, noisy readings, calibration, permission denial and lifecycle stop.
- Waypoint tests cover accuracy metadata, manual entry, permission denial and no background tracking.
- QR tests cover malformed and malicious payloads, safe preview and no automatic navigation.
- Network tests distinguish reachability, latency, packet loss and throughput; no ping-as-speed-test labeling.

### Privacy and security
- Automated tests prove picker lists, waypoints, timer history, conversion presets, QR history and tool preferences are never uploaded or synchronized.
- Microphone audio is never persisted or transmitted; microphone capture stops on exit.
- Location is not collected in the background.
- QR payloads are previewed and never auto-executed.
- No banking credentials, OTPs or account tokens are requested by Currency Converter.

### Android lifecycle and accessibility
- Test permission grant/denial/revocation, app backgrounding, process death, reboot, low-memory conditions, orientation changes and multi-window where supported.
- Verify notifications and alarm behavior on supported Android versions and target Samsung hardware.
- Test on Galaxy S26 Ultra and smaller reference widths; verify light/dark, font scaling, TalkBack, keyboard, focus, touch targets and reduced motion.
- Profile sensor sampling, flashlight, camera scanning, sound meter, memory and battery. No continuous work while hidden.
- Capture screenshot/golden tests for representative tools and states; verify on-device rendering rather than relying on previews alone.

## 13. Explicit deferred candidates

These are not part of the approved 18-tool core. Consider only after the core passes its acceptance criteria and a real use case is demonstrated:
- Screen ruler/protractor, magnifier, tally counter, color picker, metronome, sensor dashboard, document scanner/OCR and secure password generator.
- Interval timer may be promoted within Timer if the base timer is reliable.

Do not add weather, habit tracking, health tracking, expense tracking or another task manager to GEAR. Do not introduce crypto/metals in the default fiat currency board. Do not add background tracking or invasive monitoring.

## 14. Source-of-truth references

- Product-wide principles and module boundary: `../PRODUCT_BIBLE.md`.
- Implementation status and delivery phases: `../IMPLEMENTATION_GUIDE.md`.
- Shared PMD utility visual language: `../FEATURES/DESIGN_SYSTEM.md`.
- Shared notification scheduling, delivery and recovery: `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.
- Shared local persistence, transaction, backup/restore and migration contracts: `../ARCHITECTURE/DATA_AND_MIGRATIONS.md`.
- Android permissions, provider integrations and privacy boundaries: relevant documents under `../ARCHITECTURE/`.

## 15. As built

*Compiled for Android and covered by JVM tests of the rules (calculator, Everyday sums, dice, light, QR payloads, sensor maths, timers); not run on a device, with no TalkBack or font-scale evidence.* Gear pays nothing and has no store of its own: the actions are extension functions on `GameStore` (`app/gear/GearActions.kt`, `GearTimers.kt`), the screens are in `ui/gear/`.

**The 18 tools** (`GearTool`, grouped by `ToolCatalog` into Calculate, Convert, Time, Light, Choose, Measure; a tolerant local search with keywords, favourites, recents):
- **Calculator:** brackets, percentages, scientific functions, memory, Ans, optional history. **Everyday sums:** tip and split, percent, sale and tax, a loan, savings, trip cost. **Units:** lengths, weights, volume and cooking, temperature, data and more, starred pairs. **Currency:** reference rates from `api.frankfurter.dev` (a copy under 6 hours old is used; Refresh asks), entered rates, a board, a "what it really costs" fee mode; only the rates are fetched.
- **Timer:** several timers, length typed (`1h30`, `45s`), they ring with the app closed through the shared alarm layer (`gear-timers`). **Stopwatch** with laps. **World clock:** chosen zones, a time converted, date sums.
- **Flashlight:** torch, screen light (colour, brightness) and SOS (stops by itself). **Picker:** your list with weights and optional history. **Coin and dice:** `2d6+3` notation, optional history.
- **Compass, Level, Barometer** (pressure and a rough height from a sea-level setting): sensor readings. **Device check:** what the phone has and which permissions are granted. **Waypoints:** places saved on explicit capture, distance and bearing. **Sound meter:** level with an offset and a pitch reading, nothing recorded. **QR:** make a code from text, or scan one; a scanned payload is classified and warned about, never opened. **Network check:** connection details and a few round trips to a Cloudflare trace endpoint.

**Saved state** (all in the one `save`, device-only, every field defaulted so older saves load): `AppData.gear` (`GearData`: favourites, recents, home currency and board, manual rates, fee, unit favourites and category, calculator history, memory and angle mode, `timers` with their end times, the stopwatch, Picker weights and history, dice history, screen-light colour and brightness, World clock zones, sea-level pressure, up to 30 waypoints, the sound offset; each history has its own on/off switch, saved), `AppData.gearHelpers` (tool name to Pokémon id), `AppData.pickerOptions` and `AppData.gearRates` (the cached rates). No reward event, no record in the ledger.

**Helpers:** a Pokémon helps one tool and is then in no other place (`Places.GearHelper`); the choosers say where a Pokémon is now and leave out a team on an expedition. Visual only; a tool with no helper shows no empty strip.

**Android adapters** (`androidApp/.../platform/`): `AndroidTorch` (camera flash, no permission), `AndroidSensorFeed` (`SensorManager`), `AndroidCapabilities` (sensors and features read on demand, nothing started), `AndroidGearServices` (location fix on explicit capture, microphone on explicit start, network info, the Google code scanner), and the timers' alarm in `reminders/Alarms.kt`. Each asks its permission only when the tool is used.

**Known gaps** (REP-028 and REP-042 hold the owner's decisions): the tools to drop or merge (Device check, Barometer, Sound meter, Network check, Coin and dice into Picker); Everyday sums lack unit price, ratio, recipe scaling, VAT-inclusive and custom shares, and the date sums sit under World clock; no kept timer presets; the World clock has 28 fixed cities, no search, 24-hour only; timers and the stopwatch use the wall clock, not a monotonic source (a deliberate choice in `GearTimers`); a helper's sprite is not yet the tool's catalogue icon (the reaction fires on real actions only); every `changeGear` writes the whole save (REP-018), the Sound meter's spectrum runs on the main thread and the Stopwatch redraws its lap list 20 times a second; QR codes for Wi-Fi and contacts, Currency history charts, true north and calibration for the Compass, and the document scanner with OCR are not built; the capture minigame (section 7) is not built.

## Shared Pokémon ecosystem contract

GEAR has **no Pokémon acquisition method, party, progression, XP, items or Poké rewards**. Helper Pokémon may appear as visual-only tool icons, headers or restrained reactions. The Ranger-style capture minigame (section 7, not built) would be entertainment only. See `../FEATURES/POKEMON_ECOSYSTEM.md`.

---

## Testing reference

GEAR domain calculations, unit conversion, input validation, provider/device failure paths, Compose semantics and complete user workflows must be covered under [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md).

# Changelog

Versions are `MAJOR.MINOR.PATCH` ([semantic versioning](https://semver.org)), kept in the `VERSION` file. While the app is
below 1.0, a new feature bumps the minor number and a fix bumps the patch number. Bump `VERSION` in the pull request that
ships something; the first merge with a new number publishes that version's own GitHub release (the rolling "Latest build"
release always has the newest merge).

## 0.236.1

The loading pixels move again, and the work under them no longer stops the phone's frame clock. UI only: no reward rule, schema or save change.

- **The pixels follow the frame clock.** `LoadingDots` used an infinite tween, and a tween is scaled by the phone's animation setting while any work on the UI thread stops frames altogether, so the indicator could sit on its first pixel. It now steps every 300 ms from frame times, and holds three solid pixels under Reduce motion or with the phone's animations turned off.
- **Back up now and Restore no longer block the UI thread.** Both wrote or swapped the save on the UI thread, which froze the button, the pixels and the blocking overlay for as long as the file work took; they now run on `Dispatchers.Default` and apply their result on the UI thread, like the other copies already did.
- **Test:** `LoadingDotsTest` renders the composable at known frame times and reads the pixels back: they advance, hold under Reduce motion and hold with the platform motion scale at zero.

## 0.236.0

UI only: search, filters, add forms and rare controls now stay out of sight until asked for (`DESIGN_SYSTEM.md` 12B). No data, rule or storage change; `RewardRules.VERSION` stays.

- **Pull-down search and filters.** A shared helper (`PullReveal.kt`) opens search, filter and sort when you pull down at the top of a list. Used by Modules, Link People, the Base and Link menus, Mint Activity, Tasks (All and Done), the PC, the Pokédex and Gear.
- **Add keys.** Link People, Care routines and Mint Activity open their add form from one Add key instead of an always-open form.
- **Closed accordions.** Care diary (Feelings, Urges, Skills used); Heal (export, erase, imported records, Huawei readings and seen pages, today's drinks); Mint settings (overview, days off, categories, export, erase), Mint review statement check and Mint earlier imports; Me notification groups, exports and erase; Plan progress sections; the Pokémon More section (rename, natural moves, star, transfer); the World report (opens itself when recruits wait).
- **Heal hydration.** An Other amount key.

## 0.235.0

Owner's list of 23 November 2026, UI and one MINT form rule: no reward rule, schema or save-format change, `RewardRules.VERSION` stays.

- **Launcher: folders removed.** The Folders page, the folder widget, "Add to folder" in the app sheet and the Open folders gesture are gone (owner, 23 November 2026); old saved folders are ignored.
- **Launcher Pokémon no longer overlap, moonwalk or move in their sleep.** A stroll now chooses a stop clear of every other Pokémon's position and destination (room from the sprite size), and its path bends around sleeping ones; the facing and walk pose follow each stretch of the curved path instead of the net direction; a tap while walking no longer turns the Pokémon backwards; a sleeping Pokémon ignores taps (only the ripple answers), drops any hop or graze in progress when it falls asleep, and the sleeping pose wins over a hop (`TownBehavior.settle`, `TownBehavior.clear`).
- **Home: ready to meet, inside the module cards.** The Care card gains a second row "Dream World: N dreamers to visit" and the Link card "Friend Safari: N visits open" (Care and Link unlocked, Safari on), each with an Open key to that place; a card with one lifts to at least severity 1 in the sort. No separate box.
- **MINT: the IOU form loses "They paid me already".** An income is money already received, so an IOU income is always saved posted and stays marked "IOU" on the record; the toggle reads "Paid back by someone (IOU)" and the field "Who paid it back". An older pending IOU keeps "Mark received" (`MINT.md` 5.8A).
- **Launcher floor finds are rare.** Each of the 3 daily places holds a berry, vitamin or Poké with only a 25% chance (`TownFinds.APPEAR_PERCENT`, *tune*), decided by the date so reopening the home screen never rerolls; `RewardRules.VERSION` 34, golden vector regenerated (`docs/economy.md`).
- **Launcher: Hidden apps no longer sticks.** Opening "Hidden apps" from Settings made the hidden view the app list's starting view every time; it now resets when the list closes.
- **More item sprites.** `fetch_items.py all` fetched every PokeAPI/sprites item icon (2,029 more) into `assets/items-all` (not bundled; `docs/assets.md`).
- **WORLD Town uses Mystery Dungeon item pictures** where the sheet has a match: the five flavour berries (Kecleon shelf, Swap Shop) and the Gimmighoul Coin (`PmdArt.town`); every other item keeps its mainline icon.
- **Friend Safari asks before it visits.** The Visit key now confirms ("Use one of today's N visits?"), so a stray tap cannot spend a catch; nothing visits on its own.
- **Update notification.** When PokéLife opens it checks once for a newer build and, if there is one, posts a notification (once per version and build; a tap opens Me, where Settings → About installs it). It needs the notification permission the reminders already ask for; no background polling (`UpdateAccess.notifyAvailable`).
- **LINK: people as cards, random picture picker.** Each person is a small card with their Pokémon; the picker opens on a random draw of the whole Dex with Shuffle and "My Pokémon" (`LINK.md`).

## 0.234.1

- **Modules rows are flush.** The module-colour wash now runs edge to edge inside the Modules list box, against the dialogue frame instead of floating in the box's inner margin; the row contents keep their usual inset. A `TextBox` may take its own `contentPadding` for this, and `dialogueFillInsetX`/`dialogueFillInsetY` state where a box's fill starts. Only `MenuList(rowTint = true)` uses it; every other list is unchanged. No reward or schema change.

## 0.234.0

A pass on the bottom-tab screens (owner, 23 November 2026), UI only: no reward rule, schema or save-format change, `RewardRules.VERSION` stays and no golden vector was regenerated. The one settings addition (`weekStartDay`) is a serialized field with a default, so old saves load unchanged.

- **Home shows every module.** The three module cards and the "Also today" rows are replaced by one card per module (PLAN, MINT, BASE, CARE, HEAL, LINK, WORLD) in the module's own accent, with its lead, a truthful status line and one in-place action. A card with something due or overdue floats above the quiet ones; ties stay in the rainbow order, so what needs the owner is never below a quiet module. A screen-locked module and gated MINT show only "Locked" and an Open key, never a count.
- **Modules:** the "Jump to" search is hidden until the list is pulled down at its top (140 ms expand, instant under Reduce motion; the close key, Back or an upward swipe with nothing typed puts it away and clears the query). Each module row now carries a light wash of its module's accent inside the list's one dialogue box, and a search result is tinted by the module it opens.
- **Pokémon:** the menu follows the canon start-menu order: Pokédex, Party, PC boxes, Bag, Poké Mart, Quests.
- **Me → Settings → Date and time:** "Week starts on" is a dropdown (Sunday, Monday, Saturday) instead of a Monday toggle, and the choice now reaches the date picker, the Plan month grid and the launcher calendar widget. "Date format" is a dropdown of pattern strings, adding `MM/dd/yyyy` and `dd/MM/yyyy` to the existing styles.

## 0.233.0

- **Small updates (REP-077, after REP-075).** The updater no longer downloads the whole 116 MB APK for every change: the
  app now updates from the version's own release, and that release carries patches built from the last three versions'
  APKs (`tools/update/make_patch.py`). A patch lists the unchanged entries (about 30,000 sprites, cries, maps) as copies
  from the installed APK and carries only the changed dex, the signing block and the ZIP directory, gzipped: about 4 MB
  from the next version, up to about 20 MB after a few versions, against 116 MB for the whole file. The app rebuilds the
  exact signed APK from its own installed copy (no signing key is in the app), checks it against the release manifest's
  size and SHA-256, and installs it; a missing or damaged patch, or a different installed build, falls back to the full
  download. The release workflow now publishes the version's release, with its patches and its own `update.json`, before
  the rolling "latest" release; the app reads the rolling release only to learn which version is newest. Still to do on a
  phone: the patch download and the rebuilt APK installing over the running app. No reward rule changed.

## 0.232.0

One loading vocabulary, drawn from tokens and text only (DESIGN_SYSTEM 5.6 and 6.10): `LoadingDots` (three pixels), `LoadingState` (three pixels and one short line), the `Skeleton` family (`Skeleton`, `SkeletonRows`, `SkeletonPanel`: static pixel placeholders of the shape that is coming, never a shimmer or a pulse), `LoadingScreen` (a full screen that has only tokens and text) and `LoadingOverlay` (a scrim that blocks the page). A button's loading state now keeps its label's width, so the row does not jump, and announces the action ("Save, working") instead of going silent.

It is applied to the app's opening screen, Settings' backup and restore (a blocking overlay while the save is replaced), Health Connect connect and disconnect, Gear's locating and connection check, the contact import list, and portraits while a face decodes. UI only: no reward rule, schema or save changed; `RewardRules.VERSION` stays and no golden vector was regenerated.

## 0.231.2

- **Emotional wellbeing’s All data:** the page’s All data carries the same **Emotion** and **Stress** tabs, so the walk opens it once and reads both tabs there (the emotion days and the stress averages); the card page is scrolled only after the pills. If the tabs are out of reach of accessibility they are tapped by position at the top left, from the owner’s 22 November 2026 screenshots. No reward or schema change.

## 0.231.1

Huawei Health read: retry a card that opens a blank window, and a second way into Cycle Calendar's menu (HEAL, REP-046, probe 13 of 22 November 2026). No reward or schema change.

- **Heart and Emotional wellbeing:** probe 13 opened both in two empty `FrameLayout` windows with no text, no four-dots button and no pills, so the walk could not open their All data. The walk now closes that blank page and taps the card once more **with a real touch at the card’s middle**, not another accessibility click, and logs whether the second try shows words. The owner’s screenshots (22 November 2026) show both pages and their native ⋮ menus with **All data** at the top; when the page still shows no words, the walk taps the top-right ⋮ position itself and reads that native menu.
- **Cycle Calendar:** probe 13 reached the page but its `More options` menu never appeared after a click or a tap. The walk now also tries a long press on that button, then a real tap at the top-right ⋮ position, and accepts a menu item about periods whose wording is not exactly "Period records" ("Period record", "Period history", …) by its pop-up item text. The owner’s screenshot shows the menu: **Period records, Settings, All data, About**.
- **Emotional wellbeing’s pills:** the page is scrolled only after both pills are read (a scroll had moved them away), and if the pills are still out of reach they are tapped by position — Emotion on the left, Stress on the right, about two thirds down the screen, from the owner’s screenshot.
- **Docs:** `INTEGRATIONS_AND_PRIVACY.md` 5.2.2 3c records the probe 13 result, so the code no longer claims the all-window read alone fixed Heart and Emotional wellbeing.

## 0.231.0

Every species and every regional form obtainable (REP-044 first slice): the Mothim evolution fix, regional forms from wild Finds, Galar's three birds and Mega Stones. No reward-rule change: World payments are outside the reward engine (docs/economy.md says so), no reward-engine outcome changed, `RewardRules.VERSION` stays and no golden vector was regenerated. No schema change.

- **All 1,025 species and 57 regional forms now have a route.** Module eggs and evolution cover every default form, WORLD wild Finds in a region's layer give the 57 regional forms, and quests and WORLD legends give the 125 legendary, mythical, Ultra Beast and Paradox species.
- **Mothim can be reached.** In `EvolutionRules.applicable`, a Pokémon in its species' default form now keeps the generic (`requiredForm == null`) evolutions beside its own form rows, so Burmy's Wormadam cloak rows no longer hide the Mothim branch (#414).
- **A wild Find keeps its home region's form.** `RegionalForms` maps a regional form to its layer (Alolan to Alola, Galarian to Galar, Hisuian to Hisui, Paldean to Paldea, White-Striped Basculin to Hisui); the Find carries the form (`Find.formIdentifier`, `WorldEvent.Found.formIdentifier`) and recruiting it grants that form. The wild tables gained 15 species, so every regional form now has a home-layer encounter: Alola (Raticate, Sandshrew, Vulpix, Grimer), Galar (Meowth, Ponyta, Slowpoke, Farfetch'd, Corsola, Zigzagoon, Darumaka, Stunfisk), Hisui (Voltorb, Zorua) and Paldea (Tauros, its three breeds); every one is in that game region's dex.
- **Nine blocked final evolutions open up:** Obstagoon, Perrserker, Cursola, Sirfetch'd, Runerigus, Basculegion, Sneasler, Overqwil and Clodsire now have their regional pre-evolution; Mothim is the tenth.
- **Galar's three birds are legend quests.** Clearing Slumbering Weald, Dusty Bowl and Galar Mine No. 2 three times each grants Galarian Articuno, Zapdos and Moltres, with no Guardian gate; `LegendQuest.form` stores the granted form and `worldLegends` avoids a duplicate of the same species and form.
- **Mega Stones have sources.** All 99 Mega forms' stones are sold on the Kecleon Shop's own always-sold Mega Stones shelf for 2,000 Poké each (ten to a page in Town; the daily five shelf never carries them), and can drop when a silver (3%) or gold (10%) treasure box is opened, only a stone not owned yet. The form dex counts the Mega form while its Pokémon holds the stone, so every Mega form can now be shown and collected.
- **Tests:** `EvolutionRulesTest.burmyKeepsBothTheWormadamCloaksAndTheMothimBranch`, the new `world-core` `ReachabilityTest` (all non-special species, all regional forms, White-Striped Basculin, the ten formerly missing species and the wild-form mapping), `KecleonShopTest.everyMegaStoneIsAlwaysSoldOnItsOwnShelf` and `WorldStoreTest`'s Galar birds, regional find recruitment and gold-box Mega Stone tests. `jvmTest` (except composeApp, which needs libGL) and `compileKotlinWasmJs` pass.

## 0.230.0

- **The app updates itself (REP-075).** Me → Settings → About has an Updates box: opening Me checks the rolling "latest"
  GitHub release once per app start, and About shows the new version and its size, downloads the APK into the app,
  checks its size and SHA-256 against the release manifest, and hands it to the Android installer. The phone's "install
  unknown apps" permission is asked for once, and the box leads to that page when it is missing. The repository is public,
  so the check needs no token and the app keeps no account or credential; nothing is stored but the downloaded APK.
  `release-apk.yml` now publishes `update.json` beside `pokeplan.apk` (version, build, size, checksum, commit, date) in the
  "latest" release and in each version's own release, and the app falls back to the GitHub release API for a release
  without the manifest. **The first install of v0.230.0 still comes from the release page**: the app that is on the phone
  now has no updater yet; every build after this one installs in the app.

## 0.229.0

Huawei Health read: every card's All data, two read modes (HEAL, REP-046, owner feedback 22 November 2026). No reward change: `RewardRules.VERSION` and `docs/economy.md` are unchanged. No schema change.

- **Two reads in Connections:** **New only** (the default) skips what HEAL already holds and stops lists at the first saved row; **Everything** opens every card and its All data again, reads the longer lists (Exercise 20 scrolls, All data 30, the newest 31 Sleep days) and has a 900-second budget. Both visit the cards in the owner's order: Steps, Exercise records, Heart, Weight, Sleep, Skin temperature, Emotional wellbeing, SpO2, Cycle Calendar.
- **Every module opens its All data:** Steps, Heart, Weight, Sleep, Skin temperature, Emotional wellbeing (each of its Emotion and Stress pills) and SpO2 open the four-dots menu → All data; Cycle Calendar opens Period records. If an Emotional wellbeing pill cannot be found, the page's own All data still opens.
- **Menu and page fixes:** the walk now returns to the card page after an All data list before tapping the next pill or menu item (Emotional wellbeing's second pill and Cycle Calendar's Period records were looked for on the history page, so they were never found); each menu is opened once per item, the menu button and its items are looked for in every Huawei Health window, and a web page's menu gets longer to draw. Slow pages now have 14 seconds to show words, and every Huawei Health window is read, not only the active one: Heart and Emotional wellbeing open their page in a window of its own while a FrameLayout is active, which is why the last probe found no words, no menu button and no pills there.
- **Sleep days one by one:** a new read opens the newest 14 Sleep days, an all read the newest 31; each day's nights and naps keep their real times.
- **Controls report:** `SpO₂` and `SpO2` are the same card, so a report no longer says the SpO2 card was never seen when Huawei writes the subscript.
- **Tests:** `HuaweiWalkTest` covers the order, the two modes, the longer budgets and the canonical `SpO₂`/`SpO2` title. Not run on a phone.

## 0.228.3

- **The off-phone backup folder is tested (REP-034, #382).** `SafBackupFolderTest` runs `SafBackupFolder` against a fake documents provider
  with a tree URI: listing, writing (and replacing), reading and deleting go through `DocumentsContract` and `ContentResolver` the way a
  phone would. Robolectric runs it in `./gradlew :androidApp:testDebugUnitTest`; a run on a real phone with a cloud-synced folder is
  still worth doing before calling the folder signed off.

## 0.228.2

- **New APKs install again after the hand-built 9,001 debug APKs.** The Android `versionCode` now comes from `VERSION`
  (`0.228.2` becomes `228002`) and never from a CI run number or a hand-set `POKEPLAN_BUILD`: a debug APK built with build
  number 9,001 had left every later release, numbered in the hundreds, refused by Android as a downgrade
  (`INSTALL_FAILED_VERSION_DOWNGRADE`). `POKEPLAN_BUILD` still labels the build in Settings → About and in the release
  title. Every build of one version shares its `versionCode`, which Android accepts as an update; bump `VERSION` to
  outrank a build already installed.

## 0.228.1

The comment-audit issues (GitHub #381 to #411): dead fields and constants removed, the QR mask picked by the standard's
rules, one cached-image helper, honest Heal scrubbing, Help text that used to live only in comments, and the tool fixes.
No reward change: `RewardRules.VERSION` and `docs/economy.md` are unchanged.

- **Retired settings removed:** `Settings.musicVolume` (nothing played music), `Settings.worldKeepPartyOut` and
  `Settings.careModule` (forced on at every load) are gone, with the branches that read them (Home, Modules, Jump to,
  Collection, Pokémon detail, Care reminders, the Care widget). Old saves still load: the JSON ignores unknown keys, and a
  test covers a save that still carries all three.
- **Dead code:** `BuddyReaction.petPool` (only a test called it), `PickForMe.BONUS_MULTIPLIER` (a copy of
  `RewardEngine.PICKED_BONUS` that never paid), `SourceKind.DERIVED`/`ESTIMATED`, `WorldData.shopCandies` and
  `WorldData.worldVersion` (nothing read or wrote them; old saves load), the `Units` shim, and the legacy
  `STREAK_LATER`/`RECAP_LATER` alarm kinds and their cancel loop.
- **Numbers in one place:** `GameStore.CARE_DAILY_CAP` uses the `CareRules` limits (which gained the rhythm and Play
  numbers), the Plan colour names live beside `TaskList.COLORS` for both editors, the launcher widgets size app rows from
  `PinnedAppRow` (the row's own layout, including the pixel font's line) instead of guessed 80/56 dp, and `:data`'s
  Guardian slot order is tested against `Guardians.MODULES`.
- **Heal:** `HealTables.scrub` now reports whether VACUUM and the checkpoint succeeded, records a non-sensitive marker,
  and is retried at the next start until both pass; a test makes VACUUM throw.
- **QR:** the mask penalty follows ISO/IEC 18004 N1 to N4 exactly and is scored with the format area still light, as
  reference encoders do; the chosen mask matches python-qrcode for a set of texts (its codewords match ours byte for byte).
- **Images:** one `rememberCachedImage(cache, key) { … }` holds the reseed-then-load-then-cache rule that about fifteen
  composables copied with the same explanation.
- **Rain:** the town's rain now runs at the game's own pace: effect 16 is the `efbg004` background effect, 2 ticks (about
  33.5 ms) a frame, not the guessed 70 ms, looping the sheet's 61 drawn frames (`GbaWeather.RAIN_TICKS`); the source trace is
  in `LAUNCHER.md` 4.4.
- **Help:** the bill-schedule rule (a payment locks the schedule; pause the bill and add a new one) and the phone-calendar
  rule (read-only, never pays) are in Help, and the comments that held them on the Mint and Settings screens are gone.
  The stale "turn Care off" line is gone too. The buddy stage's level chip is a full 48 dp target.
- **Tools:** `fetch_rescue_maps.py` returns the position from `chunk_rows` instead of re-walking every BMA layer;
  `extract_sky_maps.py` only skips entries whose own data is empty (a real decode failure stops the run) and writes
  `skipped.tsv`; `convert_pmd_sfx.py` names the songs the owner confirmed and writes `assets/sfx/provenance.tsv`.
- **Signing:** debug builds keep the tracked debug key; `release` takes its key from CI secrets and is unsigned without
  them, never quietly signed with the debug key (`INTEGRATIONS_AND_PRIVACY.md` 4.4).
- **Still needs a person:** auditioning the guessed UI sounds and the story cues (#390, #403), the renderer's vibrato,
  pan and reverb (#404), the PMD-inspired dungeon species (#407), the later regions' map art (#408), a real phone for
  Health Connect and the SAF backup folder (#383, #382), and the owner's release key (#381).

## 0.228.0

MINT: an IOU is income someone owes the player back, and the Shared bills ledger is gone (owner, 22 November 2026). No reward change: `RewardRules.VERSION` and `docs/economy.md` are unchanged, because an IOU pays nothing.

- **Removed: Shared bills** (`MINT.md` 5.8A). The display-only Splitwise-style ledger is deleted: the screen (`ui/mint/Shared.kt`), its Mint section and menu row, `SharedEntry`/`SharedKind`/`SharedRules`/`SharedBalance`, `MintStore.addShared/settleShared/deleteShared`, the `shared` list in `MintData` and `MintKinds.SHARED` (export and erase). An old save that still carries the `shared` key loads unchanged; the entries go on the next save.
- **Added: IOU as income owed back.** A `Transaction` of type `INCOME` with `iou = true`; the person who owes is its `payee` and its category is a spending category, like a refund. It is `PENDING` while owed (balance unchanged, shown as expected) and `POSTED` once received (Mark posted, labelled "Mark received" / "Mark owed"). It counts as no income (`MintRules.earned`), pays no reward, and lowers spending and budgets in its category by its amount from the moment it is recorded, owed or received (`countsForSpending`, direction -1; excluded and voided do not count).
- **Income form:** toggle "Someone owes me this (IOU)", "Who owes it" and "They paid me already" (saves `POSTED` instead of `PENDING`).
- **Rules:** only an income can be an IOU; no splits; an edit keeps `iou` as it was; a plain income cannot be turned into an IOU by editing.
- **No new storage:** the flag rides inside the transaction's own row, so there is no `.sqm` migration and no schema step.
- **Tests:** `MintIouTest` (4) and `ScreenshotTest.mintIou` in both palettes; `MintSharedTest`, `SharedRulesTest` and `SharedBillsSealedTest` are gone. Not run on a phone.

## 0.227.0

Huawei Health read: less scrolling, one pass (HEAL, REP-046, owner feedback 21 November 2026). `RewardRules.VERSION` unchanged. No schema change.

- **One pass down the Health page:** each wanted card is opened as it comes into view and Back returns to the same place, so the page is no longer scrolled to its end and back up before the cards are opened. The pass ends when every wanted card has been opened.
- **Exercise records stops early:** the list is newest first, so scrolling ends at the first session older than a read can pay for (8 days), instead of always scrolling 6 times.
- **Budgets per page:** Weight and Sleep 2 scrolls each (Sleep's Week tab 2), Heart 1, Exercise records up to 8 with the cut-off above. New pure `HuaweiWalk` (tested); the service only taps and scrolls.
- **Controls debug script:** `python3 -I tools/huawei/controls.py` (needs `adb` and USB debugging, nothing switched on in the app) dumps the Huawei Health screen in front with `uiautomator`, digits hidden, and lists what can be tapped, long-pressed or scrolled, which label is tapped through which clickable ancestor, the main scroller, and whether each card the walk opens is found and tappable. `--file` reads a saved dump; `--out` saves the report. The app's own capture now also tags each control with the actions Android offers (`do:click+long-click`).
- **Controls check in the app:** Heal → Connections → Huawei Health, **Show controls and pages** now starts with a Controls report built from what the reader saw: per page how many controls can be tapped and scrolled, and for each card on the Health page whether it can be tapped and which element takes the tap (the same nearest-clickable-ancestor rule the walk uses), plus the cards the walk wanted but never saw. A **Walk log** lists each step of the last read (card in view, tap sent or refused, page opened, Week tab, Back, scroll moved or not) with card titles and outcomes only. Both are part of **Save, numbers hidden**. Memory only, like the rest of the capture.
- **Fix from the owner's first capture of this build (21 November 2026):** the four-dots button is described "More items", which the walk did not know, so All data never opened; worse, the fallback matched the toolbar icon beside it by its id (`hwappbarpattern_menu_icon`), which is Share or Stats, and opened the Share preview and Stats (backed out of, nothing shared or saved). The button is now found by its description only, and Share, Stats, Add and Edit are never tapped. A click refused by a control is retried on its clickable ancestors (the Steps summary was refused), and the Steps log line no longer says "not found" after a visit.
- **Second capture (21 November 2026):** All data now opens for Sleep, Skin temperature and SpO2 (`PrivacyDataModelActivity`: month headers, then a value and a date per row; not parsed yet). Weight, Health Clovers, Activity rings and Cycle Calendar have no All data in their menu (theirs are Health plan, Weekly report, Reminders, About, Edit goals), so they are looked at only. The walk now tries a refused tap again after a moment on a fresh look at the element, writes the chain of elements it tried to the walk log when it still fails (the Steps summary), and waits once more for a page that shows no words yet, logging how many elements each page had (Heart and Emotional wellbeing captured nothing).
- **All data kept as history (owner, 21 November 2026, "keep all"):** the All data lists of Sleep, Skin temperature and SpO2 are scrolled until a row is 90 days old (at most 14 scrolls) and every row becomes a reading in Heal → Readings: **Sleep length** (a night or a nap, in minutes), **Skin temperature, lowest and highest** and **Blood oxygen, lowest and highest** for the day, each stamped at noon of its own day, never judged, never paid (history is old, and a read still pays once a day per family). New reading types in the sealed `heal_vital` JSON, no schema change. Reading again adds nothing twice.
- **Sleep entries opened one by one:** each Sleep All data row opens to its bed and wake times, so the walk opens the newest 8 and captures each (the list's Delete icon is never touched); the times are not parsed yet, they wait for the next capture. Skin temperature and SpO2 are now cards the walk reads (not just looks at); Health Clovers and Activity rings are no longer opened.
- **Weight and Cycle Calendar menus:** their four-dots button is described "More options" and their menu is drawn a moment later; the walk waits longer and looks for "All data" in the item's text or description.
- **A real touch for the Steps summary:** its click is refused even though it is marked tappable (it handles touches itself), so after every click is refused the walk touches the middle of the control. This needs the screen reader's **gesture** permission (`canPerformGestures`); Android may ask you to switch screen reading off and on once after this update. It is used for that fallback only. A read may take up to 330 seconds.
- **Sleep days become real nights (third capture, 21 November 2026):** each Sleep All data row opens to a day page ("11/12/2026 data") that lists that day's nights and naps with a length and a span ("11/11, 11:30 PM-11/12, 5:50 AM"). The walk opens the newest 14 days and `HuaweiRecords.sleepDay` turns every session into a real Sleep record with its bed and wake times (id from its start, so reading again adds nothing twice); a day with real times keeps no length-only reading, and today's night from the Sleep page is replaced by the real one. Sleep records pay like any import (the usual once a day per family).
- **Diagnostics for what still fails:** a refused tap now logs whether the real touch was sent, refused or not allowed; a web page's menu (Weight, Cycle Calendar) is also tried with a touch; a card page that shows no words has every Huawei Health window read (Heart and Emotional wellbeing); a read may take up to 420 seconds.
- **Fifth capture (21 November 2026):** the Steps touch was never tried (a control with few ancestors made the tap give up before it reached the touch fallback); fixed. The probe kept only the newest 40 pages, so the first pages of a long read (Health page, Weight, Heart) were lost from the saved file; it now keeps 150 pages and 400 log lines. The Weight and Cycle Calendar menus still did not show after a touch: "All data" is now looked for in every Huawei Health window (a pop-up may be a window of its own), and those windows are captured when the menu opens.
- **Readings are modules (owner, 21 November 2026):** Heal → Readings is a list of tiles, one per kind of reading (Steps, Heart rate, Blood oxygen, Skin temperature, Sleep length and score, Stress), each with its newest day; tapping one opens its own list, one line per day, with Delete for a day. Only kinds with data show. New pure `ReadingGroups` in heal-core (tested).
- **Heart rate is a range per day:** the Health page's heart rate is no longer one entry per bpm; a day keeps its lowest and highest (new reading types Heart rate, lowest and highest) with the resting rate beside it, and a later read only ever widens the range (the same for blood oxygen and skin temperature). Readings saved before keep showing, as one-value ranges.
- **A read skips what has nothing new:** it now knows what HEAL already holds from Huawei Health (`HuaweiWalk.Known`, from `HealStore.huaweiKnown`): Sleep is skipped when tonight's night is saved, Skin temperature and SpO2 when today's are, Weight when today's is; Exercise records and the All data lists stop scrolling at the first row already saved, and the Sleep days stop opening at the first saved day. A first read, with nothing saved, does everything as before. The Walk log says "skipped, nothing new" or "reached a saved day".
- **Seventh capture (21 November 2026):** the touch worked: **Steps now opens**, and so does its All data page ("All recorded data": a month header, then "N steps" and a date per row). Each row is saved as that day's steps (history, never paid; the largest count seen for a day wins). A fault found on the way: the Health page reader counted an All data row such as "6,842 steps" as today's steps; it now reads only the Health page. The Weight and Cycle Calendar menu items are plain text with no clickable parent (so they were never found): "All data" is now matched by its text alone, then clicked or touched. The skip rule uses the newest saved row of each All data list: a row of yesterday means nothing is new until tomorrow, so Steps, Sleep, Skin temperature and SpO2 are skipped on a second read the same day; the single value the Health page shows for today no longer counts as having read the list.
- **Lag fix, first part (owner: "Heal seems super slow", 21 November 2026):** the Huawei page parsed every captured page (up to 150) each second while a read was running, drew all their text when "Show controls and pages" was open, and recomputed the Health summary on every redraw; it now parses only when no read is running, draws the newest 6 pages (Save, numbers hidden still has them all) and checks every 3 seconds when idle. The walk log ends with how long the read took and how much of it was spent reading Huawei Health's screens (that work runs on the app's main thread). Still open, in the sealed Heal storage: every save decrypts every Heal row first, so the hundreds of history rows added by All data make each save slow on a phone (see the 0.225.2 notes).
- **Lag fix, second and third parts (owner: "Heal seems super slow", "explore asynchronous ways", 21 November 2026):** (1) **Heal saves open only the rows that changed.** Heal keeps its rows as last written, in memory only, and uses them while they provably still match the database (same sealed state, same key object, same row count and text length per table); a save then seals just the changed rows and a read or the locked check opens nothing. Anything else, and every erase, clear, seal, start or failed save, throws the copy away and the rows are opened as before. Nothing sealed is written differently, a missing key still blocks writes, and the 16 sealing tests pass beside 6 new ones (`HealRowCacheTest`). (2) **The Huawei read runs off the app's main thread.** Reading Huawei Health's screens is a call to another process per element and used to run on the main thread for minutes; it now runs on a thread of its own, and only its result and the service switching itself off go back to the main thread.
- **Tenth capture (21 November 2026):** a page now has up to seven seconds to draw before the walk gives up on it (Heart and Emotional wellbeing opened a window with no words; Cycle Calendar draws its toolbar late); a card whose click is taken but opens nothing is touched; the menu button of a web page is waited for and its menu looked for for a few seconds after the touch; Emotional wellbeing is read as two pills, emotions and stress, each captured and given its own All data. The walk log says "click opened nothing, touch sent" or "pill Stress opened".
- **Cycle Calendar's Period records (owner, 21 November 2026):** after All data, the walk opens the other item of that menu, Period records, captures it with a scroll or two and comes back (nothing is changed or saved from it). When a menu item is not found the walk log now says how many windows exist, how many are Huawei Health's and their kinds (no text), and the first line of a read says the Android version and whether the service is declared an accessibility tool, to tell a slow page from one Android does not show to this service.
- **Steps too:** the Steps summary at the top of the Health page is opened first (2 scrolls) so its page is captured; it is not a card, so the walk finds it by its own element. Not parsed yet. Its four-dots menu → **All data** is opened too (menu button found by description, item by text; 4 scrolls), and so is every other card's that has one (owner: almost all do; Exercise records does not), 3 scrolls for Heart, Weight and Sleep and 2 for the rest; the walk log says which step worked. The walk now presses Back only while the Health page is not showing, so extra pages are closed without leaving Huawei Health.
- **Every card is opened (owner, 21 November 2026):** besides Exercise records, Weight and Sleep (read into records), the walk now opens each other card on the Health page once (Heart, Skin temperature, SpO2, Health Clovers, Activity rings, Emotional wellbeing, Cycle Calendar and any other), one scroll each, to capture its page so it can be parsed next. Nothing is saved or paid from them. A look-only card gets two extra Back presses before the walk gives up, and a read may take up to 240 seconds. Cycle Calendar is private: its page is only held in memory and only leaves the phone if you tap Save, numbers hidden. To confirm on the phone: the time of a read and that Back returns after every card.
- **Heal's opened-row cache (21 November 2026):** saves no longer open every sealed row (one Keystore call each) to find which changed: the rows as last written are kept in memory, used only while the database provably still matches (same key object, same row count and text length per table), and dropped the moment it does not; erasing, sealing or activating Heal forgets it. Nothing sealed is written differently.

## 0.225.4

Performance and battery pass (static audit, no device trace; owner, 21 November 2026). No reward change: `RewardRules.VERSION` and `docs/economy.md` are unchanged.

- **Nothing runs behind a stopped app.** The 2 Hz tick, the power-saver poll, sprite and status animations, the buddy's drift, the egg wobble, Deep Focus's accelerometer, the Gear sensors, the sound meter, the SOS torch, the Huawei poll and World's wait stop while the app is not in front (`rememberInForeground`). A locked screen is no longer a Deep Focus movement.
- **Start-up:** the database opens off the main thread behind a small loading screen, and the normal start runs `PRAGMA quick_check` (the full check stays for tests and recovery). The daily sealed backup is WorkManager's: a due copy is asked for at every start and the periodic job runs daily (storage and battery not low, two retries).
- **Heal** rows are decrypted once per read, not twice (`readOrNull`), without letting an exception leave a surrounding transaction.
- **Widgets and reminders:** widget bitmaps are capped (512 KB each, 16 MB cached) and widgets are refreshed on leaving the app only after a change to the save or to Mint; a fired reminder reuses its plan, unchanged alarms are not set again (lost ones are), and the boot receiver is not exported.
- **UI and rules:** the Pokédex is a lazy list; sprite sheets and tables decode off the UI thread; the day dither is built once per size; Mint balances are remembered; the journal field no longer recomposes the screen per key; `GameStore.tick` and the streak do less; a bill's occurrences start at an arithmetic index (`BillOccurrencesWindowTest` checks it against the walk from the first date).

## 0.225.3

Navigation audit (REP-074; the map and the findings are in `docs/pokelife/evidence/NAVIGATION_AUDIT-2026-11-21.md`). Back behaviour, tab places and the Pokégear overlay; no reward change: `RewardRules.VERSION` and `docs/economy.md` are unchanged.

- **Back closes a page before it leaves its section.** `SectionScaffold` now knows the page opened inside it (`UpTo`), so the header's Back key and the system Back gesture do the same thing, one level at a time. World's Town facility and Journal pages use it: their header Back used to leave World while the system Back and the on-page "Back to the …" key only closed the page (`world/World.kt`).
- **The Pokémon detail's system Back no longer leaves the app.** It drew a Back key but registered no handler (`pokemon/PokemonDetail.kt`).
- **A jump to a Pokémon works while its tab is open.** The Pokémon tab reads the one-shot jump itself (Home's buddy chip, a widget's Pokémon, the Party shortcut), so it lands when the tab is already showing and wins over a saved place.
- **The Pokémon and Me tabs keep their place.** A section, an open Pokémon, a Pokédex entry and the Me section are saved with `rememberSaveable`, so another tab, or the app being put away, finds them where they were, as the Modules tab already did for its module.
- **Care and Plan close their pages first too.** A skill's guide, Care Play and the Pokémon panel in Dream, a calendar draft or event panel and an open task card now register with `UpTo`, so Back closes the page before it leaves the place (Care's and Plan's system Back used to leave the section, and lose a draft).
- **The intro has a Back.** It goes to the step before (the greeting line by line) and leaves the app only on the very first line, instead of exiting from every step (`screens/Onboarding.kt`).
- **A second tap on a tab returns it to its first screen.** The Pokémon and Me tabs now do what Modules already did: a tap on the tab you are on goes back to its menu (a restored place is not reset by the restore).
- **A Pokémon opened from Home or a module goes back there.** Back from that Pokémon returns to the tab it was opened from; a plain visit to the Pokémon tab keeps the usual Back to its own list.
- **A destination replaces the Pokégear.** A widget, shortcut or notification no longer opens its screen behind the overlay; only Gear opens it (`AppModel.open`).
- **Mint starts a plain entry from outside.** A kind left by an earlier quick action (a transfer, say) no longer pre-fills the next entry reached from a widget or notification.
- **Docs:** `DESIGN_SYSTEM.md` 6.7 gained the two rules (a page inside a section is one level up first; every tab keeps its place).
- **Tests:** `BackChainTest` (a tab switch keeps the Me and Pokémon places; the Town's Back goes to the town, then out of World) and `NavigationTest` (the Pokégear and the Mint kind).

## 0.225.1


Docs review and cleanup (owner, 21 November 2026). No reward change: `RewardRules.VERSION` and `docs/economy.md` numbers unchanged.

- **Docs pruned and realigned:** `docs/` lost about 4,500 lines of history, duplicated figures and retired material; names, versions and statuses were checked against the code; HOME reads BASE except in stored names. The Implementation Guide status table is rebuilt for 0.225.0. Specs now say: goals complete when all their milestones are done, Plan has no priority field, Mint's Quick actions are Expense, Income and Transfer, eggs hatch from the Plan Region (no picker).
- **Deleted:** the UI review and the 0.199.0 audit under `docs/pokelife/evidence/`; `docs/economy-report.md`, `docs/world-economy-report.md` and `docs/world-story-script.md` (generated on demand by `simulateEconomy`, `simulateWorldEconomy` and `storyScript`, and now git-ignored).
- **Plan:** the quick-add box on Plan's main page has the "Quick actions" title like the other modules.
- **Dead code removed:** egg tickets (`EggTickets.kt`, `AppData.eggTickets`); nothing issued tickets. Old saves that still carry the key load unchanged.
- **Comments:** stale comments in Kotlin and Python corrected (Base for Home, sealing, backups, candy curve numbers, story sources).

## 0.225.0

LINK people and Friend Safari (REP-072; owner, 20 and 21 November 2026). `RewardRules.VERSION` 32 to 33; `docs/economy.md` updated.

- **A screen for each person:** inside LINK People a person opens their own screen (header "Profile", "My profile" for the Me card, back to the directory) instead of an accordion under the row.
- **Profile card:** a Pokémon the user chooses as picture (`Person.avatar`, from caught Pokémon or a search of all species, in the ordinary Portrait frame), the whole name wrapped, role and organization, tags for Me, relationship and Pinned. Cosmetic: pays nothing. No schema step.
- **Friend Safari** (`LINK.md` 9.5): each person, the Me card included, has a pool of Link Region Pokémon seeded only by a hash of their opaque record key. Slots: 2, plus 1 per 6 people (people capped at 20, Me excluded), at most 5, rarities Common, Uncommon, Uncommon, Rare, Rare, fixed by the seed so adding people never reshuffles. A visit is free and meets one slot: species not caught yet weigh x6, caught directly into Link's party else the PC, ordinary shiny chance, candies for a new species only; no Poké, trainer point, track point or item.
- **Caps:** one catch per person a day and at most 5 Safari catches a day in total (`GameStore.SAFARI_PER_DAY`, *(tune)*, added so made-up contacts buy nothing; owner to confirm). Own event type `SafariEvent`, so Postcard Trail is untouched. Off when Postcard Trail is off or with the new Settings switch "Friend Safari on each person".
- **Not simulated:** the economy simulator plays only Plan, so no simulation was re-run; `docs/economy.md` gives an analytic bound. Built under Sonnet with the Opus advisor, not an Opus economy session.
- **Tests:** `LinkSafariTest` (8), `SafariRulesTest` (3), `ScreenshotTest` linkPersonScreen and linkPersonPicture, `NavigationTest`.

## 0.224.0

Me, Settings, Plan and Mint (owner, 20 November 2026). No reward change: `RewardRules.VERSION` and `docs/economy.md` unchanged.

- **Party perks fold away:** a reusable `Accordion`; each module's Today page shows its party perks behind it, closed by default.
- **Me, Stats:** a new page with totals (trainer points, tasks finished, minutes focused, streaks) that opens Plan progress and Care trends. The trainer card keeps name, buddy and rank.
- **Settings is a menu of pages under Me:** General (trainer name, sound and touch, look, Reduce motion, week start); Privacy and access (the screen lock for Care, Heal, Base, Link and Mint in one place; Mint's switch left Mint settings); Notifications (permission status with buttons, Quiet hours moved from Plan settings, one reminders switch per module, each module's lead time and hour shown only when its switch is on; Heal has none, its reminders are the times you add); Backups (complete backup, a Module exports card with one export per module, Reset); About.
- **Reset:** Erase this device now also resets Link and every module's settings, clears all the Android shared preferences and old save files, and cancels set alarms. The launcher's open layout refreshes only after a restart.
- **Plan, no categories:** tasks, habits, goals and lists lose their category. Insights and the Review recap split by list, the weekly recap names the top list, quick add `#word` is always a list, and the calendar and widgets take colour and icon from the list. Lists are made, edited and archived in Plan settings; the Lists row on Tasks is gone and the Filter on All and Done has a List chooser. Old saves' `category` keys are ignored.
- **Mint, Shared bills** (`MINT.md` 5.8A): a display-only ledger of who owes whom, kept in the sealed Mint state. It pays nothing and touches no account.
- **Baseline and padding (from phone screenshots):** two texts of different sizes on one row (a title and its count, a name and its value) now share a baseline instead of a centre line: the party header and perks, Me's stats, Mint's value rows, the volume rows (`BaselineTest`). Single labels in keys and fields were measured on the phone screenshot and are centred within a pixel. Care's "How is today?" box is a dialogue box like Quick actions under it (it was a form box with 16 dp inside the frame, not 24 by 18).
- **Launcher movement (0.221.0 regression):** Pokémon went tile by tile because the walk set the ghost fade with `fade.animateTo(..., tween(160))` before every square, and an animation to the value a state already has still waits its whole time (`AnimateToSameValueTest`): a 160 ms pause on every tile. The fade is now set beside the step and only when it changes. Not run on a device.
- **Not done:** the 12-hour clock and date format added in 0.221.0 are used by the launcher only; the rest of the app builds time text in about 22 places without a shared formatter.

## 0.223.0

LINK slambook profile (owner request, 20 November 2026, REP-071; `docs/pokelife/MODULES/LINK.md`, changed first). Built and JVM-tested only: not run on a device. Pays nothing and nothing reaches `LinkEvent`; no schema step (inside the person JSON); `RewardRules.VERSION` unchanged.

- **link-core:** `Person` gains `isMe` and `profile` (socials, likes, dislikes, slambook answers, MBTI, Enneagram type and wing, love languages, attachment, DISC, Big Five, birth details: time, UTC offset, birthplace with latitude and longitude, Kua formula). New: `Profile.kt`, `Birthplaces.kt`, `Insights.kt`, `Sky.kt` (Western natal chart: Sun, Moon, Mercury to Pluto, mean North Node, aspects, rising, midheaven, whole-sign houses; about 0.3 degrees), `BaZi.kt` (Four Pillars with Day Master and five-element count, Chinese zodiac animal and element at Chinese New Year, Kua number, Life Path; 1902 to 2098), `BaZiTables.kt` (generated by `tools/link/generate_bazi.py`), `LoreWestern.kt`, `LoreChinese.kt`, `LorePersonality.kt` (fixed plain-language readings). Tests: `BirthChartTest`, `BirthVectors` (vectors from ephem and lunar_python).
- **app-core:** `LinkStore.createMe`, `updateProfile`, `setBirthday`; `LinkRules.directory` pins the Me card first and search covers the slambook words.
- **UI:** `ui/link/LinkProfile.kt`: a Profile tab (About, Mind, Stars) after Overview in a person's page (Notes is now tab 3, Follow-ups tab 4), "Set up my profile" in People, a summary line on Overview.
- **Next:** Friend Safari (REP-072, `LINK.md` 9.5), which needs an economy session.

## 0.221.0

Launcher pass (REP-045; `docs/pokelife/FEATURES/LAUNCHER.md` 4.5, 6.2, 10 and 11.4, changed first). Built and JVM-tested only: none of it has been run on a device. No reward or economy change (`RewardRules.VERSION` unchanged), no schema change.

- **Town:** a soft shadow under every Pokémon; status and emotion icons are centred on one line above the head whatever their size. Trap effects fixed: a trap's own status icon used to pause the walker and cancel the Gust, Warp or Spin effect; those feelings no longer stop the walk. Ghost types take a short cut through walls only rarely (12% of long legs), fade while inside, and never rest or sleep in a wall (`canRest` no longer allows walls; a ghost caught in one walks out or is set on the floor). Meetings on a floor happen on their own squares, with up to one square between the two (Chebyshev distance 2 or less); nobody steps off the grid.
- **Species quirks (`TownQuirks.kt`, `Quirk`):** always asleep (Snorlax, Slakoth, Slaking, Abra, Komala, Munna, Musharna, Morelull), rooted (Metapod, Kakuna, Cascoon, Silcoon, Sudowoodo, Ferroseed, Ferrothorn, Sandygast, Palossand; set down in a room), disguised as another species of the town until tapped and back after a minute (Mew, Ditto, Zorua, Zoroark), slow at half speed (Shuckle, the Slowpoke line, Torkoal, Carbink), fast at 1.9 times (Vigoroth, Ninjask, Varoom, Revavroom, Zebstrika, Doduo, Dodrio, Cyclizar, Cinderace, Arcanine, Talonflame, Pikachu and clones), erratic (Spinda: 30% stumble a step, 8% when confused), timid (Ralts, Wimpod: flee from anyone within 2 squares). Every Guardian shows its type's stat icon (flame, ice, fist, shield, zap, skull, else twinkle) with a hop every 25 to 60 seconds.
- **Home button:** a finished (completed or failed) focus session no longer pulls Home back into PokéLife; strict focus applies only to a running one (`LauncherActivity.keepFocusScreen`).
- **Gestures:** swipe up and down reach lowered from 96 to 64 dp; new triggers: swipe up or down with two fingers, press and hold empty space; new jobs: Open folders, Open widgets, Edit the home screen, Go to the next page, Pull down notifications (`LauncherHost.expandNotifications`, reflection on `StatusBarManager`, new `EXPAND_STATUS_BAR` permission, used only for this gesture).
- **Launcher settings:** the duplicate Sound section is gone (global Settings keeps it). Date format and 12-hour clock moved to General Settings, "Date and time" (`Settings.dateFormat`, `Settings.clock12Hour` in `AppData`; week start was already global; the launcher's own `dateStyle`, `hour12` and `sundayFirst` are gone). Cycling lines became explicit choice lists (`PmdChoiceLine`, `PmdChoiceDialog`) for town size, weather, temperature, battery saver and every gesture; on/off lines are `PmdToggleLine` switches; battery saver options explain themselves. "Forget pins and icons" is now "Clear pinned apps and custom icons" and asks first (`PmdConfirmDialog`); new "Reset launcher settings" (`LauncherPrefs.reset`, keeps pins, icons, hidden, moved, folders, launch counts and the weather place) also asks first.
- **Open:** the Favorites folder "goes somewhere else" report was not reproduced.

## 0.220.1

Care: the five places (Today, Routines, Calm, Reflect, Dream) are reached from the Sections key in the header, as in every other module, with their state for today; there is no row of keys on the page. Today follows the order every module's main screen keeps (the short lead, today's routines and the mood key, a Quick actions box with the commonly done actions (Calm, Check in, Write), then Care's party; Dream keeps the party too). UI only; `docs/pokelife/MODULES/CARE.md` 3 notes it.

## 0.220.0

Care: navigation redesign (owner, 20 November 2026, REP-030; `docs/pokelife/MODULES/CARE.md` 1.5, changed first). UI only: no game-rule or economy change, no save or schema change, `RewardRules.VERSION` unchanged.

- **Five places:** Today (the landing: buddy, today's routines, mood key), Routines, Calm, Reflect, Dream, reached from the Sections key in the header (0.220.1 removed the row of keys 0.220.0 first put on the page). Back from any place goes to Today, from Today to the module list.
- **Calm:** the breathing break plus the DBT skills library. Each skill opens as a guided walk-through like the breathing break (progress bar, an intro page, one page per step with Next and Back, a closing page where "I practised this" pays as before); the breathing skill leads back to the break.
- **Reflect:** the diary card and the journal as two modes of one place; it opens on the journal when a draft waits.
- **Dream:** replaces the Meadow, the Visitors card on the landing and the Play screen. One dusk scene (the garden scene darkened, the buddy and the dreamers asleep with Zz; a tap wakes one for 2.5 seconds), "Spend time" (Care Play opened in place), the dreamers (the visitors) with queue, sent away and met, and Care's party.
- **Removed as separate screens:** Diary card, Skills, Journal, Meadow, Play. Jump-to targets are now Routines, Calm, Reflect, Dream. `CareMeadow.kt` became `CareDream.kt`; `playTarget` left `App.kt`.
- **Tests:** `BackChainTest` and `ScreenshotTest` follow the new places.

## 0.219.0

Mint: transfer fees, interest earned, the credit card flow and a flat reward for logging (rules version 32; `docs/pokelife/MODULES/MINT.md` 4.3E, 5.5, 5.6D and 16.2, and `docs/economy.md`, changed first). `RewardRules.VERSION` is 32 and `golden.json` is regenerated. No schema migration: every new field sits inside the sealed Mint rows with a default.

- **Transfer fee:** a transfer can carry `Transaction.feeMinor`. The source falls by amount plus fee, the destination by the amount only; the fee counts as spending (a fee), as cash out of liquid accounts and in a credit account's fee activity. It is one record, so edit and delete stay consistent. The Activity form has an optional Fee field (new transfer and edit) and the row says "fee X".
- **Interest earned:** a deposit account can earn interest (rate, daily or monthly schedule, tax, auto-post, start date). Pending estimates wait in an "Interest to confirm" box on the Overview, where each is confirmed, adjusted or skipped; auto-post books them. An "Interest" income category is seeded. The account add and edit forms have the Interest earned fields.
- **Credit card flow:** the Credit line box shows "Owed now", the latest statement balance, minimum and pay-by date, a "Pay card" or "Pay balance" button that opens Activity as a transfer to the card, and a "?" key opening "How a card works". The Activity form hints when you are paying a card.
- **Rewards (16.2 reversed):** logging an expense, a bill or installment payment or a transfer typed in today pays a flat reward (1 Rare Candy, 1 Ledger Egg-meter point, Mint lead friendship, Poke, no trainer point), never based on the amount, with no daily limit (frequent actions pay about like a Plan focus egg; farming is the player's own call). New `MintKind.LOGGED` (`record_logged`).
- **Outbreaks (MINT.md 16.2A):** a reconciled account or a finished weekly review also opens an outbreak: a pick of three different Mint Region Pokémon (better rarities than an egg), caught directly with no egg, with 2 or 3 shiny rolls plus one per week in a row with a review (at most 7). Shown on the Overview as "Outbreak: choose one". `MintEvent` gains a review `streak` (weeks, no figure); golden vector regenerated.
- **Tests:** `MintFeesAndInterestTest`, `MintLoggingRewardTest`, and LOGGED cases in `MintRewardTest`.

## 0.218.0

Plan: event companions and task reward focus (rules version 31; `docs/pokelife/MODULES/PLAN.md` 8.2 and 12.1, `docs/pokelife/POKEMON_ECOSYSTEM.md` 2.2 and `docs/economy.md`, changed first). `RewardRules.VERSION` is 31. No schema migration: `Task` and `PlanEvent` gained fields with defaults inside their JSON rows and Settings gained `defaultReward`; a test loads old rows.

- **Task reward focus:** a task has a reward focus, Exp, Egg or Items, chosen in quick add under More options or in the task editor's More tab. The default for new tasks is in Plan Settings, Rewards, "New tasks pay most in". Exp is the default and pays exactly what a task paid before (the golden vector's outcomes are unchanged; only its recorded input gained `focus`, and `rulesVersion` is 31). Egg pays half the candies of each roll and `ceil(tier x 1.5)` Egg points. Items pays half the candies and doubles the item-drop chance (cap 100%). Poké and trainer points never change. The focus is read when the task is ticked (it is part of the pending completion's task copy); repeating tasks' copies keep it.
- **Event companions:** a PLAN event can name a companion from the PLAN party (event panel: Companion; new event: Additional details). After an occurrence ends, the companion gains one Exp Candy's worth of exp (`XP_PER_CANDY`) per 30 minutes (a quarter hour counts as one, at most 4 an event, at most 6 a day across events) and has a 10% chance of one item from the drop table. Paid once per occurrence (the event keeps the dates it has paid, and moving a paid event keeps them), only for occurrences that end after the companion was named and within two days of ending, never for all-day events, skipped occurrences or device calendar events, and not if the companion left the PLAN party. Paid in `GameStore.tick` (`settleEvents`). A new reward `Reward.Exp` shows in the reward log as "<name> +N exp".
- **Egg meter:** it is 5 points in code and docs (`docs/design.md` said 10 by mistake).
- **Not simulated:** `simulateEconomy` plays the default focus only, so Egg and Items focus are not covered (numbers marked tune).

## 0.217.0

Plan → Tasks polish (owner, 20 November 2026; `docs/pokelife/MODULES/PLAN.md` 3 item 1 and 3.2, changed first). No schema change, `RewardRules.VERSION` and `docs/economy.md` unchanged.

- **Opens on Today:** the Tasks screen opens on Today. The views are four pills: Today, Upcoming, All, Done ("Open" is now "All").
- **No Inbox view:** open tasks in no list show under "No list" in All. The data and the quick-add parsing are unchanged.
- **Lists row:** the lists sit in a "Lists" row under the pills, folded by default and showing the chosen list's name while folded. Open, it is one row per list with its open count; Manage makes, edits and archives. Choosing a list narrows the open view (Today, Upcoming, All, Done) to that list without changing the view; the choice and the open state are kept across tabs.
- **Header and quick add:** the Calendar key is gone from the Tasks header (Calendar stays in the Sections key). The placeholder reads "Add a task (date, #list)". On Today inside a list a new task is planned for today, and the Today progress count follows the chosen list.
- **Screenshot tests:** `planner-inbox` removed, `tasks-lists-open` and `tasks-list-chosen` added.

## 0.216.0

Module pages share one order (owner, 20 November 2026; `docs/pokelife/FEATURES/DESIGN_SYSTEM.md` 5.1). No schema change; the rules change is the weekly review and frequency targets going (below).

- **Shorter lead:** a module's lead window is 112 dp high (was 144; Care's meadow was 248) with a 72 dp sprite.
- **Same order everywhere:** lead, overview, action items in a box of their own, party last. Mint's overview now lists a few accounts and budgets, and Expense / Income / Transfer moved below it; Heal's Log activity / Add a drink, Base's and Link's Add, and a new Care row (Routine, Calm, Diary) follow the same way. Care's visitors and Egg meter and the Base and Link search moved down beside the party.
- **Plan's page has no menu (owner, 20 November 2026):** lead (112 dp), overview (today's open tasks, five at most, each with a check key, an "All N tasks" button; one title when nothing is planned), action items (Inbox quick add, the task to do next with Focus and Pick for me), party last. Tasks, Calendar, Focus (phone), Goals and Settings are in the header `SectionsKey`, replacing More. `PlanSection` is TASKS, CALENDAR, FOCUS, GOALS, SETTINGS.
- **Weekly review scrapped:** no 1-berry payout, review card, past reviews, "Weekly review is ready" key or `Feature.WEEKLY_REVIEW`; the `WEEKLY_REVIEWS` quest requirement is gone (Uxie, Mesprit and the mythical long-road quests lose it); `HABIT_DAYS` counts the days a repeating task was finished plus old ticked-target days. Saved reviews stay unread in `plan_review` / `AppData.reviews`; no schema change. `RewardRules.VERSION` 30 (`docs/economy.md`).
- **Frequency targets scrapped:** the Targets screen and their payout path are gone. A saved target becomes an ordinary recurring task on load (`GameStore.convertTargets`, plan-core `TargetConversion`; Daily to daily, weekdays to weekly on them, N a week to weekly on N spread days, once a month to monthly on its day, N a month to weekly on round(N*7/30) days; id, title, rank, category, completion days and payment day kept, nothing paid, idempotent). New repeating tasks pay as ordinary tasks. The `target` field and `plan_series` rows stay.
- **Stats moved to Me:** Me lists Plan progress (growth and trends, no review) and Care trends (gone from Care's Sections key), above Settings and Help. Mint Review and Heal Readings stay.
- **Quick actions:** every module's action box has a "Quick actions" title, and the module party row is shorter (40 dp sprites, less padding). Defaults applied: the owner chose A, A, B for the three questions: drop Plan's menu, Progress and Trends only to Me, saved targets become recurring tasks.

## 0.215.0

PLAN module redesign (owner, 18 November 2026; `docs/pokelife/MODULES/PLAN.md` 3, 3.5A, 6.1, 8.1, 13; REP-058). `RewardRules.VERSION` and `docs/economy.md` are unchanged (nothing pays differently). No SQL schema change: the new list and task fields are optional JSON fields in the `plan_list` and `plan_task` rows. Not run on a device.

- **Plan's page** follows the standard module layout: lead, overview menu of Tasks / Calendar / Focus, action items (Inbox quick add, next task, waiting keys), party. A More key in the header holds Progress, Goals, Targets and Settings. `PlanSection` is now TASKS, CALENDAR, FOCUS, MORE, PROGRESS, GOALS, TARGETS, SETTINGS; Inbox, Upcoming, Lists, All tasks and Outstanding are no longer sections.
- **One Tasks workspace** (`plan/Tasks.kt`): `AppModel.tasksView` is OPEN (default, grouped by list), TODAY, INBOX, UPCOMING or DONE; list chips and Manage lists sit inside it. `ListScreen`, `OutstandingView` and `AllTasksView` are removed; the filter box lost its List dropdown. Generic quick add on Plan's page creates Inbox tasks (`AddTo.INBOX`); the Today view adds to Today.
- **Focus:** setup offers any open task (Today's first, plus "Another task" search); a session summary (`FocusEnd` gained `minutes`, `plannedMinutes`, `taskId`, `deepFocus`); a Recent list of the last 5 sessions.
- **Task editor tabs** are Schedule (day, time, repeat, reminders), Details (title, category, notes, estimate, list, steps) and More (difficulty, goal, deadline, Won't do, Duplicate, Delete). A task with no day can carry a nudge (`Task.nudge`, "Remind me at"): it shows on Today from its time, is never overdue, and fires through `Reminders.upcoming`.
- **plan-core / app-core:** `TaskList` gained `kind` (`ListKind` TASKS, CHECKLIST, TRACKER), `template`, `reset` (`ChecklistReset` NEVER, DAILY, WEEKLY) and `resetOn`. `GameStore.addList(kind, startFrom)`, `saveTemplate` and `resetChecklists` (run from `refresh()`; reopens done items, paid stays so nothing pays twice).
- **Settings:** `PlanSettings` (Plan, More, Settings) holds Task reminders (with quiet hours), repeat until answered, focus halfway and end alerts and the device calendars picker. The week-start toggle moved to General settings. `CalendarSettings` is removed.
- **Not done:** no priority field (the owner dropped Rank versus priority); external calendar import is only the calendar picker (no copy, no .ics); recurrence presets unchanged; the 10 second leave rule (6.3) kept; unticked checklist items at a reset are not written to a history (the spec says logged; follow-up).

## 0.214.0

MINT BNPL redesign (owner, 17 November 2026; `MINT.md` 5.6C, REP-039): one monthly bill per pay-later or card account. `RewardRules.VERSION` and `docs/economy.md` are unchanged (nothing pays differently). No schema change: plans and transactions ride in the sealed `mint_state` JSON and old saves read unchanged. Not run on a device.

- **Monthly bills:** one bill per account and month, derived from all its purchases (`MonthlyBills`, `BillMonth`, `BillLine`); the first due day comes from the account's statement and due day (`MonthlyBills.firstDue`).
- **Payments** are allocated installment by installment in due order (`PlanPaymentAllocation.no`, a new optional field). `MintStore.payBill`, `openBills` and `bills` back "Pay" on a bill.
- **"Use the 5th for every bill"** (`alignInstallmentsToDueDay`, `misalignedInstallments`) moves installments to the account's due day.
- **One reminder per bill** (`MintReminders.billRef`); old `plan:` refs are dropped when they fire; `installmentRef` is gone.
- **Counts:** Overview, the Bills screen box (now "Credit bills due") and the due count count bills, not installments (`Installments.dueWithin` counts bills).
- **UI:** in the Credit line box, Monthly bills come before Purchases, "Add plan" is now "Add purchase", and the two caption lines are replaced by one "Bill reminders are off."
- **Fixed (review of 0.213.0):** editing a purchase plan's terms no longer resets its first due day to the purchase day (`termsFor` started from the plan's `startDay`, the purchase day, instead of its first installment).
- **Files:** mint-core `MonthlyBills.kt` (new), `Installments.kt`, `MintData.kt`, `MintRules.kt`, `OverviewCounts.kt`; app-core `MintStore.kt`, `MintReminders.kt`; composeApp `ui/mint/Plans.kt`, `Mint.kt`, `Overview.kt`; tests `MonthlyBillsTest`, `MintStoreTest`, `ScreenshotTest` (mint-paylater-bill).

## 0.213.0

MINT BNPL correctness and daily-use improvements (REP-039). Adds installment repayment allocation across plans, guards linked purchases and pay-later accounts against destructive or unusable state, and allows eligible plan and installment edits. Adds savings targets, transaction category splits, one optional PNG receipt per transaction, statement/minimum-payment records with a clearly labelled simple APR/365 interest estimate, and every-six-month recurrence. Clarifies reminders, bill settlement, plan deletion and CSV-import limits. No SQLDelight schema change or reward change. Not run on a device.

## 0.212.0

Forms follow eight principles (owner request, 16 November 2026; rule in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md` 6.4A): prioritize, group, label, consolidate, progressively disclose, standardize, respect mobile space, preserve PMD context. `RewardRules.VERSION` and `docs/economy.md` are unchanged (nothing pays differently). No schema change. Not run on a device.

- **New form kit** (`composeApp` `screens/Form.kt`): `FormBox`, `FormStack`, `FormGroup`, `FormLabel`, `FormDivider`, `PixelChoice`, `FormDisclosure`, `FormSave`; `PixelField` gains `large` and `prefix`. Every field label is now one small soft style (no bold section titles used as captions).
- **Mint Add transaction is the reference:** amount first and large, transaction type as a segmented Expense / Income / Transfer, labelled Account and Category dropdowns, When, then one "Additional details" row (other kinds of entry such as refund, fee, balance adjustment; payee; note; pay in installments) and one Save.
- **Applied to** the forms of Mint (accounts, transactions, budgets, bills, reconcile, pay-later plans, import), Plan (task card, repeat, events, goals, lists), Care (routines, diary, journal, calm break), Heal (activity, hydration, sleep, body, goals), Base (assets, supplies, chores, maintenance, warranty, notes), Link (people, dates, follow-ups, import), and the search and name fields of Pokémon, World and Gear.
- **Removed:** the "Now" key beside time fields; the "New category" key in Add transaction (categories are made in Mint Settings); the "Fewer options" / "Payee and a note" buttons (replaced by Additional details).
- **Pay-later installment terms:** the preset list and "Or any number" are one numeric field.

## 0.211.0

Dropdowns for long single-choice rows (owner request, 16 November 2026; rule in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md`). `RewardRules.VERSION` and `docs/economy.md` are unchanged (nothing pays differently). No schema change. Not run on a device.

- **New `PixelDropdown`** (`composeApp` `screens/Tabs.kt`): one key showing the choice and a chevron; it opens a stair-cornered scrolling list as wide as the key, the chosen row ticked. An All/None pill becomes the first entry.
- **Converted** (single-choice pill rows that could grow or run long): Plan (task list filter, move to list, goal link), World (job regions, Notebook layers, journal kinds), Pokémon (PC sort and Where, Pokédex region/National and generation), Gear (tool categories, converter categories, QR repair level, everyday sums), Care (emotion, invite Pokémon, play activity), Heal (activity type, goal kind), Base (areas, assets, chores area filter, supply unit), Link (people category filter and picker, month, "Same as" in contact import), Mint (account type, currencies, accounts, categories, plans, frequency, budgets, filters, overview, import).
- **Kept as pills:** 2 to 4 short options, multi-select toggles, date and time presets, view tabs.
- **Behaviour change:** the Base chores area filter and the Mint income category no longer clear by tapping the active one again; choose All areas / None. Mint's category chips lost their More/Fewer toggle: every category is in the list, with New beside it.

## 0.210.1

Fix (owner report, 16 November 2026): a recurring income (a salary, `Bill.income`) was counted as a bill to pay. `MintRules.available` took it off available money as a "bill due", which the longer Overview windows of 0.210.0 made much worse (a monthly salary inside "until the end of next month" cut available money by its amount); the Overview's upcoming list, the Mint widgets' due total, count and list, and Home's "bills to settle" count included it too. Income is now left out of all of them (it stays on the Bills and income screen as money expected, and is posted only when received, as before). `OverviewCountsTest` covers it. Bill reminders still include income bills the player has not switched off. No schema change, no reward change.

## 0.210.0

MINT widgets and overview counts (owner requests, 16 November 2026, REP-039; `MINT.md` 3.2B, 22G). `RewardRules.VERSION` and `docs/economy.md` are unchanged (nothing pays differently). No schema change and no migration: the new settings are JSON with defaults in the sealed records, so the old behaviour stays until changed. Not run on a device.

- **Widgets:** two Glance widgets in `widgets/GlanceWidgets.kt`. "Mint" shows available money, net position, and the due total and count; "Mint due" lists the next bills and installments. Both follow Hide amounts (dots; counts and dates stay) and the screen-lock gate (the widget says "Locked" and shows no figure), read the sealed Mint records read-only and refresh in `updateWidgets`.
- **Overview counts:** a new Mint Settings box "Overview counts" with two choices. Bills and installments due window: next 14 days (default), next 30 days, rest of this month, until end of next month. "Pay later in net position": whole balance (default), only what is due in the window, not counted; credit cards always count in full. So a whole pay later balance need not show in the Overview.
- **Code:** `mint-core` `OverviewCounts.kt` (`OverviewWindow`, `PayLaterCount`, `OverviewCounts`, `OverviewSummary`); `MintRules.netPosition(data, today)` and `breakdown` use it; `MintSettings.overviewWindow/overviewPayLater`; `MintStore.setOverviewWindow/setOverviewPayLater`; `OverviewCountsBox` in `ui/mint/Overview.kt`.

## 0.209.0

MINT installments from Activity and Bills, installment reminders, overview breakdown (owner requests, 16 November 2026, REP-039 neighbourhood; `MINT.md` 3.2A, 4.1, 5.6A, 22F). `RewardRules.VERSION` and `docs/economy.md` are unchanged (nothing pays differently). No schema change and no migration.

- **Entry points:** for credit cards and pay later accounts (`Installments.allowedFor`), the Activity add form has a "Pay in installments" toggle on an expense to a credit account (name required, shared `InstallmentTerms`, live schedule; Save calls `addPlanPurchase`); a transfer to a credit account shows "Pay for" chips (No plan, each unpaid plan) and Save calls `payPlan`; transfers into a liability are titled "Repayment". The Bills and income list has an "Installments due" box (due within 30 days or overdue; rows expand to Pay and Bill it via the shared `PayArea`); the menu detail counts them.
- **Reminders:** with bill reminders on, an unpaid installment asks for one reminder like a bill (`MintReminders.installmentRef` "plan:<planId>:<no>", `parseInstallmentRef`; `stillWanted` and `text` handle them; same generic words, name only if allowed, never an amount).
- **Overview:** quick hide (eye or eye-off button in the Overview box title row, toggling the hide-values setting); tapping the box opens the "Overview breakdown" dialog (`ui/mint/Overview.kt`, from `MintRules.breakdown` in `Breakdown.kt` returning `OverviewBreakdown`): net position by account (assets, liabilities, "Not in total" for hidden accounts), available money (liquid less bills due and installments due) and credit lines (limit, owed, left).
- **Fix:** the account type label is "Pay later" (was "Pay later (BNPL)"). Cause of the bold "BNPL": the pixel button font lacks the glyphs "·" and "(", so a chip label containing them fell back to a bold font; the new chips avoid them and the "?" definition keeps "(BNPL)" as plain body text.
- **Open:** other glyph fallbacks of the pixel font ("·", "(" and similar in chip labels elsewhere in the app may still draw bold), widgets for Mint. Not run on a device: all the new screens.
- **Docs:** `MINT.md` 22F (and 22D/22E "not built" lines); `code-map.md`; `IMPLEMENTATION_BACKLOG.md` REP-039.

## 0.208.0

MINT existing installments and paydays (owner requests, 15 November 2026, REP-039 neighbourhood; `MINT.md` 5.6A, 8.3B, 22E). `RewardRules.VERSION` and `docs/economy.md` are unchanged (nothing pays differently). No schema change and no migration: every new field is JSON with a default in the sealed rows; plans stay in `mint_state`.

- **Existing installments:** `MintStore.addExistingPlan` makes an `InstallmentPlan` with `purchaseTxId = null` for a schedule running before tracking: no purchase record, not spending; the amounts owed are added to the account's starting balance owed unless turned off; then Pay, Bill it, Late fee, Edit, Refund and Delete work as for any plan. `Installments.checkExisting`. The New installment purchase dialog has "New purchase" and "Already owed" chips, rows (due date, amount owed, optional fee), "Add month", "Next 3 months", an "Add to starting balance owed" toggle and a live total; the hint "Already owe installments? Add them" sits in the Credit line box of an account with no plans; such plans show "Existing schedule".
- **Paydays:** `Frequency.SEMI_MONTHLY` ("Twice a month", `Bill.days` default 15 and 30, a missing day is the month's last); `Bill.workday` moves a due day to the nearest working day on or before (not a weekend, not a counted holiday); `Occurrence.payDay` (really due) versus `dueDay` (scheduled, the settlement key, so changing a holiday never orphans a payment); reminders use `payDay`.
- **Estimated income:** `Bill.estimate` shows the amount as "≈", the real amount is always entered, any amount settles it as paid, Part is hidden; rows read "+≈ ₱X" with "Fri 13 Nov · for the 15th".
- **Holidays:** `PhHolidays` (fixed holidays, Holy Week from Easter, Heroes Day) and `Payday` in mint-core `Payday.kt`; `MintSettings.builtInHolidays`, `holidaysIgnored`, `daysOff` with `setBuiltInHolidays`, `setHolidayCounted`, `setDayOff`; "Paydays and holidays" box in Mint settings (`ui/mint/Paydays.kt`). The list is a starting point, not an official calendar.
- **Open:** reminders from installment due days, other countries' holiday calendars, a range for an estimate. Not run on a device: all the new screens.
- **Docs:** `MINT.md` 5.6A, 8.3B and 22E; `code-map.md`; `IMPLEMENTATION_BACKLOG.md` REP-039.

## 0.207.0

MINT pay later and installment plans (owner request, 15 November 2026, REP-039 neighbourhood; `MINT.md` 4.1, 5.6A, 22D). `RewardRules.VERSION` and `docs/economy.md` are unchanged (nothing pays differently). No schema change and no migration: plans are JSON in the sealed `mint_state` row. Not run on a device.

- **Pay later account:** `AccountType.PAY_LATER` (SPayLater and other BNPL lines) is a credit liability like a card; both share limit, statement day and due day (`Installments.allowedFor`), and available credit is the limit less what is owed.
- **Installment plan:** `InstallmentPlan` (mint-core `Installments.kt`, `MintData.plans`): schedule from price, count 1 to 36, every week, 2 weeks or month (month-end clamp from the first day); fee rule none, per installment, total, or a rate per period on the unpaid principal (basis points, half up), as fee or interest; principal split exact.
- **Records:** the purchase is one expense at full price; a repayment is a transfer from the payment source to the credit account (not spending); fee, interest and late fee are FEE records with `Transaction.feeKind` (`FeeKind`), recorded when billed or reached by a payment; a refund is a REFUND linked to the purchase that cancels unpaid installments from the last backwards; `Transaction.planId/planNo`. Paid state is derived (repayments applied in date order to the earliest installment, early and part payments), never stored, so voiding a repayment reopens it. The purchase record cannot be deleted, voided or edited while the plan exists; deleting a plan keeps its records, unlinked.
- **MintStore:** `addPlanPurchase`, `payPlan`, `billInstallmentFee`, `addLateFee`, `refundPlan`, `updateInstallment`, `deletePlan`, `planView(s)`, `creditActivity`; `deleteTransaction` now returns a Boolean.
- **Reports:** spending counts the purchase once plus fees, interest and late fees, never repayments; new `Installments.cashFlow` (liquid accounts: a repayment is cash out, a credit purchase is not cash flow); net position is unchanged by repayments; `MintRules.available` also subtracts installments due within the window (`Available.installmentsMinor/Count`). Export and erase: plans travel with the accounts kind, and erasing accounts or transactions clears them.
- **UI (`ui/mint/Plans.kt`):** pay later in the type picker and its "?" help; a Credit line box in the account options (limit, owed, available credit, the month's activity) with the plan list and "New installment purchase" (live schedule preview); plan options (schedule with states, Pay with a source account, Bill it, Late fee, Edit, Refund, Delete); Activity shows "Plan: name" and the fee kind; Overview "Installments due"; Review "Cash flow".
- **Open:** reminders from installment due days, interest accrued automatically (the player or the schedule gives each fee), multi-currency plans, per-installment grace periods, statement-balance and minimum-payment tracking, a plan editor beyond due day and fee. Not run on a device.

## 0.206.0

MINT accounts upgrade (owner requests, 15 November 2026, REP-039 neighbourhood). `RewardRules.VERSION` is unchanged (nothing pays differently). No schema change and no migration: every new field is JSON with a default in the sealed account row. Not run on a device.

- **Groups:** the Accounts list shows accounts under their `group` label as headings with a per-currency subtotal (assets less liabilities of active, counted accounts) and an "Other" heading for ungrouped ones when any group exists (`ui/mint/AccountList.kt`).
- **Arrange:** an Arrange/Done button; groups move as blocks and accounts within their group with up and down. `MintStore.arrangeAccounts(orderedIds)` reorders the ids in their existing slots (the saved order).
- **Hide from total:** `FinancialAccount.hideFromTotal` (switch in Edit): the account keeps its balance and records, is marked "not in total", and is left out of `MintRules.netPosition`, `available` and group subtotals.
- **Open the wallet's app:** `FinancialAccount.appPackage` (package name only), chosen from installed apps (name search) in Edit, Android only; "Open <app>" on the account options. New `AppLauncher`/`InstalledApp` in `Integrations.kt` (`appLauncher` param of `App()`) and `AndroidAppLauncher` (queryIntentActivities MAIN/LAUNCHER; the manifest already holds QUERY_ALL_PACKAGES, personal use).
- **Credit card (MINT.md 4.3D):** the opening field reads "Starting balance owed"; optional `limitMinor`, `statementDay`, `dueDay` (1 to 31, 0 none; short months clamp via `MintRules.nextOnDay`); the row shows "Credit card · ₱X left", the options show Owed, used of limit and left to spend (or over the limit by), statement and due days with days to go (`ui/mint/CardDays.kt`). `MintRules.checkLimit`, `checkCardDay`, `leftToSpend`. Information only: no reminder, bill or record is made from them and the limit is in no total.
- **Open:** custom group records and group renaming, reminders from card statement or due days, per-card minimum payment tracking. Not run on a device: the installed-app list, opening an app, the arrange feel (the Photo Picker and the rest are from 0.205.0).
- **Docs:** `MINT.md` 4.3, 4.3B to 4.3D, 22 and 22C; `code-map.md`; `IMPLEMENTATION_BACKLOG.md` REP-039.

## 0.205.0

MINT receive-money QR full screen and share (owner request, built on 0.204.0). `RewardRules.VERSION` is unchanged (nothing pays differently). No schema change and no migration. Not run on a device.

- **Full screen:** tapping the receive-money QR, or the new Open button, opens `QrFullScreen` (`ui/mint/Mint.kt`): edge to edge, the whole image at its own aspect ratio (scrolls if taller than the screen), no name line, Close and Share on a slim bar below; Back closes it. On Android the screen goes to full brightness and stays on while open (`ImageViewer.bright`, `AndroidImageViewer`: the window's own brightness override, no permission, undone on close). Share (`ImageViewer.share`) writes `receive-qr.png`, `.jpg` or `.webp` (following the bytes, mime from the extension) to the cache `exports/` folder and opens the share sheet through the existing FileProvider; the file name never carries the account's name. Both are off while "hide values" is on. New interface `ImageViewer` in `ui/Integrations.kt`, param `imageViewer` of `App()` and `AppModel`.
- **Whole screenshot kept, as picked:** the image is stored as picked (PNG, JPEG or WebP, up to about 1 MB): `MintRules.QR_MAX_CHARS` 1,400,000 base64 characters, `checkReceiveQr` accepts the PNG/JPEG/WebP signatures (`IMAGE_BASE64_PREFIXES`). `AndroidImagePicker` hands back the original bytes when they are at most 1,000,000 and start like one of those (`isKeepableImage`); otherwise it re-encodes as PNG at 2400, 1800, 1440, 1080, then 720 px until at most 1 MB, else "That picture is too big to keep...". (Was 600 px / 64 KB, then 1080 px / 192 KB; too small to scan a QR inside a screenshot.) The Receive money box and the full screen draw the whole image at its own ratio, no white tile, no height cap.
- **Receive money box and picture buttons:** no per-account switch and no `receiveHidden` field. The "Receive money" box on an account's options is drawn only when the account has a stored picture (Open and Share; a Hidden tile when "hide values" is on); with no picture nothing is drawn. Add, Replace and Remove live in the account's Edit form, in a "Receive money" section shown only on Android: "Add QR code" with no picture; with one, a preview at its own ratio (about 40% width, no white tile) with Replace and Remove (Remove asks to confirm); each saves at once. The add-account form has no QR section.
- **Card colour picker:** `FinancialAccount.card` is a `CardStyle` id or a custom `#RRGGBB` (`MintRules.cardId`, `CardStyle.customRgb`); `ui/mint/CardColor.kt` `CardColorPicker` has six style swatches plus Custom with Hue, Saturation, Brightness `PixelSlider`s and a hex field (`accountColor`). Cosmetic only.
- **Fix:** the account actions row (Edit, Archive, Close, Delete) clipped "Archive" on a phone; now two rows of two.
- **Open:** not run on a device: brightness, the share sheet, the Photo Picker keeping a real screenshot, the full-screen dialog's edge-to-edge look, the custom colour picker's feel. A crop step in the picker is a possible later improvement. Open an app from the account (for example GCash) is not built; asked by the owner, needs package names and Android package visibility.
- **Docs:** `MINT.md` 4.2, 13.4 and 22B; `code-map.md`; `IMPLEMENTATION_BACKLOG.md` REP-039.

## 0.204.1

Navigation refinement of REP-067 (rule in `FEATURES/DESIGN_SYSTEM.md` 6.7, "Moving between modules and sections"). `RewardRules.VERSION` is unchanged (nothing pays differently). No schema change and no migration. Not run on a device.

- **One-row header:** a module page header is now one row (compact Back, the title, one small "Sections" key via `SectionsKey` in `screens/ModuleNav.kt`), so the module's content starts right under it.
- **Modules key dropped:** the second header row, `ModuleNavKeys`, the module switcher sheet and `JumpSearch`'s boxed option are gone. Tapping the bottom Modules tab again while a module is open returns to the module list (`backToModuleList` in `App.kt`).
- **Modules list:** compact rows (`MenuList`) instead of large square cards, with the Jump to field above and the Gear key in the header.

## 0.204.0

MINT receive-money QR and recurring income (REP-039, first slice, and an owner request). `RewardRules.VERSION` is unchanged (nothing pays differently). No schema change and no migration. None of it was run on a device.

- **Receive QR:** an account can keep one image of its receive-money QR code (`FinancialAccount.receiveQr`, base64 PNG, at most `MintRules.QR_MAX_CHARS` = 262,144 characters, about 192 KB (superseded in 0.205.0), checked by `MintRules.checkReceiveQr`; set with `MintStore.setReceiveQr`). It lives inside the account's existing sealed `mint_account` JSON row. It is picked with Android's Photo Picker (`ImageChooser` in `Integrations.kt`, `images` parameter of `App()`; `AndroidImagePicker`; superseded in 0.205.0: stored as picked up to about 1 MB, `QR_MAX_CHARS` now 1,400,000). The account options show a "Receive money" box (Replace, Remove; hidden when "hide values" is on; "Add QR code" on Android only). Never uploaded or read.
- **Recurring income:** `Bill.income` (JSON default false). "Received, record it" writes an income transaction through `settleAndRecord`; nothing is ever posted automatically. The Bills screen is now "Bills and income" with a "Bill to pay / Income expected" choice and an income-category picker; the direction is fixed when the entry is created.
- **Docs:** `MINT.md` 4.2, 8.3A, 13.4, new 22B; `IMPLEMENTATION_BACKLOG.md` REP-039 and priority row 16; `code-map.md`. Still open in REP-039: savings targets, rollover, splits, refunds, charts, other attachments, widget, custom recurrence, spotting subscriptions.

## 0.203.0

Navigation: one less level inside the Modules tab (REP-067; rule in `FEATURES/DESIGN_SYSTEM.md` 6.7, "Moving between modules and sections"). `RewardRules.VERSION` is unchanged (nothing pays differently). No schema change and no migration.

- **Owner's decisions (15 November 2026):** Home's module cards open the daily view in one tap (already so, no change); a "Sections" key in each module's header opens the section list; a "Modules" key opens a switcher sheet; Gear is a labelled key in the Modules header, not a card.
- **Sheets:** `screens/ModuleNav.kt` holds `ModuleNavKeys`, `NavSheet` (bottom sheet in a Dialog: one opaque dialogue box, scrim, Close key), `JumpSearch` and `LocalFocusTab`. Plan, Care, Heal, Mint, Base, Link and World no longer draw their inline card grid or row menu; the list moved into the Sections sheet. World's Settings button is a sheet row.
- **Jump to:** a field on top of the Modules tab searches the fixed names of every module and section (`jumpTargets`, `jumpMatches`) and opens the match.
- **Plan cut to five rows:** Inbox, Upcoming, Lists, Focus, More. `PlanSection.OUTSTANDING` joined `MORE_HOLDS` (Back from it goes to More); Calendar left the menu and still opens from Upcoming's Calendar key.
- Code: `MenuRows` in `screens/Menu.kt` (the rows of `MenuList` without its box).
- Tests: `NavigationTest` (OUTSTANDING.parent); screenshots `sectionsSheet` and `modulesJump`.

## 0.202.1

Fix: the 16 call sites of Compose's deprecated `androidx.compose.ui.backhandler.BackHandler` now use one app wrapper (REP-048 leftover). No behaviour change, and nothing pays, so `RewardRules.VERSION` is unchanged.

- **One `BackHandler`:** `app.pokeplan.ui.BackHandler(enabled, onBack)` (`composeApp/src/commonMain/kotlin/app/pokeplan/ui/BackHandler.kt`) is built on `NavigationBackHandler` from the new explicit dependency `org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.0`. Base, Care, Heal, Link, Mint, World (3), Pokédex, the launcher (2), Cutscenes, Modules (2), Menu and ModuleGate call it; no `BackHandler` warning is left in the compile log.

## 0.202.0

WORLD team choice, PLAN Outstanding and monthly repeat, BASE first use, and a Huawei listener battery change (REP-054, REP-055, REP-056 BASE part, REP-057). `RewardRules.VERSION` is unchanged (nothing pays differently). No schema change and no migration. None of it was run on a device.

- **WORLD (REP-054):** the Teams editor has dungeon chips, "Recommend team" and "Replace team" keys, a poor-match alert on a saved team for the chosen dungeon, a search over the whole PC, the chosen slots on top, favoured types first and type plates. Ranking: favoured type, then contribution, level, id, with one reason line per pick and "strongest instead" when nobody matches (`TeamAdvice` in world-core; poor match is under 33% matching). `worldRecommend` and `worldReplaceTeam` in app-core (the replace goes through `worldAssign`, so the one-place rule applies, and it refuses while the team is out). No stored data and no formula change (`WORLD.md` 3.2); "Send my strongest" is unchanged.
- **Battery (REP-055, code side):** `HuaweiNotificationService` unbinds after reading the active notification and rebinds when the app comes to the front (`HuaweiComponents.sync`); both Huawei services (listener, accessibility) start `android:enabled="false"` and are enabled only when switched on in Android's settings or when setup starts. Cost: step notifications posted while the app is closed are read at the next rebind only. The rest of the scan found nothing else wasteful (launcher loops stop with the screen, the tick clock does not save, widgets 30 min, backup daily). To confirm on the phone: the Background figure after a day.
- **BASE (REP-056):** maintenance saves with a name only (first due is today plus one interval, one-time is today plus 7 days, so it is not overdue on the day); three tappable example chips under the chore, supply, maintenance, asset and note add fields while their list is empty; each Area row has a pencil (rename, Archive/Restore, Move up/down) and a bin (the existing confirm). Known leftover: the Chores tab label "Upcoming" is clipped as "Upcom..." at 320 dp.
- **PLAN (REP-057):** an Outstanding section (`PlanSection.OUTSTANDING`, last in the enum): all open tasks across lists, Inbox first then lists in order, with the shared filter panel (`TaskViews.outstanding` in plan-core); its menu card reuses the Lists icon. A monthly repeat day picker in the task editor (every 1, 2, 3 or 6 months, day 1 to 31 or Last day; day 31 clamps in short months, tested).
- **Fix:** a repeating task's next instance (and so its reminder) was only created when the app was opened (`GameStore.refresh`), so a monthly bill reminder could be missed. Android reminder planning now refreshes first (`SharedReminders.live()`), with a Robolectric test.
- **Removed:** the debug "DB check" launcher icon and `DeviceSelfCheck` (owner's request, 15 November 2026).
- **Not built:** tags and saved recurrence presets (need a migration and a spec); quick-add (needs a look on the phone). **Found, not fixed:** `RepeatMode.AFTER_COMPLETION` has no caller outside tests.
- **Defaults applied:** D10 is moot (no place was ever required, no "Condo" area is created, `STARTING_AREAS` unchanged); tags and presets deferred; listener unbinding chosen.

## 0.201.0

LINK can import the phone's contacts, and a reminder is chosen when a date is made (REP-043, first slice; spec `MODULES/LINK.md` 3.5, 6.1). Nothing pays and nothing reaches `LinkEvent`, so `RewardRules.VERSION` is unchanged.

- **Import contacts** (Android only): People → "Import contacts" explains what is read (names, phones, emails, companies, birthdays) and only then asks for the new `READ_CONTACTS` permission (nothing else). If it is denied, Link works as before. The list has search, shows 40 at a time and preselects nothing; rows are New, Already in Link (matched by the contact's lookup key) or Maybe already in Link (same name, phone or email; unselected, and if picked it fills the matched person's empty fields only). Then a reminder (No, On the day, 1, 3 or a week before; no default), "Import N", one result line and Undo. Contacts without a name are counted and shown. A birthday becomes a Birthday date unless the person has one; a second import changes nothing. Nothing is overwritten.
- **Reminder in one step:** the Add-a-date form has "Remind me" keys, and the first reminder turns Link's reminders switch on (like Base).
- Code: `ContactImport` and `Person.sourceId` (link-core, no schema step), `LinkStore.planImport`/`importContacts`/`addDate(..., remindDays)`, `ContactSource` (`AppModel.contacts`, null on desktop, web and launcher) with `AndroidContacts`, `ui/link/ContactImportView.kt`.
- Tests: `ContactImportTest`, new `LinkStoreTest` cases, `LinkRemindersTest` updated; screenshot `linkContactImport`.
- **Not built:** a chooser for changed fields on re-import, upcoming dates on the launcher (REP-022), sealing Link's rows, D8. Not run on a device; the contacts query has no Robolectric test. **Known gap:** the notification permission is asked only when a reminder falls within the 4-day window, so a birthday months away does not ask yet.

## 0.200.0

MINT first use and a cosmetic card style (REP-056, MINT part). `RewardRules.VERSION` is unchanged (nothing pays differently). No schema change and no migration.

- **"Where is this money?"**: the new-account form has that title, a "GCash" placeholder in the name field, and a "?" key that opens one dialogue defining the eight account types (written once, in code). Saving the first account returns to the Mint overview, where Expense, Income and Transfer are the next actions.
- **Card style**: `FinancialAccount.card` with `CardStyle` (Plain, Blue, Green, Gold, Violet, Slate). Ids are saved and colours are chosen in the UI, so a palette change never touches data; an unknown id reads and saves as plain. `MintStore.addAccount` and `updateAccount` keep it; the add and edit forms have a picker of coloured keys, and the account list shows a small colour square before the name (the soft ink colour for Plain, so names line up). It never changes a balance, rule or report. The field lives inside each account's existing sealed JSON row with an empty default, so old rows read as plain.
- Test: `MintStoreTest.aCardStyleIsCosmeticAndAnUnknownOneReadsAsPlain`. Not run on a device.

## 0.199.0

Tier 2 starts: the backup leaves the phone, and a damaged database no longer quietly brings back old files. `RewardRules.VERSION` is unchanged (nothing pays differently).

- **Automatic backup off the phone** (REP-034; spec `DATA_AND_MIGRATIONS.md` 10.7): Settings → Backups → "Back up off this phone". The player types a passphrase twice and picks a folder once (a folder a cloud app syncs, for example); a daily job then writes one verified backup file there, with Mint and Heal sealed under the passphrase because the Keystore keys do not travel. The newest 7 stay, only files with the app's own names are ever touched, a copy that fails or cannot be read back is removed and earlier ones are never altered. The card shows the folder and the last copy, says quietly when the last copy is over 3 days old, and offers Copy now, Restore copy (stages the newest in the folder; the usual preview, passphrase and undo apply), Change folder and Stop. The passphrase is kept on the phone only, sealed with its own Keystore key. Not run on a device.
- **A damaged database with no backup asks first** (REP-026 c): the old `save.json` and `care.json` are only as new as the cutover, so they are no longer imported silently. A choice screen shows before anything opens: "Use the older data" or "Start fresh". The damaged file is kept aside either way, widgets, reminders and the backup job wait for the answer, and the answer survives a restart.
- **Screenshots of the 0.198.0 UI** (Tier 1 follow-up): looked at in both palettes: Credits, Care Skip and Yesterday, Bills "Paid, record it", the hold-to-confirm key (and its Reduce motion version), Plan's Duplicate row, and this version's new screens. One clipped label found and fixed ("Restore the newest" became "Restore copy"). New `ScreenshotTest` cases keep them (`interact` taps a screen before it is drawn).
- Tests: `OffPhoneBackupTest`, `OffPhoneTest`, and four recovery tests in `LiveStoreTest`.
- **Known, not new:** `LiveStoreTest.everyFixtureIsCarriedOverIntoTheDatabaseUnchanged` failed intermittently (twice here, once on a clean `main` worktree, then passed in the gate run): the heavy fixture's `caught` list came back sorted, so an order-sensitive comparison looks flaky. Left for its own fix.

## 0.198.0

The quick wins from the re-ordered backlog (`docs/pokelife/IMPLEMENTATION_BACKLOG.md`, Priority board tier 1). `RewardRules.VERSION` 29.

- **Care Play no longer fills Care's Egg meter** (owner's call D1, 15 November 2026: it was the cheapest egg in the app). It still pays its friendship step and Rare Candy. Rules version 29 (`docs/economy.md`).
- **Care:** a routine can be skipped for the day (no reward, no guilt text, undoable; kept in the routine's own JSON, no schema step); the Today box and the Routines list say "2 of 5 today"; Routines has Today and Yesterday tabs (yesterday records the tick and pays nothing); Stop tracking an urge sits behind a confirm and out of the daily form; a resting visitor shows "ready about HH:MM"; "Leave the visit" is a labelled key.
- **Plan:** "Added to ..." says where a quick-added task went; the quick-add box shows an example and keeps its unsent text; a task can be duplicated (a fresh copy, nothing paid); Month cells end a label with an ellipsis and "+n" opens the day.
- **Base:** "Remind me" is always offered on a chore and the first one turns Base reminders on; chore history is trimmed at 400 days; the daily cap is counted once.
- **Gear:** the helper reacts to real actions, not every keystroke; the dice history switch is saved.
- **Mint:** "Paid, record it" on a bill creates its transaction (`billId` is now set); the empty-state line shows ₱.
- **Safety:** the Guardian, the World legends and a recruited find are each granted in one write that re-checks its flag, and the Guardian is also granted when a settle climbs no floor; Transfer and Erase this device are hold-to-confirm (two taps with Reduce motion).
- **Credits:** Settings → About → Credits lists the art, sound and font sources and their licences.
- **Cleanup and docs:** the unused launcher `Calculator` is gone; the hosted widget size uses the Android 12 call; `IMPLEMENTATION_GUIDE.md` section 3.2 and `GEAR.md` section 15 are brought up to date; the backlog has a Priority board.

## 0.197.0

Plan's goal badges and Hall of Fame (REP-053 chunk 5; spec `MODULES/PLAN.md` 9.1, owner took the four defaults on 15 November 2026). Display only: nothing new is paid, so `RewardRules.VERSION` is unchanged.

- **Badge case:** every goal card shows one Gym badge per milestone, in colour once the milestone is done and a flat shape until then (unticking empties it). A goal is given one set of eight (Kanto, Johto, Hoenn, Sinnoh or Unova) by its id, and the pictures repeat after eight milestones.
- **Hall of Fame:** a finished goal moves under a "Hall of Fame" box at the foot of Goals, newest first, with its case, the day it was finished and the Plan party of that day (lead first), kept on the goal (`Goal.hallOfFameParty`, no schema step).
- Gym badge pictures 1 to 40 are bundled (`assets/badges`). Tests: `GoalTest`, `GoalHallOfFameTest`; screenshot `planGoalsBadges`.

## 0.196.0

Base's Secret Base room (REP-053 chunk 3; spec `MODULES/BASE.md` 9.5, owner's answers 15 November 2026).

- **Room:** a new card in Base's menu. Base's party stands in a Hoenn Secret Base room (Pokémon Emerald art) among the decorations you place. The lead's primary type picks the look: tree, shrub, or a red, brown, blue or yellow cave. It is only a view of the party: nothing is assigned to it, nothing wilts or asks for a visit. It is drawn at 5x on a phone, 4x when narrower, flush in its frame.
- **Arrange:** a dot on every empty square of the 44, and a box of the decorations you own and have not placed. Tap one, then a square; tap a placed one to move it or put it away. Free, any number of times, and each step has an undo.
- **Decorations:** 42 cosmetic objects (dolls, mats, desks, plants, tents, bricks, ornaments and a few others). Habitat Discovery's payments fill a **Decoration meter** (a chore 1 point, maintenance 2, under the same daily caps and switch); at 5 points you find one you do not own yet (one of each, so all 42 take about 10 weeks of regular chores). A new save, and a save from before the room, is given three: a Snorlax Doll, a Thunder Mat and a Big Plant.
- **Rules version 28** (`docs/economy.md`): the new reward; nothing a rule paid before changed. Schema 12 adds `home_room` (the placements). The art is composed by `tools/sprites/compose_secret_base.py` into `assets/hoenn/room/` and bundled (208 KB).

## 0.195.1

- **Repo tidy and the last HOME names:** `Module.BASE`, `Place.BASE`, `Region.BASE` and `ReminderModule.BASE` replace `HOME` and `HOUSEHOLD` in code. Saves are unchanged: `Region.BASE` is still saved as `"HOUSEHOLD"`, a screen lock saved as `"HOME"` is kept as `"BASE"`, and a reminder stored as `"HOME"` is read as Base. `docs/pokelife/MODULES/HOME.md` is now `BASE.md`, `tools/species/regions/household.txt` is `base.txt`, and the README lists every module and folder. The four tracked `__pycache__` files are removed and ignored.
- **Modular layout (no behaviour change):** the UI screens are packaged by module (`ui.plan`, `ui.care`, `ui.heal`, `ui.mint`, `ui.base`, `ui.link`, `ui.world`, `ui.gear`, `ui.pokemon`; shared ones stay in `ui.screens`), `app-core` by module (`app.base`, `app.care`, `app.heal` with the Huawei parsing, `app.link`, `app.mint`, `app.world`, `app.gear`, `app.plan`, `app.reminders`, `app.storage`), and the Android platform files into `platform/` and `health/`. World's and Gear's actions moved out of `GameStore.kt` (2,340 to 1,937 lines) into `WorldActions.kt` and `GearActions.kt` as extension functions. The three `Widgets.kt` files are now `ThemeWidgets.kt`, `LauncherWidgets.kt` and `GlanceWidgets.kt`. No saved name changed: serialized classes did not move, and the manifest classes and `BackupWorker` stayed where they were.

## 0.195.0

The launcher's Gust Trap and its crowd, from phone testing (owner, 15 November 2026).

- **Gust Trap:** it now blows the Pokémon away as the games' blown-away effect does: in a random direction, in a straight line, until it meets a wall or another Pokémon (at most ten squares), landing on the last square it can rest on. (It used to push it back along the way it came by six squares.)
- **Pokémon are walls to each other:** a walking Pokémon goes round the others instead of through them, one square at a time, looking at who is in the way before each step. Where there is no way round (a corridor with someone in it) it swaps places with them, as the games' team does: the other walks to the square it left. A sleeping Pokémon, or one stopped for a meeting, is never swapped with; it waits a moment and finds another way.

## 0.194.0

The art for the modules' places is fetched, and a test room shows how it looks with the Mystery Dungeon sprites (REP-053 chunks 1 and 2, owner, 14 November 2026). No app change: nothing here is bundled or shown yet.

- **Hoenn art (`assets/hoenn/`, 1.3 MB):** `tools/sprites/fetch_pret.py` (stdlib, pinned commit, skips existing files, `manifest.tsv` receipt) fetches from pret/pokeemerald the 43 Secret Base decoration sprites, the Secret Base tilesets (primary and the cave, shrub and tree ones, with their palettes and metatiles), the 24 room layouts and the 32 Berry tree sheets.
- **PokeAPI extras:** `fetch_items.py extras` adds 74 Berry and 31 Mail icons to `assets/items` (not bundled until a screen uses them) and the 77 badges to `assets/badges`.
- **Scale test:** `ScreenshotTest.secretBaseRoom` draws a Secret Base room (floor and walls from the tileset and the game's layout, six decorations, six SpriteCollab Pokémon) in a dialogue box at 2x to 5x, flush with the frame, in both palettes. It is a test render only. The owner chose 5x (4x below about 1000 px), flush with the frame, and accepted the Gen 3 room with Mystery Dungeon Pokémon; chunk 3 builds the room.

## 0.193.0

Launcher spacing now counts how big each Pokémon is (owner, 14 November 2026).

- A Pokémon's room on the dungeon floor follows the size of its sprite: a Wailord or a Moltres is given about three squares across, an ordinary Pokémon two, and a big one hides what stands right behind it (towards the top of the screen), not what stands before it. Where they start, where they choose to stand and where two are heading all use it.

## 0.192.0

More of Explorers of Sky in World: lost Pokémon's calls, recruits' words and dungeon entrances (owner, 14 November 2026).

- **The job board:** a rescue notice shows the lost Pokémon's Mystery Dungeon portrait (tearful) beside the game's own call for help ("I can't get out!", "I've lost my way!", "Help me!"...), in place of the app's description. A job keeps the same call every time the board is opened.
- **Recruits speak:** a Pokémon that joins your team says one of the game's lines ("Let's do our best!", "I've got a good feeling that we're going to find treasure!"); a species always says the same one.
- **Entrances:** the Atlas card of Tiny Woods, Thunderwave Cave, Mt. Steel, the Glacier Palace, the Sky Tower, Frosty Forest and Beach Cave opens with the game's picture of the dungeon's entrance.
- The Town's shop pages are checked in the dark palette (Spinda's Café, the Kecleon Shop, the Swap Shop).
- **Kecleon's shelf icons:** Protector, Magmarizer, Electirizer, Reaper Cloth, Dubious Disc, Up-Grade, Metal Coat, King's Rock, Dragon Scale, Deep Sea Tooth and Scale, Prism Scale, Sachet and Whipped Dream had no picture; they have now.
- **Launcher, from phone testing:** Pokémon no longer act out the same script at the same moments (each one's timing is its own, and a crowd has one meeting at a time instead of every pair at once); they keep a square apart when they start, when they choose where to stand and while two head for the same spot, so they overlap far less; and none is placed where it could never walk out (a swimmer's lake in the rock, a ghost's hollow in a wall).
- **Traps with effects:** a Spin Trap leaves a Pokémon confused for about 25 seconds (a question mark over its head; it stumbles a square to the side now and then), and the new Poison Trap poisons it for about 30 seconds (a skull over its head; it walks slower). Each wears off by itself with a twinkle. Nothing is lost.

## 0.191.0

World takes more of Explorers of Sky: its places, its shopkeepers' words and its jingles (owner, 14 November 2026).

- **Banners from the game:** Spinda's Café, the Kecleon Shop, the Croagunk Swap Shop and the mission board now open on Explorers of Sky maps (the café's room, Treasure Town south and north, the guild hall) instead of the older crops.
- **Shopkeepers speak:** Kecleon, Spinda and Croagunk show their Mystery Dungeon portraits beside the game's own lines (welcome, "Thank you! All yours!", "I've got a good feeling about this drink!", and the café's and counter's refusals). The four vitamins on Kecleon's shelf show the game's description.
- **Journal:** the Records page opens with an Adventure Log in the game's wording (Pokémon that joined you, dungeons cleared).
- **Jingles:** a recalled team plays the game's sentry-duty jingle, a collected research step the Team Charm jingle.
- **Places:** twelve Explorers of Sky places (Treasure Town, the café, the guild's summit, sea cliffs, a meadow, the night sky, a sunrise) are page backgrounds in Settings → Look; the evening scenes of "The Marine Resort" are set on the cliff at dusk. `docs/ASSETS/SKY_SOUND_LISTENING_SHEET.md` lists the unlabelled game sounds to listen to.

## 0.190.0

Traps play the game's own effects, and Explorers of Sky is ripped for reference (owner, 14 November 2026).

- **Trap effects:** when a Spin, Gust or Slow trap springs on the dungeon floor, the game's own effect plays at the Pokémon's feet (the swirl, the wind, the falling light), picked through the game's `TRAP_ANIMATION_INFO` table (`tools/sprites/extract_sky_effects.py`).
- **The rip** (`rip/sky/`, not in the app; `rip/sky/README.md`): every string, every script decompiled and as dialogue (with the main story in order in `dialogue/story.txt`), the Pokémon, move, item and floor tables, the portraits, backgrounds, textures and all 1,020 effect animations (`tools/rip/rip_sky.py`).

## 0.189.0

The town's Pokémon pick up the day's finds (owner, 14 November 2026; `FEATURES/LAUNCHER.md` 4.5).

- A Pokémon that walks onto a find on the dungeon floor picks it up into the Bag, as a team does in the games: a hop, a twinkle, the find rising with its name, and the pick-up sound when it is on Home. Now and then one heads for the nearest find it has spotted. Tapping a find still works; it is the same three finds a day, so nothing pays more.

## 0.188.1

Fixes to the launcher's dungeon floor from phone testing (owner, 14 November 2026; `FEATURES/LAUNCHER.md` 4.5).

- **Nobody walks in the walls:** each Pokémon's walk starts again on a new floor (it kept walking the floor that was there before, through the new one's walls), and a meeting only steps onto a square it can stand on.
- **Only Ghost types pass through walls**, as in the games; Water types swim and Flying types fly over water.
- **Every trap springs** when a Pokémon steps on it (a found one sprang only one time in four), with the game's sound on Home; they step round the traps they know about and never stop on one.
- **Less lag coming back to the home screen:** paths, the tile pictures and their weather colours are worked out off the main thread, and the floor's tiles are worked out once per floor and drawn in a layer of their own.

## 0.188.0

The launcher town is a Mystery Dungeon floor (owner, 14 November 2026; REP-052, `FEATURES/LAUNCHER.md` 4.5). `RewardRules.VERSION` 27.

- **Rooms and corridors** from the dungeon's own tiles across every page, walls everywhere else, ponds in some rooms and lakes in the walls; a new floor each day, and Launcher settings → Town → New floor layout for another one. Rooms go where the widgets are not.
- **Real shapes:** each Explorers of Sky tileset's full autotile set (the 47 ways a square can meet its neighbours, the game's three variants) cut from the ROM by `tools/tiles/extract_sky_tiles.py`; the named towns that are Explorers dungeons use it too. Red Rescue Team tilesets keep rectangles until that ROM's sets are cut.
- **Walking on the floor:** the Pokémon follow paths over the floor; Water types wade into water, Flying types cross walls; widgets are soft walls (nobody stops under one). A Guardian comes up the stairs and goes down them.
- **Traps, for show:** hidden Warp, Spin, Slumber, Gust, Slow and Trip traps that a Pokémon finds by stepping on them, and sometimes a Wonder Tile; the game's own tiles (`tools/tiles/extract_sky_floor.py`).
- **Three finds a day** on the floor (`TownFinds`: berries, vitamins or a little Poké), drawn with the game's floor pictures; a tap picks one up into the Bag, once a day.

## 0.187.0

A kinder economy and a Poké Mart of its own (owner, 14 November 2026: "economy seems stingy", "anything for individual Pokémon growth should be generous", "held items?"). `RewardRules.VERSION` 26.

- **Poké Mart:** its own row in the Pokémon tab (it was a box at the top of the Bag), separate from World's Kecleon Shop. Exp. Candy 5 Poké as before, plus Super Candy 100 and each berry flavour 20; Buy 1, 10 or as many as you can afford.
- **More Poké from real life:** a focus session pays 1 Poké a minute (was 1 per 5), a task 10 per rank tier (was 2), and each paid Care, Heal, Mint, Base and Link moment 10 (Care's weekly rhythm 30; Care Play, steps and imports none). About twice the Poké over 60 days in `docs/economy-report.md`.
- **Kecleon Shop at about a fifth of its prices:** stones 400, Link Cord 500, Everstone 300, vitamins 100, perk held items 500 (Shiny Charm 1,500), Nature Mint 400, Ability Capsule 600, Bottle Cap 800, Tera Shards 1,000, Ability Patch 2,000. It no longer sells Exp. Candy (2,500 and rising); the Mart does. World's facilities, Café and Dojo keep their prices.

## 0.186.0

Heal keeps steps and the readings Huawei Health shows (owner, 14 November 2026, `MODULES/HEAL.md` 22B). Own PR: **schema 11** (`10.sqm`, `databases/11.db`; `heal_steps` and `heal_vital`, sealed like every Heal row). `RewardRules.VERSION` 25.

- **New records, history only:** daily steps, heart rate, resting heart rate, SpO2, skin temperature, and Huawei's own stress number and sleep score. Read from the Health page, the Sleep page and the step notification (parsers written from the owner's probe capture); in Heal → Readings, export and erase. Never judged: no target, no good or bad.
- **No duplicates:** one record per day (and bpm); the page and the notification, a repeated read, or the Sleep page and the insights line are one record; a larger step count or a new value for the day updates it. The preview says new, updated and already here.
- **Rewards:** steps pay per 2,000 (at most 5 a day: 1 Rare Candy and a friendship step each); reading records in pays 1 Rare Candy per family of record that brought something new, once a day each, at most 3 a day, for Health Connect too. No reading pays for its value. Simulated in `docs/economy-report.md`.
- **Reader:** also taps the Week tab of a page that has one, to capture it for the next probe (not parsed yet).
- `HealthKind.unsupported` and HEAL.md's "Not read" list updated.

## 0.185.0

Huawei Health records, nightly to weekly (REP-046, owner 14 November 2026, `INTEGRATIONS_AND_PRIVACY.md` 5.2.2). `RewardRules.VERSION` 24. Nothing stored on the phone needs a migration (new optional settings).

- **Saved into Heal after a preview:** workouts from Exercise records (type, start, duration, distance), weight and body fat from the Weight page (at their own time), and today's sleep (its length, ending at the get-up time on the Health page; other nights show no times, so they are left out). Reading the same pages again adds nothing twice.
- **Huawei workouts pay like ones you log** (rules version 24): same length rule and daily share on the session's own day, up to 8 days back so a weekly read still counts. A workout you also logged by hand is flagged as a repeat and is not paid twice. Health Connect imports still pay nothing.
- **Remind me to read:** Off, Nightly or Weekly at a time you pick, counted from the last read. A daily read keeps every night's sleep; weekly keeps every workout and the latest weight.
- **Save, numbers hidden** writes the capture to a `.txt` file instead of the clipboard.
- The walk scrolls each card page a little further (6 steps), for a week of Exercise records.

## 0.184.0

Huawei Health reader, second step (REP-046, `INTEGRATIONS_AND_PRIVACY.md` 5.2.2), from the owner's first capture. Nothing is saved or paid yet.

- **Banking apps open again.** MariBank and GCash refuse to run while a sideloaded accessibility service is on. Screen reading now turns itself off as soon as a read ends, however it ends, and Connections has a Turn off button. Optional one-tap reads: after `adb shell pm grant app.pokeplan android.permission.WRITE_SECURE_SETTINGS` from a computer, Read Huawei Health switches screen reading on for that one read and off after.
- **The walk opens the card pages.** After scrolling the Health page, Read Huawei Health opens the Sleep, Weight and Exercise records cards one at a time (found by their title), scrolls each a little and goes back. It stops if Back does not return to the Health page. These three cards are the only things it taps.
- **Only what is on screen is captured.** Huawei Health keeps its hidden Devices and Me tabs in the window, which made the first capture too long to paste.
- **The Health page is read:** steps, last night's sleep and get-up time, weight, heart rate and the day's exercise distance show on Connections. They are not saved, because the page gives no times; the card pages should have them.

## 0.183.0

Reading Huawei Health on the phone, probe stage (REP-046, owner decision 14 November 2026, `INTEGRATIONS_AND_PRIVACY.md` 5.2.2). Nothing is saved or paid yet, so nothing stored on the phone changes.

- **Heal → Connections → Huawei Health.** Two readers you turn on in Android's settings: screen reading (an accessibility service declared for Huawei Health only) and notification reading (drops every other app's notifications before reading them). On Android 13 and later, a sideloaded app needs App info → ⋮ → Allow restricted settings first; the page links there.
- **Read Huawei Health** opens it, scrolls its first page to the end, capturing each step, and comes back (at most 12 scrolls or 30 seconds; it never taps or types). Pages you open in Huawei Health yourself are captured too.
- **What it saw** is shown on the page, with the step count from Huawei Health's notification, held in memory only. **Copy, numbers hidden** puts the capture on the clipboard with every digit replaced by `#`, to share as the sample the parsers will be written against.
- Decided for the next step: workouts, sleep and weight read this way go into Heal's existing records and pay like manual ones (with a `RewardRules.VERSION` bump then); English labels only.

## 0.182.0

Evolution methods, items and the shop (owner, 14 November 2026). `RewardRules.VERSION` 23: the life modules drop items. Nothing stored on the phone needs a migration (new optional fields).

- **Every special evolution has a way in.** Checked on the spot: a known move (Mime Jr., Tangela, Yanma, Lickitung, Aipom, Piloswine, Steenee, Poipole, Clobbopus, Girafarig, Dipplin), a held item at a time of day (Happiny, Sneasel, Gligar), the party (Mantyke, Pancham), Attack against Defense (Tyrogue), the nature (Toxel), a Fairy move and friendship (Sylveon), 400 Exp. Candies (Meltan), 999 Gimmighoul Coins (Gimmighoul), a Link Cord (Finizen).
- **Evolutions done with the phone:** Inkay turns into Malamar when you hold the phone upside down, Milcery into Alcremie when you spin with it (gyroscope), and Pawmo, Bramblin and Rellor after 1,000 real steps (the step counter keeps counting while the app is closed; a 5-floor World expedition also counts).
- **Evolution Trials in World**, in the evolution's own region: a team with the Pokémon reaches the trial floor (Galarian Sirfetch'd, Runerigus, Weezing and Mr. Mime in Galar; the Hisuian forms, Wyrdeer, Basculegion and Overqwil in Hisui; Alolan Marowak in Alola; Goodra in Kalos's rain; Annihilape and Kingambit in Paldea; Shedinja in Hoenn). The Pokémon's page says where.
- **Six new World regions:** Unova, Kalos, Alola, Galar, Paldea and Hisui, nine dungeons each with their own Pokémon, five story chapters, and their legendaries as quests and Guardians. They have no map picture yet.
- **Evolve asks first**, with Cancel, and **Stop** during the evolution cancels it with nothing used up. A Pokémon **holding an Everstone** does not evolve.
- **Items from every module:** each Care, Heal, Mint, Base and Link moment has a small chance of a themed item (Care sweets and soft stones, Heal vitamins and claws, Mint Nature Mints and coins, Base stones and the Everstone, Link the Link Cord and trade items), and a finished Mint review always gives a Nature Mint.
- **The Kecleon Shop** always sells every evolution stone, the Link Cord, the Everstone and Gimmighoul Coins, and has a shelf of five more items that changes each day.
- New item icons: Everstone, Oval Stone, Razor Claw, Razor Fang, Strawberry Sweet, Gimmighoul Coin (PokeAPI/sprites).

## 0.181.0

The launcher's town (LAUNCHER.md 6.2): calmer, more natural roaming, and a setting for it. Cosmetic only: no reward number changes, `RewardRules.VERSION` is unchanged, nothing stored needs a migration.

- **How lively** (launcher settings → Town, under "Pokémon walk about"): ten blocks from Sleepy to Restless, default Calm (4), which is quieter than before. It sets how often the Pokémon set off and how long they rest; Restless is about as busy as the old town.
- **Rest and restless spells:** each Pokémon alternates between resting (standing, looking about, grazing, shifting a step) and pottering about (two to four connected legs with short pauses), with a temper of its own, so the town has a rhythm instead of everyone rolling a fresh dice every beat.
- **Walks that look walked:** each leg carries on roughly the way it was going (a correlated random walk) and bends back towards the Pokémon's own corner the further it strays, leans along the back and front of the ground instead of bouncing off it, avoids stopping on top of another Pokémon, and only now and then crosses the town. A leg follows a gentle curve and eases in and out instead of a straight line at one speed, and the stroll is a touch slower.
- The volume and liveliness rows' value text now shares the label's centre line.
- **The clock widget's battery shows its charge:** empty, one, two or three bars (Pixelarticons' battery levels), red at 20% or lower, with a bolt beside it while charging, instead of an empty outline whatever the charge.
- **The agenda widget on a light day:** on the widget grid the list starts at the top instead of floating in the middle of its cells, it shows only whole rows (never one cut in half), the time column is as wide as the times instead of a third of the row, and the next day with anything on it (within a week) fills the room left, dimmed under its own heading ("Tomorrow" or the date).
## 0.180.0

A better PC. No reward number changes; `RewardRules.VERSION` is unchanged, and nothing stored on the phone needs a migration.

- **The PC shows the Pokémon resting in it**, as before, and a **Show all Pokémon** switch lists every one you own: the buddy, the leads and parties, the Guardians, the World teams and the Pokégear helpers. Each tile then names its place (Buddy, Plan lead, Care, Heal Guardian, World, Pokégear).
- **Where** joins the sort and filters while every Pokémon shows: PC, Buddy, Leads, Parties, Guardians, World teams or Pokégear. Clear keeps the switch and the sort.
- An empty PC with Pokémon elsewhere offers Show all Pokémon instead of a dead end.

## 0.178.0

Plan's dates (REP-030, PLAN.md sections 4.7 and 5.1). No reward number changes; `RewardRules.VERSION` is unchanged, and nothing stored on the phone needs a migration.

- **One set of day keys** wherever a task or a Plan event gets a day (Move to, Deadline, quick add, a new or edited event): Today, Tomorrow, **This weekend**, **Next week** (the coming Monday), **Pick a day** and No date. A key that names the same day as an earlier one is left out, so two never light up together. Quick add gets Pick a day too.
- **Repeat says more:** Yearly joins the keys, and Weekly takes every 1 to 4 weeks and the weekdays. A repeating task reads it in words ("repeats every 2 weeks on Mon, Thu"), and one the keys cannot show (every 3 days) is said in words instead of "Custom".
- **Reminders:** 30 min and 2 hours before join the keys, and **Other** takes a typed length before, up to a week.
- **A moved day of a repeating task says "this one only"** and where it came from; the others keep their days.
- **The Calendar's tray says how to place a task** ("Tap one, then an hour (or drag).") until the first task has a time.
- Removed the day-load estimate against an "awake" window: it was worked out but never shown.

## 0.177.0

Plan's task row and card (REP-030, PLAN.md section 10.1). No reward number changes; `RewardRules.VERSION` is unchanged, and nothing stored on the phone needs a migration (a list's category is a new optional field).

- **The check key comes first on every row.** A finished task's is ticked in a quiet colour and a won't-do task's is a cross; tapping either reopens it (the Reopen button no longer pushes the title aside). The rank is a small framed tag after the title.
- **The When tab is the day and the list**, then one **Time and repeat** row that says what is set ("Any time, no reminder, does not repeat") and unfolds the time, reminders and repeat.
- **A finished task's card has Reopen and Delete** side by side.
- **A list can carry a category** ("New tasks are" in Edit list): a task added to it, by its quick add or with `#List`, starts with it unless the words name one. The tasks already in the list keep theirs.
- **A task can count toward a goal** (Goal on the Edit tab: None or an unfinished goal; one goal at a time). A goal with no milestones shows "N of M tasks" on its card. Linking pays nothing.

## 0.176.1

Where a Pokémon is, shown where it matters (REP-009, the last open part). No reward number changes; `RewardRules.VERSION` is unchanged, and nothing stored on the phone changes.

- **The Gear helper list says where each Pokémon is now** ("In Mint's party", "Your buddy"), since choosing one takes it from there, and leaves out a team on an expedition (choosing one was refused without a word).
- **"Move to..." on a Pokémon reads its real place.** The PC row said "Here" for the buddy, a Guardian, a World team member or a Gear helper, and its button did nothing for them; it now sends a Guardian, a team member or a helper back to the PC (`GameStore.sendToPc`), and stays off for the buddy (choose another buddy instead). On an expedition under way, Make buddy and Move to... are off.
- A Gear helper's place names its tool ("Helping the Flashlight").

## 0.176.0

Plan's structure (REP-030, PLAN.md section 3): Modules → Plan is Today, with one menu. No reward number changes; `RewardRules.VERSION` is unchanged, and nothing stored on the phone changes.

- **Plan opens on Today.** Quick add, the day's progress, the task to do next (Focus on this, Pick for me; a daytime task before an overdue or evening one), From before with Move to today, Today, **today's unmet targets** as a group ticked with the same check key, This evening, and Done today folded. The Planner menu is gone.
- **One menu below:** Inbox, Upcoming, Lists, Calendar, Focus and More, as rows with counts. More holds Progress, Goals, Targets and All tasks.
- **Inbox** is an open task with no list and no date; **Upcoming** has no 7-day cap and a Calendar key in its header.
- **The weekly review is a card in Progress** when one is ready: each unfinished task has Move (tomorrow), Later (next week's first day) and Won't do (with Undo); past reviews and their notes are listed under Progress.
- **Done is a preset of All tasks** (the Done key beside Search), and shows won't-do tasks too (`TaskFilter.Status`).
- **Plan's settings page is retired:** the length and Deep Focus a session starts with become the usual ones; the week start and the phone's calendars sit at the foot of the Calendar.

## 0.175.1

Automated Android tests, first slice (REP-033; owner: automated tests over manual phone checks).

- **Fixed: a damaged database was wiped instead of recovered.** androidx's SQLite helper deletes a database it finds corrupt and opens an empty one, so the app's own recovery (the damaged file kept aside, the newest daily backup put back) never ran and the app would have opened empty. `AndroidDatabase` now leaves the file alone and reports the damage, and `LiveStore` recovers as designed.
- **`androidApp` has tests**, on the JVM with Robolectric (`./gradlew :androidApp:testDebugUnitTest`, run by CI's Android step): the database cutover on the five 0.123.0 save fixtures, a process death, a corrupt and a newer-schema database, Heal, Mint and the daily backups sealed (checked in the raw database bytes) and failing closed without the Keystore key, the shared reminders (exact alarms with no title in them, Done and 10 min on the notification, quiet hours, a reminder missed while the phone was off, the boot, alarm and time-zone receivers), the Today widget's tick against the app's store, and a focus session across a process death.

## 0.175.0

The HOME module is now **Base**, after Hoenn's Secret Bases (owner, 13 November 2026), and World's Base is now **Town**; a leaner test suite. No reward number changes; `RewardRules.VERSION` is unchanged, and nothing stored on the phone changes.

- **Base.** Replaces the "Nest" label 0.174.0 gave the module (the owner's final choice is Base). Modules → Base, its party, perks, region ("Base Region"), tutorial ("a 5 second tidy of your secret base"), reminders channel, the launcher's search and every place that named the module say Base; its scene is a secret base under the trees (`forest`). "Home" now means only the Home tab. In code: `base-core` (package `app.pokeplan.base`), `BaseStore`, `BaseData`, `BaseRules`, `BaseEvent`, `BaseTables`, `screens/Base.kt` and the rest. Stored names stay so no migration is needed: the `home_*` tables, save keys (`homeLeadId` and `homePaid` through `@SerialName`), ledger and reminder ids, and the constants stored by name (`Region.HOUSEHOLD`, `Module.HOME`, `Place.HOUSEHOLD`, `ReminderModule.HOME`). New reward-log entries start "Base: "; old "Home: " entries are still read.
- **Town.** World's headquarters destination (facilities, Skills, Finds) is called Town, so one screen never shows two Bases (`WorldSection.TOWN`, WORLD.md section 2).
- **Fewer, more important tests** (Bible section 3, REP-050): `LegacyJsonImporter`, used only by its 9 tests, is deleted with them (REP-025 d), and 11 tests of constants, copy, a test fixture or a removed feature are gone; a Gear test loses its retired-loan half.
- Docs: the owner's decisions of 13 November 2026 (lean tests, BASE, Town) in the Product Bible; REP-049 (Secret Base decorations) and REP-050 in the backlog.
## 0.174.0

The UI/UX review of every screen (REP-031) and its fixes, against Laws of UX, Laws of UI and the owner's bar: "as simple as Linear or Apple Notes; cut taglines, helper text and secondary labels; if a screen needs them, fix the layout." `RewardRules.VERSION` is 22 (one rule change, below).

- **Screens stop explaining themselves.** About 450 sentence-length strings were in the screens; the paragraphs under Settings switches, privacy and method notes, unavailable-feature notes, legends written as prose, research notes in Mint Import and Heal Connections, the perks and research captions on every module page, "Label:" prefixes and echo lines are gone. `CopyBudgetTest` fails on a new caption of eight or more words in `screens/`; a long string that is a result, warning or game line says so with `// copy-ok`. `EmptyState` is a title and one action (its line parameter is gone).
- **One input per value.** A date is one row of keys (the typed box opens with the calendar); times and lengths lost their nudge keys and their "9:30 am" echo; a typed value is rewritten in the app's spelling when the box loses focus.
- **One colour per module.** Each module screen sets its own tone (`ModuleTone`), and `Pixel.Tone` replaced the blue and green defaults inside modules, so keys, chips, frames and bars match the module wherever a screen is drawn. Heal's Goals and Care's Trends cards take their module's colour; the Pokémon tab is neutral.
- **Module pages lead with the work**: lead stage, today's items, the section cards, then the party, which is one row of six sprites. Lists sit in one box with lines between rows (Help, Bag, routines, history, accounts, bills, budgets, skills). Add forms open from one key. Checks come first on every checkable row.
- **Sections that hold records are rows with counts** (`MenuList`, owner's decision): the Planner's views, the Pokémon tab, World's Journal and Me.
- **A module's first Pokémon comes with its first real record** (owner's decision), not a 5-second demo card (`GameStore.firstRecordEgg`; `POKEMON_ECOSYSTEM.md` 10.2.1, `docs/economy.md`).
- **HOME shows as Nest** (owner's decision), so "Home" is only the tab; code ids and saves keep HOME.
- **The tab bar keeps the module's colour inside a module** (owner's decision; `DESIGN_SYSTEM.md` 5.1 now says so).
- **Fixed:** Care's mood keys cut to "Ro…" (also in Home's Quick log); Mint category names wrapping a letter at a time; Link's follow-up label drawn over its placeholder at large text; a Heal goal chip in the wrong font (the pixel font has no brackets); week titles cut to four letters on phones (three days at a time now); a log tag leaking into World's Finds; Back on the right of the Pokédex entry.
- Plan: one Start key, the Planner's filters behind one key. Mint: Available first, categories as rows. World: the "Since you last looked" report is a headline and the finds. Gear: the star sits in the helper bar; the Picker shows its keys on the tapped row.

## 0.173.1

Reminder and World clock fixes left by the 0.167.2 follow-up (REP-026 a, b, d). No reward number changes; `RewardRules.VERSION` is unchanged.

- **A reminder due while the phone was off now shows when it comes back** (REP-026 a). The scheduler remembers when it last planned; at the next plan (boot, opening the app, any alarm) the reminders the modules had planned for the time in between, and that did not go off, are held to go off at once, checked again by their module first (a task still open, a routine not yet ticked, a streak still for today). Only the latest of each source counts (a daily routine missed three days is one notice), only the last 24 hours are looked at, and one that went off already is never repeated (`ReminderCoordinator.missed`, tested). The same covers an alarm the phone delivered so late that a newer plan had dropped it.
- **Two reminders can no longer share one alarm** (REP-026 b). An alarm and a notification button are now told apart by the reminder's id in the intent itself, not only by `id.hashCode()`, so two ids with the same hash no longer replace each other's alarm (or make one notification's Done act on the other). The alarms an older version set are cancelled once.
- **World: a clock set back and forward again no longer pays the same hours twice** (REP-026 d). Teams still climb on the earlier clock (no freeze), but the moment the clock was set back from is kept (`WorldData.rewoundFrom`), and when the clock reaches it again each team's settle point moves up to it (WORLD.md 3.1). The most a back-and-forward change can add is the time really spent on the earlier clock.
- Still open: REP-026 (c), asking before a damaged database with no backup falls back to the old files.
## 0.173.0

The rest of REP-025: corrections in Mint, and one copy of the parts Home, Link and Mint shared. No reward number changes; `RewardRules.VERSION` is unchanged.

- **Mint: settle a bill again.** A paid or skipped occurrence used to leave the Bills list for good. It now waits under "Settled lately" for 60 days, where its amount and day can be put right (Paid, Part or Skip again) or the settlement taken back, with Undo. A partly paid bill opens with what was paid so far already filled in.
- **Mint: a budget's period can change** with its amount (weekly to monthly and back); a second budget for the same category and period is refused, as when adding.
- **One screen-lock door.** Mint's lock and the lock in front of Care, Heal, Home and Link were two copies of the same code; they are now one (`ScreenLockGate`). Nothing changes on screen.
- **Home and Link share their list row, tabs and date field** (`ModuleParts.kt`); nothing changes on screen.
## 0.172.1

The easy fixes left by the Plan audit and the Care review, the owner's answers of 12 November 2026, and the Home dashboard. No reward number changes.

- **Plan.** "Add a task" in the empty Next 7 days and All tasks opens Today with the keyboard up (it did nothing); the empty Done offers "Open Today"; the empty Inbox and Targets point at the field above them, and Targets says "Add a target". "No date" clears the planned day only, not the deadline. "Won't do" offers Undo. A task's edits are saved as they are made (a short pause after typing, and when the tab closes): no Save key, nothing lost by switching tabs. Steps can be renamed, removed and unticked; a goal's milestone can be unticked; a goal deleted from its editor offers Undo; a target can be edited (words, how often, rank, category) and deleted with Undo; a finished task can be deleted. Unticking keeps "paid", so ticking again pays nothing more. The calendar pages freely back and forward, and a week is the calendar week from the day Settings sets. Every delete in Plan is grey; the Targets add key is no longer red; Give up asks "Really give up?" first; Progress points at Plan, Goals.
- **Care.** The sweet berry is offered to a visitor even when the Bag holds all five flavours. The Meadow's corner labels are readable in the dark palette. A visitor is visited or played with in the Meadow itself, and Care Play's invite list is your own Pokémon with Show more. The journal and the earlier diary cards end with Show older, and a diary card's urge ratings show in its history row. A calm break survives leaving the screen and finishes in an effect, not during drawing (one left for ten minutes past its length is dropped, not paid). Surprise me changes after each time played. Care's landing and the Meadow call `arrive()` once. Words: routines earn Exp. Candy and a little friendship (not berries); the Egg meter line counts a play; `befriend` says up to four a day. Removed the dead `CareRules.paidOn`.
- **Home dashboard.** The new-rewards box shows on Home only (it showed on every module page, so a World Pokémon appeared in Care). A Quick log card (task, +250 ml, expense, and how is today in one tap), and "Also today" adds Mint bills due in the next 3 days and Link dates this week (counts only, never a name or an amount; nothing for a locked or gated module).
- Left for the owner or for a bigger change: REP-030 in `docs/pokelife/IMPLEMENTATION_BACKLOG.md`.

## 0.172.0

Exact dates, times and lengths in every module, and edit screens where a record could only be added or deleted (owner, 12 November 2026: "I would prefer to input specific dates and times, to have edit functions"). No reward number changes; `RewardRules.VERSION` is unchanged, and a correction never pays and never takes back.

- **Typed entry, one set of fields everywhere** (`ExactInput` in app-core, `ExactFields.kt` in the UI, `docs/pokelife/FEATURES/DESIGN_SYSTEM.md` 6.4). Every date field (Plan, Heal, Mint, Home, Link) now has a text box beside its keys and month: `2026-11-07`, `7/11/2026`, `7 Nov 2026`, or `today`/`tomorrow`; a time takes `9`, `930`, `21:05`, `9pm`, `noon`; a length takes `45`, `1h30`, `1:30`. Each reads back what it understood, and a day or time out of range (a payment in the future, a day that does not exist) says so instead of being guessed. Tested spelling by spelling (`ExactInputTest`).
- **Plan.** A task's day ("Move to") is a full date field (it had three keys and no calendar); its time of day is typed; a deadline has its own time (it could only keep 23:59); the estimate is typed (`1h30`) as well as nudged. A goal's target date is typed, and **goals and milestones can finally be edited and deleted** (title, area, target date, milestone names; before, a typo meant a new goal and a goal could never be removed). A repeating target (frequency task) can mark or take back **any earlier day** ("Another day"); it counts toward the target and never pays. A calendar event's day, start time and length are typed, an all-day event's first and last days are typed, and the focus length (the screen and the default in Settings) and quiet hours are typed.
- **Care.** A routine's time is typed. The diary can be filled in for **any earlier day** and an earlier card opened and corrected (one tap on it); a journal entry can be dated to an exact moment when written, and **edited afterwards** (words, tags and moment). `CareStore.editEntry` existed with no screen.
- **Heal.** An activity's end and start, a night's two moments ("fell asleep" and "woke up", with the length worked out), a drink's and a measurement's exact moment are typed. **Drinks, nights and measurements can be edited** (`updateDrink`, `updateSleep`, `updateMeasurement`, with tests); an activity's edit now also changes when it started. The wake-up alarm and each hydration reminder are typed times, and a new reminder time is chosen before it is added.
- **Mint.** A record's moment (day and time) is always shown, not behind "Date, payee and a note"; a bill is settled on a day you choose (it was always today); a bill's first due day and frequency can be corrected **until a payment is recorded** (then they stay fixed, so no settled occurrence is rewritten).
- **Home.** A record of work can be edited (what was done, the exact day, the item, notes, cost) from the maintenance history and from an item's page (`updateMaintenanceRecord`). Warranties are editable too (merged with the HOME audit's change).
- **Link.** A follow-up takes its day when it is added (it could only be set afterwards), and a follow-up on a person's page opens the same editor as the main list.
- **Gear.** A timer's length is typed as hours, minutes and seconds (`90`, `1h30`, `1:30:00`, `45s`) and an existing timer can be **renamed and resized** (it goes back to ready).
- Not changed, for the owner: reminder hours in Mint, Home and Link and the streak reminder are whole hours (a stored number, so minutes need a schema change); a budget's period and a bill payment's amount stay as they are (settle again to correct a payment).

## 0.171.4

HOME audit (end to end: rules, store, reminders, screens). No reward number changes; `RewardRules.VERSION` is unchanged.

- **Editing a maintenance schedule no longer moves the date it counts from.** Any edit, a rename included, reset the start date to the next date, so a monthly job begun on the 31st and standing at 28 February drifted to the 28th for good. The start now moves only when the next date or the kind of repeat changes.
- **A tick can be taken back wherever it was made.** Ticking a chore or maintenance offers Undo. Before, a chore ticked on the overview, or an overdue repeating chore ticked anywhere, left no row to undo it from, and finished maintenance could not be undone at all (deleting its record left the schedule moved on).
- **Search opens something every time.** Archived chores, finished one-time maintenance and the warranties of an archived item were found and then opened an empty list; a chore done today opened the wrong tab.
- **Warranties can be edited** (tap one); before, a typo meant deleting and typing it again. Archived items can be deleted, like archived chores and supplies.
- **"Bought" on a low supply asks for the new quantity.** Saving it blank did nothing and left the supply on the list.
- **A chore's instructions show when it is opened**, as maintenance instructions already did; they were visible only inside the edit form.
- Faster: the chore list reads the history once instead of once per chore, and the Home menu works out the overview once, not twice. Smaller: an unreachable branch and a wrapper in `Schedules`, and a never-set error in the schedule options, are gone.
- What was left for the owner to decide is REP-029 in the backlog.

## 0.171.3

WORLD story rewrite, first pass (scripts only; no rules, rewards or scene ids change).

- **The opening, Rescue Team and the first four Explorers chapters are rewritten** for warmth and continuity: the lunch Mew steals in scene 1 comes back in scene 6, the Sentret siblings and Pelly's and Braviary's habits recur, the visiting team sleeps in the loft over Persian's bank and walks the Rescue Team's roads one step behind (blanket, berries and all), and Hoopa now drops the team into Explorers with a joke instead of a lecture. Side characters get small lives of their own (Dugtrio's worried family, Caterpie's waiting, Bidoof's third apple).
- Still to do: Explorers 5 to 8, both post-story sets and the four regions.

## 0.171.2

Gear audit (end to end: the 18 tools, their saved state, the Android adapters). Nothing here pays, so no reward number changes.

- **The calculator's Ans key gives the last answer.** It typed the value of the sum being written onto its own end ("2+3" became "2+35").
- **A time converted in the World clock follows today's summer time.** It was worked out for 1 January 2000, so London or New York were an hour out for half the year.
- **The Picker's "Skip last" skips the last pick.** With motion on and the history off it skipped whichever name the roll happened to flash last.
- **The Sound meter's quietest, average and loudest follow the offset.** They kept the offset the tool was opened with.
- **Currency does not fetch on every opening.** The reference rates are a working day old most of the day (and all weekend), so each opening asked the service again; a copy fetched in the last 6 hours is now used, and Refresh still asks.
- **One row over a tool instead of two or three.** The star and the helper share a row; a tool with no helper shows no "Helper / Show" strip (GEAR.md section 6: never an empty placeholder), and the large helper box with "Make it small" is gone. A helper is still chosen from the row when you own one that fits.
- A search with no match says so with one way back; "After 5.0 years" reads "5 years"; the QR tool shows no empty scan box where there is no scanner.
- Removed: `GearTool.blurb` and `move`, `GearData.helperCompact` and `currencyPair`, `convertCurrency` (none was read; old saves load, unknown keys are ignored), a second copy of the "not available here" box and 50 unused imports in `GearTools.kt`.
- Left for the owner: REP-028 in `docs/pokelife/IMPLEMENTATION_BACKLOG.md`.

## 0.171.1

Mint audit: fixes only. No reward number changes.

- **A transfer goes where the screen says.** The destination shown as chosen is now the one saved; before, with accounts in two currencies, the default could be an account in the other currency and the transfer was refused.
- **Activity shows all of its history.** The list stopped at 50 records with no way further except search; it now has "Show more". The quick "Income" or "Transfer" key on the overview no longer sticks for the next visit.
- **Bills.** The first due day is any date (the shared date picker), not five fixed offsets. A bill's name and amount can be edited (tap it under "Your bills"). Paid, part paid and skipped each offer an undo, since a settled occurrence leaves the list. A part payment that covers the whole bill is saved as paid.
- **Weekly review** uses the app's first day of the week on the screen, as the rule already did.
- **Faster with a long history:** every account's balance is worked out in one pass over the records; the Activity search is not redone on every keystroke in the entry form; the import preview looks for look-alikes by day.
- Removed: the "Close" key under a record (tapping the row closes it).
## 0.171.0

Link audit (end to end: rules, store, screens, reminders, tables). No reward number changes.

- **Find a person by phone, email or address.** The directory and the one search matched names, organization, role, category and tags only; a phone is matched by its digits however it is spaced (three digits or more).
- **Add a date from a person's profile** (LINK.md 3.3). It was only possible from Dates, by picking the person again.
- **Deleting a person can be undone** (their notes went with them for good).
- **Opening one search hit no longer sets the tab of the next profile** (a note hit left every profile opening on Notes).
- **A cancelled follow-up shows the day it was cancelled**, not always today.
- **Link and Plan agree about a handed-over follow-up.** Reopening one Plan finished is done in Plan (the button is gone; it used to bounce back at once), and a task Plan takes back to open opens the follow-up here too. Neither pays.
- **"N due" counts only what Link owns.** Home and the Modules card counted follow-ups that had become Plan tasks; the Overview already left them out (`LinkStore.dueNow`).
- **A long directory shows 40 people at a time** (every row was composed at once).
- **Categories:** a person's form has a New category field; Settings no longer has a Categories box whose chips did nothing.
- Tests: three new in `LinkRulesTest` and `LinkStoreTest`.

## 0.170.1

HEAL audit (end to end). No reward number changes; `RewardRules.VERSION` is unchanged.

- **Every record stays reachable.** Activity, Sleep and Body histories end with **Show older** (30 more each tap). Before, anything past the newest 30 activities, 21 nights or 10 measurements could not be seen, edited or deleted one at a time.
- **A night that has not ended is refused** ("That night is not over yet."), as an imported one already was; "Woke today" at a time still to come used to save a night in the future and show it as the last sleep.
- **An active-days goal is at most 7**; the goal field no longer accepts a number the store then silently dropped.
- **One delete pattern.** Deleting an activity, or an import from "Might be the same", now offers Undo like drinks, nights, measurements and goals (the activity's confirm step is gone; the import had neither).
- **One save per log.** A paid activity or drink is written once, already marked, instead of saved and then rewritten: half the passes over Heal's sealed rows for the commonest action.
- Smaller: the 7 or 30 days choice in Connections is a pair of tab buttons like every other choice; histories are sorted once per change, not on every keystroke; two captions that restated the screen are gone; `updateDrink`, `updateSleep` and `drinksToday` (no caller) are deleted.

## 0.169.0

Pokémon asset audit (11 November 2026, `docs/ASSETS/ASSET_USAGE_AUDIT.md`) and what it found. No reward number changes. The app grows by roughly 25 MB of sprite sheets (the new rows and faces) and loses 12.5 MB of Black/White sheets it never drew.

- **Story faces match the story.** Eight more SpriteCollab portrait faces (Sad, Angry, Worried, Teary-Eyed, Pain, Sigh, Dizzy, Stunned) join the first six. Sad, angry, teary and worried lines (about 225) show those faces instead of Normal or Surprised; a form without a face shows a near one, then Normal. Focus shows a sigh when you leave and a dizzy or sighing face when a session fails.
- **A shiny is never in the regular colours.** The 282 forms SpriteCollab has no shiny for (sheets, portraits or both) get one recoloured from their real regular and shiny still sprites (`tools/sprites/derive_shiny.py`, listed in `assets/pmd/derived-shiny.tsv`); a real one replaces it on the next fetch.
- **The buddy reacts with Mystery Dungeon status icons, not emoji** (`ReactionIcon`): a sparkle, "!" and "?", the Zz, and by type a flame, a bolt, a block of ice, a fist, a shield or a drop, with a heart, a note, the sun, the moon, a star and an apple (real item icons and glyphs) where the games have no icon. The Home and module stages and the buddy widget draw them. Four stat icons (flame, shield, fist, ice) were cut from the status sheet.
- **Walking keeps its direction.** A Pokémon in the town that stops stands facing the way it went (new Idle side and up rows). New one-offs: Wake (the buddy stretches awake after a focus session), Pose (just hatched), Rotate (evolved, or a level gained on the Pokémon page), DeepBreath (Care's calm break). A form without a row hops instead; Eat falls back to Hop the same way, so feeding always shows a bite or a hop.
- **Sound.** Every owned Pokémon's tap and pet plays its own slightly detuned cry; up to three cries overlap on Android; the town's meeting icons have their game sounds beside them when the buddy is in the meeting. Unused sounds (`pokemon-center`, the drawer pair, `reminder`, `timer-done`) and the skull icon are no longer bundled, nor the Black/White sheets of forms that have a Mystery Dungeon one.
- Three portrait credits that were blank now name the SpriteCollab contributor. `PmdAssetsTest` checks every sheet table against its files.

## 0.168.0

Design review batch 1. No reward number changes.

- **The zero is no longer an 8.** Both Pokémon DP font files get a bar through the middle of the 0 (`tools/fonts/patch_zero.py`, the licence covers the changed files). The PMD dialogue font's zero already has one; it is a bitmap drawn by `PmdText`, not a font family, so it is not used for running text.
- **₱.** The font gains the peso sign (U+20B1, drawn as a P with two bars) and Mint shows `₱1,250.50` for PHP; other currencies keep their code.
- **Mint categories.** Settings has a Categories box (spending and income: add, rename, put away, bring back, delete when nothing uses it), and the entry form has a New key beside the category keys. The store already supported all of it; no screen reached it.
- **One date picker.** `PixelDatePicker`: Today and Yesterday as keys, and Pick a date opens a month to tap, up to today. Mint's entry form uses it, so a record can be made for any past day. Other modules and editing a record's date follow.
- **Undo for more than tasks.** The undo bar now also brings back, for 10 seconds, a deleted budget, bill or record (Mint), drink, sleep, measurement or goal (Heal), diary card or journal entry (Care), chore, supply or warranty (Home) and note (Link). Plan's lists and events are still without it.
- **Any day for a record.** Heal drinks and body measurements take a past day, a Mint record's date, account and category can be edited, and Home and Link use the date picker instead of a typed `2026-11-07` (`PixelDateField` allows no date and future days).
- **The work comes first on a module page.** The lead's picture window is a 144 dp strip (it was 248) with a smaller sprite, and the new-rewards box runs a payout together as small icon-and-words pieces (a new Pokémon keeps its own row).
- **Also today, on Home.** One card, only when something waits: Mint bills to settle, Home things to do, Link follow-ups due, and Heal's water so far with a +250 ml key. Words are counts only, never a bill, chore or person; a module behind the screen lock (and Mint with its lock on) shows nothing.
- **Exp. Candy.** The everyday level-up candy is shown as Exp. Candy (the games' Exp. Candy icon) instead of Rare Candy, which a fan reads as rare. Only the name and icon moved: its id, rules and every reward number are unchanged, so saves and the ledger are untouched and `RewardRules.VERSION` stays. Super Candy is unchanged. A true Rare Candy prize is not built (it needs an owner decision on where it is earned).
- **One tap for mood (Care).** "How is today?" on Care's page: Rough, Low, Okay, Good, Great as five keys, kept on today's diary card (`DiaryCard.mood`, no schema change), shown in Trends, never paid. PMD portraits for the faces are waiting on fetching more emotions (the strips hold only Normal, Happy, Joyous, Inspired, Determined and Surprised).
- **One place list on a Pokémon.** Twelve role buttons became Make buddy and one Move to... list: the PC and each module's party with "n of 6", Move here, and Make lead in the party it is in.
- **Undo for Plan's lists and events too.** Deleting a list (its tasks return to it) or an event, one occurrence or all, shows the undo bar for 10 seconds.
- **Two ranks, two names.** Home and Me say "Trainer rank" and World says "Exploration rank", so one word no longer means two ladders.
- **Link: the month is a key.** A date's month is picked from twelve keys; the day and the optional year stay short fields.
- **Module colour on keys.** Keys and chips default to the open module's accent (blue only outside a module), the lead buttons on a Pokémon use each module's accent, friendship is pink, and Heal's Log activity is no longer red.

## 0.167.2

What the 0.167.1 audit found and left. No reward number changes.

- **Fix: on a phone out of storage, another store could bring the older save back.** A save that does not fit is kept in memory; a widget or a reminder opening its own store then read the older one from the disk and could write it over the unsaved changes. The stores of the process share what could not be saved until a save fits (`Storage.saved`, `SharedDocuments.keep`).
- **Fix: a snooze or a repeat that came due while the phone was off was never shown.** The planner looks only ahead, so it sat until it was dropped a day later. It goes off at the next plan (boot, opening the app), after the usual check that it is still wanted.
- **Fix: putting one reminder off cleared every held reminder of the same thing.** A snooze lets go of its own reminder and its repeats; "Done" still clears them all.
- **A restore says what it did not bring back.** A backup that lacks a module (an older one), or holds Heal or Mint sealed with another phone's key, leaves that module as it was; the message now names it instead of saying only "Restored" (`LiveDocuments.replaceAll` returns the names).

## 0.167.1

Repo-wide audit (11 November 2026): fixes found by reading every state machine, dead code out, and the agent files brought up to date. No reward number changes.

- **Fix: a focus timer that ran out inside the 10 second grace failed the egg.** Leaving with less than 10 seconds to go and coming back later ended as "left the app", although time inside the grace counts as focus. It is a finished session now (`FocusSession.tick`).
- **Fix: a recurring task moved to another day came back twice.** The day it was moved from had no task planned on it, so the next refresh made that day's instance again, with the same id. An instance is known by its id now (`RecurringTasks.instancesToCreate`).
- **Fix: a transfer could leave a Pokémon's seat behind.** A Pokémon on a World team, in a Guardian slot or helping a Gear tool was removed while its place still named it, and one out on an expedition could be transferred. The transfer waits for the expedition and gives the place up (`Places.movable`, `Places.vacate`).
- **Fix: the screen-lock door of Mint, Care, Heal, Home and Link could stay open.** The once-a-second "still here" tick kept running after the app was left, which counted as coming back. It counts only while the app is in front.
- **Fix: restoring a backup did not reload Link**, so Link's next save could write the old records over the restored ones.
- **Fix: a hatch scene could be replaced by a World moment** started in the same frame, and was then never shown.
- **Fix: a second tap on "Start" (focus) or on the last onboarding line crashed** instead of doing nothing.
- **Fix: the open app could save an older copy over a widget's or a notification's change.** The app, each widget and each reminder open their own store on the same save, each with a lock of its own. There is one lock for the process now, every change starts from the saved document when another store wrote it (`SharedDocuments`, `Storage.shareKey`), and the app's tick picks such a change up within half a second (`sync()`). `worldAssign` builds its change from the document being written.
- **Android's own backup is off** (`allowBackup="false"`, `data_extraction_rules.xml`): nothing goes to the system's cloud backup or to another phone by itself. Heal's and Mint's rows are sealed with a key that never leaves the phone, so such a copy could not be opened there; the app's own backups are the way to move a save (INTEGRATIONS_AND_PRIVACY.md).
- **Fix: a repeatable World job the board offered could be refused** east of UTC: accepting counted the day in UTC, the board and the claim in the player's time.
- **Fix: a clock set back during a focus pause** shortened the pauses already counted.
- **Removed (nothing used them, tests included):** `GameStore.worldClaimItems` (the items go to the Bag in `worldPay`), `CareStore.hasEntries`, `LiveBackup.lastBackupMillis`, `LinkRules.tagsInUse`, `Learnset.methodsFor` and `LearnMethod`, `Inventory.removeAll` and `asContext`, `FloorEventResult`, `LauncherModel.pageIsEmpty`, `Pixel.RedDark`, `Spacing.XXXL`, `Spacing.CardRadius`, two private helpers and two imports.
- **Simpler:** `Confirm`, `Rule`, `Actions` and `parseDay` were copied in the Home, Link and Mint screens; they are one each in `screens/ModuleParts.kt`. The launcher's agenda uses `CalendarEvent.shownTitle` for a private event.
- **Agent files:** `CLAUDE.md` is a third shorter (its Layout detail moved, word for word, to `docs/code-map.md`); stale lines fixed (no server, `.idea/` is ignored, the backlog is the record of progress, cloud sessions work on their own branch); `.claude/settings.json` refuses a push to `main` and a forced push; `ci.yml`'s token is read-only; the old `docs/DESIGN_SYSTEM.md` says it is superseded.
## 0.167.0

A launcher audit and the town's behaviour pass. No reward number changes.

- **Fix: the launcher no longer saves or pays on its own clock.** Its one-second tick settled tasks and focus sessions and wrote the launcher's copy of the save, even with the screen off; it now only moves the clock, and only while the launcher is in sight.
- **Fix: widget set-up screens** are started through the widget host, so a provider whose set-up is not exported to other apps no longer fails and leaves the widget unconfigured.
- **Swipe up and swipe down** on the open town (up opens the app list; down is free to set in Launcher settings → Gestures).
- **Less work per frame.** A page swipe no longer composes the whole screen every frame; walking Pokémon move without composing; the dungeon-tile town builds its map once and sits on its own layer; weather draws at 30 frames a second; the town's Pokémon list, the widgets' room and each hosted widget's size are worked out once, not on every change.
- **Less work on returning home.** One read of the save per Home press (was two), no widget reloads on every resume, icons drawn at the size they are shown, kept to a memory budget, and only the changed app's picture is drawn again after an install. The clock widget no longer polls the battery itself and redraws only each minute without seconds; the app list's blur is off in battery saver. A dark window shows before the first frame.
- **Town behaviour.** Idle variation (turning, grazing, hopping), roaming in short legs around a favourite spot at each Pokémon's own pace, staggered bedtimes with a wake-up hop and breakfast, surprise at a tap or an arrival, weather felt through the game's own status icons, tap reactions (hop, happy, turn, flustered when tapped over and over).
- **The Guardian is calmer and changes.** It is left out of the pairwise meetings, glances from others are rare and do not hold it, and a startle happens at most once every few minutes. Unless the buddy is a Guardian, the Guardians take turns one a day: the old one walks off the edge of Home and the new one walks in.
- **Launcher polish.** Icons on the Folders, All apps, Widgets and Gear buttons, the search placeholder in the game's font, and TalkBack reaches the buddy, the Guardian and favourite Pokémon in the town by name (the rest of the crowd is scenery).
- Docs: `LAUNCHER.md` 6.1 and 11.4.

## 0.165.1

Weather against Red Rescue Team (REP-024; `tools/weather/`). No reward number changes.

- **Fix: Guardian and the Pokémon it met stayed frozen on the launcher.** The reaction and chirp holds were taken off only after their delay; when the town stopped being calm in the middle (a page swiped, the launcher out of sight) the effect was cancelled and the hold stayed for good. They are cleared in a `finally` now.
- **Fix: the sandstorm showed the sheet's caption.** The two sand textures were cut from 8 rows too high, so the word "Sandstorm" and a dark band repeated over the town. Cut from the right rows.
- **Snow, blizzard, clouds and sand follow the game's own numbers.** From the decompiled ground-weather code: a snow frame lasts 5 ticks (83.7 ms, was 220), a blizzard frame 2 ticks (33.5 ms, was 80) and neither has a veil any more (the flakes are opaque in the game); the cloud shadows are the game's blue at a quarter (was black at 16%) and drift 30 pixels right and 15 up a second in whole game pixels (was 6 right); the sand textures are blended at a quarter with no tint rectangle and slide at the game's two texture speeds.
- **Towns built from a dungeon's tiles are graded like the dungeon.** The game sends every tile colour through a per-weather table for red, green and blue (`colvec`: sandstorm yellow-brown, rain dark blue, cloudy cool, hail and snow cold); the town does the same through the real tables (`assets/weather/ramps.tsv`) instead of keeping its clear colours.
- Tools: `tools/weather/decode_bg.py` rebuilds the game's weather layers from its files, `match_sheets.py` proves which layer each sheet is (snow = W03P01, blizzard = W03P03, clouds = W01), `inspect_sheets.py` reports every frame, `extract_ramps.py` writes the ramps. The rain's timing and loop points and the sandstorm's own effect are still open (REP-024).

## 0.165.0

REP-023 (UI consistency) and a launcher pass. No reward number changes.

- **The open task card is a sheet with three tabs.** **When** (move to, list, time, reminder, repeat), **Steps** (notes and small steps) and **Edit** (title, notes, difficulty, category, deadline, estimate, and Won't do and Delete, apart from the day-to-day tabs); a finished task shows only its notes and steps. About twenty controls in one column became one tab at a time. Screenshots in both palettes (`task-card-*`).
- **One feedback system.** `AppModel.say(text, kind)` and `Report(model, text, tone)` put saved, reward and error lines in one bar above the tabs (`FeedbackHost`, over the undo bar), the same on every screen: saved 2.5 seconds, reward 4, error 6, tap to dismiss, errors announced assertively, an error not hidden by a "Saved." arriving with it, the same words again shown again. Heal, Mint, Home, Link and the module lock use it instead of their own notice boxes; a field that cannot be read still says so under the field.
- **Every empty state has a next step.** Its button is in the primary colour: add a task, add a routine or log (the focus moves to the field above), start a focus session for eggs, or Clear for a search or filters that found nothing.
- **Tokens.** `Sizes` (hairline, line, icon and sprite steps) beside `Spacing`; the screens use them for icon and sprite sizes, borders and the last typed gaps.
- **Launcher: one Guardian, and the town notices it.** At most one Guardian walks the town (the buddy if it is one, else the highest level); a Pokémon that walks close to it stops and is startled while the Guardian sparkles. A new weather choice, **Guardian's weather**, gives the town the Guardian's own (Kyogre rain, Articuno snow, Kyurem a blizzard, Landorus a sandstorm, Rayquaza cloud shadows; none of its own is a clear sky); Live forecast stays the default.
- **Launcher: Pokémon chirp.** Now and then one Pokémon in the town says its own cry, quiet and short, with a hop (the buddy more often, the Guardian rarely and lower); only while the town is in sight, never in Reduce motion, battery saver, a call or with Sound off. Settings → Town → Pokémon chirp turns it off.

## 0.164.0

Code-review fixes (saving, memory, battery) and a first batch of Android hooks. No reward number changes.

- **Saving is safer.** `FileStorage` writes a synced temporary file and swaps it in atomically; if the swap fails it throws and never writes over the live save. The save before the last write is kept as `<name>.bak`, and a save that cannot be read is kept aside and the `.bak` is used before the document starts empty (and is written back, so the next backup is a good one). `SavedDocument` now saves first and only then updates the value and tells the listeners, so a failed save leaves the app exactly as it was. Every store change (`GameStore`, Care, Heal, Mint, Home, Link) runs under one re-entrant lock (`StoreLock`), so two threads (a widget, a receiver, the UI) can no longer both start from the same document and drop one change.
- **Crash loops.** `CrashGuard` records every uncaught crash (time, and a log of exception types and stack frames with no messages). More than two crashes within 60 seconds start the launcher in **safe mode**: a plain list of installed apps built from ordinary Android views, with **Copy crash log** and **Try again**, touching nothing of the save.
- **Daily backups.** Seven daily and four weekly copies are kept (`BackupRetention`, was the newest three); every file in them is sealed with a Keystore key (`BK1:` prefix, `KeystoreBackupKey`), including the Care journal, and no backup is made without the key. A WorkManager job (`BackupWorker`) takes the backup about once a day even if the app is not opened. Settings → Backups shows the last backup. Older plain backups still restore; a backup sealed with a key the phone no longer has is skipped.
- **Exact reminders.** An alarm that the phone will not run exactly is no longer set as an inexact one in silence: it is logged, and Settings → Notifications says so with a button to the phone's "Alarms & reminders" screen.
- **Memory and battery.** Decoded pictures (scenes, sprite sheets, tiles, weather, world art) live in size-bounded `BitmapCache`s that let go of the least recently used instead of growing for ever. The Pokégear timer, the dungeon strips, the camp yard and the weather layer animate and poll only while the app is in sight (and the strips only while on screen); the camp yard no longer recomposes every frame. The flashlight's `TorchCallback` is unregistered when the tool or the app is left. Launcher category chips have stable keys.
- **Gear number fields** say "Enter a number, like 12.5." under the field instead of silently turning text into 0 (fees, rates, price and the everyday sums); a wrong value gives no answer instead of a wrong one.
- **Undo** shows a bar that drains over the ten seconds left to undo.
- **The Dex data is JSON, not Kotlin source.** The seven tables (species, forms, evolutions, regions, entries, moves, learnsets) are `pokemon-core/src/commonMain/resources/dex/*.json`, typed rows one per line (numbers, text, nulls and real lists instead of pipe-separated text), written by `tools/species/generate.py` (a fresh run from PokeAPI reproduces them byte for byte) and read by `DexParser`. The JVM and Android read them as resources; the web build gets them built into its code by a `generateDexWebSources` Gradle task (nothing generated is committed). `DexData.kt` and its 760 KB of Kotlin string constants are gone, so the compiler no longer has to chew them on every build. CI checks the JSON instead of the Kotlin file.
- **Android:** share text to PokéLife from any app (it opens the Planner's quick add with the text typed in, nothing added until you say so); Quick Settings tiles for **Add task** and **Focus**; **Call, Message and Email** buttons on a person in Link (they open the dialer, messages and mail apps, never place anything); a **wake-up alarm** in Heal → Sleep handed to the phone's clock app; the phone's screen lock can now also guard **Care, Heal, Home and Link** (Settings → Private modules; Mint keeps its own switch).

## 0.163.0

- **Story scenes on a ground map are shown the way the games show them.** The map is no longer squeezed into a ten by six box with black bands round it: it fills the screen at a whole-number pixel scale (about 256 pixels of the art across, as on the DS), never smoothed, and the characters are drawn at that same scale, so a Pokémon is the size it is next to a house instead of towering over the square. A camera looks at the characters: it centres on them when they fit in the view, follows the speaker when they are further apart, glides between the two, stays inside the map and rests on a whole map pixel. Conversations on a map put the two speakers close together in the middle of the view; cinematics are one step closer, with a "big_" character at twice the scale. The script's grid is now a window of ten by six cells (about a tile and a half each) centred on the map, so existing scripts keep their layout. Scenes on stock backdrops and a dungeon's own tiles are unchanged for now.
- **Nothing is "claimed" any more.** Finishing a quest now says **Welcome Wigglytuff** (the Pokémon joins you), a finished World job says **Report back** (on the board, in the journal and in the primary action), and a research step says **Collect**. The rules are unchanged.

## 0.162.0

HEAL and MINT external integrations (REP-014, `ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` sections 5, 7 and 14; `MODULES/HEAL.md` section 10; `MODULES/MINT.md` section 10). No reward number changes (`RewardRules.VERSION` stays 20): nothing imported ever pays. Tested on the JVM and compiled for Android; **not run on a device, and Health Connect has not been tried on a real phone or the Huawei Watch Fit 4 Pro**.

- **MINT: statement import from a CSV file** (user-assisted import, Mint → Import). The player picks an account and a file through Android's document picker (no storage permission; read once as strict UTF-8, 2 million characters at most, 5,000 rows at most). `StatementImport` reads quotes, doubled quotes, line breaks in a field, comma, semicolon or tab and a byte-order mark; guesses the date, description, amount (or money out and money in) and reference columns from the header, all of which can be changed; reads dates in the order the file uses (it says so when the file cannot settle it) and money exactly, with no rounding and no conversion; and builds a preview: new rows, rows already added (recognised by a fingerprint of account, day, signed amount, description, the bank's reference and a count, so two identical coffees stay two), rows that may match a record the player wrote (left out unless tapped; never merged or deleted) and unreadable rows with the reason. `MintStore.importStatement` saves the kept rows in one write as posted, unreviewed transactions of source `IMPORTED` with their batch, file name and fingerprint, and a retry or a second import adds nothing. Earlier imports are listed; each can be marked reviewed or deleted on its own, which never touches what the player wrote. An import is never a reward event, and reconciling still counts only records the player wrote.
- **HEAL: Health Connect, read only** (Android platform API, Heal → Connections). The player chooses which of exercise sessions, sleep sessions, weight, body fat and drinks Heal may read; Android's own screen asks for each read permission (no write, no background and no history permission, so only the 30 days before the first grant can be read), and every visit rechecks them. Nothing is read until the player taps Read now (last 7 or 30 days). A preview shows what would be added, skipped (same provider id: idempotent), left out (impossible or future) and flagged before anything is saved. A record that looks like one the player wrote is added beside it and flagged; the player chooses Keep both or Delete the import, and the record they wrote is never changed. Every imported record keeps its provenance (provider, writing app's package, provider id, import time) and shows as imported; imported records never pay Field Research and never repay history. Disconnect stops reading and takes Android's permissions back but keeps what was imported; imported records can be deleted per writing app. A failed or partial read keeps what came and does not move the last-read time. No exception text is logged or shown.
- **Honest status** (`Integrations`): each page lists its integrations with class and status. The statement file is a user-assisted import. GCash, Maya, MariBank, GoTyme and UnionBank, reading payment notifications and a direct Huawei link are listed as unsupported or unverified, with why; nothing logs in, scrapes or asks for a password, PIN or code. HEAL lists what it does not read (steps, heart rate, HRV, stress, SpO2, calories, sleep stages, nutrition).
- **Android:** `androidx.health.connect:connect-client:1.1.0` (read permissions for the five record types, the `com.google.android.apps.healthdata` query, a rationale screen and the Android 14 permission-usage alias), `AndroidHealthConnect`, `AndroidTextFiles`.
- **Data:** no schema change. `Transaction` gains `importBatch`, `importLabel`, `fingerprint` and `unreviewed`; the four HEAL record kinds gain `provenance`; HEAL's settings keep the chosen kinds and the last successful read. All are optional with defaults, so older saves and backups read as before.

## 0.161.0

- **Launcher: who walks the town follows the spec** (`LAUNCHER.md` 6.1). The buddy first, then the favourites, then the other Pokémon; inside the favourites and the others, those with an animation sheet (PMD SpriteCollab or Black/White) come before those without one, then by level. With more than 24 owned, the Pokémon without a sprite are the first to stay out of the town. Tested on the JVM (`LauncherModelTest`), **not run on a device**.

## 0.160.0

- **The two Mystery Dungeon arcs have their own places and real pictures.** The old home dungeon list is split by the brief (`docs/world-story-rewrite.md`, work item 2): **Rescue Team** has Tiny Woods, Thunderwave Cave, Mt. Steel, the new **Frosty Forest** and Sky Tower; **Explorers** has the new **Beach Cave**, **Drenched Bluff**, **Crystal Cave** and **Hidden Land** besides Waterfall Cave, Glacier Palace, Ancient Ruins, the Temporal Tower and the Distortion Rift (14 dungeons in all; every id kept, so clears, missions and teams in old saves still resolve; Thunderwave Cave moved to the Rescue Team layer). The new places use the game's own tilesets (`beach-cave`, `drenched-bluff`, `crystal-cave`, the Explorers sheet 42 for Hidden Land, Rescue Team's 36 for Frosty Forest) and sit on the world map; the map now uncovers in eight steps whatever the layer's number of dungeons. The scenes that were set in a stand-in dungeon (the stolen treasure, Bidoof's lesson, the frozen pond, the Hidden Land, Absol in the snow) now play in their own.
- **Story scenes are set on real Mystery Dungeon maps instead of stock backdrops.** A scene's background can be `map:<name>`: a ground map of Red Rescue Team (Pokémon Square, the village, Frosty Forest, the snow forest, the comet, the Nightmare, the sky) or of Explorers of Sky (Treasure Town, the Guild gate by day and by sunset, the hall, the mess hall, the dorm, the sea cliff, the Hidden Land, the Temporal Tower under its red sky, the dark future, the forest clearing), 42 pictures bundled (about 2 MB; `StoryPlaces`, `storyRescueMaps` and `storySkyMaps` in `composeApp/build.gradle.kts`). A test checks that every scene's picture exists and is bundled.
- **The optional episodes after each story are written.** Rescue Team: Gengar's Debt, The Three Birds, The Buried Chamber, The Wish and The Old Legend (chapters 101 to 105); Explorers: Bidoof's Wish, Igglybuff the Prodigy, Today's "Oh My Gosh", In the Future of Darkness, Manaphy's Return and The Marine Resort (101 to 106). They open when the arc's Guardian has been faced, are never queued or forced, and are played (and replayed) from World → Journal → Story archive under "<arc>: after the story" (`StoryEpisode.special`, `WorldMoment.SpecialEpisode`, `StoryCatalog.specialsOf`). No scene pays anything.
- Not done: the Rescue Team arc has no world map picture of its own (the game has none among the ground maps; it shares the Explorers map with its own markers).

## 0.159.0 and earlier

One line each; the full notes are in this file's git history.

- **0.159.0**: Battle identity: moves, abilities and held items count in a World team's power (WORLD.md 3.2, `POKEMON_ECOSYSTEM.md` 4.1 and 8.3).
- **0.158.0**: Guardians are normal Pokémon with a slot per module (owner, 8 November 2026; `RewardRules.VERSION` 21).
- **0.157.0**: The story has sound (WORLD.md 4.4, REP-017).
- **0.156.0**: The Rescue Team and Explorers of Sky arcs have their scenes (`RescueChapters` 6 episodes and 20 scenes, `ExplorersChaptersA` and `B` 8 episodes and 19 scenes), ported from the owner's brief…
- **0.154.0**: Heal is sealed (REP-014; owner decision 6 of 8 November 2026).
- **0.152.0**: Launcher: other apps' widgets are hosted by what their provider allows, a removed provider no longer leaves a hole, and search finds PokéLife's own places (REP-014, `FEATURES/LAUNCHER.md` sections 5.2 and 11.5).
- **0.150.0**: PLAN's and CARE's rewards settle under their own ledger event ids (REP-014, `DATA_AND_MIGRATIONS.md` section 7.3.0 step 2).
- **0.149.0**: Link reminders, and the streak reminder and weekly recap on the shared scheduler (REP-014, REP-011).
- **0.148.0**: World: expedition Pokémon experience (WORLD.md 5.2), and a fix for Guardians that could not be seen.
- **0.147.0**: 23 more Pokémon get their Mystery Dungeon sprite.
- **0.146.0**: Every module opens on its lead, and the rounded parts that were left are pixel parts.
- **0.145.0**: Link, the connection module, built manual first (REP-014).
- **0.144.0**: Cross-module links, Home reminders, and real reward events in the ledger (REP-014).
- **0.143.0**: Home, the household module, built manual first (REP-014).
- **0.142.0**: Mint follow-ups (REP-014): a backup of Mint that opens on any phone, bill reminders, the optional access gate, and a full edit for accounts (owner: "mint edit accounts").
- **0.141.0**: Mint, the finance module, built manual first (REP-014), and a scene of its own for every way a new Pokémon arrives (owner: "all are egg, that was made for Plan's focus eggs: reuse it, but not too much, whatever makes…
- **0.140.0**: Real Rescue Team art from the game's own files.
- **0.139.0**: Species lists of every module region, curated together (owner: "curate all modules species list, as much as possible, no dupes, keep it relevant to the theme, follow mainline regional dex patterns, module guardians…
- **0.138.0**: From phone testing of 0.137.0.
- **0.137.0**: Design pass, from the owner's phone testing: consistency, pixel parts and fewer words.
- **0.136.0**: Heal (Modules → Heal), the manual slice.
- **0.135.0**: Your Pokémon, Bag and Poké now live in their own database tables (schema 6), the way Plan's and Care's already do.
- **0.134.0**: Each module teaches how it gives Pokémon.
- **0.133.0**: One Pokémon, one place.
- **0.132.0**: Visitors are calmer and kinder (Care).
- **0.131.0**: One reminder system for Plan and Care.
- **0.130.0**: PokéLife is fully local.
- **0.129.0**: Your save now lives in a database on the phone (Android).
- **0.128.0**: Debug builds get a second icon, "DB check", that runs the database checks on a throwaway file (SQLite version, foreign keys, WAL mode, rollback, speed, backup copy, reopen) and shows PASS or FAIL for each line.
- **0.127.0**: Under the hood (REP-011): reminder source adapters for Plan and Care and a router for notification actions.
- **0.126.0**: Care's routine reminders now respect quiet hours.
- **0.125.0**: One currency: Poké.
- **0.124.0**: The buddy and each party's lead are now separate.
- **0.123.0**: The home layer is gone; World has two optional Mystery Dungeon arcs.
- **0.122.0**: World opens with Mew's prank.
- **0.121.0**: A simpler World landing.
- **0.120.0**: Currency is a set of rows you can type in.
- **0.119.3**: Other apps' widgets fit their cells.
- **0.119.2**: From the deep review of 3 November (its P0 story and dungeon items):
- **0.119.1**: Launcher faster.
- **0.119.0**: Every chapter is a scene now.
- **0.118.0**: World story engine, first slice.
- **0.117.0**: Pokégear, the field tools (Phase 2 finished): four more tools (eighteen in all).
- **0.116.0**: World: the Story archive (a small button on the World landing): every rank reached, every story chapter opened (by layer) and every legendary Pokémon that joined, each with a play button to watch its cutscene again.
- **0.115.0**: World: the Sinnoh layer (fifth button on the Map's layer switch, open from the first day): nine dungeons from Diamond, Pearl and Platinum (Eterna Forest, Oreburgh Mine, Wayward Cave, Old Chateau, Iron Island, Mt.
- **0.114.0**: World, Phase 3 finished: hazards and choices.
- **0.113.0**: Cutscenes for a new World rank and a new story chapter.
- **0.112.0**: World, dungeon modifiers (Phase 3).
- **0.111.0**: World, Phase 3 (first part): side paths opened with field moves.
- **0.110.0**: Pokégear, the specialist toolbox (Phase 2): six new tools (fourteen in all), with a new Measure group on the home.
- **0.109.0**: World, Phase 2 (first part).
- **0.108.0**: Launcher: pages and Pokémon.
- **0.107.0**: Pokégear is a toolbox now: eight tools, with a home that has search, favourites, the tools you used lately and groups.
- **0.106.0**: Pokégear privacy fix.
- **0.105.0**: World landing, rebuilt around one action.
- **0.104.0**: World, Expeditions: an "All teams" card at the top says what every team is doing (where, which floor, who is on it, or ready or empty) and how many Pokémon are waiting to be recruited.
- **0.103.0**: Plan has no quest for the legendaries World gives.
- **0.102.0**: World teams: a Guardian (legendary or mythical) can join a team while it is idle, like any other Pokémon; one that is slotted as a Guardian cannot, and a Guardian on a team cannot be slotted until it is recalled.
- **0.101.0**: World: the Hoenn layer (fourth button on the Map's layer switch, open from the first day): nine dungeons from Ruby, Sapphire, Emerald, Omega Ruby and Alpha Sapphire (Petalburg Woods, Granite Cave, Meteor Falls, Mt.
- **0.100.0**: World: the Johto layer (open from the first day, third button on the Map's layer switch): nine dungeons from Gold, Silver, Crystal, HeartGold and SoulSilver (Sprout Tower, Ilex Forest, Union Cave, Ruins of Alph,…
- **0.99.0**: World: the Kanto layer.
- **0.98.0**: Primal Dialga is its own Dex form (`dialga-primal`, id 20001, an alternate form of species 483), separate from Dialga, for the home layer's Temporal Tower Guardian.
- **0.97.0**: World job alert (Android): a notification when an exploring team finishes a job on the mission board ("A World job is done", or how many are ready), opening World.
- **0.96.0**: Moves in the Dex (owner's decision d16): `tools/species/generate.py` now also writes every move (919: type, category, power, PP, accuracy, priority, generation) and each form's learnset from the newest game it…
- **0.95.1**: Care: the journal draft is kept across an app restart (saved after a short pause, cleared when the entry is written).
- **0.95.0**: World, clearer (audit Phase 5, first slice): the landing opens with "Next up" after your first trip (the first open dungeon you have not cleared, or what the nearest closed one is waiting for, with a one-tap "Send my…
- **0.94.4**: First-use states (audit): a screenshot test renders every landing page for a brand-new player.
- **0.94.3**: Audit: a screenshot test (`ScreenshotTest.largeTextPlaces`) renders Home, Plan, Planner, Focus, Care, Pokémon, Me and World at 130% system text; all eight were looked at and nothing is clipped or overlapping.
- **0.94.2**: Plan usability, Phase 3 (finished): checked against the code and the audit's three points.
- **0.94.1**: Back chains are tested with real taps (`BackChainTest`): the test presses the header's Back button in an offscreen screen and checks where it lands.
- **0.94.0**: Launcher pages: make your own pages and drag widgets between them.
- **0.93.0**: Launcher widget grid is on by default (Launcher settings → "Widget grid (drag, resize)" goes back to the old list).
- **0.92.0**: Launcher widget grid (preview switch in Launcher settings): drag and resize like Smart Launcher.
- **0.91.1**: World map: the wild Pokémon shown on each dungeon are dark shapes standing still, not walking sprites: they are samples of who lives there, not Pokémon you have.
- **0.91.0**: Launcher: the widget grid can be switched on as a preview (Launcher settings → "Widget grid (preview)", off by default).
- **0.90.8**: Groundwork for launcher widgets like Smart Launcher's (drag anywhere, resize): the grid model (`launcher/WidgetGrid.kt`: 4 columns, per-kind size limits, add, move with what is in the way going down, resize, and the…
- **0.90.7**: Launcher: the night tint is one sheet over the whole town, so it reaches the right edge (a lighter, untinted strip showed there at night).
- **0.90.6**: Plan usability, Quick Add (audit Phase 3, first step): the box now shows what it understood while you type ("Understood: Tomorrow · 17:00 · Social · Difficulty B · 30 min"), so a typed line is never a guess.
- **0.90.5**: First-use pass: every landing page was rendered on a brand-new save (`ScreenshotTest.firstUse`, 33 pages: Home, Modules, Plan and its sections, the planner's views, Care and its sections, World's sections, the…
- **0.90.4**: Control system, the rest of it: a tappable list row tints faintly under a mouse and shrinks to 98% when pressed (like a key); the bottom tab bar items tint under a mouse.
- **0.90.3**: `PixelField`: one text field for the whole app, replacing 29 plain Material fields (`theme/Fields.kt`).
- **0.90.2**: Design system: one key for every control.
- **0.90.1**: Care routines can be edited: tap a routine, then Edit to change its name, time and days (what it earned and the days it was done stay, and editing never lets today pay again).
- **0.90.0**: Phase 1 of the audit (`docs/audit-2026-10-31.md`) and its five decisions: World's rewards are in the reward history (Poké and items, merged per ten minutes, never a reveal) and its items go straight into the Bag, so…
- **0.89.1**: Audit (`docs/audit-2026-10-31.md`, answering the owner's repository audit brief): findings with severity, what was and was not inspected, a clean-up classification, a phased plan and the decisions only the owner can…
- **0.89.0**: Emoji show in the launcher's pixel text (an agenda event or a task called "🏠 Home" showed "??"): the dialogue font cannot draw them, so the system's emoji font draws them beside the letters.
- **0.88.2**: World review fixes: a daily or weekly job comes back on the right day even when no team has settled since (the screens now pass the clock); a Café brew is no longer wasted by brewing again while one still works; a…
- **0.88.1**: The World economy simulation now plays jobs of every kind (Explore, Find items, Rescue, Deliver, Escort, Defeat), the Swap Shop and the Dojo (`docs/world-economy-report.md`).
- **0.88.0**: World jobs: Escort (the team must be at least three Pokémon) and Defeat (the team must have enough power) jobs, five in all.
- **0.87.0**: World's map draws each dungeon like the expedition box: the dialogue border round that dungeon's tiles, its wild Pokémon walking the floor, and its details written on the wall tiles.
- **0.86.2**: World expeditions, redone: the box of a team that is out is the app's dialogue box filled with its dungeon's own tiles: one row of wall on top, then a floor that scrolls past while the Pokémon walk across it (only on…
- **0.86.1**: World expeditions: the box of a team that is out is made of its dungeon's own tiles (every dungeon now has its own tileset except the Distortion Rift, which uses the World Abyss): a frame of walls round a floor that…
- **0.86.0**: World, the rest of the first design: five more dungeons across the other regions (Waterfall Cave, Glacier Palace, Ancient Ruins, Sky Tower, Temporal Tower) and a post-story Distortion Rift; dungeons…
- **0.85.0**: World skills, base and research: three skills level as teams explore, even away (Exploration: 2% more Poké per level; Foraging: 1% more berry chance; Scouting: 0.3% more find…
- **0.84.0**: World missions and rescues: a Missions section in World, open with story chapter 2 (Bronze rank and one dungeon clear).
- **0.83.1**: World economy simulation (`./gradlew :world-core:simulateWorldEconomy` writes `docs/world-economy-report.md`).
- **0.83.0**: World, the dungeon game (not the same as the Explore module in the ecosystem plan): a new card in Modules.
- **0.82.0**: Buttons are redrawn as flat pixel keys (the "Outline" option): a stair-cornered outline in the key's colour on the panel colour, the label in the same colour, and while held the key fills with its colour and the…
- **0.81.0**: Pokégear: a hub of handy tools with helper Pokémon, in Modules (the Pokégear card) and from the launcher (the Pokégear button, which replaces the phone Utilities screen of 0.80.0).
- **0.80.0**: Launcher: battery saver (Launcher settings → Battery saver: Off, Automatic or Always).
- **0.79.0**: Launcher: the Buddy widget, with portraits and no words.
- **0.78.0**: Launcher: more space between an app's icon and its name.
- **0.77.0**: Launcher: hold any quiet part of a widget on the home screen (or the Today page) to edit the widgets right there.
- **0.76.0**: Launcher: put another app's Android widget on the screen.
- **0.75.0**: Launcher: every box on the home page is a widget now, and so are the calendar and the day's agenda on Today.
- **0.74.0**: Launcher: Niagara-style features.
- **0.73.0**: Sound: only the moments of the game make a sound now (an egg, a rank up, a berry, a focus session starting or ending, the lock when Home is pressed during focus, notifications).
- **0.72.0**: Launcher: weather over the town, from the real Mystery Dungeon effects: rain with splashes, snow, a blizzard, a sandstorm and the shadows of drifting clouds, set in the launcher settings (Weather).
- **0.71.0**: Launcher: a second page beside the town, Today: a month calendar (Prev and Next change month, a dot marks days with plans) and the chosen day's agenda, events first and then its tasks with a box to tick.
- **0.70.3**: Launcher: the app list comes in from the right again, but only a short glide (56 dp) while it fades in and its blur clears, on the app's smooth curve (a flick still keeps its speed and settles on a spring).
- **0.70.2**: Launcher: the app list no longer slides in.
- **0.70.1**: Launcher: the Pokémon's "?" and "!" icons sit just over their heads (they floated far above them).
- **0.70.0**: Sound: the app and the launcher now have their own sounds, from the Mystery Dungeon sound effects: a cursor blip for tabs, menu cards, settings rows and the letter scroller, confirm and back for buttons, whooshes for…
- **0.69.0**: Launcher: the app list now comes in from the right.
- **0.68.0**: Launcher settings: All apps has a Settings button that opens a dialogue box with the town (the picture or the scene), how many Pokémon live in it (8, 16 or 24), whether they walk about, whether the status box shows,…
- **0.67.0**: The whole app uses the game's dialogue box as its card: cards, list rows, menu cards and the Home region cards are drawn with a thin version of the launcher's frame (a pale hairline, a blue band, an inner line,…
- **0.66.1**: Launcher: the dialogue boxes are drawn like the game's menu boxes: a pale hairline, a thin blue band at the top and bottom that is thicker at the sides, a deep-blue line, rounded corners, and a see-through black…
- **0.66.0**: Polish and states (phase 3 of the design audit).
- **0.65.0**: Tokens and consistency (phase 2 of the design audit).
- **0.64.0**: Accessibility, contrast and layout (phase 1 of the design audit, `docs/DESIGN_SYSTEM.md`).
- **0.63.0**: Launcher home: a clearer hierarchy, in three layers.
- **0.62.1**: Launcher: the home page's text is one dialogue box, not several, with even padding all round.
- **0.62.0**: The launcher's home page uses the game's dialogue boxes: your pinned apps and the parts of PokéLife (Plan, Focus, Care, Party, Pokémon) are two menu boxes side by side, the buddy and your next task sit in a message…
- **0.61.0**: Launcher: pressing Home now always returns to the home page, from the app list, the long-press sheet or the town picker (it used to stay in the app list).
- **0.60.0**: The whole app can use a scene or a dungeon town as its page background: Settings → Look → Background → Change shows small pictures of Day (the default time-of-day gradient), the 11 scenes and the 17 dungeon towns.
- **0.59.0**: The launcher's town can be a dungeon: 17 towns laid out from the tilesets of Mystery Dungeon dungeons (Lush Prairie, Apple Woods, Drenched Bluff, Craggy Coast, Crystal Cave, Dark Hill and more), with a wall band…
- **0.58.0**: The Pokémon of the launcher's town now walk: towards you, away from you, or to the side (mirrored for left), with their own Mystery Dungeon walk animations, instead of sliding about facing front.
- **0.57.0**: The launcher's town can be set in any of 11 real Pokémon scenes (meadow, city, route, forest, mountain, beach, desert, thunder plains, ice cave, damp cave, volcano cave): press and hold the town and pick one.
- **0.56.0**: The launcher's home page is now a town: a real Pokémon scene where every Pokémon you own (up to 24, the buddy and the highest levels first) wanders with its own idle animation; tap one for its cry and a hop.
- **0.55.0**: PokéLife can be the phone's home screen, opt-in: nothing changes until it is chosen as the home app in Android's settings (the home page has a button that opens them).
- **0.54.0**: The app icon has a themed version: on Android 13 and later, with Themed icons on, the launcher tints the egg's outline, check and stopwatch in the wallpaper's colours.
- **0.53.0**: The PC is now boxes of 30, like the games, with Previous, Next and "Box 2 of 5" above and below the grid.
- **0.52.0**: The Party page shows every region's party at once: Plan's, then Care's, each under its region's name and colour with its lead first and its perks after, instead of a switch between them.
- **0.51.0**: A Pokémon's page reads in a clear order: who it is (its Pokédex number, species, level and types), its traits (nature with the stat it raises and lowers, ability, gender, shiny, and the IV judge with its best stat),…
- **0.50.0**: Each region has its own look.
- **0.49.0**: Home is now an overview of your whole world: a card for each region (Plan and Care) with its lead, how today is going there and the one next thing to do, then your streak, mini-quests and what happened recently.
- **0.48.1**: Shinies are back to 1 in 128 per egg (0.46.0 made them 1 in 192).
- **0.48.0**: Moving around feels smoother: the bottom tabs fade into each other, and opening a module, a section or a Pokémon slides it in a little, with Back sliding it out the other way.
- **0.47.1**: A save that cannot be read is now kept aside (as `save.json.unreadable-…` or `care.json.unreadable-…` beside it) instead of being overwritten when the app starts fresh, so progress and Care's diary can still be…
- **0.47.0**: The app is now called PokéLife: a Pokémon world for real life, where each module is its own region.
- **0.46.0**: Much more candy, and a Pokédex that fills faster.
- **0.45.1**: Mini-quests answer when you tap Done: the quest turns to "Done" straight away and the card says what you got (the berry, and the bonus one for all three).
- **0.45.0**: More time with visitors.
- **0.44.0**: Only the Plan and Care Regions now.
- **0.43.1**: Care's perks now open at rank Trainer, together with Plan's party perks: before then, Care's party panel and its card in Pokémon, Party say which rank shows them.
- **0.43.0**: Plan and Care pages now match.
- **0.42.0**: Care feels more like Pokémon.
- **0.41.0**: Visitors like berries.
- **0.40.0**: A Care widget for the home screen: today's routines with a tick (ticking one there counts and pays just as in the app), the ones already done, and whether today's diary card is saved, in the meadow.
- **0.39.0**: Gentle routine reminders on Android.
- **0.38.0**: Care can keep a copy in the cloud, if you want it to.
- **0.37.0**: Visits finished.
- **0.36.0**: Befriending is now a visit instead of a button.
- **0.35.1**: Fixed the "Just 5 minutes" button on the Focus screen: it was drawn on top of the Start button, so one hid the other.
- **0.35.0**: One party per module.
- **0.34.0**: Care, the second module, under Modules, inspired by journalling and DBT: small routines you tick off each day, a 1 or 2 minute calm break with your buddy asleep beside you, a daily diary card (how strongly you felt…
- **0.33.0**: A new Modules tab, ready for Care and the modules after it.
- **0.32.0**: The Care Region's dex is chosen: 143 gentle, soothing Pokémon (Chansey, Snorlax, Munna, Comfey, Milotic, Wooloo and more), with Cresselia and Shaymin as its patrons and Goomy and Slakoth as its Super Rare lines.
- **0.31.1**: The Home Region is now the Plan Region, the first of the module regions.
- **0.31.0**: Pick for me on Home's Next up card: it picks one of today's tasks for you, and finishing that one pays 10% more candies.
- **0.30.0**: Notifications on Android that the design promised.
- **0.29.0**: Editing a task now also sets its category, its notes and its deadline (a day, with Today, Tomorrow, Next week and a day-by-day nudge, or none).
- **0.28.0**: Trainer ranks come quickly at first and slow down later: Apprentice after a day or two of play (it took over two weeks), Ace in under two weeks, and each rank after that takes longer than the last.
- **0.27.0**: Training plans are gone: no more choosing focus stats.
- **0.26.0**: Party perks you can see: the Party screen shows each perk's total and, opened, where every part comes from (your buddy's types and ability, its held item, the rest of the party, Guardians).
- **0.25.0**: Behind the scenes, the Care module's first part (no screens yet): self-care routines, 1 to 2 minute calm breaks, private mood check-ins with an optional note and tags, gratitude and reflection prompts, and rolling…
- **0.24.2**: Fix: undoing a habit and ticking it again the same day no longer pays twice.
- **0.24.1**: Behind the scenes, before a second module: the cloud save is now Plan's own (`plan.saves`), so the shared game tables hold only the game; every reward the server records says which version of the reward rules made it…
- **0.24.0**: Your own events can repeat: daily, on weekdays, weekly, every 2 weeks, monthly or yearly, timed or all day ("Gym" every Monday, "Home" every weekend).
- **0.23.0**: More Pokémon sounds.
- **0.22.0**: A Month view in the Calendar (Day / Week / Month), after TickTick and Google Calendar: events over several days are one solid bar across them, one-day all-day events are short bars, and timed events and tasks sit on…
- **0.21.0**: Lists in the Planner, after TickTick.
- **0.20.0**: The Pokédex tracks forms.
- **0.19.5**: Each caught Pokémon's Pokédex entry now shows it in a real scene for its type, the same ones buddies get on Home: Water Pokémon on the beach, Grass and Bug in the forest, Electric on the thunder plains, and so on.
- **0.19.4**: Pokémon in the Pokédex no longer lose their animation (or show nothing) after you page through entries quickly with Next: a sprite whose loading was cut short is no longer remembered as missing.
- **0.19.3**: The Planner's menu and lists are redesigned after well-regarded to-do apps (Apple Reminders' smart lists, Things' Today and Logbook, Todoist's Upcoming).
- **0.19.2**: Your buddy no longer shrinks for a moment when you pet it or give it a berry: its hop and bite play at its usual size.
- **0.19.1**: The Calendar in the Planner is redesigned after well-regarded calendar apps (Fantastical's day ticker, Apple and Google Calendar's grid, Structured's inbox).
- **0.19.0**: Easier to use, after a review of every screen against the Laws of UX.
- **0.18.0**: Eggs hatch like in the games: "Oh?", the egg rocks three times, harder each time, a flash, and your new Pokémon is there and calls out, with its category ("the Seed Pokémon").
- **0.17.1**: Easier to read and tap: green and blue text is a little darker so it stands out on cards (and red a little lighter in dark mode), every button is at least 48 dp tall, and the starter picker, PC tiles and the opening…
- **0.17.0**: Clearer numbers under Me: the trainer card says which totals are all time and how the streak counts.
- **0.16.1**: Focus keeps your egg when you lock the screen or a call comes in, as it always should have: put the phone face down and the timer runs on, and a call pauses the timer until you come back.
- **0.16.0**: The Pokédex tells you where each Pokémon stands: Owned (in your party or PC now, with a Poké Ball on it), Caught (yours once, since transferred), Seen (a friend's buddy or a quest's Pokémon, still a grey shape but…
- **0.15.0**: Your own events: New event on Planner → Calendar adds an appointment or a block of time that isn't a task, like a lesson or lunch.
- **0.14.0**: Drag to plan: Planner → Day is now Planner → Calendar, with Day and Week.
- **0.13.0**: More of the Mystery Dungeon look: your buddy curls up and sleeps on Home and the widget while a focus session runs, hops when you pet it and munches a berry you give it, all with its own SpriteCollab animations (the…
- **0.12.0**: Your calendar on the Day timeline (Android): Me → Settings → Calendars lets PokéPlan read the phone's calendars, Google Calendar included, and you tick which to show.
- **0.11.0**: A new look for every Pokémon: the Pokémon Mystery Dungeon style sprites of the PMD SpriteCollab, idling in place on Home, the widget and each Pokémon's page and standing still in lists, the Pokédex and the PC…
- **0.10.0**: Egg tickets: when you are signed in, your phone keeps a stock of 20 eggs the server has already rolled the dice for, so a focus egg hatches the same way with or without a connection and failing a session never…
- **0.9.0**: Planner → Day: a timeline of your hours.
- **0.8.1**: A cleaner interface: four text sizes instead of seven (none below 12 sp, widgets included), no coloured strips on the left of cards, nested corners that line up, commas instead of "·" separators, and shorter copy…
- **0.8.0**: Settings → Your data: export everything as a JSON file (the share sheet on Android, Downloads on desktop, a download on the web), delete your account and its cloud data for good, or erase this device and start over.
- **0.7.0**: Tasks have a time: open one and pick At 9:00, 12:00, 15:00 or 19:00 and nudge it by 15 minutes; the card shows the time and whether a reminder is set.
- **0.6.1**: The Buddy widget keeps its idle animation while it reacts to a pet or a berry (it used to freeze on one picture until the emoji went away), and a still buddy (Reduce motion, during focus) stands on its shadow instead…
- **0.6.0**: Task reminders: open a task with a day and pick up to three under Remind me (at the time, 10 minutes, an hour or a day before).
- **0.5.0**: Pokémon evolve at their real game level (Charmander at 16, Charmeleon at 36) instead of half of it.
- **0.4.0**: Every finished task now pays Rare Candies; a Super Candy bonus comes on top (10% for rank E up to 40% for rank A, raised by the Super Candy chance perk) instead of replacing them.
- **0.3.1**: Fix: the Buddy widget froze on a still picture after a pet or a berry (and the Today and Focus widgets could show old data for a while).
- **0.3.0**: Dark mode: Settings has Match phone (the default), Light and Dark; the dithered page background turns deep navy and the Android navigation bar icons follow.
- **0.2.0**: The Focus widget shows a live countdown; the Today widget has a "+ Add" button; long-press the app icon for "Add task" and "Start focus" shortcuts; the app is single-instance so a widget tap or shortcut never opens a…
- **0.1.0**: The first private build.

# Decision records: composeApp

Moved here from code comments in the comment audit (21 November 2026). Each entry is a decision, rule or constraint that
the code alone does not show. Entries are grouped by source file; a line saying "Documented in" means the owning doc
already holds the rule and the entry only points to it.

## `composeApp/build.gradle.kts`

### Story ground maps bundled as a subset

Only the Mystery Dungeon ground maps that story scenes are set on (the places of Red Rescue Team and Explorers of Sky, `storyRescueMaps`) are bundled into the Compose resources, not all 685, because each is up to a few hundred KB.

### Berry and Mail item icons not bundled

The Berry and Mail icons under `assets/items` are fetched for REP-053 (Berry plots) but no screen uses them yet, so `prepareComposeAssets` excludes `*-berry.png` and `*-mail.png` from the bundle.

### Black/White animations bundled only as fallback

The Black/White animation sheets are read only for a form SpriteCollab has no sprite for, because the Mystery Dungeon sheet wins. So `prepareComposeAssets` bundles only the animations of forms missing from `assets/pmd/pmd.tsv`.

### AppVersion generated from VERSION

Documented in `docs/code-map.md composeApp`.

### zxing is a test-only dependency

`com.google.zxing:core` is a dependency only so tests can read the QR generator's codes back with an independent decoder.

### ScreenshotTest skipped by jvmTest

`ScreenshotTest` renders every screen (about 12 of the full gate's 15 minutes), so `jvmTest` skips it unless a test is named with `--tests` or `-Pscreenshots` is passed (CI does). Name the screens a change touches, for example `--tests "*ScreenshotTest.collection*"`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/AnimatedSprite.kt`

### Mystery Dungeon sprites share one pixel size

`pixelScale` fits a 32 pixel frame (`PMD_REFERENCE_PIXELS`; seven in ten sheets are this size or smaller) into the space for Mystery Dungeon sprites, as the games do, so a Weedle stays smaller than a Snorlax and only bigger sprites step down to fit.

### Animation source precedence

`animations()` reads the Black/White table first and the Mystery Dungeon (SpriteCollab) table on top of it, so the Mystery Dungeon style sprite wins where one exists.

### SpriteAction plays and stand-in

Each `SpriteAction` is one SpriteCollab animation folder under `assets/pmd`. `plays` is how many times a one-off runs before it hands back to idle, and 0 loops it. `stand` names the action used when the form lacks that row.

### Side-facing sprite rows face right

The side rows of a SpriteCollab sprite (`WALK_SIDE`, `IDLE_SIDE`) face right, and the UI mirrors them for left.

### No non-shiny stand-in for a shiny

`actionAnimation` returns a shiny form's action only if the shiny recolour exists, because the regular colours on a shiny would look like a different Pokémon for the length of the action.

### AnimatedSprite action contract

`AnimatedSprite` plays the `action` animation at the idle animation's size. `SLEEP` loops. A one-off (`HOP`, `EAT`) runs its `plays` and then calls `onActionDone`, and a new `actionKey` plays it again. A form without that animation stays idle and calls `onActionDone` at once. With `animate` off (Reduce motion) it shows the first frame.

### Sheet state is paired with its name

`AnimatedSprite` keeps a loaded sheet as a pair with its name, so a sheet still being read can be told from one that is missing.

### Sheet state is replaced when the name changes

The sheet state is reloaded whenever the sheet name differs, not only when empty, because after an evolution the same sprite shows another form and the held sheet is the old form's.

### Only the pick for the current request is used

`AnimatedSprite` uses a stored pick only if it matches the form, shiny flag and action asked for now, because the previous pick stays in the state for a moment after a change.

### Empty box while the animation sheet loads

While the animation sheet is being read, `AnimatedSprite` draws an empty box rather than the still sprite, because the still sprite is in another art style and would flash first.

### Actions draw at the idle sprite scale

Every `SpriteAction` draws at the idle sprite's scale, so the Pokémon never changes size when it lies down, eats or hops. Hop frames are taller than idle ones (they hold the jump), so the top of a jump may reach above the box.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/App.kt`

### Place names are fixed for external entry points

The `Place` names ("PLANNER", "FOCUS", "CARE") are named by widgets, shortcuts and notifications, so they must keep their spelling when screens move.

### HealStore pays a minimal event

Documented in `docs/design.md A module (minimal events)`.

### MintStore pays a minimal event

Documented in `docs/design.md A module (minimal events)`.

### LinkStore pays a minimal event

Documented in `docs/design.md A module (minimal events)`.

### Module screen-lock timestamps

`moduleUnlockedAt` and `moduleLeftAt` hold, by module name, when each module behind the screen lock was last opened with it and last left (epoch millis). They are used by `ScreenLockGate` in `screens/ModuleGate.kt`.

### Individual cry detune

Documented in `docs/pokelife/MODULES/WORLD.md 4.4 Story runtime (CryVoice)`.

### Cry gain is shown against the loudest

`AppModel.cry` shows a voice's gain against `CryVoice.LOUD`, because the player has no headroom above full volume. A default cry in a story therefore plays a sixth under the Sound level and a loud one at it.

### UI tap gain

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md UI tap sounds`.

### mintStartTo is read once

`AppModel.mintStartTo` holds the account a "Pay card" action asked the Activity screen to send money to. The Activity screen reads it once and then clears it.

### linkPersonTab indexes

`AppModel.linkPersonTab` is the tab index of a Link person screen: 0 Overview, 1 Profile, 2 Notes, 3 Follow-ups.

### Quick-add drafts live in memory only

`AppModel.quickAddDrafts` keeps the unsent words of each quick-add box, keyed by where it files, while the player visits another screen and returns. They are in memory only, never saved.

### Calm break survives leaving the screen

`AppModel.calmBreakStartedAt` and `calmBreakSeconds` hold a calm break in progress (start in epoch millis, length in seconds) on the model, so leaving Care's screen for a moment does not lose it.

### addTaskRequested

`AppModel.addTaskRequested` is set by the widget's "+" and the app icon's "Add task" shortcut. The Planner then opens on Today with the keyboard up.

### sharedText is typed in but not added

`AppModel.sharedText` is text another app shared to PokéLife. The Planner's quick add opens with it typed in but does not add it until the player says so. It is used once.

### openPokemonId is used once

`AppModel.openPokemonId` names a Pokémon the Pokémon tab should open on (the buddy's level chip on Home, the widget's chip). It is used once.

### openParty is used once

`AppModel.openParty` is set by the "Party" shortcut so the Pokémon tab opens on the Party page. It is used once.

### reflectJournal is read once

`AppModel.reflectJournal` is set by Care's "Write" quick action so Reflect opens on the journal instead of the diary card. It is read once and then cleared.

### worldTeamDungeon is UI only

`AppModel.worldTeamDungeon` is the dungeon the World team editor is choosing a team for. It is UI state only, never saved. Null means the team's own dungeon, else the next one to explore.

### Journal draft is not saved

`AppModel.journalDraft` keeps the Care journal entry being written while the player looks elsewhere in the app. It is not saved to disk until saved as an entry.

### worldNow

`AppModel.worldNow` is the clock World's screens read. It is the store's clock, refreshed every few seconds while a World screen is open, so lists that depend on the day or a brew follow it.

### playedHatches is per run

`AppModel.playedHatches` holds the ids of Pokémon whose hatch already played in this run, so one still waiting for its OK plays only once.

### celebratedAt

`AppModel.celebratedAt` records when the buddy last celebrated an event, so opening Home again does not repeat the celebration.

### Reminder Done refreshes module records

`AppModel.tick` calls each module store's `sync()` because a reminder's "Done" may have changed a module's records while the app is open. The game's own save is picked up in `store.tick()`.

### tickClock settles nothing

`AppModel.tickClock` only moves the clock. It is for the launcher, which shows the time and settles nothing because the app owns the save.

### A linked task is a projection

Documented in `docs/pokelife/PRODUCT_BIBLE.md 4 Canonical module map and ownership`.

### focusTab is false on desktop and web

`App`'s `focusTab` is false on desktop and web, because strict focus makes no sense there: people work in other windows.

### Heal change callback leaks nothing

`onHealChanged` receives the settings' reminder times only through the data. Nothing else leaves Heal.

### Mint change callback is local

`onMintChanged` is for the host's own reminder code only, which looks inside the records. Nothing is sent anywhere.

### Link change callback is local

`onLinkChanged` is for the host's own reminder code only, which looks inside the records. Nothing is sent anywhere.

### Initial data is reported too

`App` calls `onDataChanged(store.data)` once at start as well as after each change, because the state the app starts in counts too: a focus session already running keeps the screen on.

### Layout breakpoints

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md Layout breakpoints`.

### System bars match the header

The header colour continues under the status bar, and the tab bar wears it under the system navigation bar, so the system bars match the app instead of showing the page colour. The keyboard lifts the whole column. Wide windows keep the page a phone-width column and move the tabs to a side rail.

### Tabs keep their scroll position

The tabs crossfade in `ScreenSwitch`, and each keeps its scroll position while another tab is open.

### Feedback and undo bars stack

`FeedbackHost` and `UndoBar` are stacked in one column, so neither hides the other.

### Tab label text scale capped at 1.25x

The tab labels cannot grow with the system text size without being cut off, so their text scales up to 1.25x only, and the icons and touch targets carry the rest.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/AppBackdrop.kt`

### Page backdrop sits under a veil

`AppBackdrop` draws a scene or dungeon town behind every screen under a veil of the page colour, so the text on top stays readable.

### Long background groups start closed

Background choice groups start closed unless the chosen background is in them, because only an open group draws its pictures.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/BackHandler.kt`

### App-wide BackHandler wrapper

Compose's own `BackHandler` is deprecated in favour of `NavigationBackHandler`, which needs a state object. The app's `BackHandler` wraps it and keeps the old call shape `BackHandler(enabled) { ... }` so every screen stays a one-line call.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/Background.kt`

### Background cache key may change

In `PixelBackground` the `name` key may change under the same composable, so the loader starts from what is cached for the new name and never shows the old name's image.

### Wallpaper frame is cropped

The box wallpapers carry a 2 pixel frame. `PixelBackground` drops it so the tiles run together, and mirrors every other tile.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/BackupAccess.kt`

### Automatic backups

The app takes one automatic backup a day and keeps the newest three (`BackupAccess.automatic`).

### Restore is staged

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.5 Restore`.

### Portable passphrase backup

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.7 Automatic backup off the phone`.

### eraseLeftovers scope

A full-device erase must also remove what the stores do not write blank: the daily backups, the copies a migration or restore kept aside, held reminders, and the old `save.json` and `care.json` rollback files. `BackupAccess.eraseLeftovers` does this and is a no-op where the platform has none.

### Off-phone backup passphrase

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.7 Automatic backup off the phone`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/BitmapCache.kt`

### BitmapCache eviction rules

`BitmapCache` holds decoded pictures by name up to `maxBytes` of pixels (width x height x 4). Beyond that the least recently used are dropped and re-read on demand. A missing name is cached as null so it is not re-read every frame. The newest picture always stays even if it alone exceeds the limit. It is for the UI thread only.

### Per-kind cache budgets

`CacheBudget` gives each kind of picture its own cache budget, so a big scene cannot push every sprite sheet out.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/Calendars.kt`

### CalendarSource is Android-only and blocking

`CalendarSource` reads the phone's calendars read-only (Google Calendar's among them once its account syncs on the phone). Only the Android app provides one. The reads can take a moment, so callers run them off the main thread.

### Calendar events filtering

`CalendarSource.events` returns events of the given calendars that touch the days from to to, with recurring ones expanded. Declined and cancelled ones are left out.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/ContactSource.kt`

### ContactSource scope

Documented in `docs/pokelife/MODULES/LINK.md 3.5 Import device contacts`.

### Contacts permission only on import

Documented in `docs/pokelife/MODULES/LINK.md 3.5 Import device contacts`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/Cries.kt`

### CryPlayer platforms

Each platform provides its own `CryPlayer`: Android uses MediaPlayer and the web an audio element. The desktop build has none, because the JVM cannot play Ogg without extra libraries.

### CryPlayer never throws

`CryPlayer.play` and `effect` never throw: a missing file is silent.

### playVoiced default

`playVoiced` has a default that plays the cry as recorded, so `play` stays the one method every player must implement. A player that cannot shift pitch or speed uses it.

### Evolution clip

`Sfx.EVOLUTION` is the first 6.5 seconds of the games' evolution music, faded out ("What? it is evolving"), not the healing jingle. It streams.

### Notification sound is not played by the cry player

The notification sound is not played through `CryPlayer`: the notification channel needs it as `res/raw/pokelife_notify.ogg` (the same clip as `assets/sfx/reminder.ogg`). `assets/sfx` also keeps pokemon-center, drawer-open, drawer-close and timer-done for later, and the build does not bundle them.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/CryVoice.kt`

### CryVoice pitch, speed and gain

Documented in `docs/pokelife/MODULES/WORLD.md 4.4 Story runtime and presentation`.

### Gain is shown against LOUD

A `CryVoice.Voice` gain is relative to a cry at its default level (1; `Condition.LOUD` is 1.2). The player has no headroom above full volume, so `AppModel.cry` shows a gain against `CryVoice.LOUD`.

### Cry loudness levels

Documented in `docs/pokelife/MODULES/WORLD.md 4.4 Story runtime and presentation`.

### Cry distance

Documented in `docs/pokelife/MODULES/WORLD.md 4.4 Story runtime and presentation`.

### Voice stays within 15 percent

Documented in `docs/pokelife/MODULES/WORLD.md 4.4 Story runtime and presentation`.

### Individual detune

Documented in `docs/pokelife/MODULES/WORLD.md 4.4 Story runtime and presentation`.

### Mood voices

Documented in `docs/pokelife/MODULES/WORLD.md 4.4 Story runtime and presentation`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/DayBackground.kt`

### DayBackground palette rule

DayBackground paints the page behind every screen as a pixel-dithered gradient whose hue follows the time of day (peach dawn, mint morning, cream afternoon, rose dusk, blue-grey night). The light tones stay pale so the dark text on them is always readable; only the mood changes. In dark mode the same hues are pulled down to deep navy so the light text stays readable.

### DayBackground dark mode has no dither

On the dark page DayBackground draws a smooth, quiet gradient instead of the Bayer dither, because the ordered dither reads as noise against a dark ground; the dither belongs to the light page only.

### DayBackground bitmap cache key

The dithered bitmap is cached under a key built from the rounded top and bottom colours, because the colours drift slowly through the day; rounding keeps a handful of pictures (the cache is cleared past six) instead of one per minute.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/DeviceGate.kt`

### DeviceGate is only a door

Documented in `docs/pokelife/MODULES/MINT.md 13.2 Access protection`.

### DeviceGate.available

Documented in `docs/pokelife/MODULES/MINT.md 13.2 Access protection`.

### DeviceGate.leftAt

DeviceGate.leftAt returns when the app last went out of sight in epoch milliseconds, and 0 when it has not since the app started; callers compare it with the clock and the grace period, so 0 means no departure to measure.

### DeviceGate.GRACE_MILLIS

Documented in `docs/pokelife/MODULES/MINT.md 13.2 Access protection`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/Ease.kt`

### Ease curves

Ease is the app's one set of easing curves and the platform default easings are not used. They are tuned by feel, so a change is made in Ease alone and everything follows. Smooth is for almost everything, Out for decorative entrances, Spring for things that pop (a tiny overshoot) and InOut for symmetric moves and loops.

### Modifier.entrance has no blur

The entrance effect (rise 6 dp while fading in over 280 ms on Ease.Smooth) deliberately uses no blur, because the art is crisp pixels and a blur would smear it. It happens at once under Reduce motion.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/Feedback.kt`

### Feedback bar

The app tells the player how something went (saved, earned, did not work) in one place: a single line in one bar pinned above the tabs (FeedbackHost), the same on every screen. A screen says it once with AppModel.say when it happens, or keeps it up while it holds with Report, and never draws its own message box. Rich reward moments such as an egg hatching or a level up keep their own scenes; the bar is for the sentence that goes with an ordinary action.

### Feedback bar shows one line

FeedbackHost shows at most one line at a time, the newest, but an error is not replaced by a plain "Saved." arriving at the same moment.

### Feedback sticky and timed lines

A sticky feedback line is taken down by whoever put it up (Report on dispose); a timed one takes itself down after its kind's duration. The serial number identifies the line so a timer cannot remove a newer one that replaced it.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/FileSaver.kt`

### FileSaver.save return value

FileSaver.save and ImageViewer.share return a short human-readable string: a note on what happened, or an error message in words. The caller shows it to the player as is; there is no separate error channel.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/Foreground.kt`

### rememberInForeground

A stopped Android Activity keeps its composition, so a `while (true)` loop inside a LaunchedEffect would keep waking the CPU in the background. A loop that only paints or polls for the screen must therefore run while rememberInForeground is true.

### rememberRunningSeconds

rememberRunningSeconds advances one frame callback at a time and none while not running, carrying on from where it stopped. Read its value inside a draw or layout block (`{ seconds.value }`) so each frame redraws or re-lays-out instead of recomposing the whole screen.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/Integrations.kt`

### HealthSource

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.1 Health Connect`.

### HealthSource.granted

HealthSource.granted reports the record types Android currently allows the app to read; it is rechecked on every call and never remembered by the app, because the player can revoke access in Android settings at any time.

### HealthSource.request

HealthSource.request opens Android's own permission screen. Android may show it only a limited number of times, so a request can return without the screen appearing; done receives the kinds now allowed either way.

### HealthSource.read

HealthSource.read is read-only and fetches at most the last 30 days of the chosen record kinds; done gets the records, or null and a reason in words.

### HealthSource.revoke

HealthSource.revoke takes back every permission the app holds in Health Connect, but records already imported into HEAL stay.

### ImageChooser

Documented in `docs/pokelife/MODULES/MINT.md 4.2 Account fields`.

### ImageViewer

Documented in `docs/pokelife/MODULES/MINT.md 4.2 Account fields`.

### AppLauncher

Documented in `docs/pokelife/MODULES/MINT.md 4.3C Open the wallet's own app`.

### HuaweiReaderState

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2 Reading Huawei Health on the phone`.

### HuaweiReaderState.canSwitchItself

HuaweiReaderState.canSwitchItself is true only when screen reading can be turned on for a single read without opening Android's settings, which needs WRITE_SECURE_SETTINGS granted over adb; otherwise the player turns it on by hand in Accessibility settings.

### HuaweiReader

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2 Reading Huawei Health on the phone`.

### HuaweiReader.walk

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2 Reading Huawei Health on the phone`.

### HuaweiReader.openAppInfo

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2 Reading Huawei Health on the phone`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/Motion.kt`

### PhoneState

Documented in `docs/design.md Focus (strict mode: locking the screen does not count as leaving)`.

### PhoneState.exactAlarms

PhoneState.exactAlarms is false when the phone does not let the app set exact alarms, so reminders and timers can arrive minutes late; Settings then tells the player. It defaults to true on platforms without the restriction.

### PhoneState.call

PhoneState.call only opens the dialer with the number typed in; it never places the call, the player does. Message and email likewise only open the composer.

### PhoneState.setAlarm

PhoneState.setAlarm hands a wake-up alarm for the given hour and minute to the phone's own clock app, which shows it for the player to keep. It then rings with the phone's alarm sound, even in Doze or silent mode, which an in-app reminder could not do.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/Portrait.kt`

### Emotion.stand fallback faces

Emotion.stand lists the faces to try in turn when a form has no portrait for the requested emotion, because about a third of forms have only a Normal face; the last resort is Normal.

### portraitCredit

portraitCredit returns who drew a form's SpriteCollab portraits, because the CC BY-NC 4.0 licence asks for credit; it is null when the form has no portrait.

### Portrait shiny rule

A shiny shows only its own SpriteCollab recolour; the regular colours would be a different-looking Pokémon, so a shiny without its own portrait falls back to the still Sprite.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/RewardText.kt`

### Acquisition kinds

How a new Pokémon came to the player decides the scene and the words (owner, 8 November 2026: not everything is an egg). Only Plan's eggs, a visitor's gift and anything unrecognised hatch from an egg; Care's friends wander over, Heal's are found on a route, Mint's are catalogued by Ledger Research, Base's settle into the habitat and Link's come by postcard.

### acquisitionOfSource

acquisitionOfSource infers the acquisition from a reward-log source text because the log keeps no Origin beside it: "Care: a routine" arrives, "Care: made a friend" is an egg, and the Heal and Mint log prefixes map to field and ledger. Changing a source string changes the scene shown.

### Reward.describe source

Reward.describe writes one short line per reward in the voice of the old games' text boxes. It takes the reward log entry's source so that what World gives (a recruit, a legendary) is not said to hatch.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/Scene.kt`

### isGroundMap

isGroundMap is true for a background that is a ground map of the Mystery Dungeon games ("map:" names) or a Friend Area, which are pixel art with a grid of their own, as opposed to a stock scene photo; callers draw them crisp and not smoothed.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/Sprite.kt`

### orNull

orNull returns null when a load fails (missing or broken file) but passes a CancellationException on. If it were caught, a page left before its sprite was read (Next in the Pokédex) would cache that sprite as missing for good.

### Glyph

Documented in `docs/pokelife/FEATURES/ASSETS_AND_AUDIO.md 4.4 Icons and non-Pokémon UI`.

### Sprite art source

Documented in `docs/pokelife/FEATURES/ASSETS_AND_AUDIO.md 4.0 Which art goes where`.

### Sprite produceState seed

Sprite seeds its produceState from the animation and sprite caches so that a list being scrolled does not flash the still sprite before the Mystery Dungeon sprite arrives.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/Torch.kt`

### Torch

Documented in `docs/pokelife/MODULES/GEAR.md 5.8 Flashlight / Screen Light`.

### Torch.set

Torch.set returns whether the light is on afterwards rather than echoing the request, because the camera may be in use by another app and the light then stays off.

### Torch.observe

Torch.observe calls its listener on the main thread whenever the light goes on or off, whoever did it (another app or the system as well), and returns a function that stops listening.

### Torch.release

Torch.release turns the light off only if this app turned it on; a light another app switched on is left alone. It is called when the tool or the app is left.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/UiSound.kt`

### UI tap sound

Documented in `docs/pokelife/FEATURES/ASSETS_AND_AUDIO.md 6.1 Audio categories`.

### TAP_GAIN

TAP_GAIN is 6% of the Sound effects volume because the owner found 10% still loud and 3% not audible at all; 6% is heard in a quiet room and never over a game sound. AppModel.tap is the only place it is applied.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/base/Base.kt`

### Base screens

Documented in `docs/pokelife/MODULES/BASE.md 11 Visual presentation`.

### tickChore undo

tickChore marks a chore done from any list and offers to take it back, because a tick on the overview or on an overdue chore leaves no row in the list from which to undo it.

### tickMaintenance undo

tickMaintenance records a schedule's work as done and offers to take it back: the record goes and the schedule returns to its date, and nothing is paid twice.

### baseSubtitle privacy

The line under the household card in Modules shows how much waits today, or what the module is for, and never a private word of the household, such as a chore or item name.

### BaseOverview

Documented in `docs/pokelife/MODULES/BASE.md 3.1 Overview`.

### Quick actions placement

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.1`.

### Supply quantity unit

A quantity of exactly one drops the trailing s of a plural unit ("1 bottles" reads wrong) only when the unit is longer than three characters, so short units such as kg or L, which have no s to drop, are left alone.

### inDueTab

The Due tab lists what waits plus chores done or skipped today, so a tick can be taken back on the screen where it was made.

### Chore link to Plan

Documented in `docs/pokelife/MODULES/BASE.md 2.1 BASE versus PLAN`.

### warranty edit state

In the asset detail screen the `warranty` state holds the id of the warranty being edited, the empty string for a new one, and null when none is being edited.

### Habitat Discovery help

Documented in `docs/pokelife/MODULES/BASE.md 9.2 Qualifying activities`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/base/BaseRoom.kt`

### Secret Base room view

Documented in `docs/pokelife/MODULES/BASE.md 9.5.1 What the room is`.

### SPRITE_BOX_PX

SPRITE_BOX_PX is the box a Pokémon is drawn in inside the Secret Base room: a frame of up to 48 art pixels, and the Pokémon stands on the bottom edge of the box.

### roomLook

Documented in `docs/pokelife/MODULES/BASE.md 9.5.3 The room: grid and looks`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/care/Care.kt`

### Care sections

Documented in `docs/pokelife/MODULES/CARE.md 3.1 Today and routines`.

### Care navigation back

In Care the header stays put and only the page under it changes; Back from a place goes to Today, and Back from Today goes to the module list.

### Care Today screen order

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.1`.

### Quick actions then party

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.1`.

### Care quick actions

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.1`.

### ReflectView

Documented in `docs/pokelife/MODULES/CARE.md 3.3 Reflect`.

### CareBuddy

Documented in `docs/pokelife/MODULES/CARE.md 3.1 Today and routines`.

### RoutineLine day

RoutineLine takes the day it is ticked for, which is today or yesterday; ticking yesterday only records the routine and never pays.

### Routine payment help

Documented in `docs/pokelife/MODULES/CARE.md 3.1 Today and routines`.

### Routines Yesterday tab

The Routines list can tick yesterday for a day that slipped by; it only records the routine and never pays.

### Calm break left open

A calm break whose elapsed time exceeds its length by more than 600 seconds (the screen was closed and reopened much later) is dropped and not paid, instead of paying a break that was not actually done.

### Calm break payment help

Documented in `docs/pokelife/MODULES/CARE.md 3.2 Calm`.

### Diary card day

The diary card can be opened for any day up to today, so a forgotten day can be filled in and an earlier card corrected.

### Diary card urges

Taking an urge off the diary card is rare and asks first; cards already saved keep that urge.

### Care screens redraw on careData

A bare read of `model.careData` in a composable subscribes it to Care's observable state, so the screen redraws when Care changes; the `model.care` store queries used beside it read plain fields and would not trigger a redraw on their own.

### Skills help text

Documented in `docs/pokelife/MODULES/CARE.md 3.2 Calm`.

### SkillGuide

Documented in `docs/pokelife/MODULES/CARE.md 3.2 Calm`.

### Care screens redraw on careData

A bare read of `model.careData` in a composable subscribes it to Care's observable state, so the screen redraws when Care changes; the `model.care` store queries used beside it read plain fields and would not trigger a redraw on their own.

### Journal draft in the model

The half-written journal entry lives in the app model (model.journalDraft), not in the screen, so leaving the screen or a tab and coming back finds it still there.

### Journal draft persistence

The journal draft is also saved to Care's own save about 1.2 seconds after the player stops typing, so it survives the app being closed.

### Journal writtenAt

In the journal screen `writtenAt` is null when the entry was written just now; otherwise it holds the chosen moment of the entry.

### Care trends in Me

Documented in `docs/pokelife/MODULES/CARE.md 3.3 Reflect`.

### Care screens redraw on careData

A bare read of `model.careData` in a composable subscribes it to Care's observable state, so the screen redraws when Care changes; the `model.care` store queries used beside it read plain fields and would not trigger a redraw on their own.

### Care screens redraw on careData

A bare read of `model.careData` in a composable subscribes it to Care's observable state, so the screen redraws when Care changes; the `model.care` store queries used beside it read plain fields and would not trigger a redraw on their own.

### Mood row box

The mood row uses the same dialogue box as the Quick actions box under it (a TextBox), because a form box sits 16 dp inside its frame and made this row look tighter than its neighbours.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/care/CareDream.kt`

### Dream view

Documented in `docs/pokelife/MODULES/CARE.md 5 Dream`.

### Dream visit versus Care Play

In Dream a dreamer (a visitor) is visited or played with right on the scene (the visit scene), while Care Play, below it, is for Pokémon the player owns.

### CareDream timeWith state

In the Care Dream screen, `timeWith` is null while Care Play is closed. Otherwise it holds the id of the Pokémon Care Play opens on, and the empty string means the buddy.

### Dream spot coordinates

The four Dream spots for dreamers are fixed positions given as shares of the scene (x, y of the sprite's bottom centre), not pixels. The buddy always takes the centre.

### Spend time opens Care Play in place

Documented in `docs/pokelife/MODULES/CARE.md 5 Dream`.

### DreamersCard rules

Documented in `docs/pokelife/MODULES/CARE.md 6 Visitors`.

### Sending a dreamer away

Documented in `docs/pokelife/MODULES/CARE.md 6.4 Retention, departure and dismissal`.

### PlayView and Care Play

Documented in `docs/pokelife/MODULES/CARE.md 4 Care Play`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/CalcEngine.kt`

### CalcEngine rules

The Pokégear calculator reads `+ - × ÷ ^`, brackets, a unary minus, `%`, `!`, scientific functions, `pi`/π and `e`. A percent after a number alone is a hundredth (`50%` is 0.5); after `+` or `-` it is that share of what came before (`200 + 10%` is 220), as on a shop calculator. Numbers are Double and the answer is shown rounded to 12 significant digits (so `0.1 + 0.2` shows 0.3); that is the precision policy and nothing claims more. Errors (divide by zero, domain errors such as `sqrt(-1)`, overflow, unreadable input) are reported in words and never produce NaN or Infinity as an answer.

### CalcEngine formatSignificant exponent fix

log10 can round up just below a power of ten (999.9999999999999 gives 3), which leaves the mantissa one digit short. The formatter then takes the mantissa again one exponent lower.

### CalcEngine expression percent flag

`expression()` returns the value and a flag saying the last term was a bare percentage, so that `+ 10%` or `- 10%` means a share of what came before rather than a hundredth.

### CalcEngine implicit multiplication

A number straight before a bracket, a constant name or a function is a product: `2(3)` and `2pi` multiply.

### CalcEngine exponent literals

A number like `2e3` is read as an exponent only when digits follow the `e`, so `2e` stays the product of 2 and the constant e.

### CalcEngine trigonometric zero

`sin(180°)` is about 1.2e-16 in floating point, so a trigonometric result with magnitude below 1e-14 is shown as exactly 0.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/Capabilities.kt`

### Capability detection

What a Pokégear tool needs from the device is found out at run time on Android and never inferred from the phone model's name. Every other platform reports none, so a tool that needs one says it is not available there instead of faking a reading.

### PermissionNeed asked only on use

Documented in `docs/pokelife/MODULES/GEAR.md 4.1 Shared platform behavior`.

### CapabilityStatus.granted

`CapabilityStatus.granted` is null when the capability needs no permission, true or false otherwise; `usableNow` is false only when it is explicitly false.

### needsAsking at tool open

Documented in `docs/pokelife/MODULES/GEAR.md 4.1 Shared platform behavior`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/Currencies.kt`

### Currency names bundled

Documented in `docs/pokelife/MODULES/GEAR.md 5.3 Currency Converter`.

### Currencies.rate manual rates

A rate the player typed by hand wins only when one of the two currencies is the home currency and the other has a manual rate; a manual rate is units of that currency per one unit of home. Otherwise the reference rates through the dollar are used. The result is null for an unknown currency or a rate that is not a positive finite number.

### Travel.estimate inputs

`referenceRate` is foreign units per one home unit; `quotedRate` is the rate the player was offered in the same unit (null uses the reference). The home cost at the quoted rate gets `feePercent` on top and then `feeFixed` in home currency. The reference rate is only an estimate, since banks, cards and changers each use their own.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/Everyday.kt`

### Everyday.savings contribution timing

`Everyday.savings` grows the start amount at the annual percent compounded `perYear` times a year, and adds `addEach` at the end of every period.

### Everyday.money formatting

`Everyday.money` always shows two decimals without a locale, because the web target has no number formatting to rely on.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/Gear.kt`

### Gear helpers are visual only

Documented in `docs/pokelife/MODULES/GEAR.md 6 Helper Pokémon`.

### Choosing a helper moves it

Documented in `docs/pokelife/MODULES/GEAR.md 6 Helper Pokémon`.

### Gear reactionFor

`reactionFor` returns a pair: (true, item id) when the reaction picture is an item icon, or (false, Pixelarticons glyph name) when it is a glyph.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/GearCalculator.kt`

### Calculator behaviour

The Pokégear calculator shows the result as the player types, but an error only after `=`, and says what is wrong. The calculator history is off until the player turns it on and stays on the device. Nothing in it pays or records anything else.

### Typing does not move the helper

Documented in `docs/pokelife/MODULES/GEAR.md 6 Helper Pokémon`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/GearClock.kt`

### World clock conversion date

The World clock converts a typed time using today's date, so the answer follows the summer time in force now rather than a fixed offset.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/GearCurrency.kt`

### Currency tool rates and home currency

Documented in `docs/pokelife/MODULES/GEAR.md 5.3 Currency Converter`.

### Failed fetch keeps cached rates

Documented in `docs/pokelife/MODULES/GEAR.md 5.3 Currency Converter`.

### Currency tool refetch window

The reference rates change once a working day, so a copy fetched within `RATES_REFETCH_MS` (6 hours) is not fetched again on every opening of the tool; Refresh always asks.

### One active currency input

Documented in `docs/pokelife/MODULES/GEAR.md 5.3 Currency Converter`.

### Currency fee input

A fee field whose text is not a valid non-negative number is reported as such and leaves the saved fee as it was; it is never turned into 0.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/GearEveryday.kt`

### Everyday sums input

Text in an Everyday sums field that is not a number is reported under the field and gives no answer; it is never read as 0 or as the field's default.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/GearField.kt`

### Waypoints read one fix

Documented in `docs/pokelife/MODULES/GEAR.md 5.15 Waypoints`.

### Sound meter level floor

Readings at or below -119 dB full scale (silence is -120) are left out of the quietest, average and loudest, so changing the offset moves those figures together with the live reading.

### Sound meter spectrum bands

The spectrum is drawn as 24 bands whose bin ranges follow a squared curve, so the low pitches are not squashed into a few pixels.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/GearLight.kt`

### Flashlight SOS limits

The SOS signal only starts on request after a warning, only on the torch, never with Reduce motion on, never faster than one change in 250 ms and for at most five minutes (`Sos.MAX_MS`). Leaving the flashlight tool turns the torch off, and the torch state shown is the hardware's, so another app switching it shows here.

### Torch state follows hardware

Documented in `docs/pokelife/MODULES/GEAR.md 5.8 Flashlight / Screen Light`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/GearRandom.kt`

### Picker skip last pick

The pick to skip is read before the roll animation starts, because the animation puts other names on screen (`shown`) and would change what counts as the last pick.

### Coin flip animation target

The coin turns four whole turns from the last whole turn, plus a half turn for tails, so it always ends showing a face rather than on its edge.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/GearTime.kt`

### Gear timers versus Plan timers

Documented in `docs/pokelife/MODULES/GEAR.md 8 Data ownership and relationships`.

### Timer tool tick loop

The timer face ticks twice a second only while the app is in the foreground, because a stopped Activity keeps its composition; the timers' alarms ring on their own, and a timer that ran out is marked finished once.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/GearTools.kt`

### Pixel dropdown, not Material

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 4 Shape, border and surface language`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/Geo.kt`

### Geo accuracy

Distances and bearings treat the Earth as a sphere, which is good to about half a percent.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/Light.kt`

### Sos.MAX_MS

The SOS signal stops by itself after five minutes, so a forgotten SOS cannot drain the battery.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/Qr.kt`

### QR generator scope

The QR generator follows ISO/IEC 18004 in byte mode, versions 1 to 10 and error correction L, M, Q or H, written from the standard so the QR tool needs no library and works on every platform. It holds up to 271 bytes at level L (version 10) and the screen draws it with a quiet border.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/QrPayload.kt`

### QrPayload safety

Documented in `docs/pokelife/MODULES/GEAR.md 15 As built`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/Randomizer.kt`

### Randomizer is casual randomness

Documented in `docs/pokelife/MODULES/GEAR.md 5.10 Coin & Dice`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/Rates.kt`

### Frankfurter rates client

Documented in `docs/pokelife/MODULES/GEAR.md 15 As built`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/Sensors.kt`

### SensorFeed listening

Documented in `docs/pokelife/MODULES/GEAR.md 4.1 Shared platform behavior`.

### SensorMath.Tilt signs

`SensorMath.Tilt` is in degrees from gravity: `pitch` is positive when the top edge is up and `roll` is positive when the right edge is down.

### Barometer altitude is a guide

Documented in `docs/pokelife/MODULES/GEAR.md 5.13 Barometer`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/Services.kt`

### LocationFeed never tracks

Documented in `docs/pokelife/MODULES/GEAR.md 5.15 Waypoints`.

### SoundFeed lifetime

Documented in `docs/pokelife/MODULES/GEAR.md 4.1 Shared platform behavior`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/SoundMath.kt`

### Sound level is an estimate

Documented in `docs/pokelife/MODULES/GEAR.md 5.16 Sound Meter`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/TimeFormat.kt`

### formatTimer rounding

The timer shows whole seconds rounded up, so a timer with 400 ms left shows 0:01 and 0:00 only means it has ended.

### formatStopwatch truncation

The stopwatch cuts to hundredths instead of rounding, so the shown time never runs ahead of the real elapsed time.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/gear/UnitCatalog.kt`

### UnitInfo id and conversion functions

`UnitInfo.id` is `category:symbol`. It is what favourites save, so it must stay stable and never be a translated label. `toBase` and `fromBase` are functions, not factors, because temperature and fuel economy are not a simple multiple of a base unit. `note` flags units that are easy to mistake (US gallon vs imperial gallon, decimal vs binary kilobyte).

### UnitCatalog volume and weight

Documented in `docs/pokelife/MODULES/GEAR.md 5.4`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/heal/Heal.kt`

### Heal subtitle shows a count, never a score

Documented in `docs/pokelife/MODULES/HEAL.md 2.2`.

### Today card: dash not zero, no score

Documented in `docs/pokelife/MODULES/HEAL.md 2.2`.

### Quick actions sit between overview and party

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md module main screen order checklist`.

### Field Research minimum session length

Documented in `docs/economy.md Heal: activity logged`.

### Hydration entry time default

In the hydration form a null time means the drink is stamped now when logging for today and at noon when logging for an earlier day, since no real time of day is known for a backfilled glass.

### Readings are history only, never judged

Documented in `docs/pokelife/MODULES/HEAL.md 22B`.

### GoalKind title without bracketed unit

`GoalKind.title` drops the unit in brackets from the label because the dialogue font has no bracket glyphs, and a label containing one falls back to a different, bold font.

### Heal settings promises

Field Research can only come from a session of `HealRules.RESEARCH_MIN_MINUTES` or more just logged and from the day's first drink; weight, sleep and other numbers never earn anything. Heal never sets a water target; reminders are off by default, at most `HealSettings.MAX_REMINDERS` a day and silent in quiet hours. Erasing Heal records never touches Pokemon, Poke, the Bag, Plan or Care.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/heal/HealConnections.kt`

### Heal Connections flow

Documented in `docs/pokelife/MODULES/HEAL.md 22A`.

### Health Connect permission rechecked each visit and read

Documented in `docs/pokelife/MODULES/HEAL.md 22A`.

### Health Connect read scope and rewards

Documented in `docs/pokelife/MODULES/HEAL.md 22A`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/heal/HealHuawei.kt`

### Huawei probe polls every second

The Huawei probe rechecks the readers' state every second while the page is open, because both accessibility readers are switched in Android's settings and pages arrive while Huawei Health is in front, so no callback tells the page when to refresh.

### Huawei pages preview then pay like manual

Documented in `docs/pokelife/MODULES/HEAL.md 22A`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/BuddyWidget.kt`

### Buddy face size

The buddy's face is 40 art pixels drawn at twice the town's pixel scale so it stays crisp, and its frame hugs it, adding only the frame's own 3 art pixels of room above and below.

### Buddy name box height

The name box is given the frame's height (face plus 3 art pixels of room above and below) explicitly instead of using intrinsic sizes, because the text is measured lazily and intrinsic measurement would fail.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/DateText.kt`

### Numeric date style is ISO

The dialogue font has no slash glyph, so the numeric date style is ISO with dashes (2026-10-31) rather than a slashed format.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/DungeonFloor.kt`

### FloorTrap kinds are cosmetic

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Wonder Tile always shown

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Mover rules: swim, fly, ghosts through walls

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### DungeonFloor is seeded and draws nothing

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### restingCells only reachable squares

`restingCells` returns only squares the mover can both rest on and walk to from the stairs. A pocket it cannot leave (a lake in the rock for a swimmer, a hollow in a wall for a ghost) is never a place to start, be warped to or head for, or the Pokemon there would be stuck.

### Path cost: widgets and known traps are soft

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Path may start off the floor

`DungeonFloor.path` allows the start square to be off the floor, for example a wall a ghost is resting in, and always lets the Pokemon leave it.

### Gust trap landing square

A Pokemon blown by a Gust Trap flies in a straight line until the next square is a wall or water it cannot cross, holds another Pokemon, or `max` squares have passed, as in the games. It lands on the last square along the way where it can rest (not a trap, the stairs or under a widget), or stays at its origin if there is none.

### Organic autotile shapes vs 3x3 tilesets

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Room layout of the dungeon floor

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Walls at least two squares thick without autotiles

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Ponds kept off room edges

Ponds are only put in big rooms and kept off the room's edge so the room stays one connected piece; only a tileset with the full autotile set also gets lakes inside walls.

### Autotile neighbourhood bit layout

The autotile column for a wall or water square is picked from the 47 neighbourhoods of `tools/tiles/extract_sky_tiles.py` (`blob_masks`). Bits are N 1, NE 2, E 4, SE 8, S 16, SW 32, W 64, NW 128, set where the neighbour is the same; a diagonal counts only when both squares beside it do. Squares beyond the map count as the same, so walls run off the edge.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/Forecast.kt`

### Temperature text without degree sign

Temperatures are written `28 C` or `82 F` with no degree sign because the dialogue font cannot draw one.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/GbaWeather.kt`

### Weather layer timings from Red Rescue Team

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

### Weather layer timings from Red Rescue Team

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

### Weather layer timings from Red Rescue Team

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

### Weather layer timings from Red Rescue Team

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

### Weather layer timings from Red Rescue Team

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

### Weather layer timings from Red Rescue Team

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

### Weather layer timings from Red Rescue Team

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

### Weather layer timings from Red Rescue Team

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

### Weather layer timings from Red Rescue Team

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/GridPage.kt`

### Grid editing gestures

Documented in `docs/launcher-widget-grid.md Rules`.

### Edit canvas has two spare rows

While editing, the grid canvas is two rows taller than its lowest widget so there is always a free place to drop a widget lower.

### GridEditBar floats over pages

The grid edit bar floats at the foot of the screen over the pages instead of standing in them, so entering edit mode moves nothing. Page- removes the shown page only when it is one of the user's own, and its widgets move to the page beside it.

### Add-page buttons on a second line

The Left+ and Right+ add-page buttons sit on their own second line so their names fit at any text size.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/HostedWidgets.kt`

### Hosted widget spec in dp

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 11.5`.

### Hosted widget cell limits avoid clipping

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 11.5`.

### Launcher search finds places by name only

`LauncherDestination` lets the launcher search open a PokeLife place by its name or keywords only; no search ever reads a record, so private Care, Heal or Mint content cannot surface in results.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/LauncherModel.kt`

### Launcher data stays on the device

The launcher's rules describe the apps installed on this device, so the app list, pins, hidden apps and moved apps stay on the device and are never synced.

### LauncherApp key

`LauncherApp.key` names an app uniquely (its component and, for a work profile, the profile) and is what pins and hidden apps remember. `removable` is false for system apps, which have no uninstall.

### Dungeon floor seed and New layout

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Launcher page order

`pageOrder` always contains the home page and Today plus up to three of the user's own pages, each of which can sit left of Home or after Today. Only grid mode shows more than two pages. It starts as Home, Today.

### Launch counts only

`launches` stores how many times each app was opened from the launcher, to drive suggestions. It holds counts only, never what was done inside an app.

### Grid null falls back to legacy slots

`grid` is null until the grid is first changed; until then the old `widgets` slot list is converted and laid out as a grid, so users who never edited the grid keep their layout.

### LauncherPrefs grid and stacked layout stay twins

LauncherPrefs holds two layouts of the same widgets: the stacked `layout` and the free `grid`. Once the grid has been changed it is the layout that counts, so a widget added later also goes into its first free spot on the grid, not only into the stacked list.

### Removing a grid widget removes its stacked twin

LauncherPrefs.removeGridWidget removes the widget from the grid and its twin from the stacked layout too, so the two representations never disagree about what is on screen (switching grid mode off must not bring a removed widget back).

### Legacy edge-drag and triple-tap switches still count

LauncherPrefs.action returns the gesture the user chose, or the trigger's usual job. For the edge drag and the triple tap the older on/off switches (`edgeDrag`, `gatherTap`) still apply when no gesture is set: switched off means no action. Old saved preferences therefore keep behaving as the user left them.

### Deleting a launcher page keeps its widgets

LauncherPrefs.deletePage never loses widgets: what was on the removed page moves to the page next to it (the one before, or the one after when it is the first user page). Only the user's own pages can be removed; Home and Today stay.

### New grid widget also enters the stacked layout

LauncherPrefs.addGridWidget puts a widget in its first free grid spot, once only (a widget lives on one page). On Home and Today it also adds the widget to the old stacked layout, so switching the grid off still shows it; a widget on a user-made page exists only on the grid.

### Moving a grid widget between pages keeps its stacked twin in step

LauncherPrefs.moveGridWidgetToPage creates the target page first when it is past the last one, and moves the widget's stacked-layout twin along, so grid and stacked layouts stay consistent.

### Launch counts are halved at a cap

LauncherPrefs.recordLaunch counts each app opening; when any one count passes MAX_LAUNCHES (60) all counts are halved (and zeros dropped), so recent use outweighs old use in the Often used suggestions.

### Launcher reset keeps what the user built

LauncherPrefs.reset restores every setting to a new launcher's start (town, widgets and grid, pages, gestures, weather, battery saver) but keeps what the user built or chose about their apps and place: pinned apps and icons, hidden apps, moved categories, folders, the launch counts behind Often used, and the weather place.

### Page order is saved as a count when it is the usual one

LauncherPrefs.encode writes the page order as a plain page count when it is the default order (Home, Today, then Page 3 and on), as older saves did, and by page name only for any other order, so old and new saves read the same way.

### Launcher prefs decode skips bad lines

LauncherPrefs.decode skips any damaged or unknown line of the stored text instead of failing, so a corrupted or newer preferences string can never stop the launcher from starting.

### Red Rescue Team dungeon tiles

Documented in `docs/assets.md`.

### Explorers of Sky dungeon tiles

Documented in `docs/assets.md`.

### Red Rescue Team maps

Documented in `docs/assets.md`.

### Mystery Dungeon 2 items

Documented in `docs/assets.md`.

### 11.4 Default gestures

Documented in `docs/pokelife/FEATURES/LAUNCHER.md`.

### Long press on empty space excludes Pokémon

The LONG_PRESS gesture trigger fires only on empty space; a long press on a Pokémon in the town opens that Pokémon's own menu instead.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/LauncherScreen.kt`

### LauncherHost.icon threading

LauncherHost.icon draws an app's icon off the main thread and the host caches it; it returns null when the app has none, and callers must tolerate that.

### LauncherHost.forecast sources

LauncherHost.forecast returns a recent forecast kept on the device when there is one, else a fresh one from the network, and null when there is neither.

### Hosted widget sizing

LauncherHost.HostedWidget shows another app's widget by its Android widget id. On the widget grid the widget is told exactly the room its cells give it (widthDp, heightDp). In a list (heightDp 0) it gets the given width and a height of its own times 1, 1.5 or 2 for size 0, 1 or 2. Only a phone can host widgets; elsewhere it shows nothing.

### pickWidget callback contract

LauncherHost.pickWidget asks the user to choose a widget from the installed apps; onPicked receives its id and is not called at all when the user cancels.

### Hosted widget whose provider is gone

LauncherHost.widgetSpec returns null when a hosted widget's provider is gone (app uninstalled, disabled or being updated). The slot then shows a note instead of an empty space and keeps its place until the user removes it.

### Launcher pays nothing itself

The launcher home screen shows and starts what already pays and never pays anything itself: rewards stay with the app's own stores.

### Launcher only ticks the clock

The launcher's loop only ticks the clock (every second, every 10 seconds in battery saver) and stops when the screen is inactive. A task or focus session is settled by the app, which owns the save; the launcher never settles rewards.

### Town Guardian rotation is read from the clock

The Guardian that visits the town changes each day (see shownGuardian). The rotation is derived from the model clock inside derivedStateOf, so it turns over at midnight without recomposing the town every second.

### Triple tap window

The launcher's gather gesture counts a tap only when it comes within 600 ms of the previous one; a third such tap sends every Pokémon to the main screen.

### App list reveal value

The launcher's app list slides in from the right: the reveal value 1 is fully off screen and 0 is in place. A drag from the edge moves it with the finger; letting go settles it.

### Release velocity units

The release velocity of the app list drag is in screen widths per second, negative towards the open end; the list keeps gliding with it after the finger lifts.

### Launcher page positions

The launcher's page position 0 is Home and -1 the page to its left. Without grid mode there are two pages (Home and Today); on the grid there are up to three more of the user's own, each left of Home or after Today.

### Page derived state

Values such as atHome, current page and visible sides are read through derivedStateOf so the screen recomposes only when one of them changes, not on every frame of a page swipe.

### Town is two screens wide

The launcher town is two screens wide so its Pokémon can wander over to the Today page; only on the main screen can they be touched or meet one another.

### 4.5 The dungeon floor

Documented in `docs/pokelife/FEATURES/LAUNCHER.md`.

### Touches pass through widget pages to the town

The launcher's widget pages are drawn over the town and swallow touches meant for the Pokémon below, so a tap that lands on a widget page is handed on to the town by explicit hit handling.

### 11.4 Default gestures

Documented in `docs/pokelife/FEATURES/LAUNCHER.md`.

### Two-finger swipe observed without consuming

The two-finger up and down gestures are watched on the Initial pointer pass without consuming touches, so the town and widgets still receive them.

### Pager layout width

The launcher's page strip is laid out as pageCount screens wide with a fixed measure and placed from the left edge, slid by the page value; a required size would be centred instead and shift every page.

### Home never closes on back

Home is the one screen that must never close: back only steps out of an open sheet, menu or list and never leaves the launcher.

### App icon is square from the host

AppIcon expects the host's icon without the phone's round or squircle mask (the picture is square), cuts it to stair-stepped corners with a one-pixel edge, and shows a quiet placeholder while loading so scrolling never waits on it.

### Icon edge colour cached

AppIcon's border takes the icon's own edge colour so it melts into the picture and only the pixel corners show. The colour is computed once per icon and cached, because scanning pixels on every scroll-in made the list stutter.

### Icon frame filled with edge colour

AppIcon fills the whole stair-cornered square with the icon's edge colour before drawing the picture, so nothing shows through a rounded or transparent corner and no gap is left between picture and frame.

### App list blur support

The app list page darkens the wallpaper always but blurs it only on Android 12 and later, where blur is available.

### 4.5 The dungeon floor

Documented in `docs/pokelife/FEATURES/LAUNCHER.md`.

### PmdVolume

PmdVolume is a ten-block volume control (0 to 10) that says '7 of 10' because the pixel font has no % sign. onDone fires when the finger lifts so the new level can be heard; its text and bar start and end where a menu line's do, and the bar's row is a full Spacing.Target tall.

### PmdVolume first block toggles off

In PmdVolume tapping a block sets that level, but tapping the first block again at level 1 sets 0, so a sound can be switched off without a separate switch.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/LauncherWidgets.kt`

### Cells

Cells gives the widget being drawn its size in grid cells; the stacked layout counts as 4 wide and as tall as it needs. Widgets with a short form use it to choose that form (narrow is under the grid's column count).

### 8.3 Picker and editing

Documented in `docs/pokelife/FEATURES/LAUNCHER.md`.

### Buddy widget sits flush

Launcher widgets keep the page margin except the buddy widget's portraits, which sit flush against the sides of the screen.

### Hosted widgets get the cells' room

On the widget grid a hosted app widget gets exactly its cells' room with no frame of ours around it, and the provider is told that size.

### Unavailable widget keeps its cells

When another app's widget is gone, UnavailableWidget keeps its cells so nothing moves, until the user removes it (hold, then the X).

### Clock reads the model tick

The clock widget reads the model's seconds tick so the displayed minute stays right; without seconds it derives the minute so it redraws only on a new minute.

### Agenda task rows pay through the model

Tapping a task in the launcher agenda calls model.completeTask, so it finishes and pays exactly like the Plan tab; the launcher itself computes no reward.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/PmdUi.kt`

### PmdBox frame and inset units

PmdBox draws a see-through black panel in a layered frame (pale hairline, blue band, deep line; band thicker at the sides than at top and bottom), every part a whole number of art pixels. Its `inset` is the room in art pixels between frame and content; text lines add 6 more (where the cursor goes), so text sits 9 art pixels in. The frame is drawn in code as UI chrome.

### 5.5 Code tokens and type scale

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md`.

### PmdBox on the widget grid

On the widget grid a PmdBox is as tall as its cells; content that does not fit is cut off inside it, never squeezed, and is centred vertically so the room above and below is equal.

### ROW_X

ROW_X is the room, in art pixels, at the side of a line of text in a PmdBox. Every row (menu line, slider label and bar) starts and ends this far in so rows line up.

### PmdBox frame thickness constants

The PmdBox frame is 1 (pale line) + 1 (band) + 1 (deep line) = 3 art pixels thick at top and bottom and 1 + 5 + 1 = 7 at the sides; TOP and SIDE encode this.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/Town.kt`

### TOWN_CAP

At most TOWN_CAP (24) Pokémon walk the launcher town at once: the buddy first, then the highest levels, so a big collection never slows the home screen.

### 8. Resolution, scaling and animation

Documented in `docs/pokelife/FEATURES/ASSETS_AND_AUDIO.md`.

### townResidents ordering

The town shows the buddy first, then the Guardian, then favourites, then the other Pokémon (LAUNCHER.md 6.1). Inside the last two groups a Pokémon with a sprite sheet comes before one without, then higher level first, up to TOWN_CAP. At most one Guardian walks the town (shownGuardian among guardianIds); the other Guardians stay in their modules.

### Town pixel grid

Every Pokemon in the home-screen town is drawn at one whole-number townScale without smoothing, on one pixel grid, so art pixels never blur or shear. With Reduce motion the residents stand still.

### Town avoid rectangles

The avoid rectangles mark where launcher widgets cover the town: x is in screens (pages) and y is a fraction of the height. A Pokémon prefers to walk elsewhere and never stops, rests or meets under one.

### TownTapper

Widget pages are drawn over the town and would swallow touches, so the launcher passes taps through a TownTapper: its hit function takes a point in the town's pixels and says whether a Pokémon was there. Without it a Pokémon under a widget page could not be tapped.

### Guardian rotation in the town

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.1`.

### Town liveliness setting

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### New layout roll

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Sprite sheets load once

Sprite sheets are read once; until they arrive every Pokémon counts as having one, and the town picks its residents again when they load. So the first frame shows a full town and later swaps in the right order.

### Guardian departure and arrival

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.1`.

### Town spans several screens

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.2`.

### Dungeon town layout

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Square under a widget

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Room follows sprite frame size

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Town finds recompose on counters

Town finds are read inside remember keyed on data.counters, so collecting a find changes the counters and the collected find disappears at once. Dropping that key would leave a picked-up find on the floor until tomorrow.

### Town finds pick-up

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Pokémon pick up finds

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Night tint over the whole town

The night tint is one sheet laid over every screen of the town, not one per backdrop, so it reaches the right edge whatever the backdrops' widths.

### Floor versus scene ground band

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Guardian change without a departure

The old Guardian only walks off the edge if it is still owned; with Reduce motion, or while the launcher is off screen, a Guardian change is just a change with no departure or arrival.

### Surprise at a Guardian's arrival

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Disguised species

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Unscripted behaviour per Pokémon

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Disguise reveal

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Town spot coordinates

A Pokémon's spot is in fractions of the ground: x runs across the screens (pages), y from the back of the ground (0) to the front (1).

### Stairs and starting squares

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Trap effects on a Pokémon

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Trap status conditions

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Trap effect animations

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Trap icon does not stop the walk

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Tap reactions

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.4`.

### Flustered when tapped repeatedly

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.4`.

### Next walking leg avoids widgets and others

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Timid species

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Disguise returns after a minute

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Guardian aura icon

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Waking up

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Walk direction weighs depth

The ground is deeper than it looks: a step in depth covers more screen than a step across, so the walk direction (side, up or down) is chosen from the distance weighed for depth, not from the raw screen offset.

### Scene stroll pace

A scene stroll takes about 7 seconds for the width of the screen, a little quicker or slower for each Pokémon and for a livelier town. It follows TownBehavior.path, a gentle curve that eases in and out, not a ruler line at one speed.

### Floor walk is steady

On a dungeon floor a Pokémon walks each straight run at a steady pace with no ease in or out, as the games' Pokémon do; the eased curve is only for the scene ground band.

### Traps are for show

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Trap sounds only on Home

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Spin Trap confuses

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Gust Trap

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Swapping places

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Ghost walking

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Goals prevent shared squares

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Known traps are avoided

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Pokémon are walls to each other

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Floor paths off the main thread

The floor path search runs on Dispatchers.Default: two dozen Pokémon setting off together when the home screen comes back must not hold up its first frames.

### One square at a time

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Ghost fade only on change

The faint shade while a Ghost is inside a wall is animated only when the target shade changes, and beside the step, never before it. An animation to the value it already has still waits out its whole time, which put a pause before every square.

### Guardian leaves the town

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.1`.

### Walking pauses when hidden or reduced

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 12.2`.

### Ghosts never rest in a wall

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Meetings on a floor

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Timid species

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Gathering the town

Three taps on the screen call the town together: each Pokémon, after a moment of its own, strolls to the main screen (Home) where it can be touched. Rooted species stay put, and one that already answered a gathering is not sent again.

### Rooted species

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Heading for a find

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Ghost shortcuts

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Scene towns wrap round

A scene town has no side walls: a Pokémon may walk off one edge and come back in at the other (the position is wrapped after each leg). Dungeon floors have walls and do not wrap.

### Coarse draw order, cached layers

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 12.1`.

### Only Home Pokémon are touchable

Only on the main screen (Home) can a Pokémon be touched or long-pressed; while it wanders over the other launcher pages it is scenery with no pointer handling.

### Town accessibility semantics

For screen readers only the buddy, the Guardian and the favourites are exposed, by name and level, with the tap and the long-press menu as actions; the rest of the crowd is scenery so the page does not read out two dozen Pokémon.

### Status icons on one line

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Ghost shortcut rate

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Find-seeking rate

On a dungeon floor, 15 in a hundred long legs (FIND_SEEK_PERCENT) send a non-Guardian Pokémon to the nearest find it has spotted instead of wandering. The value is a starting number to tune.

### Who sleeps at night

A third of the Pokémon sleep at night, always the same ones: the choice is the id's hash modulo 3, so it does not change between runs.

### Found traps stay shown

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Pokémon are walls, awake ones swap

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Crowding gap between Pokémon

Two Pokémon are too close when the gap is the two half-widths (and the two half-heights, a little less since the one behind is only partly covered) plus a quarter of a square. Sprites meet edge to edge at a square's width, so a Wailord is given more room than a Pidgey.

### Overlap covers squares behind

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Holds are released in finally

startleAround holds each startled Pokémon (alert mark, hop, turned towards the source) with a chance in a hundred, at most max of them. Every hold is taken off in a finally, so a cancelled effect never leaves a Pokémon standing for good.

### TownTapper hit test

TownTapper.hit is the launcher's way to a tap on a Pokémon when widget pages are drawn over the town: it takes a point in the town's pixels and says whether a Pokémon was there.

### Town meetings

Two Pokémon that walk up to each other stop and react, as in a Mystery Dungeon town: a greeting (one curious, the other startled), a quarrel (one cross, the other startled) or both startled at once, with matching emote sounds. There is at most one sound every few seconds, none in Reduce motion or while the launcher is out of sight, and each pair meets again after a while.

### One meeting at a time

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Guardian takes no part in meetings

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.1`.

### One meeting at a time

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Floor meeting distance

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Meeting sounds only with the buddy

Each meeting icon has the games' own sound beside it, but only when the buddy is in the meeting, because the buddy is the Pokémon the person is watching. Other meetings are silent.

### TileTown atlas cache key

The composable's key may change (a new scene) under the same instance, so the atlas starts from what is cached for the new key, never the old one's image.

### Weather-graded tiles

The dungeon's weather grades the colours of its tiles (WeatherGrade.kt); the picture shows as it was until the graded one is made, so the town never flashes blank when the weather changes.

### Cached town backdrop layer

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 12.1`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/TownBehavior.kt`

### Town behaviour beats

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Tap reactions are brief and free

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.4`.

### Default liveliness is calm

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Restless spell rhythm

Spells of rest and pottering last a while, so the town has a rhythm (a few walk while most rest, and it changes) instead of every Pokémon rolling fresh dice each beat. The time restless is about 15% at the sleepiest, 35% at the default and 70% at the most restless; the Guardian is half as much.

### Guardian stands and looks longer

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.1`.

### Per-id spread hash

spread maps an id to 0..1 with a multiplicative hash, so ids that differ in one character still land far apart, and a salt gives each use (temper, pace, bedtime) its own spread. A plain hashCode would make similar ids behave alike.

### Anchor place per Pokémon

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Perspective depth factor

The ground is drawn in perspective: a whole step from the back to the front (y 0 to 1) looks about DEPTH screen widths long. Directions and distances are worked out in that looks-like space so a turning walk does not speed up or stretch when it heads towards the viewer.

### Leg heading units

A Leg's heading is in radians, 0 to the right, in the looks-like space of DEPTH, not in raw screen fractions.

### Correlated random walk

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Leaning along the edges

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Eased curve for a stroll

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Bend in looks-like space

The curve's bend is applied across the way the Pokémon goes in the looks-like space of DEPTH, then converted back into the ground's fractions, so it bends the same whichever way it walks.

### Partly linear easing

The stroll easing mixes 45% linear with 55% smoothstep so the pace dips at the ends without the feet sliding to a stop.

### Tap flustered after repeated taps

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### Staggered bedtimes

Each sleeping Pokémon lies down at its own darkness between 0.4 and 0.65, so they do not all drop off in the same minute, and the same threshold wakes it in the morning. A third of the Pokémon sleep at night, always the same ones (sleepsAtNight).

### Guardians take turns one a day

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.1`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/TownChirps.kt`

### Town chirps

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.1`.

### Chirp hold released in finally

The chirp hold is taken off in a finally: the effect is cancelled when the town stops being calm, and a hold left behind would keep that Pokémon standing for good.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/TownFloor.kt`

### Tileset choice and autotiles

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### New floor each day

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Floor item columns

FLOOR_ITEM_COLUMNS must list the items in the order of assets/tiles/floor-items.png (FLOOR_ITEMS in tools/tiles/extract_sky_floor.py): each item's index is the column of its floor picture.

### Ghost, water and walker movers

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### FloorGeometry coordinates

FloorGeometry maps a floor's squares onto the town: tilePx screen pixels a square, screenPx a page, heightPx tall. A Pokémon's spot is the town's fraction (x in pages, y down the whole height) of its feet, a little below the middle of its square.

### restingCell rooms-only flag

restingCell picks a square a mover can stop on and walk away from, avoiding crowded squares if there is room. With roomsOnly it picks inside a room and not a corridor, for a Pokémon that stays put and must not block the way.

### Nobody stands overlapping

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Floor image decoded off main thread

The floor atlas is decoded on Dispatchers.Default, so coming back to the home screen does not wait on it.

### Floor drawn as one cached layer

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 12.1`.

### Tile map computed once

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 12.1`.

### Floor things and Wonder Tile

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Trap effect animations

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

### Condition (trap status)

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.5`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/TownGuardian.kt`

### shownGuardian

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.1`.

### guardianWeather

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.1`.

### TownGuardianAwe

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.1`.

### TownGuardianAwe holds are released in a finally

The Guardian-awe effect releases the holds it put on the Guardian and the startled Pokemon in a finally block. The effect is cancelled whenever the town stops being calm (page swiped away, launcher out of sight), and a hold left behind would keep both standing for good.

### TownWeatherReactions

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/TownMap.kt`

### drawInnerCorners tile geometry

drawInnerCorners fills the notch a bay leaves in a wall rim by copying rim-thick blocks (3/8 of the 24 pixel tile) from the wall block of the 3 x 3 tileset: the left or right edge tile without its outermost column (ground colour, because an edge tile expects ground beside it), and that column from the top or bottom edge tile, where the rim of the bay's other side begins. It does nothing unless the tile is 24 pixels. The scale is screen pixels per atlas pixel.

### Inner corner source tiles

In the town atlas the edge tiles sit at left (column 0, row 1), right (2, 1), top (1, 0) and bottom (1, 2). The right edge's rim is x 13 to 22 and the left edge's x 1 to 10. The three pixels of the copied column nearest the ground are the rim's own outline carried on, so no ground-coloured pixel is left at the turn.

### TownMap areas are plain rectangles

A wall or water area in a TownMap is always a plain rectangle at least two squares wide and tall, so its edge tiles come from the 3 x 3 block of the tileset (corners, edges, middle) and need no other rules. Squares beyond the map count as the nearest square inside it, so an area that runs off the screen shows no edge there.

### TownMap.atlas tile layout

The town atlas (tools/tiles/cut_tiles.py) is 6 x 4 tiles: the wall block at columns 0 to 2, the water block at 3 to 5 (rows 0 to 2), and on row 3 the ground tile and its two alternates, which a few ground squares use. The alternate is chosen from the seed, so a town always looks the same.

### TownMap.innerCorners

innerCorners returns a bit per diagonal (CORNER_TL, TR, BL, BR) of a wall square that is open while both squares beside it are wall. The 3 x 3 tile block has only outer corners, so where a bay is cut into a wall its rim has a notch there, which drawInnerCorners fills from the block's own edge tiles.

### TownLayout walk band

TownLayout.WALK_TOP and WALK_BOTTOM are fractions of the screen height between which the town's Pokemon walk; the generated ponds and wall clumps stay clear of that band.

### TownLayout areas keep a square of ground apart

When TownLayout places a wall or water area it must not come within one square of an existing one, so a square of ground always stays between areas.

### DungeonFloorLayout

DungeonFloorLayout builds a dungeon floor for a walk or a team strip from the same atlas as the town: one row of wall on top, floorRows rows of ground, then wall below with bays of ground cut into it. The floor rows are always clear so nobody walks through a wall; bays and ground alternates only break the repetition. There are no ponds because not every tileset's water is fit to show. The same seed (from the dungeon and floor) always gives the same floor, and nothing is drawn by the layout.

### alternatesFit

alternatesFit decides per atlas whether each ground alternate (row 3, columns 1 and 2) is close enough to the plain ground tile to scatter over a floor. A few tilesets' alternates are something else (a pit, a patch of water) and would look like holes. It is worked out from the pixels, once per atlas.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/TownQuirks.kt`

### Quirk

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### TownQuirks.stays

TownQuirks.stays is true for a quirk that keeps a Pokemon in one place (ROOTED, ASLEEP), so such a Pokemon is set down in a room and not in a corridor that others need to walk through.

### TownQuirks.stumble

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

### TownQuirks.aura

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.2`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/WeatherGrade.kt`

### WeatherRamp

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

### WeatherRamp.grade

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

### gradedFor threading

gradedFor works out the graded colours of a big atlas (thousands of points to draw) on Dispatchers.Default, off the main thread. The graded-image cache itself is read and written on the caller's thread.

### ImageBitmap.graded draws points

ImageBitmap.graded sends every colour through the ramp by drawing every pixel of one colour with one call (a tileset has a few dozen colours), so it needs no pixel buffer to write to: it only reads the pixels and draws points.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/WeatherLayer.kt`

### WeatherLayer sheet loading on key change

The weather layer's sheet key can change under the same composable. It must start from what is cached for the new key and never show the previous key's image.

### WeatherLayer

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

### Snow and blizzard layers

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

### Sandstorm textures

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

### scrolling

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/WidgetGrid.kt`

### WidgetPage

WidgetPage covers the home page (over the town), Today and up to three pages of the user's own (Page 3 to 5), made and deleted in edit mode. Pages are saved by name, and their order (any of the user's own pages may stand left of Home) is LauncherPrefs.pageOrder.

### GridWidget

GridWidget.arg is a folder's name or an Android widget's id. The position is the top-left cell (col, row) and the size is in cells (w wide, h tall); rows grow downwards without end.

### WidgetGrid layout rules

WidgetGrid holds the layout rules of the launcher's widget grid (4 columns, like Smart Launcher's): where a new widget goes, moving one (what it bumps moves down out of the way), resizing between its limits, and turning the old stacked layout into a grid. It is pure, so it is tested without a screen; the screens only draw it and report where a finger dropped a widget.

### WidgetGrid.NOMINAL_COLUMN_DP

NOMINAL_COLUMN_DP (82 dp) is a column's width on a typical phone (a 360 dp screen less its margins), used to size a widget before the grid is on screen to measure.

### WidgetGrid.ROW_DP

ROW_DP (36 dp) is about a twelfth of a phone's width, so the old stacked layout (about 17 rows) fits one screen and a half-width widget is not tall and thin.

### WidgetGrid.settle

settle keeps the given widget where it is; any other widget it overlaps goes down below it, and what that bumps goes down in turn. Widgets are settled top to bottom, so the order they had is kept.

### WidgetGrid.fromSlots

A launcher that has only the old stacked layout (WidgetSlot) gets its grid from fromSlots: each page's widgets keep their order, one under the other and full width, and a widget's old size (Small, Medium, Large) becomes a height between its limits. On the home page the bottom widgets end at row homeRows (16), as they sat at the foot of the screen, with the town between.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/launcher/WidgetsPage.kt`

### Widgets page and folders

On the launcher's Widgets page (stacked layout) widgets are listed by area (top of the home page, its bottom, the Today page) with Up, Down, Remove and Add. A folder widget is a labelled folder with its apps; "New folder" makes the folder and its widget together, and the folder's apps are chosen from the long-press sheet in All apps.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/link/ContactImportView.kt`

### ContactImportView

Documented in `docs/pokelife/MODULES/LINK.md 3.5`.

### Contact import duplicates

Documented in `docs/pokelife/MODULES/LINK.md 3.5`.

### ContactImportView off-main-thread work

The contact import reads the address book and matches it against Link on Dispatchers.Default, so a large address book is not a freeze on the main thread.

### Contact import permission scope

Documented in `docs/pokelife/MODULES/LINK.md 3.5`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/link/Link.kt`

### LinkSection

Documented in `docs/pokelife/MODULES/LINK.md 10`.

### pendingLinkJump

pendingLinkJump holds where the next Link section should open (a search hit or an overview line) and is read once when the section appears.

### openPerson tab index

openPerson takes the person screen's tab as an index: 0 Overview, 1 Profile, 2 Notes, 3 Follow-ups.

### linkSubtitle never shows a record

The line under Link's card in Modules says how many follow-ups are due today, or what the module is for. It never shows a name or a word of a record.

### linkSubtitle reads model.linkData

linkSubtitle reads model.linkData only so Compose observes the save: the store itself is not Compose state, so without that read the due count would not be recomputed when the save changes.

### syncFromPlan

Documented in `docs/pokelife/MODULES/LINK.md 2.4`.

### planTask

Documented in `docs/pokelife/MODULES/LINK.md 2.4`.

### planTask reads model.data

planTask reads model.data only to observe the save, because model.store is not Compose state and the task's status would otherwise not refresh.

### LinkOverview

Documented in `docs/pokelife/MODULES/LINK.md 15`.

### Quick actions box position

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.1`.

### People filter pinnedKey

The People filter uses "\u0001pinned" as the key of its Pinned choice, a string no relationship category can equal, so it shares the dropdown with the category names.

### People directory paging

Documented in `docs/pokelife/MODULES/LINK.md 15`.

### Person screen tabs in two rows

The person screen's four tabs (Overview, Profile, Notes, Follow-ups) are shown as two rows of two so no label is clipped at a phone's width or a larger system font.

### Annual dates on 29 February

Documented in `docs/pokelife/MODULES/LINK.md 15`.

### Date reminder opt-in

Documented in `docs/pokelife/MODULES/LINK.md 5`.

### Add-a-date form after saving

After saving, the Add-a-date form closes when it was opened from a person's profile, and clears for the next date when it was opened from the Dates list.

### Follow-up reminders

A follow-up can be given a reminder only when it is open and has a due day, and a follow-up handed to Plan is reminded about by Plan alone, so Link offers no reminder for it.

### Postcard Trail payment

Documented in `docs/pokelife/MODULES/LINK.md 15`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/link/LinkProfile.kt`

### LinkProfile

Documented in `docs/pokelife/MODULES/LINK.md 2.6`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/link/PersonCard.kt`

### PersonCard

Documented in `docs/pokelife/MODULES/LINK.md 2.6`.

### Avatar species 0

Avatar takes a Pokemon species number for a person's picture; 0 means no picture and shows a Friend Ball in the same plain frame. Passing onClick makes it a button.

### SafariPanel

Documented in `docs/pokelife/MODULES/LINK.md 9.5`.

### SafariPanel reads model.linkData

SafariPanel reads model.linkData only so Compose observes the save and the visit state is read again after a visit.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/mint/AccountList.kt`

### AccountGroup name

AccountGroup.name is the empty string for accounts with no group; the list shows that group under the heading "Other".

### AccountList

Documented in `docs/pokelife/MODULES/MINT.md 4.3`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/mint/Interest.kt`

### InterestReview

Documented in `docs/pokelife/MODULES/MINT.md 4.3E`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/mint/Mint.kt`

### MintSection

Documented in `docs/pokelife/MODULES/MINT.md 3.3`.

### mintSubtitle never shows an amount

The line under Mint's card in Modules says how many accounts there are, or what the module is for. It never shows an amount, so the Modules list shows no figure.

### OverviewCard

Documented in `docs/pokelife/MODULES/MINT.md 3.2`.

### Overview breakdown tap

Documented in `docs/pokelife/MODULES/MINT.md 3.2A`.

### Quick actions box position

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.1`.

### First account ends on the overview

Documented in `docs/pokelife/IMPLEMENTATION_BACKLOG.md 23a`.

### Account last digits

Documented in `docs/pokelife/MODULES/MINT.md 4.2`.

### Account currency lock

Documented in `docs/pokelife/MODULES/MINT.md 22`.

### Account detail buttons in two rows

The account detail screen's buttons are laid out as two rows of two so the longest label ("Archive") is never clipped at a large font.

### ReceiveMoney

Documented in `docs/pokelife/MODULES/MINT.md 4.2`.

### Receive money edits save at once

The Edit form's "Receive money" section saves an added, replaced or removed picture at once, not with the form's Save button.

### qrFilter

A bare, roughly square QR image (aspect ratio 0.9 to 1.1) is upscaled, so it is drawn with FilterQuality.None to keep its modules crisp; a tall wallet screenshot is shrunk, so it is drawn with FilterQuality.Medium.

### QrFullScreen

QrFullScreen shows the receive-money image (a bare QR or a whole wallet screenshot) on a plain white page, fitted at its own aspect ratio and as large as fits, so another phone can scan it. Close and, when the platform can, Share sit at the bottom.

### QrFullScreen image area

The receive-money image is shown edge to edge at its own ratio: fitted when it fits the screen, otherwise full width and scrollable, never shrunk. The Close and Share bar sits below the image area so nothing covers the image.

### Transaction form payAt null

In the transaction form payAt is null when the record happened just now; the time is read when Save is pressed, not when the form opens.

### Transfer target choice

A transfer can go to another open account in the same currency. The account shown as chosen is the one saved: if the chosen id is no longer a valid target, the first target is used.

### BillsView recordKey and recordAccount

In BillsView, recordKey is the occurrence whose small account picker for "Paid, record it" is open, and recordAccount is the account chosen there (the bill's own until another is tapped).

### Bill settlement always offers Undo

Settling a bill occurrence takes it off the list, so every settlement offers an Undo.

### Marking a bill paid and its expense

Documented in `docs/pokelife/MODULES/MINT.md 8.2 Lifecycle`.

### Settled occurrences stay reachable

Documented in `docs/pokelife/MODULES/MINT.md 8.2 Lifecycle`.

### Bill schedule locked after a payment

Documented in `docs/pokelife/MODULES/MINT.md 8.3 Recurrence`.

### Mint categories box

Documented in `docs/pokelife/MODULES/MINT.md 6. Categories and merchants`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/mint/MintImport.kt`

### Statement import page

Documented in `docs/pokelife/MODULES/MINT.md 10. Philippine provider integrations`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/mint/OutbreakPanel.kt`

### Mint outbreak panel

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 10.0 Reward cadence and the loop of each module`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/mint/Overview.kt`

### Overview breakdown

Documented in `docs/pokelife/MODULES/MINT.md 3.2A Overview breakdown and quick hide`.

### Overview counts

Documented in `docs/pokelife/MODULES/MINT.md 3.2B Overview counts and the Mint widgets`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/mint/Paydays.kt`

### Paydays and holidays

Documented in `docs/pokelife/MODULES/MINT.md 8.3B Paydays: twice a month, on working days, estimated`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/mint/Plans.kt`

### percentToBasisPoints

percentToBasisPoints (Mint Plans) turns "1.5" or "1,5" into basis points (150) by plain digit parsing with no floating point, because Mint never uses binary floats for money or rates. It returns null unless the text is a percentage with at most two decimals.

### Credit line box

Documented in `docs/pokelife/MODULES/MINT.md 5.6A Credit lines, pay later and installment plans`.

### Monthly bill row

Documented in `docs/pokelife/MODULES/MINT.md 5.6C One monthly bill per pay-later or card account`.

### Early and part payments go to the earliest installment

Documented in `docs/pokelife/MODULES/MINT.md 5.6C One monthly bill per pay-later or card account`.

### First due day of an installment purchase

Documented in `docs/pokelife/MODULES/MINT.md 5.6C One monthly bill per pay-later or card account`.

### New installment purchase form

Documented in `docs/pokelife/MODULES/MINT.md 5.6A Credit lines, pay later and installment plans`.

### Installments already owed

Documented in `docs/pokelife/MODULES/MINT.md 5.6A Credit lines, pay later and installment plans`.

### Credit bills due soonest first

Documented in `docs/pokelife/MODULES/MINT.md 5.6C One monthly bill per pay-later or card account`.

### Card flow info

Documented in `docs/pokelife/MODULES/MINT.md 5.6D The card month, step by step`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/mint/Shared.kt`

### Shared bills view

Documented in `docs/pokelife/MODULES/MINT.md 5.8A Shared bills`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/plan/DayPlan.kt`

### DayPlan drag Item

The planner drag `Item` carries a task or one of the Plan module's own events (`event`). `start` is minutes after midnight and is null for a task without a time; `minutes` is the length; `day` is where it sits now.

### DayPlan Drag pointer

`Drag.pointer` is the finger position in root coordinates, not the hours' own; `Grid.local` converts it. With `resize` set the drag moves the block's lower edge instead of the block.

### DayPlan drop Target

A drop `Target` is a day, a `start` in minutes after midnight and a length in `minutes`.

### DayPlan Grid.scrolled

`Grid.scrolled` counts the hours moving under a drag (the page scrolling while the finger stays still), so the drop preview, which is computed from the finger, follows the scroll.

### DayPlan Grid.local

`Grid.local` takes the pointer in root coordinates and returns it in the hours' own coordinates (null when detached); `target` and `minuteAt` expect the latter.

### DayPlan Grid.target recomposition

`Grid.target` reads `scrolled` on purpose and discards the value: the read subscribes the composable to the counter so the drop preview recomposes when the page scrolls under a still finger.

### dragToPlan gesture

`dragToPlan` starts a drag after a long press on touch, or as soon as the pointer moves with a mouse. `onStart` gets the finger in the element's own coordinates and its size, `onMove` the finger in root coordinates, `onEnd` says whether it was dropped (released after moving) rather than cancelled. Moving consumes the pointer, so a drag never also counts as a tap and the page does not scroll under it.

### Plan week view on a phone

On a phone the Plan week view shows three days at a time (from today, paging by three) instead of seven, so each column is wide enough for a title. Wider screens (600 dp and up) show seven.

### Repeating events in the day view

A repeating Plan event is shown as each of its occurrences on the days in view, and each occurrence gets an id of its own so it can be picked, moved or dragged without touching the others.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/plan/EventPanels.kt`

### Only timed events take a companion

Documented in `docs/pokelife/MODULES/PLAN.md 8.2 PLAN events`.

### EventPanel edits on a repeating event

On a repeating Plan event, edits made in `EventPanel` (rename, length, colour, repeat) apply to every occurrence, while moving one occurrence on the calendar moves just that one, and Delete asks this one or all. `follow` receives the id the event has after a change that gives it a new one, so the panel stays on it.

### END_AFTER in repeat end

`END_AFTER` is 10: "Set an end" for a repeating event ends it after ten occurrences counting the one shown.

### RepeatEnd

A repeating event ends never, or on one of its occurrences stepped with the arrows; the end is never before the occurrence being shown, and "Set an end" picks the tenth counted from that one.

### Event companion picker

Documented in `docs/pokelife/MODULES/PLAN.md 8.2 PLAN events`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/plan/Focus.kt`

### Session summary

Documented in `docs/pokelife/MODULES/PLAN.md 6.1 Session model`.

### Failed focus session buddy face

When a focus session fails the buddy shows a mild face: dizzy when the phone was moved, a sigh otherwise, never a sad or crying one. The failure is a gentle notice, not a punishment.

### Deep Focus

Documented in `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Help.kt What is Deep Focus?`.

### Recent focus sessions

Documented in `docs/pokelife/MODULES/PLAN.md 6.4 History and rewards`.

### Focus grace and pauses

Documented in `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Help.kt What happens if I leave the app?`.

### FocusWatch placement

`FocusWatch` watches a running session wherever the player is in the app, not only on the Focus tab: leaving the app, a call, a locked screen and, for Deep Focus, the phone moving. It therefore has to be composed at the app root, not inside the Focus screen.

### FocusWatch lifecycle events

Leaving the app (ON_PAUSE) starts the 10 second grace period and coming back stops it. A call pauses the clock instead, and a locked screen is not leaving. ON_STOP re-checks the call and screen-off states because the screen can report off only after the pause has already fired.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/plan/Growth.kt`

### Balance wheel ranges

Documented in `docs/design.md Growth: goals, habits, insights`.

### Goal progress kept apart from tasks

Documented in `docs/pokelife/MODULES/PLAN.md 9. Goals, projects, and milestones`.

### Goal target date

A goal's target date is optional and only a date to aim for: nothing happens when it passes (no penalty, no auto-close).

### Goal milestone pays a Super Candy

Documented in `docs/pokelife/MODULES/PLAN.md 9. Goals, projects, and milestones`.

### Hall of Fame

Documented in `docs/pokelife/MODULES/PLAN.md 9.1 Goal badges and the Hall of Fame (owner, 15 November 2026; REP-053 chunk 5)`.

### Goal card

Documented in `docs/pokelife/MODULES/PLAN.md 9.1 Goal badges and the Hall of Fame (owner, 15 November 2026; REP-053 chunk 5)`.

### Milestone untick keeps paid

Unticking a goal milestone keeps its "paid" mark, so ticking it again pays nothing more; only the first completion pays its Super Candy.

### Badge case

Documented in `docs/pokelife/MODULES/PLAN.md 9.1 Goal badges and the Hall of Fame (owner, 15 November 2026; REP-053 chunk 5)`.

### ProgressView in Me

Plan's progress and trends (the balance wheel and charts, `GrowthView`) are shown in Me as Progress (owner, 20 November 2026). The weekly review is scrapped and has no screen.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/plan/Lists.kt`

### ListEditor archive and delete

`ListEditor` makes or edits a task list. An existing list can be archived (hidden from the menu, its tasks kept) or deleted (its tasks go back to no list). `done` gets the list's id, or null when the list was deleted or nothing was made.

### Checklist templates

Documented in `docs/pokelife/MODULES/PLAN.md 3.5A List kinds, the resetting checklist and a reminder without a due date (owner, 15 November 2026; backlog REP-058)`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/plan/MonthView.kt`

### Month view lane order

In the month grid, spans are laid out in a fixed order: all-day events first (the longest keep the top lanes), then timed events by start, then tasks. The order of the concatenated lists decides which items get the first lanes.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/plan/Planner.kt`

### PlanView onFocus

`PlanView` receives `onFocus` to start Focus on a task; it is null where there is no Focus (desktop and the web, which are dev conveniences), and the Focus buttons are then hidden. `open` moves to another Plan section (the Tasks screen's Calendar key).

### Plan Today list

Documented in `docs/pokelife/MODULES/PLAN.md 3.1 Today`.

### Today "From before" Move to today

Only tasks planned for an earlier day (no deadline) get the Move to today action; a passed deadline stays as it is, because moving the plan would not change the deadline.

### EmptyState has no explanatory line

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.1`.

### Difficulty ranks

Documented in `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Help.kt What do the ranks E to A mean?`.

### Repeat kinds in the planner

Documented in `docs/pokelife/MODULES/PLAN.md 5.1 Recurrence modes`.

### DuplicateButton

Duplicate makes an open copy of a task with the same words, list and steps and nothing paid, and says so. It is hidden for a task another module owns (`task.link != null`), because that module owns the record.

### Quick add focus request

"Add task" from a widget or the app icon's shortcut sets `model.addTaskRequested`, and the quick add box answers by opening with the keyboard up.

### Shared text in quick add

Text shared from another app (`model.sharedText`) is typed into quick add, cut to 200 characters, for the player to look at, change and add; it is never added automatically.

### Quick add time and evening

In quick add, a time ("tomorrow 5pm") and "tonight" (evening) are kept only when the task has a planned day; they mean nothing on an undated task.

### Check key first on every task row

Documented in `docs/pokelife/MODULES/PLAN.md 10.1`.

### Overdue tasks shown calmly (No shame)

Documented in `docs/design.md ADHD-friendly design (lowkey)`.

### Task editor Schedule tab

Documented in `docs/design.md Screens and flows (proposal, to confirm)`.

### Moving one day of a repeating task

Documented in `docs/pokelife/MODULES/PLAN.md 5.1`.

### Task deadline time of day

A task's deadline keeps its time of day. A deadline with no time typed is stored as the end of that day, 23:59, so 23:59 is the stored form of "no time" and the editor shows it as none.

### Linking a task to a goal pays nothing

Documented in `docs/pokelife/MODULES/PLAN.md 3.6`.

### Monthly repeat day 31

A monthly repeat takes a day of the month from 1 to 31, or the last day; in a month too short for the chosen day the occurrence falls on that month's last day.

### Task reminder limits

A task carries up to three reminders, counted from its planned time or its deadline; a task with no day has nothing to count from and can carry a nudge instead. The common offsets are keys; Other takes a typed length of up to a week.

### Nudge: a reminder without a due date

Documented in `docs/pokelife/MODULES/PLAN.md 3.5A`.

### Base chore shown in Plan is a projection

Documented in `docs/pokelife/PRODUCT_BIBLE.md 4`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/plan/Tasks.kt`

### One task engine for list kinds

Documented in `docs/pokelife/MODULES/PLAN.md 3.5A`.

### Checklist keeps finished items on view

Documented in `docs/pokelife/MODULES/PLAN.md 3.5A`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/pokemon/Collection.kt`

### Pokémon tab keeps PC view state

The PC's view (search, sort, filters, box) is held by the Pokémon tab's Collection page, not inside the PC section, so opening a Pokémon and coming back leaves the sort and filters as they were.

### One PC for every region's party

Documented in `docs/design.md Parties per module`.

### PC holds only Pokémon with no other place

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2 Pokémon roles`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/pokemon/EvolutionTrial.kt`

### Device evolution trial thresholds

Documented in `docs/design.md Evolution`.

### DeviceTrial sensors and step counting

DeviceTrial shows an evolution done with the phone (upside down, a spin or a walk) under its option on the Pokémon's page. Sensors listen only while it is on screen. Once done, the store keeps it (GameStore.deviceTrialDone) and the option can evolve. A walk keeps counting with the app closed because the phone's step counter runs on; the next reading adds the steps since the last one.

### Upside-down test

The upside-down trial reads the accelerometer's gravity on the y axis: below -7 (well below zero) means the phone is upside down, whichever way the screen faces.

### Spin trial rotation

The spin trial counts the turn around the axis through the screen (phone held flat) in one direction. A pause (rate under 1 rad/s) or a spin taking over 5 seconds starts the count again.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/pokemon/PartyPanel.kt`

### Party header baseline

The party header's title and its count are two text sizes on one row, so they share a baseline rather than a centre line (BaselineTest); this is the exception to the one-centre-line row rule.

### PartyGrid tap and long press

PartyGrid lays the party's six places in two rows of three equal slots, the lead first and named by its role. With tapOpens a tap opens the Pokémon and a long press plays its cry (as on the Pokémon tab); otherwise a tap plays the cry and a long press opens it (as inside a module page).

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/pokemon/PartySwitcher.kt`

### PartySwitcher design

Six module names do not fit as tabs on a phone (they came out as "Pl...", "Ca...") and a dropdown would hide the choices, so the switcher follows the games' box screens: the module's full name and count in its colour between two wrapping arrows, six squares for where you are (a swipe along the party does the same), and a tap on the name opens all six in a list with their leads and Guardians. Nothing is conveyed by colour alone.

### A Guardian is a normal Pokémon with one module slot

Documented in `docs/design.md Guardians (legendaries and mythicals)`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/pokemon/PerksPanel.kt`

### Party perks card and the cap

Documented in `docs/design.md Buddy and perks`.

### Perks never read what the user wrote

Documented in `docs/design.md Parties per module`.

### Perks never read what the user wrote

Documented in `docs/design.md Parties per module`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/pokemon/Pokedex.kt`

### DexView kept by the Pokémon tab

The Pokédex's chosen list (region or National, generation, filter) is held by the Pokémon tab, not the Pokédex section, so opening an entry and coming back leaves it as it was.

### Dex numbering per region and National

Documented in `docs/design.md Data to add`.

### Dex height and weight units

Species height is stored in decimetres and weight in hectograms (tenths of a metre and of a kilogram), as the games keep them; tenths() shows them as "0.7 m" and "6.9 kg".

### Form dex

Documented in `docs/design.md Roster`.

### SpriteCollab licence asks for artist credit

Documented in `docs/pokelife/FEATURES/ASSETS_AND_AUDIO.md 10`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/pokemon/PokemonDetail.kt`

### Detail page reading order

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2 Pokémon roles`.

### Moves count only toward WORLD team power

Documented in `docs/pokelife/MODULES/WORLD.md 3.2`.

### Legendary or mythical is a normal Pokémon

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2 Pokémon roles`.

### Pokémon on an expedition cannot be moved

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2 Pokémon roles`.

### One Move-to place picker

Documented in `docs/pokelife/PRODUCT_BIBLE.md Owner decisions of 11 November 2026`.

### Place picker PC row

In the place picker the PC row says "Here" only for a Pokémon that really is in the PC (not the buddy, a Guardian, a team member or a helper), and its button sends such a Pokémon back to the PC; the buddy is changed by choosing another buddy.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/BuddyStage.kt`

### Buddy reacts nonverbally, no speech bubble

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.4 Interactions`.

### Buddy stage height

The buddy stage is 248 dp tall (112 dp compact) so a whole hop fits: hop frames hold the jump and draw at the idle scale, about 200 dp high at the peak.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Components.kt`

### RewardBox drawn on Home only

RewardBox, the newest unseen rewards with an OK button, is drawn on Home only; a module page never shows another module's payout (for example a World Pokémon on Care's page).

### SpeakerBox

SpeakerBox puts the buddy's portrait at the start of a text box, as Mystery Dungeon games put a face beside text. The face only listens and cheers: the words are the app's, never the buddy talking. With no buddy it is a plain text box.

### 10 second undo window

Documented in `docs/design.md Tasks`.

### Deleted task undo window

A deleted task can be brought back for as long as a finished one can be undone (the 10 second undo window), so both share one timing.

### Decoration reward has no Bag icon

Reward.Decoration has no Bag icon (iconItem returns null): it is drawn with its own sprite in BaseRoom.kt.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Credits.kt`

### SpriteCollab CC BY-NC credit list

Documented in `docs/pokelife/FEATURES/ASSETS_AND_AUDIO.md 10`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Cutscenes.kt`

### Evolution scene Stop cancels the evolution

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 8.5`.

### CutsceneHost World moment must not replace a hatch scene

In CutsceneHost the effect that opens a World moment reads `model.cutscene` when it runs, not the value captured by the composition, because the arrival effect above it may just have started a hatch scene in the same pass and the World moment must not replace it.

### A visitor's egg hatches at once

Documented in `docs/pokelife/MODULES/CARE.md 6.6`.

### Visitor leaves an egg before it hatches

Documented in `docs/pokelife/MODULES/CARE.md 6.6`.

### How a new Pokémon arrives

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 10.2.1`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/DatePicker.kt`

### PixelDatePicker shows keys and a month, no typed ISO date

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 6.4`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/ExactFields.kt`

### TypedValueField rewrite and keep-last-value rules

A typed field (date, time, length) rewrites the text to the app's one spelling only when it loses focus. Every valid spelling is applied at once through `onChange` so Save reads what is on screen; while the text is not yet a value (`9:` on the way to `9:30`) the last good value stays. When the value changes from outside (a key or a stepper) the text is rewritten to match, but typing is never rewritten under the cursor.

### PixelMomentField never lies in the future

PixelMomentField picks a moment on the phone's clock: the day is never after today and, unless `future` is true, the time is never later than `nowMillis`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Form.kt`

### The form kit

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 6.4A`.

### PixelChoice segmented row or dropdown

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 6.4A`.

### SEGMENT_LABEL_MAX

`SEGMENT_LABEL_MAX` (9 letters) is the longest label a segmented key holds on a 360 dp phone when four keys share the row; longer labels or more than four options use a dropdown.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Help.kt`

### Help text leaves out tuned numbers

The help text is written from docs/design.md in plain words and leaves out numbers that are still being tuned, so the text does not go stale when a reward changes.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Home.kt`

### Home is the world at a glance, not Plan's dashboard

Home shows the trainer, every region with its lead and its one most useful next step, then the day's streak, mini-quests and what happened lately. It is not Plan's dashboard: the planner, focus and each lead's scene stay inside their region, and nothing on Home punishes a quiet day.

### Global buddy shows on Home only

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 7`.

### Bare state read subscribes a screen to a module store

A composable reads a module's Compose state (such as `model.careData`, `model.healData`, `model.data`) as a bare statement to subscribe to it, because the module stores are not observable on their own. Without the read the card never redraws after the module changes and its buttons look dead, so the read must stay even though its value is unused.

### Bare state read subscribes a screen to a module store

A composable reads a module's Compose state (such as `model.careData`, `model.healData`, `model.data`) as a bare statement to subscribe to it, because the module stores are not observable on their own. Without the read the card never redraws after the module changes and its buttons look dead, so the read must stay even though its value is unused.

### Also today shows counts only

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 13.3`.

### Bare state read subscribes a screen to a module store

A composable reads a module's Compose state (such as `model.careData`, `model.healData`, `model.data`) as a bare statement to subscribe to it, because the module stores are not observable on their own. Without the read the card never redraws after the module changes and its buttons look dead, so the read must stay even though its value is unused.

### Home shows counts, never a bill's name or amount

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 13.3`.

### Recently lists only seen rewards

The Recently card lists only rewards already looked at; rewards not yet seen are in RewardBox above it.

### Rest day pauses the streak

Documented in `docs/design.md Rest days`.

### Mini-quests are an honour system

Documented in `docs/design.md Daily mini-quests`.

### Bare state read subscribes a screen to a module store

A composable reads a module's Compose state (such as `model.careData`, `model.healData`, `model.data`) as a bare statement to subscribe to it, because the module stores are not observable on their own. Without the read the card never redraws after the module changes and its buttons look dead, so the read must stay even though its value is unused.

### Bare state read subscribes a screen to a module store

A composable reads a module's Compose state (such as `model.careData`, `model.healData`, `model.data`) as a bare statement to subscribe to it, because the module stores are not observable on their own. Without the read the card never redraws after the module changes and its buttons look dead, so the read must stay even though its value is unused.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/IntegrationUi.kt`

### Integration card contents

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 14`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Me.kt`

### MeSection order is persisted by index

Me's sections are opened from a saved index (owner, 20 November 2026), so new sections are appended at the end of `MeSection` and existing ones are never reordered; otherwise a saved index would open a different section.

### MeSection order is persisted by index

Settings is a menu of pages (owner, 20 November 2026) and its page entries are appended at the end of `MeSection` so a saved index still opens the same section.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Menu.kt`

### MenuEntry.detail is spoken, not drawn

`MenuEntry.detail` is no longer drawn because the owner wants every menu card the same, an icon and a title only; it is kept as what a screen reader says after the title.

### Section menus are compact rows

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 6.7`.

### Navigation transition timing

Ordinary navigation transitions (`FADE_MS`, `SLIDE_MS`) stay inside the 160 to 240 ms band of the transitions spec; game moments take longer on their own.

### ScreenSwitch content must draw the page it is given

`ScreenSwitch` keeps each screen's saved state (such as scroll position) while another is shown. Its `content` must draw the page it is given, not read the current one, because the old page is still drawn while it leaves. Going deeper slides in from the end, coming back slides the other way, same-depth screens crossfade, and with Reduce motion the switch is instant.

### ScreenSwitch size transform is null

Every page fills the screen, so `ScreenSwitch` animates no size change.

### ScreenSwitch saved-state key is a string

The saveable-state key is the page's string form, so the saved state survives on Android, which keeps it in a Bundle.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/ModuleGate.kt`

### Which modules the screen lock can guard

Documented in `docs/pokelife/MODULES/MINT.md 13.2`.

### Screen lock is a door, not encryption

The screen lock in front of a private module is a door only: the person proves themselves with the phone's own lock, nothing is encrypted with it and no password is made, so it can never make a record unreadable. One door serves every private module, Mint included, whose records stay sealed either way.

### ScreenLockGate grace only counts while the app is in front

Returning inside the grace period keeps a module open, but only while the app is in front (state RESUMED): a tick after the app was left must not count as being back, or the door would never close again.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/ModuleNav.kt`

### Moving around inside the Modules tab

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 6.7`.

### NavSheet needs an opaque page colour behind the box

The sheet's dialogue box sits on an opaque page colour because the dialogue box alone lets the page behind show through the rows.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/ModuleParts.kt`

### DateField keys, month and typed day

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 6.4`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/ModuleStage.kt`

### Module lead stage rules

A module's lead stage shows nothing until the module has a lead, so a new player sees what to do first. A tap pets the lead (friendship, with the day's cap shared by all), and it celebrates what its module paid. The global buddy never appears there (it lives on Home) and there is no berry button: feeding belongs to the buddy and Care's meadow.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Modules.kt`

### Switching Care off hides it but keeps its data

Switching Care off in Settings hides the module, but what it saved is kept.

### Bare state read subscribes a screen to a module store

A composable reads a module's Compose state (such as `model.careData`, `model.healData`, `model.data`) as a bare statement to subscribe to it, because the module stores are not observable on their own. Without the read the card never redraws after the module changes and its buttons look dead, so the read must stay even though its value is unused.

### Gear key and module order

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 6.7`.

### Focus handles Back inside its own screens

Focus has its own header, and the screens it opens handle Back themselves before the module-level handler does.

### Plan page layout

Documented in `docs/pokelife/MODULES/PLAN.md 3`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Quests.kt`

### A quest shows its Pokémon so the Pokédex counts it seen

Showing a quest displays its Pokémon, so the Pokédex counts the species as seen; locked ones stay grey shapes.

### World owns its jobs

World's jobs belong to World (a module owns its quests and its story); the shared quest list only says where they stand.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/RecoveryChoice.kt`

### Damaged database with no backup: player chooses

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Settings.kt`

### Notification permissions re-read on return

The notification settings re-read Android's permissions when the player comes back from the phone's settings, so the state shown is current.

### Screen lock is a door, not encryption

The screen lock in front of a private module is a door only: the person proves themselves with the phone's own lock, nothing is encrypted with it and no password is made, so it can never make a record unreadable. One door serves every private module, Mint included, whose records stay sealed either way.

### Restore keeps what it replaces aside

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12`.

### Off-phone backup setup and status

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.7`.

### Module exports are plain text once saved

Each module export is one readable file from the module's own export (the complete, restorable copy is the backup). Heal's and Mint's files are plain text once saved, so the player chooses where each one goes.

### Plan settings contents

Documented in `docs/pokelife/MODULES/PLAN.md 13`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Tabs.kt`

### When PixelDropdown replaces a row of tabs

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 6.4`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/Tutorials.kt`

### A module's first Pokémon comes with its first record

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 10.2.1`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/screens/VisitorPlay.kt`

### Visitor free play pays nothing

Free play with a visitor (a pat, a treat, a game of tag) warms nothing and pays nothing; it is only the visitor's answer.

### Kind words are warm and never about feelings

The visitor scene's kind words are warm and plain and never about how anyone feels.

### Visitor scene gestures and accessibility

Documented in `docs/pokelife/MODULES/CARE.md visitor interaction scene`.

### Leaving a visit pays a friend and retries

Leaving a visit whenever you like keeps what the visit warmed; a friend is paid at that moment, and the payment is tried again if it did not go through.

### VisitorPlay gesture routing

In VisitorPlay the visitor's own pointer handler does nothing while the chosen care is Service (swipe to tidy) or the play is Tag: the meadow behind the visitor takes those gestures, so the visitor must not consume them.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/theme/Controls.kt`

### Control system key states

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.6`.

### Light key colours get darker letters

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.6`.

### PixelKey fill and heights

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.6`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/theme/DialogueFrame.kt`

### StairShape insets

StairShape draws a rectangle whose corners are stairs of whole art pixels; `insets` lists, for each row from the corner, how far in the row starts, and must end in 0. `roundTop` and `roundBottom` square off a side (a header is round only at the bottom). It serves for clipping, backgrounds and borders alike.

### dialogueBox frame and padding

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.5`.

### dialogueBox inner line colour

On a light card the game's saturated violet inner line is too loud, so the app's default light-palette version (CardDeep) is a muted indigo; the dark palette keeps the game's own violet.

### pixelDiscPath pixel snapping

pixelDiscPath draws a disc as one flat span per row of whole art pixels, so its edge is stairs and never an anti-aliased curve. The centre and each row snap to the pixel grid so a ring and its fill line up.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/theme/Fields.kt`

### PixelField states

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.6`.

### Field error text

PixelField's `error` text says what went wrong and what to do, replaces the helper line, turns the field red and is read out by screen readers. It never blames the user.

### PixelField text lift

A BasicTextField draws its own text and ignores the text style's baselineShift. PixelField therefore offsets the text box by -2.25 dp so field text sits on the same centre line as the lifted Pokémon DP text elsewhere.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/theme/HoldToConfirm.kt`

### HoldToConfirm

HoldToConfirm is the key for a step that cannot be undone (design rule REP-014): press and hold about 1.2 seconds while a PixelBar fills; letting go early empties it and nothing happens. With Reduce motion on there is no bar: the first tap arms the key ("Tap again to confirm") and a second tap within about 4 seconds confirms, so nobody must hold a finger still. A screen reader's own activate action confirms at once because it was chosen on purpose.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/theme/PixelInputs.kt`

### PixelToggle is a picture only

PixelToggle only draws the on/off state (stair-cornered track, square knob left for off and grey, right for on and green). The row that holds it takes the tap and speaks the state to screen readers.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/theme/PixelTheme.kt`

### Pixel colour tokens

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.5`.

### Pixel.Outline

Pixel.Outline is the dark frame of the EXP and HP style bars and stays dark (#252637) in both palettes, so the bars keep the game's look on a dark theme.

### Pixel.Chrome

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.5`.

### Identity colours

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.5`.

### Spacing.BoxX and BoxY

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.5`.

### PixelTheme palette ordering

PixelTheme sets `Pixel.dark` (inside `remember(dark)`) before anything below reads a colour token, so the first frame already uses the right palette.

### Pokémon DP font baseline lift

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 12A`.

### Type scale

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.5`.

### Material helper and label styles

Material draws field labels, helper text and error text with `bodySmall` and `labelSmall`, so those two styles are set to the app's body font; otherwise they would show in the system typeface.

### GameBox versus TextBox

GameBox is the game's pixel-bordered text box and is reserved for the moments that deserve it: rewards, the Professor's dialogue and a hatch. Everything else uses the calm TextBox.

### GameBox inner frame

The GameBox inner frame sits 3 dp inside the outer one, so its corner stairs are 3 dp smaller and the two outlines stay concentric.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/theme/PmdFont.kt`

### lettersOnly filter

lettersOnly paints only the white pixels of the dialogue bitmap font in the given colour and drops the black shadow, so tinted text is one thin colour; letters merged with their shadow looked bold.

### PmdGlyph dy

PmdGlyph.dy is how far the glyph's top lies below the baseline, in art pixels; a negative value means above the baseline.

### PmdFont bitmap font

Documented in `docs/assets.md Dialogue font`.

### PmdFont.LINE

PmdFont.LINE (10 art pixels) is the height of the capitals plus their shadow, so a line of text is centred by its letters; descenders hang below the line.

### PmdFont.BASELINE

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 12A`.

### PmdText emoji rendering

PmdText draws text in the bitmap dialogue font on one line, cut with "..." if it does not fit; marks the font lacks become look-alikes or `?`. Emoji are the one exception: the system emoji font draws them at the height of the letters beside the bitmap text, so a task called "🏠 Home" still shows its house.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/theme/RegionLook.kt`

### ModuleAccent

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md Chrome and module accents`.

### RegionLook

RegionLook is a region's own look, the first of three visual layers: the real scene its lead stands in, and its colour. A region's page, its section headers and its card on Home and in Modules wear it; the lists, forms and settings inside stay in the plain look, and game moments (hatch, evolution) keep their own.

### MINT region scene

Mint's cave scene is only a backdrop and never sits behind a figure, since Mint is a utility surface with sensitive money data.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/theme/ThemeWidgets.kt`

### Rank meaning

A difficulty rank says how hard a task feels to the player, not how urgent it is; harder ranks earn more.

### RankBadge letter size

The rank letter sits in a fixed-size square and is composed with a Density whose font scale is 1, so the badge keeps its size when the system text size grows.

### LocalChromeColor

LocalChromeColor holds the colour of the header on the page now shown, which the bottom tab bar takes on: `App` provides the state and each screen header writes to it.

### NoticeTone

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 5.5`.

### Notice copy

A Notice is one calm sentence about the state of something (offline, a save to choose, a sign-in that did not work) in a tinted card with a coloured edge, announced politely by screen readers. It says what happened and what to do, and never blames the user.

### quietClickable

quietClickable is a tap with no ripple, for a Pokémon or tile whose answer is plain to see (a page opens, a cry plays): a ripple there reads as a grey box stuck on the sprite.

### focusRing placement

Modifier.focusRing draws a 2 dp keyboard focus ring while the control after it in the modifier chain has focus, so it must go just before the `clickable`, `selectable` or `toggleable`. Touch users never see it.

### ChoiceRow

ChoiceRow is one choice out of a few as a full-width row with a radio dot; the dot is filled and the border darker on the chosen one, so the choice does not rest on colour, and choices never look like action buttons.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/world/ShopVoice.kt`

### Explorers of Sky text sources

The Town shopkeepers' lines (ShopLines) are the Explorers of Sky text from rip/sky/text/strings.tsv ("Kecleon Market Strings", "Spinda's Juice Bar Strings", "Recycle Shop Strings"), with the game's line breaks and ♪ kept as words and its tags removed. Only the shop's own words change where the app's shop differs from the game's (the Swap Shop is the Recycle Shop's counter). Nintendo / Chunsoft text; private use only.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/world/SkyBarks.kt`

### SkyBarks source and determinism

SkyBarks holds what a Pokémon says when happy to be with you, taken from the Explorers of Sky text (rip/sky/text/strings.tsv: "Pokemon HEALTHY Dialogue" and "Pokemon LEVEL_UP Dialogue", only the lines that name no move, item or player; line breaks removed). A Pokémon always says the same line (via pick), so a species sounds like itself. Nintendo / Chunsoft text; private use only.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/world/SkyJobs.kt`

### SkyJobs rescue calls

Rescue notices on the job board use Explorers of Sky wording (rip/sky/text/strings.tsv, "Mission Title Set Phrases": the lost Pokémon's own call for help) in place of the app's description. Nintendo / Chunsoft text; private use only.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/world/StoryCues.kt`

### Feeling cry before the face

A speaker's cry plays immediately before its feeling is shown (what the games' sound design does), so what is heard and what is seen agree; only a change into a strong feeling cries, so a calm or pleased face is silent.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/world/StoryPlayer.kt`

### Story buddy cast

In a story scene the buddy character is whichever Pokémon the player chose as their buddy, never a fixed partner.

### Story portrait fallback

When a Pokémon form has no portrait for an expression, the next nearest one is used: sad falls back to teary, worried to surprised, then Normal.

### StoryPlayer progress

StoryPlayer plays an episode scene by scene; each scene watched to its end is recorded as a record for the Story archive, never as a reward, so replaying changes nothing.

### MapScope pixel scale

In StoryPlayer, a scene on a ground map draws with one whole-number screen-pixels-per-art-pixel scale shared by the map and every sprite, so all the art has the same pixel size. Characters' feet are placed on a whole map pixel by rounding against the camera origin.

### MapStage camera

StoryPlayer's MapStage shows a ground map at a whole-number pixel scale, about 256 art pixels across (a tile is about a tenth of the width), never squeezed to fit. The camera centres on the characters when they all fit, follows the speaker when they are further apart, glides, stays inside the map and always rests on a whole map pixel.

### faceSpecies for the buddy

faceSpecies returns null for the buddy: the buddy's cry and face come from its own current form, not from a cast species id in the scene.

### big_ character ids

A character id that starts with "big_" in a StoryPlayer scene is drawn large (about 4.6 cells instead of 2.2) to fill the frame, for cinematics. The prefix is the only marker; there is no separate flag in the script.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/world/World.kt`

### WORLD module scope

Documented in `docs/pokelife/MODULES/WORLD.md 14`.

### WORLD destinations and sections

Documented in `docs/pokelife/MODULES/WORLD.md 2.1`.

### WORLD settlement by timestamp

Documented in `docs/pokelife/MODULES/WORLD.md 3.1`.

### WORLD landing order

Documented in `docs/pokelife/MODULES/WORLD.md 2.1`.

### Every layer open from day one

Documented in `docs/pokelife/MODULES/WORLD.md 14.1`.

### Town pages

Documented in `docs/pokelife/MODULES/WORLD.md 2.1`.

### townPageToOpen test hook

TownPage selection in World.kt can be preset through the internal townPageToOpen variable so a screenshot test opens straight on a Town page; in the app it stays null and the Town opens on its menu.

### Finds wait in the Town

Documented in `docs/pokelife/MODULES/WORLD.md 2.1`.

### Team editor dungeon choice is UI only

In the World team editor the dungeon chosen to suit the team to is only UI state and is never saved: it just steers Recommend and the favoured-type ordering.

### Floor choices wait for an answer

Documented in `docs/pokelife/MODULES/WORLD.md 14.4`.

### Story archive replays

Replaying a story moment from the Journal's story archive changes nothing (it is a scene, not a reward). Chapters that are not opened yet are not listed, so the archive gives nothing away.

### Journal pages

Documented in `docs/pokelife/MODULES/WORLD.md 2.5`.

### Journal pages

Documented in `docs/pokelife/MODULES/WORLD.md 2.5`.

### Journal pages

Documented in `docs/pokelife/MODULES/WORLD.md 2.5`.

### Adventure Log wording

The Records page words the counters World keeps as in the Adventure Log of Pokemon Mystery Dungeon: Explorers of Sky ("Adventure Log Entries"), so the labels follow the game's own text.

### Journal pages

Documented in `docs/pokelife/MODULES/WORLD.md 2.5`.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/world/WorldArt.kt`

### Art cache restarts on key change

World art images (worldArt, tileAtlases, fxSheets) are loaded in produceState keyed by name. The key can change under the same composable, so the state restarts from what is cached for the new key and never shows the previous key's image.

### WorldMapHero scaling

WorldMapHero scales a map picture by the whole number that covers the box, so its pixels stay square, and trims the picture's edges, which are plain margins in these maps. The caption sits on a plate along the bottom.

### Team base art by partner

Team base pictures live in assets/rescue-maps and are bundled as team-base-<partner>-<stage>, for the Rescue Team games' sixteen partners; partnerLines maps each partner to its evolution line of species ids.

### Dungeon tileset choice

dungeonTiles gives a dungeon its own Explorers of Sky sheet where one is bundled, otherwise the closest Red Rescue Team tileset (assets/tiles/rr-NN) by look; the rifts keep the World Abyss tileset.

### Dungeon tileset choice

dungeonTiles gives a dungeon its own Explorers of Sky sheet where one is bundled, otherwise the closest Red Rescue Team tileset (assets/tiles/rr-NN) by look; the rifts keep the World Abyss tileset.

### Dungeon tileset choice

dungeonTiles gives a dungeon its own Explorers of Sky sheet where one is bundled, otherwise the closest Red Rescue Team tileset (assets/tiles/rr-NN) by look; the rifts keep the World Abyss tileset.

### TileBox motion modes

TileBox draws the dungeon's own tiles inside the dialogue border: the floor scrolls while the team walks right on it, and content is set on wall tiles under a dark veil. With Reduce motion (animate false) nothing moves. With scrolls false only the Pokemon move, because a screen with many boxes, such as the map, must not redraw them all every frame.

### Art cache restarts on key change

World art images (worldArt, tileAtlases, fxSheets) are loaded in produceState keyed by name. The key can change under the same composable, so the state restarts from what is cached for the new key and never shows the previous key's image.

### TileBox animation clock

TileBox moves only while the app is in the foreground and the box is on screen, and reads the running time in the draw block, so a frame redraws without recomposing anything.

### TileBox floor layout

The TileBox floor is the dungeon's own layout, 40 squares wide and repeated along the strip; ponds and bays sit well inside it so the wrap shows no seam.

### TileWalker sample

A TileWalker with sample true (a wild Pokemon of a dungeon shown on the map) is drawn as a still dark silhouette: it is a hint of who lives there, not someone the player owns.

### Art cache restarts on key change

World art images (worldArt, tileAtlases, fxSheets) are loaded in produceState keyed by name. The key can change under the same composable, so the state restarts from what is cached for the new key and never shows the previous key's image.

### CampYard time reads

CampYard reads the running time where the offset is laid out and where the facing is derived, so each frame moves the Pokemon without recomposing the yard.

### Map markers are not colour only

Documented in `docs/pokelife/MODULES/WORLD.md 8`.

### Art cache restarts on key change

World art images (worldArt, tileAtlases, fxSheets) are loaded in produceState keyed by name. The key can change under the same composable, so the state restarts from what is cached for the new key and never shows the previous key's image.

### Art cache restarts on key change

World art images (worldArt, tileAtlases, fxSheets) are loaded in produceState keyed by name. The key can change under the same composable, so the state restarts from what is cached for the new key and never shows the previous key's image.

## `composeApp/src/commonMain/kotlin/app/pokeplan/ui/world/WorldSpeed.kt`

### oneDecimal avoids String.format

oneDecimal formats a number with one decimal by rounding and toString, because String.format is not available in the web (wasmJs) build and this code is shared common code.

### Outcomes of team power

Documented in `docs/pokelife/MODULES/WORLD.md 3.6`.

### WorldReportCard three kinds

WorldReportCard splits what teams brought back into three kinds so a chance is never read as a promise: what every floor gives, the fixed milestone rewards, and the chance finds. Berries, Poke and milestone items are already in the Bag and Pokemon wait in Finds. The card shows until dismissed and is not saved, since the reward log is the record.

## `composeApp/src/commonTest/kotlin/app/pokeplan/ui/gear/LightTest.kt`

### SOS flash rate cap

The SOS light never changes faster than once per 200 ms (five a second at most with the default unit), well below the rate that can trouble people with photosensitivity; a test pins it.

## `composeApp/src/commonTest/kotlin/app/pokeplan/ui/launcher/GbaWeatherTest.kt`

### GbaWeather numbers

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

### GbaWeather frame ticks

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 4.4`.

## `composeApp/src/commonTest/kotlin/app/pokeplan/ui/launcher/TownBehaviorTest.kt`

### TownBehavior turn measure

In TownBehaviorTest the turn measure is the average heading change per leg in radians: a ruler-straight walk scores 0 and a fresh random direction each leg scores about 1.57 on average. The roam must stay below 1.0, which is what makes the town walk read as mostly gentle turns.

## `composeApp/src/jvmTest/kotlin/app/pokeplan/ui/AnimateToSameValueTest.kt`

### Animatable.animateTo to its current value

An Animatable animated to the value it already holds (for example `fade.animateTo(1f, tween(160))` before every step) still waits out the whole tween. The launcher town walk (`roamFloor` in Town.kt) once did this and its Pokemon moved tile by tile, so it now sets the fade beside the step and only when the value changes; AnimateToSameValueTest keeps the fact on record.

## `composeApp/src/jvmTest/kotlin/app/pokeplan/ui/BaselineTest.kt`

### Baseline alignment of two texts on one row

Two texts of different sizes on one row (a title and its count, a name and its value) must sit on one baseline, not one centre line; the owner saw 'Party' and '6 of 6' at different heights on the phone. BaselineTest measures it on a rendered image with capitals that have no descenders: the ink of both ends must end on the same row within 1 dp.

### 24 sp titles are centred by letters

24 sp titles are centred by their letters alone (TextCentreTest) and are not set on a row beside a smaller text, so BaselineTest does not cover them.

## `composeApp/src/jvmTest/kotlin/app/pokeplan/ui/CopyBudgetTest.kt`

### CopyBudgetTest sentence-length copy

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 12A rule 11`.

## `composeApp/src/jvmTest/kotlin/app/pokeplan/ui/DayPlanTapTest.kt`

### DayPlanTapTest tap coordinates

In DayPlanTapTest, once a task is picked its panel sits above 'To plan' (with its how-to line, shown until a task has a time) and the hours start under both. 8:00 is at about y 1046 px and every hour is 96 px, so 14:00 starts at about 1622 px; the tap at y 1640 relies on this layout.

## `composeApp/src/jvmTest/kotlin/app/pokeplan/ui/HoennRoom.kt`

### HoennRoom pokeemerald data format

HoennRoom is a test-only composer for the REP-053 scale test (POKEMON_PLACES_IDEAS.md section 7) and ships nothing. A room layout is `secret-base/layouts/<name>/map.bin` with 16-bit cells (metatile id in bits 0..9); each metatile is two layers of four 8x8 tiles in `metatiles.bin` (tile id bits 0..9, flip x bit 10, flip y bit 11, palette bits 12..15). The primary tileset owns tile and metatile ids below 512 and palettes 0..5, the room's secondary tileset owns the rest, and `tiles.png` files are indexed PNGs whose index is the palette entry, so they must be read as indices, not as the grey they show.

### Hoenn decoration transparency

In the Hoenn decoration PNGs (assets/hoenn/decorations) palette colour 0 is the see-through colour, so the index, not the displayed grey, decides transparency.

## `composeApp/src/jvmTest/kotlin/app/pokeplan/ui/NavigationTest.kt`

### Place names used by Android side

The Android side (widgets, shortcuts, notifications) looks an app Place up by its enum name, so every name it uses must exist in Place; NavigationTest lists them and fails if one is renamed or removed.

## `composeApp/src/jvmTest/kotlin/app/pokeplan/ui/ScreenshotTest.kt`

### ScreenshotTest frame pump

Sprites load asynchronously, so a screenshot render draws a few frames with a short wait between them before taking the picture.

### ScreenshotTest opening story

The opening story ('A New Explorer') covers Home until it is done, so ScreenshotTest marks it done first (`worldEpisodeDone` of the intro) before screenshotting a real screen.

### Growlithe derived shiny

Documented in `docs/assets.md Derived shinies (PMD sprite paragraph)`.

### Buddy reaction screenshot frame

A buddy reaction lasts about two seconds of animation time, so the screenshot of the Home buddy event stops at frame 30 (50 ms per frame) to catch it on screen.

### Module-specific Pokemon arrival scenes

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md How a new Pokemon arrives`.

### Care visitor egg arrival

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md How a new Pokemon arrives`.

### Sprite stays after in-place evolution

The Home buddy sprite stays on screen while its Pokemon evolves in place. It once went blank because the sprite composable kept the old form's sheet; the test changes the form id in place and checks the new sheet draws.

### Image composable keyed in place

An image composable whose key changes in place (a map card showing another dungeon's tiles) once drew the wrong picture; it must draw exactly what a freshly composed one draws, which the test checks by comparing frames.

### Lock screen screenshot size floor

The Mint lock screen is only a title and two keys, so there is little to draw and its screenshot uses a lower minimum byte size (3,000 instead of 5,000).

### Phone-size launcher screenshot

The launcher is also rendered at a real phone's size and density (923 x 1990 px at 2.625), where a box's width is not a multiple of the pixel scale, to catch seams and rounding that whole-number densities hide.

## `composeApp/src/jvmTest/kotlin/app/pokeplan/ui/SfxAssetsTest.kt`

### Sfx bundled from two folders

The composeApp build bundles assets/sfx and the three Explorers of Sky jingles from assets/sky-sfx together under files/sfx, so a Sfx file may live in either folder; SfxAssetsTest checks both.

## `composeApp/src/jvmTest/kotlin/app/pokeplan/ui/SkyVoiceTest.kt`

### Explorers of Sky map ids

Explorers of Sky story maps are named `map:sky-<code>` (a letter, two digits, p, two digits, optional letter). Rescue Team's `sky-tower-entry` is not one, because its code SKY_TOWER_ENTRY is longer than a map code, so the test treats it as a Rescue Team map.

## `composeApp/src/jvmTest/kotlin/app/pokeplan/ui/StorySoundTest.kt`

### CryVoice condition levels

Documented in `docs/pokelife/MODULES/WORLD.md CryVoice levels (default 100%, loud 120%, low 80%, injured 95%, seriously injured 85%)`.

## `composeApp/src/jvmTest/kotlin/app/pokeplan/ui/TextCentreTest.kt`

### Text centred by its letters

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 12A rule 8`.

## `composeApp/src/jvmTest/kotlin/app/pokeplan/ui/launcher/WeatherRampTest.kt`

### Weather ramps provenance

Documented in `docs/pokelife/FEATURES/ASSETS_AND_AUDIO.md 5.6 Weather sheets`.

## `composeApp/src/wasmJsMain/kotlin/app/pokeplan/ui/WebCries.kt`

### Web cry playback

Browsers may refuse to play audio before the first user tap, and a refused play is deliberately silent (the promise rejection is ignored). A voiced cry sets `preservesPitch` off so the browser shifts pitch and speed together, the way the first games' cries were bent.

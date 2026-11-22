# Decision records: androidApp

Moved here from code comments in the comment audit (21 November 2026). Each entry is a decision, rule or constraint that
the code alone does not show. Entries are grouped by source file; a line saying "Documented in" means the owning doc
already holds the rule and the entry only points to it.

## `androidApp/build.gradle.kts`

### VERSION file and POKEPLAN_BUILD

Documented in `CLAUDE.md Layout (Versioning)`.

### versionCode comes from VERSION, never from a build number

`versionCode` is `MAJOR * 1,000,000 + MINOR * 1,000 + PATCH`, read from `VERSION` alone; `POKEPLAN_BUILD` is only the "build N" label in About and in the release title. A hand build once used 9,001 as its build number to outrank the phone's installed copy, and every later release, numbered in the hundreds, was then refused with `INSTALL_FAILED_VERSION_DOWNGRADE`. Every build of one version deliberately shares its `versionCode`: Android accepts an equal code as an update, and a build that must outrank one already installed bumps `VERSION`.

### Robolectric dependency mirror

The androidApp unit tests point `robolectric.dependency.repo.url` at Google's mirror of Maven Central, because Robolectric downloads its Android jars itself and Maven Central rate-limits the shared cloud proxy while the Google mirror does not.

### Code scanner needs no camera permission

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 4.2`.

### Health Connect dependency review

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 12.1`.

## `androidApp/src/main/AndroidManifest.xml`

### Optional hardware features

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 4.3`.

### Huawei WRITE_SECURE_SETTINGS

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

### ACTIVITY_RECOGNITION

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 4.3`.

### SET_ALARM

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 4.3`.

### READ_CONTACTS

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 4.2`.

### EXPAND_STATUS_BAR

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 4.2`.

### QUERY_ALL_PACKAGES and package visibility

The launcher declares `QUERY_ALL_PACKAGES` because, from Android 11, an app sees only the apps it has declared a query for; until PokéLife is chosen as the home app it would otherwise list too few apps.

### Health Connect read permissions

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 4.3`.

### Exact alarms for task reminders

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 4.3`.

### Opt-in launcher

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 11.1`.

### Huawei screen service

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

## `androidApp/src/main/kotlin/app/pokeplan/AndroidBackups.kt`

### Restore stages a file whole before swapping

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.6`.

### Restore note names modules left unchanged

A restore from a backup that lacks some modules says which modules still hold what they held before, so the player does not take a mixed save for a whole one. This is the `restoredNote` shown after a restore.

## `androidApp/src/main/kotlin/app/pokeplan/BackupWorker.kt`

### Daily backup on WorkManager

`BackupWorker` makes the daily backup on WorkManager's schedule, not only when the app opens, so it runs about once a day, survives a restart and waits while storage is low. It calls `LiveBackup.takeIfDue`, so it and the backup the app makes at start never double up.

### No document text in logs

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 11.2`.

## `androidApp/src/main/kotlin/app/pokeplan/CrashGuard.kt`

### CrashGuard and launcher safe mode

An uncaught exception is recorded (time and a log with no exception messages) before the process dies. More than two crashes within a minute start the launcher in safe mode, a plain list of apps with the log to copy, so the phone can always open its apps. The previous handler still runs, so system crash reporting is unchanged.

### Crash record written with commit

The crash ledger is written with `commit()`, not `apply()`, because the process is about to die and an asynchronous write would be lost.

### Recording a crash never hides it

`CrashGuard` swallows any failure while recording a crash and still calls the previous uncaught-exception handler, so the bookkeeping can never hide the original crash.

### WorkManager start failure is tolerated

If WorkManager cannot start in `PokeLifeApp.onCreate`, the failure is ignored: the backup still runs when the app opens, so a scheduling failure never stops the app.

## `androidApp/src/main/kotlin/app/pokeplan/DeviceReset.kt`

### Erase this device

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 10.3`.

### Erase releases off-phone folder first

`DeviceReset.clear` forgets the off-phone backup setup first, so the folder permission the player granted is released before the preferences that name it are wiped. What is already in that folder belongs to the player and is left alone.

### Erase reschedules alarms after emptying the save

`DeviceReset.clear` re-plans reminders and alerts once the save is empty, so that planning from the empty save cancels every alarm that was set for what is gone.

## `androidApp/src/main/kotlin/app/pokeplan/HealthRationaleActivity.kt`

### Health Connect rationale activity

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 4.3`.

## `androidApp/src/main/kotlin/app/pokeplan/LiveStore.kt`

### Stores share one document per process

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 4.3`.

### Failed write rolls back whole

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 13.1`.

### Database cutover and recovery

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12.6`.

### Damaged database waits for the player

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12.6`.

### Daily backup interval is 20 hours

Daily backups are sealed with a Keystore key (none is taken without it), and the interval is 20 hours, not 24, so the daily job never skips a day by running a little late.

### Module tables move once with read-back

Care, Plan and the player's side (Pokémon, places, Bag, Poké and its ledger) move from their document rows into their own tables once. The save is checked the way the app checks any save it loads, written as rows, read back and compared. Any difference or an unrepresentable save changes nothing: the document row stays the live save and the move is tried again at the next start.

### Heal sealing on first start with the key

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1`.

### Mint needs the Keystore key

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1`.

### Sealed backup replaces clear Heal copies

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1`.

### No document text in logs

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 11.2`.

### No dual writes after cutover

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12.6`.

### Recovery choice before any store opens

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12.6`.

### Recovery keeps damaged database and old files

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12.6`.

### Damaged database moved aside, never deleted

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12.6`.

### Keys installed before a backup is restored

Before the newest backup is restored into a fresh database, the Mint and Heal Keystore keys are installed (`useKeys`), because the backup holds those modules sealed and without the keys they would be set aside as locked.

### Restore sets the live marker

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12.6`.

### No backup: ask before using old files

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12.6`.

## `androidApp/src/main/kotlin/app/pokeplan/MainActivity.kt`

### Platform adapters register launchers in onCreate

The Android adapters for the field tools, sensors, backups, device gate, Health Connect, text files and image picker are built in `MainActivity.onCreate` before the activity starts, because they register their `ActivityResult` launchers (permissions and pickers) there, which Android only allows before the activity is started.

### Heal reminders carry only timing

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.9`.

### Mint reminders carry only id and day

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.9`.

### Base reminders carry only id, kind and day

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.9`.

### Link reminders carry only id, kind and day

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.9`.

### Notification permission asked when any reminder is set

LINK asks for notification permission as soon as any reminder is set, not only when one falls in the next few days, because a birthday chosen months ahead must still be able to notify.

### Shared text lands in the Planner quick add

Text shared to PokéLife from another app's Share sheet opens the Planner's quick add with the text typed in; nothing is added until the player confirms.

### Shortcut extras may arrive as text

A launcher shortcut's boolean extra (such as add-task) can arrive as a boolean or as the string `"true"`, so it is compared through `toString()`.

### First resume is skipped

`MainActivity` emits the resume signal only after the first resume, because the first one follows `onCreate`, which has just read the save.

## `androidApp/src/main/kotlin/app/pokeplan/OffPhone.kt`

### Off-phone backup setup

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.7`.

### Stopping leaves the folder's copies

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.7`.

### Off-phone job retries only a closed store

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.7`.

## `androidApp/src/main/kotlin/app/pokeplan/QuickTiles.kt`

### Quick Settings tiles hold no state

`OpenAppTile` keeps no state and reads nothing from the save, so pulling the notification shade down costs nothing and shows nothing private; the tile only starts the app on one screen, like a launcher shortcut.

## `androidApp/src/main/kotlin/app/pokeplan/SaveFile.kt`

### Save is one database document

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 12.6`.

### Heal save sealed, no widget reads it

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1`.

### Sealed Heal never stored in clear without key

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1`.

### Base save is private and read by no widget

The BASE module's save (private household records, serial numbers among them) is its own set of tables in the database, apart from every other module's, created there from the start; `home.json` exists only as a fallback while the database has never opened. No widget reads it.

### Link save is private and read by no widget

The LINK module's save (people, notes, dates and follow-ups) is its own set of tables in the database, apart from every other module's, created there from the start; `link.json` exists only as a fallback while the database has never opened. No widget reads it.

### Mint save sealed, read-only widgets

Documented in `docs/pokelife/MODULES/MINT.md 3.2B`.

### Mint never written in clear

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 10.4.1`.

### Mint reminder never pays or changes a record

Documented in `docs/pokelife/MODULES/MINT.md 3.2B`.

### Linked tasks completed by Base

Documented in `docs/pokelife/PRODUCT_BIBLE.md 4`.

## `androidApp/src/main/kotlin/app/pokeplan/health/AndroidHealthConnect.kt`

### Health Connect import page cap

When one Health Connect read hits the page or record cap and more data exists, the import keeps what arrived and reports the read as partial (`failed = true`) instead of silently dropping the rest.

### Log only exception class

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 11.2`.

## `androidApp/src/main/kotlin/app/pokeplan/huawei/AndroidHuaweiReader.kt`

### Wait for the Huawei screen service to bind

Android binds the Huawei screen service a moment after the accessibility setting changes, so the reader polls every 100 ms for up to about 3 seconds before the walk starts; the walk turns the service off again.

## `androidApp/src/main/kotlin/app/pokeplan/huawei/HuaweiCapture.kt`

### Huawei captures in memory only

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

## `androidApp/src/main/kotlin/app/pokeplan/huawei/HuaweiComponents.kt`

### Huawei reader services stay enabled

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

## `androidApp/src/main/kotlin/app/pokeplan/huawei/HuaweiNotificationService.kt`

### Huawei notification listener drops other packages

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

### Listener unbinds after each read

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

## `androidApp/src/main/kotlin/app/pokeplan/huawei/HuaweiScreenService.kt`

### Huawei screen reader scope

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

### Screen service off after every walk

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

### Week view captured on tabbed pages

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

### Huawei Health card list is virtualized

Huawei Health's Health page is a recycled list that holds only the cards near the screen, so `findCard` scrolls back up and down to bring a card (Sleep, Weight, Exercise records) into view before tapping it.

## `androidApp/src/main/kotlin/app/pokeplan/launcher/LauncherActivity.kt`

### Launcher is opt-in

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 11.1`.

### Strict focus holds Home

During a focus session the PokéLife launcher brings the Focus screen back when Home is pressed (strict focus), so leaving through Home does not break the session.

### Only a running timer holds Home

The launcher keeps Home on the Focus screen only while the timer is still running; a session that has ended (completed or failed, waiting to be dismissed) must not pull Home back into the app.

### Launcher skips redraw when the app list is unchanged

Home and every return to the launcher reload the app list; when nothing changed, the list and the drawn icons are kept, because redrawing every icon each time was the slowest part of coming back to the home screen.

### Adaptive icons drawn without the phone's mask

Icons are drawn without the phone's mask (round or squircle): the layers fill the square and the launcher cuts it to its own pixel shape. Only the middle two thirds of an adaptive icon are meant to show, so the layers are drawn 1.5 times as big.

### Widget provider setup screen is started by the host

A hosted widget's configuration screen is started by the launcher as the host, because the provider's activity may not be exported to other apps; its answer returns through `onActivityResult`.

### Widget id released after process kill

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 11.5`.

### Widget id released when nothing waits

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 11.5`.

### Pending widget id kept on the device

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 11.5`.

### Widget provider sizes are pixels

A widget provider's sizes are reported in pixels, not dp as they look, so `widgetSpec` converts them once with the screen's density.

### widgetEpoch read to redraw

`widgetSpec` reads `widgetEpoch` only to subscribe the composition to it, so a provider that came or went (a package change) draws the hosted widgets again.

### Hosted widget size on grid and in a list

On the grid a hosted widget is told the room its cells really give it. In a list it gets the height it asks for, times 1, 1.5 or 2 for the small, medium and large sizes, capped at 640 dp.

### Pull-down notifications gesture

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 4.2`.

## `androidApp/src/main/kotlin/app/pokeplan/launcher/SafeMode.kt`

### Safe mode uses plain Android views

The launcher's safe mode after a crash loop is built from plain Android views on purpose, with nothing from the normal screen (no Compose, no save, no pictures, no widgets, no town), because the normal screen is what keeps crashing.

## `androidApp/src/main/kotlin/app/pokeplan/launcher/WeatherClient.kt`

### Launcher weather forecast privacy

The launcher's forecast comes from Open-Meteo, which needs no account or key. It is sent only the coordinates of the place the player chose, never the phone's location. A forecast is kept on the device for 30 minutes; when the network is down the last one is shown however old it is.

## `androidApp/src/main/kotlin/app/pokeplan/platform/AndroidAppLauncher.kt`

### AndroidAppLauncher

Documented in `docs/pokelife/MODULES/MINT.md 4.3C`.

## `androidApp/src/main/kotlin/app/pokeplan/platform/AndroidCalendars.kt`

### Android activity-bound sources are made in onCreate

AndroidCalendars, AndroidContacts, AndroidDeviceGate, AndroidSensorFeed, AndroidImagePicker and AndroidTextFiles register an ActivityResult launcher when they are constructed, and Android only allows that before the activity starts. They must therefore be created in the activity's `onCreate`, never lazily.

### AndroidCalendars query range

The calendar provider stores all-day events in UTC, so they can fall just outside the local date range being asked for. The query therefore widens the range by a day on each side and the results are trimmed afterwards.

### AndroidCalendars attendee status filter

In the calendar provider an event with no attendees has a null STATUS and a null SELF_ATTENDEE_STATUS, and a plain `!=` comparison in the selection would drop it. The selection tests for null explicitly so events without attendees stay.

### AndroidCalendars all-day versus timed events

All-day events are midnight to midnight in UTC and must be read in UTC, whatever the phone's zone; timed events are instants and are shown in the phone's own zone.

## `androidApp/src/main/kotlin/app/pokeplan/platform/AndroidCapabilities.kt`

### AndroidCapabilities

What the phone has is asked of Android at the moment (sensors from SensorManager, other features from the package manager and the camera service) and is never guessed from the phone model. Asking starts no sensor, opens no camera and requests no permission: it only reads what exists and whether the permission is already granted.

## `androidApp/src/main/kotlin/app/pokeplan/platform/AndroidContacts.kt`

### AndroidContacts

Contacts are read through the contacts provider, read-only, with one query for name, phones, emails, organization and birthday and nothing else (no photos, no call log). Contacts are keyed by their lookup key, which survives a sync. The class registers a permission launcher and must be created in onCreate.

### AndroidContacts name row

The query also asks for the structured-name data row, so that a contact holding nothing but a name is still offered for import.

## `androidApp/src/main/kotlin/app/pokeplan/platform/AndroidCries.kt`

### AndroidCryPlayer

Cries play through MediaPlayer, up to MAX_CRIES (3) at once so two Pokémon meeting in the town both speak, and the oldest is cut when another starts. Short effects go through a SoundPool loaded on first use, so a run of taps or the letter scroller never cuts itself off. The one long jingle uses MediaPlayer.

### Compressed sound assets are copied out before playing

Sound files packaged in the compose resources are stored compressed inside the APK, so SoundPool and MediaPlayer cannot open them in place. AndroidCryPlayer copies each file once into the cache directory and loads or plays the copy.

### Compressed sound assets are copied out before playing

Sound files packaged in the compose resources are stored compressed inside the APK, so SoundPool and MediaPlayer cannot open them in place. AndroidCryPlayer copies each file once into the cache directory and loads or plays the copy.

### Voiced cry pitch and speed

A voiced cry (CryVoice) sets pitch and speed through PlaybackParams in the prepared listener. Setting them on a prepared player may already start it, which the following start() then repeats harmlessly.

## `androidApp/src/main/kotlin/app/pokeplan/platform/AndroidDeviceGate.kt`

### AndroidDeviceGate

Documented in `docs/pokelife/MODULES/MINT.md 13.2`.

## `androidApp/src/main/kotlin/app/pokeplan/platform/AndroidGearServices.kt`

### AndroidGearServices

The Pokégear field tools (location, microphone, network state, QR scanner) ask for a permission only when a tool is used, after the screen has said why, and nothing runs in the background. The services register permission launchers and must be created in onCreate.

### PermissionAsker

PermissionAsker holds one pending callback, so only one permission request can be in flight at a time and the answer goes to the callback that asked.

### AndroidCodeScanner

The QR scanner is Google's code scanner: it opens its own screen, needs no camera permission from PokéLife and returns the text. It requires Google Play services, and scanning fails where they are missing.

## `androidApp/src/main/kotlin/app/pokeplan/platform/AndroidHaptics.kt`

### AndroidHaptics

Compose's own haptic feedback follows the system touch-feedback setting and is often silent, so ticks go to the phone's vibrator directly. They still respect the phone's vibration and Do Not Disturb settings.

## `androidApp/src/main/kotlin/app/pokeplan/platform/AndroidImagePicker.kt`

### AndroidImagePicker

Documented in `docs/pokelife/MODULES/MINT.md 4.2`.

### isKeepableImage

Documented in `docs/pokelife/MODULES/MINT.md 4.2`.

## `androidApp/src/main/kotlin/app/pokeplan/platform/AndroidImageViewer.kt`

### AndroidImageViewer

Documented in `docs/pokelife/MODULES/MINT.md 4.2`.

## `androidApp/src/main/kotlin/app/pokeplan/platform/AndroidPhoneState.kt`

### AndroidPhoneState.inCall

Whether a call rings or is on is read from the audio mode (ringtone, in-call, call screening), which needs no phone-state permission.

### AndroidPhoneState dial, message and email

The dialer, messages and mail apps are opened with what the person has written down. None of them is started without the player pressing send or call, and no permission is needed.

### AndroidPhoneState.setAlarm

A wake-up alarm is handed to the clock app, which shows it for the player to save, because an alarm owned by the phone rings loudly and on time whatever PokéLife is doing.

## `androidApp/src/main/kotlin/app/pokeplan/platform/AndroidSensorFeed.kt`

### AndroidSensorFeed

A sensor listener is registered only when a screen asks (at the normal UI rate) and the returned function unregisters it, so nothing listens while no tool is open. It registers an activity permission launcher and must be created in onCreate.

## `androidApp/src/main/kotlin/app/pokeplan/platform/AndroidTextFiles.kt`

### AndroidTextFiles

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 8.2`.

## `androidApp/src/main/kotlin/app/pokeplan/platform/AndroidTorch.kt`

### AndroidTorch

The torch knows every camera that has a flash and tries them in turn. A TorchCallback keeps its state true: a light switched by another app, the system or a camera taking the flash is reported as it happens. release() turns off a light this app turned on and unregisters the callback (the activity calls it on stop), so the torch is never left on and the camera service holds no reference while the app is in the background. The callback registers again on next use and the system then reports the state of every flash.

### AndroidTorch.listen

If the torch callback cannot be registered, the reported state is only what this app last set.

### AndroidTorch turning on

Switching on tries the first flash camera that accepts it, because a camera in use by another app refuses and the next one may succeed.

### AndroidTorch callback state

The lit and taken sets are only valid while the callback is registered. They are cleared on release and rebuilt when the callback registers again, because the system reports every flash's state at that moment.

## `androidApp/src/main/kotlin/app/pokeplan/reminders/Alarms.kt`

### Notification channel ids with a -2 suffix

Android keeps a channel's sound for good once it is created, so channels that carry the game's notification sound get a new id with a `-2` suffix. That gives installs that already had the old channel the new sound.

### TASK_REMINDERS_PREFS

The preferences file `task_reminders` holds the task reminders' alarm codes. The app also keeps its one-time `asked` flag for the notification permission there.

### Alarms.MAX_ALARMS

Android limits the alarms an app may hold, so at most MAX_ALARMS (24) of the upcoming reminders are set and the rest wait for a later reschedule.

### Alarms.reschedule

Alarms are set for the first MAX_ALARMS of the upcoming items and earlier ones no longer wanted are cancelled. Each alarm is known by its key, kept in a preferences file. The intent builder must make the key part of the intent's identity (its data), so two keys never share an alarm whatever their request codes. The `legacy` finder cancels alarms an older version set under a bare request code, once.

### Alarms.canSetExact

Whether the phone lets the app set exact alarms can change: on Android 12 and 13 the player can switch it off, and Android 14 grants it to alarm apps. Settings shows this state with a button to the phone's permission screen.

### Alarms.set

An alarm is exact where the phone allows it; otherwise it is set as close as the system lets, which can be minutes late. The function returns whether the alarm is exact and never hides the other case: it is logged and canSetExact is what Settings shows.

### BroadcastReceiver.doAsync

A receiver's own time is only about ten seconds, so work runs off the main thread and the receiver is kept alive with goAsync until it ends.

## `androidApp/src/main/kotlin/app/pokeplan/reminders/AppAlerts.kt`

### AppAlerts

AppAlerts holds the app's own notifications beyond task reminders: the focus halfway and end alerts, World job alerts and Pokégear timers. When they are due comes from the pure rules in Nudges; each gets an alarm and NudgeReceiver checks the save again when one goes off, since the save may have changed. None counts as leaving the app: a notification posted by the app never pauses its activity and none uses a full-screen intent, so a running focus session never sees them as Left.

### AppAlerts.Kind.JOB

One alarm is set for the earliest finishing World job. When it goes off it is checked against the settled World before anything is announced.

### AppAlerts.Kind.TIMER

One alarm is set for the earliest running Pokégear timer, and the notification says which timers have finished.

### TimerActionReceiver

The Stop and +1 min buttons in a timer's notification each act only on timers that are still ringing, so a repeated tap does nothing more.

## `androidApp/src/main/kotlin/app/pokeplan/reminders/BillReminders.kt`

### BillReminders

Documented in `docs/pokelife/MODULES/MINT.md 12`.

## `androidApp/src/main/kotlin/app/pokeplan/reminders/HydrationReminders.kt`

### HydrationReminders

Documented in `docs/pokelife/MODULES/HEAL.md Reminders (section 13)`.

### Huawei read reminder

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

## `androidApp/src/main/kotlin/app/pokeplan/reminders/LegacyReminders.kt`

### LegacyReminders

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.11.1`.

### ReminderReceiver legacy shim

An alarm of the old task-reminder feature that the app can no longer name (a snooze, a repeat) lands on this receiver and shows nothing, so the old and the new reminder feature can never both deliver. A button on a notification posted before the update is translated to the shared action keys.

### RoutineReminderReceiver legacy shim

Like ReminderReceiver, this receiver for the old routine reminders shows nothing for an old alarm and translates a button on a notification posted before the update to the shared action keys.

## `androidApp/src/main/kotlin/app/pokeplan/reminders/LinkNotices.kt`

### LinkNotices

Documented in `docs/pokelife/MODULES/LINK.md 5`.

## `androidApp/src/main/kotlin/app/pokeplan/reminders/SharedReminders.kt`

### SharedReminders

One scheduler serves PLAN, CARE, HEAL, MINT, BASE and LINK (INTEGRATIONS_AND_PRIVACY.md section 9). The policy and decisions live in ReminderCoordinator, which is JVM-tested; SharedReminders only sets alarms at the planned times, posts what it is told to show, and routes a tapped action to the owning module. Alarms carry only the opaque request (module, reference, kind, occurrence), never a title or a note. Snoozes and repeats are the only thing stored (`reminder_hold`); everything else is rebuilt from the modules' own saves, so a reboot, an update or a time change just plans again.

### SharedReminders.SETTLE_MILLIS

A reminder that came due within the last SETTLE_MILLIS (60 s) may still be on its way to fire, so it counts as missed only at the next plan, which looks back this far.

### SharedReminders refresh

A repeating task's next instances, and so their reminders, are made only by refresh(), which the app runs when it opens. The scheduler runs it too, so the nightly refresh and every fired alarm plan the next bill or chore even when the app has not been opened for weeks.

### BASE chore Done from a notification

A chore's Done button is BASE's own completion, the one that pays Habitat Discovery once. Nothing else in BASE is ever done from a notification.

### LINK follow-up Done from a notification

A follow-up's Done button is LINK's own completion, the one that pays Postcard Trail once. A date's reminder offers no action but Tomorrow.

### Reminders missed while the phone was off

A reminder the modules planned that came due while the phone was off, or whose alarm came too late, is owed once (REP-026). It is held like a snooze: the hold table's `snooze` reason means any reminder held for later, as fire() does for quiet hours. A plan made before it goes off therefore keeps it.

### Overdue snoozes and repeats

A snooze or repeat that came due while the phone was off or the app was being replaced is still owed. It goes off at once and fire() checks again that it is wanted. Without this the planner, which looks only ahead, would leave it until it was dropped.

### SharedReminders last plan time

The next plan looks back to just before now, so a reminder that was on its way to fire() is not taken for missed.

### SharedReminders.setDailyRefresh

One inexact alarm a night only plans everything again. The planner looks HORIZON_MILLIS (three days) ahead, so without it a reminder set days before a date (Link's a-week-before birthday) would be planned only if the app opened, or another alarm fired, in the last three days.

### SharedReminders nightly refresh alarm

If the system refuses the nightly refresh alarm with a SecurityException, nothing is lost: the next time the app opens it plans again.

### SharedReminders.fireIntent

A reminder's alarm uses the reminder id as the intent's data, so two reminders never share an alarm even when their request codes (the id's hash) collide. Extras do not tell PendingIntents apart, and with a shared code one reminder's alarm would replace the other's.

### SharedReminders.identity

The request id holds only the module, an opaque reference, the kind and the occurrence, never a title, so the intent identity leaks nothing private.

### Snoozing a reminder

A snoozed reminder is the same logical reminder later (INTEGRATIONS_AND_PRIVACY.md 9.7): the base request keeps its id, and a repeat becomes the base request again.

## `androidApp/src/main/kotlin/app/pokeplan/reminders/TaskReminders.kt`

### Task reminder notification buttons

Android shows three notification actions at most, so only the snooze buttons are offered. The Tomorrow action is still handled by the legacy ReminderReceiver for notifications posted by an older version.

## `androidApp/src/main/kotlin/app/pokeplan/reminders/UpkeepReminders.kt`

### UpkeepReminders

How a BASE reminder looks as a notification (rules in BaseReminders and BASE.md section 6): the lock screen shows the same words for every record whatever the user allowed (the kind only, such as a chore is due). The full text, which names the chore, supply or job only when the user said so, shows after unlocking. There is never a quantity, a serial number or a note. A chore's notification has Done (the chore's own completion) and every kind has Tomorrow. A reminder never marks maintenance done, restocks a supply or changes a warranty.

## `androidApp/src/main/kotlin/app/pokeplan/widgets/GlanceWidgets.kt`

### Glance widgets reload data on every update

Glance runs provideGlance once per session (about 45 seconds), and an update to a widget whose session is still open only recomposes. Values worked out in provideGlance then never change, which froze the buddy on its still picture after a pet. So updateWidgets stamps each widget's Glance state and a new stamp makes provideFresh load the data again inside the composition.

### Widget task completion is paid on next app open

Documented in `docs/design.md Defaults (proposal, change if you disagree)`.

### Buddy widget pet callback stays alive

The widget's pet action callback stays alive while the hearts show, through settleLater, so the cry has long enough to play out before the system ends the callback.

### Buddy widget idle animation

The buddy's idle animation plays by itself in a flipper and keeps playing under a reaction. During a focus session the buddy sleeps, using its SpriteCollab Sleep animation (one still picture for a form without it). With Reduce motion on, the widget shows one still picture.

### Focus widget countdown is a Chronometer

The Focus widget's countdown is built as a Chronometer RemoteViews so the system ticks it and it stays right without the widget being updated.

### Mint widget states

Documented in `docs/pokelife/MODULES/MINT.md 3.2B`.

## `androidApp/src/main/kotlin/app/pokeplan/widgets/WidgetArt.kt`

### WidgetArt

Widget bitmaps are made from the app's real assets (sprites, items, box wallpapers). Widgets cannot use the Compose renderer, so tiling and rounded corners are done with a Canvas. Nothing here draws pixel art: the only shape drawn in code is the small shadow under the buddy, which is UI chrome.

### WidgetArt.wallpaper

A box wallpaper is tiled and mirrored like the app's PixelBackground, with its two-pixel frame trimmed, and scaled by a whole number so the pixels stay sharp. roundBottom false rounds only the top, for a header strip.

### WidgetArt.scene tint

The night tint is quantized to 20 steps so a slowly changing dusk does not build a new picture every minute.

### WidgetArt.withoutEmptyRowsBelow

The regular sprites have empty rows under the feet. They are trimmed so the buddy stands on its shadow, as the animated frames do.

### WidgetArt frame scale

Mystery Dungeon sprites share one pixel size (a 32 pixel frame fills the canvas), so they are scaled by that size rather than by their own frame size, as in the app.

### WidgetArt.reaction

The widget shows one still frame of a buddy's reaction: the icon's best frame, because a RemoteViews cannot play a strip.

## `androidApp/src/main/kotlin/app/pokeplan/widgets/WidgetRefresh.kt`

### WidgetRefreshReceiver

A pet or an event shows a still picture for a few seconds and a delayed update then brings the animation back. That update used to run inside the short-lived tap handler, which the system can end early and leave the buddy frozen, so it now runs from an alarm, which survives.

### scheduleWidgetRefresh failure

The refresh alarm is best effort: if it cannot be set, the next widget update brings the animation back anyway.

## `androidApp/src/main/res/values/launcher_theme.xml`

### Launcher window background is dark

The launcher's window before its first frame is dark so starting or returning to the home screen never flashes white.

## `androidApp/src/main/res/xml/data_extraction_rules.xml`

### Backup exclusion rules

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 4.3 Manifest audit`.

## `androidApp/src/main/res/xml/huawei_reader.xml`

### Huawei Health accessibility service scope

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

## `androidApp/src/test/kotlin/app/pokeplan/DeviceResetTest.kt`

### Erase this device

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 10.3 Disconnect and deletion`.

### Erase this device goes through every preferences file

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 10.3 Disconnect and deletion`.

## `androidApp/src/test/kotlin/app/pokeplan/SharedBillsSealedTest.kt`

### Shared bills are sealed

Documented in `docs/pokelife/MODULES/MINT.md 5.8A`.

## `androidApp/src/test/kotlin/app/pokeplan/huawei/HuaweiComponentsTest.kt`

### Huawei listener components stay enabled (REP-055)

The Huawei notification listener is rebound only while notification access is granted, and its components are never disabled, because Android binds a listener or accessibility service only after the player switches it on.

## `androidApp/src/test/kotlin/app/pokeplan/huawei/HuaweiNotificationServiceTest.kt`

### Huawei Health notifications only

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

## `androidApp/src/test/kotlin/app/pokeplan/reminders/SharedRemindersTest.kt`

### QuietHours with equal start and end

A QuietHours whose start equals its end is never inside, so tests use it to switch quiet hours off and do not depend on the hour they run at.

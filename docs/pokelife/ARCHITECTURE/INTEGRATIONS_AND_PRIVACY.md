# PokéLife — Integrations and Privacy Architecture

**Version:** 1.1  
**Status:** Approved target architecture (owner, 6 November 2026; implementation details such as the database choice stay subject to their stated feasibility spike)  

> This document defines cross-module Android integration, provider, permission, privacy, and security contracts. The Product Bible owns product principles; module specifications own domain behavior; `DATA_AND_MIGRATIONS.md` owns persistence, transactions, encryption implementation foundations, backup, restore, and migration; this Section 9 owns reminder scheduling and delivery. This document connects those authorities without duplicating their detailed rules.

## 1. Purpose and non-negotiable principles

PokéLife is an Android-first, local-first personal operating system. Android and external services may provide optional capabilities, but they must never become prerequisites for core utility or access to the shared Pokémon collection.

The following are product constraints, not implementation preferences:

1. No mandatory PokéLife account, cloud profile, cloud save, cloud synchronization, telemetry, analytics upload, advertising, or tracking SDK.
2. No sensitive personal information is used to determine Pokémon eligibility, rarity, rewards, evolution, story branches, or WORLD progression.
3. No private module content is sent to another module, WORLD, the launcher, a provider, or a network service without an explicit, documented, user-authorized purpose.
4. No private banking credentials, PINs, CVVs, OTPs, health credentials, or unrelated notification contents are collected.
5. Never assume private APIs, undocumented endpoints, authenticated screen scraping, or inaccessible third-party data can be obtained.
6. Provider integrations are optional. Manual workflows and existing local data remain usable when a provider is absent, denied, disconnected, offline, or discontinued.
7. Request Android permissions only when the user invokes the capability that needs them, and request the narrowest scope available.
8. Be explicit about data source, freshness, provenance, supported record types, and limitations.
9. User-controlled export, backup, restore, disconnect, and deletion are core product capabilities.
10. Security and privacy failures must fail closed for external access without corrupting committed local data.

## 2. Capability classification and provider status

Every integration capability must be labelled using one of four categories:

| Classification | Meaning | Release requirements |
|---|---|---|
| **Official provider API** | Documented API or SDK offered by the service owner | Verify eligibility, region, terms, scopes, approval, production access, and end-to-end behavior |
| **Android platform API** | Documented Android API or system provider | Verify OS/version behavior, permissions, lifecycle, and device compatibility |
| **User-assisted import/export** | User explicitly selects a file, record, or share action | Validate format, preview changes, preserve provenance, and support correction/deletion |
| **Unsupported / unverified** | Private, undocumented, inaccessible, or untested path | Do not advertise or ship as a supported integration; do not build core behavior around it |

A provider adapter must declare supported operations and record types individually. A connection to a provider does not imply access to all of its data. A provider's sandbox, merchant, partner, or payment API does not imply personal-account aggregation access.

Integration status values are: `not_configured`, `available`, `permission_required`, `connected`, `partially_authorized`, `temporarily_unavailable`, `permission_revoked`, `disconnected`, `unsupported`, and `reauthorization_required`.

Do not display a provider as connected merely because an SDK is installed or an account exists on the device.

## 3. Architecture and ownership

Use the modular, layered architecture defined in `DATA_AND_MIGRATIONS.md`:

```text
Application shell
├── Permission coordinator
├── Integration registry / connection settings
├── Shared reminder infrastructure
├── Data lifecycle / security services
├── Platform adapters (Android)
│   ├── Health Connect
│   ├── Huawei provider feasibility adapter
│   ├── Calendar Provider / intents
│   ├── Notifications / alarms / WorkManager
│   ├── Contacts / Photo Picker / SAF
│   ├── Location / sensors / Bluetooth (only when needed)
│   └── Network state / provider SDK boundaries
└── Module-owned provider interfaces and repositories
    ├── PLAN   ├── HEAL   ├── MINT   ├── LINK
    ├── BASE   ├── CARE   ├── GEAR   └── WORLD
```

### 3.1 Layer rules

- Presentation requests a use case; it does not call Android providers or SDKs directly.
- Domain contracts are platform-neutral and do not depend on Android framework classes, Compose, SQLDelight generated types, or vendor SDK types.
- Platform adapters translate provider/platform data into provider-neutral DTOs and domain import commands.
- Module repositories own imported records and their validation, reconciliation, provenance, and deletion.
- The integration registry owns connection metadata and capability status, not the module's business records.
- Provider credentials/tokens belong in platform secure storage, never ordinary domain tables, logs, or exports.
- External provider IDs are namespaced and stored separately from immutable local primary keys.
- Cross-module views use references or rebuildable projections, not duplicate editable records.
- Platform failure cannot roll back or invalidate already committed local records.

### 3.2 Provider adapter contract

Every adapter declares:

- Provider and adapter identity/version.
- Capability list and supported record types.
- Platform/API version and device/region constraints.
- Required Android permissions and provider authorization scopes.
- Read/write direction and whether the operation is user-initiated or background-capable.
- Refresh and pagination behavior, rate limits, and freshness semantics.
- Provenance mapping, external identifiers, units, time zones, and deduplication rules.
- Error states, retry policy, and offline behavior.
- Disconnect, token revocation, imported-data retention, and deletion behavior.
- Whether raw provider payloads are retained; default is not retained after successful normalization.

Provider-specific SDK objects must not leak into module domain models.

## 4. Android permissions and timing

### 4.1 Just-in-time permission policy

Do not request a batch of permissions during onboarding. Before a system prompt, explain the feature, the exact data category needed, whether access is read-only, and what remains usable if the user declines.

Permission flow:

1. User opens or invokes a capability.
2. PokéLife explains why the capability needs access and what data it will read or write.
3. Request the narrowest Android permission, provider scope, or system-picker selection.
4. Record only the permission/connection state needed to present the feature correctly.
5. Recheck access on resume and before provider operations; never assume a previous grant persists.
6. On denial/revocation, stop access and retain user-entered/local data.
7. Provide a settings route when Android does not allow another runtime prompt.

Never repeatedly prompt after denial. Do not use a permission rationale to pressure the user.

### 4.2 Permission/capability matrix

| Capability | Android access | Timing and policy |
|---|---|---|
| Internet | `INTERNET` manifest permission | Manifest-only; network used only for an invoked optional service or provider operation |
| Network state | `ACCESS_NETWORK_STATE` | Only if needed to show connectivity/provider state; not for profiling |
| Notifications | `POST_NOTIFICATIONS` on Android 13+ | First time user enables reminders or a notification-dependent feature |
| Notification reading | `NotificationListenerService`, granted in Android's own notification-access settings (no runtime permission) | Only when the player turns on MINT → Settings → Record from messages → Bank notifications; the listener component is disabled while the switch is off, so Android never binds it, and every notification outside MINT's bank allowlist is dropped on its package name before a field is read (7.3) |
| Text messages | `RECEIVE_SMS` (runtime) | Only when the player turns on MINT → Settings → Record from messages → Bank text messages; the receiver component is disabled while the switch is off, the sender is checked before the body, and every sender outside MINT's allowlist is dropped (7.3) |
| Exact alarms | `SCHEDULE_EXACT_ALARM` / applicable platform mechanism | Only if a genuinely exact reminder capability is justified and Android policy permits it; prefer inexact scheduling otherwise |
| Calendar | `READ_CALENDAR`; `WRITE_CALENDAR` only for PLAN's opt-in two-way sync of its own events (6.2) | `READ_CALENDAR` when user connects a calendar; `WRITE_CALENDAR` only when the player turns on Calendar sync |
| Health Connect | Per-record-type Health Connect permissions | When user selects health data categories to import/read/write |
| Location | `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` | Foreground, only when invoking a location-dependent tool; prefer approximate location |
| Background location | `ACCESS_BACKGROUND_LOCATION` | Not supported by the baseline product |
| Microphone | `RECORD_AUDIO` | Only when starting an audio measurement/recording tool; no background capture |
| Camera | Camera permission only for an implemented direct-camera workflow | Prefer system camera intents or scanner APIs where practical; request at capture time |
| Contacts | `READ_CONTACTS` or platform contact picker | Only when user explicitly invokes LINK import; prefer picker-based selection. Built: `READ_CONTACTS`, asked at "Import contacts" after the list of what is read, nothing else |
| Photos/media | Android Photo Picker | Use selected-item access; no broad media permission for ordinary import |
| Microsoft OneDrive (Microsoft account) | No Android runtime permission; Microsoft OAuth consent for read-only OneDrive photo scopes (8.2A) | Only when the player connects OneDrive in LINK; optional and revocable; read-only scopes, never a write scope |
| Documents | Storage Access Framework (SAF) | User selects a file/folder; avoid broad storage access |
| Bluetooth | Version-appropriate Bluetooth permissions | Only for a verified direct device integration; not needed merely because a watch exists |
| Sensors | `SensorManager` where applicable | Usually no runtime permission for basic motion/environment sensors; only while relevant GEAR tool is active |
| Package visibility | Launcher intents / `<queries>`; `QUERY_ALL_PACKAGES` only if justified | Only for optional launcher/app-drawer functionality and subject to Play policy; avoid broad visibility if launcher queries suffice |
| Boot receiver | `RECEIVE_BOOT_COMPLETED` | Only to restore user-configured reminders/widgets that require reboot recovery |
| Status bar | `EXPAND_STATUS_BAR` (normal permission, no prompt) | Only for the launcher gesture "Pull down notifications" (`LauncherHost.expandNotifications`, reflection on `StatusBarManager`); nothing else uses it |

The matrix is a policy map, not a declaration that every permission should be present in the manifest. Each manifest entry must have an implemented owner and documented purpose.

### 4.3 Manifest audit

`androidApp/src/main/AndroidManifest.xml` declares: `INTERNET`, `ACCESS_NETWORK_STATE`, coarse and fine location and microphone (the matching hardware features optional), `POST_NOTIFICATIONS`, `READ_CALENDAR`, `READ_CONTACTS` (LINK's import), `ACTIVITY_RECOGNITION` (the step counter, asked only when a walking evolution starts), `SET_ALARM` (hands a wake-up alarm to the clock app), `VIBRATE`, `EXPAND_STATUS_BAR`, `RECEIVE_BOOT_COMPLETED`, `RECEIVE_SMS` (MINT's opt-in capture of bank text messages, 7.3), `REQUEST_DELETE_PACKAGES`, `QUERY_ALL_PACKAGES` (the launcher), `USE_EXACT_ALARM` (with `SCHEDULE_EXACT_ALARM` up to Android 12L), five `READ_*` health permissions (read only, with the Health Connect package query, a rationale activity and the permission-usage alias), and `WRITE_SECURE_SETTINGS` for the optional one-tap Huawei read (5.2.2; the app cannot grant it, only the player over adb). `allowBackup` is `false` with `data_extraction_rules.xml`, so nothing goes to Android's cloud backup or another phone by itself; the user-controlled design is the app's own backups (10.1). No storage permission (the document picker is used). Exported components are the activities, the widget receivers, `ReminderBootReceiver`, the quick-settings tiles and the Huawei screen service (each of the last three behind the system's bind permission); the reminder receivers and `WidgetRefreshReceiver` are not exported, and MINT's SMS receiver is exported behind the system-only `BROADCAST_SMS` permission. MINT's notification listener and message receiver are declared **disabled** and are enabled only while their own switch is on (7.3). Every capability must still be checked against the shipped features before release:

- Remove unused permissions and deprecated or duplicated alarm declarations; reassess exact-alarm eligibility and remove any declaration not justified by an approved use case.
- Keep `QUERY_ALL_PACKAGES` only if the optional launcher genuinely requires broad app enumeration and distribution policy permits it; otherwise use launcher intent queries.
- Keep nonessential hardware features `required="false"`.
- Verify exported components, intent filters, URI grants and receiver permissions.

### 4.4 Signing and the release key

Debug builds (local and the rolling "Latest build" APK) are signed by the tracked `androidApp/pokeplan-debug.keystore` (SHA-1 `1A:28:99:CD:34:46:02:04:1E:AF:7B:15:7F:D1:07:41:BC:30:F0:A5`); a debug key's password is public by design. A `release` build takes its key from CI secrets (`POKEPLAN_KEYSTORE`, `POKEPLAN_KEYSTORE_PASSWORD`, `POKEPLAN_KEY_ALIAS`, `POKEPLAN_KEY_PASSWORD`) and is left **unsigned** when they are absent, never signed with the debug key. The repository holds no release keystore or password.

- **While the repository and the APKs stay private** (owner decision, 6 November 2026) the debug key is acceptable for the APKs that are published: anyone who can read the repository can already change the code.
- **Before a public build** (owner action): add the release key and its password as CI secrets, build release APKs with those variables, create an APK Signature Scheme v3 rotation lineage from the debug key (`apksigner rotate`) so existing installs accept the new one (without it the app must be uninstalled and its data lost, so take a verified backup first), re-register the new key's SHA-1 wherever the old one is registered, and add a CI check that fails if a keystore or a `storePassword` literal is tracked.
- **Not established:** whether the repository or any fork was ever public, GitHub secret-scanning alerts, and whether the debug SHA-1 is registered with an external service (the signing comment there only asks for the SHA-1 to be re-registered before a public release).

## 5. Health Connect and wearable integrations

### 5.1 Health Connect

Health Connect is the preferred Android interoperability route when it is available on the device and the source application writes the requested record types. It is not a wearable connection by itself and does not guarantee data from every watch/vendor.

HEAL must:

- Request only selected record-type permissions.
- Distinguish read access, write access, background read access, and historical read access.
- Check Health Connect availability and provider version at runtime.
- Handle user revocation and missing source applications.
- Preserve provider/source, record ID, timestamp, time zone, units, and import time.
- Avoid treating absent data as zero or as proof that an activity did not occur.
- Deduplicate imported workouts and records; never silently overwrite manual entries.
- Keep historical backfill separate from prospective reward eligibility.
- Remain fully useful through manual logging if Health Connect is unavailable.

Health Connect's Android availability, permissions, background/historical access rules, and supported record types must be verified against current Android documentation and tested on target devices before release.

### 5.2 Huawei Health and Huawei Watch Fit 4 Pro

Official and interoperability routes are a feasibility workstream, not a committed capability; the one built route is the on-phone reader (5.2.2). Investigate in this order:

1. **Health Connect interoperability:** whether the installed Huawei Health version and account region write the required types into it.
2. **Huawei Health Service Kit / official interfaces:** developer eligibility, app registration and approval, OAuth, scopes, region restrictions, distribution requirements and production access.
3. **Huawei Health export:** user-assisted import if the official export format holds the required records and parses safely.
4. **Maintained third-party bridge:** only as an optional, explicitly unofficial path after security, maintenance, licensing and data-flow review. HEAL never depends on it.
5. **Direct Bluetooth/proprietary protocol:** unsupported unless Huawei documents and authorizes the interface. No reverse engineering, no promises.

The feasibility matrix is completed on the actual Huawei Watch Fit 4 Pro, paired phone, Philippine account/region, Huawei Health version, firmware and any relevant Health Connect provider, one metric at a time (steps and distance, workouts, heart rate, resting heart rate, HRV, sleep and stages, stress, calories, SpO2, weight), each with permission, history depth, timestamp behaviour, sync delay, units, region and firmware limits, cost and maintenance risk. HEAL has records for workouts, sleep, weight, body fat and drinks, so those come first. A successful account connection is not evidence that historical records or all metric types are accessible.

Do not require HMS Core, AppGallery, a Huawei account, or cloud relay for core HEAL functionality. Explain any external Huawei account/cloud processing before the user connects.

#### 5.2.1 Route status (desk research only; nothing tested end to end)

1. **Health Connect:** no official statement that Huawei Health writes into it, and reviews list "no Health Connect sync" for the Fit 4 Pro. *Unverified, probably absent*; settled on the owner's phone (does `com.huawei.health` appear under Health Connect → App permissions, and does Heal's "Imported records" list it?).
2. **Health Kit / Health Service Kit:** needs a Huawei developer account, an app registered in AppGallery Connect and Huawei's approval per app, with user OAuth per data type. *Blocked on the owner*: no key, scope list or proof it is open to an individual app in the Philippines.
3. **Huawei Health export:** no document names a format, history depth or stable ids. *Unverified*; the owner would request their data from Huawei's privacy centre and share a sample with personal values removed.
4. **Third-party bridge (Health Sync):** reads Huawei Health and writes to Health Connect, one way for most data; Fit 4 Pro coverage undocumented. *Optional, unofficial, unverified*; it feeds the existing Health Connect import and needs no code.
5. **Direct Bluetooth, or reading Huawei's cloud with a login:** *rejected*.

HEAL reads whatever reaches Health Connect and shows the writing app of every imported record; the Huawei card in Heal → Connections says what is true.

#### 5.2.2 Reading Huawei Health on the phone

**PokéLife reads what Huawei Health shows**, on the phone only, through an Android accessibility service and a notification listener the player turns on by hand. It needs no Huawei account, cloud, developer approval, root or protocol reverse engineering; it is unofficial and fragile (any Huawei Health update can break it), and that is accepted. It is the one exception, for Huawei Health only, to HEAL.md 10.1's "no fragile accessibility automation". The rules:

1. **Huawei Health only.** The accessibility service is declared for the package `com.huawei.health` alone, so Android sends it no other app's screens. The notification listener cannot be narrowed by Android (it is offered every notification), so the code drops every notification from any other package on arrival, before reading a field. Nothing from another app is read, kept, counted or logged.
2. **Off until the player turns it on, and off again after every read.** Each is turned on separately in Android's own settings (Accessibility; Notification access). On Android 13 and later a sideloaded app's accessibility service is greyed out until App info → ⋮ → Allow restricted settings; the Connections page says so. **Banking apps refuse to open while a sideloaded accessibility service is on** (MariBank locks its app until the service is off or PokéLife is uninstalled; GCash shows a security notice). So the screen reader turns itself off (`disableSelf`) as soon as a read ends, however it ends, and Connections has a Turn off button. It is never hidden, renamed or disguised from those apps' checks: it is simply off when they run. Optional one-tap reads: if the player grants `WRITE_SECURE_SETTINGS` once from a computer (`adb shell pm grant app.pokeplan android.permission.WRITE_SECURE_SETTINGS`), Read Huawei Health switches the service on itself for that one read and it is off again afterwards. The notification listener stays on; if a banking app objects to it too, it is turned off the same way.
3. **Active walk-through, on a tap only.** "Read Huawei Health" opens Huawei Health, waits for its Health page, then makes **one pass down it** (`HuaweiWalk`): every card is opened as it comes into view, found by its title element (`function_set_items_title`), its page is scrolled within that card's own budget, and Back returns to the same place. The order is the owner's (22 November 2026): **the Steps summary first** (not a card: its element is `time_strength_layout_middle`; opened with 2 scrolls), then **Exercise records, Heart, Weight, Sleep, Skin temperature, Emotional wellbeing, SpO2 and Cycle Calendar**. Each of those pages also opens its four-dots menu → **All data** (the menu button is the toolbar icon described **"More items"** / **"More options"**, found by that description only; the icon beside it (`hwappbarpattern_menu_icon`: Share, Stats) is never tapped; the item by its text). Emotional wellbeing's two pills, **Emotion** and **Stress**, are tapped in turn, each with its own All data. Cycle Calendar opens **Period records** instead (it has no All data). A page without the menu is noted in the walk log and left. Back is pressed only while the Health page is not showing. **Cards read into records**, in that order: **Exercise records** (up to 8 scrolls in a new read, ending as soon as the list shows a session older than a read can pay for, 8 days, `IMPORT_FRESH_MILLIS`; 20 in an all read), **Weight** (2) and **Sleep** (2, and its Week tab, 2). **The other wanted cards** (Heart 2 scrolls, Skin temperature, Emotional wellbeing, SpO2, Cycle Calendar) are opened and scrolled within their budgets to capture their pages; Heart is not parsed yet, and nothing is saved or paid from these pages. Health Clovers and Activity rings are skipped (`HuaweiWalk.SKIP`). The pass ends at the end of the Health page (a wanted card that never came into view is looked for once, scrolling back), and stops as soon as Back does not bring the Health page back. It returns to PokéLife (at most 420 seconds, or 900 in an all read).
3b. **Two reads: new only and everything (0.229.0, owner, 22 November 2026).** Heal → Connections → Huawei Health has two reads: **Read new** (`HuaweiWalk.Scan.NEW`) and **Read everything** (`Scan.ALL`). Both visit the same cards in the same order and open the same All data pages; **Read new** skips a card HEAL already has today and stops every list at the first saved row, while **Read everything** never skips and reads the full budget (the Exercise list 20 scrolls, every All data list up to 30 scrolls, the newest 31 Sleep days instead of 14). An all read has up to 900 seconds; a new read 420.
3c. **All data as history, Sleep entries, and one menu open at a time (owner, 21 November 2026; 22 November 2026).** The All data lists of Sleep, Skin temperature and SpO2 (`PrivacyDataModelActivity`: month headers, then a value and a date per row) are scrolled until a row is 90 days old and every row is kept as a reading (`HuaweiRecords.allData`): Sleep length (minutes; a night or a nap, no bed or wake time on the list), the day's lowest and highest skin temperature and SpO2, stamped at noon of the row's own day, never judged and never paid. Each Sleep row (a day) opens to a day page titled "MM/DD/YYYY data" listing that day's nights and naps with a length and a span ("11/11, 11:30 PM-11/12, 5:50 AM"), so the walk opens the newest 14 days (31 in an all read) and `HuaweiRecords.sleepDay` makes each session a real Sleep record (id from its start); a day with real times keeps no length-only reading and replaces today's night from the Sleep page. Each four-dots menu is opened once and one item is chosen from it; after an All data page the walk presses Back to the card page before the next pill or menu item, so Emotional wellbeing's second pill and Cycle Calendar's Period records are reached from their own page, not from a history list. The page itself, the menu button, its items and the pills are all looked for in every Huawei Health window, because a web page or a card page may draw in one of its own. Probe 13 (22 November 2026) still found Heart and Emotional wellbeing as two empty `FrameLayout` windows with no words, a menu button or pills, so after that blank page the walk goes Back and taps the card once more **with a real touch at the card's middle**; if it still shows no words, the walk taps the top-right four-dots position itself and reads the native menu there (the owner's screenshots show All data at the top of it). Cycle Calendar's `More options` menu did not appear after a click or a tap in that probe, so the walk also tries a long press, then a real tap at the top-right position, and accepts a menu item about periods whose wording is not exactly "Period records". Emotional wellbeing's **All data** page carries the same **Emotion** and **Stress** tabs, so the walk opens that page once and reads both tabs there; if the tabs are out of reach of accessibility they are tapped by position at the top left. The card page is scrolled only after the pills are read. Health Clovers and Activity rings are not opened (`HuaweiWalk.SKIP`). The Weight and Cycle Calendar pages are web pages: their menu button is described "More options" and their menu appears a moment later. The Steps summary declares a click but refuses it, so when every click is refused the service **touches the middle of the control** (`dispatchGesture`, `canPerformGestures` in `huawei_reader.xml`), for that fallback only; it never swipes or types.
3d. **Steps history; skip rule (0.225.2; 22 November 2026).** The Steps summary at the top declares a click but refuses it, so the service touches its middle (`canPerformGestures`); the Steps page opens, and its four-dots menu → All data ("All recorded data", `PrivacyDataModelActivity`) lists a day per row ("N steps", "MM/DD", month headers): each row is saved as that day's steps, history only (steps pay for today alone), the largest count seen for a day winning. The Weight and Cycle Calendar pages are web pages whose menu items ("Share", "All data", "Period records", ...) are plain text with no clickable parent; an item is found by its text and clicked or touched. The skip rule (a new read only) counts only rows an All data list gives (ranges, lengths, steps), never the single value the Health page shows for today: a newest saved row of yesterday means nothing is new until tomorrow.
3e. **Performance (0.225.2).** The walk runs on a thread of its own (reading nodes, clicks, scrolls and touches are fine from any thread); `done` and switching the service off are posted to the main thread. Heal's tables keep their rows as last written (opened, memory only) and use them only while the database provably matches (`HealTables`, same sealed state and key object, per-table row count and text length); a save then seals only the changed rows. Considered and not built: writing Heal in the background (see the changelog notes of 0.225.2).
3a. **Controls debug script (0.225.1).** `tools/huawei/controls.py` runs on a computer: it takes `adb shell uiautomator dump` of the Huawei Health screen in front (no accessibility service on the phone, so banking apps are unaffected), hides every digit, and reports which controls can be tapped, long-pressed or scrolled, how each card title is reached (nearest clickable ancestor within 8 levels, as the walk does), the main scroller, and a check of every card the walk opens. It only reads. The same check is in the app (Heal → Connections → Huawei Health → Show controls and pages, `HuaweiControls`): each captured control carries its Android actions (`do:click`) and the element that takes a tap on it (`via:`), and a Walk log lists each step of the last read; both are in the saved sample, digits hidden, memory only.
3f. **Four outcomes per page; no wasted retries (0.237.0).** Every page or metric the walk meets has one of four outcomes from the capability table (`HuaweiSources.SOURCES`: page → how it is reached → what HEAL can extract → support status), and Heal → Connections → Huawei Health shows them per card with the number of records the preview would add (`HuaweiSourceReport`): **Read** (the Health page's cards, Steps, Exercise records, Weight, Sleep, Skin temperature, SpO₂), **Can't open / INACCESSIBLE** (Heart and Emotional wellbeing: probe 14 shows they open as blank windows with no words and no menu, plus any page whose All data does not open; today's heart rate and stress still come from the Health page cards), **No record model / NO_MODEL** (Sleep stages, Cycle Calendar's period days, Health Clovers, Activity rings: reliable values HEAL keeps no record for), and **Not supported / UNSUPPORTED** (the Sleep Week/Month totals, Huawei's Health insights, and a card HEAL does not know). The walk records what it saw per card in memory (`HuaweiVisit`: the card's title, its Android activity class, whether it showed words, whether its menu opened, whether it was skipped), and a page that is not Read is opened **once** and then reported: its menu, pills, All data and top-right taps are never retried for the whole budget. The saved probe carries the same list in a "Visits" section, digits hidden.

4. **What is saved.** Captures stay in memory. From them `HuaweiRecords` makes HEAL records in its existing families, provider `huawei-screen`, shown as a preview (to add, already here, flagged) before **Add them** saves anything (HEAL.md 10.3): workouts from Exercise records (type, start, duration, distance in km; the year from the month headers), weight and body fat from the Weight page (its own date and time; the year from the weekday), and **today's** night from the Sleep page (its length, ending at the Health page's get-up time: the page shows no bedtime, so another night has no times and is left out, never guessed). Ids come from each record's own time, so reading again adds nothing twice. The Weight page's **All data** (`HealthDataHistoryActivity`: a day header, then that day's readings with their own time) is read as history too, so weight and body fat get a record per measurement with its own date and time. **Save, numbers hidden** writes the capture to a `.txt` file (every digit of Huawei's text replaced by `#`), for probing a layout change without personal values.
5. **Rewards.** A session read this way pays like a manual one (`HealRules.paysLikeManual`) but may have ended up to 8 days ago (`IMPORT_FRESH_MILLIS`), because reads are nightly to weekly; a session flagged as a possible repeat of one already here pays nothing. Sleep and measurements never pay, as by hand. Health Connect imports pay nothing. Amounts and caps are in `docs/economy.md`.
6. **Nightly to weekly.** Heal → Connections → Huawei Health has **Remind me to read**: Off, Nightly or Weekly, at a time the player picks (21:00 by default). The reminder counts from the last read, follows the shared quiet hours and focus deferral, and says only "Time to read Huawei Health". Weekly keeps every workout (Exercise records is a list) and the latest weight; only a daily read keeps every night's sleep, because only today's night has times.
7. **English only:** the parsers match Huawei Health's English labels and card titles (a card is a `function_set_items_title` element followed by its data, unit and bottom-left elements), not view ids, some of which are misleading. They use only the cards HEAL has records for and the step count, and save nothing from the rest (Cycle Calendar's Period records included) even though the capture contains them.
8. Like every HEAL record, anything saved is sealed (11), never logged and never sent anywhere.

**Steps and readings** (`MODULES/HEAL.md` 22B): Heal keeps daily **steps**, **heart rate**, resting heart rate, **SpO2**, **skin temperature**, Huawei's **stress** number and **sleep score** as their own records, read from the Health page, the Sleep page and the step notification, as history only; Heal never says whether a reading is good or bad. Steps and reading records pay as `docs/economy.md` says; no reading pays for its value. Ids come from each record's day, so reads never duplicate.

**Step notification listener.** The listener reads the active notifications when Android binds it, then lets go (`requestUnbind`), so it does not sit in the resident list. It is bound again (`requestRebind`, only while notification access is granted) when the app or the PokéLife launcher comes to the front; both services stay normal enabled components, because Android binds a listener or accessibility service only after the player switches it on. A step count is dated by the notification's own post time, and the largest count of a day wins. Only the notification still showing when the listener next binds is read: if Huawei Health replaces it after midnight and the app or launcher was not opened before then, that day's final count is not seen and the day keeps the last count that was read.

## 6. Calendar integration and ownership

### 6.1 Baseline: Android Calendar Provider, read-only for others' events

PLAN may read calendars exposed through Android's Calendar Provider after the user explicitly selects calendar access. This can include Google Calendar calendars synchronized to the device and other compatible calendar providers. PokéLife must not assume every Google account calendar is available locally.

- User chooses which calendars to display.
- Imported events are provider-owned, read-only projections.
- PLAN tasks and native PLAN events remain PokéLife-owned.
- External events do not automatically become tasks, commitments, or reward events.
- A linked PLAN task stores an opaque provider/calendar/event reference; it does not create an editable duplicate.
- Provider event changes/deletions are reflected when the provider query refreshes; stale cached projections are labelled or removed according to the module's display policy.
- Calendar access denial does not disable PLAN's native calendar.
- The exceptions are PLAN's own events and, in the one synced calendar, the events the player made there (brought in as PLAN events), under the opt-in sync in 6.2; everything else stays read-only.

### 6.2 Writes and synchronization

**Owner decision of 24 November 2026:** PLAN gets an optional two-way sync of its own events with ONE calendar on the phone, through the Calendar Provider (no Google sign-in, OAuth, network code or tokens; the phone's Google account sync carries it to Google). This reverses "do not implement bidirectional synchronization in the initial release" for this one feature only; "local-only, no accounts" stands. The rules are in `../MODULES/PLAN.md` 8.1A: off until turned on, `WRITE_CALENDAR` asked then, the player picks one writable calendar, only PLAN's own events and only PokéLife's own provider rows are written, changed or deleted (a link per event and a fingerprint in the `integrations` document), the calendar's version wins a true conflict with PLAN's offered back through Undo, it runs only with the app in front, and nothing is a reward input. Second decision the same day: events the player makes in that one calendar are brought in as PLAN events (PLAN.md 8.1A rule 3A: player is organizer, only what PLAN can hold, linked, pays only through a companion the player assigns, no Undo for a calendar-side import). Every other calendar and every event the player did not make stays read-only and never becomes a task or pays.

Anything beyond this (importing events made in Google as PLAN events, syncing tasks, or another module's data) is not approved. Avoid a second source of truth and conflict-resolution burden elsewhere.

For any other event creation, prefer Android calendar intents that hand the user to the chosen calendar application for confirmation. Direct Calendar Provider writes require a separately reviewed permission, user consent, account/calendar selection, conflict behavior, and tests. Never silently edit or delete external events.

Calendar access is an Android platform integration, not a dependency on Google OAuth. A separate Google API integration should be considered only if a concrete feature cannot be met by the platform provider and the required scopes are approved.

## 7. Financial integrations in the Philippines

### 7.1 MINT baseline

MINT is manual-first and must be fully usable offline. Supported statement/file imports are the primary automation path until a provider offers verified personal-account access under acceptable terms.

Investigate GCash, Maya, MariBank, GoTyme, and UnionBank independently. For each, verify:

- Is there a documented API for personal account aggregation, not merely merchant payments or partner collections?
- Is independent developer access available in the Philippines?
- Is production approval required, and can an individual/local app qualify?
- Which account types, balances, transaction histories, and pagination ranges are supported?
- What OAuth scopes, credentials, refresh rules, fees, and rate limits apply?
- What user consent, security review, and provider terms govern access?
- Can the integration be maintained without scraping or fragile automation?

A public developer portal, sandbox, payment API, QR interface, or merchant API is not evidence of personal account aggregation. No bank/e-wallet auto-sync may be promised without a verified, approved, end-to-end mechanism for the exact use case and account type.

#### 7.1.1 Findings (desk research)

Public pages only; nothing tested with a bank account. Every provider is **unsupported** until verified.

| Provider | Finding | Status |
|---|---|---|
| UnionBank | API developer portal since 2017 (hundreds of APIs: bills payment, transfers, inquiries, a payment gateway), aimed at fintechs and businesses; no source on personal-account transaction history for an individual developer's app, OAuth consent or current terms | **Unverified.** The owner would see whether an individual can register and which APIs return a customer's own transactions with consent |
| GCash, Maya, GoTyme, MariBank | No public documentation of an API giving a user's own transaction history to a third-party app; a vendor page lists them but describes protocol reverse engineering, which this spec forbids | **Unverified; treat as unsupported** |
| BSP Open Finance (Circular 1122, 2021) | A framework for permissioned data sharing with authorized third-party providers; no official list of live participants or endpoints, and unclear whether an individual developer's app can qualify | **Watch, do not build.** If real, the route is an official consented API (7.2, item 4) |
| Notification reading | `NotificationListenerService` | **Built**, opt-in for MINT's bank alerts (7.3, `MODULES/MINT.md` 10.4); no provider connection |

The statement file (CSV) stays MINT's only integration. Next evidence the owner can supply: whether UnionBank's portal lets an individual register and which APIs return own transactions; which statement formats GCash, Maya, GoTyme and MariBank let the user export, with one sample each (values removed) to test the reader's column guesses; whether the BSP lists any participant an individual can use.

### 7.2 Approved integration order

1. Manual account, transaction, transfer, budget, and bill workflows.
2. CSV and spreadsheet imports with preview and reconciliation.
3. User-selected statement/document imports for supported formats.
4. Official provider APIs only after access and production eligibility are verified.
5. Optional local notification parsing only after a separate privacy/platform review; not part of the initial release. **Decided 25 November 2026:** built for MINT as an opt-in capture, see 7.3.

Never request/store bank passwords, app PINs, CVVs, OTPs, recovery codes, or session cookies. Never scrape authenticated app screens, use Accessibility Services to extract banking data, bypass app protections, or execute transfers/payments. PokéLife is not a payment initiation application.

### 7.3 Notification parsing

Android `NotificationListenerService` can expose notifications across applications after a broad user-granted special access. It is not a narrowly scoped bank permission. It can reveal unrelated private notifications.

**The separate review this section asked for was made and the capability was approved for MINT (owner, 25 November 2026).** It is built as an explicit opt-in experimental capability (`MODULES/MINT.md` 10.4), and these are the rules it keeps:

- **Off until the player turns it on, per source.** MINT → Settings → Record from messages has two switches, **Bank notifications** and **Bank text messages**, both off on a new save. Each asks for Android's own access first: notification access through Android's settings page, text messages through the runtime `RECEIVE_SMS` prompt. Turning a switch off disables that Android component, so the system does not bind the listener and does not wake the receiver: nothing is read while it is off.
- **A narrow allowlist, checked before any content.** Notification packages and SMS sender ids for GCash, Maya, MariBank, GoTyme, UnionBank, BDO, BPI, Metrobank, LandBank, CIMB and SeaBank only (`CaptureSource`). A notification is dropped on its package name before a field is read; a message on its sender before the body is touched. Nothing from another app is read, kept, counted or logged.
- **Processed locally, and the raw text is never persisted.** The parse runs on the phone; the raw text is used for it and nothing else. What waits for the player is only the minimum parsed fields (amount, direction, merchant, day, last four digits, source), sealed with MINT's own key in a small box, capped at 40 alerts and a week, never written in the clear.
- **A proposal, never a posting.** An alert becomes a `PENDING` record in the same "To confirm" box as an interest estimate and is posted only when the player confirms it; the player can adjust the amount, skip it, or erase every waiting proposal. Nothing is inferred from an incomplete or ambiguous message: a message whose amount or direction cannot be read is refused rather than guessed, and an OTP, PIN or code delivery is always refused.
- **No balance is ever inferred, and no SMS fallback:** the two sources are separate switches; text messages are never read because notifications were denied.
- **The alert's content never reaches a notification.** When one is waiting, MINT posts one notification on its own channel that says only that a transaction is waiting (or how many) and to open MINT; it carries no amount, bank, merchant or date, the lock-screen version says only that one is waiting, and it is cancelled as soon as the box is read or erased. Android's `POST_NOTIFICATIONS` is asked for when a capture switch is turned on, never at onboarding; a denied permission only means no notification, never that reading stops.
- **Revocation and deletion.** Switching a source off stops it; "Erase captured alerts" deletes every waiting proposal and clears the box. A confirmed record is ordinary MINT history and follows MINT's own delete/erase rules.
- **What it never does.** It does not log in, scrape, use accessibility, ask for a password, PIN, OTP, CVV or code, or claim a provider connection. It is not a sync and never promises automatic balance updates.

### 7.4 Financial privacy and progression

MINT data never enters WORLD, launcher surfaces, generic logs or other module payloads (15). Financial outcomes and amounts never affect rewards or Pokémon access.

## 8. Contacts, files, media, location, sensors, and other Android capabilities

### 8.1 Contacts (LINK)

LINK is manual-first. For import, prefer Android's contact picker or selected-contact flow over broad address-book access. If broad `READ_CONTACTS` access is required for a clearly explained import scope, request it only after the user invokes import.

- Preview selected contacts and proposed matches before import.
- Preserve source provenance for imported fields.
- Never upload contact lists, scan messages/call logs, monitor social apps, score relationships, or send messages automatically.
- LINK notes and relationship context remain local and are never included in progression events.
- Disconnecting contact access stops future reads but does not delete imported LINK records unless the user chooses deletion.

### 8.2 Files, photos, and sharing

Use Storage Access Framework for documents and Android Photo Picker for images. Android's photo picker may take several images at once, and LINK's "From files" uses SAF `OpenMultipleDocuments` to read only the images the player picks from a documents provider (including a cloud app's folder), with no broad permission and no account inside the app. Do not request broad storage/media access for normal import/export. Use content URIs and temporary URI grants; do not assume a selected URI remains accessible forever unless the permission is persisted where supported.

All imports must validate file size, type, encoding, schema, archive paths, and content. Reject path traversal, decompression bombs, malformed archives, and unsupported versions. Preview changes before commit. Treat files from other apps as untrusted.

Use Android Sharesheet/intent flows for explicit user-initiated sharing. Do not silently upload or share a file. Remove temporary export files after the documented retention period and ensure they are not exposed through public storage by default.

### 8.2A OneDrive photos (LINK; owner, 25 November 2026; built 0.247.0)

LINK may read photos from the player's Microsoft OneDrive as one **optional** photo source for memories (LINK.md section 6.2). This is an official provider API capability, not a PokéLife account and not cloud save: no LINK record, note, memory, era, person or setting is ever uploaded, and the app still has no server, no sync and no mandatory account (section 1, rule 1). It is Android only; desktop and web have no source.

- **Read-only, least privilege.** Microsoft Graph v1.0 read calls over HTTPS (`HttpsURLConnection`, no third-party SDK) with the exact delegated scope `Files.Read offline_access` (no selected-file scope, no write scope). LINK never creates, uploads, edits, moves, renames or deletes a OneDrive file, and it never changes a folder.
- **Sign-in: authorization code + PKCE, no client secret.** Microsoft's authorize endpoint (`https://login.microsoftonline.com/common/oauth2/v2.0/authorize`) in the phone's browser with S256 PKCE and a random `state`; the redirect URI is exactly `pokeplan-onedrive://auth` (custom scheme `pokeplan-onedrive`, host `auth`) and is handled on cold start and in `MainActivity.onNewIntent`. Tokens come from `https://login.microsoftonline.com/common/oauth2/v2.0/token`; Graph reads use `https://graph.microsoft.com/v1.0/me/drive`. Only HTTPS is used.
- **Registration and build.** The owner registers a Microsoft Entra app: public client, personal accounts supported, redirect URI `pokeplan-onedrive://auth`, delegated Graph `Files.Read`. The app id is compiled from the `onedriveClientId` Gradle property or `ONEDRIVE_CLIENT_ID` into `BuildConfig.ONEDRIVE_CLIENT_ID`; the release workflow and the `apk`-labelled CI build pass the `ONEDRIVE_CLIENT_ID` repository secret. No client secret is used or stored; an empty id leaves OneDrive off and the UI says it is not set up.
- **What is stored.** Tokens are sealed with an Android Keystore AES-256-GCM key (alias `pokeplan-onedrive`) into `filesDir/onedrive-tokens.bin` (temporary write, then rename), are never in the save, an export, a backup or a log, and disconnect deletes the file; refresh is on demand and rotates Microsoft's refresh token. Browsing thumbnails are fetched per tile and held in memory, not cached on disk. An added photo is downloaded (at most 20 MB) and stored as a normal LINK memory photo (base64 in the Link document), so it follows LINK's save, export and backup rules; no OneDrive reference record is kept.
- **Player-invoked only.** Sign-in opens only from LINK's own connect action, after a plain explanation of what is read; folder browsing, thumbnail loading and the add-to-memory download happen only while the player is in the OneDrive place. No background scan, no watch, no automatic import, no notification from OneDrive content.
- **Limits and errors.** A folder listing is read up to 4 MB, a thumbnail up to 2 MB and a full photo up to 20 MB (refused over); failures show a short Microsoft/Graph message.
- **Failure and revocation.** Offline, a revoked permission or an expired sign-in keeps every LINK record and every photo already added readable, and the place shows needs sign-in or unavailable; manual memory entry never depends on OneDrive. Disconnecting removes the tokens; photos already added stay as LINK's own local photos.
- **No media permission.** The app requests no broad storage or media permission for this; OneDrive is a network read of files the player explicitly adds, alongside (not instead of) the Android Photo Picker of 8.2.
- **Provider review and open items.** The Microsoft provider due diligence is still open (app registration, terms, privacy notice) and Microsoft Graph OneDrive read access is not advertised until it is done. Multi-select, cache management and clearing (with a dedicated photo store/cache) and a run on a device are also open.

### 8.3 Location

Location is foreground and user-initiated only. Prefer approximate location and one-shot/current readings where sufficient. No background tracking, location history, geofencing, location-derived Pokémon encounters, or location-based WORLD rewards in the baseline product.

If a user saves a location in a module, explain what is saved and let them remove it. A weather or local utility may use a manually selected city instead of device location. Do not persist raw coordinates for a transient lookup unless the feature requires it and the user consents.

### 8.4 Sensors and microphone

GEAR may use compass, level, barometer, accelerometer, light, proximity, or microphone-based sound measurement where supported. Register sensor listeners only while the relevant tool is active; stop on exit/background and release resources. Do not collect sensor streams in the background or turn sensor readings into HEAL records without a separately specified user action and data contract.

Audio measurement should process locally and should not record/store raw audio unless the user explicitly invokes a recording feature with a separate retention control. A sound-level estimate from a phone microphone is not a calibrated or certified measurement.

### 8.5 Camera, QR, and barcode

Prefer system camera/scanner intents or a narrowly scoped camera implementation. A QR/barcode scan should process the selected frame locally unless an explicit lookup service is invoked. Do not upload images by default. Explain when an online barcode or food lookup sends a code to a third-party service.

### 8.6 Bluetooth and wearables

Request Bluetooth permissions only for an actual, verified direct connection workflow. Health Connect/Huawei cloud or provider interoperability does not automatically require PokéLife to pair directly with the watch. Do not scan continuously or collect nearby-device inventories.

### 8.7 Launcher and installed applications

The optional launcher may need to enumerate launchable applications. Prefer package visibility declarations and launcher intent queries. Use `QUERY_ALL_PACKAGES` only if broad enumeration is genuinely necessary and permitted by the intended distribution channel. Installed-app information is used only to render launcher functionality; it is not logged, transmitted, profiled, or used for progression.

### 8.1A In-app integrations (owner, 24 November 2026)

Two modules that must stay separate meet in an integration layer that neither depends on: `app-core` `app.pokeplan.app.integrations` (`IntegrationStore`, `LinkInPlan`) and `composeApp` `ui/integrations`. The first one joins LINK and PLAN.
- **What crosses:** LINK dates and open follow-ups with a due day (not ones made a PLAN task) show in PLAN's calendar as read-only all-day entries; LINK people can be tied to a PLAN event by opaque person id (at most 12 per event).
- **What never crosses:** nothing is written to the phone's calendar, reminders, the launcher or widgets; nothing pays and nothing is a reward input; no figure, note or private word goes into PLAN's data, and `PlanEvent` and `Person` carry no link. LINK locked hides the entries. Two switches in Me > Settings ("LINK in the calendar": Dates, Follow-ups, on by default).
- **Calendar sync (24 November 2026, second use of the layer):** PLAN's own events sync two-way with one phone calendar (PLAN.md 8.1A; `CalendarSync`, `PlanCalendarSync`, `CalendarEventMapping`, `CalendarWriter` in `app.pokeplan.app.integrations`; androidApp `AndroidCalendars`). The links (series id to provider event id, fingerprint) live in the same document. Events the player makes in that calendar are brought in as PLAN events (3A, `CalendarWriter.importable`). Companion, people, rewards, tasks and LINK dates never cross.
- **Storage and backup:** one saved document named `integrations` in the `live_document` table (no schema migration), in the backup bundle's known set and erased by Delete all data. Links to an event or person that no longer exists are dropped when the app opens.
- **An event and a memory (owner, 25 November 2026; REP-088, built 0.263.0):** a PLAN event may link to a LINK memory — "Save as Memory" after the event creates the memory with the event's day, title and associated people prefilled, and an existing memory can be attached to an event — by opaque series and memory ids in the same integration document. The memory is independent of the event: deleting or editing the event never deletes or edits the memory, and no calendar row is duplicated. Nothing pays and no note or photo content crosses.

## 9. Shared reminder and notification architecture

This section is the sole authority for shared reminder scheduling, policy, delivery, action routing, lifecycle recovery and reminder privacy. Module specifications define only their source records, preferences and action meaning. `DATA_AND_MIGRATIONS.md` owns the physical schema, transactions, backup/restore and migrations.

### 9.1 Purpose and boundaries

Provide one reliable, predictable reminder system for PokéLife without merging module data ownership. A module owns its source record and decides whether it needs a reminder. The shared feature owns scheduling policy, delivery mechanics, global quiet hours, focus deferral, recovery, and notification action routing contracts.

The shared reminder feature does not own tasks, routines, bills, household chores, important dates, calendar events, or any other business record. It stores only the minimum scheduling metadata needed to deliver or cancel a reminder.

Initial participating modules:

- **PLAN:** tasks, recurring-task occurrences, and supported PLAN-created events.
- **CARE:** self-care routines and optional Diary Card reminders.
- **BASE:** household chores, maintenance, and low-stock reminders when those capabilities are implemented.
- **LINK:** important dates and user-created follow-up reminders when implemented.
- **HEAL:** explicitly enabled medication-schedule and appointment reminders; optional hydration or health-goal prompts. HEAL owns the source records and preferences; the scheduler never validates or changes medication instructions.
- **MINT:** bill-occurrence reminders. MINT owns the bill records, status, preferences and action meaning (9.13).
- Other modules may integrate only through this contract and an approved module-specific specification.

### 9.2 Ownership and contracts

#### 9.2.1 Source ownership

The module that owns the source record owns:

- Whether a reminder is enabled for that record.
- The reminder's meaning and user-facing content.
- The canonical record and its completion state.
- Validation and execution of source-specific notification actions.
- Module-specific reminder preferences.

The shared feature owns:

- Scheduling and rescheduling requests.
- Global quiet-hours enforcement.
- Notification permission and channel integration.
- Focus deferral policy and pending-request revalidation.
- Android notification delivery and action dispatch.
- Reboot/app-update recovery.
- Duplicate prevention and notification identity.
- Cleanup of stale scheduled requests.

A notification action must call the owning module's adapter. The shared feature must not directly mutate module records or depend on module data classes.

#### 9.2.2 Reminder request

Define a platform-neutral request contract. Minimum conceptual fields:

- Stable reminder ID.
- Source module identifier.
- Opaque source-record reference.
- Reminder kind/action identifier.
- Requested delivery instant or local schedule definition.
- Time-zone/local-time interpretation where relevant.
- Minimal display title/body or a module callback to resolve display text at delivery time.
- Requested actions supported by the source module.
- Module preference/enablement state.
- Focus-deferral preference.

Do not persist sensitive source content in the shared reminder store. Prefer opaque references and resolve current display content through the source adapter when needed. If Android requires notification text to be materialized, keep it transient and avoid logging it.

#### 9.2.3 Source adapter

Each participating module supplies an adapter that can:

- Validate whether the source still exists and is eligible.
- Produce or refresh a reminder request.
- Resolve minimal notification presentation data.
- Execute an allowed notification action in the owning module.
- Report source changes that require cancellation or rescheduling.

The shared scheduler must not import PLAN, CARE, HEAL, BASE, or LINK repositories directly.

### 9.3 Scheduling and time behavior

- Interpret user-entered times in the device/user's current local time zone unless the source record explicitly specifies another zone.
- Recalculate future reminders after time-zone or relevant system-time changes.
- Use stable IDs so recalculation is idempotent.
- Avoid duplicate alarms when the same request is resubmitted.
- Schedule only a bounded forward horizon where platform limits or reliability require it; modules remain the source of truth for future recurrence.
- Revalidate the source before delivery and before executing a notification action.
- Cancel or replace pending requests when the source changes, completes, is archived, deleted, or becomes disabled.
- Do not schedule reminders for past occurrences unless the source module explicitly requests a catch-up behavior.
- Do not create an unbounded backlog of missed reminders.

### 9.4 Global quiet hours

All modules respect one global quiet-hours policy.

Requirements:

- User-configurable start and end times.
- Correct handling of windows crossing midnight.
- Local-time interpretation and predictable time-zone-change behavior.
- No module may silently bypass or override global quiet hours.
- Module-level reminder toggles may disable requests but cannot bypass global quiet hours.
- A reminder suppressed by quiet hours must follow a defined next-eligible-delivery policy; do not silently shift it to an arbitrary time.
- If a reminder becomes stale before the next eligible time, discard it after source validation.

Global quiet-hours settings are managed once in the app's shared settings surface. Module settings link to or summarize the policy but do not create independent copies.

**One Notifications page** (Me, Settings, Notifications). It shows what Android allows (notifications and exact alarms: Allowed or Blocked, with a key to the phone's own screen while blocked, read again when the player returns to the app), the quiet hours, and one reminders switch per module (Plan tasks, Care routines, Base, Link, Mint bills, plus World jobs, the streak reminder and the weekly recap). A module keeps its own lead time, hour and naming choice in its settings, shown there only while its switch is on. Heal has no switch: its reminders are the times the player adds.

### 9.5 Focus deferral

Reminders marked as deferrable are held while a qualifying focus session is active.

Requirements:

- Shared feature consumes a small focus-state contract: active/inactive, session identity, and expected end time if known.
- PLAN Focus publishes state; the reminder feature does not depend on PLAN implementation classes.
- Revalidate pending requests when focus ends, pauses, is cancelled, or is restored after process death.
- After focus, re-check source eligibility and global quiet hours before delivery.
- Do not release completed, archived, deleted, disabled, or otherwise stale reminders.
- Do not queue an unlimited backlog; coalesce or discard stale repeated reminders according to source policy.
- Avoid rescheduling loops when focus ends during quiet hours.

Modules may mark a reminder non-deferrable only where a clear, user-approved safety or time-critical requirement exists. No current module should assume this exception by default.

### 9.6 Notification delivery and actions

#### 9.6.1 Delivery

- Use shared notification channels/categories with clear user-facing names.
- Respect Android notification permission state.
- Do not repeatedly prompt after denial.
- Provide a clear settings route when notifications are unavailable.
- Notification text is concise and module-appropriate.
- Use accessible actions and do not rely on color alone.
- Avoid notification spam; use stable notification IDs and update/cancel existing notifications where appropriate.

#### 9.6.2 Action routing

Each action contains the reminder ID, source module, opaque source reference, and stable action key. On action:

1. Resolve the registered source adapter.
2. Validate the source record and current eligibility.
3. Confirm the action is still allowed.
4. Execute through the owning module.
5. Reconcile/cancel/reschedule the reminder request.
6. Return a clear result to the notification layer.

Examples:

- PLAN task completion routes to PLAN and applies PLAN task completion/reward rules.
- CARE routine completion routes to CARE and applies CARE completion/reward rules.
- HEAL medication/appointment actions route to HEAL for source validation and module-specific handling; reminder delivery or dismissal does not confirm medication adherence or create a health record.
- Snooze changes delivery time but does not complete or modify the source record.
- Dismissal dismisses the notification; source-specific future scheduling follows the module's policy.

Never route a CARE routine action through PLAN or create a duplicate PLAN task.

### 9.7 Snooze and repeat behavior

- Snooze duration and availability are defined by the source module or reminder kind, within shared platform limits.
- A snooze creates a rescheduled delivery for the same logical reminder, not a new business record.
- Repeated snoozes must not create duplicate alarms or notifications.
- A source completion or deletion cancels snoozed deliveries.
- Recurring reminders are regenerated from the source record, not by an unbounded notification repeat loop.

CARE's existing 30-minute routine snooze may be retained as a CARE-specific behavior if approved by CARE acceptance testing. It is not a global default.

### 9.8 Android lifecycle and recovery

The implementation must handle:

- App process death.
- Device reboot.
- App update/replacement.
- Permission revocation or channel disablement.
- Time-zone changes.
- System clock changes.
- Source-record edits while alarms are pending.
- Multiple modules requesting reminders at the same local time.

Persist enough scheduler metadata to reconstruct pending requests, but treat module repositories as canonical. On recovery, rebuild or reconcile scheduled alarms from active module requests; do not assume the scheduler database alone is authoritative.

### 9.9 Privacy and data minimization

- Shared scheduling stores opaque source references and only necessary timing/action metadata.
- Do not copy task notes, journal content, Diary Card values, mood/context data, health measurements, medication names or instructions, symptom notes, appointment details, meal/nutrition details, financial transactions, relationship notes, or household inventory details into shared reminder storage.
- Do not log sensitive notification text or source data.
- Notification content should be resolved as late as practical and respect Android lock-screen privacy settings where available.
- Shared reminder infrastructure does not send content to WORLD or the reward system.
- Reminder delivery itself is not a reward-bearing action.

### 9.10 Implementation architecture

Platform-neutral roles: a scheduler (schedule, update, cancel, reconcile), a policy (quiet hours, focus deferral, permissions, eligibility), a repository (stable scheduling metadata and notification identities), a delivery layer (Android notification creation, update, cancellation and action dispatch), a source adapter per module (validation, display resolution, action execution), a focus-state provider that does not couple to PLAN classes, and a recovery step (reboot, update, process death, system changes). The classes that fill these roles are listed in 9.11.1. Domain policy stays testable outside Android APIs; the AlarmManager, WorkManager and notification choices belong in the Android adapter.

#### 9.10.1 Shared persistence contract

Reminder requests and delivery metadata follow the shared database, stable-ID, transaction, backup/restore and migration rules in `DATA_AND_MIGRATIONS.md`. Reminder records never replace the owning module's canonical business record.

### 9.11 Migration from existing implementations

HEAL, MINT, BASE and LINK used the shared contract from their first reminder; PLAN and CARE have been cut over from their per-module alarms. A later cutover of a source repeats the same rules: map each legacy identity to a stable reminder id; rebuild active requests from the canonical module records; preserve enablement, schedule, snooze and action semantics; make sure old and new schedulers cannot deliver the same reminder; cancel legacy alarms only after the new request is registered; never replay completions or reward events; make every step idempotent. CARE keeps routine ownership, completion and reward history; PLAN keeps task and occurrence ownership and reward accounting; the shared layer owns neither.

#### 9.11.1 As built

- **One scheduler, one receiver.** `ReminderRequest` (stable id `MODULE|ref|kind|occurrence`), `ReminderPolicy.plan`/`deliverAt` (global quiet hours with midnight wrap and next eligible time; focus deferral re-checked at delivery, no loop), source adapters (`PlanReminderAdapter`, `CareReminderAdapter`, `HealReminderAdapter`, `BaseReminderAdapter`, `LinkReminderAdapter`), `ReminderRouter` (a stale or unknown source cannot mutate; snooze and dismiss never complete or pay), `ReminderSnooze`, and on Android `SharedReminders`, `SharedReminderReceiver` and `ReminderBootReceiver`. Active requests are rebuilt from the canonical module saves on every `reschedule`.
- **Persistence.** Only snoozes and repeats are stored, as opaque ids and times, in table `reminder_hold`. `reminder_migration` marks the cutover; `LegacyReminders.cancelOnce` cancels the old alarms after the new ones are set.
- **Nightly refresh.** The planner looks a bounded horizon ahead and runs when the app opens or an alarm fires; one inexact alarm a night at 04:00 local (`setAndAllowWhileIdle`, `ACTION_REFRESH`) plans everything again.
- **Snoozes per module:** PLAN 10 min / 1 h / tomorrow (and its own repeat, bounded to 3); CARE 30 min; HEAL uses CARE's. A snooze survives a reboot because it is stored.
- **Stay in `AppAlerts` on purpose:** focus halfway/end alerts (the session's own feedback), World jobs and GEAR timers (user-started timers, not reminders on a source record). The streak reminder and weekly recap are PLAN nudges on the shared scheduler (`PlanReminderAdapter` kinds `streak` and `recap`): a streak request for each of the next three days at the chosen hour, the recap for the next Sunday at 18:00; a request inside quiet hours is not made, and `isStillWanted` drops one whose day has gone by, so a nudge held until morning is dropped instead of nagging. No repeats, no actions.
- **BASE and LINK reminders** are off by default, switched on per record behind a module master switch, with a lock-screen text that never names a record. A request holds an opaque record id, the kind and a day; unlocked text names a record only by the user's choice. A request that fires after the date it was made for has passed is stale and dropped. Notification actions never change a record except a chore's or follow-up's own completion ("Done"), which pays once like ticking it in the module; maintenance, restocking and warranties have no such action. Details are in `MODULES/BASE.md` and `MODULES/LINK.md`.
- **Not run on a device:** the alarms, notifications and buttons themselves. Phone checks: a routine a few minutes ahead notifies, Done ticks it and "In 30 min" returns it; a snooze survives a restart; a reminder during a focus session waits for its end; a 23:00 routine comes at 07:00; notifications posted by an older version still react to their buttons.

### 9.12 Testing and acceptance criteria

- Quiet hours work across midnight and time-zone changes; every module respects them; a module toggle disables only that module's requests.
- Focus deferral holds and releases eligible reminders without stale delivery or loops.
- Notification permission and channel disablement degrade gracefully.
- Re-submitting a request does not duplicate an alarm; editing replaces the prior request; completion, archive or delete cancels active and snoozed notifications.
- Reboot, app update, process death and system time or time-zone changes reconstruct and reconcile active reminders; concurrent reminders from different modules stay independently addressable.
- Actions execute only through the owning module's adapter; a stale or invalid source reference cannot mutate a record; reward processing stays with the source module and is idempotent.
- Snooze and dismissal never complete a source record, create a record or reward event, or record a dose as taken.
- Shared reminder storage and scheduler logs contain no module content (9.9); lock-screen content follows privacy settings.

### 9.13 Module-specific requirements remain in module specifications

- `../MODULES/PLAN.md` defines which PLAN records request reminders and how PLAN task/event actions behave.
- `../MODULES/CARE.md` defines optional routine and Diary Card reminder preferences and CARE action semantics.
- `../MODULES/BASE.md` defines BASE-owned chore, maintenance, low-stock and warranty reminder sources and their module-specific action semantics.
- `../MODULES/LINK.md` may define important-date/follow-up reminder sources when LINK is specified.
- `../MODULES/HEAL.md` defines optional medication, appointment, hydration and health-goal reminder sources and HEAL-specific action semantics. 
- `../MODULES/MINT.md` defines bill-occurrence reminder preferences and MINT-owned navigation/action semantics. Shared reminder metadata and lock-screen text hold no amounts, account details, provider names or payment status; a reminder action does not mark a bill paid.
- `../MODULES/GEAR.md` defines user-created timer identity, duration and completion semantics. Timer completion notifications use the shared Android delivery/recovery infrastructure and must not create a second scheduler.
- `../MODULES/WORLD.md` defines optional, event-driven WORLD notification requests and module-specific opt-in/rate limits. WORLD does not schedule independently or use private module activity as a notification trigger.

This document owns shared scheduling, policy, delivery, recovery, privacy, and cross-module acceptance. Module specifications must not restate these shared requirements.

### 9.14 Testing reference

Reminder tests follow [`TEST_STRATEGY.md`](TEST_STRATEGY.md); actual notification delivery and channel behavior need Android/device validation.

## 10. Backup, export, import, and deletion

`DATA_AND_MIGRATIONS.md` owns the detailed backup format, transaction consistency, restore staging, schema migrations, and recovery protocol. This section defines the privacy/integration requirements that those mechanisms must satisfy.

### 10.1 Backup

- No automatic PokéLife cloud backup or sync.
- Disable uncontrolled Android Auto Backup for sensitive app data; configure Android backup/data-extraction rules deliberately.
- Provide user-initiated full and module-specific local backup through SAF/document picker.
- Portable backup archives must be independently encrypted with a user-controlled passphrase and authenticated encryption; derive keys with a maintained password-based KDF.
- Local at-rest encryption may use Android Keystore-backed keys. Portable backup encryption must not depend solely on a device-bound Keystore key.
- Verify archive integrity, schema compatibility, module coverage, attachments, and checksums before restore.
- Restore to staging, validate, and activate only after verification; preserve a recoverable pre-restore state.
- Explain that copies exported by the user to other destinations are outside PokéLife's deletion control.

Built form: `DATA_AND_MIGRATIONS.md` 10.4.1 and 10.5.1; the portable backup is a passphrase-sealed file (PBKDF2-HMAC-SHA256, AES-256-GCM), separate from the device-sealed daily backups.

### 10.2 Export and import

Exports should be readable, documented, and module-selectable. Provide structured JSON and CSV where meaningful; CARE may additionally provide readable Markdown/PDF. Plaintext export of sensitive data requires explicit user action and a clear warning. Exports must not contain provider credentials/tokens by default.

Imports must be user-selected, validated, previewed, provenance-preserving, deduplicated, transactional or safely resumable, and must not silently overwrite manual records. Imported history must not replay reward events.

### 10.3 Disconnect and deletion

Provide controls to disconnect a provider, revoke provider tokens where supported, delete provider connection metadata, delete imported records by source, delete individual records, delete a module's data, and erase all local PokéLife data. These are distinct operations and must be explained separately.

Disconnecting a provider stops future access but does not automatically erase previously imported data. Deleting active records must also remove associated attachments, thumbnails, search indexes, derived caches, and temporary files as specified by `DATA_AND_MIGRATIONS.md`. App-managed local backups must be included in full erase scope; user-exported copies are not.

**Erase this device** clears the whole app, not only the save: every module's records and settings (Link and each sealed module included), the daily and kept-aside backups, the off-phone backup setup (folder permission released, sealed passphrase forgotten; what is already in the folder is the player's), the launcher's layout and weather cache, the reminder and alarm bookkeeping with every alarm already set, the crash guard, the widget's state and the old save files. It goes through every preferences file the app has, so a store added later is cleared too (`DeviceResetTest`). It does not revoke Health Connect grants (the player does that in Health Connect) or delete files the player saved elsewhere.

Deleting a module must not delete unrelated module records, shared Pokémon ownership, Bag, Poké balance, or reward history. Shared ledger integrity must be preserved using the retention/anonymization rules in `DATA_AND_MIGRATIONS.md` and the Pokémon/economy specification.

## 11. Encryption and local security

`DATA_AND_MIGRATIONS.md` owns the concrete database technology and migration implementation. The following security requirements are mandatory:

### 11.1 Data classes

| Class | Examples | Required handling |
|---|---|---|
| Packaged/public | Pokémon catalog, bundled assets | Normal read-only app resources |
| Internal | Theme, non-sensitive preferences, UI state | App-private storage |
| Personal | Tasks, household records, relationship records | App-private storage; no external transmission by default |
| Sensitive | CARE journals/diary, HEAL records, MINT accounts/transactions, LINK notes | Encrypted local persistence and carefully scoped access |
| Secret | OAuth refresh tokens, provider credentials, encryption keys | Android Keystore-backed protection; never in ordinary domain tables or exports |

### 11.2 Requirements

- App-private storage; no sensitive files in shared/public storage by default.
- Encrypt sensitive database content at rest using a maintained, authenticated encryption design (for example AES-256-GCM where appropriate).
- Keep key material outside the database; use Android Keystore for local key protection.
- Define key rotation, invalidation, recovery, and app-reinstall behavior. If a Keystore key becomes unavailable, do not silently reset the database or claim data recovery is possible.
- Never log sensitive content, raw provider payloads, tokens, authorization headers, or full financial/health records.
- Redact exception messages and diagnostics that could contain provider data.
- Exclude sensitive data from clipboard, screenshots, recent-task previews, and lock-screen notifications where platform controls permit; respect user-visible OS behavior and avoid false guarantees.
- Do not treat root detection or obfuscation as substitutes for data protection.

A biometric/app lock may be offered as an optional additional UI gate, but it must not be confused with database encryption and must not be required for basic local use.

**Built.** MINT and HEAL rows and every copy of them are sealed with separate Android Keystore keys (AES-256-GCM, non-exportable, fail closed without the key, no silent reset of a database the key does not open; `DATA_AND_MIGRATIONS.md` 10.4.1). CARE journals and diary, BASE and LINK are not yet sealed, so the Sensitive row above is met for Mint and Heal only. Open: whether to seal Care, Base and Link (an owner decision for each).

## 12. Network access and external dependencies

Network access is optional and feature-triggered. The application must not contact a PokéLife-operated backend because none is part of the product architecture.

Potential network-dependent features include optional exchange-rate retrieval, weather, food/barcode lookup, official provider adapters, and app/reference-content updates. Each feature must declare the destination, data sent, purpose, retention where known, and offline fallback.

Requirements:

- HTTPS/TLS only (cleartext only in a narrowly justified development configuration); no sensitive data in URL query strings.
- No embedded private API secrets. Public client identifiers are not secrets.
- OAuth authorization code with PKCE where supported, least-privilege scopes, explicit account/provider consent, and tokens only in secure platform storage.
- Request timeouts, bounded retries, rate-limit handling, and cancellation.
- No hidden background refresh of sensitive data; refresh only when user-enabled and provider terms permit it.
- No external SDK that introduces telemetry, ads, fingerprinting, or unapproved data collection.
- Review transitive SDK dependencies and their network behavior.
- Keep provider-specific network code behind adapters; domain logic must work with a fake/offline provider.
- Clearly label cached data with its last successful update and source. Never call stale snapshots live.
- A provider outage must not block local record creation, editing, search, reporting, or Pokémon collection access.

### 12.1 Dependency review

Before adding a provider SDK/library, review license, maintenance activity, Android/Kotlin compatibility, permissions, transitive dependencies, network destinations, data collection, security advisories, and exit strategy. Pin versions and update deliberately. A dependency that violates no-telemetry/no-advertising principles is rejected even if its primary API is useful.

## 13. Local-first and graceful failure behavior

| Failure | Required behavior |
|---|---|
| No internet | Core modules and local Pokémon systems continue working |
| Provider unavailable | Keep last permitted local snapshot; label freshness; offer retry/manual route |
| Provider login expires | Mark reauthorization required; do not erase imported history |
| Permission denied/revoked | Stop access; preserve manual/local data; provide settings route where useful |
| Health Connect missing | Manual HEAL workflows remain complete |
| Huawei integration unsupported | Show unsupported/blocked status; do not imply watch data is syncing |
| Calendar unavailable | Native PLAN calendar/tasks remain available |
| Bank API unavailable | MINT manual capture and statement import remain available |
| Notification permission denied | In-app reminders and due-state remain visible; no repeated prompt |
| File provider unavailable | Offer another SAF destination or local workflow; do not corrupt data |
| Provider returns malformed data | Reject/quarantine import; preserve prior committed data |
| Partial import/process death | Resume or retry idempotently; do not duplicate records/rewards |
| Network timeout/rate limit | Bounded retry with clear status; no infinite background loop |

Offline mode is not a degraded trial mode. It is the normal operating mode for PokéLife-owned data.

## 14. Consent and integration management UX

Provide one Integrations destination in Settings. Each integration page shows:

- What it does and why it is useful.
- Provider/platform name and classification (official API, Android platform, user-assisted import, unsupported).
- Data categories and read/write direction.
- Permissions and provider scopes currently granted.
- Last successful access/import and freshness.
- Current connection state and any limitation.
- Disconnect/revoke action.
- Separate action to delete imported data.
- Link to the owning module's data export/deletion controls.

Not built yet: each module's page lists its own integrations; there is no single Integrations destination in Settings.

Consent must be specific, informed, revocable, and not bundled with unrelated onboarding. If a provider changes scopes or starts collecting a new category, require renewed consent before using that capability. No preselected opt-ins for sensitive integrations.

## 15. Privacy boundaries: modules, shared services, and WORLD

The module ownership rules in `PRODUCT_BIBLE.md` and `DATA_AND_MIGRATIONS.md` remain authoritative. The integration layer enforces these additional boundaries:

| Owner | Data that stays private to owner | Permitted cross-boundary output |
|---|---|---|
| CARE | Journal, diary cards, mood, urges, skills responses, private notes | Only explicitly approved non-sensitive activity metadata; never content-derived events |
| HEAL | Measurements, body composition, calories, heart rate, sleep, nutrition, symptoms, diagnoses, medication, appointments, health notes | Minimal opaque eligible participation event evaluated within HEAL/reward boundary; WORLD receives generic reward result only. The `HealEvent` fields are in `MODULES/HEAL.md` section 22. |
| MINT | Account names, balances, transactions, merchants, budgets, savings, debts, bills, payment state | Approved abstract administrative Ledger Research event only; no values/circumstances |
| LINK | Contact names, IDs, phone/email/address, relationship categories, notes, associated-person identity | Opaque eligible follow-up ID, module, policy version, completion time inside local reward contract; no identity/context |
| BASE | Household contents, inventory, maintenance details, saved locations, private notes | Approved minimal Habitat Discovery event; no inventory or location payload |
| PLAN | Task titles/notes, projects, private calendar details, goals | Approved completion/progression event with opaque ID and policy metadata only |
| GEAR | Sensor readings, location readings, network/device diagnostics | No progression by opening/using tools; helper Pokémon remain visual helpers |
| WORLD | Campaign, expeditions, story, dungeon and game state | Own game data and generic committed reward outcomes; no direct access to private module repositories |

### 15.1 Reward-event contract

Reward evaluation should occur locally inside the owning module/reward boundary. Event payloads must contain only the fields needed for eligibility, deduplication, and policy versioning, such as an opaque event ID, module ID, event kind, occurrence timestamp, and policy version. Do not include source titles, notes, contact/provider IDs, financial values, health measurements, coordinates, or raw imported records.

The shared reward service returns a generic committed result. WORLD may present that result but cannot query the source record or infer the underlying private activity. Events are idempotent and transactional with their source completion according to `DATA_AND_MIGRATIONS.md` and the canonical Pokémon ecosystem specification.

## 16. Threat model and security requirements

### 16.1 Threats

- Lost/stolen device and opportunistic access to an unlocked device.
- Another app attempting to read PokéLife files or exported content.
- Malicious/malformed import files and archive traversal.
- Provider token theft, SDK compromise, or provider outage.
- Notification leakage on lock screen, wearable, or connected devices.
- Cross-module data leakage through projections, logs, analytics, or reward events.
- Duplicate/replayed imports or reward events after retry, migration, or restore.
- Backup archive theft, tampering, wrong passphrase, or incompatible schema.
- Accidental destructive restore, deletion, or migration failure.
- Excessive Android permissions or broad special access.
- Supply-chain compromise in third-party libraries.

### 16.2 Mitigations

Each threat is answered by a rule stated once above: app-private and sealed storage (11), user-controlled and passphrase-sealed backup and staged restore (10), validated imports and system pickers (8.2), narrow runtime permissions (4), minimal typed event contracts (15.1), redacted logs and dependency review (11.2, 12.1), late-resolved notification text (9.9), idempotent import and reward operations (8.2, 10.2), and explicit disconnect, deletion and full erase (10.3).

### 16.3 Limitations

PokéLife cannot guarantee protection on a fully compromised/rooted device, against malware with equivalent privileges, against a compromised provider, or for plaintext copies the user exports to other applications or destinations. A forgotten backup passphrase may make an encrypted archive unrecoverable. Explain these limitations without implying that local encryption makes a device invulnerable.

## 17. Reuse of existing code

An existing adapter, permission or receiver is reused only if its behavior and scope pass this specification; source presence is not release readiness. Health Connect, Huawei, banking and contact capabilities are not presented as complete until real-device or provider tests pass (section 18).

## 18. Implementation phases and exit gates

### Phase 0 — Policy and source audit

Inventory manifest permissions, exported components, SDKs, network destinations and provider dependencies; reconcile module privacy boundaries and reminder contracts; record provider capability classifications and evidence. **Exit:** no undocumented permission, network destination or data owner remains.

### Phase 1 — Local security and data foundation

Database and repository boundaries (`DATA_AND_MIGRATIONS.md`), sensitive-data encryption and Keystore key lifecycle, secure token storage, backup/export rules, consent/connection metadata and module-safe import contracts. **Exit:** local records, encrypted sensitive data, backup/restore and offline use pass tests before provider expansion.

### Phase 2 — Shared platform services

Central permission coordinator and manifest cleanup; shared reminders and notification privacy; SAF/Photo Picker import/export with secure temporary files; read-only Calendar adapter. **Exit:** permission denial/revocation, reboot, process death and external provider failure do not corrupt or block local records.

### Phase 3 — Health interoperability

Built for HEAL: Health Connect availability and record-type adapter, import preview, provenance, deduplication, freshness, disconnect and deletion (`MODULES/HEAL.md` 22A), and the Huawei Health reader (5.2.2). **Exit not met:** every advertised data type verified on a real device (none yet), the Huawei feasibility matrix done (5.2), unsupported metrics explicitly unavailable, manual HEAL complete.

### Phase 4 — Financial import and provider research

Built: MINT CSV import (`MODULES/MINT.md` 22A). Open: provider due diligence beyond desk research (7.1.1) for GCash, Maya, MariBank, GoTyme and UnionBank, building only adapters with verified personal-account access and approved terms. Notification parsing was approved on 25 November 2026 as MINT's opt-in capture and is built to the rules in 7.3 (broad-access burden accepted, on-device only). **Exit:** no unverified auto-sync claims; no credential scraping; MINT remains fully offline-capable. Every provider is listed unsupported until then.

### Phase 5 — Contacts and optional platform capabilities

LINK selected-contact import is built (4.2). Open: review optional launcher package visibility and system intents; verify location, sensor, audio, camera and Bluetooth tools are foreground, user-initiated and minimally permissioned. **Exit:** no background collection, contact scraping, or unapproved data sharing.

### Phase 6 — Security verification and release

Threat-model review, dependency audit, network inspection, permission audit, backup recovery drills, deletion verification, module-boundary tests, and a check that no telemetry, advertising, account or server endpoints remain. Test airplane mode and provider outages on real devices. **Exit:** all acceptance criteria below pass; remaining provider limitations are documented honestly.

## 19. Acceptance criteria

The principles in section 1, the permission policy in 4.1 and the reminder criteria in 9.12 are the criteria for those areas. The boxes below add what needs its own verification.

### Platform and ownership

- [ ] Manifest permissions match implemented capabilities and distribution policy (4.3).
- [ ] No background location, passive sensor collection, SMS reading, or broad notification access in the baseline (the opt-in Huawei Health reader, 5.2.2, is the one exception: off until the player turns it on, and it drops every other app's notifications).
- [ ] Every canonical record has one module owner; cross-module views use references or projections.

### Provider integrations

- [ ] Every adapter declares classification, scopes, supported data, provenance, freshness, failure and deletion behavior.
- [ ] Health Connect access is per-record-type and tested on supported Android versions.
- [ ] Huawei claims are verified against the actual watch, firmware, app, account region and data types.
- [ ] Calendar events remain external/read-only by default and are not duplicated into PLAN.
- [ ] GCash/Maya/MariBank/GoTyme/UnionBank auto-sync is not advertised without verified personal-account access and approval.
- [ ] Imports are previewed, validated, idempotent and provenance-preserving.

### Security and data lifecycle

- [ ] Sensitive local records are encrypted and keys are protected by Android Keystore (11.2).
- [ ] Provider tokens are not stored in ordinary domain tables or exports.
- [ ] Portable backups use independent authenticated encryption.
- [ ] Restore validates staged data and preserves the prior known-good state on failure.
- [ ] Export/import and deletion include indexes, attachments, caches and app-managed backups as applicable.
- [ ] No sensitive content appears in logs, crash diagnostics or raw provider payload retention.

### Reliability

- [ ] Provider outages, no network, process death, reboot, timezone changes, low storage, and revoked permissions are tested.
- [ ] Imports and reward events cannot duplicate on retry or migration.
- [ ] Stale provider data is clearly labelled and never represented as live.
- [ ] Manual workflows remain complete without provider accounts or internet.

## 20. Cross-reference authority

| Document | Authoritative responsibility |
|---|---|
| `../PRODUCT_BIBLE.md` | Local-first/no-account principles, module ownership, product-wide privacy boundaries |
| `../IMPLEMENTATION_GUIDE.md` | Baseline status, dependency order and implementation phases |
| `DATA_AND_MIGRATIONS.md` | SQLite/SQLDelight, repositories, transactions, encryption foundations, backup/restore, migrations, deletion and recovery |
| this Section 9 | Shared scheduling, quiet hours, focus deferral, notification delivery, action routing, reboot recovery and reminder privacy |
| `../FEATURES/POKEMON_ECOSYSTEM.md` | Canonical Pokémon identity, collection, acquisition, progression, reward and event contracts |
| `../MODULES/PLAN.md` | PLAN records and calendar workflows |
| `../MODULES/CARE.md` | Private CARE records and permitted non-sensitive activity metadata |
| `../MODULES/HEAL.md` | Health records, Health Connect/Huawei feasibility, imports and Field Research boundary |
| `../MODULES/MINT.md` | Philippine financial records, provider investigation, statement imports and Ledger Research boundary |
| `../MODULES/BASE.md` | Household records, optional location use and Habitat Discovery boundary |
| `../MODULES/LINK.md` | Contact import, relationship privacy and Postcard Trail boundary |
| `../MODULES/GEAR.md` | Sensor, location, microphone, camera, Bluetooth and device utility behavior |
| `../MODULES/WORLD.md` | Game progression and strict isolation from private module records |
| `../FEATURES/LAUNCHER.md` | Optional launcher package visibility and privacy-conscious surfaces |
| `TEST_STRATEGY.md` | Cross-layer integration, permission, security, offline, migration and privacy test catalogue |

This document owns cross-module integration and privacy contracts; module specifications own their domain workflows and link here rather than duplicate provider or security policy.

---

## Testing reference

Permission timing, denial/revocation, provider failure, privacy boundaries, sensitive-data redaction and real-device integration feasibility must be verified under [`TEST_STRATEGY.md`](TEST_STRATEGY.md). A passing fake-provider test does not establish real provider or device capability.

# PokéLife — Integrations and Privacy Architecture

**Version:** 1.1  
**Status:** Approved target architecture (owner, 6 November 2026; implementation details such as the database choice stay subject to their stated feasibility spike)  
**Baseline:** PokéLife `0.123.0`, updated through 0.162.0  
**Review date:** 6 November 2026

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
    ├── HOME   ├── CARE   ├── GEAR   └── WORLD
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
| Exact alarms | `SCHEDULE_EXACT_ALARM` / applicable platform mechanism | Only if a genuinely exact reminder capability is justified and Android policy permits it; prefer inexact scheduling otherwise |
| Calendar | `READ_CALENDAR`; write permission only if a separately approved direct-write workflow requires it | When user connects a calendar; read-only baseline |
| Health Connect | Per-record-type Health Connect permissions | When user selects health data categories to import/read/write |
| Location | `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` | Foreground, only when invoking a location-dependent tool; prefer approximate location |
| Background location | `ACCESS_BACKGROUND_LOCATION` | Not supported by the baseline product |
| Microphone | `RECORD_AUDIO` | Only when starting an audio measurement/recording tool; no background capture |
| Camera | Camera permission only for an implemented direct-camera workflow | Prefer system camera intents or scanner APIs where practical; request at capture time |
| Contacts | `READ_CONTACTS` or platform contact picker | Only when user explicitly invokes LINK import; prefer picker-based selection |
| Photos/media | Android Photo Picker | Use selected-item access; no broad media permission for ordinary import |
| Documents | Storage Access Framework (SAF) | User selects a file/folder; avoid broad storage access |
| Bluetooth | Version-appropriate Bluetooth permissions | Only for a verified direct device integration; not needed merely because a watch exists |
| Sensors | `SensorManager` where applicable | Usually no runtime permission for basic motion/environment sensors; only while relevant GEAR tool is active |
| Package visibility | Launcher intents / `<queries>`; `QUERY_ALL_PACKAGES` only if justified | Only for optional launcher/app-drawer functionality and subject to Play policy; avoid broad visibility if launcher queries suffice |
| Boot receiver | `RECEIVE_BOOT_COMPLETED` | Only to restore user-configured reminders/widgets that require reboot recovery |

The matrix is a policy map, not a declaration that every permission should be present in the manifest. Each manifest entry must have an implemented owner and documented purpose.

### 4.3 Manifest audit from baseline 0.123.0

The 0.123.0 manifest declared network, location, microphone, notification, calendar-read, boot, package-deletion, `QUERY_ALL_PACKAGES` and exact-alarm permissions, optional location/microphone hardware features, a home activity, widgets, reminder receivers and `android:allowBackup="true"` (still true; owner decision, 8 November 2026: left for now). Since then: five `READ_*` health permissions (read only), the Health Connect package query, a rationale activity and the permission-usage alias; no storage permission (the document picker is used). Exported components are the activities, the widget receivers and `ReminderBootReceiver`; the reminder receivers and `WidgetRefreshReceiver` are not exported. Every capability must still be checked against the shipped features before release:

- Remove unused permissions and deprecated/duplicated alarm declarations; reassess exact-alarm eligibility and remove any declaration not justified by an approved use case.
- Keep `QUERY_ALL_PACKAGES` only if the optional launcher genuinely requires broad app enumeration and distribution policy permits it; otherwise use launcher intent queries.
- Replace uncontrolled Android Auto Backup with the reviewed user-controlled backup design in `DATA_AND_MIGRATIONS.md` (sealing of Mint and Heal, section 10.4.1, already makes their cloud-backed copy unreadable elsewhere).
- Keep nonessential hardware features `required="false"`.
- Verify exported components, intent filters, URI grants and receiver permissions.

### 4.4 Signing and the release key

All builds (local and CI, `debug` and `release` types) are signed by one `shared` config using the tracked `androidApp/pokeplan-debug.keystore`, with the store password, alias and key password in clear text in `androidApp/build.gradle.kts` (SHA-1 `1A:28:99:CD:34:46:02:04:1E:AF:7B:15:7F:D1:07:41:BC:30:F0:A5`). It is the only keystore ever tracked, and is a test-grade key documented as private-use; a code comment and `CLAUDE.md` say so.

- **While the repository and the APKs stay private** (owner decision, 6 November 2026), the shared key is acceptable: anyone who can read the repository can already change the code.
- **It must not ship publicly.** Anyone with a clone could sign an update Android would accept for existing installs. Before any public build: generate a release key with a strong password and keep it and its password as CI secrets; sign `release` with it and keep `debug` on a separate debug key; create an APK Signature Scheme v3 rotation lineage from the old key (`apksigner rotate`) so installs signed with the old key accept the new one; then remove the clear-text passwords and the tracked keystore and add a CI check that fails if a keystore or a `storePassword` literal is tracked.
- **Rotating is not free:** Android refuses an update signed with a different key, and without a lineage the app would have to be uninstalled and its data lost. Take a verified backup before rotating.
- **Not established:** whether the repository or any fork was ever public, GitHub secret-scanning alerts, and whether the debug SHA-1 is registered with an external service (a comment in `androidApp/build.gradle.kts` mentions Google sign-in).

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

Treat Huawei Health integration as a feasibility workstream, not a committed product capability.

Investigate in this order:

1. **Health Connect interoperability:** verify whether the installed Huawei Health version and account region can write the required data types into Health Connect.
2. **Huawei Health Service Kit / official Huawei interfaces:** verify developer eligibility, app registration/approval, OAuth/account authorization, available scopes, region restrictions, cloud synchronization prerequisites, distribution requirements, and production access.
3. **Huawei Health export:** provide user-assisted import if the official export format contains the required records and can be parsed safely.
4. **Maintained third-party bridge:** consider only as an optional, explicitly unofficial path after security, maintenance, licensing, account, and data-flow review. Never make HEAL depend on it.
5. **Direct Bluetooth/proprietary protocol:** unsupported unless Huawei documents and authorizes the interface and the actual device/firmware behavior is verified. Do not reverse-engineer or promise access.

The feasibility matrix must be completed on the actual Huawei Watch Fit 4 Pro, paired Android phone, Philippine account/region, Huawei Health version, firmware, and any relevant Health Connect provider. Test each metric independently (steps, workouts, heart rate, sleep, weight, etc.). A successful account connection is not evidence that historical records or all metric types are accessible.

Do not require HMS Core, AppGallery, a Huawei account, or cloud relay for core HEAL functionality. Explain any external Huawei account/cloud processing before the user connects.

#### 5.2.1 Findings (desk research, 8 November 2026)

Public pages only (some are vendor pages); no watch, phone or Huawei account; nothing tested end to end, so nothing here makes a provider "supported".

1. **Health Connect:** no official statement that Huawei Health writes into it, and reviews list "no Health Connect sync" for the Fit 4 Pro. *Unverified, probably absent*; settled only on the owner's phone (does `com.huawei.health` appear under Health Connect → App permissions after sharing is enabled, and does Heal's "Imported records" list it?).
2. **Health Kit / Health Service Kit:** official, but needs a Huawei developer account, an app registered in AppGallery Connect and **Huawei's approval per app**, with user OAuth per data type (latest heart rate needs continuous monitoring on; sleep is historical only). *Blocked on the owner*: no key, scope list or proof it is open to an individual app in the Philippines. HEAL must not depend on HMS Core, AppGallery or a Huawei account.
3. **Huawei Health export:** no document names a format, history depth or stable ids. *Unverified*; the owner would request their data from Huawei's privacy centre and share a sample with personal values removed.
4. **Third-party bridge (Health Sync):** reads Huawei Health and writes to Health Connect, one way for most data; Fit 4 Pro coverage undocumented. *Optional, unofficial, unverified*; it feeds the existing Health Connect import, needs no code and HEAL never depends on it.
5. **Direct Bluetooth, or reading Huawei's cloud with a login:** *rejected* (no credential scraping or reverse engineering).

HEAL already reads whatever reaches Health Connect and shows the writing app of every imported record; a direct connection waits for an approval only the owner can start, and the Huawei card in Heal → Connections says what is true. **Matrix still to fill on the real watch, phone and Philippine-region account, one metric at a time:** steps and distance, workouts, heart rate, resting heart rate, HRV, sleep and stages, stress, calories, SpO2, weight, each with route, permission, history depth, timestamp behaviour, sync delay, units, region and firmware limits, cost and maintenance risk. HEAL only has records for workouts, sleep, weight, body fat and drinks, so those five come first.

#### 5.2.2 Reading Huawei Health on the phone (owner decision, 14 November 2026)

The owner chose a sixth route, on the phone only: **PokéLife reads what Huawei Health shows**, through an Android accessibility service and a notification listener the player turns on by hand. It needs no Huawei account, no cloud, no developer approval, no root and no reverse engineering of a protocol; it is unofficial and fragile (any Huawei Health update can break it), and that is accepted. It replaces the HEAL.md section 6 constraint "no fragile accessibility automation" for Huawei Health only. The rules:

1. **Huawei Health only.** The accessibility service is declared for the package `com.huawei.health` alone, so Android sends it no other app's screens. The notification listener cannot be narrowed by Android (it is offered every notification), so the code drops every notification from any other package on arrival, before reading a field. Nothing from another app is read, kept, counted or logged.
2. **Off until the player turns it on, and off again after every read.** Each one is turned on separately in Android's own settings (Accessibility; Notification access). On Android 13 and later a sideloaded app's accessibility service is greyed out until App info → ⋮ → Allow restricted settings; the Connections page says so. **Banking apps refuse to open while a sideloaded accessibility service is on** (owner, 14 November 2026: MariBank locks its app until the service is off or PokéLife is uninstalled; GCash shows a security notice). So the screen reader turns itself off (`disableSelf`) as soon as a read ends, however it ends, and Connections has a Turn off button. It is never hidden, renamed or disguised from those apps' checks: it is simply off when they run. Optional one-tap reads: if the player grants `WRITE_SECURE_SETTINGS` once from a computer (`adb shell pm grant app.pokeplan android.permission.WRITE_SECURE_SETTINGS`), Read Huawei Health switches the service on itself for that one read, exactly as the Accessibility switch would, and it is off again afterwards. The notification listener stays on; if a banking app objects to it too, it is turned off the same way.
3. **Active walk-through, on a tap only.** "Read Huawei Health" opens Huawei Health, waits for its Health page, scrolls it to the end capturing each step, then opens the **Sleep**, **Weight** and **Exercise records** cards one at a time (found by their title element, `function_set_items_title`), scrolls each card's page a little, and presses Back. It stops as soon as Back does not bring the Health page back, and returns to PokéLife (at most 90 seconds in all). Those three cards are the only things it taps; it never types or changes anything, and never opens settings, sharing, account or payment screens. Nothing runs on a timer. While screen reading is on, pages the player opens in Huawei Health themselves are captured too. Only elements visible on screen are captured (Huawei Health keeps its hidden Devices and Me tabs in the window).
4. **What is saved (0.185.0).** Captures stay in memory. From them `HuaweiRecords` makes HEAL records in its existing families, provider `huawei-screen`, shown as a preview (to add, already here, flagged) before **Add them** saves anything (HEAL.md 10.3): workouts from Exercise records (type, start, duration, distance in km; the year from the month headers), weight and body fat from the Weight page (its own date and time; the year from the weekday), and **today's** night from the Sleep page (its length, ending at the Health page's get-up time: the page shows no bedtime, so another night has no times and is left out, never guessed). Ids come from each record's own time, so reading again adds nothing twice. **Save, numbers hidden** writes the capture to a `.txt` file (every digit of Huawei's text replaced by `#`), since a paste cannot hold the Weight page's date picker.
5. **Rewards (owner decision, rules version 24):** a session read this way **pays like a manual one** (`HealRules.paysLikeManual`): the same length rule and the daily share of the session's own day, oldest first, but it may have ended up to **8 days** ago (`IMPORT_FRESH_MILLIS`), because reads are nightly to weekly; a session flagged as a possible repeat of one already here (logged by hand too) pays nothing, so nothing is paid twice. Sleep and measurements never pay, as by hand. Health Connect imports still pay nothing.
5a. **Nightly to weekly (owner, 14 November 2026).** Heal → Connections → Huawei Health has **Remind me to read**: Off, Nightly or Weekly, at a time the player picks (21:00 by default). The reminder counts from the last read, follows the shared quiet hours and focus deferral, and says only "Time to read Huawei Health". Weekly keeps every workout (Exercise records is a list) and the latest weight; only a daily read keeps every night's sleep, because only today's night has times.
6. **English only:** the parsers match Huawei Health's English labels.
7. Like every HEAL record, anything saved is sealed (section 4), never logged and never sent anywhere.

**Steps and readings (owner decisions, 14 November 2026; built in 0.186.0, `MODULES/HEAL.md` 22B):** Heal keeps daily **steps**, **heart rate**, resting heart rate, **SpO2**, **skin temperature**, Huawei's **stress** number and **sleep score** as their own records (schema 11), read from the Health page, the Sleep page and the step notification, as history only; Heal never says whether a reading is good or bad. **Steps pay per amount** (2,000 steps, at most 5 a day) and **reading records in pays** once a day per family of record (rules version 25); no reading pays for its value. The walk now also taps the **Week** tab of a page that has one (the Sleep page), to capture the week for the next probe; it is not parsed yet. Ids come from each record's day, so reads never duplicate.

**What Huawei Health's home page shows** (owner's screenshot, English, 14 November 2026; labels only, no values kept): a top summary of Sleep (get-up time), Steps and Mood (stress); then cards for Exercise records (distance in km), Health Clovers, Heart (heart rate in bpm, "Recent avg RHR"), Weight (kg), Sleep (hours and minutes), Skin temperature (°C), Activity rings, Emotional wellbeing, SpO2 (%) and Cycle Calendar; Health insights below; bottom tabs Health, Exercise, Discover, Devices, Me. The parsers use only the cards HEAL has records for (exercise, sleep, weight) and the step count, and skip the rest, Cycle Calendar included, even though the walk's capture contains them. A screenshot shows the labels but not how the text is split into elements or the view ids, so the parsers still wait for a probe capture.

**First capture (owner, 14 November 2026, numbers hidden):** each Health page card is a `function_set_items_title` element followed by `function_set_items_data`, `function_set_items_unit` and `function_set_bottom_left` (Weight: `##.#` then `kg`; Sleep: `# h # min`; Heart: `Heart rate ## bpm`; Exercise records: `#.##` then `km`). The summary's get-up time is an element described `##:##, Get-up time` and its steps one described `#### steps` (`time_strength_layout_middle`); some ids are misleading (`stepsText` holds the get-up time), so the parser matches the words, not those ids. `HuaweiHome` reads all of this and Connections shows it; it saves nothing, because the page has no times (the sleep card is a length, the weight card has no date, the exercise card is the day's distance). The card pages the walk now opens should carry the times: the next capture decides the record parsers.

## 6. Calendar integration and ownership

### 6.1 Baseline: Android Calendar Provider, read-only

PLAN may read calendars exposed through Android's Calendar Provider after the user explicitly selects calendar access. This can include Google Calendar calendars synchronized to the device and other compatible calendar providers. PokéLife must not assume every Google account calendar is available locally.

- User chooses which calendars to display.
- Imported events are provider-owned, read-only projections.
- PLAN tasks and native PLAN events remain PokéLife-owned.
- External events do not automatically become tasks, commitments, or reward events.
- A linked PLAN task stores an opaque provider/calendar/event reference; it does not create an editable duplicate.
- Provider event changes/deletions are reflected when the provider query refreshes; stale cached projections are labelled or removed according to the module's display policy.
- Calendar access denial does not disable PLAN's native calendar.

### 6.2 Writes and synchronization

Do not implement bidirectional synchronization in the initial release. Avoid a second source of truth and conflict-resolution burden.

If event creation is later approved, prefer Android calendar intents that hand the user to the chosen calendar application for confirmation. Direct Calendar Provider writes require a separately reviewed permission, user consent, account/calendar selection, conflict behavior, and tests. Never silently edit or delete external events.

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

#### 7.1.1 Findings (desk research, 8 November 2026)

Public pages only; nothing tested with a bank account. Every provider is **unsupported** until verified.

| Provider | Finding | Status |
|---|---|---|
| UnionBank | API developer portal since 2017 (hundreds of APIs: bills payment, transfers, inquiries, a payment gateway), aimed at fintechs and businesses; no source on personal-account transaction history for an individual developer's app, OAuth consent or current terms | **Unverified.** The owner would see whether an individual can register and which APIs return a customer's own transactions with consent |
| GCash, Maya, GoTyme, MariBank | No public documentation of an API giving a user's own transaction history to a third-party app; a vendor page lists them but describes protocol reverse engineering, which this spec forbids | **Unverified; treat as unsupported** |
| BSP Open Finance (Circular 1122, 2021) | A framework for permissioned data sharing with authorized third-party providers; no official list of live participants or endpoints, and unclear whether an individual developer's app can qualify | **Watch, do not build.** If real, the route is an official consented API (7.2, item 4) |
| Notification reading | `NotificationListenerService` | **Not built** (7.3) |

The statement file (CSV) stays MINT's only integration. Next evidence the owner can supply: whether UnionBank's portal lets an individual register and which APIs return own transactions; which statement formats GCash, Maya, GoTyme and MariBank let the user export, with one sample each (values removed) to test the reader's column guesses; whether the BSP lists any participant an individual can use.

### 7.2 Approved integration order

1. Manual account, transaction, transfer, budget, and bill workflows.
2. CSV and spreadsheet imports with preview and reconciliation.
3. User-selected statement/document imports for supported formats.
4. Official provider APIs only after access and production eligibility are verified.
5. Optional local notification parsing only after a separate privacy/platform review; not part of the initial release.

Never request/store bank passwords, app PINs, CVVs, OTPs, recovery codes, or session cookies. Never scrape authenticated app screens, use Accessibility Services to extract banking data, bypass app protections, or execute transfers/payments. PokéLife is not a payment initiation application.

### 7.3 Notification parsing

Android `NotificationListenerService` can expose notifications across applications after a broad user-granted special access. It is not a narrowly scoped bank permission. It can reveal unrelated private notifications.

Therefore:

- Exclude notification parsing from initial MINT scope.
- Do not request notification-listener access as part of onboarding or ordinary MINT use.
- If separately approved later, make it an explicit opt-in experimental capability with a precise explanation of broad access.
- Process locally; restrict recognized source packages; discard unrelated notification content immediately; do not persist raw notification text.
- Do not infer transactions from incomplete or ambiguous messages without user review.
- Never read SMS automatically as a fallback.

### 7.4 Financial privacy and progression

MINT accounts, balances, transaction descriptions, merchants, budgets, debts, savings targets, bills, and payment status never enter WORLD, launcher surfaces, generic logs, telemetry, or other module payloads. MINT's Ledger Research may emit only its approved abstract administrative event metadata through the local shared reward contract. Financial outcomes and amounts never affect rewards or Pokémon access.

## 8. Contacts, files, media, location, sensors, and other Android capabilities

### 8.1 Contacts (LINK)

LINK is manual-first. For import, prefer Android's contact picker or selected-contact flow over broad address-book access. If broad `READ_CONTACTS` access is required for a clearly explained import scope, request it only after the user invokes import.

- Preview selected contacts and proposed matches before import.
- Preserve source provenance for imported fields.
- Never upload contact lists, scan messages/call logs, monitor social apps, score relationships, or send messages automatically.
- LINK notes and relationship context remain local and are never included in progression events.
- Disconnecting contact access stops future reads but does not delete imported LINK records unless the user chooses deletion.

### 8.2 Files, photos, and sharing

Use Storage Access Framework for documents and Android Photo Picker for images. Do not request broad storage/media access for normal import/export. Use content URIs and temporary URI grants; do not assume a selected URI remains accessible forever unless the permission is persisted where supported.

All imports must validate file size, type, encoding, schema, archive paths, and content. Reject path traversal, decompression bombs, malformed archives, and unsupported versions. Preview changes before commit. Treat files from other apps as untrusted.

Use Android Sharesheet/intent flows for explicit user-initiated sharing. Do not silently upload or share a file. Remove temporary export files after the documented retention period and ensure they are not exposed through public storage by default.

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

## 9. Shared reminder and notification architecture

This section is the sole authority for shared reminder scheduling, policy, delivery, action routing, lifecycle recovery and reminder privacy. Module specifications define only their source records, preferences and action meaning. `DATA_AND_MIGRATIONS.md` owns the physical schema, transactions, backup/restore and migrations.

### 9.1 Purpose and boundaries

Provide one reliable, predictable reminder system for PokéLife without merging module data ownership. A module owns its source record and decides whether it needs a reminder. The shared feature owns scheduling policy, delivery mechanics, global quiet hours, focus deferral, recovery, and notification action routing contracts.

The shared reminder feature does not own tasks, routines, bills, household chores, important dates, calendar events, or any other business record. It stores only the minimum scheduling metadata needed to deliver or cancel a reminder.

Initial participating modules:

- **PLAN:** tasks, recurring-task occurrences, and supported PLAN-created events.
- **CARE:** self-care routines and optional Diary Card reminders.
- **HOME:** household chores, maintenance, and low-stock reminders when those capabilities are implemented.
- **LINK:** important dates and user-created follow-up reminders when implemented.
- **HEAL:** explicitly enabled medication-schedule and appointment reminders; optional hydration or health-goal prompts. HEAL owns the source records and reminder preferences; medication instructions are user-entered and are never validated or changed by the scheduler.
- **MINT:** bill-occurrence reminders. MINT owns bill records, occurrence/payment status, reminder preferences and source-action meaning; the shared feature owns scheduling and delivery. Financial amounts, account details and provider names are not stored in shared reminder metadata or exposed in lock-screen notifications by default.
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

The shared scheduler must not import PLAN, CARE, HEAL, HOME, or LINK repositories directly.

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

Recommended platform-neutral interfaces (names are conceptual; align with actual codebase conventions):

- `ReminderScheduler` — schedules, updates, cancels, and reconciles requests.
- `ReminderPolicy` — applies quiet hours, focus deferral, permissions, and delivery eligibility.
- `ReminderRepository` — stores stable scheduling metadata and notification identities.
- `ReminderDelivery` — Android notification creation, update, cancellation, and action dispatch.
- `ReminderSourceAdapter` — module-owned validation, display resolution, and action execution.
- `FocusStateProvider` — exposes focus activity without coupling to PLAN classes.
- `ReminderRecovery` — reconciles after reboot, update, process death, and system changes.

Keep domain policy testable outside Android APIs. Android AlarmManager/WorkManager/notification implementation decisions belong in the Android adapter and must be selected based on exact timing needs, platform limits, and reliability tests. Do not commit to a specific scheduler API without validating the requirements and target Android versions.

#### 9.10.1 Shared persistence contract

Reminder requests and delivery metadata follow the shared database, stable-ID, transaction, backup/restore and migration rules in `DATA_AND_MIGRATIONS.md`. Reminder records never replace the owning module’s canonical business record.

### 9.11 Migration from existing implementations

HEAL, MINT, HOME and LINK used the shared contract from their first reminder. The migration of PLAN and CARE (done) followed these rules, which any later cutover of a source must repeat: inventory the sources, alarm ids, channels, receivers and persisted state; map each legacy identity to a stable reminder id; rebuild active requests from the canonical module records; preserve module enablement, schedule, snooze and action semantics; make sure old and new schedulers cannot deliver the same reminder; cut over by module with explicit version state; cancel legacy alarms only after the new request is registered; never replay completions or reward events; make every step idempotent; keep rollback information until verified. CARE retains routine ownership, completion and reward history and rhythm calculations; PLAN retains task/occurrence ownership and reward accounting; the shared layer owns neither.

#### 9.11.1 As built

- **One scheduler, one receiver.** `ReminderRequest` (stable id `MODULE|ref|kind|occurrence`), `ReminderPolicy.plan`/`deliverAt` (global quiet hours with midnight wrap and next eligible time; focus deferral re-checked at delivery, no loop), source adapters (`PlanReminderAdapter`, `CareReminderAdapter`, `HealReminderAdapter`, `HomeReminderAdapter`, `LinkReminderAdapter`), `ReminderRouter` (a stale or unknown source cannot mutate; snooze and dismiss never complete or pay), `ReminderSnooze`, and on Android `SharedReminders`, `SharedReminderReceiver` and `ReminderBootReceiver`. Active requests are rebuilt from the canonical module saves on every `reschedule`; the old per-module alarm codes are not reused.
- **Persistence.** Only snoozes and repeats are stored, as opaque ids and times, in table `reminder_hold` (schema 3). The cutover marker is `reminder_migration` (version 1); `LegacyReminders.cancelOnce` runs after the new alarms are set and the old receivers show nothing.
- **Nightly refresh.** The planner looks a bounded horizon ahead and was otherwise re-run only when the app opened or an alarm fired, so one inexact alarm a night at 04:00 local (`setAndAllowWhileIdle`, `ACTION_REFRESH`) plans everything again.
- **Snoozes per module:** PLAN 10 min / 1 h / tomorrow (and its own repeat, bounded to 3); CARE 30 min; HEAL uses CARE's. A snooze survives a reboot because it is stored.
- **Stay in `AppAlerts` on purpose:** focus halfway/end alerts (the session's own feedback), World jobs and GEAR timers (user-started timers, not reminders on a source record). The streak reminder and weekly recap are PLAN nudges on the shared scheduler (`PlanReminderAdapter` kinds `streak` and `recap`): a streak request for each of the next three days at the chosen hour, the recap for the next Sunday at 18:00; a request inside quiet hours is not made, and `isStillWanted` drops one whose day has gone by, so a nudge held until morning is dropped instead of nagging. No repeats, no actions.
- **HOME and LINK reminders** are off by default, switched on per record behind a module master switch, with a lock-screen text that never names a record. A request holds an opaque record id, the kind and a day; unlocked text names a record only by the user's choice. A request that fires after the date it was made for has passed is stale and dropped. Notification actions never change a record except a chore's or follow-up's own completion ("Done"), which pays once like ticking it in the module; maintenance, restocking and warranties have no such action. Details are in `MODULES/BASE.md` and `MODULES/LINK.md`.
- **Not run on a device:** the alarms, notifications and buttons themselves. Phone checks: a routine a few minutes ahead notifies, Done ticks it and "In 30 min" returns it; a snooze survives a restart; a reminder during a focus session waits for its end; a 23:00 routine comes at 07:00; notifications posted by an older version still react to their buttons.

### 9.12 Testing and acceptance criteria

#### Shared policy

- Quiet hours work across midnight and time-zone changes.
- Every module respects global quiet hours.
- Module toggles disable only that module's requests.
- Focus deferral holds and releases eligible reminders without stale delivery or loops.
- Notification permissions and channel disablement produce graceful behavior.

#### Scheduling and lifecycle

- Re-submitting a request does not duplicate an alarm.
- Editing/rescheduling replaces the prior request.
- Completion/archive/delete cancels active and snoozed notifications.
- Reboot/app update/process death reconstructs active reminders correctly.
- System time/time-zone changes reconcile local schedules.
- Concurrent reminders from different modules remain independently addressable.

#### Action routing

- PLAN actions execute only through PLAN adapters.
- CARE actions execute only through CARE adapters.
- HEAL actions execute only through HEAL adapters; dismissing or snoozing a medication reminder never records a dose as taken.
- Stale or invalid source references cannot mutate records.
- Snooze does not create a new source record or reward event.
- Dismissal does not accidentally complete a source record.
- Reward processing remains owned by the source module and idempotent.

#### Privacy

- Shared reminder storage contains no unnecessary module content.
- Private CARE content, HEAL measurements/medication/symptom/appointment/nutrition details, finance data, relationship notes, and task notes are not copied into scheduler logs or WORLD events.
- Lock-screen notification content follows privacy settings.

#### Migration

- No duplicate notifications during or after cutover.
- Legacy CARE reminder behavior is preserved where approved.
- PLAN task reminder behavior is preserved.
- Interrupted migration can safely resume.
- Historical actions are not replayed as reward events.

### 9.13 Module-specific requirements remain in module specifications

- `../MODULES/PLAN.md` defines which PLAN records request reminders and how PLAN task/event actions behave.
- `../MODULES/CARE.md` defines optional routine and Diary Card reminder preferences and CARE action semantics.
- `../MODULES/BASE.md` defines HOME-owned chore, maintenance, low-stock and warranty reminder sources and their module-specific action semantics.
- `../MODULES/LINK.md` may define important-date/follow-up reminder sources when LINK is specified.
- `../MODULES/HEAL.md` defines optional medication, appointment, hydration and health-goal reminder sources and HEAL-specific action semantics. Medication details and instructions remain in HEAL and are not copied into shared scheduler storage.
- `../MODULES/MINT.md` defines bill-occurrence reminder preferences and MINT-owned navigation/action semantics. Shared reminder metadata and lock-screen text must not contain amounts, account details, provider names or payment status; a reminder action does not mark a bill paid.
- `../MODULES/GEAR.md` defines user-created timer identity, duration and completion semantics. Timer completion notifications use the shared Android delivery/recovery infrastructure and must not create a second scheduler.
- `../MODULES/WORLD.md` defines optional, event-driven WORLD notification requests and module-specific opt-in/rate limits. WORLD does not schedule independently or use private module activity as a notification trigger.

This document owns shared scheduling, policy, delivery, recovery, privacy, and cross-module acceptance. Module specifications must not restate these shared requirements.

---

### 9.14 Testing reference

Reminder policy, recurrence, idempotent scheduling, cancellation, quiet hours, reboot recovery, notification actions and permission-denied behavior must follow [`TEST_STRATEGY.md`](TEST_STRATEGY.md). Actual notification delivery and channel behavior require Android/device validation.

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

As built: backups and Mint/Heal sealing are specified in `DATA_AND_MIGRATIONS.md` sections 10.4.1 and 10.5.1; the portable form is a passphrase-sealed file (PBKDF2-HMAC-SHA256, AES-256-GCM) separate from the device-sealed daily backups.

### 10.2 Export and import

Exports should be readable, documented, and module-selectable. Provide structured JSON and CSV where meaningful; CARE may additionally provide readable Markdown/PDF. Plaintext export of sensitive data requires explicit user action and a clear warning. Exports must not contain provider credentials/tokens by default.

Imports must be user-selected, validated, previewed, provenance-preserving, deduplicated, transactional or safely resumable, and must not silently overwrite manual records. Imported history must not replay reward events.

### 10.3 Disconnect and deletion

Provide controls to disconnect a provider, revoke provider tokens where supported, delete provider connection metadata, delete imported records by source, delete individual records, delete a module's data, and erase all local PokéLife data. These are distinct operations and must be explained separately.

Disconnecting a provider stops future access but does not automatically erase previously imported data. Deleting active records must also remove associated attachments, thumbnails, search indexes, derived caches, and temporary files as specified by `DATA_AND_MIGRATIONS.md`. App-managed local backups must be included in full erase scope; user-exported copies are not.

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
- Use independent passphrase-based encryption for portable backups.
- Use TLS for network requests; reject cleartext traffic except a narrowly justified development-only configuration.
- Use OAuth authorization code with PKCE where supported; request minimum scopes and store tokens only in secure platform storage.
- Never log sensitive content, raw provider payloads, tokens, authorization headers, or full financial/health records.
- Redact exception messages and diagnostics that could contain provider data.
- Exclude sensitive data from clipboard, screenshots, recent-task previews, and lock-screen notifications where platform controls permit; respect user-visible OS behavior and avoid false guarantees.
- Do not treat root detection or obfuscation as substitutes for data protection.

A biometric/app lock may be offered as an optional additional UI gate, but it must not be confused with database encryption and must not be required for basic local use.

**As built.** MINT and HEAL rows and every copy of them are sealed with separate Android Keystore keys (AES-256-GCM, non-exportable, fail closed without the key, no silent reset of a database the key does not open); portable backups are sealed under a passphrase (`DATA_AND_MIGRATIONS.md` section 10.4.1). CARE journals and diary, HOME and LINK are not yet sealed, so the Sensitive row above is met for Mint and Heal only. Open: whether to seal Care, Home and Link (an owner decision for each).

## 12. Network access and external dependencies

Network access is optional and feature-triggered. The application must not contact a PokéLife-operated backend because none is part of the product architecture.

Potential network-dependent features include optional exchange-rate retrieval, weather, food/barcode lookup, official provider adapters, and app/reference-content updates. Each feature must declare the destination, data sent, purpose, retention where known, and offline fallback.

Requirements:

- HTTPS/TLS; no sensitive data in URL query strings.
- No embedded private API secrets. Public client identifiers are not secrets.
- Least-privilege OAuth scopes and explicit account/provider consent.
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

Consent must be specific, informed, revocable, and not bundled with unrelated onboarding. If a provider changes scopes or starts collecting a new category, require renewed consent before using that capability. No preselected opt-ins for sensitive integrations.

## 15. Privacy boundaries: modules, shared services, and WORLD

The module ownership rules in `PRODUCT_BIBLE.md` and `DATA_AND_MIGRATIONS.md` remain authoritative. The integration layer enforces these additional boundaries:

| Owner | Data that stays private to owner | Permitted cross-boundary output |
|---|---|---|
| CARE | Journal, diary cards, mood, urges, skills responses, private notes | Only explicitly approved non-sensitive activity metadata; never content-derived events |
| HEAL | Measurements, body composition, calories, heart rate, sleep, nutrition, symptoms, diagnoses, medication, appointments, health notes | Minimal opaque eligible participation event evaluated within HEAL/reward boundary; WORLD receives generic reward result only. *As built (0.136.0): `HealEvent` = stable id, module, opaque record reference, `activity_logged` or `participation`, time; nothing else (`MODULES/HEAL.md` section 22).* |
| MINT | Account names, balances, transactions, merchants, budgets, savings, debts, bills, payment state | Approved abstract administrative Ledger Research event only; no values/circumstances |
| LINK | Contact names, IDs, phone/email/address, relationship categories, notes, associated-person identity | Opaque eligible follow-up ID, module, policy version, completion time inside local reward contract; no identity/context |
| HOME | Household contents, inventory, maintenance details, saved locations, private notes | Approved minimal Habitat Discovery event; no inventory or location payload |
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

- Android app-private storage and encrypted sensitive data.
- Keystore-protected local keys; independently encrypted portable backups.
- Strict module repository boundaries and minimal typed event contracts.
- SAF/Photo Picker, narrow runtime permissions, and no broad storage access.
- Validated import parsing, archive path checks, size limits, checksums, and staging.
- OAuth PKCE and least-privilege scopes where supported; no credential scraping.
- Redacted logs, no telemetry, no analytics SDK, and dependency review.
- Privacy-conscious notifications and late resolution of sensitive display text.
- Idempotency keys and transactionally committed import/reward operations.
- Staged restore/migration with verified pre-operation recovery point.
- Explicit disconnect, revoke, selected-source deletion, module deletion, and full erase.

### 16.3 Limitations

PokéLife cannot guarantee protection on a fully compromised/rooted device, against malware with equivalent privileges, against a compromised provider, or for plaintext copies the user exports to other applications or destinations. A forgotten backup passphrase may make an encrypted archive unrecoverable. Explain these limitations without implying that local encryption makes a device invulnerable.

## 17. Existing implementation audit and target changes

The 0.123.0 baseline was a Kotlin Multiplatform/Compose project with Android platform code: a read-only Android calendar implementation, on-demand sensor utilities, location/microphone/scanner abstractions, reminder receivers with reboot recovery, JSON persistence in two stores, and Supabase account/cloud-save pathways (retired in 0.130.0). Target actions still open:

1. Reuse only platform adapters whose behavior and permission scope pass this specification; source presence is not release readiness.
2. Preserve the read-only Calendar Provider foundation behind PLAN's provider-neutral adapter.
3. Keep sensors on-demand and foreground; remove unused permission declarations.
4. Replace uncontrolled Android Auto Backup with the reviewed user-controlled backup design.
5. Keep provider credentials in secure platform storage, not the general save model.
6. Do not present Health Connect, Huawei, banking or contact capabilities as complete until real-device/provider tests pass.

## 18. Implementation phases and exit gates

### Phase 0 — Policy and source audit

Inventory manifest permissions, exported components, SDKs, network destinations, account/cloud pathways and provider dependencies; reconcile module privacy boundaries and reminder contracts; record provider capability classifications and evidence. **Exit:** no undocumented permission, network destination or data owner remains.

### Phase 1 — Local security and data foundation

Implement the database and repository boundaries (`DATA_AND_MIGRATIONS.md`), sensitive-data encryption and Keystore key lifecycle, secure token storage, backup/export rules, consent/connection metadata and module-safe import contracts; remove uncontrolled Auto Backup and retire cloud pathways only after migration gates pass (cloud retired in 0.130.0; Auto Backup still on, section 4.3). **Exit:** local records, encrypted sensitive data, backup/restore and offline use pass tests before provider expansion.

### Phase 2 — Shared platform services

Central permission coordinator and manifest cleanup; shared reminders and notification privacy; SAF/Photo Picker import/export with secure temporary files; read-only Calendar adapter. **Exit:** permission denial/revocation, reboot, process death and external provider failure do not corrupt or block local records.

### Phase 3 — Health interoperability

- Health Connect availability and selected record-type adapter.
- Import preview, provenance, deduplication, freshness, disconnect and deletion.
- Huawei Health Service Kit/Health Connect/export feasibility on actual Philippine-region devices.

**Exit:** every advertised data type is verified; unsupported metrics are explicitly unavailable; manual HEAL remains complete.

### Phase 4 — Financial import and provider research

- MINT CSV/statement imports and reconciliation.
- Individual provider due diligence for GCash, Maya, MariBank, GoTyme, UnionBank.
- Build only adapters with verified personal-account access and approved terms.
- Keep notification parsing out of scope unless a separate decision approves its broad-access/privacy burden.

**Exit:** no unverified auto-sync claims; no credential scraping; MINT remains fully offline-capable.

### Phase 5 — Contacts and optional platform capabilities

- LINK selected-contact import and preview-first reconciliation.
- Review optional launcher package visibility and system intents.
- Verify location/sensor/audio/camera/Bluetooth tools are foreground, user-initiated, and minimally permissioned.

**Exit:** no background collection, contact scraping, or unapproved data sharing.

### Phase 6 — Security verification and release

- Threat-model review, dependency audit, network inspection, permission audit, backup recovery drills, deletion verification, and module-boundary tests.
- Verify no telemetry, advertising, account, or PokéLife cloud endpoints remain.
- Test airplane mode and provider outages on real devices.

**Exit:** all acceptance criteria below pass; remaining provider limitations are documented honestly.

### As built, 0.162.0

Phase 3's Health Connect adapter and import preview, provenance, deduplication, freshness, disconnect and deletion are built for HEAL (`MODULES/HEAL.md` 22A), and Phase 4's CSV import for MINT (`MODULES/MINT.md` 22A). Each module's page lists its integrations with class and status; there is no single Settings destination yet (section 14). **Exit gates not met:** no advertised data type has been verified on a real device, the Huawei feasibility matrix is not done (5.2.1), and no provider due diligence beyond desk research has been done for GCash, Maya, MariBank, GoTyme or UnionBank (7.1.1), so every one of them is listed unsupported.

## 19. Acceptance criteria

### Product and privacy

- [ ] No mandatory account, cloud profile, telemetry, ads, analytics upload, or PokéLife cloud sync.
- [ ] Core modules, local Pokémon collection, and local WORLD progression work offline.
- [ ] No sensitive personal information affects Pokémon progression or is exposed to WORLD/launcher.
- [ ] Every canonical record has one module owner; cross-module views use references/projections.

### Permissions and Android platform

- [ ] Permissions are requested only at point of use with a clear rationale.
- [ ] Manifest permissions match implemented capabilities and distribution policy.
- [ ] Denied/revoked permissions stop access without destroying local data.
- [ ] No background location, passive sensor collection, SMS reading, or broad notification access in the baseline.
- [ ] File and contact access use system pickers/selected records where practical.

### Provider integrations

- [ ] Every adapter declares classification, scopes, supported data, provenance, freshness, failure and deletion behavior.
- [ ] Health Connect access is per-record-type and tested on supported Android versions.
- [ ] Huawei claims are verified against the actual watch, firmware, app, account region and data types.
- [ ] Calendar events remain external/read-only by default and are not duplicated into PLAN.
- [ ] GCash/Maya/MariBank/GoTyme/UnionBank auto-sync is not advertised without verified personal-account access and approval.
- [ ] Imports are previewed, validated, idempotent and provenance-preserving.

### Notifications

- [ ] Shared reminder infrastructure is the only scheduler/delivery authority.
- [ ] Reminder storage contains only opaque source references and minimum timing/action metadata.
- [ ] Sensitive lock-screen content is hidden by default.
- [ ] Permission denial/channel disablement leaves in-app source records usable.
- [ ] Dismissal/snooze never implies source completion, medication taken, payment, or reward.

### Security and data lifecycle

- [ ] Sensitive local records are encrypted and keys are protected by Android Keystore.
- [ ] Provider tokens are not stored in ordinary domain tables or exports.
- [ ] Portable backups use independent authenticated encryption.
- [ ] Restore validates staged data and preserves the prior known-good state on failure.
- [ ] Export/import and deletion include indexes, attachments, caches and app-managed backups as applicable.
- [ ] No sensitive content appears in logs, crash diagnostics, analytics, or raw provider payload retention.
- [ ] Network calls use TLS, bounded retries, and no hardcoded private secrets.

### Reliability

- [ ] Provider outages, no network, process death, reboot, timezone changes, low storage, and revoked permissions are tested.
- [ ] Imports and reward events cannot duplicate on retry or migration.
- [ ] Stale provider data is clearly labelled and never represented as live.
- [ ] Manual workflows remain complete without provider accounts or internet.

## 20. Cross-reference authority

| Document | Authoritative responsibility |
|---|---|
| `../PRODUCT_BIBLE.md` | Local-first/no-account principles, module ownership, product-wide privacy boundaries |
| `../IMPLEMENTATION_GUIDE.md` | Baseline status, dependency order, cloud-path retirement and implementation phases |
| `DATA_AND_MIGRATIONS.md` | SQLite/SQLDelight, repositories, transactions, encryption foundations, backup/restore, migrations, deletion and recovery |
| this Section 9 | Shared scheduling, quiet hours, focus deferral, notification delivery, action routing, reboot recovery and reminder privacy |
| `../FEATURES/POKEMON_ECOSYSTEM.md` | Canonical Pokémon identity, collection, acquisition, progression, reward and event contracts; reconcile its canonical file presence before implementation if missing |
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

This document owns cross-module integration and privacy contracts. Module specifications own their domain-specific workflows and must link here rather than duplicate provider/security policy. `DATA_AND_MIGRATIONS.md` remains authoritative for database and backup implementation details; this Section 9 remains authoritative for reminder behavior.

---

## Testing reference

Permission timing, denial/revocation, provider failure, privacy boundaries, sensitive-data redaction and real-device integration feasibility must be verified under [`TEST_STRATEGY.md`](TEST_STRATEGY.md). A passing fake-provider test does not establish real provider or device capability.

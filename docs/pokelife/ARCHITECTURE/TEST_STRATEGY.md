# PokéLife — Test Strategy

**Version:** 1.0  
**Status:** Approved testing strategy  
**Location:** `ARCHITECTURE/TEST_STRATEGY.md`  
**Scope:** All PokéLife modules, shared systems, Android application surfaces and release workflows

## 1. Purpose and governing principles

This document defines how PokéLife behavior is verified from pure domain rules through persistence, shared services, Android integration, UI behavior and physical-device acceptance. It is the authoritative testing strategy; feature specifications remain authoritative for expected product behavior.

Testing must preserve the product's defining architectural properties:

- **Local-first:** essential supported workflows remain usable without network access.
- **Single ownership:** each domain owns its authoritative records; shared systems expose documented contracts rather than allowing cross-module writes.
- **Single economy and collection:** Pokémon ownership, inventory and reward balances have one authoritative implementation.
- **Privacy by boundary:** sensitive module data does not leak through events, projections, logs, notifications or analytics.
- **Recoverability:** interruption, migration and restore must not silently destroy or duplicate user data.
- **Accessible, coherent presentation:** visual quality, accessibility and responsive behavior are part of correctness.
- **Evidence-based status:** a specification, a successful build, an executed automated test and physical-device acceptance are distinct states.

Do not claim a test passed unless the exact relevant test was executed and its result is available. This document specifies required coverage; it does not certify the current implementation.

## 2. Test levels and environments

Use a layered testing pyramid. Most verification belongs in deterministic lower-level tests; physical-device testing is reserved for platform and hardware behavior that cannot be faithfully established elsewhere.

| Level | Purpose | Typical scope | Required environment |
|---|---|---|---|
| L1 — Domain | Verify deterministic business rules and state transitions | Calculations, validation, recurrence, progression, reward eligibility | Kotlin common/JVM tests |
| L2 — Data | Verify durable storage and repository contracts | Database, transactions, serialization, migrations, backup/restore | JVM where supported; Android instrumented for actual Android persistence |
| L3 — Integration | Verify boundaries and platform adapters | Module contracts, reminders, permissions, provider adapters, lifecycle services | JVM fakes plus Android instrumented tests |
| L4 — UI / E2E | Verify user-visible behavior and complete workflows | Compose semantics, navigation, golden images, cross-module journeys | Compose tests, emulator CI, selected device tests |
| L5 — Device acceptance | Verify real hardware and release-like behavior | Notifications, launcher, widgets, battery, memory, TalkBack, sensors | Physical Android devices; primary target first |

### 2.1 Test doubles and deterministic control

- Inject clocks, time zones, random generators, dispatchers, schedulers and provider clients.
- Use fixed clocks and seeded randomness for reproducible tests.
- Prefer in-memory fakes for domain tests and production implementations for repository contract tests.
- Keep fakes behaviorally faithful; contract-test important fakes against the same interfaces as production adapters.
- Avoid sleeps and wall-clock waiting. Use virtual time or explicit scheduler advancement.
- Tests must isolate their databases, files, preferences, notification state and external-service fixtures.

### 2.2 Target platforms

The primary physical acceptance target is the **Samsung Galaxy S26 Ultra**. Record its exact Android release/build and app build for each acceptance run. Emulator and JVM results do not substitute for physical-device validation. Add a representative secondary Android device/API-level matrix as the supported deployment range is finalized.

## 3. Existing test baseline and migration posture

The repository has about 150 Kotlin test files outside `legacy-android/` (Pokémon progression and ownership, rewards and the golden vector, PLAN, CARE, HEAL, MINT, HOME, LINK, WORLD, GEAR, persistence and compatibility, reminders, navigation, Compose screenshots). The database, sealing, backup/restore and importer tests run on the JVM against real SQLite files and the five committed 0.123.0 fixtures (`app-core/src/jvmTest/resources/fixtures/0.123.0/`). A file's existence is not an execution report; the run result is what counts (section 1).

**Known gaps (still true):**

- `androidApp` has no test source set and there is no instrumented suite: widgets, reminders and notification buttons, permissions and boot recovery are untested off a device. The only on-device check is the debug **DB check** icon (`DeviceSelfCheck`, `DATA_AND_MIGRATIONS.md` section 4.5). Legacy example tests do not count as acceptance coverage.
- `ScreenshotTest` renders screens to PNG and asserts only that the encoded image is larger than a minimum size (it protects against a blank screen, not a visual change). It is **not a golden test** and must not be counted as visual regression coverage in a release gate; approved golden images, font-scale and dark-mode comparisons do not exist yet.
- The real kill during a write, low storage and the Keystore path are proven on the JVM only (`DATA_AND_MIGRATIONS.md` section 4.5).

When tests are replaced at a new ownership boundary: map each to the contract it protects, preserve meaningful regressions, update obsolete assumptions only with an explicit specification decision, and do not remove a test merely because it fails during migration (decide whether it exposes a regression or obsolete behavior).

## 4. Unit and domain tests

Every domain must test its rules independently of Compose, Android framework classes, persistence implementation and external providers.

Required coverage:

- Valid, invalid, missing, empty and boundary inputs.
- State transitions, illegal transitions and terminal states.
- Numeric precision, rounding, limits and overflow handling.
- Date, time-zone, locale, recurrence and daylight-saving behavior where relevant.
- Deterministic outputs for identical inputs.
- Randomized behavior with controlled seeds and explicit distribution/invariant checks where probability is part of the specification.
- Concurrent or repeated commands where domain correctness depends on ordering or uniqueness.
- Versioned rules and compatibility behavior.
- Error types and recoverable versus terminal failures.

Use property-based or generative tests where they materially improve confidence, particularly for recurrence, conversions, progression, reward constraints, inventory quantities and serialization invariants. Keep example-based tests for named product rules and important edge cases.

WORLD simulations, expeditions and battle outcomes must be reproducible from recorded initial state, rules version and random seed. Do not make tests depend on uncontrolled random outcomes or fragile probability thresholds.

## 5. Persistence and repository tests

Repository tests must exercise the real persistence implementation wherever practical, not only mocked repositories. Verify:

- Create, read, update, delete and query behavior.
- Filtering, sorting, pagination and stable ordering.
- Unique constraints, foreign keys and referential integrity.
- Atomic transactions and rollback on failure.
- Concurrent readers/writers and conflict policy.
- Serialization round trips and unknown-field handling.
- Database close/reopen and cold-start reconstruction.
- Process death during or immediately after writes.
- Large collections and bounded query behavior.
- Storage-full, I/O failure and inaccessible-file behavior.
- Durable outbox/event state where asynchronous processing is used.

Tests must assert authoritative records and derived projections separately. Rebuilding a projection must not mutate source records or create reward events.

Required database-specific tests: foreign keys are read back as enabled on every connection and the store refuses to open without them; a schema edit without a numbered migration fails the build (`verifyCommonMainDataDbMigration` against the committed `data/databases/N.db`); a newer schema is left untouched; a save writes only the rows that changed (counted with triggers); the ledger cannot be edited or emptied even by raw SQL; an activation that cannot finish leaves the old row live; tests do not log or write Heal/Mint rows unsealed (`DATA_AND_MIGRATIONS.md` section 10.4.1).

## 6. Data migrations, backup and recovery

Migration and recovery are P0 release requirements. Maintain versioned fixtures from every supported source schema, including representative real-world shapes and synthetic edge cases. The legacy 0.123.0 save format must be represented if it remains a supported import/upgrade source.

For every supported migration path, verify:

1. The original source or backup remains intact until migration commit is durable.
2. Migration is deterministic and safe to retry, or has a documented single-use transaction boundary.
3. Every supported record is mapped or explicitly reported as unsupported.
4. Stable IDs and relationships are preserved or deterministically remapped.
5. Pokémon species/form identity and individual owned-Pokémon identity remain correct.
6. Currency, Bag quantities, progression and reward history reconcile.
7. Module records, references, timestamps, attachments and supported story/expedition progress survive.
8. Unknown fields are preserved where the compatibility contract requires it.
9. Failure at each migration phase leaves a recoverable state and never silently destroys the source.
10. Reopening the migrated database produces the same valid state and does not reapply side effects.

Backup/restore tests must cover interrupted export, interrupted import, invalid or tampered backup, wrong password/key, insufficient storage, partial file writes, app reinstall where supported, and restore into a clean installation. Verify integrity before replacing existing data. Document unsupported downgrade paths rather than implying reversibility.

## 7. Reward ledger, idempotency and duplication prevention

**Critical release gate.** The shared reward ledger and Pokémon ownership model are authoritative. Reward-bearing actions must be represented by canonical source events and stable idempotency keys. A UI callback, notification action, retry or replay is not a new business event by itself.

Automated tests must prove:

- Processing one eligible source event once creates the expected ledger transaction once.
- Reprocessing the same event, including after app restart, produces no additional payout.
- Concurrent duplicate submissions cannot create duplicate ledger entries.
- A crash between event acceptance, reward calculation, ledger write and projection update cannot lose or duplicate value.
- Offline queue replay and provider re-import cannot create duplicate rewards.
- Editing, reopening, rescheduling or completing an already-completed record does not pay again unless the specification defines a distinct eligible event.
- One-time milestones, quests, achievements and recruitment claims cannot be claimed twice.
- Reversals/corrections are explicit, auditable compensating entries; history is not silently rewritten.
- Currency and inventory projections reconcile exactly with ledger entries.
- Recruitment creates one persistent owned individual; retries do not create extra Pokémon.
- Evolution, transfer and form changes preserve canonical individual identity and ownership invariants.

Required cross-module scenarios include task completion through multiple surfaces, HOME-created tasks owned by PLAN, CARE routine completion, linked focus sessions, repeated WORLD expedition completion/resume, milestone claims and offline event replay. Assert both ledger rows and final balances/ownership. Include property/invariant tests for non-negative quantities, unique transaction IDs, unique event application and reconciliation.

## 7.1 Held items, item acquisition and expanded Pokémon mechanics

Held-item behavior is a P0 data-integrity invariant and must have dedicated unit, repository, transaction and migration coverage:
- Equip, unequip and swap preserve item ownership and quantity; enforce one active assignment per item instance and one held item per Pokémon in the initial model.
- Trigger every supported effect, including per-encounter triggers, and assert the item remains owned/equipped with unchanged quantity and is reusable in the next eligible encounter; reject lifetime single-use behavior.
- Verify duration, cooldown and once-per-encounter/floor/expedition runtime resets without consuming or deleting the item.
- Verify process death, restart, offline settlement, backup/restore and interrupted transactions preserve both item and assignment.
- Reject double assignment, incompatible species/form assignment, stale assignment updates and crafting/consumption of an equipped held item.
- Verify all item grants have a declared source, stable idempotency key, provenance and ledger entry. Retry mission, floor, boss, shop and crafting settlement and assert no duplicate item grants or partial purchases.
- Verify shop purchase and crafting atomically debit Poké/ingredients and grant outputs; rollback all sides on failure.
- Verify essential evolution materials have deterministic/reliable paths and species-specific eligibility.
- Verify legacy migration reconciles equipped-only items and Bag stacks without duplicate inventory or silent loss.
- Verify Ability, Hidden Ability, Nature/Mint, move, training, evolution, transformation, Ribbon/Mark and research effects are centralized, deterministic and eligibility-safe.
- Verify transformations are temporary encounter state and never create duplicate owned Pokémon or permanent base-stat inflation.
- Verify offline jobs, berries, outbreaks and research settlement are idempotent, non-expiring and free of attendance penalties.
- Verify no real-life sensitive data from HEAL, MINT, CARE, LINK or HOME enters item acquisition, drop rates, combat or Pokémon progression.

## 8. Module ownership and integration contracts

Each module owns its records and domain rules. Cross-module communication must use documented commands, events, references or read-only projections. Architecture/contract tests must prevent:

- Direct writes into another module's private persistence or domain state.
- Duplicate authoritative representations of the same real-world record.
- Independent Pokémon collections, Bag inventories, reward engines or Poké balances.
- Dependencies from domain code on Compose or platform UI internals.
- Private CARE, HEAL or MINT details leaking into WORLD or shared reward envelopes.
- External provider data silently replacing native authoritative records.
- A projection refresh or event replay generating new business side effects.

Every module must test its public contracts: accepted commands, emitted events, referenced IDs, error behavior, idempotency expectations and data minimization. Include negative tests that prove prohibited fields and writes are rejected.

## 9. Offline, interruption and recovery behavior

Test all workflows whose specifications promise local operation with connectivity unavailable. Scenarios include:

- Airplane mode before launch and during an active workflow.
- Network loss during provider import, export or synchronization.
- Connectivity restoration after queued local changes.
- Duplicate, delayed, out-of-order and partial synchronization responses.
- Activity recreation, app background/foreground and process termination.
- Device reboot and app upgrade.
- Low-memory process death and storage pressure.
- Clock, locale and time-zone changes.
- Permission denial or revocation while the app is active.

Operations must either commit atomically or remain safely resumable. Never leave ambiguous partial rewards, ownership changes or financial records. Retry policies must be bounded and idempotent. Local-first workflows must not block on an unavailable provider unless the feature explicitly requires it.

## 10. Reminders, scheduling and notifications

`../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` is the behavioral authority. Test the shared reminder scheduler independently from module adapters.

Automate policy and scheduling tests for create, update, reschedule, snooze, defer, cancel, completion, deletion, duplicate request prevention, quiet hours, focus deferral, recurrence, time-zone changes, daylight-saving transitions, stale requests, invalid source references, and reboot rescheduling policy. Verify notification action routing back to the owning module and ensure actions are idempotent.

Android instrumented and physical-device tests must verify actual permission prompts/state, notification channel configuration, disabled channels, delivery, grouping, action buttons, reboot behavior, exact/inexact scheduling constraints, background restrictions and manufacturer-specific behavior. Denial of notification permission must not corrupt reminder records; the app must explain the degraded state and preserve in-app access.

## 11. Android lifecycle and background behavior

Instrument Android-specific behavior including:

- Activity recreation and configuration changes.
- Process death and state restoration.
- App task removal and relaunch.
- Background/foreground transitions.
- WorkManager/job scheduling, constraints, retries and cancellation where used.
- Device reboot and package update recovery.
- Doze, battery saver and background execution limits.
- App standby and restricted background modes.
- Permission revocation and settings changes.
- Launcher role/default-home changes and fallback to the system launcher.

Do not depend on a continuously running process for durable work. Persist the minimum state needed to resume. Verify that background work is bounded, cancellable and does not duplicate foreground work.

## 12. Compose UI and visual regression

Compose tests must verify semantic structure and interaction, not just that a composable rendered. Cover navigation, user actions, validation, empty/loading/error states, dialogs, sheets, back behavior, focus handling and state restoration.

Golden/screenshot tests must compare rendered output against reviewed reference images. Capture representative screens and states across:

- Light and dark themes.
- Empty, normal populated, dense and error/permission-denied states.
- Relevant selected, expanded, pressed and disabled states.
- Supported narrow/wide window sizes and orientation changes where supported.
- Default and enlarged font scales.
- Relevant display densities and insets.

Fix screenshot environment variables such as density, font scale, locale, clock and animation state. Control dynamic content and use stable fixtures. Define a documented image-diff tolerance; investigate every unexpected difference. Golden updates require explicit review and must never be auto-accepted merely to clear CI. A screenshot test that only checks that pixels were drawn is a smoke test, not a golden regression test.

## 13. Theme, responsive layout and accessibility

Test all shared components and representative module screens in light and dark themes, including contrast, disabled/error/focus states, surfaces, overlays and system bars. Test the responsive layouts defined by the design system at minimum supported width, typical phone width, large phone width and larger supported windows. Verify that content is not clipped, controls remain reachable and scrolling works with large text.

Accessibility automation must verify:

- Meaningful labels, roles, state and value announcements.
- Logical TalkBack traversal and focus order.
- Correct grouping of compound controls and exclusion of decorative content.
- Keyboard, switch and D-pad navigation where applicable.
- Dialog/sheet focus entry, containment and restoration.
- Adequate touch targets and spacing.
- Text scaling without clipping or loss of actions.
- Information is not conveyed by color alone.
- Reduced-motion behavior and accessible alternatives to animation.

Automated semantics checks are necessary but not sufficient. Perform manual TalkBack acceptance on the physical target for core workflows and custom controls.

## 14. Pixel-art rendering, assets and animation

Test the rendering pipeline for nearest-neighbor/integer scaling where specified, pixel-grid alignment, sprite dimensions, transparent edges, alpha handling, palette consistency, clipping, atlas lookup, missing-asset fallback and asset density selection. Golden tests should include representative sprites and scenes at supported sizes.

Animation tests must verify frame order, frame duration, loop boundaries, deterministic playback under a test clock, pause/resume, visibility suspension, lifecycle disposal and reduced-motion behavior. Validate that animations do not continue consuming unnecessary resources while off-screen or backgrounded. Audio tests should cover asset fallback, playback failure, mute/volume settings, focus loss and interruption by other audio.

Asset pipeline tests must also validate manifest references, missing files, duplicate IDs, unsupported formats and required attribution/license metadata. A technically renderable asset is not automatically approved for distribution; rights review remains a release responsibility.

## 15. Performance, memory and battery

Measure user-visible performance on representative hardware. Establish budgets from measured baselines and product requirements; do not invent thresholds or report emulator measurements as device results.

Measure:

- Cold/warm startup and time to first usable screen.
- Navigation and interaction latency.
- Database query/write latency and large-collection behavior.
- Scroll frame stability and dropped frames.
- Asset decode/load time and cache hit rate.
- Sprite/scene rendering and animation frame pacing.
- Launcher scene, widget and roaming-Pokémon overhead.
- Peak memory, retained caches, leaks and long-session growth.
- Idle/background battery drain and scheduled-work frequency.
- Thermal behavior during sustained rendering or processing.

Use repeatable benchmark fixtures and profile on the Galaxy S26 Ultra. Verify that hidden launcher surfaces and off-screen animations suspend work. Test low-memory behavior and cache eviction. Performance regressions beyond agreed budgets block release until corrected or explicitly accepted with evidence and an owner.

## 16. Security and privacy testing

Test the actual storage, integration and data-boundary design described in `ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md`.

Required checks include private storage and backup boundaries, encryption requirements, backup integrity, token/credential handling, secure intents and deep links, permission denial/revocation, export and deletion behavior, notification privacy, log/crash-report redaction, network egress, dependency vulnerabilities, release signing and secret handling.

Use synthetic data. Never commit real financial, health, journal or identity data to fixtures. Assert that CARE journal content, HEAL measurements and MINT financial details do not leak through shared Pokémon events, reward envelopes, analytics, logs, notifications, screenshots or diagnostics. Test unauthorized access and malformed external input. Security findings must be triaged by severity; unresolved critical findings block release.

Sealing tests that must exist are listed in `DATA_AND_MIGRATIONS.md` section 10.4.1: no figure, name, note or date of Mint or Heal in any stored text, snapshot, backup file or kept-aside copy; fail-closed without the key; another phone's key sets rows aside sealed; the passphrase backup opens only with the passphrase.

## 17. External integration failure and permission revocation

Every integration must sit behind a provider-independent interface. Test adapters with fakes and contract fixtures for:

- Provider unavailable or unsupported.
- Permission denied, limited or revoked.
- Empty, stale, duplicated, malformed or partial responses.
- Timeout, rate limit, authentication expiry and transient server errors.
- Device/API-version incompatibility.
- Duplicate imports and source-record updates/deletions.
- Timestamp, timezone, provenance and unit handling.
- Retry, cancellation and recovery after provider restoration.

A passing fake-adapter test proves only the adapter's behavior. It does not prove a real provider, account, wearable or device exposes the required capability. Verify actual feasibility and permissions on supported hardware before declaring an integration available. Unsupported providers must fail gracefully and must not block native app use.

## 18. End-to-end user workflows

Automate complete critical journeys and assert durable outcomes, not just transient UI messages. Maintain fixtures and workflow coverage for:

| Workflow | Required assertions |
|---|---|
| PLAN task lifecycle | Create/edit/complete, persist, correct owner and one reward |
| Focus session | Start/pause/resume/finish, duration accounting and interruption recovery |
| CARE routine | Completion, privacy boundary, reminder and reward ownership |
| HEAL activity import | Permission, provenance, deduplication, local history and denial path |
| MINT transaction | Create/edit, balance projection, persistence and private-data boundary |
| HOME integration | Linked task/reference with PLAN retaining completion authority |
| LINK follow-up | Record, reminder, routing and duplicate-action safety |
| GEAR utility | Input validation, calculation correctness and result persistence if applicable |
| WORLD expedition | Team selection, deterministic simulation, pause/resume and single outcome/reward |
| Pokémon evolution | Eligibility, item consumption, form/state and individual identity |
| Pokémon transfer | Ownership, location/party/box state and collection consistency |
| Backup recovery | Export, clean install/reset, restore and integrity verification |
| Launcher | Home scene, roaming Pokémon, buddy, widgets, shortcuts, default-home selection and fallback |

At least one cross-module journey must demonstrate that a completed real-life action becomes a minimized eligible event, is processed by the shared progression system, and updates the shared Pokémon collection without exposing the originating module's private data.

## 19. CI and automation policy

The following categories are mandatory automated checks:

- Domain and business-rule tests.
- Reward-ledger idempotency and ownership invariants.
- Repository/database and serialization tests.
- Migration, backup and recovery tests.
- Module ownership and contract tests.
- Reminder policy and scheduler logic.
- External adapter behavior and failure handling.
- Compose semantics and navigation tests.
- Approved screenshot/golden comparisons.
- Automated accessibility semantics checks.
- Static analysis, dependency and security checks.
- Critical end-to-end journeys.
- Android compilation and instrumented emulator tests.

Recommended CI sequence:

1. Formatting, lint and static analysis.
2. Common/domain tests.
3. Repository and persistence tests.
4. Migration and recovery suite.
5. Reward ledger and Pokémon ownership suite.
6. Module contract/integration suite.
7. JVM Compose semantics and golden tests.
8. Android debug and release compilation.
9. Android instrumented emulator suite.
10. Release artifact, signing-configuration and dependency verification.

Run focused tests during development and the complete relevant regression suite before merge/release. Flaky tests must be diagnosed and corrected; do not silently skip, indefinitely retry until green or delete a test because it exposes nondeterminism. Associate results with commit, build variant, test environment and timestamp. Publish test reports and preserve failure artifacts such as screenshots/logs with sensitive data redacted.

Physical-device tests remain a separate required acceptance activity and must be recorded in the release evidence.

## 20. Real-device acceptance

The primary target is the Samsung Galaxy S26 Ultra. Run a production-like signed or release-equivalent build and record exact device model, Android version/build, app version, commit, build variant and test date.

Required checks:

- Fresh install, upgrade install and supported migration.
- Database creation, reopen, backup and restore.
- Cold/warm startup and core navigation across all eight modules.
- Shared Pokémon collection, rewards, inventory and evolution.
- Light/dark theme, font scaling, display scaling and responsive layout.
- Manual TalkBack traversal of critical workflows.
- Notification permission, channels, delivery and actions.
- Process death, app relaunch, reboot and interruption recovery.
- Offline workflows and connectivity restoration.
- Storage pressure and low-memory behavior.
- Relevant camera/sensor/file/external-intent permission paths.
- Widget resizing and third-party launcher hosting.
- Actual default-home selection, launcher behavior and fallback to system launcher.
- Audio focus/interruption and animation suspension.
- Battery/performance profiling and thermal behavior where applicable.

Use a device acceptance record with scenario, expected result, actual result, pass/fail/not-run, evidence link, defect reference and reviewer. Emulator success cannot substitute for physical acceptance. If a capability depends on hardware/provider support, record the exact configuration and limitations.

## 21. Release gates and severity

### P0 — Always release-blocking

- Known critical data loss, corruption or unrecoverable migration failure.
- Duplicate currency, inventory, reward transactions or Pokémon ownership.
- Broken supported backup/restore or migration integrity.
- Sensitive-data leakage or unauthorized access.
- Critical security vulnerability or exposed signing secrets.
- Core offline workflows failing where offline operation is promised.
- Critical end-to-end journeys failing.
- Unhandled lifecycle/permission failure that risks user data or security.

### P1 — Release-blocking unless explicitly waived with documented evidence

- Essential module workflow failure.
- Reminder scheduling/recovery failure that loses user obligations or creates harmful duplicates.
- Material accessibility failure in core workflows.
- Major light/dark theme or responsive layout defect.
- Unapproved golden-image regression in a core screen.
- Performance, memory or battery regression beyond approved budgets.
- Physical-device launcher, widget or notification failure for a claimed supported capability.

P2/P3 issues may be deferred only when they do not compromise essential functionality, data integrity, security, privacy, accessibility or documented usability requirements. Deferrals require severity, impact, workaround, owner and target resolution. No silent waiver.

## 22. Regression prevention and definition of done

Every fixed defect receives a regression test at the lowest practical layer. Every new feature includes test cases in its implementation plan. Shared-system changes trigger dependent-module regression suites. Schema, reward, reminder, Pokémon ownership and privacy-boundary changes trigger their full dedicated suites. Golden changes require explicit visual review.

A feature is test-complete only when:

1. Documented domain rules have automated coverage.
2. Persistence and failure behavior are verified.
3. Ownership and integration contracts are verified.
4. Offline/interruption behavior is covered where applicable.
5. UI states, theme and accessibility requirements are verified.
6. Relevant regression tests pass on the recorded commit.
7. Required physical-device behavior is accepted.
8. Known limitations and unverified behavior are documented.

Maintain separate status values in implementation/release tracking:

- **Specified:** requirements and expected behavior are defined.
- **Implemented:** code exists; this alone is not verification.
- **Automated verified:** the relevant automated suite was executed and passed for the identified commit/build.
- **Device accepted:** required physical-device checks were executed and accepted.

Never infer a higher status from a lower one. A successful compile is not a passing test suite; a passing emulator suite is not device acceptance.

## 23. Ownership and maintenance

- Domain/module owners maintain domain and module contract tests.
- Data architecture owners maintain repository, migration, backup and recovery tests.
- Pokémon/economy owners maintain reward ledger, inventory and ownership invariants.
- Platform owners maintain Android lifecycle, scheduling, permissions and provider adapters.
- Design-system owners maintain Compose semantics, golden images and accessibility baselines.
- Release acceptance owners maintain device matrix, evidence and release gates.

This document defines the shared framework. Feature specifications remain the source of truth for detailed expected behavior; this strategy defines how those expectations must be verified.

## 24. Related specifications

- [Product Bible](../PRODUCT_BIBLE.md)
- [Implementation Guide](../IMPLEMENTATION_GUIDE.md)
- [Data Architecture and Migrations](DATA_AND_MIGRATIONS.md)
- [Integrations and Privacy](INTEGRATIONS_AND_PRIVACY.md)
- [Pokémon Ecosystem](../FEATURES/POKEMON_ECOSYSTEM.md)
- [Reminders](../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture)
- [Design System](../FEATURES/DESIGN_SYSTEM.md)
- [Assets and Audio](../FEATURES/ASSETS_AND_AUDIO.md)
- [Launcher](../FEATURES/LAUNCHER.md)
- Module specifications: `../MODULES/`

- `../FEATURES/POKEMON_ECOSYSTEM.md` Section 20 — persistent held-item, item-acquisition, expanded Pokémon progression and WORLD activity acceptance requirements.

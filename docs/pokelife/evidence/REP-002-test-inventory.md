# REP-002: existing tests mapped to the target contracts

**Evidence for:** `ARCHITECTURE/TEST_STRATEGY.md` section 3 and `IMPLEMENTATION_GUIDE.md` section 9.  
**Checked:** 6 November 2026, repository `main` at commit `2b22975d`, by reading file names and the screenshot test. A file here is evidence that a test exists, not that it passes; the run result is in the guide (section 3.1).

## 1. Counts

90 `*Test*.kt` files outside `legacy-android/`: pokemon-core 20 (including `TestSupport`), care-core 1, world-core 13, plan-core 16, app-core 19, composeApp 21, **androidApp 0**.

## 2. Mapping to the target contracts

| Target contract (spec) | Tests that protect it today | Gap |
|---|---|---|
| Shared Pokémon: owned instance, stats, nature, evolution, items, guardians, perks | `OwnedPokemonTest`, `NatureAndStatsTest`, `EvolutionRulesTest`, `EvolutionDataTest`, `ItemsTest`, `GuardiansTest`, `PerksTest`, `PerksStoreTest`, `FormDexTest`, `PcViewTest`, `DexDataTest`, `MoveDataTest` | Roles (`POKEMON_ECOSYSTEM.md` section 2.2): no test of a six-slot party with a lead, or of module-scoped bonuses (REP-009) |
| Eggs and hatching | `EggTest`, `HatchRulesTest`, `HatchingTest` | None known |
| Reward engine and economy | `RewardEngineTest`, `RewardJsonTest`, `GoldenVectorTest`, `CandyRewardsTest`, `CareRewardTest`, `TransferTest`, `TrainerRankTest`, `GenerousEconomyTest`, `CountersTest`, `QuestsTest` | The golden vector exists. Several store tests mention double payment or duplicates (`GameStoreTest`, `CareStoreTest`, `WorldStoreTest`), but I did not check whether they cover the spec's ledger-level idempotency, which is not caller-side; a single Poké currency needs new vectors (REP-010) |
| PLAN: tasks, recurrence, focus, goals, reminders | the 16 `plan-core` tests (`TaskRulesTest`, `RecurrenceTest`, `FocusSessionTest`, `GoalTest`, `RemindersTest`, …) | Habits are still a separate system; the spec merges them into recurring tasks |
| CARE | `CareRulesTest` (care-core), `CareStoreTest`, `CareRemindersTest`, `CareSyncTest` (app-core) | CARE is thinly tested at the rules layer (one file) compared with its spec |
| WORLD: progression, story, regions | the 13 `world-core` tests, `WorldStoreTest` | Deterministic expedition and reward vectors against the shared ledger are not separated yet |
| GEAR | `GearTest`, `GearTimersTest` (app-core); `CalcEngineTest`, `UnitCatalogTest`, `CurrencyRowsTest`, `ToolCatalogTest`, `GearToolsTest`, `FieldToolsTest`, `SensorMathTest`, `RandomizerTest`, `QrCodeTest`, `LightTest`, `WorldClockTest` (composeApp) | Lifecycle and permission behaviour per tool is not covered |
| Persistence and migration | `SaveCompatibilityTest`, `GameStoreTest`, `NudgeStoreTest` | No fixture files exist; there are no migration, interrupted-write or recovery tests for the target database (REP-003, REP-006) |
| Cloud (to be retired) | `SyncManagerTest`, `SocialClientTest`, `CareSyncTest` | Remove with REP-012 after Gate B |
| Navigation and launcher | `NavigationTest`, `BackChainTest`, `LauncherModelTest`, `WidgetGridTest` | No test of the launcher's Android lifecycle |
| UI rendering | `ScreenshotTest` | See section 3 |
| Android platform (widgets, reminders, permissions, boot recovery) | none | `androidApp` has no test source set and there is no instrumented suite |

Tests tied to the legacy architecture, to replace at the new ownership boundary: `SyncManagerTest`, `SocialClientTest` and `CareSyncTest` (cloud). `GameStoreTest`, `CareStoreTest` and `SaveCompatibilityTest` exercise the JSON save and will need review when the database lands (not read in detail here).

## 3. The screenshot test is not a golden test

`composeApp/src/jvmTest/.../ScreenshotTest.kt` renders screens offscreen to PNG files and asserts only that the encoded image is larger than a minimum size (its own comment says "The assertion only checks that something was drawn"). It protects against a blank screen, not against a visual change. The design system and test strategy both call for approved golden images, font-scale and dark-mode coverage; none exist yet.

## 4. Consequences

- Keep every existing domain test; none needs removing for the target architecture except the cloud tests above, after REP-012.
- The largest gaps are on the Android side (no tests of widgets, reminders, boot recovery or permissions), migration and recovery, the Pokémon roles, and real visual comparison.
- Do not count the screenshot test as visual regression coverage in a release gate.

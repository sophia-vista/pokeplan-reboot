Saves written by app version 0.123.0 (`AppData.SCHEMA` 1) with fixed clocks and a fixed random seed, from `SaveFixtureTest`.

| File | What it is |
|---|---|
| `main-empty.json` | A new save, nothing done |
| `main-typical-week.json` | Seven days of use: 22 tasks, a habit, a goal, 7 focus sessions and the Pokémon they hatched |
| `main-heavy-70-days.json` | Seventy days of use, for a long history (about 170 KB) |
| `main-focus-running-and-pending.json` | A focus session running and a finished task not yet paid, as a widget leaves it |
| `care-week.json` | The Care save (`care.json`) with a routine ticked for seven days |

These are baselines for the storage migration (docs/pokelife REP-003 and REP-006). Do not edit them; add a new folder named after the version that wrote them if a new baseline is needed.

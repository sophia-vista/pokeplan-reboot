# PokéLife documentation (source of truth)

This folder is the **source of truth** for what PokéLife is and what to build (owner decision, 6 November 2026). Start with `PRODUCT_BIBLE.md`, then the module or feature you are working on. `IMPLEMENTATION_GUIDE.md` records what the code does today and the order to build the rest. `IMPLEMENTATION_BACKLOG.md` is the active work list.

This README is orientation only; it specifies nothing.

`evidence/` holds the owner's interview of 15 November 2026. It is evidence, not authority: what it decided is in the specifications and the backlog.

## How it relates to the repository's older documents

Precedence follows the Product Bible (section 10). The older documents in `docs/` are **evidence of current behaviour, not authority**. Where one disagrees with this folder, this folder wins.

| Older document | Still used for | Superseded by this folder in |
|---|---|---|
| `docs/design.md` | Rules the code implements today (Pokémon, rewards, perks) until each is migrated | Product direction, modules, buddy and party roles |
| `docs/economy.md` | The numbers the code pays today (`RewardRules.VERSION`) | The shape of the economy (one Bag, one currency, the ledger) in `FEATURES/POKEMON_ECOSYSTEM.md` |
| `docs/regional-storylines.md` | The regional expedition and main-storyline design | `MODULES/WORLD.md` for structure and contracts |
| `docs/world-story-engine.md`, `docs/world-story-rewrite.md` | How the story and scene engine works, and the writing brief with the scripts not yet in the code | `MODULES/WORLD.md` for structure and contracts |
| `docs/assets.md` | Where each asset comes from | `FEATURES/ASSETS_AND_AUDIO.md` for the policy |
| `legacy-android/` | Reference for the original rules | Unchanged: reference only |

## Names in the older documents

| Older name | Now |
|---|---|
| Health region | HEAL |
| Finance region | MINT |
| Connect region | LINK |
| Pokégear | GEAR |
| Explore region | Part of WORLD |
| Grow region | Not a module (goals and milestones are PLAN's, reflection is CARE's) |
| HOME (the household module) | BASE. Stored names keep `home_*` and `HOME`; WORLD's old "Base" destination is now Town |
| PokéPlan as the name of the whole app | PokéLife. PLAN is the planning module; code packages, the repository and the Android application id keep `pokeplan` |
| Plan coins, World Poké | One currency: Poké |
| Plan's buddy, Care's lead | The global buddy; each module's party lead (`FEATURES/POKEMON_ECOSYSTEM.md` section 2.2) |

## Keeping it current

Change the owning document when a requirement changes, then the guide and the backlog in the same pull request. Never mark something built, tested or device-verified from a specification alone.

# PokéLife documentation (source of truth)

This folder is the **source of truth** for what PokéLife is and what to build (owner decision, 6 November 2026). Start with `PRODUCT_BIBLE.md`, then the module or feature you are working on. `IMPLEMENTATION_GUIDE.md` records what the code actually does today and in what order to build the rest. `IMPLEMENTATION_BACKLOG.md` is the active work list.

This README is orientation only; it specifies nothing.

## How it relates to the repository's older documents

Precedence follows the Product Bible (section 10). The older documents in `docs/` were written for the app as it was built and are **evidence of current behaviour, not authority**. Where one disagrees with this folder, this folder wins.

| Older document | Still used for | Superseded by this folder in |
|---|---|---|
| `docs/design.md` | Rules the code implements today (Pokémon, rewards, perks) until each is migrated | Product direction, modules, buddy and party roles, accounts and cloud |
| `docs/ecosystem.md` | History of the module plans and the effort-event idea | Module map (sections 4, 11, 12), roles, regions |
| `docs/economy.md` | The numbers the code pays today (`RewardRules.VERSION`) | The single Poké currency (changes with the next reward-rules version) |
| `docs/world*.md`, `docs/regional-storylines.md` | World content and the story rewrite in progress | `MODULES/WORLD.md` for structure and contracts |
| `docs/ui-ux-brief.md`, `docs/DESIGN_SYSTEM.md` | Theme tokens in code | `FEATURES/DESIGN_SYSTEM.md` |
| `docs/assets.md` | Where each asset comes from | `FEATURES/ASSETS_AND_AUDIO.md` for the policy |
| `supabase/`, `app-core/cloud`, `tools/server` | Existing backend and sync | Retired after the migration gates (Bible section 3) |
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
| Plan coins, World Poké | One currency: Poké |
| Plan's buddy, Care's lead | The global buddy; each module's party lead (`FEATURES/POKEMON_ECOSYSTEM.md` section 2.2) |
| Coins, candies, eggs, `RewardRules` | Unchanged until the reward rules are versioned |

## Keeping it current

Change the owning document when a requirement changes, then the guide and the backlog in the same pull request. Never mark something built, tested or device-verified from a specification alone.

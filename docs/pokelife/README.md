# PokéLife documentation (source of truth)

This folder is the **source of truth** for what PokéLife is and what to build (owner decision, 6 November 2026). Start with `PRODUCT_BIBLE.md`, then the module or feature you are working on. `IMPLEMENTATION_GUIDE.md` records what the code does today and the order to build the rest. `IMPLEMENTATION_BACKLOG.md` is the active work list.

This README is orientation only; it specifies nothing.

`evidence/` holds dated reviews and audits (the UI review at 0.172.0, the feature and personal-use audit at 0.199.0). They are evidence, not authority: what they decided is in the specifications and the backlog.

## How it relates to the repository's older documents

Precedence follows the Product Bible (section 10). The older documents in `docs/` are **evidence of current behaviour, not authority**. Where one disagrees with this folder, this folder wins.

| Older document | Still used for | Superseded by this folder in |
|---|---|---|
| `docs/design.md` | Rules the code implements today (Pokémon, rewards, perks) until each is migrated | Product direction, modules, buddy and party roles, accounts and cloud |
| `docs/economy.md` | The numbers the code pays today (`RewardRules.VERSION`) | The single Poké currency (changes with the next reward-rules version) |
| `docs/regional-storylines.md` | The story rewrite in progress | `MODULES/WORLD.md` for structure and contracts |
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
| Plan coins, World Poké | One currency: Poké |
| Plan's buddy, Care's lead | The global buddy; each module's party lead (`FEATURES/POKEMON_ECOSYSTEM.md` section 2.2) |
| Coins, candies, eggs, `RewardRules` | Unchanged until the reward rules are versioned |

## Keeping it current

Change the owning document when a requirement changes, then the guide and the backlog in the same pull request. Never mark something built, tested or device-verified from a specification alone.

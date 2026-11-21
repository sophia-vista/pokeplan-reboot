# PokéLife Pokémon Ecosystem

**Status:** Authoritative specification. Open items carry REP ids in `IMPLEMENTATION_BACKLOG.md`.  

> This document is the authoritative specification for the shared Pokémon domain: canonical species data, owned instances, collection and storage, module parties, buddy selection, progression, eggs, items, currency, rewards, acquisition contracts, and cross-module rules. Module specifications remain authoritative for their own acquisition triggers and local user workflows. The Product Bible defines product intent; the Data and Migrations architecture defines physical persistence and migration execution.

## 1. Purpose and invariants

PokéLife has one Pokémon ecosystem shared by PLAN, CARE, HEAL, MINT, BASE, LINK, GEAR, WORLD, and the living launcher. The ecosystem must feel coherent while allowing each module to express a distinct regional identity and acquisition loop.

The following invariants are non-negotiable:

1. **One canonical collection.** Every owned Pokémon exists exactly once as an authoritative owned instance.
2. **Shared ownership, separate rosters.** Each applicable module has its own independent party/roster. WORLD expedition teams remain WORLD-owned and independent. Parties and teams contain references to owned Pokémon IDs, never copies.
3. **Shared progression.** Level, XP, evolution, nature, stats, moves, abilities, held item, nickname, shiny/form state, and ownership history belong to the canonical owned instance.
4. **One shared inventory and currency.** There is one Bag and one universal Poké balance.
5. **One reward settlement.** Every eligible source event has one owner, one stable identity, and at most one successful settlement.
6. **Distinct acquisition identities.** Module-specific acquisition methods remain separate and recognizable; they do not create separate collections or parallel reward ledgers.
7. **Privacy by construction.** Sensitive real-life content and measurements are not progression inputs. Only explicitly approved, minimal eligibility events may cross a module boundary.
8. **Offline-first.** Collection, progression, hatching, reward settlement, inventory, and currency work locally without an account or network connection.
9. **Generous and forgiving.** No login requirement, attendance pressure, punitive streaks, decay, expiring essential progress, or permanent Pokémon loss.
10. **Canon-aware, product-sized mechanics.** Preserve recognizable Pokémon identity without importing competitive simulator complexity or breeding/IV/EV optimization.

## 2. Ownership and module boundaries

### 2.1 Domain ownership

| Domain | Authoritative owner | Other modules may |
|---|---|---|
| Species, forms, types, base data, evolution metadata | Pokémon catalog | Read/cache catalog data |
| Owned Pokémon instances and their progression | Shared Pokémon domain | Reference and display instances |
| National and regional Pokédex state | Shared Pokédex domain | Display regional views |
| Module parties | Respective module | Read only through contracts where needed |
| WORLD expedition teams and assignments | WORLD | Read canonical Pokémon summaries |
| Global buddy selection | Shared Pokémon preferences | Present buddy contextually |
| Eggs and hatch lifecycle | Shared Pokémon domain | Create approved module-origin eggs |
| Bag, item stacks and held-item assignments | Shared inventory domain | Request validated item operations |
| Poké balance and transactions | Shared economy domain | Request approved grants/spends |
| Reward event identity and settlement | Shared reward ledger | Submit eligible source events |
| Acquisition trigger and eligibility | Source module | Publish minimal approved event |
| Exploration Rank, campaigns and dungeon progress | WORLD | Read non-sensitive display summaries only |

No module may create a local shadow collection, local copy of a Pokémon's progression fields, private item inventory, or alternative currency balance. A module may cache read models for performance, but cache invalidation and source ownership must remain explicit.

### 2.2 Pokémon roles

An owned Pokémon is in **exactly one place** at any time: the global buddy, one module's party, a module's Guardian slot (one per module: a legendary or mythical is a normal Pokémon that can also be one module's Guardian, its perk applying in that module only), a WORLD expedition team, a GEAR tool as its helper, or the PC. A PLAN event's **companion** (PLAN.md 8.2) is not a place: it is a Pokémon already in the PLAN party that an event names, and naming it moves nothing. Putting a Pokémon in a place takes it out of the place it was in (the PC is the place of every Pokémon that holds no other); nothing is ever copied, and taking a Pokémon from a place never releases or deletes it. A Pokémon on an expedition under way cannot be moved until the expedition ends. The roles themselves stay independent of each other (changing one place never changes another place's other members).

| Role | Scope | What it does | What it never does |
|---|---|---|---|
| **Global buddy** | One for the whole app | The main companion: the face of the launcher, module headers, contextual scenes and WORLD's story | Multiply rewards, add combat stats, or gate a module action (section 2.3) |
| **Module party** (lead + 5) | One per applicable module (PLAN, CARE, HEAL, MINT, BASE, LINK) | Six slots: the **lead** in slot 1 and five members. The party gives **bonuses to its own module** (section 2.2.1) | Affect another module, WORLD, or any utility |
| **WORLD expedition team** | WORLD only | Goes on expeditions and dungeons under WORLD's own team-size and assignment rules | Count as a module party or share its bonuses |
| **GEAR helper** | GEAR tools and the Pokégear menu | A visual helper: its sprite is the tool icon and menu decoration | Have any effect: aesthetics only (`MODULES/GEAR.md` section 6) |

Places reference canonical owned IDs and never copy a Pokémon. A GEAR helper helps one tool. The place is derived from the references (the buddy id, the party each Pokémon is in, the Guardian slots, the WORLD teams and the GEAR helpers) and every operation that assigns a place first vacates the old one in the same save, so no state with two places can be written; a save from before this rule is resolved once on load (section 2.2.3).

#### 2.2.3 Resolving a save with a Pokémon in two places

Done once, deterministically and without removing any Pokémon, by this priority: Guardian slot, then WORLD team, then **buddy** (the starter stays the buddy), then module party, then GEAR helper. A Pokémon that holds several places keeps the highest and leaves the others. So a save whose buddy was also Plan's lead keeps the buddy and Plan's party loses it (an empty party is allowed; Plan's perks then come from whoever joins). If the buddy is lost this way (to a Guardian slot or a team) the buddy becomes the player's highest-level Pokémon that is in the PC (ties: friendliest, then id), so the app always has a face; with none in the PC the buddy stays empty until the player picks one. The resolution is recorded with the save (a version, so it runs once) and never pays or costs anything.

#### 2.2.1 Module party bonuses

- A party's bonuses apply **only to rewards and effects of its own module's activities** (for example PLAN's party affects PLAN's rewards, not CARE's). The party of one module never boosts another module or WORLD.
- The **lead** (slot 1) carries the strongest bonus; the five members add smaller assists. The mechanics and values are those of the implementation (buddy types, abilities, held items and assists in `docs/design.md`, "Party perks").
- Bonuses are **capped per bonus type** (starting value: +25% per bucket, a *tune* number) so no single Pokémon or build breaks the economy, and every bonus is **shown to the player**, not hidden.
- The Shiny hunter and Egg luck buckets and the Mega boost are party bonuses, so a party bonus may raise shiny and egg-luck odds; they stay capped per bucket and shown. Bonuses **never make high IVs or a chosen nature easier** to obtain, and never gate or degrade a module's utility: every module works fully with an empty party.
- Bonuses are settled through the shared reward ledger: the party composition and the bonus applied are recorded with the settled event, so settlement stays deterministic and idempotent. Anything locked at the start of an action (for example a focus session's egg luck) stays locked if the party changes mid-action.
- Party bonuses are a Pokémon-side effect. They are never a reason for a module's private content to reach WORLD or the ledger.

### 2.2.2 Module parties and WORLD teams

Each applicable module owns an independent party; there is no single global active party. PLAN, CARE, HEAL, MINT, BASE and LINK each keep their own membership and ordering. GEAR has no party, acquisition or progression (helpers are visual-only). WORLD keeps its own expedition teams, which are not a module party. The launcher may prioritize the global buddy; it owns no party.

A party stores an ordered list of canonical `ownedPokemonId` references, never a copied Pokémon. Capacity is **six: one lead (slot 1) and five members**; a party can hold fewer and never auto-fills. Slot validation rejects unknown, released or otherwise ineligible IDs, but removing a Pokémon from a party never releases or deletes it. A change to one party must not mutate another party or WORLD team, except that the Pokémon being moved leaves its old place.

### 2.3 Global buddy

There is one global buddy. It is a place of its own, not a membership of any party or WORLD expedition team.

- Any currently owned Pokémon may be selected, including a Legendary or Mythical Pokémon if the collection rules permit it.
- **Where it shows:** on Home, in a scene of its own type, and in an empty state asleep. Module pages show their own party and lead, not the buddy, and the PC lists only Pokémon in no other place, so the buddy is never in the PC. **Show all Pokémon:** a switch on the PC lists every owned Pokémon instead, each tile naming its place (Buddy, Plan lead, Care, Heal Guardian, World, Pokégear), with a Where filter (PC, Buddy, Leads, Parties, Guardians, World teams, Pokégear); it only shows them, and a Pokémon still has one place.
- Switching is immediate and does not reset campaign progress or rewrite authored dialogue. The buddy is one of the Pokémon places (section 2.2): choosing a Pokémon that is in a party, a team or a helper role takes it from there, and the previous buddy goes to the PC.
- Story continuity is maintained by using the selected Pokémon as the visual buddy while keeping dialogue semantically coherent.
- The buddy is the main companion. Buddy selection is cosmetic/narrative; it is not a reward multiplier, combat stat bonus, or required participant in a module action. Bonuses come from module parties (section 2.2.1), not from the buddy.
- No separate affection meter, daily maintenance, decay, or needy pet loop.
- Outside WORLD, presentation is contextual and restrained. No unsolicited dialogue bubbles or text.
- Each module's **lead** is slot 1 of that module's party (section 2.2), not a second buddy. CARE-specific temporary interaction participants remain CARE state and must not impose a global buddy lock.

### 2.4 Pokémon availability and references

A module can use an owned Pokémon in its party even if it was acquired elsewhere; a party is a selection roster, not ownership. Placement is exclusive (section 2.2). WORLD expedition assignment is validated against WORLD's rules and capacity. Removing a Pokémon from a place sends it to the PC; it never releases it.

## 3. Canonical species and form catalog

### 3.1 Species identity

Species catalog data is immutable, versioned reference data. It is separate from owned instances.

Canonical catalog entities include:

- `SpeciesDefinition`: stable species identifier, National Dex number where applicable, canonical name, genus/category where available, base species relationship, canonical type(s), base stats, growth/experience group, gender metadata where relevant, egg group metadata only if needed for canonical species data (not a breeding feature), and source/provenance/version.
- `FormDefinition`: stable form identifier, owning species, canonical form name, form kind, type/stat overrides where applicable, visual asset keys, availability rules, and canonical relationship to regional variants.
- `EvolutionDefinition`: source species/form, target species/form, canonical method metadata, adapted method key, level/item/context requirements, and reversible/branching metadata where applicable.
- `MoveDefinition`, `AbilityDefinition`, `TypeDefinition`, `NatureDefinition`, and `ItemDefinition`: canonical identifiers and versioned display/mechanical data.

Use stable machine identifiers rather than display names, localized names, National Dex numbers alone, or enum ordinal positions as database keys. A species and a form are not interchangeable IDs. A form must resolve to one owning species.

### 3.2 Canonical IDs and data provenance

Prefer stable internal IDs such as `species:<canonical-slug>` and `form:<canonical-slug>` or stable catalog IDs already established in source. Keep aliases for legacy IDs and external data-source identifiers. Never derive an owned-instance ID from species ID: duplicates are separate owned entities.

Catalog updates must be additive or migration-aware. Renames, form corrections, changed evolution metadata, or changed source datasets must not orphan existing owned Pokémon. Persist catalog version/provenance where a future correction could affect interpretation.

### 3.3 Forms and regional identity

Regional forms, gender differences, cosmetic forms, battle forms, and special forms must be classified explicitly. Only forms that represent persistent owned identity should be selectable as an owned form. Temporary battle transformations are runtime state, not separate owned Pokémon. Cosmetic/visual variants must not create duplicate species ownership unless the canonical game identity requires it.

A regional Pokédex can list a form independently where canon supports it, but the global collection must not duplicate the underlying owned instance. Form discovery and shiny state are tracked on the owned instance and indexed in the Pokédex.

### 3.4 Catalog coverage and fallbacks

The target catalog covers official Pokémon species and relevant official forms. Asset availability does not define species availability. Missing sprites or portraits must use the shared asset fallback policy in `FEATURES/ASSETS_AND_AUDIO.md`; they must never create substitute species identities or block acquisition.

## 4. Owned Pokémon instances

### 4.1 Owned instance record

An owned Pokémon is a durable instance with a globally unique immutable ID. Its canonical record should contain, conceptually:

- `ownedPokemonId` — stable UUID/ULID or equivalent immutable ID.
- `speciesId` and `formId` — catalog references.
- `nickname` — optional user override.
- `level` — bounded to the supported level range (currently 1–100).
- `experience` — individual accumulated XP or a mathematically equivalent canonical progress representation.
- `natureId`, `abilityId`, `gender`, `shiny`, and persistent form traits where applicable.
- Current derived stats and any persistent battle parameters that the selected model requires; derived values should be recalculable from canonical inputs.
- `moveset` — ordered move references, normally up to four active moves.
- `heldItemId` — optional reference to a shared Bag item instance/stack reservation model; never a copied item definition.
- `friendship` or relationship value only if required by canonical evolution or a specific approved mechanic; no neglect/decay loop.
- `acquiredAt`, `originModule`, `originMethod`, `originEventId`, and optional origin-region/story references.
- `evolvedFrom`/evolution history, hatch history, recruitment history, and relevant acquisition provenance.
- `favorite`, protection flags, and release/transfer status.
- creation/update timestamps and schema version metadata as required by the persistence architecture.

The physical schema may be normalized across tables/entities. The list above defines logical ownership and required semantics, not a mandate for one oversized database row.

### 4.2 Identity and evolution

Evolution normally updates the existing owned instance's species/form reference and evolution history. It does not create a second owned instance, reset XP, change its ID, remove party references, or erase its acquisition origin. Branching evolution requires explicit user choice when more than one valid path exists. Any irreversible evolution must communicate the consequence before confirmation.

### 4.3 Duplicate instances

Duplicates are expected and remain independent owned Pokémon. They may differ in level, nature, ability, shiny status, origin, moves, history, and user preference. Duplicate detection is a UI convenience, never a uniqueness constraint on species/form.

Safe duplicate actions may include compare, rename, favorite, move to PC, or release/convert only through explicit guarded flows. Release/transfer must require confirmation and must protect favorites, shinies, unique forms, active buddy, active party references, active WORLD teams, and any other user-protected instance. No automatic duplicate deletion or conversion.

## 5. Pokédex and regional dexes

### 5.1 National Pokédex

One global National Pokédex tracks catalog knowledge separately from ownership:

- Unknown: no encounter/discovery evidence.
- Seen/discovered: species/form identity has been encountered or documented.
- Owned: at least one active owned instance exists.
- Shiny/form/origin indexes: derived or event-backed collection records.

Exact display labels may be adapted to UI, but seen/discovered and owned must remain distinct. Hatching, recruitment, direct gifts and other origins update the same canonical dex state.

### 5.2 Regional dexes

Each life module has a distinct regional identity and regional dex view. WORLD has campaign/region dexes tied to its PMD/mainline/Ranger-inspired exploration structure. The initial module region IDs are stable machine IDs:

- `plan`
- `care`
- `heal`
- `mint`
- `home`
- `link`
- `gear` (catalog/presentation only; no acquisition)
- `world:<region-id>` for WORLD campaign regions

**Species lists.** The six module regions (`plan`, `care`, `heal`, `mint`, `home`, `link`; in code `PLAN`, `CARE`, `HEAL`, `MINT`, `BASE` (saved as `HOUSEHOLD`), `LINK`) divide the whole National Dex: every species is in exactly one module region and no evolution line is split. Each has about 65 to 75 hatchable lines, at least one of every rarity (two of the 12 Super Rare lines each), its starters first, its lines in regional-dex order, and its patrons (legendary-class and mythical Guardians) as the last entries. The lists are content in `tools/species/regions/`, summarised in `docs/design.md`; GEAR and WORLD have no module region.

Legendary-class and mythical species count towards regional dex completion, so a region is complete only with its quest-earned patrons; the Dex shows them as "earned by quest" so they never read as missing eggs, and economy simulations report both "hatchable done" and "fully complete".

All regions are accessible from day one. Progression can unlock deeper chapters, dungeons, bosses, and special encounters, but not basic access to an entire life module/region.

A Pokémon can count toward multiple regional dexes only when the acquisition/encounter rules explicitly record it. Do not infer regional dex completion merely because a species exists in the global collection. Regional dexes are views over shared discovery/ownership events, not separate collections.

### 5.3 Legacy region migration

Region ids are stable machine ids (BASE is `home` in a region id and `HOUSEHOLD` in a saved record). A legacy `HOME` string is disambiguated by record provenance (PLAN or household), never mapped blindly; WORLD game-region IDs keep their campaign mapping. Unknown IDs are preserved as unresolved legacy metadata until mapped; never silently discard or coerce them.

## 6. Module parties, PC, and collection UI

### 6.1 Shared collection and PC

The collection is the canonical list of all owned instances. PC/storage is a global organizational state or view over the collection, not a second ownership repository. Storage location may be represented as a collection status or container reference, but it cannot duplicate a Pokémon record.

### 6.2 Party data

Each applicable module persists an independent `ModuleParty` (`moduleId`, ordered `ownedPokemonIds`, update/version metadata), with the capacity and uniqueness rules of section 2.2.2 and one place per Pokémon across all parties. Do not auto-fill or auto-replace a party when a Pokémon evolves, changes buddy status, or is added to another module.

**Storage:** a Pokémon's place is at most one row of `pokemon_place` (the Pokémon is its primary key, so two places cannot be stored; no row is the PC), with the lead in party slot 1 (`DATA_AND_MIGRATIONS.md` section 7.3). WORLD teams and GEAR helpers stay in their modules' records, and the live write refuses a save that names a Pokémon there and in the table. A Pokémon obtained from WORLD (a Guardian included) arrives in the PC until the player assigns it.

### 6.3 WORLD teams

WORLD owns named or contextual expedition teams, assignments, active expedition membership and team-size limits, referencing canonical owned IDs (place rules: section 2.2).

### 6.4 Favorites and protection

Favorites and protection flags belong to the owned instance and apply globally. A module may offer a local pin/quick-access marker, but it must be explicitly scoped to that module and not overwrite global favorite state.

### 6.5 Collection UI

The global Pokémon area provides:
- National and regional Pokédex navigation.
- Collection filters and search (species, form, level, origin, shiny, favorite, module party membership, storage state).
- Pokémon detail/profile and progression history.
- Party management for each applicable module.
- WORLD team management through WORLD, not through module-party editing.
- PC/storage and safe duplicate management.
- Shared Bag and Poké balance access.
- Buddy selection independent of parties.

The Pokémon detail reads in this order: identity (sprite, number, name, types, level), the specimen (nature, ability, gender, shiny, IVs), growth (EXP, friendship, stats), evolution, its role (buddy, party, helper, team, favourite) and where it came from.

Keep module-party selection quick and contextual. Do not force the user into the full global collection for every simple party adjustment.

## 7. Buddy presentation and behavior

The buddy is a global presentation preference. It can appear in the launcher, module headers, selected empty states and contextual scenes when useful; do not add it to every screen or repeat a buddy card in every module. It never affects task priority, rewards or productivity metrics.

- PLAN: restrained appearance during focus or a contextual scene. CARE: Care Play/Meadow and approved visitor interactions. BASE and LINK: optional, revealing no household or contact data.
- HEAL: optional; never comments on body or health measurements or implies medical judgment. MINT: decorative only; never exposes amounts or account context.
- GEAR: helper Pokémon may be tool icons or restrained reactions; they are not the global buddy unless separately displayed as such and do not affect utility outputs.
- WORLD: the buddy takes part in authored story and scenes; buddy selection must not break scripted party roles.

Buddy presence is not consent to expose sensitive module information (CARE private content never becomes dialogue; section 13.3). No unsolicited dialogue bubbles or text outside WORLD.

## 8. Progression model

### 8.1 Individual Pokémon XP and levels

Each owned Pokémon has individual XP and level progression. Keep the supported range 1–100 unless a later canonical-data decision explicitly changes it. XP thresholds use one versioned growth/level curve, with species growth groups where supported and product simplification documented.

- XP is awarded by approved WORLD activity and any explicitly approved Pokémon-specific reward event; ordinary life modules do not grant Exploration Rank or expedition-floor XP.
- Every eligible Pokémon participating in a completed WORLD floor receives the full individual XP award; it is not divided among team members.
- Only Pokémon assigned to the eligible expedition/floor receive that floor's XP. A Pokémon added after the floor has settled does not receive retroactive XP.
- WORLD Exploration Rank XP remains a separate progression track owned by WORLD.
- XP settlement must be idempotent and auditable.

Individual XP is `OwnedPokemon.xp` (absent for a legacy level-only save). Do not conflate `WorldData.xp`/Exploration Rank with individual Pokémon XP.

### 8.2 Rare Candy and Super Candy

The everyday level-up candy is shown as **Exp. Candy**, with the Exp. Candy icon of the games. Its item id (`rare-candy`), its rules and every number below are unchanged, and this section and the code still say "Rare Candy" for the item id. Super Candy stays as it is. A true Rare Candy (one full level, an occasional prize) is **not built**; it needs an owner decision on where it is earned and a `RewardRules.VERSION` bump.

Retain and reconcile existing Rare Candy and Super Candy semantics; do not introduce parallel candy types or progression currencies without approval.

- Rare Candy grants direct level progression according to the existing `CandyRules` behavior, including its partner-level catch-up baseline where present. Preserve this rule unless a tested migration/rebalance is explicitly approved.
- Super Candy supports the existing approved evolution/progression function. Its use must be consistent across modules and WORLD facilities.
- Candy quantities are held in the shared Bag.
- Candy use is an atomic inventory-and-Pokémon transaction: consume exactly once and apply exactly once.
- Failed or interrupted use must not consume the item without applying its effect, or apply the effect without consuming it.
- Candy cannot be granted twice by retrying a reward event.

### 8.3 Stats and battle identity

Preserve official species identity, types, base-stat relationships, abilities, moves, natures, and meaningful differences. Use a simplified, deterministic expedition-strength calculation suitable for idle resolution.

- Derived stats are calculated centrally from species/form, level, nature, and any approved modifiers.
- Avoid competitive IV/EV training, complex hidden optimization, and excessive per-Pokémon micromanagement.
- The existing Nature and StatCalculator logic is a starting point; audit and consolidate rather than reimplement blindly.
- Nature remains a stable owned-Pokémon attribute and affects stats only through the selected documented model.
- Abilities and moves must have clear deterministic expedition effects where supported; do not imply a move/ability is active if the simulator ignores it.
- Keep an active moveset to a manageable four moves by default, with clear replacement and learning behavior. *(`Moveset`: `OwnedPokemon.moves` holds the choice as move identifiers and is absent until one is made; absent means the last four level-up moves by level. A Pokémon may be given its level-up moves up to its level, its egg moves and, from level 30, its tutor and machine moves; choosing is free and can be undone with Natural. WORLD's team power is the only reader, see WORLD.md 3.2.)*
- Held items reference the shared Bag/inventory model and use one authoritative effect calculation.
- No competitive PvP or breeding loop.

### 8.4 Friendship and relationship

Do not create a daily affection-maintenance loop. Friendship/relationship may exist only where required for canonical evolution or explicitly authored story behavior. It must not decay from absence, require repeated taps, or be a source of pressure. Any hidden relationship state must have documented gameplay consequences and user-facing explanation where relevant.

### 8.5 Evolution

Evolution rules are centralized. Preserve meaningful canonical requirements but adapt unavailable mechanics for a single-player, offline product. Examples include trade evolution via Link Cord/Link Cable-style items, friendship thresholds without neglect, and contextual evolution choices.

- Show eligible evolution and its requirements.
- Require explicit confirmation for irreversible/branching outcomes.
- Apply item consumption, evolution, party read-model refresh, dex updates, and history in one transaction.
- Preserve owned ID, acquisition origin, XP/level, nickname, favorite/protection, and applicable persistent state.
- Evolution must not create a second Pokémon or reward event.
- Evolve asks first, with Cancel, and the evolution scene can be stopped (as B in the games) with nothing used up. A Pokémon holding an Everstone does not evolve. Special evolutions are checked on the spot (a known move, a held item, the party, the stats, the nature), done with the phone (Malamar upside down, Alcremie's spin with the gyroscope, 1,000 steps on the step counter) or done as an Evolution Trial in WORLD, in the region the evolution belongs to (docs/design.md, "Evolution").
- Forms and regional evolutions resolve through canonical catalog definitions.

## 9. Eggs, hatching, and recruitment

### 9.1 Shared Egg entity

Eggs are first-class shared records. They are not transient module counters or separate local egg inventories.

Logical fields include:
- stable `eggId`
- egg/pool/catalog reference and possible species/form constraints
- `originModule`, `originMethod`, `originEventId`
- created/acquired timestamp
- hatch progress and required progress model, if any
- deterministic outcome seed/commitment or equivalent local-safe mechanism
- eligibility/rarity metadata required for fair resolution
- hatch status and resulting `ownedPokemonId`
- schema/version and audit metadata

An egg belongs to one user collection, has one lifecycle, and can hatch once. It may be displayed in its origin module, but that display references the shared Egg record.

### 9.2 Hatching

Hatching creates one owned Pokémon atomically and updates the relevant Pokédex/origin records. Duplicate species are allowed. A failed/retried hatch cannot create two owned instances or consume the egg twice.

- User can view egg origin and any already-disclosed information.
- Outcome resolution must be deterministic for a committed egg, including offline use and restore.
- No network/server-issued ticket is required for ordinary local hatching.
- **Shiny odds** are fixed at 1 in 128 per egg, with a small bonus for longer focus sessions (`Rules.BASE_ODDS`). There is no Shiny Charm, no step-based eggs and no Oval Charm. A granted legendary gets one ordinary shiny roll when created and is never rerolled; legendaries are not tradable.
- Preserve the existing PLAN immediate-hatch behavior unless PLAN's approved spec explicitly changes it; do not introduce artificial incubation friction there.
- CARE visitor eggs persist in the shared Egg collection and are hatched under user control.
- Other module eggs follow their own approved progression/hatch rules while using the same shared lifecycle.

### 9.3 CARE visitors and recruitment

CARE's acquisition identity is Care Play and visitor befriending. Approved outcomes:
- Successful befriending normally yields a persistent Care Egg representing the visitor's first evolutionary stage.
- A rare direct recruitment outcome may grant a more-developed/higher-level Pokémon.
- Visitor rarity may modestly influence direct-recruit chance but must not make ordinary recruitment feel futile.
- Light inheritance may influence nature, stat potential or a cosmetic trait where already approved; this is not breeding and does not create parent/offspring lineage mechanics.
- Returning/reunion visits do not duplicate recruitment or rewards.
- Visitor progress, eggs and outcomes survive process death, backup/restore and migration.

CARE private journal, Diary Card, mood, urges, therapy/skills responses and other sensitive content never influence visitors, egg outcomes, rarity, or rewards.

### 9.4 WORLD encounters and recruitment

WORLD uses Exploration Finds, deterministic floor encounters, authored story recruitment and guardian/boss outcomes (section 14). A Find is a discovery record, not yet an owned Pokémon; recruitment explicitly converts it into one canonical owned instance. Seen/discovered state can precede owned state. A Find or recruit action cannot silently replace a party member or change the global buddy.

### 9.5 Other module acquisition families

The acquisition identity of each module is in the matrix of section 10.1; each module specification defines its own exact eligibility, pacing, caps and presentation. The shared system owns the Egg/recruitment record, reward settlement, canonical ownership, dex update and anti-duplication guarantees.

## 10. Acquisition contracts and reward generosity

### 10.0 Reward cadence and the loop of each module

1. **A frequent action pays about as often as a Plan focus egg or a Care visitor** (an egg per finished session; about 1.35 visitors a day). Every module with an acquisition (PLAN, CARE, HEAL, MINT, BASE, LINK) has one such everyday action whose reward reaches the Egg meter often enough that an active day finds at least one Pokémon. These actions carry **no daily limit**: farming them is the player's own responsibility, and a cap is added only to guard the privacy contract or a bug, not to slow the player down. MINT's is logging a purchase, a payment or a transfer (a flat reward, never based on an amount: MINT.md 16.2).
2. **A rarer action pays better, not just more**: a reconciled account, a weekly review, a finished goal or a long streak opens an **outbreak** (built for MINT, `MINT.md` 16.2A): a choice among three Pokémon of the module's region, drawn from better rarities than an egg gives, each **caught directly (no egg)**, with extra **shiny rolls** that a streak raises. The other modules adopt it for their own rare actions (backlog REP-069).
3. **Every module has a clear Pokémon influence and a gameplay loop of its own**, as Plan has one: a daycare-style egg that hatches over a cycle, filled by a Forest-style focus timer. What each other module's loop is, and what its Pokémon changes in the module, is designed one module at a time (backlog REP-068 to REP-070); until then a module keeps the loop it has.
4. The privacy contracts of each module (what an event may carry) do not change.

### 10.1 Acquisition identity matrix

| Module | Acquisition identity | Canonical output | Important boundary |
|---|---|---|---|
| PLAN | Focus Eggs; Task Eggs | Shared Egg record, plus approved PLAN rewards | Reward eligible completed focus/task events only; preserve approved duration/rarity/meter rules |
| CARE | Care Play; visitor befriending; Care Eggs | Shared Egg or rare direct recruitment | No sensitive CARE content in eligibility or outcome |
| HEAL | Field Research | Approved egg/discovery/reward event | Participation only; no body/medical outcomes or biometric inputs |
| MINT | Ledger Research | Approved egg/discovery/reward event | Abstract admin activity only; no amounts, balances, merchant or account data |
| BASE | Habitat Discovery | Approved egg/discovery/reward event | Qualifying upkeep only; no household contents/notes/property identifiers |
| LINK | Postcard Trail | Approved egg/discovery/reward event | Distinct follow-up completions; no contact identifiers or message content |
| GEAR | None | No Pokémon, egg, XP, currency or item rewards | Helper Pokémon are visual-only; Ranger-style capture minigame is entertainment only |
| WORLD | Exploration Finds, encounters, expeditions, story/Guardian recruitment | Find record and/or canonical owned instance | WORLD owns adventure progress and Exploration Rank |

### 10.2 Module-specific acquisition rules

#### 10.2.1 A module's first Pokémon

The starter is the buddy and starts in no party, so each module that has a Pokémon acquisition method gives one Pokémon, once, **with the module's first real record**. It hatches into the module's own party and leads it:

| Module | First real record | What it gives |
|---|---|---|
| PLAN | The first finished task | A Common egg of the open region, into Plan's party, leading it |
| CARE | The first routine, calm break, skill, diary card or journal entry | A Common egg of the Care Region, into Care's party, leading it |
| HEAL | The first activity, drink or night logged | A Common egg of the Heal Region, into Heal's party, leading it |
| MINT | The first account or money record | A Common egg of the Mint Region, into Mint's party, leading it |
| BASE | The first chore, supply or item | A Common egg of the Base Region, into its party, leading it |
| LINK | The first person, date or follow-up | A Common egg of the Link Region, into Link's party, leading it |
| WORLD | Its authored opening (`MODULES/WORLD.md`) | Unchanged |
| GEAR | None: GEAR has no acquisition (`MODULES/GEAR.md` section 6) | Nothing |

Rules: nothing is shown before it (the module's empty state is its one add action); it is given once per save (`AppData.tutorials`, `GameStore.firstRecordEgg`), so reloading cannot pay twice; the egg is no focus session, calm break or logged record in itself and adds no history, streak, cap, meter, friendship or trainer points beyond what the record itself pays; the first of its species pays the usual new-species candies; a save that already has records receives it once, the next time the module opens; and it needs no permission. A new module adds its own row here.

**How a new Pokémon arrives.** The egg-hatching scene belongs to Plan's focus and task eggs and to the egg a Care visitor leaves (CARE.md section 6 says an egg). Every other acquisition has a scene in its own module's scenery, and its words, in `Acquisition` (`RewardText.kt`) and `Cutscenes.kt`: **Care** (a routine or break, the tutorial): a new friend wanders in over the meadow and settles ("A new friend wandered over"); **Heal** (Field Research): the Pokémon runs in along the route ("Field Research found"); **Mint** (Ledger Research): a Star Piece glints in the cave and the Pokémon rises from the floor ("Ledger Research catalogued"). **Base** (Habitat Discovery): the Pokémon settles into the habitat from above ("Habitat Discovery found"); **Link** (Postcard Trail): it arrives along the route from the upper right ("Postcard Trail delivered"); World keeps no scene. The origin line, the reward box and Home's Recently list use the same words, and Reduce motion goes straight to the Pokémon. Underneath it is still a shared record in one place, and the Origin keeps the egg kind (`Origin.How`).

The ecosystem preserves the approved module mechanics; it does not redefine them. Exact eligibility, pacing and caps are in each module spec; what follows is the shared contract. What an event may carry is in section 13.2.

- **PLAN (Focus Eggs, Task Eggs):** completed focus sessions and task occurrences are the source events; the session rarity bands, Deep Focus bonus, task meter and the anti-tiny-session rules are `MODULES/PLAN.md`'s. The buddy/session context is locked at focus start so switching later cannot rewrite the event. A BASE-owned chore linked to a PLAN task is paid by BASE for that completion and never also by PLAN.
- **CARE (Care Play, visitor befriending):** arrival, slots, interaction and befriending are `MODULES/CARE.md`'s. Every successful befriending yields the approved egg or rare direct recruitment; the visitor egg lifecycle is shared and persistent; a reunion visit cannot duplicate an earlier outcome.
- **HEAL (Field Research):** only user-completed eligible activities and approved capped participation events qualify. A manual and an imported record of the same activity deduplicate to one event; historical backfill, passive samples and repeated import do not pay.
- **MINT (Ledger Research):** the qualifying actions are those `MODULES/MINT.md` defines. Creating or viewing empty records, or repeating a review of the same data, does not pay.
- **BASE (Habitat Discovery):** completed chore occurrences, meaningful maintenance and approved upkeep milestones qualify. Creating, editing or viewing a chore, changing inventory quantities, purchase/restocking, undo/re-complete and schedule edits do not pay.
- **LINK (Postcard Trail):** distinct eligible follow-up completions under `MODULES/LINK.md` qualify (the balancing target begins around three distinct follow-ups per encounter opportunity). Adding, importing, editing or viewing contacts, notes, dates, call/message frequency, social media activity or responsiveness do not pay. A PLAN-converted follow-up task is one event with a single reward owner.
- **GEAR:** no acquisition (section 2.2, 10.1); a Ranger-style capture minigame is entertainment only and cannot recruit or pay.
- **WORLD:** exploration, Finds and recruitment follow section 14; floor settlements are deterministic and idempotent.

### 10.3 Generosity and balance

Module acquisition rates should be broadly comparable without making modules identical. Balance by expected useful output over 7-, 30-, 60-, and 90-day horizons for light, typical, and heavy engagement patterns.

Measure:
- eggs/discovery/recruitment opportunities
- Pokémon acquired and duplicates
- Rare Candy/Super Candy and other useful items
- Poké currency
- effort/time and action count
- progress retained after interruption or missed days

**Easy to fill, hard to perfect**: completing a Pokédex is easy, with plenty of Pokémon and Rare Candies a day; what stays hard is a good specimen (a shiny, high IVs, the right nature). Loosen pacing and caps that slow collecting, and never make shinies or IVs easier to compensate.

Favor generous progression. Avoid attendance streaks, decay, expiring meters, login rewards, daily hard gates, arbitrary cooldowns and FOMO. Limits may prevent obvious automation/farming but should not punish ordinary repeated use. Balance changes require simulation and documentation, not casual nerfs.

## 11. Shared Bag, items, and Poké economy

### 11.1 One Bag

There is one global Bag/inventory. Every item stack has a canonical item ID, quantity, and any required instance/stack metadata. Modules and WORLD facilities request validated inventory operations; they do not directly mutate local item quantities.

- Item definitions are catalog data; owned quantities are inventory state.
- Held items are persistent equipment (section 11.1.1); consumables are a separate item class with explicit, atomic consumption.
- Item grants, consumption, transfers and held-item changes are atomic and idempotent.
- `WorldData.items` is only a staging list of what World collected, folded into the shared Bag; it is never a second active inventory.
- Module-specific unlocks or rewards may grant shared items but cannot create duplicate inventory systems.

### 11.1.1 Persistent held-item equipment (non-consumable invariant)

Held items are persistent, reusable equipment, not consumables or lifetime single-use items. Equipping, triggering, activating, unequipping, swapping, evolution, expedition settlement, battle resolution, process death or app restart must never consume, destroy, charge, break or lose a held item. A per-encounter trigger limit may reset for a later eligible encounter; it does not make the item single-use. A trigger may update scoped effect-runtime state (for example, once per encounter or a temporary duration), but never item quantity or ownership. Held-item assignment is a separate relationship between one owned Pokémon and one item instance/stack; under the initial model, each Pokémon has at most one held item and an item instance can be assigned to at most one Pokémon. Swapping is an explicit atomic operation. No automatic destruction, durability, theft, random loss or permanent lock-in is permitted.

Items canonically classified as consumables (for example, vitamins or evolution stones) remain consumable and are governed by their own explicit operation. Do not classify a canonical held item as consumable merely because its effect is triggered once per encounter. The expanded catalog, effect registry, acquisition model and acceptance criteria are consolidated in Section 20 of this document.

### 11.1.2 Item acquisition and provenance

Every obtainable item definition declares one or more acquisition sources (taxonomy: section 20.2.1). All grants, purchases, crafting outputs, transfers, consumption and corrections go through the shared inventory/economy transaction coordinator and durable ledger; a module may request a validated grant but never mutates Bag quantities directly. Essential evolution/progression materials need a reliable route and cannot depend only on random drops. No paid item, gacha, expiring stock or real-life-sensitive-data-driven reward is permitted.

### 11.2 One universal currency: Poké

Poké is the sole universal game currency: one shared balance, no PLAN Coins and no parallel WORLD wallet. UI labels use Poké consistently.

**Legacy balances:** an old save's two balances sum 1:1 (`newPoké = legacyCoins + legacyWorldPoké`, an absent one counts as zero, no rounding) into the shared balance once; the migration keeps both source values, the source save version, the conversion time and a migration ID as audit data, and never re-adds them after a restore or retry.

**Storage:** the balance and the Bag (`currency_balance`, `bag_stack`) are written only by `EconomyRepository.settle` together with their `reward_ledger_entry` rows in one transaction; the migration's opening balance records the merge's two source values and pays nothing. `PlayerState.coins` is the balance as the app reads it. `WorldData.poke` is not stored: it is World's in-memory rule input, set from the balance on load and folded back after each action, and an old save's World balance is read once into `legacyPoke` for the merge (`DATA_AND_MIGRATIONS.md` section 7.3.0).

### 11.3 Economy operations

All grants and spends are transaction-backed and linked to a reason/source event. A spend fails without partial mutation if funds are insufficient. A reward retry cannot duplicate Poké. A rollback/recovery must restore both balance and ledger consistency.

Poké sinks may include WORLD facilities, training/expedition capability, convenience, settlement upgrades and optional cosmetics. Essential life-management functionality is never gated by Poké. No Pokémon purchases, paid eggs, loot boxes, paid progression, or time-limited exclusives.

### 11.4 Reward and economy ledger

Maintain auditable entries for grants, spends, refunds/reversals, item changes, candy use, currency migration and other consequential operations. Do not treat a displayed current balance as sufficient audit history.

## 12. Reward ledger and cross-module event contract

### 12.1 Canonical event

Every reward-bearing action is represented by a stable source event before settlement. Logical fields:

- stable `eventId`
- `sourceModule`
- opaque `sourceRecordId`
- `eventKind`
- canonical event timestamp
- eligibility metadata explicitly approved for that event kind
- policy/ruleset version
- settlement status and resulting reward references

A deterministic idempotency key should be based on canonical source identity, e.g. `<module>:<source-record-id>:<event-kind>`. Do not derive identity from timestamps, display names, mutable descriptions, or generated random values.

### 12.2 Ownership and settlement

The source module owns the real-life record and decides whether the source action is eligible. The shared reward service owns reward policy execution, idempotent settlement and canonical reward creation. The Pokémon domain owns the resulting owned instance/egg/progression state. WORLD does not become the owner of a PLAN/CARE/HEAL/MINT/BASE/LINK source record merely because it displays the reward.

One event can settle once. Its settlement can grant multiple approved reward components, but every component must be linked to the same settled event. A source event cannot be submitted independently by two modules for the same real-world action.

### 12.3 Transaction boundary

Within one local database transaction (or equivalent atomic unit):
1. Validate source event identity and module ownership.
2. Check eligibility and applicable caps using a versioned policy.
3. Insert or retrieve the unique reward-ledger event.
4. Create egg/owned Pokémon/item/currency reward records as applicable.
5. Update dex/collection references and balances.
6. Mark settlement complete and persist audit metadata.

If the transaction fails, none of its reward side effects may remain. If the same event is retried, return the existing settlement rather than granting again. If settlement is intentionally asynchronous, use an outbox/inbox pattern with unique IDs and recoverable states; never rely on best-effort UI callbacks.

### 12.4 Durable local identity

Rewards are decided on the phone. A unique event id in the local ledger, with the transaction guarantees of 12.3, gives idempotency. If export/import is used, event IDs remain stable across imports.

`RewardEngine` is a pure deterministic calculator; idempotency lives in the ledger, not in callers. `RewardRules.VERSION` and the golden-vector tests are kept and extended with every rule change.

### 12.5 Undo, redo, and corrections

Undoing a real-life action must not silently delete a settled reward. Each module defines whether an action can be undone before settlement, reversed with an explicit compensating event, or edited without affecting the original reward. Never reuse an idempotency key for a semantically new event. Historical corrections are auditable and do not trigger duplicate rewards.

## 13. Privacy and cross-module boundaries

### 13.1 Minimal event envelope

A module may publish only the minimum event data needed for approved eligibility and deduplication: stable event ID, module ID, opaque source-record reference, event kind, timestamp, and narrowly scoped eligibility flags/counts. Keep reward evaluation local. Do not pass full domain objects or free-text fields into the reward engine.

### 13.2 Explicit exclusions

- CARE: no journal, diary card, mood, urges, skills responses, therapy notes, prompts, or sensitive reflections.
- HEAL: no weight/body composition/measurements, calorie or nutrient totals, biometrics, heart rate, sleep, recovery, diagnoses, symptoms, medications, appointments or medical records.
- MINT: no amounts, account balances, transaction descriptions, merchant/provider/account/card/e-wallet IDs, income, spending, savings, debt, payment or connection status.
- BASE: no room names, inventory contents/quantities, household notes, property identifiers, serial numbers or attachments.
- LINK: no contact names/IDs, phone numbers, addresses, relationship labels, message text, social activity or response frequency.
- PLAN: no task titles/notes, project/goal names, calendar event descriptions, or private focus notes.
- GEAR: no sensor readings or device data as reward input.

Do not infer eligibility from sensitive records merely because the data is available locally. If an event's safe eligibility cannot be evaluated without sensitive details, it is not a valid reward source.

### 13.3 Presentation privacy

Notifications, launcher surfaces, buddy dialogue and WORLD logs must not reveal module content. Reward feedback may say an abstract activity was recognized but must not disclose private records, amounts, measurements, contacts, or notes.

## 14. WORLD integration

WORLD is the adventure/gameplay owner, not the owner of all Pokémon data.

- WORLD owns expeditions, dungeon/floor settlement, campaign/region progress, missions, Exploration Rank, story/Guardian state and expedition team assignments.
- Shared Pokémon owns owned instances, levels, XP, moves, abilities, stats, items held, eggs, recruitment output, and the global collection.
- Shared economy owns Bag, Poké and reward ledger.
- Other modules publish only their approved non-sensitive activity events. Those events do not advance WORLD floor progress or Exploration Rank unless a separate explicit rule is approved; current product rule is that WORLD is the sole ordinary source of Exploration XP.

### 14.1 Expedition XP

The rules are in section 8.1. The floor settlement captures the exact participant owned IDs at settlement and creates one XP ledger effect per eligible participant.

### 14.2 Exploration Finds

A Find is a durable WORLD discovery entity with its own stable ID, species/form reference, region/floor/source metadata, discovery timestamp and recruitment state. It can be inspected in the notebook/Finds in Town or Journal. It does not expire. Recruiting it creates one owned instance with origin provenance and updates the global dex. The Find remains in history as the source record.

### 14.3 Guardians and story recruits

Guardian/Legendary/Mythical recruitment is authored and distinct from ordinary random encounter recruitment. Story state and reward settlement must be idempotent. Do not auto-add the recruited Pokémon to the current expedition team, a module party, or global buddy selection.

**Where a legendary guards:**
- Guardians are legendary and mythical only. **Main legendaries guard their home region**: every box legendary of a region's games is one of its quests (both version legendaries are in the story) and each becomes a Guardian of that region, giving the region's permanent boost (as Primal Dialga does for the home layer). How several boosts in one region combine is open (assumed: they add up inside the cap on Guardian effects).
- **Side legendaries guard in modules** (the lake trio for PLAN, others as each module is designed), not in a region.
- **Primal Dialga is not Dialga**: the home layer's Temporal Tower Guardian is the separate form `dialga-primal` (id 20001, `tools/species/custom_forms.txt`, Dialga's base stats, SpriteCollab's "Primal" sprites); Sinnoh's Dialga quest pays the real Dialga, and Sinnoh's Guardians are Dialga, Palkia and Giratina.
- **Hisui is its own layer** after Paldea (a historical era): it awards the Origin-form items (Adamant Crystal, Lustrous Globe, Griseous Core) and Arceus is its main quest and Guardian.
- Main legendaries: Kanto Mewtwo and the three birds; Johto Ho-Oh and Lugia; Hoenn Groudon, Kyogre, Rayquaza; Sinnoh Dialga, Palkia, Giratina; Unova Reshiram, Zekrom, Kyurem; Kalos Xerneas, Yveltal, Zygarde; Alola Solgaleo, Lunala, Necrozma; Galar Zacian, Zamazenta, Eternatus; Paldea Koraidon, Miraidon, Terapagos.

**Region layers:** each mainline region is its own layer behind the Map switch, open from the first day, beside the Mystery Dungeon layers. A region has eight to nine dungeons, its wild tables follow its regional dex (its Pokémon are found only there), and its extra areas (Sevii Islands, Isle of Armor, Crown Tundra, Kitakami, Blueberry Academy) are late dungeons of it. Special spaces (Hyperspace Lumiose, Ultra Space, Ultra Megalopolis, Battle Zone, Terarium, Area Zero) are Guardian-tier places inside their region, opened by its story. A region is released only after its own `:world-core:simulateWorldEconomy` run. The spin-off layer starts with the Ranger regions (Fiore, Almia, Oblivia) and Orre; other settings stay on the owner's atlas list and are assumed one layer each.

## 15. Local-first persistence, offline, and recovery

### 15.1 Repository contracts

Expose domain repositories and services rather than direct cross-module table access: species catalog, Pokédex, owned Pokémon, module party, WORLD team, buddy preference, egg, evolution, progression, inventory, economy, reward ledger, and acquisition adapters per module. The physical schema and transaction model are defined by `ARCHITECTURE/DATA_AND_MIGRATIONS.md`; this document owns the domain invariants and contract semantics.

### 15.2 Offline behavior

All core collection browsing, party editing, buddy selection, progression, candy use, evolution, egg creation/hatching, Bag, Poké, and reward settlement must work offline. Catalog and asset data required for owned Pokémon display must be bundled/cached. No server-issued ticket is required for ordinary hatching or recruitment.

### 15.3 Backup and restore

Backup/export includes owned instances, party references, WORLD teams, buddy preference, eggs, dex discovery, Bag, Poké balance/ledger, reward event identity, acquisition origins, evolution/hatch history and migration metadata. Restore must preserve IDs and event uniqueness. Importing the same backup twice must not duplicate Pokémon, eggs, rewards, items or currency.

### 15.4 Migrations and recovery

Migrations are versioned, transactional where possible, idempotent and tested against real legacy save shapes. Use staging/recovery markers when multiple legacy stores must be merged. Do not delete legacy sources until canonical data and verification metadata are committed.

Legacy shapes (the shared collection and `inParty`/`partyId` flags, `careLeadId`, the two currency balances, `WorldData.items`, server-ticket eggs, legacy region ids, level-only Pokémon) are read once into the canonical records: parties become independent module party references (never one global party), eggs become persistent local Egg records with deterministic hatch outcomes, reward history gains durable idempotency keys and is never replayed.

If any migration fails, retain recoverable source data and do not mark the migration complete. Migration verification must compare pre/post record counts, balances, inventory quantities, ownership references and reward totals.

## 16. UI, interaction, and shared components

Use the visual language of `FEATURES/DESIGN_SYSTEM.md`. The shared components are: Pokémon sprite/portrait with asset fallback, compact row/card, detail sections, party slot and editor, WORLD team selector (distinct from the party editor), collection filters/search and Pokédex entry, egg tile and hatch reveal, Find/recruitment card, item stack and held-item selector, Bag list, Poké balance history, and the reward box.

Rules: do not duplicate global collection information inside every module; do not show every stat on compact cards; do not use a module party as a hidden global active party; keep GEAR helpers non-interactive beyond their decorative behavior; support TalkBack, scalable text, reduced motion, focus navigation and legible dark mode; major hatching, recruitment and evolution moments may use expressive scenes, routine reward feedback stays brief.

## 17. Behaviours to preserve

Unless a tested, approved rule change says otherwise: the level range 1 to 100 and the candy progression baseline; the approved PLAN Focus and Task Egg and CARE visitor lifecycles; WORLD's deterministic floor settlement and full per-participant floor XP, with Exploration Rank kept apart from individual Pokémon XP; no acquisition or reward loop in GEAR.

## 18. Testing and acceptance criteria

### P0 — ownership and data integrity
- A Pokémon acquired from any module appears exactly once in the shared collection.
- Evolution changes the same owned instance and preserves history/party references.
- Each module party is independent; changing one party never mutates another.
- No Pokémon is in two places (section 2.2): assigning one takes it from its old place.
- WORLD teams are independent of module parties and preserve their own capacity/assignment rules.
- Buddy selection is independent of every party and team.
- No module can create a duplicate local collection or inventory.
- Egg hatching, reward settlement, item use, currency grant/spend and evolution are atomic and idempotent.
- Replaying a reward event cannot duplicate any reward component.
- 1:1 Coins + WORLD Poké migration is applied once and preserves audit values.
- Legacy party, inventory, egg, region, and reward history migration is recoverable and non-destructive.

### P1 — progression and acquisition
- XP/level calculation is deterministic and bounded.
- Each eligible WORLD floor participant receives the full individual XP award once.
- Later team members receive no retroactive floor XP.
- Exploration Rank XP is not Pokémon XP.
- Rare Candy and Super Candy work consistently across modules and WORLD facilities.
- Regional and National dex state distinguish discovered from owned.
- PLAN, CARE, HEAL, MINT, BASE, LINK and WORLD retain their approved acquisition identities.
- GEAR cannot grant Pokémon, XP, items or currency.
- No module can reward the same canonical real-world event twice.
- Acquisition generosity meets the approved simulation horizons without attendance pressure.

### P1 — privacy
- Reward payloads contain only approved minimal event metadata.
- Automated tests assert sensitive fields are absent from module reward envelopes.
- No CARE/HEAL/MINT/BASE/LINK private content appears in WORLD logs, launcher surfaces, notifications or buddy dialogue.
- GEAR sensor/device readings never become Pokémon progression input.

### P2 — usability and content
- Module party editors are independently understandable and fast.
- Collection, PC, Pokédex, egg, Bag, item and buddy surfaces use shared components.
- Missing assets do not block collection or acquisition.
- Dark mode, accessibility, process interruption, offline operation, backup/restore and low-storage behavior are tested.

## 19. Implementation priorities

Order and status live in `IMPLEMENTATION_GUIDE.md` and `IMPLEMENTATION_BACKLOG.md`.

## 20. Expanded Pokémon gameplay systems

The following staged systems extend the canonical ownership, Bag, Poké, reward-ledger, privacy and persistence contracts above. They are approved target scope, not claims of implementation. WORLD owns the player-facing gameplay; shared Pokémon services own canonical data and deterministic effects. The held-item non-consumption invariant in Section 11.1.1 is controlling and takes precedence over any generic source-game description that implies one-time use.

### 20.1 Shared Pokémon systems

#### 20.1.1 Held-item catalog and effect registry

Implement a versioned catalog with canonical ID, display name, category, canonical game references, compatible species/forms where restricted, supported PokéLife effect, acquisition sources, stackability, equipment eligibility, rarity/tier, UI icon/art reference and data version.

Initial effect families:
- Stat specialization: Choice Band, Choice Specs, Choice Scarf, Muscle Band, Wise Glasses, Expert Belt, Assault Vest, Eviolite.
- Damage/recovery: Life Orb, Shell Bell, Leftovers, Black Sludge, Metronome.
- Critical/tempo/control: Scope Lens, King's Rock, Quick Claw, BrightPowder, Rocky Helmet.
- Protection/utility: Focus Sash, Focus Band, Safety Goggles, Covert Cloak, Air Balloon, Smoke Ball, Shed Shell, Eject Button, Red Card, White Herb, Mental Herb, Ability Shield, Utility Umbrella.
- Friendship/experience: Soothe Bell, Lucky Egg, Exp. Share. Their effects must be non-punitive and bounded; no login pressure or item consumption.
- Type boosters: Charcoal, Mystic Water, Miracle Seed, Magnet, Never-Melt Ice, Black Belt, Poison Barb, Soft Sand, Sharp Beak, Twisted Spoon, Silver Powder, Hard Stone, Spell Tag, Dragon Fang, Black Glasses, Metal Coat and Pixie Plate.
- Form/identity items: Plates, Memories, Drives, Griseous/Adamant/Lustrous Orbs, Reveal Glass, DNA Splicers, Prison Bottle, N-Solarizer/N-Lunarizer, Rusted Sword/Shield and other species/form-specific canonical items.

The initial playable set should prioritize approximately 30–40 distinct, testable held-item effects. Additional entries may be catalogued before they receive mechanical effects. Do not implement many redundant percentage bonuses simply to increase item count.

**Effect design:** map canonical effects to simplified WORLD mechanics with explicit formulas and caps. Keep effects legible in the Pokémon detail and equipment UI. Avoid competitive stat arithmetic where it does not improve idle expedition decisions. Choice-style restrictions should be adapted as transparent specialization trade-offs rather than unexpectedly disabling the player's controls.

#### 20.1.2 Abilities and Hidden Abilities

Store canonical ability slots per species/form, standard ability choice, optional Hidden Ability eligibility and current selected ability. Implement an AbilityService that resolves passive and conditional effects in shared simulation rules. Ability Capsule and Ability Patch are distinct catalog items with species/form eligibility; they do not grant impossible abilities.

Initial adapted ability families include Pickup, Honey Gather, Harvest, Compound Eyes, Frisk, Keen Eye, Illuminate, Run Away, Swift Swim, Chlorophyll, Levitate, Sturdy, Synchronize, Cute Charm, Friend Guard and Pressure. Item-producing abilities require deterministic caps and anti-loop safeguards. Ability suppression/protection is only added if WORLD has a real mechanic that uses it.

#### 20.1.3 Natures, mints and training

- Store canonical nature as an individual owned-Pokémon attribute.
- Derive any effective stat modifier through one documented NatureService.
- Mints change the effective nature/stat modifier while preserving the original nature as identity/history, consistent with the mainline distinction.
- Every nature remains viable; no nature makes a Pokémon unusable or invalidates acquisition.
- Vitamins (HP Up, Protein, Iron, Calcium, Zinc, Carbos), Feathers and Power items may provide bounded training/specialization.
- Bottle Cap and Gold Bottle Cap may improve a limited selected stat model (Hyper Training adaptation).
- Do not introduce full competitive EV/IV optimization. Use a small transparent training-point or specialization budget, allow respec/reset through fair means, and cap stacked bonuses.

#### 20.1.4 Moves and mastery

- Keep a manageable four-move active set by default.
- Support canonical learnsets, relearning, move replacement, tutors/TMs where applicable, and species/form-specific eligibility.
- Separate learning a move from mastering it. Mastery improves documented expedition utility, reliability or cooldown behavior; it does not create unbounded stat inflation.
- Preserve signature moves and regional/form-specific move differences where meaningful.
- Egg Moves may be catalogued as species metadata without introducing breeding.
- PP management is not required unless later gameplay demonstrates a clear strategic benefit.

#### 20.1.5 Evolution and form items

Catalog canonical evolution requirements and species-specific item compatibility. Examples include elemental stones; Moon/Sun/Shiny/Dusk/Dawn/Ice Stones; Oval Stone; King's Rock; Metal Coat; Dragon Scale; Deep Sea Tooth/Scale; Prism Scale; Protector; Electirizer; Magmarizer; Reaper Cloth; Razor Claw/Fang; Sachet; Whipped Dream; Upgrade/Dubious Disc; Sweet/Tart Apple; Cracked/Chipped Pot; Galarica Cuff/Wreath; Auspicious/Malicious Armor; Black Augurite; Peat Block; and Link Cable/Linking Cord adaptation.

Evolution items are consumable progression materials where canonically appropriate; this does not make held items consumable. Their use, evolution, dex update and history entry form one atomic transaction. Species-specific requirements cannot be substituted by a generic item. The user confirms branching or irreversible evolution.

#### 20.1.6 Poké Balls and recruitment identity

Catalog Poké Ball variants (Poké, Great, Ultra, Master, Premier, Luxury, Friend, Nest, Net, Dive, Dusk, Quick, Repeat, Timer, Heavy, Love and Dream Balls). Ball choice may influence recruitment presentation, provenance, initial friendship or bounded discovery modifiers where suitable. Do not create separate capture minigames or turn recruitment into paid/gacha mechanics. Master Ball availability is extremely rare, deterministic/earned and never purchasable with real money. Ball identity is stored on the owned Pokémon's acquisition metadata, not as a duplicate ownership record.

#### 20.1.7 Ribbons, Marks and research

- Preserve official canonical Ribbons and Marks as distinct collectible records attached to an owned individual where earned.
- Keep PokéLife achievements separate from official ribbons/marks.
- Provide per-Pokémon history for discovery, recruitment, evolution, forms, shiny state, ribbons, marks and notable milestones.
- Research objectives are optional, varied and non-grindy. Dex completion must not require repetitive actions for every species.

### 20.2 Item acquisition and economy

Every item definition must declare at least one valid acquisition source before it can be surfaced as obtainable. Item acquisition must use the shared Bag and reward ledger. No module may directly increment inventory quantities.

#### 20.2.1 Acquisition source taxonomy

| Source | Examples | Rules |
|---|---|---|
| Expedition loot | Dungeon floor finds, treasure boxes, hidden rooms, environmental nodes | WORLD-owned event; deterministic reward tables with disclosed chance-based entries; no expiry |
| Mission/request rewards | Guild requests, rescue missions, regional milestones | Fixed or clearly described reward; replay cannot duplicate a claim |
| Boss/Guardian rewards | Guardian investigations, raid-like encounters, Rogue Mega challenges | Encounter settlement is idempotent; rare rewards remain obtainable through repeatable or permanent paths |
| Shops and facilities | General store, specialty shop, evolution/item exchange, training facility | Purchases spend shared Poké atomically; prices and stock rules are explicit; no paid currency |
| Crafting/refinement | Apricorns to Balls, berries to meals/remedies, shards/materials to approved items/TMs | Recipe catalog, ingredient costs and output are deterministic; no recipe can consume held equipment |
| Foraging and gathering | Berries, Apricorns, shards, fossils, materials, treasure | WORLD gathering/expedition systems; controlled output; no ability-based infinite farming |
| Research and discovery | Pokédex milestones, field research, fossils, form discoveries | Shared research service issues reward events; no sensitive real-life data as eligibility or quantity input |
| Pokémon abilities/jobs | Pickup, Honey Gather, Harvest, Poké Jobs | Capped offline settlement, clear provenance and no duplicate job assignment |
| Story and authored events | Campaign scenes, NPC gifts, regional events, postgame investigations | Replay-safe; no permanently missable essential item |
| Module-origin rewards | Existing approved PLAN/CARE/HEAL/MINT/BASE/LINK acquisition/reward contracts | Only explicitly allowed item reward types; source modules publish minimal eligible events; no private content crosses boundaries |
| Exchange/collection milestones | Fossil restoration, contest awards, ribbons, Pokéathlon | Rewards are earned, not random paid draws; duplicates have a documented use or are prevented |

#### 20.2.2 Item category acquisition matrix

| Item category | Primary acquisition paths | Notes |
|---|---|---|
| Held items | Shops, boss/Guardian rewards, rare treasure, crafting/refinement, research milestones, permanent investigations | Persistent equipment; never consumed, charged, broken or lost on trigger |
| Evolution stones and evolution materials | Shops, dungeon nodes, mission rewards, research, crafting, species-specific investigations | Consumable on confirmed evolution only where canonical; preserve eligibility and transaction atomicity |
| Vitamins, Feathers and training items | Shops, jobs, contests, Pokéathlon, research, expedition rewards | Bounded training; no competitive EV grind |
| TMs and move-learning items | Shops, dungeons, contests, research, crafting, story rewards | Eligibility validated by species/form/learnset |
| Poké Balls | Shops, Apricorn crafting, missions, regional research | Stored as recruitment supplies; ball identity retained as provenance; no paid capture chance |
| Berries and cooking ingredients | Berry Garden, foraging, expeditions, jobs, story rewards | Oran, Sitrus, Pecha, Cheri, Chesto, Rawst, Aspear, Leppa, Lum and other canonical varieties |
| Shards, Plates and form-change items | Regional dungeons, legendary investigations, research, boss rewards, crafting | Species/form compatibility and canonical rules enforced |
| Fossils and restoration materials | Fossil nodes, excavation missions, regional research | Restore at a WORLD facility; recruitment is a normal shared acquisition event |
| Treasure and sellable collectibles | Expeditions, contests, jobs, exploration finds | Selling is optional and ledgered; do not create parallel currency |
| Gimmick equipment | Campaign milestones, regional research, permanent challenge rewards, facilities | Unlocks access to a system; does not grant unlimited stacked power |

#### 20.2.3 Shops, stock and affordability

- Shops are WORLD facilities backed by shared Bag and Poké repositories.
- Purchase is a single transaction: validate price/stock/capacity, debit Poké, grant item, append ledger entries; rollback all if any step fails.
- No expiring offers, countdowns or login-timed deals. The Kecleon Shop has a fixed stock that is always sold (every evolution stone, the Link Cord, the Everstone, Gimmighoul Coins) and a daily shelf of five more items chosen for the player's local day; nothing is sold only on the shelf and every catalogue item comes round (`KecleonShop`, docs/economy.md).
- Two shops: the **Poké Mart**, the general store, its own section of the Pokémon tab (not in the Bag, not in World), selling Exp. Candy, Super Candy and berries; and the **Kecleon Shop**, the specialty shop in WORLD's Town, which sells no candy. Anything that grows one Pokémon (candies, stones, vitamins, mints, caps, held items) is priced generously, since a player keeps many Pokémon; permanent upgrades (WORLD's facilities, the Dojo) keep their prices (`Shop`, `KecleonShop`, docs/economy.md).
- Essential evolution and progression materials must have a reliable path. Chance drops may supplement but cannot be the only route for an essential requirement.
- Selling items is optional and never required to recover from ordinary play. Held items may be sold only after explicit confirmation and must be unequipped first; default UI should favor storage/equipment rather than liquidation.

#### 20.2.4 Crafting and refinement

Crafting is an optional WORLD utility, not a mandatory survival loop. Recipes are data-driven, versioned and auditable. Crafting consumes only declared ingredient categories. A held item assigned to a Pokémon cannot be consumed as an ingredient. No recipe may silently transform or destroy held equipment. Recipe outputs enter the shared Bag through the reward/inventory transaction coordinator.

### 20.3 Mainline gameplay systems in WORLD

#### 20.3.1 Battle transformations and gimmicks

Implement a shared TransformationService with species/form eligibility and separate transformation state. Systems:
- **Mega Evolution:** Key Stone and species-specific Mega Stone; temporary Mega form, stats/types/ability where supported.
- **Z-Moves:** Z-Ring and compatible Z-Crystal; one special action per encounter/expedition scope as tuned, with type/species-specific compatibility.
- **Dynamax/Gigantamax:** Dynamax Band and a bounded Dynamax state; Gigantamax only for eligible species/forms with distinct appearance/move data.
- **Terastallization:** Tera Orb and Tera Type; temporary defensive type and STAB adaptation, including Stellar where supported.
- **Rogue Mega and special boss variants:** encounter/boss modifiers using the same transformation framework, not a second battle engine.

Use one major transformation choice per encounter unless a future approved rule explicitly says otherwise. Transformation state is separate from ordinary owned-Pokémon form/identity, does not create a duplicate owned Pokémon, and does not permanently inflate base stats. Equipment/unlock and charges/cooldowns, if any, govern access; the equipment itself is never consumed by activation. Charge state belongs to the transformation capability/runtime, not to held items. Regional mastery unlocks access/content but never blocks entry to a region.

#### 20.3.2 Poké Jobs and offline assignments

Pokémon may take compatible assignments for research, berry gathering, material collection, training, habitat maintenance, scouting and deliveries. Assignments settle offline with deterministic elapsed-time caps and explicit start/end state. A Pokémon cannot be assigned to incompatible simultaneous jobs or an active expedition. Jobs never expire into penalties; the user can collect later. Rewards enter the shared Bag/ledger.

#### 20.3.3 Contests and Pokéathlon

Optional WORLD activities:
- Contests: Cool, Beauty, Cute, Clever and Tough categories; moves, accessories, presentation and ribbons/cosmetics.
- Pokéathlon: Speed, Power, Skill, Stamina and Jump; simplified species-aware competitions and cosmetic/research rewards.

Do not make contest rewards depend on the user's body, health metrics or personal fitness. These are Pokémon activities, not real-life exercise scoring.

#### 20.3.4 Berry Garden, cooking, camps and picnics

- Berry Garden: planting, growth stages, harvest, optional crossbreeding only if simple, storage and recipe supply.
- Cooking: Curry-, sandwich- and Poké Puff-inspired recipes with ingredients and temporary team effects.
- Camp/Picnic/Amie/Refresh interactions: rest, play, food, toys, species animations, group interactions, decoration and photo mode.
- Use one shared interaction history and one non-decaying friendship/relationship model. CARE, WORLD and launcher may present context-specific interactions but must not create separate affection meters.
- No daily watering/feeding obligation, plant death, neglect penalty or mandatory streak.

#### 20.3.5 Fossils, outbreaks, raids and research encounters

- Fossil restoration: discover fossils/materials, restore at a facility, then recruit eligible fossil Pokémon through shared ownership.
- Outbreaks/Massive Mass Outbreaks: deterministic, persistent discovery opportunities with regional species focus, research and shiny/form opportunities. No real-time expiry or FOMO.
- Raid/Max Raid/Tera Raid/Rogue Mega encounters: use shared Guardian/boss encounter architecture with presentation and modifier profiles, not parallel ownership/reward systems.
- Alpha Pokémon and special variants are encounter metadata/individual variants with clear stat/size/presentation rules; do not duplicate species identity.
- Safari Zone/Poké Radar/Legends research/GO-style buddy exploration/Poké Pelago-inspired discovery may be optional encounter profiles or facility activities, not separate capture or progression engines.

#### 20.3.6 Secret Bases, collections and photography

Optional post-core systems include customizable Pokémon settlement/Secret Base spaces, furniture and habitat themes, visitor scenes, trophies, photo missions, Poké Ball seals/patterns, markings, nicknames, size metadata, collection cards and user-defined collection views. Views reference existing owned IDs and never duplicate ownership.

### 20.4 Pokémon Ranger integration

Ranger mechanics are adapted as expedition support, not real-time stylus simulation:
- Capture Styler / GEAR identity and Ranger Signs where supported by the approved GEAR specification.
- Field Moves (Cut, Tackle, Burn, Water, Electrify, Psychic, Dig, Rock Smash) for optional environmental interactions, shortcuts and bonus discoveries.
- Poké Assists as temporary expedition support profiles.
- Partner Pokémon and Partner Gauge as a WORLD party/encounter presentation mechanic, not another friendship meter.
- Ranger missions, bases, habitats, boss encounters and Guardian Signs as regional content.

Field Move paths must never become mandatory progression locks that strand the player without a compatible Pokémon. Every essential route has an alternate route or an accessible support option.

### 20.5 Module ownership and presentation

| Owner | Approved role |
|---|---|
| Shared Pokémon domain | Species/form data, owned instance, abilities, nature, moves, evolution, ribbons/marks, research, item definitions, Bag/equipment assignment, transformation eligibility and shared interaction identity |
| WORLD | Held-item loadout UI, item shops, crafting, training, jobs, contests, Pokéathlon, Berry Garden, camps, raids/bosses, fossils, outbreaks, Ranger-style field interactions and regional content |
| PLAN, CARE, HEAL, MINT, BASE, LINK | Optional, restrained presentation of buddy, companion or field-research scenes only; no separate friendship or held-item inventory, and no private content (section 13.2) affects item drops, Poké rewards, Pokémon stats or rewards |
| GEAR | Ranger-inspired utility/field support where approved; no separate Bag or Pokémon ownership |
| Launcher | Ambient display and quick entry only; no independent progression/equipment state |

### 20.6 Shared services and logical contracts

Recommended domain services (an implementation may combine them where boundaries stay explicit): item catalog, inventory (Bag grants, spends, provenance, ledger), held item (equip, unequip, swap, compatibility; no consume API), item effect registry (deterministic effects, scope, caps, runtime trigger state), ability, nature (original nature, mint-adjusted modifier, derived stats), training, move (learnsets, relearning, tutors/TMs, mastery), evolution, ball, transformation (Mega/Z/Dynamax/Gigantamax/Tera), research, ribbon/mark, and, as each system is delivered, crafting, job, contest, interaction, encounter and form services.

All mutation APIs validate ownership and compatibility and participate in the transaction coordinator. Services expose domain contracts, not Compose or Android framework types.

### 20.7 Logical data requirements

This is a logical model; physical normalization is decided by Data and Migrations.

| Record | Required information |
|---|---|
| `item_definition` | Stable item ID, category, display metadata, canonical references, stack/equipment/consumable classification, compatibility, acquisition source declarations, definition version |
| `inventory_stack` / `item_instance` | Stable ID, item ID, quantity or unique-instance identity, acquisition provenance, created/updated metadata |
| `held_item_assignment` | Owned Pokémon ID, item instance/stack reference, slot, assigned timestamp, uniqueness constraints |
| `item_ledger_entry` | Grant/spend/transfer/equip/unequip/craft/purchase/reversal, quantity, source event, idempotency key, before/after references |
| `item_effect_runtime` | Item assignment ID, scope (floor/encounter/expedition), trigger state/cooldown/duration, reset rule; never changes item quantity |
| `pokemon_ability_state` | Owned Pokémon ID, selected ability, source slot, eligibility and change history |
| `pokemon_nature_state` | Original nature, effective mint override, history |
| `pokemon_move_state` | Learned moves, active slots, mastery and acquisition source |
| `pokemon_ribbon_mark` | Owned Pokémon ID, canonical ID, earned event and date/provenance |
| `pokemon_research_state` | Species/form/owned individual research milestones and source event |
| `pokemon_transformation_state` | Eligible owned Pokémon, transformation type, encounter scope, temporary form and resolution state |
| `item_recipe` / `crafting_event` | Recipe version, ingredient costs, output, source event and atomic settlement |
| `pokemon_job` | Assigned owned IDs, job type, start/end, settlement state and output event |
| `pokemon_activity_record` | Contest/Pokéathlon/camp/fossil/encounter participation and results |

Do not create a unique table for every catalog noun if a typed definition/metadata model suffices. Avoid polymorphic unvalidated references. Enforce foreign keys, positive quantities, single assignment and unique idempotency keys.

### 20.8 Migration and compatibility rules

- Existing Bag items must map to canonical item IDs. Unknown legacy items are retained as recoverable unmapped records until reviewed; never silently discard them.
- Existing held-item fields are reconciled into persistent assignment records and shared inventory ownership. Detect duplicates, missing quantities, incompatible assignments and duplicate equipment references before cutover.
- If legacy saves represent a held item only as an equipped string and do not track Bag quantity, create exactly one recovered item instance per valid assignment and record migration provenance. Do not fabricate additional copies.
- Existing item quantities and ownership are not multiplied when importing duplicate legacy views of the same inventory.
- New catalog entries do not imply they existed in legacy saves.
- New runtime trigger state is reset or migrated only when its scope and meaning are known; do not convert a previously triggered effect into item loss.
- Migration must be deterministic, idempotent, backed up and auditable. Preserve original nature separately if mints are introduced; preserve owned Pokémon ID and history.

### 20.9 Priority and delivery tiers

### Tier 0 — Shared foundation
1. Item catalog taxonomy and acquisition-source metadata.
2. Shared Bag item provenance and item ledger.
3. Persistent held-item assignments and effect registry.
4. Held-item compatibility/effect tests and migration reconciliation.
5. Canonical evolution-item catalog and species-specific rules.

### Tier 1 — Pokémon identity and build depth
1. Ability slots and Hidden Abilities.
2. Natures and Mints.
3. Move relearning, tutors/TMs and mastery.
4. Bounded training items and specialization.
5. Poké Ball identity, Ribbons, Marks and research depth.

### Tier 2 — WORLD gameplay expansion
1. Mega Evolution and Terastallization.
2. Dynamax/Gigantamax and Z-Moves.
3. Jobs, shops, item sourcing and crafting.
4. Berry Garden, cooking, camps and picnics.
5. Contests, Pokéathlon, fossils, raid/boss profiles and outbreaks.
6. Ranger field interactions and Poké Assists.

### Tier 3 — Optional world-building
Secret Bases, photography, advanced habitat customization, Safari Zone and Radar-style encounter profiles, special collection cards and extended regional events.

Do not start Tier 2/3 before Tier 0 inventory, transaction, persistence and migration gates pass. Implement each system as a vertical slice with content definitions, UI, rewards, automated tests and device validation.

### 20.10 Acceptance criteria

- Held items stay owned and equipped after every trigger, expedition, battle, process death and restart (section 11.1.1); no held-item API or UI action consumes, destroys, charges, breaks or loses equipment.
- A held item can be equipped to at most one Pokémon and one Pokémon has at most one held item under the initial slot model.
- Swapping/equipping is atomic, validated, reversible and correctly reflected in Bag quantity/assignment views.
- Effect triggers are scoped and reset without altering item ownership or quantity.
- Every obtainable item has a documented source and all grants are ledgered/idempotent.
- Essential progression items have reliable acquisition routes; chance-based rewards are disclosed and non-exclusive.
- Shop purchases and crafting are atomic and cannot duplicate or partially settle.
- Evolution requirements are species-specific and item/evolution/dex/history updates are atomic.
- Ability, nature, move, training, transformation and research effects are centrally evaluated and deterministic.
- No real-life sensitive module content affects Pokémon rewards, item drops, combat or progression.
- No module creates a second Bag, currency, item owner, friendship meter or Pokémon collection.
- Migration preserves existing quantities and assignments without duplication or loss and supports backup/rollback.
- Tests cover offline operation, interruption, process death, retries, corruption/recovery and restore.

## 21. Source-of-truth and cross-reference map

| Document | Responsibility |
|---|---|
| `PRODUCT_BIBLE.md` | Product-level Pokémon promise, shared areas, acquisition principles and universal economy |
| `IMPLEMENTATION_GUIDE.md` | Implementation phases, audit status, dependency order and exit gates |
| **This document** | Shared Pokémon domain, owned records, parties, buddy, progression, eggs, Bag, Poké, rewards, acquisition contracts and integration invariants |
| `ARCHITECTURE/DATA_AND_MIGRATIONS.md` | Physical schema, repositories, transaction strategy, migration execution, backup/restore and recovery |
| `ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` | Platform/provider integrations, permissions and privacy implementation |
| `ARCHITECTURE/TEST_STRATEGY.md` | Test pyramid and acceptance strategy for these contracts |
| `FEATURES/DESIGN_SYSTEM.md` | Shared PMD dialogue-box visual grammar and reusable components |
| `FEATURES/ASSETS_AND_AUDIO.md` | Pokémon species/form asset coverage, provenance, sprite/portrait/audio pipeline and fallback rules |
| `ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` | Shared reminder policy and delivery; reminders do not own Pokémon progression |
| `MODULES/PLAN.md` | PLAN Focus Egg and Task Egg triggers/eligibility |
| `MODULES/CARE.md` | Care Play, visitor befriending, Care Eggs and CARE privacy boundary |
| `MODULES/HEAL.md` | Field Research triggers and health-data exclusions |
| `MODULES/MINT.md` | Ledger Research triggers and financial-data exclusions |
| `MODULES/BASE.md` | Habitat Discovery triggers and household-data exclusions |
| `MODULES/LINK.md` | Postcard Trail triggers and contact-data exclusions |
| `MODULES/GEAR.md` | Helper Pokémon visual-only rule; no acquisition or rewards |
| `MODULES/WORLD.md` | WORLD expeditions, Finds, recruitment, story, Exploration Rank and team assignment |

When a module specification and this document appear to conflict, the module specification owns the exact module trigger and user workflow; this document owns shared record shape, reward settlement, privacy envelope and cross-module invariants. Product-wide changes to shared ownership, economy, parties, or progression require updating this document and the Product Bible/Implementation Guide together.

---

## Testing reference

Canonical ownership, identity preservation, inventory reconciliation, reward-ledger idempotency, duplicate prevention and progression invariants are critical release gates. Implement and maintain the required automated coverage in [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md).

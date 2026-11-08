# PokéLife — WORLD Module Specification

**Version:** 1.0  
**Status:** Owner-approved target specification  
**Decision status:** Approved; individual expedition participants each receive the full per-Pokémon XP award (not a divided pool)  
**Baseline reviewed:** `pokeplan-reboot-main`, source archive version `0.123.0`  
**Last consolidated:** 5 November 2026

> This document defines WORLD's module-specific requirements and contracts. It is not a claim that every described feature is implemented. The Product Bible remains the product-wide authority; the Implementation Guide records baseline status and delivery work. Shared Pokémon ownership, progression, battle identity, Bag, Poké and reward-ledger contracts are defined in `../FEATURES/POKEMON_ECOSYSTEM.md`. Shared visual rules are owned by `../FEATURES/DESIGN_SYSTEM.md`; shared reminder scheduling and delivery are owned by `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.

## 1. Purpose and boundaries

WORLD is PokéLife's Pokémon Mystery Dungeon-inspired exploration game. It is the one module whose purpose is the Pokémon adventure itself: players form teams from their existing collection, send them into dungeons, complete missions, discover and recruit Pokémon, develop exploration capabilities, and experience authored regional campaigns.

**Design principle:** do not make an idle game where players collect Pokémon; make a Pokémon exploration game that happens to have idle mechanics.

WORLD must be enjoyable as a game without requiring real-life module activity. Its idle mechanics make exploration continue while the user lives their life; they do not create a daily obligation.

### 1.1 WORLD owns

- Expedition team definitions, presets, assignments and active expedition state.
- Dungeon, route, place and regional exploration progress.
- Expedition settlement calculations and WORLD-specific event outcomes.
- WORLD mission/request progress and claim eligibility.
- Regional campaign, chapter, scene, branch and playback progress.
- Exploration research notebook and regional discovery records.
- WORLD skill and facility progression.
- Exploration Finds awaiting recruitment.
- Expedition journal and WORLD-specific historical records.
- WORLD-originated reward eligibility events and pending delivery references.

### 1.2 WORLD does not own

- The canonical Pokédex or owned Pokémon instances.
- Pokémon levels, individual XP balances, evolution, moves, abilities, forms or stats.
- Global Exploration XP/Rank authoritative balance.
- The shared Bag, item quantities, Poké currency or reward ledger.
- Guardian Pokémon ownership or the shared Guardian collection/perk configuration.
- Real-life tasks, health activity, financial records, household records, relationship records, journals or personal reflections.
- Android launcher persistence, global notifications, shared reminders or backup/restore infrastructure.
- A separate travel/journey system for real-life trips. WORLD is distinct from any future Explore/travel feature; no separate Explore module is part of the approved eight-module product.

### 1.3 Product rules

- All regions and maps are accessible from the beginning, with suitable entry-level content.
- Individual dungeons, chapters, facilities, bosses and special encounters may require relevant rank or regional progress.
- Regional campaigns are self-contained and can be played in any order.
- No humans, trainers or human institutions appear in WORLD.
- No punitive streaks, stamina lockouts, expiring essential rewards, permanent Pokémon loss, missable seasonal content or forced login cadence.
- No mandatory account, network connection, cloud save, multiplayer service or external game API.
- The game remains optional; no real-life module feature is gated behind WORLD progression.

## 2. Primary navigation and information architecture

WORLD has five primary destinations. Secondary functions remain contextual rather than becoming additional top-level destinations.

| Destination | Purpose | Primary contents |
|---|---|---|
| **Base** | Exploration headquarters and community | Rank summary, facilities, active team summary, Finds, Guild attention and story continuation |
| **Atlas** | Geographic exploration | Regions, maps, settlements, routes, landmarks, dungeons and Guardian locations |
| **Expeditions** | Main gameplay loop | Team roster, team presets, dungeon selection, active expeditions, recall and expedition reports |
| **Guild** | Requests and objectives | Rescue, retrieval, delivery, escort, investigation, challenge and story requests |
| **Journal** | Discoveries and history | Research notebook, expedition history, dungeon records, recruitment history, story archive and Guardian records |

Facilities are reached through Base. Research and story history live in Journal. Team setup is available from Expeditions and may be opened contextually from a dungeon. Bag access opens the shared Bag; it is not a WORLD-owned inventory screen.

### 2.1 Base — exploration headquarters

Base is the player's central home within WORLD. It should feel like a functioning Pokémon settlement rather than a dense management dashboard.

Landing hierarchy:
1. Exploration Rank and next meaningful milestone.
2. Compact PMD-style headquarters scene.
3. One context-sensitive primary action.
4. Active expedition summary.
5. Finds awaiting recruitment.
6. Guild requests requiring attention.
7. Current regional story progress.
8. Facility shortcuts.

**Implemented (0.135.0):** the Base page now opens on the headquarters picture, as big as the width allows (the team base of the first partner from the Rescue Team games, plain, under construction or finished as the Camp grows), then the rank strip and a menu of the places in it (Facilities, Research, Café, Kecleon Shop, Swap Shop, Dojo). Each opens its own page, with a banner from the Square, Pokémon Square or the Dojo; before, every facility, research step, the Café and every shop stood in one long list. The primary action and the story, Finds and Guild lines of the landing hierarchy live on the WORLD menu above it, not on this page.

The primary action is state-dependent: begin the introductory adventure for a new save; prepare a team when none is active; review an expedition when teams are away; recruit a Pokémon when Finds are waiting; review a completed request when a claim is ready; or continue a chapter when a story scene is available. Do not show every available statistic and shortcut at once.

### 2.2 Atlas — regions, places and dungeons

Atlas represents the geography of WORLD. It includes the custom Pokémon-only home setting/Frontier, PMD campaigns, mainline Pokémon regions, and later Pokémon Ranger regions and selected spin-off settings.

Distinguish geographic entities:
- **Region:** a campaign/map grouping with its own identity and progress.
- **Settlement:** a community with characters, facilities, shops or story scenes.
- **Route:** a geographic connection or explorable area.
- **Landmark:** a notable location, ruin, structure or point of interest.
- **Dungeon:** an expedition destination with floor-based progression and encounter/reward rules.

A place may contain services, characters, story scenes or dungeon entrances. A dungeon is not synonymous with a map marker.

Map states should distinguish unknown, discovered, available, currently exploring, cleared, story-relevant and Guardian locations. States must not rely on color alone. Every map interaction must have an accessible list-based alternative. All regions remain selectable from day one; a map marker must not imply that the entire region is locked merely because a specific dungeon requires higher rank.

### 2.3 Expeditions — core gameplay

An expedition is a persistent, timestamp-based journey undertaken by a team of up to four owned Pokémon.

Workflow:
1. Select a region and dungeon.
2. Review dungeon floors, rank requirement, difficulty, favored types, possible discoveries and reward categories.
3. Select an available team or apply a saved preset.
4. Review team suitability and a concise explanation of estimated floor time.
5. Dispatch the team.
6. Allow the expedition to progress while the app is open or closed.
7. Settle progress and review significant events/rewards.
8. Continue or recall the team.

Team rules:
- A team contains up to four stable owned-Pokémon IDs.
- A Pokémon may be assigned to only one active expedition team at a time.
- Assignment is explicit; do not silently remove a Pokémon from another team.
- Recalled Pokémon become available for reassignment.
- Rank determines the number of expedition team slots; preserve current baseline slots unless simulation justifies a change.
- Preserve the current six saved team presets, team names and accent identity unless usability testing identifies a material problem.
- Presets must validate ownership and availability when applied; missing, released or currently assigned members are skipped with clear feedback.
- Provide a strongest-team recommendation as an optional shortcut, never as an automatic replacement for the user's choice.
- Team suitability should explain relevant type coverage, moves, abilities and dungeon modifiers without demanding competitive optimization.

Expedition status must show team identity/portraits, destination, current floor, floor count, progress since last settlement, significant discoveries and recall action. Do not simulate literal real-time combat or movement when the result is timestamp-calculated. A visual expedition feed may animate the latest state but must remain honest about what is simulated.

### 2.4 Guild — missions and requests

Guild owns WORLD request definitions, acceptance, progress and completion state. Initial categories:
- Exploration: reach a specified dungeon/floor or discover a place.
- Retrieval: obtain specified shared items through eligible expedition events.
- Delivery: deliver an item or complete an expedition to a destination.
- Rescue: find and assist a Pokémon.
- Escort: complete an expedition with a specified team size or condition.
- Challenge: meet a dungeon, team composition or strength condition.
- Investigation: reveal a discovery, event or research milestone.
- Story request: advance an authored regional chapter.

Each request shows requester, concise narrative, objective, requirements, destination, reward preview, progress and status. Distinguish one-time story/rescue requests from repeatable requests. Retain the existing three-active-mission limit as the initial behavior; review only if real usability evidence shows it creates friction. Repeatable mission availability and claim windows must be explicit. No mission should expire in a way that destroys essential progression or rewards.

### 2.5 Journal — research and history

Journal consolidates WORLD's records without becoming another task list. Sections:
- **Expedition history:** significant completed expeditions and major events.
- **Regional research:** discovered species and regional research milestones.
- **Recruitment history:** Pokémon that joined the shared collection.
- **Dungeon records:** deepest floor, clears and permanent milestones.
- **Story archive:** completed chapters, episodes and replayable scenes.
- **Guardian records:** legendary encounters and outcomes.
- **Mission history:** completed requests and their rewards.

Research records discoveries; it does not own the Pokédex. Recruited species count as caught through the shared Pokémon service. A species may be discovered in WORLD before it is recruited. Research milestones must be claimable without revisiting an old screen and must not pay twice.

## 3. Core gameplay systems

### 3.1 Expedition settlement and offline progress

Expeditions progress through deterministic timestamp-based settlement. The app calculates the completed floors when the user opens/resumes WORLD or when a relevant state transition requires settlement; no always-on process or continuous UI polling is required.

Preserve the current 12-hour away-time cap as the baseline: up to 12 hours of elapsed away-time is credited per settlement. The cap is a progression-rate boundary, not a loss mechanic. If elapsed time exceeds the cap, excess time is not credited, but previously earned progress and rewards remain permanent. Revisit the cap only with explicit simulation evidence and product approval.

Requirements:
- Deterministic floor outcomes: settling frequently or after a long absence must produce the same outcomes for the same expedition seed/state.
- Stable expedition/team/floor/reward identifiers.
- Preserve fractional elapsed time between settlements where the cap is not exceeded.
- Clock rollback must not freeze a team until the clock catches up; reconcile the settlement timestamp safely.
- Time-zone changes must not create or erase elapsed progress; use absolute instants for elapsed-time calculations.
- Handle process death, reboot, app update, interrupted settlement, multiple active teams and repeated settlement idempotently.
- No reward or floor may be paid twice after retry, migration or restore.
- The expedition must not require the app to remain open.

### 3.2 Team strength and simplified battle identity

The final simulation belongs to the shared Pokémon battle/expedition model in `../FEATURES/POKEMON_ECOSYSTEM.md`. It must preserve meaningful canonical identity without turning idle expeditions into a competitive battle simulator.

Inputs include:
- Individual Pokémon level and relevant canonical stats.
- Type effectiveness and dungeon type modifiers.
- Moves and their role, type, power/utility and target/range abstraction.
- Abilities and relevant passive/conditional effects.
- Team size, coverage and synergy.
- Dungeon difficulty and authored modifiers.

The current baseline uses the sum of member levels, adding 20% for each member with a type favored by the dungeon. Floor time is calculated from dungeon base time divided by `1 + power / 100`, with a floor-time minimum of 25% of base; Guardian mastery and relevant legendary bonuses may further reduce time. This is a verified starting implementation, not the final battle model. Preserve its understandable explanatory output while evolving the formula through balance simulations. Skills that improve reward discovery must not silently change floor time unless explicitly documented.

The final model must provide a human-readable suitability explanation and must be tested across team sizes, level bands, type matchups, abilities, moves, dungeon modifiers and rank tiers. No single species, move or type should dominate every dungeon. Broad experimentation must remain viable.

### 3.3 Exploration Rank and skills

Exploration XP and Exploration Rank are authoritative shared progression records, not WORLD-owned balances. WORLD is the sole source of ordinary Exploration XP events: completed WORLD expedition floors and approved WORLD milestones. Real-life tasks, habits, routines, health records, finance activity, household actions, contacts and GEAR utility use do not advance expedition floors or Exploration Rank.

Preserve the current six-rank structure as the baseline:

| Rank | Baseline Exploration XP threshold | Expedition teams |
|---|---:|---:|
| Normal | 0 | 2 |
| Bronze | 300 | 2 |
| Silver | 1,500 | 3 |
| Gold | 6,000 | 3 |
| Platinum | 20,000 | 4 |
| Diamond | 60,000 | 4 |

Thresholds and team slots may be tuned only after progression simulation; do not casually nerf generosity. Rank unlocks deeper dungeons, additional team capacity and relevant facilities/content. It never locks an entire region.

WORLD owns three module skills:
- **Exploration:** improves Poké reward yield.
- **Foraging:** improves berry discovery.
- **Scouting:** improves Pokémon discovery.

Preserve the current level-10 skill ceiling as a baseline. Pokémon abilities may contribute a small, transparent skill bonus while assigned to a team. Skill progression and bonuses must be understandable, testable and not mandatory for success.

### 3.4 Facilities and settlement

Facilities are useful, limited systems inside Base, not a regional city-management simulator. Preserve and refine:
- **Exploration Camp:** expedition/treasure-related bonuses and community identity.
- **Research Lab:** Exploration XP or research-related bonuses.
- **Spinda's Café:** temporary expedition bonus purchased with shared Bag berries.
- **Kecleon Shop:** purchases useful approved items using shared Poké.
- **Croagunk Swap Shop:** exchanges berry varieties using shared Bag quantities.
- **Dojo:** improves WORLD exploration skills and team capability.

Each facility has a clear effect, level/cost preview and visible benefit. Facility spending uses the shared Poké service and items use the shared Bag. Avoid a second inventory, facility-specific currency, excessive upgrade tiers or upgrades that make core progress inaccessible. Cosmetic customization is optional and lower priority than core gameplay.

### 3.5 Dungeon events and variety

Reuse and extend the existing floor-event foundation:
- Optional side paths and forks.
- Field-move interactions based on team member capabilities.
- Environmental events and hazards.
- Springs, treasure and research discoveries.
- Choices with clear outcomes and safe default resolution when no response is provided.
- Bonus rewards and milestone events.

Events must be deterministic per expedition/floor seed. A missing capability may mean a bonus is unavailable, never that an essential route or reward is permanently lost. No event may permanently injure, delete or release Pokémon, destroy owned items, or create a punitive recovery timer. Pending choices must have bounded storage and a safe default resolution after their response window.

### 3.6 Expedition outcomes, defeat and recall

**Implemented (0.156.0, `Outcomes`, rule version 1; the numbers are *(tune)*, a decision the owner may reverse):** a floor is cleared, a setback or a safe retreat by one deterministic comparison of the team's power (the sum of levels, 20% more for a favored type) with what the floor asks: the dungeon's recommended power (by the rank it needs: Bronze 90, Silver 150, Gold 220, Platinum 300, Diamond 400) times floor/floors. At 40% or more the floor is cleared in full; from 20% to 40% it is a **setback** (half the Poké and Exploration XP, no treasure, find, milestone, side path or choice, no dungeon clear, a job does not move on; the members still get their Pokémon XP and berries still count); under 20% the team **turns back** and comes home safely (the expedition ends). The first floor and every Normal-rank dungeon always clear in full, so a new player with a single starter is never stuck; only Bronze rank and above can set a team back. Nothing earned is rolled back, nothing is injured or locked, the team can be sent again at once, the report and the journal say what happened, and the team page explains how far it clears in full. Not done: moves, abilities and held items in the power, and the shared battle service.

Expeditions must have meaningful outcomes without making Pokémon disposable or turning failure into a punishment loop.

- A team may clear a floor, encounter a setback, retreat or be defeated. These outcomes are resolved by the shared expedition model and recorded against the floor's stable event ID.
- A defeated or forced-to-retreat team returns safely to Base. Pokémon are never permanently lost, injured for a real-time recovery duration, or made unavailable by a punitive timer.
- A failed floor may award reduced/partial floor rewards according to a transparent, versioned outcome rule. Do not award the full clear reward for an uncleared floor, but preserve rewards from earlier completed floors.
- Previously earned rewards, completed floors, discoveries, Finds and XP are never rolled back because a later floor fails.
- Recall is always available. Recall settles eligible elapsed progress up to the recall instant, then returns the team; it does not erase completed-floor outcomes.
- The expedition report distinguishes completed floors, setbacks/defeats, partial rewards, discoveries and the reason the team returned.
- No equipment durability, item destruction, Pokémon injury, team wipe penalty or consumable recovery requirement.

The shared battle/expedition specification owns the reusable outcome vocabulary and calculation. WORLD owns dungeon-specific modifiers and the presentation of the report. Balance simulations must cover both successful and failed expeditions.

### 3.7 Expedition presentation and optional active play

The expedition experience should communicate an adventure in progress, not just a timer and a number.

- Use a lightweight side-scrolling or tile-based expedition scene with assigned Pokémon sprites moving automatically through the dungeon. This is presentation of timestamp-settled state, not manual tile navigation or a continuously simulated battle.
- The live feed prioritizes meaningful events: floor cleared, new place/species discovered, Find created, mission objective advanced, Guardian/story event, significant reward, setback or return. Routine low-value events are summarized rather than spammed.
- A concise event history remains available in the expedition report. The live scene must not become a scrolling wall of every calculation.
- Optional active interactions may include reviewing a meaningful choice, choosing a discovered side path or using an available team capability. The expedition remains fully playable if the user does not open WORLD during progress.
- Optional acceleration may shorten remaining expedition time only through an explicit, understandable control. It must not require paid currency, create a competitive advantage in real-life modules or become necessary to receive normal rewards.
- Local day/night may influence atmosphere using device-local time. Weather and seasonal presentation are optional and configurable; no reward, encounter, story or progression may be missable because of real-world time, weather or season.
- Respect reduced motion, battery/performance limits and sound preferences. Pause scene animation when the screen is not visible.

## 4. Story and regional campaign architecture

WORLD has a gameplay progression system and a narrative experience. They complement each other; neither should make the other inaccessible.

### 4.1 Opening — Mythical Invitation

The opening is a simple Mythical-led adventure/tutorial, not a prophecy, chosen-one premise, universal catastrophe or mandatory overarching mystery. The custom home setting is Pokémon-only.

Mew's prank/welcome introduces the player and selected buddy to exploration. The opening naturally teaches the first dungeon, a request, team registration, expedition dispatch, settlement and recruitment. If satisfied with the pair's adventure, Mew chooses to join the shared collection. This is a permanent story/recruitment outcome and must be replay-safe.

Additional Mythical episodes may be introduced by Hoopa, Celebi, Jirachi, Victini, Shaymin, Darkrai, Marshadow and other suitable characters. Each episode has its own personality and adventure premise. Mythicals may act as tricksters/catalysts for dreams, memories and dimensional travel, but do not need a shared organization or master plan. Main plot remains low priority compared with regional adventures and PMD gameplay.

### 4.2 Player and buddy roles

The user is the player/Witness; there is no user avatar and no Pokémon avatar representing the user. The selected buddy is the visible recurring companion. The buddy can be changed to any owned Pokémon at any time, without resetting story, team or regional progress. Initial buddy selection follows the Product Bible and shared Pokémon specification.

Dialogue is authored for the buddy role and remains coherent when the selected Pokémon changes. Species-specific scenes use explicit authored variants or neutral role dialogue; do not mechanically rewrite a character-specific plot to fit a new species. Buddy choice is narrative/visual, not an unfair gameplay advantage.

### 4.3 Campaign families and content order

- **Custom Frontier:** original Pokémon-only home setting and tutorial environment.
- **Rescue Team:** distinct retelling inspired by Pokémon Mystery Dungeon: Red/Blue Rescue Team.
- **Explorers:** distinct retelling inspired by Explorers of Time/Darkness/Sky.
- **Mainline regions:** Kanto, Johto, Hoenn, Sinnoh and later regions as authored.
- **Additional PMD settings:** other Mystery Dungeon worlds and regions as content is developed.
- **Ranger regions:** Fiore, Almia and Oblivia, after the PMD/mainline foundation.
- **Selected spin-off settings:** only where the setting offers a coherent Pokémon-led adventure.

Rescue Team and Explorers remain separate, recognizable campaign experiences. Mainline stories are reinterpreted as Pokémon-only adventures, preserving recognizable characters, personalities, relationships, locations, conflicts and major events without copying the original games scene-for-scene. Do not require one universal chronology or forced crossover. Each campaign has independent chapter and completion state; shared collection, rank and achievements provide continuity.

All regions/maps are accessible from day one, with entry-level opportunities. Deeper content unlocks through relevant regional story progress, Exploration Rank and milestones. There is no mandatory regional travel order or travel timer.

### 4.4 Story runtime and presentation

Reuse the current story DSL/runtime, scene models, branch execution, resume state and archive foundations. Support:
- Dialogue with authored character portraits and expressions.
- Staged group scenes.
- Scripted dungeon interactions and sequences.
- Cinematic scenes for significant events.
- Background and environment changes.
- Scripted sprite movement and expressions.
- Narration, dialogue, sound effects and Pokémon cries.
- Branches, conditional lines, callbacks and flags.
- Scene/episode resume, replay and archive completion state.

Every scene must be validated for valid references, reachable branches, safe defaults, valid reward actions and resumability. Scenes can be skipped and replayed. Playback is never a reward gate; replay must not re-award recruitment, items, currency, XP or mission completion. Dialogue must be authored, character-consistent, emotionally coherent and natural. Avoid generic exposition and generated filler.

## 5. Pokémon acquisition and reward contracts

### 5.1 Exploration Finds — WORLD acquisition identity

WORLD's Pokémon acquisition method is **Exploration Finds**. A wild Pokémon discovered during an expedition becomes a Find awaiting the user's decision; it is not an egg and does not use a hatch scene.

Flow:
1. A deterministic expedition floor encounter identifies a species and records the encounter.
2. The species is added to WORLD's regional research notebook as discovered.
3. A Find record is created with stable identity, species/form, dungeon, floor, time and encounter provenance.
4. The Find appears in Base and Journal.
5. The user chooses to recruit it.
6. Shared Pokémon service creates one persistent owned instance with acquisition source `WORLD / Exploration Find`.
7. WORLD marks the Find as recruited/closed and records the shared owned-Pokémon ID.

Finds do not expire, disappear because the user was absent, consume a party slot automatically or replace another Pokémon. Recruitment is a deliberate action. Duplicate species are allowed as separate individuals. The species can count as discovered before recruitment, but it counts as caught/owned only after the shared service confirms creation.

Regional pools are configured per dungeon/region/floor. Regional forms retain canonical form identity. Mythical and legendary Pokémon are primarily story, investigation or Guardian acquisitions, not ordinary random floor finds. Cross-regional species require a permanent, justified special encounter.

### 5.2 Individual Pokémon XP — approved distribution rule

Every owned Pokémon that actively participates in an expedition receives the full individual Pokémon XP award for each eligible completed floor. The XP award is **not divided** among team members. Every participating member receives the same base floor award, subject only to explicitly documented shared progression modifiers that apply equally or to a clearly specified individual effect.

- Only members assigned to the team for that floor receive the award.
- Pokémon outside the expedition receive no individual expedition XP.
- Recalled, removed or unavailable members do not receive XP for later floors they did not participate in.
- Each floor award is tied to a stable event ID and is idempotent.
- Individual Pokémon XP is written through the shared Pokémon progression service; WORLD does not maintain a shadow Pokémon XP balance.
- Exploration XP/Rank is a separate shared progression track and is awarded once per eligible floor, not once per participating Pokémon.
- Exact XP amounts and modifiers are tuning data owned by the shared progression/economy specification and must be validated through simulation. Generous progression is preferred; do not add a participation split or catch-up penalty.

### 5.3 Reward categories and delivery

WORLD may generate:
- Exploration XP for shared Exploration Rank.
- Individual Pokémon XP for participating team members.
- Shared Poké.
- Shared Bag berries and approved items.
- Treasure boxes, whose opening and contents use shared item/reward services.
- Rare Candy and other approved progression items.
- Exploration Finds.
- Research, mission, story and milestone rewards.
- Guardian recruitment and approved shared Guardian perks.

WORLD creates reward events; the shared economy/progression services validate, ledger and deliver them. A pending reward claim is a delivery state/reference, not a separate spendable balance or inventory. Reward events use stable idempotency keys such as `world:<teamId>:<expeditionId>:<floorIndex>:<rewardType>` and include source, rule version, timestamp and relevant opaque entity references.

Reward design:
- Generous, legible and validated over long-run simulations.
- No daily login requirement, punitive absence mechanic or expiring essential reward.
- No arbitrary cooldown solely to slow collection.
- No Pokémon purchases, breeding, trade requirement or seasonal exclusives.
- No real-life essential functionality gated by Poké or WORLD rank.
- Explain guaranteed, milestone and chance-based rewards separately.

### 5.4 Guardians

Guardians are major legendary Pokémon associated with regional campaign climaxes or special permanent investigations. Their lifecycle:
1. A story/challenge becomes available through relevant campaign progress.
2. The player completes requirements and encounters the Guardian.
3. Encounter outcome is recorded once.
4. Shared Pokémon/Guardian service grants the canonical Guardian/owned identity without duplicate ownership.
5. WORLD records the story and Guardian outcome.
6. Shared progression configures approved Guardian perk effects and rank-gated slots.

A Guardian's perk may benefit WORLD or another module only through the shared Guardian system and the receiving module's approved contract. WORLD cannot directly modify another module's private data.

## 5.5 Expanded Pokémon gameplay and item systems

WORLD is the primary player-facing home for the expanded mainline, PMD and Ranger-inspired Pokémon activities. The shared Pokémon domain remains authoritative for owned Pokémon, abilities, natures, moves, evolution, held-item assignments, item inventory, item effects and reward settlement. WORLD owns the presentation and local gameplay state for expeditions, shops, crafting, jobs, contests, Pokéathlon, Berry Garden, cooking, camps, fossils, raids/boss variants, outbreaks, research encounters and Ranger-style field interactions.

The approved staged scope is defined in `../FEATURES/POKEMON_ECOSYSTEM.md` Section 20. Core rules include:
- Held items are persistent, reusable, non-consumable equipment and never lifetime single-use. An effect trigger never consumes, breaks, charges or removes the item; scoped trigger/cooldown state is separate from inventory and resets for later eligible encounters.
- Item acquisition is explicit and source-backed: expedition loot, missions, bosses/Guardians, permanent shops, crafting, foraging, research, capped Pokémon abilities/jobs and authored story rewards. All delivery uses the shared Bag and ledger.
- Evolution stones/materials, vitamins, TMs, Poké Balls, berries, shards, fossils and other item categories retain their own canonical consumable/material/equipment classifications.
- Mega Evolution, Z-Moves, Dynamax/Gigantamax and Terastallization use one shared transformation framework. Temporary transformation state never creates a duplicate owned Pokémon or permanently inflates base stats.
- Abilities, Hidden Abilities, natures/Mints, moves/mastery, bounded training, Ribbons/Marks and Pokédex research are shared Pokémon identity/progression systems, not WORLD-owned shadow attributes.
- Jobs and other offline activities settle deterministically, cannot double-assign incompatible Pokémon, and never create attendance penalties or expiring essential rewards.
- Contests, Pokéathlon, camps, cooking, Berry Garden, fossils, outbreaks, raids, Secret Bases and Ranger field mechanics are optional Pokémon activities. They do not depend on personal health, financial, relationship, diary or household data.

WORLD facilities may expose shops, training, item refinement/crafting, research and activity interfaces, but all inventory mutations must go through the shared inventory/economy services. Essential evolution/progression materials require reliable acquisition paths. Do not introduce a WORLD-only Bag, currency, friendship meter, collection or item ownership model.

## 6. Data ownership, relationships and persistence

### 6.1 Ownership matrix

| Data | Authoritative owner |
|---|---|
| Team definitions, presets and assignments | WORLD |
| Expedition state, timestamps, floor index and settlement state | WORLD |
| Dungeon/place/region progress | WORLD |
| Mission/request definitions and progress | WORLD |
| Regional story, chapter, branch and playback state | WORLD |
| Research notebook and expedition journal | WORLD |
| WORLD skills and facilities | WORLD |
| Finds and recruitment status | WORLD until shared recruitment succeeds |
| Canonical species and forms | Shared Pokémon system |
| Owned Pokémon instances, individual level/XP, moves, stats and evolution | Shared Pokémon system |
| Pokédex and caught state | Shared Pokémon system |
| Exploration XP and Exploration Rank | Shared progression service; WORLD is the eligible source of ordinary XP events |
| Bag and item quantities | Shared economy |
| Poké balance and spending | Shared economy |
| Reward ledger and idempotency | Shared economy/reward service |
| Guardian ownership and perk slots | Shared Pokémon/progression system |
| Android notifications and delivery | Shared reminder/notification infrastructure |
| Backup, restore, encryption and export | Shared data lifecycle architecture |

### 6.2 Cross-module relationships

| Module | Permitted relationship |
|---|---|
| PLAN | Pokémon acquired/developed through PLAN may be assigned to WORLD teams. PLAN tasks/focus do not advance WORLD expeditions or Exploration Rank. |
| CARE | Shared Pokémon and Bag berries may be used by CARE. CARE journal, mood, Diary Card and routine content never enters WORLD. |
| HEAL | Field Research acquisitions enter shared collection. Health records, measurements, diet, sleep, symptoms and medical information never enter WORLD. |
| MINT | No finance records, balances or transactions enter WORLD. |
| HOME | No chores, inventory, maintenance or household notes enter WORLD. |
| LINK | No contact data, relationship notes or personal correspondence enter WORLD. |
| GEAR | May provide navigation shortcuts only; utility use produces no WORLD progression/rewards. |
| Launcher | Read-only projection of WORLD rank, team state, Finds and approved scene state; launcher does not own WORLD records. |

WORLD does not receive sensitive real-life data. The fact that a Pokémon was acquired through HEAL or CARE may be retained as non-sensitive acquisition provenance in the shared Pokémon record, but the underlying activity and private record are not exposed to WORLD.

### 6.3 Logical persistence model

The following is a logical ownership model, not a mandate to create one table per node:

```text
WorldState
├── ExpeditionTeams
│   ├── TeamIdentity
│   ├── AssignedPokemonIds
│   └── ActiveExpedition
├── RegionProgress
│   ├── PlaceAndDungeonRecords
│   ├── DiscoveredPlaces
│   ├── StoryProgress
│   └── GuardianProgress
├── MissionProgress
├── WorldSkills
├── FacilityProgress
├── ResearchRecords
├── ExplorationFinds
├── PendingRewardDeliveryReferences
├── ExpeditionJournal
└── StoryPlaybackState
```

Use stable IDs, versioned schemas and transactional updates. WORLD may keep opaque references to shared owned-Pokémon IDs, reward event IDs and shared service receipts; it must not duplicate canonical shared records. Migration must preserve historical WORLD state and reconcile legacy balances/items exactly once.

## 7. Reminders, notifications and Android integrations

### 7.1 Notifications

WORLD notifications are optional, event-driven and rate-limited. Candidates:
- A mission/request is complete and ready for review.
- A meaningful expedition milestone has occurred.
- A new Find is waiting, if the user enables this notification.
- A story/Guardian milestone is available.

Do not notify for every floor, berry, minor item or routine reward. Notifications are never required to preserve, settle or claim rewards. Group/coalesce multiple events into a concise summary.

WORLD registers requests through the shared notification/reminder infrastructure and uses the shared notification delivery contract. It does not create a separate scheduler or override global quiet hours. Module-specific notification preferences belong to WORLD settings; global permission, quiet hours, delivery, reboot recovery and action routing remain shared.

### 7.2 Widgets, launcher and deep links

Preserve and improve the WORLD Android widget and launcher integration. Widget content may include rank, active teams/destinations, significant Finds and ready requests. It is a read-only projection and may deep-link to Base, Atlas, Expeditions, Guild or Finds. It must not duplicate WORLD persistence or reward logic.

Deep links must validate that the referenced record still exists and route to a safe fallback if it has been removed or migrated. No sensitive module data appears in widget or launcher surfaces.

### 7.3 Network, permissions and external services

WORLD works without an account or internet connection. It requires no location permission, account, multiplayer server, cloud save, social service or external Pokémon API. All required region, dungeon, story, species-pool and art metadata is packaged locally or imported through the approved app update/content process. If future optional external content is proposed, it requires a separate privacy, licensing, offline and dependency review.

## 8. Visual presentation

WORLD uses the immersive presentation intensity defined in `../FEATURES/DESIGN_SYSTEM.md`. It may use PMD scene composition, character portraits, expressive dialogue panels, pixel environments, sprites, tile maps and contextual sound, while retaining shared typography, spacing, tokens, borders, buttons, dialogs, interaction states and accessibility behavior.

Primary shared components:
- **PrimaryDialogueBox:** important character messages, contextual prompts and major action groups.
- **SecondaryDialogueBox:** supporting status, expedition reports and grouped context.
- **InlinePanel/FramedSurface:** team configuration, dungeon information and controls that are not dialogue.
- **CharacterPortrait:** authored story scenes and significant Pokémon interactions.
- **ListRow/DataRow:** mission lists, team rosters, research and history.
- **ProgressIndicator:** rank, expedition and research progress where a visual indicator adds value.
- **DialogueDialog/BottomSheet/ConfirmationPrompt:** contextual actions, team editing and consequential confirmations.

Presentation rules:
- Use actual PMD dialogue-box geometry and crisp pixel-inspired borders; do not make every value or row a dialogue box.
- Dialogue panels are content-driven and adapt to available width; no fixed speech-bubble dimensions.
- Use system typography for long descriptions, dense data and accessibility-critical text. Pixel fonts are limited to short accents or authored game dialogue where legible.
- Preserve original pixel-art dimensions and nearest-neighbor rendering; use nine-slice borders without stretching corners.
- Use character portraits, background changes and scripted sprite movement for narrative emphasis, not decoration on every utility surface.
- Keep the primary action obvious; use progressive disclosure for advanced controls.
- Avoid dashboard clutter, redundant captions, nested boxes and repeated reward fanfare.
- Sound effects, cries and music have separate controls; essential information never depends on sound.

Accessibility and responsiveness:
- Light and dark themes are first-class.
- Respect system text scaling and display size; content must reflow without clipping.
- TalkBack names, roles, state descriptions and logical traversal for all controls.
- Minimum 48 dp interactive targets.
- Reduced-motion support; no essential content depends on animation.
- Sound can be disabled independently.
- Map has a text/list alternative; color is never the only state indicator.
- Provide alternatives to long press, drag, swipe and timed choices.
- Verify phone, landscape where relevant, tablet/foldable and narrow/wide layouts.

## 9. Privacy, security and data lifecycle

- WORLD stores only WORLD game records and opaque references to shared entities.
- No health, financial, CARE, HOME or LINK record content is copied into WORLD logs, story flags, reward events, notifications, widget text or analytics.
- No account, cloud sync, telemetry or third-party transmission is required.
- Notification text is concise and must not reveal unrelated private information.
- Reward logs include game event provenance, not sensitive real-life source data.
- Backup/restore and export use shared data lifecycle services. WORLD must support schema migration, interrupted restore and integrity validation.
- Imported/restored records must not replay rewards, recruitment, story grants or mission claims.
- Asset provenance/licensing is tracked separately; assets without clear distribution rights are not bundled in a public release.

## 10. Existing implementation audit — baseline 0.123.0

The supplied source archive contains a substantial WORLD foundation. The implementation direction is **reuse and refactor**, not a ground-up rewrite of the expedition engine.

### 10.1 Reuse after review

| Existing code/assets | Direction |
|---|---|
| `world-core` module | Retain as the proven domain foundation; refactor boundaries as shared architecture lands |
| `WorldSim` | Preserve deterministic floor settlement and seed-based outcomes; extend reward and battle model |
| `WorldData` | Preserve proven behaviors and migration knowledge; split ownership responsibilities |
| `WorldTeam`, `Expedition`, `TeamPreset` | Reuse concepts and stable team behavior |
| `Dungeons`, `Layers`, Kanto/Johto/Hoenn/Sinnoh dungeon definitions | Reuse and expand content configuration |
| `Missions` | Reuse mission categories/progress after ownership and idempotency review |
| `Choices`, `FloorEvents`, `DungeonModifiers` | Reuse safe optional-event foundations |
| `Notebook`, `Research`, `WorldMoments` | Reuse and expand research/milestone behavior |
| `Legends` | Reconcile with shared Guardian ownership and perks |
| Story DSL, models, catalog, scene state and authored chapter files | Reuse runtime and authoring pipeline; expand and validate content |
| `WorldSpeed` and speed explanation | Preserve explanatory UI concept and align to final battle model |
| PMD/mainline maps, sprites, portraits, item art, tilesets and SFX | Reuse only after asset provenance and rendering checks |
| Existing tests | Preserve, update and expand; add integration/device coverage |

### 10.2 Refactor and improve

**Persistence and ownership** *(status 0.145.0: Poké is done, since World holds no balance and earns through `pokeEarned`; items go to the Bag as they arrive; `xp` and unopened `treasure` are still World's own)*: `WorldData` at the 0.123.0 baseline stored WORLD-local `xp`, `poke`, item quantities and treasure counts alongside teams, story, Finds and research. These balances/quantities conflict with the Product Bible's shared Exploration Rank, shared Poké and shared Bag. Move authoritative values to shared services; retain only WORLD source events and pending delivery references. Review `GameStore`'s WORLD-specific functions and establish module-owned repositories/use cases with transactions and migration.

**Expedition rewards:** current `WorldSim` deterministically calculates floor XP, Poké, berries, treasure, Finds, milestones and events. Keep deterministic settlement, but route each reward through shared idempotent services. Add the approved individual Pokémon XP award to every participating member per completed eligible floor; do not split the award.

**Strength model:** current power sums Pokémon levels and applies a 20% favored-type bonus per matching member. Floor time uses `base / (1 + power / 100)`, with a 25% minimum, plus mastery/legendary modifiers. Retain as a tested starting point and migrate toward the shared simplified battle identity model using stats, types, moves and abilities. Do not replace with opaque math or assume the current formula is final.

**Navigation:** the current landing/navigation was simplified in 0.121.0. Reconcile the existing UI into the target Base, Atlas, Expeditions, Guild and Journal destinations. Reuse screens and components that pass UX/accessibility review; move skills, facilities, research, recruitment and settings into contextual destinations.

**Expedition presentation:** improve team suitability, dungeon preview, active status, progress summaries, reward explanation and report readability. Clearly separate guaranteed, milestone and chance-based rewards.

**Story:** the 0.123.0 baseline already separates Mew's opening from regional layers, keeps Rescue Team and Explorers distinct, executes story branch actions and supports recursive branch validation. Do not rebuild these as missing. Extend the runtime, content coverage, replay-safety and authoring validation pipeline.

**Lifecycle/performance:** remove recurring three-second UI polling as a settlement mechanism. Settle on WORLD entry/resume and relevant transitions using persisted timestamps. Profile large save settlement, multi-team operation, animation and rendering.

**Visuals:** existing Compose components such as `TextBox`, `PixelButton` and `PixelBar` are candidates for selective reuse, not automatic approval. Assess against the shared dialogue-box anatomy, content sizing, themes, TalkBack, text scaling and responsive layout.

### 10.3 Retire or replace

- WORLD-owned Poké balance → shared economy balance.
- WORLD-owned Exploration XP/rank derivation → shared progression record; WORLD emits eligible events.
- WORLD-owned permanent item/treasure inventory → shared Bag and treasure/reward services.
- Direct shared Pokémon mutation from WORLD → shared Pokémon progression/recruitment APIs.
- Monolithic WORLD operations embedded in `GameStore` → module-owned use cases/repositories behind shared services.
- Old navigation grouping → five primary destinations.
- Continuous UI polling → lifecycle-aware deterministic settlement.
- Hardcoded campaign-specific progression where reusable content configuration is appropriate → validated campaign/region configuration.

Do not discard pending reward state until all outstanding rewards are safely reconciled. Do not remove legacy storage before migration fixtures prove the data is represented in the new owners.

## 11. Implementation phases and priorities

WORLD work follows the dependency-first roadmap in the Implementation Guide. Do not broaden campaign content while shared ownership and migration remain unresolved.

### Phase 0 — Architecture and migration (P0)
- Finalize WORLD/shared-service dependency contracts.
- Inventory legacy WORLD fields, save versions, `GameStore` operations, alarm/notification identities and reward paths.
- Define transactional migration from 0.123.0.
- Reconcile WORLD XP/Poké/items/treasure with shared progression/economy/Bag.
- Define reward idempotency keys and pending delivery reconciliation.
- Define individual Pokémon XP event contract and test fixtures.
- Preserve historical team, dungeon, story, mission, research and Guardian progress.

**Exit criteria:** existing saves migrate without data loss, duplicate Pokémon, duplicate rewards, duplicated currency or duplicated items; interrupted migration resumes safely.

### Phase 1 — Core gameplay and navigation (P1)
- Implement Base, Atlas, Expeditions, Guild and Journal on the shared app shell.
- Complete team assignment, presets, dungeon selection and suitability explanation.
- Integrate shared collection, Finds/recruitment, Bag, Poké, Exploration Rank and reward ledger.
- Implement per-floor individual Pokémon XP for each participating member.
- Complete mission progress and claim delivery.
- Replace polling with lifecycle-aware settlement and validate offline recovery.

**Exit criteria:** full basic exploration loop works offline through process death/reboot, with deterministic and idempotent rewards.

### Phase 2 — Story and regional foundations (P1)
- Complete the Mew/Mythical introductory tutorial.
- Complete story runtime, branch/resume/replay validation.
- Keep Rescue Team and Explorers as separate authored campaigns.
- Improve existing regional campaigns and story archive.
- Establish region/campaign content schema and asset validation pipeline.

**Exit criteria:** opening and campaign framework are playable, resumable, replayable and do not re-award rewards on replay.

### Phase 3 — Deeper expedition gameplay (P2)
- Finalize shared battle-strength model for idle expedition resolution.
- Incorporate relevant stats, types, moves, abilities and dungeon modifiers.
- Expand field moves, floor events, choices and side paths.
- Improve research, skills, facilities and useful Poké sinks.
- Simulate rank progression, team balance, XP, rewards, rarity and long-run economy.

**Exit criteria:** team composition matters without competitive micromanagement; progression remains generous, understandable and sustainable.

### Phase 4 — Regional content expansion (P2)
- Complete authored PMD and mainline region coverage in a sustainable sequence.
- Add further PMD settings and permanent legendary investigations.
- Add Ranger regions Fiore, Almia and Oblivia after the PMD/mainline foundations.
- Expand settlements, routes, dungeons, regional rosters, missions, Guardians and postgame.

**Exit criteria:** every released region has coherent geography, regional availability, entry-level content, deeper progression, missions and authored story identity.

### Phase 5 — Polish and release QA (P0 before release)
- Complete asset provenance/licensing review.
- Test offline settlement, restore, migration and duplicate reward protection.
- Test widgets, launcher deep links and optional notifications.
- Complete light/dark, TalkBack, font scaling, reduced-motion and touch-target review.
- Profile battery, memory, rendering and large-save settlement.
- Run automated domain/integration tests and physical Android device acceptance.

**Exit criteria:** no unresolved critical data, reward, privacy, accessibility, licensing, lifecycle or performance defects.

## 12. Acceptance criteria

### Gameplay and progression
- [ ] New save can complete the Mythical-led opening tutorial.
- [ ] Teams can be created, edited, saved as presets and dispatched.
- [ ] No Pokémon can be assigned to conflicting active teams.
- [ ] All regions are accessible from day one at entry-level depth.
- [ ] Expeditions progress while the app is closed and recover after process death/reboot.
- [ ] Same seeded expedition produces the same floor outcomes regardless of settlement frequency.
- [ ] Offline cap is transparent; no earned progress is removed.
- [ ] Every participating Pokémon receives the full individual XP award per eligible completed floor; award is not divided.
- [ ] Exploration XP/Rank is awarded once per floor through shared progression.
- [ ] Finds are permanent until the user recruits them; recruitment creates one shared owned instance.
- [ ] No egg or hatch scene is used for WORLD recruitment.
- [ ] Story, mission and Guardian rewards are replay-safe.

### Shared ownership and economy
- [ ] One shared Pokémon collection and Pokédex.
- [ ] One shared Bag and item quantity source.
- [ ] One shared Poké balance.
- [ ] One authoritative Exploration XP/Rank record.
- [ ] Individual Pokémon XP is stored only by shared Pokémon progression.
- [ ] Reward processing is idempotent across retries, migration and restore.
- [ ] No pending reward is spendable as a parallel inventory.
- [ ] Guardian ownership/perks use shared services without duplicate Pokémon.

### Story and content
- [ ] Mew opening is separate from regional campaign progression.
- [ ] Mythical episodes are optional and self-contained.
- [ ] Rescue Team and Explorers remain separate.
- [ ] Campaigns can be played in any order.
- [ ] Dialogue is authored and character-consistent; no human characters.
- [ ] Scenes can be skipped, resumed and replayed.
- [ ] Buddy switching does not reset story or change gameplay advantage.
- [ ] Replay cannot re-award recruitment, currency, items, XP or mission completion.

### Privacy, offline and reliability
- [ ] WORLD works without account, network, location permission or external service.
- [ ] No health, finance, CARE, HOME or LINK content enters WORLD records, logs, notifications or reward events.
- [ ] Save migration and backup/restore preserve WORLD progress and do not replay rewards.
- [ ] Repeated settlement and interrupted operations are idempotent.
- [ ] Notifications are optional, rate-limited and use shared delivery/quiet-hours policy.
- [ ] Widget and launcher surfaces are read-only projections.

### Presentation and accessibility
- [ ] Shared PMD dialogue-box language is consistently applied.
- [ ] Pixel art remains crisp and correctly scaled.
- [ ] No unnecessary dashboard clutter, redundant captions or nested containers.
- [ ] Light/dark themes, enlarged text, TalkBack and reduced motion pass.
- [ ] Maps have accessible non-map alternatives.
- [ ] Sound is optional and never carries essential information.
- [ ] Phone and larger responsive layouts remain usable.

## 13. Source-of-truth relationships

Shared persistence, transactional reward delivery, backup/restore, legacy save migration and recovery follow `../ARCHITECTURE/DATA_AND_MIGRATIONS.md`. WORLD owns adventure progress but not canonical Pokémon ownership, Bag, currency or the shared reward ledger.

- Product-wide intent and module ownership: `../PRODUCT_BIBLE.md`.
- Baseline audit and delivery order: `../IMPLEMENTATION_GUIDE.md`.
- Shared Pokémon collection, progression, battle identity, acquisition, economy and reward ownership: `../FEATURES/POKEMON_ECOSYSTEM.md`.
- Expanded item acquisition, persistent held-item rules, abilities, moves, transformations and optional Pokémon activities: `../FEATURES/POKEMON_ECOSYSTEM.md` Section 20.
- Shared PMD visual language and components: `../FEATURES/DESIGN_SYSTEM.md`.
- Shared notification/reminder delivery: `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.

This file is the single WORLD requirements source. Do not maintain parallel WORLD feature specifications with conflicting ownership or behavior. Historical World design/story/economy documents and source code are references; when they conflict with the approved Product Bible or this specification, the current approved specifications take precedence.

## Shared Pokémon ecosystem contract

WORLD owns expedition teams, Exploration Rank, campaigns, dungeon/floor settlement, Exploration Finds and authored recruitment. Its expedition teams remain independent of every life-module party. WORLD references canonical owned Pokémon IDs; the shared Pokémon ecosystem owns the global collection, individual Pokémon XP, evolution, eggs, Bag, Poké, reward ledger and canonical ownership. Each eligible Pokémon participating in a completed floor receives the full individual XP award once; Exploration Rank XP remains separate. Finds persist until explicitly recruited and do not auto-slot or replace a Pokémon. See `../FEATURES/POKEMON_ECOSYSTEM.md` and `../FEATURES/POKEMON_ECOSYSTEM.md` Section 20.

---

## Testing reference

WORLD deterministic simulation, expedition resume, story-state persistence, reward idempotency, Pokémon ownership and offline recovery must follow [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md). Use fixed seeds and assert persisted outcomes, not transient UI messages.

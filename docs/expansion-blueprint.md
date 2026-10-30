# PokéLife — Ecosystem & Expansion Blueprint

**Product direction and conceptual architecture**  
**Status:** Strategic concept document, added in 0.53.0 as an outside proposal. It is **not** a set of owner decisions: `docs/ecosystem.md`, section 12, says what is adopted, what is open and where it conflicts with decisions already taken. Written against 0.52.0, so "Home" here means the household region and "Plan" is the old Home Region in code (`Region.HOUSEHOLD`, `Region.PLAN` stored as `HOME`).  
**Scope:** Regions/modules, shared Pokémon systems, Pokégear utilities, Android companion experiences, architecture and roadmap

---

## 1. Product vision

PokéLife is a Pokémon-themed life companion: a collection of useful life-management tools and distinct personal-development environments connected by one shared Pokémon world.

It should work as a practical app first and a Pokémon experience second. Pokémon add personality, companionship, discovery and motivation; they should not gate essential functionality or turn ordinary life into a punishing game.

The long-term product has three connected layers:

1. **Regions:** substantial life domains, each with its own identity, activities, records, progression and Pokémon-acquisition loop.
2. **Shared Pokémon world:** one trainer identity, collection, Pokédex, inventory and cross-region progression.
3. **Companion experience:** Pokégear, widgets, live wallpaper, notifications, launcher integration and wearable experiences that make PokéLife useful outside the app.

### Design principles

- Useful without Pokémon rewards; enhanced by Pokémon, not dependent on them.
- Several Pokémon and meaningful rewards per day should be possible through ordinary use.
- Generous acquisition; do not design around scarcity, grind or artificial daily limits.
- No decay or punishment for missed days. Returning should feel welcoming.
- Distinct regions should feel like separate places in one world, not tabs with different colors.
- One real-world action should have one primary reward owner.
- Personal, sensitive or detailed records stay within their owning region. Shared game systems receive only the minimum event data needed.
- External surfaces should be glanceable, fast, configurable and battery-conscious.
- Essential utilities must work regardless of which Pokémon the user owns.

---

## 2. The region ecosystem

The long-term concept includes eight regions: the existing Plan and Care regions, plus six proposed regions.

| Region | Core fantasy | Domain |
|---|---|---|
| Plan | Organize your time | Tasks, schedules, focus, habits and projects |
| Care | Take care of yourself | Personal routines, rest, emotional wellbeing and reflection |
| Grow | Become better at something | Learning, study, skill development and practice |
| Home | Care for your living space | Cooking, groceries, cleaning and household management |
| Health | Build a healthier body | Movement, fitness, nutrition and recovery |
| Finance | Build financial stability | Budgeting, bills, savings, debt and financial literacy |
| Connect | Maintain meaningful connections | Relationships, social plans and community |
| Explore | Discover the world | Travel, places, outdoor activities and experiences |

A region should only be implemented when it has a meaningful domain, useful activities, its own data ownership, distinct progression and a coherent acquisition method. Avoid creating a region for every small feature.

### 2.1 Plan — organize your time

**Status:** Existing region.

**Core fantasy:** A place to organize commitments and make progress on what matters.

**Domain and features**
- Tasks and projects
- Schedules and calendar-linked activities
- Focus sessions
- Habits and recurring activities
- Planning and prioritization
- Regional party and lead Pokémon
- Focus Eggs and Task Eggs

**Acquisition:** Focus and task activity generates Plan-region progress and Pokémon rewards.

**Boundary:** Plan can schedule or initiate work owned by another region, but should not automatically claim that region's activity as its own reward. For example, a workout scheduled in Plan is still a Health activity.

### 2.2 Care — take care of yourself

**Status:** Existing region.

**Core fantasy:** A calm, forgiving environment for personal maintenance and emotional wellbeing.

**Domain and features**
- Personal routines
- Calm breaks
- Skills and self-care activities
- Reflection and private journaling
- Visitors and befriending
- Regional party and lead Pokémon
- Care Eggs and visitor-related acquisition

**Acquisition:** Care activities contribute to its egg meter and visitor relationship loop.

**Privacy boundary:** Journal entries, mood details and other sensitive Care records remain local to Care. Only minimal effort events should be shared with the global reward system.

**Design caution:** Avoid making Care feel like a productivity checklist. Rest and missed days should not create debt or failure states.

### 2.3 Grow — become better at something

**Status:** Proposed.

**Core fantasy:** An academy, library, dojo and discovery lab where the trainer learns, experiments and develops skills.

**Domain and features**
- Learning paths and learning goals
- Study and practice sessions
- Review schedules and flashcards
- Lessons and reference materials
- Skill-practice logs
- Projects and milestones
- Learning reflections
- Challenges and optional evolution trials

Support both formal and informal learning: languages, programming, cooking techniques, photography, music, professional development, hobbies and other skills.

**Acquisition:** Research Discoveries. Meaningful learning actions contribute to discovery progress and Grow Eggs. Multiple ordinary learning activities should be capable of producing several Pokémon in a day.

Possible qualifying actions:
- Completing a practice or study session
- Reviewing material
- Completing a lesson
- Reaching a project milestone
- Recording a learning reflection
- Trying a new technique or skill

**Signature mechanic — Evolution Trials:** Optional, forgiving milestones based on practice, application, review or exploration. Do not grade the user's actual intelligence or knowledge, and do not make evolution depend on perfect attendance.

**Possible Pokémon identity:** Abra, Slowpoke, Natu, Smeargle, Magikarp, Caterpie, Larvesta, Riolu, Machop, Elgyem, Unown and Beldum. Kubfu/Urshifu could serve as a regional patron or aspirational figure.

**Privacy boundary:** Learning notes, subjects, goals and detailed journal content remain in Grow. Shared systems receive only a minimal qualifying activity event.

### 2.4 Home — care for your living space

**Status:** Proposed.

**Core fantasy:** A cozy, inhabited home that becomes more complete and personal through everyday care.

**Areas and features**
- **Kitchen:** recipes, meal planning, cooking notes
- **Pantry/Market:** groceries, shopping lists, pantry inventory
- **Living Space:** cleaning, organization and maintenance
- **Closet:** clothing, laundry and outfit care
- **Home Projects:** repairs, decorating, decluttering and improvements
- Household routines and checklists

A practical first release should be kitchen-first, then expand into broader household management and eventually closet features.

**Acquisition:** Household Finds. Cooking, grocery, cleaning, laundry, maintenance and decorating activities feed a Home discovery system. Avoid awarding one Pokémon for every tiny checkbox; use sensible activity groupings and milestones while retaining generous daily acquisition.

**Signature mechanic — Rotom and household interactions:** Rotom forms or other Pokémon can interact with appliance-themed spaces:
- Microwave / kitchen
- Washing machine / laundry
- Refrigerator / pantry
- Fan / living space
- Mower / garden or outdoor area

Do not require the user to own any particular appliance. These can be themed interactions, cosmetics or optional environmental details.

**Possible Pokémon identity:** Rotom, Miltank, Swirlix, Milcery, Applin, Sinistea, Lechonk, Fidough, Smoliv, Tandemaus, Sewaddle, Furfrou and Frigibax. Ogerpon could serve as a patron.

**Boundary:** Home owns the household activity. Plan may schedule it; Health may receive a linked nutrition record if the user chooses to log a meal.

### 2.5 Health — build a healthier body

**Status:** Proposed. Consolidates Move, Fitness and Nutrition into one region.

**Core fantasy:** A supportive training ground and outdoor environment for physical wellbeing.

**Subdomains**
- **Move:** walking, steps, mobility, sports and general movement
- **Fitness:** strength training, cardio, workout plans, training history and progress
- **Nutrition:** meals, hydration, food logs and nutrition goals
- **Recovery:** rest, sleep integration, soreness and training-load context

**Features**
- Activity and workout logging
- Training plans and exercise library
- Mobility and warm-up routines
- Sports and activity history
- Nutrition and hydration tracking
- Recovery and rest-day context
- Optional integration with supported health platforms and wearables

**Acquisition:** Activity Discoveries. Movement, workouts and nutrition-related effort can contribute to Health rewards. Rewards should be generous and not require extreme exercise or precise tracking.

**Signature mechanic:** Training, growth and physical milestones.

**Safety and privacy principles**
- Do not reward weight loss, calorie restriction, low body weight or other potentially harmful outcomes.
- Avoid shame, streak penalties and pressure to exercise while unwell or injured.
- Keep detailed health data private to Health and connected health stores. Share only minimal effort events with the game layer.
- Make manual logging possible when wearable integrations are unavailable.

### 2.6 Finance — build financial stability

**Status:** Proposed; privacy-sensitive.

**Core fantasy:** A treasury or market where the trainer learns to manage resources and build stability.

**Features**
- Income and expense tracking
- Budgets and spending categories
- Bills and recurring payments
- Savings goals and sinking funds
- Debt tracking
- Subscription tracking
- Financial education and explainers
- Optional account or transaction integrations in a later phase

**Acquisition:** Financial Milestones. Reward reviewing finances, planning, learning and completing administrative tasks—not spending money, having a high balance, earning a particular income or taking financial risks.

**Signature mechanic:** Building a treasury, completing financial goals and unlocking milestones.

**Privacy and security**
- Financial records should be isolated from the shared Pokémon system.
- Consider encryption, secure local storage and explicit export/deletion controls.
- Do not expose balances, merchants, transactions or debt details in widgets or notifications by default.
- Shared game systems should receive only minimal milestone events.
- Do not make access to Pokémon or progression conditional on financial status.

### 2.7 Connect — maintain meaningful connections

**Status:** Proposed; privacy-sensitive.

**Core fantasy:** A village plaza or community garden where relationships and community life are represented through encounters and shared experiences.

**Features**
- Social plans and reminders
- Birthdays and important-date calendar
- Gift ideas and occasion planning
- Private relationship notes
- Community and volunteering activities
- Shared activities and meaningful moments

**Acquisition:** Encounters. Social effort and community participation can trigger discoveries.

**Signature mechanic:** NPC relationships, community spaces and a growing village.

**Privacy and ethical boundaries**
- Avoid scoring real people or ranking relationships.
- Do not track other people's behavior, location or responsiveness.
- Avoid requiring contact uploads or access to private messages.
- Do not make users feel obligated to socialize to maintain progress.
- Keep personal relationship details private; the shared game layer should receive only a minimal activity event.

### 2.8 Explore — discover the world

**Status:** Proposed.

**Core fantasy:** A map, campsite and expedition network that grows as the trainer experiences the world.

**Features**
- Travel planning and itineraries
- Places visited and wishlists
- Local discoveries
- Outdoor activities
- Museums, events and attractions
- Travel journals and photo memories
- Optional map and location integration

Support everyday local exploration as well as major trips. The region must not assume that travel is frequent or expensive.

**Acquisition:** Exploration Finds. Visits, experiences, outdoor activities and discoveries contribute to Explore rewards.

**Signature mechanic:** Maps, discovered locations, regional collections and expedition milestones.

**Privacy boundary:** Location history should be opt-in. Avoid continuous tracking by default. Let users record discoveries manually.

---

## 3. Region boundaries and cross-region ownership

Regions will naturally share context. The system needs a canonical owner for each real-world action.

| Real-world activity | Primary owner | Possible linked context |
|---|---|---|
| Learning to cook | Grow | Home recipe collection |
| Cooking dinner | Home | Health nutrition log |
| Going for a run | Health | Explore, if part of an expedition |
| Studying for work | Grow | Plan schedules the session |
| Paying an electricity bill | Finance | Home can link to the bill |
| Planning a trip | Explore | Finance can track the budget |
| Meeting a friend | Connect | Explore can record the destination |
| Scheduling a workout | Plan | Health records the workout |
| Cleaning the kitchen | Home | Plan can schedule it |
| Taking a calm break | Care | Pokégear can provide the timer |

### Reward ownership rules

1. Each qualifying real-world action has one primary reward owner.
2. Other regions may receive linked records or contextual information, but should not independently reward the same action.
3. A region may initiate an action owned by another region without taking ownership of its reward.
4. Use explicit source ownership and idempotency keys to prevent duplicate rewards.
5. Utility tools do not own regional rewards. The context that launched a utility determines whether an event belongs to a region.
6. Direct use of a utility from Pokégear should not automatically grant regional progress.

Example: a workout created in Plan and completed in Health should produce one Health reward. Plan records the schedule/completion context but does not issue a second reward.

---

## 4. Shared Pokémon world

All Pokémon acquired across all regions belong to one trainer identity and shared collection.

### Shared systems
- Trainer profile and rank
- Global Pokémon collection
- PC/storage
- Pokédex
- Form Dex
- Shiny Dex
- Shared Bag and items
- Evolution system
- Quests and achievements
- Global progression and reward ledger
- Collection milestones and history

### Regional identity remains separate

- Each region has its own party and lead Pokémon.
- A region's lead and party should not be replaced by a single global buddy.
- Pokémon can be transferred between regional parties and the shared PC through explicit actions.
- Each Pokémon should retain acquisition provenance, including source region, acquisition method and date.
- Regional environments, activities, NPCs and progression remain distinct even though the collection is shared.

### Regional acquisition identity

| Region | Acquisition concept |
|---|---|
| Plan | Focus Eggs and Task Eggs |
| Care | Care Eggs and visitor befriending |
| Grow | Research Discoveries |
| Home | Household Finds |
| Health | Activity Discoveries |
| Finance | Financial Milestones |
| Connect | Encounters |
| Explore | Exploration Finds |

These names are conceptual. They do not require eight entirely different technical egg systems. A shared reward/acquisition framework can support different regional presentations and rules.

### Economy principles

- Several Pokémon per day should be possible with ordinary engagement.
- More Pokémon and more Rare Candy are generally preferable to artificial scarcity.
- Do not introduce grind merely to make each region feel different.
- Do not force numerical parity by nerfing a region's rewards.
- Measure acquisition across realistic light, typical and heavy use.
- Duplicate Pokémon should still have a useful destination or conversion path.
- Avoid making perfect attendance a requirement for collection completion.

### Economy validation

The current economy simulator does not cover all Care acquisition loops. A future multi-region simulator should model all implemented regions and measure:
- Pokémon acquired
- Eggs or discovery rewards generated
- Rare Candy and other item rewards
- Duplicate rates and duplicate handling
- Species and form completion
- Rarity distribution
- Effort and time required
- Light, typical and heavy usage
- 7-, 30-, 60- and 90-day horizons

Do not compare regions using incomplete simulator coverage.

---

## 5. Pokégear — global tools and utilities

Pokégear is a global utility hub, not another region. It should be accessible from anywhere and should make common tools faster to reach than opening a separate app.

### Utility categories

| Category | Utilities |
|---|---|
| Time | Clock, alarms, timer, stopwatch, countdown |
| Navigation | Compass, coordinates, distance, map shortcuts |
| Calculations | Calculator, percentage calculator, unit conversion |
| Randomness | Dice, coin toss, random picker, random number |
| Environment | Weather, UV, sunrise and sunset |
| Device | Flashlight, battery information, device shortcuts |
| Focus | Pomodoro timer, breathing timer, ambient sounds |
| Reference | Measurement conversion, date calculator, timezone converter |

### Pokémon presentation

Pokémon can act as utility mascots and visual skins, but must not unlock essential functions. A user should be able to use the flashlight without owning Ampharos, for example.

Previously considered mascot mappings:
- Flashlight: Ampharos, Chinchou or Lanturn; Volbeat as an alternative
- Compass: Nosepass, Probopass or Magnemite line
- Clock/alarm: Hoothoot or Noctowl
- Dice/random picker: Clefairy
- Coin toss: Meowth or Gimmighoul
- Weather: Castform
- Breathing/calm break: Meditite
- Sleep sounds: Jigglypuff

These are presentation choices, not functional requirements.

### Contextual integration

| Utility | Useful in |
|---|---|
| Timer | Plan, Grow, Health |
| Stopwatch | Health, Grow |
| Calculator | Home, Finance |
| Unit converter | Home, Grow, Health |
| Weather | Explore, Health, Home |
| Compass | Explore |
| Breathing timer | Care |
| Sleep sounds | Care |
| Random picker | Plan, Create-related activities, Explore |
| Clock and alarms | All regions |

### Utility ownership rules

- Utilities are independent tools and do not own regional progression.
- A timer launched from a Plan focus session can record a Plan focus event.
- A timer launched from a Grow study session can record a Grow study event.
- A timer opened directly in Pokégear does not automatically grant progress.
- The calling region owns the activity event; Pokégear provides the tool.

---

## 6. Android companion ecosystem

PokéLife should extend beyond the app into the Android experience. The app, widgets, live wallpaper, notifications, shortcuts and wearable integrations should use the same shared data and progression systems.

### Product roles

| Surface | Primary purpose |
|---|---|
| App | Full activities, records, regions and progression |
| Launcher integration | App identity, shortcuts and quick access |
| Widgets | Glanceable information and quick actions |
| Live wallpaper | Persistent Pokémon environment |
| Notifications | Important, contextual updates |
| Quick Settings | Instant access to selected tools/actions |
| Wearables | Lightweight interactions and supported health signals |
| Sharing | Trainer cards, collection snapshots and milestones |

### 6.1 Launcher integration

**Initial direction: support existing launchers rather than build a custom launcher.**

A custom launcher is a substantial product in its own right and duplicates functionality already provided by One UI, Smart Launcher and other Android launchers. Treat a dedicated PokéLife launcher as an optional future experience, not a requirement.

Initial features:
- Adaptive app icon
- Themed and monochrome icon variants
- App shortcuts
- Shortcuts for favorite regions
- Shortcuts for Pokégear utilities
- Quick actions such as start focus, log activity or view party
- Search integration for Pokémon, utilities and activities where supported

### 6.2 Widgets

Widgets should be genuinely useful, not merely decorative. Offer compact and expanded sizes where practical.

**Trainer dashboard**
- Trainer level and experience
- Regional companion Pokémon
- Daily activity summary
- Egg or reward progress
- Quick access to regions

**Region widgets**
- Plan: next tasks and focus timer
- Care: routine shortcuts and calm break
- Grow: current learning goal
- Home: meal plan and household checklist
- Health: activity and workout summary
- Finance: budget snapshot and upcoming bills
- Connect: upcoming occasions
- Explore: saved places and next trip

**Pokémon widgets**
- Current regional party
- Favorite Pokémon
- Egg progress
- Optional affection or interaction status
- Pokédex completion
- Random Pokémon of the day

**Pokégear widgets**
- Clock and date
- Timer and stopwatch
- Weather
- Calculator
- Random picker
- Flashlight shortcut

Widget configuration should let users select the region, Pokémon and information displayed. Do not force a single global buddy or fixed dashboard layout.

### 6.3 Live wallpaper — PokéLife Habitat

The live wallpaper could become a defining part of PokéLife: a small environment with Pokémon living in it, reflecting selected preferences and game events.

**Core concept:** a persistent habitat with configurable environments, companions and ambient behavior.

Possible behaviors:
- Time-of-day lighting, from morning to night
- Weather effects such as rain, clouds and sunlight
- Pokémon idle animations and interactions
- Small environmental changes based on regional milestones
- Optional tap interaction to animate a Pokémon or open its details
- Egg hatch, evolution and milestone celebrations

**Environment themes**
- Plan: town square, study room or training grounds
- Care: peaceful meadow, pond or garden
- Grow: academy, library or laboratory
- Home: cottage, kitchen or backyard
- Health: forest trail, beach or training field
- Finance: market square or treasury
- Connect: village plaza or community garden
- Explore: campsite, mountain trail or seaside

Use a shared habitat engine with different environment assets and themes rather than maintaining eight unrelated wallpaper implementations.

**Customization**
- Select a region or shared habitat
- Choose one or more Pokémon to display
- Configure time-of-day behavior
- Toggle weather and ambient animations
- Choose pixel art, illustrated or minimal visual styles
- Adjust animation intensity and battery-saving mode

**Performance requirements**
- Use lightweight animations.
- Avoid continuously polling app data or running a full game simulation in the background.
- Update in response to meaningful state changes and lifecycle events.
- Provide reduced-motion and low-battery modes.
- Allow static wallpaper fallback.

### 6.4 Notifications

Notifications should communicate useful events, not create pressure to keep engaging.

Possible events:
- Egg hatched
- New visitor arrived
- Pokémon is ready to evolve
- Grow milestone reached
- Upcoming bill
- Scheduled activity approaching
- Saved trip is coming up

Requirements:
- Notification channels by region and event type
- User-configurable controls
- Quiet hours and sensible grouping
- No streak-loss warnings
- No guilt-based reminders
- No excessive engagement prompts
- Sensitive details hidden by default on the lock screen

### 6.5 Quick Settings and app shortcuts

Potential Android Quick Settings tiles:
- Start/stop focus timer
- Start breathing timer
- Open Pokégear
- Log a quick activity
- Open a selected utility

App shortcuts:
- Open a selected region
- Start focus
- Start timer
- Log a quick activity
- View party
- Open Pokégear

Actions should be configurable and should not require opening the full app when the action can safely be completed directly.

### 6.6 Lock screen

Where supported by the Android version and device:
- Trainer clock and date
- Favorite Pokémon
- Next calendar event
- Timer progress
- Small activity summary

Do not assume all Android launchers or lock screens support the same widget capabilities. Provide fallbacks.

### 6.7 Wearables

Potential experiences:
- Pokémon companion display
- Activity and workout summaries
- Timer and stopwatch
- Quick Care actions
- Milestone notifications

For Huawei Watch Fit 4 Pro, treat integration as a separate technical investigation. It uses HarmonyOS rather than Wear OS, so Wear OS APIs and watch-face tooling should not be assumed to work.

Investigate:
- Supported Huawei Health data access
- Historical health-data availability
- Health Connect compatibility and synchronization
- Supported Huawei watch-side development options
- Whether third-party watch faces or companion experiences can access the required data
- Free or low-maintenance integration paths

Keep manual logging and phone-side functionality available if direct watch integration is not supported.

### 6.8 Sharing

Optional shareable outputs:
- Trainer card
- Favorite Pokémon or party card
- Collection milestones
- Regional progress snapshot
- Egg hatch or evolution celebration
- Travel/exploration postcard

Sharing should be user-initiated. Do not expose private health, finance, Care or relationship data.

---

## 7. Companion settings

Create one **PokéLife Companion** settings area to configure the external experiences.

### Appearance
- App icon and icon variants
- Theme and colors
- Visual style

### Widgets
- Enabled widgets
- Region and Pokémon selection
- Displayed information
- Compact/expanded layout preferences

### Wallpaper
- Habitat and environment
- Companions
- Weather
- Animation intensity
- Battery-saving mode

### Notifications
- Region and event preferences
- Quiet hours
- Lock-screen privacy

### Shortcuts
- Favorite utilities
- Favorite regions
- Quick actions

### Battery and performance
- Update frequency
- Animation quality
- Background behavior
- Reduced-motion settings

### Privacy
- What information can appear outside the app
- Sensitive-data display controls
- Connected services and permissions

---

## 8. Technical architecture

Use one shared data and progression foundation. External Android surfaces should read from the same repositories and state as the main app.

### Logical layers

1. **App shell**
   - Navigation
   - Search
   - Pokégear entry point
   - Notifications
   - Companion settings

2. **Regions**
   - Independent activities and records
   - Region-specific progression
   - Regional parties and leads
   - Environments and acquisition presentation

3. **Utilities**
   - Independent tools
   - Reusable across regions
   - Context-aware activity handoff

4. **Shared Pokémon core**
   - Trainer identity
   - Collection and PC
   - Pokédex/Form Dex/Shiny Dex
   - Bag and items
   - Evolution
   - Reward ledger
   - Cross-region milestones

5. **Android companion layer**
   - Widgets
   - Wallpaper service
   - Notifications
   - App shortcuts
   - Quick Settings tiles
   - Wearable integrations

### Suggested Android implementation

| Component | Suggested technology |
|---|---|
| Main app and region UI | Jetpack Compose |
| Widgets | Jetpack Glance; RemoteViews where needed |
| Live wallpaper | Android `WallpaperService` |
| Notifications | Android notification channels |
| Quick Settings | `TileService` |
| App shortcuts | Android App Shortcuts |
| Background work | WorkManager |
| Preferences | DataStore |
| Shared state | Existing PokéLife database and repositories |
| Wearables | Platform-specific integration after feasibility validation |

These are implementation directions, not commitments. Confirm compatibility with the project's actual Kotlin/Compose architecture and minimum Android version before implementation.

### Core engineering rules

- Widgets and wallpapers read PokéLife state; they do not own game logic.
- Progression and reward calculations happen in the core application/domain layer.
- External surfaces should not independently award Pokémon or items.
- Use idempotent event processing to prevent duplicate rewards.
- Keep each region's data ownership explicit.
- Keep sensitive region data out of generic game events.
- Provide sensible offline behavior.
- Avoid excessive background polling.
- External surfaces should degrade gracefully if permissions or integrations are unavailable.

---

## 9. Navigation and information architecture

Keep the main app navigation focused. Pokégear is a global action, not another region.

A possible high-level structure:

- **Home:** shared trainer dashboard and overview of regions
- **Regions:** access to all available life domains
- **Pokémon:** collection, PC, Pokédex and parties
- **Me:** trainer profile, settings and progression

Pokégear should be accessible from the top bar or another persistent global entry point, with shortcuts on Home and Android surfaces.

The Home tab and the proposed Home Region are different concepts:
- **Home tab:** shared trainer dashboard.
- **Home Region:** household-management module.

Do not conflate their names in navigation, documentation or code.

---

## 10. Region admission criteria

Before adding a new region, verify that it has:

1. A distinct life domain.
2. A useful set of activities that can stand on their own.
3. Clear data ownership and privacy boundaries.
4. Its own meaningful progression.
5. A distinct but generous acquisition presentation.
6. A regional identity, environment and companion fantasy.
7. Clear boundaries with existing regions.
8. A reason to exist as a region rather than a feature inside another region.

If a concept does not meet these criteria, implement it as a feature, utility or shared system instead.

## 11. Utility admission criteria

A utility belongs in Pokégear when it:
- Solves a practical problem.
- Is faster or more convenient than opening another app.
- Works independently of regional progression.
- Benefits from Pokémon presentation without being obstructed by it.
- Can be implemented and maintained without excessive background activity.

---

## 12. Suggested development roadmap

This is a strategic sequence, not a fixed release schedule.

### Phase 0 — Stabilize the foundation

- Validate Plan and Care acquisition and reward rates.
- Build a multi-region economy simulator.
- Reconcile product and design documentation.
- Establish region ownership, event contracts and idempotent reward processing.
- Establish privacy boundaries for sensitive regions.
- Define the shared region framework.

### Phase 1 — Android foundations and Pokégear

- Adaptive app icon and themed icons
- App shortcuts
- Basic notification channels
- Shared data access for external surfaces
- Pokégear framework
- Timer and stopwatch
- Calculator and unit converter
- Random picker and coin toss
- Flashlight shortcut
- Favorites and quick access

### Phase 2 — First widgets

- Trainer dashboard
- Plan and Care widgets
- Timer and Pokégear widgets
- Party and favorite Pokémon widgets
- Widget configuration
- Battery and update behavior

### Phase 3 — Grow and Home

- Grow region framework and Research Discoveries
- Home region framework and Household Finds
- Regional environments and parties
- Shared reward-ledger integration
- Regional collection and milestone views

### Phase 4 — Health

- Move, Fitness and Nutrition under one region
- Manual activity and workout logging
- Nutrition and hydration features
- Recovery context
- Supported health-platform investigation and integration

### Phase 5 — PokéLife Habitat

- One initial habitat environment
- Day/night cycle
- Pokémon idle animations
- User-selected companions
- Wallpaper configuration
- Battery and lifecycle optimization

### Phase 6 — Explore and Finance

- Explore maps, discoveries and travel planning
- Manual location logging and optional location integrations
- Finance budgets, bills and goals
- Strong privacy and security boundaries

### Phase 7 — Connect and full companion ecosystem

- Connect region and community features
- Additional region-specific widget packs
- Multiple wallpaper environments
- Quick Settings tiles
- Lock-screen support where available
- Wearable feasibility and integrations
- Optional custom launcher exploration
- Sharing features

---

## 13. Prioritization guidance

### High-value foundations
- Shared region framework
- Reward ledger and event ownership
- Multi-region economy simulator
- Pokégear architecture
- Android widget data access
- Companion settings and privacy controls

### High-value user-facing additions
- Useful timer and utility tools
- Trainer dashboard widget
- Region widgets
- Party/favorite Pokémon widget
- First live wallpaper habitat

### More expensive or uncertain work
- Custom launcher
- Advanced animated wallpaper environments
- Huawei watch-face integration
- Continuous location-based experiences
- Third-party financial or health account integrations

Do not let visually exciting companion features delay the foundations needed to make them reliable.

---

## 14. Success criteria

Evaluate the ecosystem on usefulness and sustainability, not only engagement.

**Regions**
- Each region is useful without rewards.
- Regional progression feels distinct.
- Ordinary use yields satisfying rewards.
- Missed days do not create debt or decay.
- Duplicate rewards from cross-region actions are prevented.

**Shared Pokémon world**
- All regional acquisitions enter one collection.
- Regional parties and leads remain independent.
- Provenance is retained.
- Collection and progression remain consistent across the app and external surfaces.

**Pokégear**
- Common utilities are fast to open and use.
- Essential functionality is not gated by Pokémon ownership.
- Utilities work without unnecessary background activity.

**Android companion**
- Widgets show accurate, current-enough information.
- Wallpaper is visually appealing without excessive battery use.
- Notifications are useful and controllable.
- External surfaces respect privacy.
- Launcher integration works without requiring a custom launcher.

**Privacy**
- Sensitive records remain in their owning region.
- Finance, Health, Care and Connect do not leak private information into notifications, widgets, wallpaper or shared game events.
- Integrations are opt-in and have clear permission boundaries.

---

## 15. Overall product direction

PokéLife should become a connected ecosystem with:

- **Eight life regions:** Plan, Care, Grow, Home, Health, Finance, Connect and Explore.
- **One shared Pokémon world:** one trainer, one collection, shared inventory and global progression, with independent regional parties.
- **One global utility hub:** Pokégear.
- **One Android companion layer:** launcher integration, widgets, live wallpaper, notifications, shortcuts and wearables.

The recommended approach is to build the shared foundations once and add regions and companion experiences incrementally. The result should feel like a useful life platform inhabited by Pokémon—not a collection of disconnected trackers, nor a game that demands constant attention.

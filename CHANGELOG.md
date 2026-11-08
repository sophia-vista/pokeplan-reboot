# Changelog

Versions are `MAJOR.MINOR.PATCH` ([semantic versioning](https://semver.org)), kept in the `VERSION` file. While the app is
below 1.0, a new feature bumps the minor number and a fix bumps the patch number. Bump `VERSION` in the pull request that
ships something; the first merge with a new number publishes that version's own GitHub release (the rolling "Latest build"
release always has the newest merge).

## 0.148.0

- **PLAN's and CARE's rewards settle under their own ledger event ids** (REP-014, `DATA_AND_MIGRATIONS.md` section 7.3.0 step 2). A finished task (`plan:task:<id>:complete`), a recurring task's day, a step, a milestone, a weekly review, a mini-quest, a welcome-back gift, a focus session or Magic Guard egg (`plan:focus:<egg id>`), a Care moment (`care:<kind>:<day>:<count>`), a befriended visitor and a Shop purchase now leave a `LedgerNote` like HEAL, MINT, HOME and LINK already did, so the database ledger says why the balance and the Bag moved instead of one net change per save. The notes hold only opaque ids and amounts, never a task title. No number changed (`RewardRules.VERSION` stays 20). Tested on the JVM (`PlanCareLedgerNotesTest`).

## 0.147.0

- **23 more Pokémon get their Mystery Dungeon sprite.** An audit of the 64 species that had none found that SpriteCollab's current files hold sprites for 26 of them (its status list is behind): Jellicent, Stunfisk, Aromatisse, Toucannon, Rolycoly, Sirfetch'd, Milcery, Stonjourner, Cufant, Copperajah, Arctovish, Tarountula, Dolliv, Grafaiai, Toedscool, Bombirdier, Orthworm, Iron Treads, Ting-Lu, Gouging Fire, Raging Bolt, Pecharunt and Miraidon's Low Power Mode are in (`tools/sprites/fetch_pmd_new.py`, artists in the credits). Five of them (Rolycoly, Milcery, Cufant, Tarountula, Bombirdier) have the full set of animations.
- **Audit documents** in `docs/ASSETS/`: every one of the 64 species with its status, sources and licences, a source catalogue, the gaps, and a machine-readable manifest (`tools/sprites/pmd_audit.py`). Eight more species exist only in forks or in Pokémon Auto Chess and are marked permission pending (not in the app); 30 have portraits only.

## 0.146.0

Every module opens on its lead, and the rounded parts that were left are pixel parts. No reward number changes.

- **A lead scene on every module page** (`ModuleLeadStage`, the Care meadow's window): the module's lead idles in the module's own scene, a tap pets it, its level and progress sit small in the corner, and it celebrates what its module paid (`GameStore.moduleEvent`: a finished task or hatch for Plan, Field Research for Heal, Ledger Research for Mint, Habitat Discovery for Home, Postcard Trail for Link). World shows the lead of the team out exploring. Nothing shows until the module has a lead; the global buddy stays on Home.
- **Pixel audit.** Picture windows (`Stage`, `SceneStage`: Calm break, hatch and visitor scenes, Pokédex, Focus), the background picker, the whole launcher (search field, category chips, rows, sheets) and the Gear unit drop-down (now a pixel list in a dialogue frame, not Material's menu) use stair corners. Colour swatches, the Menu badge, calendar day markers and the dots on lists are pixel squares, and the breathing circle, the compass and level, the Ranger ring and the World map markers are drawn as pixel discs (`drawPixelDisc`, `drawPixelRing`). The buddy's progress bar and its berry button lost their round shapes. Unused Material imports are gone.

## 0.145.0

Link, the connection module, built manual first (REP-014). Stacked on the cross-module links of 0.144.0.

- **Link is a module** (Modules → Link, blue; `link-core`, `LinkStore`, `screens/Link.kt`). **People** (a name is enough; phone, email, organization, role, address, a relationship word and tags are optional; a directory sorted by name or by what changed last, a Pinned filter that is only a shortcut; a profile with Overview, Notes and Follow-ups; possible duplicates are suggested by name, phone digits or email and never merged; deleting a person asks whether their dates and follow-ups stay), **dates** (birthday, anniversary, milestone or custom; month and day, a year only when known, every year or once; "turns N" when the year is known; **29 February is observed on 28 February in a year without one**), **follow-ups** (a title is enough; Open and History; done, cancelled, reopened; a late one is only "Due since", no warning), an **Overview** with only what is due or near, and one **search** over everything. No contact is ever contacted, and no screen shows a score, a streak, a frequency or a "last contacted".
- **Postcard Trail** (rules version 20): a follow-up you finish pays 2 Rare Candies, friendship for Link's lead and a trainer point, and adds a point to the track; **three finished follow-ups find a Postcard Egg** from the Link Region into Link's party. At most 3 a day; one follow-up pays once however often it is reopened and finished again; adding people, notes, dates, tags, favourites, editing, rescheduling, cancelling and reopening never pay. On by default, off in Settings without losing anything. The event is `LinkEvent` (id, module, opaque reference, kind, time, policy): no name, number, category, tag, note, date or follow-up title; tests prove none of them reaches the shared save, the reward log, the event or the shared tables.
- Link's party (lead, perks that change only Postcard Trail), a first-run postcard tutorial, and a scene of its own for the new Pokémon (delivered along the route). The Link Region is released (a Pokédex tab). `link_*` tables (schema 10), in the daily backup and the backup file; export and erase by kind. A follow-up can be made a Plan task (the conversion of 0.144.0): PLAN then owns it, Link shows "In Plan" with no tick and never pays for it. Not built: reminders, importing device contacts, widgets.
- **A follow-up handed to Plan stays Plan's for good.** It is marked `converted` the moment it becomes a task and the mark is never cleared, so Link pays nothing for it even if the task is unlinked or deleted afterwards. While the task exists Link refuses to finish or cancel the follow-up (one real action, one completion); a task finished in Plan is mirrored as done in Link without a reward. Link's payout also leaves a per-event ledger note (`link:followup:<id>`) like Heal, Mint and Home.

## 0.144.0

Cross-module links, Home reminders, and real reward events in the ledger (REP-014). No reward number changes (`RewardRules.VERSION` stays 19).

- **A PLAN task can be linked to a HOME chore** (Home → a chore → "Show in Plan"; PRODUCT_BIBLE.md section 4, HOME.md 2.1). The task is a projection of the chore's current occurrence: its words and day follow Home, a repeating chore's task moves on to the next occurrence, and a task marked "from Home" is completed *by Home*: ticking it in Plan runs Home's own completion, Home pays Habitat Discovery once, and Plan pays nothing (no task reward, no steps reward, no activity day). Reopen and "won't do" in Plan are Home's undo and skip. "Remove from Plan" removes only the projection; deleting or pausing the chore takes it along (restorable), and a task cannot be made to repeat on its own. Without Home's store (a widget before Home is known) a linked task is left exactly as it is.
- **A LINK follow-up can be converted into a PLAN task** (`GameStore.convertFollowUp`): from then on PLAN owns the task, its completion and its reward, once, by Plan's rules; the same follow-up converts to the same task; LINK reads its status with `linkedTask`. This is Plan's half of LINK.md 2.4; the LINK module itself is not built.
- **Home reminders** through the shared scheduler (`HomeReminders`, `HomeReminderAdapter`, `UpkeepReminders`; HOME.md section 6). **Off until turned on** in Home → Settings, and then each chore, maintenance schedule, supply (with a threshold) and warranty (with an end date) is switched on by itself, so there is never a default reminder. One reminder each at a chosen hour (a chore on its day; maintenance 0, 1, 3 or 7 days before; a warranty a week to two months before; a low supply once), never repeated, nothing for what is overdue, and a done, skipped, paused, archived, edited-away or deleted source stops asking when the alarm goes off. The lock screen says only the kind ("A chore is due"); the name shows after unlocking and only if the player allowed it; never a quantity, serial or note. A chore's notification has "Done" (Home's own completion) and every kind has "Tomorrow"; delivery, snooze and dismiss pay nothing.
- **Real events in the ledger** (the first typed-repository step of DATA_AND_MIGRATIONS.md 7.3.0): a HEAL, MINT or HOME reward now leaves a `LedgerNote` with the event's own id (`home:chore:<id>:<day>`), module, opaque reference and kind, and the database settles it as that event (once; the id is the idempotency key) instead of folding it into "net change of one save". Whatever else differs between the save and the tables is still the net change, so the balance and the Bag always equal the save. Holds no household, health or money word.
- Tests: `TaskLinksTest` (13), `HomeRemindersTest` (7), two ledger tests in `PokemonTablesTest`.

## 0.143.0

Home, the household module, built manual first (REP-014).

- **Home is a module** (Modules → Home, green; `home-core`, `HomeStore`, `screens/Household.kt`). **Chores** (one time, daily, weekly on chosen weekdays, monthly, yearly; done, skipped, put off, undone; pause and archive; history; a repeating chore shows only its latest occurrence, so being away never builds a backlog), **supplies** (exact quantities, unknown is not zero, optional low-stock threshold, restocking list), **belongings, maintenance and warranties** (schedules, done records, cost as plain text), **reference notes**, a search across all of them, and an Overview that shows only what needs attention.
- **Habitat Discovery** (rules version 19): a chore done and a piece of maintenance done pay Rare Candies, friendship for Home's lead and a trainer point, and fill a Habitat Egg meter (5 points); a full meter finds a Habitat Egg from the Home Region into Home's party. One occurrence pays once however often it is undone; at most 5 chores and 2 pieces of maintenance a day. Creating or editing anything never pays. Off in Settings without losing anything. The event holds no household word.
- Home's party (lead, perks that change only Habitat Discovery), a first-run tidy tutorial, and a scene of its own for the new Pokémon (it settles into the habitat). `home_*` tables (schema 9), in the daily backup and bundle; export and erase by kind.

## 0.142.0

Mint follow-ups (REP-014): a backup of Mint that opens on any phone, bill reminders, the optional access gate, and a full edit for accounts (owner: "mint edit accounts"). No reward rule changes (`RewardRules.VERSION` stays 18).

- **Passphrase-sealed backup** (Settings → Backups → "Save a copy that opens on any phone"). Mint in that file is sealed under a passphrase the player types twice (at least 10 characters), through PBKDF2-HMAC-SHA256 (600,000 rounds, a random salt, the count stored in the file) into AES-256-GCM (`PassphraseSealer`, `Pbkdf2Sealer`, `BackupBundle.portable`). Restoring such a file asks for the passphrase; a wrong one changes nothing, and Mint is sealed again with the new phone's own key as it is written. The passphrase is stored nowhere and cannot be recovered; the screen says so. An ordinary copy still opens only on this phone. A backup whose Mint was sealed by another phone's key cannot be made portable here and says why. The stretch runs off the main thread.
- **Bill reminders** through the shared scheduler (`MintReminders`, `MintReminderAdapter`; MINT.md section 12). **Off until turned on** in Mint → Settings; then one reminder for each bill occurrence, some days before (the day, 1, 2, 3 or 7) at a chosen hour, held by the quiet hours and focus sessions like every other module's. A late bill is never nagged daily, a paid, skipped, paused or deleted one stops asking, and a bill can switch itself off. The notification says only "A bill is coming up" (the bill's name only if the player allows it, after unlocking; never an amount, balance, account or merchant), opens Mint, and offers "Tomorrow", never "Paid". The shared tables hold only the bill's own opaque id and its due day.
- **Access gate** (Mint → Settings → Privacy → "Ask for my screen lock"): the phone's own confirm-credential screen (PIN, pattern, password or biometrics, `KeyguardManager`, no new library) in front of the Mint screens, asked again after the app or Mint has been left for 30 seconds. A door only: no key or password is made, nothing is encrypted with it, so it cannot lock anyone out of their records. Not offered where the phone has no screen lock, and not on desktop or web.
- **Edit an account**: Edit now changes the name, type (within asset or liability), bank or provider, group, last digits and the opening balance, and the currency while the account has no records. The balance is derived, so correcting the opening balance moves it by the difference and rewrites no record.
- **Fixes (owner testing).** A new Pokémon no longer takes the lead of a Care, Heal or Mint party whose lead was never chosen (the implicit first member kept leading until an egg arrived, then the newcomer took over); a sprite that stays on screen while its Pokémon evolves (the buddy, the detail page) no longer goes blank (it kept the old form's sheet name); the Party page of the Pokémon tab shows one region at a time behind tabs, in the six-place grid, with perks folded away (it was four long lists); expedition and camp tile boxes start their tiles at the frame's fill (a dark strip over the top corners), show one more row so a bay's rim closes before the veil, and draw the missing inner corner where a bay meets the wall (the rim had a notch).
- **Wrong pictures after a change in place** (owner: "map wrong/buggy", a Rock Tunnel card in the Sky Pillar's tiles and type plates). Every image holder that loads asynchronously (`produceState(cache[key], key) { if (value == null) ... }`: tile atlases, scenes, backgrounds, status icons, weather, World art, Sprites) kept the previous key's image when its key changed under the same composable (a layer switch, a reordered list), because the producer only loaded when nothing was held. They now start from what is cached for the new key. A tile box changed in place is tested to draw what a fresh one draws.
- **Sound effects.** Evolution plays the opening of the games' own evolution music (6.5 s, `evolution`) for the glow and the unlock chime at the end, not the Pokémon Center healing jingle; a Pokémon arriving (hatch, Care, Heal and Mint arrival scenes, a befriended visitor, a World recruit) or joining a party plays Red Rescue Team's own "monster joins" sound (`recruit`, id 206), leaving a party the drop sound; sending a team out and recalling it play the stairs down and up (340, 341); training a World skill and practising a Care skill play "learned a move" (312); opening a module from the Modules list plays a door; logging in Heal plays the heal chime; paying a bill and finishing a reconcile or a review in Mint play the money and "got something" sounds. The Red Rescue Team clips are renders and **not yet listened to**: `tools/sprites/pick_pmd_sfx.py` says which id each is and why, and is where to swap one. `SfxAssetsTest` checks every `Sfx` has its file.
- Tests: `PortableBackupTest` (6), `MintRemindersTest` (7), an account-edit test, and screenshots of the lock card, the settings, the bills with reminders and the account edit form.

## 0.141.0

Mint, the finance module, built manual first (REP-014), and a scene of its own for every way a new Pokémon arrives (owner: "all are egg, that was made for Plan's focus eggs: reuse it, but not too much, whatever makes sense for the module").

- **Mint is a module** (Modules → Mint, ochre; `mint-core`, `MintStore`, `screens/Mint.kt`). **Accounts** (bank, e-wallet, digital bank, cash, credit card, loan, other asset or liability; archive, close, reopen, delete only with no history), **activity** (expense, income, transfer, refund, fee, balance adjustment; pending, posted, excluded, voided; search and filters), **budgets** (monthly or weekly, overall or one category, what is left and what is over, never a failure), **bills** (one time, weekly, monthly, yearly; month-end and leap-day dates exact; paid, part paid, skipped and undo; marking paid creates no transaction), and a **Review** page with this month's money in and out, spending by category and the **reconcile** and **weekly review** actions. Overview shows net position per currency, available money with its basis, and the next bills.
- **Exact money.** Amounts are whole minor units of one currency (centavos for PHP), parsed and shown without floating point; currencies are never added together; a transfer is one record with two legs, so it can never be saved one-sided; a card purchase is one expense and paying the card is a transfer, not a second expense; the balance is derived from the opening balance and posted records, never stored twice.
- **Ledger Research** (rules version 18): only organising pays, never money moving. A reconciled account (once per account a day, 2 a day) and a finished weekly review (once a week) pay Rare Candies, friendship for Mint's lead and a trainer point, and fill Ledger Research's Egg meter; a full meter finds a Ledger Egg into Mint's party. On by default, off in Settings without losing anything. The event is `MintEvent` (id, module, opaque reference, kind, time): no amount, currency, account, merchant, category or note; tests prove none of them reaches the shared save, the ledger or the event.
- **Privacy.** Hide amounts is one switch for the whole module; the Modules card shows no figure; export by kind (amounts are minor units with their currency) and erase by kind or all; no notification or widget reads Mint. **Mint is encrypted.** Every record and setting is sealed with AES-256-GCM under a key held in the Android Keystore; the daily backup, a backup file and anything kept aside carry Mint only sealed, and the stored rows show no amount, name or date. Cost: a backup of Mint opens only on a phone that has the key (a restore elsewhere keeps it sealed and aside, nothing is deleted); Mint's Export is the readable way to move records. Not built and not offered: bank or e-wallet connections, statement import, bill reminders, splits, rollover, savings targets, foreign-currency transfers, access protection, and a passphrase-sealed portable backup (`evidence/REP-014-mint.md`).
- **Every acquisition method starts on:** Heal's Field Research is now on by default (it was off until turned on; a save that already stored the old default keeps it), like Ledger Research.
- **Mint's own tables** (schema 8, `mint_*`, `MintTables`; in the daily backup and the backup file; a backup made before Mint leaves Mint alone), the Mint Region is released (about 65 to 75 hatchable lines, patrons Diancie and Melmetal), Mint's party of six with its own lead and perks (Candy finder: Steel, Rock, Ground, Normal, Electric, Psychic; Friendship gain: Ghost, Dark, Poison, Fire, Fairy, Dragon), and a 5-second tally tutorial that gives one Mint Pokémon into Mint's party.
- **Arrival scenes by module.** Plan's focus and task eggs and a visitor's gift keep the egg. A **Care** friend now wanders in over the meadow and settles; a **Heal** find runs in along the route; a **Mint** entry is catalogued in the cave (a Star Piece glints, then the Pokémon rises from the floor). The words follow ("A new friend wandered over", "Field Research found", "Ledger Research catalogued"), on Home's Recently list, in the reward box and on the Pokémon's origin line ("Arrived through Care"). Reduce motion goes straight to the Pokémon.

## 0.140.0

- **Real Rescue Team art from the game's own files.** 245 maps (the 57 Friend Areas, towns, team bases and more) and the 64 dungeon tilesets of Pokémon Mystery Dungeon: Red Rescue Team, decoded by `tools/sprites/fetch_rescue_maps.py` (from pret/pmd-red's committed map data) and `tools/tiles/extract_rescue_tiles.py` (from the owner's ROM, which is not in the repo). Sources and licences in `docs/assets.md`.
- **Explorers of Sky too.** 440 maps (`assets/sky-maps`, not used yet) and 143 dungeon tilesets (`assets/tiles/ex-NNN`) from the owner's Explorers of Sky ROM, by `tools/sprites/extract_sky_maps.py` and `tools/tiles/extract_sky_tiles.py`. Their sound (DSE, rendered by `tools/sprites/extract_sky_audio.py`, an approximation not yet listened to) is in `assets/sky-sfx`: 11 system effects, 14 jingles and 27 short music files named from pret/pmd-sky.
- **World makes sounds.** A claimed job plays the Explorers of Sky mission-success jingle; building a facility, claiming research, buying a candy, brewing, swapping and training use existing game clips.
- **Backgrounds and towns use them.** Settings → Look → Background now lists the Rescue Team dungeon towns and the 57 Friend Areas in groups; the launcher's town picker gains the Rescue Team dungeons.
- **World's Base is no longer one long list.** It opens on the team base of your first partner, as big as the width allows, growing with the Camp, then your rank and a menu: Facilities, Research, Café, Kecleon Shop, Swap Shop, Dojo. Each opens its own page with a banner from the Square or the Dojo.
- **World dungeons without a sheet of their own** (all of Kanto, Johto, Hoenn and Sinnoh) borrow the Rescue Team tileset closest to their look instead of the World Abyss.
- **Sound effects, with their triggers.** 496 Red Rescue Team sounds rendered from the ROM's sound data (`assets/pmd-sfx`, approximate and not yet listened to, not used by the app) and `triggers.tsv`, which maps each id to the game events that play it from pret/pmd-red's source. The audit found `level-up` was mapped to the dungeon wind sound; it now shares the rank-up clip.

## 0.139.0

Species lists of every module region, curated together (owner: "curate all modules species list, as much as possible, no dupes, keep it relevant to the theme, follow mainline regional dex patterns, module guardians last"; Plan and Care may be reworked).

- **Every species is in exactly one module region.** All 1025 species are split over six lists in `tools/species/regions/`: Plan (179), Care (179), Heal (179), Mint (159), Home (171) and Link (158), each about 65 to 75 hatchable lines with the 12 Super Rare lines two per region. The generator and `HatchRulesTest` refuse a species in two lists, a region without a hatchable line of every rarity, and a list that does not end with its patrons.
- **Themes.** Plan: everyday route Pokémon, busy bug workers, clever Psychic thinkers, the Unova and Paldea starters. Care: soft Fairy, Grass, Water and Normal, with the Kanto starters. Heal: Fighting, Fire, Water swimmers and fast runners, with the Hoenn and Galar starters. Mint: Steel, Rock, Ground, fossils, gems and hoarders, with the Sinnoh starters. Home: the kitchen, garden, hearth, fridge, closet and pets, with the Johto starters. Link: postal birds, loyal dogs, pairs, singers, gift-givers and Eevee, with the Kalos starters.
- **Mainline dex order.** Each list starts with its starters, then the lines by rarity (Common, Uncommon, Rare, Super Rare) in National Dex order with every stage next to its pre-evolution, then the ultra beasts, paradox, legendary and mythical species, and the **patrons last**: Uxie, Mesprit and Azelf (Plan), Cresselia and Shaymin (Care), Raikou, Entei and Suicune (Heal), Diancie and Melmetal (Mint), Tapu Bulu and Ogerpon (Home), Latias and Latios (Link).
- **Mint and Link are regions now** (`Region.MINT`, `Region.LINK`, unreleased like Home until their modules ship, so their eggs still come from the Plan Region).
- **Moved out of Plan, Care and Heal:** Plan keeps no starter from Kanto to Sinnoh (its birds Taillow, Wingull, Starly and the Pichu line went to Link, the Swords of Justice and Keldeo to Heal, Hoopa, Mew, Phione and Manaphy to Link), Care lost Lotad, Lapras and Kangaskhan to Link and took Cubone's, Duskull's and Cetoddle's comfort lines, and Heal's other starters went to Mint, Home and Link. A Pokémon you already own stays yours; only where new eggs of a line hatch changes.

## 0.138.0

From phone testing of 0.137.0.

- **Plan's card on Home shows Plan's own lead** (the first of its party), not the buddy; with an empty party it says "No lead yet". The buddy stays on Home's stage.
- **Back is a pixel chevron and the label in the dialogue font**, on one centre line; the old "‹ Back" text fell back to another font and sat high in its key. `TextCentreTest` checks it.
- **Pokégear is called Gear** in the Modules menu, its header, the launcher and its messages.

## 0.137.0

Design pass, from the owner's phone testing: consistency, pixel parts and fewer words.

- **Module colours run down a quiet rainbow.** The Modules menu is Pokégear (rose) first and World (violet) last, with Plan (orange), Mint (ochre), Home (olive), Care (green), Heal (teal) and Link (blue) between; Mint, Home and Link are dimmed placeholder cards until they are built. The header bar of Home, Modules, Pokémon and Me is a neutral slate instead of red. Inside a module every card, frame, lead marker and main button wears its one colour (no yellow, red or blue tiles in Plan's menu or the Planner). The table is in `DESIGN_SYSTEM.md` 5.1.
- **The buddy lives on Home** (in a scene of its own type) and no longer on Plan's page; the PC list shows only Pokémon that are in no other place, so the buddy is not in the PC.
- **Pixel parts, no rounding.** Text fields (`PixelField`), progress and EXP bars, the on/off toggle, the volume sliders, cards, picture windows, menu discs and the header bar have stair corners of whole art pixels; Material's rounded field, switch and slider are gone. The text in a field is centred in its 48 dp, and a field beside keys shares a centre line (Pokégear's currency rows, Planner's add row, timers, dice).
- **Pokégear and World take a whole row each** in the Modules menu; the Planner's tiles are framed cards like the menus' and have no caption line.
- **Rank badges are rounded pixel squares**, not circles.
- **Text is centred by its letters, and it is measured.** The Pokémon DP font sat 2 to 4 dp low in its line box and the dialogue font a pixel high, so labels in keys, fields and badges were off the centre line. Every text style is lifted to its centre, the dialogue font moved down a pixel, and `TextCentreTest` renders them and fails if the gap above the letters and below differ by more than 1 dp.
- **Big sections sit in dialogue boxes**: World's rank and XP strip is a box now.
- **Fewer words.** Removed the captions that said what the screen already shows: the Modules intro and subtitle, "Modules" breadcrumbs under headers, the Pokémon intro, every empty state's explanation (a title and one action remain), reassurance lines in Care, Heal, Lists and Pokégear tools, the long tutorial blurbs, settings details, World's start and map blurbs. `DESIGN_SYSTEM.md` 12A records the rules so they hold next time.
- Carried from the first pass (the same version): one accent per module and neutral chrome, AA contrast on filled keys (`TokenContrastTest`), real art instead of emoji, the buddy asleep in empty states, World on Home, the haul as icons in World's report, unboxed party slots, and screenshot tests that show real screens in both palettes.

## 0.136.0

- **Heal (Modules → Heal), the manual slice.** Log an activity (a type, how long, when it ended; distance and a note if you like), the drinks you have, a night's sleep and an optional body measurement, and set goals of your own that you can pause or put away. Today shows what you logged, and "not logged" where you did not (never a zero, never a score). Nothing is advice, nothing is judged, and a missed day costs nothing. There is no watch, Health Connect or import yet, and Settings says so.
- **Field Research (off until you turn it on).** A session of 10 minutes or more that you log just after doing it, and the day's first drink, can find Pokémon: Rare Candies, a berry, friendship for Heal's lead and a Field Egg from the new Heal Region (64 sporty, outdoor Pokémon) that hatches into Heal's party. Sleep, measurements, goals and every other number never earn anything, older entries never pay, and editing or deleting never pays again. Heal has its own party of six whose perks change only what Field Research pays.
- **First-run walk.** Heal shows a one-time 5-second walk that gives you one Pokémon for Heal's party (no record, no streak, nothing counted); a player who has already logged an activity does not see it.
- **Private by design.** Your records live in their own tables (schema 7): activity, hydration, sleep, measurements and goals each in a family of their own, so one kind can be exported or erased alone (Heal → Settings); erasing never touches your Pokémon, Poké, Bag, Plan or Care. They are in the daily backup and the backup file, and a backup from before Heal leaves them alone. The only thing the rest of the app learns is a stable id, an opaque reference, "activity" or "participation" and the time.
- **Hydration reminders (off).** Up to three times a day if you add them, through the shared reminders: quiet hours and focus sessions apply, the words are always "A moment for a drink?", the lock screen shows nothing more, and there is no "done" button.
- Under the hood (REP-014): `heal-core`, `HealStore`, `HealTables`, `LiveDocuments.moveHealToTables`, `HealEvent`/`RewardEngine.heal`/`healEgg`, `Region.HEAL`, `HealPerks`, `HealReminderAdapter`, `Tutorials.HEAL_WALK`; `RewardRules.VERSION` 17 (`docs/economy.md`); `MODULES/HEAL.md` section 22, `evidence/REP-014-heal.md`. Not run on a device; tables and backups are not encrypted yet.

## 0.135.0

- **Your Pokémon, Bag and Poké now live in their own database tables** (schema 6), the way Plan's and Care's already do. The game works exactly as before: the app still reads and writes one save, and the database splits it into rows and puts it back together, so saving a level-up writes one Pokémon's row instead of the whole collection. The first start with this version moves everything over, checks it reads back equal, keeps the old save whole under an `aside:` copy, and pays nothing; if anything differs, the old save stays the live one and it tries again next start.
- **A Pokémon is in one place, and the database enforces it.** The buddy, each module's party and the Guardian slots are now one table keyed by the Pokémon, so a second place for the same Pokémon cannot be stored; the save is refused if it would also put that Pokémon on a World team or as a Pokégear helper. A Pokémon from World, a Guardian included, arrives in the PC until you assign it.
- **The ledger is the only writer of your Poké and Bag.** Both change only together with a ledger entry in the same transaction (one event per change, never edited or removed, and the balance can always be rebuilt from it). World's copy of the Poké balance is no longer saved at all: the World screen shows the one balance, and an old save's World Poké is read once for the merge.
- Restoring a backup goes through the same checks and split, so an older backup restores correctly.
- Under the hood: `5.sqm` and `databases/6.db`, `PokemonTables`, `LiveDocuments.movePokemonToTables`, the DB check screen has a line for it. No reward number changed (`RewardRules.VERSION` stays 16).

## 0.134.0

- **Each module teaches how it gives Pokémon.** Your starter is your buddy and starts in no party, so Plan and Care each show a one-time tutorial until you have done the real thing: Plan a 5-second focus session, Care a 5-second breath. Each hatches one egg into that module's own party and leads it. They are not focus sessions or calm breaks and leave no history, streak or meter, they never pay twice, and nothing needs them.
- **The starter stays the buddy** when an older save had it as both buddy and Plan's lead: the buddy now beats a party and a Pokégear helper when sorting out a Pokémon in two places (Plan's party loses it).
- Cosmetic variation is dropped from egg inheritance (spec and notes).
- Under the hood: `Tutorials`, `GameStore.completeTutorial`, `RewardEngine.tutorialEgg`, `TutorialCard`; `POKEMON_ECOSYSTEM.md` section 10.2.1. No reward rules changed beyond the one-time egg (`RewardRules.VERSION` stays 16).

## 0.133.0

- **One Pokémon, one place.** A Pokémon is now the buddy, or in one module's party, or a slotted Guardian, or on a World team, or a Pokégear helper, or in the PC, and never two. Choosing it for a place takes it from where it was. Your starter is the buddy and starts in no party (the first Pokémon to hatch joins Plan's). A save that had a Pokémon in two places is sorted out once on load: a Guardian slot beats a World team, which beats a party, a Pokégear helper, then the buddy; if your buddy was taken, your best Pokémon in the PC becomes the buddy. Nobody leaves your collection. Settings → "Keep party Pokémon out of World" is gone (it is always so now), and a Pokégear tool shows a helper only when you chose one.
- **Plan has its own tables, and Habits are recurring tasks.** Tasks, lists, events, goals, reviews, focus history, the running focus session and your rest and active days now live in their own tables (schema 5); your save loads and backs up exactly as before. Your habits became recurring tasks with a frequency target ("3 times a week"), with their history, their paid days and their goal links kept (the old list is kept aside) and nothing paid again. The Planner's Habits view is now **Targets**, showing progress in the period first (`2 / 3 this week`); the streak is secondary.
- **Care, finished.** *Meadow*: one fixed garden with your buddy and four visitor spots (tap for a reaction, long press for details). *Care Play*: spend time with your buddy, a visitor or any Pokémon you invite; each has two ways it likes best, found out by trying, and the first time with each Pokémon each day pays a little (Rule version 16). *Reunions*: a friend you made may come back to say hello (takes no place, pays nothing). *Egg inheritance*: an egg a friend leaves may take its nature or a strong stat, and keeps a note of who it came from.
- **Backups you can use (Settings → Backups).** Back up now, save a copy anywhere as one file, restore the daily copy or a file you choose. A file is checked whole first (damaged, cut short, newer, edited: refused, nothing changes), then swapped in one step, and what it replaces is kept aside. A backup is skipped, not half written, when the phone is short of storage, and the app says so.
- Under the hood (REP-008, REP-009, REP-014): `PlanTables`, `LiveDocuments.movePlanToTables`, `RecordRows`, `Places`, `TaskSeries.target`, `Frequency`, `VisitorInheritance`, `CarePlay`/`Reunions`, `BackupBundle`, `LiveBackup.list/restore/bundleOf`, `AndroidBackups`; `RewardRules.VERSION` 16. Evidence: `docs/pokelife/evidence/REP-014-plan-tables.md`.

## 0.132.0

- **Visitors are calmer and kinder (Care).** Visitors now arrive on their own, one a day and sometimes two, whatever you did in Care. Four stand in the meadow and up to six wait in a quiet line that never runs out; nobody is pushed out. You can visit any of them at any time with nothing to do first. Every kind of care warms a visitor straight away and the warmth is never lost, however long you are away: no three-day goodbye and no one visit a day. A visit can be left whenever you like, and a visitor then rests a few hours. Sixteen warmth makes a friend, about two visits. You can send a visitor away on purpose, and ask it back later. Visitors from an older save keep what they had (each warm day counts for a quarter). What a friend pays has not changed.
- **Care's data has its own tables.** Routines, diary, journal, calm breaks, skills and visitors now live in their own tables inside the app's database (schema 4) instead of one block of text, so a save writes only what changed. The daily backup still writes the same `care.json`.
- Under the hood (REP-014): `CareTables`, `LiveDocuments.moveCareToTables` (read back and compared, the old row kept aside), `Visitors.arrivals`, `CareStore.leaveVisit/dismiss/restore`, `MODULES/CARE.md` section 6.8, `evidence/REP-014-care-tables.md`. DB check also tests the Care tables.

## 0.131.0

- **One reminder system for Plan and Care.** Task and routine reminders now share one scheduler: the same quiet hours for both, a reminder that goes off during a focus session waits for its end, and a snooze now survives a restart of the phone. Plan keeps its Done / 10 min / 1 hour buttons and its three gentle repeats; Care keeps Done / In 30 min and never repeats. Old notifications still react to their buttons after the update.
- Under the hood (REP-011): `reminder_hold` table (schema v3), `ReminderCoordinator`, `SharedReminders`; the old alarms are cancelled once, after the new ones are set. DB check also tests the new table.

## 0.130.0

- **PokéLife is fully local.** The cloud is gone: no account, no sign-in, no sync, no friends board or gifts, and no server deciding rewards (they were always computed on the phone first). Settings loses its Account card, Care loses its Settings page (it held only the cloud copy), and Home loses the sync dot. Your save is unchanged; any sign-in tokens it held leave the save the next time it is written.
- Under the hood (REP-012): `supabase/`, `app-core/cloud`, `tools/server`, the deploy workflow, the JavaScript build of the engine and its CI steps are deleted. CI no longer needs PostgreSQL or Node.

## 0.129.0

- **Your save now lives in a database on the phone (Android).** On the first start, `save.json` and `care.json` are copied into the app's database in one step, checked, and from then on the database is the only place the app saves to. Your old files are left exactly as they were and never written again, so they are a copy to go back to. Nothing changes on screen.
- A backup of the save is written once a day (the newest three are kept). If the database file is ever damaged, the app moves it aside whole and restores the newest backup, or falls back to the untouched old files.
- The widgets and reminders use the same database.
- Debug builds: DB check also tests activation, backup and restore.

## 0.128.0

- **Debug builds get a second icon, "DB check"**, that runs the database checks on a throwaway file (SQLite version, foreign keys, WAL mode, rollback, speed, backup copy, reopen) and shows PASS or FAIL for each line. It never touches your save. Release builds do not have it.

## 0.127.0

- Under the hood (REP-011): reminder source adapters for Plan and Care and a router for notification actions. A stale or unknown reminder cannot change anything, snoozing keeps the same reminder, and dismissing or snoozing never finishes a task or routine or pays a reward. Nothing changes on screen yet.

## 0.126.0

- **Care's routine reminders now respect quiet hours.** One global quiet-hours window (default 22:00 to 07:00) holds every module's reminders until it ends; a routine set for 23:00 now goes off the next morning at 07:00. Focus sessions still hold a reminder until they end, and a focus that ends in quiet hours waits for the quiet window's end.
- Under the hood (REP-011, first slice): the shared reminder policy `ReminderPolicy` with `ReminderRequest` (stable ids, opaque source references, no titles or notes stored), `FocusWindow` and the `ReminderSourceAdapter` contract. The Android scheduler, the reminder tables in the database and Plan's move onto it wait for device checks.

## 0.125.0

- **One currency: Poké.** Plan's coins and World's Poké are now a single balance, merged one to one on first open (a save with 120 coins and 35 World Poké opens with 155 Poké). What you earn anywhere is Poké, and the Bag's Rare Candy shop, World's facilities, shop and Dojo all spend the same Poké. Nothing was re-priced and no earnings changed. Both old values are kept in the save (`pokeMigration`) for checking. `RewardRules.VERSION` is 15. The economy simulations were re-run.
- Under the hood: World's own `poke` field is only a working copy of the one balance until World moves onto the shared economy.

## 0.124.0

- **The buddy and each party's lead are now separate.** Your **buddy** is your main companion (home, widgets, story) and only for show: it never changes a reward. **Plan's party has a lead** (the first slot, "Make Plan lead" on a Pokémon's page), and Plan's perks, egg luck and friendship come from that lead and Plan's party; **Care's lead** does the same for Care. A save from before keeps its buddy as Plan's lead, so nothing you earn changes. The buddy can now leave the party, and **Care no longer warms your Plan buddy** when Care's party is empty (put a Pokémon in Care's party to give it a lead). `RewardRules.VERSION` is 14, with no change to any amount.
- Under the hood: the new `:data` module (REP-008) is merged but not used by the app yet; the JSON save is still the only store.
- Known limit: the old Supabase reward function does not know the Plan lead (cloud sign-in is being retired), so a signed-in account's server-checked rewards use Plan's first party member.

## 0.123.0

- **The home layer is gone; World has two optional Mystery Dungeon arcs.** The Mystery Dungeon world is split into **Rescue Team** (Pokémon Square: Tiny Woods, Mt. Steel and the Sky Tower, six chapters, Sky Tower as its Guardian dungeon) and **Explorers** (Treasure Town: Thunderwave Cave, Waterfall Cave, Glacier Palace, Ancient Ruins and the Temporal Tower, eight chapters, Primal Dialga as its Guardian, the Distortion Rift after it). Each is a layer beside Kanto, Johto, Hoenn and Sinnoh. The old six-chapter home story (the temporal saga) is removed; the two arcs' chapters have titles and gates now and their scenes are the next step (`docs/world-story-rewrite.md`).
- **The opening belongs to no layer.** Mew's Unofficial Welcome plays first on a new save (and is listed in the Story archive under "The opening"). A save that watched the old opening counts it as watched; its dungeon clears, rank, Guardian and everything else are kept (the dungeons keep their ids). The missions board opens with chapter 2 of either arc.
- Not in this change: the arcs' own scenes and extra dungeons (Frosty Forest, Beach Cave and the rest), and arc-specific map pictures (both arcs use the stage map for now).

## 0.122.0

- **World opens with Mew's prank.** The first World story is now "Mew's Unofficial Welcome" (seven scenes, from the owner's rewrite brief in `docs/world-story-rewrite.md`): a pink thief, a chase out of town, a short first dungeon, a job that becomes a promise, Braviary on preparing a team, Mew's real test at dusk, and the expedition board. It replaces the old guild-letter chapter 1. At the end Mew asks to join and waits in **Finds** to be recruited, once (replaying the episode never gives a second Mew). The scene ids are now `intro-mew-*`; chapters 2 to 6 of the home story are unchanged for now.
- The story script export (`:world-core:storyScript`) and the rest of the brief (Mythical follow-ups, the Rescue Team and Explorers arcs, the regions) are still to be ported; the brief's status line says which.

## 0.121.0

- **A simpler World landing.** The page now reads top to bottom as: your rank in one quiet strip (badge, progress, Poké), what the teams brought back (shorter: no repeated "every floor" and "already in your Bag" lines), the one card for the region on show (its switch, the chapter, what is worth doing now with its one button, and what the next chapter asks, quietly), the teams out, then the places to go. The separate rank card, story card and "Exploration team" banner are gone; nothing they showed is lost (the story is in the Story archive, the rank on the strip).
## 0.120.0

- **Currency is a set of rows you can type in.** Each currency you watch has its own amount: type in any row (a number or a sum) and every other row is worked out from it. Your home currency is the first row; add a currency by name or code, move one up or remove it. "A rate you were offered" and "What it really costs" now follow the row you are typing in (or the first foreign one). The rates, their age and source, the offline cache and the reference-rate note are unchanged, and so is the saved board.
## 0.119.3

- **Other apps' widgets fit their cells.** On the launcher's widget grid, a widget from another app gets exactly the room its cells give it (before, it was told a width and height from the old small, medium and large sizes, so after a resize it could be clipped, show empty space or lay out for the wrong size) and has no frame of ours around it. In the older list layout nothing changes.

## 0.119.2

From the deep review of 3 November (its P0 story and dungeon items):

- **Story branches play like everything else.** Inside a "if you chose this" line, a walk, a cry, a sound, a shake, a flash, a wait, a fade or an emote now plays (before, only the words did, so a character could jump and an effect was lost). The checker also looks inside branches now: unknown or unshown characters, empty paths, long lines, a choice or End inside a branch, and a branch that leaves someone on or off stage, are reported. A dialogue scene that shows three characters at once is reported too (it draws two).
- **Dungeon floors use the tileset's layout** instead of a repeated four-tile strip: the story walks and the team strips draw a wall along the top, a clear floor, and wall below with bays of ground cut into it (different on each dungeon), with the ground alternates scattered over the floor only where a tileset's alternate really is ground (a few tilesets' are pits or water and are left out).

## 0.119.1

- **Launcher faster.** Coming back to the home screen (Home, or from an app) no longer redraws every app icon: the app list and its icons are kept when nothing was installed, updated or removed. The app list computes its categories, sections and rows once per change instead of on every frame, and an icon's border colour is worked out once instead of each time it scrolls into view.
- **Settings and Close in the app list are icons** (a cog and a cross) instead of words.

## 0.119.0

- **Every chapter is a scene now.** All 26 chapters of the five storylines (Mystery Dungeon, Kanto, Johto, Hoenn, Sinnoh) have an episode of two or more scenes: a conversation, a walk through the chapter's dungeon, and for the big moments a cinematic (Dialga, the three birds, Mewtwo, Lugia, Ho-Oh, Groudon and Kyogre, Rayquaza, Palkia, Giratina). Each region has its own guide, who talks with your buddy (Pelly the Pelipper, Nia the Eevee, Marin the Marill, Cloud the Swablu, Pip the Piplup) and its own villains or elders. The writing is a first pass of mine, made from the chapter summaries; every line is plain text in `world-core/.../story/*Chapters.kt` to rewrite.
- **Engine finished:** cinematic scenes (black bars, big sprites, shake and flash, Reduce motion aware); cries and sound effects as events; **choices** (two or three answers that only set a flag, never a reward or a lock) and lines that follow them; **resume** (leave an episode and it restarts at the next scene, not from the top; a watched episode replays from its start); and **People you have met** in the Story archive, with their sprites.

## 0.118.0

- **World story engine, first slice.** Chapters can now be real scenes. A script of typed events (show, move, say, emote, items, fades) plays as a **dialogue** (the scene and large Mystery Dungeon portraits, the speaker bright), a **stage** (sprites walking with their own animations) or a **dungeon walk** (the team crossing a dungeon's own tiles), with tap to advance, Skip scene, and a typewriter that Reduce motion turns off. **Your buddy is the companion** in every scene, whoever it is. The first episode is written: Mystery Dungeon chapter 1, "A New Explorer" (a letter from Pelly the Pelipper, the square, a first walk through Tiny Woods and back to the Guild). It plays when the chapter opens and from the Story archive; chapters without a script still play their title card. Skipping lands on the scene's end state, and scenes watched are recorded for the archive without any reward. Engine, authoring guide and what is still to build: `docs/world-story-engine.md`.

## 0.117.0

- **Pokégear, the field tools (Phase 2 finished): four more tools** (eighteen in all). **Waypoints:** mark where you are (one location reading when you press the button; it asks for permission the first time and tracks nothing) and see how far and which way each saved place is, with a rename, a copyable map link and delete; places stay on this phone. **Sound meter:** a level in dB (an estimate, with an offset you can tune against a real meter), the quietest, average and loudest, the pitch it hears with its note and a spectrum, only while the tool is open and recording nothing. **QR codes:** make a code from text or a link (drawn in the app, up to 271 bytes, four repair levels; checked against an independent decoder) and scan one with Google's scanner screen (needs Google Play services; it shows the text and never opens a link). **Network check:** how the phone is connected (Wi-Fi, mobile data, metered or not, whether the internet was reached) and a three-request timing test against one small Cloudflare page, with a verdict. The app now declares location and microphone permissions (asked for only when these tools are used) and network state.
- Not built: the document scanner with text recognition (a Google scanning library and its result plumbing, to be tried on a phone).

## 0.116.0

- **World: the Story archive** (a small button on the World landing): every rank reached, every story chapter opened (by layer) and every legendary Pokémon that joined, each with a play button to **watch its cutscene again**. Playing one again changes nothing. Chapters not opened yet are not listed, so nothing is given away. **New scene:** a legendary quest completing (Zapdos, Dialga, Giratina...) now plays a scene of its own (the Pokémon and its cry, what it gives), once, like ranks and chapters; a save from before counts the legends it already has as seen.

## 0.115.0

- **World: the Sinnoh layer** (fifth button on the Map's layer switch, open from the first day): nine dungeons from Diamond, Pearl and Platinum (Eterna Forest, Oreburgh Mine, Wayward Cave, Old Chateau, Iron Island, Mt. Coronet, Victory Road, Spear Pillar and the Distortion World) with wild tables from the Sinnoh dex, a five-chapter story, "The Distortion of Creation", the Pokémon Platinum Town Map as its picture, and eight regional jobs (Gible to rescue), notebook entries, cutscenes, modifiers, hazards and choices like the other layers. **Dialga** (Mt. Coronet cleared three times: Steel-favouring dungeons 10% faster), **Palkia** (Spear Pillar three times: Water-favouring 10% faster) and **Giratina**, Sinnoh's Guardian (the Distortion World once its Guardian has been faced: 3% more treasure) join as Guardians; Plan no longer has quests for them. Primal Dialga, the home Guardian, is a form of its own, so owning it does not stand in for Sinnoh's Dialga.

## 0.114.0

- **World, Phase 3 finished: hazards and choices.** Four **hazards** join the side paths (toxic fumes, a heat vent, a slick of ice, a crackling field): a team member of the right type weathers it for a small bonus; otherwise the team hurries past and loses nothing. **Choices:** about one floor in twenty-five from the third offers a fork or a spring with two ways on, each a reward (Poké or a treasure box; Experience or berries). The team does not wait: you have 12 hours to answer on the landing or the Expeditions screen, and an unanswered choice takes its first way by itself, so nothing is lost and the app never has to be open. At most three wait at once. Each choice is a record of its own, the reward comes from its own id, so the answer is the same whenever it is given, and a settle split any way is unchanged. Optional team roles, equipment and extra skills were left out: their purpose and balance are not clear yet (docs/world-audit.md).

## 0.113.0

- **Cutscenes for a new World rank and a new story chapter.** Reaching a new exploration rank plays a gold flash, the rank in big letters with its Mystery Dungeon badge and what it opens (more teams, new dungeons). A story chapter opening plays its place, number and title with its summary and what it brings. They play once each, one at a time, wherever you are in the app (like a hatch), however the progress came (exploring while away, a job, research); a tap skips ahead, and Reduce motion drops the flash. A save from before this counts what it already reached as seen, so nothing replays.

- **More of the Mystery Dungeon art in World:** the rank card, the cutscene and the return report use the games' rank badges, the Poké coin and the Wonder Boxes (a red one for a plain treasure box, blue for silver, striped for gold), in the treasure list, the rank card and "Since you last looked". Apples, seeds, gummis, orbs and ribbons are in the same sheet if you want them for other places.

## 0.112.0

- **World, dungeon modifiers (Phase 3).** About a third of the days a dungeon has a small bonus on its rewards: Bountiful (20% more Poké), Berry season (berries easier to find), Lucky day (more treasure boxes) or Curious Pokémon (more finds). It is worked out from the dungeon and the day (UTC) alone, each floor uses the day it was climbed, and it never slows anything or takes anything away. An exploring team's card says what today's modifier is.
- **Keep party Pokémon out of World** (Settings, Teams, off by default): Pokémon in a module's party are not offered for World teams, Send my strongest passes them by, and they cannot be newly put on a team (one already on a team stays). It is off by default because a new save has only party Pokémon.
- **World's Camp folded into Base:** the separate Camp button and screen are gone; the Camp upgrade stays in Base with the yard of recruited Pokémon above it.

## 0.111.0

- **World, Phase 3 (first part): side paths opened with field moves.** About one floor in ten (never the first) has something on it: an overgrown path (Cut), a stream (Surf), a wall of rock (Rock Smash), a heavy boulder (Strength), a dark room (Flash), a burrow (Dig), a gap (Fly) or a traveller who needs nothing. A team member of level 10 or more whose Pokémon can learn the move (from the Dex's learnsets) opens it for a small bonus: berries, Poké, Exploration XP or a treasure box. A team with no one who can goes around and loses nothing, and nothing on the way to a Guardian needs one. Outcomes come from the dungeon, team, trip and floor alone, so a settle is the same however it is split, and they use their own random stream, so no floor's old rewards changed. The expedition log has an Events filter; the return report says how many side paths were opened and how many passed by. Choices, hazards, team roles and dungeon modifiers are still to come.

## 0.110.0

- **Pokégear, the specialist toolbox (Phase 2): six new tools** (fourteen in all), with a new Measure group on the home. **Everyday sums:** tip and split, percent (of, what percent, change), sale and tax, a loan's monthly payment, savings growth, trip cost. **World clock:** the time in places you choose (28 bundled, daylight saving included), a time at home converted to the others, and date sums (days from today, a date plus days, the weekday and day of the year). **Compass, Level and Barometer** read the phone's own sensors only while the tool is open: the compass is tilt-compensated and smoothed, the level shows the angle on both axes with a "call this flat" for sloped surfaces, the barometer shows hPa and a rough height against a sea-level pressure you can set. Each says plainly when the phone has no such sensor. **Device check** lists what this phone can do and which permission each thing needs, without starting or asking for anything. The device-only Pokégear state gained the clock's places and the sea-level pressure.
- Not in this release (they need the camera, the microphone or a permission flow that has to be tried on a phone): GPS waypoints, the sound meter, the QR scanner and generator, the document scanner, and the network check.

## 0.109.0

- **World, Phase 2 (first part).** Teams can be **named** (up to 16 letters) and given one of six **accent colours**; the Expedition log and reports use the name. **Saved teams:** save who is on a team as a preset (six kept) and put it back in a tap; a Pokémon since traded away, slotted as a Guardian or busy on another team is left out. The **expedition log is typed** (finds, clears, milestones, jobs, Guardians) with a filter and, under a filter, the place; old lines show as plain notes. The World save gained `worldVersion` (1). All new fields have defaults, so older saves load unchanged.
- **World, Phase 2 (rest).** **24 regional jobs** on the mission board for Kanto, Johto and Hoenn (explore, escort, defeat and one rescue each region: Clefairy, Slowpoke, Spheal), filtered by region, open only while the region's story lets a team into the dungeon. A **notebook by region** (Research): each layer's species as sprites, shadows for the ones not met, a tick for those you own. The Legendary quests card now says whether a Guardian that joined you is placed, exploring or idle.
- **Small controls are icons everywhere.** Delete, Remove, Edit, Add, Cancel, Done, Save, Undo, Previous, Next and the like are Pixelarticons keys with a spoken label in every screen; the launcher's clock widget shows a battery glyph (a bolt while charging, a low battery in the saver) with the percentage instead of the word.

## 0.108.0

- **Launcher: pages and Pokémon.** Any page of your own can be removed, with widgets on it (they move to the page beside it). The page left of Home now has the town behind it, like the others. Pokémon prefer to walk where no widget covers them (grid layout). Pokémon cries are back on tap: the widget pages over the town had been swallowing the touch, so the launcher now hands the tap to the Pokémon under it.
- **Launcher settings in sections** (Town, Home screen, Gestures, Time and date, Battery, Sound, Apps), each with a Pixelarticons glyph. **Gestures have jobs:** drag in from the right edge, tap three times and double tap empty space can each be set to nothing, the app list, gather the Pokémon, open PokéLife, open the Pokégear or the launcher settings.
- In-app Settings groups and cards have Pixelarticons glyphs in their headings.

## 0.107.0

- **Pokégear is a toolbox now: eight tools**, with a home that has search, favourites, the tools you used lately and groups. Every tool page has what it is for and a star, and the helper Pokémon is a small strip (tap Show for the full bar, and it stays as you left it). Nothing a tool does pays or records anything else.
- **Small controls use Pixelarticons** (MIT, `assets/pixelarticons/`) instead of words: edit, up, down, more, less, remove, copy, swap, reset, pause, stop and close are icon keys with a spoken label. The Pokémon item icons stay the main icons of the tools.
- **Calculator:** type or tap a whole sum with brackets (`2 + 3 × 4` is 14), percentages as a shop calculator reads them (`200 + 10%` is 220), a scientific panel (sin, cos, tan, their inverses, ln, log, sqrt, factorial, π, degrees or radians), a memory and a history you can turn on (kept on this phone only), and a copy button. Errors say what is wrong.
- **Units** (new, split from the calculator): 16 kinds and over 110 units, searched by name, symbol or alias, swap, every unit at once, starred pairs, decimals; US and imperial gallons, cups and spoons are named; decimal and binary data units and bits and bytes are kept apart; volume is never turned into weight.
- **Currency:** the Philippine peso is the home currency (changeable), search by name or code, a board of the currencies you watch, a rate of your own (what a money changer offered) and "what it really costs" with the reference, the rate you were offered and the fees apart; yen and won show no decimals.
- **Timer and stopwatch** (new): several named timers that keep running when the app is closed and ring with a notification (Stop, +1 min); a stopwatch with laps, fastest, slowest and average.
- **Picker:** edit in place, reorder, weights, several picks without repeats, skip the last pick, shuffle, an optional history. **Coin and dice:** d4 to d100, several dice, your own roll (`2d6+3`), an optional history.
- **Flashlight:** a screen light with colours (white, warm, red, amber) and brightness, and an SOS signal for the torch on request only (not with Reduce motion on, at most five minutes).
- New save fields are device-only with defaults, so older saves load. Docs: `docs/pokegear-audit.md` (Phase 1 status and limits) and the Pokégear section of `docs/ecosystem.md`.

## 0.106.0

- **Pokégear privacy fix.** The Picker's list was documented as never leaving the device but was part of the cloud save for a signed-in player (together with the currency rate cache). Both are now device-only: not in the cloud document, kept when a cloud save is adopted (even from an older upload that carries them), and "Export my data" still has the Picker list. A copy uploaded earlier is replaced at the next upload.
- **Currency tool:** one HTTP client for as long as the tool is open (it made a new one on every entry and never closed it), with timeouts and a status check; the rates are validated (base, date, codes, finite positive numbers) and normalised to the dollar; the tool says where the rates are from and how old they are, and that they are reference rates, not a bank's. A failed fetch says why and keeps the cached rates.
- **Flashlight:** the state is the hardware's (every flash camera is tried, a torch callback shows a change by another app or the system at once), and a light this app turned on is turned off when the tool is left or the app stops.
- A capability layer for later hardware tools (`DeviceCapabilities`): the Android app asks the phone what sensors, camera features and permissions it really has, other platforms say nothing is available. No tool uses it yet; nothing is started or asked for.
- Docs: `docs/pokegear-audit.md` (the audit and the plan for Phase 1) and the Pokégear section of `docs/ecosystem.md` corrected (five tools; no timer or stopwatch yet).

## 0.105.0

- **World landing, rebuilt around one action.** From the top: your rank (with what the next rank opens), what your teams brought back since you last looked, **one big button for what is worth doing now** (recruit the Pokémon waiting, claim a finished job, send a team to the next dungeon of the layer on show, or look in on teams that are all out), all teams at a glance (where, which floor, jobs, Finds), the layer's story, the world picture, then four places (Map, Expeditions, Missions, Base) with Finds and Bag, Skills, Camp and Settings as small buttons below.
- **"Since you last looked" report** on the landing and the Expeditions screen: floors climbed and where, what every floor gave (Exploration XP and Poké), the milestones reached (fixed rewards), what was found by chance (berries, treasure boxes), dungeons cleared, jobs done, a Guardian faced, and the Pokémon waiting in Finds. Berries, Poké and items are already in the Bag; Pokémon stay in Finds until you recruit them. Dismiss with OK. It is built from the same events as the reward history, kept in memory only and never pays anything (`ReportSummary`).
- **Rank card** says what the next rank opens (teams and dungeons).
- **Mission board:** each job says where it is and how often it comes back (one time, daily, weekly or every 14 days) and shows its reward; a job you cannot take is not hidden but listed under "Not available now" with why (rank, a wait, or done for good); with three jobs held the Take buttons say why they are off.
- The story card shows how many different dungeons of the layer you have cleared against what the next chapter needs.

## 0.104.0

- **World, Expeditions:** an "All teams" card at the top says what every team is doing (where, which floor, who is on it, or ready or empty) and how many Pokémon are waiting to be recruited. A team that is out has a **"How fast?"** button that shows its floor time in steps from the real numbers: the team's levels, the +20% of the levels of members of a favoured type, the power, the base time divided by 1 + power/100 (never under a quarter), Temporal Mastery and the region's legendaries, and the result; abilities are listed as help for skills, not speed (`WorldSim.explainSpeed`, tested to equal the real floor time).
- **World, Map:** each legendary quest says what it gives and where ("Water and Flying-favouring dungeons climb 10% faster in Johto"), from the same numbers the simulation uses.

## 0.103.0

- **Plan has no quest for the legendaries World gives.** Zapdos, Articuno, Moltres, Mewtwo, Mew, Lugia, Ho-Oh, Groudon, Kyogre and Rayquaza now come only from World's region quests; their Plan quests are gone from the quest list (a Plan quest already finished stays finished and its Pokémon owned; one in progress for such a species goes away). Every other legendary and mythical keeps its Plan quest until a World layer gives it, one species at a time (`Legends.coveredSpecies`).

## 0.102.0

- **World teams:** a Guardian (legendary or mythical) can join a team while it is idle, like any other Pokémon; one that is slotted as a Guardian cannot, and a Guardian on a team cannot be slotted until it is recalled. "Send my strongest" takes the strongest idle Pokémon. The team picker shows the team's own members and the 20 strongest idle Pokémon (it showed the strongest 40, Guardians included, whatever they were doing).
- **World landing:** "Next up" is for the layer on show (it always spoke of the Mystery Dungeon world), with the layer buttons right there.
- **Locked dungeons** list every requirement not met yet, each with how far along you are (rank XP, the story chapter's rank and number of different dungeons cleared, the story itself), not only the first.
- **World map markers** have a shape as well as a colour: a diamond (open), a circle with a tick (cleared), a square (not open yet), a circle with an arrow (a team is there), and a red ring around a Guardian's dungeon, with a line under the map saying so.
- Docs: `docs/world-audit.md` (the World audit and the owner's answers) and `docs/world-module.md`.

## 0.101.0

- **World: the Hoenn layer** (fourth button on the Map's layer switch, open from the first day): nine dungeons from Ruby, Sapphire, Emerald, Omega Ruby and Alpha Sapphire (Petalburg Woods, Granite Cave, Meteor Falls, Mt. Chimney, Shoal Cave, Seafloor Cavern, Cave of Origin, Victory Road, Sky Pillar), wild Pokémon from the Hoenn Pokédex, the Hoenn map of Pokémon Emerald's PokéNav (`assets/world/map-hoenn.png`, built by `tools/sprites/fetch_hoenn_map.py`) with a marker on each, and its own five-chapter story, "The Primal Awakening". Legendary quests, all Guardians of Hoenn: Groudon and Kyogre after three clears of Mt. Chimney and the Seafloor Cavern, Rayquaza at the top of the Sky Pillar.
- A legendary from a World quest is not given a second time when the player already owns that species from Plan's quest for it; the World quest still counts, so its boost for the layer applies. New `docs/world-module.md` describes the whole World module (features now and planned, how it fits with the other modules, storylines and quests).
- Launcher pages: a page of your own can now stand at the left of Home (edit mode on the grid: "Left+" adds one at the left end, "Right+" at the right end, as "Page+" did), and any empty page of your own can be removed with "Page-" while you are on it, not only the last one. Home and Today stay. The pages' order is saved by name (the usual order is still saved as a count, so older saves load), and swiping, the page dots, dragging a widget to the next page and the Home button all follow it.

## 0.100.0

- **World: the Johto layer** (open from the first day, third button on the Map's layer switch): nine dungeons from Gold, Silver, Crystal, HeartGold and SoulSilver (Sprout Tower, Ilex Forest, Union Cave, Ruins of Alph, Slowpoke Well, Mt. Mortar, Burned Tower, Whirl Islands, Tin Tower), wild Pokémon from the Johto Pokédex, the Johto map of Pokémon Crystal's Pokégear (`assets/world/map-johto.png`, built by `tools/sprites/fetch_johto_map.py`) with a marker on each, and its own five-chapter story, "The Sacred Balance". Legendary quests, both Guardians of Johto: Lugia after three clears of the Whirl Islands, Ho-Oh at the top of the Tin Tower. Legendary boosts are data now (`LegendQuest.types`, `findPermille`, `treasurePercent`). New layer buttons wrap onto a second line when narrow.
- Home and the reward history say that a Pokémon from World "joined you" instead of that an egg hatched (the Recently list, the reward box and World's own history).

## 0.99.0

- **World: the Kanto layer.** The Map screen has a layer switch (the Mystery Dungeon world and Kanto, both open from the first day). Kanto has nine dungeons from Red, Blue, FireRed and LeafGreen (Viridian Forest, Mt. Moon, Rock Tunnel, Pokémon Tower, Power Plant, Seafoam Islands, Mt. Ember, Victory Road, Cerulean Cave), each with wild Pokémon from the Kanto Pokédex (found only there), its own rank gate and milestone rewards, the official FireRed and LeafGreen map with a marker on each, and its own five-chapter story, "The Genetic Crisis". Clears in one layer do not advance the other layer's story.
- **Legendary quests (Kanto):** Zapdos (Power Plant), Articuno (Seafoam Islands) and Moltres (Mt. Ember) join your Guardians after three clears of their dungeon; Mewtwo joins when Cerulean Cave's last floor is reached (Kanto's Guardian) and Mew after three clears of it. Each given once, with a reward line in the log. A bird makes the Kanto dungeons that favour its type 10% faster; Mewtwo adds a little to the chance of a find in Kanto, Mew to the chance of a treasure box. The quests are listed under the Kanto map.
- New save fields with defaults (`layersDone`, `legends`), so old saves load. The home layer's numbers are unchanged (checked with `simulateWorldEconomy`; the report lists Kanto's dungeons beside the home ones).
- Launcher Calendar widget: the rows fit the box (they were a fixed 48 dp, so six weeks overflowed the largest box and the top row of buttons was cut off); when the box is too short for the whole month, only the weeks around the chosen day are shown. The header is "Prev", a short month and year, and "Next" so none is cut; the widget can now be 10 rows tall (was 8), and new ones start 8 tall.

## 0.98.0

- Primal Dialga is its own Dex form (`dialga-primal`, id 20001, an alternate form of species 483), separate from Dialga, for the home layer's Temporal Tower Guardian. PokeAPI has no such form, so it is listed in the new `tools/species/custom_forms.txt`, which `generate.py` adds to the Dex and `tools/sprites/fetch.py` to the sprite manifest. Its sprites and portrait are SpriteCollab's "Primal" ones (credited to Chunsoft); base stats are Dialga's, as there are no official ones. The Temporal Tower Guardian now gives Primal Dialga (`Story.GUARDIAN_FORM`; `GameStore.grantPokemon` takes an optional form). Players who already received Dialga keep it.
- `assets/world/map-kanto.png`: the official FireRed and LeafGreen Kanto region map, built from the game's tiles by `tools/sprites/fetch_kanto_map.py`, for the coming Kanto layer of World. Not used by any screen yet.
- Docs: the World regions decisions (all nine mainline regions in order, each region's box legendaries as quests and Guardians, side legendaries as module Guardians, one layer each, Hisui as its own layer with the Origin-form items and Arceus) are in `docs/ecosystem.md`.

## 0.97.0

- World job alert (Android): a notification when an exploring team finishes a job on the mission board ("A World job is done", or how many are ready), opening World. The moment is forecast from the team's pace (`WorldSim.jobsDoneAt`, tested against a real settle: not a millisecond before, done at it); one alarm is kept for the earliest, and World is settled when it goes off. Berry jobs have no forecast. A switch in Settings ("World job alert", on). New settings field with a default, so old saves load.
- World, the dungeon pictures (the Map's cards and the Teams scene) centre their Pokémon along the strip; they were bunched to the left.
- Launcher widget grid, less tall: rows are 36 dp (were 40), and a widget's text is centred in its frame (it hugged the top, leaving the empty space below). Inside a grid box the room above and below is whatever the cells leave, so nothing is cut off at the default sizes (checked in `launcher-grid.png`).
- World recruits are not eggs: recruiting a find (or receiving the World's Guardian) no longer plays the full-screen "hatched from the Egg!" scene, and the buddy no longer reacts as if an egg hatched. The Pokémon is still added as before, and its reward line is in the log. Eggs hatching from Focus, tasks and Care are unchanged.

## 0.96.0

- Moves in the Dex (owner's decision d16): `tools/species/generate.py` now also writes every move (919: type, category, power, PP, accuracy, priority, generation) and each form's learnset from the newest game it appears in (level-up with levels, egg, tutor, machine). `Dex.move(id or identifier)`, `Dex.learnset(form or species)` and `Dex.movesOfType(type)` read them; Mega and Gigantamax forms use their species' set. No gameplay uses them yet; they unblock move-based skills and Pokégear helpers. `DexData.kt` grows by about 410 KB.

## 0.95.1

- Care: the journal draft is kept across an app restart (saved after a short pause, cleared when the entry is written). The owner's World regions decisions and the merge rule for failures outside a PR are recorded in the docs.

## 0.95.0

- World, clearer (audit Phase 5, first slice): the landing opens with "Next up" after your first trip (the first open dungeon you have not cleared, or what the nearest closed one is waiting for, with a one-tap "Send my strongest"); each open dungeon on the map says, for every team that is ready, how long a floor takes it and how many of its members like the place; and the expedition log is kept in the save (finds, milestones, clears, finished jobs, the Guardian; a dungeon cleared again and again in one stretch is one line, "(3 times)"), so it is still there after a night away. The save gains a `journal` list with a default, so old saves load.

## 0.94.4

- First-use states (audit): a screenshot test renders every landing page for a brand-new player. World's menu now opens with a "Start here" card that says what World is and sends your strongest Pokémon into Tiny Woods in one tap; Care's landing puts Today (and "Add a routine") right under the scene instead of below six empty party slots, with the visitors and the party after the menu; the Pokémon tab says where the next Pokémon come from while you have only one.
- Care audit pass (`docs/audit-2026-10-31.md`): a half-written journal entry now survives leaving the screen or the tab (it was lost), and a test shows a routine's reminder follows an edit. The rest of Care checked out against the audit (privacy of the Care event, reminders and their Done action, calm break under Reduce motion, explicit skill practice, opt-in cloud copy with delete-on-off).

## 0.94.3

- Audit: a screenshot test (`ScreenshotTest.largeTextPlaces`) renders Home, Plan, Planner, Focus, Care, Pokémon, Me and World at 130% system text; all eight were looked at and nothing is clipped or overlapping.

## 0.94.2

- Plan usability, Phase 3 (finished): checked against the code and the audit's three points. Quick Add: shows what it understood and keeps the time (0.90.6). Inbox vs Today: the Inbox is the tasks in no list and Today is what is planned for today, with "Move to" on every task; nothing to change. A way to plan on the calendar without dragging: tap a task in "To plan", then tap an hour (with a panel to nudge by 15 minutes, set the length or take the time away) already existed; it is now covered by a test with real taps (`DayPlanTapTest`), and the "To plan" row fades at its right edge when more tasks are past it, so a clipped chip reads as "scroll for more".

## 0.94.1

- Back chains are tested with real taps (`BackChainTest`): the test presses the header's Back button in an offscreen screen and checks where it lands. Every Plan section, the Planner (a view, then its menu, then Plan), Focus, every Care section, every World section, the four Pokémon sections and Me's sections go back to their menu, and each module's menu goes back to Modules. All pass; no behaviour changed.

## 0.94.0

- Launcher pages: make your own pages and drag widgets between them. Edit mode (long-press a widget) has a bar that floats at the foot of the screen over the page (entering edit mode moves nothing): Add, Page+ (a new page; up to five pages: Home, Today and three of your own), Page- (deletes the last page when it is empty, in place of Page+) and Done. Hold a dragged widget at either side of the screen for half a second and it hops to the next page (past the last page, onto a new one) and the launcher goes there; each widget also has "To the next page" and "To the previous page" accessibility actions. Swiping, the page dots and Home work for any number of pages, and the town is as wide as the pages so Pokémon can wander across all of them. The Widgets list (Launcher settings) shows the grid by page.
- Live preview while dragging or resizing: the other widgets slide out of the way as you go, showing exactly where everything will be when you let go (still at once with Reduce motion), as well as the yellow landing frame.

## 0.93.0

- Launcher widget grid is on by default (Launcher settings → "Widget grid (drag, resize)" goes back to the old list). Your layout is converted in the same order; widgets that are half width or short now reshape instead of being cut off badly: the clock shows the time alone, Shortcuts show pictures only, App buttons go one to a line, and every widget's frame is as tall as its cells, with what does not fit cut off inside it. Tested at the smallest size of every kind (`launcherGridSizes`).

## 0.92.0

- Launcher widget grid (preview switch in Launcher settings): drag and resize like Smart Launcher. Long-press a widget to edit; every widget gets a frame. Drag it anywhere and it snaps to the cell under it when you let go (what is in the way moves down); pull the square in its corner to resize in whole cells, between its kind's smallest and largest; X removes it; a yellow frame shows where it will land. Add widget and Done are at the top as before, and a widget you add takes the first free spot. Each move and resize also has an accessibility action (move up, down, left, right, wider, narrower, taller, shorter, remove). Tested with real pointer events (drag and corner pull). Not yet: dragging to the other page, and widget contents that reshape for narrow cells (they clip).

## 0.91.1

- World map: the wild Pokémon shown on each dungeon are dark shapes standing still, not walking sprites: they are samples of who lives there, not Pokémon you have. (The team's own expedition box still walks.)
- UI taps: 6% of the Sound effects volume (3% was not audible; 10% was too loud).

## 0.91.0

- Launcher: the widget grid can be switched on as a preview (Launcher settings → "Widget grid (preview)", off by default). Home and Today then draw the same widgets in cells, 4 across and 40 dp tall rows, each clipped to its cells, with the town showing through the empty ones; your layout is converted with the same order. You cannot drag or resize on the grid yet (that is the next step, `docs/launcher-widget-grid.md`); switch it off to edit the stacked layout. A row is 40 dp (the model's limits were scaled to match) and the old Small, Medium and Large become a widget's height.

## 0.90.8

- Groundwork for launcher widgets like Smart Launcher's (drag anywhere, resize): the grid model (`launcher/WidgetGrid.kt`: 4 columns, per-kind size limits, add, move with what is in the way going down, resize, and the old stacked layout laid out as a grid) and its saved form, with 12 tests. Nothing changes on screen yet; the plan and what is next are in `docs/launcher-widget-grid.md`.

## 0.90.7

- Launcher: the night tint is one sheet over the whole town, so it reaches the right edge (a lighter, untinted strip showed there at night). A render of the town at 8:39 pm is in the screenshot test.
- Menus: every menu card is the same, an icon and a title with no caption (the captions made cards unequal, e.g. World's wrapped to two lines); a screen reader still hears the old caption after the title.
- World: the dungeon floor scrolls again on a phone with the battery saver on. The saver had been stopping it; only the "Scrolling floors" setting (World → Settings) and Reduce motion stop it now.

## 0.90.6

- Plan usability, Quick Add (audit Phase 3, first step): the box now shows what it understood while you type ("Understood: Tomorrow · 17:00 · Social · Difficulty B · 30 min"), so a typed line is never a guess. And a bug it exposed: "Call mum tomorrow 5pm" and "tonight" were understood but the time and the evening were thrown away when the task was made; they are kept now (on a planned day), so the task lands on the calendar at 17:00 and "tonight" goes under This evening. Tests for the hints and for the stored time.

## 0.90.5

- First-use pass: every landing page was rendered on a brand-new save (`ScreenshotTest.firstUse`, 33 pages: Home, Modules, Plan and its sections, the planner's views, Care and its sections, World's sections, the Pokémon tab, Me). Nearly all already say what goes there and what to do; the two that gave only a title now give a line too: Planner's Done ("Tasks you finish show up here, newest first.") and an empty Today ("Add one above, or plan one for today from the inbox or the calendar.").

## 0.90.4

- Control system, the rest of it: a tappable list row tints faintly under a mouse and shrinks to 98% when pressed (like a key); the bottom tab bar items tint under a mouse. In Settings → Account the password field turns red with "A few more characters: at least 6" once you have typed fewer than 6 (the rule shows as a quiet line before), and the email and password fields are dimmed while signing in.

## 0.90.3

- `PixelField`: one text field for the whole app, replacing 29 plain Material fields (`theme/Fields.kt`). Rest, focused (blue), error (red, with a line that says what to do, announced by screen readers), disabled (shaded, dim letters) and read-only; labels and placeholders are plain strings. The error and disabled looks are new; nothing validates yet, so only the look is added.
- Taps click again, very quietly: buttons, tabs, filter and category chips and the day arrows play their menu sound at 3% of the Sound effects volume (it was silent since the UI sounds were removed; `TAP_GAIN` in `UiSound.kt`). Game sounds are unchanged. Off when Sound is off.

## 0.90.2

- Design system: one key for every control. Buttons, tabs, filter chips, category chips, the day and week arrows and the Day/Week/Month switch are all the same stair-cornered pixel key with the same states (resting, hovered with a mouse, pressed, selected, focused, disabled, loading) and the same 48 dp touch target; the table is in `docs/ui-ux-brief.md` section 5.1 and the code in `theme/Controls.kt`. Choice rows, notices and stat chips share the outline; text fields take the dialogue corner, the blue focus colour and the app's font for labels and error text (they showed the system font). Tabs and chips are filled when selected, as before, but no longer rounded; the arrows are keys, not bare symbols.
- A component gallery in the screenshot test renders every control in every state, light, dark and at 130% text.

## 0.90.1

- Care routines can be edited: tap a routine, then Edit to change its name, time and days (what it earned and the days it was done stay, and editing never lets today pay again). The time and days controls are shared with the add form. Tested in the store and rendered at 130% text.

## 0.90.0

- Phase 1 of the audit (`docs/audit-2026-10-31.md`) and its five decisions: World's rewards are in the reward history (Poké and items, merged per ten minutes, never a reveal) and its items go straight into the Bag, so there is one inventory and the Finds screen holds Pokémon and treasure boxes only; Plan's "Growth" section is called "Progress"; the Quests screen says how many World jobs are taken and ready; no further candy cuts.
- Settings: the Hatching region picker and the Modules group are gone (Care is always shown); Routine reminders are in Notifications; settings are per module: Plan, Care and World each have a Settings entry on their menu (Plan: focus defaults, first day of the week, phone calendars; Care: cloud copy; World: scrolling floors).
- World: the Pokémon walk again. The phone's power saver had also stopped them (build 155); now it only stops the scrolling floor and the effects, and a new World setting turns the scrolling off.
- Tests for old saves (every field has a default, stored names do not move), export and erase, and the World ledger. Two unused composables removed; `dayOfMonth` replaced by `day`.

## 0.89.1

- Audit (`docs/audit-2026-10-31.md`, answering the owner's repository audit brief): findings with severity, what was and was not inspected, a clean-up classification, a phased plan and the decisions only the owner can make. `docs/world.md` gains a status table of what is built.
- Fixes it led to: World expeditions no longer freeze when the phone's clock is set back; daily and weekly World jobs come back at the player's midnight, not UTC's; the world map tells a screen reader how many dungeons are cleared and open and that the list holds them all; unused World code and three unused previews are gone. New tests: the clock, the day, and `NavigationTest` (every place the app can be sent lands on the right tab, module and section). World screens are also rendered at 130% text in the screenshot test.

## 0.89.0

- Emoji show in the launcher's pixel text (an agenda event or a task called "🏠 Home" showed "??"): the dialogue font cannot draw them, so the system's emoji font draws them beside the letters.
- The launcher's settings have a real item icon on every line (a town map for the town, a timer ball for the clock, a bag of leftovers for the battery saver, and so on) and on the two volume controls.
- Launcher pages (settings, widgets, folders, forecast, weather place, town picker) now fade and glide in from the right and out the same way, instead of appearing and vanishing; at once under Reduce motion.
- World's map is the Mystery Dungeon world map, uncovered a stage more with each dungeon cleared, with a marker on each dungeon (yellow open, green cleared, grey not yet open); tapping one brings that dungeon to the top of the list. Spinda's Café has its room as a banner. Real attack effects play at the right moments: steam when you brew, a hit on Dojo training, a welcome when a Pokémon joins, sparkles on a treasure box (not under Reduce motion or the phone's power saver).
- 214 Mystery Dungeon 2 item pictures are choices for the home-page icon of a pinned app.
- A `spacing-system` project skill (the spacing scale and rules, mapped to the app's `Spacing` tokens).
- World, from the review: ordinary jobs pay 40% less (World jobs are now about a third of an engaged player's Poké and Exploration XP, 45% of a casual one's); story chapters count different dungeons cleared (3, 4, 5 and 7), so repeating Tiny Woods no longer opens them; the map's "Send my strongest" button sends the highest-level free Pokémon (never a Guardian) in one tap; abilities help skills (Pickup and Honey Gather for Foraging, Keen Eye and Illuminate for Scouting, Run Away and Swift Swim for Exploration: each helper on a team is one level, up to three); the clock on World's screens follows the store every few seconds; World's animations stand still while the phone's power saver is on.

## 0.88.2

- World review fixes: a daily or weekly job comes back on the right day even when no team has settled since (the screens now pass the clock); a Café brew is no longer wasted by brewing again while one still works; a Pokémon transferred away leaves its World team instead of holding the slot (a team left empty comes home); a finished Deliver job says what the Bag needs and its Claim button waits for it; a Defeat job shows your best team's real power; the map's boxes no longer redraw their floor every frame (only the Pokémon on them move), which keeps the battery for the expedition screen. Every floor of every dungeon is now tested to have someone to find.

## 0.88.1

- The World economy simulation now plays jobs of every kind (Explore, Find items, Rescue, Deliver, Escort, Defeat), the Swap Shop and the Dojo (`docs/world-economy-report.md`). It showed that jobs paid about half of all Poké and Exploration XP (700 jobs a year at a 7 day wait), so ordinary jobs now wait 14 days (the weekly patrol stays weekly, daily berry run daily); they still pay about 40 to 50% of Poké. It also showed the Swap Shop is hardly ever needed (berries are plentiful) and that the Dojo, played after the Base is built, only saves about three weeks of skill levelling and costs about 3,000 Poké: both are small. The job screen no longer calls every ordinary job "weekly".

## 0.88.0

- World jobs: Escort (the team must be at least three Pokémon) and Defeat (the team must have enough power) jobs, five in all. The Croagunk Swap Shop in Base trades three berries of one flavour for one of another. The Camp has a yard in the tiles of a clearing that grows into a meadow and an orchard as the Camp is built up, where the recruited Pokémon roam, each walking back and forth facing the way it goes.
- The status bar strip takes the header colour too, like the tab bar.
- A World Home-screen widget: each team's dungeon and floor, Finds waiting and jobs to claim; it settles the teams when it refreshes, and a tap opens World.
- Still not built: move-based skills (the Dex has no moves).

## 0.87.0

- World's map draws each dungeon like the expedition box: the dialogue border round that dungeon's tiles, its wild Pokémon walking the floor, and its details written on the wall tiles.
- Recruiting a Pokémon you found is a short scene in the style of the dungeon games, not an egg: its portrait beside "wants to join your team. Recruit it?" (Recruit or Not now), then its portrait, happy, beside "joined your team!", with its cry. The list of finds now says Meet.
- The bottom tab bar takes the colour of the header of the page you are on (red, Care's green, World's teal), with white labels.

## 0.86.2

- World expeditions, redone: the box of a team that is out is the app's dialogue box filled with its dungeon's own tiles: one row of wall on top, then a floor that scrolls past while the Pokémon walk across it (only on the floor) with their walking animations (still under Reduce motion), and the text written on wall tiles under it. Nine new tilesets are available (every dungeon has its own except the Distortion Rift, which uses the World Abyss).

## 0.86.1

- World expeditions: the box of a team that is out is made of its dungeon's own tiles (every dungeon now has its own tileset except the Distortion Rift, which uses the World Abyss): a frame of walls round a floor that scrolls past while the Pokémon walk across it with their walking animations (still under Reduce motion). Nine new tilesets are also available to the launcher's town.

## 0.86.0

- World, the rest of the first design (`docs/world.md`): five more dungeons across the other regions (Waterfall Cave, Glacier Palace, Ancient Ruins, Sky Tower, Temporal Tower) and a post-story Distortion Rift; dungeons now also need their story chapter. Clearing the Temporal Tower faces the Guardian: the story is complete, Dialga joins your Guardians once, and every expedition climbs 10% faster (Temporal Mastery). Spinda's Café (three berries from the Bag brew three hours of 30% more Exploration XP, 5% more treasure chance, 1% more find chance), the Kecleon Shop (Rare Candies for Poké, a price that rises with each) and the Dojo (Poké into skill XP). Deliver jobs, daily and weekly jobs, and six more jobs; ordinary jobs now wait a week before they can be taken again. World has a `Place` the app can be sent to.
- Tuned by the new simulation: silver treasure boxes give a Rare Candy 15% of the time and gold 35% (it was 60% and 100% to 175%, hundreds a year), and the last research steps are 35 and 45 species (the dungeons only have 50 findable, so 60 could not be reached).
- World screens have real Mystery Dungeon art: a town banner on the menu and the map, the guild room on Missions, the guild summit on Base, the bone cave on Camp, and a tileset picture on the dungeons that have one. Dungeon cards show type plates, a depth bar and why a dungeon is locked; jobs carry an icon and a Done mark; the sections are called Map and Base.
- Not built: Escort and Defeat jobs, the Swap Shop, camp roaming and habitats, move-based skills, a Home-screen widget for World.

## 0.85.0

- World skills, base and research (`docs/world.md`, sections 10, 12 and 13): three skills level as teams explore, even away (Exploration: 2% more Poké per level; Foraging: 1% more berry chance; Scouting: 0.3% more find chance; level 10 at 3,000 XP). A Base buys the Camp (1% more treasure chance per level) and Research Lab (10% more Exploration XP per level) for Poké, five levels each (the cost triples every level). Research pays Poké, berries and Rare Candies at 3 to 60 species discovered. This gives Poké its first use. None of it changes how long a floor takes. Café, Dojo, shop and storage are not built.

## 0.84.0

- World missions and rescues (`docs/world.md`, section 8): a Missions section in World, open with story chapter 2 (Bronze rank and one dungeon clear). Take up to three jobs from the board, then send a team where they point. Explore jobs finish when a team reaches the named floor, Find-items jobs count berries, and a rescue leaves the lost Pokémon (Vulpix, Marill, Eevee) among your Finds to recruit, once only. Finished jobs are claimed for Poké, Exploration XP and sometimes an item. Ordinary jobs repeat; missing a day loses nothing. Nine jobs for now; Escort, Deliver, Defeat and daily/weekly jobs are not built.

## 0.83.1

- World economy simulation (`./gradlew :world-core:simulateWorldEconomy` writes `docs/world-economy-report.md`). The first guesses paid far too fast (a floor every 1 to 5 minutes, 20 to 50 Pokémon found a day on a team that stays out, Diamond rank in 23 days), so floors now take 6 times as long (30, 48 and 72 minutes at base), the find chance is 3% and the treasure chance 6%. A typical player (two opens a day) now gets roughly 1 find a day, Gold in about 4 weeks and Platinum in about 3 months.

## 0.83.0

- World, the dungeon game (design: `docs/world.md`; not the same as the Explore module in the ecosystem plan): a new card in Modules. Send up to two expedition teams of up to four Pokémon each into Tiny Woods, Thunderwave Cave or Mt. Steel (the last needs Bronze rank). Teams climb the floors on their own, while the app is closed too (up to 12 hours away at once), and settle exactly as if you had stayed. They bring back berries, Poké, treasure boxes and Exploration Finds: ordinary Pokémon you recruit into your collection (the same persistent Pokémon as any other, never a legendary or mythical). Pokémon that suit a dungeon's types explore it faster. Rank (Normal to Diamond) opens dungeons and more teams; the story chapters are data for later.
- World screens: the World map (dungeons, depth, clears, Send team), Expeditions (choose a team, floor progress, Recall), Camp (the Pokémon you recruited and the species found) and Finds and Bag (recruit, move items to the Bag, open treasure boxes). The numbers (floor times, find chance, rank points) are first guesses to tune.
- Not yet: missions and rescues, skills, facilities and research, the quest engine, the Guardian.

## 0.82.0

- Buttons are redrawn as flat pixel keys (the "Outline" option): a stair-cornered outline in the key's colour on the panel colour, the label in the same colour, and while held the key fills with its colour and the label turns white. They are smaller: 38 dp tall (32 dp compact) with the same 48 dp touch target and a smaller label. Quiet buttons have a grey outline and an ink label; a disabled one has a faint outline and a dim label; a new `loading` state shows three blinking pixels and cannot be tapped.
- Text lines are set closer (about 1.25 times the size instead of 1.5), and a button's coloured label is drawn as thin letters without the font's black shadow, which had made it look bold.
- Dark theme: labels on tabs and buttons were drawn dark on a dark bar (the label's colour was only checked against white); they now follow the colour's lightness, so the bottom tabs and the buttons read on both themes.

## 0.81.0

- Pokégear: a hub of handy tools with helper Pokémon, in Modules (the Pokégear card) and from the launcher (the Pokégear button, which replaces the phone Utilities screen of 0.80.0). The tools: **Flashlight** (the phone's torch on Android, a white screen elsewhere), **Calculator** (a pocket calculator, and a unit converter for length, mass, volume, speed and temperature), **Currency** (the European Central Bank's daily rates through Frankfurter, kept for offline use), **Picker** (choose one from a list you type, kept on the device) and **Coin toss**. Every tool works from the first day and pays nothing.
- Pokégear: each tool has a helper Pokémon, which is flavour only: its sprite beside the tool, and one emoji when the tool is used. The helpers are Mareep, Flaaffy and Ampharos for the Flashlight; Porygon, Porygon2 and Porygon-Z for the Calculator; Meowth, Persian and Perrserker for Currency; Cleffa, Clefairy and Clefable for the Picker; Gimmighoul and Gholdengo for the Coin toss. Any owned one can help (the friendliest by default; Change helper chooses; one Pokémon may help several tools).
- Pokégear: with no one to help, Ask a Ranger plays a Capture Styler: draw loops around the roaming loaner (2 to 4 by its rarity, faster if its Speed is high; slow with Reduce motion). It lends a hand to that tool as a loan, never joining your party, PC or Dex, until you own a Pokémon who can help.
- Not yet: helpers that learn a tool's move (the Dex has no moves), and Android widgets for the flashlight and the coin toss.
- Menus: a card's caption is optional, and the Pokégear, Settings and Help cards have none (their titles say enough).
- Friends is switched off in the Me menu for now (the screens and the server side are kept; `FRIENDS_ENABLED` in `Me.kt` brings it back).

## 0.80.0

- Launcher: battery saver (Launcher settings → Battery saver: Off, Automatic or Always). While it is on, the town's Pokémon stand still, the weather and the clock's seconds stop, nothing blurs or animates, and the launcher checks the clock, the battery and the forecast less often. Automatic turns it on at 20% or lower when not charging, or whenever the phone's own power saving is on. The clock widget says "Saver" beside the charge.
- Launcher: the calendar and agenda widgets show Google Calendar (and any other calendar on the phone), like the app's Planner: the same calendars you chose in PokéLife → Me → Settings, read each time and never saved, private events as "Busy", with dots on the days that have plans. The agenda asks for the calendar permission when it needs it.
- Launcher: a Utilities screen (the Utilities button in the App buttons widget): a flashlight, a pocket calculator, a stopwatch, and links to the phone's own internet and volume panels and its Bluetooth, display, battery saver and alarm pages. None of it needs a permission.
- Widget: tapping a task's name in the Today's tasks widget finishes it from the widget (it is paid when the app next opens), instead of opening the app; the header still opens the Planner.

## 0.79.0

- Launcher: the Buddy widget, with portraits and no words. It shows the buddy's PMD portrait in a pixel frame with its name and level (or "Focusing"), and replaces the Companion box on a launcher that has not changed its widgets (Companion is still there to add: it has the weather). Tap the portrait for its cry and a smile. The portraits are bigger (the face at twice the town's pixel scale, so it stays crisp), fill their pixel frames with no gap inside, and sit flush against the sides of the screen with no page margin; the name box is as tall as the portrait.
- Launcher: when the buddy meets another Pokémon in the town, that Pokémon's portrait appears beside it and both faces change with what happens: a greeting is calm, then startled, then happy and joyous; a quarrel is determined and startled; being startled together is both startled. A few seconds later the faces go calm and the visitor leaves.

## 0.78.0

- Launcher: more space between an app's icon and its name.
- Launcher: the clock widget's date and time can be written your way (Launcher settings): the date as "Sat 31 Oct", "Saturday 31 October", "31 Oct 2026", "Oct 31" or "2026-10-31"; the time on a 24-hour or 12-hour clock, with or without seconds. The agenda's times follow the clock.
- Launcher: the calendar's weeks start on Sunday (a setting switches to Monday).

## 0.77.0

- Launcher: hold any quiet part of a widget on the home screen (or the Today page) to edit the widgets right there. Each widget gets a bar: hold and drag it up or down to move it (past the last widget at the top it goes to the bottom of the page, and the other way), tap its size to change it (Small, Medium, Large), or Remove. Add widget and Done sit at the top. Swiping between pages is off while editing, and Back also finishes.
- Launcher: sizes mean what fits each widget: Favorites, Often used and folders show one row of apps, two or all (the rest are counted); the calendar one week or the month; the agenda three lines, six or all; the forecast no days, three or four; an Android widget its own height times 1, 1.5 or 2.
- Launcher: the Favorites widget has no label; folders and Often used have a small one instead of a tall title row with a rule.
- Launcher: app icons are filled, not outlined: the whole pixel-cornered square is the icon's most common edge colour with the picture over it, so a rounded or see-through corner shows no gap.

## 0.76.0

- Launcher: put another app's Android widget on the screen. Launcher settings → Widgets (or the new Widgets button in the App buttons widget) → Add a widget → "Android widget from another app" opens the phone's widget picker, then the widget's own set-up screen if it has one, and the widget appears where you added it (top or bottom of the home page, or the Today page) in a pixel frame. Remove drops it. The launcher is the widgets' host, so they update by themselves while it is in sight.

## 0.75.0

- Launcher: every box on the home page is a widget now, and so are the calendar and the day's agenda on Today. Launcher settings → Widgets lists them by place (top of the home page, bottom of it, the Today page); each can be moved Up or Down, Removed and Added back, and a place can hold any widget. The widgets: Date and time, Companion, Shortcuts, Often used, Favorites, Folder, App buttons, Calendar, Agenda and tasks, and a new Forecast widget (today and the next four days).
- Launcher: folders are widgets. The pinned-apps box is now the Favorites widget; a folder is a labelled box with its apps in it (tap an app to open it, press and hold for its sheet, tap the label to manage the folder). "New folder" in Widgets, in Folders or in an app's sheet makes the folder and its widget together; the apps go in from the long-press sheet in All apps ("Add to folder"). Removing a folder's widget keeps the folder.
- Launcher: the bottom widgets scroll when there are more than fit.
- Launcher: an app icon's pixel border is the icon's own edge colour, so it melts into the picture and only the stair-stepped corners show.
- The old "Date and time box" and "Status box" switches are now the widgets (a launcher that had them off keeps them off).

## 0.74.0

- Launcher: Niagara-style features. **Often used**: the apps you open most from the launcher (not pinned, opened at least 3 times) show above your pins, kept as counts on the device only. **Folders** on a screen of their own: make, rename and delete folders, add an app from the long-press sheet in All apps ("Add to folder"), open and remove apps. **Weather forecast**: choose a town (Launcher settings → Weather place); the status box shows the temperature and sky, and tapping it opens the forecast for the days ahead. It comes from Open-Meteo (no account; only the chosen place's coordinates are sent), is kept for 30 minutes and works offline from the last one.
- Launcher: the town's weather follows the real weather by default (Weather: Live forecast): rain, snow or cloud shadows over the town when it is raining, snowing or cloudy where you are. Pick a fixed look in the settings if you prefer.
- Launcher: a box at the top with the date, the time and the battery (it can be turned off).
- Launcher: the Pokémon have much more ground to walk, from just under the top boxes to behind the bottom ones. Tap the screen three times quickly and every Pokémon strolls over to the main screen, one after another, where it can be touched.
- Launcher: the page markers show only while you swipe. Holding the town no longer opens the town picker; it is in the settings, with a new Gestures group (drag in for apps, swipe for Today, three taps to gather) that turns each one off.
- Launcher: app icons are squares with pixel corners and a pixel border instead of the phone's round or squircle shape.
- Sound: separate volumes for sound effects and for the Pokémon's cries, in Settings and in the launcher settings (a save from before follows the effects volume until you change it).

## 0.73.0

- Sound: only the moments of the game make a sound now (an egg, a rank up, a berry, a focus session starting or ending, the lock when Home is pressed during focus, notifications). Taps, tabs, menu rows, the app list, pinning, long presses and the Pokémon's meetings are silent.
- Launcher: the town is two screens wide. Its Pokémon wander over to the Today page and back, and can be touched, long-pressed and meet one another only on the main screen. The page swipe has its own momentum and rubber-band edges.
- Launcher: the app list has more room: wider gaps between the search, the category chips and the list, full-height touch targets, and more space around each app.
- Calendar: the month and year are centred.
- Launcher: the Today page is dimmed less, so the Pokémon wandering behind it can be seen.

## 0.72.0

- Launcher: weather over the town, from the real Mystery Dungeon effects: rain with splashes, snow, a blizzard, a sandstorm and the shadows of drifting clouds, set in the launcher settings (Weather). It is only a look, not the real weather, and it never takes a touch; it stands still with Reduce motion.
- Launcher: three more status icons for the Pokémon's meetings: a yellow flash when both are startled, a blue drop for the nervous one in a quarrel and a white sparkle when a greeting ends happily.
- Launcher: more room everywhere. The dialogue boxes keep the design system's own padding inside their frames (24 dp sideways, 18 dp above and below), the home page and the settings and Today pages have the page margin and wider gaps between boxes, and pinned apps have more space around them.
- Assets: `assets/weather` (cut by `tools/sprites/cut_weather.py`) and three more icons in `assets/status`.

## 0.71.0

- Launcher: a second page beside the town, Today: a month calendar (Prev and Next change month, a dot marks days with plans) and the chosen day's agenda, events first and then its tasks with a box to tick. It reads PokéPlan's own planner, so it needs no permission. Swipe across to it, press Home or Back to return; two blocks at the top show which page you are on.

## 0.70.3

- Launcher: the app list comes in from the right again, but only a short glide (56 dp) while it fades in and its blur clears, on the app's smooth curve (a flick still keeps its speed and settles on a spring).

## 0.70.2

- Launcher: the app list no longer slides in. It comes into focus where it is: it fades in, rises a few dp and its blur clears, following your finger when you drag in from the edge (a flick still keeps its speed and settles on a spring). Closing it is silent now.
- Launcher settings: the sound volume is ten blocks you tap or drag across ("7 of 10", since the font has no % sign), lined up with the menu lines' text; the Back line has no stray arrow character.

## 0.70.1

- Launcher: the Pokémon's "?" and "!" icons sit just over their heads (they floated far above them).
- Launcher: Pokémon meet less often (one meeting at a time, about every half minute or more, and the same two not again for three minutes), and when they do they step up to each other, stand side by side and turn to face one another.
- Launcher: the settings are a screen of their own, with Back, instead of a pop-up.
- Launcher: a sound volume slider in the settings (the same level as Settings → Sound), which plays a blip when you let go.

## 0.70.0

- Sound: the app and the launcher now have their own sounds, from the Mystery Dungeon sound effects: a cursor blip for tabs, menu cards, settings rows and the letter scroller, confirm and back for buttons, whooshes for the app list, a long-press tone, pin, unpin, hide, move and uninstall sounds, a buzz when Home is pressed during a focus session, and sounds for starting and ending a focus session, a hatched egg, a rank up, ticking a habit, a calm break, feeding a berry and a notification.
- Sound: Android plays the short sounds through a SoundPool, so a run of taps or the letter scroller no longer cuts itself off. The focus, task and routine notifications use the game's notification sound (their channels are renamed, so every install gets it).
- Launcher: the town's Pokémon meet. Two that walk up to each other stop and react with the Mystery Dungeon status icons over their heads, a "?" and a "!" (a greeting), a red X and a "!" (a quarrel) or two "!" (both startled), with the matching emote sounds (at most one every few seconds). At night some of them lie down with a Zz over their heads. Reduce motion turns it all off.
- The buddy has a Zz over its head while it sleeps through a focus session or a calm break.
- Motion: one set of easing curves (`Ease` in `ui/Ease.kt`) replaces the default ones: smooth for almost everything, in-out for the idle loops. Every button, menu card, tab and launcher row shrinks to 98% while pressed. The launcher's dialogue boxes and sheets rise 6 dp as they fade in (no blur, which would smear the pixel art). The app list follows your finger with real physics: a flick keeps its speed and settles on a spring, and pulling past the open end stretches it a little and lets it spring back. All of it stops under Reduce motion.
- Assets: `assets/status` (the status icons, cut by `tools/sprites/cut_status.py`) and 38 more sound effects (`tools/sprites/convert_pmd_sfx.py`).

## 0.69.0

- Launcher: the app list now comes in from the right. Dragging in from the right edge pulls it across with your finger and it settles open or shut when you let go; All apps and Close slide it the same way (it stays put under Reduce motion).
- Launcher: the app list's letters, names, categories and buttons are in the dialogue font.
- Launcher: the town's Pokémon have more room to walk (the ground is taller) and no side walls: one that walks off an edge comes back in at the other.

## 0.68.0

- Launcher settings: All apps has a Settings button that opens a dialogue box with the town (the picture or the scene), how many Pokémon live in it (8, 16 or 24), whether they walk about, whether the status box shows, whether dragging in from the right edge opens the app list, your hidden apps (opens the list on them), forgetting your pins and icons, and the Android home-app setting. All of it stays on the device.
- The app's cards are a little see-through (80% solid), so the page behind them, the day gradient or the scene you chose as the background, shows faintly through.

## 0.67.0

- The whole app uses the game's dialogue box as its card: cards, list rows, menu cards and the Home region cards are drawn with a thin version of the launcher's frame (a pale hairline, a blue band, an inner line, rounded corners) around the usual surface, in the light and the dark theme. Each module has its own colour: Plan's pages and cards are red, Care's green, and the menu cards wear their own tint (the coloured bar down the left edge of the Home cards is gone).
- More of the Pokémon dialogue font in the app: screen titles, card titles, section labels, buttons and tab labels use it (dark on light pages, white on the dark theme and on coloured bars). A title it cannot draw (another alphabet, a mark it lacks) stays in the pixel font. Long text keeps its readable font, and the see-through black fill stays in the launcher.
- Nunito is gone: the whole app is in the Pokémon DS-era pixel font (secondary text is 14 sp). More room inside the boxes and buttons (the text sits about 9 art pixels in from the frame, as in the game). Buttons are redrawn to match the boxes: stair-stepped corners, an outline, a one-pixel lip under a solid key that sinks when pressed, and an even inset around a centred label.

## 0.66.1

- Launcher: the dialogue boxes are drawn like the game's menu boxes: a pale hairline, a thin blue band at the top and bottom that is thicker at the sides, a deep-blue line, rounded corners, and a see-through black inside so the town shows behind the words. The text in them is in the Pokémon dialogue font only, centred in its line, and every line starts the same distance in. Long names are cut with "..." and a mark the font lacks becomes a look-alike or "?".
- Launcher: the home page is boxes over the town: a status box at the top (companion and collection), a navigation box with three places and their icons (PokéLife, Pokémon, Me), and a box of your pinned apps with logos, then All apps. The task and the Start focus button are gone from the home page; Plan, Focus and Care are one tap away inside the app.
- Launcher: a pinned app shows its own logo, or a game item you pick (press and hold the app, then "Icon on the home page"). Dragging in from the right edge opens every app, like Niagara; All apps still does the same.

## 0.66.0

Polish and states (phase 3 of the design audit).
- Buttons sink 2 dp while pressed. Scenes and the launcher's town fade in instead of popping in.
- A calm `Notice` for the sync status and sign-in messages in Settings (blue for what is going on, amber for what needs a look, red only when something was not saved).
- The dark page is a smooth gradient; the dithered grain is for the light page only.
- Launcher: the town's Pokémon stop walking while the launcher is out of sight, and press and hold a Pokémon for a menu: its name and level, Open profile, and Make buddy.

## 0.65.0

Tokens and consistency (phase 2 of the design audit).
- Spacing and colours come from the design tokens: no 6 dp or 10 dp gaps, touch targets of 48 dp, and the category colours (violet, pink, teal, amber) and image scrims named once.
- Home is lighter: the "Regions" label is gone, and Care no longer repeats the same Pokémon portrait as Plan when it has no lead of its own (it shows its own icon).
- Menus: a lone last card spans the row instead of leaving a gap. The Pokémon menu's Party card has its own icon (Premier Ball) instead of repeating the Poké Ball.
- Selected chips are blue everywhere (the focus length buttons were black). Focus: the four lengths show first and "Another length" opens the -5 / +5 stepper.
- Small labels use the body font: the party card names and perks line no longer use the pixel font at 13 sp.

## 0.64.0

Accessibility, contrast and layout (phase 1 of the design audit, `docs/DESIGN_SYSTEM.md`).
- The brand red is a little darker so white text on it passes AA (4.8:1); the rank bar's EXP tag and the header subtitles are now plain white (they were about 1.4:1 and 3.5:1).
- Home's region cards no longer cut a long task title: at large text sizes the button goes under the words.
- Keyboard users see where they are: every button, tab, row and choice shows a blue focus ring.
- Wide windows: from 600 dp the page is a phone-width column in the middle; from 840 dp the four tabs move to a side rail.
- Launcher: the dialogue text now has content descriptions (a screen reader reads each line), menu lines are 48 dp, an empty pinned list says how to pin, and All apps has a visible "Town" button for changing the town.

## 0.63.0

- Launcher home: a clearer hierarchy, in three layers. A small strip at the top names your companion and its level on the left and the size of your collection on the right; the town fills the middle; one dialogue box by the thumb has today's task (with how many are left), a red Start focus button as the main action (green "Back to focus" while a session runs), then Plan, Care, Party and Pokémon, your pinned apps and All apps.
- Every panel now has the same blue frame; the green outline is gone, so colour means something (the red main action, the green running session). Task and app names are in the readable body font; the dialogue font is kept for labels.

## 0.62.1

- Launcher: the home page's text is one dialogue box, not several, with even padding all round. Inside: the buddy and your task, a row of shortcuts (Plan, Focus, Care, Party, Pokémon), your pinned apps in two columns, and All apps, divided by thin rules. It is shorter, so the town's Pokémon stay in view.

## 0.62.0

- The launcher's home page uses the game's dialogue boxes: your pinned apps and the parts of PokéLife (Plan, Focus, Care, Party, Pokémon) are two menu boxes side by side, the buddy and your next task sit in a message box, and All apps is a small box below, all in the Pokémon dialogue font at the same pixel scale as the town. Names the font cannot draw (other alphabets, symbols) fall back to the app's own font.

## 0.61.0

- Launcher: pressing Home now always returns to the home page, from the app list, the long-press sheet or the town picker (it used to stay in the app list).
- Launcher: more categories in the app list. Most apps declare none, so apps are also sorted by the words in their name (Messages and calls, Money, Health and fitness, Shopping, Tools and system join Games, Social, Music/video/photos, Productivity, Maps and travel, News); system apps with no match go under Tools and system. You can still move any app.
- Launcher: the home page is less about Plan. It shows the buddy, one task to tick off and a row of shortcuts to Plan, Focus (or Back to focus), Care, Party and Pokémon, above your pinned apps.

## 0.60.0

- The whole app can use a scene or a dungeon town as its page background: Settings → Look → Background → Change shows small pictures of Day (the default time-of-day gradient), the 11 scenes and the 17 dungeon towns. A veil of the page colour sits over the picture so the text stays readable, in the light and the dark theme. The choice is a setting in the save, so it follows you across devices.

## 0.59.0

- The launcher's town can be a dungeon: 17 towns laid out from the tilesets of Mystery Dungeon dungeons (Lush Prairie, Apple Woods, Drenched Bluff, Craggy Coast, Crystal Cave, Dark Hill and more), with a wall band along the top, a pond and wall clumps, and every Pokémon walking the open ground. Pick one under "Dungeons" when you press and hold the town.
- One pixel grid: the tiles, the Pokémon and where they stand are all drawn at the same whole-number scale without smoothing, and the scene backdrops are scaled by whole numbers too. A flipped Pokémon is flipped pixel for pixel.

## 0.58.0

- The Pokémon of the launcher's town now walk: towards you, away from you, or to the side (mirrored for left), with their own Mystery Dungeon walk animations, instead of sliding about facing front. A form without a walk animation keeps its idle one.

## 0.57.0

- The launcher's town can be set in any of 11 real Pokémon scenes (meadow, city, route, forest, mountain, beach, desert, thunder plains, ice cave, damp cave, volcano cave): press and hold the town and pick one. The choice stays on the device with the other launcher settings, and the app list blurs the same scene.

## 0.56.0

- The launcher's home page is now a town: a real Pokémon scene where every Pokémon you own (up to 24, the buddy and the highest levels first) wanders with its own idle animation; tap one for its cry and a hop. They stand still with Reduce motion. Tasks, Focus and pinned apps sit on a soft dark fade at the bottom; the app list opens over the same town, blurred and darkened.
- Fix: the app list showed only a few apps until PokéLife was chosen as the home app (Android 11 and later hide apps from a launcher that is not the home app). It now declares permission to see every app.

## 0.55.0

- PokéLife can be the phone's home screen, opt-in: nothing changes until it is chosen as the home app in Android's settings (the home page has a button that opens them). The home page shows the buddy, today's tasks with a Done key, a Focus button and pinned apps, on a Pokémon pixel wallpaper; a swipe or tap up opens every app A to Z over the wallpaper, blurred and darkened, with search, a letter scroller, categories (the one each app declares, with "Other" as the fallback) and work-profile apps.
- Long-pressing an app pins it, hides it (a Hidden chip brings it back), moves it to another category, opens its app info or uninstalls it. Pins, hidden apps and moved apps stay on the device and are never synced. The launcher pays nothing.
- Strict focus: pressing Home during a focus session brings the Focus screen back.
- Widget hosting comes in the next launcher release.

## 0.54.0

- The app icon has a themed version: on Android 13 and later, with Themed icons on, the launcher tints the egg's outline, check and stopwatch in the wallpaper's colours. It is the real icon with its light fill let clear, nothing redrawn.
- Long-pressing the app icon offers four shortcuts: Add task, Start focus, Care and Party (the Pokémon tab opens on the party, every region's party on one page).

## 0.53.0

- The PC is now boxes of 30, like the games, with Previous, Next and "Box 2 of 5" above and below the grid. Each Pokémon shows its name under its sprite (cut short with … when long) with its level, ✨ and ★, and the sort and filters sit in a quiet button; any new search, sort or filter starts again from the first box. One PC serves every region's party.
- The Pokédex is an index: a row per species with its number, sprite, name and one quiet line (Owned, Caught, Seen or Not found yet), hairlines between rows instead of a card each, a ✨ when a shiny was found, and a Poké Ball for ones in your party or PC. A search box finds a species by name or number (a species not found yet matches by number only, so its name stays a secret). The progress card is a number, a bar and one quiet line.

## 0.52.0

- The Party page shows every region's party at once: Plan's, then Care's, each under its region's name and colour with its lead first and its perks after, instead of a switch between them. Each Pokémon is one tidy row: its sprite, its name, and its level and types, with Buddy or Lead, Starred and Shiny at the end.

## 0.51.0

- A Pokémon's page reads in a clear order: who it is (its Pokédex number, species, level and types), its traits (nature with the stat it raises and lowers, ability, gender, shiny, and the IV judge with its best stat), how it is growing (Rare Candies, friendship and how it likes to bond, stats with the nature's ▲ and ▼), its evolution, its role (party, lead or PC, and which region's Pokédex it counts for), and where it came from. Transfer sits quietly at the end.
- Pokémon remember where they came from: your first partner, a Focus, Task or Care Egg and the region it hatched in, a Care visitor you befriended, or a quest, and the day you met. Pokémon from before this version show the day only.
- The page holds up at large text sizes: names no longer wrap beside Rename, and stat names no longer get cut off.

## 0.50.0

- Each region has its own look. Plan is a town: your buddy now stands on a red-brick street on Plan's page, on Home and in the widgets, whatever its type. Care is a meadow, with green headers on its pages. On Home each region's card shows its lead in its own scene, with the region's colour down the edge. Pokédex entries still show each species in the scene of its type.

## 0.49.0

- Home is now an overview of your whole world: a card for each region (Plan and Care) with its lead, how today is going there and the one next thing to do, then your streak, mini-quests and what happened recently. Tap a region's card to open it, or a recent hatch to see that Pokémon.
- Your buddy's scene, petting and berries, and the Next up card with Focus on this and Pick for me, are on Plan's own page (Modules, Plan), next to today's tasks.

## 0.48.1

- Shinies are back to 1 in 128 per egg (0.46.0 made them 1 in 192). With eggs coming more often since 0.46.0, you'll find shinies a little more often than before. Reward rules version 13.

## 0.48.0

- Moving around feels smoother: the bottom tabs fade into each other, and opening a module, a section or a Pokémon slides it in a little, with Back sliding it out the other way. Coming back to a tab or a list finds it scrolled where you left it. Reduce motion (Me, Settings) switches screens instantly.

## 0.47.1

- A save that cannot be read is now kept aside (as `save.json.unreadable-…` or `care.json.unreadable-…` beside it) instead of being overwritten when the app starts fresh, so progress and Care's diary can still be recovered. A widget catching the save mid-write no longer empties Care.
- A cloud save this version cannot read leaves the device as it is and says so in Settings, instead of stopping the sync (Plan) or reporting success (Care).
- The evening streak reminder counts the streak the way the app does (the day turns over at 4am, and perks forgive missed days).
- Quick add no longer fails on "0m" or a huge number: a duration counts from a minute to a day, anything else stays in the title.
- The bag no longer offers a Tera Shard to a Pokémon that already has its type, and the widget feeds the buddy the same berry as the app.
- Under the hood: the calendar, item and tally rules moved out of the app store into their modules, the largest screens are split by feature, and the Android reminders share one set of helpers.

## 0.47.0

- The app is now called **PokéLife**: a Pokémon world for real life, where each module is its own region. PokéPlan lives on as the name of the Plan module. Your Pokémon, save and cloud sync carry on as before.

## 0.46.0

- Much more candy, and a Pokédex that fills faster. Levels cost half as many Rare Candies. Tasks pay twice the Rare Candies, and Task Eggs come twice as often. Focus sessions now pay Rare Candies too (10 for 25 minutes), so do mini-quests (2 each, 5 more for all three), and the first of every species brings 5 Rare Candies and a Super Candy so it can evolve. Eggs favour species you haven't caught yet even more.
- Coins finally buy something: the Bag has a shop where 5 coins buy a Rare Candy.
- Care gives Pokémon too, not just visitors: every routine, calm break and skill fills a Care Egg meter (on the Care page), and a full one hatches a Care Egg from the Care Region into Care's party. Care moments also pay Rare Candies (3 to 4 each, 15 for a weekly rhythm).
- Good Pokémon stay special: with eggs coming more often, the shiny odds are now 1 in 192 per egg (were 1 in 128), so you'll find about as many shinies as before. Reward rules version 12.

## 0.45.1

- Mini-quests answer when you tap Done: the quest turns to "Done" straight away and the card says what you got (the berry, and the bonus one for all three). Before, the card did not redraw, so Done looked like it did nothing. The welcome-back gift had the same problem and is fixed too.
- Mini-quests now turn over at 4am like your streak, so a quest done late at night stays done after midnight.
- "Pet your buddy" ticks itself when you pet your buddy, and "Different ones" always gives three new quests.

## 0.45.0

- More time with visitors. Every visitor can now have its own visit each day, once you've done something in Care, so you can care for all of them, not just one. Up to four stay in the meadow at once, and the two warmest stand beside your Care buddy: tap one to pat it.
- Care for visitors with your hands, not just buttons: tap where to pat (its head, under its chin, or its back), drag a treat or berry to it, press and hold it to sit together, swipe across the meadow to tidy its spot, or pick something kind to say. Tap it when it reaches out to you. A small counter shows how far the visit is. Every gesture also works with a screen reader.
- Play with any visitor any time: pat it, give it a treat, or play tag as it bounds to wherever you tap. Playing is just for fun and never counts as a visit.
- A new friend is now usually an Egg of its family's first stage, like an egg from the Day Care (a Pikachu leaves a Pichu egg), which hatches at once. About 1 friend in 10 stays as itself, with a high friendship. Reward rules version 11.

## 0.44.0

- Only the Plan and Care Regions now. Kanto, Johto, Hoenn, Sinnoh, Unova, Kalos, Alola, Galar and Paldea are gone from the hatching region picker and the Pokédex tabs, and ranks no longer open them; the National Dex tab still lists every species. Pokémon you already hatched from them stay yours, and if one of them was your hatching region, eggs now come from the Plan Region. Legendary quests no longer ask you to catch a share of those regions.

## 0.43.1

- Care's perks now open at rank Trainer, together with Plan's party perks: before then, Care's party panel and its card in Pokémon, Party say which rank shows them. They already count, as Plan's do.

## 0.43.0

- Plan and Care pages now match. Both open on their buddy in its scene, then the whole party in six equal slots (the lead outlined and named Buddy or Lead, free places shown as a faint Poké Ball), the party's perks, today's tasks or routines, and the sections. Tap a party member to hear its cry and see it hop; hold it to open its page. Plan's perks say which rank opens them (they already count before that). In Pokémon, Party, the Plan and Care tabs now both show a perk card, and Care's has "Where they come from" too. Smaller fixes: "1 entry" and "1 routine today" instead of "1 entries" and "1 routines today", and a gap under the party tabs closed.

## 0.42.0

- Care feels more like Pokémon. Care opens on your Care buddy in the meadow, just like Home: pet it, give it a berry, and it cries out and hops when you finish a routine, a calm break or a skill. Your Care party stands below it; tap one to hear its cry. Visitors move with their own animations and cry when you tap or visit them. A favourite kind of care gets their cry, a treat the healing chime, and a new friend the Pokémon Center tune. After a calm break your buddy wakes with a stretch and its cry. Ticking a routine, practising a skill and saving a diary card play the menu blip. Sound and Reduce motion settings are respected.

## 0.41.0

- Visitors like berries. "Offer a treat" during a visit can now be a berry from your bag. Each kind of Pokémon likes one flavour, the same every time; find it by trying. The first time a visitor gets the berry it likes, it counts as an extra warm day, so a friend can come a day sooner. Any berry you offer is eaten. This finishes Care's first version.

## 0.40.0

- A Care widget for the home screen: today's routines with a tick (ticking one there counts and pays just as in the app), the ones already done, and whether today's diary card is saved, in the meadow. Tap it to open Care.

## 0.39.0

- Gentle routine reminders on Android. A Care routine with a time now sends one quiet notification at that time on its days, unless it is already done, with Done (ticked off and paid just as in the app) and "In 30 min". It never repeats, and it waits for the end of a focus session. Switch them off in Me, Settings, Modules, "Routine reminders". Routines with no time never remind.

## 0.38.0

- Care can keep a copy in the cloud, if you want it to. In Me, Settings, Modules, "Care copy in the cloud" is off by default; signed in and switched on, your routines, diary cards and journal go with your account (on their own, apart from your Plan save, and never to friends or leaderboards), so another phone can have them. If two phones both changed, you choose which to keep. Switching it off, or erasing Care's data, deletes the copy. Needs the new Supabase migration, the `care` schema exposed in the API settings, and a redeployed reward function.

## 0.37.0

- Visits finished. The visitor now stands in the meadow during a visit and answers with its own animation (a hop for its favourite, eating for a treat; still with Reduce motion). A gentle pat asks where: each species likes one of head, back or under the chin, and finding it warms once more (a wrong spot costs nothing). Two more bids, a little hop and curling up closer. A "Met" row shows every visitor you have cared for, friend or not, with no counts.

## 0.36.0

- Befriending is now a visit instead of a button. Tap Visit on a visitor and care for it three ways from five (kind words, sitting together, a treat, tidying its spot, a gentle pat, after Chapman's love languages). Each visitor has a favourite you find out by trying; every kind of care warms it, the favourite warms more, and a kind it already had adds nothing. After the first and second action it may make a small bid (it looks up, nudges your hand, brings you something) and noticing it adds a little warmth. A visit with 4 warmth counts as a day; a quieter one counts as none and costs nothing. Four warm days still make a friend, one visit a day, and no rewards changed.

## 0.35.1

- Fixed the "Just 5 minutes" button on the Focus screen: it was drawn on top of the Start button, so one hid the other. They now sit one above the other.

## 0.35.0

- One party per module. The Party screen now has a Plan tab and a Care tab over the one shared PC: a Pokémon is in one party at a time, up to six each, and Care's party has its own lead, the buddy that takes Care's friendship (a Pokémon's page has "Make Care lead" and "To Care party"). Care's party has its own perks (Friendship gain from Normal, Fairy and Water; Berry finder from Grass, Bug and Fighting; Visitor luck from Flying, Psychic and Ghost, used when Care visitors arrive). Until Care has a lead, Care keeps warming your Plan buddy, and Plan's rewards are unchanged.
- Reward rules versions 8 and 9: the party a Pokémon is in, and Care's lead and perks. Needs the new Supabase migrations (`party_ids`, `care_lead`) and a redeployed reward function.

## 0.34.0

- Care, the second module, under Modules, inspired by journalling and DBT: small routines you tick off each day, a 1 or 2 minute calm break with your buddy asleep beside you, a daily diary card (how strongly you felt six feelings, any urges you choose to track, the skills you used), a library of DBT skills (Mindfulness, Distress tolerance, Emotion regulation, Interpersonal effectiveness) with short how-tos, and a journal with a question for each day if you want one. Gentle trends, no streaks. Routines, calm breaks and practised skills earn friendship and berries; a diary card or a journal entry never earns anything, and everything stays on this device. Care is on for everyone and can be switched off in Me, Settings, Modules.
- Reward rules version 7: practising a skill pays friendship (doubled for Presence natures) and a berry, up to 3 skills a day.
- The Care Region opens to everyone: 143 gentle Pokémon to hatch, from the first day, with no rank needed.

## 0.33.0

- A new Modules tab, ready for Care and the modules after it. The tabs are now Home, Modules, Pokémon and Me: Home, Pokémon and Me hold what every module shares (your buddy, party, PC, Pokédex, Bag, trainer card, friends and settings), and each module has its own place under Modules. Plan is the first: Planner, Focus, Growth, Goals and Review are under Modules, Plan. The Modules tab remembers what you had open, and widgets, shortcuts and reminders still open the Planner and Focus directly.

## 0.32.0

- The Care Region's dex is chosen: 143 gentle, soothing Pokémon (Chansey, Snorlax, Munna, Comfey, Milotic, Wooloo and more), with Cresselia and Shaymin as its patrons and Goomy and Slakoth as its Super Rare lines. It opens to everyone when the Care screens arrive. Swablu, Altaria, Cottonee and Whimsicott move there from the Plan Region; the ones you already have stay yours.

## 0.31.1

- The Home Region is now the Plan Region, the first of the module regions. Your saves, dex progress and focus history carry over unchanged. The name "Home Region" will belong to the household module's own region later.

## 0.31.0

- Pick for me on Home's Next up card: it picks one of today's tasks for you, and finishing that one pays 10% more candies.
- Daily mini-quests on Home (from Apprentice): three small things a day, like a glass of water or a stretch. A berry each, one more for all three, and one reroll. "Open the task you've been avoiding" starts 5 minutes of focus on your oldest task.
- Just 5 minutes on the Focus screen, for when starting is the hard part.
- Tasks you focused on show how long they took against your estimate, and Growth tells you how your estimates run.
- Rest today: mark a rest day from Home's progress card; your streak waits for you.
- Welcome back: after a week or more away, a gift of Rare Candies and berries, and no scolding.
- The streak day now ends at 4am, so a late night still counts for the day before. Finished work warms your buddy's friendship up to 10 times a day. Reward rules version 6.

## 0.30.0

- Notifications on Android that the design promised. Focus sessions of 15 minutes or more send a quiet "halfway there" note, and every session sends a "time is up" note; neither counts as leaving the app, and both are cancelled if the session fails or is given up. In the evening (20:00 by default) a gentle streak reminder appears if nothing has counted yet today and you have a streak going. On Sunday evening a weekly recap lists tasks finished, focus time and your top category. A task reminder you do not answer comes back up to 3 times, 10 minutes apart, then stops, and reminders now offer a 10 minute snooze (Done, 10 min and 1 hour: Android shows three buttons, so "Tomorrow" left the notification). Each one can be switched off under Settings, Notifications and week.

## 0.29.0

- Editing a task now also sets its category, its notes and its deadline (a day, with Today, Tomorrow, Next week and a day-by-day nudge, or none).
- A task can repeat: daily, on weekdays, weekly or monthly. Pick Repeat in a task's options, or in More options when you add one. Repeating tasks say so in their line, and Never stops the repeat.
- Planner has a new tile, All tasks: search titles and notes, and filter by status, category, list and difficulty.
- New habits can be on chosen days, a number of times a week or a number of times a month (not only every day), and have a category.
- New goals can have a category and a target date, both shown on the goal.
- The weekly review shows where the week's finished tasks went, by category.
- Fix: a repeating task's later days pay only once their day comes, so ticking ahead earns nothing extra.

## 0.28.0

- Trainer ranks come quickly at first and slow down later: Apprentice after a day or two of play (it took over two weeks), Ace in under two weeks, and each rank after that takes longer than the last. Nobody loses a rank.
- Each rank now opens something, shown once on Home in a "Rank up" card: evolution, the Bag and goals at Apprentice; held items, party perks, Growth and coins at Trainer; training items and stats at Ace; Guardians and legendary quests at Veteran. Day one stays simple: tasks, focus, your buddy and your collection. Reward rules version 5. Online: push the new migration (`rank_curve`) and redeploy the reward function.

## 0.27.0

- Training plans are gone: no more choosing focus stats. Finished tasks no longer add EVs; Vitamins from the Bag are how a Pokémon's stats (and the perks they power) grow. Synchronize now gives a little Super Candy chance. Reward rules version 4. Online: push the new migration (`no_training_plans`) and redeploy the reward function.

## 0.26.0

- Party perks you can see: the Party screen shows each perk's total and, opened, where every part comes from (your buddy's types and ability, its held item, the rest of the party, Guardians). Each Pokémon's page says what it gives as your buddy and in the party.
- Held items work: give one from the Bag or a Pokémon's page, swap or take it back. Its perk applies while your buddy holds it, and items like a Metal Coat (now found from tasks) let trade evolutions happen.
- Nature Mint, Bottle Cap, Ability Capsule and Tera Shards can be used from the Bag.
- Magic Guard now keeps its promise: once a week, a failed focus session of 5 minutes or more still hatches its egg. A strong Streak keeper perk forgives a second missed day a week. Reward rules version 3.

## 0.25.0

- Behind the scenes, the Care module's first part (no screens yet): self-care routines, 1 to 2 minute calm breaks, private mood check-ins with an optional note and tags, gratitude and reflection prompts, and rolling trends with personal bests instead of streaks. Care keeps its own save, apart from Plan's. Doing earns: a routine grows your buddy's friendship, a calm break adds a berry, and care on 4 days of a week ("kept a rhythm") gives 3 berries, each with a little trainer rank, capped each day. A mood, a note or a reflection never earns anything. Reward rules version 2. Online, Care's copy is opt-in and only yours (`care.saves`); push the new migration to add it.

## 0.24.2

- Fix: undoing a habit and ticking it again the same day no longer pays twice. A habit now remembers the last day it paid, apart from the days it was done.

## 0.24.1

- Behind the scenes, before a second module: the cloud save is now Plan's own (`plan.saves`), so the shared game tables hold only the game; every reward the server records says which version of the reward rules made it (rules version 1); and `docs/economy.md` lists every reward source, use and cap. Nothing changes in the app. To take effect online, push the new migration, then redeploy the reward function.

## 0.24.0

- Your own events can repeat: daily, on weekdays, weekly, every 2 weeks, monthly or yearly, timed or all day ("Gym" every Monday, "Home" every weekend). Pick it under Repeats in New event, or tap an event to change it. A repeating event never ends unless you set an end, then ‹ › moves the end one occurrence at a time. Changes in the event panel are made to every one; dragging one, or tapping another hour, moves just that one. Delete asks whether to delete this one or all. Changing how a later one repeats leaves the earlier ones as they were.

## 0.23.0

- More Pokémon sounds. The buddy cries when Home celebrates a real event (a task done, a hatch, a shiny) and when you make a Pokémon your buddy. Five real game sound effects (from the same package as the display font, in `assets/sfx`) each mark one kind of moment: the menu select blip when a task is ticked off, a hop for each rock of a hatching egg, the healing chime when a berry or item is given, the run-away sound when a Pokémon is transferred, and the Pokémon Center jingle after an evolution's cry. They follow Sound and its volume in Settings, play beside a cry without cutting it off, and are silent on desktop as the cries are.

## 0.22.0

- A Month view in the Calendar (Day / Week / Month), after TickTick and Google Calendar: events over several days are one solid bar across them, one-day all-day events are short bars, and timed events and tasks sit on their day. Tap a day to see its agenda under the grid, and Open day to plan its hours. In the Week, all-day events now run across their days as one bar too.
- Your own events can be all day, over one day or several ("Home" for a weekend, "WFH" for a day): pick All day in New event, then its first and last day. Every event can have a colour. Tap an event's bar to change it.

## 0.21.0

- Lists in the Planner, after TickTick. Make a list for each part of your life (a name, a colour and a real item icon) from the new Lists section under the menu's tiles. The Inbox is now every task that isn't in a list, whatever its date. A list and the Inbox group their tasks by when they are: From before, Today, Tomorrow, Next 7 days, Later and No date. Add straight into a list, type `#Groceries` in any quick-add, or move a task from its options. In Today and Next 7 days (was Soon), each task shows its list's colour and name. Lists can be renamed, recoloured, archived or deleted (their tasks go back to the Inbox). The Calendar's "To plan" card now calls tasks with no date "No date".

## 0.20.0

- The Pokédex tracks forms. Each caught Pokémon's entry lists all its forms (regional variants, female forms, Megas, Gigantamax and more) with a Regular / ✨ Shiny switch. Forms you have had are in colour, the rest are black shapes. Tap one you have had to see it in the scene with its own types, abilities and stats. A form counts once you have had a Pokémon in it (a Mega while it holds its Mega Stone, a female form for a female) and stays counted after a transfer. Gigantamax and other special forms can't be found yet, so they are faded and don't count. Each region's list shows how many of its forms you have.

## 0.19.5

- Each caught Pokémon's Pokédex entry now shows it in a real scene for its type, the same ones buddies get on Home: Water Pokémon on the beach, Grass and Bug in the forest, Electric on the thunder plains, and so on. Species you have only seen keep the plain wallpaper, so the scene never gives their type away.

## 0.19.4

- Pokémon in the Pokédex no longer lose their animation (or show nothing) after you page through entries quickly with Next: a sprite whose loading was cut short is no longer remembered as missing. While an entry's animation loads, its space stays empty for a moment instead of flashing the still sprite in another art style.
- Tapping a Pokémon's sprite to hear its cry (on its page and in the Pokédex), a Pokédex tile, an evolution in an entry or a PC tile no longer flashes a grey highlight over it.

## 0.19.3

- The Planner's menu and lists are redesigned after well-regarded to-do apps (Apple Reminders' smart lists, Things' Today and Logbook, Todoist's Upcoming). The menu is one "Add a task" field (the day chips show up once you type) above six smaller tiles that each show their count, so all of them fit on one screen. Today shows how the day is going with a thin bar, then the tasks from before with a "Move to today" button, today's tasks, and "This evening". Soon is grouped by day and Done by the day you finished each task. Rows in a group share one panel and no longer repeat the date the group is already on.

## 0.19.2

- Your buddy no longer shrinks for a moment when you pet it or give it a berry: its hop and bite play at its usual size. The scene on Home is a little taller so the whole jump fits. Tapping a Pokémon in your party no longer flashes the row.

## 0.19.1

- The Calendar in the Planner is redesigned after well-regarded calendar apps (Fantastical's day ticker, Apple and Google Calendar's grid, Structured's inbox). The day or week and two arrows sit on one line, Day and Week are one switch, and a "Today" button appears once you move away. A day opens with a strip of the week's days, a dot under each day that has plans. Tasks still waiting for an hour share one "To plan" card, each a chip with its icon that scrolls sideways, so the hours start much higher up. The hours sit on their own panel with labels on the lines, a faint half-hour line, a red now-line with a dot, and all-day events in a band at the top. Blocks have a coloured strip on the left, a bolder title and their start and end time; in a week they show their title on one line when there is room, never broken letter by letter.

## 0.19.0

- Easier to use, after a review of every screen against the Laws of UX (`docs/laws-of-ux-review.md`). Back now sits at the top left of every page, with the page it returns to written under the title. Settings are switches you can tap anywhere on the row, sorted into small groups (Focus, Sound and touch, Look, Reminders and week). Undo for a finished or deleted task now floats just above the tabs, near your thumb, on every tab, so nothing on the page jumps when it appears. Focus keeps a "Start 25 min" button pinned at the bottom, picks the task to work on with radio rows, keeps Give up quiet, and after a hatch offers "Meet" the new Pokémon. The keyboard's Done key adds a task, a step, a habit, a goal or a milestone. Overdue tasks are marked in calm amber instead of red. Empty lists show their item icon, goals say how many milestones are done, the Bag shows a Use button on items you can use, Transfer and removing a friend stay quiet until you confirm, and the berry button on Home is easier to hit.

## 0.18.0

- Eggs hatch like in the games: "Oh?", the egg rocks three times, harder each time, a flash, and your new Pokémon is there and calls out, with its category ("the Seed Pokémon"). Shinies flash gold, twice. It plays once for every new egg, from a focus session, a Task Egg or a task ticked on the widget; tap or Skip to jump ahead, and Reduce motion goes straight to the Pokémon.
- Evolving plays its own scene: "What? Charmander is evolving!", the Pokémon turns white and flickers between its old and new shape, faster and faster, then a flash and "Congratulations!".
- Each Pokédex species has its own page: its category, height, weight and entry, its types, abilities (the hidden one marked), base stats, and its evolution line with how each step works in PokéPlan (Lv. 16 and a Super Candy, a Fire Stone, a Link Cord...). Tap its sprite to hear its cry, a member of its line to open that one, or Previous and Next to flip through the list. The list itself has a completion bar and All, Caught and Not caught filters, and keeps its place when you come back.

## 0.17.1

- Easier to read and tap: green and blue text is a little darker so it stands out on cards (and red a little lighter in dark mode), every button is at least 48 dp tall, and the starter picker, PC tiles and the opening dialogue tell screen readers what they are and what a tap does.

## 0.17.0

- Clearer numbers under Me: the trainer card says which totals are all time and how the streak counts. Growth shows the last 7, 30 or 90 days, with each area's finished tasks and focus minutes beside its share (quiet areas are listed on one line instead of as empty bars), weekly bars for tasks and for focus with the week under each bar, and your goals' progress kept apart from tasks and time. Weekly charts follow your week start setting.

## 0.16.1

- Focus keeps your egg when you lock the screen or a call comes in, as it always should have: put the phone face down and the timer runs on, and a call pauses the timer until you come back. Leaving the app or moving the phone in Deep Focus now counts on every tab, not only on Focus. The Focus screen spells out the rules, and the timer shows the minutes focused, with paused minutes apart.

## 0.16.0

- The Pokédex tells you where each Pokémon stands: **Owned** (in your party or PC now, with a Poké Ball on it), **Caught** (yours once, since transferred), **Seen** (a friend's buddy or a quest's Pokémon, still a grey shape but named when you tap it), or ??? for ones not found yet. Each tile says so in words, and screen readers read the number, name and state.

## 0.15.0

- Your own events: New event on Planner → Calendar adds an appointment or a block of time that isn't a task, like a lesson or lunch. Give it a name, tap an hour (or nudge it by 15 minutes) and Add. Events sit on the hours in teal, drag and resize like tasks, and count towards how full the day is; tap one to rename it, change its length or delete it. They never pay, and they sync with the rest of your save but stay in PokéPlan.

## 0.14.0

- Drag to plan: Planner → Day is now Planner → Calendar, with Day and Week. Hold a task (or just drag it with a mouse) and drop it on an hour to plan it there, drag a task on the hours to move it, or drag its lower edge to change how long it takes. Everything snaps to 15 minutes and the page scrolls as you drag. Week shows the next seven days side by side, so tasks can be dragged between days; tap a day's name to open it. Tapping still works too, and a picked task now has Shorter and Longer.

## 0.13.0

- More of the Mystery Dungeon look: your buddy curls up and sleeps on Home and the widget while a focus session runs, hops when you pet it and munches a berry you give it, all with its own SpriteCollab animations (the few that don't have one hop as before). Its portrait shows how it feels: happy in the corner of Home when a task is done, overjoyed at a hatch, awed by a shiny, determined beside the Focus timer and surprised if you leave. Each Pokémon's page shows its portrait too. The artists' credits moved to the Pokédex (tap a caught species), in smaller, fainter text.

## 0.12.0

- Your calendar on the Day timeline (Android): Me → Settings → Calendars lets PokéPlan read the phone's calendars, Google Calendar included, and you tick which to show. Their events sit on the hours next to your tasks in the calendar's colour, all-day ones above, and the time they take counts in how full the day is. They're read-only and never pay.

## 0.11.0

- A new look for every Pokémon: the Pokémon Mystery Dungeon style sprites of the PMD SpriteCollab, idling in place on Home, the widget and each Pokémon's page and standing still in lists, the Pokédex and the PC (shinies included). The few forms SpriteCollab doesn't have yet keep their old sprite. Each Pokémon's page credits the sprite's artists.

## 0.10.0

- Egg tickets: when you are signed in, your phone keeps a stock of 20 eggs the server has already rolled the dice for, so a focus egg hatches the same way with or without a connection and failing a session never rerolls one. Each sync reports the tickets you used and tops the stock back up; if you run out offline, eggs still hatch as before. (Needs the new database migration and the reward function deployed.)

## 0.9.0

- Planner → Day: a timeline of your hours. Tasks without a time (and the inbox) wait at the top; tap one, then tap an hour to plan it there, and fine-tune with +15 and -15. Tap a task on the hours to move it or take its time away. Blocks show the category's icon and last as long as the estimate (30 minutes without one), tasks at the same time sit side by side, a red line marks now, and a line above says how full the day is. Earlier and Later switch days, up to two weeks ahead.

## 0.8.1

- A cleaner interface: four text sizes instead of seven (none below 12 sp, widgets included), no coloured strips on the left of cards, nested corners that line up, commas instead of "·" separators, and shorter copy with the lines that only repeated a title or a button removed. The Music slider, which did nothing yet, is hidden until music ships; "Haptics" is now "Vibration".

## 0.8.0

- Settings → Your data: export everything as a JSON file (the share sheet on Android, Downloads on desktop, a download on the web), delete your account and its cloud data for good, or erase this device and start over. Both deletions ask twice.

## 0.7.0

- Tasks have a time: open one and pick At 9:00, 12:00, 15:00 or 19:00 and nudge it by 15 minutes; the card shows the time and whether a reminder is set.
- A reminder can be snoozed until tomorrow at 9:00 as well as for an hour.

## 0.6.1

- The Buddy widget keeps its idle animation while it reacts to a pet or a berry (it used to freeze on one picture until the emoji went away), and a still buddy (Reduce motion, during focus) stands on its shadow instead of floating above it.

## 0.6.0

- Task reminders: open a task with a day and pick up to three under Remind me (at the time, 10 minutes, an hour or a day before). The notification has Done and Snooze 1 hour. Reminders wait during quiet hours (22:00 to 07:00 by default, Settings), a focus session and rest days, and are set again after a restart. Android asks once for permission to notify.

## 0.5.0

- Pokémon evolve at their real game level (Charmander at 16, Charmeleon at 36) instead of half of it. Levels cost Rare Candies on a curve like the games' experience: 2 a level at first, more at higher levels (8 at 30, 18 at 50, 67 at 99). Pokémon that already evolved stay evolved.

## 0.4.0

- Every finished task now pays Rare Candies; a Super Candy bonus comes on top (10% for rank E up to 40% for rank A, raised by the Super Candy chance perk) instead of replacing them.

## 0.3.1

- Fix: the Buddy widget froze on a still picture after a pet or a berry (and the Today and Focus widgets could show old data for a while). Glance only recomposes a widget that is still showing, so the widgets now load their data again on every update.

## 0.3.0

- Dark mode: Settings has Match phone (the default), Light and Dark; the dithered page background turns deep navy and the Android navigation bar icons follow.
- The PC boxes have a search box and a Sort and filters panel (newest, level, dex number, name, friendship, IVs; type, shiny, starred).
- A Help screen under Me with tap-to-expand questions and answers.
- A shiny buddy glints on Home (four small stars that flash one at a time; off with Reduce motion).
- The Today and Focus widgets use the buddy's scene instead of the box wallpapers.

## 0.2.0

- The Focus widget shows a live countdown; the Today widget has a "+ Add" button; long-press the app icon for "Add task" and "Start focus" shortcuts; the app is single-instance so a widget tap or shortcut never opens a second copy.
- Quick-add chips (Today, Tomorrow, Weekend, No date) on the task box; the bag is grouped by kind; a cloud-save indicator on Home; a one-time "What's new" card after an update.
- Fix: the widget buddy could stay frozen on a still picture after a tap. An alarm now brings the animation back.
- A pixel-dithered page background with a light grain whose colours follow the time of day (dawn, morning, afternoon, dusk, night), always light enough to read.
- Shiny set: a shiny hatch shows a 🌟 reaction and "A shiny X hatched!", shinies are marked in the Party and PC lists, the Pokédex counts and marks the species you have ever found as a shiny (kept after a transfer), and the odds are explained in the Dex.
- Star a Pokémon to lock it against transfer.
- Haptics: the pet tick now uses the phone's vibrator directly (the earlier one followed the system touch-feedback setting and often did nothing) and has a Settings switch.
- The screen stays on while a focus session runs.

- Pokédex: the Home Region and every region are numbered from 1 in their own order; a new National tab (by generation) uses the real National Dex numbers; each list shows caught and shiny counts.
- The buddy's level chip opens its detail screen (app and widget); a light vibration on a pet; the same emoji never repeats twice in a row.
- The scene darkens slowly through dusk and lightens through dawn (in the app and the widget) instead of switching at 8 pm and 6 am.
- The egg on the Focus setup screen wobbles gently; friendlier empty states for party, PC, bag, Guardians, quests and friends.
- Large system text: the tab bar scales up to 1.25x, rank badges keep their size, task titles wrap to four lines.

- System bars match the app: the red header runs under the status bar and the white tab bar under the navigation bar.
- Sound effects and Music volume sliders replace the Sound toggle (music itself comes later).
- The buddy's pet and berry reactions are a wider set of emoji picked from its bond style, type and the time of day.
- The Buddy widget refreshes when the buddy, its level, a berry, a focus session or today's tasks change.

## 0.1.0

The first private build.

- Planner with difficulty ranks E to A, habits, goals, weekly review and rest days; today's tasks, inbox and recurrence.
- Focus timer that hatches an egg (Deep Focus on Android), with strict rules and calm breaks.
- Pokémon: party, PC, Pokédex, bag, items, natures, abilities, shiny, friendship, evolution and Guardians (legendaries and mythicals with quests).
- Buddy on Home: animated Black/White sprites, cries, petting and berries; Android widgets (Buddy, Today, Focus).
- Cloud save with an account, friends, a weekly leaderboard and daily gifts (Supabase), and the server-side reward function.
- Original app icon, wallpapers, Pokémon DP and Nunito fonts.

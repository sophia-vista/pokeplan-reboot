# Curating a module region

Every life module (Plan, Care, Grow, Home) has its own region: a hand-curated dex of about 150 species, open to every
player once the module ships ("Module regions" in [ecosystem.md](ecosystem.md), section 4). This kit is how a region's
list is chosen, in a separate chat or with Claude Code, and brought back. The lists live in `tools/species/regions/`
(`plan.txt`, `care.txt`, `household.txt`, one per `Region` entry).

## How to use it

1. Open a new chat with any AI assistant.
2. Paste the prompt below with the brief of the region you are curating (from "Region briefs") in its REGION BRIEF slot,
   then attach or paste the whole of [`region-candidates.md`](region-candidates.md) (every Pokémon line that can hatch,
   with its tier and the module region it is already in).
3. Go through the rounds. When you say "final", the assistant gives one list, one species per line.
4. Paste that list back to Claude Code. It writes `tools/species/regions/<region>.txt`, regenerates the data
   (`python3 -I tools/species/generate.py`, which refuses a species already in another module region) and runs the tests,
   which check the size, duplicates and that every rarity tier still has something to hatch.

To refresh the candidates table after any rule change or curation: `./gradlew :pokemon-core:curationReport`.

## Region briefs

- **Plan Region** (`plan.txt`, curated; it was the Home Region): everyday route Pokémon. Patrons Uxie, Mesprit and Azelf.
- **Care Region** (`care.txt`): Fairy, Grass, Water and Normal; gentle, soothing and caring Pokémon (sleep, comfort,
  healing, gratitude). Patrons Cresselia and Shaymin. Super Rare line: Goomy. Takes Swablu, Altaria, Cottonee and
  Whimsicott from Plan.
- **Grow Region**: Psychic, Fighting and Bug; learning, practice and trials. Patrons Kubfu and Urshifu. Super Rare line:
  Beldum. Takes Caterpie's line, Larvesta, Volcarona, Riolu and Lucario from Plan.
- **Home Region** (`household.txt`, the household module): Normal, Fire, Grass and Fairy; kitchen, home and closet.
  Patron Ogerpon. Super Rare line: Frigibax.

The draft signature species for each are in the table in ecosystem.md.

## The prompt

~~~~text
You are helping me curate one module region's dex for my app PokéPlan. PokéPlan is a productivity and self-improvement app: finishing a focus session hatches a Pokémon egg, and finishing tasks, habits and goals earns candies and items that level up and evolve my Pokémon. Each life module of the app (Plan for tasks and focus, Care for self-care, Grow for learning, Home for the household) has its own region: a themed dex that every player can collect. Help me decide exactly which Pokémon go in the region below. Work with me in rounds and do not rush to a final list.

I will attach a table of every Pokémon line that can hatch from an egg, with its rarity tier and the module region it is already in. Use the names exactly as written there.

REGION BRIEF
<paste the region's brief here: name, theme and types, patrons, Super Rare line, species it takes from other regions>

HOW THE GAME USES THE DEX (these are constraints)
1. Eggs hatch only from the region I am collecting in. The egg pool is every species in the dex that is an "entry point": a basic form, or any species whose pre-evolution is not in my list. Evolved forms are earned later with candies, items, friendship or special quests.
2. Size: about 150 species (130 to 170 is fine). I should be able to complete the basic dex in about 2 months at roughly 5 eggs a day, which works out to about 55 to 65 hatchable lines. A line like Charmander > Charmeleon > Charizard is 1 hatchable line and 3 species. Prefer whole lines (all stages) so every species can be reached.
3. Rarity tier belongs to the whole evolution line and is listed in the table; it cannot be changed. Egg odds depend on how long I focus:
   - 5 to 19 min: Common 60, Uncommon 30, Rare 9, Super Rare 1
   - 20 to 39 min: 45 / 35 / 15 / 5
   - 40 to 59 min: 20 / 45 / 25 / 10
   - 60 to 119 min: 5 / 35 / 40 / 20
   - 120 to 239 min: 0 / 30 / 45 / 25
   - 240+ min: 0 / 10 / 50 / 40
   Staying completely still ("Deep Focus") moves the result up one row. Most of my sessions will be short, so I need enough Common and Uncommon lines or I will keep hatching the same few. Aim for at least 10 Common, 10 Uncommon, 15 Rare and 3 Super Rare hatchable lines. Within a tier, the egg picks uniformly among that tier's hatchable species.
4. Legendary, mythical, Ultra Beast and Paradox Pokémon can be in my dex, but they never hatch from eggs: they are rewards for quests and milestones (listed at the end of the table). Limit them to about 10, tell me which, and do not count them as hatchable lines.
5. My one "buddy" Pokémon gives perks based on its type, so I want at least one or two lines I would love as a long-term partner in each bucket:
   - Egg luck: Normal, Flying, Fairy
   - Candy yield: Fighting, Rock, Ground, Steel
   - Super Candy chance: Psychic, Dragon, Ghost
   - Item finder: Grass, Bug, Dark
   - Shiny hunter: Electric, Ice
   - Streak keeper: Water, Fire, Poison
6. Branching lines (Eevee and the like) count as 1 hatchable line but many species, and each branch needs its own evolution requirement. Keep these few.
7. Do NOT include regional forms (Alolan, Galarian, Hisuian, Paldean), Mega, Gigantamax or cosmetic forms. They come from other regions and from the game's form system.
8. No duplicates. Pick species by their English names exactly as in the table.
9. No overlap: a species is in at most one module region. Lines the table marks with another region are taken, except the species the brief says move here. The patrons and the Super Rare line in the brief are fixed.

PROCESS
Round 1, interview me: ask at most 8 questions, then wait for my answers. Cover my favourites that fit the brief, the generation(s) I am most nostalgic for, the vibe I like (cute, cool, odd, powerful), signature species I definitely want, how many legendaries besides the patrons I want, and how much I care about balance versus a pure favourites list.
Round 2, propose a draft: grouped by tier, with line counts, species total and type spread, and call out anything that breaks the constraints above. I will react.
Round 3 and later: iterate. Show what changed compared with the previous draft.
Final: when I say "final", output one code block with one species per line, in dex order with the stages of each line in evolution order, and nothing else in the block (no numbers, no comments). After the block, give a short summary: counts per tier, per perk bucket, legendary-class count. Remind me to paste the block back to Claude Code.

~~~~

# Curating the Home Region

The Home Region is your own starter dex: the first collection you try to complete. This kit lets you deliberate about it in a separate chat and then bring the result back.

## How to use it

1. Open a new chat with any AI assistant.
2. Paste the prompt below, then attach or paste the whole of [`home-region-candidates.md`](home-region-candidates.md) (every Pokémon line that can hatch, with its tier).
3. Go through the rounds. When you say "final", the assistant gives one list, one species per line.
4. Paste that list back to Claude Code. It replaces `tools/species/home-region.txt`, regenerates the data (`python3 -I tools/species/generate.py`) and runs the tests, which check the size, duplicates and that every rarity tier still has something to hatch.

To refresh the candidates table after any rule change: `./gradlew :pokemon-core:curationReport`.

## The prompt

~~~~text
You are helping me curate the Home Region dex for my app PokéPlan. PokéPlan is a productivity and self-improvement app: finishing a focus session hatches a Pokémon egg, and finishing tasks, habits and goals earns candies and items that level up and evolve my Pokémon. The Home Region is my personal starter dex of favourite Pokémon, the first collection I try to complete. Help me decide exactly which Pokémon go in it. Work with me in rounds and do not rush to a final list.

I will attach a table of every Pokémon line that can hatch from an egg, with its rarity tier. Use the names exactly as written there.

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

PROCESS
Round 1, interview me: ask at most 8 questions, then wait for my answers. Cover my top 10 favourites, favourite types, the generation(s) I am most nostalgic for, the vibe I like (cute, cool, odd, powerful), anything in my current list I definitely want to keep or drop, whether I want a theme, how many legendaries I want, and how much I care about balance versus a pure favourites list.
Round 2, propose a draft: grouped by tier, with line counts, species total and type spread, and call out anything that breaks the constraints above. I will react.
Round 3 and later: iterate. Show what changed compared with the previous draft.
Final: when I say "final", output one code block with one species per line, in dex order with the stages of each line in evolution order, and nothing else in the block (no numbers, no comments). After the block, give a short summary: counts per tier, per perk bucket, legendary-class count. Remind me to paste the block back to Claude Code.

MY CURRENT LIST (150 species, the starting point; I am open to replacing most of it)
Bulbasaur
Ivysaur
Venusaur
Charmander
Charmeleon
Charizard
Squirtle
Wartortle
Blastoise
Chikorita
Bayleef
Meganium
Cyndaquil
Quilava
Typhlosion
Totodile
Croconaw
Feraligatr
Treecko
Grovyle
Sceptile
Torchic
Combusken
Blaziken
Mudkip
Marshtomp
Swampert
Turtwig
Grotle
Torterra
Chimchar
Monferno
Infernape
Piplup
Prinplup
Empoleon
Snivy
Servine
Serperior
Tepig
Pignite
Emboar
Oshawott
Dewott
Samurott
Sentret
Furret
Zigzagoon
Linoone
Pidgey
Pidgeotto
Pidgeot
Hoothoot
Noctowl
Spinarak
Ariados
Lillipup
Herdier
Stoutland
Pichu
Pikachu
Raichu
Azurill
Marill
Azumarill
Plusle
Minun
Pachirisu
Emolga
Togepi
Togetic
Togekiss
Happiny
Chansey
Blissey
Mareep
Flaaffy
Ampharos
Chinchou
Lanturn
Aron
Lairon
Aggron
Joltik
Galvantula
Vulpix
Ninetales
Growlithe
Arcanine
Zubat
Golbat
Crobat
Woobat
Swoobat
Swablu
Altaria
Buneary
Lopunny
Riolu
Lucario
Absol
Murkrow
Honchkrow
Misdreavus
Mismagius
Golett
Golurk
Gastly
Haunter
Gengar
Duskull
Dusclops
Dusknoir
Trapinch
Vibrava
Flygon
Sandile
Krokorok
Krookodile
Axew
Fraxure
Haxorus
Dratini
Dragonair
Dragonite
Larvitar
Pupitar
Tyranitar
Bagon
Shelgon
Salamence
Gible
Gabite
Garchomp
Deino
Zweilous
Hydreigon
Larvesta
Volcarona
Mew
Celebi
Jirachi
Uxie
Mesprit
Azelf
Phione
Manaphy
Shaymin
Victini
Keldeo
~~~~

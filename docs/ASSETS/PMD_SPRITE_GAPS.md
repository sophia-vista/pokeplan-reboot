# PMD sprite gaps

Ordered by how likely existing artwork can be recovered. The species lists come from `PMD_SPRITE_MANIFEST.json`.

## 1. Artwork exists; permission needed (8 species)

| Species | Where | What is known | Action |
|---|---|---|---|
| Tranquill (520) | lxsmnsyc fork `compact/unova/0520/0000`, sylvainpolletvillard fork `sprite/0520`, Pokémon Auto Chess atlas | full animation set; credit in the fork: Pokejavi. | ask Pokejavi. (SpriteCollab Discord) to submit or confirm the licence; watch SpriteCollab for approval |
| Crustle (558) | lxsmnsyc fork, Pokémon Auto Chess atlas | full set; credit JFain | ask JFain |
| Shelmet (616) | lxsmnsyc fork | full set; credit Pokejavi. | ask Pokejavi. |
| Frillish (592) | lxsmnsyc fork | regular and female coats; credit Pokejavi. | ask Pokejavi. |
| Bouffalant (626) | lxsmnsyc fork (credit Pokejavi. per its notes), PokeBattle (partial, no artist) | full set in the fork | ask Pokejavi. |
| Simisear (514) | lxsmnsyc fork | full set; credit only a Discord id (298217315294773251) | identify the contributor through SpriteCollab credits |
| Simipour (516) | lxsmnsyc fork | full set; the sheet records no credit | find the artist in the fork's commit "add: simisear and simipour" |
| Okidogi (1014) | Pokémon Auto Chess atlas only | Attack, Hop, Hurt, Idle, Shoot, Sleep, Walk; no artist or timings | ask the Auto Chess maintainers who drew it |

None is in the distributable bundle. Re-run `tools/sprites/pmd_audit.py` after SpriteCollab updates to see whether any was approved.

## 2. Alternative-style artwork exists, no PMD sprite (9 species)

Squawkabilly (931), Mabosstiff (943), Shroodle (944), Brambleghast (947), Toedscruel (949), Klawf (950), Rabsca (954), Espathra (956)
and Flamigo (973) are named on DarkusShadow's Paldea overworld sheets (free to use with credit). The sheets are not PMD style and
their files were not inspected (login needed). Needs: a decision on a deliberate fallback renderer and the artist's files. Maschiff (942)
is not on the sheets.

## 3. Genuine PMD art present but animations missing (17 integrated, 4 form-only)

Integrated with Idle at least; the artists may still be drawing. The table is generated from the manifest (the default form's animations after
`CopyOf` is resolved; `upstream_folders` has every animation name).

| Dex | Species | Missing of Idle, Walk, Attack, Hurt, Sleep | Animations drawn |
|---|---|---|---|
| 593 | Jellicent | attack, hurt | 5 |
| 618 | Stunfisk | walk, attack, hurt, sleep | 2 |
| 683 | Aromatisse | walk, attack, hurt, sleep | 2 |
| 733 | Toucannon | walk, attack, hurt, sleep | 2 |
| 865 | Sirfetch'd | attack, hurt, sleep | 5 |
| 874 | Stonjourner | hurt, sleep | 8 |
| 879 | Copperajah | attack, hurt, sleep | 3 |
| 883 | Arctovish | hurt, sleep | 7 |
| 929 | Dolliv | walk, attack, hurt, sleep | 2 |
| 945 | Grafaiai | walk, attack, hurt, sleep | 2 |
| 948 | Toedscool | attack, hurt | 6 |
| 968 | Orthworm | hurt | 13 |
| 990 | Iron Treads | walk, attack, hurt | 3 |
| 1003 | Ting-Lu | walk, attack, hurt, sleep | 2 |
| 1020 | Gouging Fire | walk, attack, hurt, sleep | 2 |
| 1021 | Raging Bolt | walk, attack, hurt | 5 |
| 1025 | Pecharunt | attack | 9 |

Form-only upstream: Pyroar (female only, so the male default has nothing), Falinks (Brass and Trooper), Gimmighoul (Roaming only; Chest
missing), Miraidon (Low Power only; the Ultimate default missing). Upstream tracker marks Rolycoly, Cufant and Bombirdier "in progress"
although their sets are already complete by our definition.

## 4. No known artwork except portraits (30 species)

Gen 5: Blitzle (522), Zebstrika (523), Throh (538), Tirtouga (564), Carracosta (565), Amoonguss (591). Gen 7: Trumbeak (732), Gumshoos
(735), Shiinotic (756), Oranguru (765). Gen 8: Carkol (838), Coalossal (839), Mr. Rime (866), Zarude (893). Gen 9: Maschiff (942),
Brute Bonnet (986), Iron Jugulis (993), Wo-Chien (1001), Chien-Pao (1002), Iron Boulder (1022), Iron Crown (1023), plus the nine in
section 2. Searched: SpriteCollab (all branches that matter), RawAsset, the three forks, PokeBattle, Auto Chess, the named web pages and
three web searches. Not searched: the SpriteCollab submission channel and other Discord archives (not reachable), and the "Arène Pokémon"
channel itself. NO_PUBLIC_SOURCE_FOUND applies to these species only for the repositories above.

Next steps in order of likelihood: (a) ask the "Arène Pokémon" and Pokémon Battlegrounds channels where their Gen 7 sheets come
from; (b) check SpriteCollab's Discord for sprites in progress; (c) a deliberate decision about hand-made art, which the project's art rule
currently forbids.

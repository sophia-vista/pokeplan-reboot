# Reference: lists and collections (minimal.gallery, a1.gallery, seesaw.website) and component libraries

## Lists and collections (minimal.gallery, a1.gallery, seesaw.website)
Seen: minimal.gallery (categories include App, Games & gaming, Pets, Software). a1.gallery (categories Productivity
and Education among many; Jeffrey Stubbs: an exhibition index, a plain numbered list of titles, places and years;
Sophie Sato: images of different sizes placed loosely; Decimals: a soft serif on a pale ground). seesaw.website
(categories include Productivity; each entry a thumbnail, a name and one quiet grey line; Dial, a time tracker; Gaia,
baby tracking with nap predictions).

**Take this**
- The index list (Jeffrey Stubbs): plain rows, aligned numbers, dividers, no card per row. Ours: the Pokédex (number,
  sprite, name) and the Planner lists (`ListRow`).
- Thumbnail, name and one quiet line (seesaw.website): a Pokémon row is its sprite, its name, and one line in
  `InkSoft` (level, type); a task row is the title and one line (rank, due).
- Calm grounds and generous spacing (Decimals, minimal.gallery): the `Spacing` scale, few borders.
- Browse the App, Games & gaming and Productivity categories on these galleries for neighbours.

**Not that**
- Loose collage layouts for lists people scan, chips and tag clouds for every filter, sticker badges on sprites.

## Component libraries (ideas only)
None of their code ports: they are React with Tailwind and Motion (beui.dev also React Native), and PokéLife is
Kotlin with Compose Multiplatform. Take the idea, rebuild it with our theme and components, give it a Reduce motion
version, and never copy code. If something must be copied, read the project's own LICENSE file, keep its notice,
and ask the owner first.

| Site | What it is | Licence, as its home page put it | Idea to take |
| --- | --- | --- | --- |
| interior.dev | React micro-interactions | MIT | Loading button, hold to confirm, slider detents, nothing jumps |
| uiarc.dev | React library with blocks; Pro is paid | Not stated; Pro paid | Numbers that count, confirm in place with undo, a list that closes the gap |
| beui.dev | React and React Native animated components; Pro tier | Permissive, keep the copyright notice | Swipeable list (only with a visible tap alternative), toast stack for several rewards at once, a spring-pressed button |
| obsidianui.dev | React and Tailwind components | MIT (footer) | None: marquees and galleries |
| rareui.com | Animated React components | "Open-source", licence not named | None |
| cuedesign.space | Paid component library with AI prompts | Cue Kit MIT; the rest paid | None: trend-led sections |
| feralui.dev | Physics-driven React elements | Not stated | None: physics clashes |


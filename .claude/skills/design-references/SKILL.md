---
name: design-references
description: Ideas from 16 design-reference sites (navigation, calls to action, empty states, micro-interactions, lists, component libraries), translated from websites to PokéLife's app screens and filtered through its rules (real game assets only, gentle by design, Reduce motion), with an avoid list of trends that clash. Use before designing or restyling a screen, tab, menu, empty or error state, reward moment, onboarding step or micro-interaction in composeApp or the widgets, or when someone asks what a reference site suggests.
---

# Design references

What 16 reference sites show, sorted into what fits PokéLife and what doesn't. Captured on 23 and 24 October 2026
from each site's home page only. The galleries change daily, so a named example may be gone; the site name is there
so you can look the pattern up again. They are websites and PokéLife is an app, so every idea below is already
translated to a screen, a tab or a moment in the app.

**Order of authority:** `CLAUDE.md`, `docs/pokelife/FEATURES/DESIGN_SYSTEM.md` (sections 5.1 and 12A) and the
`docs/pokelife/` spec of the module win; then `docs/design.md` (what the code does today: Look and feel, ADHD-friendly
design, Ideas adopted from other apps). This file
only gives ideas. Nothing here changes a game rule, a reward or a number. If an idea needs a new dependency or a new kind of
asset, ask the owner first.

**Not captured:** maxibestof.one sits behind a Cloudflare bot check that blocked curl and the headless browser on both
days (403, "Performing security verification"); rareui.com showed a Vercel security checkpoint on the second day
(its notes are from the first); feralui.dev renders everything in the browser, so only its tagline was read. Look at
them by hand if you need them.

## How to use it
1. Find the part of the app you're designing below. Read "take this" and "not that".
2. Check the avoid list. If the idea is on it, drop it.
3. Build it from what exists: colours and sizes from `Pixel` and `Spacing` in `theme/PixelTheme.kt`; `TextBox`,
   `PixelButton`, `GameBox`, `ExpBar` there; `ScreenHeader`, `SectionLabel`, `StatChip`, `ListRow`, `RankBadge` in
   `theme/Widgets.kt`; `MenuGrid` and `SectionScaffold` in `screens/Menu.kt`; `EmptyState` in `screens/Planner.kt`;
   `RewardBox` and `UndoBar` in `screens/Components.kt`. No one-off colours or sizes.
4. Check it in `ScreenshotTest` (light and dark, and at a large font scale), with Reduce motion on, and at a narrow
   phone width.

## The filter (what every idea must pass)
- **Pokémon supplies the personality; the productivity interface supplies the clarity.** Game styling (pixel
  borders, raised buttons, ceremonies) is for meaningful moments and primary actions, not every component.
- **Real game assets only:** sprites, animated sprites, cries, item icons, scenes and wallpapers from PokeAPI/sprites,
  Showdown and PKHeX. Never hand-drawn, generated, 3D or AI art, and no mascot that isn't a real Pokémon sprite. UI
  chrome (panels, buttons, bars) is drawn in code.
- **Gentle by design:** low friction, few choices, sensible defaults, warm neutral words, no shame. Overdue is calm,
  never a red alarm. A missed day never costs anything.
- **The buddy never chats:** no speech bubbles, needs or mood meters; one status-icon reaction (`ReactionIcon`, no emoji) only for a real event or a tap.
- **Motion:** every animation has a still version for Reduce motion, ceremonies are skippable, nothing flashes, and
  nothing moves while someone is reading or typing. The real idle animations of sprites are the one thing that loops.
- **`DayBackground` is the page:** screens never paint their own opaque background over it.
- **Accessibility:** 48 × 48 dp targets for important controls, status never by colour alone, names on icon-only
  controls, body text in Pokemon DP (the app's only typeface; the dialogue bitmap font is for titles, labels and buttons), layouts that survive large
  text and long task names.

## Avoid
Trends seen across these sites that clash with the rules. Don't bring them in, even in a small way.
- **Made-up art:** 3D renders, AI images, mascots, illustrations and hand-drawn pixel art (supahero.io: Anubi, eddie,
  Uigraphic; 404s.design: Dispel, Nothin; cta.gallery: Tines and Numa's 3D objects). Ours are real sprites.
- **Glass, blur and glow:** frosted or floating blurred bars and sheets (supahero.io's own nav pill; beui.dev's
  glass Command Palette and Bottom Sheet), aurora, mesh and glow backgrounds (supahero.io: Better Stack, Wope;
  navbar.gallery: Orchid). The background is `DayBackground` and the PC box wallpapers.
- **Text effects:** gradient text, chromatic sweeps, character scrambles, letter cascades and shimmer text
  (beui.dev Text Animation; uiarc.dev "Titles can reveal by blur, word, line or wipe"). Words must be readable at once.
- **Anything on a loop that isn't a sprite:** marquees and ticker strips (obsidianui.dev Draggable Marquee, Text
  reel; supahero.io: Tigran Azatyan), pulsing badges, spinning objects, auto-advancing carousels.
- **Physics toys:** dragging, swinging and crumpling elements (feralui.dev, "playful, physics-driven"), tilt cards
  with cursor glare (beui.dev Tilt Card), magnetic and metallic buttons, custom cursors, scroll-driven scenes. The
  game feel comes from sprites, cries and the hatch, level-up and evolution ceremonies.
- **Bento dashboards:** walls of mixed-size stat tiles (the component-library home pages: rareui.com, uiarc.dev's
  Metrics dashboard, cuedesign.space). Home is a short overview: one card per region with its lead and one next step.
- **Growth and guilt patterns:** "51 joined today" counters, star counts, testimonial walls (interior.dev,
  obsidianui.dev), leaderboards against strangers, countdown offers, paywalls on core features, ads, sponsor slots,
  newsletter and email capture, rating popups, streak-loss warnings. `docs/design.md` already rules these out.
- **Menus that hide things:** hamburger and full-screen overlay menus over content, dropdown and mega menus, a dock
  of unlabelled icons (beui.dev Dock). Tabs are four labelled tabs (Home, Modules, Pokémon, Me); sections open on `MenuGrid` cards.
- **Giant display type** that fills the screen (supahero.io: KARO, Red Antler; 404s.design: UPCLOSE, BOC Studio).
- **Shape morphs** that move the content you're reading: a panel that grows into the next view with a blur
  cross-fade (beui.dev Morphing Modal, Dynamic Island; uiarc.dev Share sheet, Morph select).

## Navigation (navbar.gallery)
Seen: categories Static, Dropdown, Mega Menu, Side Bar, Search Bar, Announcement, Full Screen, Breadcrumbs. Examples:
Chesapeake Plywood (a dark static bar with dropdown carets), Velt (a dark mega menu with icons in three columns),
Clonix (a dark full-screen menu of large words), Aveon (a cream side panel of plain stacked words beside a
full-height photo), Orchid (a floating pill nav over a blurred colour background).

**Take this**
- Static: the four bottom tabs, each an icon and a label, the selected one filled (DESIGN_SYSTEM.md 5.5). Nothing nested.
- Clonix's full-screen list of large words is close to our section menus: `MenuGrid`'s big cards with real item
  icons, one tap to a section, Back to return.
- Breadcrumbs, made small: `SectionScaffold`'s title and Back, and the system back gesture, always.
- Side Bar: on wide desktop and web windows (dev conveniences only; Android is the product) a labelled side rail can
  stand in for the bottom tabs (same four sections, same order). Phones keep the bottom tabs.
- Announcement: one slim, dismissible line for something that really changed (What's new, tomorrow's spotlight
  type). One at a time, never stacked.
- Search Bar: only where lists get long (Planner All, Pokédex), at the top of that list, never in the tab bar.

**Not that**
- Dropdown carets, mega menus, hamburgers, floating pill navs, a filled "Sign in" button in a header.

## Home and onboarding (supahero.io)
Seen: Dream Design Laboratory (a full-bleed photo of plants against glass, small nav, almost no text), Lando Norris
(a large portrait cut-out), Treize grammes (a serif headline in its own column beside a 3D toggle), Dribbble (a centred
headline, one line and one button), Better Stack (dark glow, email field, logo strip), Anubi and eddie (3D), Red
Antler and KARO (huge type).

**Take this**
- The picture carries the screen, with very little text (Dream Design Laboratory, Lando Norris). Ours: the buddy,
  large and animated in its scene (`BuddyStage`), level and progress small in a corner.
- Text in its own space beside or under the picture, never over it (Treize grammes). Over a scene, use a panel
  (`TextBox`) rather than bare text.
- One headline, one line, one button (Dribbble). Ours: the title screen and each onboarding step: one question,
  sensible default already chosen, one PixelButton.

**Not that**
- 3D, product screenshots, rotating words, autoplay video, dark glow, an email field on the first screen (the app
  has no accounts).

## Primary actions and reward moments (cta.gallery)
Seen: categories Button, Call-to-Buy, Download, Form, Modal / Pop-up, Navigation, Newsletter, Pricing. Examples:
plnty ("Claim your handle" with one input), Augustus ("START BUILDING", lots of white space, one small action),
Get-ripe (a condensed headline over a real photo, on cream), Kronborg (a repeating discount pattern), Aya (a loud
poster), Tines and Numa (giant headlines with 3D objects).

**Take this**
- One action per screen that matters, as a `PixelButton`: Start focus, Add task, Hatch. Everything else is a plain
  row or a secondary button (DESIGN_SYSTEM.md 5.7, Surfaces and depth).
- plnty's single field with one button: naming the trainer, quick-add's title field, a nickname. The rest of the
  form waits behind "more" (design.md, Planner).
- Augustus's white space: a reward sheet shows what was earned (`RewardBox`, real item icons), one line, one Done.

**Not that**
- Pricing tables and upgrade prompts in the core loop, discount or countdown patterns, newsletter forms, modals that
  interrupt a task, two equal buttons side by side.

## Empty, error and offline states (404s.design)
Seen: filters by style (3D, Bold, Brutalist, Colorful, Dark, Editorial, Glitchy, Hand-drawn, Illustrative, Large
Type, Light, Minimal, Photographic, Playful, Retro, Typographic) and interaction (Animated, Custom Cursor, Game,
Hover Effects, Interactive, Scroll-driven). Examples: Kobbe (a small drawing, a short line, a couple of links),
WithUPI ("This page didn't go through.", in the product's own terms), Tribe AI ("This page lost its momentum."),
Ethan+ AI ("Nothing at this address."), Sruthi (tic-tac-toe), Ente (a game), UPCLOSE and BOC Studio (giant "404").

**Take this**
- Retro, Playful, Light and Minimal are our register; Illustrative only with real sprites and item icons.
- Kobbe's shape: one small real sprite or item icon, a short title, one line, at most one action. Ours: `EmptyState`
  ("The PC is empty", then what fills it).
- Speak in the game's own terms (WithUPI, Tribe AI): the party, the PC, the Bag, an egg. Say what still works, e.g.
  that progress is kept on the device while offline, and never blame the person.
- Every list has an empty, a loading and an error state (DESIGN_SYSTEM.md 6.10).

**Not that**
- Games inside an error screen, glitch effects, giant numerals, dark dead ends with no way back.

## Micro-interactions (interior.dev, detail.design)
interior.dev: "Software feels cheap in the half-second after a click." Loading Button ("The label changes. The button
stays put."), Hold to Confirm ("A moment to change your mind."), Tooltip Group ("A little patience. Then no
waiting."), Slider Detents ("Stops you can feel."), Segmented Control ("The selection travels. Nothing jumps."), Like
Burst ("Even a second tap is considered."). detail.design: categories Design, Interactivity, Motion, Copywriting,
Accessibility, Easter Egg; entries Smarter Word Break, Name-Derived Placeholder, Adaptive Text Color, Respect Platform
Language, Unified Login Field, Avoid Browser Default Back Behavior, Clicking the Input Label Focuses the Input Field,
Hold to Reveal Shortcut Hint.

**Take this**
- Nothing jumps: a button keeps its size while its label changes (Saving…, Saved), rows keep their height when a
  badge appears, a finished task leaves the list and the gap closes smoothly (uiarc.dev "An inbox that makes room").
- Hold to Confirm for the few things that can't be undone: transferring a Pokémon, deleting data. Never for
  finishing a task, which gets the 10 second Undo instead (uiarc.dev "Confirm morph: asks in place, with undo").
- Slider Detents for the custom focus length: stops at 15, 25, 45 and 60 with a soft haptic (`Haptics.kt`, off with
  the setting).
- Like Burst's care for the second tap: petting reacts every time, but the friendship cap is shown calmly once
  reached, never as an error.
- Numbers that count to their new value (uiarc.dev "Numbers that move when they change"): candy, coins and EXP after
  a reward. Instant with Reduce motion; screen readers get the final number once.
- Name-Derived Placeholder: placeholders echo what the person already gave (a nickname field shows the species name,
  a subtask field mentions the task).
- Clicking the label focuses the input: a settings row toggles from anywhere on the row, not just the switch.
- Respect Platform Language and back behaviour: "tap" on phones, "click" on desktop; the system back gesture always
  closes the sheet or section first (`SectionScaffold` already does this).
- Smarter Word Break: long task names and nicknames wrap or end with an ellipsis, never push a button off screen.
- Adaptive Text Color is the problem of text on pictures: put text on a panel instead.

**Not that**
- Easter eggs that hide features, hold-to-reveal hints on phones (most people are on phones; a keyboard hint is fine
  on desktop only), tooltips as the only way to learn a control.

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

## Pattern map
Where each idea belongs in the app. "Exists" means it is already built; use the idea only to check or tune it.

| Pattern | Source | Home | Planner | Focus | Pokémon | Me | Widgets |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Labelled tabs, nothing nested | navbar.gallery (Static) | Exists | Exists | Exists | Exists | Exists | |
| Big cards that open a section | navbar.gallery (Clonix) | | Exists | | Exists | Exists | |
| Side rail on wide windows | navbar.gallery (Side Bar) | Desktop | Desktop | | Desktop | Desktop | |
| The picture carries the screen | supahero.io (Dream Design Laboratory) | Region leads | | Egg | Detail sprite | | Buddy |
| Text on a panel, never on the picture | supahero.io (Treize grammes) | Level corner | | | | | Buddy |
| One field, one button | cta.gallery (plnty) | | Quick-add | | Nickname | Trainer name | |
| One primary action per screen | cta.gallery (Augustus) | One step per region | Add task | Start | | | Focus |
| Small sprite, short line, one action | 404s.design (Kobbe) | | Empty lists | | Empty party, PC | Offline | Empty |
| Speak in the game's terms | 404s.design (WithUPI) | | | Failed session | | | |
| Nothing jumps | interior.dev | | Task done | Timer | Rows | | |
| Hold to confirm, only if irreversible | interior.dev | | | | Transfer | Delete data | |
| Undo in place | uiarc.dev (Confirm morph) | Task done | Task done | | | | |
| Numbers that count | uiarc.dev | Counters | Reward sheet | Hatch summary | EXP | | |
| Detents | interior.dev (Slider Detents) | | | Custom length | | | |
| Index list with aligned numbers | a1.gallery (Jeffrey Stubbs) | | All, Upcoming | | Pokédex | History | |
| Thumbnail, name, one quiet line | seesaw.website | Recently | Rows | | Party, PC | | Tasks |
| Placeholder from what's known | detail.design | | Subtasks | | Nickname | | |

## Before you ship a design
- Every colour and size comes from `Pixel` and `Spacing`; every picture is a real asset; nothing on the avoid list.
- Reduce motion gives a still, complete version; ceremonies can be skipped; nothing flashes.
- It reads at a large font scale and in dark mode, targets are at least 48 dp, status isn't colour alone.
- Empty, loading, error and offline states exist for every new list.
- `./gradlew jvmTest compileKotlinWasmJs` passes and the changed screen's `ScreenshotTest` PNG looks right.

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
   `theme/ThemeWidgets.kt`; `MenuGrid` and `SectionScaffold` in `screens/Menu.kt`; `EmptyState` in `plan/Planner.kt`;
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

## Which reference to open
Open only the file for the kind of screen you are designing; each holds "Take this" and "Not that" per site.

| Designing or asking about | Open |
| --- | --- |
| Bottom tabs, a module's section menu, breadcrumbs, search, a side rail, an announcement line, the title screen, onboarding steps, what the picture carries | `references/navigation-and-home.md` |
| A primary button, a quick-add field, a reward sheet, anything that asks for one decision | `references/calls-to-action.md` |
| An empty, error or offline state; loading and saving feedback, confirm and undo, detents, counting numbers, placeholders, long text | `references/empty-states-and-micro-interactions.md` |
| A list, the Pokédex, a Pokémon or task row, a collection; or a question about a component library (licence, what to take) | `references/lists-and-components.md` |
| Where an idea already lives in the app (Home, Planner, Focus, Pokémon, Me, widgets) and what already exists | `references/pattern-map.md` |

## Before you ship a design
- Every colour and size comes from `Pixel` and `Spacing`; every picture is a real asset; nothing on the avoid list.
- Reduce motion gives a still, complete version; ceremonies can be skipped; nothing flashes.
- It reads at a large font scale and in dark mode, targets are at least 48 dp, status isn't colour alone.
- Empty, loading, error and offline states exist for every new list.
- `./gradlew jvmTest compileKotlinWasmJs` passes and the changed screen's `ScreenshotTest` PNG looks right.

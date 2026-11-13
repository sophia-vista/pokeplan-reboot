# PokéLife — Shared Design System

**Version:** 1.0  
**Status:** Authoritative cross-module visual and component specification  
**Last consolidated:** 5 November 2026

> This document is the single source of truth for PokéLife's visual language, design tokens, reusable UI components, interaction states and presentation/accessibility rules. The Product Bible defines the product-wide visual direction. Every module specification must reference this file and describe only its module-specific adaptations. Do not duplicate or fork the shared component rules.

## 1. Design intent

PokéLife's visual identity is based on Pokémon Mystery Dungeon dialogue boxes, interpreted through a clean, minimal modern Android interface. The interface should feel like a polished PMD game, not a conventional productivity app with Pokémon decorations added on top.

PMD dialogue boxes are the primary visual reference and the foundation of the shared component language. This does **not** mean every component is a literal speech bubble or every screen is a conversation. Lists, calendars, charts, editors, forms, dashboards and utility tools retain the layout best suited to their job while sharing PMD-inspired geometry, borders, surfaces, hierarchy and feedback behavior.

The design balances:
- Recognizable PMD character through crisp pixel-inspired outlines, corners, restrained shadows and dialogue-panel surfaces.
- Modern Android usability, readable content, appropriate information density and predictable interaction.
- Minimalism: use fewer, better components; avoid redundant labels, decorative framing and nested containers.
- Module flexibility within one coherent system.

## 2. Global rules

1. Use shared components and tokens before creating a module-specific component.
2. Dialogue boxes and framed panels adapt to content; never impose one fixed height or width on all content.
3. Use hierarchy, spacing and alignment before adding another border or container.
4. Do not put every row, value, chart or paragraph inside an individual box. Group related content only when the grouping improves comprehension or interaction.
5. Avoid redundant captions, labels that repeat visible context, excessive nested containers and decorative framing.
6. Keep titles, descriptions, values, actions and secondary information visually distinct.
7. Keep the primary action obvious. Put advanced or infrequent controls behind progressive disclosure, contextual menus or secondary screens.
8. Maintain consistent typography, spacing, padding, borders, buttons, focus behavior and interaction states across all modules.
9. Preserve native Android expectations for back behavior, keyboard/input, selection, scrolling, accessibility and system insets.
10. Do not use Pokémon art or decorative effects to obscure, delay or compete with a real-life task.

## 3. Presentation intensity

There is one shared design system with different presentation intensity:

### 3.1 Utility presentation

PLAN, CARE, HEAL, MINT, HOME, LINK and GEAR use compact, restrained PMD framing. Dialogue-box surfaces may contain a primary action, concise status, short prompt, summary or contextual interaction. Dense records and repeated data use efficient native-feeling rows, tables, timelines, calendars, charts and forms with selective PMD borders or panel framing.

### 3.2 World presentation

WORLD and the optional launcher may use more immersive PMD scene composition, character portraits, expressive dialogue panels, pixel environments, sprites, tile maps and contextual sound. They may introduce specialized layouts and scene components, but reuse shared typography, spacing, tokens, borders, buttons, dialogs, states and accessibility behavior. WORLD's game interface must not be imposed on utility modules.

### 3.3 Module adaptation principle

Modules may change layout, content density and expressive intensity to suit their purpose. They may not independently redefine global typography, spacing, border language, control anatomy, button hierarchy, state colors, accessibility or interaction behavior. Any new shared pattern belongs here first.

## 4. Shape, border and surface language

- Use crisp pixel-inspired borders, corner treatments and outlines. The treatment should evoke PMD dialogue windows without copying a specific game's exact screen or creating a heavy ornamental frame.
- Prefer a small, consistent family of border weights and corner constructions. Use the same geometry for equivalent component roles across themes and modules.
- Use subtle, short shadows only where they clarify elevation or separation. Avoid large soft Material-style floating shadows that conflict with the pixel edge language.
- Backgrounds remain restrained and low-noise. Text and controls must remain the visual focus.
- Use nine-slice or equivalent scalable border assets where bitmap frames are used. Keep corners and edge thickness stable while the center stretches; do not stretch the corner pixels.
- For code-drawn borders, use integer-aligned coordinates and stable stroke widths where possible. Avoid fractional scaling that creates blurred or uneven pixel edges.
- Dialogue panels grow with content and available width. Apply sensible maximum widths on large screens and allow full-width layouts on phones when appropriate.
- A panel's visual weight should reflect its semantic importance. Do not give every secondary item the same prominence as the primary action.

## 5. Tokens

All feature UI must use centralized semantic tokens. Do not scatter literal colors, dimensions, typography or border values through module screens.

### 5.1 Color tokens

Define paired light and dark values for:
- `background`, `surface`, `surfaceRaised`, `surfaceSunken`
- `textPrimary`, `textSecondary`, `textTertiary`, `textOnAccent`
- `outline`, `outlineStrong`, `divider`, `focus`
- `accent`, `accentContainer`
- `success`, `warning`, `error`, `info` and their container variants
- `disabledContent`, `disabledSurface`

Colors are semantic, not decorative. Module/region accents may tint selected elements, but must not impersonate success, warning, error or financial status. Do not rely on color alone to communicate meaning. Verify text contrast to WCAG AA where measurable.

#### Chrome and module accents (8 November 2026)

- **App chrome is neutral outside a module.** The header bar of every page that belongs to no module (Home, Modules, Pokémon, Me) and the bottom tab bar there use `Pixel.Chrome`, a deep slate; inside a module the tab bar takes the module's header colour (owner, 13 November 2026). The brand red is not chrome: it is the Poké Ball accent for a few primary game actions (start a focus, choose a starter) and never identifies a module.
- **One accent per module**, in `ModuleAccent` (`RegionLook.kt`), ordered as the Modules menu is: **Pokégear first, World last, and the six between running down a quiet rainbow** (owner, 8 November 2026; the order may be rearranged to fit the colours, but Pokégear stays on top and World at the bottom). A module's header bar is its `header` colour (the same in both palettes, white text on it, at least 4.5:1); its dialogue-frame tint, menu-card tint and primary action are its `accent` (a brighter shade in the dark palette so the frame stays visible on a dark panel). A module that is not built yet is a placeholder card in its own colour, dimmed, that opens nothing. Starting values *(tune)*:

| Order | Module | Hue | Header (both palettes) | Dark-palette accent |
|---|---|---|---|---|
| 1 | GEAR (Pokégear) | rose | `#B83A4B` | `#F0707F` |
| 2 | PLAN | orange | `#B35309` | `#EE9040` |
| 3 | MINT | ochre | `#8A6700` | `#D4A82A` |
| 4 | HOME | olive | `#4D7C0F` | `#8DB83A` |
| 5 | CARE | green | `#1E7F52` | `#34B07A` |
| 6 | HEAL | teal | `#0F766E` | `#2DB5A8` |
| 7 | LINK | blue | `#2C6CCC` | `#5B96EE` |
| 8 | WORLD | violet | `#6D3FD6` | `#9B7BF0` |

- MINT, HOME and LINK are reserved so the modules built later do not invent their own. An accent never stands for success, warning, error or money status; those keep the semantic tokens. Inside a module every menu card, frame and primary action uses that one accent (no rainbow of tiles inside a module); only quiet items such as Settings use the neutral ink.
- **Letters on a filled key** (pressed, selected) are white or the dark ink, whichever has the higher contrast with the fill (`keyLook`), so a mid-tone fill in the dark palette still reads at AA.
- **Frames.** One frame per group: a dialogue box holds its rows without a box round each row, and a box is not put inside a box. **Every big section sits in a dialogue box** (a rank and XP strip, a list of teams, a settings group), never loose on the page background; only the header, a menu grid and a picture window stand outside one.
- **Pixel parts, no rounding (8 November 2026).** Text fields, bars, toggles, sliders, cards, picture windows and the header bar use stair corners of whole 2 dp art pixels (`StairShape`: `DialogueShape`, `PixelShape`, `PixelDisc`), drawn in code. Material's rounded `OutlinedTextField`, `Switch` and `Slider` are not used: `PixelField` (outline like a key, a label above it or a placeholder inside, text centred in 48 dp), `PixelToggle`, `PixelSlider`, `PixelBar` and `ExpBar` replace them. A row that puts a field and keys side by side centres them on one line (`verticalAlignment = CenterVertically`); a label for such a field goes above the row, never inside the field.
- **Nothing is round (7 November 2026).** Windows onto a scene (`Stage`, `SceneStage`), the background picker and the launcher use `PixelShape` or `DialogueShape`; swatches, badges and dots are pixel squares; a circle that must be a circle (the breathing circle, the compass, map markers) is a stair-stepped pixel disc (`drawPixelDisc`, `drawPixelRing`). `RoundedCornerShape`, `CircleShape`, Material menus and `drawCircle` are not used for chrome.
- **Every module page opens on its lead** (`ModuleLeadStage`): a picture window with the module's own scene and its lead idling in it, level and progress small in a corner, a tap to pet it. Nothing shows until the module has a lead. The buddy is not a module lead and shows on Home only.
- **The buddy lives on Home only** (`HomeBuddyStage`, in a scene of its own type). Module pages show their own party and lead, never the buddy, and the PC list shows only Pokémon that are in no other place (`Places.of == Pc`), so the buddy is never in the PC.

### 5.2 Spacing and sizing

Use a 4 dp-based spacing scale and named semantic tokens. Initial scale: `space0=0`, `space1=4`, `space2=8`, `space3=12`, `space4=16`, `space5=20`, `space6=24`, `space8=32`, `space10=40`, `space12=48` dp. Prefer semantic aliases such as `contentInset`, `panelPadding`, `rowGap`, `sectionGap`, `pageGutter` and `compactControlGap` in component APIs.

- Interactive touch targets are at least 48 × 48 dp even when the visible icon is smaller.
- Component padding is consistent by role, but may adapt at compact widths and larger font scales.
- Respect system insets and edge-to-edge safe areas.
- Avoid one-off spacing values unless a documented visual or platform constraint requires them.

### 5.3 Typography

- Use readable system sans-serif typography for body content, long descriptions, forms, financial/health values, notes and user-entered text.
- Use a restrained semantic scale: display, screen title, section title, body, body compact, label, caption and numeric emphasis.
- Pixel/bitmap type is optional and selective for short accents, small headings or game dialogue where it remains legible. Never use it for long reading, dense data, accessibility-critical instructions or user-entered content.
- Use tabular numerals for aligned values where appropriate.
- Respect Android font scaling and display-size settings. Layouts must reflow rather than clip or force horizontal scrolling.

### 5.4 Elevation and density

Use the minimum elevation needed to express hierarchy. Density may vary by context (e.g. finance tables vs. reflective writing), but tap targets, readable type, border language and spacing semantics remain consistent.

## 6. Reusable component catalogue

The following are canonical component families. Implement shared variants rather than separate copies per module. Each component must define anatomy, sizing, semantics, focus behavior, interaction states, light/dark appearance and screenshot coverage.

### 6.1 Primary and secondary dialogue boxes

**PrimaryDialogueBox**
- For the most important contextual message, action group, summary or short interaction.
- Clear internal hierarchy: optional title/portrait, content, primary action and optional secondary action.
- Content-driven height; no fixed speech-bubble dimensions.
- May support a small PMD-style pointer/tail only when a speaker or source is meaningful. The tail is optional, not a default ornament.

**SecondaryDialogueBox**
- For supporting context, grouped status or secondary information.
- Lower visual emphasis than the primary variant; do not stack many secondary boxes vertically without a clear reason.

**InlinePanel / FramedSurface**
- For grouping controls or information that benefits from a subtle PMD border but is not dialogue. No tail, portrait or conversational framing by default.

### 6.2 Section headers and compact labels

- `SectionHeader`: title plus optional concise action or status; no redundant explanatory caption.
- `CompactLabel`: short metadata, category or field label; maintain legibility and adequate contrast.
- Use section headers to create hierarchy, not to add decorative separators to every group.

### 6.3 Buttons and icon buttons

- `PrimaryButton`, `SecondaryButton`, `TertiaryButton` and `DestructiveButton` share shape, type, padding and state behavior.
- `IconButton` has a clear accessible name and at least a 48 dp target.
- Buttons use restrained pixel-inspired outlines/corners, not bitmap-only text or decorative bevels that reduce legibility.
- Define enabled, pressed, focused, selected (where applicable), disabled, loading and destructive-confirmation states.
- One primary button per local action group unless the task genuinely requires multiple equivalent choices.

### 6.4 Input fields and selectors

- Shared text fields, multiline editors, search fields, numeric/currency inputs, date/time selectors, dropdowns, segmented controls, checkboxes, switches and radio groups.
- Labels and helper/error text must remain programmatically associated with controls.
- Use input types and keyboards appropriate to the data. Preserve user input through validation errors and interruptions.
- Advanced options are collapsed or placed in a secondary step where practical.
- Do not use dialogue-box framing to make fields cramped; writing and data entry need comfortable content width.
- **Exact entry (owner, 12 November 2026: "I am a very exact person"):** every date, time of day and length can be typed, never only picked from keys or nudged by a stepper. A date field (`PixelDateField`) is quick keys, a month to tap *and* a text box taking `2026-11-07`, `7/11/2026` (day first, four-digit year) or `7 Nov 2026`; a time field (`PixelTimeField`) takes `9`, `930`, `21:05`, `9:30 pm`, `noon`; a length field (`PixelDurationField`) takes `45`, `1h30`, `1:30`; a moment (`PixelMomentField`) is a day and a time. Each reads back what it understood ("Thu 12 Nov 2026", "9:30 am", "1 h 30 min") and says what it takes when the text is not yet a value; a valid value is applied at once, so Save always reads what is on the screen. The rules are `ExactInput` in app-core (tested once, every spelling). A number that could be read two ways is never guessed. Whatever a record's date or time is when it is made, it can be corrected afterwards; a correction never pays and never takes back (rewards follow the first save).

### 6.5 Lists and list rows

- Shared `ListRow`, `SettingRow`, `TaskRow`, `DataRow` and `SelectableRow` patterns.
- Rows align primary text, secondary text, leading/trailing icons and values consistently.
- Prefer dividers, spacing or subtle grouping over a separate framed card for every row.
- Support selected, pressed, focused, disabled, completed and error states as applicable.

### 6.6 Cards and summaries

- `SummaryCard` and `MetricCard` use dialogue-box visual grammar sparingly for meaningful grouped summaries.
- A card must provide a clear grouping or action benefit. Avoid cards used only to decorate a value.
- Dense comparisons, transaction histories, charts and tables should use fit-for-purpose layouts rather than many small cards.

### 6.7 Tabs and navigation

- Shared top tabs, bottom navigation, navigation rail/drawer, module switcher and contextual navigation items.
- Selected state is clear in both themes and not communicated by color alone.
- Do not make the global module navigation look like a row of speech bubbles. The shared visual language can be expressed through outline, corner and selection treatment.
- Preserve standard Android back behavior and clear current-location indication.

### 6.8 Dialogs, sheets and confirmation prompts

- `DialogueDialog`, `BottomSheet`, `ConfirmationPrompt` and `AlertPrompt` share typography, border, button and focus conventions.
- Use dialogue framing for the content surface; do not imitate a full-screen story dialogue for routine system confirmations.
- Destructive or consequential actions explain the outcome and require deliberate confirmation. Reversible actions remain low-friction.
- Support back dismissal where safe, focus trapping where appropriate, TalkBack announcement and keyboard behavior.

### 6.9 Progress indicators

- Shared linear, circular and compact progress indicators use semantic colors and accessible value descriptions.
- Pixel styling may shape the track, frame or cap, but must not compromise accuracy or legibility.
- Always provide textual context when progress meaning is not self-evident. Do not turn every metric into a progress bar.

### 6.10 Empty, loading, error and success states

- `EmptyState`: concise explanation and one useful next action; no unrelated feature promotion.
- `LoadingState`: stable placeholder or restrained progress feedback; never spinner-only for long waits.
- `ErrorState`: what failed, whether data is safe, and the next useful action (retry, edit, reconnect or continue offline).
- `SuccessFeedback`: compact confirmation, inline state change or restrained toast/dialogue panel. Avoid repeated reward-like fanfare for routine actions. **One feedback line (0.165.0):** saved, reward and error all go through `AppModel.say` or `Report` and show in one bar above the tabs (`FeedbackHost`), the same on every screen; a screen never draws its own message box. A problem that stays until it is fixed uses `Report` (kept up while the screen holds it); a field that cannot be read still says so under the field.
- **Empty states (0.165.0):** every `EmptyState` has one button, in the primary colour: where something can be added, it adds (or moves to the field above); otherwise it takes the player to the thing that fills the list (a focus session for eggs, Clear for a search or filters that found nothing).
- Include stale/offline, permission-denied and partial-data states where relevant.

### 6.11 Tooltips and contextual menus

- Shared tooltip and contextual-menu surfaces use the same outline, corner, typography and elevation language.
- Tooltips are supplementary; never hide essential instructions exclusively inside them.
- Context menus expose secondary actions without cluttering the primary screen. Ensure equivalent accessibility actions are available without long-press-only interaction.

## 7. Interaction states and behavior

Every interactive component must account for the applicable states: default, pressed, focused, selected, disabled, loading, validation error, success and destructive confirmation. State transitions should be brief and purposeful. Avoid animated border shimmer, pulsing or idle motion in utility screens.

- Provide visible keyboard/focus indication.
- Ensure disabled states remain understandable and sufficiently legible.
- Do not use color as the only indication of selected, error, success or warning.
- Keep action feedback close to the control or content that changed.
- Support undo for reversible actions where appropriate.

## 8. Light/dark theme and responsive behavior

- Light and dark themes are designed as first-class palettes, not automatic inversion.
- Dialogue surfaces, outlines, shadows, dividers, input states, charts and pixel art backdrops must be reviewed independently in both themes.
- Support compact phones, standard phones, landscape where relevant, tablets/foldables and resizable windows without clipping or broken hierarchy.
- Dialogue boxes use available width responsibly: full width when useful on phones; bounded readable width on larger screens; content-driven height at all sizes.
- At large font scales, labels and buttons wrap/reflow. Essential values and actions must not disappear behind ellipses.
- Avoid horizontal scrolling for primary navigation or essential information.

## 9. Accessibility requirements

- TalkBack labels, roles, state descriptions and logical traversal order for every interactive control.
- Minimum 48 dp touch targets and adequate spacing between adjacent targets.
- WCAG AA text contrast where measurable; visible focus indicators.
- System font scaling and display-size support without clipping.
- No essential color-only, sound-only, motion-only or gesture-only communication.
- Provide accessible alternatives to long press, drag, swipe and timed interactions.
- Respect Android Reduce Motion and app-level motion preferences. Avoid parallax or continuous decorative movement in utility modules.
- Charts and visual statistics require textual summaries and accessible data labels.
- Dialogue-box semantics must be exposed as grouped content with meaningful reading order; do not announce ornamental borders or tails.

## 10. Pixel-art rendering rules

- Use nearest-neighbor sampling for pixel art; preserve source dimensions and pixel grid.
- Prefer integer scale factors where practical. If fractional scaling is unavoidable, inspect for uneven pixel widths and shimmering.
- Use nine-slice scaling for framed dialogue assets so corners remain intact.
- Do not apply blur, bilinear smoothing, glow, noise overlays or arbitrary texture to pixel art.
- Pixel art is content, not a substitute for accessible text labels.
- Respect source asset provenance and licence requirements defined in the asset specification.

## 11. Compose implementation guidance

- Implement shared design tokens in the design-system module; expose semantic APIs rather than raw color/size literals.
- Build composable primitives for dialogue surfaces, buttons, fields, rows, summaries, navigation, prompts and feedback states. Modules consume these through stable public APIs.
- Keep domain logic out of composables. UI components render state and emit user intent; they do not own business rules or persistence.
- Prefer standard Compose/Android controls where they provide better semantics and accessibility, then apply shared visual styling. Do not replace robust native behavior solely to imitate game UI.
- Use `Modifier.semantics`, appropriate roles, state descriptions and custom actions where needed. Verify TalkBack traversal on actual devices.
- Support dynamic type with responsive constraints and content measurement. Avoid hard-coded heights for dialogue boxes, cards, fields or buttons.
- Add screenshot/golden tests for component variants, light/dark themes, font scaling, empty/error/loading/success states and narrow/wide widths.
- Validate pixel rendering on device and emulator at the actual target density; do not rely on previews alone.

## 12. Module application checklist

Every module specification must include a short section titled **Visual presentation** that:
1. References `../FEATURES/DESIGN_SYSTEM.md` (or the correct relative path from that module file).
2. States the module's presentation intensity (utility or immersive World) and why.
3. Identifies which shared components are primary in the module.
4. Documents only genuine module-specific components or deviations, with a reason.
5. Confirms light/dark themes, text scaling, TalkBack, reduced motion and responsive layouts.

All module UI reviews must check:
- [ ] Shared dialogue-box border/corner/surface grammar is recognizable.
- [ ] Content-driven sizing; no forced identical dialogue-box dimensions.
- [ ] No unnecessary boxes, captions, nested containers or decoration.
- [ ] Clear hierarchy for titles, descriptions, values, actions and secondary details.
- [ ] Consistent spacing, padding, typography, buttons and interaction states.
- [ ] Progressive disclosure for advanced controls.
- [ ] Light/dark, enlarged text, TalkBack, touch targets and responsive widths verified.
- [ ] Pixel art remains crisp and does not obstruct the task.

## 12A. Copy and anti-slop rules

Condensed from the owner's `unslop-ui` reference (8 November 2026), for a native app. Slop is an unspecified default: the first thing that comes to mind, which every generated screen shares. A screen should read as if someone decided each thing on it.

1. **A caption must say something the screen does not already show.** No sentence that restates the title, the visible state or the button's label; no reassurance ("Only you see this", "Nothing here earns anything", "No hurry"), no tutorial paragraph under a menu, no "Each module is one part of your day". If a rule matters it belongs in Help, once. An empty state is a short title and at most one action, not an explanation. A menu card is an icon and a title. **Sections that hold records are rows, not cards** (owner, 13 November 2026): an icon, a title and the count on one line, in one dialogue box (`MenuList`): the Planner's views, the Pokémon tab, World's Journal, Me. A module's landing page keeps its cards (`MenuGrid`).
10. **One input per value.** A date is one row of keys (the typed box opens with the calendar); a time or a length is its keys and one typed box. No echo line in another format and no nudge keys; a typed value is rewritten in the app's own spelling when the box loses focus.
11. **No sentence-length copy in the UI** outside Help, What's New and the story: `CopyBudgetTest` fails on a new one (a field error, a confirmation, an accessibility label and feedback through `say` are not counted).
2. **No breadcrumb under a header** that repeats where the Back button goes; a header's second line is a fact (rank, a count that changes), not a label.
3. **Specific copy, no clichés.** "Send a team into a dungeon", not "Unleash your team's potential". Say what the thing does in the words of the game.
4. **No emoji as icons or bullets**; item icons and Pixelarticons glyphs only (a buddy's tap emote is the one game-style exception).
5. **No gradient text, no glow, no animation on every element.** Motion tells state or a real event and respects Reduce motion; the dither background is the only texture.
6. **No uniform treatment.** Rounded corners everywhere, the same shadow on everything and a box round every row all read as generated. Use the stair corner, one frame per group, and hierarchy (size, weight, spacing) before another border.
7. **Colours come from the tokens.** A new colour is a decision recorded in section 5.1 with its reason, not the next-most-likely hex; module accents follow the rainbow table, red is not chrome.
8. **Text is centred by its letters, and that is measured, not eyeballed.** The Pokémon DP font sits low in its line box, so `PixelTheme` lifts every text style (`baselineShift`) and the dialogue bitmap font draws one art pixel lower (`PmdFont.BASELINE`); `TextCentreTest` renders keys, fields, badges and each text style and fails if the gap above the letters and below them differ by more than 1 dp. A new font, size or control gets a line in that test. Rank badges are rounded pixel squares (`DialogueShape`), never circles.
9. **Layout quality is checked by eye on a real screenshot, in both palettes, on every change**: nothing clipped or overflowing, one spacing scale (`Spacing`), every row's contents on one centre line, edges aligned to the page gutter, one thing leading the eye per screen.

## 13. Change control

Changes to shared tokens, component anatomy, border language, typography, navigation or global states must be made here first, then reflected in the Product Bible or Implementation Guide when they alter product-wide requirements or delivery scope. Module documents must not silently redefine shared rules. When a module has a legitimate exceptional need, document the exception in that module's Visual presentation section and keep the shared system intact.

---

## Testing reference

Shared components and representative screens require semantic Compose tests, approved light/dark golden images, responsive/font-scale coverage and accessibility verification as defined in [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md). Golden updates require explicit review.

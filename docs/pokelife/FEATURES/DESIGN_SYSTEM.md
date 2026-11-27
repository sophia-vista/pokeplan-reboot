# PokéLife — Shared Design System

**Status:** Authoritative cross-module visual and component specification

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

PLAN, CARE, HEAL, MINT, BASE, LINK and GEAR use compact, restrained PMD framing. Dialogue-box surfaces may contain a primary action, concise status, short prompt, summary or contextual interaction. Dense records and repeated data use efficient native-feeling rows, tables, timelines, calendars, charts and forms with selective PMD borders or panel framing.

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

#### Chrome and module accents

- **App chrome is neutral outside a module.** The header bar of every page that belongs to no module (Home, Modules, Pokémon, Me) and the bottom tab bar there use `Pixel.Chrome`, a deep slate; inside a module the tab bar takes the module's header colour. The brand red is not chrome: it is the Poké Ball accent for a few primary game actions (start a focus, choose a starter) and never identifies a module.
- **One accent per module**, in `ModuleAccent` (`RegionLook.kt`), ordered as the Modules menu is: **Pokégear first, World last, and the six between running down a quiet rainbow** (the order may be rearranged to fit the colours, but Pokégear stays on top and World at the bottom). A module's header bar is its `header` colour (the same in both palettes, white text on it, at least 4.5:1); its dialogue-frame tint, menu-card tint and primary action are its `accent` (a brighter shade in the dark palette so the frame stays visible on a dark panel). Starting values *(tune)*:

| Order | Module | Hue | Header (both palettes) | Dark-palette accent |
|---|---|---|---|---|
| 1 | GEAR (Pokégear) | rose | `#B83A4B` | `#F0707F` |
| 2 | PLAN | orange | `#B35309` | `#EE9040` |
| 3 | MINT | ochre | `#8A6700` | `#D4A82A` |
| 4 | BASE | olive | `#4D7C0F` | `#8DB83A` |
| 5 | CARE | green | `#1E7F52` | `#34B07A` |
| 6 | HEAL | teal | `#0F766E` | `#2DB5A8` |
| 7 | LINK | blue | `#2C6CCC` | `#5B96EE` |
| 8 | WORLD | violet | `#6D3FD6` | `#9B7BF0` |

- Every module's accent is fixed in this table, so none invents its own. An accent never stands for success, warning, error or money status; those keep the semantic tokens. Inside a module every menu card, frame and primary action uses that one accent (no rainbow of tiles inside a module); only quiet items such as Settings use the neutral ink.
- **Letters on a filled key** (pressed, selected) are white or the dark ink, whichever has the higher contrast with the fill (`keyLook`), so a mid-tone fill in the dark palette still reads at AA.
- **Frames.** One frame per group: a dialogue box holds its rows without a box round each row, and a box is not put inside a box. **Every big section sits in a dialogue box** (a rank and XP strip, a list of teams, a settings group), never loose on the page background; only the header, a menu grid and a picture window stand outside one.
- **Pixel parts, no rounding.** Text fields, bars, toggles, sliders, cards, picture windows and the header bar use stair corners of whole 2 dp art pixels (`StairShape`: `DialogueShape`, `PixelShape`, `PixelDisc`), drawn in code. Material's rounded `OutlinedTextField`, `Switch` and `Slider` are not used: `PixelField` (outline like a key, a label above it or a placeholder inside, text centred in 48 dp), `PixelToggle`, `PixelSlider`, `PixelBar` and `ExpBar` replace them. A row that puts a field and keys side by side centres them on one line (`verticalAlignment = CenterVertically`); a label for such a field goes above the row, never inside the field.
- **Nothing is round.** Windows onto a scene (`Stage`, `SceneStage`), the background picker and the launcher use `PixelShape` or `DialogueShape`; swatches, badges and dots are pixel squares; a circle that must be a circle (the breathing circle, the compass, map markers) is a stair-stepped pixel disc (`drawPixelDisc`, `drawPixelRing`). `RoundedCornerShape`, `CircleShape`, Material menus and `drawCircle` are not used for chrome.
- **Every module page opens on its lead** (`ModuleLeadStage`): a short picture window (112 dp) with the module's own scene and its lead idling in it, level and progress small in a corner, a tap to pet it. Nothing shows until the module has a lead. **The page keeps one order in every module:** the short lead, an overview of the module's main records (Plan's today's open tasks, three at most, with an "All N tasks" key, Mint's balance with its accounts and budgets, Care's routines, Heal's day, Base's and Link's coming-up list), the commonly done action items in a box titled **Quick actions** (add a task, an expense, a drink, a chore, a person; set-up such as adding a routine or an account is not one), then the party last, a short row (40 dp sprites, little padding). Plan has no on-page menu: its places are in the header Sections key, and Progress and Trends (Plan progress, Care trends) live in Me. Anything that is not the party (Care's visitors and Egg meter, search) sits between the action items and the party at most; World's page keeps its own order (region, expeditions) and has no party.
- **The buddy lives on Home only** (`HomeBuddyStage`, in a scene of its own type). Module pages show their own party and lead, never the buddy, and the PC list shows only Pokémon that are in no other place (`Places.of == Pc`), so the buddy is never in the PC.

### 5.2 Spacing and sizing

Spacing is a 4 dp-based scale of named tokens (`Spacing`, values in section 5.5; the `spacing-system` skill has the rules). Component APIs use semantic aliases such as `contentInset`, `panelPadding`, `rowGap`, `sectionGap` and `pageGutter`, not raw numbers.

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

### 5.5 Code tokens and type scale

`Pixel` in `PixelTheme.kt` holds the colours in code, light / dark: Paper `#F7F5F0` / `#14151F` (`background`), Panel `#FFFFFF` / `#1F2132` (`surface`), Shade `#ECECF2` / `#2B2E44` (empty tracks, secondary surfaces), Border `#E1E1E8` / `#3A3E58`, Ink `#252637` / `#ECEDF5`, InkSoft `#666B7E` / `#A9AEC4`, Red `#D23B2E` / `#F2665A`, Blue `#2C6CCC` / `#5B96EE` (selection, focus, links), Green `#1E7F52` / `#34B07A` (success, a running session), Warning `#946000` / `#D99A1E`, Danger `#B42332` / `#EC6A75` (data at risk only), Chrome `#2B3050` (both). Night, Scrim, Violet, Pink, Teal and Amber are identity or overlay colours, never state. Red text-on-fill is 4.8:1; text is at least 4.5:1 (3:1 only for 24 sp or bold 18.66 sp and up); no colour literal outside `theme/`, the day palette and `PmdPalette`. Pokémon type colours stay in their own palette and never colour unrelated controls. Colour means one thing: red the main action, green done or running, blue selected or focused.

- **Spacing (`Spacing`):** 1 and 2 dp hairlines, 4 `XS`, 8 `S`, 12 `M`, 16 `L`, 20 `XL` / `Page`, 24 `XXL` / `Section`; no 6 or 10 dp. A dialogue box keeps `BoxX` 24 dp sideways and `BoxY` 18 dp above and below. `Spacing.Target` = 48 dp; no literal 44. Icon and sprite sizes come from `Sizes` (`IconS` 24, `Icon` 32, `IconL` 40, `SpriteS` 56, `SpriteM` 72, `SpriteL` 96 dp).
- **Type scale:** four sizes, never below 12 sp: 24 (screen titles, big numbers), 18 (section titles and button labels, pixel font bold), 16 (body), 14 (secondary lines, labels, tab labels). When a screen seems to need another size, use weight, colour and spacing. Titles, labels and buttons use the dialogue bitmap font (`PixelTitle`, `PixelLabel`: dark on light, white on dark and on coloured faces; a text it cannot draw falls back to the Pokémon DP font); body, descriptions and user text are Pokémon DP regular. The dialogue font (`PmdText`) is the only font of the World / launcher register and is not used in utility screens; long text there is cut with "...". In a World box, text starts 9 art pixels inside the fill (3 for the box's inset, 6 for the line where the cursor goes). Widgets use 15 sp for a title and 13 sp for the rest.
- **Motion:** purposeful only (a screen change, a reward, a buddy reaction); everything stops under Reduce motion, and anything that loops pauses when the screen is off or the app is in the background. Scenes and towns fade in over 150 ms (`rememberFadeIn`; instant under Reduce motion); no spinners.
- **Layout breakpoints:** compact (under 600 dp) one column with the tab bar at the bottom; medium (600 to 839 dp) a centred column at most 640 dp wide; expanded (840 dp and up) the same column plus a 96 dp side rail with the four tabs. At 1.3x text and up a button never sits beside text that no longer fits; it goes underneath.
- **Tab icons** are real item sprites, one per destination: Home `town-map`, Modules `explorer-kit`, Pokémon `poke-ball`, Me `member-card`. A section's icon is at the start of its header; when the header has Back, it moves to the end.

### 5.6 Controls and states

Every tappable key (button, tab, chip, arrow, segment) is one `PixelKey` (`theme/Controls.kt`): the same stair-cornered outline, states and a 48 dp target; a control only picks its key colour and content. `ScreenshotTest.componentGallery` renders every state (`components.png`, `-dark`, `-xl` at 130% text).

| State | Face | Outline | Letters | Notes |
|---|---|---|---|---|
| Rest | panel | key colour | key colour (ink for neutral keys) | |
| Hovered | key at 10% over the panel | key colour | key colour | pointer only |
| Pressed | key colour | key colour | white | shrinks to 98% unless Reduce motion |
| Selected | key colour | key colour | white | tabs, chips, segments; announced as `selected`; pressed goes darker |
| Focused | any of the above | plus a 2 dp blue ring on the touch target (`Modifier.focusRing`, placed before the `clickable`) | | keyboard and D-pad only |
| Disabled | panel | border | ink-soft at 55% | not tappable |
| Loading | as disabled | border | three blinking pixels | not tappable; holds three solid pixels under Reduce motion |

A key in the loading state keeps its label's width, so the row does not jump when the pixels appear, and keeps that label in the accessibility tree (TalkBack reads "<label>, working"); a loading key is never tappable. `LoadingDots` (`theme/Loading.kt`) is the only indeterminate motion in the app and holds three solid pixels under Reduce motion and when the phone's own animations are turned off. It follows the frame clock rather than an animation tween, which shrinks to nothing when animations are off, so it cannot freeze on its first pixel.

Neutral keys (`InkSoft`, `Ink`, `Gray`) look grey with ink letters; a light key colour (yellow) gets darker letters; colour is never the only signal; keys in wrapped rows fill their 48 dp target and buttons are 38 dp (32 compact) inside it. Rows (`ChoiceRow`, `ListRow`), `Notice` and `StatChip` share the key outline. `PixelField` (`theme/Fields.kt`) has the same idea: rest (panel, border), focused (2 dp blue), error (2 dp danger; the error text replaces the helper line and is announced), disabled (shade, letters at 55%), read-only. Use a label when the field is alone on a line and a placeholder beside a button. `ListRow` (when tappable) and `NavButton` also hover (a faint tint) and press (98% scale).

**Pills or a dropdown:** a row of `TabButton` pills is for 2 to 4 short fixed options, multi-select toggles (weekdays, tags), date and time presets and tabs that swap a view. A single choice that can grow or run long is a `PixelDropdown` (`screens/Tabs.kt`): one key with the current choice and a chevron, opening a stair-cornered list in a dialogue frame as wide as the key, scrolling when tall, the chosen row ticked as well as tinted. Use it when the options come from what the person made (lists, goals, accounts, categories, areas, people, Pokémon), when there are six or more, or when a label can be long enough that three would wrap on a 360 dp phone. An "All" or "None" pill becomes the dropdown's first entry.

Other shared parts: `Notice(text, tone)` (Info, Warning, Danger) is one calm sentence saying what happened and what to do, announced politely; Danger only where data could be lost. `MenuGrid` is two columns, a lone last card spanning the full width. A completion offers Undo. Component states to cover: task cards (active, overdue, completed, selected), completion (pending, completing, completed, undo available), Pokémon cards (normal, selected, active companion, unavailable), timer (idle, running, paused, complete, interrupted), form fields (empty, focused, filled, invalid, disabled).

**UI tap sounds:** a tap on a button, tab, chip or arrow plays a click (`CONFIRM`, `BACK`, or `CURSOR` for tabs, chips and arrows) at `TAP_GAIN` (6%) of the Sound effects volume (`UiSound.kt`); game sounds keep their full volume.

### 5.7 Screen and copy principles

- Pokémon supplies the personality; the productivity interface supplies the clarity. The game layer must not become another demanding task system: keep actions fast, make progression understandable, never punish a missed day, and keep productivity usable without engaging with game mechanics.
- Background texture is selective (the dither is the only texture); depth is subtle; thick pixel treatments are for important game actions and reward moments. No coloured strip on the left edge of a card: status shows in words, an icon or the button.
- Copy: no middle dot (·) as a separator (a comma or a new line); no "tap here to..." hints; plain friendly words for players of the games, no made-up terms (say "Long-term", not "long road"); ask the player to decide only where they already mean to choose, and show no setting that does nothing yet.
- Screen requirements: Home keeps one primary call to action, the next useful action more prominent than secondary stats, never obscured by the tab bar, with first-run, no-task and all-done states. Planner: creating a task needs only a title; difficulty grades E/D/C/B/A are explained and have accessible labels; edit, reschedule and delete are discoverable and swipe has a visible alternative; recurring tasks never treat a missed occurrence as personal failure. Focus: timer state obvious; Deep Focus says what it restricts, what counts as success and how to exit; elapsed time is not silently corrupted by backgrounding (timestamps, not ticks); elapsed is distinct from successfully focused; reward feedback brief and skippable. Pokémon: Dex shows seen, owned and undiscovered; animations for levelling, hatching and evolution are brief and skippable. Me: every metric has a definition and a time range; time allocation, completed tasks and goal progress are not interchangeable.
- Navigation: keep the four tabs; preserve the selected tab and scroll position; confirm destructive or hard-to-reverse actions, not routine completion; fixed navigation never covers scrollable content or primary actions; notifications and animations are dismissible; avoid flashing and long reward animations.
- Reward principles: reward meaningful effort, not the creation of many low-value tasks; longer focus is not so rewarding that people skip breaks; formulas stay understandable; no random rewards that feel unfair; core productivity is never locked behind game engagement.

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
- **Exact entry:** every date, time of day and length can be typed, never only picked from keys or nudged by a stepper. A date field (`PixelDateField`) is quick keys, a month to tap *and* a text box taking `2026-11-07`, `7/11/2026` (day first, four-digit year) or `7 Nov 2026`; a time field (`PixelTimeField`) takes `9`, `930`, `21:05`, `9:30 pm`, `noon`; a length field (`PixelDurationField`) takes `45`, `1h30`, `1:30`; a moment (`PixelMomentField`) is a day and a time. Each reads back what it understood ("Thu 12 Nov 2026", "9:30 am", "1 h 30 min") and says what it takes when the text is not yet a value; a valid value is applied at once, so Save always reads what is on the screen. The rules are `ExactInput` in app-core (tested once, every spelling). A number that could be read two ways is never guessed. Whatever a record's date or time is when it is made, it can be corrected afterwards; a correction never pays and never takes back (rewards follow the first save).

### 6.4A Forms

Every form is built from the form kit (`screens/Form.kt`) and follows eight principles; the reference is Mint's Add transaction (`ActivityView`).

1. **Prioritize:** the figure or name the form is about comes first and is the largest (`PixelField(large = true, prefix = ...)` for an amount), above a thin `FormDivider`.
2. **Group:** related fields sit in a `FormGroup` ([Spacing.S] apart); groups are [Spacing.L] apart in a `FormStack`. No stray key hangs alone on a line.
3. **Label:** every field, dropdown and choice has one small soft label above it (`FieldLabel` / `FormLabel`, the same voice as `PixelField`'s). Section titles in the big font are for sections of a screen, not for a field.
4. **Consolidate:** one control per decision. One of a few is a `PixelChoice` (a segmented row for up to four labels of nine letters or fewer, else a `PixelDropdown`); a second control that does the same thing (a toggle beside a dropdown, a "more" key beside a list) is merged into the first.
5. **Progressively disclose:** optional and advanced fields (payee, note, rare kinds, a second way to enter) sit behind one `FormDisclosure` row ("Additional details", a chevron), closed until asked for or until what the form holds needs them.
6. **Standardize:** 48 dp fields, keys and dropdowns; one label style; one gap; one full-width `FormSave` at the bottom in the module's colour.
7. **Respect mobile space:** a form is a column of 48 dp rows, with segmented and inline pairs (a time and its "Now") sharing a line instead of stacking big square controls; `FormBox` keeps the dialogue frame but with 16 dp inside it, not 24.
8. **Preserve context:** the frame, stair corners, pixel fonts and module colour stay; framing never costs a field its width.

### 6.5 Lists and list rows

- Shared `ListRow`, `SettingRow`, `TaskRow`, `DataRow` and `SelectableRow` patterns.
- Rows align primary text, secondary text, leading/trailing icons and values consistently.
- Prefer dividers, spacing or subtle grouping over a separate framed card for every row.
- A list of modules may wash each row with that module's accent (`MenuList(rowTint = true)`): the wash stays inside the group's one dialogue box and runs flush to its frame (the rows' contents keep the usual dialogue-box inset), never a frame per row, and the palette's ink keeps AA contrast on it.
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
- **Moving between modules and sections.** The four bottom tabs stay the only global navigation; a phone gets no side drawer (its edge swipe is the system Back gesture, and a drawer hides what the tabs show). Depth is cut inside the Modules tab instead:
  - **A module opens on its daily view**, not on a menu of cards, and **its content starts directly under a one-row header**: a compact Back key, the title, and one small, quiet "Sections" key at the end of the same row. No second row of keys. Home's module cards open that view in one tap.
  - **Home opens on the buddy, then one card for every module.** The cards follow PLAN, MINT, BASE, CARE, HEAL, LINK, WORLD in the rainbow order, except that a module with something due or overdue floats above the quiet ones (ties keep the rainbow order); each card is a module-accent box with the module's lead, one truthful status line and one in-place action. A screen-locked module and gated MINT show only "Locked" and an Open key, never a count. There is no "Also today" row list: the cards are the summary.
  - **Sections sit behind that small labelled "Sections" key**, which opens a bottom sheet of compact horizontal rows (icon, title, count or badge; one dialogue box, no box in a box). The inline card grid and rows are gone from module pages. Plan's rows are Tasks, Calendar, Focus (phone only), Goals and Settings (`PlanSection`; PLAN.md section 3); Progress is in Me.
  - **A module's places are grouped, not one per feature (owner, 23 November 2026).** The six grouped life modules (PLAN, CARE, MINT, HEAL, LINK, WORLD) have at most four primary destinations each; a destination may hold two or three related views behind one row of pills at the top of its page, and the pills are the only row of keys on that page. BASE and GEAR keep the places their own specs give them. The places a module keeps for less often used set-up and data (Goals, Connections, Import, Review, Settings, Skills, Finds, Follow-ups) follow the primary destinations in the same Sections sheet, so nothing loses its door; the sheet lists them after the primary ones. The groupings are: PLAN Tasks, Calendar, Focus; CARE Practice (routines and calm), Reflect, Dream; MINT Accounts, Activity, Plans (budget, bills, income); HEAL Activity (steps, exercise, sleep), Wellness (nutrition, hydration, measurements, vitals, period), Records (the medication log today; the timeline and illnesses when built); LINK People, Memories, Reminders (owner, 25 November 2026: Memories replaced planned Moments and Reminders replaced the separate Dates and Follow-ups places); WORLD Explore (Atlas and Guild), Expeditions, Town, Journal. A destination whose feature is not built yet is not listed.
  - **No module switcher on a module page.** The bottom Modules tab already switches module; tapping it again while it is open returns to the list of modules. A switcher key would only repeat it.
  - **The Modules list is compact rows** (icon, title, badge) in the rainbow order, not large square cards; each row carries a light wash of its module's accent that runs flush to the single list box's frame, so the rainbow is read without a frame around every row. The **"Jump to"** field is hidden: pulling the list down at its top reveals it in about 140 ms (instantly under Reduce motion), and the close key, Back or an upward swipe with nothing typed puts it away. It is a plain search that opens any module or section by its fixed name (it reads no record, name or amount, so it is safe for Heal and Mint).
  - **Gear is not a life module.** It sits as a labelled "Gear" key in the Modules header (and in Jump to), outside the module list.
  - **A page opened inside a section is one level up before the section is left.** Where a section opens a page of its own (a town's facility, a journal page), the header's Back key and the system Back gesture both close that page and stay in the section; a second Back leaves the section. A Back press never discards a draft or a place by skipping the level it looked like it would close.
  - **Every tab keeps its place.** Switching tabs keeps the section open in that tab, an open record (a Pokémon, a Pokédex entry) and the scroll position, so coming back finds the tab where it was left. Arriving from a widget, a shortcut or a notification replaces the Pokégear instead of opening behind it; only Gear asks for it.

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
- **Loading:** three pixels, never a spinner, and the real content replaces its placeholder in place. `LoadingState` (three pixels and one short line) for a panel or a list header; `Skeleton`, `SkeletonRows` and `SkeletonPanel` (static pixel blocks of the coming content's shape, never a shimmer or pulse, 12A.5) for a row, list or panel not read yet; `LoadingScreen` for a full screen with only tokens and text; the app opening uses `StartupLoading` (the brand, one status line and the pixels), drawn in the palette the app closed in (kept in a small Android preference, with a night-aware window background) so the palette does not change when the first real frame arrives. It is not a skeleton of Home (owner, 27 November 2026: a placeholder that is not measured to the real screen looks bad); a page-shaped skeleton is allowed only where it is built from the real layout's own sizes; `LoadingOverlay` for a wait that blocks the page (a save being replaced, whose scrim swallows every tap). The work behind a loading state runs off the UI thread, so the pixels keep moving while it runs: a wait must never stop the frame clock. The startup screen is the first case of that rule: it stays up while the save opens and the stores and Dex tables are read off the UI thread, so its pixels keep moving until the first real frame is ready.
- `ErrorState`: what failed, whether data is safe, and the next useful action (retry, edit, reconnect or continue offline).
- `SuccessFeedback`: compact confirmation, inline state change or restrained toast/dialogue panel. Avoid repeated reward-like fanfare for routine actions. **One feedback line:** saved, reward and error all go through `AppModel.say` or `Report` and show in one bar above the tabs (`FeedbackHost`), the same on every screen; a screen never draws its own message box. A problem that stays until it is fixed uses `Report` (kept up while the screen holds it); a field that cannot be read still says so under the field.
- **Empty states:** every `EmptyState` has one button, in the primary colour: where something can be added, it adds (or moves to the field above); otherwise it takes the player to the thing that fills the list (a focus session for eggs, Clear for a search or filters that found nothing).
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
- Clickable tiles (Pokédex, PC, starters) have a role and a spoken label; Pokédex states, Growth numbers and focus time are written in words beside any colour or bar; long task names and localization must not break layouts; layouts work on short screens and varied aspect ratios; task, timer and reward behaviour survives interruption and restart. Open: the SpriteCollab credit line is deliberately faint (owner's choice) and below 4.5:1.

## 10. Pixel-art rendering rules

- Use nearest-neighbor sampling for pixel art; preserve source dimensions and pixel grid.
- Prefer integer scale factors where practical. If fractional scaling is unavoidable, inspect for uneven pixel widths and shimmering.
- Use nine-slice scaling for framed dialogue assets (section 4).
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
- Validate pixel rendering on device and emulator at the actual target density; do not rely on previews alone.

## 12. Module application checklist

Every module specification must include a short section titled **Visual presentation** that:
1. References `../FEATURES/DESIGN_SYSTEM.md` (or the correct relative path from that module file).
2. States the module's presentation intensity (utility or immersive World) and why.
3. Identifies which shared components are primary in the module.
4. Documents only genuine module-specific components or deviations, with a reason.
5. Confirms light/dark themes, text scaling, TalkBack, reduced motion and responsive layouts.

All module UI reviews must check:
- [ ] The module's main screen keeps the one order of 5.1: short lead, overview of its records, a Quick actions box, then the party last.
- [ ] Shared dialogue-box border/corner/surface grammar is recognizable.
- [ ] Content-driven sizing; no forced identical dialogue-box dimensions.
- [ ] No unnecessary boxes, captions, nested containers or decoration.
- [ ] Clear hierarchy for titles, descriptions, values, actions and secondary details.
- [ ] Consistent spacing, padding, typography, buttons and interaction states.
- [ ] Progressive disclosure for advanced controls.
- [ ] Light/dark, enlarged text, TalkBack, touch targets and responsive widths verified.
- [ ] Pixel art remains crisp and does not obstruct the task.

## 12A. Copy and anti-slop rules

Condensed from the owner's `unslop-ui` reference, for a native app. Slop is an unspecified default: the first thing that comes to mind, which every generated screen shares. A screen should read as if someone decided each thing on it.

1. **A caption must say something the screen does not already show.** No sentence that restates the title, the visible state or the button's label; no reassurance ("Only you see this", "Nothing here earns anything", "No hurry"), no tutorial paragraph under a menu, no "Each module is one part of your day". If a rule matters it belongs in Help, once. An empty state is a short title and at most one action, not an explanation. A menu card is an icon and a title. **Sections that hold records are rows, not cards:** an icon, a title and the count on one line, in one dialogue box (`MenuList`): the Planner's views, the Pokémon tab, World's Journal, Me. A module's landing page keeps its cards (`MenuGrid`).
2. **No breadcrumb under a header** that repeats where the Back button goes; a header's second line is a fact (rank, a count that changes), not a label.
3. **Specific copy, no clichés.** "Send a team into a dungeon", not "Unleash your team's potential". Say what the thing does in the words of the game.
4. **No emoji as icons or bullets**; item icons and Pixelarticons glyphs only (a buddy's tap emote is the one game-style exception).
5. **No gradient text, no glow, no animation on every element.** Motion tells state or a real event and respects Reduce motion; the dither background is the only texture.
6. **No uniform treatment.** Rounded corners everywhere, the same shadow on everything and a box round every row all read as generated. Use the stair corner, one frame per group (section 5.1), and hierarchy (size, weight, spacing) before another border. What hides until asked for follows 12B.
7. **Colours come from the tokens.** A new colour is a decision recorded in section 5.1 with its reason, not the next-most-likely hex; module accents follow the rainbow table, red is not chrome.
8. **Text is centred by its letters, and that is measured, not eyeballed.** The Pokémon DP font sits low in its line box, so `PixelTheme` lifts every text style (`baselineShift`) and the dialogue bitmap font draws one art pixel lower (`PmdFont.BASELINE`); `TextCentreTest` renders keys, fields, badges and each text style and fails if the gap above the letters and below them differ by more than 1 dp. A new font, size or control gets a line in that test. Rank badges are rounded pixel squares (`DialogueShape`), never circles.
9. **Layout quality is checked by eye on a real screenshot, in both palettes, on every change**: nothing clipped or overflowing, one spacing scale (`Spacing`), every row's contents on one centre line, edges aligned to the page gutter, one thing leading the eye per screen.
10. **One input per value.** A date is one row of keys (the typed box opens with the calendar); a time or a length is its keys and one typed box. No echo line in another format and no nudge keys; a typed value is rewritten in the app's own spelling when the box loses focus.
11. **No sentence-length copy in the UI** outside Help, What's New and the story: `CopyBudgetTest` fails on a new one (a field error, a confirmation, an accessibility label and feedback through `say` are not counted).

## 12B. On-demand controls

Owner decision, 23 November 2026: a screen shows its records first, and what is not needed on every visit stays out of sight until asked for (12A rule 6 applies: hierarchy before another box).

1. **Records first.** Whatever is not needed on every visit is hidden until asked for.
2. **Search, filter and sort sit behind the pull-down at the top of the list** (`PullReveal.kt`: `rememberPullReveal`, `pullReveal`, `PullRevealSlot`; the Modules search is the model). Pull down at the top to open it; a swipe up with nothing typed, the close key or Back puts it away; a query or filter in use keeps it open; a narrowed list always shows a visible "clear" state.
3. **An add form opens from one "Add X" key** and closes after saving; it opens by itself when the list is empty.
4. **Secondary information that can grow** (history, stats, a second chart, readings, earlier imports) goes in an `Accordion` (title, and a summary shown while closed). It starts open only when it holds the one thing the person came for or something waiting for a decision.
5. **Exports, erase and other rare or destructive controls** sit in closed accordions at the bottom of their page.
6. **Rare actions on a record** (rename, star, transfer) sit in one "More" accordion on its screen, transfer last.

## 13. Change control

Changes to shared tokens, component anatomy, border language, typography, navigation or global states must be made here first, then reflected in the Product Bible or Implementation Guide when they alter product-wide requirements or delivery scope. Module documents must not silently redefine shared rules. When a module has a legitimate exceptional need, document the exception in that module's Visual presentation section and keep the shared system intact.

---

## Testing reference

Shared components and representative screens require semantic Compose tests, approved light/dark golden images, responsive/font-scale coverage and accessibility verification as defined in [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md). Golden updates require explicit review.

### 6.12 What's New card

After an update, Home shows one card titled **What's new in `<version>`**: one bullet per line from `WhatsNew`, and a single **Got it** key that marks the version seen (`AppData.lastSeenVersion`, `GameStore.markVersionSeen`). It appears only while the running version has an entry and has not been seen; a version with no entry shows no card. It has no other dismissal and never covers the screen.

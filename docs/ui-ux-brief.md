# Pokémon Productivity App — UI/UX Audit & Design System Brief

## Purpose

Redesign the app so it feels like a **modern Pokémon game that helps users manage real life**, rather than a productivity app with Pokémon sprites added on top.

> **Status (October 2026):** this brief was written from screenshots for a Flutter app with five tabs. The app is **Kotlin and Compose Multiplatform** (`composeApp/`) with four tabs, **Home / Modules / Pokémon / Me**, and each life module (Plan, Care, later Grow and Home) is its own Pokémon region inside Modules. Its tokens are in `theme/PixelTheme.kt`. Read Flutter names below as their Compose equivalents (for example `MediaQuery.disableAnimations` is `Settings.reduceMotion`, a widget is a composable, a golden test is `ScreenshotTest`). Its principles, states and the accessibility checklist (section 9) still apply. The product direction from the October 2026 audit (regions, a lead per region, the PokéLife name, navigation transitions) is in `docs/ecosystem.md`, section 11.

The original framing: treat this document as the design direction and implementation brief, not as proof of the current architecture.

### Core principle

**Pokémon supplies the personality; the productivity interface supplies the clarity.**

The game layer should encourage real-life progress without becoming another demanding task-management system. Keep actions fast, make progression understandable, and avoid punishing missed days.

---

## 1. Current App Structure

*(Historical: the app now has four tabs, Home / Modules / Pokémon / Me; Planner, Focus, Growth, Goals and Review are sections of Plan inside Modules, and Care is a module beside it.)* The UI the screenshots showed had five main tabs:

1. **Home** — trainer summary, active Pokémon, next task, shortcuts.
2. **Planner** — Today, Inbox, Soon, Habits, Done; task creation and completion.
3. **Focus** — duration selection, focus timer, Deep Focus, egg hatching.
4. **Pokémon** — Party, PC, Dex, Bag.
5. **Me** — trainer profile, Growth, Goals, Review, Settings.

The brief said to keep five tabs; the owner moved to four tabs with a Modules tab once Care shipped (0.33.0).

## 2. Initial Screenshot Audit

### Strengths to preserve
- Recognizable pixel-art/Pokémon identity.
- Starter Pokémon and trainer progression.
- Pokémon collection vocabulary: Party, PC, Dex, Bag, eggs, levels.
- Bright, playful, game-inspired interaction style.
- Clear five-tab navigation.

### Issues to address
- **Overly uniform styling:** red headers, grass texture, rounded white cards, and pixel fonts appear almost everywhere. The result lacks visual hierarchy.
- **Background noise:** the repeating grass texture competes with content, especially behind long lists and empty space.
- **Typography:** pixel fonts used for long descriptions, metadata, and task content reduce readability.
- **Home hierarchy:** the next useful action is not prominent enough relative to the character, prompt, and other elements.
- **Layout consistency:** some screens have large unused areas; Home has actions partially obscured by bottom navigation.
- **Task interaction clarity:** difficulty grades E/D/C/B/A need an explicit meaning and accessible labels.
- **Focus reliability:** timer states, app interruptions, and Deep Focus behavior need clear rules and feedback.
- **Profile metrics:** statistics such as “This week by area” need clear definitions, units, and time ranges.

These are preliminary visual observations. Do not assume persistence, accessibility semantics, timer behavior, or code architecture are broken without testing.

---

## 3. Design Direction

### Target style: modern Pokémon adventure
Retain Pokémon identity through sprites, pixel-art accents, trainer ranks, Pokédex, items, eggs, discovery, evolution, and reward feedback.

Modernize the interface around that identity:
- Calm, warm, low-noise surfaces.
- Clear content hierarchy.
- Legible body typography.
- Consistent spacing and reusable components.
- Subtle depth rather than heavy shadows on every card.
- Game-like styling reserved for meaningful moments and primary actions.
- Background texture used selectively, not behind every screen.

Avoid making the UI sterile or removing its playful personality. Do not add decoration that makes task completion slower or obscures information.

---

## 4. Design Tokens — Initial Proposal

Treat these as **version 0.1 starting values**. Validate with actual Flutter screenshots, contrast testing, and device layouts before finalizing.

### Colors

| Token | Hex | Usage |
|---|---|---|
| `brandPrimary` | `#E84436` | Brand actions and selected highlights |
| `brandDark` | `#252637` | Primary text and strong controls |
| `background` | `#F7F5F0` | Main app background |
| `surface` | `#FFFFFF` | Cards, dialogs, sheets |
| `surfaceMuted` | `#ECECF2` | Secondary surfaces |
| `textPrimary` | `#252637` | Main content |
| `textSecondary` | `#666B7E` | Metadata and descriptions |
| `success` | `#1E7F52` | Completion and positive feedback (was `#218858`, darkened to pass 4.5:1 as text on cards) |
| `info` | `#2C6CCC` | Informational and secondary actions (was `#3478DB`, same reason) |
| `warning` | `#946000` | Attention and caution |
| `danger` | `#B42332` | Destructive actions and errors |
| `border` | `#E1E1E8` | Dividers, outlines, card boundaries |

Keep Pokémon type colors in a separate semantic palette. Type colors should not dictate unrelated app controls.

### Typography

Use at most two font families:
- **Display:** existing licensed pixel font for game headings, major stats, rank, and reward moments.
- **Body:** legible sans-serif such as Roboto for task names, descriptions, dates, settings, forms, and longer content.

Type scale: four sizes, no more (three is better), and never below 12 sp. When a screen seems to need another size, use
weight, colour and spacing instead.
- 24 sp: page titles and big numbers (the focus clock, a friend code).
- 18 sp: section titles and button labels (pixel font, bold).
- 16 sp: body.
- 13 sp: secondary lines, captions, chips and tab labels.

The widgets use two: 15 sp for a widget's title and 13 sp for the rest. Respect system text scaling and test enlarged fonts and long labels. Verify the pixel font license before distribution.

### Spacing and shape

Use a consistent 4 px-based spacing scale. Initial values:
- Page horizontal padding: 20 logical px.
- Card padding: 16 logical px.
- Card radius: 16 logical px.
- Button/control height: 48–52 logical px.
- Section separation: 24 logical px.
- Use 4, 8, 12, 16, 20, 24, and 32 px spacing values as appropriate.

Flutter dimensions are logical pixels, not physical screen pixels. Adapt to constraints and accessibility settings rather than enforcing fixed heights.

### Surfaces and depth
- Cards: white surface, 16 px radius, subtle border.
- Primary buttons: solid brand color with sufficient text contrast.
- Secondary buttons: neutral surface with visible border.
- Selected tabs: clear filled treatment, icon, and label.
- Progress: neutral track and semantic fill; add a label when meaning is not obvious.
- Shadows: reserve for floating elements, dialogs, and selected elevated controls.
- Pixel borders and thick raised-button treatments: reserve for important game actions/reward moments, not every component.
- No coloured strip on the left edge of a card; status shows in the words, an icon or the button.
- Nested rounded shapes are concentric: an inner corner radius is the outer radius minus the inset (a 16 dp card with an
  8 dp inset holds 8 dp corners). Two radii cover almost everything: 16 dp for cards, 12 dp for controls and tiles, plus
  full pills and circles.

### Copy
- No middle dot (·) as a separator; use a comma or a new line.
- No line that only repeats what the title, a button or the screen header already says, and no "tap here to..." hints.
- Plain, friendly words for players of the games: not slangy, no made-up terms (say "Long-term", not "long road").
- Ask the player to decide only where they already mean to choose; a setting that does nothing yet is not shown.

---

## 5. Shared Component System

Build reusable components before polishing each screen. Adapt naming and structure to the existing repository; do not refactor architecture unnecessarily.

Components to standardize:
- Primary and secondary buttons.
- Task card and completion control.
- Pokémon card and selection state.
- Progress bar.
- Tabs/segmented controls.
- Bottom navigation.
- Empty state.
- Dialog and bottom sheet.
- Toast/feedback message.
- Form field.
- Timer controls and session status.

Define states consistently:
- Buttons: default, pressed, disabled, loading.
- Task cards: active, overdue, completed, selected.
- Completion: pending, completing, completed, undo available.
- Progress: empty, active, complete.
- Pokémon cards: normal, selected, active companion, unavailable if applicable.
- Tabs: selected, unselected, disabled.
- Form fields: empty, focused, filled, invalid, disabled.
- Timer: idle, running, paused, complete, interrupted.
- Feedback: success, warning, error, informational.

Use shared theme values and component constructors rather than scattering hard-coded styles throughout widgets.

---

### 5.1 Controls (Compose app, 0.90.2)

Every tappable "key" (button, tab, chip, arrow, segment) is one `PixelKey` (`theme/Controls.kt`): the same stair-cornered outline, the same states and a 48 dp touch target. A control only picks its key colour and its content. The table in that file is the specification; `ScreenshotTest.componentGallery` renders it (`components.png`, `-dark`, `-xl` at 130% text).

| State | Face | Outline | Letters | Notes |
|---|---|---|---|---|
| Rest | panel | key colour | key colour (ink for neutral keys) | |
| Hovered | key at 10% over the panel | key colour | key colour | pointer only (desktop, web) |
| Pressed | key colour | key colour | white | shrinks to 98% unless Reduce motion |
| Selected | key colour | key colour | white | tabs, chips, segments; announced as `selected`; pressed goes darker |
| Focused | any of the above | plus a 2 dp blue ring on the touch target | | keyboard and D-pad only |
| Disabled | panel | border | ink-soft at 55% | not tappable |
| Loading | as disabled | border | three blinking pixels | not tappable; still under Reduce motion |

Rules: neutral keys (`InkSoft`, `Ink`, `Gray`) look grey with ink letters; a light key colour (yellow) gets darker letters on the panel; colour is never the only signal (selected and disabled are in the semantics); keys in wrapped rows fill their 48 dp target (`fill`), buttons are 38 dp (32 compact) inside it. Rows (`ChoiceRow`, `ListRow`), `Notice` and `StatChip` share the same outline (`pixelFrame`, `pixelPlate`); text fields take the dialogue corner and the blue focus colour from the Material theme. 
Text fields are `PixelField` (`theme/Fields.kt`), with the same five-state idea: rest (panel, border), focused (2 dp blue), error (2 dp danger; the `error` text replaces the helper line and is announced), disabled (shade, letters at 55%) and read-only. Labels, placeholders and helper text are plain strings; use a label when the field is alone on a line and a placeholder beside a button. Not yet in the system: `NavButton` (on the coloured tab bar) has no hover, and fields have no hover outline.

**UI tap sounds:** a tap on a button, tab, chip or arrow plays a click (`CONFIRM`, `BACK`, or `CURSOR` for tabs, chips and arrows) at `TAP_GAIN` (3%) of the Sound effects volume (`UiSound.kt`); game sounds keep their full volume.

## 6. Screen Requirements

### 6.1 Home — P0

**Purpose:** Help the user decide what to do next.

Recommended hierarchy:
1. Compact trainer header with name/rank and progression.
2. Active Pokémon companion.
3. One recommended task or clear task-selection action.
4. Today's progress.
5. Secondary shortcuts to Planner and Focus.

Requirements:
- Keep the header shorter than the current oversized red header.
- Make the next useful action more prominent than secondary stats.
- Keep one primary CTA.
- The task card may open task details; completion should remain a distinct action.
- Show the character prominently without a large empty stage unless it has meaningful interaction/animation.
- Ensure bottom navigation never obscures actions.
- Design first-run, no-task, and all-tasks-completed states.
- Do not punish users for missing a streak.

### 6.2 Planner — P0

**Purpose:** Capture, organize, and complete tasks efficiently.

Keep current filters unless testing shows they are confusing:
- Today
- Inbox
- Soon
- Habits
- Done

Requirements:
- Task creation requires only a title; other fields are optional/advanced.
- Make Add action obvious.
- Explain difficulty grades E/D/C/B/A, if retained; provide accessible labels. Do not leave letter grades unexplained.
- Task card hierarchy: title first, due date/relevant metadata second, completion action clear.
- Keep category, duration, difficulty, and dates available without showing every attribute on every card.
- Support edit, reschedule, and delete with discoverable actions.
- Provide visible alternatives to swipe gestures.
- Completion gives immediate feedback and offers Undo.
- Support recurring tasks without treating missed occurrences as personal failure.
- Design empty states for every filter, plus long titles, overdue dates, and populated lists.

### 6.3 Focus — P0

**Purpose:** Start and maintain a focus session.

Recommended hierarchy:
1. Selected task or “Nothing in particular.”
2. Timer and duration controls.
3. Start/Pause/Resume/Finish actions.
4. Egg progress and Deep Focus settings.
5. Session outcome and reward.

Requirements:
- Keep duration presets and custom adjustment.
- Make timer states obvious.
- Explain what Deep Focus restricts, what counts as success, and how to exit.
- Define app-backgrounding, screen-lock, interruption, pause, and resume behavior.
- Do not silently corrupt elapsed time when the app is backgrounded.
- Prefer reliable timestamps/session state over relying solely on repeated UI timer ticks.
- Keep normal focus usable without requiring the phone to remain physically still. If stillness earns a bonus, explain it and keep ordinary timing available.
- Distinguish time elapsed from time successfully focused.
- Reward feedback should be brief and skippable.

### 6.4 Pokémon — P1

**Purpose:** Collect, inspect, and manage Pokémon.

Current subsections:
- Party
- PC
- Dex
- Bag

Requirements:
- Party: up to six Pokémon, with clear selection, level, status, and companion designation.
- PC: storage with sorting/filtering; distinguish stored from active Pokémon.
- Dex: distinguish seen, owned, and undiscovered entries. Preserve discovery if that is a game mechanic.
- Bag: group items by purpose; explain acquisition and use.
- Detail view: stats, level, growth, evolution conditions, favorite status, and productivity role as appropriate.
- Design acquisition, duplicate, storage, evolution, and loss rules before expanding features.
- Make animations for leveling, hatching, and evolution brief and skippable.
- Ensure visual states accurately reflect game data.

### 6.5 Me — P1

**Purpose:** Show trainer progress, goals, reflection, and settings.

Recommended hierarchy:
1. Trainer card: name, rank, current Pokémon, total XP.
2. Growth overview: tasks completed, focus time, streak history.
3. Goals.
4. Weekly review.
5. Settings, accessibility, notifications, and data management.

Requirements:
- Define each metric and its time range.
- Distinguish time allocation, completed tasks, and goal progress; they are not interchangeable.
- Avoid overwhelming the overview with all six life areas if detailed breakdowns fit better in Review.
- Do not rely on color alone to communicate trends or status.

---

## 7. Navigation and Global Interaction Rules

- Keep the four primary tabs: Home, Modules, Pokémon, Me (`Tab` in `App.kt`). Home shows every region, not only Plan.
- Preserve selected tab and relevant scroll position when navigating away and returning, where appropriate.
- Use consistent back navigation for detail screens and modal sheets.
- Fixed navigation must not cover scrollable content or primary actions.
- Use confirmation for destructive or difficult-to-reverse actions, not routine task completion.
- Offer Undo after task completion.
- Show loading, saving, error, and success states.
- Do not use color as the only status indicator.
- Give icon-only controls accessible names.
- Respect Android font scaling, display size, system insets, keyboard, and navigation bar.
- Target at least 48 × 48 logical px for important touch targets, with adequate spacing.
- Support reduced motion; avoid flashing and unnecessarily long reward animations.
- Make notifications and animations dismissible.
- Keep productivity tasks usable without engaging with game mechanics.

---

## 8. Gamification/Product Rules to Resolve

Do not invent or change reward mechanics during a visual-only refactor. First document existing behavior, then propose changes for approval.

Resolve:
- Trainer XP: rewards per action; whether difficulty affects XP.
- Trainer ranks: thresholds, unlocks, maximum-rank behavior.
- Pokémon XP: whether it comes from task completion, focus, or both.
- Leveling: automatic or manual; animation behavior.
- Egg hatching: progress rules and interrupted-session outcomes.
- Streaks: what counts as meaningful daily activity; missed-day behavior.
- Habits: how recurring tasks differ from one-off tasks.
- Difficulty: impact on XP, planning, or both.
- Items: purpose, use, and whether they can be wasted/lost.
- Evolution: conditions and reversibility.

Principles:
- Reward meaningful effort, not creation of excessive low-value tasks.
- Avoid making longer focus sessions so rewarding that users avoid breaks.
- Keep reward formulas understandable.
- Do not erase progress or shame users for missing a day.
- Avoid random rewards that make outcomes feel unfair.
- Do not lock core productivity features behind game engagement.

---

## 9. Accessibility and QA Checklist

Validate in the actual app; do not infer success from screenshots alone.

**Status in the Compose app (0.17.x, checked in code; still to confirm on a phone with TalkBack and large text):** text tokens pass 4.5:1 on cards and the page in both themes (`success`, `info` and dark `danger` were darkened or lightened for it) and button labels, which are large bold text, pass 3:1; every button, compact ones included, is at least 48 dp tall; clickable tiles (Pokédex, PC, starters, the dialogue box) have a role and a spoken label; Pokédex states, Growth numbers and focus time are written in words beside any colour or bar; long animations respect Reduce motion. The `large-text-*` screenshots cover enlarged text. Open: the SpriteCollab credit line is deliberately faint (owner's choice, 0.13.0) and below 4.5:1.

- [ ] Text contrast meets WCAG 2.2 AA where applicable: typically 4.5:1 for normal text and 3:1 for large text.
- [ ] Body text remains readable without the pixel font.
- [ ] Important controls have accessible labels and adequate touch targets.
- [ ] Layout supports enlarged Android text/display settings.
- [ ] Bottom navigation does not obscure content.
- [ ] Status is communicated with more than color.
- [ ] Screen readers can identify buttons, tabs, progress, and task status.
- [ ] Motion respects reduced-motion preferences.
- [ ] Empty, loading, offline, error, and success states are handled.
- [ ] Long task names and localization do not break layouts.
- [ ] Layout works on short screens and varied aspect ratios.
- [ ] Task/timer/reward behavior survives app interruption and restart as intended.
- [ ] Navigation and existing functionality have regression tests.
- [ ] Major component states have widget tests; key flows have integration tests.
- [ ] Use consistent screenshot/golden tests where practical.

---

## 10. Flutter Implementation Guidance

Implement the design system centrally, then migrate screens incrementally. Do not replace the app architecture merely to achieve a visual redesign.

Suggested structure (adapt to the repository's actual structure):

```text
lib/
  app/
    app.dart
    app_theme.dart
    app_colors.dart
    app_typography.dart
    app_spacing.dart
  shared/
    widgets/
      app_card.dart
      app_button.dart
      app_progress_bar.dart
      app_empty_state.dart
      app_bottom_navigation.dart
      task_card.dart
      pokemon_card.dart
  features/
    home/
    planner/
    focus/
    pokemon/
    profile/
```

Flutter-specific requirements:
- Use `ThemeData`, `ColorScheme`, `TextTheme`, and shared component constructors.
- Prefer `const` widgets when practical.
- Use `SafeArea` and correct scaffold/system insets.
- Use scrollable layouts that adapt to content height; avoid fixed device-specific heights.
- Use `MediaQuery` and constraints for responsive decisions.
- Ensure body scroll content accounts for bottom navigation and system insets.
- Keep gameplay logic separate from presentation code.
- Preserve existing routing and state management unless a concrete issue justifies changing them.
- Load font assets only after checking license and existing font configuration.
- Respect `MediaQuery.disableAnimations` or the relevant supported reduced-motion behavior in the project’s Flutter version.
- Test semantics for custom controls and progress indicators.
- Use reliable timestamps/session state for timer correctness.

Suggested Flutter components:
- `AppCard`
- `AppButton`
- `AppProgressBar`
- `AppEmptyState`
- `AppBottomNavigation`
- `TaskCard`
- `PokemonCard`
- Shared tab/segmented control
- Shared dialog/bottom sheet
- Shared feedback/toast
- Timer controls and session status

These are conceptual names; inspect existing widgets before creating duplicates.

---

## 11. Implementation Phases and Priority

### Phase 1 — Screenshot and UI inventory
**Output:** screen/state inventory and issue list.

Capture full-screen states at top/middle/bottom; dialogs, sheets, task creation/completion, focus session states, and populated/empty screens. Separate verified visual issues from assumptions about code or behavior.

### Phase 2 — Shared design system
**Output:** centralized tokens, theme, typography, and reusable components.

Finalize color/spacing/type scales, component states, navigation styling, and background rules. Validate contrast and device layouts.

### Phase 3 — Home and Planner (P0)
**Output:** redesigned core productivity flows.

Implement shared header, task cards, primary actions, progress, empty states, and navigation. Test create, complete, undo, overdue, return-navigation, and short-screen flows.

### Phase 4 — Focus and rewards (P0)
**Output:** reliable focus experience and consistent reward feedback.

Implement timer states, interruption handling, egg progress, Deep Focus explanation, and skippable reward presentation. Test timer correctness independently of visual polish.

### Phase 5 — Pokémon and Me (P1)
**Output:** coherent collection, profile, goals, and review experiences.

Standardize cards, filters, statistics, settings, progression, and reward states.

### Phase 6 — Accessibility, regression, and release QA
**Output:** validated build and QA checklist.

Test physical Android devices, font scaling, contrast, semantics, touch targets, navigation, app restoration, persistence, and before/after screenshots.

### Priority summary
1. Design tokens and shared components.
2. Home hierarchy, Planner usability, and navigation overlap.
3. Typography, spacing, background treatment.
4. Focus timer reliability and reward feedback.
5. Pokémon collection and trainer progression.
6. Accessibility, responsive layouts, regression tests.

---

## 12. Screenshot-Based Audit Workflow

Code access is not currently available, so use screenshots to refine the visual specification. Request screens that reveal behavior, not just repeated initial views.

### Home
- After completing a task.
- Several tasks and progress.
- Scrolled to bottom.

### Planner
- Task creation and difficulty selection.
- Task edit/details.
- Inbox, Habits, Done states.

### Focus
- Running timer.
- Paused/completed timer.
- Deep Focus enabled and any permission prompts.

### Pokémon
- Pokémon detail.
- PC and Dex contents.
- Bag, reward, or evolution flow.

### Me
- Goals and Review.
- Settings/accessibility.
- Populated progress chart.

For each screenshot, record:
- Observed issue.
- User impact.
- Proposed change.
- Priority.
- Acceptance criteria.
- Whether it is visually verified or needs behavior/code testing.

Do not claim to verify persistence, semantics, performance, or state management from screenshots alone.

---

## 13. Instructions for the Claude Coding Agent

Before modifying code:

1. Inspect the existing Flutter project, navigation, state management, theme, font assets, and reusable widgets.
2. Identify existing implementations before creating replacements.
3. Map each screen to the requirements in this document.
4. Report architecture assumptions and any conflicts with the current code.
5. Propose a small, ordered implementation plan.
6. Preserve existing functionality and data behavior unless a change is explicitly agreed upon.
7. Do not invent new gamification mechanics or change XP/evolution/streak rules during visual refactoring.
8. Implement the design tokens centrally and migrate one screen at a time.
9. Avoid broad rewrites and unrelated refactoring.
10. Run available Flutter analysis, tests, and screenshot/golden checks.
11. Report changed files, test results, remaining issues, and anything that needs manual device validation.

### Expected result

A cohesive, modern Pokémon-inspired Flutter app with:
- Recognizable game identity.
- Clear and accessible productivity workflows.
- Consistent design tokens and reusable components.
- Reliable timer and reward feedback.
- Calm, readable layouts across Android screen sizes.
- No regressions to existing functionality.

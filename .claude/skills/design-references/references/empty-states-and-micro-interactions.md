# Reference: empty, error and offline states (404s.design) and micro-interactions (interior.dev, detail.design)

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


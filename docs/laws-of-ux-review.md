# Laws of UX review (0.19.0)

Every screen of the Compose app (`composeApp/`) checked against the 30 laws on [lawsofux.com](https://lawsofux.com/),
read on 26 October 2026. Each law is filtered through the project's own rules first: `CLAUDE.md`, `docs/design.md` (No
shame, the buddy never chats, real assets only) and the `design-references` skill win where they disagree. **Fixed**
items shipped in 0.19.0; **Kept** means the screen already follows the law; **Later** is a finding left for another
change, with the reason.

## What changed, by law

| Law | Finding | Change |
| --- | --- | --- |
| Jakob's Law | Back sat at the top right as a white button; every phone app puts it at the top left. | **Fixed:** `ScreenHeader(onBack)` puts "‹ Back" at the start; the section icon moves to the end. Used by `SectionScaffold` and the Pokémon page. |
| Jakob's Law, Law of Similarity | On/off settings were "On"/"Off" buttons: a green raised "On" looked like an action, and six of them in Settings competed with the real actions. | **Fixed:** `ToggleRow` (a switch, `Role.Switch`, the whole row is the target) in Settings, Focus (Deep Focus), Calendars and the weekly review. |
| Law of Similarity, Von Restorff | Focus → "Working on" drew every task as a raised button and the chosen one solid blue, so a choice looked like the main action. | **Fixed:** `ChoiceRow` radio rows (dot, darker border, `Role.RadioButton` in a selectable group). |
| Fitts's Law, Serial Position | Focus Start was the last thing on a long scroll, below the fold on most phones. | **Fixed:** Start is pinned under the scroll, full width, near the thumb, and says the length ("Start 25 min"). |
| Fitts's Law, Selective Attention (change blindness), "nothing jumps" | Undo for a finished task appeared as a card at the top of the page: far from the Done that was tapped, often scrolled out of view, and it pushed the whole list down. A deleted task's Undo did the same in the Planner only. | **Fixed:** one `UndoBar` floats above the tabs on every tab (polite live region), for finished and deleted tasks; the page under it never moves. |
| Fitts's Law | The berry button on Home was 40 dp; the buddy's level chip was a thin strip. | **Fixed:** berry 48 dp; the chip has a 40 dp minimum height. |
| Von Restorff | Several screens had two or three solid colours side by side: Give up red next to Pause; Make buddy, To PC and Transfer all solid on the Pokémon page. | **Fixed:** one solid primary per group; Give up, To PC and Transfer are quiet, and Transfer turns red only in its confirm step ("Transfer" / "Keep Pikachu" instead of Yes / No). |
| Fitts's Law, Peak-End | Remove friend was a bare "X" right beside Gift, with no confirm. | **Fixed:** "Remove" asks once in place (Remove / Keep); "No" on a request is now "Decline". |
| Peak-End Rule | A hatch ended on "Have a look at your Pokémon" and a lone OK, with no way to look. | **Fixed:** "Meet <name>" opens the new Pokémon on the Pokémon tab. |
| Peak-End, docs/design.md No shame | Overdue tasks were labelled in danger red. | **Fixed:** the word "Overdue" in amber (`Pixel.Warning`). |
| Postel's Law, Doherty Threshold | Text fields ignored the keyboard's Done key; the email kept stray spaces; the password rule (6 characters) was never said, the buttons just stayed grey. | **Fixed:** Done submits quick add, steps, habits, goals, milestones, the trainer name and the onboarding name; email keyboard, trimmed email, labelled fields and "At least 6 characters" under the password. |
| Chunking, Law of Common Region, Miller's Law | Settings put eleven unrelated controls in one card. | **Fixed:** four titled cards: Focus, Sound and touch, Look, Reminders and week. |
| Chunking, Law of Proximity | An open task card ran "Move to", "At" and "Remind me" in body text with no separation from the controls, and Edit / Won't do / Delete straight after. | **Fixed:** small bold `FieldLabel`s name each group and a divider sets the actions apart. |
| Working Memory (recognition over recall) | A section's header showed only its own name. | **Fixed:** the subtitle says where it sits (Planner, Pokémon, Me), like a breadcrumb. |
| Goal-Gradient, Zeigarnik | A goal's bar had no words; the focus timer bar, friendship and candy bars had no spoken label. | **Fixed:** "2 of 5 milestones" beside the goal bar; every one of those bars now has a screen-reader label (and friendship shows its percent). |
| Law of Similarity, Paradox of the Active User | In the Bag, usable items looked exactly like items that only sit there. Tapping a task row opened its options with no hint. | **Fixed:** usable items get a Use button; task rows say "Show options" / "Hide options" to screen readers. |
| Aesthetic-Usability, Paradox of the Active User | Empty lists were a bare title and line. | **Fixed:** `EmptyState` takes a real item icon (and at most one action), in the shape the design-references skill asks for. |
| Accessibility (Law of Similarity's "not colour alone") | The starter picker and PC tiles were clickable boxes with no role. | **Fixed:** starters are radio buttons with a selected state; PC tiles say "Open <name>". |
| Hick's Law, Law of Common Region, Chunking | (0.19.3) The Planner menu needed scrolling to see all its lists, the quick-add showed every choice before anything was typed, and the lists were one long column of separate cards. | **Fixed:** six smaller tiles with counts fit on one screen, the quick-add's day chips wait until there is text, and each list is grouped under headings with counts, one panel per group. |
| Law of Similarity, Jakob's Law | (0.43.0) Plan's page was four cards while Care's opened on its buddy and party, so the two modules felt like different apps; Care's party sat in a ragged, left-aligned row, and only Care's party showed perks. | **Fixed:** both pages share one layout (buddy in its scene, the whole party in six equal slots with its perks, today, the sections) through `PartyPanel.kt`, and the Party screen shows a perk card for both parties. |
| Chunking, Law of Common Region, Serial Position | (0.50.0) The Pokémon detail mixed nature, ability and the IV judge beside the portrait, put friendship apart from the stats, and ran party, buddy, rename, star and transfer together in one box of buttons; at large text the name wrapped mid-word and stat names were cut off. | **Fixed:** titled boxes in the audit's order (identity, Traits, Growth, Evolution, Role, Origin), Rename under the name, Transfer alone at the end; stat labels grow instead of clipping, and the nature's stats are marked ▲ ▼, not by colour. |

## Already followed (kept)

- **Hick's Law, Choice Overload:** tabs are four labelled tabs (Home, Modules, Pokémon, Me; 0.33.0); sections open on `MenuGrid` cards of six or fewer; quick add hides difficulty behind "More options"; Home recommends one task (Next up) with "Pick another" instead of a list to choose from.
- **Tesler's Law:** quick add understands "Call mum tomorrow 5pm 30m", so the app carries the date parsing, not the person.
- **Goal-Gradient:** Home's region cards ("2 of 5 tasks done today"), the EXP bar to the next rank, quest bars with "x of y".
- **Doherty Threshold:** every action applies locally at once; sync runs behind it with a status chip.
- **Flow, Selective Attention:** a running focus session shows only the egg, the clock, the buddy's face and two buttons.
- **Parkinson's Law:** focus presets (15 / 25 / 45 / 60) and task estimates set the time box up front.
- **Mental Model:** the PC, party, Bag, Pokédex and Professor intro follow the games the audience knows.
- **Serial Position:** Home first and Me last in the tabs.
- **Doherty threshold, Fitts's Law (0.33.0):** Planner and Focus moved one level down, into Modules, Plan, so every module gets the same place. The Modules tab reopens the module last used, and Home's Next up, Add a task and Start focus, the widgets, the shortcuts and the reminders still go straight to them.
- **Cognitive Load, Occam's Razor:** one reaction at a time from the buddy, no meters, no speech bubbles.
- **Law of Prägnanz, Uniform Connectedness:** one card shape (`TextBox`), one button shape (`PixelButton`), one row shape (`ListRow`).

## Later (not in this change)

- **Hold to confirm** for Transfer and Erase this device (design-references): the two-step confirm in place is kept for now; a hold needs its own Reduce motion version.
- **Slider detents for the focus length** (design-references): the presets and ±5 work; a slider is a bigger redesign of the Focus card.
- **A countdown on Undo** (Doherty, feedback): the bar shows while the 10 seconds run but not how many are left; a draining line would need a still version for Reduce motion.
- **Side rail on wide windows** (Fitts's Law on desktop and web): the bottom tabs stay; desktop and web work is deprioritised (docs/design.md, Platforms).
- **Large text in the header** (Fitts, Chunking): at 1.5× the title shares the bar with "‹ Back" and the icon and wraps on long names; it still fits, but the header could cap Back's font scale like the tab bar does.
- **The open task card** still holds about twenty controls; a sheet with tabs (When, Steps, Edit) would cut it further, but it is a larger Planner change.

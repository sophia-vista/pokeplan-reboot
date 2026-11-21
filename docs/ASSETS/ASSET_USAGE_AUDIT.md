# Pokémon asset usage: open items

What is still open or limited in how the app uses Pokémon art and sound. Sprite gaps by species are in `PMD_SPRITE_GAPS.md`. The shipped inventory is in `docs/assets.md`.

## Open

- **Mixed art styles in one list.** The 13 Gen 5 species on Black/White sheets are fitted to the box (frames up to 153 x 117 px), not to the PMD scale, so they draw larger and in another style beside PMD neighbours; 116 forms have a PMD portrait but no PMD sprite. Needs a decision on a PMD-scale fit.
- **No sound has been checked by ear.** The Red Rescue Team and Explorers of Sky clips are approximate renders (`assets/pmd-sfx/README.md`); the story and emote clips have never been listened to. This is the largest open risk in the sound set and needs a person with a phone (`SKY_SOUND_LISTENING_SHEET.md`; REP-016, REP-017). Record the result in `docs/assets.md`.
- **One face for every event on the Normal-only portrait strips** (332 strips, 208 species: a third of Gen 8 and nearly half of Gen 9): a hatch and a shiny look the same as a plain line. Upstream limit.
- **Minior (774) and one other form** have no usable still pair for a recoloured shiny and stay regular.
- **No pose for a World recruit or a rank up** (they show portraits only), and no "streak saved" sigh (no such screen).
- **Idle faces the viewer** everywhere but the town (only the Down row of Idle, Sleep, Eat and Hop is kept).
- **Portrait faces not fetched:** Pain, Sigh, Dizzy, Stunned, Crying, Shouting (about 63% of forms each): Sigh for Focus "away", Dizzy for Deep Focus moved.
- **Animations not fetched:** `Hurt`, `Attack`, `Charge`, `Shoot` (96 to 99% of forms: Dojo training with the `hit` strip, expedition reports, `Charge` at a focus start); `Laying`, `EventSleep`, `Nod`, `Sit`, `LookUp`; idle side and up rows (100%). Anything fetched needs the Hop-style fallback and the same credit handling.
- **Cries:** Android plays at most three at once through `MediaPlayer` (not tried on a phone), each a fresh `prepareAsync` with a short delay (`StoryCues.CRY_LEAD_MILLIS`, 140 ms, is not exact); no sound on desktop; the web player shifts pitch and speed together; cries are per species (regional forms and Megas use the base cry).
- **Sprites:** frame timing is one coroutine per sprite, so sprites drift apart and cannot be synchronised; 19 PMD idles are a single frame (the Home stage adds a slow drift, other screens show a still).
- **Sounds never played:** `DRAWER_OPEN`, `DRAWER_CLOSE`, `URGENT_SHOCK`, `GREAT_SHOCK`, `QUICK_ESCAPE`, the story clips without a script, `REMINDER` and `TIMER_DONE` (`reminder.ogg` is byte-identical to `res/raw/pokelife_notify.ogg`; `timer-done.ogg` has no notification channel). Wire or drop them.
- **`StatusKind.SKULL`** is cut and never shown; it could leave the build.
- **A sprite viewer on the detail page** (tap the portrait to step through its faces, the sprite through its animations) is an idea, not built.

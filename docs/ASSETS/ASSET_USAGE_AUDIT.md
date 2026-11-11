# Pokémon asset usage audit (11 November 2026, app 0.167.1)

What the app ships for Pokémon art and sound, whether the files and tables agree, how the code uses them, where
that use is inconsistent or limited, and what the assets already in reach could add. Sprite gaps by species are in
`PMD_SPRITE_GAPS.md` and are not repeated here.

**How it was checked.** Every row of every sheet table was compared with its PNG (frame count × frame size against the
image header, frame-time count, shiny file present when the row says so, files without a row). Every `Sfx`, `Emotion`,
`SpriteAction`, `StatusKind` and `Emote` value was counted in the non-test Kotlin sources. SpriteCollab's `tracker.json`
at the pinned commit (`cf6c95f`) was read to see which animations and faces exist upstream. **Not checked:** nothing
was listened to (no audio in the sandbox) and no screen was rendered; sound findings are about wiring, not how a clip
sounds.

## 1. Verified inventory

| Asset | Files | Coverage | Integrity |
|---|---|---|---|
| Still sprites (`assets/sprites`) | 1,352 forms, 2.9 MB | all 1,025 species; shiny for 1,342 | clean |
| PMD idle (`assets/pmd`) | 1,098 forms | 983 of 1,025 species, 115 of 327 other forms; shiny for 935 | clean: 0 bad sizes, 0 missing, 0 stray files |
| PMD sleep / hop / walk ×3 | 1,087 / 1,081 / 1,089 | 11, 17 and 9 PMD forms lack one | clean |
| PMD eat | 242 | **22% of PMD forms** (SpriteCollab has Eat for about a quarter) | clean |
| PMD portraits | 1,211 strips, 6.2 MB | all 1,025 species; 773 have all six faces, **332 have Normal only** (208 species: 55 of Gen 9, 46 of Gen 5); shiny for 973 | clean; every strip has Normal first; 3 rows have no artist |
| Black/White animated (`assets/animated`) | 649 species, 12.8 MB | Gen 1 to 5 | clean |
| Cries | 1,025 Ogg, 14.2 MB | every species; none per form | all present |
| Sound effects (`assets/sfx`) | 67 Ogg, 0.9 MB | every `Sfx` value has its file, every file its value | clean |
| Status icons (`assets/status`) | 8 strips | alert, question, cross, sleep, skull, zap, sweat, twinkle | n/a |
| Effect strips (`assets/world/fx`) | 4 | hit, welcome, steam, sparkle | n/a |
| Credits | `credits.tsv` 1,098 rows | every PMD sprite has a non-empty credit | clean |

All PMD assets total 26.6 MB (the three walk rows are about 9 MB of that).

By generation, PMD idle sprites: Gen 1 to 4 complete; Gen 5 143/156; Gen 6 71/72; Gen 7 84/88; Gen 8 91/96; Gen 9 101/120.
Of the 42 species without one, 13 (all Gen 5) show their Black/White animation and 29 a still sprite.

## 2. How the code uses them

- **Sprites.** `Sprite` and `AnimatedSprite` try PMD, then Black/White, then the still. PMD sprites share one pixel scale
  (a 32 px reference frame), so sizes relate as in the games. 36 `AnimatedSprite` call sites in 16 files.
- **Actions.** `SLEEP` (focus, Care, Planner, Town), `HOP` (taps, party, visitors), `EAT` (buddy berry, visitor treat, Town
  grazing), `WALK_*` (Town, story, World art). A one-off hands back to idle; Reduce motion shows the first frame.
- **Portraits.** Six faces. Used by the Home buddy event, Focus's `SpeakerBox`, the story player, Town meetings, World
  recruits and the detail page (Normal only). A missing face falls back to Normal.
- **Emotes.** Three separate vocabularies: `StatusKind` (real PMD icons; Town, Guardian, the sleep Zz), `Emote` (item icons
  and Pixelarticons glyphs; Care's visitor play only), and Unicode emoji text (`BuddyEvent.emoji`, `BuddyReactions`;
  the Home buddy).
- **Cries.** 37 call sites. `CryVoice` (individual detune, mood, distance, condition) is used by the story player only;
  every other cry plays as recorded.
- **Sound effects.** 47 of 68 `Sfx` values are referenced in code and 5 more by story scripts (`sound("STORY_…")`).

## 3. Findings

### Inconsistencies (ordered by how visible they are)

1. **Story faces contradict story sounds.** `Expression` has `SAD`, `ANGRY`, `TEARY` and `WORRIED`, the scripts use them
   on about 225 lines (ANGRY 62, SAD 50, TEARY 3, WORRIED 110), and `StoryCues` plays the sad, mad and confused clips and
   bends the cry for them. But only six faces are fetched, so `emotionOf` (`StoryPlayer.kt:114`) shows **Normal** for
   sad, angry and teary lines and **Surprised** for worried ones. SpriteCollab has Sad, Angry, Teary-Eyed and Worried
   for the same share of forms as Joyous (each about 64% of its portrait entries).
2. **A shiny can show regular colours.** 163 PMD forms (49 species) have no shiny sheet. `Animation.sheetName` then draws
   the regular idle, while actions and portraits refuse to ("the regular colours would be a different-looking
   Pokémon"). `Portrait` falls back to `Sprite`, which draws that same regular PMD frame, so its stated intent is not
   met either. The still shiny sprite exists for all but 10 forms and is never reached once a PMD sheet exists.
3. **Three emote styles.** The Home buddy answers with font emoji (the phone's emoji font, not pixel art), Care's
   visitors with item icons, the Town with the real PMD icons. The art rule asks for real assets for icons;
   `Emote.kt`'s own comment says it replaced emoji, but the Home buddy still uses them (CLAUDE.md documents that as
   current behaviour).
4. **Feeding mostly does not show eating.** 78% of PMD forms have no Eat row. `BuddyStage` then plays a code-drawn
   offset tween instead of the Pokémon's own Hop row, which 1,081 forms have.
5. **Mixed art styles in one list.** The 13 Gen 5 species on Black/White sheets are fitted to the box (frames up to
   153 × 117 px), not to the PMD scale, so they draw much larger and in another style beside PMD neighbours. 116 forms
   have a PMD portrait but no PMD sprite, so a PMD face sits by a mainline still.
6. **Idle always faces the viewer.** Only the Down row of Idle, Sleep, Eat and Hop is kept, so a Town Pokémon that walked
   up or sideways turns to the front when it stops.
7. **One face for every event** on the 332 Normal-only strips (a third of Gen 8 and nearly half of Gen 9): a hatch and a
   shiny look the same as a plain line. Upstream limit, not a bug.

### Unused or stale

- **12.5 MB of Black/White sheets never drawn.** PMD overrides `animated` for every species that has both; only the 13
  species above (0.28 MB) are reachable. The rest of `assets/animated` is in the APK for nothing.
- **16 sounds never played:** `POKEMON_CENTER` (its comment says it plays at an evolution's "Congratulations!"),
  `DRAWER_OPEN`, `DRAWER_CLOSE`, five emote clips (`SHOCK`, `URGENT_SHOCK`, `GREAT_SHOCK`, `QUICK_ESCAPE`,
  `CONFUSION_STATUS`), six story clips (`STORY_ELECTRICITY_LONG`, `STORY_HOT_WATER`, `STORY_VISION`, `STORY_STONESHIP`,
  `STORY_TEAM_CHARM`, `STORY_BEACH_DUSK`), and `REMINDER` and `TIMER_DONE`. `reminder.ogg` is byte-identical to
  `res/raw/pokelife_notify.ogg`, which is the copy notifications use; `timer-done.ogg` has no notification channel.
- **`StatusKind.SKULL`** is cut and never shown (reasonable for a gentle app; it could leave the build).
- **Three portrait rows without an artist** in `portraits.tsv`: the licence asks for credit, so check them upstream.

### Technical limits

- **Cries cut each other off.** Android plays one cry at a time through one `MediaPlayer` (`AndroidCries.kt`): when two
  Town Pokémon meet, the second cry stops the first. Each cry is a fresh `prepareAsync`, so there is a short delay, and
  `StoryCues.CRY_LEAD_MILLIS` (140 ms) is not exact on a phone.
- **No sound on desktop**; the web player shifts pitch and speed together (one rate), so a voiced cry differs by
  platform.
- **Effects.** `SoundPool` with 6 streams; a clip's first play waits for its load.
- **No sound has been checked by ear.** The Red Rescue Team and Explorers of Sky clips are approximate renders
  (`assets/pmd-sfx/README.md`); the story clips and emote clips have never been listened to. This is the largest open
  risk in the sound set and needs a person with a phone.
- **Cries are per species.** Regional forms and Megas use the base cry.
- **Frame timing** is one coroutine per sprite with `delay`; sprites drift apart and cannot be synchronised (fine for
  idles, a limit for any choreographed scene).
- **19 PMD idles are a single frame**; the Home stage covers that with a slow drift, other screens show a still.

## 4. Opportunities with assets already in reach

SpriteCollab at the pinned commit, as a share of its sprite or portrait entries. Anything fetched needs the same
fallback pattern as Eat and the same credit handling.

| Add | Upstream coverage | Use | Mainline or PMD precedent |
|---|---|---|---|
| Faces: Sad, Angry, Worried, Teary-Eyed | ~64% each (same as Joyous) | fixes finding 1; about 1 MB per face | PMD dialogue portraits |
| Faces: Pain, Sigh, Dizzy, Stunned, Crying, Shouting | ~63% each | Focus "away" (Sigh, not Surprised), a streak kept by a save (Sigh of relief), Deep Focus moved (Dizzy) | PMD |
| `Rotate` | 100% | a spin for a level up or an evolution; a turntable on the Dex and detail pages | the Pokédex model viewer, Amie |
| `Hurt`, `Attack`, `Charge`, `Shoot` | 96 to 99% | Dojo training (with the existing `hit` strip), expedition reports, `Charge` when a focus session starts | battle animations |
| `Pose` | 24% | a hatch, a rank up, a recruit | the PMD recruit and rank-up pose |
| `Wake`, `EventSleep`, `Laying` | ~21% | focus ends: the buddy wakes instead of cutting from Sleep to Idle | PMD mornings at the guild |
| `DeepBreath`, `Nod`, `Sit`, `LookUp` | ~21% | Heal's breathing and calm breaks (`DeepBreath`), a task ticked (`Nod`), resting scenes | camp and picnic idles |
| Idle side and up rows | 100% | fixes finding 6 | following Pokémon in HeartGold/SoulSilver |

Without fetching anything:

- **Play the Hop row when Eat is missing** (finding 4).
- **One emote vocabulary:** `StatusKind` over the Home buddy and Care's visitors too, with `BuddyEvent` and
  `BuddyReactions` choosing a kind instead of an emoji. `assets/status/sheet.png` and `sheet2.png` are the source sheets
  (not inspected in this audit for further icons such as a heart or a note).
- **Pair the unused emote clips with their icons:** `SHOCK` with `ZAP`, `CONFUSION_STATUS` with `QUESTION`, as the games do.
- **Give owned Pokémon their voice:** `CryVoice.individual(pokemon.id)` on every tap and pet, so two of a species differ,
  and a quieter `Distance.FAR` cry for Town chirps. The code exists and only the story calls it.
- **A sprite viewer on the detail page:** tap the portrait to step through its faces, tap the sprite to step through
  its animations. It shows what is already shipped and sits naturally by the artist credits.
- **The evolution sequence** has `EVOLUTION` but its closing jingle (`POKEMON_CENTER`) is not wired; either play it or
  remove it and fix the comment.
- **Shiny fallback:** for a still, use the still shiny sprite when the PMD form has no shiny sheet; for an animated one,
  keep the regular sheet only with the glints drawn (finding 2). This is an owner's decision between style and colour.
- **Trim the APK:** bundle only the Black/White sheets whose id is not in `pmd.tsv` (about 12.5 MB saved), and drop
  `reminder.ogg`, `timer-done.ogg` and the skull strip if they stay unused.

## 5. Suggested order

1. Fetch the four missing story faces and map them in `emotionOf` (finding 1): the largest visible gain for about 4 MB.
2. Hop-for-Eat fallback, the unused-asset trim and the stale `POKEMON_CENTER` comment: small, no new assets.
3. Decide the shiny fallback and the single emote vocabulary (both change what the owner sees on Home).
4. Listen to the sound set on a phone and record the result in `docs/assets.md`.
5. New animations (`Rotate`, `Wake`, `DeepBreath`, `Pose`) as the screens that want them are built.

## 6. Status after 0.169.0

| Finding or task | State |
|---|---|
| Story faces (finding 1), more faces for Focus | Done: 14 faces; story, Focus away and failed use them |
| Shiny shows regular colours (2) | Done by recolouring from the real stills (`derive_shiny.py`); Minior (774) and one other form have no usable still pair and stay regular |
| Three emote styles (3) | Home, module stages and the buddy widget use `ReactionIcon`; Care's visitor play keeps its item icons and glyphs (`Emote`) |
| Eat missing (4) | Done: Eat, Wake, Pose and Rotate fall back to Hop |
| Mixed styles (5) | Open: the 13 Black/White species still draw larger; it needs a decision on a PMD-scale fit |
| Idle faces the viewer (6) | Done in the town; other screens stay front-facing |
| Unused assets | Black/White sheets, five sounds and the skull left the bundle; the story clips without a script stay |
| Cries cut each other off | Up to three overlap on Android (not tried on a phone) |
| Voices | Pets, taps and the Pokémon page use `CryVoice.individual` |
| Emote clips | Town meetings play CURIOUS, SURPRISE, MAD, SHOCK and CONFUSION_STATUS beside the icons, only when the buddy is in the meeting |
| New animations | Wake, Pose, Rotate, DeepBreath wired (focus end, hatch, evolution and level, calm break). **Not done:** a pose for a World recruit or a rank up (they show portraits only) and a "streak saved" sigh (no such screen) |
| Credits | Three blank portrait credits name the contributor id |
| Listening to the sound set | Open: needs a person with a phone |

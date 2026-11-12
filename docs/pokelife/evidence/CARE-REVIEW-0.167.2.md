# CARE review at 0.167.2

**Date:** 11 November 2026. **Method:** a read of the spec (`MODULES/CARE.md` 1.4) against the code: `care-core/`, `CareStore.kt`, `CareReminders.kt`, `screens/Care.kt`, `CareMeadow.kt`, `VisitorPlay.kt`, and the reward side in `GameStore.care` and `RewardEngine.care`. **Not done:** no build, no screenshots, no device pass; every finding below is from the code, with its line. It builds on `DESIGN-REVIEW-0.165.1.md` (CARE section) and does not repeat it: one-tap mood, journal editing, the 14-card limit, undo on deletes, one breathing pace, the lock, the eight menu cards and the reward copy in a calm break are already recorded there, and some are being built on `claude/pokelife-design-review-jllhji`.

**One reading note.** "Needs tracking" for Pokémon is ruled out by the spec (5.1: no hunger, fatigue, boredom, needs, neglect or decay; 4.1: no Pokémon requires daily attention). The code keeps to that, and this review recommends keeping it. What CARE tracks is the person's routines and check-ins, and, for Pokémon, only what was learned about them and how warm a visitor is.

## 1. What works

- The rules are gentle all the way down: no streaks, no decay, a visitor's warmth is never lost, the queue never expires, dismissal can be undone, repeats are free and pay nothing.
- Privacy holds. `CareEvent` carries a kind and a time; a diary card, a note and a journal entry never reach `World`.
- The visit scene (`VisitorPlay.kt`) is the best interaction in the module: a pat lands where you tap, a treat is dragged, sitting is a held press, tidying is a swipe, each with a screen-reader action and real SpriteCollab hop and eat animations with a code fallback.
- Befriending ends in the owner's cutscene (`Cutscenes.kt`: the visitor leaves an egg, it hatches).
- The world caps every kind a day by its own counter (`GameStore.CARE_DAILY_CAP`), so Care's own caps cannot be farmed by deleting a paid routine and adding another.

**Fixed in 0.172.1:** B2, B3, B5, B7, B8, B10, and B4 in part (a tap reads the whole entry; Show older reaches every entry and card; editing an entry is not built). Left: B1, B6, B9 (REP-030).

## 2. Bugs (each confirmed by reading the code)

| # | What happens | Where | Fix |
|---|---|---|---|
| B1 | **A visitor cannot be played with from the Meadow.** Long press a visitor, "Spend time": Care Play opens with the visitor's id, finds no owned Pokémon with it and shows "No one to spend time with yet. Hatch an egg first" with a key to Focus. | `CareMeadow.kt:141`, `:194` | Offer "Visit" for a visitor (see 4.1), "Spend time" only for owned Pokémon. |
| B2 | **The sweet berry is never offered once the bag holds all five flavours.** The treat row shows the first four berries by id; "berry-sweet" sorts last. A visitor whose liked berry is sweet can never get its bonus (4 of the 16 warmth). | `VisitorPlay.kt:363` | Show all five (there are only five). |
| B3 | **The Meadow's two corner labels are unreadable in the dark palette:** `Pixel.Ink` (near white in dark) on a 75% white chip. | `CareMeadow.kt:112`, `:119` | Use the scrim chip the visit scene already uses (`VisitorPlay.kt`, "n of 16"). |
| B4 | **A long journal entry cannot be read again.** Rows cut the text at 8 lines and do not open. Only the newest 30 entries are listed (older ones only through search), and diary cards past the newest 14 cannot be reached at all. | `Care.kt:753`, `:747`, `:631` | A row opens the entry (read, edit, delete); list by month with "Earlier". |
| B5 | **Urge ratings are write-only.** They are saved on the card and never shown again: not in "Earlier cards", not in Trends. | `Care.kt:634-639`, `TrendsView` | Show them in the card's history row and as a Trends line. |
| B6 | **Care Play's invite list is wrong and capped.** Its "visitors" are owned Pokémon that share a species with a visitor; the list is the 24 with the highest friendship, with no way to reach anyone else. | `CareMeadow.kt:219-220` | Drop the visitor mapping; use the shared Pokémon picker. |
| B7 | "Surprise me" gives the same activity for a whole minute (the seed is the minute). | `CareStore.kt:326` | Seed with the number of times played. |
| B8 | **A calm break is lost when the screen is left.** The start time is screen state, so Back, another tab or a recomposed page drops it with no record. `finish()` also runs inside composition. | `Care.kt:459`, `:481` | Keep the running break in `AppModel` (as the journal draft is) and finish it in an effect. |
| B9 | **Reading makes arrivals.** `visitors()` and `reunion()` write on a read. A reunion is rolled only when the Meadow is opened, so the landing page never knows a friend is back. | `CareStore.kt:176`, `:299` | One `arrive()` call when Care opens; the getters only read. |
| B10 | Wrong or stale words: routines "earn friendship and berries" (they pay 3 Rare Candies and no berry); the Egg meter line leaves out Care Play; `GameStore.befriend` says "once a day" (the cap is 4); `CareRules.paidOn` is dead (the old "befriending needs a paid action" rule); `economy.md` still says 17 points and 5 Care Eggs a day (see 5). | `Care.kt:384`, `:810`, `GameStore.kt:528`, `Care.kt (care-core):173` | Text and cleanup. |

## 3. Confusing interactions

1. **Four places to meet a Pokémon, four vocabularies.** The landing stage (pet, berry), the Visitors card ("Visit" and "Play"), the Meadow (tap is a cry, long press a panel) and the Play page (press one key). "Play" on a visitor's row is free play that pays nothing; the "Play" menu card is Care Play, which pays; the panel calls the same thing "Spend time".
2. **The Meadow is not where visits happen.** The scene shows the visitors and their warmth but has no way to visit one. Visiting lives in a card at the bottom of the landing page, under the stage, the reward box, the tutorial, today's routines, the Egg meter and eight menu cards.
3. **What you learn is never shown.** A visitor has a favourite kind of care, a liked pat spot and a liked berry, and the copy says "It will remember that", but nothing records them for the player. Worse, the Meadow panel reads Care Play's separate preference table (`CarePlay.preferences(visitor.id)`, not `Visitor.favorite`), which a visitor can never fill (B1), so it says "You have not found out what it likes yet" for ever, even after "Eevee loved that".
4. **Sending a visitor away is hidden.** The key shows only while the free-play scene is open, and "Play" replaces "Visit" only while the visitor rests. A new visitor must be visited and left before it can be dismissed (`Care.kt:265`).
5. **A visit that ends without a friend closes the scene in the same frame** (the visit is cleared and nothing else keeps the scene open), so the last hop and emote are never seen; only the line stays.
6. **Care Play is one key pressed 4 to 10 times.** All six activities are the same mechanic with a different label, with no reaction from the sprite until the end, and the pay-off line is "A little something came of it". The buddy, the "primary companion" (4.1), gets the thinnest interaction in the module while a stranger gets the richest.
7. **A routine's time is set in steps of 15 minutes from 09:00** (`Care.kt:397`): 21:30 is fifty taps. There is no shared time picker in the theme yet.
8. **"Stop tracking" sits under every urge on every card** (`Care.kt:591`), a settings action in the middle of a daily form, with no confirm.
9. **Resting has no end shown:** "resting, ready for a visit later". The rule is three hours; "ready about 15:00" costs nothing.
10. **"Leave the visit" is a bare tick**, which reads as "confirm".

## 4. What is missing

Against the spec, in the order a player would feel it:

- **Routines (3.1):** no skip; no "2 of 5 today" progress; no icon although `Routine.icon` exists and is stored (the add form never sets it); no starter suggestions (the empty state names water, a stretch, a walk, but they are not keys); yesterday cannot be ticked; no per-routine history.
- **Calm (3.2):** only breathing at one pace, for one or two minutes. No grounding tool (5-4-3-2-1), no haptic or sound cue for in and out, so it cannot be done with eyes closed.
- **Reflect (3.3):** Diary and Journal are two menu cards, not one destination; a past card cannot be opened or corrected; the journal has no open, edit or tag filter, and tags are a fixed six; no optional diary reminder (the spec allows one).
- **Meadow (5.2):** a tap gives a cry and a buzz and nothing to see (no hop, no emote), and the cry plays on every tap after the 0.9 s cooldown, which 7 asks not to do. With sound off a tap does nothing visible.
- **Care Play (4.4):** the "30 to 90 seconds of simple touch, matching, observation" standard is not met (see 3.6).
- **Visitors (6.2):** arrivals lean to a type by what was done in Care that week (`Visitors.lean`). Nothing tells the player, so it is a rule nobody can enjoy; show it ("calm breaks draw Water and Fairy visitors") or drop it.

### 4.1 Opportunities, without new rules or new art

These reuse what exists and keep the PMD presentation (dialogue boxes, SpriteCollab sprites and portraits, the scene window).

1. **Make the Meadow the one place for Pokémon.** Tap: a hop and an emote (the stage guests already do this, `StageGuestSprite`). Long press: the panel, with **Visit** for a visitor and **Spend time** for the buddy. Then remove the Visitors card and the Play menu card from the landing page; a single line there ("2 visitors, 1 ready to be a friend") opens the Meadow. This fixes B1, 3.1, 3.2 and 3.4 together and brings the landing page to the spec's four areas.
2. **One interaction scene.** Run Care Play through the visit scene's gestures (pat where you tap, drag a treat, hold to sit, swipe to groom). Both already share the `CareAction` vocabulary. Care Play then meets 4.4 with no new mechanic, and the six-keys page goes away.
3. **A visitor's notebook in the panel:** its favourite, pat spot and berry, each shown once found, as item and emote icons. Three stored flags; discovery starts to matter.
4. **Reactions in a speaker box with the portrait's expression** (Happy for liked, Joyous for loved, Normal otherwise, with the usual fallback where SpriteCollab has none). The line stays the app's ("Eevee loved that"), so the "never chats" rule holds, and it is the most PMD thing the module could do.
5. **Say what Care Play paid** with the reward icons the reward box already draws, in place of "A little something came of it".
6. **Routine starters as keys** in the empty state (a glass of water, a stretch, a walk, wind down), each with its real item icon, so the first routine is one tap.
7. **A returning friend stands on the landing stage** as a guest, so a reunion is found without opening the Meadow.

Do not add: needs or mood meters for Pokémon, streaks, daily visit quotas, timers on visitors, or anything a missed day could cost. The spec is right that these would turn care into chores.

## 5. Progression and pacing (owner's call)

- **Care's Egg meter is 3 points.** With Care Play (rules version 16) a full day is 5 routines (5), 3 calm breaks (6), 3 skills (6) and 6 plays (6): 23 points, **7 Care Eggs a day**, plus up to 4 friends. `economy.md` still records 17 points and 5 eggs.
- **Care Play is the cheapest egg in the app:** about forty key presses across six Pokémon is 6 points, two eggs, in under a minute. If Care Play becomes the richer scene of 4.1, this is fine; as it stands it invites a daily tap-through. Options: Care Play fills no Egg meter (friendship and a candy only), or the meter grows to 5 like Plan's. Either changes a payout: bump `RewardRules.VERSION` and update `economy.md`.
- **Befriending** is well paced (about two visits three hours apart, set by arrivals at about 1.35 a day), and the first visit can never finish it (at most 15 of 16), which is a good, quiet design.
- **Feedback on progress is thin where it matters most:** a routine tick gives a sound and "Done today", and the reward box appears only on pages that include it (not on Play or the Meadow), so an egg hatched by Care Play is first seen on the way back out.

## 6. Suggested order

1. The bugs that lose or hide the player's own words and choices: B4, B5, B8, B2, B3.
2. The Meadow as the hub (4.1 items 1 to 3), which removes B1, B6 and most of section 3.
3. Care Play through the visit scene (4.1 item 2), then decide the Egg meter (5).
4. Routines: starters, icon, skip, a real time picker (shared, since Plan and Heal need one too).
5. Calm: a grounding tool and a cue for eyes closed. Reflect as one destination.

Nothing here needs a schema change except the visitor's notebook (three flags on `Visitor`, one `.sqm`) and a skipped routine day.

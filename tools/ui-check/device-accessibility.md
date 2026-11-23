# The phone pass for the UI quality gates

The JVM gates in `composeApp/src/jvmTest` prove what a semantics tree and a rendered PNG can prove: a control has a
label, an icon key is at least 48 dp, a screen renders at normal and 1.3× text, and a screen matches its reviewed
baseline. They cannot prove TalkBack's spoken order, the phone's real font-scale setting, a real large screen or real
reduced-motion behaviour. This file is the record for that half.

Run it on the primary target (Samsung Galaxy S26 Ultra, `TEST_STRATEGY.md` 2.2) with a release-equivalent or debug APK
built from the commit under review. Record model, Android build, app version, commit and date at the top.

## What the JVM gates already cover

| Gate | Where | What it proves |
|---|---|---|
| Golden screenshots | `ScreenshotCompare` + `ScreenshotTest` | every compared screen matches its reviewed PNG (tolerance 8/255 a channel, 0.2% of pixels) |
| Baseline refresh | `UPDATE_SCREENSHOTS=1 ./gradlew :composeApp:jvmTest --tests "app.pokeplan.ui.ScreenshotTest"` | new/updated baselines, which must be reviewed as PNGs in the commit, never auto-accepted |
| Semantics | `SemanticsTest` | every interactive node on every main screen has a label, at normal and 1.3× text |
| Touch targets | `SemanticsTest` | icon-only controls are at least 48 dp |
| Navigation/back | `NavigationTest`, `BackChainTest` | every place is reachable and Back returns where it came from |
| Contrast | `TokenContrastTest` | the palette pairs meet their contrast ratio |

## What only the phone can prove

Use `tools/ui-check/device-accessibility.sh` for device facts (`font`, `talkback`, `shot`), then walk the checklist it
prints. Record one row per screen and state changed in this session, not the whole app every time:

| Check | Expected |
|---|---|
| TalkBack traversal of the changed screens | first focus is the title/lead; every key announces its action; rows announce label and state; a dialog contains and restores focus; nothing is announced as an unnamed button |
| 1.3× and 1.5× system font | no clipped label, no control pushed off screen, buttons keep their target size, long lists still scroll, dialogs scroll |
| Light and dark palette | no clipped or invisible text; no information carried by colour alone |
| Reduce motion | World/launcher animation stops or shortens; no essential information was only in the motion |
| Larger screen / display size | the pixel layouts scale without stretching sprites or overlapping rows |
| Reduced palette / larger display | the same two checks once |

A check that was not run is recorded as **Not run**, never as a pass. A screenshot of each state the change adds goes
with the record (`tools/ui-check/device-accessibility.sh shot <name>` writes to `build/ui-check/`).

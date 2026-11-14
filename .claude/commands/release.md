---
description: Gather the PRs merged since the last release tag into one release PR (version, changelog, gate, screenshots), then stop for the owner's approval
argument-hint: "[--dry-run]"
---

Release what `main` has merged since the last `vX.Y.Z` tag. Arguments: `$ARGUMENTS`.

**Dry run** (`--dry-run` in the arguments): change nothing. No file edits, no branch, no commit, no push, no PR, no label. Do
steps 1 to 4 read-only, say which commands steps 5 and 6 would run, and print the report (the end of this file) with the
changelog entry as it would be written.

How releases already work here (CLAUDE.md, Versioning): `VERSION` is the only version source (Gradle derives the Android
`versionName` and `AppVersion`). `release-apk.yml` publishes every merge to `main` and creates the `vX.Y.Z` tag and release
the first time a `VERSION` is seen. Most PRs bump `VERSION` and write their own changelog entry, so this command is for the
merges that did not (docs, content, small fixes gathered up), or for a version the workflow never published. **Never tag by
hand, and never merge the release PR:** the owner approves it, overriding the standing merge rule for this command only.

## 1. What is unreleased

1. `git fetch origin main '+refs/tags/v*:refs/tags/v*'` (not `--tags`: the rolling `latest` tag moves on every merge and makes
   that fetch fail). The last release is `git tag --list 'v*' --sort=-v:refname | head -1`.
2. The merges after it: `git log <tag>..origin/main --first-parent --merges --format='%h %s'` (`Merge pull request #N`).
   Read each PR with `mcp__github__pull_request_read` (`get` for the title, body and labels; `get_files` for the paths).
3. Compare `VERSION` on `origin/main` with the tag:
   - **Higher, with a `## <VERSION>` changelog entry:** that version merged but was never published (Actions down, or a
     docs-only merge the workflow skips). Do not bump again. Report it and say a merge that touches code publishes it.
   - **The same:** the PRs found are the unreleased ones. Go on.
4. No PRs: report "Nothing to release since <tag>" and stop.

## 2. Classify and pick the version

Read each PR's title type (`<type>(<area>): ...`), body and files:

| Class | When |
|---|---|
| breaking | `!` after the type, or "BREAKING" in the body, or it removes a feature, or it changes stored data with no migration back |
| feature | `feat`, or `content`/`design` that adds something a player sees |
| fix | `fix`, or `content`/`design` that corrects something |
| none | `chore`, `docs`, `.claude/` or `legacy-android/` only |

The bump comes from the highest class. Below 1.0 (semver section 4 and CHANGELOG.md): breaking or feature bumps the minor,
fix bumps the patch, and none means stop with "nothing user-facing to release". Going to 1.0 is the owner's call, never this
command's. Then run `.claude/hooks/in-flight.sh` and take the next number no in-flight branch already uses.

## 3. The version

Write the new number to `VERSION` only. Leave the Gradle files alone, and leave the
`*(X.Y.Z)*` marks in `docs/design.md`, which belong to the PR that built the feature.

## 4. The changelog entry

A `## X.Y.Z` section above the newest entry in `CHANGELOG.md`, in the house style (read only the newest entry):

- One opening line: what the release is about, whether reward numbers changed (`RewardRules.VERSION`), and whether stored
  data needs a migration.
- One bullet per change a player would notice, the lead words in bold, in plain words ("The PC shows..."), not commit or
  class names. End each with its PR (`(#316)`). Group by module when there are several. Leave out chores and docs.
- Screenshots: link only images a PR really carries (`![...](...)` or `user-images.githubusercontent.com` links in its body),
  as `([screenshot](url))`. `ScreenshotTest` PNGs are build output, not committed, so never invent a link to one.

## 5. Gate and visual check

1. `.claude/hooks/wait-for-setup.sh`, then `./gradlew jvmTest compileKotlinWasmJs --console=plain` in the background (it
   skips the screenshots).
2. Visual check, only for the screens the PRs touched: for each changed file under `composeApp/src/commonMain/kotlin/app/pokeplan/ui/`
   (`screens/`, `launcher/`, ...), find its `ScreenshotTest` methods (grep the screen's name) and run them by name. A change in
   `theme/` reaches every screen: then render them all with `-Pscreenshots`. The run by name is
   `./gradlew :composeApp:jvmTest --tests "*ScreenshotTest.<method>*"`. Open each PNG once, in both palettes where a dark
   shot exists, and check for clipped or overlapping text and a wrong palette.
3. Anything red: **stop**. No commit, no PR. Report the failing task, test or screenshot, with the trimmed output
   (`grep -E "FAILED|e: |BUILD"`), and the PR it most likely comes from.

## 6. The release PR

1. Work on the session's own branch (a cloud session's `ccr-…` or `claude/…`, restarted from `origin/main` if its PR merged);
   by hand, `chore/repo-release-<x>-<y>-<z>` from `origin/main`.
2. One commit, `chore(repo): Release X.Y.Z`, with the commit trailers the session gives. Push.
3. `mcp__github__create_pull_request`, title `chore(repo): Release X.Y.Z`. Body: the PRs with their class, why this bump,
   the gate result, the screens checked, and anything left out.
4. CI runs only on a labelled PR (owner's Actions rule), and a `VERSION` and `CHANGELOG.md` change does not need the runner,
   since the code was gated on its own PRs. Add `ci` only if a PR in the release changed code that never ran CI. If a check
   runs, `subscribe_pr_activity` and end the turn; report when it finishes.
5. **Stop.** Do not merge. Say: merging publishes `vX.Y.Z` (the tag and the APK) through `release-apk.yml`.

## Report

```
Release <X.Y.Z> (was <tag>)   [DRY RUN: nothing changed]
PRs since <tag>:
  #N  <title>  -> <feature|fix|breaking|none>
Bump: <minor|patch|none>, because <one line>
Gate: <passed | failed: ... | not run (dry run)>
Screens checked: <list, or none touched>
PR: <url, or "would open: chore(repo): Release X.Y.Z">
Next: <approve and merge to publish vX.Y.Z | fix ... | nothing to do>

Changelog entry:
<the entry>
```

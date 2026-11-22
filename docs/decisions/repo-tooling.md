# Decision records: repo-tooling

Moved here from code comments in the comment audit (21 November 2026). Each entry is a decision, rule or constraint that
the code alone does not show. Entries are grouped by source file; a line saying "Documented in" means the owning doc
already holds the rule and the entry only points to it.

## `.claude/hooks/claim.sh`

### claim.sh

Documented in `CLAUDE.md Parallel sessions`.

### claim.sh --release

Documented in `CLAUDE.md Parallel sessions`.

### claim.sh clash rule

`claim.sh` looks for every word of four or more letters of the claim (the backlog id counts too) in the other unmerged branches. Two hits of different words, or a hit on the id, is a clash: exit 2 and nothing is claimed, unless FORCE=1.

## `.claude/hooks/guard-read.sh`

### guard-read.sh

`guard-read.sh` is a PreToolUse hook for Read that blocks reading a text file over 32 KB in one go (no offset, limit or pages), so a stray read of a big doc cannot put 20K+ tokens into the context. It exits 2 to block and fails open: no python3, bad input, a missing file, an image or a PDF all let the read through. Passing offset or limit overrides it for one file.

## `.claude/hooks/in-flight.sh`

### in-flight.sh

Documented in `CLAUDE.md Parallel sessions`.

## `.claude/hooks/session-start.sh`

### session-start.sh

Documented in `CLAUDE.md Commands (rewrite, from the repo root)`.

### session-start.sh background run

Documented in `CLAUDE.md Commands (rewrite, from the repo root)`.

### Android SDK level in session-start.sh

The session start hook installs `platforms;android-37.0` and `build-tools;37.0.0` because `androidApp/build.gradle.kts` sets `compileSdk = 37`. Raise the hook's SDK level together with `compileSdk`, or cloud builds fail for want of the platform.

### Maven mirror init script

Maven Central rate-limits the shared cloud proxy, so the session start hook writes a Gradle init script (`~/.gradle/init.d/mirror.gradle.kts`) that tries Google's mirror of Maven Central first. It applies only where repositories are already declared, so the settings repositories (Google's Maven among them) stay in use.

## `.claude/hooks/wait-for-setup.sh`

### wait-for-setup.sh

Documented in `CLAUDE.md Commands (rewrite, from the repo root)`.

## `.github/workflows/ci.yml`

### CI runs on labelled PRs only

Documented in `CLAUDE.md Commands (rewrite, from the repo root)`.

### CI paths-ignore

Documented in `CLAUDE.md Commands (rewrite, from the repo root)`.

### CI token scope

The CI job only reads the repository and uploads an artifact, so `permissions` grants `contents: read` and no write scope on the token.

### CI timeout-minutes

The CI job has `timeout-minutes: 30` so a run that hangs cannot eat the month's Actions minutes; the whole job normally takes about ten minutes.

### CI step order

The cheap CI checks (VERSION format and the like) come before the Gradle build, so a PR that breaks one stops before the ten-minute build and spends fewer Actions minutes.

### Gradle cache key shared by CI and release

`ci.yml` and `release-apk.yml` use the same `GRADLE_BUILD_ACTION_CACHE_KEY_PREFIX` and `GRADLE_BUILD_ACTION_CACHE_KEY_JOB`, so a PR starts from what the last merge to main downloaded and compiled. A PR can read main's caches but not write them. Change both workflows together.

### CI Android compile condition

Compiling the Android app costs about as much as packaging it (most of it is compiling the shared modules a second time), so CI does it only when the PR touches the Android app, an Android source set or the Gradle setup, or carries the `android` or `apk` label. Shared code is already compiled for JVM and web, and every merge to main is built and published as a release, which would show anything else.

### Android unit tests in CI

Documented in `CLAUDE.md Commands (rewrite, from the repo root)`.

### APK artifact upload continue-on-error

The APK artifact upload step has `continue-on-error: true`: it is a convenience, not a check, and a full artifact storage quota must not fail the PR or skip the checks after it.

## `.github/workflows/release-apk.yml`

### Latest APK workflow

Documented in `CLAUDE.md Layout`.

### Release workflow concurrency

The release workflow runs in one concurrency group (`latest-apk`) with `cancel-in-progress: true`, so two merges close together cannot delete each other's release halfway and the newer one wins.

### Gradle cache key shared by CI and release

`release-apk.yml` uses the same Gradle cache key prefix and job as `ci.yml`, so pull requests that follow start from this build's downloads and compiled code.

### POKEPLAN_BUILD

Documented in `CLAUDE.md Layout`.

### The update manifest beside the APK

The rolling release carries `update.json` as well as `pokeplan.apk`: version, build, asset name, size, SHA-256, commit and
date. It is a separate asset so the app can learn of a build without downloading 120 MB and without the GitHub API's
unauthenticated rate limit, and it is attached under the fixed tag `latest`, which every merge deletes and recreates
(the asset URL stays the same). `pokeplan.apk` itself is never renamed, because the app builds its download URL from
that name. The repository is public, so the app reads both without a token; making it private again would need an
authentication path before the updater could work. The manifest is also attached to each version's own `vX.Y.Z`
release.

## `build.gradle.kts`

### Test logging in root build

The root build sets Gradle `testLogging` to print a failing test's whole stack trace (the default is one line), so a failure that only happens on the CI runner can be diagnosed from its log.

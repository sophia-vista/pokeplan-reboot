# REP-005: signing and committed credentials at 0.123.0

**Evidence for:** `IMPLEMENTATION_GUIDE.md` section 3.1 (release signing security follow-up) and release gate E.  
**Checked:** 6 November 2026, repository `main` at commit `8b3dfedf`, by reading tracked files and Git history. Nothing was tested on a device or against GitHub's settings.

## 1. Findings

| Item | Finding |
|---|---|
| Keystore | `androidApp/pokeplan-debug.keystore` is tracked. It was first added in PR #112 (the commit `d997c700`) and is the only keystore, `.jks`, `.p12` or `.pem` file ever tracked (a search of deleted files in all history found none) |
| Passwords | `androidApp/build.gradle.kts` holds the store password, key alias and key password in clear text (`pokeplan` for all three) |
| Where it signs | The one `shared` signing config signs **both** the `debug` and the `release` build types, so every local build, every CI build and every published APK (`latest` and each versioned release) carries the same signature (SHA-1 `1A:28:99:CD:34:46:02:04:1E:AF:7B:15:7F:D1:07:41:BC:30:F0:A5`, as `CLAUDE.md` records) |
| Intent | Documented as a private-use key for a private repository, with a TODO to replace it before any public release; a code comment and `CLAUDE.md` both say so. This is a test-grade key, not evidence that a production key was exposed |
| Private keys elsewhere | A search for PEM private-key headers in the 50 newest commits found none |
| Supabase anon key | `composeApp/.../CloudSettings.kt` holds a JWT whose payload is `role: anon` (not `service_role`), expiring in 2036. An anon key is meant to be public; its safety rests on the row-level security rules in `supabase/`. The server's deploy token is a repository secret (`SUPABASE_ACCESS_TOKEN` in `deploy-reward.yml`) |
| Firebase file | `legacy-android/app/google-services.json` is tracked (project `pokeplan-8930c`, package `com.mobdeve.s11.pokeplan`). Such files hold client identifiers, not server secrets, and belong to the retired legacy app |
| Backups | `android:allowBackup="true"` (see REP-003) lets Android back up app data with no user control |

## 2. What this means

- **While the repository stays private and the APK is for the owner's own phone**, the shared key is an acceptable convenience: anyone who can read the repository can sign an APK that installs over the owner's app, which is the same group who can already change the code.
- **It must not ship publicly.** Release gate E requires a protected release key. Anyone who cloned the repository (or a fork, or a leaked copy) could sign an update that Android would accept for existing installs.
- **Rotating is not free:** Android refuses an update signed with a different key. Because the owner holds the old key, the clean path is APK Signature Scheme v3 key rotation (`apksigner rotate` builds a lineage that lets installs signed with the old key accept the new one). If that is not wanted, a new application id would mean a fresh install and a data export/import (which the backup design, `DATA_AND_MIGRATIONS.md` section 10, provides anyway).

## 3. Plan

1. Decide how the app will be distributed (private sideload only, or public). If private only, keep the key and record that decision here.
2. Before any public build: generate a release key with a strong password; keep it and its password as CI secrets (not in the repository); sign the release build type with it and keep the debug build on a separate debug key; create a v3 rotation lineage from the old key.
3. Remove the clear-text passwords and the tracked keystore after step 2, and add a CI check that fails if a keystore or a `storePassword` literal is tracked.
4. Replace `allowBackup="true"` with the reviewed backup design (REP-008).
5. When the cloud path is retired (REP-012), remove the anon key and `deploy-reward.yml` with it.

## 4. Not established

Whether the repository or any fork was ever public; GitHub's secret-scanning alerts; whether the debug key's SHA-1 is registered with any external service (`CLAUDE.md` mentions Google sign-in in a code comment); a scan of the full history beyond the 50 newest commits for private-key headers.

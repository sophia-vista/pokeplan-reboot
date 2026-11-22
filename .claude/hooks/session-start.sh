#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

echo '{"async": true, "asyncTimeout": 600000}'
STATUS="$HOME/.pokeplan-setup"
rm -f "$STATUS.done" "$STATUS.failed"
trap 'echo "failed at line $LINENO" > "$STATUS.failed"' ERR

SDK="$HOME/android-sdk"
REPO="${CLAUDE_PROJECT_DIR:-$(pwd)}"

if [ ! -d "$SDK/platforms/android-37.0" ] || [ ! -d "$SDK/build-tools/37.0.0" ]; then
  mkdir -p "$SDK/cmdline-tools"
  if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    tmp=$(mktemp -d)
    curl -sSfo "$tmp/tools.zip" https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
    unzip -q "$tmp/tools.zip" -d "$tmp"
    rm -rf "$SDK/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
    rm -rf "$tmp"
  fi
  (yes || true) | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platform-tools" "platforms;android-37.0" "build-tools;37.0.0" > /dev/null
fi
[ -f "$REPO/local.properties" ] || echo "sdk.dir=$SDK" > "$REPO/local.properties"
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=\"$SDK\"" >> "$CLAUDE_ENV_FILE"
fi

INIT="$HOME/.gradle/init.d/mirror.gradle.kts"
if [ ! -f "$INIT" ]; then
  mkdir -p "$(dirname "$INIT")"
  cat > "$INIT" <<'EOF'
val mirror = "https://maven-central.storage-download.googleapis.com/maven2"
fun RepositoryHandler.addMirrorFirst() {
    if (isEmpty() || any { it.name == "CentralMirror" }) return
    val m = maven { url = uri(mirror); name = "CentralMirror" }
    remove(m); addFirst(m)
}
settingsEvaluated {
    pluginManagement.repositories.addMirrorFirst()
    dependencyResolutionManagement.repositories.addMirrorFirst()
}
allprojects {
    afterEvaluate { repositories.addMirrorFirst() }
    buildscript.repositories.addMirrorFirst()
}
EOF
fi

cd "$REPO" && ./gradlew --version > /dev/null 2>&1 || echo "Gradle warm-up skipped; the first build downloads it." >&2

touch "$STATUS.done"

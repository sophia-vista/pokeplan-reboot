#!/usr/bin/env bash
# The phone half of the UI quality gates (docs/pokelife/ARCHITECTURE/TEST_STRATEGY.md 12, 13 and 20).
#
# The JVM gates prove what a semantics tree and a rendered PNG can prove; they cannot prove TalkBack's spoken order,
# a real font-scale setting, a real large screen or real reduced-motion behaviour. This script drives the parts of the
# device pass that adb can drive and prints the rest as a checklist. It never installs, clears or changes app data.
#
# Usage:
#   tools/ui-check/device-accessibility.sh                # print the checklist and the attached device
#   tools/ui-check/device-accessibility.sh font 1.3       # set the system font scale and print what to check
#   tools/ui-check/device-accessibility.sh font 1.0       # restore it
#   tools/ui-check/device-accessibility.sh talkback on    # enable TalkBack (if the APK/component is present)
#   tools/ui-check/device-accessibility.sh talkback off
#   tools/ui-check/device-accessibility.sh shot home       # pull a screenshot to build/ui-check/home.png
#
# The app must already be installed (./gradlew :androidApp:assembleDebug, then install the debug APK).
set -euo pipefail
ADB="${ADB:-adb}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/build/ui-check"

command -v "$ADB" >/dev/null || { echo "adb not found; set ADB=/path/to/adb (the Android SDK platform-tools)."; exit 1; }
if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "No device: connect the phone, enable USB debugging and accept the computer's key."; exit 1
fi

checklist() {
  cat <<'TEXT'
Device accessibility pass (record model, Android build, app version and date in the PR or handoff)
  1. Install the debug/release-equivalent APK, open PokéLife, finish onboarding.
  2. Enlarge text:   tools/ui-check/device-accessibility.sh font 1.3
     Walk Home, Modules, Plan, Care, Heal, Mint, Base, Link, World, Gear, Me, Pokémon.
     Expected: no clipped label, no control pushed off screen, buttons keep their target size, dialogs scroll.
     Restore with:   tools/ui-check/device-accessibility.sh font 1.0
  3. TalkBack:       tools/ui-check/device-accessibility.sh talkback on
     Expected: every screen's first focus is its title/lead; every icon key announces its action; rows announce their
     label and state; a dialog moves focus inside and restores it on close; no control is announced as "button" with no
     name. Turn it off afterwards with: tools/ui-check/device-accessibility.sh talkback off
  4. Look: Settings → Look → a light background and a dark one; then Settings → General → Reduce motion on.
     Expected: no clipped text in either palette; the World/Launcher animations stop or shorten under Reduce motion.
  5. Larger screen (tablet or Android's display size): the same walk once; the pixel layouts scale without stretching a
     sprite or overlapping a row.
  6. Record each screen with Pass/Fail/Not-run and attach the two screenshots per state that the change adds.
TEXT
}

case "${1:-}" in
  font)
    [ $# -eq 2 ] || { echo "usage: $0 font <1.0|1.15|1.3|1.5>"; exit 2; }
    "$ADB" shell settings put system font_scale "$2"
    echo "font_scale=$2 set. The app redraws on reopen; compare against the same screen at 1.0."
    ;;
  talkback)
    [ $# -eq 2 ] || { echo "usage: $0 talkback <on|off>"; exit 2; }
    if [ "$2" = on ]; then
      "$ADB" shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
      "$ADB" shell settings put secure accessibility_enabled 1
      echo "TalkBack enabled (if it is installed). Walk the checklist; turn it off when done."
    else
      "$ADB" shell settings put secure accessibility_enabled 0
      "$ADB" shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
      echo "TalkBack disabled."
    fi
    ;;
  shot)
    [ $# -eq 2 ] || { echo "usage: $0 shot <name>"; exit 2; }
    mkdir -p "$OUT"
    "$ADB" exec-out screencap -p > "$OUT/$2.png"
    echo "wrote $OUT/$2.png"
    ;;
  ""|help|-h|--help)
    echo "Device: $("$ADB" shell getprop ro.product.model 2>/dev/null | tr -d '\r'), Android $("$ADB" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
    echo
    checklist
    ;;
  *)
    checklist
    ;;
esac

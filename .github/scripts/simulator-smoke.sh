#!/usr/bin/env bash
#
# iOS simulator smoke test.
#
# Builds nothing itself: the workflow stages SharedKit.xcframework and runs xcodebuild before calling
# this. What this adds is the part that was missing - actually *looking* at the app. It installs the
# simulator build, launches it straight into the migrated Compose tree, and captures screenshots of
# the native shell and of the Compose settings pane in both appearances.
#
# The reason this exists: the shared Compose UI, the calligraphic fonts and the imperial palette were
# only ever verified by a green compile. A compile cannot tell you whether a font rendered or whether
# the palette applied. A screenshot can.
#
# Requires an arm64 runner. shared/build.gradle.kts declares only iosArm64 and iosSimulatorArm64, so
# an x86_64 simulator build has no framework slice to link against.
#
# Inputs (environment):
#   APP_PATH       path to the built .app (default: the Debug-iphonesimulator product)
#   ARTIFACT_DIR   where screenshots and logs land (default: build/simulator-smoke)
#   MIN_DISTINCT   minimum distinct colours for a screenshot to count as rendered (default: 25)
#
# Output: PNG screenshots, console/log captures, screenshot-checks.txt, summary.md in ARTIFACT_DIR.

set -euo pipefail

BUNDLE_ID="com.lyihub.archiveassistant"
PRODUCT_NAME="聚合拾遗"
PREVIEW_ARGUMENT="--compose-preview"

APP_PATH="${APP_PATH:-build/SimDerivedData/Build/Products/Debug-iphonesimulator/${PRODUCT_NAME}.app}"
ARTIFACT_DIR="${ARTIFACT_DIR:-build/simulator-smoke}"
# Calibrated against real captures from run 37203635522 (measured at 4-pixel sampling, 5-bit colour
# quantisation, exactly what check-screenshot.swift computes):
#   native SwiftUI shell   86 distinct colours
#   Compose tree, light  2663
#   springboard fallback 2822   <- a crashed app passes any threshold, which is why crashes are
#                                  detected separately rather than by this check
# A genuinely blank screen sits at 1-3. The threshold is deliberately far from 86: the sparse native
# shell is legitimate content and must not sit one shade away from failing.
MIN_DISTINCT="${MIN_DISTINCT:-25}"

log() { echo; echo "=== $* ==="; }
die() { echo "::error::$*"; exit 1; }

mkdir -p "$ARTIFACT_DIR"

# ---------------------------------------------------------------------------------------------------
# Preconditions
# ---------------------------------------------------------------------------------------------------

log "host architecture"
HOST_ARCH="$(uname -m)"
echo "host arch: $HOST_ARCH"
if [ "$HOST_ARCH" != "arm64" ]; then
  die "this smoke test needs an arm64 runner (got $HOST_ARCH): the shared kernel declares iosSimulatorArm64 only, so an x86_64 simulator build cannot link."
fi

[ -d "$APP_PATH" ] || die "app bundle not found at '$APP_PATH'; expected the simulator build step to produce it."
echo "app bundle: $APP_PATH"

# ---------------------------------------------------------------------------------------------------
# Pick and boot a simulator
# ---------------------------------------------------------------------------------------------------

log "available simulators"
# Printed in full on purpose: this listing is the first thing to look at when the device parse below
# or the runtime choice looks wrong, and a truncated head is what makes that debugging guesswork.
xcrun simctl list devices available

# Emit "Name|UDID" pairs, preferring a recent full-size iPhone from the newest runtime.
#
# Two things about this listing cost a CI round trip: simctl pads every device line with a TRAILING
# SPACE, so an anchored `)$` never matched and the parse silently produced nothing; and the runtimes
# are grouped oldest-first, so taking the last match lands on the newest runtime.
DEVICES="$(xcrun simctl list devices available \
  | sed -nE 's/^[[:space:]]*(iPhone [^(]*) \(([0-9A-Fa-f-]+)\) \(.*\)[[:space:]]*$/\1|\2/p')"
if [ -z "$DEVICES" ]; then
  echo "::error::could not parse any iPhone simulator out of the simctl listing above."
  exit 1
fi

# `iPhone SE` is excluded on purpose: it is the one current phone whose width would misrepresent the
# layout, and the 16-series-and-later list is where the full-size devices live.
PICK="$(printf '%s\n' "$DEVICES" | grep -E '^iPhone (1[5-9]|Air)' | tail -n 1 || true)"
[ -n "$PICK" ] || PICK="$(printf '%s\n' "$DEVICES" | tail -n 1)"

DEVICE_NAME="${PICK%%|*}"
UDID="${PICK##*|}"
echo "selected: $DEVICE_NAME ($UDID)"

log "booting simulator"
xcrun simctl boot "$UDID" 2>/dev/null || echo "(already booted)"
xcrun simctl bootstatus "$UDID" -b

log "installing app"
xcrun simctl install "$UDID" "$APP_PATH"

# Anything written to the crash directory after this marker is from this run.
MARKER="$ARTIFACT_DIR/.smoke-start"
touch "$MARKER"

# ---------------------------------------------------------------------------------------------------
# Crash reports
# ---------------------------------------------------------------------------------------------------
#
# A crashing launch is invisible in a screenshot: what gets captured is whatever the simulator fell back
# to, which is colourful and passes the pixel check. So crashes are checked after every phase, and the
# failure names the phase that crashed.
CRASH_DIR="$HOME/Library/Logs/DiagnosticReports"
: > "$ARTIFACT_DIR/crashes.txt"

check_no_crash() {
  local what="$1"
  local console_log="$2"
  local found=0

  if [ -d "$CRASH_DIR" ]; then
    while IFS= read -r report; do
      grep -q "$BUNDLE_ID" "$report" 2>/dev/null || continue
      found=1
      echo "$report" >> "$ARTIFACT_DIR/crashes.txt"
      cp "$report" "$ARTIFACT_DIR/" 2>/dev/null || true
    done < <(find "$CRASH_DIR" -type f -name '*.ips' -newer "$MARKER" 2>/dev/null)
  fi

  [ "$found" -eq 1 ] || return 0

  # The .ips body is one enormous JSON line, so pulling the identifying fields beats printing it whole
  # and beats needing the artifact to know what happened. For a Kotlin exception those fields are mostly
  # empty (the report records a SIGABRT and nothing else), which is why the console log tail below
  # matters more: that is where `Uncaught Kotlin exception: ...` and its message actually appear.
  while IFS= read -r report; do
    echo "--- $report ---"
    grep -oE '"(exception|termination|asi|isCorpse|faultingThread)"[^,]{0,300}' "$report" 2>/dev/null | head -n 12 || true
    grep -oE '"(type|signal|code|subtype|reason|namespace|indicator|description)":("[^"]*"|[0-9]+)' "$report" 2>/dev/null | head -n 24 || true
    echo
  done < "$ARTIFACT_DIR/crashes.txt"

  if [ -f "$console_log" ]; then
    echo "--- console log tail: $console_log ---"
    tail -n 40 "$console_log" || true
    echo
  fi

  die "$what: the app produced a crash report during this phase. The report and its summary are in $ARTIFACT_DIR (crashes.txt plus the .ips files)."
}

# ---------------------------------------------------------------------------------------------------
# Launch, screenshot, repeat
# ---------------------------------------------------------------------------------------------------
#
# `simctl launch --console` streams the app's stdout/stderr and stays in the foreground for as long as
# the app lives, so every launch is backgrounded and then ended with `simctl terminate`. The PID is
# kept in a global rather than returned through `$(...)`: a background job started inside command
# substitution is not a child of this shell, so `wait` could never reap it.
CONSOLE_PID=""

start_app() {
  local console_log="$1"
  shift
  xcrun simctl launch --terminate-running-process --console "$UDID" "$BUNDLE_ID" "$@" \
    > "$console_log" 2>&1 &
  CONSOLE_PID=$!
}

shot() {
  xcrun simctl io "$UDID" screenshot --type=png "$ARTIFACT_DIR/$1"
  echo "captured $1 ($(wc -c < "$ARTIFACT_DIR/$1" | tr -d ' ') bytes)"
}

stop_app() {
  xcrun simctl terminate "$UDID" "$BUNDLE_ID" >/dev/null 2>&1 || true

  # `simctl launch --console` exits once the app dies, but do not stake the whole run on that: give it
  # a few seconds, then kill the stream so a stuck process cannot hang the job.
  local waited=0
  while [ "$waited" -lt 10 ]; do
    kill -0 "$CONSOLE_PID" 2>/dev/null || return 0
    sleep 1
    waited=$((waited + 1))
  done
  kill "$CONSOLE_PID" 2>/dev/null || true
  wait "$CONSOLE_PID" 2>/dev/null || true
}

# A failed launch is the one failure that would otherwise slip through: the screenshots would show the
# simulator home screen, which is full of content and passes the blank check. So the launch is verified
# explicitly before anything is captured. Two independent signals are accepted, because neither is
# documented as guaranteed: the app showing up in the simulator's launchd job list, and the
# "bundle: <pid>" line simctl prints.
assert_running() {
  local what="$1"
  local console_log="$2"
  local waited=0

  while [ "$waited" -lt 25 ]; do
    if xcrun simctl spawn "$UDID" launchctl list 2>/dev/null | grep -q "$BUNDLE_ID"; then
      echo "$what: running (launchd job present)."
      return 0
    fi
    if grep -qE "^${BUNDLE_ID}:[[:space:]]*[0-9]+" "$console_log" 2>/dev/null; then
      echo "$what: running (simctl reported a pid)."
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done

  echo "=== console output from the failed launch ==="
  cat "$console_log" 2>/dev/null || true
  echo "=== launchd jobs matching the bundle id ==="
  xcrun simctl spawn "$UDID" launchctl list 2>/dev/null | grep -i archive || echo "(none)"
  die "$what: the app did not start within 25s; screenshots would have shown the home screen."
}

# The pid line simctl prints proves the launch was *attempted*, not that the app survived it, so the
# check before a screenshot trusts only the launchd job list. Without this, an app that starts and then
# crashes is screenshotted as the springboard - colourful, and passing every pixel check.
assert_alive() {
  local what="$1"
  local console_log="$2"
  if xcrun simctl spawn "$UDID" launchctl list 2>/dev/null | grep -q "$BUNDLE_ID"; then
    echo "$what: still running before capture."
    return 0
  fi
  echo "::error::$what: the app is no longer running, so the screenshot would not have shown it."
  xcrun simctl spawn "$UDID" launchctl list 2>/dev/null | grep -i archive || echo "(no launchd job)"
  echo "=== recent console output ==="
  tail -n 40 "$console_log" 2>/dev/null || true
  exit 1
}

# Compose loads its fonts and resource pack on first composition, so the first frames are empty. The
# sleeps are generous on purpose: a too-early screenshot would look like a rendering bug.
COMPOSE_SETTLE_SECONDS=15
NATIVE_SETTLE_SECONDS=8

log "1/3 native shell, light appearance"
xcrun simctl ui "$UDID" appearance light >/dev/null 2>&1 || true
start_app "$ARTIFACT_DIR/console-native-shell.log"
assert_running "native shell" "$ARTIFACT_DIR/console-native-shell.log"
sleep "$NATIVE_SETTLE_SECONDS"
assert_alive "native shell" "$ARTIFACT_DIR/console-native-shell.log"
shot 01-native-shell-light.png
stop_app
check_no_crash "native shell phase" "$ARTIFACT_DIR/console-native-shell.log"

log "2/3 Compose tree, light appearance"
start_app "$ARTIFACT_DIR/console-compose-light.log" "$PREVIEW_ARGUMENT"
assert_running "Compose tree (light)" "$ARTIFACT_DIR/console-compose-light.log"
sleep "$COMPOSE_SETTLE_SECONDS"
assert_alive "Compose tree (light)" "$ARTIFACT_DIR/console-compose-light.log"
shot 02-compose-settings-light.png
stop_app
check_no_crash "Compose light phase" "$ARTIFACT_DIR/console-compose-light.log"

# Relaunch rather than toggling appearance under a running app: this exercises the value
# `isSystemInDarkTheme()` resolves at first composition, which is what the theme reads.
log "3/3 Compose tree, dark appearance"
xcrun simctl ui "$UDID" appearance dark >/dev/null 2>&1 || true
start_app "$ARTIFACT_DIR/console-compose-dark.log" "$PREVIEW_ARGUMENT"
assert_running "Compose tree (dark)" "$ARTIFACT_DIR/console-compose-dark.log"
sleep "$COMPOSE_SETTLE_SECONDS"
assert_alive "Compose tree (dark)" "$ARTIFACT_DIR/console-compose-dark.log"
shot 03-compose-settings-dark.png
stop_app
check_no_crash "Compose dark phase" "$ARTIFACT_DIR/console-compose-dark.log"

# ---------------------------------------------------------------------------------------------------
# App log and crash reports
# ---------------------------------------------------------------------------------------------------

log "app log (last 3 minutes)"
xcrun simctl spawn "$UDID" log show --last 3m --style compact \
  --predicate "process == \"$PRODUCT_NAME\"" > "$ARTIFACT_DIR/app-log.txt" 2>&1 || true
wc -l < "$ARTIFACT_DIR/app-log.txt" | tr -d ' ' | sed 's/$/ lines captured/'

echo "no crash reports for $BUNDLE_ID in any phase."

# ---------------------------------------------------------------------------------------------------
# Blank-screen check
# ---------------------------------------------------------------------------------------------------

log "screenshot pixel checks (minimum $MIN_DISTINCT distinct colours)"
: > "$ARTIFACT_DIR/screenshot-checks.txt"
FAILED=0
SHOT_COUNT=0
for png in "$ARTIFACT_DIR"/*.png; do
  [ -e "$png" ] || die "no screenshots were captured at all."
  SHOT_COUNT=$((SHOT_COUNT + 1))
  swift .github/scripts/check-screenshot.swift "$png" "$MIN_DISTINCT" 2>&1 \
    | tee -a "$ARTIFACT_DIR/screenshot-checks.txt" || FAILED=1
done

# ---------------------------------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------------------------------

{
  echo "## iOS simulator smoke test"
  echo
  echo "| | |"
  echo "|---|---|"
  echo "| simulator | \`$DEVICE_NAME\` (\`$UDID\`) |"
  echo "| host arch | \`$HOST_ARCH\` |"
  echo "| launch argument | \`$PREVIEW_ARGUMENT\` |"
  echo "| screenshots | $SHOT_COUNT |"
  echo "| crash reports | none |"
  echo
  echo "Screenshots for each appearance are in the \`ios-simulator-smoke\` artifact. The check below"
  echo "only proves content was drawn - it cannot tell a correct layout from a broken one, so the"
  echo "images themselves are the real output."
  echo
  echo '```'
  cat "$ARTIFACT_DIR/screenshot-checks.txt"
  echo '```'
} > "$ARTIFACT_DIR/summary.md"

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  cat "$ARTIFACT_DIR/summary.md" >> "$GITHUB_STEP_SUMMARY"
fi

if [ "$FAILED" != "0" ]; then
  cat "$ARTIFACT_DIR/screenshot-checks.txt"
  die "at least one screenshot did not look like rendered content; the app may have shown a blank screen."
fi

log "simulator smoke test passed ($SHOT_COUNT screenshots captured)"

#!/usr/bin/env bash
#
# iOS simulator smoke test.
#
# Builds nothing itself: the workflow stages SharedKit.xcframework and runs xcodebuild before calling
# this. What this adds is the part that was missing - actually *looking* at the app. It installs the
# simulator build, launches it, and captures screenshots of the Compose dashboard in both appearances,
# first on an iPhone and then on an iPad.
#
# Every capture is the same screen. The SwiftUI shell that used to sit above Compose (a
# `NavigationSplitView` with two placeholder columns) is gone: `MainWorkspaceView` now mounts
# `ArchiveComposeHostingViewController` and nothing else, so there is no native shell left to
# photograph and no launch argument needed to reach Compose. The two devices are still both captured
# because the dashboard reflows for a 2064pt-wide window and that reflow - along with the resource and
# font loading behind it - is exactly what has never been seen on an iPad-sized canvas.
#
# Which pane is captured follows the app, not this script: `IosComposeRoot` builds the store with
# `selectedPane = AppPane.TOPICS` (the dashboard). It used to open on settings back when settings was
# the only migrated pane, which is why the captures are named for the home pane.
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
#   MIN_DISTINCT   override the minimum distinct colours below (default: 300, see the calibration note)
#   RUN_IPAD       0 to skip the iPad pass (default: 1)
#
# Output: PNG screenshots, console/log captures, screenshot-checks.txt, summary.md in ARTIFACT_DIR.

set -euo pipefail

BUNDLE_ID="com.lyihub.archiveassistant"
PRODUCT_NAME="聚合拾遗"

APP_PATH="${APP_PATH:-build/SimDerivedData/Build/Products/Debug-iphonesimulator/${PRODUCT_NAME}.app}"
ARTIFACT_DIR="${ARTIFACT_DIR:-build/simulator-smoke}"
# The threshold is calibrated against real captures, measured at 4-pixel sampling with 5-bit
# per-channel colour quantisation — exactly what check-screenshot.swift computes:
#
#   iPhone 16 Plus, 1290x2796 (run 39979857765)
#     Compose dashboard, light / dark   1153 / 1154              (settings era: 167 / 154)
#   iPad Pro 13-inch (M4), 2064x2752 (run 39979857765)
#     Compose dashboard, light           1177
#   springboard fallback (either device)  2663 / 2822           <- a crashed app passes ANY threshold,
#                                                                  which is why crashes are detected by
#                                                                  crashes.txt and not by this check
#   genuinely blank screen (either device) 1-3
#
# The springboard is the *colour-rich* failure: it must never be mistaken for success, which is the
# whole reason the richer-looking numbers above are the failing ones.
#
# Every capture is now the same screen, so there is one floor instead of one per device. It used to
# be two (iPhone 25 over an 86-colour native shell, iPad 20 over an 81-colour one) because the sparse
# SwiftUI chrome was the least-coloured thing on screen and the floor had to sit below it. With the
# shell gone the least-coloured legitimate capture is the dashboard itself, so the old 25 was left
# over-claiming nothing and under-claiming everything: it would have kept passing a dashboard that
# lost 97% of its colours.
#
#   300 — 1153 / 300 = 3.84x headroom, slightly more than the 86 / 25 = 3.44 the iPhone value used to
#         carry, and ~100x above a blank screen's 3. Low enough that a partially-drawn dashboard (one
#         that lost its tiles or its list but still painted the palette) is not failed on grounds of
#         taste, high enough that a mostly-empty screen cannot pass it.
MIN_DISTINCT="${MIN_DISTINCT:-300}"
RUN_IPAD="${RUN_IPAD:-1}"

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

# Selects a device, boots it, installs the app and arms the crash-report marker.
#
# Emits "Name|UDID" pairs for one device family, preferring a match for `$2` from the newest runtime.
#
# Two things about this listing cost a CI round trip: simctl pads every device line with a TRAILING
# SPACE, so an anchored `)$` never matched and the parse silently produced nothing; and the runtimes
# are grouped oldest-first, so taking the last match lands on the newest runtime.
#
# `iPhone SE` is excluded on purpose: it is the one current phone whose width would misrepresent the
# layout, and the 16-series-and-later list is where the full-size devices live. The iPad preference is
# a plain `^iPad Pro` rather than an enumerate-the-lineup regex, because Apple's iPad naming churns far
# more often than the "iPhone 1x" pattern does; any iPad is an improvement over no iPad, so the
# fallback to "last iPad listed" is a legitimate outcome rather than a failure.
prepare_device() {
  local kind="$1"     # "iPhone" or "iPad"
  local prefer="$2"   # extended regex over the "Name|UDID" pairs; empty means "no preference"
  local slug="$3"     # used for the crash marker so each device has its own
  local devices pick

  # The name group is `.*` and not `[^(]*` because iPad device names contain parentheses themselves
  # ("iPad Pro 13-inch (M4)", "iPad (10th generation)"); an "up to the first paren" pattern parses
  # every iPhone and then silently finds no iPad at all. The 36-character UUID group is what keeps the
  # greedy `.*` honest.
  devices="$(xcrun simctl list devices available \
    | sed -nE "s/^[[:space:]]*(${kind} .*) \(([0-9A-Fa-f-]{36})\) \([A-Za-z ]+\)[[:space:]]*$/\\1|\\2/p")"
  if [ -z "$devices" ]; then
    die "could not parse any ${kind} simulator out of the simctl listing above."
  fi

  pick=""
  if [ -n "$prefer" ]; then
    pick="$(printf '%s\n' "$devices" | grep -E "$prefer" | tail -n 1 || true)"
  fi
  [ -n "$pick" ] || pick="$(printf '%s\n' "$devices" | tail -n 1)"

  DEVICE_NAME="${pick%%|*}"
  UDID="${pick##*|}"
  echo "selected: $DEVICE_NAME ($UDID)"

  log "booting simulator"
  xcrun simctl boot "$UDID" 2>/dev/null || echo "(already booted)"
  xcrun simctl bootstatus "$UDID" -b

  log "installing app"
  xcrun simctl install "$UDID" "$APP_PATH"

  # Anything written to the crash directory after this marker is from this device's run. It is
  # per-device so that a report from the iPhone cannot be misattributed to an iPad phase.
  MARKER="$ARTIFACT_DIR/.smoke-start-${slug}"
  touch "$MARKER"
}

prepare_device iPhone '^iPhone (1[5-9]|Air)' iphone
IPHONE_NAME="$DEVICE_NAME"
IPHONE_UDID="$UDID"
IPAD_NAME=""
IPAD_UDID=""

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

# A phase can also end with the app simply *gone*: no crash report, a console log that stops after the
# first couple of lines, and no launchd job left. Run 40062440293 hit exactly that in the dark phase on
# a loaded runner — the log had `iOS shared module initialized`, `crashes.txt` was empty, and nothing
# in the run suggested a Compose fault. So one relaunch is allowed, but only when no report explains
# the exit: a real crash still fails on the first attempt, because then the report is the finding.
#
# The pid line simctl prints only proves a launch was *attempted*, which is why survival is re-checked
# through the launchd job list right before the screenshot. Without that, an app that starts and then
# dies is screenshotted as the springboard: colourful, and passing every pixel check.
capture_phase() { # <label> <console log> <settle seconds> <png name> [launch args...]
  local label="$1"
  local console_log="$2"
  local settle="$3"
  local png="$4"
  shift 4
  local attempt=1

  while [ "$attempt" -le 2 ]; do
    start_app "$console_log" "$@"
    assert_running "$label" "$console_log"
    sleep "$settle"

    if xcrun simctl spawn "$UDID" launchctl list 2>/dev/null | grep -q "$BUNDLE_ID"; then
      echo "$label: still running before capture."
      shot "$png"
      stop_app
      check_no_crash "$label phase" "$console_log"
      return 0
    fi

    echo "::warning::$label: the app is no longer running after ${settle}s."
    stop_app
    # Dies with the report details when one exists, so the relaunch below is only reached for a
    # termination the simulator left unexplained.
    check_no_crash "$label phase" "$console_log"
    [ "$attempt" -lt 2 ] || break
    echo "::warning::$label: no crash report, so the app was dropped rather than crashed. Relaunching once."
    attempt=$((attempt + 1))
  done

  echo "=== recent console output ==="
  tail -n 40 "$console_log" 2>/dev/null || true
  die "$label: the app was gone before capture on two consecutive launches, and neither left a crash report. Screenshots would have shown the simulator home screen."
}

# Compose loads its fonts and resource pack on first composition, so the first frames are empty. The
# sleeps are generous on purpose: a too-early screenshot would look like a rendering bug.
COMPOSE_SETTLE_SECONDS=15

log "iPhone 1/2 dashboard, light appearance"
xcrun simctl ui "$UDID" appearance light >/dev/null 2>&1 || true
capture_phase "dashboard (light)" "$ARTIFACT_DIR/console-compose-light.log" \
  "$COMPOSE_SETTLE_SECONDS" 01-compose-home-light.png

# Relaunch rather than toggling appearance under a running app: this exercises the value
# `isSystemInDarkTheme()` resolves at first composition, which is what the theme reads.
log "iPhone 2/2 dashboard, dark appearance"
xcrun simctl ui "$UDID" appearance dark >/dev/null 2>&1 || true
capture_phase "dashboard (dark)" "$ARTIFACT_DIR/console-compose-dark.log" \
  "$COMPOSE_SETTLE_SECONDS" 02-compose-home-dark.png

log "iPhone app log (last 3 minutes)"
xcrun simctl spawn "$UDID" log show --last 3m --style compact \
  --predicate "process == \"$PRODUCT_NAME\"" > "$ARTIFACT_DIR/app-log-iphone.txt" 2>&1 || true
wc -l < "$ARTIFACT_DIR/app-log-iphone.txt" | tr -d ' ' | sed 's/$/ lines captured/'

# ---------------------------------------------------------------------------------------------------
# iPad
# ---------------------------------------------------------------------------------------------------
#
# The project ships `TARGETED_DEVICE_FAMILY = "1,2"`, and the dashboard is a responsive Compose
# layout, so a 2064pt-wide window is a genuinely different rendering: the five-tile grid and the
# topic list reflow, and the calligraphic fonts are asked for at other sizes. Capturing it is the
# cheapest way to notice that broke, because nothing on a phone would show it.
#
# Landscape is deliberately not attempted here. `simctl` has no rotation command at all, and the
# app's own orientation mask is ignored on iPadOS' resizable-window model, so this pass captures
# portrait. Run 40064146914 proved it: with a landscape-only mask the iPad still produced a
# 2064x2752 image. A UI-test target that rotates the simulator through `XCUIDevice.shared` lives
# on a separate branch; until that lands, capture what the runner can actually produce instead of
# asserting an orientation it cannot set.
if [ "$RUN_IPAD" != "0" ]; then
  prepare_device iPad '^iPad Pro' ipad
  IPAD_NAME="$DEVICE_NAME"
  IPAD_UDID="$UDID"

  xcrun simctl ui "$UDID" appearance light >/dev/null 2>&1 || true

  log "iPad 1/1 dashboard, light appearance"
  capture_phase "iPad dashboard" "$ARTIFACT_DIR/console-ipad-compose-light.log" \
    "$COMPOSE_SETTLE_SECONDS" 03-ipad-compose-home-light.png

  log "iPad app log (last 3 minutes)"
  xcrun simctl spawn "$UDID" log show --last 3m --style compact \
    --predicate "process == \"$PRODUCT_NAME\"" > "$ARTIFACT_DIR/app-log-ipad.txt" 2>&1 || true
  wc -l < "$ARTIFACT_DIR/app-log-ipad.txt" | tr -d ' ' | sed 's/$/ lines captured/'
fi

echo "no crash reports for $BUNDLE_ID in any phase."

# ---------------------------------------------------------------------------------------------------
# Blank-screen check
# ---------------------------------------------------------------------------------------------------

log "screenshot pixel checks (>= $MIN_DISTINCT distinct colours)"
: > "$ARTIFACT_DIR/screenshot-checks.txt"
FAILED=0
SHOT_COUNT=0
for png in "$ARTIFACT_DIR"/*.png; do
  [ -e "$png" ] || die "no screenshots were captured at all."
  SHOT_COUNT=$((SHOT_COUNT + 1))
  echo "-- $(basename "$png") minimum: $MIN_DISTINCT" >> "$ARTIFACT_DIR/screenshot-checks.txt"
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
  echo "| iPhone | \`$IPHONE_NAME\` (\`$IPHONE_UDID\`) |"
  if [ -n "$IPAD_NAME" ]; then
    echo "| iPad | \`$IPAD_NAME\` (\`$IPAD_UDID\`) |"
  else
    echo "| iPad | skipped (\`RUN_IPAD=0\`) |"
  fi
  echo "| host arch | \`$HOST_ARCH\` |"
  echo "| screenshots | $SHOT_COUNT |"
  echo "| crash reports | none |"
  echo
  echo "Screenshots for each appearance are in the \`ios-simulator-smoke\` artifact. The check below"
  echo "only proves content was drawn - it cannot tell a correct layout from a broken one, so the"
  echo "images themselves are the real output."
  echo
  echo "Every capture is the same screen, so one floor covers both devices: >= $MIN_DISTINCT distinct"
  echo "colours. Calibrated captures measure 1153 / 1154 on a 1290x2796 iPhone and 1177 on a"
  echo "2064x2752 iPad; a blank screen measures 1-3, and a crashed app falls through to the springboard,"
  echo "which measures 2663-2822 and would pass any threshold - which is why crashes are judged by"
  echo "crashes.txt instead of by this check."
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

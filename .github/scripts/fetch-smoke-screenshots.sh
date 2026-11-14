#!/usr/bin/env bash
#
# Downloads one run's simulator screenshots into ./screenshot/, keeping only the two most recent
# archives.
#
# Why this exists: GitHub's artifact retention for this repository is short (an artifact from a run
# less than three weeks old already answers `410 Artifact has expired`), and the smoke job's pixel
# check can only prove that *something* was drawn. Judging the layout - and in particular deciding
# whether the ten XML vector drawables actually rendered instead of falling back to a transparent
# 1x1 placeholder - needs the PNGs in front of a human while they still exist.
#
# Usage:
#   .github/scripts/fetch-smoke-screenshots.sh              # newest run on the current branch
#   .github/scripts/fetch-smoke-screenshots.sh 39979857765  # a specific run id
#   .github/scripts/fetch-smoke-screenshots.sh --list       # show what is already kept, and stop
#   .github/scripts/fetch-smoke-screenshots.sh --keep 3     # change how many archives are retained
#
# Archives are laid out as screenshot/<runId>-<shortSha>/ and pruned after each successful download:
# the two newest by run id survive, older ones are removed. Run ids increase monotonically, so
# sorting them numerically is the same as sorting by capture time and does not depend on file
# timestamps being preserved by a copy or a checkout.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEST_ROOT="$REPO_ROOT/screenshot"
WORKFLOW_FILE="ios-build.yml"
ARTIFACT_NAME="ios-simulator-smoke"

# Files worth keeping. The artifact also carries xcodebuild logs, which are large and reproducible
# from the run page; the images and their verdicts are the part that expires into nothing.
KEEP_PATTERNS=(*.png summary.md screenshot-checks.txt crashes.txt app-log-*.txt console-*.log)

KEEP_ARCHIVES=2
RUN_ID=""

log() { printf '%s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

while [ "$#" -gt 0 ]; do
  case "$1" in
    --list)
      if [ -d "$DEST_ROOT" ]; then
        log "kept archives under $DEST_ROOT:"
        kept="$(find "$DEST_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null \
          | sort -t- -k1,1nr || true)"
        if [ -n "$kept" ]; then
          printf '%s\n' "$kept" | sed 's/^/  /'
        else
          echo "  (none)"
        fi
      else
        log "no screenshot directory yet: $DEST_ROOT"
      fi
      exit 0
      ;;
    --keep)
      shift
      KEEP_ARCHIVES="${1:?--keep needs a number}"
      ;;
    -h|--help)
      sed -n '2,22p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    -*)
      die "unknown option: $1"
      ;;
    *)
      RUN_ID="$1"
      ;;
  esac
  shift
done

case "$KEEP_ARCHIVES" in
  ''|*[!0-9]*) die "--keep expects a positive whole number, got '$KEEP_ARCHIVES'" ;;
esac
[ "$KEEP_ARCHIVES" -ge 1 ] || die "--keep must be at least 1"

command -v gh >/dev/null 2>&1 || die "gh (GitHub CLI) is not on PATH."

# gh fails intermittently on this machine with `net/http: TLS handshake timeout`, and a single
# failure here would otherwise look like "this run has no screenshots". Retry before concluding.
gh_retry() {
  local attempt out
  for attempt in 1 2 3 4 5 6; do
    if out="$("$@" 2>&1)"; then
      printf '%s' "$out"
      return 0
    fi
    case "$out" in
      *timeout*|*"TLS handshake"*|*502*|*503*)
        sleep $((attempt * 5))
        continue
        ;;
      *)
        printf '%s' "$out"
        return 1
        ;;
    esac
  done
  printf '%s' "$out"
  return 1
}

if [ -z "$RUN_ID" ]; then
  BRANCH="$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)"
  log "looking up the newest run of $WORKFLOW_FILE on $BRANCH"
  RUN_ID="$(gh_retry gh run list --branch "$BRANCH" --workflow "$WORKFLOW_FILE" \
    --limit 1 --json databaseId,headSha --jq '.[0].databaseId')" \
    || die "could not list runs: $RUN_ID"
  [ -n "$RUN_ID" ] || die "no run found for branch '$BRANCH'."
fi

META="$(gh_retry gh run view "$RUN_ID" --json headSha,status,conclusion,workflowName)" \
  || die "could not read run $RUN_ID: $META"
SHA="$(printf '%s' "$META" | sed -n 's/.*"headSha":"\([0-9a-f]*\)".*/\1/p')"
STATUS="$(printf '%s' "$META" | sed -n 's/.*"status":"\([^"]*\)".*/\1/p')"
CONCL="$(printf '%s' "$META" | sed -n 's/.*"conclusion":\("\([^"]*\)"\|null\).*/\2/p')"
[ -n "$SHA" ] || die "run $RUN_ID did not report a headSha; got: $META"

SHORT_SHA="$(printf '%s' "$SHA" | cut -c1-7)"
ARCHIVE_DIR="$DEST_ROOT/${RUN_ID}-${SHORT_SHA}"

log "run $RUN_ID ($SHORT_SHA) status=$STATUS conclusion=${CONCL:-pending}"

if [ "$STATUS" != "completed" ]; then
  die "run $RUN_ID is still $STATUS; screenshots do not exist until the smoke job finishes."
fi

# Artifacts are attached even to failed runs, and a failed run's screenshots are the ones worth
# looking at, so a red conclusion is reported but not treated as a reason to refuse the download.
if [ "$CONCL" != "success" ]; then
  log "note: run $RUN_ID concluded '${CONCL}'; downloading anyway - a failed run's screenshots are usually the point."
fi

mkdir -p "$ARCHIVE_DIR"
TMP_DIR="$(mktemp -d)"
cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

log "downloading artifact '$ARTIFACT_NAME'"
OUT="$(gh_retry gh run download "$RUN_ID" --name "$ARTIFACT_NAME" --dir "$TMP_DIR")"
case "$OUT" in
  *"has expired"*|*410*)
    die "the artifact for run $RUN_ID has expired (GitHub returned 410). Screenshots must be fetched shortly after the run finishes; re-run the workflow to produce a fresh set."
    ;;
esac
[ -d "$TMP_DIR" ] || die "download reported success but produced no directory."

shopt -s nullglob
copied=0
for pattern in "${KEEP_PATTERNS[@]}"; do
  for f in "$TMP_DIR"/$pattern; do
    [ -f "$f" ] || continue
    cp "$f" "$ARCHIVE_DIR/"
    copied=$((copied + 1))
  done
done
shopt -u nullglob

if [ "$copied" -eq 0 ]; then
  die "the downloaded artifact contained none of the expected files (${KEEP_PATTERNS[*]})."
fi

log "saved $copied file(s) to ${ARCHIVE_DIR#"$REPO_ROOT/"}"

# Prune. Sorted numerically by the run id prefix, newest first.
mapfile -t ARCHIVES < <(find "$DEST_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null \
  | sort -t- -k1,1nr)
for ((i = KEEP_ARCHIVES; i < ${#ARCHIVES[@]}; i++)); do
  victim="$DEST_ROOT/${ARCHIVES[$i]}"
  log "pruning ${victim#"$REPO_ROOT/"}"
  rm -rf "$victim"
done

log "kept ${KEEP_ARCHIVES} newest archive(s); use --list to see them."

#!/usr/bin/env bash
#
# Measure the NATURAL width and the EFFECTIVE on-screen font size of the Mermaid diagrams in one or
# more markdown files — the numbers behind `docs/formatting/readability-guide.md` §"Diagram size".
#
# Usage:
#   bash scripts/measure-mermaid.sh <file.md|dir> [more…] [--width N]
#
#   bash scripts/measure-mermaid.sh .clinerules/hexagonal-architecture.md docker/fuseki/README.md
#   bash scripts/measure-mermaid.sh docs --width 430          # default width is the chat column
#
# Why this is a committed script and not a throwaway probe: the widths in the readability guide are
# load-bearing (they decide whether a diagram gets restructured), and a probe that lives under
# `target/` is deleted by `mvn clean` — so the claim could not be re-checked, and the earlier
# "it is 1239px everywhere" error survived on hand-waving.  Run this before citing a width.
#
# Output, one row per block:
#   ok|WIDE  natW=<natural px>  font=<effective px>  <file> #<n>
# WIDE means the renderer had to shrink the diagram to fit the column (font < 18px); `≤ 420px`
# natural is the cheap conservative target.  Read the guide's *Scope* note before restructuring:
# the IntelliJ plugin does not shrink the way Chromium does.
#
# Skips (exit 0, with a SKIP note) when node, a Chromium binary, or the Mermaid build is
# unavailable, so it never blocks a machine that cannot run a browser.  The Mermaid build is fetched
# once from jsDelivr into the fixture dir (under `target/`, so it is gitignored) and then cached.
#
# NOTE (why the fixture lives under target/): the Chromium snap cannot read /tmp (private tmp
# namespace), so the fixture must sit somewhere under the repo.
set -uo pipefail
cd "$(dirname "$0")/.."      # repo root

MERMAID_VERSION="11.4.1"
DIR="domox-webapp/target/mermaid-measure"
WIDTH=430
PATHS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --width) WIDTH="${2:-430}"; shift 2 ;;
    *) PATHS+=("$1"); shift ;;
  esac
done
[ ${#PATHS[@]} -gt 0 ] || PATHS=(.clinerules docs)

# --- prerequisites (skip, never fail) ----------------------------------------

NODE="$(command -v node || true)"
[ -n "$NODE" ] || { echo "SKIP: node not found (needed to drive the browser)"; exit 0; }

BROWSER=""
for c in chromium chromium-browser google-chrome google-chrome-stable /snap/bin/chromium; do
  if command -v "$c" >/dev/null 2>&1; then BROWSER="$(command -v "$c")"; break; fi
done
[ -n "$BROWSER" ] || { echo "SKIP: no chromium/chrome binary found"; exit 0; }

mkdir -p "$DIR"
rm -f "$DIR/measure.html"

# The Mermaid build the Cline viewer uses.  Cached across runs; a failed fetch is a skip, not an
# error, so an offline machine is not blocked.
if [ ! -s "$DIR/mermaid.min.js" ]; then
  curl -fsSL "https://cdn.jsdelivr.net/npm/mermaid@${MERMAID_VERSION}/dist/mermaid.min.js" \
    -o "$DIR/mermaid.min.js" 2>/dev/null \
    || { echo "SKIP: could not fetch mermaid@${MERMAID_VERSION} (offline?)"; exit 0; }
fi

# --- fixture -----------------------------------------------------------------

"$NODE" scripts/measure-mermaid.mjs gen "$WIDTH" "$DIR" "${PATHS[@]}" || exit 2

# --- run ---------------------------------------------------------------------

UDD="$PWD/$DIR/chrome-profile"
rm -rf "$UDD"
"$BROWSER" --headless=new --no-sandbox --disable-gpu --remote-debugging-port=0 \
  --user-data-dir="$UDD" "file://$PWD/$DIR/measure.html" > "$DIR/browser.log" 2>&1 &
BROWSER_PID=$!
trap 'kill $BROWSER_PID 2>/dev/null' EXIT

PORT=""
for _ in $(seq 1 40); do
  if [ -s "$UDD/DevToolsActivePort" ]; then PORT="$(head -1 "$UDD/DevToolsActivePort")"; break; fi
  sleep 0.25
done
if [ -z "$PORT" ]; then
  echo "SKIP: headless browser did not expose a debugging port (see $DIR/browser.log)"
  exit 0
fi

echo "===> mermaid width probe (headless $(basename "$BROWSER"), ${WIDTH}px column, CDP port $PORT)"
"$NODE" scripts/measure-mermaid.mjs read "$PORT"
STATUS=$?
kill $BROWSER_PID 2>/dev/null
exit $STATUS

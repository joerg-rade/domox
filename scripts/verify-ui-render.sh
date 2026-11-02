#!/usr/bin/env bash
#
# UI render guardrail for DoMoX.
#
# Why this exists:
#   No other check renders anything, so a CSS change that *looks* right but produces an
#   unusable UI passes every existing guardrail.  This was learned the hard way: the D3 graph
#   column was reported "verified" on the strength of a panel-box measurement (96x32) while
#   the graph's CONTENT was drawn 343px to the right of the clipped cell — the column was
#   empty.  This script asserts the user-visible properties instead (see verify-ui-render.mjs).
#
# What it does:
#   Builds a fixture that mirrors the real markup Causeway generates for a table cell —
#     <div class="table-responsive"><table class="contents table …">
#       <tr><td class="causeway-domox-ClassCdd-candidateName">…
#           <td class="causeway-domox-ClassCdd-d3Graph"><div><div class="d3GraphPanel">
#             <div class="d3GraphCanvas"><svg width="100%" height="800" viewBox="0 0 700 800">…
#   — with the REAL static/css/domox.css, the extension's D3Panel.css (from the jar) and
#   Bootstrap (from the webjar), then drives it in headless Chromium over the DevTools
#   protocol and asserts:
#     * the teaser shows the graph inside the cell (content actually on screen)
#     * the teaser does not blow up the row height
#     * the popup stays CLOSED over a non-graph column  (the tr:hover regression)
#     * the popup OPENS over the graph column, is large enough, shows the graph, and does not
#       change the row height
#
# Usage:
#   bash scripts/verify-ui-render.sh
#
#   Configurable via env vars:
#     DOMOX_M2    local Maven repository (default: ~/.m2/repository)
#     DOMOX_CSS   stylesheet under test    (default: domox-webapp/.../static/css/domox.css)
#                 — overridable so the check itself can be negative-tested with a deliberately
#                 broken copy.
#
# Skips (exit 0, with a SKIP note) when node, a Chromium/Chrome binary, or the jars needed for
# the fixture are missing — so it never blocks a machine that cannot run a browser.
#
# NOTE (why the fixture lives under target/): the Chromium snap cannot read /tmp (private tmp
# namespace), so the fixture must sit somewhere under the repo.  `mvn clean` removes it.
set -uo pipefail
cd "$(dirname "$0")/.."      # repo root

M2="${DOMOX_M2:-$HOME/.m2/repository}"
CSS="${DOMOX_CSS:-domox-webapp/src/main/resources/static/css/domox.css}"
DIR="domox-webapp/target/verify-ui"

# --- prerequisites (skip, never fail) ----------------------------------------

NODE="$(command -v node || true)"
[ -n "$NODE" ] || { echo "SKIP: node not found (needed to drive the browser)"; exit 0; }

BROWSER=""
for c in chromium chromium-browser google-chrome google-chrome-stable /snap/bin/chromium; do
  if command -v "$c" >/dev/null 2>&1; then BROWSER="$(command -v "$c")"; break; fi
done
[ -n "$BROWSER" ] || { echo "SKIP: no chromium/chrome binary found"; exit 0; }

[ -f "$CSS" ] || { echo "ERROR: $CSS not found (run from the repo root)"; exit 2; }

# NOTE: the -sources / -tests / -javadoc classifiers must be excluded — `sort -V | tail -1`
# otherwise picks one of those (they sort after the plain jar), and the fixture would silently
# lose D3Panel.css / Bootstrap and therefore not be faithful to the real page.
D3_JAR="$(ls -1 "$M2"/org/apache/causeway/extensions/causeway-extensions-d3-wicket-integration/*/causeway-extensions-d3-wicket-integration-*.jar 2>/dev/null | grep -vE '(-sources|-tests|-javadoc)\.jar$' | sort -V | tail -1)"
BS_JAR="$(ls -1 "$M2"/org/webjars/bootstrap/*/bootstrap-*.jar 2>/dev/null | grep -vE '(-sources|-tests|-javadoc)\.jar$' | sort -V | tail -1)"
if [ -z "$D3_JAR" ] || [ -z "$BS_JAR" ]; then
  echo "SKIP: D3Panel.css / Bootstrap jar not in $M2 (run 'mvn -q validate' once)"
  exit 0
fi

# --- fixture -----------------------------------------------------------------

rm -rf "$DIR"; mkdir -p "$DIR"
cp "$CSS" "$DIR/domox.css"
unzip -p "$D3_JAR" org/apache/causeway/extensions/d3/wkt/integration/components/D3Panel.css > "$DIR/d3panel.css"
unzip -p "$BS_JAR" 'META-INF/resources/webjars/bootstrap/*/css/bootstrap.css' > "$DIR/bootstrap.css"

cat > "$DIR/fixture.html" <<'HTML'
<!DOCTYPE html><html><head><meta charset="utf-8">
<link rel="stylesheet" href="bootstrap.css">
<link rel="stylesheet" href="d3panel.css">
<link rel="stylesheet" href="domox.css">
</head><body>
<div class="table-responsive">
<table class="contents table table-striped table-sm table-hover table-bordered">
<tbody><tr class="even">
<td class="causeway-domox-ClassCdd-candidateName">Pet</td>
<td class="causeway-domox-ClassCdd-d3Graph"><div><div class="d3GraphPanel">
<div class="d3GraphCanvas" id="d3CanvasFixture"><svg width="100%" height="800" viewBox="0 0 700 800" preserveAspectRatio="xMidYMid meet">
<g class="d3-nodes">
  <rect x="60" y="60" width="580" height="680" fill="none" stroke="#3498DB"></rect>
  <rect x="300" y="350" width="100" height="100" fill="#3498DB"></rect>
  <rect x="180" y="250" width="60" height="40" fill="#E74C3C"></rect>
  <rect x="460" y="480" width="60" height="40" fill="#2ECC71"></rect>
</g>
</svg></div>
</div></div></td>
</tr></tbody></table>
</div>
</body></html>
HTML

# --- run ---------------------------------------------------------------------

UDD="$PWD/$DIR/chrome-profile"
"$BROWSER" --headless=new --no-sandbox --disable-gpu --remote-debugging-port=0 \
  --user-data-dir="$UDD" "file://$PWD/$DIR/fixture.html" > "$DIR/browser.log" 2>&1 &
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

echo "===> UI render check (headless $(basename "$BROWSER"), CDP port $PORT)"
"$NODE" scripts/verify-ui-render.mjs "$PORT"
STATUS=$?
kill $BROWSER_PID 2>/dev/null
exit $STATUS

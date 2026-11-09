#!/usr/bin/env bash
#
# Headless-render guardrail for the STATIC UML diagram payload — the `"layout":"uml"` branch of
# `d3-integration.mjs`.
#
# Why: `UmlLayoutIntegrityTest` asserts the *geometry* over the real model, but nothing checked that
# the CLIENT actually draws what the payload says.  A regression in the static renderer — a lost arrow
# marker, a missing legend, the wrong viewBox — would pass every other check in the repo.
#
# Usage: bash scripts/verify-uml-render.sh
#
#   Skips (exit 0, with a SKIP note) when node, a Chromium binary, or the d3 webjar is unavailable, so
#   it never blocks a machine that cannot run a browser.
#
# NOTE (why the fixture lives under target/): the Chromium snap cannot read /tmp (private tmp
# namespace), so the fixture must sit somewhere under the repo.  `mvn clean` removes it.
set -uo pipefail
cd "$(dirname "$0")/.."      # repo root

M2="${DOMOX_M2:-$HOME/.m2/repository}"
RENDERER="domox-webapp/src/main/resources/org/apache/causeway/extensions/d3/wkt/integration/res/d3-integration.mjs"
DIR="domox-webapp/target/verify-uml"

# --- prerequisites (skip, never fail) ----------------------------------------

NODE="$(command -v node || true)"
[ -n "$NODE" ] || { echo "SKIP: node not found (needed to drive the browser)"; exit 0; }

BROWSER=""
for c in chromium chromium-browser google-chrome google-chrome-stable /snap/bin/chromium; do
  if command -v "$c" >/dev/null 2>&1; then BROWSER="$(command -v "$c")"; break; fi
done
[ -n "$BROWSER" ] || { echo "SKIP: no chromium/chrome binary found"; exit 0; }

[ -f "$RENDERER" ] || { echo "ERROR: $RENDERER not found (run from the repo root)"; exit 2; }

# The -sources / -tests / -javadoc classifiers must be excluded: `sort -V | tail -1` otherwise picks
# one of those (they sort after the plain jar) and the fixture would silently lose d3.
D3_JAR="$(ls -1 "$M2"/org/webjars/npm/d3/*/d3-*.jar 2>/dev/null | grep -vE '(-sources|-tests|-javadoc)\.jar$' | sort -V | tail -1)"
if [ -z "$D3_JAR" ]; then
  echo "SKIP: the d3 webjar is not in $M2 (run 'mvn -q validate' once)"
  exit 0
fi

# --- fixture -----------------------------------------------------------------

rm -rf "$DIR"; mkdir -p "$DIR"
unzip -p "$D3_JAR" 'META-INF/resources/webjars/d3/*/dist/d3.min.js' > "$DIR/d3.min.js"
cp "$RENDERER" "$DIR/d3-integration.mjs"

cat > "$DIR/fixture.html" <<'HTML'
<!DOCTYPE html><html><head><meta charset="utf-8"><title>uml static render</title></head>
<body>
<div id="d3CanvasFixture" style="width:900px;height:600px"></div>
<script>var jQuery = {};</script>
<script src="d3.min.js"></script>
<script src="d3-integration.mjs"></script>
<script>
// A payload shaped exactly as UmlLayoutDiagrams emits it: three boxes, a generalization whose path runs
// from the target (sub-class) to the source (super-class), an association, and the three-row legend.
// Node `c` is a real overflow case — the name needs ~188 units at 12px and the box is 159 wide.
var PAYLOAD = {
  "layout": "uml", "title": "Fixture", "canvas": {"width": 600, "height": 400},
  "nodes": [
    {"id": "a", "name": "Alpha", "kind": "Class", "x": 40, "y": 40, "w": 160, "h": 60},
    {"id": "b", "name": "Beta", "kind": "Class", "x": 40, "y": 260, "w": 160, "h": 60},
    {"id": "c", "name": "ServiceProviderOnNegotiation", "kind": "Class", "x": 300, "y": 40, "w": 159, "h": 70}
  ],
  "links": [
    {"source": "a", "target": "b", "kind": "generalization", "label": "",
     "points": [[120, 260], [120, 100]]},
    {"source": "a", "target": "b", "kind": "association", "label": "rel",
     "points": [[40, 70], [40, 290]]},
    {"source": "c", "target": "a", "kind": "generalization", "label": "", "arrow": false,
     "points": [[300, 75], [200, 75], [200, 70]]}
  ],
  "legend": [
    {"kind": "node", "color": "#3498DB", "label": "Class", "description": "a class box"},
    {"kind": "association", "color": "#7f8c8d", "label": "Association", "description": "a plain line"},
    {"kind": "generalization", "color": "#e67e22", "label": "Generalization", "description": "an arrow"}
  ]
};
WicketStuff.D3.init({ canvasId: 'd3CanvasFixture', graphData: JSON.stringify(PAYLOAD), enableZoom: true });
</script>
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

echo "===> UML render check (headless $(basename "$BROWSER"), CDP port $PORT)"
"$NODE" scripts/verify-uml-render.mjs "$PORT"
STATUS=$?
kill $BROWSER_PID 2>/dev/null
exit $STATUS

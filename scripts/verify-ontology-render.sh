#!/usr/bin/env bash
#
# Headless-render guardrail for the FORCE-LAYOUT ontology diagram — the branch of
# `d3-integration.mjs` that draws an ontology payload ({nodes, links, legend, reSearch}).
#
# Why: the server side is covered by `RdfGraphDiagramsTest` (the node/label mapping) and the
# adapter's `JenaRdfAdapterTest` (the real SPARQL path), but nothing checked that the CLIENT actually
# draws the label lines it is given.  The fourth line — the source reference ontology (gist, BFO,
# Schema.org, …) — is easy to drop in a refactor and invisible to every other check.
#
# Usage: bash scripts/verify-ontology-render.sh
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
DIR="domox-webapp/target/verify-ontology"

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
<!DOCTYPE html><html><head><meta charset="utf-8"><title>ontology force render</title></head>
<body>
<div id="d3CanvasFixture" style="width:700px;height:500px"></div>
<script>var jQuery = {};</script>
<script src="d3.min.js"></script>
<script src="d3-integration.mjs"></script>
<script>
// A payload shaped exactly as RdfGraphDiagrams emits it: three ontology nodes (two attributed to a
// source reference ontology, one not), two links, a legend, and the reSearch drill-down flag.
// No "layout" key, so the renderer takes the force-layout path.
var PAYLOAD = {
  "nodes": [
    {"id": "n1", "name": "Agreement", "subdomain": "", "pos": "Class", "color": "#3498DB",
     "r": 14, "approved": true, "isCandidateColumn": false, "ontology": "gist"},
    {"id": "n2", "name": "Person", "subdomain": "", "pos": "Class", "color": "#3498DB",
     "r": 14, "approved": true, "isCandidateColumn": false, "ontology": "Schema.org"},
    {"id": "n3", "name": "LocalThing", "subdomain": "", "pos": "Individual", "color": "#2ECC71",
     "r": 11, "approved": true, "isCandidateColumn": false}
  ],
  "links": [
    {"source": "n1", "target": "n2", "label": "relatedTo"},
    {"source": "n2", "target": "n3", "label": "instanceOf"}
  ],
  "legend": [
    {"color": "#3498DB", "label": "Class", "description": "a concept"},
    {"color": "#2ECC71", "label": "Individual", "description": "an instance"}
  ],
  "reSearch": true
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

echo "===> ontology render check (headless $(basename "$BROWSER"), CDP port $PORT)"
"$NODE" scripts/verify-ontology-render.mjs "$PORT"
STATUS=$?
kill $BROWSER_PID 2>/dev/null
exit $STATUS

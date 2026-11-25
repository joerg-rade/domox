#!/usr/bin/env bash
#
# Headless-render guardrail for the three UML branches of `d3-integration.mjs`: the STATIC one (the
# OntoUML export's stored geometry, `"layout":"uml"`), the FORCE one (`"layout":"uml","force":true`,
# the assembled model's seed grid plus a `d3-force` simulation, which the reader can drag), and the
# PACKAGE flavour of the force one (`"packages":true` — the sub-domain overview, where each node is drawn
# as a UML package symbol rather than as a class box, and every line of its body is a link to the class it
# stands for).
#
# Why: `UmlLayoutIntegrityTest` asserts the *geometry* over the real model, but nothing checked that
# the CLIENT actually draws what the payload says.  A regression in any renderer — a lost arrow
# marker, a missing legend, the wrong viewBox, a compartment the server sized for but the client does
# not draw, a collision force that leaves the boxes on top of one another, a package drawn as a plain
# rectangle — would pass every other check in the repo.
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
<div id="d3CanvasForce" style="width:900px;height:600px"></div>
<div id="d3CanvasPackages" style="width:900px;height:600px"></div>
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
    {"kind": "generalization", "color": "#000000", "label": "Generalization", "description": "an arrow"}
  ]
};
WicketStuff.D3.init({ canvasId: 'd3CanvasFixture', graphData: JSON.stringify(PAYLOAD), enableZoom: true });

// A payload shaped exactly as UmlDiagramLayout emits it: `force:true`, a *seed* rectangle per box (no
// routes at all), members and an archetype on the boxes, and the legend's archetype row.  Box `c` is the
// overflow case: the server now *builds* a box to fit its widest line, so a narrow box with a long member
// line in it is what a payload from an older server looks like — it is here to keep the client's
// shrink-to-fit fallback honest.  Box `a` carries the pair `ClassDiagram` now puts on a class box
// (`objectType` + `candidateId`), so the client's click-through to that class's own page is asserted
// rather than assumed; `b` and `c` carry neither and must stay inert.
var FORCE_PAYLOAD = {
  "layout": "uml", "force": true, "title": "Fixture", "canvas": {"width": 600, "height": 400},
  "nodes": [
    {"id": "a", "name": "Alpha", "kind": "Class", "x": 40, "y": 40, "w": 160, "h": 60,
     "attributes": ["id: Long", "name: String"], "methods": [], "superclass": true,
     "objectType": "domox.ClassCdd", "candidateId": 42},
    {"id": "b", "name": "Beta", "kind": "Class", "x": 40, "y": 260, "w": 160, "h": 48,
     "attributes": [], "methods": ["approve()"]},
    {"id": "c", "name": "ServiceProviderOnNegotiation", "kind": "Class", "x": 300, "y": 40, "w": 159, "h": 60,
     "attributes": ["ServiceProviderNegotiationStrategy: String"], "methods": ["findAll(): List"],
     "archetype": "Party", "color": "#ffe0b2"}
  ],
  "links": [
    {"source": "a", "target": "b", "kind": "generalization", "label": "", "arrow": true},
    {"source": "c", "target": "a", "kind": "association", "label": "rel"}
  ],
  "legend": [
    {"kind": "node", "color": "#3498DB", "label": "Class", "description": "a class box"},
    {"kind": "association", "color": "#7f8c8d", "label": "Association", "description": "a plain line"},
    {"kind": "generalization", "color": "#000000", "label": "Generalization", "description": "an arrow"},
    {"kind": "archetype", "color": "#ffe0b2", "label": "Party", "description": "a Coad/Lefebvre archetype"}
  ]
};
WicketStuff.D3.init({ canvasId: 'd3CanvasForce', graphData: JSON.stringify(FORCE_PAYLOAD), enableZoom: true });

// A payload shaped exactly as `ClassDiagram#graphOverview` + `UmlDiagramLayout#render(…, packages=true)`
// emit it: `"packages":true` in the header, `kind` "Package" on every node, each node's *attributes* the
// classes of that sub-domain that relate to a class outside it, no methods, and a legend whose node row is
// "Package" with no archetype row (a sub-domain has no Coad/Lefebvre archetype).  The Generalization row is
// present but unused — no link is one — so the check also pins the client's "only explain what is actually
// drawn" filter.  The second node is the overflow case: `CheckoutAndPaymentProcessing` is wider than the
// tab it can have (159 units), so the client must shrink it into the tab and keep it readable.
// The two edges stand for *different numbers* of collaborations (2 and 7), which is what pins the
// connector weight: on the overview the label is a count, and the line is drawn the thicker the more
// relations it stands for.
// Each line of a package's body is also listed in `contents` with the class row it stands for — the pair
// the client links on.  Inventory's two lines carry it (so both must be underlined, pointer-cursored and
// click-through); Checkout's `Till` deliberately carries no row, so it must stay plain text that opens
// nothing; Delivery lists nothing at all.
var PACKAGE_PAYLOAD = {
  "layout": "uml", "force": true, "packages": true, "title": "Fixture — sub-domain overview",
  "canvas": {"width": 600, "height": 400},
  "nodes": [
    {"id": "Inventory", "name": "Inventory", "kind": "Package", "x": 40, "y": 40, "w": 180, "h": 56,
     "attributes": ["Order", "OrderLine"], "methods": [],
     "contents": [{"name": "Order", "objectType": "domox.ClassCdd", "candidateId": 7},
                  {"name": "OrderLine", "objectType": "domox.ClassCdd", "candidateId": 8}]},
    {"id": "Checkout", "name": "CheckoutAndPaymentProcessing", "kind": "Package", "x": 300, "y": 40, "w": 159, "h": 44,
     "attributes": ["Till"], "methods": [], "contents": [{"name": "Till"}]},
    {"id": "Delivery", "name": "Delivery", "kind": "Package", "x": 40, "y": 240, "w": 140, "h": 44,
     "attributes": [], "methods": []}
  ],
  "links": [
    {"source": "Inventory", "target": "Checkout", "kind": "association", "label": "2"},
    {"source": "Inventory", "target": "Delivery", "kind": "association", "label": "7"}
  ],
  "legend": [
    {"kind": "node", "color": "#3498DB", "label": "Package",
     "description": "a sub-domain, listing the classes that relate to another sub-domain"},
    {"kind": "association", "color": "#7f8c8d", "label": "Association", "description": "a plain line"},
    {"kind": "generalization", "color": "#000000", "label": "Generalization", "description": "an arrow"}
  ]
};
WicketStuff.D3.init({ canvasId: 'd3CanvasPackages', graphData: JSON.stringify(PACKAGE_PAYLOAD), enableZoom: true });
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

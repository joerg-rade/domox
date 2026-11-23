#!/usr/bin/env bash
#
# UI render guardrail: a multi-line String ACTION RESULT keeps its line breaks.
#
# Why this exists:
#   The PlantUML exports (generateUmlDiagram / generateSubDomainUmlDiagram) were verified correct
#   SERVER-side — the action really returns 53 lines separated by "\n" — while the page showed the
#   whole diagram as ONE long line.  No server-side check can see that: the newlines reach the DOM
#   intact and are collapsed by the browser.  This script asserts the rendered line boxes instead.
#
#   The cause (traced through the viewer sources) is that an action result is shown by
#   StandaloneValuePanel, which builds the value through the ordinary ATTRIBUTE_NAME_AND_VALUE
#   pipeline with a PSEUDO attribute.  A pseudo attribute's rendering hint is
#   STANDALONE_PROPERTY_COLUMN, which RenderingHint.isInTable() reports as in-table, so
#   AttributePanel takes the COMPACT scenario and emits the plain label fragment
#   <span class="fragment-compact-label"> — never a <textarea> (isUsingTextarea() short-circuits on
#   isCompact(), so @MultiLine / MultiLineFacet cannot help).  The fix is therefore the CSS rule
#   .standaloneValuePanel .fragment-compact-label { white-space: pre-wrap } in static/css/domox.css.
#
# What it does:
#   Builds a fixture with the REAL markup shape and the REAL stylesheet —
#     <div class="standaloneValuePanel standaloneValueComponentType">
#       <span class="standaloneValue">
#         <div class="scalarNameAndValueComponentType">
#           <span class="fragment-compact-label">@startuml
#           …19 lines…
#           @enduml</span>
#   — plus two controls: the SAME text with an inline `white-space: normal` (i.e. the pre-fix
#   behaviour, which must collapse to one line) and the same fragment class inside a table cell
#   (which the rule must NOT affect).  Then it drives it in headless Chromium over the DevTools
#   protocol and asserts, by counting the text's rendered line boxes (distinct line tops from
#   Range.getClientRects()):
#     * the result renders one line box per source line (the diagram is no longer one line)
#     * white-space: pre-wrap is what does it, and the un-styled copy collapses to one line
#     * the rule does not leak into list/table cells, which use the identical fragment class
#
# Usage:
#   bash scripts/verify-standalone-value-render.sh
#
#   Configurable via env vars:
#     DOMOX_CSS   stylesheet under test (default: domox-webapp/.../static/css/domox.css)
#                 — overridable so this check can be NEGATIVE-tested with a deliberately
#                 broken copy (it must then FAIL, not pass).
#
# Skips (exit 0, with a SKIP note) when node or a Chromium/Chrome binary is missing, so it never
# blocks a machine that cannot run a browser.  It needs no jars and no running instance.
#
# NOTE (why the fixture lives under target/): the Chromium snap cannot read /tmp (private tmp
# namespace), so the fixture must sit somewhere under the repo.  `mvn clean` removes it.
set -uo pipefail
cd "$(dirname "$0")/.."      # repo root

CSS="${DOMOX_CSS:-domox-webapp/src/main/resources/static/css/domox.css}"
DIR="domox-webapp/target/verify-standalone-value"

# --- prerequisites (skip, never fail) ----------------------------------------

NODE="$(command -v node || true)"
[ -n "$NODE" ] || { echo "SKIP: node not found (needed to drive the browser)"; exit 0; }

BROWSER=""
for c in chromium chromium-browser google-chrome google-chrome-stable /snap/bin/chromium; do
  if command -v "$c" >/dev/null 2>&1; then BROWSER="$(command -v "$c")"; break; fi
done
[ -n "$BROWSER" ] || { echo "SKIP: no chromium/chrome binary found"; exit 0; }

[ -f "$CSS" ] || { echo "ERROR: $CSS not found (run from the repo root)"; exit 2; }

# --- the payload -------------------------------------------------------------
#
# Source-like text: blank lines, indentation that is significant, and one long line.  Written as a
# real multi-line text node, exactly as the viewer emits it (only HTML-escaped — escaping leaves
# "\n" alone).  Command substitution strips the trailing newline, so the text node ends with the
# last character of the payload, as in the real page.

PAYLOAD="$(cat <<'PLANTUML'
@startuml
title Pet sub domain

package "Pet" {
  class Pet {
    + name : String
    + birthDate : Date
    + owner : Owner
    --
    + rename(newName : String) : Pet
  }
  class Owner {
    + name : String
  }
}

Pet "1" --> "0..*" Owner : owned by
Pet ..> Pet : supersedes (a deliberately long line, long enough to prove that pre-wrap still wraps rather than forcing a horizontal scrollbar)
@enduml
PLANTUML
)"
LINES="$(printf '%s\n' "$PAYLOAD" | wc -l)"
[ "$LINES" -gt 10 ] || { echo "ERROR: fixture payload has only $LINES lines"; exit 2; }

# --- fixture -----------------------------------------------------------------

rm -rf "$DIR"; mkdir -p "$DIR"
cp "$CSS" "$DIR/domox.css"

{
  cat <<'HTML'
<!DOCTYPE html><html><head><meta charset="utf-8">
<link rel="stylesheet" href="domox.css">
</head><body>
<!--
  Faithfulness note: the panels sit in a deliberately WIDE container (20000px) so that NO line
  wrapping is involved.  The property under test is whether the newlines are preserved, and a wide
  container makes the expected line-box count exact instead of dependent on the page width.  The
  <table> below is left at normal width, since only the CSS SCOPING is asserted for it.
-->
<div id="wide" style="width:20000px">

  <!-- subject: the real markup shape of an action result, rendered by the real stylesheet.
       The chain is taken verbatim from the viewer's own templates —
         StandaloneValuePanel.html:  div.standaloneValuePanel.standaloneValueComponentType > span.standaloneValue
         AttributePanelWithFormField.html:  div.scalarNameAndValueComponentType
                                            > span.fragment-compact-label  (the COMPACT fragment) -->
  <div class="standaloneValuePanel standaloneValueComponentType">
    <span class="standaloneValue">
      <div class="scalarNameAndValueComponentType">
HTML
  # NOTE: the payload starts IMMEDIATELY after the opening tag (no newline), exactly as the viewer
  # emits it — a newline here would add a blank first line and shift the expected line count by one.
  printf '%s' '        <span id="subject" class="fragment-compact-label">'
  printf '%s' "$PAYLOAD"
  cat <<'HTML'
</span>
      </div>
    </span>
  </div>

  <!-- control: the SAME text with the pre-fix behaviour forced inline, so the rule is proven to be
       what preserves the breaks (and that the assertion can fail) -->
  <div class="standaloneValuePanel standaloneValueComponentType">
    <span class="standaloneValue">
      <div class="scalarNameAndValueComponentType">
HTML
  printf '%s' '        <span id="control" class="fragment-compact-label" style="white-space:normal">'
  printf '%s' "$PAYLOAD"
  cat <<'HTML'
</span>
      </div>
    </span>
  </div>

</div>

<!-- regression guard: the IDENTICAL fragment class inside a list/table cell must be unaffected -->
<div class="table-responsive">
<table class="contents table table-sm"><tbody><tr>
<td class="causeway-domox-DomainModel-umlDiagram"><span id="inCell" class="fragment-compact-label">Pet</span></td>
</tr></tbody></table>
</div>
</body></html>
HTML
} > "$DIR/fixture.html"

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

echo "===> standalone value render check (headless $(basename "$BROWSER"), CDP port $PORT, payload $LINES lines)"
"$NODE" scripts/verify-standalone-value-render.mjs "$PORT" "$LINES"
STATUS=$?
kill $BROWSER_PID 2>/dev/null
exit $STATUS

#!/usr/bin/env bash
#
# Layout-XML guardrail for DoMoX.
#
# Why this exists:
#   Causeway SILENTLY DISCARDS a *.layout.xml that does not satisfy its structural rules: the build
#   stays green, the app boots, and the page quietly falls back to Causeway's bundled
#   GridFallbackLayout.xml — which puts every property in a span-4 column.  The only trace is one
#   ERROR line buried in the boot log:
#
#     Grid metadata errors in domox.dom.requirements.OntologyDiagram:
#     <bs:metadataError>No column and also no tabgroup found with the 'unreferencedCollections'
#     attribute set</bs:metadataError>
#
#   That cost a debugging cycle: the layout looked correct, compiled, and did nothing.  This script
#   fails fast on the mechanical half of the rules, without booting anything.
#
# What it checks (per *.layout.xml under domox-domain/src/main/java):
#   FAIL  a column or tabGroup carries unreferencedCollections="true"      (proven requirement)
#   FAIL  every <cpt:property> sits inside a <cpt:fieldSet>                (BSCol has no properties
#                                                                          list — see the fieldSet rule)
#   WARN  a column carries unreferencedActions="true"                      (convention; every layout
#                                                                          in the repo has it, but
#                                                                          it is not proven required)
#
# Usage:  bash scripts/check-layout-xml.sh
set -uo pipefail

cd "$(dirname "$0")/.."   # repo root

FAILED=0
WARNED=0

mapfile -t LAYOUTS < <(find domox-domain/src/main/java -name '*.layout.xml' | sort)

if [ ${#LAYOUTS[@]} -eq 0 ]; then
  echo "ERROR: no *.layout.xml found — wrong working directory?"
  exit 2
fi

# Comments must be stripped before the checks: a layout's own explanatory comment mentions the
# markers by name ("Causeway requires ... unreferencedCollections=\"true\""), so grepping the raw
# file matches the COMMENT and passes a layout that would in fact be discarded.  (Found by
# negative-testing this script.)  One line in, one line out, so line numbers are preserved.
STRIP_COMMENTS='
{
  line=$0; out=""
  while (length(line) > 0) {
    if (incomment) {
      i=index(line,"-->")
      if (i==0) { line="" } else { line=substr(line,i+3); incomment=0 }
    } else {
      i=index(line,"<!--")
      if (i==0) { out=out line; line="" } else { out=out substr(line,1,i-1); line=substr(line,i+4); incomment=1 }
    }
  }
  print out
}'

for layout in "${LAYOUTS[@]}"; do
  problems=""
  stripped=$(mktemp)
  awk "$STRIP_COMMENTS" "$layout" > "$stripped"

  # 1. The proven requirement: without this marker Causeway rejects the whole grid.
  if ! grep -q 'unreferencedCollections=' "$stripped"; then
    problems="${problems}missing unreferencedCollections= ; "
  fi

  # 2. A <cpt:property> must be inside a <cpt:fieldSet> (BSCol exposes fieldSets/collections/actions
  #    but no properties list).  Track fieldSet depth.
  stray=$(awk '
    /<cpt:fieldSet/    { depth++ }
    /<\/cpt:fieldSet>/ { if (depth > 0) depth-- }
    /<cpt:property/    { if (depth == 0) { printf "%d ", NR } }
  ' "$stripped")
  if [ -n "$stray" ]; then
    problems="${problems}<cpt:property> outside a <cpt:fieldSet> at line(s): ${stray}; "
  fi

  # 3. Convention (reported, not fatal — it is not proven to be required).
  if ! grep -q 'unreferencedActions=' "$stripped"; then
    echo "    [WARN] $layout — no unreferencedActions= (every other layout sets it)"
    WARNED=1
  fi

  rm -f "$stripped"

  if [ -n "$problems" ]; then
    echo "    [FAIL] $layout"
    echo "           ${problems%; }"
    echo "           Causeway would DISCARD this grid and fall back to the span-4 default layout."
    FAILED=1
  fi
done

if [ $FAILED -eq 0 ]; then
  echo "OK: ${#LAYOUTS[@]} layout XML(s) satisfy the structural rules (unreferencedCollections marker, properties inside fieldSets)."
fi
exit $FAILED

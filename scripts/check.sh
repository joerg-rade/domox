#!/usr/bin/env bash
#
# DoMoX fast-check bundle (local guardrail).
#
# Usage:
#   bash scripts/check.sh            # schema drift + JS syntax + layout XML + dep alignment + unit tests + UI render + webjar + metamodel guardrail
#   bash scripts/check.sh --quick    # schema drift + layout XML only (both instant, no Maven)
#
# This mirrors what CI runs (.github/workflows/ci.yml) but exploits the local dev DB where the
# schema-drift guardrail applies. Steps that cannot run right now (e.g. the domox-db container
# is not up) are skipped with a warning, not failed. Run from anywhere; the script cd's to the
# repo root so mvn reactor paths and the DDL file resolve exactly as documented.
set -uo pipefail

cd "$(dirname "$0")/.."   # repo root

FAILED=0
step() { printf '\n===> %s\n' "$1"; }
ok()   { printf '    [OK]   %s\n' "$1"; }
skip() { printf '    [SKIP] %s\n' "$1"; }
bad()  { printf '    [FAIL] %s\n' "$1"; FAILED=1; }

# --- Java/Maven environment (no .sdkmanrc in this repo; falls back to SDKMAN default JDK) --
# set -u must be lifted while sourcing sdkman-init.sh: it reads env vars that are unbound in a
# bare shell (e.g. SDKMAN_CANDIDATES_API) and would otherwise abort the whole script.
if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
  set +u
  # shellcheck disable=SC1091
  . "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk env >/dev/null 2>&1 || true
  set -u
fi

# --- 1. Schema drift (dev-DB guardrail) ------------------------------------------------
step "1/8  Schema drift (DDL vs live domox-db)"
if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx domox-db; then
  if bash scripts/check-schema-drift.sh; then ok "no drift"; else bad "drift detected"; fi
else
  skip "container 'domox-db' not running (docker start domox-db) — drift check can't run"
fi

# --- 2. Layout-XML structure (instant, Maven-free) --------------------------------------
# Causeway SILENTLY DISCARDS a *.layout.xml that breaks its structural rules and falls back to the
# span-4 default grid — the build stays green and only a buried boot-log ERROR hints at it.  Cheap
# enough to run in --quick too, so a layout mistake is caught at commit time.
step "2/8  Layout XML (unreferencedCollections marker, properties inside fieldSets)"
if LO=$(bash scripts/check-layout-xml.sh 2>&1); then
  printf '%s\n' "$LO" | sed 's/^/    /'
  ok "layout XML structure valid"
else
  printf '%s\n' "$LO" | sed 's/^/    /'
  bad "layout XML would be discarded by Causeway (see FAIL lines above)"
fi

if [ "${1:-}" = "--quick" ]; then
  echo
  [ $FAILED -eq 0 ] && echo "QUICK CHECK PASSED" || echo "QUICK CHECK FAILED"
  exit $FAILED
fi

# --- 3. JavaScript syntax check (hand-edited .mjs files) --------------------------------
step "3/8  JavaScript syntax check"
JSOK=0
# Source only: a scratch .mjs under target/ (or in venv/) is build output, not something we maintain.
for f in $(find . \( -name node_modules -o -name target -o -name venv -o -name .git \) -prune -o -name '*.mjs' -print 2>/dev/null); do
  if node --check "$f" 2>/dev/null; then JSOK=$((JSOK+1)); else bad "syntax error in $f"; fi
done
[ $JSOK -gt 0 ] && ok "$JSOK .mjs files pass syntax check" || skip "no .mjs files found"

# --- 4. Dependency alignment (pom vs the Causeway parent chain) -------------------------
step "4/8  Dependency alignment (pom vs Causeway parent BOM)"
# The parent chain (causeway-app-starter-parent -> causeway-bom) pins spring-boot.version;
# this pom overrides it, so the two must be kept in step by hand.  Drift mixes releases on the
# classpath and fails at startup, not at build time — see the script header.
if bash scripts/check-dependency-alignment.sh; then
  ok "spring-* / spring-boot versions aligned with the parent chain"
else
  bad "version skew — see the FAIL lines above"
fi

# --- 5. Unit tests (domox-domain + domox-adapter + domox-webapp unit tests) ------------
step "5/8  Unit tests (domain + adapter + webapp unit)"
LOG=/tmp/domox-check-unit.log
# -Dmaven.build.cache.enabled=false: the maven-build-cache extension (.mvn/) restores cached
# surefire executions and skips re-running them; a guardrail script's job is to actually run
# the tests, so the cache must be explicitly disabled here.
if mvn -B -pl domox-domain -am test -Dmaven.build.cache.enabled=false >"$LOG" 2>&1; then
  ok "unit tests passed"
else
  bad "unit tests failed (log: $LOG — last lines: $(tail -5 "$LOG" | tr '\n' ' '))"
fi
# The webapp module's unit tests (domox.webapp.unittests.**) are container-free, so they can run
# in this bundle.  Named by package rather than by class, so a new webapp unit test is picked up
# without editing this script.  (The *integ* tests need Docker and stay out of this bundle.)
LOG=/tmp/domox-check-unit-webapp.log
if mvn -B test -pl domox-webapp -Dtest='domox.webapp.unittests.**' -am \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.build.cache.enabled=false >"$LOG" 2>&1; then
  ok "webapp unit tests passed"
else
  bad "webapp unit tests failed (log: $LOG — last lines: $(tail -5 "$LOG" | tr '\n' ' '))"
fi
# The RDF/OWL adapter test is container-free as well, but the adapter module's OTHER tests spin up
# containers (Kroki, CoreNLP), so run this class by name rather than the whole module.
LOG=/tmp/domox-check-unit-rdf.log
if mvn -B test -pl domox-adapter -Dtest=JenaRdfAdapterTest,UmlLayoutIntegrityTest -am \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.build.cache.enabled=false >"$LOG" 2>&1; then
  ok "RDF adapter tests passed"
else
  bad "RDF adapter tests failed (log: $LOG — last lines: $(tail -5 "$LOG" | tr '\n' ' '))"
fi

# UmlLayoutIntegrityTest dumps every diagram's payload to domox-adapter/target/uml-payloads, and the
# Python report asserts the SAME geometry invariants over that dump.  Running it here keeps the two
# implementations from drifting apart, and is the offline half of the measurement loop — no instance,
# no database, no endpoint (see .clinerules/d3-diagram.md).
DUMP=domox-adapter/target/uml-payloads
if ! command -v python3 >/dev/null 2>&1; then
  skip "python3 not available — the offline payload report can't run"
elif [ ! -d "$DUMP" ]; then
  skip "$DUMP not written (the adapter test above did not run)"
elif python3 scripts/uml-diagram-report.py --check --payload "$DUMP" >/tmp/domox-check-uml.log 2>&1; then
  ok "uml-diagram-report.py agrees on the dumped payloads"
else
  bad "uml-diagram-report.py disagrees (log: /tmp/domox-check-uml.log)"
fi

# --- 6. UI render check (headless browser; CSS/JS in list views) ------------------------
step "6/8  UI render check (headless browser)"
# Asserts the user-visible behaviour of the D3 graph column (content actually on screen, popup
# only over the graph column, row height stable) — the class of bug no other check can see.
# Skips (exit 0 + "SKIP:") when node / a Chromium binary / the fixture jars are unavailable.
if UIOUT=$(bash scripts/verify-ui-render.sh 2>&1); then
  printf '%s\n' "$UIOUT" | sed 's/^/    /'
  if printf '%s' "$UIOUT" | grep -q 'SKIP:'; then
    skip "not runnable here (node/chromium/jars missing — see above)"
  else
    ok "table-cell graph rendering as expected"
  fi
else
  printf '%s\n' "$UIOUT" | sed 's/^/    /'
  bad "UI render check failed"
fi

# The static UML payload has its own renderer branch in d3-integration.mjs, so it needs its own
# assertion that the boxes, the arrow markers and the legend are actually drawn.  Same skip behaviour.
if UMLOUT=$(bash scripts/verify-uml-render.sh 2>&1); then
  printf '%s\n' "$UMLOUT" | sed 's/^/    /'
  if printf '%s' "$UMLOUT" | grep -q 'SKIP:'; then
    skip "not runnable here (node/chromium/d3 webjar missing — see above)"
  else
    ok "static UML rendering as expected"
  fi
else
  printf '%s\n' "$UMLOUT" | sed 's/^/    /'
  bad "UML render check failed"
fi

# --- 7. Webjar resource check (dev-instance guardrail) ---------------------------------
step "7/8  Webjar resources served correctly (not through Wicket)"
if curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/webjars/pdfjs-dist/5.4.624/build/pdf.min.mjs 2>/dev/null | grep -q 200; then
  ok "webjar resource accessible at /webjars/... (not /wicket/wicket/...)"
else
  bad "webjar resource not accessible — double-wicket URL regression? (app must be running on :8080)"
fi

# --- 8. Metamodel guardrail (boot-time DomainModelValidator veto) ----------------------
step "8/8  Metamodel guardrail (ValidateDomainModelIntegTest)"
# This test boots a Spring context (and EclipseLink, against the shared domox.SEQ_GEN).  With a dev
# instance already owning :8080 that second context contends with it and can hang for minutes
# instead of failing, so skip rather than block.  (See the "Port / DB ownership caveat" in
# .clinerules/commands.md.)
if curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/ 2>/dev/null | grep -qE '^[23]'; then
  skip "a dev instance owns :8080 — stop it and re-run to validate the metamodel"
  echo
  [ $FAILED -eq 0 ] && echo "ALL CHECKS PASSED (metamodel guardrail skipped)" || echo "FAILURES DETECTED — see logs above"
  exit $FAILED
fi

LOG=/tmp/domox-check-meta.log
# Cache disabled for the same reason as step 2: -Dtest values are NOT part of the build-cache
# key, so a cached entry from a different -Dtest run would restore and silently skip the
# metamodel veto entirely.
if mvn -B test -pl domox-webapp -Dtest=ValidateDomainModelIntegTest -am \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.build.cache.enabled=false >"$LOG" 2>&1; then
  ok "metamodel guardrail passed"
else
  bad "metamodel guardrail failed (log: $LOG)"
fi

echo
[ $FAILED -eq 0 ] && echo "ALL CHECKS PASSED" || echo "FAILURES DETECTED — see logs above"
exit $FAILED
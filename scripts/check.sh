#!/usr/bin/env bash
#
# DoMoX fast-check bundle (local guardrail).
#
# Usage:
#   bash scripts/check.sh            # schema drift + JS syntax + domain unit tests + webjar check + metamodel guardrail
#   bash scripts/check.sh --quick    # schema-drift only
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
step "1/7  Schema drift (DDL vs live domox-db)"
if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx domox-db; then
  if bash scripts/check-schema-drift.sh; then ok "no drift"; else bad "drift detected"; fi
else
  skip "container 'domox-db' not running (docker start domox-db) — drift check can't run"
fi

if [ "${1:-}" = "--quick" ]; then
  echo
  [ $FAILED -eq 0 ] && echo "QUICK CHECK PASSED" || echo "QUICK CHECK FAILED"
  exit $FAILED
fi

# --- 2. JavaScript syntax check (hand-edited .mjs files) --------------------------------
step "2/7  JavaScript syntax check"
JSOK=0
for f in $(find . -path '*/node_modules' -prune -o -name '*.mjs' -print 2>/dev/null); do
  if node --check "$f" 2>/dev/null; then JSOK=$((JSOK+1)); else bad "syntax error in $f"; fi
done
[ $JSOK -gt 0 ] && ok "$JSOK .mjs files pass syntax check" || skip "no .mjs files found"

# --- 3. Dependency alignment (pom vs the Causeway parent chain) -------------------------
step "3/7  Dependency alignment (pom vs Causeway parent BOM)"
# The parent chain (causeway-app-starter-parent -> causeway-bom) pins spring-boot.version;
# this pom overrides it, so the two must be kept in step by hand.  Drift mixes releases on the
# classpath and fails at startup, not at build time — see the script header.
if bash scripts/check-dependency-alignment.sh; then
  ok "spring-* / spring-boot versions aligned with the parent chain"
else
  bad "version skew — see the FAIL lines above"
fi

# --- 4. Unit tests (domox-domain + domox-adapter + domox-webapp unit tests) ------------
step "4/7  Unit tests (domain + adapter + webapp unit)"
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

# --- 5. UI render check (headless browser; CSS/JS in list views) ------------------------
step "5/7  UI render check (headless browser)"
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

# --- 6. Webjar resource check (dev-instance guardrail) ---------------------------------
step "6/7  Webjar resources served correctly (not through Wicket)"
if curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/webjars/pdfjs-dist/5.4.624/build/pdf.min.mjs 2>/dev/null | grep -q 200; then
  ok "webjar resource accessible at /webjars/... (not /wicket/wicket/...)"
else
  bad "webjar resource not accessible — double-wicket URL regression? (app must be running on :8080)"
fi

# --- 5. Metamodel guardrail (boot-time DomainModelValidator veto) ----------------------
step "7/7  Metamodel guardrail (ValidateDomainModelIntegTest)"
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
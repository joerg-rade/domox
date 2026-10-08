#!/usr/bin/env bash
#
# DoMoX fast-check bundle (local guardrail).
#
# Usage:
#   bash scripts/check.sh            # schema drift + domain unit tests + metamodel guardrail
#   bash scripts/check.sh --quick    # schema-drift only (fast; used by the pre-commit hook)
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
step "1/3  Schema drift (DDL vs live domox-db)"
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

# --- 2. Domain unit tests (domox-domain + upstream domox-adapter) ----------------------
step "2/3  Unit tests (domox-domain + domox-adapter)"
LOG=/tmp/domox-check-unit.log
if mvn -B -pl domox-domain -am test >"$LOG" 2>&1; then
  ok "unit tests passed"
else
  bad "unit tests failed (log: $LOG — last lines: $(tail -5 "$LOG" | tr '\n' ' '))"
fi

# --- 3. Metamodel guardrail (boot-time DomainModelValidator veto) ----------------------
step "3/3  Metamodel guardrail (ValidateDomainModelIntegTest)"
LOG=/tmp/domox-check-meta.log
if mvn -B test -pl domox-webapp -Dtest=ValidateDomainModelIntegTest -am \
  -Dsurefire.failIfNoSpecifiedTests=false >"$LOG" 2>&1; then
  ok "metamodel guardrail passed"
else
  bad "metamodel guardrail failed (log: $LOG)"
fi

echo
[ $FAILED -eq 0 ] && echo "ALL CHECKS PASSED" || echo "FAILURES DETECTED — see logs above"
exit $FAILED
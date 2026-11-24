#!/usr/bin/env bash
#
# DoMoX mutation-testing guardrail: run pitest over domox-domain, then enforce the per-class floors.
#
# Usage:
#   bash scripts/check-mutation.sh                  # run pitest (~2 min), then check the floors
#   bash scripts/check-mutation.sh --no-run         # check the report already on disk (no Maven)
#   bash scripts/check-mutation.sh --allow-stale    # ... and accept a report older than domox-domain/src
#
# This bundles two commands with an ordering constraint that is easy to get wrong:
#
#   1. mvn -o -pl domox-domain verify -Pmutation     # writes domox-domain/target/pit-reports/mutations.xml
#   2. python3 scripts/pitest-floors.py --check      # fails when an extracted UML-layout collaborator
#                                                    # has dropped below its floor
#
# **The mutation profile is the LAST thing you run.**  pitest mutates the production classes but takes
# the *test* classes as its input, so editing a test afterwards leaves mutations.xml looking healthy
# while its numbers describe a suite that no longer exists.  That is an interval, not a moment: while
# the run is polling, domox-domain/src stays frozen.  This script stamps the run's START into
# domox-domain/target/pit-reports/.run-start and removes it once step 2 has run, so step 2 also refuses
# a report whose inputs changed *during* the run — an edit made while waiting for it is older than the
# report, and used to pass as current.  Step 2 exits 3 rather than reporting a pass; --allow-stale
# overrides that when you know better (e.g. a build cache restored the report with a checkout-fresh
# mtime).
#
# It is deliberately NOT part of scripts/check.sh or the default `mvn verify`: a full run takes
# minutes.  For the report's contents — whole-module table, per-class detail of what no test killed —
# run `python3 scripts/pitest-floors.py --table` / `--detail <Class>`.
#
# NOTE for a tool-driven run: ~2 min exceeds a ~30 s command cap, so run it through scripts/run-bg.sh,
# which starts it detached and waits a bounded time for it — one call, no `sleep` loop:
#   bash scripts/run-bg.sh /tmp/mutation.log --wait 28 -- bash scripts/check-mutation.sh
set -uo pipefail

cd "$(dirname "$0")/.."   # repo root

RUN=1
FLOOR_ARGS=()
for arg in "$@"; do
  case "$arg" in
    --no-run)      RUN=0 ;;
    --allow-stale) FLOOR_ARGS+=(--allow-stale) ;;
    -h|--help)     sed -n '2,32p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $arg (try --help)" >&2; exit 2 ;;
  esac
done

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required (scripts/pitest-floors.py)" >&2
  exit 2
fi

# --- Java/Maven environment (no .sdkmanrc in this repo; falls back to SDKMAN default JDK) --
# set -u must be lifted while sourcing sdkman-init.sh: it reads env vars that are unbound in a
# bare shell (e.g. SDKMAN_CANDIDATES_API) and would otherwise abort the whole script.
if [ "$RUN" -eq 1 ] && [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
  set +u
  # shellcheck disable=SC1091
  . "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk env >/dev/null 2>&1 || true
  set -u
fi

STAMP=domox-domain/target/pit-reports/.run-start

if [ "$RUN" -eq 1 ]; then
  echo "===> 1/2  mutation profile: mvn -o -pl domox-domain verify -Pmutation   (~2 min)"
  LOG=/tmp/domox-mutation.log
  # The staleness guard's reference time: domox-domain/src is frozen from here, so an edit made while
  # the run is polling is caught by step 2 as well (see the header).
  mkdir -p "$(dirname "$STAMP")"
  date +%s > "$STAMP"
  # -Dmaven.build.cache.enabled=false: the maven-build-cache extension restores cached pitest
  # executions and would hand back a report for a suite that is no longer there — the exact
  # failure the staleness guard exists to prevent, so it must not be short-circuited here.
  if ! mvn -B -o -pl domox-domain verify -Pmutation -Dmaven.build.cache.enabled=false >"$LOG" 2>&1; then
    rm -f "$STAMP"
    echo "    [FAIL] mutation profile failed (log: $LOG — last lines:)" >&2
    tail -20 "$LOG" | sed 's/^/    /' >&2
    exit 1
  fi
  echo "    [OK]   report written to domox-domain/target/pit-reports/mutations.xml"
fi

echo
echo "===> 2/2  per-class floors: python3 scripts/pitest-floors.py --check"
python3 scripts/pitest-floors.py --check ${FLOOR_ARGS[@]+"${FLOOR_ARGS[@]}"}
status=$?
# Only the run that wrote the stamp may clear it: a --no-run check keeps a stamp left by an interrupted
# run, so it still reports stale instead of falling back to the report's own mtime.
if [ "$RUN" -eq 1 ]; then rm -f "$STAMP"; fi

echo
case "$status" in
  0) echo "MUTATION FLOORS OK" ;;
  1) echo "MUTATION FLOOR VIOLATED — see the BELOW FLOOR lines above" >&2
     echo "investigate with: python3 scripts/pitest-floors.py --detail <Class>" >&2 ;;
  2) echo "NO REPORT — run without --no-run to produce one" >&2 ;;
  3) echo "STALE REPORT — the numbers describe an older test suite" >&2
     echo "re-run without --no-run, or pass --allow-stale if you know the report is current" >&2 ;;
esac
exit "$status"

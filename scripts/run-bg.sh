#!/usr/bin/env bash
#
# Run a long command DETACHED and wait a bounded time for it — in ONE shell call.
#
# Why: the shell tool caps a single call at ~30 s, so `mvn test` (~50 s), `check.sh` (~4 min) and
# `check-mutation.sh` (~2 min) die mid-run and their output is lost.  The documented workaround was
# `setsid <cmd> >log 2>&1 </dev/null &` followed by a series of `sleep 25; grep …` calls — and the
# sleeps are pure cost: one reviewed session spent 21 of its 178 shell calls on them.
#
# This does both halves at once: it starts the command in its own session (so it survives the shell
# that launched it), then waits up to --wait seconds (default 25, i.e. inside the cap) and either
# reports the result or says it is still running.
#
# Usage:
#   scripts/run-bg.sh <logfile> [--wait SECONDS] [--tail LINES] [--grep PATTERN] -- <command…>
#
#   scripts/run-bg.sh /tmp/x.log -- mvn -o -pl domox-domain test
#   scripts/run-bg.sh /tmp/x.log --wait 28 -- bash scripts/check.sh --units
#   scripts/run-bg.sh /tmp/x.log --grep 'Tests run|BUILD' -- mvn -B test -pl domox-webapp
#
# Output: the log path, then the last --tail lines (default 25) or the --grep matches, then the exit
# code when the command finished inside the window.  Exit codes: the command's own, or 124 when it is
# still running (like `timeout`), or 2 for a usage error.  A still-running command keeps writing to
# <logfile> and its exit code lands in <logfile>.exit, so the next call is a plain `tail` / `grep` /
# `cat <logfile>.exit` — never a `sleep` loop.
#
# The log is TRUNCATED at start (a stale tail reads as this run's output); the exit-code file is
# emptied for the same reason.
set -uo pipefail

WAIT=25
TAIL=25
PATTERN=""
LOG=""

usage() { sed -n '2,28p' "$0" | sed 's/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
  case "$1" in
    --wait)    WAIT="$2"; shift 2 ;;
    --tail)    TAIL="$2"; shift 2 ;;
    --grep)    PATTERN="$2"; shift 2 ;;
    --)        shift; break ;;
    -h|--help) usage; exit 0 ;;
    -*)        echo "run-bg: unknown option: $1" >&2; usage >&2; exit 2 ;;
    *)         if [ -n "$LOG" ]; then echo "run-bg: only one log file argument is allowed" >&2; exit 2; fi
               LOG="$1"; shift ;;
  esac
done

if [ -z "$LOG" ]; then echo "run-bg: missing <logfile>" >&2; usage >&2; exit 2; fi
if [ $# -eq 0 ]; then echo "run-bg: missing '-- <command>'" >&2; usage >&2; exit 2; fi
case "$WAIT" in ''|*[!0-9]*) echo "run-bg: --wait needs a whole number of seconds" >&2; exit 2 ;; esac

# --- Java/Maven environment (no .sdkmanrc in this repo; falls back to SDKMAN default JDK) ---------
# `run-app.sh` and `verify-boot.sh` do the same; a bare `mvn` here would not find the JDK.
if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
  set +u
  # shellcheck disable=SC1091
  . "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk env >/dev/null 2>&1 || true
  set -u
fi

mkdir -p "$(dirname "$LOG")" 2>/dev/null || true
EXITFILE="$LOG.exit"
: > "$LOG"          # truncate: a stale tail reads as this run's output
: > "$EXITFILE"     # empty means "still running"; the command writes its exit code here when done

echo "run-bg: starting detached (own session, survives this shell) → $LOG"
echo "run-bg:   $*"
# The command runs inside `sh -c` so its exit status can be recorded even when we stop waiting for it.
# setsid puts it in a new session; it is deliberately NOT `setsid -w` (that would wait, and this shell
# must stay free to report "still running" and exit).  The log and exit-code paths go in as positional
# parameters: single-quoted, the inner shell is the one that would expand them, and it has no variables.
setsid sh -c 'log="$1"; exitfile="$2"; shift 2; "$@" >"$log" 2>&1 </dev/null; echo $? >"$exitfile"' \
  _ "$LOG" "$EXITFILE" "$@" &
disown 2>/dev/null || true

DEADLINE=$((SECONDS + WAIT))
while [ ! -s "$EXITFILE" ]; do
  if [ "$SECONDS" -ge "$DEADLINE" ]; then
    echo "run-bg: STILL RUNNING after ${WAIT}s — poll it (no sleep loop needed):"
    echo "run-bg:   tail -40 $LOG"
    echo "run-bg:   cat $EXITFILE        # empty until it finishes, then its exit code"
    [ -n "$PATTERN" ] && echo "run-bg:   grep -E '$PATTERN' $LOG"
    exit 124
  fi
  sleep 0.5
done

STATUS="$(cat "$EXITFILE")"
echo "run-bg: finished — exit $STATUS (log: $LOG)"
if [ -n "$PATTERN" ]; then
  echo "run-bg: --- grep -E '$PATTERN' $LOG ---"
  grep -E "$PATTERN" "$LOG" || echo "run-bg: (no line matched)"
else
  echo "run-bg: --- tail -$TAIL $LOG ---"
  tail -"$TAIL" "$LOG"
fi
exit "$STATUS"

#!/usr/bin/env bash
#
# Wait (bounded) for a command launched earlier to finish — in ONE short shell call.
#
# Why: the shell tool caps a single call at ~30 s, so `sleep 45; tail -50 /tmp/x.log` is killed and its
# output is LOST, which reads like the task failed.  Once a long run is already going (`run-bg.sh …`, or
# a `setsid` from an earlier call), the only question left is "is it done yet?" — this answers it in one
# call that can never exceed the cap, and prints the tail (or the exit code) when it is.
#
# Companion to scripts/run-bg.sh: that one starts the run and waits inside the same call; this one waits
# on a run that is already going.  Never a bare `sleep` — that is what loses the output.
#
# Usage:
#   scripts/wait-for-bg.sh <logfile> [--wait 20] [--tail 15] [--grep PATTERN]
#
#   scripts/wait-for-bg.sh /tmp/domox-check.log
#   scripts/wait-for-bg.sh /tmp/domox-check.log --wait 25 --tail 40
#   scripts/wait-for-bg.sh /tmp/x.log --grep 'Tests run|BUILD'
#
# <logfile>.exit holds the command's exit code once it finished (written by run-bg.sh, or by a `setsid`
# wrapper that echoes $?), and is empty while it runs.  --wait is capped at 25 s so the call stays under
# the tool's 30 s cap.
#
# Output: the exit code and the tail (or the --grep matches), or "still running" plus the last progress
# line (check.sh prints "===> n/11 …") and the poll hint.  Exit codes: the command's own; 124 when still
# running (like `timeout`); 2 for a usage error.
set -uo pipefail

WAIT=20
TAIL=15
PATTERN=""
LOG=""

usage() { sed -n '2,27p' "$0" | sed 's/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
  case "$1" in
    --wait)    WAIT="$2"; shift 2 ;;
    --tail)    TAIL="$2"; shift 2 ;;
    --grep)    PATTERN="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    -*)        echo "wait-for-bg: unknown option: $1" >&2; usage >&2; exit 2 ;;
    *)         if [ -n "$LOG" ]; then echo "wait-for-bg: only one log file argument is allowed" >&2; exit 2; fi
               LOG="$1"; shift ;;
  esac
done

if [ -z "$LOG" ]; then echo "wait-for-bg: missing <logfile>" >&2; usage >&2; exit 2; fi
case "$WAIT" in ''|*[!0-9]*) echo "wait-for-bg: --wait needs a whole number of seconds" >&2; exit 2 ;; esac
if [ "$WAIT" -gt 25 ]; then
  echo "wait-for-bg: --wait capped at 25s (the shell tool kills a call at ~30s, and the output is lost)"
  WAIT=25
fi

EXITFILE="$LOG.exit"
if [ ! -f "$LOG" ] && [ ! -f "$EXITFILE" ]; then
  echo "wait-for-bg: no $LOG — was the run started? (see scripts/run-bg.sh)" >&2
  exit 2
fi

# The last progress line, if the run prints one (check.sh prints "===> n/11 …") — the answer to "how far?".
progress() { grep -E '^===> [0-9]+/[0-9]+ ' "$LOG" 2>/dev/null | tail -1; }

DEADLINE=$((SECONDS + WAIT))
while [ ! -s "$EXITFILE" ]; do
  if [ "$SECONDS" -ge "$DEADLINE" ]; then
    echo "wait-for-bg: STILL RUNNING after ${WAIT}s — call the same command again, no sleep:"
    echo "wait-for-bg:   bash scripts/wait-for-bg.sh $LOG"
    line="$(progress)"
    [ -n "$line" ] && echo "wait-for-bg:   $line"
    exit 124
  fi
  sleep 0.5
done

STATUS="$(cat "$EXITFILE")"
echo "wait-for-bg: finished — exit $STATUS (log: $LOG)"
if [ -n "$PATTERN" ]; then
  echo "wait-for-bg: --- grep -E '$PATTERN' $LOG ---"
  grep -E "$PATTERN" "$LOG" || echo "wait-for-bg: (no line matched)"
else
  echo "wait-for-bg: --- tail -$TAIL $LOG ---"
  tail -"$TAIL" "$LOG"
fi
exit "$STATUS"

#!/usr/bin/env bash
#
# Stop whatever is listening on the given TCP ports.
#
# Why this exists: the obvious command is wrong.  `pkill -f <pattern>` also matches the shell that is
# running the pkill itself — its own command line contains the pattern — so it SIGTERMs the caller and
# the command's output is lost.  That happened twice in one session (killing a stub server and a
# polling loop).  Kill by port instead.
#
# Usage:
#   scripts/stop-app.sh            # default port 8080
#   scripts/stop-app.sh 8082 9999
set -uo pipefail

PORTS=("$@")
[ ${#PORTS[@]} -eq 0 ] && PORTS=(8080)

rc=0
for port in "${PORTS[@]}"; do
  pids=$(ss -ltnp 2>/dev/null | grep ":$port " | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | sort -u)
  if [ -z "$pids" ]; then
    echo "port $port: nothing listening"
    continue
  fi
  for pid in $pids; do
    if kill "$pid" 2>/dev/null; then
      echo "port $port: killed pid $pid"
    else
      echo "port $port: could not kill pid $pid (already gone?)"
      rc=1
    fi
  done
done
exit $rc

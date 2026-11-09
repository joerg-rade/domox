#!/usr/bin/env bash
#
# Bring up a THROWAWAY app instance — its own Postgres, on a free port — and wait until it is serving.
#
# Why: verifying anything against a live instance otherwise means four hand-run steps (make the DB, pick
# a port, install, boot detached) plus a poll loop, and the poll loop is what the agent's shell budget is
# worst at.  This does all of it in one call and prints the log path.
#
#   scripts/verify-boot.sh                    # throwaway DB on :5433, app on :8082
#   scripts/verify-boot.sh --port 8090
#   scripts/verify-boot.sh --stop             # stop the app and remove the DB
#
# NEVER point this at the shared `domox-db`: the shared domox.SEQ_GEN sequence makes a second instance
# fail with `seq_gen already exists`.  A dev instance on :8080 and its own DB are left untouched.
#
# The upstream modules are only rebuilt when their sources are newer than the installed jar, so the
# common case (boot to look at a diagram) skips the ~16 s install and finishes inside the shell budget.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DB=domox-db-verify
DB_PORT=5433
PORT=8082
WAIT=25
LOG="${DOMOX_VERIFY_LOG:-/tmp/domox-verify.log}"
STOP=0
while [ $# -gt 0 ]; do
  case "$1" in
    --port)    PORT="$2"; shift 2 ;;
    --db-port) DB_PORT="$2"; shift 2 ;;
    --wait)    WAIT="$2"; shift 2 ;;
    --stop)    STOP=1; shift ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [ "$STOP" = 1 ]; then
  bash scripts/stop-app.sh "$PORT" 2>/dev/null
  docker rm -f "$DB" >/dev/null 2>&1
  echo "verify-boot: stopped the app on :$PORT and removed $DB"
  exit 0
fi

if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
  set +u
  # shellcheck disable=SC1091
  . "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk env >/dev/null 2>&1 || true
  set -u
fi

# --- already up?  re-running must be harmless --------------------------------------------------
if curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/" 2>/dev/null | grep -qE '^[23]'; then
  echo "verify-boot: an instance is already serving on :$PORT — reusing it"
  echo "verify-boot: READY  http://localhost:$PORT"
  echo "verify-boot: stop with  bash scripts/verify-boot.sh --stop --port $PORT"
  exit 0
fi

# --- the throwaway database --------------------------------------------------------------------
if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$DB"; then
  echo "verify-boot: reusing the running $DB"
else
  docker rm -f "$DB" >/dev/null 2>&1
  if ! docker run -d --name "$DB" -p "$DB_PORT:5432" \
      -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=postgres \
      postgres:latest >/dev/null; then
    echo "ERROR: could not start $DB (is docker running?)" >&2
    exit 1
  fi
  for _ in $(seq 1 30); do
    docker exec "$DB" pg_isready -U postgres >/dev/null 2>&1 && break
    sleep 1
  done
fi

# --- the upstream modules, only when stale -----------------------------------------------------
JAR="$(ls -1 "$HOME"/.m2/repository/domox/domox-domain/*/domox-domain-*.jar 2>/dev/null | head -1)"
if [ -n "$JAR" ] && [ -z "$(find domox-domain/src domox-adapter/src -type f -newer "$JAR" 2>/dev/null | head -1)" ]; then
  echo "verify-boot: domox-domain/domox-adapter are up to date — skipping the install"
else
  echo "verify-boot: installing domox-domain, domox-adapter ..."
  if ! mvn -q -B install -DskipTests -pl domox-domain,domox-adapter; then
    echo "ERROR: installing the upstream modules failed" >&2
    exit 1
  fi
fi

# --- boot, detached, with the repo root as the working directory --------------------------------
: > "$LOG"
setsid mvn -pl domox-webapp spring-boot:run \
  -Dspring-boot.run.workingDirectory="$ROOT" \
  -Dspring-boot.run.arguments="--server.port=$PORT --spring.datasource.url=jdbc:postgresql://localhost:$DB_PORT/postgres" \
  >"$LOG" 2>&1 </dev/null &

for _ in $(seq 1 "$WAIT"); do
  if grep -q 'Started Application' "$LOG" 2>/dev/null; then
    echo "verify-boot: READY  http://localhost:$PORT   (log: $LOG)"
    echo "verify-boot: stop with  bash scripts/verify-boot.sh --stop --port $PORT"
    exit 0
  fi
  if grep -qE 'APPLICATION FAILED TO START|BUILD FAILURE' "$LOG" 2>/dev/null; then
    echo "ERROR: the app did not start — last lines of $LOG:" >&2
    tail -20 "$LOG" >&2
    exit 1
  fi
  sleep 1
done

# Still booting: the install above probably ran.  The app is detached, so it will finish on its own.
echo "verify-boot: STILL BOOTING after ${WAIT}s — tail $LOG (then: http://localhost:$PORT)"
exit 0

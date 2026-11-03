#!/usr/bin/env bash
#
# Boot the app the way .clinerules/commands.md requires — in one step.
#
# Two traps this removes (both cost a cycle in one session):
#   1. `spring-boot:run -pl domox-webapp` resolves domox-domain / domox-adapter from ~/.m2, NOT from
#      the reactor.  After changing either you MUST install them first, or the boot dies with
#      NoClassDefFoundError / AbstractMethodError for a class that compiles fine.
#      Batching `mvn install` and `mvn spring-boot:run` in one shell call does NOT help: the two run
#      concurrently and the app starts against a half-written jar — which looks like "my change had
#      no effect" (a live check reported 46 nodes when the code produced 6).
#   2. EclipseLink resolves `eclipselink.create-ddl-jdbc-file-name`
#      (domox-webapp/create-tables.sql) relative to the WORKING DIRECTORY, so the app must start with
#      the REPO ROOT as its CWD — otherwise it dies at startup with EclipseLink-7018 FileNotFound.
#
# Usage:
#   scripts/run-app.sh                       # port 8080, dev DB from application.yml
#   scripts/run-app.sh --server.port=8082 --spring.datasource.url=jdbc:postgresql://localhost:5433/postgres
#   scripts/run-app.sh --domox.rdf.endpoint-url=http://127.0.0.1:9999/sparql
#
# NOTE on overrides: a **non-empty** `--key=value` overrides application.yml, but an **empty** value
# (`--domox.rdf.endpoint-url=`) does NOT — verified: the app keeps the YAML value.  To force the
# bundled-ontology (local) mode, set `domox.rdf.endpoint-url` to "" (or " ") *in application.yml*
# instead; `RdfProperties.isRemote()` treats blank/whitespace as local.
#
# The app is started DETACHED in its own session, so it survives the shell that launched it (an
# ordinary `&` does not: killing the caller's process group takes the app with it).  The script
# prints the PID and the log path and returns immediately.
#   follow: tail -f /tmp/domox-boot.log      stop: scripts/stop-app.sh 8082
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
LOG="${DOMOX_BOOT_LOG:-/tmp/domox-boot.log}"

if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
  set +u
  # shellcheck disable=SC1091
  . "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk env >/dev/null 2>&1 || true
  set -u
fi

# 1. Build + install the upstream modules FIRST, and finish before booting (trap 1).
echo "building + installing domox-domain, domox-adapter ..."
if ! mvn -q -B install -DskipTests -pl domox-domain,domox-adapter; then
  echo "ERROR: installing the upstream modules failed — not booting." >&2
  exit 1
fi

# 2. Boot detached, with the REPO ROOT as the working directory (trap 2).
# An ARRAY, not a string: `-Dspring-boot.run.arguments=…` holds spaces and must reach Maven as ONE
# word (an unquoted "$ARGS" splits it and Maven prints its usage instead of booting).
MVN_ARGS=(-Dspring-boot.run.workingDirectory="$ROOT")
if [ $# -gt 0 ]; then
  MVN_ARGS+=("-Dspring-boot.run.arguments=$*")
fi

: > "$LOG"
setsid mvn -pl domox-webapp spring-boot:run "${MVN_ARGS[@]}" >"$LOG" 2>&1 < /dev/null &
PID=$!

echo "started (pid $PID)"
echo "log:     $LOG"
echo "follow:  tail -f $LOG     (expect 'Validation PASSED' then 'Started Application')"
echo "stop:    scripts/stop-app.sh <port>"

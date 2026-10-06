#!/usr/bin/env bash
#
# Schema-drift guardrail for DoMoX.
#
# Why this exists:
#   EclipseLink runs with eclipselink.ddl-generation=create-tables, which CREATES missing
#   tables but NEVER ALTERS existing ones.  Adding a JPA field to an entity therefore desyncs
#   the live dev DB from the regenerated DDL (domox-webapp/create-tables.sql) WITHOUT any
#   startup error -- the failure only surfaces later at runtime as "column X does not exist".
#
#   This bites hard for a TABLE_PER_CLASS base like Candidate: a field added to it (e.g. the
#   hopDepth migration) must be ALTERed into EVERY per-type table (classcdd, propertycdd,
#   actioncdd, associationcdd, packagecdd, parametercdd), not just the base.
#
# What it does:
#   Diffs the columns declared in the latest generated create-tables.sql against the live dev
#   database (in docker) and exits non-zero if any table is missing a column the DDL expects.
#
# Usage:
#   bash scripts/check-schema-drift.sh
#
#   Configurable via env vars (defaults match the local dev setup):
#     DOMOX_DDL          path to the generated DDL     (default: domox-webapp/create-tables.sql)
#     DOMOX_DB_CONTAINER docker container name         (default: domox-db)
#     DOMOX_DB_SCHEMA    Postgres schema to compare    (default: domox)
#     DOMOX_DB_USER      psql user                     (default: postgres)
#
# Depends on:
#   - docker container with Postgres running (postgres/postgres)
#   - domox-webapp/create-tables.sql regenerated (run the app once after entity changes)
#
# NOTE: this is a dev-DB guardrail, not a CI check.  It diffs against a GROWN database; against
# a fresh/empty schema every table would be reported as missing columns.  In CI the equivalent
# check would have to boot the app against a Postgres service so EclipseLink regenerates the
# tables first.
set -uo pipefail

DDL="${DOMOX_DDL:-domox-webapp/create-tables.sql}"
CONTAINER="${DOMOX_DB_CONTAINER:-domox-db}"
SCHEMA="${DOMOX_DB_SCHEMA:-domox}"
PSQL_USER="${DOMOX_DB_USER:-postgres}"

[ -f "$DDL" ] || { echo "ERROR: $DDL not found (run the app once to regenerate it)"; exit 2; }
docker ps --format '{{.Names}}' | grep -qx "$CONTAINER" \
  || { echo "ERROR: container $CONTAINER is not running"; exit 2; }

# Extract "<table>\t<lowercase column names>" pairs from the DDL.
# - fields are separated by commas (types never contain commas)
# - column list starts at the FIRST "(" after the table name
# - drop the trailing "PRIMARY KEY (...)" region before splitting
# - DDL columns are UPPERCASE; Postgres folds stored identifiers to lowercase, so lowercase
#   them here to match information_schema cleanly
ddl_columns() {
  awk '
    /^CREATE TABLE / {
      line=$0
      sub(/ *PRIMARY KEY.*/, "", line)
      tname=line
      sub(/\(.*/, "", tname)            # strip from first "(" -> "CREATE TABLE domox.ActionCdd "
      sub(/^CREATE TABLE +/, "", tname)
      sub(/ +$/, "", tname)
      sub(/^.*\./, "", tname)           # schema prefix -> bare table name
      cols=""
      if (match(line, /\(.*/)) {
        body=substr(line, RSTART + 1)
        sub(/[)]+$/, "", body)          # drop trailing ")" (empty tables like "domox.Candidate ()")
        n=split(body, chunks, ",")
        for (i = 1; i <= n; i++) {
          chunk=chunks[i]
          sub(/^ +/, "", chunk)
          sub(/ +.*$/, "", chunk)       # first token of each chunk = column name
          if (chunk != "") {
            chunk=tolower(chunk)
            cols = (cols == "" ? chunk : cols "\t" chunk)
          }
        }
      }
      print tname "\t" cols
    }' "$DDL"
}

mapfile -t drift < <(
  while IFS=$'\t' read -r table cols; do
    [ -n "$table" ] || continue
    [ -n "$cols" ] || continue          # skip tables with no columns (empty TABLE_PER_CLASS base)

    table_lc=$(echo "$table" | tr '[:upper:]' '[:lower:]')
    live_cols=$(docker exec "$CONTAINER" psql -U "$PSQL_USER" -d postgres -tA \
        -c "SELECT column_name FROM information_schema.columns WHERE table_schema='$SCHEMA' AND table_name='$table_lc'") \
      || { echo "ERROR: could not query columns for $table"; exit 2; }

    missing=""
    for col in $cols; do
      if ! echo "$live_cols" | grep -qx "$col"; then
        missing="$missing $col"
      fi
    done
    if [ -n "$missing" ]; then
      printf 'TABLE %s is missing column(s):%s\n' "$table" "$missing"
    fi
  done < <(ddl_columns)
)

if [ ${#drift[@]} -gt 0 ]; then
  echo "SCHEMA DRIFT DETECTED against $DDL:"
  printf '  %s\n' "${drift[@]}"
  echo
  echo "Fix by ALTERing the affected table(s), e.g.:"
  echo "  ALTER TABLE domox.<table> ADD COLUMN <col> <TYPE> [NOT NULL DEFAULT ...];"
  echo
  echo "Remember: a field added to the TABLE_PER_CLASS base Candidate must be added to EVERY"
  echo "per-type table (classcdd, propertycdd, actioncdd, associationcdd, packagecdd, parametercdd)."
  exit 1
fi

echo "OK: live DB schema matches $DDL (no missing columns)."
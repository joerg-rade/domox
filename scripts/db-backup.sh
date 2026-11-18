#!/usr/bin/env bash
#
# Export the DoMoX analysis database into a timestamped, self-describing backup directory.
#
# Why this exists:
#   The candidate review decisions (domox.review) and the sub-domain partition (domox.subdomain
#   plus domox.classcdd.subdomain_id) live ONLY in Postgres.  They are the product of hours of
#   review and cannot be regenerated from the corpus, so losing the container loses the analysis.
#   This script takes a consistent snapshot and, next to the binary dump, writes the decisions out
#   as readable TSV — so the analysis survives even with no Postgres to restore into.
#
# Usage:
#   bash scripts/db-backup.sh                  # -> backups/domox-<timestamp>/
#   bash scripts/db-backup.sh --restore-check  # also restore into a scratch DB and diff the counts
#   bash scripts/db-backup.sh --list           # list the backups taken so far
#
# Configurable via env vars (defaults match the local dev setup):
#   DOMOX_DB_CONTAINER  docker container name        (default: domox-db)
#   DOMOX_DB_NAME       database name                (default: postgres)
#   DOMOX_DB_USER       psql user                    (default: postgres)
#   DOMOX_DB_SCHEMA     schema to dump               (default: domox)
#   DOMOX_BACKUP_DIR    where backups are written    (default: backups)
#
# The dump is taken with the CONTAINER's own pg_dump, so the client major version always matches
# the server (a host pg_dump of a different major refuses a v18 server).  `public` is excluded on
# purpose: it holds only the stale, empty `tables.sql` legacy scaffolding.  Pass --all to include
# the whole database instead of just the domox schema.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

CONTAINER="${DOMOX_DB_CONTAINER:-domox-db}"
DBNAME="${DOMOX_DB_NAME:-postgres}"
DBUSER="${DOMOX_DB_USER:-postgres}"
SCHEMA="${DOMOX_DB_SCHEMA:-domox}"
OUT_ROOT="${DOMOX_BACKUP_DIR:-backups}"

RESTORE_CHECK=0
LIST=0
ALL=0
while [ $# -gt 0 ]; do
  case "$1" in
    --restore-check) RESTORE_CHECK=1; shift ;;
    --list)          LIST=1; shift ;;
    --all)           ALL=1; shift ;;
    --dir)           OUT_ROOT="$2"; shift 2 ;;
    -h|--help)       sed -n '2,26p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [ "$LIST" = 1 ]; then
  if compgen -G "$OUT_ROOT/domox-*" >/dev/null; then
    du -sh "$OUT_ROOT"/domox-* 2>/dev/null
  else
    echo "no backups in $OUT_ROOT"
  fi
  exit 0
fi

docker ps --format '{{.Names}}' | grep -qx "$CONTAINER" \
  || { echo "ERROR: container $CONTAINER is not running (docker compose up -d db)" >&2; exit 2; }
docker exec "$CONTAINER" pg_dump --version >/dev/null 2>&1 \
  || { echo "ERROR: pg_dump not available in $CONTAINER" >&2; exit 2; }

# Scope: just the analysis schema, or the whole database with --all.
SCOPE=(-n "$SCHEMA")
SCOPE_DESC="schema '$SCHEMA'"
if [ "$ALL" = 1 ]; then
  SCOPE=()
  SCOPE_DESC="whole database '$DBNAME'"
fi

TS="$(date +%Y%m%d-%H%M%S)"
DEST="$OUT_ROOT/domox-$TS"
mkdir -p "$DEST"

echo "db-backup: $CONTAINER / $DBNAME — dumping $SCOPE_DESC ..."
if ! docker exec "$CONTAINER" pg_dump -U "$DBUSER" -d "$DBNAME" "${SCOPE[@]}" \
      --no-owner --no-privileges -Fc > "$DEST/domox-schema.dump.part"; then
  echo "ERROR: pg_dump (custom format) failed — $DEST is incomplete, discard it" >&2
  exit 1
fi
[ -s "$DEST/domox-schema.dump.part" ] || { echo "ERROR: the dump is empty" >&2; exit 1; }
mv "$DEST/domox-schema.dump.part" "$DEST/domox-schema.dump"

# A plain-SQL copy as well: restorable with nothing but psql, and greppable by eye.
if ! docker exec "$CONTAINER" pg_dump -U "$DBUSER" -d "$DBNAME" "${SCOPE[@]}" \
      --no-owner --no-privileges -Fp | gzip -9 > "$DEST/domox-schema.sql.gz.part"; then
  echo "ERROR: pg_dump (plain SQL) failed — $DEST is incomplete, discard it" >&2
  exit 1
fi
mv "$DEST/domox-schema.sql.gz.part" "$DEST/domox-schema.sql.gz"
echo "db-backup: dumps written"

# --- the analysis itself, as readable TSV --------------------------------------------------------
# The dumps above restore the analysis; these files let you READ it without a database.  Every row
# is a decision or an assignment, joined to the PER-TYPE candidate tables — never to
# domox.candidate, which is a TABLE_PER_CLASS base and therefore empty by design (the runbook's
# "data is in the per-type tables" trap).
psql_q() { docker exec "$CONTAINER" psql -U "$DBUSER" -d "$DBNAME" -tA -c "$1"; }
tsv() {   # $1 = SQL, $2 = destination file
  docker exec "$CONTAINER" psql -U "$DBUSER" -d "$DBNAME" \
    -c "COPY ($1) TO STDOUT WITH (FORMAT csv, DELIMITER E'\t', HEADER true);" > "$2"
}

REVIEWS_SQL="$(cat <<'SQL'
SELECT r.candidate_id AS candidate_id,
       coalesce(cl.candidatename, ac.candidatename, pr.candidatename, ag.candidatename) AS name,
       coalesce(cl.candidatetype, ac.candidatetype, pr.candidatetype, ag.candidatetype) AS type,
       r.status AS status,
       coalesce(r.rationale, '') AS rationale,
       r.username AS reviewer,
       r.createdat AS decided_at
FROM domox.review r
LEFT JOIN domox.classcdd       cl ON cl.id = r.candidate_id
LEFT JOIN domox.actioncdd      ac ON ac.id = r.candidate_id
LEFT JOIN domox.propertycdd    pr ON pr.id = r.candidate_id
LEFT JOIN domox.associationcdd ag ON ag.id = r.candidate_id
ORDER BY r.candidate_id
SQL
)"

SUBDOMAINS_SQL="$(cat <<'SQL'
SELECT s.id AS subdomain_id, s.name AS subdomain, s.classification AS classification,
       count(c.id) AS candidates
FROM domox.subdomain s
LEFT JOIN domox.classcdd c ON c.subdomain_id = s.id
GROUP BY s.id, s.name, s.classification
ORDER BY s.id
SQL
)"

ASSIGNMENTS_SQL="$(cat <<'SQL'
SELECT c.id AS candidate_id, c.candidatename AS name,
       s.name AS subdomain, s.classification AS classification
FROM domox.classcdd c
JOIN domox.subdomain s ON s.id = c.subdomain_id
ORDER BY s.id, c.id
SQL
)"

tsv "$REVIEWS_SQL"     "$DEST/analysis-reviews.tsv"
tsv "$SUBDOMAINS_SQL"  "$DEST/analysis-subdomains.tsv"
tsv "$ASSIGNMENTS_SQL" "$DEST/analysis-assignments.tsv"
echo "db-backup: analysis TSVs written"

# --- counts, captured at dump time ---------------------------------------------------------------
COUNTS_SQL="$(cat <<'SQL'
SELECT 'review (decisions)' AS metric, count(*)::text AS value FROM domox.review
UNION ALL SELECT 'review APPROVED', count(*)::text FROM domox.review WHERE status='APPROVED'
UNION ALL SELECT 'review REJECTED', count(*)::text FROM domox.review WHERE status='REJECTED'
UNION ALL SELECT 'subdomain', count(*)::text FROM domox.subdomain
UNION ALL SELECT 'classcdd', count(*)::text FROM domox.classcdd
UNION ALL SELECT 'classcdd assigned to a subdomain', count(*)::text FROM domox.classcdd WHERE subdomain_id IS NOT NULL
UNION ALL SELECT 'actioncdd', count(*)::text FROM domox.actioncdd
UNION ALL SELECT 'propertycdd', count(*)::text FROM domox.propertycdd
UNION ALL SELECT 'associationcdd', count(*)::text FROM domox.associationcdd
UNION ALL SELECT 'rulematch', count(*)::text FROM domox.rulematch
UNION ALL SELECT 'sentence', count(*)::text FROM domox.sentence
UNION ALL SELECT 'document', count(*)::text FROM domox.document
ORDER BY 1
SQL
)"
docker exec "$CONTAINER" psql -U "$DBUSER" -d "$DBNAME" -c "$COUNTS_SQL" > "$DEST/counts.txt" 2>&1
echo "db-backup: counts written"

# --- prove the dump is readable, then describe it ------------------------------------------------
# A backup nobody has read back is not a backup: pg_restore --list parses the whole archive and
# fails loudly on a truncated or corrupt file.  (--restore-check below goes further and actually
# replays it.)
if ! docker exec -i "$CONTAINER" pg_restore --list < "$DEST/domox-schema.dump" \
      > "$DEST/domox-schema.toc.txt" 2>"$DEST/domox-schema.toc.err"; then
  echo "ERROR: pg_restore cannot read the dump — the backup is NOT valid" >&2
  exit 1
fi
# Prefer the archive's own count from the pg_restore header (";     TOC Entries: 92").  Counting
# the lines that merely contain ';' instead over-reports, because the header block is itself
# several such lines.
TOC_ENTRIES="$(grep -oE '^; +TOC Entries: +[0-9]+' "$DEST/domox-schema.toc.txt" | grep -oE '[0-9]+' | head -1)"
[ -n "$TOC_ENTRIES" ] || TOC_ENTRIES="$(grep -c ';' "$DEST/domox-schema.toc.txt" 2>/dev/null || echo 0)"
[ "${TOC_ENTRIES:-0}" -gt 0 ] \
  || { echo "ERROR: the dump's table of contents is empty" >&2; exit 1; }

DUMP_SHA="$(sha256sum "$DEST/domox-schema.dump" | awk '{print $1}')"
SQL_SHA="$(sha256sum "$DEST/domox-schema.sql.gz" | awk '{print $1}')"
SERVER_VER="$(psql_q 'SHOW server_version;')"
SCHEMA_SIZE="$(psql_q "SELECT pg_size_pretty(sum(pg_total_relation_size(quote_ident(schemaname)||'.'||quote_ident(tablename)))) FROM pg_tables WHERE schemaname='$SCHEMA';")"
DUMP_SIZE="$(du -h "$DEST/domox-schema.dump" | cut -f1)"

cat > "$DEST/README.md" <<EOF
# DoMoX analysis backup — $TS

Taken from the \`$CONTAINER\` container (PostgreSQL $SERVER_VER), database \`$DBNAME\`,
scope: **$SCOPE_DESC** ($SCHEMA_SIZE of table data).

This directory is a self-contained snapshot of the candidate-review analysis: the review
decisions, the sub-domain partition, and every table those rest on.

## Contents

| File | What it is |
|---|---|
| \`domox-schema.dump\` | PostgreSQL **custom-format** dump ($DUMP_SIZE) — the restorable backup |
| \`domox-schema.sql.gz\` | The same content as **plain SQL**, gzipped — restorable with nothing but psql |
| \`domox-schema.toc.txt\` | The dump's table of contents ($TOC_ENTRIES entries) — proof it is readable |
| \`analysis-reviews.tsv\` | Every review decision: candidate id, name, type, status, rationale, reviewer, timestamp |
| \`analysis-subdomains.tsv\` | The sub-domains, with classification and candidate count |
| \`analysis-assignments.tsv\` | The class candidates assigned to each sub-domain |
| \`counts.txt\` | Row counts captured at dump time |
| \`README.md\` | This manifest |

Checksums (verify with \`sha256sum -c\`):

    $DUMP_SHA  domox-schema.dump
    $SQL_SHA  domox-schema.sql.gz

## Restore

Into the running container. Use a FRESH database — the dump creates the \`$SCHEMA\` schema and
does not drop what is already there:

    docker exec -i $CONTAINER psql -U $DBUSER -d postgres -c 'CREATE DATABASE domox_restored;'
    docker exec -i $CONTAINER pg_restore -U $DBUSER -d domox_restored --no-owner --no-privileges < domox-schema.dump

From the plain SQL instead (no pg_restore, and portable across pg_dump major versions):

    gunzip -c domox-schema.sql.gz | docker exec -i $CONTAINER psql -U $DBUSER -d domox_restored

## Notes

- \`public\` is NOT included: it holds only the stale, empty \`tables.sql\` legacy scaffolding.
  Re-take with \`--all\` if you want the whole database.
- The dumps use \`--no-owner --no-privileges\`, so they restore under any role.
- \`domox.candidate\` (the TABLE_PER_CLASS base) is EMPTY by design — the real rows live in
  \`classcdd\` / \`actioncdd\` / \`propertycdd\` / \`associationcdd\`. Query those, never the base.
- \`subdomain.classification\` is a column added after that table was first created, and
  EclipseLink's \`create-tables\` never ALTERs. A database older than that change needs:
  \`ALTER TABLE domox.SubDomain ADD COLUMN classification VARCHAR(255) NOT NULL DEFAULT 'UNCLASSIFIED';\`
- Re-take at any time with: \`bash scripts/db-backup.sh\`
EOF

# --- optional: replay the dump and diff the counts ------------------------------------------------
if [ "$RESTORE_CHECK" = 1 ]; then
  SCRATCH="domox_restore_check_$$"   # unique, so a pre-existing database is never dropped
  echo "db-backup: restoring into $SCRATCH to prove the dump ..."
  docker exec "$CONTAINER" psql -U "$DBUSER" -d postgres -q -c "CREATE DATABASE $SCRATCH;" \
    || { echo "ERROR: could not create the scratch database $SCRATCH" >&2; exit 1; }
  if ! docker exec -i "$CONTAINER" pg_restore -U "$DBUSER" -d "$SCRATCH" \
        --no-owner --no-privileges < "$DEST/domox-schema.dump" \
        > "$DEST/restore-check.log" 2>&1; then
    echo "ERROR: the restore reported errors — see $DEST/restore-check.log" >&2
    exit 1
  fi
  rc=0
  for t in review subdomain classcdd actioncdd propertycdd associationcdd rulematch sentence document; do
    live="$(psql_q "SELECT count(*) FROM $SCHEMA.$t")"
    back="$(docker exec "$CONTAINER" psql -U "$DBUSER" -d "$SCRATCH" -tA -c "SELECT count(*) FROM $SCHEMA.$t")"
    if [ "$live" = "$back" ]; then
      printf '  %-16s %8s  ok\n' "$t" "$live"
    else
      printf '  %-16s live=%s restored=%s  MISMATCH\n' "$t" "$live" "$back"
      rc=1
    fi
  done
  docker exec "$CONTAINER" psql -U "$DBUSER" -d postgres -q -c "DROP DATABASE $SCRATCH;" >/dev/null 2>&1
  [ "$rc" = 0 ] || { echo "ERROR: the restored copy does not match the live database" >&2; exit 1; }
  echo "db-backup: restore check PASSED — the dump reproduces the live counts"
fi

ln -sfn "domox-$TS" "$OUT_ROOT/latest"

echo
echo "db-backup: OK  ->  $DEST"
echo "db-backup: size $(du -sh "$DEST" | cut -f1), latest -> $OUT_ROOT/latest"
echo "db-backup: readable analysis in analysis-reviews.tsv / analysis-subdomains.tsv / analysis-assignments.tsv"
[ "$RESTORE_CHECK" = 1 ] || echo "db-backup: re-run with --restore-check to prove the dump replays"

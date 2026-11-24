# DoMoX Docker services — restart policy, healthchecks, the named DB volume

Loaded on demand. Every fact here was verified against this repo's `docker-compose.yml` and a live
`docker inspect`. It answers "why did the DB data / CoreNLP survive (or not survive) a reboot?" — and
"how do I add a healthcheck to a container that is already running?".

## The four services, and what keeps them up

All four compose services (`db`, `nlp`, `kroki`, `fuseki`) carry `restart: unless-stopped`, so they
return after a host or daemon reboot instead of needing a hand-start — that hand-start step is what left
CoreNLP down for 5 hours and failed `CorpusCandidateDedupIntegTest` (which reaches the real CoreNLP at
`localhost:8999`).

`db` also mounts the **named** volume `domox-db-data` (`/var/lib/postgresql`), deliberately **not** the
postgres image's anonymous volume, which `docker compose down` would drop along with the 971 `classcdd`
rows and the 302 review decisions.

## Restart policy is runtime state; healthchecks and volume mounts are create-time only

- **Restart policy** applies with no recreate: `docker update --restart unless-stopped <ctr> …`.
- **Healthchecks and volume mounts cannot be added to a running container.** Activating them needs
  `docker compose up -d --no-build --wait` (≈45 s, so run it detached through `scripts/run-bg.sh`;
  `--no-build` avoids rebuilding the fuseki/kroki images). It recreates all four and blocks until each
  reports healthy.
- Compose then warns *`volume "domox-db-data" already exists but was not created by Docker Compose`* —
  benign, it still uses the volume. `external: true` is deliberately **not** set, because that would make
  `up` fail on a fresh clone.

## Copying a volume's data — how the named volume was seeded from the old anonymous one

Back up first (`scripts/db-backup.sh`), **stop the DB** (`docker stop domox-db`) so the cluster is shut
down cleanly, then copy and prove the copy:

```shell
docker run --rm -v <src-vol>:/from:ro -v domox-db-data:/to \
    postgres:18-alpine sh -c 'cp -a /from/. /to/'
# verify: file count, byte count and the pg_control checksum must match on both sides
docker run --rm -v <src-vol>:/from:ro -v domox-db-data:/to postgres:18-alpine sh -c \
    'find /from -type f | wc -l; find /to -type f | wc -l; md5sum /from/18/docker/global/pg_control /to/18/docker/global/pg_control'
```

**Never** let a recreate start Postgres against an EMPTY volume: it would `initdb` a fresh cluster and the
analysis data would be gone. Afterwards confirm the mount and the rows:

```shell
docker inspect domox-db --format '{{range .Mounts}}{{.Name}}{{end}}'
docker exec domox-db psql -U postgres -d postgres -c 'SELECT count(*) FROM domox.classcdd;'
```

## Related

- Webapp integration tests that need the dev services up, and the port / DB-ownership caveat (never two
  instances against the same Postgres — the shared `domox.SEQ_GEN` sequence) → `.clinerules/commands.md`.
- Schema drift against the live dev DB → `.clinerules/memory-bank/technical/SCHEMA.md`.

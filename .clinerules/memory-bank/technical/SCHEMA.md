# DoMoX persistence / schema

## The one trap to remember: `create-tables` never ALTERs an existing table

EclipseLink runs with `eclipselink.ddl-generation: create-tables`
(`domox-webapp/src/main/resources/application.yml`). It **creates missing tables** but
**never alters existing ones**. So adding a JPA field to an entity silently desyncs the live
DB from the regenerated DDL — there is **no startup error**, the failure only appears at
runtime as `column X does not exist`.

Regenerated DDL: `domox-webapp/create-tables.sql`.

The DDL output path is **CWD-relative** (`eclipselink.application-location: "."`): the JVM must
start at the REPO ROOT or startup dies with EclipseLink-7018 FileNotFound. Surefire is pinned to
the repo root in `domox-webapp/pom.xml` (`<workingDirectory>${maven.multiModuleProjectDirectory}</workingDirectory>`);
CLI boots need `-Dspring-boot.run.workingDirectory=<repo root>`.

## TABLE_PER_CLASS inheritance (Candidate & friends)

`Candidate` uses `TABLE_PER_CLASS`. Real rows live in the **per-type tables**, not the base:

| Entity | Table |
|---|---|
| `ClassCdd` | `domox.classcdd` |
| `PropertyCdd` | `domox.propertycdd` |
| `ActionCdd` | `domox.actioncdd` |
| `AssociationCdd` | `domox.associationcdd` |
| `PackageCdd` | `domox.packagecdd` |
| `ParameterCdd` | `domox.parametercdd` |

`domox.candidate` (base) is an empty stub — never depend on it.

A new **persisted field added to the `Candidate` base must be ADD COLUMN'd into all six
per-type tables**, otherwise queries against any per-type entity that references the field
fail at runtime. Example — the `hopDepth` migration:

```sql
ALTER TABLE domox.classcdd       ADD COLUMN hopdepth INTEGER NOT NULL DEFAULT 1;
ALTER TABLE domox.propertycdd    ADD COLUMN hopdepth INTEGER NOT NULL DEFAULT 1;
ALTER TABLE domox.actioncdd      ADD COLUMN hopdepth INTEGER NOT NULL DEFAULT 1;
ALTER TABLE domox.associationcdd ADD COLUMN hopdepth INTEGER NOT NULL DEFAULT 1;
ALTER TABLE domox.packagecdd     ADD COLUMN hopdepth INTEGER NOT NULL DEFAULT 1;
ALTER TABLE domox.parametercdd   ADD COLUMN hopdepth INTEGER NOT NULL DEFAULT 1;
```

(Names fold to lowercase in Postgres; unquoted DDL `HOPDEPTH` -> stored `hopdepth`.)

## Schema-drift guardrail

Run after any entity change that adds/removes columns, before trusting the app:

```bash
bash scripts/check-schema-drift.sh
```

It diffs `domox-webapp/create-tables.sql` against the live `domox-db` and exits non-zero if
any table is missing a column the DDL expects. Regenerate `create-tables.sql` first by
starting the app once.

## Conventional approach

There is no Flyway/Liquibase baseline yet. If a durable migration mechanism is adopted, the
drift check above should become redundant — until then it is the cheapest guardrail the repo
has.
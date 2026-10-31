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

| Entity (Java, post-CRC-rename) | Table (persisted name — unchanged) |
|---|---|
| `ClassCdd` | `domox.classcdd` |
| `AttributeCdd` | `domox.propertycdd` |
| `ResponsibilityCdd` | `domox.actioncdd` |
| `CollaboratorCdd` | `domox.associationcdd` |
| `ResponsibilityParameter` | `domox.parametercdd` |
| ~~`PackageCdd`~~ (entity deleted) | `domox.packagecdd` (orphaned table, 0 rows) |

> **Class names ≠ table names (deliberate).** The CRC-vocabulary rename
> (`ActionCdd`→`ResponsibilityCdd`, `PropertyCdd`→`AttributeCdd`,
> `AssociationCdd`→`CollaboratorCdd`, `ParameterCdd`→`ResponsibilityParameter`) pinned
> `@Table`/`@Named`/`candidateType` to the historical values so **no DB migration was
> needed**. See the "Remaining work" section for the deferred migration.
>
> ⚠️ **Pinning `@Table` alone is NOT enough.** Two more names are *derived* from the Java
> class name and silently drift if you forget them (both were caught by
> `scripts/check-schema-drift.sh` after the rename):
>
> 1. **The `ruleMatches` M:N join table columns.** EclipseLink derives
>    `<entityName>_RuleMatch` / `<entityName>_ID` from the **JPA entity name**, not from
>    `@Table`.  Fix: `@Entity(name = "ActionCdd")` (etc.) on each renamed entity — this keeps
>    `domox.ActionCdd_RuleMatch(ActionCdd_ID, ruleMatches_ID)` intact.
> 2. **A `@JoinColumn` with no explicit `name`.** The column name defaults to
>    `<propertyName>_<pk>`, so renaming the `actionCdd` field broke
>    `domox.parametercdd.actioncdd_id`.  Fix: `@JoinColumn(name = "actioncdd_id", …)`.
>
> Also note the **Causeway object type** is *not* the class name — `Candidate.getObjectType()`
> reads it back from `@Named` because the diagram JSON feeds it into
> `/wicket/object/<objectType>:<id>` click-through URLs.

`domox.candidate` (base) is an empty stub — never depend on it.

A new **persisted field added to the `Candidate` base must be ADD COLUMN'd into all
per-type tables that still have a live entity**, otherwise queries against any per-type entity
that references the field fail at runtime. Example — the `hopDepth` migration:

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

## Remaining work — deferred persisted-name migration

The 2026-10 CRC rename changed **Java class names only**; `@Table`, `@Named` and the
`candidateType` string literals were pinned to the historical values so the live DB keeps
working. Still outstanding:

| Item | What is needed |
|---|---|
| `@Table(name = "ActionCdd")` on `ResponsibilityCdd` | `ALTER TABLE domox.actioncdd RENAME TO responsibilitycdd` (+ join table + FK columns), then flip the annotation |
| `@Table(name = "PropertyCdd")` on `AttributeCdd` | same for `domox.propertycdd` |
| `@Table(name = "AssociationCdd")` on `CollaboratorCdd` | same for `domox.associationcdd` (+ `source_id`/`target_id` FKs) |
| `@Table(name = "ParameterCdd")` on `ResponsibilityParameter` | same for `domox.parametercdd` |
| `@Named(... ".ActionCdd")` etc. | flip together with the table rename (Causeway object types are persisted in `bookmark`/`command` tables) |
| `candidateType` literals (`RuleMatches`, `*Candidates`, `CandidateD3Graph.typeLabel`) | `UPDATE domox.rulematch SET candidatetype='ResponsibilityCdd' WHERE candidatetype='ActionCdd';` etc. |
| Orphaned `domox.packagecdd` + `domox.packagecdd_rulematch` tables | `DROP TABLE` (entity `PackageCdd` was deleted; tables hold 0 rows) |
| `@Entity(name = "ActionCdd")` / `"PropertyCdd"` / `"AssociationCdd"` / `"ParameterCdd"` pins | flip together with the table rename — they exist only to keep the derived join-table columns stable |
| `@JoinColumn(name = "actioncdd_id")` on `ResponsibilityParameter.responsibilityCdd` | rename the column together with the field (or drop the explicit name once the property is renamed back) |
| `scripts/check-schema-drift.sh` comment listing `packagecdd` | drop from the comment once the table is dropped |

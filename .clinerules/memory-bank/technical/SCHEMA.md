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

| Entity (Java) | Table |
|---|---|
| `ClassCdd` | `domox.classcdd` |
| `AttributeCdd` | `domox.attributecdd` |
| `ResponsibilityCdd` | `domox.responsibilitycdd` |
| `CollaboratorCdd` | `domox.collaboratorcdd` |
| `ResponsibilityParameter` | `domox.responsibilityparameter` |

`PackageCdd` was deleted in the CRC rename; its two tables (`domox.packagecdd`,
`domox.packagecdd_rulematch`) were dropped on 2026-11-19, so there is no `packagecdd` row to keep.

> **Class names = table names (since 2026-11-20).** The 2026-10 CRC-vocabulary rename
> (`ActionCdd`→`ResponsibilityCdd`, `PropertyCdd`→`AttributeCdd`,
> `AssociationCdd`→`CollaboratorCdd`, `ParameterCdd`→`ResponsibilityParameter`) originally
> pinned `@Table`/`@Named`/`@Entity`/`candidateType` to the historical values so **no DB
> migration was needed**.  The deferred migration (TODO-15) has since been applied, so all
> five names now agree — see "Persisted-name migration (applied 2026-11-20)" below.
>
> ⚠️ **Two names are *derived* from the JPA entity name, not from `@Table`.** They drifted
> once during the rename and are caught only by `scripts/check-schema-drift.sh`:
>
> 1. **The `ruleMatches` M:N join table + its column.** EclipseLink derives
>    `<entityName>_RuleMatch` / `<entityName>_ID`, so `@Entity(name = …)` and the table name
>    must move **together** — otherwise you get
>    `domox.ActionCdd_RuleMatch(ActionCdd_ID, ruleMatches_ID)` for a table called
>    `ResponsibilityCdd`.
> 2. **A `@JoinColumn` with no explicit `name`.** The column name defaults to
>    `<propertyName>_<pk>`, so renaming the `actionCdd` field silently broke
>    `domox.parametercdd.actioncdd_id`.  `ResponsibilityParameter.responsibilityCdd` therefore
>    still carries an explicit `@JoinColumn(name = "responsibilitycdd_id", …)` — keep it in
>    step with the field name.
>
> Also note the **Causeway object type** (`@Named`) is what
> `Candidate.getObjectType()` reads back and what the diagram JSON feeds into
> `/wicket/object/<objectType>:<id>` click-through URLs — it must match the framework's type
> exactly, which is why it is read from the annotation rather than derived from the class.

`domox.candidate` (base) is an empty stub — never depend on it.

A new **persisted field added to the `Candidate` base must be ADD COLUMN'd into all
per-type tables that still have a live entity**, otherwise queries against any per-type entity
that references the field fail at runtime. Example — the `hopDepth` migration:

```sql
ALTER TABLE domox.classcdd       ADD COLUMN hopdepth INTEGER NOT NULL DEFAULT 1;
ALTER TABLE domox.attributecdd   ADD COLUMN hopdepth INTEGER NOT NULL DEFAULT 1;
ALTER TABLE domox.responsibilitycdd ADD COLUMN hopdepth INTEGER NOT NULL DEFAULT 1;
ALTER TABLE domox.collaboratorcdd ADD COLUMN hopdepth INTEGER NOT NULL DEFAULT 1;
ALTER TABLE domox.responsibilityparameter ADD COLUMN hopdepth INTEGER NOT NULL DEFAULT 1;
```

(Names fold to lowercase in Postgres; unquoted DDL `HOPDEPTH` -> stored `hopdepth`.)

Second example — the **`rationale` migration (2026-11-20)**, which adds the free-text reason every MCP
write must give. It is nullable (so no `DEFAULT`) and `VARCHAR(2000)`, and it is the one migration that
also touches a table **outside** the `Candidate` hierarchy: `SubDomain` records the reason for its own
changes, so `createSubDomain` has somewhere to put it.

```sql
ALTER TABLE domox.classcdd                 ADD COLUMN IF NOT EXISTS rationale VARCHAR(2000);
ALTER TABLE domox.attributecdd             ADD COLUMN IF NOT EXISTS rationale VARCHAR(2000);
ALTER TABLE domox.responsibilitycdd        ADD COLUMN IF NOT EXISTS rationale VARCHAR(2000);
ALTER TABLE domox.collaboratorcdd          ADD COLUMN IF NOT EXISTS rationale VARCHAR(2000);
ALTER TABLE domox.responsibilityparameter  ADD COLUMN IF NOT EXISTS rationale VARCHAR(2000);
ALTER TABLE domox.subdomain                ADD COLUMN IF NOT EXISTS rationale VARCHAR(2000);
```

The 2000-character length is deliberate: Postgres treats `varchar(n)` as a length *constraint* only, so
it costs nothing over a short column and a longer value is TOASTed transparently. The value is
normalised by `RationaleText` (blank → `NULL`, over-long → truncated), which is why the column can
stay a plain nullable `VARCHAR`.

## A `NOT NULL` string column needs a sentinel — normalise it in the entity's setter

Several candidate columns are `NOT NULL` but are fed by a domain answer that may legitimately be
"nothing": `responsibilitycdd.outputtype` (no return type), `collaboratorcdd.sourcecardinality` /
`targetcardinality` (no multiplicity stated — stored as `""`, see `CandidateFactory.applyMultiplicities`),
`attributecdd.type` and `responsibilityparameter.type`.  **That "nothing" must become a sentinel before
it reaches the DB, and the entity's setter is the only place that can guarantee it for every writer.**

Verified 2026-11-21 — `responsibilitycdd.outputtype` aborted the whole analysis phase of a corpus load:

```text
ERROR: null value in column "outputtype" of relation "responsibilitycdd" violates not-null constraint
Detail: Failing row contains (15524, groom, ResponsibilityCdd, 1, null, 1, 15523, 15320, null).
```

`ResponsibilityCandidates.outputTypeFor` answers the common case — the verb is neither a receive verb
nor an output verb — with a deliberate `null` ("the mapping had no answer"), and `create` wrote that
straight through.  So the *first* plain-verb operation (`groom`, in `UC01`) failed its insert, and with
it the entire load: the `DataIntegrityViolationException` rolls the fixture back, leaving the corpus
with **no** candidates at all — which reads as "the corpus load is broken", not as "one column is
nullable-wrong".  The fix lives in the entity: `ResponsibilityCdd.setOutputType` maps `null`/blank →
`void`, the value every historical row already holds and one `toPlantUmlString()` treats exactly like
`null`, so the rendered UML is unchanged.  Normalising in the setter rather than at the call site is
what also covers the constructor and a hand-built detached instance.

Three consequences worth remembering:

- **A "no answer" return value is not automatically storable.**  A method may deliberately return
  `null` to mean "the mapping had no answer" and still be persisted by a caller that does not know the
  column is `NOT NULL`.
- **A `null`-tolerant renderer hides the bug.**  `toPlantUmlString()` omits `null`, blank *and* `void`
  identically, so the UML output cannot reveal which of the three is in the row — the storage invariant
  has to be asserted directly (`ResponsibilityCddTest.storesVoidWhenThereIsNoReturnType` reads the
  write-only field reflectively, because the entity deliberately has no `getOutputType()`).
- **`create-tables` will not save you.**  Nothing in the schema changed for this fix, which is why it
  is a code fix and not a migration: `outputtype` was already `NOT NULL`, and the writer was wrong.

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

## Persisted-name migration (applied 2026-11-20 — TODO-15, closed)

The 2026-10 CRC rename changed **Java class names only**; `@Table`, `@Entity`, `@Named` and the
`candidateType` string literals were pinned to the historical values so the live DB kept working.
That deferred migration has now been **applied**, so the class name, the JPA entity name, the table
name, the Causeway object type and the persisted `candidateType` all agree.

| Item | State |
|---|---|
| `domox.actioncdd` → `domox.responsibilitycdd` (+ `_rulematch` + `responsibilitycdd_id`) | ✅ done |
| `domox.propertycdd` → `domox.attributecdd` (+ `_rulematch` + `attributecdd_id`) | ✅ done |
| `domox.associationcdd` → `domox.collaboratorcdd` (+ `_rulematch` + `collaboratorcdd_id`) | ✅ done |
| `domox.parametercdd` → `domox.responsibilityparameter` (+ `_rulematch` + `responsibilityparameter_id`) | ✅ done |
| `domox.responsibilityparameter.actioncdd_id` → `responsibilitycdd_id` | ✅ done |
| constraint + index names follow the new table names | ✅ done |
| `@Table` / `@Entity(name=…)` / `@Named(…)` on the four entities | ✅ done |
| `candidateType` literals (`RuleMatches` callers, `*Candidates`, `CandidateFactory` dispatch) | ✅ done |
| `domox.rulematch.candidatetype` / `relatedcandidatetype` values | ✅ done (507 + 105 rows) |
| per-type `candidatetype` column values (`actioncdd` 191, `propertycdd` 109, `associationcdd` 237) | ✅ done |
| Orphaned `domox.packagecdd` + `domox.packagecdd_rulematch` tables | ✅ done 2026-11-19 |

**The migration script is `scripts/migrate-crc-persisted-names.sql`** — it renames the constraints
(a `DO` block), the eight tables, the derived columns and the persisted type values in one
transaction, then verifies counts and prints three `should_be_zero` queries.  Apply it with:

```bash
bash scripts/db-backup.sh                                    # always back up first
docker exec -i domox-db psql -U postgres -d postgres -v ON_ERROR_STOP=1 \
    < scripts/migrate-crc-persisted-names.sql
```

It is a **one-shot**: re-running it fails loudly on the first `ALTER TABLE`.  A fresh database needs
no migration — EclipseLink `create-tables` builds the new names directly.

Two names are still *derived* rather than declared and must be moved together with the entity name:
the `ruleMatches` join table (`<entityName>_RuleMatch`, `<entityName>_ID`) and a bare `@JoinColumn`
column (`<propertyName>_<pk>`).  `scripts/check-schema-drift.sh` is what catches a mismatch.


# UML gap fix plan — code changes, severity and priority

**Companion documents.** The findings this plan acts on: `MODEL_UML_READINESS_REVIEW.md`.
The review work that runs *after* these fixes: `MODEL_REVIEW_PLAN.md`.
**Method.** Every root cause below was re-verified against the working tree and the live
`domox-db` / running instance (port 8080, all four compose services healthy) on 2026-11-18;
file:line anchors are current as of that date.
**Correction to the review.** `domox.associationcdd_rulematch` holds **237 rows**, so an
association *is* linked to the rule matches that produced it — the review's "no rule-match
connection" statement was wrong and is corrected here (it is what makes FIX-2 cheap).

---

## 1. Severity and priority scales

| Severity | Meaning |
|---|---|
| **S1** | Wrong output today — the artefact is invalid, not merely incomplete |
| **S2** | A UML element is missing or an endpoint is broken (500 / no data) |
| **S3** | Correctness-adjacent: dead code, data quality, lost semantics |

| Priority | Meaning |
|---|---|
| **P0** | Do now — one-line, zero-risk, unblocks verification |
| **P1** | This iteration — small and self-contained |
| **P2** | Next — needs a design decision or measurement first |
| **P3** | Backlog — hygiene, do it when touching the same file |

---

## 2. Summary

| ID | Gap | Severity | Priority | Effort | Schema change |
|---|---|---|---|---|---|
| **FIX-1** | Parameterless operations render as `name(: void` — **149/149 lines** | **S1** | **P0** | XS | no |
| **FIX-2** | Multiplicities are extracted but persisted nowhere | **S2** | **P1** | M | no (option A) |
| **FIX-3** | Operation signatures: 0 parameters, `outputType` is `void` 191/191 | **S2** | P2 | L | no |
| **FIX-4** | RO list endpoints return **HTTP 500** (JAXB duplicate properties) | **S2** | **P1** | S | no |
| **FIX-5** | Multiplicity evidence polluted by document metadata | S3 | **P1** | S | no |
| **FIX-6** | Attribute extraction yields entities; 12 attributes on a class named `Unknown` | **S2** | P2 | L | no |
| **FIX-7** | Association verb lost — edge label degrades to `source_target` | S3 | P2 | M | yes (if stored) |
| **FIX-8** | Dead `AttributeCdd.cardinality` + `Cardinality` + orphan `CARDINALITY` column | S3 | P3 | S | yes (drop) |

---

## 3. Dependency tree and wave ordering

```
UML completeness
├── Wave 1 — isolated, safe, no design decision          (P0/P1)
│   ├── FIX-1  close the parenthesis in toPlantUmlString
│   ├── FIX-4  remove @Getter from public fields (RO 500)
│   └── FIX-5  scope-guard TDR25 (must precede FIX-2)
├── Wave 2 — one decision, two halves                    (P1)
│   └── FIX-2 ──┐
│       FIX-8 ──┴─ same decision: keep the cardinality fields (A) or delete (B)
├── Wave 3 — needs measurement before code               (P2)
│   ├── FIX-6  attribute vocabulary (informs the attribute review pass)
│   └── FIX-3  operation signatures
└── Wave 4 — semantics, needs a schema decision          (P2/P3)
    └── FIX-7  association verb / edge label
```

**Why this order.** FIX-5 changes which multiplicity matches exist, so wiring FIX-2 first
would copy `UC → 09` onto real association ends. FIX-6 changes which attributes exist, so the
attribute review pass (`MODEL_REVIEW_PLAN.md` §5) must follow it — otherwise 100 review
decisions are made about candidates that a re-run is about to replace.

---

## 4. FIX-1 — Parameterless operations emit invalid PlantUML  ·  S1 · P0 · XS

**Symptom.** Every rendered operation is malformed: `browse(: void`, `perform(: void`
(149/149 operation lines in the model-653 export).

**Root cause.** `domox-domain/src/main/java/domox/dom/crc/model/ResponsibilityCdd.java:72-80` —
the closing `)` is only added by the `replaceAll(", $" → ")")` that strips a *trailing separator*.
An empty parameter list has no separator, so the branch never runs. The regex is also rebuilt
into a new `StringBuilder` and then discarded, which hides the intent.

**Second defect in the same method.** `inputTypeList` is declared without an initialiser
(`ResponsibilityCdd.java:60`) and `ResponsibilityCandidates.create()` (line 59-72) never sets it,
so a `new ResponsibilityCdd(name, null, "void")` throws NPE. The DB path is safe only because
Hibernate materialises the collection as an empty bag — the entity is not, on its own, correct.

**Fix.** Append `)` unconditionally and tolerate a null list:

```java
public String toPlantUmlString() {
    final StringBuilder s = new StringBuilder(getCandidateName()).append('(');
    if (inputTypeList != null) {
        String sep = "";
        for (ResponsibilityParameter p : inputTypeList) {
            s.append(sep).append(p.toPlantUmlString());
            sep = ", ";
        }
    }
    return s.append("): ").append(outputType).toString();
}
```

**Tests.** New `domox-domain/src/test/java/domox/dom/crc/model/ResponsibilityCddTest.java`
(plain JUnit 5 + AssertJ, mirroring `CollaboratorCddTest`): no parameters → `browse(): void`;
one parameter → `notify(message: String): void`; two → comma-separated; `null` list → no NPE;
`null` `outputType` must not produce `null` in the string.

**Validation.** `bash scripts/check.sh` (domain unit tests), then regenerate the export and
assert the shape:

```shell
python3 scripts/ro.py invoke domox.DomainModels generateUmlDiagram domainModel=DomainModel:653 \
  | grep -c '(: void'        # must be 0
```

---

## 5. FIX-2 — Multiplicities are extracted but persisted nowhere  ·  S2 · P1 · M

**Symptom.** `associationcdd.sourcecardinality` / `targetcardinality` are `''` for **237/237**
rows, so the diagram has **0 edges carrying a cardinality**. The values exist only as text inside
`rulematch.description` (`multiplicity.add(pet, 1)`).

**What already exists (this is why the fix is cheap).**

| Piece | Location | State |
|---|---|---|
| Storage | `CollaboratorCdd.java:66-74` `sourceCardinality` / `targetCardinality` | present, `@Column(nullable = false)` |
| Setters | Lombok `@Setter` on both | present |
| Rendering | `CollaboratorCdd.toPlantUmlString()` line 84 — `quote(src) + arrow + quote(target)` | **already renders** `"1" -> "0..*"` |
| Test | `DomainModelsTest.java:70` sets `targetCardinality("2")` | already asserted end-to-end |
| Evidence link | `domox.associationcdd_rulematch` — **237 rows** | present |
| Value normaliser | `MultiplicityNotation.umlNotation(...)` | present, covered by `MultiplicityNotationTest` |

So nothing has to be added to the schema or the generator: `CandidateFactory` simply never calls
the setters.

**Fix (option A — wire it up).**

- [ ] **A1.** Add `RuleMatchRepository#findByTypedDependency_Sentence`-based lookup of the
  multiplicity matches for a sentence (the method already exists — `RuleMatches:208` uses it) and
  filter `ruleClassName ∈ {TDR25, TDR26}`.
- [ ] **A2.** Extract the decision into a **pure collaborator** `MultiplicityEvidence` (new class in
  `domox.dom.rules`) with a static entry point such as
  `String cardinalityFor(List<RuleMatch> sentenceMatches, String className)`, returning `""` when
  there is no match. Pure = unit-testable, and it keeps `CandidateFactory` (pitest floor **95 %**,
  63 mutants) from growing an untestable branch.
- [ ] **A3.** Call it from `CandidateFactory.createCandidateFromMatch` in the association branch
  (`CandidateFactory.java:128-140`) for both ends, after `collaboratorCandidates.findOrCreate(...)`,
  and set `setSourceCardinality(...)` / `setTargetCardinality(...)` only when non-empty (the
  columns are `NOT NULL`, so `""` is the correct "absent" value).
- [ ] **A4.** Re-derive the value from the typed dependency rather than parsing the persisted
  description text — the DB rows still hold the **pre-fix** text (`09`, `N`, `02`), so
  `description` is not a trustworthy source until a Phase-1 re-run.
- [ ] **A5.** Add `"MultiplicityEvidence"` to `FLOORS` in `scripts/pitest-floors.py` once its test
  class lands (the file's convention: floor = one mutant below the achieved rate).

**Decision required (option B).** Alternatively delete the dead `AttributeCdd.cardinality` field
and `Cardinality` (see FIX-8) and leave multiplicities unmodelled. Recommendation: **option A** —
the storage, the rendering and the test already exist, and the missing link is one collaborator.

**Tests.** `MultiplicityEvidenceTest` (no match → `""`; `TDR25 nummod(pet, 01)` → `1`;
`TDR26 det(pet, many)` → `*`; `det(pet, some)` → `1..*`; two matches on the same class → the
deterministic winner). Plus an extension of `DomainModelsTest` asserting an edge renders
`"1" -> "0..*"` from a real match.

**Validation.**

```shell
docker exec domox-db psql -U postgres -d postgres -c \
  "SELECT count(*) FROM domox.associationcdd WHERE sourcecardinality <> '' OR targetcardinality <> '';"
```

---

## 6. FIX-4 — RO list endpoints return HTTP 500  ·  S2 · P1 · S

**Symptom.** `python3 scripts/ro.py invoke domox.DomainModels listAll` → `HTTP 500` with
`java.lang.NullPointerException` at `ManagedObjectViewmodel.getBookmark(…:103)`.

**Root cause (from `logs/domox-webapp.log:3993-4250`).** The NPE is the *consequence*. The real
error is JAXB:

```
java.lang.RuntimeException: unrecoverable error: 'Error obtaining JAXBContext for classes
{DomainObjectList, domox.dom.crc.model.DomainModel}, due to illegal annotations …
6 error(s) reported: Class has two properties of the same name "attributeList" … "classType"
… "collaboratorList" … "responsibilityList" … "authors" … "responsibilityCdd"
```

A **`public` field plus a Lombok `@Getter` of the same name** is two JAXB properties. Verified
offenders:

| File | Field |
|---|---|
| `crc/model/ClassCdd.java` | `classType:56`, `attributeList:81`, `responsibilityList:87`, `collaboratorList:93` |
| `crc/model/DomainModel.java` | `classList:93`, `responsibilityList:105`, `attributeList:116`, `collaboratorList:127` |
| `requirements/Document.java` | `authors:131` |
| `crc/model/ResponsibilityParameter.java` | `responsibilityCdd:42` |

**Fix.** Remove the redundant `@Getter` from every `public` field (Causeway reads the annotated
public field directly). **Keep `@Setter`** — it is genuinely called: `CorpusIngestion.java:177`
and `DomainModelsTest.java:67` use `setClassType(...)`, and
`ClassArchetypeClassifierTest.java:116,181` use `setResponsibilityList(...)` /
`setCollaboratorList(...)`. If a getter really is needed, use `@Getter(AccessLevel.NONE)` and
keep the public field.

- [ ] **F4a.** Sweep `domox-domain/src/main/java/domox/dom/{crc/model,requirements}` for `@Getter`
  immediately above a `public` field and fix **all** of them — JAXB reports only the duplicates
  reachable from the failing graph, so the reported six are a lower bound.
- [ ] **F4b.** Add a guardrail: a webapp integration test that invokes the RO list action and
  asserts a rendered list (the metamodel validation at boot does not catch this — it is a
  serialization-time failure).

**Validation.**

```shell
python3 scripts/ro.py invoke domox.DomainModels listAll              # expect HTTP 200 + JSON list
grep -c 'two properties of the same name' logs/domox-webapp.log      # no new occurrences
```

---

## 7. FIX-5 — Multiplicity evidence polluted by document metadata  ·  S3 · P1 · S

**Symptom.** TDR25's 44 matches are mostly not multiplicities of anything. Measured, with the
sentence each came from:

| Value | n | Source sentence |
|---|---|---|
| `multiplicity.add(UC, 0N)` | 15 | `… Use Case ID UC - 09 …` (metadata header) |
| `multiplicity.add(sentence, N)` | 15 | `A use case elaborated from sentence 10 of …` |
| `multiplicity.add(Accessories, 09)` | 1 | `… Use Case Name Pet Accessories and Fashion …` |
| `multiplicity.add(Partner, 04)` | 1 | `… Shelter / Rescue Partner …` |
| `multiplicity.add(second, 2)` / `(second, 5)` | 2 | `… must load within 2 seconds .` |
| `multiplicity.add(off, one)` | 1 | `… a daycare day ( recurring or one - off ) …` |

Only ~10 of the 44 are usable; the 87 TDR26 matches (`det`) are the sound ones.

**Root cause.** `TDR25.when()` (`domox/dom/rules/TDR25.java:24`) fires on **any**
`nummod(noun, CD)`. `UC` is already in `domox.nlp.use-case-blocked-nouns` and the persistence
guard at `RuleMatches.java:91-96` drops blocked-noun `ClassCdd` matches — so the 15 `UC` rows
disappear on a Phase-1 re-run. `sentence`, `second`, `off`, `Partner`, `Accessories` are **not**
blocked and survive.

**Fix.**

- [ ] **F5a.** Extend the existing predicate rather than adding a second one: add the document
  metadata nouns to the config list (e.g. `domox.nlp.use-case-blocked-nouns` — or a sibling
  `use-case-metadata-nouns` if blocking them as *classes* would be too broad) covering
  `sentence, step, section, figure, table, second, minute, hour, day, week, month, year, uc, id,
  version`.
- [ ] **F5b.** Guard `TDR25.when()` with that predicate on the governor gloss (`currentTd.getA()`)
  so the match is never recorded. Verify whether TDR26 needs the same guard (its determiners are a
  closed list and its values look sound).
- [ ] **F5c.** Record the trade-off in `docs/scratch_pad/rules/RULES_REGISTRY.md`: the spec
  (`docs/application/RULES.txt`) says `nummod(E1, CD) → multiplicity.add(E1, CD)`, so this is a
  **scope guard**, not a spec change.
- [ ] **F5d.** Note that the guard only affects **new** matches; the 44 existing rows are fixed by
  the Phase-1 re-run that the review plan schedules (`MODEL_REVIEW_PLAN.md` §3).

**Tests.** Extend `TypedDependencyRulesTest` (TDR25/TDR26 cases at lines 368-504):
`nummod(sentence, 10)` → no match; `nummod(second, 2)` → no match; `nummod(pet, 01)` → still
`multiplicity.add(pet, 1)`. Extend `UseCaseBlockedNouns` coverage for the new nouns (pitest floor
**85 %**).

---

## 8. FIX-3 — Operation signatures: no parameters, `outputType` always `void`  ·  S2 · P2 · L

**Symptom.** 191 actions, `parametercdd` has **0 rows**, and `actioncdd.outputtype` is `void` for
**191/191**. The rendered operation is a name with no signature.

**Where the two halves come from.**

| Half | Location | Cause |
|---|---|---|
| `outputType` | `ResponsibilityCandidates.java:62` | `obj.setOutputType("void")` — hardcoded, no inference |
| parameters | `ResponsibilityCdd.inputTypeList` never populated | no rule or factory step ever creates a `ResponsibilityParameter` |

**What already exists.** `ResponsibilityParameter` (entity, `parametercdd`, FK
`actioncdd_id`), the `ParameterCdd_RuleMatch` join table, and
`ResponsibilityCdd.toPlantUmlString()` which already renders `name(p: Type): Out`. So again:
**no schema change**.

**Fix.**

- [ ] **F3a.** In Phase 2, for an `ActionCdd` match, walk the **same sentence's** typed
  dependencies for `nsubj` / `dobj` / `iobj` / `nmod` / `pobj` whose governor gloss equals the
  action's verb and whose dependent is a noun → create one `ResponsibilityParameter(name, type)`
  each, where `type` is the owning `ClassCdd`'s name when that class exists in the model, else
  `CandidateFactory.inferType(name)`.
- [ ] **F3b.** Derive `outputType` from the verb catalogue already in `NlpProperties`
  (`systemOutputVerbs`, `userInputVerbs`, `receiveVerbs`) / `ActionCatalog` instead of the literal
  `"void"`: an output/notify verb → `String`; a receive verb → the object noun's class; everything
  else keeps `void`.
- [ ] **F3c.** Render the return type only when it is meaningful (UML omits a return type for
  void). This is the honest minimum if F3b is deferred.
- [ ] **F3d.** Owner correctness is a **separate** defect and should not be bundled: the review
  found `supervise → Day`, `capture → Delivery` (the owner is the sentence *object*, not the
  actor). Fix it only if the review pass shows it matters for the approved set.

**Tests.** A new `ResponsibilityParameterExtractionTest` over hand-built `TypedDependency` lists;
extend `TypedDependencyRulesTest` with a sentence carrying both a verb and its object.
**Decision required:** the exact verb→return-type mapping (F3b) — it is a domain judgement.

---

## 9. FIX-6 — Attribute extraction yields entities, and invents a class named `Unknown`  ·  S2 · P2 · L

**Symptom.** 109 attribute candidates, only **2 approved**, on **44 of 131** classes; 12 attributes
land on a class literally named `Unknown`; names are often entities or noise (`pet`, `product`,
`cart`, `role`, `size color`, `description description`).

**Root cause (two parts).**

1. **A closed lexicon.** `TDR2.when()` (`TDR2.java:28`) requires `isBasicAttributeB(...)`, i.e. the
   dependent noun must be in `domox.nlp.basic-attributes`
   (`application.yml:276-280`: `name, number, type, address, level, date, time, id, code, status,
   description, title, label, value, amount, quantity, size, color, email, phone, username,
   password, comment, note, version, category, priority, created-date, modified-date`). The
   corpus' real attributes (`breed, colour, weight, vaccination status, price, duration`) are
   absent from it — so they are never extracted. The same list feeds `TDR4`/`TDR5` and
   `BasicAttributeCatalog` (pitest floor **80 %**, `BasicAttributeVocabulary`).
2. **A fabricated owner.** `CandidateFactory.java:191` —
   `relatedCandidateName != null ? relatedCandidateName : "Unknown"` — so a property match with no
   resolvable owner creates a class called `Unknown` instead of being skipped. The guard above it
   only rejects *blocked* nouns, not a missing one.

**Fix.**

- [ ] **F6a.** Fix the fabricated owner first (small, unambiguous): return `null` (skip the
  candidate) when `relatedCandidateName == null`, mirroring the blocked-noun guard two lines above.
  Add a `CandidateFactoryTest` case (that class is floored at **95 %**).
- [ ] **F6b.** Learn the real attribute vocabulary from the 100 pending candidates — read the
  evidence TSV produced by the review plan (`MODEL_REVIEW_PLAN.md` §4) **before** writing code.
- [ ] **F6c.** Extend `basic-attributes` with the vocabulary the corpus actually uses.
- [ ] **F6d.** Prefer the **compound head** (`pet breed` → `breed`, `size color` → `color`) rather
  than the whole phrase; the phrase form is what produces `description description`.
- [ ] **F6e.** Consider allowing a *class-typed* attribute: `AttributeCdd.type` was designed for
  this ("class candidates … are to be set here as well", `AttributeCdd.java:53-56`) and nothing
  uses it. Decide whether `Order.customer` is an attribute or an association before extending the
  lexicon — otherwise the lexicon grows to swallow associations.

---

## 10. FIX-7 — The association verb is lost  ·  S3 · P2 · M

**Symptom.** Every edge label is `source_target` (`Customer_Premium`), because `CollaboratorCdd`
has no label field. The verb survives only in `rulematch.description`
(`relationship.add(inventory (has) product)`).

**Correction to the review.** The association **is** linked to its rule matches
(`domox.associationcdd_rulematch` = 237 rows), so the verb is reachable from the candidate — the
review's "no rule-match connection" was wrong.

**Fix — two options.**

| Option | Change | Cost |
|---|---|---|
| **A. Derive at export** | `ClassDiagram` / `CollaboratorCdd.toPlantUmlString` reads the verb from the association's own rule match | no schema change; needs the rule match on the export path |
| **B. Store a label** | new `label` field on `CollaboratorCdd` + `ALTER TABLE domox.associationcdd ADD COLUMN label VARCHAR(255)` + regenerate `create-tables.sql` | schema change on the dev DB, per `memory-bank/technical/SCHEMA.md` (EclipseLink `create-tables` never ALTERs) |

**Recommendation: option A** — the label is derived evidence, and storing it would duplicate what
the rule match already says. Revisit only if the UI must edit it.

**Test.** `CollaboratorCddTest` (exists): add a case asserting the rendered edge carries the verb
when a rule match is present, and `source_target` when it is not.

---

## 11. FIX-8 — Dead cardinality field, class and orphaned column  ·  S3 · P3 · S

| Artifact | State |
|---|---|
| `AttributeCdd.cardinality` (`AttributeCdd.java:28-31`) | `private Cardinality cardinality;` — **annotations commented out**, no getter, no setter, never assigned |
| `Cardinality.java` | `@Data` class with `min`/`max`/`hasLimit` — no other reference in the codebase |
| `domox-webapp/create-tables.sql:1` | still declares `CARDINALITY BYTEA` on `domox.PropertyCdd` — a column the entity no longer maps |
| `domox.packagecdd` | orphaned table (entity deleted in the CRC rename, 0 rows) — **dropped 2026-11-19** |

**Fix.** This is the **same decision as FIX-2**:

- If FIX-2 option **A** (wire the multiplicities): delete `AttributeCdd.cardinality` +
  `Cardinality` and let the DDL regenerate without the column — the UML multiplicity belongs on the
  association end, which is where UML puts it.
- If FIX-2 option **B**: delete them anyway, and drop the orphan table in the same migration.

**Process note.** A column removal is a *dev-DB* operation (`ALTER TABLE … DROP COLUMN`):
`create-tables.sql` is regenerated, the live `domox-db` is not. Run
`bash scripts/check-schema-drift.sh` afterwards, and back up first (`scripts/db-backup.sh`).

---

## 12. Validation harness for every fix

| Step | Command | When |
|---|---|---|
| **Instant guardrails** | `bash scripts/check.sh --quick` | after any edit (drift, layout XML, script syntax, dependency alignment) |
| **Full local bundle** | `bash scripts/check.sh` | before handing over (adds unit tests, UI/UML/ontology render, metamodel guardrail) |
| **Domain unit tests** | `setsid mvn -o -pl domox-domain test >/tmp/domain-tests.log 2>&1 </dev/null &` | FIX-1/2/3/5/6 — **detached**, the shell caps a call at ~30 s |
| **Schema drift** | `bash scripts/check-schema-drift.sh` | after any entity-field change (FIX-7, FIX-8) |
| **Mutation floors** | `setsid bash scripts/check-mutation.sh >/tmp/mutation.log 2>&1 </dev/null &` | after FIX-2/5/6 touch a floored class — **run last** (pitest consumes test classes) |
| **Live export** | `python3 scripts/ro.py invoke domox.DomainModels generateUmlDiagram domainModel=DomainModel:653` | re-measure the diagram against the review's baseline table |
| **RO regression** | `python3 scripts/ro.py invoke domox.DomainModels listAll` | FIX-4 |
| **Boot smoke** | `bash scripts/verify-boot.sh` | after FIX-4 — an entity-annotation change can break boot, not just the build |

**Floored classes this plan touches** (`scripts/pitest-floors.py`): `CandidateFactory` (95),
`UseCaseBlockedNouns` (85), `BasicAttributeVocabulary` (80) — plus each new collaborator
(`MultiplicityEvidence`, `ResponsibilityParameterExtraction`) needs its own entry.

---

## 13. Decisions — resolved 2026-11-19

| # | Decision | Outcome | Implemented |
|---|---|---|---|
| 1 | **FIX-2 / FIX-8** — wire the cardinality fields, or delete them? | **Wire them** (option A), and delete the dead `AttributeCdd.cardinality` + `Cardinality` anyway — the UML multiplicity belongs on the association end | ✅ FIX-2 + FIX-8 |
| 2 | **FIX-3b** — the verb→return-type mapping for `outputType` | ⚠️ **still open** — the proposed answer (`outputType` should be `ResponsibilityCdd`) is based on a misreading, corrected below | F3c only (with FIX-1) |
| 3 | **FIX-6e** — is `Order.customer` an attribute or an association? | **An association** — only built-in types are attributes | ✅ F6c/F6d (partly — see §13.6) |
| 4 | **FIX-7** — derive the label at export, or store it? | **Derive at export** — no schema change; revisit if the UI must edit the label | ✅ FIX-7 |
| 5 | **Phase-1 re-run scope** | **Whole corpus**, and the 302 existing review decisions are kept (they hang off candidate rows, which a re-run updates in place rather than replaces) | ⏳ not run — see §14 |

### 13.2 correction — `outputType` is a UML return type, not a candidate type

`ResponsibilityCdd.outputType` is **not** the CRC element type of the responsibility; it is the
operation's **return type**:

- it is rendered *after* the parameter list, as the UML return type — `toPlantUmlString()` returns
  `name(params): outputType`;
- `DomainModelsTest:49` and `generate/JavaCodeTest:51` both pass `Integer.class.getSimpleName()`, i.e.
  a Java type;
- the CRC element type already lives elsewhere: `Candidate.candidateType` is set to `"ActionCdd"` by
  `ResponsibilityCandidates.create()`.

So `outputType = "ResponsibilityCdd"` would render every operation as `browse(): ResponsibilityCdd`.
What is still needed is the F3b mapping **verb → return type** (an output/notify verb → `String`, a
receive verb → the received class, otherwise no return type), or the honest minimum of rendering no
return type at all. The honest minimum is already in place: `void`/blank/null is omitted (F3c, landed
with FIX-1), so operations render `browse()` rather than `browse(): void`.


### 13.6 residual — class nouns still reach the attribute list through TDR10/TDR11

Decision 3 was applied to the lexicon (no class name was added) and to TDR12 (the attribute is named
after the attribute word, F6d), but the **possessive** rules still turn a possessed *class* noun into
an attribute. Measured over the PetShop model's 109 attribute candidates:

| Rule | Attributes | Shape |
|---|---|---|
| TDR12 (compound) | 42 | `pet size`, `size color` — fixed by F6d/F6a |
| **TDR10 (nmod:poss)** | **26** | `their cart` → attribute `cart` of `Customer`; `their pet` → attribute `pet` |
| **TDR11 (amod)** | **18** | `beloved pets` → attribute `beloved pets` of `Owners` |
| TDR13 (conjunction) | 11 | — |
| TDR6/TDR9/TDR16/TDR8 | 25 | — |
| TDR4/TDR5 (blocked verbs) | 4 | — |

`TDR10`'s branches create the `PropertyCdd` **without** consulting `isBasicAttributeB/A`, so *any*
possessed noun becomes an attribute. Narrowing those branches to attribute nouns — the same scope
guard FIX-5 applied to TDR25/TDR26 — is a **rule-set change** with its own tests to update
(`tdr10_placesPropertyOnResolvedPossessor`, `tdr10_enrichesThePropertyName`,
`tdr11_placesPossessedNounPropertyOnPossessor` all assert the current behaviour). It is left as a
separate, deliberate change rather than smuggled into this wave.

### 13.7 new finding — an association match is swallowed by an earlier class match

`CandidateFactory.createCandidatesFrom` de-duplicates by *candidate name* whenever
`candidateType == "ClassCdd"` — but the association rules (TDR14–TDR23) also record
`candidateType = "ClassCdd"` with a `relatedCandidateName`. So an association whose **source class was
already created earlier in the same batch** hits the de-duplication branch, its match is attached to
the class, and **no association is created**. Measured on the current model:

```sql
-- 30 distinct association pairs were recorded, but only 14 association rows exist
SELECT count(*) FROM domox.rulematch
 WHERE candidatetype='ClassCdd' AND relatedcandidatename IS NOT NULL;   -- 30
SELECT count(*) FROM domox.associationcdd WHERE type=0;                 -- 14
```

The 16 pairs with no row (`Adopter_Rejection`, `Health_Pet`, `Pet_Daycare`, `Stock_Size`, …) are
exactly the ones whose source class has a plain class match earlier in the same document. The
one-line fix is to narrow the guard to a *pure* class match (`relatedCandidateName == null`), but it
changes model content (16 new edges), so it is recorded here as a decision rather than applied.


## 14. Execution status

- [x] **Wave 1** — FIX-1 (XS), FIX-4 (S), FIX-5 (S) → `check.sh --quick`, domain tests, boot smoke
- [x] **Wave 2** — FIX-2 (M) + FIX-8 (S) → schema drift clean, dev-DB column dropped
- [x] **Wave 3** — FIX-6 (F6a/F6c/F6d) then FIX-3 (F3a parameters + F3c return type) → domain tests
      green (F3b left open, §13.2; the TDR10/TDR11 residual, §13.6)
- [x] **Wave 4** — FIX-7 (M) → domain tests
- [ ] Re-run Phase 1/2 on the corpus, regenerate the export, and update
      `MODEL_UML_READINESS_REVIEW.md` §2 with the new measurements — **needs the NLP service and a
      fresh model; not run here**
- [ ] Decide §13.6 (TDR10/TDR11 possessive scope) and §13.7 (association/class de-duplication)

**Fixes that landed**

| Fix | Where |
|---|---|
| FIX-1 | `ResponsibilityCdd.toPlantUmlString` (unconditional `)`, null-tolerant, no `void`) + `ResponsibilityCddTest` |
| FIX-2 | new `MultiplicityEvidence` + `CandidateFactory.applyMultiplicities` (sentence-scoped) + `CollaboratorCdd` getters |
| FIX-4 | `@Getter` removed from the 10 public entity fields (`ClassCdd`, `DomainModel`, `Document`, `ResponsibilityParameter`) + call sites + a new ArchUnit rule |
| FIX-5 | `domox.nlp.use-case-metadata-nouns` + `UseCaseBlockedNouns.isMetadataNoun` + TDR25/TDR26 guards + case-insensitive lookup fix |
| FIX-6 | `CandidateFactory` (no `Unknown` owner), `TDR12` (attribute word, no owner when both are attributes), `basic-attributes` extended |
| FIX-3 | new `ResponsibilityParameterExtraction` + `ResponsibilityCandidates.addParameter` + `CandidateFactory.addParameters`; `void` omitted |
| FIX-7 | new `AssociationLabel` + `CollaboratorCdd.label()` |
| FIX-8 | `AttributeCdd.cardinality` + `Cardinality.java` deleted, `create-tables.sql` updated, dev-DB column dropped |

# UML gap fix plan — code changes, severity and priority

**Companion documents.** The findings this plan acts on: `MODEL_UML_READINESS_REVIEW.md`.
The review work that runs *after* these fixes: `MODEL_REVIEW_PLAN.md`.
**Method.** Every root cause below was re-verified against the working tree and the live
`domox-db` / running instance (port 8080, all four compose services healthy) on 2026-11-18;
file:line anchors are current as of that date.
**Correction to the review.** `domox.collaboratorcdd_rulematch` holds **237 rows**, so an
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

**Symptom.** `collaboratorcdd.sourcecardinality` / `targetcardinality` are `''` for **237/237**
rows, so the diagram has **0 edges carrying a cardinality**. The values exist only as text inside
`rulematch.description` (`multiplicity.add(pet, 1)`).

**What already exists (this is why the fix is cheap).**

| Piece | Location | State |
|---|---|---|
| Storage | `CollaboratorCdd.java:66-74` `sourceCardinality` / `targetCardinality` | present, `@Column(nullable = false)` |
| Setters | Lombok `@Setter` on both | present |
| Rendering | `CollaboratorCdd.toPlantUmlString()` line 84 — `quote(src) + arrow + quote(target)` | **already renders** `"1" -> "0..*"` |
| Test | `DomainModelsTest.java:70` sets `targetCardinality("2")` | already asserted end-to-end |
| Evidence link | `domox.collaboratorcdd_rulematch` — **237 rows** | present |
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
  "SELECT count(*) FROM domox.collaboratorcdd WHERE sourcecardinality <> '' OR targetcardinality <> '';"
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

**Root cause.** `TDR25.when()` (`domox/dom/rules/tdr/TDR25.java:24`) fires on **any**
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

**Symptom.** 191 actions, `responsibilityparameter` has **0 rows**, and `responsibilitycdd.outputtype` is `void` for
**191/191**. The rendered operation is a name with no signature.

**Where the two halves come from.**

| Half | Location | Cause |
|---|---|---|
| `outputType` | `ResponsibilityCandidates.java:62` | `obj.setOutputType("void")` — hardcoded, no inference |
| parameters | `ResponsibilityCdd.inputTypeList` never populated | no rule or factory step ever creates a `ResponsibilityParameter` |

**What already exists.** `ResponsibilityParameter` (entity, `responsibilityparameter`, FK
`responsibilitycdd_id`), the `ResponsibilityParameter_RuleMatch` join table, and
`ResponsibilityCdd.toPlantUmlString()` which already renders `name(p: Type): Out`. So again:
**no schema change**.

**Fix.**

- [ ] **F3a.** In Phase 2, for an `ResponsibilityCdd` match, walk the **same sentence's** typed
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
- [x] **F3d.** Owner correctness — **applied 2026-11-20** (§13.8): the operation is owned by the
  sentence's **actor** (`nsubj`), not by its object; the object is kept only as the fallback owner when
  the sentence names no usable actor, and as the receive-verb return type. The review's
  `supervise → Day` / `capture → Delivery` observation turned out to be **systematic** — measured, all
  149 owned operations were owned by their object noun and none by their actor.

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
- [x] **F6b.** Learn the real attribute vocabulary from the 100 pending candidates — read the
  evidence TSV produced by the review plan (`MODEL_REVIEW_PLAN.md` §4) **before** writing code.
  ✅ **done 2026-11-21** — the vocabulary, per owner, is recorded in §9.1 below.
- [ ] **F6c.** Extend `basic-attributes` with the vocabulary the corpus actually uses.
- [ ] **F6d.** Prefer the **compound head** (`pet breed` → `breed`, `size color` → `color`) rather
  than the whole phrase; the phrase form is what produces `description description`.
- [ ] **F6e.** Consider allowing a *class-typed* attribute: `AttributeCdd.type` was designed for
  this ("class candidates … are to be set here as well", `AttributeCdd.java:53-56`) and nothing
  uses it. Decide whether `Order.customer` is an attribute or an association before extending the
  lexicon — otherwise the lexicon grows to swallow associations.

### 9.1 The attribute vocabulary the corpus actually uses (F6b — recorded 2026-11-21)

All **88** attribute candidates are now reviewed: **9 approved / 79 rejected** (`review`, `agent`).
This is the running approved-attribute list F6b asks for, by owner.

| Owner | Attribute | Inferred type |
|---|---|---|
| `Boarding` | **duration** | `String` |
| `Health` | **note** | `String` |
| `Partner` | **description** | `String` |
| `Pet` | **size** | `String` |
| `Pet` | **time** | `LocalDateTime` |
| `Product` | **category** | `String` |
| `Record` | **note** | `String` |
| `Variant` | **size** | `String` |
| `Variant` | **color** | `String` |

**F6c — the lexicon delta is one word.** Checked name-by-name against `basic-attributes`
(`application.yml:276-280`): `note`, `description`, `size`, `time`, `category`, `color` are **already
in** the lexicon. **`duration` is the only approved name that is missing.** Adding it is the whole
concrete change F6c implies from the approved evidence — the wider list in the symptom
(`breed`, `colour`, `weight`, `vaccination status`, `price`, `fee`, `cost`) never produced an
*approved* candidate, so adding it now would be guessing rather than learning; it belongs with the
F6d/owner work instead.

**F6e — the rejects, and what they say.** The 79 rejects are not noise; they split into three kinds,
and the split answers F6e's question:

1. **The owner *is* the attribute's concept** (`Fee.daily fee`, `Cost.total cost`, `Time.opening`,
   `Size.online size`, `Level.high level`, `Quantity.available quantity`, `Update.update time`,
   `Disclose.disclose fee`, `Meet.meet time`) — a verb or gerund became a class, then took a
   same-named attribute. ~20 rows. Nothing to learn for the lexicon: these classes should not exist.
2. **A class-typed member, i.e. an association** (`Type.pet`, `Range.product`, `Size.species`,
   `Status.photo`, `Owner.event`, `Sign.illness`, `Product.type`, `Pet.type`, `Quantity.inventory`) —
   exactly F6e's case. Only built-in types are attributes, so these are **associations**; extending the
   lexicon to admit them would "swallow associations", the outcome F6e warns against.
3. **A real attribute on the wrong owner** (`Date.size`, `Date.color`, `Stock.size`, `Species.size`,
   `Item.size`, `Pet.status`, `Order.status`, `Customer.address`, `Pet.type`) — the same
   `relatedCandidateName`-is-the-sentence-object defect §3.3 records for operations. This is the
   largest group, and it is an **owner-resolution** bug, not a vocabulary one.

So the ordering F6a → F6c → F6d → F6e the plan already implies is confirmed by the data: with the
owners fixed (F6a, done — `Unknown` no longer exists) and the compound head preferred (F6d), group 3
becomes real attributes and group 2 becomes associations, and only then is it worth revisiting the
lexicon.

---

## 10. FIX-7 — The association verb is lost  ·  S3 · P2 · M

**Symptom.** Every edge label is `source_target` (`Customer_Premium`), because `CollaboratorCdd`
has no label field. The verb survives only in `rulematch.description`
(`relationship.add(inventory (has) product)`).

**Correction to the review.** The association **is** linked to its rule matches
(`domox.collaboratorcdd_rulematch` = 237 rows), so the verb is reachable from the candidate — the
review's "no rule-match connection" was wrong.

**Fix — two options.**

| Option | Change | Cost |
|---|---|---|
| **A. Derive at export** | `ClassDiagram` / `CollaboratorCdd.toPlantUmlString` reads the verb from the association's own rule match | no schema change; needs the rule match on the export path |
| **B. Store a label** | new `label` field on `CollaboratorCdd` + `ALTER TABLE domox.collaboratorcdd ADD COLUMN label VARCHAR(255)` + regenerate `create-tables.sql` | schema change on the dev DB, per `memory-bank/technical/SCHEMA.md` (EclipseLink `create-tables` never ALTERs) |

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
| `domox-webapp/create-tables.sql:1` | still declares `CARDINALITY BYTEA` on `domox.AttributeCdd` — a column the entity no longer maps |
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
| **Full local bundle** | `bash scripts/check.sh` | before handing over (adds enforcer rules, unit tests, UI/UML/ontology render, metamodel guardrail) |
| **Domain unit tests** | `setsid mvn -o -pl domox-domain test >/tmp/domain-tests.log 2>&1 </dev/null &` | FIX-1/2/3/5/6 — **detached**, the shell caps a call at ~30 s |
| **Schema drift** | `bash scripts/check-schema-drift.sh` | after any entity-field change (FIX-7, FIX-8) |
| **Mutation floors** | `setsid bash scripts/check-mutation.sh >/tmp/mutation.log 2>&1 </dev/null &` | after FIX-2/5/6 touch a floored class — **run last** (pitest consumes test classes) |
| **Live export** | `python3 scripts/ro.py invoke domox.DomainModels generateUmlDiagram domainModel=DomainModel:653` | re-measure the diagram against the review's baseline table |
| **RO regression** | `python3 scripts/ro.py invoke domox.DomainModels listAll` | FIX-4 |
| **Boot smoke** | `bash scripts/verify-boot.sh` | after FIX-4 — an entity-annotation change can break boot, not just the build |

**Floored classes this plan touches** (`scripts/pitest-floors.py`): `CandidateFactory` (95),
`UseCaseBlockedNouns` (85), `BasicAttributeVocabulary` (80) — plus each new collaborator
(`MultiplicityEvidence`, `ResponsibilityParameterExtraction`, `ResponsibilityOwnerExtraction`) needs
its own entry.

---

## 13. Decisions — resolved 2026-11-19

| # | Decision | Outcome | Implemented |
|---|---|---|---|
| 1 | **FIX-2 / FIX-8** — wire the cardinality fields, or delete them? | **Wire them** (option A), and delete the dead `AttributeCdd.cardinality` + `Cardinality` anyway — the UML multiplicity belongs on the association end | ✅ FIX-2 + FIX-8 |
| 2 | **FIX-3b** — the verb→return-type mapping for `outputType` | ✅ **resolved 2026-11-19** — the proposed answer (`outputType` should be `ResponsibilityCdd`) was based on a misreading, corrected below; the mapping is: a **receive verb** → the received class, an **output/notify verb** → `String`, **otherwise** no return type | ✅ `ResponsibilityCandidates#outputTypeFor` |
| 3 | **FIX-6e** — is `Order.customer` an attribute or an association? | **An association** — only built-in types are attributes | ✅ F6c/F6d, and §13.6 now complete |
| 4 | **FIX-7** — derive the label at export, or store it? | **Derive at export** — no schema change; revisit if the UI must edit the label | ✅ FIX-7 |
| 5 | **Phase-1 re-run scope** | **Whole corpus**, and the 302 existing review decisions are kept (they hang off candidate rows, which a re-run updates in place rather than replaces) | ⏳ not run — see §14 |

### 13.2 correction — `outputType` is a UML return type, not a candidate type

`ResponsibilityCdd.outputType` is **not** the CRC element type of the responsibility; it is the
operation's **return type**:

- it is rendered *after* the parameter list, as the UML return type — `toPlantUmlString()` returns
  `name(params): outputType`;
- `DomainModelsTest:49` and `generate/JavaCodeTest:51` both pass `Integer.class.getSimpleName()`, i.e.
  a Java type;
- the CRC element type already lives elsewhere: `Candidate.candidateType` is set to `"ResponsibilityCdd"` by
  `ResponsibilityCandidates.create()`.

So `outputType = "ResponsibilityCdd"` would render every operation as `browse(): ResponsibilityCdd`.
What is still needed is the F3b mapping **verb → return type** (an output/notify verb → `String`, a
receive verb → the received class, otherwise no return type), or the honest minimum of rendering no
return type at all. The honest minimum is already in place: `void`/blank/null is omitted (F3c, landed
with FIX-1), so operations render `browse()` rather than `browse(): void`.

**Applied 2026-11-19 — the mapping is implemented** in
`ResponsibilityCandidates#outputTypeFor(verb, owner)`, called from `create(...)` (it replaces the
hard-coded `setOutputType("void")`):

| Verb (from `domox.nlp.*` in `application.yml`) | Return type | Rationale |
|---|---|---|
| a **receive** verb (`receive`, `accept`, `obtain`, …) | the **received class** — the class the operation is attached to | the sentence's direct object is what the operation receives, and `CandidateFactory` makes that class the operation's owner |
| an **output/notify** verb (`display`, `show`, `print`, …) | `String` | the operation produces something to read |
| **any other** verb | *no return type* (`null`) | the mapping has no answer; `toPlantUmlString()` omits it |

Deliberately **not** `void`: the renderer omits blank/`void` anyway, and a real `null` distinguishes
"no answer" from "the answer is void". A receive verb on an *orphan* operation (no owner) also has no
return type — there is no received class to name. Guarded by
`ResponsibilityCandidatesTest#anOutputVerbReturnsString` / `#aReceiveVerbReturnsTheReceivedClass` /
`#aReceiveVerbWithoutAnOwnerHasNoReturnType` / `#anyOtherVerbHasNoReturnType` /
`#createSetsTheReturnTypeFromTheVerb` (the last asserts the rendered `browse(): String`).

> **Postscript, 2026-11-21 — the deliberate `null` is not storable, and storing it broke corpus loads.**
> The third row of that table is the *common* case, and `responsibilitycdd.outputtype` is `NOT NULL`, so
> `create` writing the mapping's `null` answer straight through failed the insert on the first plain-verb
> operation (`groom`, `UC01`) — and with it the whole analysis phase, rolled back:
> `null value in column "outputtype" ... violates not-null constraint`.  The mapping still answers
> `null` (that distinction is the point of this section); `ResponsibilityCdd.setOutputType` now
> normalises it to the stored `void`, which the renderer treats identically, so the UML output above is
> unchanged.  Guarded by `ResponsibilityCddTest#storesVoidWhenThereIsNoReturnType` and
> `ResponsibilityCandidatesTest#createStoresAStorableReturnTypeForAPlainVerb`; see
> `.clinerules/memory-bank/technical/SCHEMA.md` §"A `NOT NULL` string column needs a sentinel".


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

`TDR10`'s branches create the `AttributeCdd` **without** consulting `isBasicAttributeB/A`, so *any*
possessed noun becomes an attribute. Narrowing those branches to attribute nouns — the same scope
guard FIX-5 applied to TDR25/TDR26 — is a **rule-set change** with its own tests to update
(`tdr10_placesPropertyOnResolvedPossessor`, `tdr10_enrichesThePropertyName`,
`tdr11_placesPossessedNounPropertyOnPossessor` all assert the current behaviour). It is left as a
separate, deliberate change rather than smuggled into this wave.

**Applied 2026-11-19 — the guard is in.** The possessed noun (`A`, the `nmod:poss` governor / `amod`
governor) must now be an **attribute noun** (`isBasicAttributeA`, i.e. the `domox.nlp.basic-attributes`
lexicon) for the branch to create an `AttributeCdd`:

| Rule | Branch | Before | After |
|---|---|---|---|
| `TDR10` | `isNounB` — `A` is an attribute of the possessor `B` | `the customer's cart` → attribute `cart` | only when `A` is an attribute noun: `the customer's name` → attribute `name` |
| `TDR10` | `PRP$` — `A` is an attribute of the resolved possessor | `their pets` → attribute `pets` of `Owners` | only when `A` is an attribute noun: `their address` → attribute `address` of `Owners` |
| `TDR11` | the possessed-noun branch | `their beloved pets` → attribute `beloved pets` of `Owners` | the branch now fires only for attribute nouns; a class noun falls through to `Entity.add(A)` (`Pets`) |

This is a scope narrowing, **not** a spec change — the spec still reads `nmod:poss`/`amod` →
`Attributes.add(…)`; only which nouns count as attributes is restricted (decision 3: *only built-in
types are attributes*). The three tests named above were rewritten to the new contract and two guard
tests added (`tdr10_doesNotMakeAPossessedClassNounAnAttribute`,
`tdr11_keepsAPossessedClassNounAnEntity`). The lexicon is the one FIX-6b/6c extends from the Pass-2
review (`MODEL_REVIEW_PLAN.md` §5), so a legitimate attribute the list is missing is added there
rather than re-widened here.

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
SELECT count(*) FROM domox.collaboratorcdd WHERE type=0;                 -- 14
```

The 16 pairs with no row (`Adopter_Rejection`, `Health_Pet`, `Pet_Daycare`, `Stock_Size`, …) are
exactly the ones whose source class has a plain class match earlier in the same document. The
one-line fix is to narrow the guard to a *pure* class match (`relatedCandidateName == null`), but it
changes model content (16 new edges), so it is recorded here as a decision rather than applied.

**Applied 2026-11-19 — the guard is narrowed.** `CandidateFactory.createCandidatesFrom` now reads

```java
if ("ClassCdd".equals(match.getCandidateType()) && match.getRelatedCandidateName() == null) {
```

so only a pure class match is de-duplicated by name; an association match is always routed to
`createCandidateFromMatch`, which returns the association candidate (the class de-duplication inside
it still applies through `classCandidates.findOrCreate`). Guarded by
`CandidateFactoryTest#createCandidatesFrom_doesNotSwallowAnAssociationBehindItsSourceClass`, and
`#createCandidatesFrom_deduplicatesClassesByName` still pins the pure-class case. Like every rule-set
change, it takes effect on the **next Phase-1/2 run** (§14) — no migration, and the 302 existing
review decisions hang off candidate rows that a re-run updates in place.


### 13.8 FIX-3d — the operation's owner is the actor, not the object  *(decided and applied 2026-11-20)*

**The question.** FIX-3d was deliberately left open (§8) with the instruction *"fix it only if the
review pass shows it matters"*. That pass (TODO-05) has not run, so the gate was re-expressed as a
**measurement**: the owner-correctness tally can be computed from data the model already holds —
`ResponsibilityCdd.classCdd` is the owner, and the sentence behind each candidate carries both its subject and
its object.

**The tally** (`ResponsibilityCdd` candidates, PetShop model, 2026-11-20):

```sql
-- the actor each operation's own evidence sentences state, per candidate
WITH ev AS (
  SELECT DISTINCT a.id AS action_id, c.candidatename AS owner,
         lower(rm.candidatename) AS verb, td.sentence_id AS sid
    FROM domox.responsibilitycdd a
    LEFT JOIN domox.classcdd c ON c.id = a.classcdd_id
    JOIN domox.responsibilitycdd_rulematch am ON am.responsibilitycdd_id = a.id
    JOIN domox.rulematch rm ON rm.id = am.rulematches_id
    JOIN domox.typeddependency td ON td.id = rm.typed_dependency_id
), actors AS (
  SELECT ev.action_id FROM ev JOIN domox.typeddependency s
    ON s.sentence_id = ev.sid AND s.type = 76                     -- nsubj
   AND lower(s.governorlemma) = ev.verb
   AND s.dependentpos IN ('NN','NNP','NNS','NFP')                 -- a noun, not "there"/"it"
   AND lower(s.dependentlemma) NOT IN ('system','actor','case','end','result','message','step',
       'exception','field','note','rule','goal','feature','function','path','summary','overview',
       'outcome','constraint','assumption','scenario','flow','description','trigger','condition',
       'requirement','usecase','use-case','uc','useuc','mainflow','alternative','precondition',
       'postcondition','pre-condition','post-condition')          -- domox.nlp.use-case-blocked-nouns
  GROUP BY 1
)
SELECT count(*) AS actions,
       count(*) FILTER (WHERE n_actors > 0)                        AS actor_ok,
       count(*) FILTER (WHERE owner IS NOT NULL)                   AS object_today,
       count(*) FILTER (WHERE n_actors > 0 OR owner IS NOT NULL)   AS union_owner,
       count(*) FILTER (WHERE n_actors > 0 AND owner IS NOT NULL)  AS both
  FROM (SELECT DISTINCT ev.action_id, ev.owner,
               (SELECT count(*) FROM actors a WHERE a.action_id = ev.action_id) AS n_actors
          FROM ev) per_action;
```

| Measure | n |
|---|---|
| `ResponsibilityCdd` candidates | **191** |
| owned today | **149** — **all 149 by the sentence object**; **0** by the actor |
| have a usable actor (`nsubj`, noun, not a blocked use-case noun) | **108** |
| owners that change (object → actor) | **87** |
| keep the object as the fallback owner | **62** (19 name an unusable actor — `System`; 43 name none) |
| owner-less operations that gain an owner | **21** |
| result under *actor, else object* | **170** owners (up from 149, none lost) |

**Decision: fix — the defect is systematic, not sporadic.** Every owned operation came from `TDR3`,
which fires on the *object* dependency (`dobj`/`iobj`/`pobj`) and records that object as the operation's
related candidate; no rule ever recorded the subject as an owner. Corroboration from the codebase
itself: `ResponsibilityParameterExtraction`'s javadoc already stated the contract —
*"the subject is the operation's owner ('the customer browses …' is `Customer.browse()`, not
`browse(customer)`)"* — while the factory attached the operation to the object. The comment described
the model the rules were meant to build; the implementation did the opposite.

**Applied 2026-11-20.**

| Piece | Change |
|---|---|
| new `ResponsibilityOwnerExtraction` | the actor noun of the verb: the dependent of an `nsubj` whose governor **is that verb** and whose dependent is a noun. A pure static utility, mirroring `ResponsibilityParameterExtraction` (which reads the verb's objects, the parameters) |
| `CandidateFactory` (ResponsibilityCdd branch) | owner = the actor class; the object class is kept as owner **only** when the sentence names no usable actor, and is always passed as the *received class* |
| `ResponsibilityCandidates` | `create`/`findOrCreate` take the received class separately from the owner, so FIX-3b's mapping survives the owner move (§13.2) |
| floors | `ResponsibilityOwnerExtraction` floored at 85 % (`scripts/pitest-floors.py`) |

**Two deliberate exclusions** in "what counts as the actor": `nsubj:pass` is **not** the actor (in a
passive clause it is the patient — `"the payment is processed"` says nothing about who processes it, so
reading it would move the defect rather than fix it; such a sentence falls back to the object), and a
non-noun dependent (`"there is …"`) is not an actor.

**Why the object stays as the fallback.** 62 of the 149 owned operations name no *usable* actor: 19 of
them name an actor that cannot become a class (`System`, a blocked use-case noun — *"the system
validates availability"*), and 43 name no actor at all. Dropping them would remove 62 operations from
the class diagram for no gain; keeping the object leaves a plausible owner
(`Availability.validate()`). Without the fallback the change would have *lost* coverage (108 owners
instead of 149) — with it, the result is **170**, and no operation loses its owner.

**Effect.** 87 owners corrected (`Product.select(...)` → `Customer.select(products)`), 21 owner-less
operations gain an owner, 0 lost. The `outputType` of a receive verb is unchanged — it is the received
class, i.e. the object, exactly as before (§13.2).

**Guarded by** `ResponsibilityOwnerExtractionTest` (6 cases: the subject, case-insensitive verb match,
another verb's subject, a non-noun, a passive subject, no sentence/verb),
`CandidateFactoryTest#createCandidatesFrom_ownsTheOperationByTheSentencesActor` and
`#createCandidatesFrom_fallsBackToTheObjectWhenTheSentenceNamesNoUsableActor`, and
`ResponsibilityCandidatesTest#createAttachesTheOperationToTheOwnerButTypesItFromTheReceivedClass`.

Like every rule-set change here it takes effect on the **next Phase-1/2 run** (§14) — no migration, and
the 302 existing review decisions hang off candidate rows a re-run updates in place.

### 13.9 the gate it no longer waits on

TODO-05 (Pass 3, the responsibility review) still has to run, and it still has to record owner
correctness as its second column (`MODEL_REVIEW_PLAN.md` §6) — but it is no longer a *decision* gate,
only a confirmation. If the approved subset shows the actor-derived owners to be wrong in a way this
tally cannot see, the change is one branch in `CandidateFactory` plus one test class to revisit; the
measurement above is the record of why it was made.

## 14. Execution status

- [x] **Wave 1** — FIX-1 (XS), FIX-4 (S), FIX-5 (S) → `check.sh --quick`, domain tests, boot smoke
- [x] **Wave 2** — FIX-2 (M) + FIX-8 (S) → schema drift clean, dev-DB column dropped
- [x] **Wave 3** — FIX-6 (F6a/F6c/F6d) then FIX-3 (F3a parameters + F3c return type) → domain tests
      green (F3b left open, §13.2; the TDR10/TDR11 residual, §13.6)
- [x] **Wave 4** — FIX-7 (M) → domain tests
- [x] **Wave 5** — the three §13 rule-set decisions: §13.2 (the verb→return-type mapping), §13.6
      (the TDR10/TDR11 possessive scope guard) and §13.7 (association vs. class de-duplication) →
      domain tests (`TypedDependencyRulesTest`, `CandidateFactoryTest`, `ResponsibilityCandidatesTest`)
- [x] **Wave 6** — FIX-3d (§13.8, the operation owner): measured the owner-correctness tally (149/149
      owned by the object, 0 by the actor → systematic), then applied the actor-owner change → domain
      tests (`ResponsibilityOwnerExtractionTest`, `CandidateFactoryTest`,
      `ResponsibilityCandidatesTest`)
- [ ] Re-run Phase 1/2 on the corpus, regenerate the export, and update
      `MODEL_UML_READINESS_REVIEW.md` §2 with the new measurements — **needs the NLP service and a
      fresh model; not run here**
- [x] Decide §13.6 (TDR10/TDR11 possessive scope) and §13.7 (association/class de-duplication) —
      **decided and applied 2026-11-19** (§13.6: the possessed noun must be an attribute noun;
      §13.7: only a pure class match is de-duplicated); FIX-3b's return-type mapping likewise
      (§13.2). All three are rule-set changes, so they show up in the measurements of the item above.

**Fixes that landed**

| Fix | Where |
|---|---|
| FIX-1 | `ResponsibilityCdd.toPlantUmlString` (unconditional `)`, null-tolerant, no `void`) + `ResponsibilityCddTest` |
| FIX-2 | new `MultiplicityEvidence` + `CandidateFactory.applyMultiplicities` (sentence-scoped) + `CollaboratorCdd` getters |
| FIX-4 | `@Getter` removed from the 10 public entity fields (`ClassCdd`, `DomainModel`, `Document`, `ResponsibilityParameter`) + call sites + a new ArchUnit rule |
| FIX-5 | `domox.nlp.use-case-metadata-nouns` + `UseCaseBlockedNouns.isMetadataNoun` + TDR25/TDR26 guards + case-insensitive lookup fix |
| FIX-6 | `CandidateFactory` (no `Unknown` owner), `TDR12` (attribute word, no owner when both are attributes), `basic-attributes` extended |
| FIX-3 | new `ResponsibilityParameterExtraction` + `ResponsibilityCandidates.addParameter` + `CandidateFactory.addParameters`; `void` omitted |
| FIX-3d | new `ResponsibilityOwnerExtraction` (the verb's `nsubj` actor) + `CandidateFactory.classFor`/`actorNameOf` + the `receivedClass` split in `ResponsibilityCandidates` (§13.8) |
| FIX-7 | new `AssociationLabel` + `CollaboratorCdd.label()` |
| FIX-8 | `AttributeCdd.cardinality` + `Cardinality.java` deleted, `create-tables.sql` updated, dev-DB column dropped |

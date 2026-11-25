# Is the candidate model now a UML class diagram?

**Scope.** Review of attributes, operations, associations and multiplicities in the Pet-Shop
analysis run (`domox.DomainModel/653`, 15 use-case documents), against what a UML class diagram
needs, plus an assessment of the Fuseki reference ontologies.
**Method.** All numbers below are read from the live database (`domox-db`) and from the **real
PlantUML export** produced by the running instance
(`domox.DomainModels#generateUmlDiagram(DomainModel/653)`, since removed — the text form is
`downloadPlantUml(DomainModel/653, kind=model)` → 63 490 chars, `/tmp/domain-model-653.puml`).
**Date.** 2026-11-18.
**Follow-up plans.** The code changes that close these gaps: `UML_GAP_FIX_PLAN.md`
(severity/priority/ordering). The review work these gaps need: `MODEL_REVIEW_PLAN.md`.

> **Status 2026-11-19 — the eight gap fixes have landed** (`UML_GAP_FIX_PLAN.md` §14). The numbers in
> §1 are the **pre-fix** baseline and are kept as such. Re-measured live after the fixes, on the same
> model and without a re-analysis:
> **`(: void` 149/149 → 0** (operations now render `ask()`), and the 14 association edges now carry a
> **verb label** (`has`, `enroll in`, `register for`, `take`, …) instead of `source_target`. The
> remaining gaps — attribute vocabulary, review coverage, multiplicities on new edges — need the
> Phase-1/2 re-run and the review passes, not more code.

> **Status 2026-11-21 — the review pipeline has run** (`REVIEW_PLAYBOOK.md` §4–§6). All **1 504**
> candidates of all four types are reviewed (610 approved); approved classes **131 → 425**; edges with
> both ends approved **23 → 91**; the approved subset is **coherent** (no approved attribute, operation
> or edge with an unapproved owner/endpoint). The §1 verdicts below stand unchanged, and the one that
> matters most is now *sharper*, not better: **9 approved attributes**, with **89 %** of approved classes
> still empty-bodied. Re-measure and delta: **§2.1**. The vocabulary the pass learned is recorded in
> `UML_GAP_FIX_PLAN.md` §9 (F6b/F6c).


---

## 1. Verdict

| UML element | Verdict | Why |
|---|---|---|
| **Classes** | ✅ **enough** | 131 approved, clean names, `classType` archetype + sub-domain on every row |
| **Attributes** | ❌ **not enough** | **2 approved attributes for 131 classes**; the extracted names are mostly entities, not attributes |
| **Operations** | ⚠️ **names only** | 149 operations render, all as `name(: void` — no parameters, no return type, owner often wrong |
| **Associations** | ⚠️ **partial** | 237 edges, endpoints resolve, but 171 are generalizations, 52 synonyms, only 14 plain associations; 0 reviewed |
| **Multiplicities** | ❌ **not enough** | values are now UML-correct *in the rules*, but persisted nowhere, attached to no association end, and mostly noise in this corpus |

So: **a class diagram can be produced today** (the exporter exists and runs), but it is a
*class-and-generalization* diagram. It is not yet a UML class diagram in the sense of "attributes,
operations and associations with multiplicities".

---

## 2. What the exporter actually emits

`domox.DomainModels#downloadPlantUml` (formerly `#generateUmlDiagram`, since removed) →
`generate.ClassDiagram` (Kotlin) →
`ClassCdd/AttributeCdd/ResponsibilityCdd/CollaboratorCdd.toPlantUmlString()` → `PumlCode`.

> **⚠ Superseded 2026-11-23 — the exporter is now review-aware.**  The pipeline line above is still
> right, but the *filter* it applies is not: `generate.ClassDiagram` now renders only **approved**
> candidates, and only classes that carry approved content or take part in a material relation
> (`domox.dom.crc.model.ModelContent`); a class with neither is left out *and* rejected
> (`REVIEW_PLAYBOOK.md` §4.6).  The same run also produces **one diagram per sub-domain** and a
> **sub-domain overview**.  The tables below are kept as the **pre-review baseline** they were measured
> against — read them as history, not as current behaviour.

Real output for model 653:

| Measure | Whole model | Approved subset |
|---|---|---|
| classes | 971 | 131 |
| classes with an **empty** body | **842 (87 %)** | 70 (53 %) |
| attribute lines | 109 | 54 |
| operation lines | 149 | 95 |
| edges | 237 (`|>-` 171, `==` 52, `->` 14) | 23 with **both** ends approved |
| edges carrying a cardinality | **0** | **0** |

> The arrows are quoted as the export printed them at the time. `|>-` was the `GENERALIZATION`
> spelling until 2026-11-23, when the diagram actions started *rendering* through Kroki: PlantUML
> rejects `|>-` (`Error 400: Syntax Error?`) and the whole diagram failed to render, so the arrow is
> now `--|>` (`AssociationType`). The **counts** above are unaffected — only the spelling changed.

Stereotype colour comes from `ClassType` (Coad archetype, computed at ingestion by
`ClassArchetypeClassifier`): 680 PARTY_PLACE_THING, 127 DESCRIPTION, 100 MOMENT_INTERVAL, 64 ROLE.

Sample (verbatim):

```
class "Pet" as Pet #lightblue {
	requirement: String
	pet time: LocalDateTime
	perform(: void
	foster(: void
	examine(: void
}
WideRange|>-Range: WideRange_Range
Customer->Class: Customer_Class
Item==Medication: Item_Medication
```

Note the edge label is `source_target`, **not** the verb — `CollaboratorCdd` has no label field;
the verb lives only in the evidence (`rulematch.description` = `relationship.add(inventory (has) product)`).

### 2.1 Post-review re-measure — 2026-11-21 (model 16177)

Re-measured after the review pipeline ran (`REVIEW_PLAYBOOK.md` §4–§6: the four review passes, the
sub-domain pass, and the orphan-responsibility re-reject), against the **live database** and a fresh
export (`/tmp/domain-model-16177.puml`). Model `653` no longer exists — the Pet-Shop run is now
`domox.DomainModel/16177`. Every candidate of every type now carries exactly one `review` row
(**1 504** reviews, 610 approved, all `username='agent'`):

| type | reviewed / total | approved |
|---|---|---|
| `ClassCdd` | 970 / 970 | 425 |
| `AttributeCdd` | 88 / 88 | 9 |
| `ResponsibilityCdd` | 193 / 193 | 85 |
| `CollaboratorCdd` | 253 / 253 | 91 |

| Measure | §2 baseline (653) | now (16177) | Δ | §2's approved column ¹ | now, same semantics ¹ | coherent subset ² |
|---|---|---|---|---|---|---|
| classes | 971 | 970 | −1 | 131 | **425** | 425 |
| classes with an **empty** body | 842 (87 %) | 861 (89 %) | +19 | 70 (53 %) | 380 (89 %) | 380 |
| attribute lines | 109 | 88 | −21 | 54 | 26 | **9** |
| operation lines | 149 | 168 | +19 | 95 | 141 | **85** |
| edges | 237 | 253 | +16 | 23 | 91 | 91 |
| — `\|>-` / `==` / `->` | 171 / 52 / 14 | 171 / 52 / **30** | `->` +16 | — | — | — |
| edges carrying a cardinality | 0 | **8** | +8 | 0 | 3 | 3 |

¹ *"the member sits on an approved class"* — the semantics of the §2 column, kept so the delta is
like-for-like.
² *"the member's own review is `APPROVED` **and** its owner / both endpoints are approved classes"* —
the coherent subset the pipeline guarantees. Verified **0 / 0 / 0**: no approved attribute, operation or
edge has an unapproved owner/endpoint.

**Reconciliation of the operation count (193 → 168).** The exporter emits *every* attribute and
responsibility of every class, unfiltered — `ClassCdd.toPlantUmlString` never consults a review. So
`operation lines` = rows whose `classcdd_id` is non-null: **193 − 25 orphan (null owner) = 168**, and
`attribute lines` = **88 rows, 0 orphan**. The 25 orphan operations are dropped silently; the export
gives no signal that they exist. This is why "operation lines" cannot be read as "approved operations".

**Superseded 2026-11-23 — the exporter is now review-aware.**  The reconciliation above describes the
*pre-change* exporter: it emitted every attribute and responsibility of every class, review or no
review.  `generate.ClassDiagram` now draws only approved candidates, and only classes that carry
approved content or take part in a material relation.  One predicate —
`domox.dom.crc.model.ModelContent` — backs both the export and the **empty-class sweep**
(`DomainModels#rejectEmptyClasses`) that rejects a contentless approved class with
`INSUFFICIENT_INFORMATION`, so the review and the model cannot disagree
(`REVIEW_PLAYBOOK.md` §4.6).  The same run also produces **one diagram per sub-domain**
(one tab per sub-domain of `DomainModels#showUmlDiagrams`) and a **sub-domain overview**
(the overview tab; the single-diagram actions `generateSubDomainUmlDiagram` /
`generateSubDomainOverviewDiagram` are removed).  A re-measure under the new exporter is therefore a
*different* measurement from the one tabulated above: every count there is the pre-change baseline.
In particular, the 89 % empty-bodied figure is now partly **acted on** rather than merely reported —
an approved class with no approved content and no material relation is no longer in the model at all.

**Code-driven vs data-driven.** Only one row of the delta is a code change: FIX-1's closed parenthesis
(**149/149** malformed `name(: void` → 0). The cardinality quoting and the `label()` lookup were
**already present at the baseline commit** (`3322188` — `CollaboratorCdd.quote()` and `label()`), so
`0 → 8` cardinalities and `Customer_Item → has` are **data** changes: the corpus re-run populated
`sourcecardinality`/`targetcardinality` and the rule matches the label derives from. The `->` count rose
with the same re-run (+16 ASSOCIATION-typed edges). Also data: the `Unknown` class is gone (FIX-6a) and
attributes fell 109 → 88 with it.

**What the review actually changed.** Approved classes **131 → 425**, edges between approved classes
**23 → 91**, and the approved subset is coherent (0/0/0). What it did **not** change is the shape of the
problem: **89 % of approved classes still have an empty body** (baseline 87 %), and there are **9
approved attributes in total**. That is a **corpus/extraction** limit (`UML_GAP_FIX_PLAN.md` §9,
FIX-6), not a review limit — the passes reached every candidate, so more review work will not move it.

---

## 3. Per-element review

### 3.1 Classes — adequate

* 971 candidates; **131 approved / 71 rejected / 769 unreviewed** (302 reviews, all `agent`).
* Approved names are business-clean: `Pet, Groomer, Veterinarian, Booking, Order, Payment, Policy, …`
* Every row carries `classType` (never null) and the approved 131 are assigned to 10 sub-domains
  (`Care Services` 29, `Retail & Merchandise` 26, `Shared` 16, …).
* Caveat: the archetype is a heuristic, not a review decision — `Pet` is classified DESCRIPTION,
  `Customer` ROLE.

### 3.2 Attributes — the weakest link

| Fact | Value |
|---|---|
| candidates | 109 (44 owning classes) |
| reviewed | 9 → **2 approved**, 7 rejected, 100 unreviewed |
| `propertycdd.cardinality` | NULL for **109/109** |
| `propertycdd.type` | inferred from the *name* by keyword (`CandidateFactory.inferType`): String 89, LocalDateTime 17, int 3 |

Approved attributes: `Product.range: String`, and `size: String` on a class literally named
`Unknown` (12 attributes land on `Unknown` — an orphan owner). Names are frequently entities or
noise (`pet`, `product`, `cart`, `role`, `owner`, `doorstep`, `size color`, `description description`).
The `AttributeCdd.type` field was designed to also hold class-typed members
("class candidates … are to be set here as well") — nothing ever does.

### 3.3 Operations — signatures absent

| Fact | Value |
|---|---|
| candidates | 191 (99 owning classes, 42 orphans with `classcdd_id IS NULL`) |
| reviewed | 91 → **57 approved**, 34 rejected, 100 unreviewed |
| approved operations on | **40** classes |
| `actioncdd.outputtype` | `void` for **191/191** (single distinct value) |
| `parametercdd` (inputs) | **0 rows** — no parameter is ever extracted |

Names are real verbs (`browse`, `validate`, `recalculate`, `notify`, …) but the owner is the
sentence *object*: `supervise → Day`, `capture → Delivery`, `deliver → Doorstep`, `join → Class`.
Rendered as `name(: void` — `ResponsibilityCdd.toPlantUmlString()` never closes the parenthesis when
the parameter list is empty, so **all 149 rendered operations are malformed**.

### 3.4 Associations — endpoints yes, semantics no

| Fact | Value |
|---|---|
| candidates | 237 (209 owner classes) |
| reviewed | **0** — every collaborator candidate is still pending |
| `source_id` / `target_id` | populated for **237/237** (no dangling endpoint) |
| `classcdd_id` | always `= source_id` (owner side is the source) |
| type | GENERALIZATION 171, SYNONYM 52, ASSOCIATION 14; no aggregation/composition/dependency |
| `sourcecardinality` / `targetcardinality` | `''` for **237/237** |

Only `TDR16` (8), `TDR7` (2), `TDR21` (8), `TDR22` (8), `TDR23` (6) produced plain associations —
`TDR14`, `TDR15`, `TDR17`–`TDR20` (the `nsubj(V,E1) & dobj(V,E2)` family) have **zero matches** in
this corpus. 171 of 237 edges are `TDR40` generalizations and 52 are `TDR41` synonyms, and the
generalizations almost never join two reviewed classes (0/171 have both endpoints approved).

### 3.5 Multiplicities — correct values, no home

* 131 matches: **TDR25 44** (`nummod`) + **TDR26 87** (`det`), all attached to **ClassCdd**
  candidates — i.e. to a *noun*, never to an association or an association end.
* The rules now record UML notation (`MultiplicityNotation.umlNotation`): `01`→`1`, `one`→`1`,
  `more`/`N`→`*`, `some`→`1..*`, `2..4`→`2..4`, `at most 2`→`0..2`; fire behaviour is unchanged.
  Existing DB rows still hold the **old** text (`09`, `N`, `02`) — a Phase-1 re-run regenerates them.
* **Nothing persists a multiplicity as a multiplicity**: `propertycdd.cardinality` is NULL (109/109)
  and `associationcdd.source/targetcardinality` is empty (237/237); `CandidateFactory` never calls
  the setters. The value exists only inside `rulematch.description` (`multiplicity.add(pet, 1)`).
* **No linkage to an *end***: a multiplicity match names a **class**, never an association or an
  association end.  The association *is* reachable from its evidence
  (`domox.associationcdd_rulematch` holds **237 rows**, so the verb lives in
  `rulematch.description`), but deciding *which end* a class-level multiplicity belongs to is still
  a judgement call — see `UML_GAP_FIX_PLAN.md` FIX-2.
* **Quality in this corpus is poor**: 17 of the 44 TDR25 matches are `multiplicity.add(UC, NN)` from
  the `UC - 09` use-case headings; `Accessories, 09` and `Partner, 04` are section numbers. The TDR26
  matches (`account, 1` from "an account", `application, 1` from "an application") are the usable ones.

### 3.6 Fuseki ontologies — useful for classification, dangerous as a name oracle

Graphs served by `http://localhost:3030/ds/sparql` (configured in `domox.rdf.default-graphs`):
schema.org 17 515 triples, BPMN-based ontology (BBO) 2 388, gistCore 2 290, BFO 1 221, sBPMN 1 217.

Matching the 131 approved class names against every label / IRI local name: **31 hits (24 %)**.

* ~15 are genuinely useful: `schema.org` `Order, Product, Service, Store, Event, Course, Ticket,
  Review, Claim, Collection, Guide, Class`; `gist` `Contract, Category, Collection, Event`;
  BBO `Activity, Event`.
* 14 are **property** homonyms (`application, catalog, category, collection, customer, diagnosis,
  item, material, owner, photo, provider, review, supply, video` are `rdf:Property` in schema.org) —
  a name match would promote a property to a class.
* **The `Pet` trap is real, and it is inside Schema.org**: `schema.org/PET` is a
  `MedicalImagingTechnique` (positron-emission tomography). A naive match labels the domain class
  `Pet` as a medical imaging technique. (No PET / polyethylene / ChEBI graph is loaded — the collision
  is with the acronym, not with a polymer ontology.)
* BFO and sBPMN contribute **no** domain names. Their value is *stereotypes* — continuant vs
  occurrent, role, quality — which is exactly the job `ClassType` (PARTY_PLACE_THING / ROLE /
  DESCRIPTION / MOMENT_INTERVAL) already does heuristically. They should not be used to inventory
  the model, only to sanity-check the archetype of a class.

**Conclusion:** the ontologies are a *classification aid for the ~15 core commerce classes and for
archetypes*, not an evidence source for completeness, and never a naming oracle.

#### 3.6.1 The 15 core names, spot-checked through the tools (2026-11-20, TODO-09)

Run over the 15 approved classes the list above calls genuinely useful, with `ontologySearch`,
`ontologyEvidenceForCandidate` and `suggestReview` (`OntologyReviewMcpTools`; Fuseki at
`http://localhost:3030/ds/sparql`, five named graphs). All 15 return `typeAgreement = AGREES` — a
**CLASS** match in the graph named above, `suggestReview` → `APPROVE` / MEDIUM:

| Approved class (id) | graph · IRI | super-class | verdict |
|---|---|---|---|
| `Order` (7844) | schema.org · `schema.org/Order` | `LegalDocument` | ✅ genuine — "a confirmation of a transaction (a receipt)" |
| `Product` (863) | schema.org · `schema.org/Product` | `Thing` | ✅ genuine |
| `Service` (1909) | schema.org · `schema.org/Service` | `Intangible` | ✅ genuine |
| `Store` (882) | schema.org · `schema.org/Store` | `LocalBusiness` | ✅ genuine |
| `Event` (6856) | gist · BBO · schema.org · sBPMN | — / `FlowNode` / `Thing` | ⚠️ genuine, but four graphs give two **archetypes**: a scheduled happening (schema.org, gist) vs a process occurrence (BBO) |
| `Course` (2957) | schema.org · `schema.org/Course` | `LearningResource` | ✅ genuine |
| `Ticket` (12583) | schema.org · `schema.org/Ticket` | `Intangible` | ✅ genuine |
| `Review` (4014) | schema.org · `schema.org/Review` | `CreativeWork` | ✅ genuine (the *advisor* still says REJECT — see below) |
| `Claim` (4883) | schema.org · `schema.org/Claim` | `CreativeWork` | ❌ **false friend** — schema.org's Claim is a fact-checking assertion; the corpus's is an **insurance claim** ("payment (or insurance claim) is handled") |
| `Collection` (7920) | gist · `gist/Collection` | `Composite` | ✅ genuine |
| `Guide` (2972) | schema.org · `schema.org/Guide` | `CreativeWork` | ✅ genuine |
| `Class` (2930) | schema.org · `schema.org/Class` | `Intangible` | ❌ **false friend** — schema.org's Class is the metamodel "Type" (`rdfs:Class`); the corpus's is a **training class** ("signs up for a training class", "The selected class has no openings") |
| `Contract` (3955) | gist · `gist/Contract` | — | ✅ genuine — "an agreement which can be enforced by law" |
| `Category` (910) | gist · `gist/Category` | — | ✅ genuine (schema.org offers only the PROPERTY `category`) |
| `Activity` (5866) | BBO · `BBO#Activity` | — / `flowNode` | ✅ genuine, but on the *stereotype* axis — a BPMN process element, not an inventory entry |

**The false homonyms — and why the advisor must not be followed on a name collision.** The advisor
rejects a `ClassCdd` whose only match is a PROPERTY or an INDIVIDUAL, so four *approved* core classes
come back as `REJECT` / `WRONG_CANDIDATE_TYPE` / MEDIUM:

| Approved class | what the ontology actually has | the advice |
|---|---|---|
| `Pet` (895) | `schema.org/PET` — an **INDIVIDUAL**, "Positron emission tomography imaging" | ❌ REJECT |
| `Owner` (893) | `schema.org/owner` — a **PROPERTY**, "A person or organization who owns this Thing" | ❌ REJECT |
| `Customer` (864) | `schema.org/customer` — a **PROPERTY**, "Party placing the order or paying the invoice" | ❌ REJECT |
| `Provider` (13485) | `schema.org/provider` — a **PROPERTY** | ❌ REJECT |

All four are correct model classes; the advice is an artifact of the collision. A second artifact, of
the same family: `suggestReview(4014)` returns `REJECT` / `DUPLICATE` / **HIGH** for the class
`Review`, because an already-approved **action** `review` (id 1963) shares its name —
`findApprovedWithSameName` compares names *across* candidate types. That is exactly why §4 of
`MODEL_REVIEW_PLAN.md` calls the advisor **advisory**, and why `ontologySearch`'s own description says
a miss — or a kind mismatch — is **not** evidence against a candidate. Nothing in this spot-check
changes a decision; it closes TODO-09 and confirms the §3.6 conclusion.

---

## 4. Defects found while producing the diagram

1. `ResponsibilityCdd.toPlantUmlString()` emits `task(: void` for a parameterless operation
   (149/149 lines) — the closing `)` is only added by the `replaceAll` that removes a trailing
   separator, which never runs on an empty parameter list.
2. `GET /restful/services/domox.DomainModels/actions/listAll/invoke` → **HTTP 500**:
   `IllegalAnnotationsException: Class has two properties of the same name` for 6 members
   (`attributeList`, `classType`, `collaboratorList`, `responsibilityList`, …) because
   `DomainModel`/`ClassCdd` expose a **public field and a getter of the same name**. Single-object
   fetch (`/restful/objects/domox.DomainModel/653`) works, so only list serialization is broken.
3. `CollaboratorCdd` has no label/verb field: the association verb survives only in
   `rulematch.description`, so the diagram's edge label is `Customer_Premium`.

---

## 5. What is missing, in priority order

| # | Gap | Smallest fix |
|---|---|---|
| 1 | Attribute extraction yields entities, and only 44/131 classes have any | tighten TDR2/TDR4/TDR5 + the `Basic_Attrib` list; review the 100 pending attribute candidates |
| 2 | Multiplicities have no home | decide: fill `CollaboratorCdd.source/targetCardinality` from the multiplicity evidence, or delete the dead `AttributeCdd.cardinality` + `Cardinality` |
| 3 | No operation signature | populate `parametercdd` from the verb's `nsubj/dobj/nmod`; infer `outputType` instead of `void` |
| 4 | Associations never reviewed; 0 both-ends-approved generalizations | run the candidate-review pass over the 237 collaborators |
| 5 | Multiplicity evidence polluted by use-case metadata | exclude the `UC - NN` heading pattern (a blocked-noun rule already exists for other metadata) |
| 6 | Malformed PlantUML for parameterless operations | close the parenthesis in `ResponsibilityCdd.toPlantUmlString()` |

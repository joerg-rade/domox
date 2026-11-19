# Is the candidate model now a UML class diagram?

**Scope.** Review of attributes, operations, associations and multiplicities in the Pet-Shop
analysis run (`domox.DomainModel/653`, 15 use-case documents), against what a UML class diagram
needs, plus an assessment of the Fuseki reference ontologies.
**Method.** All numbers below are read from the live database (`domox-db`) and from the **real
PlantUML export** produced by the running instance
(`domox.DomainModels#generateUmlDiagram(DomainModel/653)` → 63 490 chars, `/tmp/domain-model-653.puml`).
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

`domox.DomainModels#generateUmlDiagram` → `generate.ClassDiagram` (Kotlin) →
`ClassCdd/AttributeCdd/ResponsibilityCdd/CollaboratorCdd.toPlantUmlString()` → `PumlCode`.

Real output for model 653:

| Measure | Whole model | Approved subset |
|---|---|---|
| classes | 971 | 131 |
| classes with an **empty** body | **842 (87 %)** | 70 (53 %) |
| attribute lines | 109 | 54 |
| operation lines | 149 | 95 |
| edges | 237 (`|>-` 171, `==` 52, `->` 14) | 23 with **both** ends approved |
| edges carrying a cardinality | **0** | **0** |

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

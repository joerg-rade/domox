# AssociationType Categorization: From Natural-Language Patterns to UML Relationships

## Overview

The `AssociationType` enum (`domox.dom.crc.AssociationType`) defines seven relationship types that mirror standard UML:

| Enum Value | Symbol | UML Name | Default? |
|------------|--------|----------|----------|
| `ASSOCIATION` | `->` | Association | ✅ default |
| `GENERALIZATION` | `|-` | Generalization (inheritance) | — |
| `AGGREGATION` | `*->` | Aggregation | — |
| `COMPOSITION` | `+->` | Composition | — |
| `DEPENDENCY` | `.>` | Dependency | — |
| `IMPLEMENTATION` | `` | Realization | — |
| `SYNONYM` | `==` | Semantic equivalence (TDR41) | — |

Currently `ASSOCIATION` (default), `GENERALIZATION` (`GeneralizationCdd` → subtype) and, since TDR41, `SYNONYM` (`SynonymCdd` → semantic-equivalence link) are actively assigned by rules. The remaining types are declared but rarely assigned, losing semantic nuance that the natural-language patterns already contain.

---

## 1. GENERALIZATION — "is a" / identity-based

Linguistic pattern signals that one class is a subtype of another.

| Rule | NL Pattern | Example | Semantics |
|------|-----------|---------|-----------|
| TDR38 | `nsubj(Parent, Child) + cop(Parent, be) + det(Parent, a\|an)` | *"A Dog is an Animal"* | Direct "is-a" |
| TDR39 | `nmod:of(TypeKind, Parent)` where governor contains "kind\|type\|sort" | *"A Premium service is a type of service"* | Kind-of / type-of |
| TDR40 | `amod(Noun, Adjective)` where adjective is a **classifier** (not a stop-word) | *"linked device"* → LinkedDevice `--|>` Device | Adjectival classifier |

**Already mapped.** No changes needed — `createCandidateFromMatch()` passes `AssociationType.GENERALIZATION` for `"GeneralizationCdd"` candidate type.

---

## 2. SYNONYM — semantic equivalence ("also known as")

Linguistic pattern signals that two terms denote the same concept and should be
treated as one another's synonyms rather than unrelated entities.

| Rule | NL Pattern | Example | Semantics |
|------|-----------|---------|-----------|
| TDR41 | `appos(Head, Alias)` where both are nouns | *"the store, the shop"* → Store `==` Shop | Appositive equivalence |
| TDR41 | `acl:relcl(Head, Marker)` where Marker ∈ synonym-markers | *"Acetaminophen, also known as paracetamol"* → Acetaminophen `==` Paracetamol | Defining "also known as" |

**Mapped.** `createCandidateFromMatch()` passes `AssociationType.SYNONYM` for
`"SynonymCdd"` candidate matches, creating two `ClassCdd` and one `AssociationCdd`
of symbol `==`.

---

## 3. ASSOCIATION — structural link, no ownership

Plain linguistic relatedness. Two classes participate in a sentence as subject/object or possessive descriptor, with no implication of subtyping, lifetime binding, or "uses" direction.

| Rule | NL Pattern | Example | Currently | Suggested |
|------|-----------|---------|-----------|-----------|
| TDR14 | `nsubj(Verb, E1) + dobj(Verb, E2)` | *Customer places Order* | `ASSOCIATION` ✅ | `ASSOCIATION` |
| TDR15 | `nsubjpass(VBN, E1) + nmod:by(VBN, E2)` | *Order was placed by Customer* | `ASSOCIATION` ✅ | `ASSOCIATION` |
| TDR16 | `nmod:of(E1, E2)` | *details of Order* | `ASSOCIATION` ✅ | `ASSOCIATION` |
| TDR17 | `nsubj(VB,E1) + dobj(VBN,E2) + nmod:of(E2,E3)` | *System processes payment of invoice* | `ASSOCIATION` ✅ | `ASSOCIATION` |
| TDR22 | `nsubj(VB,E1) + nmod:for(VB,E2)` | *Customer qualifies for discount* | `ASSOCIATION` ✅ | `ASSOCIATION` |

**Rule of thumb:** If the preposition is *of*, *by*, or *for*, and no containtment or direction is implied → `ASSOCIATION`.

---

## 3. DEPENDENCY — directed "uses-a", non-owning

One class uses or depends on another, indicated by directional prepositions such as *to* or *from*. The target of the preposition is the depended-upon entity.

| Rule | NL Pattern | Example | Currently | Suggested |
|------|-----------|---------|-----------|-----------|
| TDR18 | `nsubj(VB,E1) + dobj(VB,E2) + nmod:to(VB,E3)` | *System sends Report to User* | `ASSOCIATION` → **`DEPENDENCY`** |
| TDR19 | `nsubjpass(VBN,E1) + nmod:to(VBN,E2)` | *Report is sent to User* | `ASSOCIATION` → **`DEPENDENCY`** |
| TDR20 | `nsubj(VB,E1) + nsubjpass(VBN,E2) + nmod:to(VBN,E3)` | *System sends Report to User* → bidirectional chain | `ASSOCIATION` → **`DEPENDENCY`** |
| *(future)* | `nmod:from(VB, E2)` | *Customer receives Order from System* | — | `DEPENDENCY` |
| *(future)* | `dobj + nmod:to` with "send", "forward", "route" | — | — | `DEPENDENCY` |

**Rationale:** The preposition *to* (and *from*) introduces a directional flow. The entity in the `nmod:to` role is a target/destination, and the entity in `nsubj` is a source — a dependency relationship (dotted arrow `.>` in PlantUML).

---

## 4. AGGREGATION — "has-a" with independent parts

Containment where the part can exist independently of the whole. Indicated by *in*, *on*, *at* prepositions or weak possessive verbs.

| Rule | NL Pattern | Example | Currently | Suggested |
|------|-----------|---------|-----------|-----------|
| TDR21 | `nsubj(VB, E1) + nmod:in(VB, E2)` | *Item is stored in Warehouse* | `ASSOCIATION` → **`AGGREGATION`** |
| TDR7 | `nmod:in(E1, E2)` (noun-in-noun) | *items in order* | candidate → `ASSOCIATION` | `AGGREGATION` |
| *(future)* | `nmod:on(E1, E2)` | *notifications on device* | — | `AGGREGATION`|
|*(futue)* |"E1 consists of E2" | — | — | `AGGREGATION` |

**Rationale:** `nmod:in` (and prepositional containtment generally) maps to aggregation — the contained entity has an independent lifecycle but belongs structurally to the container. UML aggregation uses an empty diamond `*->`.

---

## 5. COMPOSITION — "has-a" with lifecycle-dependent parts

Strong ownership: the part cannot exist without the whole. NL rarely makes this explicit, so composition requires stronger heuristics.

| Linguistic heuristics | Example | Suggested |
|----------------------|---------|-----------|
| Compound noun where sub-element contains "line", "item", "entry", "detail" | *"OrderLine", "InvoiceItem", "JournalEntry"* | `COMPOSITION` |
| Verb "comprise", "compose", "consist of" + strong part-whole | *"Order comprises OrderLines"* | `COMPOSITION` |
| Possessive "has" + part noun that has no independent meaning | *"Order has Lines"* (where "Line" never appears outside an "Order") | `COMPOSITION` |
| *(default for unclear containment)* | — | Prefer `AGGREGATION` (easier to promote to composition in UI) |

**Rationale:** UML composition (filled diamond `+->`) implies lifecycle binding — if the whole is deleted, the parts are deleted too. This semantic is rarely explicit in requirements text. A conservative approach assigns `AGGREGATION` by default and lets the user promote to `COMPOSITION` via the UI.

---

## 6. IMPLEMENTATION — interface realization

A class realizes an interface. This is procedural/architectural knowledge that rarely appears in natural-language requirements.

| Linguistic heuristics | Example | Suggested |
|----------------------|---------|-----------|
| "implements", "realizes", "provides interface" | *"PaymentProcessor implements Gateway"* | `IMPLEMENTATION` |
| "behaves as", "acts as" | *"User acts as Buyer"* (role-based) | `IMPLEMENTATION` |
| *(most common)* **Manual assignment** in the UI | — | User sets via AssociationCdd editor |

**Current status:** `IMPLEMENTATION` now has a non-empty PlantUML symbol `..|>` (dotted realization) — `AssociationCdd.toPlantUmlString()` dispatches on `this.type`, so an implementation link renders as a dotted `..|>` arrow. Effective type defaults to `ASSOCIATION` if the field is `null`.

---

## Implementation approach

### Phase 1 — Low-hanging fruit

Update `then()` methods in existing TDR rules to pass a more specific `candidateType` or `AssociationType`:

| Rule | Change |
|------|--------|
| TDR18, TDR19, TDR20 | Pass `"DepedencyCdd"` candidateType (or explict `AssociationType.DEPENDENCY`) |
| TDR21 | Pass `"AggregationCdd"` candidateType (or explict `AssociationType.AGGREGATION`) |

This requires extending `RuleMatches.createCandidateFromMatch()` to accept an optional `AssociationType` parameter and switching on it when `candidateType` is not `"GeneralizationCdd"` — analogous to the existing generalization branch (lines 216–227).

### Phase 2 — New rules

| Candidate rule | Pattern | AssociationType |
|---------------|---------|-----------------|
| TDR(n) | `nmod:from(VB, E2)` | `DEPENDENCY` |
| TDR(n+1) | "E1 consists of E2" | `COMPOSITION` |
| TDR(n+2) | "E1 implements E2" | `IMPLEMENTATION` |

### Phase 3 — U manual override

Ensure the `AssociationCdd` viewer/editor exposes the `type` property so users can adjust automatic assigments.

---

## Decision matrix (quick reference)

| NL Signal | Examples | AssociationType |
|-----------|---------|-----------------|
| "is a", "is an" | *Dog is an Animal* | `GENERALIZATION` |
| "is a kind/type/sort of" | *is a kind of Service* | `GENERALIZATION` |
| Adjective + Noun (classifier) | *linked device* | `GENERALIZATION` |
| Subject + Verb + Object | *Customer places Order* | `ASSOCIATION` |
| Passive + "by" | *Order was placed by Customer* | `ASSOCIATION` |
| Possessive ("of") | *details of Order* | `ASSOCIATION` |
| Directional ("to") | *Report is sent to User* | `DEPENDENCY` |
| Directional ("from") | *Order is received from Customer* | `DEPENDENCY` |
| Containment ("in") | *Item is stored in Warehouse* | `AGGREGATION` |
| Surface ("on") | *Notification is sent on Device* | `AGGREGATION` |
| Whole-part ("comprises") | *Order comprises Lines* | `COMPOSITION` |
| "implements", "realizes" | *Processor implements Gateway* | `IMPLEMENTATION` |
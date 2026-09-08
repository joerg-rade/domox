Let me read the remaining action-indicating rules to give you a comprehensive answer.

Now let me check the base class to understand `getA()` / `getB()` semantics:

Good question — this comes down to how the rules encode the dependency grammar and what they do with it. Here's the breakdown based on the actual rule sources.

## The A/B convention

Every rule operates on a `TypedDependency` of the form `type(governor=A, dependent=B)`. The **grammatical role** of B is *not* defined by the rule — it's implied entirely by the dependency `type`:

| Dependency type | A (governor) | B (dependent) |
|---|---|---|
| `nsubj` | verb | **subject** (actor) |
| `nsubjpass` | verb | passive subject (the "patient") |
| `dobj` / `obj` | verb | **direct object** |
| `iobj` / `obl` | verb | indirect object |
| `nmod:by` / `agent` | verb | agent (semantic subject in passive) |
| `nmod:to/for/from/as` | verb | prepositional object (recipient, etc.) |
| `mark` | verb | subordinator (`if`) |
| `xcomp` | verb | clausal complement |

So a rule firing on `nsubj` is implicitly saying "B is the subject"; the same rule firing on `dobj` is saying "B is the direct object." The rules themselves rarely make that distinction explicit.

## Which rules indicate an action

**TDR27–TDR37** (Group 4: Operation/Action rules) are the ones that indicate actions. But they fall into two distinct flavors:

### 1. Rules that record the action (the verb) itself

| Rule | Output | Verb (A) set | How subject/object is used |
|---|---|---|---|
| **TDR32** | `User_Action.add(verb)` / `System_Actions.add(verb)` | user-input verbs vs system-output verbs | **B is read as the actor**: if B ≠ "system" → User_Action; if B == "system" → System_Actions (on `nsubj`, B is the subject; on `nmod:by`, the agent) |
| **TDR33** | `User_Action.add(verb)` | receive/accept/get/obtain/acquire/redeem | B is the actor but is **ignored** — both branches produce the same output |
| **TDR35** | `System_Actions.add("if/then/else" + B + A)` | conditional keywords (if/then/else) | Fires on `advcl`/`mark`/`advmod`; A is the verb of the clause, B is the condition/marker |
| **TDR36** | `System_Actions.add(B + A)` | "validate" only | B **is the subject** (`nsubj(validate, system)` → `system validate`) |
| **TDR37** | `System_Action.add(A + B)` | continue/restart/go/repeat | B is the subject on `nsubj`, or the complement on `xcomp`; the spec wants `nummod.B`/`dobj.B` but the implementation falls back to `currentTd.getB()` |

### 2. Rules that record data (objects), not the verb

| Rule | Output | Verb (A) set | Role of B |
|---|---|---|---|
| **TDR27** | `Input_Data.add(B...)` | input/enter/fill/click/select/add/record/process/validate | The verb only gates the rule; `then()` collects **all attribute B's** in the sentence (skipping nsubj/dobj/etc.) — i.e., the *data being input*, not the subject |
| **TDR28** | `Output_Data.add(B...)` | display/output/retrieve/show/view/print | Same pattern — collects attribute B's as output data |
| **TDR29** | `Output_Data` if B=="system", else `Input_Data` | get/send/prepare | B **is used as a routing key**: "system" → output, any basic attribute → input |
| **TDR30** | `Input_Data.add(B...)` | past-tense input verbs (entered, filled, ...) on `nmod:by/agent/with` | same data-collection pattern |
| **TDR31** | `Output_Data.add(B...)` | past-tense output verbs (displayed, printed, ...) on `nmod:by/agent/with` | same data-collection pattern |

Also technically action-adjacent: **TDR34** records `Exceptions.add(B + A)` (fires on `xcomp`/`amod`/`neg` with error terms), but it models an exception condition, not an action.

## Direct answers to your questions

- **Which rules indicate an action?** TDR27–TDR37. TDR32/TDR33/TDR35/TDR36/TDR37 surface the **verb as the action**; TDR27–TDR31 surface the **data** and only use the verb to decide whether it's input or output.
- **Do they indicate the subject?** Only incidentally. TDR32 and TDR33 semantically treat B as the actor (user vs. system) — for `nsubj` that means the subject; for `nmod:by` it's the agent. TDR36 and TDR37 fire on `nsubj`/`xcomp`, so B is the subject there too, but they don't label it as such — they just concatenate it into the result string.
- **Do they indicate objects?** Mostly **no**. TDR27–TDR31 fire on `dobj`/`iobj`/`pobj` in `when()`, so the matched dependency's B *is* often the direct object — but the `then()` logic then scans the *whole sentence* for basic attributes and adds those, discarding the object identity. TDR37's comment says it should use `nummod.B`/`dobj.B` as the object, but the implementation ignores that and just uses `currentTd.getB()`.

So the short version: **the rules encode the action verb (A) and a participant (B), but they don't systematically model "subject" vs. "object" roles** — the role is whatever the dependency type implies, and only TDR32/TDR33/TDR29 actually *interpret* B (as actor or as system) rather than blindly type-checking it.

# Actions missed
Short answer: **yes — and in fact essentially *all* of the primary action verbs in that file are outside the vocabularies of rules 27–37.** Rules 27–37 only fire on small, closed verb sets built around IT-style operations (data input/output, CRUD, validation, conditional/control flow). The pet-shop text is full of business-level verbs (`offer`, `provide`, `sell`, `train`, …) that appear in none of those sets, so none of the 15 use cases would produce an action detection from these rules.

## Why

The verb whitelists are strict string `equalsIgnoreCase` checks, so the action verb (A of the matched dependency) must literally be one of:

| Rule | Verb set |
|---|---|
| TDR27 | input, enter, fill, click, select, add, record, process, validate |
| TDR28 | display, output, retrieve, show, view, print |
| TDR29 | get, send, prepare |
| TDR30 | inputted, entered, filled, clicked, selected, added, recorded, processed, validated |
| TDR31 | displayed, outputted, retrieved, showed, viewed, printed |
| TDR32 | input, enter, fill, click, select, add, submit, choose · display, output, retrieve, show, view, print, calculate, process, update, delete, search, modify, edit, remove |
| TDR33 | receive, accept, get, obtain, acquire, redeem |
| TDR34 | (exception terms) error, fail, wrong, invalid, incorrect, not |
| TDR35 | (keywords) if, then, else |
| TDR36 | validate |
| TDR37 | continue, restart, go, repeat |

Now scanning the file: **not a single one** of those verbs occurs in the pet-shop text (no `enter`, `select`, `process`, `display`, `get`, `validate`, `if/then/else`, `go`/`repeat`, etc.).

## Per-use-case audit of the main actions

| # | Use case | Action verb(s) in text | Detected? |
|---|---|---|---|
| 1 | Selling pet products | sell / offer | ❌ |
| 2 | Grooming services | provide (grooming, bathing, haircuts, nail trimming) | ❌ |
| 3 | Pet training | offer, train, help | ❌ |
| 4 | Pet adoption | facilitate, connect | ❌ |
| 5 | Veterinary services | have, provide | ❌ |
| 6 | Pet boarding | offer, care | ❌ |
| 7 | Pet daycare | provide, allow, leave | ❌ |
| 8 | Online pet store | operate, allow, purchase, deliver | ❌ |
| 9 | Accessories & fashion | offer | ❌ |
| 10 | Health & wellness | provide, maintain | ❌ |
| 11 | Pet photography | offer, capture | ❌ |
| 12 | Pet socialization | organize, interact | ❌ |
| 13 | Events & workshops | host, attract, provide | ❌ |
| 14 | Pet insurance | partner, offer | ❌ |
| 15 | Pet-themed merchandise | sell | ❌ |

Also note: even the *nominals* used to name these use cases (grooming, boarding, daycare, adoption, training, photography, insurance, delivery) are services/entity concepts — there is no "Service" category in rules 27–37 at all, so they'd only be picked up (if at all) as entities/attributes by rules 1–13, never as actions.

## Caveats

- Only the *verb lemma* matters for detection; however, even if a verb were present, some sentences here would still be risky due to grammatical form — e.g., modal constructions ("can offer", "may offer"), gerunds ("Providing daycare…", "Hosting events…", "Operating an online store"), passives ("have them delivered"), and periphrastic verbs ("allow … to leave", "help owners train"). TDR30/31 only handle past participles, and TDR27/28/32 mostly expect finite present-tense heads, so modal + infinitive (`can offer`) or `xcomp` chains wouldn't reliably match even for covered verbs.
- TDR34/TDR35 can't help here — no exception terms and no if/then/else markers appear.

## Bottom line

Rules 27–37 would detect **zero** of the actions described in this file. To capture them you'd need to extend the vocabularies (e.g., add `offer`, `provide`, `sell`, `purchase`, `deliver`, `train`, `groom`, `board`, `adopt`, `organize`, `host`, `capture`, `care`, `maintain`), and/or introduce a new output category such as `Service` for nouns like grooming/boarding/daycare — plus handle the modal/gerund grammatical patterns those sentences use.

# Suggested to fix "Bottom Line" findings

The missing action verbs (`offer`, `provide`, `sell`, `train`, `purchase`, `deliver`, etc.) can be detected **without adding them to any whitelist**. The architecture already has everything needed: a negation-based approach that treats **any verb not in the technical verb lists as a domain action**.

## Principle

Instead of maintaining a **positive** whitelist of business action verbs (which must be extended per corpus), define what is *not* a domain action (i.e., what is "technical") and treat everything else as a domain action. The `ActionCatalog` already implements this with `isDomainAction(String verb)`:

```java
// Returns true if the verb is NOT in any technical verb list
public boolean isDomainAction(@NonNull String verb) { … }
```

The technical verb categories are configured in `application.yml` under `domox.nlp.*`:

| Category | Property | Used by |
|---|---|---|
| Auxiliary verbs | `auxiliary-verbs` | Sentence parsing (be, have, do) |
| Modal verbs | `modal-verbs` | Sentence parsing (can, could, will) |
| User input verbs | `user-input-verbs` | TDR27, TDR30, TDR32 |
| System output verbs | `system-output-verbs` | TDR28, TDR31, TDR32 |
| Action verbs (TDR29) | `action-verbs` | TDR29 |
| Input past verbs | `input-past-verbs` | TDR30 |
| Output past verbs | `output-past-verbs` | TDR31 |
| Receive verbs | `receive-verbs` | TDR33 |
| Control flow verbs | `control-flow-verbs` | TDR37 |
| Exception terms | `exception-terms` | TDR34 |
| Blocked verbs | `blocked-verbs` | TDR4/TDR5 |

Any verb lemma parsed from the input text that does **not** appear in any of these lists is a *domain action*.

## TDR38 — Domain Action Detection (generic, no whitelist needed)

**`TDR38`** fires on dependencies of type `nsubj`, `nsubjpass`, `dobj`/`obj`, `iobj`/`obl`, `pobj`, `xcomp`, `nmod:with`, `nmod:to`, or `nmod:for` where:

- The governor (A) is a verb (VB family POS)
- `ActionCatalog.isDomainAction(A)` returns `true` (i.e., the verb is NOT in any technical verb list)

The rule records the verb as a `Domain_Action` (candidate type). If the dependency exposes an actor (B = subject/agent), the actor is checked against the configurable `domox.nlp.customer-actors` list:

- If B matches a customer actor → candidate type `User_Action`
- Otherwise → candidate type `System_Actions`

No per-domain verb lists need to be maintained. The same `application.yml` configuration that defines technical verbs for TDR27–TDR37 also drives TDR38's negation.

## TDR39 — Service Concept Detection (generic, no whitelist needed)

**`TDR39`** detects service concepts — nouns that are *not* basic attributes and *not* known entities, appearing as objects, compounds, or prepositional objects in the context of a domain action verb. The service-noun vocabulary is configured via `domox.nlp.service-nouns` in `application.yml`:

```yaml
domox:
  nlp:
    service-nouns: service, product, booking, reservation, subscription, membership, shipment, delivery
```

The rule fires on dependencies where B (or A in compound) matches a configured service noun, and records it with candidate type `Service`.

## What would change in the source code

1. **`ActionCatalog`** — add `blockedVerbs` to the non-domain set (via `addAll(nlpProperties.getBlockedVerbs())`).
2. **`TDR38`** — inject `ActionCatalog` and `NlpProperties`; replace `isActionVerbA(currentTd)` with `actionCatalog.isDomainAction(currentTd.getA())`; remove the hardcoded `CUSTOMER_ACTORS` set in favour of `nlpProperties.getCustomerActors()`.
3. **`TDR39`** — new rule using `TypedDependencyPredicates.isServiceNounA/B()` with the configurable `domox.nlp.service-nouns` vocabulary.
4. **`NlpProperties`** — add `getServiceNouns()` and `getCustomerActors()` getters if not already present (they are already present as of the current codebase).

No `ActionVocabularyCatalog` is needed. The positive action-verb and service-noun vocabularies from that catalog (`isActionVerbA/B`, `isServiceNounA/B`) are replaced by the generic negation approach.

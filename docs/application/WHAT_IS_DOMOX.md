## What Is DoMoX?

**DoMoX** (Domain Model Extractor) is a **deterministic**, **rule-based** system that extracts UML domain models from natural-language requirements text — without machine learning. Think of it as "Vibe Coding without the AI": you write requirements in plain English, and DoMoX produces candidate entity classes, properties, relationships, actions, and data flows, which a human designer then curates into a final Domain Model.

The key philosophy is **traceability and human-in-the-loop**: every extracted candidate is linked back to the exact sentence and dependency in the original requirements that triggered it.

---

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    Requirements Text                         │
└────────────────────────┬─────────────────────────────────────┘
                         ▼
┌──────────────────────────────────────────────────────────────┐
│           Stanford CoreNLP (external Docker container)       │
│  Tokenization → POS tagging → Dependency parsing (UD)        │
└────────────────────────┬─────────────────────────────────────┘
                         ▼
┌──────────────────────────────────────────────────────────────┐
│                    TDR Rule Engine (RuleBook)                │
│  TDR1-TDR39 — typed dependency pattern matchers              │
│  Each rule fires on specific dependency patterns             │
│  Creates RuleMatch records                                   │
└────────────────────────┬─────────────────────────────────────┘
                         ▼
┌──────────────────────────────────────────────────────────────┐
│                 Phase 2: Candidate Creation                  │
│  RuleMatches → ClassCdd, PropertyCdd, ActionCdd, etc.        │
└────────────────────────┬─────────────────────────────────────┘
                         ▼
┌──────────────────────────────────────────────────────────────┐
│              Domain Model (UML + Apache Causeway UI)         │
│  Designers review, curate, and flag candidates               │
│  → generates PlantUML diagrams                               │
│  → generates runnable Apache Causeway application code       │
└──────────────────────────────────────────────────────────────┘
```

---

## Core Components in Detail

### 1. NLP Pipeline (Stanford CoreNLP)

The project runs **Stanford CoreNLP** as a Docker container. Raw requirements text is sent to its REST endpoint, which returns:
- **Tokens** with lemmas and part-of-spech tags (Penn Treebank)
- **Syntax trees** and **Universal Dependences (UD)** — typed dependency triples like `nsubj(creates, user)` meaning "user" is the nominal subject of "creates")
- These are mapped into the project's own `TypedDependency` and `Sentence` domain objects

### 2. TDR Rules (TypedDependency Rules) — The Heart of the System

There are currently **39 rules** (TDR1 through TDR39), implemented as Spring beans using the **RuleBook** framework (`com.deliveredtechnologies.rulebook`). Each rule:

1. Has a `@When` method that checks if the current dependency matches a pattern
2. Has a `@Then` method that records what was found
3. Runs in a fixed order via `@Rule(order = N)`

The rules are organized into categories:

| Category | Rules | What they extract |
|----------|-------|-------------------|
| **Entity & Attribute** | TDR1-TDR13 | Classes (entities) and properties (attributes) from subjects, objects, compounds, possessives, etc. |
| **Relationship** | TDR14-TDR23 | Associations between entities — who does what to whom |
| **Cardinality** | TDR24-TDR26 | Multiplicity constraints ("multiple users" → `User > *`) |
| **Operation & Action** | TDR27-TDR37 | Input/output data flows, user actions, system actions, exceptions, control flow |
| **Business Extension** | TDR38-TDR39 | Business-level actions and service concepts beyond CRUD verbs |

**Example — TDR1 (Entity Extraction):**
- **Pattern:** `nsubj(verb, noun)` where verb is a verb POS and noun is NOT a basic attribute
- **Input:** "The **customer** creates an order."
- **Dependency:** `nsubj(creates, customer)`
- **Output:** `ClassCdd.add("Customer")`

**Example — TDR2 (Attribute Extraction):**
- **Pattern:** `nsubj(verb, noun)` where noun IS a basic attribute
- **Input:** "The **name** is stored."
- **Dependency:** `nsubj(stored, name)`
- **Output:** `PropertyCdd.add("name")` on the appropriate class

### 3. Vocabulary Catalogs — Making Everything Configurable

This is the part you were investigating. Originally the rules had hardcoded verb/noun sets. Now they're loaded from `application.yml` via `NlpProperties`:

```yaml
domox:
  nlp:
    basic-attributes: name, number, type, address, date, time, email, ...
    action-verbs: get, send, prepare, generate, calculate, ...
    user-input-verbs: input, enter, save, fill, click, ...
    system-output-verbs: display, output, retrieve, show, ...
    service-nouns: service, product, booking, reservation, ...
    customer-actors: user, customer, owner, person, ...
    exception-terms: error, fail, wrong, invalid, ...
    blocked-verbs: entered, inputted, saved, added, has
    # ... and more
```

Three Spring services load these at startup and register them into `TypedDependencyPredicates`:

1. **`BasicAttributeCatalog`** — loads `basic-attributes` → registers into `TypedDependencyPredicates.BASIC_ATTRIB`
2. **`ActionVocabularyCatalog`** — loads `action-verbs` and `service-nouns` → registers into `TypedDependencyPredicates.ACTION_VERBS` and `SERVICE_NOUNS`
3. **`ActionCatalog`** — loads ALL verb lists (auxiliary, modal, input/output, etc.) for the `isDomainAction()` check (used elsewhere)

The `TypedDependencyPredicates` class holds **static** `HashSet` copies so that the rule methods (`isBasicAttributeA()`, `isActionVerbB()`, etc.) can be called statically without needing Spring injection in the rule beans.

### 4. The Flow: From Text to Persisted Matches

**Phase 1 — Rule Execution:**

When `Analysis.analyzeDocument(document)` is called, it iterates over every `Sentence` and every `TypedDependencyRule`, calling `rule.analyzeAndMatch(sentence)`. Each rule:

1. Iterates over all typed dependencies in the sentence
2. For each dependency, checks `@When` (pattern match)
3. If true, calls `@Then` → calls `ruleMatces.create()` → persists a `RuleMatch` entity with:
    - The `TypedDependency` it matched on
    - The rule class name (e.g., "TDR1")
    - The candidate type (e.g., "ClassCdd", "PropertyCdd")
    - The candidate name (e.g., "Customer")
    - Optionally, a related candidate (for properties: the owning class)

**Phase 2 — Candidate Creation:**

`RuleMaches.createCandidatesFromMatces()` reads all `RuleMatch` records and creates actual `ClassCdd` or `PropertyCdd` entities (or ignores unknown types). These are the persistent domain objects that the UI shows.

### 5. The UI — Apache Causeway

The UI is generated by **Apache Causeway** — a framework that derives a web UI directly from the domain objects. Every entity (`ClassCdd`, `PropertyCdd`, `RuleMatch`, `Document`, `Sentence`) becomes a page with CRUD tables. Designers can:

- View all `RuleMatch` records, filterd by rule name
- See the generated candidate classes and properties
- Edit them directly (editing is enabled)
- Eventually flag them for inclusion in the final `DomainModel`
- The `DomainModel` can render itself as PlantUML

---

## Key Design Decisions

| Decision | Why |
|----------|-----|
| **Rule-based, not ML** | Full traceability: every extraction is explained by which rule and dependency fired |
| **Configurable vocabularies** | Same rules, different domains — swap verb lists per deployment without recompiling |
| **Static predicates in TDP** | RuleBook beans can't easily @Inject services into @When/@Then, so static methods that check pre-registered sets are the simplest approach |
| **Two-phase** (RuleMatch → Candidate) | Decouples "what was found" from "what we create as objects" — allows review before creation |
| **Apache Causeway** | Instant web UI + REST API from domain objects; traceability is built-in |

---

## Current State

The project has:
- ✅ 39 TDR rules implemented
- ✅ Configurable vocabulary via `NlpProperties` / `application.yml`
- ✅ Three catalog services that bridge configuration to the rule predicates
- ✅ Spring Boot integration (DomainModule's `@ComponentScan` picks up all services and rules)
- ✅ A test suite (`TypedDependencyRulesTest`, `TypedDependencyPredicatesTest`)
- ✅ External NLP via Docker (Stanford CoreNLP REST endpoint)

The NlpProperties approach you were investigating is the **configuration backbone** that makes all the verb/attribute/noun lists runtime-configurable, replacing the earlier hardcoded approach.
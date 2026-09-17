# TypedDependency Rules (TDR1-TDR37) - Complete Registry

## Overview
The TypedDependency Rules (TDR) system is a rule-based framework for extracting domain models from natural language requirements using Stanford CoreNLP dependency parse trees. All 37 rules are implemented using the RuleBook framework with Spring integration (@RuleBean annotation).

## Rule Registration & Autowiring

### Spring Configuration
All rules are automatically registered as Spring beans through the `@RuleBean` annotation. The framework scans the `domox.dom.rules` package at application startup.

**Key Details:**
- **Framework**: RuleBook (Delivered Technologies)
- **Integration**: Spring with @RuleBean annotation
- **Package**: `domox.dom.rules`
- **Rule Execution**: Sequential by order number (TDR1.order=1 through TDR37.order=37)

### Automatic Bean Discovery
Rules are discovered through Spring's component scanning:
```java
RuleBookRunner ruleBookRunner = new RuleBookRunner("domox.dom.rules");
```

## Rule Categories

### Group 1: Entity & Attribute Extraction (TDR1-TDR13)
Extract domain entities (classes) and attributes (properties) from dependency parse trees.

| Rule | Dependency Types | Purpose | Conditions |
|------|------------------|---------|-----------|
| TDR1 | nsubj, nsubjpass | Extract subject entities | A=VB, B=NN, B≠Basic_Attrib |
| TDR2 | nsubj, nsubjpass | Extract subject attributes | A=VB, B=NN, B=Basic_Attrib |
| TDR3 | dobj, iobj, pobj | Extract object entities | A=VB, B=NN, B≠Basic_Attrib, no amod/advmod, not blocked verb |
| TDR4 | dobj, iobj, pobj | Extract object attributes | A=VB, B=NN, (B=Basic_Attrib OR blocked verb) |
| TDR5 | dobj, iobj, pobj | Extract mod+attribute | A=VB, B=NN, prev=amod/advmod, B=JJ |
| TDR6 | nmod:of | Possessive relationships | A,B=NN, branching on basic-attrib combinations |
| TDR7 | nmod:in | Noun-in-noun relationships | A,B=NN |
| TDR8 | nmod:to/for/from/as | Prepositional noun relationships | B=NN |
| TDR9 | nmod:by/agent/with | Agent noun relationships | B=NN, branch on basic-attrib |
| TDR10 | nmod:poss | Possessive pronoun relationships | A,B condition branching (NN, PREP, PRP$) |
| TDR11 | amod | Adjective modifier relationships | A=NN, B=JJ/VBG, branch on basic-attrib & possession |
| TDR12 | compound | Compound word entities/attributes | A,B=NN, next≠nsubj/dobj, 4-way branching |
| TDR13 | nmod:and/or | Conjunction relationships | A,B=NN, branch on basic-attrib |

**Blocked Verbs** (TDR3-TDR5): entered, inputted, saved, added, has

**Basic Attributes**: name, number, type, address, level, date, time

### Group 2: Relationship Extraction (TDR14-TDR23)
Extract relationships between entities and their actions.

| Rule | Dependency Pattern | Output |
|------|-------------------|--------|
| TDR14 | nsubj(V,E1) & dobj(V,E2) | E1 (V) E2 |
| TDR15 | nsubjpass(V,E1) & nmod:by/agent(V,E2) | E1 (V) E2 |
| TDR16 | nmod:of(E1,E2) | E1 (has) E2 |
| TDR17 | nsubj(V,E1) & dobj(V,E2) & nmod:of(E2,E3) | E1 (V) E2; E2 (has) E3 |
| TDR18 | nsubj(V,E1) & dobj(V,E2) & nmod:to(V,E3) | E1 (V) E2; E2 (V to) E3; E1 (V to) E3 |
| TDR19 | nsubjpass(V,E1) & nmod:to(V,E2) | E1 (V to) E2 |
| TDR20 | nsubj(V,E1) & nsubjpass(V,E2) & nmod:to(V,E3) | E1 (V) E2; E1 (V to) E3; E2 (V to) E3 |
| TDR21 | nsubj(V,E1) & nmod:in(V,E2) | E1 (V in) E2 |
| TDR22 | nsubj(V,E1) & nmod:for(V,E2) | E1 (V for) E2 |
| TDR23 | nmod:as(V,E1) & dobj(V,E2) | E1 (V) E2 |

### Group 3: Descriptor & Multiplicity Rules (TDR24-TDR26)
Extract entity descriptors (TDR24) and multiplicity constraints (TDR25-TDR26).

| Rule | Dependency | Condition | Output |
|------|------------|-----------|--------|
| TDR24 | amod(E,JJ) | - | descriptor.add(E, JJ) |
| TDR25 | nummod(E,CD) | - | multiplicity.add(E, CD) |
| TDR26 | det(E,DT) | DT in {Each, All, some, Any, Many, Every, multiple} | multiplicity.add(E, N) |
| TDR26 | det(E,DT) | DT in {a, an} | multiplicity.add(E, 1) |

### Group 4: Operation/Action Rules (TDR27-TDR37)
Extract operations, user actions, system actions, and exceptions.

| Rule | Dependency Types | Input Verbs | Output |
|------|------------------|-------------|---------|
| TDR27 | nsubj, dobj, nmod:to, mark | input, enter, fill, click, select, add, record, process, validate | Input_Data |
| TDR28 | nsubj, dobj, nmod:to, mark | display, output, retrieve, show, view, print | Output_Data |
| TDR29 | nsubj, dobj, nmod:to, mark | get, send, prepare | Output_Data (if B=system) / Input_Data |
| TDR30 | nmod:by/agent/with | inputted, entered, filled, clicked, selected, added, recorded, processed, validated | Input_Data |
| TDR31 | nmod:by/agent/with | displayed, outputted, retrieved, showed, viewed, printed | Output_Data |
| TDR32 | nsubj, nmod:by | User: input, enter, fill, click, select, add, submit, choose | User_Action |
| TDR32 | nsubj, nmod:by | System: display, output, retrieve, show, view, print, calculate, process, update, delete, search, modify, edit, remove | System_Actions |
| TDR33 | nsubj, nmod:by | receive, accept, get, obtain, acquire, redeem | User_Action |
| TDR34 | xcomp, amod, neg | error, fail, wrong, invalid, incorrect, not | Exceptions |
| TDR35 | advcl, mark | if condition | System_Actions (conditional) |
| TDR36 | nsubj | validate | System_Actions (validation) |
| TDR37 | nsubj, xcomp | continue, restart, go, repeat | System_Action (control flow) |

## Usage Pattern

### Rule Execution
```java
// 1. Create FactMap with input dependencies
FactMap<String> facts = new FactMap<>();
facts.setValue("currentTd", typedDependency);
facts.setValue("previousTd", previousDependency);  // optional
facts.setValue("nextTd", nextDependency);          // optional

// 2. Run rule book
RuleBookRunner ruleBookRunner = new RuleBookRunner("domox.dom.rules");
ruleBookRunner.run(facts);

// 3. Extract result
if (ruleBookRunner.getResult().isPresent()) {
    String result = ruleBookRunner.getResult().get().toString();
    // Process result...
}
```

### Rule Firing Conditions
Each rule implements:
- **@When**: Determines if rule applies (returns true/false)
- **@Then**: Generates result string with extraction output
- **@Result**: Stores the rule's output

## TypedDependency Helper Methods

All rules depend on methods in `TypedDependency` class:

### Dependency Type Checkers
- `nsubj()`, `nsubjpass()`, `dobj()`, `iobj()`, `pobj()`
- `amod()`, `advmod()`, `compound()`
- `nmodOf()`, `nmodIn()`, `nmodTo()`, `nmodFor()`, `nmodFrom()`, `nmodAs()`
- `nmodBy()`, `nmodAgent()`, `nmodWith()`, `nmodPoss()`
- `nmodAnd()`, `nmodOr()`
- `mark()`, `xcomp()`, `advcl()`, `nummod()`, `det()`, `neg()`

### Part-of-Speech Checkers
- `isVerbA()`, `isNounA()`, `isNounB()`, `isAdjectiveB()`
- `isBasicAttributeA()`, `isBasicAttributeB()`

### Element Accessors
- `getA()` - Returns dependency governor (first token)
- `getB()` - Returns dependency dependent (second token)

## File Structure

```
domox-domain/src/main/java/domox/dom/rules/
├── TypedDependencyRule.java           (base class)
├── TypedDependencyRuleWithPreviousAndNext.java (extended base)
├── TDR1.java through TDR37.java       (37 rule implementations)
└── [other supporting classes]

domox-domain/src/test/java/domox/dom/rules/
├── TypedDependencyRulesTest.java      (integration tests)
└── [rule-specific test files]
```

## Compilation & Testing

### Build
```bash
mvn clean compile -DskipTests
```

### Run Tests
```bash
mvn test -Dtest=TypedDependencyRulesTest
```

### Verify All Rules
```bash
ls -1 domox-domain/src/main/java/domox/dom/rules/TDR*.java | wc -l
```

Expected: 37 files

## Notes

- Rules execute in sequential order (1-37)
- Each rule is order-independent except where context (previousTd, nextTd) is used
- Basic attributes classification is fixed (name, number, type, address, level, date, time)
- Blocked verbs prevent certain dependency patterns from being classified as entities
- Multiple conditional branches in single rules handle complex scenarios (3-way or 4-way branching)

## See Also
- `../../../docs/application/RULES.txt` - Original rule specifications
- `./MAVEN.md` - Maven configuration documentation
- RuleBook Documentation: https://github.com/deliveredtechnologies/rulebook

## Group 6: Generalization / Inheritance Rules — Literature Research (Proposed)

The following patterns are **not yet implemented** in any TDR rule. They are documented here based on a literature review of the MODELS'16 Arora et al. paper, which in turn synthesizes the Yue et al. (2011) survey and Abbott (1983).

### Primary Source: Arora et al., "Extracting Domain Models from Natural-Language Requirements" (MODELS'16)

**Table 1 — Rule B5 (Generalization — explicit patterns):**
| Trigger | Source | Example |
|---------|--------|---------|
| `"is a"` | Abbott [1], Chen [8], Coad & Yourdon [19] | `"A car is a vehicle."` → Car --|> Vehicle |
| `"type of"` | Yue et al. survey [31] | `"Premium service is a type of service."` → PremiumService --|> Service |
| `"kind of"` | Yue et al. survey [31] | `"SUV is a kind of car."` → SUV --|> Car |
| `"may be"` | Arora et al. [1] | `"Service may be premium service or normal service."` → PremiumService --|> Service, NormalService --|> Service |

**Rule D3 (Adjectival modifier — repurposed from attribute to generalization):**
- *Original*: `amod(NP, ADJ)` → attribute (e.g., "the size is large" → attribute `size`)
- *Repurposed by Arora*: Remove the leading adjective from an NP; the remaining noun becomes the parent concept, the full NP becomes the subclass.
- *Example*: `"Linked Device"` → `amod(Device, Linked)` → Parent: `Device` (from NP minus adjective), Subclass: `Linked Device` (full NP)
- *Caveat*: Attribute inference from adjectives is impractical without user intervention; generalization via D3 requires user to decide when an attribute is more suitable.

**Key finding from Arora evaluation:**
> "Generalizations are typically left tacit in NL requirements and are thus hard to identify automatically. The main rule targeted at generalizations is B5… This rule has limited usefulness when no conscious attempt has been made by the requirements authors to use the patterns in the rule."

### Implemented Generalization Rules (Group 6)

| Rule | TDR | Dependency Pattern | Literature | Conditions | Output |
|------|-----|-------------------|------------|------------|--------|
| Copula generalization | **TDR38** | `nsubj(ParentHead, ChildNoun)` + `cop(ParentHead, is/are)` + `det(ParentHead, a/an)` | Arora B5 | A=NN, B=NN; skip "kind"/"type"/"sort" (see TDR39) | `Child --|> Parent` |
| Kind-of/Type-of generalization | **TDR39** | `nmod:of(TypeKindNoun, ParentNoun)` where TypeKindNoun contains "kind"/"type"/"sort" | Arora B5, Yue survey | B=NN; scan sentence for `nsubj(TypeKindNoun, ChildNoun)` | `Child --|> Parent` |
| Adjectival classifier generalization | **TDR40** | `amod(Noun, Adjective)` where adjective is a classifier | Arora D3 | A=NN, B=JJ/VBG; exclude evaluative adjectives and copula nouns | `Adj+Noun --|> Noun` |

### Implementation Details

#### TDR38 — Copula-based generalization
- **when()**: Accepts `nsubj(Governor, Dependent)` where both are nouns and the sentence contains both a `cop(Governor, beVerb)` and `det(Governor, a|an)`. Excludes governors that are "kind"/"type"/"sort" (reserved for TDR39).
- **then()**: Creates a `GeneralizationCdd` RuleMatch with `candidateName=child` and `relatedCandidateName=parent`.

#### TDR39 — Kind-of/Type-of generalization
- **when()**: Accepts `nmod:of(Governor, Dependent)` where the governor lemma contains "kind"/"type"/"sort" and the dependent is a noun.
- **then()**: Scans the sentence for `nsubj(Governor, ChildNoun)` to identify the subclass. Creates a `GeneralizationCdd` RuleMatch.

#### TDR40 — Adjectival classifier generalization
- **when()**: Accepts `amod(Noun, Adjective)` where the adjective is a classifier (not in a stop list and not evaluative), the noun is not in a copula relation (to avoid TDR38/39 overlap), and both POS types are valid.
- **then()**: Creates a `GeneralizationCdd` RuleMatch with `candidateName=Adjective+Noun` (e.g. "LinkedDevice") and `relatedCandidateName=Noun` (e.g. "Device").

#### Phase 2 Integration
- `RuleMatches.createCandidateFromMatch()` handles `"GeneralizationCdd"` candidateType by creating both `ClassCdd` entities (child and parent) and an `AssociationCdd` with `AssociationType.GENERALIZATION`.
- `AssociationCandidates.create()` and `findOrCreate()` now accept an optional `AssociationType` parameter.

#### New Predicates Added
The following predicates were added to `TypedDependencyPredicates`:
- `cop(TypedDependency)` — checks for `TdType.COP`
- `isBeVerbDependent(TypedDependency)` — checks if dependent is a form of "be"
- `isIndefiniteArticleDependent(TypedDependency)` — checks if dependent is "a" or "an"
- `isKindTypeOrSortDependent(TypedDependency)` — checks if dependent contains "kind"/"type"/"sort"
- `isKindTypeOrSortGovernor(TypedDependency)` — checks if governor contains "kind"/"type"/"sort"

### Next Steps
1. ✅ Implement TDR38 (copula-based generalization)
2. ✅ Implement TDR39 (kind-of/type-of generalization)
3. ✅ Implement TDR40 (adjectival generalization)
4. ✅ Update `ruleMatches.createCandidatesFrom()` to create generalization links
5. ✅ Add integration tests for TDR38-TDR40 in TypedDependencyRulesTest
6. ✅ Update RULES_EXAMPLES.md with before/after examples for the new rules

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
| TDR10 | nmod:poss | Possessive pronoun relationships | A,B condition branching |
| TDR11 | amod | Adjective modifier relationships | A=NN, B=JJ, branch on basic-attrib |
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

### Group 3: Cardinality Rules (TDR24-TDR26)
Extract multiplicity constraints.

| Rule | Dependency | Condition | Output |
|------|------------|-----------|--------|
| TDR24 | amod(E,JJ) | - | E > JJ |
| TDR25 | nummod(E,CD) | - | E > CD |
| TDR26 | det(E,DT) | DT in {Each, All, some, Any, Many, Every, multiple} | E > N |
| TDR26 | det(E,DT) | DT in {a, an} | E > 1 |

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
- `docs/application/RULES.txt` - Original rule specifications
- `./MAVEN.md` - Maven configuration documentation
- RuleBook Documentation: https://github.com/deliveredtechnologies/rulebook


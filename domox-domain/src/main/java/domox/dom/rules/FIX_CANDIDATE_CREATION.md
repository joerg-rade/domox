Let me dig into the rule classes and the candidate creation logic to see why nothing is being produced.

I've traced through the code that runs `analyzeDocument` and the rule pipeline. Here's the diagnosis.

## What's happening

`analyzeDocument` logs "Created 0 candidates" and says the analysis actually completed — no exception. That means every rule returned **empty candidate lists**, not that the pipeline crashed. The key is in `TypedDependencyRule.analyze()`:

```java
public List<Candidate> analyze(Sentence sentence) {
    List<Candidate> candidates = new ArrayList<>();
    for (TypedDependency dependency : sentence.getTypedDependencies()) {
        if (appliesTo(dependency)) {                    // calls when()
            Candidate candidate = createCandidate(dependency, sentence, candidates);
            if (candidate != null && !candidates.contains(candidate)) {
                candidates.add(candidate);
            }
        }
    }
    return candidates;
}
```


For a candidate to be created, three things must all hold:

1. `when()` must return `true`
2. `createNewCandidate(...)` must return a non-null `Candidate`
3. `candidate != null && !candidates.contains(candidate)` must pass

Here's why that chain fails in practice:

### 1. `createNewCandidate` returns `null` for most rules
The base class `TypedDependencyRule` has:

```java
@Programmatic
protected Candidate createNewCandidate(TypedDependency dependency, Sentence sentence) {
    return null;
}
```


It's a **no-op by default**. Only rules like `TDR1` (which overrides it and injects `ClassCandidates`) actually return a candidate. Any rule that relies on the default silently yields `null`, which is then discarded by the `candidate != null` check. So count of rules working end‑to‑end = count of rules overriding `createNewCandidate`.

### 2. `when()` itself rarely matches
Look at the actual dependency data (from `BasicDependencyTO.txt` — the first sentence of the Pet Shop sample). The dependencies include `ROOT`, `mark`, `det`, `amod`, `nsubj`, `aux`, `advcl:though`, `dobj`, `cc`, `conj:or`, `case`, `nmod:of`, `nmod:poss`, `punct`, `advmod`, `nmod:through`, `acl:relcl`, `ref`…

Now check what `TypedDependency` predicates actually match:
- `compound()` → needs `TdType.COMPOUND` — **no `compound` dependency appears in this sentence at all**
- `nsubj()` → matches the two `nsubj` entries, but `TDR1` also requires `isVerbA(currentTd) () && isNounB(currentTd) () && !currentTd.isBasicAttributeB()` — i.e. the governor must be a VB and the dependent a Noun, which depends on `governorPos`/`dependentPos` being populated, and especially on `isVerbA()` which checks a **different** array that only includes VB, VBG, VBN, VBP, VBZ
- Several tests/methods (e.g. `nsubjpass`, `pobj`, `dobj`, `iobj`, `nmod*`) are mostly `getType().equals(...)` against `TdType` values that may not be defined for the parsed UD labels (e.g. `dobj` → `OBJ`, `nmod:of` → `NMOD_OF`, `nmod:poss` → `NMOD_POSS`, `advcl:though` → `ADVCL` …). If those enum constants map to the wrong labels, or the parser stores the raw UD label differently, none of the `when()` predicates will ever be true.

### 3. The `nextTd` field is never populated
`TypedDependencyRule` and `TypedDependencyRuleWithPreviousAndNext` only declare `currentTd`, `previousTd`, `nextTd` with annotations; nothing in `analyze()`/`appliesTo()` ever *sets* `previousTd`/`nextTd` to anything. `appliesTo` sets only `currentTd`:

```java
public boolean appliesTo(TypedDependency dependency) {
    this.currentTd = dependency;
    return when();
}
```


So `previousTd`/`nextTd` are **always null**. This explains:
- the original NPE in `TDR12` (why we added the guard)
- why rules like `TDR11` (reads `previousTd` in `then()`) — though that one only runs if `when()` returns true — and any rule that relies on neighbors can never fire correctly even if a candidate gets created

### 4. The `contains` dedup check
`analyze()` loops over each `TypedDependency` and calls `appliesTo(dependency)`. `appliesTo` mutates the shared `currentTd` field (and would have mutated `previousTd`/`nextTd` had they been set). Because the candidate is created with `sentence` but the sentence/dependency association is effectively never set on the Candidate (see `createNewCandidate` in most rules), even when a match occurs, `candidates.contains(candidate)` may treat separately-created candidates as equal and skip adding them.

## Why TDR1 doesn't produce anything on the sample either

Honestly, even the one rule that overrides `createNewCandidate` (`TDR1`) won't fire on the sentence shown unless every single condition in its `when()` is true, and given:
- `nsubj/paradigm` → depends on `isVerbA()` and `isNounB()` being correct on the parsed data
- `previousTd.compound()` in the `then()` path requires `previousTd` to be set (it's never set)

…it can't fully work either.

## How to fix

**1. Populate `previousTd` and `nextTd` in `Analyze(...)`, then normalize the API.** For example in `TypedDependencyRule`:

```java
public List<Candidate> analyze(Sentence sentence) {
    if (sentence == null) {
        return java.util.Collections.emptyList();
    }
    List<Candidate> candidates = new java.util.ArrayList<>();
    List<TypedDependency> deps = sentence.getTypedDependencies();
    for (int i = 0; i < deps.size(); i++) {
        TypedDependency dependency = deps.get(i);
        this.currentTd = dependencyigs;
        this.previousTd = i > 0 ? deps.get(i - 1) : null;
        this.nextTd = i + 1 < deps.size() ? deps.get(i + 1) : null;
        if (appliesTo(dependency)) {
            Candidate candidate = createCandidate(dependency, sentence, candidates);
            if (candidate != null && !candidates.contains(candidate)) {
                candidates.add(candidate);
            }
        }
    }
    return candidates;
}
```


(Note: `previousTd`/`nextTd` are declared in `TypedDependencyRuleWithPreviousAndNext`, so either move them up into the base or set them via a protected setter. Also, because `appliesTo()` currently re-sets `currentTd` but not the neighbor fields, `appliesTo` should either take the neighbours or we set everything before calling `when()`.)

**2. Give the base class a usable default for `createNewCandidate`.** At minimum, make it instantiate a `Candidate` (e.g. a `ClassCdd`) when the rule says it applies, or at least log a warning when a rule returns `null` so you can see which rules are dropping work:

```java
protected Candidate createNewCandidate(TypedDependency dependency, Sentence sentence) {
    ClassCdd candidate = ... // instantiate with ClassCandidates.findOrCreate(...)
    return candidate;
}
```


**3. Audit the `TdType` enum / parse mapping.** Verify that the `TdType` enum values (`TdType.COMPOUND`, `TdType.NSUBJ`, `TdType.OBJ`, etc.) match exactly the `dep=` labels emitted by the CoreNLP output you're parsing (`compound`, `nsubj`, `dobj`, `nmod:of`, `advcl:though`, `acl:relcl`…). A mismatch here is the #1 reason `when()` stays `false` and nothing is created:
- `compound()` must match `compound`
- `dobj()` must match `obj` (you currently map it to `TdType.OBJ`)
- `nmodelOf()` must match `nmod:of`, etc.

**4. Add debug logging** in `Analyse.analyze(...)` to see, per rule, how many dependencies matched `when()` and how many candidates were dropped:

```java
for (TypedDependencyRule rule : rules) {
    List<Candidate> found = rule.analyze(sentence);
    log.info("Rule {} matched on {} dependencies of sentence {}: {} candidates",
        rule.getRuleName(), sentence.getTypedDependencies().size(), sentence.getId(), found.size());
    candidates.addAll(found);
}
```


This will tell you immediately whether the lack of results comes from `when()` never firing (TdType mapping / POS fields) or from candidates being discarded (default `createNewCandidate` returning null / `contains` dedup).

Would you like me to apply any of these fixes (particularly steps 1, 2, and 4) to the actual source files?
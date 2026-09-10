Let me examine the rule implementations in the domain module to understand which attributes they use.

Based on the rules (TDR1–TDR37) and the transfer objects in `domox.nlp`, here's the breakdown of where the attributes each rule needs actually come from.

## The attributes the rules need

The rules consistently reference the following:

| Needed by rules | Meaning |
|---|---|
| dependency type (e.g. `nsubj`, `dobj`, `nmod:of`) | The grammatical relation |
| `A` | The **governor** token (head/verb) |
| `B` | The **dependent** token |
| POS of `A` | e.g. `isVerbA()`, `isNounA()` |
| POS of `B` | e.g. `isNounB()`, `isAdjectiveB()`, `isVerbB()` for some checks |
| `prevTD` / `nextTD` | The neighboring dependency in sequence |
| `Basic_Attrib` | The hard-coded list `{name, number, type, address, level, date, time}` |
| word text of `A`/`B` | e.g. checking `A ∈ {input, enter, fill, …}` or `B = 'system'` |

## What `BasicDependencyTO` already provides

`BasicDependencyTO` maps one-to-one onto a dependency relation and gives you:

- **`dep`** — the relation type (`nsubj`, `dobj`, `nmod:of`, …) used by every `if Dependencies = …` guard
- **`governor` / `governorGloss`** — the identity and text of token `A`
- **`dependent` / `dependentGloss`** — the identity and text of token `B`
- the **governor↔dependent structure** needed to build the dependency tree

So the *core* of each rule — spotting the relation type and extracting `A`/`B` word text — is fully covered by `BasicDependencyTO` alone (or `SentenceTO.basicDependencies`/`enhancedPlusPlusDependencies`, which are `List<BasicDependencyTO>`).

## What is **not** available from `BasicDependencyTO`

### 1. Part-of-speech of A and B
Rules such as TDR1–TDR5 (`A=VB|VBN and B=Noun`), TDR6 (`A=noun`), TDR11 (``B=JJ``), TDR24 (`amod(E1, JJ)`), TDR25 (`nummod(E1, CD)`), TDR26 (`det(E1, DT)`) all need POS tags. `BasicDependencyTO` has **no** POS field.

Where these come from in this codebase:
- In the **service TO layer**: `TokenTO` has a `pos` field, and `ExtendedDependencyTO` already flattens the dependent's POS onto the dependency (its `pos` field). Note `ExtendedDependencyTO` is a *superset* of `BasicDependencyTO` — this is exactly what `ColoredPlantUmlMindmapGenerator` already does by enriching `enhancedPlusPlusDependencies` with `TokenTO.pos`.
- Note that the rules check the POS of **both** A and B (`isVerbA`, `isNounB`, `isAdjectiveB`, `isNounA`…). `ExtendedDependencyTO` only carries the dependent's POS. To get the **governor's** POS you must look up the token by index in `SentenceTO.tokens`, which requires the `SentenceTO.tokens: List<TokenTO>` list.

### 2. `prevTD` / `nextTD`
TDR3, TDR5, TDR12, TDR27–TDR31, TDR35 all branch on "if prevTD = compound" or loop `while (TD ≠ …)`. `BasicDependencyTO` is a single, standalone relation with no notion of neighbours.

- In the **domain layer**, these come from `TypedDependencyRuleWithPreviousAndNext`, which injects `@Given("previousTd")` and `@Given("nextTd")` — i.e. the neighbouring `TypedDependency` instances in the sentence's ordered dependency list.
- In the **service TO layer**, there is no dedicated "neighbour" TO. You would need either the full ordered `SentenceTO.enhancedPlusPlusDependencies` list (so you can look at index ±1) or a new TO that bundles previous/current/next.

### 3. `Basic_Attrib` list
`isBasicAttributeB()/isBasicAttributeA()` (used to choose Entity vs Attribute in TDR1–TDR2, TDR6, TDR9–TDR13) rely on a **hard-coded reference list** `{"name","number","type","address","level","date","time"}`. This is not part of any transfer object — it lives in `domox.dom.nlp.TypedDependency` (the domain entity). A service-side implementation would have to replicate that constant (e.g. in a rule/utility class in `domox.nlp`).

### 4. The domain entity for rule execution
If you want to actually run these rules, the codebase drives them off the persisted domain types (`TypedDependency`, `Token`, `Sentence`) via RuleBook annotations (`@Given("currentTd")`, etc.). `BasicDependencyTO` is the *input* used to populate those entities — see `Sentences.assignTypedDependencies(SentenceTO, Sentence)`, which maps `enhancedPlusPlusDependencies` (`BasicDependencyTO`) into `TypedDependency` and builds the `Token` list with `PartOfSpeechType` so that `isVerbA()`, `isNounB()`, etc. become computable.

## Summary

- **Available directly from `BasicDependencyTO`:** relation type (`dep`), governor/dependent indices and glosses (A and B word text), and the graph structure.
- **Must come from other TOs:**
    - **`TokenTO` / `ExtendedDependencyTO`** → POS tags (dependent), and `SentenceTO.tokens` to resolve the **governor's** POS.
    - **`SentenceTO.enhancedPlusPlusDependencies` (the full ordered list)** → prev/next dependency context (`prevTD`/`nextTD`).
    - **`SentenceTO`** generally → sentence-level text/index needed to reconstruct the token stream and validate ordering-based loops.
    - **Not a TO at all:** `Basic_Attrib` (a constant in `TypedDependency`), and the rule-execution plumbing (`TypedDependency`, `Token`, `Sentence` entities) if you run the rules through the domain RuleBook engine.

Essentially: `BasicDependencyTO` is a *pure structural* view of one dependency; the rules' POS checks, neighbour checks, and the Basic-Attribute concept all require enrichment from `ExtendedDependencyTO`/`TokenTO`, the full `SentenceTO`, and the `Basic_Attrib` constant respectively.
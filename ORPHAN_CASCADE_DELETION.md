# Analysis: Orphan Candidate entities on Document deletion

After `Analysis.loadFileSample()` loads the PetShop use-case suite, `analyzeDocument()` runs all TDR rules on every parsed sentence, producing `RuleMatch` records. These are then fed into `createCandidatesFrom()`, which uses `findOrCreate` to produce `Candidate` entities (`ClassCdd`, `ActionCdd`, `PropertyCdd`, `AssociationCdd`).

## Current design (final): a corpus owns one shared `DomainModel`

Each `Corpus` owns exactly one `DomainModel` that **all** of its documents share, so the whole use-case suite writes into a single model and candidate names de-duplicate across documents.

```
Corpus
  @OneToOne(cascade = ALL, orphanRemoval = true)   domain_model_id
  → DomainModel
      @OneToMany(cascade = ALL) mappedBy="domainModel"
        → ClassCdd
            → PropertyCdd / ActionCdd / AssociationCdd (mappedBy = "classCdd")
        → PropertyCdd      (orphans, classCdd = null)
        → ActionCdd        (orphans, classCdd = null)
        → AssociationCdd   (orphans, classCdd = null)

Document
  @ManyToOne(cascade = {PERSIST, MERGE})   domain_model_id  → the shared model
  @ManyToOne                                corpus_id         → owning Corpus
```

Key consequences of this design:

- **No per-document model.** `Analysis.analyzeDocument()` reuses the corpus’s model (get‑or‑create) instead of creating a fresh one per document. Loading N documents used to accumulate N cumulative `DomainModel`s and re-materialise the entire global rule-match set into each one — every candidate name appeared exactly N times. With a single shared model (and `findOrCreate` scoped by `DomainModel`), there is now exactly one candidate per name across the whole suite.
- **A `Document` no longer cascade-deletes the candidates.** Because `Document.domainModel` is `@ManyToOne` (not an orphan-removed `@OneToOne`), deleting a document does **not** remove the shared model. Candidate cleanup is the responsibility of the owning `Corpus`.
- **`Corpora.deleteCorpus(Corpus)` performs a full reset of one corpus.** It drops the chosen corpus's documents first (their `domain_model_id` FK points at the shared model), then clears that corpus's model — orphan-removing every candidate `ClassCdd`/`PropertyCdd`/`ActionCdd`/`AssociationCdd`, including previously-orphaned rows — removes the empty corpus from the repository, and finally purges any remaining `RuleMatch` records.

The historical notes below (problem symptoms, why naive cascade-delete from `Document` was unsafe, and the intermediate per-run scoping step) are kept for context.

---

## Historical problem: orphan `Candidate` entities on `Document` deletion

Originally each `Document` owned its own `DomainModel` with the cascade chain:

```
Document (CascadeType.ALL, orphanRemoval = true)
  → DomainModel (CascadeType.ALL)
    → ClassCdd (CascadeType.ALL)
      → PropertyCdd (mappedBy = "classCdd")
      → ActionCdd  (mappedBy = "classCdd")
      → AssociationCdd (mappedBy = "classCdd")
```

However, some `Candidate` entities survived deletion because they were **outside any cascade path** from `Document`.

## Which Candidates survived deletion

| Candidate type | Why it survives |
|---|---|
| `ActionCdd` with `classCdd = null` | `ActionCdd` has no `domainModel` field, and a null `classCdd` means it is not in any `ClassCdd.actionList`, so the cascade never reaches it. |
| `PropertyCdd` with `classCdd = null` | Same reasoning — no owning `ClassCdd`, no cascade path. |
| `AssociationCdd` with `classCdd = null` | Same — outside the cascade chain. |

TDR35 (the if/then/else rule) creates `ActionCdd` entities **without setting `classCdd`** because the `relatedCandidateName` field is null. The candidate name is built as `capitalizeFirstLetter(keyword + b)`. When the conditional keyword `"if"` is concatenated with the B token of the matched dependency, the result is often meaningless:

| NLP Dependency | keyword | `currentTd.getB()` | Candidate name |
|---|---|---|---|
| `advcl:if(notify, unable)` | `"if"` | `"unable"` | **"Ifunable"** |
| `mark(if, be)` | `"if"` | `"be"` | **"Ifbe"** |

These `ActionCdd` records are persisted with `classCdd = null`, so when the `Document` is deleted, they are **not** reachable through the cascade chain and remain as orphaned rows in the `ActionCdd` table.
## Risks of adding a cascade from Document → all Candidates

The obvious fix would be to extend the cascade so that every `Candidate` (regardless of owning `ClassCdd`) is deleted when its source `Document` is removed. This carries several risks.

### 1. Cross-document Candidate sharing

All `findOrCreate` methods do a **global lookup by candidate name**, not scoped to a `DomainModel`:

```java
// ClassCandidates
public ClassCdd findOrCreate(String candidateName, DomainModel domainModel) {
    ClassCdd candidate = classCddRepository.findByCandidateName(candidateName);  // global!
    if (candidate == null) {
        candidate = create(candidateName, domainModel);
    }
    return candidate;
}
```

If Document A (PetShop) creates `"Customer"` and Document B (Banking) calls `findOrCreate("Customer", B)`, it receives the **same entity** from A **without** adding it to B's `DomainModel.classList`. Deleting A would then cascade-delete `"Customer"` even though B still references it via `RuleMatch` join-table rows and `AssociationCdd` source/target FKs. The result is a **PostgreSQL FK violation** on `FK_AssociationCdd_SOURCE_ID` or `FK_AssociationCdd_TARGET_ID`.

### 2. AssociationCdd source/target FK constraint violations

`AssociationCdd` has two `@OneToOne` references that are **not** in the cascade:

```java
@ManyToOne private ClassCdd classCdd;   // in cascade
@OneToOne  private ClassCdd source;      // NOT in cascade
@OneToOne  private ClassCdd target;      // NOT in cascade
```

A cascade-delete that removes a `ClassCdd` serving as `source` or `target` of an `AssociationCdd` owned by a **different `DomainModel`** fails with:

```
ERROR: update or delete on table "ClassCdd" violates foreign key constraint "FK_AssociationCdd_SOURCE_ID" on table "AssociationCdd"
```

### 3. Many-to-many join-table orphans (`Candidate_RuleMatch`)

`Candidate` has:

```java
@ManyToMany
@JoinTable(schema = DomainModule.SCHEMA)
private List<RuleMatch> ruleMatches = new ArrayList<>();
```

JPA does **not** clean M:M join-table rows when the owning entity is cascade-deleted. The `Candidate_RuleMatch` table accumulates rows pointing to now-gone Candidate IDs. If the DB has FK constraints on that join table the delete fails; if not, the data silently rots.

### 4. Review data loss

`Candidate` has `orphanRemoval = true` on its `reviews` list. Cascade-deleting a `Candidate` also cascade-deletes all its `Review` records, even those from a different document's review workflow. There is no recovery path.

### 5. Re-analysis instability with orphanRemoval

During `analyzeDocument()` the old `DomainModel` is orphan-removed when `document.setDomainModel(newDm)` is called. Because `orphanRemoval = true`, the entire old class list is scheduled for deletion at flush time. If a flush happens mid-analysis (e.g., from a `persistAndFlush` inside a TDR rule), the persistence context can become inconsistent:

| Scenario | Outcome |
|---|---|
| Old Candidate still managed → `findOrCreate` returns it without re-adding to new classList → flush deletes it | `EntityNotFoundException` or silent data loss |
| Old Candidate flushed & deleted → `findOrCreate` can't find it → creates a new one with a different ID → old `RuleMatch` M:M refs point to a deleted ID | FK violation on `Candidate_RuleMatch` |
| Two documents share `"Customer"` → one deleted mid-session → `findOrCreate` for the other doesn't find it → creates a duplicate | Duplicate candidate names in the UI |

## Conclusion & Implementation

The data model's **global `findOrCreate` sharing pattern** made it unsafe to cascade-delete from a `Document` to all `Candidate` entities. The problem was resolved in two steps:

**Step 1 (scoping).** Candidates were bound to their analysis run: `ActionCdd`, `PropertyCdd` and `AssociationCdd` gained a `domainModel` field (always set even when `classCdd` is `null`, e.g. TDR35 orphan actions whose owning class is a blocked use-case noun), `DomainModel` gained `@OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)` collections for `actionList`/`propertyList`/`associationList` in addition to `classList`, and the `findOrCreate`/`findByCandidateName`/`findByClassAndName` paths were scoped by `DomainModel` via new `findBy*AndDomainModel` queries (falling back to the global lookup only when `domainModel == null` for UI convenience paths). This eliminated cross-document candidate sharing and duplicate-name ambiguity, and guarantees `AssociationCdd.source`/`target` (non-cascade `@OneToOne` to `ClassCdd`) always live in the same analysis run.

**Step 2 (ownership).** Candidate ownership moved from each `Document` to the owning `Corpus`. A `Corpus` now owns **one shared `DomainModel`** (`@OneToOne(cascade = ALL, orphanRemoval = true)`), and every `Document` points at that shared model via `@ManyToOne(cascade = {PERSIST, MERGE})`. `Analysis.analyzeDocument()` reuses the corpus’s model (get-or-create) instead of creating a fresh one per document. This is the crucial fix for the original bug: loading N documents no longer creates N cumulative models, so there is **exactly one candidate per name across the whole corpus** instead of each name appearing N times.

Because a `Document` no longer owns the model, deleting a single document does **not** cascade-delete the candidate set — candidates are removed by clearing the corpus-owned model. `Corpora.deleteCorpus(Corpus)` implements the full reset of one corpus: it deletes that corpus's documents first (their `domain_model_id` FK points at the shared model), then clears the corpus's model (orphan-removing every candidate `ClassCdd`/`PropertyCdd`/`ActionCdd`/`AssociationCdd`, including previously-orphaned rows), removes the empty corpus from the repository, then purges any remaining `RuleMatch` records.

Notes / residual caveats:

- The convenience `create(String)` / single-arg `findOrCreate` paths (auto-create a `DomainModel`) leave it to the caller to attach a model, and those candidates keep a nullable `domainModel`. In the normal `analyzeDocument()` flow a `DomainModel` is always supplied.
- `AssociationCdd.source`/`target` remain non-cascade `@OneToOne` to `ClassCdd`; scoping guarantees any source/target lives in the same analysis run, so a cascade-removed `DomainModel` never deletes a `ClassCdd` still referenced by an association owned by a different model.
- The `Candidate_RuleMatch` M:M join-table and `Review` records are removed together with their owning `Candidate` by standard JPA handling of the owning `Candidate` side.
- The change does **not** auto-clean pre-existing polluted data; the dev schema is EclipseLink-driven (`ddl-generation=create-tables`) and is dropped/recreated at startup. A regeneration test is provided by `CorpusCandidateDedupIntegTest`, which loads the full 15-document PetShop suite and asserts exactly one candidate per name.
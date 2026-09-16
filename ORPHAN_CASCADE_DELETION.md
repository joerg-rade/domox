# Analysis: Orphan Candidate entities on Document deletion

After `Analysis.loadFileSample()` loads the PetShop use-case text, `analyzeDocument()` runs all TDR rules on every parsed sentence, producing `RuleMatch` records. These are then fed into `createCandidatesFrom()`, which uses `findOrCreate` to produce `Candidate` entities (`ClassCdd`, `ActionCdd`, `PropertyCdd`, `AssociationCdd`).

When the user later deletes the PetShop `Document` via `Documents.delete(document)`, the cascade chain in the JPA annotations **does** clean up most of the derived data:

```
Document (CascadeType.ALL, orphanRemoval = true)
  → DomainModel (CascadeType.ALL)
    → ClassCdd (CascadeType.ALL)
      → PropertyCdd (mappedBy = "classCdd")
      → ActionCdd  (mappedBy = "classCdd")
      → AssociationCdd (mappedBy = "classCdd")
```

However, some `Candidate` entities **survive** deletion because they are **outside any cascade path** from `Document`.

## Which Candidates survive deletion

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

## Conclusion

The data model's **global `findOrCreate` sharing pattern** makes it unsafe to cascade-delete from `Document` to all `Candidate` entities. A proper fix would require **scoping `findByCandidateName` lookups to a `DomainModel`** (making Candidates local to a single analysis run), which is a larger architectural change. The immediate fixes are:

1. **Prevent meaningless candidate names** in rules like TDR35 (filter out concatenations with auxiliary/copular verbs).
2. **Delete orphaned `RuleMatch` records before re-analysis** (clear `ruleMatches.deleteAll()` at the start of `analyzeDocument()`) to stop stale data from accumulating.
3. **Delete `ActionCdd`/`PropertyCdd`/`AssociationCdd` entities without an owning `ClassCdd` when a `DomainModel` is cleaned up**, by adding a `domainModel` field to those entities and extending the cascade.
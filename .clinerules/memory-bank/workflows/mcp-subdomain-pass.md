# DoMoX Sub-domain Pass — Agent Runbook

Speed-run playbook for the task type: **"partition the domain into sub-domains and classify each
as core / supporting / generic, from the use-case corpus, over the MCP pipeline."** It is the
companion of `mcp-review-pipeline.md`: that one decides *which candidates are real*, this one
decides *which business area each real class belongs to*.

The generic method lives in the `identify-subdomains` skill (cohesion cues, clustering pass,
boundary tests, classification signals, output template). This file is the DoMoX wiring: where the
evidence is, which tools move it, and what the numbers are.

---

## 1. When to use this runbook

The task signature includes any of:

- "populate / fill the sub-domain table", "assign sub-domains", "partition the domain"
- "core / supporting / generic", "subdomain map", "which area does X belong to"
- anything about `SubDomains`, `ClassCdd.subDomain`, `suggestSubDomains`, `listUseCases`

**Prerequisite:** the candidate-review pass (`mcp-review-pipeline.md`) has run. Only **approved**
class candidates are sub-domain material — the tools filter on approval for exactly that reason.

---

## 2. Where the evidence is (verified 2026-11)

Every class candidate is traceable to the use cases that talk about it:

```
UC01..UC15.md → domox.document → domox.sentence → domox.typeddependency
              → domox.rulematch → domox.classcdd_rulematch → domox.classcdd.subdomain_id
```

| Item | Value |
|---|---|
| Corpus | **15 use-case documents** `UC01_SellingPetProducts` … `UC15_PetThemedMerchandise` |
| `domox.document` | 15 rows; ids 2, 1019, 2037, 3038, 4045, 4958, 5940, 6907, 7942, 8890, 9864, 10731, 11654, 12644, 13561 |
| Candidates evidenced per use case | 51–80 class candidates |
| `domox.subdomain` | `(id, name, version, classification)` — `classification` added 2026-11 |
| `ClassCdd.subDomain` | `@ManyToOne` → `classcdd.subdomain_id`; inline-editable in the Causeway UI |
| Evidence chain | `Candidate#getRuleMatches()` → `RuleMatch#getTypedDependency()` → `TypedDependency#getSentence()` → `Sentence#getDocument()` |

**The provenance read is `UseCases#provenanceFor(Candidate)`** — it walks the rule-match chain,
so it answers "which use cases the rules actually fired on", not "where does this word appear"
(that is `ContextEvidence#sentencesFor`, a different question).

---

## 3. The tools (all on `POST /mcp`)

MCP endpoint, session handling and the **`spring.ai.mcp.server.protocol` 404 trap** are in
`mcp-review-pipeline.md` §3 and §7 — reuse that driver verbatim.

| Tool | Kind | Returns |
|---|---|---|
| `listUseCases()` | 📖 read | the 15 use cases + how many class candidates each evidences and *dominates* |
| `listSubDomains()` | 📖 read | existing sub-domains with classification and owned-candidate counts |
| `listUnassignedClassCandidates()` | 📖 read | **approved** class candidates with no sub-domain (lean: id, name, type, approvalCount) |
| `subDomainEvidenceForCandidate(candidateId)` | 📖 read | one candidate's use-case profile + approval + current sub-domain |
| `suggestSubDomains()` | 🧠 advisory | the proposed partition; **writes nothing** |
| `createSubDomain(name, classification)` | ✍️ write | create, or re-classify an existing one (case-insensitive, idempotent) |
| `assignCandidatesToSubDomain(candidateIds[], subDomainName, classification)` | ✍️ write | one cluster per call; creates the sub-domain if missing |
| `clearCandidateSubDomain(candidateId)` | ✍️ write | undo one assignment |

> ⚠️ `suggestSubDomains` is **advice, not a decision**. It sees use-case provenance only — no
> business strategy, no roadmap, no org chart. Weigh it, rename the clusters, then decide.

---

## 4. The pass, in order

- **Phase 1 — Scope.** `listUseCases()` → the 15 areas. Read them as *business areas*, not as
  sub-domains (see §5).
- **Phase 2 — Evidence.** `suggestSubDomains()` → clusters, cross-cutting candidates, unevidenced
  candidates, and the reasons. Drill into anything surprising with
  `subDomainEvidenceForCandidate(id)`.
- **Phase 3 — Decide.** Apply §5 and §6: rename each cluster to the business's own word, split or
  merge where the business says so, set the classification.
- **Phase 4 — Write.** `createSubDomain(name, classification)` per sub-domain, then
  `assignCandidatesToSubDomain(ids, name)` per cluster — one call per cluster, ids straight from
  the proposal.
- **Phase 5 — Verify.** `listSubDomains()` must show every sub-domain with a non-zero count, and
  the SQL in §7 must show no approved class candidate left unassigned.

All writes are **idempotent** — re-running a pass after adjusting the clustering creates no second
sub-domain and duplicates no assignment.

---

## 5. Use cases are not sub-domains

The single most common mistake. `suggestSubDomains` proposes a conservative partition; the business
judgement is yours:

| Pattern | Example | What to do |
|---|---|---|
| **Many use cases, one sub-domain** | `UC02_PetGrooming`, `UC06_PetBoarding`, `UC07_PetDaycare` | assign all three to one sub-domain (`Care Services`) |
| **One use case, several sub-domains** | `UC08_OnlinePetStore` (catalogue / checkout / fulfilment) | split it — assign different candidates to different sub-domains |
| **Cross-cutting concept** | `Customer`, `Pet`, `Payment` | leave unassigned, or give it its own *shared* area; never hand it to one area |
| **Genuinely distinct** | `UC14_PetInsurance` vs `UC01_SellingPetProducts` | separate sub-domains |

**What the advisor does on its own** (thresholds are constants on `SubDomainAdvisor`):

| Rule | Constant | Meaning |
|---|---|---|
| Cross-cutting | `CROSS_CUTTING_USE_CASE_THRESHOLD = 5` | evidence spans ≥ 5 use cases → owned by nobody, left out of every cluster |
| Merge | `MERGE_SIMILARITY = 0.5` **and** `MERGE_MIN_SHARED = 2` | two areas merge only on ≥ 2 shared non-cross-cutting candidates at ≥ 0.5 Jaccard |
| Generic | `GENERIC_USE_CASE_SPAN = 3` | a cluster spanning ≥ 3 use cases is proposed `GENERIC` |

A candidate is assigned to the use case its evidence is **strongest** in (most sentences), and the
proposal says so when the candidate also appears elsewhere — read that reason, it is where the
splits are.

---

## 6. Classification is business judgement

`SubDomainClassification` = `CORE` | `SUPPORTING` | `GENERIC` | `UNCLASSIFIED`.

| Signal | Classification |
|---|---|
| Differentiating capability, worth bespoke modelling and the best people | `CORE` |
| Custom to this business but not differentiating | `SUPPORTING` |
| Solved the same way everywhere — buy or adopt | `GENERIC` |
| Cluster is broad (spans several use cases) — the corpus *does* show this | advisor proposes `GENERIC` |
| Anything else | **leave `UNCLASSIFIED`** and say what business input would settle it |

The corpus records **what** the business does, never **why it matters**. Core-vs-supporting is not
derivable from it — do not guess, and do not let a plausible-sounding cluster name imply a
classification.

---

## 7. Phase 5 — verify against the database

```sql
-- the sub-domains and how much each owns
SELECT s.id, s.name, s.classification, count(c.id) AS candidates
FROM domox.subdomain s LEFT JOIN domox.classcdd c ON c.subdomain_id = s.id
GROUP BY s.id, s.name, s.classification ORDER BY s.id;

-- approved class candidates still without a sub-domain (should be 0, or the intended leftovers)
SELECT count(*) FROM domox.classcdd c
WHERE c.subdomain_id IS NULL
  AND EXISTS (SELECT 1 FROM domox.review r
              WHERE r.candidate_id = c.id AND r.status = 'APPROVED');

-- assignments that do not correspond to any sub-domain (must be 0)
SELECT count(*) FROM domox.classcdd c
WHERE c.subdomain_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM domox.subdomain s WHERE s.id = c.subdomain_id);

-- duplicate sub-domain names (the name is the identity; duplicates should not exist)
SELECT lower(name) AS name, count(*) FROM domox.subdomain GROUP BY lower(name) HAVING count(*) > 1;
```

Shell: `docker exec domox-db psql -U postgres -d postgres -c "..."`.

---

## 8. Pitfalls

- **The `classification` column is newer than most databases.** Adding it is a JPA field on
  `SubDomain`, so the live dev DB needed
  `ALTER TABLE domox.SubDomain ADD COLUMN classification VARCHAR(255) NOT NULL DEFAULT 'UNCLASSIFIED';`
  — see `memory-bank/technical/SCHEMA.md` (EclipseLink `create-tables` never ALTERs). Run
  `bash scripts/check-schema-drift.sh` if the pass fails with *column "classification" does not
  exist*.
- **Approval first.** Assigning an unapproved candidate is allowed but reported in `messages` — a
  sub-domain normally follows a review decision, so run `mcp-review-pipeline.md` first.
- ⚠️ **Issue the MCP calls ONE AT A TIME.** Two calls in flight at once race: an
  `assignCandidatesToSubDomain` can run its name look-up before the `createSubDomain` that preceded
  it has committed, and creates a *second* sub-domain with the same name (verified: four rows where
  two were intended). Two concurrent updates of one sub-domain likewise fail with EclipseLink's
  *"cannot be updated because it has changed … since it was last read"*. Sequentially, every step
  converges. This is a caller discipline, not a server bug — and because every write is idempotent,
  re-running the offending call is the whole fix.
- **`suggestSubDomains()` is empty?** It lists *unassigned, approved* candidates only. Zero reviews
  → zero clusters, with the note *"No unassigned class candidates"*. That is not a failure.
- **Re-running is safe**, but re-running with a *renamed* sub-domain creates a second one (the name
  is the identity). Rename via the UI, or `clearCandidateSubDomain` and re-assign.
- **Nothing constrains the sub-domain name to be unique.** The look-up is case-insensitive and takes
  the lowest id, so a duplicate never throws — but it also is not merged. Check §7 for duplicates
  (`GROUP BY lower(name) HAVING count(*) > 1`) before assigning in bulk.
- **Do not batch Maven and boot in one shell call**; and long commands must be detached — see
  `commands.md`.

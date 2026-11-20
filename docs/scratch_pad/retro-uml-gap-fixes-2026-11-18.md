# 🔍 UML Gap-Fix Waves — Retrospective (2026-11-18 → 19)

> Produced with the `retro` skill (`.clinerules/skills/retro.md`) — it looks back over a session and
> proposes improvements to the **environment**, never to the code.
> **Status: all eight candidates implemented (2026-11-19)** — see [Status](#status) at the end of
> this document for where each one landed.

## Session summary

One task: execute the eight gap-fix waves (FIX-1…FIX-8) from `UML_GAP_FIX_PLAN.md` against the
Pet-Shop candidate model, per the five decisions already answered.

| Delivered | Detail |
|---|---|
| Source fixes | 8 — operation rendering, sentence-scoped multiplicities, parameters, `@Getter`/JAXB, metadata nouns, attribute lexicon, association labels, `cardinality` removal |
| New pure collaborators | `MultiplicityEvidence`, `AssociationLabel`, `ResponsibilityParameterExtraction` (+ pitest floors) |
| Tests | 5 new test classes, 4 extended; `domox-domain` 1006 → 1040, all green |
| Schema change | `AttributeCdd.cardinality` + `Cardinality.java` deleted, DDL regenerated, dev-DB column dropped |
| Docs | `UML_GAP_FIX_PLAN.md` §13/§14, `MODEL_REVIEW_PLAN.md` §12, `MODEL_UML_READINESS_REVIEW.md`, `RULES_REGISTRY.md` |

**It went well.** The plan doc carried per-wave acceptance criteria and an evidence trail, so "done"
was checkable rather than asserted; every wave went green on the first test run; the four guardrails
that did run (`check.sh` steps 2–7, mutation floors, `verify-boot.sh` `Validation PASSED`, the live
PlantUML export) all agreed with the tests.

**What it cost** was environment friction, not code: two bespoke RO clients because `ro.py` cannot
pass a reference, one schema-drift false positive that needed a `touch` to clear, a skipped metamodel
step that had to be hand-rolled, and two editor calls rejected for size.

---

## Candidates for improvement (in descending priority)

### 🚨 1. The session's steering document had no context pointer — and the one line covering its directory says *don't read it*

**The moment.** `docs/scratch_pad/UML_GAP_FIX_PLAN.md` was the specification for the whole session.
`grep` shows **no** file under `.clinerules/` mentions it, `MODEL_REVIEW_PLAN.md` or
`MODEL_UML_READINESS_REVIEW.md`. The only line about the directory is
`.clinerules/commands.md:172`:

> **Reference / archive docs** (completed-milestone how-to's: `RULES_*`, `COMPLETION_CHECKLIST.md`,
> `SYNONYMS.md`, implementation summaries) live in `docs/scratch_pad/` — on-demand only; skim with
> `head` first, **never a full read**.

That describes an archive, and the directory is no longer only an archive: it holds two **live plans**,
a **live reference** (`UML_LAYOUT_GEOMETRY.md`, which `.clinerules/d3-diagram.md` explicitly sends the
agent to for "the numbers behind them"), the `rules/` registry, `events/`, `ontology/`, and now the
past retros. "Never a full read" is correct for `IMPLEMENTATION_SUMMARY.md` and actively wrong for a
15-section plan the agent must execute wave by wave.

**Why it matters.** The plan is where the acceptance criteria, the evidence trail and the resolved
decisions live — the cheapest possible navigation win, and the one artefact a plan-bearing session
cannot proceed without. Today it rides entirely on **cognitive load** (the human remembering it
exists), which is exactly what the pointer tier is for.

**Proposed fix.** One edit to `.clinerules/commands.md`, replacing that bullet with three:

```markdown
- **Active work plans** → `docs/scratch_pad/*_PLAN.md` (`UML_GAP_FIX_PLAN.md`,
  `MODEL_REVIEW_PLAN.md`).  These ARE the task spec: read them fully, follow the wave order, and
  update their execution-status section as you go.  A review that produced gaps lives beside its
  plan (`MODEL_UML_READINESS_REVIEW.md`).
- **Past retros** → `docs/scratch_pad/retro-*.md` — read the one matching your task type before
  repeating a mistake it already paid for.
- **Archive** (completed-milestone how-to's: `RULES_*`, `COMPLETION_CHECKLIST.md`, `SYNONYMS.md`,
  implementation summaries) → skim with `head`, never a full read.
```

(Alternative: give plans their own `docs/plans/` directory, so the distinction is structural rather
than a naming convention. Either way the pointer is the load-bearing part.)

### 🚨 2. `check.sh` step 8 skips the metamodel guardrail exactly when the agent has been working

**The moment.** Step 8 printed
`[SKIP] a dev instance owns :8080 — stop it and re-run to validate the metamodel`, and the agent
hand-rolled the workaround: `scripts/verify-boot.sh` on a free port, then read `Validation PASSED`
out of its log.

**Why it matters.** The documented workflow *leaves* the app running — `run-app.sh` starts it detached
precisely so it survives the launching shell — so the skip is the normal case, not the exception. The
guardrail it skips is the only one that catches the bug class this session fixed (FIX-4: a Lombok
accessor on a public entity field becomes a JAXB duplicate property and kills the metamodel). CI runs
it; the local loop, where a developer would actually see it, does not.

**Proposed fix.** Make step 8 *run* instead of skip, reusing the recipe `verify-boot.sh` already
implements: a throwaway Postgres (`domox-db-verify`, port 5433) plus a free port, which is what avoids
the `domox.SEQ_GEN` contention the skip comment cites. `verify-boot.sh` returns in ~13 s warm, and
`--stop` tears down. A `[SKIP]` in a check bundle is a check that never runs.

### ⚠️ 3. The schema-drift gate fires on comment-only entity edits

**The moment.** Twice in this session `check.sh` step 1 reported `[FAIL] drift detected` for a
`create-tables.sql` that was **content-correct** — the second time after an edit that touched only a
javadoc block and a constructor body in `ResponsibilityCdd.java`. The gate is an mtime comparison
(`find domox-domain/src/main/java -name '*.java' -newer "$DDL"` filtered by `grep -lE '@Entity'`), so
any edit to any entity file trips it, whatever the edit says. The honest remedy — `touch
create-tables.sql` — looks like gaming the check, and the wrong remedy (re-boot, or worse, hand-edit
the DDL) costs minutes.

**Why it matters.** A guardrail that cries wolf on a comment gets worked around, and a worked-around
guardrail is worse than none: the next *real* drift will be `touch`-ed away too. Same failure shape
`CODING_STANDARDS.md` already names — "a guardrail that can only warn is not a guardrail".

**Proposed fix.** Compare a **projection**, not an mtime: strip comments and keep only
schema-relevant lines (`@Entity|@Table|@Column|@JoinColumn|@OneToMany|@ManyToOne|@OneToOne|@Basic`
plus field declarations), hash it, and write the hash beside the DDL when EclipseLink regenerates it.
A javadoc edit then vanishes from the comparison and a field edit still fails it. Cheaper interim:
strip `//`, `/* … */` and `*` lines from the sources before the `find -newer` filter; or document
`touch` as the sanctioned remedy for a comment-only edit, so the agent does not invent one.

### ⚠️ 4. `ro.py` cannot pass an object reference — the documented inspection tool could not run this session's key check

**The moment.** Validating the fix end-to-end needed
`generateUmlDiagram(domainModel: DomainModel/653)` — a **reference** parameter. `ro.py`'s
`cmd_invoke` builds `{"value": v}` from `k=v` strings only, so the call is un-expressible in it. The
agent wrote a bespoke RO client in Python, twice (once to inspect the response shape, once to dump the
62 k-char export to a file).

**Why it matters.** `.clinerules/commands.md` tells the agent to inspect the running app with `ro.py`
rather than reasoning about it — and the moment a service action takes an entity, the tool stops being
usable and the agent re-implements it. The re-implementation is where the mistake surface is: the
first attempt printed `chars: 0` because the result arrives as `resulttype: scalarvalue` under
`result.value`, not as an object member.

**Proposed fix.** Teach `ro.py` two things:

```shell
scripts/ro.py invoke domox.DomainModels generateUmlDiagram domainModel=@domox.DomainModel/653
scripts/ro.py invoke domox.DomainModels generateUmlDiagram domainModel=@653 --out /tmp/export.txt
```

i.e. a `@<objectRef>` value → `{"value": {"href": to_path(ref)}}` (`to_path` already exists), and
`--out FILE` / a `result.value` print for scalar-returning actions (`show()` already special-cases a
`"diagram"` member — the same need, half-implemented).

### 💡 5. Two always-loaded runbooks are branch material (478 of `.clinerules`' 1293 lines)

**The moment.** `.clinerules/*.md` is loaded every turn. `mcp-review-pipeline.md` (290 lines) fires
only for "review all N candidates / run the review pipeline"; `mcp-subdomain-pass.md` (188) only for
"partition the domain into sub-domains". Neither fired in this session.

**Why it matters.** The repo already has the on-demand tier — `.clinerules/memory-bank/technical/`
is loaded only when a pointer names it — so this is material sitting one rung too high on the
information hierarchy, spending context on every turn for a branch most sessions never take.

**Proposed fix.** Move both to `.clinerules/memory-bank/workflows/` and add one pointer line each to
`commands.md`, keeping the words that trigger them ("candidate-review pipeline", "sub-domain pass").
`hexagonal-architecture.md` (176) is the next candidate, but it reads as a standing standard for all
adapter work — leave it.

### 💡 6. The new mechanical rule is not named where the reviewer looks

**The moment.** The `@Getter`-on-a-public-field → JAXB duplicate-property bug class is now caught
deterministically by an ArchUnit rule in `domox-webapp` `ArchitectureTests`, but
`CODING_STANDARDS.md` §1 names only `ValidateDomainModelIntegTest`.

**Why it matters.** The file's own preamble says "wherever a rule is mechanically checkable the
automated guardrail is named", and the reviewer agent reads the standards, not the test sources. A
rule nobody is pointed at is enforced only by luck.

**Proposed fix.** One line in §1's guardrail paragraph naming the ArchUnit rule that fails on a Lombok
accessor over a public entity field, plus the reminder the checklist already carries (line 107) to
check the rule reports `Tests run: n > 0` — this session's rule reported **10** violations against the
stale jar before it was fixed, which is how the duplicate count was found to exceed the boot log's 6.

### 💡 7. `pitest-floors.py --check` silently accepts a class with no floor entry

**The moment.** Three new pure collaborators were floored **because the agent remembered to**. The
script gates only the classes listed in `FLOORS`; a new class with 40 mutants and no entry is
invisible to `--check`.

**Why it matters.** The floors are the mechanism that keeps extracted collaborators from decaying, and
their coverage depends on memory at the moment of extraction — the most context-pressured moment in a
session.

**Proposed fix.** Have `--check` print a warning line (exit 0) for any class at or above a mutant
threshold with no entry, so the omission surfaces at check time. The deliberate no-floor exceptions
(`Documents`, `ContextEvidence`, `UseCases` — orchestrators) are already documented in the script's
comments and would move into that same list.

### 💡 8. Two undocumented tool limits that each cost a round-trip

**The moment.** (a) Two editor calls were rejected for exceeding the `new_text` size cap (~6 k chars),
so the doc edits had to be split — the very cap this retrospective hit again while being written.
(b) `grep -cE 'Mutating|mutants' /tmp/mutation.log` printed `0` and exited **1**, which the shell tool
reported as `Command exited with code 1`, making a successful mutation run look like a failure until
the next line was read.

**Why it matters.** Both are cheap to document and both recur in any long session.

**Proposed fix.** One line in `commands.md`'s tooling section: large file edits must be split (~6 k
chars of `new_text` per call), and `grep -c` exits 1 on zero matches (`grep -c … || true`).

---

## What worked (keep)

- **The plan doc as a live artefact** — per-wave acceptance criteria, an evidence trail, a decisions
  table, and a §14 execution status the session updated as it went. This is why "done" was checkable.
  Its one weakness is finding it (candidate 1).
- **Building the check over writing the rule** — FIX-4 became an ArchUnit rule that reproduced the bug
  before it was trusted, rather than a prose standard.
- **`verify-boot.sh`** — the throwaway-DB + free-port recipe answered the metamodel question in one
  call when the check bundle refused to (candidate 2 wants it promoted into the bundle).
- **The live export as the acceptance test** — `(: void` 149 → 0 and the new verb edge labels were
  read off the running instance, not inferred from unit tests.
- **Writing down the two findings deliberately *not* fixed** (§13.6 possessive attributes, §13.7
  association de-duplication) as decisions with a one-line fix sketch, instead of leaving them silent.

---

## Status

**Implemented on 2026-11-19** (the session that compiled `TODO.md`):

| # | Candidate | Where it landed |
|---|---|---|
| **1** | The steering document had no context pointer | `.clinerules/commands.md` — the `docs/scratch_pad/` reading rules (live plan read in full; retros by header; archive on demand) |
| **2** | `check.sh` step 8 skipped the metamodel guardrail exactly when the agent had been working | `scripts/check.sh` step 9/9 now runs `ValidateDomainModelIntegTest` against a throwaway DB (`scripts/verify-boot.sh` promoted into the bundle) |
| **3** | The schema-drift gate fired on comment-only entity edits | `scripts/schema-projection.py` + the staleness guard in `scripts/check-schema-drift.sh` (hashes the *projection*, not the file mtime) |
| **4** | `ro.py` could not pass an object reference | `scripts/ro.py` — the `@<ref>` prefix for object-valued parameters, and `--out FILE` for large payloads |
| **5** | Two always-loaded runbooks were branch material | moved to `.clinerules/memory-bank/workflows/` (`mcp-review-pipeline.md`, `mcp-subdomain-pass.md`), with a one-line pointer at the top level |
| **6** | The new mechanical rule was not named where the reviewer looks | `CODING_STANDARDS.md` §1 names the ArchUnit rule `entity_public_fields_have_no_getter_of_the_same_name` |
| **7** | `pitest-floors.py --check` silently accepted a class with no floor entry | `scripts/pitest-floors.py` — the unfloored-class advisory note (`UNFLOORED_MIN_MUTANTS` / `UNFLOORED_MIN_RATE`) |
| **8** | Two undocumented tool limits that each cost a round-trip | `.clinerules/commands.md` §"Tool limits that look like task failures" (the ~6 000-char `new_text` cap and `grep -c` exiting 1 on zero matches) |

# 🔍 Sub-domain Overview Click-Through (package content links) — Retrospective (2026-11-25)

> Produced with the `retro` skill (`.clinerules/skills/retro.md`) — it looks back over a session and
> proposes improvements to the **environment**, never to the code.
> **Status: all six candidates implemented (2026-11-25)** — see [Status](#status) at the end of this
> document for where each one landed. Session analysed:
> `~/.cline/data/sessions/1795599808663_q4i0j` (957 messages, 482 tool calls, 468 shell commands).

## Session summary

One feature, carried to green: make **each class name inside a Sub-domain overview package body** render
and behave as a link — underlined, `pointer` cursor, click opens `/wicket/object/<objectType>:<id>` in a
new tab — plus the tail of the previous click-through / overview-weighting change set in the same files.

| Delivered | Detail |
|---|---|
| Server | `UmlDiagramLayout.Node` + `record Content(name, objectType, candidateId)` and a 9-arg convenience ctor |
| Wire | `DiagramJsonWriter` emits `"contents":[…]` **only when non-empty** — class-box JSON byte-identical |
| Assembly | `ClassDiagram.kt` `overviewOf` keeps the `ClassCdd` rows; `graphOverview` emits one `Content` per outward class |
| Client | `d3-integration.mjs` `appendUmlPackage` — one `isClickable` predicate drives underline + cursor + click; unpaired lines inert |
| Tests | `UmlDiagramLayoutTest` (brace-counting `nodeBody`), `ClassDiagramTest` (`graphOverview_tiesEachContentLineToTheClassItStandsFor`) |
| Guardrail | `verify-uml-render.sh/.mjs` fixture (Inventory paired, Checkout `Till` unpaired, Delivery empty) + 4 checks |
| Verification | `check.sh` **11/11, 112 `[OK]`, exit 0**; `verify-uml-render.sh` 66/66; `diff src vs target/classes` = `RESOURCE_IN_SYNC` |

**It went well.** The feature is client-heavy and its server half is *unreachable live* (the overview
payload is `@Programmatic`, and the Wicket page needs an Ajax session), yet "done" was still checkable:
the probe fixture covers the client, the two unit tests cover the server, and the additive wire change
meant no existing exact-JSON assertion had to move.

**What it cost** was environment friction, not code: 23 `sleep` polls (one of them returned *nothing*),
~200 KB of `search_codebase` output across 6 calls, and four calls spent rediscovering which diagram
payloads are readable over RO and where the dev DB's tables live.

---

## Candidates for improvement (in descending priority)

### 🚨 1. The 30 s shell-call cap is prose-only — and the rule that guards it is stated as a prohibition

**The moment.** 468 shell commands, **23** of them a `sleep`-based poll, against an always-loaded rule
(`.clinerules/commands.md:94`) that says *"→ `scripts/run-bg.sh`, never a `sleep` poll"*. The worst one:

```
sleep 45; tail -50 /tmp/umlclick-check.log; echo "--- exit: $(cat …) ---"
→ Command failed: Command timed out after 30000ms        (empty result, msg 372)
```

and four consecutive polls of the same log (`sleep 28/29; tail -6 /tmp/full-check.log`, msgs 575–581)
while a ~4-minute `check.sh` ran.

**Why it matters.** The prohibition names the failure but drags it into context, and it offers no *cheap
short* alternative for the question the agent actually has — *"is the run I already launched done yet?"*
`run-bg.sh --wait 25` requires re-specifying the whole command; so the agent reaches for `sleep`, and a
`sleep` over ~20 s is **killed with its output lost**, which reads like the task failed.

**Proposed fix.** A dedicated waiter, so the correct move is the shortest one:

```bash
# scripts/wait-for-bg.sh <logfile> [--wait 20] [--tail 15]
# waits (capped, so it can never exceed the tool's 30 s) for <logfile>.exit, then prints
# the exit code and the tail — or "still running" plus the n/11 progress line
```

and reword the rule positively: *"a run still going? → `bash scripts/wait-for-bg.sh <log>` — one call,
under the cap, never a bare `sleep`."*

---

### ⚠️ 2. `search_codebase` output is unbounded — 6 calls, ~200 KB, and one answered nothing

**The moment.** Result sizes in this session: **48111, 48110, 45978, 43931, 21843, 20923** characters —
i.e. two calls sat exactly on the ~48 KB cap. The worst pattern was searching for a bare punctuation
character:

```
\u2014        → Found 100 results … Searched 4 files      (truncated at the cap, msg 586)
" — "        → 362 chars                                  (the same question, asked usefully)
```

**Why it matters.** ~200 KB ≈ 50 k tokens on six locating calls, and the capped results *also* delivered
an incomplete answer, so the question had to be re-asked. The pointer in `commands.md` already names
`search_symbol` / `analyze_calls` for symbol-shaped questions — the session used them **0 times**.

**Proposed fix.** One line beside the *Finding code* pointer: keep a pattern **selective** — a bare
punctuation char or a common word fills the 48 KB cap and answers nothing; anchor it (`\bLegend\b`, or
two alternatives); and for a who-calls-what question go straight to `search_symbol` → `analyze_calls`,
which return locations rather than file bodies.

---

### ⚠️ 3. The dev DB's schema is `domox` — and no always-on or tech-ref doc says so

**The moment.** The natural first query reads as *"the database is empty"*:

```
select table_schema, table_name from information_schema.tables
  where table_schema not in ('pg_catalog','information_schema')   → domox | attributecdd …  (msg 909)
select id, name from domox.domainmodel                            → ERROR: column "name" does not exist
\dn                                                               → domox, public             (msg 911)
```

Every script qualifies the schema (`domox.classcdd` in `scripts/db-backup.sh`,
`migrate-crc-persisted-names.sql`), but the only doc that shows it is the **on-demand**
`memory-bank/workflows/mcp-review-pipeline.md:236` — never loaded outside the review pipeline. And
`domox.domainmodel` has only `id` + `version`: there is no documented way to name a model row.

**Why it matters.** A `public` query returning nothing is a *misleading* failure — it cost this session
two calls before the schema was found, and it will recur in every session that touches the DB (the
review pipeline's own verification SQL passes included).

**Proposed fix.** The pre-approved example in `.clinerules/commands.md:262` becomes
`docker exec domox-db psql -U postgres -d postgres -c "SELECT … FROM domox.<table>"` with the note
*"every table lives in schema `domox` (`public` is empty); `domox.domainmodel` has only `id` + `version`"*
— plus the same two lines in `memory-bank/technical/SCHEMA.md`.

---

### ⚠️ 4. Which diagram payloads are readable *live* is undocumented — four calls went into rediscovering it

**The moment.** The session wanted the real-model overview payload. The path taken:

```
ro.py invoke domox.DomainModels showUmlDiagrams domainModel=@/restful/objects/domox.DomainModel/1
  → 500, message "… is mandatory"                      (a guessed id; the error names the *parameter*)
ro.py invoke domox.DomainModels listAll                → members.objects, but element = {}   (msgs 913–915)
```

`memory-bank/technical/RO-API.md` documents `@<ref>`, the entity-parameter body shape and the
`GET`-on-an-entity 404 — but neither (a) *which* diagram reads exist over RO, nor (b) that a bogus href
surfaces as `'<X>' is mandatory` rather than as a bad-reference error. The overview payload is
`@Programmatic` and page-only; the offline route (the `uml-payloads` dump) was never reached for.

**Why it matters.** This is the retro's first category — a navigation pointer — and it is the exact cost
this session paid: four calls that ended in the honest limit *"the overview payload is not reachable
over the RO API"*, which one line in `RO-API.md` would have stated up front.

**Proposed fix.** Two lines in `RO-API.md`:

- *"Reachable live diagram reads: `UmlClassDiagram.d3Graph` (whole model / one sub-domain) and
  `OntologyDiagram`. `UmlDiagramSet`'s per-tab payloads are `@Programmatic` — read an *assembled* payload
  offline from `UmlLayoutIntegrityTest`'s dump, not over RO."*
- *"A bogus entity href surfaces as `'<X>' is mandatory`. Resolve a real id first —
  `select id from domox.domainmodel`, or the list's `collections/objects` link (elements are not inlined)."*

---

### 💡 5. The offline dump and geometry checker never see the assembled (package) flavour — so `contents` is fixture-verified only

**The moment.** The new link pair is asserted against hand-built entities and a probe fixture, and nowhere
against the real model:

- `UmlLayoutIntegrityTest.renderAll()` calls `UmlLayoutDiagrams.renderLayout(graph, name)` — the **static
  OntoUML-export** flavour. None of the 20 files in `domox-adapter/target/uml-payloads` carries
  `"packages":true`.
- `scripts/uml-diagram-report.py` (287 lines, run by `check.sh` + CI) has **no** `packages` / `contents`
  reference at all.
- `ClassDiagramTest` builds its `ClassCdd` rows by hand — no real model, no database.

**Why it matters.** This is the session's own open item (d), *"confirm real-model payload pair coverage
end-to-end"* — and it is unclosable by inspection, because the overview payload is not readable live
(candidate 4). The feature's only end-to-end guardrail is therefore missing at the exact point where a
regression (`graphOverview` stops emitting the pair, or a sub-domain's classes stop resolving) would be
invisible: the probe fixture would still pass.

**Proposed fix.** Two small additions, both already wired into `check.sh` + CI:

1. dump the **assembled** overview payload in the same test (or a sibling domain test) alongside the
   static ones;
2. add a package invariant to `uml-diagram-report.py --check`: *"in a `packages:true` payload every
   content line carries `objectType` + `candidateId`"* — and report the paired/total count, so a
   silently-unpaired real model is visible rather than merely absent.

---

### 💡 6. 403 of 468 shell commands carried a redundant `cd /home/jrade/projects/domox &&` — a verified no-op

**The moment.** Every command in the session is prefixed `cd /home/jrade/projects/domox && …`. Running
`pwd` alone returns `/home/jrade/projects/domox`: the `run_commands` tool already starts in the repo root,
so the prefix changes nothing — 13.3 KB of pure noise in one session, and the examples in
`commands.md` (the pre-approved list, lines 262–271) are where the habit comes from.

**Why it matters.** It is cheap, it is unambiguous, and it teaches a false belief about the working
directory — the one thing a wrong assumption about CWD breaks (`run-app.sh`'s EclipseLink relative path
is exactly such a case).

**Proposed fix.** Delete `cd … &&` from the examples in `.clinerules/commands.md` and add one line: *"the
shell tool starts in the repo root — `cd` only after you changed directory earlier in the same call."*

---

## What worked (keep)

- **The probe as the end-to-end guardrail for a feature whose server half is unreachable live.** A
  three-package fixture (Inventory paired, Checkout `Till` unpaired, Delivery empty) and four new checks
  in `verify-uml-render.sh` — in `check.sh` and CI — carried the whole client half. This is the pattern
  to reach for whenever `@Programmatic`/page-only code is involved.
- **The additive wire change.** `"contents"` is emitted only when non-empty, so every existing
  exact-JSON assertion stayed green and an older payload degrades to plain text. Changing the wire
  shape without moving a single existing assertion is the cheap way to change a payload.
- **The brace-counting `nodeBody` helper in the test.** The first regex (`[^}]*`) could not read a
  nested object; the test caught it before the probe did — a *test-written-then-trusted* story rather
  than a probe-only one.
- **`run-bg.sh` + `check.sh`'s `n/11` progress lines.** 271 calls carried 468 commands (≈1.7 per call),
  and a poll could say *"at step 8/11"* rather than guessing — the waiter candidate (1) only has to
  remove the last 23 of them.
- **Line-windowed reads.** 104 of 116 `read_files` entries specified a range, including on the 1478-line
  `d3-integration.mjs` — the 46 KB whole-file read is the exception, not the habit.
- **Verifying the deployed static resource.** `diff src vs target/classes` of `.mjs` =
  `RESOURCE_IN_SYNC`, after `mvn resources:resources`. Cheap, and it is the difference between "the fix
  is in the jar" and "the fix is in the source".

---

## Status

**Implemented on 2026-11-25:**

| # | Candidate | Where it landed |
|---|---|---|
| **1** | The 30 s cap was prose-only, with no cheap short alternative | `scripts/wait-for-bg.sh` — waits ≤25 s on a log's `.exit`, then prints the exit code and the tail (or `--grep PAT`, or the last `===> n/11`); exits 124 when still running, 2 on usage error. `.clinerules/commands.md` states the rule positively (*"a run still going? → `wait-for-bg.sh <log>`"*) and the `run-bg.sh` bullet now names its follow-up |
| **2** | `search_codebase` output is unbounded | `.clinerules/commands.md` *"Finding code"* — keep the pattern selective (a bare punctuation char or a common word fills the 48 KB cap and answers nothing; anchor it), and for a who-calls-what question go `search_symbol` → `analyze_calls`, which return locations, not file bodies |
| **3** | The dev DB's schema is `domox` and no doc said so | `.clinerules/commands.md` pre-approved `psql` example qualifies `domox.<table>`, and `.clinerules/memory-bank/technical/SCHEMA.md` gained *"The live DB is schema `domox` — `public` is empty"* (`domox.domainmodel` has only `id` + `version`) |
| **4** | Which diagram payloads are readable live was undocumented | `.clinerules/memory-bank/technical/RO-API.md` item **5** — only `wholeModelGraph` is RO-readable from `UmlDiagramSet` (`tabNames`/`payloadOf`/`DomainModels#umlClassDiagram` are `@Programmatic`), read an assembled payload offline from the dumps, and a bogus href surfaces as `'<X>' is mandatory` |
| **5** | The offline dump/checker never saw the assembled (package) flavour | `ClassDiagramTest#dumpsTheAssembledOverviewPayloadForOfflineAnalysis` dumps the overview to `domox-domain/target/uml-payloads/`; `scripts/uml-diagram-report.py` gained the package-pairing invariant (every `contents` line must carry `objectType` + `candidateId`) with the `plines`/`unpair` columns and a failure banner; `scripts/check.sh` passes both dump dirs; `.clinerules/d3-diagram.md` records it |
| **6** | 403 of 468 commands carried a no-op `cd /home/jrade/projects/domox &&` | `.clinerules/commands.md` — the pre-approved examples lost their `cd` prefixes, with the note that the shell tool starts in the repo root |


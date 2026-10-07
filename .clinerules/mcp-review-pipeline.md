# DoMoX Candidate-Review Pipeline — Agent Runbook

Speed-run playbook for the task type: **"review all N candidates (approve/reject) via the
MCP `ReviewMcpTools` pipeline, with LLM judgment."** Read this top-to-bottom once on first
use; future runs can jump straight to the phase you need. All facts below were verified
against the running system on 2026-09-25.

---

## 1. When to use this runbook

The task signature includes any of:
- "review the N candidates / approve / reject"
- "run the candidate review pipeline / Phase 1..4"
- "fix /mcp / MCP 404 / tools not registered"
- anything about `approveCandidate`, `rejectCandidate`, `nextCandidateForReview`, `ReviewMcpTools`

---

## 2. Environment facts (verified)

| Item | Value |
|---|---|
| Repo root | `/home/jrade/projects/domox` |
| MCP server | Spring AI 1.1.8, streamable HTTP, endpoint `POST http://localhost:8080/mcp` (SSE responses) |
| MCP tools class | `domox-webapp/src/main/java/domox/webapp/mcp/ReviewMcpTools.java` |
| MCP config | `domox-webapp/src/main/resources/application.yml` → `spring.ai.mcp.server.*` |
| Tools exposed | `nextCandidateForReview`, `approveCandidate(candidateId)`, `rejectCandidate(candidateId, rationale)` |
| DB | docker container `domox-db`, user=`postgres`, db=`postgres`, app schema=`domox` |
| Reviewer | every decision recorded as `username='agent'` (`Reviews.AGENT_USER`) via `findOrCreateReviewForAgent` — **idempotent** |
| `ReviewStatus` | `APPROVED`, `REJECTED` |
| `ReviewRationale` | `DUPLICATE`, `WRONG_CANDIDATE_TYPE`, `NOT_RELEVANT`, `INSUFFICIENT_INFORMATION`, `OTHER` |
| Corpus in this run | PetShop e-commerce use-case corpus (89 Class + 13 Property + 34 Action + 12 Association = 148) |

### 2.1 Candidate storage — IMPORTANT (TABLE_PER_CLASS)
`Candidate` uses **TABLE_PER_CLASS** inheritance. Real candidate rows live in the **per-type
tables**, NOT the base table:
- `domox.classcdd` (classtype int), `domox.propertycdd`, `domox.actioncdd`, `domox.associationcdd`,
  plus `packagecdd`, `parametercdd` (usually empty in this corpus).
- `domox.candidate` (base) and the `public`-schema tables (`tables.sql` legacy) are **stale/empty** — never query them for data.
- A candidate's evidence lives in the join table `<type>cdd_rulematch` (`<type>cdd_id`,
  `rulematches_id`) → `domox.rulematch` → `domox.typeddependency` → `domox.sentence`.

---

## 3. CRITICAL — the `/mcp` 404 root cause and fix

**Symptom:** `POST /mcp` returns 404 even on a fresh instance; the tools still show up in the
Wicket UI but MCP never registers.

**Root cause:** Spring AI 1.1.8 chooses the MCP HTTP transport via
**`spring.ai.mcp.server.protocol`** (values `STREAMABLE` | `SSE` | `STATELESS`). The app was
setting the nonexistent key `spring.ai.mcp.server.transport: STREAMABLE`. Spring Boot silently
ignored the unknown key → the streamable router function was never registered → 404.

**Fix (one config edit):** in `application.yml` change `transport:` → `protocol:`. Always:
- Rebuild resources AND restart the instance (auto-config on the same JVM won't pick it up).
- Note: the request/response alternative is `STATELESS`, not any `...HTTP` protocol value.

**Diagnose when unsure** (confirms the exact property name/condition):
```
# run app and read the autoconfig report (the -Dspring-boot.run.workingDirectory is required:
# EclipseLink resolves the CWD-relative DDL path against the repo root — see commands.md):
mvn -pl domox-webapp spring-boot:run -Dspring-boot.run.workingDirectory=/home/jrade/projects/domox -Dspring-boot.run.arguments=--debug
# look in the conditions report for:
#  McpServerStreamableHttpWebMvcAutoConfiguration ...
#    did not match: @ConditionalOnProperty (spring.ai.mcp.server.protocol=STREAMABLE)
#        did not find property 'protocol'
```

**Verify after fix:** run an MCP `initialize` (see §7) → expect HTTP 200, `serverInfo:
domox-review-server 0.1.0`, and `tools/list` returning the 3 tools. Do NOT accept "tools shown
in Wicket UI" as proof — that is unrelated to MCP registration.

---

## 4. Phase checklist (run in order)

- **Phase 0.5 — Sanity.** Docker up (`domox-db`), instance on 8080 returns `/mcp` 200 (if 404 → §3).
- **Phase 1 — Evidence.** Extract candidate ↔ evidence rows to a TSV (SQL in §6).
- **Phase 2 — Decisions.** Build `/tmp/decisions.tsv` (`id<TAB>APPROVE[<TAB>]` or `id<TAB>REJECT<TAB>RATIONALE`); apply §5 policy.
- **Phase 3 — Write.** Loop `approveCandidate`/`rejectCandidate` via the driver (§7).
- **Phase 4 — Verify.** DB counts (§8): reviews == candidate count, no unprocessed, all `agent`.

---

## 5. Phase 2 — Decision policy (heuristics + LLM judgment)

Produce a class diagram; **approve genuine domain elements**, **reject artifacts** with the
mapping below. Be decisive — the whole point is cleaning the candidate set.

| Rationale | Use for |
|---|---|
| `NOT_RELEVANT` | Doc/scaffolding: `#Case`, `Sentence`, `SourceSentence`, `MetadataField`, `OverviewField`, `SystemAction`, `BusinessRule`, `RequirementRule`, `CaseUC`, `UseUC`, `UC`, `**`, `\|`; combined artifacts (`SystemPayment`, `CheckoutPayment`, `SystemUpdate`, `PetSystem`, `ShopSystem`, `InventorySystem`, `CustomerVerification`, `CertainMedication`); generic/abstract (`Information`, `Reason`, `Intent`, `Help`, `Offer`, `Fund`, `assistance`) |
| `WRONG_CANDIDATE_TYPE` | An attribute/unit in the entity slot: `Quantity`, `Cost`, `Availability`, `Age`, `Range`, `Second`, `Time`, `Online`; a verb used as a class: `Update`, `groom`, `have`, `reflect`, `require`, `restrict`; a measure phrase: `WideRange`, `RealTime`, `TotalCost` |
| `DUPLICATE` | Capitalized `User_Action` dup of an accepted lowercase dobj action: `Select`/`select`, `Add`/`add`, `Process`/`process`, `Display`/`display`; a property that duplicates an entity (`cart` vs `Cart`) |
| `INSUFFICIENT_INFORMATION` / `OTHER` | Rarely needed |

ActionCdd policy: **approve** a genuine customer/system operation acting on a domain entity
(`browse`, `purchase`, `select`, `add`, `proceed`, `validate`, `Calculate`, `confirm`,
`Receive`, `notify`, `suggest`, `request`, `present`, `process`, `display`, `load`);
**reject** auxiliary/generic verbs (`have`, `use`, `allow`, `arrive`, `inform`, `ask`,
`provide`, `prompt`, `decline`, `create`) and marked-as-missing artifacts.

AssociationCdd (**`A --|> B`**) is `APPROVED` **only if both endpoints are approved entities**;
if either side is a rejected artifact, the generalization is spurious → `NOT_RELEVANT`.
(Real approved examples: `PhysicalStore|>Store`, `OnlineStore|>Store`, `OnlineShop|>Shop`,
`Inventory has Product`, `UnavailableItem|>Item`.)

### 5.1 Baseline for PetShop (this corpus) — canonical 54 approve / 94 reject
Approved ids by type (reference for diffing/regression):
- ClassCdd (30): 6640,6643,6644,6645,6646,6647,6652,6655,6659,6664,6666,6668,6670,6672,6676,6678,6680,6682,6684,6687,6688,6692,6694,6699,6728,6735,6739,6756,6764,6768
- PropertyCdd (3): 6686,6696,6754
- ActionCdd (16): 6669,6675,6698,6700,6704,6707,6708,6715,6719,6723,6724,6732,6734,6736,6745,6767
- AssociationCdd (5): 6677,6679,6681,6685,6693

Everything else REJECTED (statuses: NOT_RELEVANT 66, WRONG_CANDIDATE_TYPE 23, DUPLICATE 5).

---

## 6. Phase 1 & 4 — Exact SQL (run via `docker exec`)

### 6.1 Phase 1 evidence (candidate → rule match → typed dependency → sentence)
Run once per candidate type (swap the `<type>`/`<type>cdd_rulematch` names). Output columns
give you name, type, the matching rule + description, related candidate, and the raw sentence.

```sql
SELECT c.id, c.candidatename, c.candidatetype,
       rm.id AS rm_id, rm.ruleclassname, rm.description,
       rm.relatedcandidatename, rm.relatedcandidatetype,
       tdd.governorlemma, tdd.dependentlemma, tdd.type,
       s.id AS sentence_id, s.text
FROM domox.<typename> c
JOIN domox.<typename>_rulematch cr ON cr.<typename>_id = c.id
JOIN domox.rulematch rm ON rm.id = cr.rulematches_id
LEFT JOIN domox.typeddependency tdd ON tdd.id = rm.typed_dependency_id
LEFT JOIN domox.sentence s ON s.id = tdd.sentence_id
ORDER BY c.id;
```

Shell: pipe the quoted output into `grep`/`awk` to build a clean TSV. Keep the **distinct**
candidate list (id, name, type) separate to drive decisions — the cardinality is
`<type>cdd` rows (e.g. 89+13+34+12 = 148), and each candidate has multiple evidence rows.

### 6.2 Phase 4 verification — counts
```sql
-- total reviews == total candidates, all by agent
SELECT count(*) FROM domox.review;
SELECT status,   count(*) FROM domox.review GROUP BY status;
SELECT username, count(*) FROM domox.review GROUP BY username;
SELECT rationale,count(*) FROM domox.review WHERE status='REJECTED' GROUP BY rationale;
-- unprocessed candidates (per-type ids not covered by a review) MUST be 0
SELECT count(*) FROM (
  SELECT id FROM domox.classcdd UNION ALL
  SELECT id FROM domox.propertycdd UNION ALL
  SELECT id FROM domox.actioncdd UNION ALL
  SELECT id FROM domox.associationcdd
) c WHERE c.id NOT IN (SELECT candidate_id FROM domox.review);
-- type x status breakdown
SELECT t.type, r.status, count(*)
FROM domox.review r
JOIN ( SELECT id,'ClassCdd' t FROM domox.classcdd UNION ALL
       SELECT id,'PropertyCdd' FROM domox.propertycdd UNION ALL
       SELECT id,'ActionCdd' FROM domox.actioncdd UNION ALL
       SELECT id,'AssociationCdd' FROM domox.associationcdd ) t ON t.id=r.candidate_id
GROUP BY t.type, r.status ORDER BY 1,2;
```

---

## 7. Phase 3 — MCP write driver

Write `/tmp/decisions.tsv`: `id<TAB>APPROVE<TAB>` *or* `id<TAB>REJECT<TAB>RATIONALE`
(trailing tab after APPROVE). **Pre-flight validation before sending anything:**
```bash
# every candidate id has exactly one decision line, no malformed rows:
comm -23 <(cut -f2 /tmp/candidates_distinct.tsv | sort -n) <(cut -f1 /tmp/decisions.tsv | sort -n)
awk -F'\t' 'NF<2 {print "short:",NR} NF==5 {print "dup-merged:",NR}' /tmp/decisions.tsv
# CRITICAL: the file must END with a newline before you append any lines to it
tail -c1 /tmp/decisions.tsv | od -An -tx1   # must print 0a
```

Driver (`bash /tmp/review_driver.sh`):
```bash
#!/usr/bin/env bash
set -uo pipefail
MC=http://localhost:8080/mcp
H1='Content-Type: application/json'
H2='Accept: application/json, text/event-stream'
HP='MCP-Protocol-Version: 2024-11-05'
SID=$(curl -sS -D - -o /dev/null -X POST "$MC" -H "$H1" -H "$H2" -H "$HP" \
  --data-binary '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"review-driver","version":"1.0"}}}' \
  2>&1 | grep -i 'Mcp-Session-Id' | tr -d '\r' | awk '{print $2}')
[ -n "$SID" ] || { echo NO-SESSION; exit 1; }
echo "SESSION=$SID" >&2
: > /tmp/review_results.log
i=0
while IFS=$'\t' read -r id act rat; do
  [ -n "$id" ] || continue
  i=$((i+1))
  if [ "$act" = APPROVE ]; then
    payload="{\"jsonrpc\":\"2.0\",\"id\":$((i+100)),\"method\":\"tools/call\",\"params\":{\"name\":\"approveCandidate\",\"arguments\":{\"candidateId\":$id}}}"
  else
    payload="{\"jsonrpc\":\"2.0\",\"id\":$((i+100)),\"method\":\"tools/call\",\"params\":{\"name\":\"rejectCandidate\",\"arguments\":{\"candidateId\":$id,\"rationale\":\"$rat\"}}}"
  fi
  out=$(curl -sS -X POST "$MC" -H "$H1" -H "$H2" -H "$HP" -H "Mcp-Session-Id: $SID" --data-binary "$payload" 2>&1)
  if printf '%s' "$out" | grep -qE '"isError":true|"error"|event:error'; then
    echo "FAIL id=$id act=$act :: $(printf '%s' "$out" | tr '\n' ' ' | head -c 400)" >> /tmp/review_results.log
  else
    echo "ok id=$id act=$act" >> /tmp/review_results.log
  fi
done < /tmp/decisions.tsv
echo "DONE $i processed"
```

Notes:
- Calls are idempotent — safe to re-run (e.g. after fixing one line).
- A single MCP `initialize` gives one session; reuse `Mcp-Session-Id` for all calls.
- STREAMABLE responses are SSE; the JSON result is in `data:` lines; the parsed `text`
  content is itself JSON (`{"candidateId":..., "candidateName":..., "status":...,
  "reviewId":..., "reviewer":"agent"}`).

---

## 8. Phase 4 — do the real check, not the curl output

**Never trust the driver's `ok` lines alone.** In the first full run, 148/148 printed `ok`
but one candidate (id 6777) got zero reviews: its reject used a corrupted rationale
(`NOT_RELEVANT6686`) because an append had **swallowed the newline**, `parseRationale` threw
`IllegalArgumentException`, and that error did NOT match the driver's fail-pattern, so it
silently logged `ok`. **Always cross-check §6.2**: `count(*) == candidate count`,
`NOT IN (SELECT candidate_id FROM review)` returns 0 rows, and reconcile statuses.
Fix a straggler by calling the tool directly for just that id.

---

## 9. Quick pitfalls recap
- **Newline swallowing** when appending lines to a TSV that lacks a trailing newline → two records merge onto one line; validate with `tail -c1 | od`.
- **`/mcp` 404** = `protocol:` key missing/misnamed (§3).
- **Data in per-type tables**, not `candidate` / `public` legacy tables (§2.1).
- **driver `ok` ≠ success**; rely on DB verification (§8).
- **Maven/Java/cmds**: source SDKMAN first; use `docker exec domox-db psql -U postgres -d postgres -c "..."` for all DB work (see `commands.md`).
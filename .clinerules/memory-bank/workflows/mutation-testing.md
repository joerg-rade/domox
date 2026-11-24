# Mutation testing (pitest) — runbook

Task type: "is the domain actually tested?" / "close a mutation gap in class X". Read this before the
first `check-mutation.sh` of a session; the mechanics are one command, the *reading* is what needs a page.

> **This profile is the last thing you run in a session.** pitest takes the *test* classes as input, so
> `domox-domain/src` is frozen from launch until the floors report — an edit made while waiting is the one
> the staleness guard exists to catch (it stamps the run's start and step 2 refuses a report whose inputs
> changed during the run; exit 3, not a pass). `--allow-stale` overrides that when you know better (e.g. a
> build cache restored the report with a checkout-fresh mtime).

## Run it

```shell
bash scripts/check-mutation.sh                    # ~2 min: mvn -o -pl domox-domain verify -Pmutation, then the floors
bash scripts/run-bg.sh /tmp/mutation.log --wait 28 -- bash scripts/check-mutation.sh
bash scripts/check-mutation.sh --no-run           # check the report already on disk (no Maven)
```

It is deliberately **not** in `mvn verify` or `scripts/check.sh`. Exit codes: 0 ok, 1 below floor,
2 no report / no such class, 3 stale.

## Read the report rather than gate on it

| Command | What it gives |
|---|---|
| `python3 scripts/pitest-floors.py --table` | every class: killed/total/rate/no-coverage, the module total, and the `MEMORY_ERROR`/`TIMED_OUT` count |
| `python3 scripts/pitest-floors.py --detail <Class>` | each mutant no test killed, with line, method, mutator, description |

`<Class>` is a name fragment, so a package selector covers a whole package. Those two replace hand-parsing
`mutations.xml`. Save `--table` **first** when the point of a run is to close a gap — pitest overwrites the
report, so the "before" numbers are otherwise gone.

## Traps the script cannot tell you

- The JUnit-5 plugin must be a **plugin dependency**, or pitest runs no test at all.
- The minion heap needs `-Xmx3g`.
- The plugin's version and config live in the root pom's `<pluginManagement>`; the `mutation` profile in
  `domox-domain/pom.xml` only declares it.

## Reading a mutant

- **`NO_COVERAGE`** — no test *reaches* the code. On a defensive null-guard that usually means an
  *unreachable* branch: delete the guard rather than write a test for it.
- **`SURVIVED`** — a test reaches it but asserts nothing about it. Different fix: strengthen the assertion.

## How to read the module rate

pitest is scoped to `domox-domain`, so a class whose only test is a `domox-webapp` integration test reads
0 % however well tested it is — ~19 % of the mutants (751/4037) sit in classes no domain test reaches.
Read the module rate against that, and check the no-coverage column for any class you changed.

**Baseline** (~2026-11): ~3 700 mutants, ~54 % killed (~73 % of the ones a test can reach), test strength
~79 %. The per-class floors cover the collaborators extracted across the four SRP waves — the UML-layout
eight (`RouteScoring`, `LabelLayout`, `LabelMetrics`, `RouteGeometry`, `PortLayout`, `OrthogonalRouter`,
`DiagramIndex`, `DiagramJsonWriter`), the diagram/vocabulary extractions, the candidate-neighbourhood/legend
wave, and the wave-4 splits (`CandidateReviewWorkflow`, `RuleMatchSignature`, the `Dependency*Predicates`
trio, the `LexicalGraph*`/`DotLiterals` group and the `Rdf*` group).

## Related

- Generic Maven caveats (build-cache key trap) → `~/.clinerules/maven.md`.
- The full `check.sh` tiers (`--quick`, `--units`) → `.clinerules/commands.md`.

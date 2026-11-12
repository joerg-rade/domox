# Environment Setup Rules

Whenever executing terminal commands that require Java, Maven, or SDKMAN binaries:
1. Always source SDKMAN before running Maven or Java commands.
2. Prefer this shell-execution scaffold that works whether or not `.sdkmanrc` exists:
   ```shell
   source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env || true && mvn <command>
   ```
   The `|| true` prevents failure when the repo does not have a `.sdkmanrc` file.
   Note: there is NO `.sdkmanrc` in this repo, so `sdk env` always falls back to the SDKMAN
   default JDK — do not assume a pinned version.

# Repo Map (navigation)

Before searching the codebase, jump straight to the right steer/doc for common tasks.

**Rule-file partitioning:** `.clinerules/*.md` (top level) = always-active DoMoX steering;
`.clinerules/memory-bank/technical/` = DoMoX-specific tech references (loaded on demand);
`~/.clinerules/` = generic cross-project tech references (causeway, maven) — user-global
Cline rules directory, loaded for every project.

- **Causeway annotations & conventions** (e.g. `@DomainObject(editing = Editing.ENABLED)`) →
  `~/.clinerules/causeway.md` (user-global reference; full reference:
  `docs/causeway/causeway-annotations.md`; DoMoX metamodel guardrail: `CODING_STANDARDS.md` §1)
- **Module layout / build** → `.clinerules/memory-bank/technical/MAVEN.md` (DoMoX build facts;
  full reference: `docs/maven/maven-best-practices.md`); generic Maven caveats (build-cache key
  trap) → `~/.clinerules/maven.md`
- **Persistence / schema / JPA field migration** (TABLE_PER_CLASS trap, `create-tables.sql`,
  drift check) → `.clinerules/memory-bank/technical/SCHEMA.md`
- **Candidate-review pipeline over MCP** → `mcp-review-pipeline.md`
- **Domain-modeling (CRC) extraction rules** → `.clinerules/crc_domain_modeling_guide.md` (full guide: `docs/domain-modeling/crc-guide.md`)
- **Run the app & confirm boot** → use `bash scripts/run-app.sh [--spring.args…]`. It does the three
  things that are easy to get wrong, in order: (1) `install`s `domox-domain`/`domox-adapter` into
  `~/.m2` first, (2) starts `spring-boot:run` with the **REPO ROOT** as the working directory
  (EclipseLink resolves `create-ddl-jdbc-file-name` = `domox-webapp/create-tables.sql` relative to the
  CWD; the wrong CWD dies at startup with EclipseLink-7018 FileNotFound), (3) starts it **detached**
  (`setsid`, so it survives the shell that launched it) and tees to `/tmp/domox-boot.log`. It prints the
  PID and log path and returns immediately — then `tail -f /tmp/domox-boot.log` (expect
  `Validation PASSED` then `Started Application`). Liveness:
  `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/` (expect 200); MCP endpoint:
  `curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/mcp` (expect 200; 404 = the
  `spring.ai.mcp.server.protocol` key issue, see `mcp-review-pipeline.md`).
- **Stop the app** → `bash scripts/stop-app.sh [port…]` (default 8080). It resolves the PID by port.
  **Never `pkill -f <pattern>`**: the pattern also matches the shell running the `pkill` — its own
  command line contains it — so it SIGTERMs the caller and the output is lost (happened twice).

- **Long `mvn` / boot commands must run detached** → the shell tool caps one call at ~30 s, so
  `mvn test` (≈50 s) or `run-app.sh` (install + boot) die mid-run and their output is lost. Run them
  in the background into a log, then poll:
  `setsid mvn … > /tmp/x.log 2>&1 < /dev/null &` then `grep -E 'Tests run|BUILD' /tmp/x.log`.
  (`run-app.sh` already does this for the boot itself; this applies to everything else.)

- **Port / DB ownership caveat** → a dev instance may already own 8080: check
  `curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/` and
  `docker ps --format '{{.Names}} {{.Status}}'`. NEVER boot a second instance against the same
  Postgres: the shared `domox.SEQ_GEN` sequence makes parallel boots fail with
  `seq_gen already exists`.
- **A verification boot, in one call** → `bash scripts/verify-boot.sh` brings up a throwaway Postgres
  (`domox-db-verify`, port 5433) and boots the app on a free port (8082), **waiting until it actually
  serves** and printing the log path. It rebuilds `domox-domain`/`domox-adapter` only when their sources
  are newer than the installed jar, so the common case returns in ~13 s (~24 s cold) — inside the shell
  budget, and with none of the hand-run steps or poll loops this used to take. Re-running is harmless
  (it reports the instance already serving); `--stop` tears down the app **and** the DB.
- **Rebuilding upstream modules before a boot** → `mvn -pl domox-webapp spring-boot:run` resolves
  `domox-domain` / `domox-adapter` from the local repository (`~/.m2`), NOT from the reactor. After
  changing either, run `mvn -pl domox-domain,domox-adapter install -DskipTests` first — otherwise the
  boot dies with `NoClassDefFoundError` / `AbstractMethodError` for a class that compiles fine.
  `scripts/run-app.sh` does this for you. **Do not batch the `install` and the `run` in one shell call**
  — they execute concurrently and the app starts against a half-written jar, which then looks like
  "my change had no effect".
- **Version skew (Boot / Spring / micrometer)** → this pom overrides `spring-boot.version`, which the
  parent chain (`causeway-app-starter-parent` → `causeway-bom`) also pins. Drift mixes releases on the
  classpath and fails at **startup**, not at build time (e.g. `MetricsAutoConfiguration … NoSuchMethodError`).
  `bash scripts/check-dependency-alignment.sh` — part of `scripts/check.sh` — reports it; align the
  properties, then rebuild.
- **Inspecting the running app** → Wicket pages need a session and the sign-in form is an Ajax form
  that curl cannot drive. Use the RestfulObjects API with basic auth instead (users in `shiro.ini`,
  e.g. `sven:pass`) — or better, `scripts/ro.py`:
  `ro.py invoke <service> <action> [k=v…]` (e.g. `ro.py invoke domox.OntologyDiagrams searchOntologyAsD3Graph searchTerm=Pet`,
  then it dumps the returned object's members), `ro.py property <objectRef> <member>`,
  `ro.py layout <objectRef>` (top-level columns with their span — the fastest way to see whether a
  layout XML was actually applied), `ro.py members|object <objectRef>`. It reads `DOMOX_URL`
  (default `http://localhost:8080`) and `DOMOX_USER`/`DOMOX_PASS`.
  For rendered HTML/CSS questions, prefer `scripts/verify-ui-render.sh` over reasoning about CSS.
- **Causeway layouts / view models / `@D3Graph`** → `.clinerules/memory-bank/technical/CAUSEWAY-LAYOUT.md`
  (verified facts: layout-XML resolution and the silent fallback grid, the `unreferenced*` markers,
  `@D3Graph` being property-only, view-model mementos and `hideXxx()`, plus the jar-inspection recipes).
- **Overriding a config value for a verification boot** → a **non-empty** `--key=value` works; an
  **empty** one does NOT (`--domox.rdf.endpoint-url=` leaves the `application.yml` value in place —
  verified for CLI args; a `@SpringBootTest(properties = "…=")` inlined empty value DOES override, which is how the webapp integration tests pin local mode). Set blank values in the YAML.
  Related trap: `spring-boot:run` reads `target/classes/application.yml`, so after editing
  `src/main/resources/application.yml` **without rebuilding**, the app silently uses the OLD value —
  which can look like a config override working when it never did.
- **`git` after a package move** → the new path is UNTRACKED, so `git diff <new path>` prints nothing
  while the old path shows as deleted. Use `git status --short`, or `git add -N <path>` to diff it.
- **IDE (JetBrains) MCP tools** → always pass `projectPath=/home/jrade/projects/domox`, otherwise they
  fail with "Unable to determine the target project".
- **Coding standards (review)** → `CODING_STANDARDS.md` (repo root), esp. the
  no-`@Data`-on-entities rule and injected-service accessor suppression.
- **Reference / archive docs** (completed-milestone how-to's: `RULES_*`,
  `COMPLETION_CHECKLIST.md`, `SYNONYMS.md`, implementation summaries) live in
  `docs/scratch_pad/` — on-demand only; skim with `head` first, never a full read.

- **Mutation testing (pitest)** → **one command**: `bash scripts/check-mutation.sh` (opt-in, ~2 min —
  it runs `mvn -o -pl domox-domain verify -Pmutation`, then `python3 scripts/pitest-floors.py --check`
  for you, in that order).  Deliberately *not* in `mvn verify` or `check.sh`.  `--no-run` checks the
  report already on disk (no Maven); `--allow-stale` accepts one older than `domox-domain/src` (a build
  cache can restore it with a checkout-fresh mtime).  It exceeds the shell tool's ~30 s cap, so detach
  and poll: `setsid bash scripts/check-mutation.sh >/tmp/mutation.log 2>&1 </dev/null &`.
  **To read** the report rather than gate on it: `python3 scripts/pitest-floors.py --table` (every
  class — killed/total/rate/no-coverage, plus the module total and the `MEMORY_ERROR`/`TIMED_OUT`
  count) and `--detail <Class>` (each mutant no test killed, with line, method, mutator, description);
  those two replace hand-parsing `mutations.xml`.  Exit codes: 0 ok, 1 below floor, 2 no report / no
  such class, 3 stale.  Traps the script cannot tell you: the JUnit-5 plugin must be a *plugin*
  dependency (or pitest runs no test at all) and the minion heap needs `-Xmx3g`; the plugin's version
  and config live in the root pom's `<pluginManagement>`, the `mutation` profile in
  `domox-domain/pom.xml` only declares it.  A `NO_COVERAGE` mutant means no test *reaches* the code, a
  `SURVIVED` one means a test reaches it but asserts nothing — different fixes.  **Run the mutation
  profile last**: pitest takes the *test* classes as input, so a later test edit leaves a
  healthy-looking report that describes a suite which is gone.  Baseline: ~3 700 mutants, ~54 % killed,
  test strength ~79 %; the per-class floors cover the collaborators extracted across the four SRP
  waves — the UML-layout eight (`RouteScoring`, `LabelLayout`, `LabelMetrics`, `RouteGeometry`,
  `PortLayout`, `OrthogonalRouter`, `DiagramIndex`, `DiagramJsonWriter`), the diagram/vocabulary
  extractions, the candidate-neighbourhood/legend wave, and the wave-4 splits
  (`CandidateReviewWorkflow`, `RuleMatchSignature`, the `Dependency*Predicates` trio, the
  `LexicalGraph*`/`DotLiterals` group and the `Rdf*` group).


Quick checks after an entity change: run `bash scripts/check.sh` — bundles schema drift,
domain unit tests, and the metamodel guardrail (the same checks CI runs in
`.github/workflows/ci.yml`). For a faster check use `bash scripts/check.sh --quick`
(the four instant, Maven-free steps: drift, layout XML, .mjs/.py syntax, dependency alignment).

# Shell Execution Formatting Rules

When preparing terminal execution requests:
1. NEVER chain multiple long commands on a single line using `&&`.
2. Format chained commands using multiline line-continuations (`\`) with each sub-command on its own indented line.
3. Keep shell commands concise and visually structured for easy review.

Example preference (use line continuations for long command chains):
source "$HOME/.sdkman/bin/sdkman-init.sh" \
  && sdk env || true \
  && mvn clean install

# Terminal Command Guidelines

## Allowed Commands
You are pre-approved to run the following commands automatically when needed:

### Exact commands (first-token match):
- 'source'
- 'cd'
- 'mvn'
- 'java'
- 'docker'
- 'sed'
- 'sdk'
- 'which', 'find', 'ls', 'pwd', 'cat', 'echo', 'grep', 'tail', 'head', 'less', 'more', 'cp', 'mv', 'mkdir', 'rmdir'

### Chained patterns:
Any command whose first whitespace-delimited token is one of the exact commands above is pre-approved, whether standalone or chained with `&&`, `||`, `;`, `|`, and/or `2>&1`. Piping through `grep`, `sed`, `tail`, `head` for output filtering or line-range extraction is included. Additional arguments and flags may follow.

### Examples of pre-approved commands (all variants):
- source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn compile -pl domox-domain -q 2>&1
- source "$HOME/.sdkman/bin/sdkman-init.sh" && cd /home/jrade/projects/domox && mvn compile -pl domox-domain 2>&1 | tail -15
- source "$HOME/.sdkman/bin/sdkman-init.sh" && cd /home/jrade/projects/domox && mvn test -pl domox-domain 2>&1 | tail -60
- grep -n "ResponsibilityCdd" /home/jrade/projects/domox/domox-domain/src/main/java/domox/dom/crc/model/ResponsibilityCdd.java
- cat -n /home/jrade/projects/domox/domox-domain/src/test/java/domox/dom/rules/RuleMatchesTest.java | sed -n '83,100p'
- cat -n /home/jrade/projects/domox/domox-domain/src/main/java/domox/dom/rules/RuleMatches.java | sed -n '100,218p'
- docker exec domox-db psql -U postgres -d postgres -c "SELECT ..."
- docker ps --format '{{.Names}} {{.Image}} {{.Status}}' 2>/dev/null | head -5
- sdk current java 2>&1 | head -5

## Strictly Forbidden / Require Manual Confirmation
DO NOT run these commands without explicit approval or if there is any doubt:
- Any `rm` or `git reset --hard` command.
- 'git push' or any commands that modify remote repositories.
- 'git commit'
- Any dependency modification commands like `npm install`, `mvn dependency:...`
- Any command that modifies system configurations outside the workspace.
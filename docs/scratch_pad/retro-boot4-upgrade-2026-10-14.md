# 🔍 DoMoX Boot 4 Upgrade — Retrospective (2026-10-14)

## Session summary

The goal was switching the project from Spring Boot 3.5.16 to **4.2.0-M1** to match Causeway 4.0.0-SNAPSHOT's compiled target. The session hit three failures before reaching a clean start:

| Iteration | Failure | Root cause | Time to fix |
|---|---|---|---|
| `mvn spring-boot:run` | Could not download `spring-boot-buildpack-platform-4.2.0-M1.jar` | Milestone JAR not in local repo; Maven plugin path never used by direct boot | ~10 min manual download |
| `java -cp` attempt 1 | `Conflicting persistence unit definitions` | `domox-domain.jar` AND `domox-domain/target/classes` both on classpath | ~5 min |
| `java -cp` attempt 2 | `Could not resolve placeholder 'causeway.viewer.restfulobjects.base-path'` | `@ConfigurationProperties` default `/restful` not exposed to `Environment`; `@Value` needs explicit property in `application.yml` | ~30 min jar-diving |
| `mvn test` (post-upgrade) | `NoSuchMethod: ExtensionContext$Store.computeIfAbsent(Object, Function, Class)` | Root POM pinned `junit-jupiter.version=5.11.0` while Boot 4.2.0-M1 BOM manages 6.1.3 → mixed-version `NoSuchMethodError` | ~15 min |

---

## Candidates for improvement (in descending priority)

### 🚨 1. No guardrail catches major-version JUnit mismatch

The Maven build cache extension silently masked the pinned `junit-jupiter.version=5.11.0` because the cached test results looked fine until someone ran `mvn test -Dmaven.build.cache.enabled=false`.

**Finding:** No enforcer rule flags version skew between a property pinned in the root POM and the version managed by the Spring Boot BOM. The `check.sh` script runs unit tests only after the drift check — but neither step catches `enforcer:enforce` failures.

**Recommendation:** Add an enforcer rule to explicitly ban sub-6.0 JUnit artifacts:

```xml
<!-- pom.xml, inside <executions> of maven-enforcer-plugin -->
<rule>
  <bannedDependencies>
    <excludes>
      <!-- Must use Boot-managed JUnit 6+, not the old 5.x property -->
      <exclude>org.junit.jupiter:junit-jupiter-api:[5.0.0,6.0.0)</exclude>
      <exclude>org.junit.jupiter:junit-jupiter-engine:[5.0.0,6.0.0)</exclude>
    </excludes>
  </bannedDependencies>
</rule>
```

And wire `scripts/check.sh` to call `mvn enforcer:enforce` before the unit test step (currently it only calls `mvn test`).

---

### ⚠️ 2. `scripts/check.sh` does not run `enforcer:enforce`

The script has three steps: drift check → unit tests → metamodel guardrail. It never runs `mvn enforcer:enforce` independently. The version skew slipped through despite a local `mvn compile -o` succeeding.

**Recommendation:** Add step 0.5 to `scripts/check.sh`:

```bash
step "0.5  Enforcer rules (version consistency)"
if mvn -B enforcer:enforce -q 2>&1; then
  ok "enforcer rules pass"
else
  bad "enforcer violation"
fi
```

---

### ⚠️ 3. `@ConfigurationProperties` defaults are invisible to `@Value` — no test catches a missing config property

The `causeway.viewer.restfulobjects.base-path` default of `/restful` lives inside `CausewayConfiguration.Viewer.Restfulobjects`, which `@ConfigurationProperties` binds and sets as a field default — but `Environment` never sees it. A `@Value("${causeway.viewer.restfulobjects.base-path}")` annotation fails at bean creation time with `PlaceholderResolutionException`. This is a well-known Spring Boot gotcha.

**Recommendation:** Add a guardrail test that reflects every `@Value("${causeway.*}")` placeholder found via a regex scan and validates they match known properties, or add a startup smoke test (`@SpringBootTest` loading all `@Value`-dependencies) that would catch a missing config property at CI time.

Currently there is **no integration test that bootstraps the full Spring context** in CI — the `integration-test` CI job exists in `.github/workflows/ci.yml` but it bootstraps a fresh Postgres via Testcontainers and doesn't always run on PR. A cheap `@SpringBootTest` that checks every config property resolves would catch placeholder errors before a full restart.

---

### 💡 4. The `boot-direct*.log` logs are lost after the session

Each boot iteration was teed to `/tmp/boot-direct*.log`, but those files are overwritten per-session and not archived. When the user asks "what happened in the upgrade session" the logs are already stale.

**Recommendation:** The steering doc already tees logs to `/tmp/domox-boot.log` with a `nohup mvn spring-boot:run ... > /tmp/domox-boot.log 2>&1 &` recipe. When the agent switches to `java -cp`, it should use a timestamped log path instead of a rotating one, or the `commands.md` recipe should standardize on a single log path (`/tmp/domox-boot.log`) that the tail command in the steering doc always reads from.

---

### 💡 5. Root POM has metadata-fetch timeout noise in every build

Every `mvn` command (even `-o` offline) emits warnings like:

```
Could not transfer metadata org.apache.causeway.app:causeway-app-starter-parent:4.0.0-SNAPSHOT/maven-metadata.xml
from/to Apache Nexus snapshots: Connect to repository.apache.org:443 failed: Connect timed out
```

Over 35 warnings per build run (visible in `domox-build2.log` lines 2–155). This adds visual noise when diagnosing build failures.

**Recommendation:** Add the `-U` flag to snapshot repos only, or add a `-Dmaven.artifact.handler.dependencyResolutionDisabled=true` — or suppress Nexus snapshot metadata checks in `pom.xml` with `<snapshots><enabled>false</enabled></snapshots>` for the `Apache Nexus snapshots` repository. The repo is clearly offline/unreachable from this network.

---

## Summary table

| # | Category | Severity | Finding | Fix |
|---|---|---|---|---|
| **1** | Automated checks | 🔴 High | No enforcer rule catches JUnit major version mismatch | `bannedDependencies` rule in enforcer plugin |
| **2** | Automated checks | 🟠 Medium | `scripts/check.sh` never runs `enforcer:enforce` | Add step 0.5 |
| **3** | Automated checks | 🟠 Medium | Missing config property (`@Value` placeholder) only caught at boot time | Add config-property smoke test |
| **4** | Information access | 💡 Low | Boot logs overwritten per session, lost after session ends | Standardize to timestamped log path |
| **5** | Tool economy | 💡 Low | 35+ Nexus metadata timeout warnings per build command | Disable metadata checks on the unreachable snapshot repo |

---

## Candidates implemented (2026-11-20)

The three automated-check candidates were closed in one sweep, tracked as TODO-19…22 in `TODO.md`.
Each one is a *guardrail*, so each was proved by a **negative test** — the failure it exists for was
reproduced and observed to fail — not by inspection.

| # | Candidate | Where it landed | Negative test that proves it |
|---|---|---|---|
| **1** | Ban sub-6.0 JUnit | `pom.xml` — `bannedDependencies` inside the `enforce-constraints` execution (pluginManagement) | `mvn -o -B enforcer:enforce@enforce-constraints -Djunit-jupiter.version=5.11.0` → **BUILD FAILURE**, `junit-jupiter-api:jar:5.11.0 <--- banned via the exclude/include list`. With the 6.1.3 pin: `Rule 3 … BannedDependencies passed`. |
| **2** | `check.sh` runs the enforcer | `scripts/check.sh` **step 6/10** (the bundle now has ten steps) | The same re-pin is reported as step 6 `[FAIL]`, before the unit tests, with the failing rule name echoed from the log. |
| **3** | Config-property placeholder smoke test | `domox-webapp/src/test/java/domox/webapp/unittests/ConfigPlaceholderSmokeTest.java` | Renaming `causeway.viewer.restfulobjects.base-path` to `base-path-typo` in `application.yml` fails the test **naming that key**; restored afterwards (`git diff` clean). |
| **4** | Boot logs lost per session | Not a code change — addressed by the persistent `logs/domox-webapp.log` (appended across restarts, rolling) plus the fixed `/tmp/domox-boot.log` recipe in `.clinerules/commands.md`. | `grep -c 'Starting Application' logs/domox-webapp.log` counts restarts after the fact. |
| **5** | Nexus metadata timeout noise | **Still open** — TODO-24. | — |

**Two corrections to the findings above, for the next reader:**

- Candidate 3's premise — *"there is no integration test that bootstraps the full Spring context in
  CI"* — is no longer true: the `integration-test` job runs `Smoke_IntegTest` /
  `ValidateDomainModelIntegTest`, which boot the whole context and **would** fail on an unresolvable
  `@Value` placeholder. The new unit test does not duplicate that; it makes the same failure cheap,
  local, and reported as itself (it names the key) instead of as a bean-creation stack trace in the
  second CI job. It also covers the placeholder set **derived from the classpath**, so a Causeway
  upgrade that starts requiring a new key is caught without editing the test.
- Candidate 2's premise is partly wrong too: the enforcer *already ran* on every `mvn test`, because
  the execution has no explicit `<phase>` and `enforcer:enforce` defaults to `validate`. What was
  missing was not the invocation but (a) a rule that had anything to say about the JUnit skew
  (candidate 1) and (b) a *labelled* step, so a reader can see the guardrail run. Note the trap the
  step's comment records: a bare `mvn enforcer:enforce` on the command line ignores the
  `<execution>`'s `<configuration>` and runs **no rules at all** — hence `@enforce-constraints`.

---
name: domox-maven
description: "DoMoX Maven multi-module build — module layout and repo-specific gotchas. Generic Maven caveats (build-cache key trap, flags) live in the user-global rules (~/.clinerules/maven.md)."
disable-model-invocation: true
---

# DoMoX Maven Build — Project-Specific Reference

> **Generic Maven knowledge** (build-cache key trap, `-pl`/`-am`/`-B` flags) lives in the
> user-global rules at `~/.clinerules/maven.md`. This file only holds DoMoX-specific facts.

## Module layout

| Module | Role | Depends on |
|---|---|---|
| **domox** (parent) | Root POM, `packaging: pom` | — |
| **domox-domain** | Entity/JPA layer, port interfaces | — |
| **domox-adapter** | Port implementations (NLP, diagram, rule engine) | domox-domain |
| **domox-webapp** | Spring Boot web app | domox-domain, domox-adapter |

## Repo-specific gotchas

- **Working directory:** EclipseLink resolves the DDL output path (`application-location: "."`)
  relative to CWD. The JVM MUST start at the **repo root**, not the module dir. Surefire is
  pinned to the repo root in `domox-webapp/pom.xml`; CLI boots need
  `-Dspring-boot.run.workingDirectory=<repo root>`.

- **Targeted build:** `mvn -pl domox-adapter -am install` builds only the adapter plus upstream
  dependencies (`domox-domain`).

- **Full test suite:** `mvn -B test -pl domox-domain -am` runs domain + adapter unit tests.

- **Guardrail runs:** always pass `-Dmaven.build.cache.enabled=false` (see
  `~/.clinerules/maven.md` for why); `scripts/check.sh` already does this.

- **Enforcer rules run at `validate`:** the root pom's `enforce-constraints` execution
  (`requireMavenVersion`, `requireJavaVersion`, `dependencyConvergence`, and a `bannedDependencies`
  rule banning `junit-jupiter-api`/`-engine` `[5.0.0,6.0.0)` so a re-pinned 5.x JUnit fails the
  *build* instead of the test run) has no explicit `<phase>`, and `enforcer:enforce` defaults to
  `validate` — so it already runs on every lifecycle invocation, in all four reactor projects.
  To run it **alone**, select it by execution id:
  `mvn -o -B enforcer:enforce@enforce-constraints`. A bare `mvn enforcer:enforce` ignores the
  execution's `<configuration>` and runs **no rules at all** — a guardrail reporting "passed" while
  checking nothing. `scripts/check.sh` step 6/10 uses the `@enforce-constraints` form.

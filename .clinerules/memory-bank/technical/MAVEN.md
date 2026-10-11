---
name: maven
description: "DoMoX Maven multi-module build — module layout, build cache caveats, and common build commands."
disable-model-invocation: true
---

# DoMoX Maven Build — Quick Reference

**Full reference:** `docs/maven/maven-best-practices.md`

## Module layout

| Module | Role | Depends on |
|---|---|---|
| **domox** (parent) | Root POM, `packaging: pom` | — |
| **domox-domain** | Entity/JPA layer | — |
| **domox-service** | Business logic | domox-domain |
| **domox-webapp** | Spring Boot web app | domox-domain, domox-service |

## Repo-specific gotchas

- **Build cache caveat:** The `-Dtest` parameter is NOT part of the Maven build-cache key.
  Running `mvn test -Dtest=BarTest` after a cached `FooTest` run restores the stale cache.
  **Always pass `-Dmaven.build.cache.enabled=false` when running guardrail tests locally.**
  CI runs on a fresh checkout and is unaffected.

- **Working directory:** EclipseLink resolves the DDL output path (`application-location: "."`)
  relative to CWD. The JVM MUST start at the **repo root**, not the module dir. Surefire is
  pinned to the repo root in `domox-webapp/pom.xml`; CLI boots need
  `-Dspring-boot.run.workingDirectory=<repo root>`.

- **Targeted build:** `mvn -pl domox-service -am install` builds only `domox-service` plus
  upstream dependencies.

- **Full test suite:** `mvn -B test -pl domox-domain -am` runs domain + adapter unit tests.

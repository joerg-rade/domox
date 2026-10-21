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

### D3 domain-model diagrams (force-layout graph)

**Design doc** → `docs/scratch_pad/D3_DIAGRAM.md`

| Layer | File | Role |
|---|---|---|
| JSON data (Java) | `domox-domain/.../crc/DomainModelDiagramsD3.java` | Assembles `{nodes,links,legend}` from candidates + reviews |
| Client renderer | `domox-webapp/src/main/resources/org/apache/causeway/extensions/d3/wkt/integration/res/d3-integration.mjs` | SVG creation, force simulation, node/link rendering, resize |
| Container CSS | `domox-webapp/.../D3Panel.css` | `width:100%; min-height:400px` |
| Wicket panel | `domox-webapp/.../D3Panel.java` + `.html` | Wraps config into `D3Config` |

**Quick facts:**
- The JS file is a static resource served from `target/classes`; after editing it you must copy it there or run `mvn resources:resources -pl domox-webapp -q`.
- Node JSON includes `isCandidateColumn` (boolean) — the JS uses this to conditionally draw an underline `<line>` under the bold name.
- The repo has **no** `CREATE TABLE` or schema migration for D3 diagrams — it's all client-side SVG.
### D3 domain-model diagrams (force-layout graph)

**Design doc** → `docs/scratch_pad/D3_DIAGRAM.md`

| Layer | File | Role |
|---|---|---|---|
| JSON data (Java) — model-wide | `domox-domain/.../crc/diagram/DomainModelDiagramsD3.java` | Assembles `{nodes,links,legend}` from all approved candidates |
| JSON data (Java) — per candidate | `domox-domain/.../crc/diagram/CandidateD3Graph.java` | Assembles `{nodes,links,legend}` for a single candidate's neighbourhood |
| Client renderer | `domox-webapp/src/main/resources/org/apache/causeway/extensions/d3/wkt/integration/res/d3-integration.mjs` | SVG creation, force simulation, node/link rendering, resize |
| Container CSS | `domox-webapp/.../D3Panel.css` | `width:100%; min-height:400px` |
| Wicket panel | `domox-webapp/.../D3Panel.java` + `.html` | Wraps config into `D3Config` |

**Quick facts:**
- The JS file is a static resource served from `target/classes`; after editing it you must copy it there or run `mvn resources:resources -pl domox-webapp -q`. The same applies to `static/css/*.css` — and the **browser** caches a static resource by URL (no version key), so a CSS/JS edit can look like a no-op; `spring.web.resources.cache.cachecontrol.no-cache` is configured, and a hard reload is the fallback.
- Node JSON includes `isCandidateColumn` (boolean) — the JS uses this to conditionally draw an underline `<line>` under the bold name.
- The repo has **no** `CREATE TABLE` or schema migration for D3 diagrams — it's all client-side SVG.
- **`@PropertyLayout(cssClass = …)` is a NO-OP on a `@D3Graph` property.** The extension's `D3Panel` replaces Causeway's `AttributePanel` for the `ATTRIBUTE_NAME_AND_VALUE` component type, and `AttributePanel` is the only place that applies the CSS-class facet. Hook the cell Causeway generates instead: `causeway-<objectType>-<memberId>` (e.g. `causeway-domox-ClassCdd-d3Graph`), with a `.table …` / `td:has(.d3GraphPanel)` fallback that does not depend on that naming.
- **`Candidate.getObjectType()`** (not `getClass().getSimpleName()`) yields the Causeway object type — the entities were renamed to CRC vocabulary while `@Named`/`@Table`/`candidateType` were pinned, so the class name and the object type differ.
- **In a list/table the graph cell is `clipped`, so the SVG's box must be sized to the cell** (`static/css/domox.css` rules 1–5). `d3-integration.mjs` measures `.d3GraphCanvas` at DOM-ready and bakes the result into the SVG `viewBox` *and* the force layout, so the canvas is kept 700px wide for a correct layout while the SVG's own `width` is pinned to the visible cell — pin only the height and the graph is laid out around the canvas' centre and clipped away entirely (an apparently empty column).
- The hover popup assumes **(a)** the graph column is the **last** column (`right: 0`) and **(b)** `:has()` support (two rules). Both are load-bearing; a column reorder or a `:has()`-less browser degrades the popup.
- **`bash scripts/verify-ui-render.sh`** renders the real cell markup in headless Chromium and asserts the user-visible behaviour (content actually on screen, popup only over the graph column, row height stable). It is part of `scripts/check.sh`; run it after any change to `domox.css` or `d3-integration.mjs`.
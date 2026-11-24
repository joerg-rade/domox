# Causeway layout XML & view models — verified facts

Loaded on demand. Everything here was **verified against Causeway 4.0.0-SNAPSHOT in this repo**,
mostly by reading the sources jars (recipes at the bottom) — not copied from docs. Consult this before
touching a `*.layout.xml`, a `@D3Graph` member, or a view model; re-deriving it costs a dozen tool
calls.

## 1. How a `*.layout.xml` is found — and silently ignored

- The file sits **next to the domain class** and is resolved from the classpath as
  `<SimpleName>.layout.xml` (also `<SimpleName>-<layoutPrefix>.layout.xml` and
  `<SimpleName>.layout.fallback.xml`).
  `domox-domain/pom.xml` declares `src/main/java` as a resource root (excluding `**/*.java`), so the
  XML is copied to `target/classes`. **`mvn clean` wipes it** — the next build re-copies it.
- **A grid that breaks a structural rule is DISCARDED, not reported.** Causeway logs one line at
  metamodel init and then uses its bundled fallback grid:

  ```
  ERROR … GridServiceDefault : Grid metadata errors in domox.dom.requirements.OntologyDiagram:
  <?xml …> … <bs:metadataError>No column and also no tabgroup found with the
  'unreferencedCollections' attribute set</bs:metadataError></bs:grid>
  ```

  After any layout change, grep the boot log for `Grid metadata errors` — a green build and a booting
  app prove nothing.
- **The bundled fallback is narrow**: `FallbackGridProvider` / `GridFallbackLayout.xml` put
  **properties in a `<bs:col span="4">`** (tabs Identity / Other / Metadata) and collections in
  `span="8"`. A diagram that "should" be full width renders in a third of the page, which is how a
  span-4 D3 panel was mistaken for a rendering bug.

## 2. Structural rules of a layout XML

| Rule | Detail |
|---|---|
| `unreferencedCollections="true"` **required** | on some `bs:col` **or** `bs:tabGroup` — even when the type has no collections. Omit it and the whole grid is discarded. |
| Properties live in a `cpt:fieldSet` | `BSCol` exposes `fieldSets` / `collections` / `actions` / `rows` / `tabGroups` / `domainObject` — **no `properties`**. A `<cpt:property>` directly in a `bs:col` is invalid. |
| `cpt:fieldSet` holds properties + actions only | **no collections** (they go directly in a `bs:col`). |
| Marker placement | `unreferencedProperties` on a **`cpt:fieldSet`**; `unreferencedCollections` / `unreferencedActions` on a **`bs:col`** (or `bs:tabGroup`). |
| Guards | `scripts/check-layout-xml.sh` (in `check.sh` step 2, `check.sh --quick`, and CI). |

**Comment trap:** a layout's own explanatory comment tends to *name* the markers
("Causeway requires … `unreferencedCollections="true"`"), so a naive `grep` on the raw file passes a
layout that would in fact be discarded. The check strips comments first — found by negative-testing it,
which is why the check must be negative-tested after any edit.


## 3. `@D3Graph` — a property/type facet, never an action

- `@D3Graph` targets `METHOD`, `FIELD` **and `TYPE`**, but the Wicket extension only honours it on a
  **property** (or on the domain object's type). `D3PanelComponentFactory` registers for
  `UiComponentType.ATTRIBUTE_NAME_AND_VALUE` and keys on the **property's** `D3GraphFacet`.
- **On an action it is a silent no-op** — the action returns its `String` and the viewer renders text.
  The fix is to return a **view model whose property** carries the facet (see `OntologyDiagram`).
- Enforced by `ArchitectureTests.D3Graph_is_only_placed_on_property_getters` (`domox-webapp`): a
  `@D3Graph` method must be named `getX()`/`isX()`.
- Object-level `@D3Graph` resolves its data through `D3GraphAdvisor` (a `ServiceLoader` SPI keyed by
  **class**, so it cannot see per-instance state).
- Panel sizing: the extension's `D3Panel.css` sets `width:100%` + `min-height:400px` on
  `.d3GraphPanel`/`.d3GraphCanvas`; the SVG is `width:100%` of its container. Table-cell specifics
  (teaser, hover popup) are in `.clinerules/d3-diagram.md`.

## 4. View models (Causeway 4)

- `@DomainObject(nature = Nature.VIEW_MODEL)`. There is **no `@ViewModel`** annotation and **no
  `NatureOfService`** in 4.0.0-SNAPSHOT.
- The **memento is built from the metamodel properties** (getter + setter). A property that is
  `@Programmatic`, or has no getter, does not round-trip; `hidden = Where.EVERYWHERE` **still does**
  (hidden ≠ absent) — which is how `OntologyDiagram` keeps a bookmarkable URL while hiding a redundant
  "Search Term".
- `hideXxx()` is a **no-arg method returning `boolean`** (`HideForContextFacetViaMethod` casts the
  result to `Boolean`) and works for **properties** as well as actions:
  `@MemberSupport public boolean hideMessage() { … }`.
- `Where` constants: `EVERYWHERE, ANYWHERE, OBJECT_FORMS, REFERENCES_PARENT, PARENTED_TABLES,
  STANDALONE_TABLES, ALL_TABLES, ALL_EXCEPT_STANDALONE_TABLES, NOWHERE, NOT_SPECIFIED`.
  `ALL_TABLES` = hidden in tables, still shown on the object page.

## 5. Asserting on the metamodel from an integration test

```java
ManagedObject mo = objectManager.adapt(new OntologyDiagram());          // adapt -> @Inject populated
ObjectMember diagram = mo.objSpec().lookupMember("diagram").orElseThrow();
assertThat(diagram.lookupFacet(D3GraphFacet.class)).isPresent();
assertThat(searchTerm.isVisible(mo, InteractionInitiatedBy.USER, Where.EVERYWHERE).isAllowed()).isFalse();

// proves the view model is renderable as a page (round-trips through its memento)
Bookmark bm = objectManager.bookmark(mo);
OntologyDiagram restored = (OntologyDiagram) objectManager.debookmark(bm).getPojo();
```

`D3GraphFacet` lives in `org.apache.causeway.extensions.d3.metamodel.facet` (jar
`causeway-extensions-d3-metamodel`). `ObjectManager` / `ManagedObject` / `ObjectSpecification` are in
`causeway-core-metamodel`.

## 6. Inspecting a Causeway/Jena jar (how the facts above were obtained)

```bash
# 1. find the jar — exclude the sibling -tests.jar, which also matches "find"
J=$(find ~/.m2/repository/org/apache/causeway -name 'causeway-core-metamodel-*.jar' \
      | grep -vE 'sources|tests' | head -1)

# 2. API surface (javap needs SDKMAN sourced — it is not on PATH by default)
source "$HOME/.sdkman/bin/sdkman-init.sh"
javap -cp "$J" org.apache.causeway.core.metamodel.services.grid.LayoutResourceLookup

# 3. readable code + bundled resources from the -sources.jar
S=${J%.jar}-sources.jar
unzip -l "$S" | grep -E 'grid|layout'
unzip -p "$S" 'org/apache/causeway/core/metamodel/services/grid/FallbackGridProvider.java'
unzip -p "$J" 'org/apache/causeway/core/metamodel/services/grid/GridFallbackLayout.xml'
```

Worth re-reading before a layout change: `LayoutResourceLookup` (resource-name candidates),
`FallbackGridProvider` + `GridFallbackLayout.xml` (the span-4 default), `BSCol`, `FieldSet`,
`XmlLayoutRespectingFacetInstaller`.

## 7. Related checks

- `scripts/check-layout-xml.sh` — structural rules (§2), negative-tested.
- `ArchitectureTests` (`domox-webapp`) — `@D3Graph` placement, entity/DomainObject conventions.
  Requires **`archunit-junit5-engine`**: with only `archunit-junit5-api` on the classpath surefire
  reports `Tests run: 0` and every rule is silently inert.

## 8. Composition guideline — keep content above the horizon

**Full guideline: `docs/causeway/causeway-ui-layout-guidelines.md`.** The rules that decide the
*shape* of a page, as opposed to the mechanics above:

- **Nothing important below the horizon.** A panel the reader must scroll to discover is a panel
  they do not know exists. The UML class-diagram page stacked PDF + D3 + PlantUML source as three
  full-width rows and the reader reported the page as having one diagram.
- **Several independent panels → `bs:tabGroup`, one `bs:tab` each** — not stacked `bs:col` rows.
  A plain stack stays correct only while the whole page fits on one screen. The shape to copy is in
  `Candidate.layout.xml` (and now `UmlClassDiagram.layout.xml`): `bs:col` → `bs:tabGroup` →
  `bs:tab name="…"` → `bs:row` → `bs:col` → member (`bs:tab` **requires** a `bs:row`, and `bs:row`
  requires a `bs:col`).
- **Tab order is reading order**; the first tab is the default, so the primary artifact goes first.
- **A diagram panel needs the full width** (`span="12"`): the panel fills its container, and the
  container is the only width driver.
- **Tab panels load on demand.** `AjaxBootstrapTabbedPanel` puts **only the selected tab's panel in
  the DOM**; the others are Ajax links (verified live 2026-11: never two panels at once). So a panel
  that measures its container at init never sees a hidden one — but check what it does with a
  zero-width container anyway, since another container can produce one. The D3 `"layout":"uml"`
  payload and the pdf.js viewer both ignore it (payload-driven `viewBox`; `initialScale` defaults to
  the *fixed* `Scale._1_00`, so the container-width auto-scale branch is never taken). A
  **force-layout** D3 panel does not: it lays out in `initialWidth || 800`.

## 9. Wicket in this repo — test bootstrap, taking over a panel, tab names

Loaded on demand. Three things that each cost a test-fix loop to learn; the first now lives in code
(`domox.webapp.unittests.WicketTestSupport`), so a new Wicket unit test starts from a passing render
instead of three failures.

### 9.1 A bare `WicketTester` is not enough for a D3/bootstrap component

`new WicketTester(new WebApplication(){…})` renders a plain panel fine, but a **D3 panel** or an
**`AjaxBootstrapTabbedPanel`** needs three things the real application does at init — and each one fails
differently:

| Missing | Symptom |
|---|---|
| `WicketWebjars.install(app)` | the D3 panel's header references are webjar references; the resource URL cannot be resolved |
| `Bootstrap.install(app)` | `AjaxBootstrapTabbedPanel` has no bootstrap theme or behaviour |
| the package-resource guard's `+*.mjs` / `+*.css` | Wicket's default `SecurePackageResourceGuard` blocks them, so the integration script's URL cannot be generated |

**Extend `domox.webapp.unittests.WicketTestSupport`** — or call
`WicketTestSupport.installD3Prerequisites(app)` on your own application — instead of re-deriving this.
`WicketTestSupport.newTester()` returns a `WicketTester` whose app already does all three, and
`UmlDiagramTabsPanelTest` shows the panel-in-a-synthetic-page idiom
(`tester.startComponentInPage(new XPanel("id", source))`; no markup file of its own is needed).

A test that renders neither a D3 panel nor a bootstrap tab strip does **not** need it:
`StaleComponentRecoveryEndToEndTest` keeps its own plain `WebApplication`.

### 9.2 Taking over a Causeway panel: `ObjectComponentFactoryAbstract`

`ObjectComponentFactoryAbstract` + `appliesExclusivelyIf(…)` is how a *view model* gets a hand-built Wicket
component instead of the generated table — the same mechanism the D3 extension uses. Three traps:

- **A `ComponentFactory` that returns a Wicket `Component` cannot also import
  `org.springframework.stereotype.Component`** (name clash). Write the annotation fully qualified
  (`@org.springframework.stereotype.Component`) — that is why the extension does.
- **Do not hold the view-model pojo in the panel.** A Wicket page is serialized into the session, and the
  pojo holds the model and its entities. Take a small `Serializable` source that reads the object model's
  current pojo on demand (`UmlDiagramTabsPanel.Source` is the pattern).
- Pin the applicability rule as a **pure function of the object's class** — that is what makes
  `UmlDiagramTabsPanelComponentFactoryTest` a guardrail rather than a smoke test.

### 9.3 A tab's label comes from the layout XML — unless the tabs are built in code

A `bs:tab name="…"` label is fixed in the layout XML, and one layout serves every instance of the view
model (`BSTab` exposes `getName`/`setName` only, and `TabGroupPanel.tabsFor` labels each tab from it —
verified against `causeway-applib`). So a tab label **cannot** carry a per-instance value. Two ways out,
both used in this repo:

- a **member-support method** on the view model (`UmlClassDiagram#namedD3Graph()`,
  `MemberNamedFacetViaMethod`) — re-read per instance, which is how the sub-domain name reaches the tab;
- **build the tabs in code** (`UmlDiagramTabsPanel extends AjaxBootstrapTabbedPanel`, one `ITab` per name,
  each payload assembled lazily inside `ITab#getPanel` — the shown tab only).

Only the selected tab's panel is in the DOM, so a panel that measures its container at init never sees a
hidden one (see §8). What the payload then means is in `.clinerules/d3-diagram.md`.


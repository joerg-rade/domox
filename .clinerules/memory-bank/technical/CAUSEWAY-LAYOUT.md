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

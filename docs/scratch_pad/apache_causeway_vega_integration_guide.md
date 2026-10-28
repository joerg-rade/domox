# Using Vega in Apache Causeway

Apache Causeway provides first-class support for **Vega** and **Vega-Lite** data visualizations through the `causeway-valuetypes-vega` ecosystem. This module enables developers to treat Vega chart specifications as domain-level value types, storing and managing chart models alongside domain entities and view models.

---

## 1. Prerequisites and Setup

To use Vega in your Apache Causeway application, add the appropriate value type dependencies to your project's `pom.xml`.

### Maven Dependencies

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.apache.causeway.valuetypes</groupId>
            <artifactId>causeway-valuetypes-vega</artifactId>
            <version>${causeway.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Core Metamodel Support -->
    <dependency>
        <groupId>org.apache.causeway.valuetypes</groupId>
        <artifactId>causeway-valuetypes-vega-metamodel</artifactId>
    </dependency>

    <!-- UI Support (Wicket Viewer) -->
    <dependency>
        <groupId>org.apache.causeway.valuetypes</groupId>
        <artifactId>causeway-valuetypes-vega-ui-wkt</artifactId>
    </dependency>

    <!-- JAXB Serialization Support -->
    <dependency>
        <groupId>org.apache.causeway.valuetypes</groupId>
        <artifactId>causeway-valuetypes-vega-applib</artifactId>
    </dependency>
</dependencies>
```

---

## 2. Registering the Module

Ensure that the Vega value type module is imported into your application's domain module or Spring Boot `AppManifest`.

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.apache.causeway.valuetypes.vega.applib.CausewayModuleValVegaApplib;
import org.apache.causeway.valuetypes.vega.ui.wkt.CausewayModuleValVegaUiWkt;

@Configuration
@Import({
    // ... other modules
    CausewayModuleValVegaApplib.class,
    CausewayModuleValVegaUiWkt.class
})
public class ApplicationModule {
}
```

---

## 3. Core Concepts & Usage in Domain Objects

The `Vega` class (`org.apache.causeway.valuetypes.vega.applib.value.Vega`) wraps a raw Vega or Vega-Lite JSON schema string.

### A. Using `Vega` as an Entity or View Model Property

You can declare a property of type `Vega` inside a domain object. The Causeway UI viewer (Wicket) automatically renders the spec as an interactive JavaScript chart.

```java
import javax.inject.Named;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.valuetypes.vega.applib.value.Vega;

@Named("demo.VegaViewModel")
@DomainObject
public class VegaViewModel {

    private Vega chartData;

    @Property
    public Vega getChartData() {
        return chartData;
    }

    public void setChartData(Vega chartData) {
        this.chartData = chartData;
    }
}
```

### B. Dynamically Constructing Vega Charts in Action Methods

> ⚠️ **Schema version matters — it decides whether the chart renders at all.**
> The Wicket viewer classifies a `Vega` value from its `$schema` field via the
> `Vega.Schema` enum, which this release recognizes **only** as
> `https://vega.github.io/schema/vega-lite/v6.json` or
> `https://vega.github.io/schema/vega/v6.json`. Any other value — including a
> `…/vega-lite/v5.json` URL — is classified `Schema.NONE`, and that silently:
> 1. **Loads no Vega/vega-lite/vega-embed JS libraries** into the page head, and
> 2. **Renders an empty HTML comment** (`<!-- empty Vega (unsupported schema) -->`).
>
> The chart then simply does not appear — no error, no console trace. When building
> a spec by hand, always emit the **v6** schema URLs above.

You can generate Vega specifications on the fly within action methods or domain services using standard JSON string representations or builders.

```java
import org.apache.causeway.applib.annotation.Action;
import org.apache.causeway.valuetypes.vega.applib.value.Vega;

public class AnalyticsService {

    @Action
    public Vega generateMonthlySalesChart() {
        String vegaJsonSpec = """
            {
              "$schema": "https://vega.github.io/schema/vega-lite/v6.json",
              "description": "Monthly Sales Performance",
              "data": {
                "values": [
                  {"month": "Jan", "sales": 120},
                  {"month": "Feb", "sales": 200},
                  {"month": "Mar", "sales": 150}
                ]
              },
              "mark": "bar",
              "encoding": {
                "x": {"field": "month", "type": "nominal"},
                "y": {"field": "sales", "type": "quantitative"}
              }
            }
            """;

        return new Vega(vegaJsonSpec);
    }
}
```
```

### C. Full-Vega Force-Directed Node-Link Diagrams (Cyclic Graphs)

For <em>node-link</em> graphs — e.g. a force-directed "cyclic" graph of entities and their
relationships, analogous to a D3 force layout — use **full Vega** (schema
`https://vega.github.io/schema/vega/v6.json`), not Vega-Lite: the force layout requires the Vega
`force` transform, which Vega-Lite does not support.

> ⚠️ **Renderer caveat.** The Wicket viewer renders full-Vega specs via
> `new vega.View(vega.parse(spec), {renderer: 'canvas', ...})` and loads **only**
> `vega.min.js`. The force layout, marks, labels and legends all render normally in canvas;
> hover tooltips and hyperlink `href` channels are canvas-inert (they only apply to SVG
> renderers). For bar charts and other non-layout plots keep using Vega-Lite (rendered by
> `vegaEmbed`, which supports tooltips).

Minimal full-Vega force spec shape (nodes carry a unique `id`; links reference those ids; the
link force declares `"id": "id"` so `source`/`target` resolve by node id rather than index):

```json
{
  "$schema": "https://vega.github.io/schema/vega/v6.json",
  "width": 700,
  "height": 460,
  "padding": 20,
  "data": [
    {
      "name": "node-data",
      "values": [
        {"id": "customer", "name": "Customer", "group": "Noun", "r": 12, "area": 452}
      ],
      "transform": [
        {
          "type": "force",
          "iterations": 300,
          "static": true,
          "forces": [
            {"force": "center", "x": {"signal": "width / 2"}, "y": {"signal": "height / 2"}},
            {"force": "link", "links": "link-data", "id": "id", "distance": 65},
            {"force": "collide", "radius": 15},
            {"force": "nbody", "strength": -40}
          ]
        }
      ]
    },
    {
      "name": "link-data",
      "values": [
        {"source": "purchase", "target": "customer", "label": "nsubj"}
      ],
      "transform": [
        {"type": "lookup", "from": "node-data", "key": "id", "fields": ["source"], "as": ["sourceNode"]},
        {"type": "lookup", "from": "node-data", "key": "id", "fields": ["target"], "as": ["targetNode"]}
      ]
    }
  ],
  "scales": [
    {
      "name": "color",
      "type": "ordinal",
      "domain": ["Noun", "Verb"],
      "range": ["#3498DB", "#E74C3C"],
      "legend": {"orient": "top-left", "title": "Part of Speech"}
    }
  ],
  "marks": [
    {"type": "rule", "from": {"data": "link-data"}, "encode": {"update": {
      "x": {"field": "sourceNode.x"}, "y": {"field": "sourceNode.y"},
      "x2": {"field": "targetNode.x"}, "y2": {"field": "targetNode.y"}}}},
    {"type": "symbol", "from": {"data": "node-data"}, "encode": {
      "enter": {"fill": {"scale": "color", "field": "group"}},
      "update": {"x": {"field": "x"}, "y": {"field": "y"}, "size": {"field": "area"}}}},
    {"type": "text", "from": {"data": "node-data"}, "encode": {"update": {
      "x": {"field": "x"}, "y": {"field": "y"}, "dx": {"value": 10},
      "text": {"field": "name"}}}}
  ]
}
```

See `domox-domain/src/main/java/domox/dom/crc/CandidateVegaGraph.java` for a production example
that builds such a spec from domain entities (candidate words as nodes, typed dependencies as
links, POS-coloured legend, hop-depth neighbourhood expansion).

---

## 4. XML / JAXB Serialization Support

When building view models backed by JAXB XML, use `VegaJaxbAdapter` to ensure smooth serialization and deserialization across REST endpoints or XML state persistence.

```java
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.causeway.valuetypes.vega.applib.jaxb.VegaJaxbAdapter;
import org.apache.causeway.valuetypes.vega.applib.value.Vega;

@XmlRootElement(name = "dashboard")
@XmlAccessorType(XmlAccessType.FIELD)
public class DashboardViewModel {

    @XmlElement
    @XmlJavaTypeAdapter(VegaJaxbAdapter.class)
    private Vega metricsChart;

    public Vega getMetricsChart() {
        return metricsChart;
    }

    public void setMetricsChart(Vega metricsChart) {
        this.metricsChart = metricsChart;
    }
}
```

---

## 5. Summary Checklist

| Task | Component | Key Class / Dependency |
| :--- | :--- | :--- |
| **Include Dependencies** | Maven POM | `causeway-valuetypes-vega-*` |
| **Import Spring Module** | AppManifest / Configuration | `CausewayModuleValuetypesVegaApplib` / `UiWkt` |
| **Declare Domain Property** | Entity or View Model | `org.apache.causeway.valuetypes.vega.applib.value.Vega` |
| **XML Adapter** | JAXB View Models | `@XmlJavaTypeAdapter(VegaJaxbAdapter.class)` |

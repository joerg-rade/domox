package domox.dom.rdf;

import domox.port.RdfGraphTO;
import domox.port.RdfTripleTO;
import jakarta.annotation.Priority;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Turns an RDF/OWL graph (as delivered by {@link domox.port.RdfPort}) into the JSON structure the
 * D3 force-layout renderer consumes: <code>{nodes, links, legend}</code>.
 *
 * <p>The shape is the same one {@code DomainModelDiagramsD3} / {@code CandidateD3Graph} produce, so
 * the existing client renderer (`d3-integration.mjs`) draws an ontology without any change:</p>
 *
 * <pre>
 *   "nodes": [ {"id","name","subdomain","pos","color","r","approved","isCandidateColumn"} ]
 *   "links": [ {"source","target","label"} ]
 *   "legend":[ {"color","label","description"} ]
 *   "reSearch": true          (enables the client's node-click drill-down)
 * </pre>
 *
 * <p><b>RDF → diagram mapping</b> (every RDF statement becomes an edge):</p>
 * <ul>
 *   <li><b>node id</b> — the IRI (blank nodes keep their <code>_:label</code>)</li>
 *   <li><b>name</b> — <code>rdfs:label</code> when present, else <code>ontouml:name</code>, else
 *       <code>skos:prefLabel</code>, else the IRI's local name.  A node <em>without</em> a name whose
 *       fallback local name is an opaque tool id (e.g. <code>ykj3OGFS_j2pB_5</code>) is dropped; a node
 *       carrying a real name is always kept, however machine-like that name happens to look — see
 *       {@link #looksLikeOpaqueId(String)}</li>
 *   <li><b>subdomain</b> — the cluster group (the client clusters nodes by this field):
 *       a <b>class</b> by its super-class' name (empty for a root class), an <b>individual</b> by its
 *       <code>rdf:type</code>, a <b>literal</b> by its predicate</li>
 *   <li><b>pos</b> — the node kind shown in <code>&lt;&lt;…&gt;&gt;</code>:
 *       {@code Class}, {@code Individual} or {@code Literal}</li>
 *   <li><b>links</b> — one per triple: subject → object, labelled with the predicate's local name
 *       (e.g. {@code subClassOf}, {@code owns}, {@code isVaccinated})</li>
 *   <li><b>ontology</b> — the reference ontology a node came from ({@code gist}, {@code BFO},
 *       {@code Schema.org}, …), shown as a fourth label line.  Empty when the source has no named
 *       graphs — see {@link #ontologyLabel(String)}</li>
 * </ul>
 *
 * <p><b>Two class vocabularies are understood.</b> A plain OWL/RDFS ontology types its classes
 * {@code owl:Class}/{@code rdfs:Class} and relates them with {@code rdfs:subClassOf}; a
 * <b>UML/OntoUML</b> export types them {@code ontouml:Class}, names them with {@code ontouml:name}
 * and reifies generalization (an {@code ontouml:Generalization} resource linking
 * {@code ontouml:general} to {@code ontouml:specific}).  Both are mapped onto the same node kinds
 * and the same cluster field — see the OntoUML constants below.
 */
@DomainService
@Named("domox.dom.rdf.RdfGraphDiagrams")
@Priority(PriorityPrecedence.LATE)
public class RdfGraphDiagrams {

    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String RDFS_SUBCLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
    private static final String OWL_CLASS = "http://www.w3.org/2002/07/owl#Class";
    private static final String RDFS_CLASS = "http://www.w3.org/2000/01/rdf-schema#Class";
    // A SKOS vocabulary (e.g. gist) names its terms with `skos:prefLabel` instead of `rdfs:label`.
    private static final String SKOS_PREF_LABEL = "http://www.w3.org/2004/02/skos/core#prefLabel";

    // -- OntoUML vocabulary (https://w3id.org/ontouml#) -------------------------
    //
    // A UML/OntoUML tool exports a model in its own vocabulary: classes are typed `ontouml:Class`
    // (not `owl:Class`), the human name lives in `ontouml:name` (there is no `rdfs:label`), and
    // generalization is *reified* — an `ontouml:Generalization` resource points at its parent via
    // `ontouml:general` and at its child via `ontouml:specific`, instead of a direct
    // `rdfs:subClassOf` statement.  Without these three rules such a file renders as a cloud of
    // opaque IDs with no classes at all.

    private static final String ONTOUML_NS = "https://w3id.org/ontouml#";
    private static final String ONTOUML_CLASS = ONTOUML_NS + "Class";
    private static final String ONTOUML_NAME = ONTOUML_NS + "name";
    private static final String ONTOUML_GENERAL = ONTOUML_NS + "general";
    private static final String ONTOUML_SPECIFIC = ONTOUML_NS + "specific";

    /**
     * Renders the graph as D3 JSON (nodes/links/legend).
     *
     * @param graph the RDF graph, e.g. {@code rdfPort.load("ontology.ttl")}
     */
    @Programmatic
    public String renderDiagram(final RdfGraphTO graph) {
        final Map<String, Node> nodes = indexNodes(graph);
        final StringBuilder json = new StringBuilder(256);
        json.append('{');
        appendNodes(json, nodes);
        json.append(',');
        appendLinks(json, graph, nodes);
        json.append(',');
        appendLegend(json, nodes);
        // The ontology payload supports drilling down: the client re-runs the ontology search for a
        // clicked node's label and redraws the graph.  Flagged here so the shared renderer enables that
        // only for ontology graphs — a CRC candidate diagram's synthetic nodes must not trigger it.  An
        // empty graph has nothing to click, so it keeps the plain {nodes,links,legend} shape.
        if (!nodes.isEmpty()) {
            json.append(",\"reSearch\":true");
        }
        json.append('}');
        return json.toString();
    }

    // -- nodes -----------------------------------------------------------------

    /**
     * Every resource mentioned by the graph becomes a node; literals become nodes too, so data
     * values stay visible.
     * <p>
     * <b>Resource <em>objects</em> count as well, not just subjects.</b>  {@code appendLinks} skips
     * an edge whose endpoints are not both nodes, so a resource that only ever appears as an object
     * (e.g. the class in {@code Whiskers rdf:type Cat}) would otherwise have its edge silently
     * dropped — the "one hop of context" a neighbourhood search is meant to show.
     */
    private static Map<String, Node> indexNodes(final RdfGraphTO graph) {
        final Map<String, String> labelByIri = new LinkedHashMap<>();
        final Map<String, String> ontoumlNameByIri = new LinkedHashMap<>();
        final Map<String, String> skosPrefLabelByIri = new LinkedHashMap<>();
        final Map<String, String> typeByIri = new LinkedHashMap<>();
        final Map<String, String> superClassByIri = new LinkedHashMap<>();
        // IRI → the named graph it was read from; empty for a local file / single-default-graph endpoint.
        final Map<String, String> graphByIri = graph.graphByIri();
        final Set<String> classIris = new LinkedHashSet<>();
        // An OntoUML generalization is reified: g --general--> parent, g --specific--> child.
        // Collected per generalization resource, then folded into superClassByIri after the loop
        // (the two statements are not adjacent in the source, so a single pass cannot resolve them).
        final Map<String, String> generalOfGeneralization = new LinkedHashMap<>();
        final Map<String, String> specificOfGeneralization = new LinkedHashMap<>();
        for (final RdfTripleTO triple : graph.triples()) {
            if (RDFS_LABEL.equals(triple.predicate()) && triple.literal()) {
                labelByIri.putIfAbsent(triple.subject(), triple.objectValue());
            } else if (ONTOUML_NAME.equals(triple.predicate()) && triple.literal()) {
                ontoumlNameByIri.putIfAbsent(triple.subject(), triple.objectValue());
            } else if (SKOS_PREF_LABEL.equals(triple.predicate()) && triple.literal()) {
                skosPrefLabelByIri.putIfAbsent(triple.subject(), triple.objectValue());
            } else if (RDF_TYPE.equals(triple.predicate()) && !triple.literal()) {
                typeByIri.putIfAbsent(triple.subject(), triple.objectValue());
                if (OWL_CLASS.equals(triple.objectValue())
                        || RDFS_CLASS.equals(triple.objectValue())
                        || ONTOUML_CLASS.equals(triple.objectValue())) {
                    classIris.add(triple.subject());
                }
            } else if (RDFS_SUBCLASS_OF.equals(triple.predicate()) && !triple.literal()) {
                superClassByIri.putIfAbsent(triple.subject(), triple.objectValue());
                classIris.add(triple.subject());
                classIris.add(triple.objectValue());
            } else if (ONTOUML_GENERAL.equals(triple.predicate()) && !triple.literal()) {
                generalOfGeneralization.putIfAbsent(triple.subject(), triple.objectValue());
            } else if (ONTOUML_SPECIFIC.equals(triple.predicate()) && !triple.literal()) {
                specificOfGeneralization.putIfAbsent(triple.subject(), triple.objectValue());
            }
        }
        // `ontouml:name` is the OntoUML equivalent of `rdfs:label`, and `skos:prefLabel` is the SKOS
        // one (gist names its classes with it); an explicit `rdfs:label` still wins over both.
        ontoumlNameByIri.forEach(labelByIri::putIfAbsent);
        skosPrefLabelByIri.forEach(labelByIri::putIfAbsent);
        // A reified generalization carries the same "child is a kind of parent" information that
        // rdfs:subClassOf does, so it feeds the same map (and both ends are classes).
        specificOfGeneralization.forEach((generalization, child) -> {
            final String parent = generalOfGeneralization.get(generalization);
            if (parent == null) {
                return;
            }
            superClassByIri.putIfAbsent(child, parent);
            classIris.add(child);
            classIris.add(parent);
        });

        final Map<String, Node> nodes = new LinkedHashMap<>();
        for (final RdfTripleTO triple : graph.triples()) {
            addResourceNode(nodes, triple.subject(), labelByIri, typeByIri, superClassByIri, classIris, graphByIri);

            if (triple.literal()) {
                final String literalId = literalId(triple.objectValue());
                nodes.putIfAbsent(literalId,
                        new Node(literalId, triple.objectValue(),
                                localName(triple.predicate()), Kind.LITERAL,
                                ontologyLabel(graphByIri.get(triple.subject()))));
            } else {
                addResourceNode(nodes, triple.objectValue(), labelByIri, typeByIri, superClassByIri, classIris, graphByIri);
            }
        }
        // Drop only the nodes that have NO name to show — an unnamed resource whose sole label is its
        // IRI's local name, which for a UML/OntoUML export is an opaque tool id (`ykj3OGFS_j2pB_5`).
        // A resource carrying a real `rdfs:label` / `ontouml:name` is NEVER dropped: the name has
        // already replaced the id, and the heuristic must not second-guess it — a genuine name such as
        // `B2B` interleaves letters and digits and would otherwise be mistaken for an id.  Dropping a
        // node also drops the edges that pointed at it, because `appendLinks` skips an edge whose
        // endpoints are not both nodes — which is how the export's `_shape` / `_path_point_0`
        // satellites (thousands of them in the bundled model) go with it.
        nodes.values().removeIf(node -> !hasName(node.id(), labelByIri) && looksLikeOpaqueId(node.name()));
        return nodes;
    }

    /** Adds an IRI node (a no-op if it is already present), classifying it as class or individual. */
    private static void addResourceNode(
            final Map<String, Node> nodes,
            final String iri,
            final Map<String, String> labelByIri,
            final Map<String, String> typeByIri,
            final Map<String, String> superClassByIri,
            final Set<String> classIris,
            final Map<String, String> graphByIri) {
        if (nodes.containsKey(iri)) {
            return;
        }
        final Kind kind = classIris.contains(iri) ? Kind.CLASS : Kind.INDIVIDUAL;
        final String typeIri = typeByIri.get(iri);
        final String group = kind == Kind.CLASS
                ? groupOfClass(iri, superClassByIri, labelByIri)
                : (typeIri != null ? localName(typeIri) : "");
        nodes.put(iri, new Node(iri, labelOf(iri, labelByIri), group, kind, ontologyLabel(graphByIri.get(iri))));
    }

    /**
     * A class clusters with its super-class ({@code Dog} under {@code Pet}); a root class has no
     * group, so the client places it at the centre of the canvas.
     * <p>
     * The group is the parent's <em>name</em> where one is known (the client renders this italic
     * line above the node label) — an OntoUML model's parent IRIs are opaque tool IDs, so the local
     * name alone would put an unreadable id in the diagram.
     */
    private static String groupOfClass(
            final String iri,
            final Map<String, String> superClassByIri,
            final Map<String, String> labelByIri) {
        final String parent = superClassByIri.get(iri);
        if (parent == null) {
            return "";
        }
        final String parentLabel = labelOf(parent, labelByIri);
        // Only a parent with NO name of its own would leak its opaque tool id into the cluster label;
        // a named parent is used verbatim, however machine-like the name looks.
        return !hasName(parent, labelByIri) && looksLikeOpaqueId(parentLabel) ? "" : parentLabel;
    }

    /**
     * Whether the resource behind {@code id} carries an explicit name — an {@code rdfs:label}, an
     * {@code ontouml:name} or a {@code skos:prefLabel}.
     * <p>
     * Only an <em>unnamed</em> resource may be discarded for looking like a generated id: a name the
     * model actually states is authoritative, however machine-like it happens to look.  A literal
     * node's id ({@code literal:…}) is never a key here, so a literal is judged by its own value.
     */
    private static boolean hasName(final String id, final Map<String, String> labelByIri) {
        return labelByIri.containsKey(id);
    }

    private static String labelOf(final String iri, final Map<String, String> labelByIri) {
        final String label = labelByIri.get(iri);
        return label != null ? label : localName(iri);
    }

    private static String literalId(final String lexicalForm) {
        return "literal:" + lexicalForm;
    }

    // -- links -----------------------------------------------------------------

    private static void appendLinks(
            final StringBuilder json, final RdfGraphTO graph, final Map<String, Node> nodes) {
        json.append("\"links\":[");
        boolean first = true;
        final Set<String> seen = new LinkedHashSet<>();
        for (final RdfTripleTO triple : graph.triples()) {
            final String target = triple.literal() ? literalId(triple.objectValue()) : triple.objectValue();
            if (!nodes.containsKey(triple.subject()) || !nodes.containsKey(target)) {
                continue;
            }
            if (!seen.add(triple.subject() + "->" + target + ":" + triple.predicate())) {
                continue;   // duplicate statements in the source graph
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"source\":\"").append(escapeJson(triple.subject()))
                    .append("\",\"target\":\"").append(escapeJson(target))
                    .append("\",\"label\":\"").append(escapeJson(localName(triple.predicate())))
                    .append("\"}");
        }
        json.append(']');
    }

    // -- nodes / legend JSON ---------------------------------------------------

    private static void appendNodes(final StringBuilder json, final Map<String, Node> nodes) {
        json.append("\"nodes\":[");
        boolean first = true;
        for (final Node node : nodes.values()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"id\":\"").append(escapeJson(node.id()))
                    .append("\",\"name\":\"").append(escapeJson(node.name()))
                    .append("\",\"subdomain\":\"").append(escapeJson(node.typeLocalName()))
                    .append("\",\"pos\":\"").append(node.kind().label)
                    .append("\",\"color\":\"").append(node.kind().color)
                    .append("\",\"r\":").append(node.kind().radius)
                    .append(",\"ontology\":\"").append(escapeJson(node.ontology()))
                    .append("\",\"approved\":true")
                    .append(",\"isCandidateColumn\":false}");
        }
        json.append(']');
    }

    /** One legend entry per node kind that actually occurs in the graph. */
    private static void appendLegend(final StringBuilder json, final Map<String, Node> nodes) {
        json.append("\"legend\":[");
        boolean first = true;
        for (final Kind kind : Kind.values()) {
            if (nodes.values().stream().noneMatch(node -> node.kind() == kind)) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"color\":\"").append(kind.color)
                    .append("\",\"label\":\"").append(kind.label)
                    .append("\",\"description\":\"").append(kind.description).append("\"}");
        }
        json.append(']');
    }

    // -- helpers ---------------------------------------------------------------

    /**
     * Whether {@code label} is an opaque machine identifier rather than a human-readable name — the
     * discriminator the diagram uses to drop the noise nodes a UML/OntoUML tool leaves behind
     * (e.g. {@code ykj3OGFS_j2pB_5}, {@code 02kj3OGFS_j2pCNa_path_point_0}, a UUID).
     *
     * <p>A name is considered human when it <b>contains whitespace</b> ({@code "Feather Cat Toy"},
     * {@code "Sale 4711"}, {@code "Social Relator"}) or when letters and digits are <b>not
     * interleaved</b>: a plain word ({@code Pet}, {@code actions}, {@code cl_hiredprovider}), a
     * leading/trailing number ({@code "1st Order Entities"}, {@code AssociationClass1}) or a pure
     * number ({@code 507}, {@code 24.90}) all stay.  It is opaque when <b>letters and digits alternate
     * at least twice</b> — the signature of a generated id — which every example above shares, while
     * the human names do not.</p>
     *
     * <p>Consulted only for resources that have <em>no</em> name of their own: a resource with an
     * {@code rdfs:label} / {@code ontouml:name} is kept whatever that name looks like, so a real
     * {@code B2B} or {@code H1N1} is never mistaken for a generated id.</p>
     *
     * <p>Public for the guardrail test; it is a pure function and static, so Causeway does not treat
     * it as a member (see {@code localName}).</p>
     */
    static boolean looksLikeOpaqueId(final String label) {
        if (label == null) {
            return true;
        }
        final String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (trimmed.chars().anyMatch(Character::isWhitespace)) {
            return false;   // a phrase of words, however short ("A", "Based on")
        }
        int alternations = 0;
        for (int i = 1; i < trimmed.length(); i++) {
            final char previous = trimmed.charAt(i - 1);
            final char current = trimmed.charAt(i);
            if (Character.isLetterOrDigit(previous) && Character.isLetterOrDigit(current)
                    && Character.isDigit(previous) != Character.isDigit(current)) {
                alternations++;
            }
        }
        return alternations >= 2;
    }

    /**
     * A short, human-friendly name for the reference ontology a graph IRI denotes — the label the
     * diagram prints on a node's fourth line ({@code gist}, {@code BFO}, {@code Schema.org}, …).
     *
     * <p>Falls back to the graph IRI's local name for an ontology we do not know, and to an empty
     * string when there is no attribution at all (a local file, or a single-default-graph endpoint) —
     * in which case the client simply omits the line.</p>
     *
     * <p>Public for the guardrail test; it is a pure function and static, so Causeway does not treat
     * it as a member (see {@code localName}).</p>
     */
    static String ontologyLabel(final String graphIri) {
        if (graphIri == null || graphIri.isBlank()) {
            return "";
        }
        return switch (graphIri) {
            case "https://schema.org/" -> "Schema.org";
            case "http://purl.obolibrary.org/obo/bfo.owl" -> "BFO";
            case "https://w3id.org/semanticarts/ontology/gistCore" -> "gist";
            case "http://BPMNbasedOntology" -> "BBO";
            case "https://ntimes404.github.io/sBPMN/" -> "sBPMN";
            default -> localName(graphIri);
        };
    }

    /** Local name of an IRI: after {@code #}, else after the last {@code /}. */
    static String localName(final String iri) {
        if (iri == null || iri.isEmpty()) {
            return "";
        }
        final int hash = iri.lastIndexOf('#');
        if (hash >= 0 && hash + 1 < iri.length()) {
            return iri.substring(hash + 1);
        }
        final int slash = iri.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < iri.length()) {
            return iri.substring(slash + 1);
        }
        return iri;
    }

    static String escapeJson(final String value) {
        if (value == null) {
            return "";
        }
        final StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private record Node(String id, String name, String typeLocalName, Kind kind, String ontology) {
    }

    /** The three node kinds of an RDF graph, rendered in the {@code <<…>>} label line. */
    private enum Kind {
        CLASS("Class", "#3498DB", 14, "owl:Class / rdfs:Class - a concept"),
        INDIVIDUAL("Individual", "#2ECC71", 11, "An instance (rdf:type of a class)"),
        LITERAL("Literal", "#AAB7B8", 8, "A data value");

        final String label;
        final String color;
        final int radius;
        final String description;

        Kind(final String label, final String color, final int radius, final String description) {
            this.label = label;
            this.color = color;
            this.radius = radius;
            this.description = description;
        }
    }
}

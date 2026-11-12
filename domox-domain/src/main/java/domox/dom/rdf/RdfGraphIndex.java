package domox.dom.rdf;

import domox.port.RdfGraphTO;
import domox.port.RdfTripleTO;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * <b>Indexes an RDF/OWL graph into the nodes a diagram draws.</b>  Every resource mentioned by the
 * graph becomes a node (literals too, so data values stay visible), classified as a class or an
 * individual and grouped into the cluster the client renders.
 *
 * <p>Extracted from {@code RdfGraphDiagrams}, which also owned the IRI naming rules and the JSON
 * emission; this class does nothing but turn the triples into nodes, so a change to what counts as a
 * node — or to which one is dropped as noise — is made here and nowhere else.</p>
 *
 * <p><b>Two class vocabularies are understood.</b> A plain OWL/RDFS ontology types its classes
 * {@code owl:Class}/{@code rdfs:Class} and relates them with {@code rdfs:subClassOf}; a
 * <b>UML/OntoUML</b> export types them {@code ontouml:Class}, names them with {@code ontouml:name}
 * and reifies generalization (an {@code ontouml:Generalization} resource linking
 * {@code ontouml:general} to {@code ontouml:specific}).  Both are mapped onto the same node kinds
 * and the same cluster field.</p>
 */
final class RdfGraphIndex {

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

    private RdfGraphIndex() {
    }

    /**
     * Every resource mentioned by the graph becomes a node; literals become nodes too, so data
     * values stay visible.
     * <p>
     * <b>Resource <em>objects</em> count as well, not just subjects.</b>  {@code RdfDiagramJson}
     * skips an edge whose endpoints are not both nodes, so a resource that only ever appears as an
     * object (e.g. the class in {@code Whiskers rdf:type Cat}) would otherwise have its edge silently
     * dropped — the "one hop of context" a neighbourhood search is meant to show.
     */
    static Map<String, RdfDiagramNode> index(final RdfGraphTO graph) {
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

        final Map<String, RdfDiagramNode> nodes = new LinkedHashMap<>();
        for (final RdfTripleTO triple : graph.triples()) {
            addResourceNode(nodes, triple.subject(), labelByIri, typeByIri, superClassByIri, classIris, graphByIri);

            if (triple.literal()) {
                final String literalId = literalId(triple.objectValue());
                nodes.putIfAbsent(literalId,
                        new RdfDiagramNode(literalId, triple.objectValue(),
                                RdfIriNames.localName(triple.predicate()), RdfNodeKind.LITERAL,
                                RdfIriNames.ontologyLabel(graphByIri.get(triple.subject()))));
            } else {
                addResourceNode(nodes, triple.objectValue(), labelByIri, typeByIri, superClassByIri, classIris, graphByIri);
            }
        }
        // Drop only the nodes that have NO name to show — an unnamed resource whose sole label is its
        // IRI's local name, which for a UML/OntoUML export is an opaque tool id (`ykj3OGFS_j2pB_5`).
        // A resource carrying a real `rdfs:label` / `ontouml:name` is NEVER dropped: the name has
        // already replaced the id, and the heuristic must not second-guess it — a genuine name such as
        // `B2B` interleaves letters and digits and would otherwise be mistaken for an id.  Dropping a
        // node also drops the edges that pointed at it, because `RdfDiagramJson` skips an edge whose
        // endpoints are not both nodes — which is how the export's `_shape` / `_path_point_0`
        // satellites (thousands of them in the bundled model) go with it.
        nodes.values().removeIf(node -> !hasName(node.id(), labelByIri) && RdfIriNames.looksLikeOpaqueId(node.name()));
        return nodes;
    }

    /** Adds an IRI node (a no-op if it is already present), classifying it as class or individual. */
    private static void addResourceNode(
            final Map<String, RdfDiagramNode> nodes,
            final String iri,
            final Map<String, String> labelByIri,
            final Map<String, String> typeByIri,
            final Map<String, String> superClassByIri,
            final Set<String> classIris,
            final Map<String, String> graphByIri) {
        if (nodes.containsKey(iri)) {
            return;
        }
        final RdfNodeKind kind = classIris.contains(iri) ? RdfNodeKind.CLASS : RdfNodeKind.INDIVIDUAL;
        final String typeIri = typeByIri.get(iri);
        final String group = kind == RdfNodeKind.CLASS
                ? groupOfClass(iri, superClassByIri, labelByIri)
                : (typeIri != null ? RdfIriNames.localName(typeIri) : "");
        nodes.put(iri, new RdfDiagramNode(iri, labelOf(iri, labelByIri), group, kind,
                RdfIriNames.ontologyLabel(graphByIri.get(iri))));
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
        return !hasName(parent, labelByIri) && RdfIriNames.looksLikeOpaqueId(parentLabel) ? "" : parentLabel;
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
        return label != null ? label : RdfIriNames.localName(iri);
    }

    /**
     * The id a literal node carries.  Shared with {@link RdfDiagramJson}, which must derive the same
     * id for a literal link target or the edge would be dropped as dangling.
     */
    static String literalId(final String lexicalForm) {
        return "literal:" + lexicalForm;
    }
}

package domox.dom.rdf;

import domox.port.RdfGraphTO;
import domox.port.RdfTripleTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level guardrail for the RDF → D3 JSON mapping.  The end-to-end path over a real ontology
 * (Jena load → SPARQL → JSON, parsed as JSON) is covered by {@code JenaRdfAdapterTest} in
 * {@code domox-adapter}; this one pins the mapping rules and the JSON escaping without any
 * Semantic-Web dependency.
 */
class RdfGraphDiagramsTest {

    private static final String PS = "http://example.org/petshop#";
    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String RDFS_SUBCLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
    private static final String OWL_CLASS = "http://www.w3.org/2002/07/owl#Class";

    // -- OntoUML vocabulary (a UML tool's export) ------------------------------
    private static final String OU = "https://w3id.org/ontouml#";
    private static final String ONTOUML_CLASS = OU + "Class";
    private static final String ONTOUML_NAME = OU + "name";
    private static final String ONTOUML_GENERAL = OU + "general";
    private static final String ONTOUML_SPECIFIC = OU + "specific";

    private final RdfGraphDiagrams diagrams = new RdfGraphDiagrams();

    @Test
    void mapsTriplesToNodesLinksAndLegend() {
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Dog", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.resource(PS + "Dog", RDFS_SUBCLASS_OF, PS + "Pet"),
                RdfTripleTO.resource(PS + "Pet", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.resource(PS + "Buddy", RDF_TYPE, PS + "Dog"),
                RdfTripleTO.literal(PS + "Buddy", RDFS_LABEL, "Buddy")
        ), Map.of("ps", PS));

        final String json = diagrams.renderDiagram(graph);

        assertThat(json).startsWith("{");
        assertThat(json).contains("\"nodes\":[");
        assertThat(json).contains("\"links\":[");
        assertThat(json).contains("\"legend\":[");

        // a class clusters under its super-class; an individual under its rdf:type
        assertThat(json).contains(
                "\"id\":\"" + PS + "Dog\",\"name\":\"Dog\",\"subdomain\":\"Pet\",\"pos\":\"Class\"");
        assertThat(json).contains(
                "\"id\":\"" + PS + "Buddy\",\"name\":\"Buddy\",\"subdomain\":\"Dog\",\"pos\":\"Individual\"");

        // every statement becomes an edge, labelled with the predicate's local name
        assertThat(json).contains(
                "\"source\":\"" + PS + "Dog\",\"target\":\"" + PS + "Pet\",\"label\":\"subClassOf\"");

        // all three node kinds occur -> one legend entry each
        assertThat(json).contains("\"label\":\"Class\"");
        assertThat(json).contains("\"label\":\"Individual\"");
        assertThat(json).contains("\"label\":\"Literal\"");
    }

    @Test
    void resourceObjectsBecomeNodesSoTheirEdgesSurvive() {
        // Whiskers --rdf:type--> Cat, and Cat appears ONLY as an object (it is never a subject)
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Whiskers", RDF_TYPE, PS + "Cat")
        ), Map.of("ps", PS));

        final String json = diagrams.renderDiagram(graph);

        assertThat(json).as("the object resource must become a node").contains("\"id\":\"" + PS + "Cat\"");
        assertThat(json).as("…otherwise the edge is silently dropped").contains("\"target\":\"" + PS + "Cat\"");
        assertThat(json).contains("\"label\":\"type\"");
    }

    @Test
    void escapesLabelsAndLeavesRootClassesUngrouped() {
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Quote", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.literal(PS + "Quote", RDFS_LABEL, "a \"quoted\" label")
        ), Map.of());

        final String json = diagrams.renderDiagram(graph);

        assertThat(json).contains("\"name\":\"a \\\"quoted\\\" label\"");
        assertThat(json).as("a root class has no cluster group").contains("\"subdomain\":\"\"");
    }

    /**
     * A UML/OntoUML export uses its own vocabulary: {@code ontouml:Class} (not {@code owl:Class}),
     * {@code ontouml:name} (not {@code rdfs:label}) and a <b>reified</b> generalization — an
     * {@code ontouml:Generalization} resource pointing at its parent via {@code ontouml:general} and
     * at its child via {@code ontouml:specific}.  Without those three rules such a model renders as a
     * cloud of opaque tool IDs with no classes at all.
     */
    @Test
    void mapsAnOntoumlModelClassesNamesAndReifiedGeneralization() {
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(OU + "SocialRelator", RDF_TYPE, ONTOUML_CLASS),
                RdfTripleTO.literal(OU + "SocialRelator", ONTOUML_NAME, "Social Relator"),
                RdfTripleTO.resource(OU + "Commitment", RDF_TYPE, ONTOUML_CLASS),
                RdfTripleTO.literal(OU + "Commitment", ONTOUML_NAME, "Commitment"),
                // Commitment is a kind of Social Relator, reified as a Generalization resource
                RdfTripleTO.resource(OU + "G1", RDF_TYPE, OU + "Generalization"),
                RdfTripleTO.resource(OU + "G1", ONTOUML_SPECIFIC, OU + "Commitment"),
                RdfTripleTO.resource(OU + "G1", ONTOUML_GENERAL, OU + "SocialRelator")
        ), Map.of());

        final String json = diagrams.renderDiagram(graph);

        // ontouml:Class is a class, and its label is the ontouml:name (not the opaque IRI local name)
        assertThat(json).contains("\"id\":\"" + OU + "Commitment\",\"name\":\"Commitment\","
                + "\"subdomain\":\"Social Relator\",\"pos\":\"Class\"");
        // the parent of the reified generalization is a root class: a class, but no cluster group
        assertThat(json).contains("\"id\":\"" + OU + "SocialRelator\",\"name\":\"Social Relator\","
                + "\"subdomain\":\"\",\"pos\":\"Class\"");
        // …so the Class kind is present in the legend
        assertThat(json).contains("\"label\":\"Class\"");
    }

    /** An explicit {@code rdfs:label} wins over an {@code ontouml:name} on the same resource. */
    @Test
    void rdfsLabelTakesPrecedenceOverAnOntoumlName() {
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Pet", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.literal(PS + "Pet", RDFS_LABEL, "Pet"),
                RdfTripleTO.literal(PS + "Pet", ONTOUML_NAME, "Haustier")
        ), Map.of());

        final String json = diagrams.renderDiagram(graph);

        // the *resource* is labelled "Pet" — the ontouml:name did not override it
        assertThat(json).contains("\"id\":\"" + PS + "Pet\",\"name\":\"Pet\",\"subdomain\":\"\",\"pos\":\"Class\"");
        // (the ontouml:name is still present as its own Literal node — every statement becomes an edge)
        assertThat(json).contains("\"label\":\"name\"");
    }

    /**
     * A SKOS vocabulary names its terms with {@code skos:prefLabel} rather than {@code rdfs:label} —
     * the shape <b>gist</b> uses.  It is the third name source, after {@code rdfs:label} and
     * {@code ontouml:name}.
     */
    @Test
    void skosPrefLabelNamesANode() {
        final String skos = "http://www.w3.org/2004/02/skos/core#prefLabel";
        final String gist = "https://w3id.org/semanticarts/ns/ontology/gist/";
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(gist + "Agreement", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.literal(gist + "Agreement", skos, "Agreement"),
                RdfTripleTO.resource(gist + "Contract", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.resource(gist + "Contract", RDFS_SUBCLASS_OF, gist + "Agreement"),
                RdfTripleTO.literal(gist + "Contract", skos, "Contract")
        ), Map.of());

        final String json = diagrams.renderDiagram(graph);

        assertThat(json).contains("\"id\":\"" + gist + "Agreement\",\"name\":\"Agreement\","
                + "\"subdomain\":\"\",\"pos\":\"Class\"");
        assertThat(json).as("the sub-class clusters under its skos:prefLabel-named parent")
                .contains("\"id\":\"" + gist + "Contract\",\"name\":\"Contract\","
                        + "\"subdomain\":\"Agreement\",\"pos\":\"Class\"");
    }

    /** An explicit {@code rdfs:label} wins over a {@code skos:prefLabel} on the same resource. */
    @Test
    void rdfsLabelTakesPrecedenceOverASkosPrefLabel() {
        final String skos = "http://www.w3.org/2004/02/skos/core#prefLabel";
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Pet", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.literal(PS + "Pet", RDFS_LABEL, "Pet"),
                RdfTripleTO.literal(PS + "Pet", skos, "Haustier")
        ), Map.of());

        final String json = diagrams.renderDiagram(graph);

        assertThat(json).contains("\"id\":\"" + PS + "Pet\",\"name\":\"Pet\",\"subdomain\":\"\",\"pos\":\"Class\"");
    }

    @Test
    void localNameHandlesHashSlashAndMissingIris() {
        assertThat(RdfGraphDiagrams.localName(PS + "Dog")).isEqualTo("Dog");
        assertThat(RdfGraphDiagrams.localName(OWL_CLASS)).isEqualTo("Class");
        assertThat(RdfGraphDiagrams.localName("http://example.org/index.html")).isEqualTo("index.html");
        assertThat(RdfGraphDiagrams.localName(null)).isEmpty();
    }

    /**
     * The opaque-id discriminator: a UML/OntoUML export names its diagram *layout* resources with
     * generated ids ({@code ykj3OGFS_j2pB_5}), which must be told apart from real names.
     */
    @Test
    void distinguishesOpaqueIdsFromHumanNames() {
        // opaque tool ids (letters and digits alternate at least twice, no whitespace)
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId("ykj3OGFS_j2pB_5")).isTrue();
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId("02kj3OGFS_j2pCNa")).isTrue();
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId("02kj3OGFS_j2pCNa_path_point_0")).isTrue();
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId("092b46b9-c5e1-428c-9e20-4343e235985b")).isTrue();

        // human names — a phrase, a plain word, a leading/trailing number, a pure value
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId("Feather Cat Toy")).isFalse();
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId("Sale 4711")).isFalse();
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId("Social Relator")).isFalse();
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId("Pet")).isFalse();
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId("actions")).isFalse();
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId("cl_hiredprovider")).isFalse();
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId("AssociationClass1")).isFalse();
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId("1st Order Entities")).isFalse();
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId("507")).isFalse();

        // a missing name is treated as opaque (nothing to display)
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId(null)).isTrue();
        assertThat(RdfGraphDiagrams.looksLikeOpaqueId("  ")).isTrue();
    }

    /**
     * A node whose name is an opaque tool id is dropped, and so is the edge that pointed at it — but
     * a node with a human name (and its edges) survives untouched.
     */
    @Test
    void dropsOpaqueIdNodesAndTheirEdges() {
        final String opaque = "https://w3id.org/ontouml-models/model/services2015#ykj3OGFS_j2pB_5";
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                // a named class, connected to a named parent ...
                RdfTripleTO.resource(OU + "Commitment", RDF_TYPE, ONTOUML_CLASS),
                RdfTripleTO.literal(OU + "Commitment", ONTOUML_NAME, "Commitment"),
                RdfTripleTO.resource(OU + "G1", RDF_TYPE, OU + "Generalization"),
                RdfTripleTO.resource(OU + "G1", ONTOUML_SPECIFIC, OU + "Commitment"),
                RdfTripleTO.resource(OU + "G1", ONTOUML_GENERAL, OU + "SocialRelator"),
                RdfTripleTO.literal(OU + "SocialRelator", ONTOUML_NAME, "Social Relator"),
                // ... and a layout resource named only by its generated id
                RdfTripleTO.resource(OU + "Commitment", OU + "shape", opaque),
                RdfTripleTO.resource(opaque, RDF_TYPE, OU + "Rectangle")
        ), Map.of());

        final String json = diagrams.renderDiagram(graph);

        assertThat(json).as("the opaque-id node (and the edge to it) must be gone").doesNotContain("ykj3OGFS_j2pB_5");
        assertThat(json).as("a human-named node stays").contains("\"id\":\"" + OU + "Commitment\",\"name\":\"Commitment\"");
        assertThat(json).as("the named nodes keep their edge").contains("\"label\":\"specific\"");
    }

    /**
     * A class whose super-class is known only by an opaque id must not leak that id into the cluster
     * label — the group is blanked, exactly as for a root class.
     */
    @Test
    void blanksAClusterGroupThatIsOnlyAnOpaqueId() {
        final String parent = "https://w3id.org/ontouml-models/model/services2015#aCkj3OGFS_j2pB5b";
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(OU + "Commitment", RDF_TYPE, ONTOUML_CLASS),
                RdfTripleTO.literal(OU + "Commitment", ONTOUML_NAME, "Commitment"),
                RdfTripleTO.resource(OU + "G1", RDF_TYPE, OU + "Generalization"),
                RdfTripleTO.resource(OU + "G1", ONTOUML_SPECIFIC, OU + "Commitment"),
                RdfTripleTO.resource(OU + "G1", ONTOUML_GENERAL, parent)
        ), Map.of());

        final String json = diagrams.renderDiagram(graph);

        assertThat(json).contains("\"id\":\"" + OU + "Commitment\",\"name\":\"Commitment\","
                + "\"subdomain\":\"\",\"pos\":\"Class\"");
    }

    /**
     * A node whose <em>real</em> name happens to interleave letters and digits (e.g. {@code B2B}) must
     * survive: the opaque-id heuristic is a fallback for <em>unnamed</em> resources, not a veto on a
     * name the model actually carries.
     */
    @Test
    void keepsANamedNodeEvenWhenItsNameLooksMachineGenerated() {
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(OU + "B2B", RDF_TYPE, ONTOUML_CLASS),
                RdfTripleTO.literal(OU + "B2B", ONTOUML_NAME, "B2B"),
                // ... while an unnamed layout resource is still dropped
                RdfTripleTO.resource(OU + "B2B", OU + "shape", OU + "ykj3OGFS_j2pB_5"),
                RdfTripleTO.resource(OU + "ykj3OGFS_j2pB_5", RDF_TYPE, OU + "Rectangle")
        ), Map.of());

        final String json = diagrams.renderDiagram(graph);

        assertThat(json).as("a named node is kept even though its name looks like an id").contains("\"name\":\"B2B\"");
        assertThat(json).as("the unnamed layout node is still dropped").doesNotContain("ykj3OGFS_j2pB_5");
    }

    /**
     * A cluster label taken from a <em>named</em> parent is used verbatim, even when the name looks
     * machine-generated — only an unnamed parent is blanked.
     */
    @Test
    void keepsAClusterGroupThatComesFromANamedParent() {
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(OU + "Commitment", RDF_TYPE, ONTOUML_CLASS),
                RdfTripleTO.literal(OU + "Commitment", ONTOUML_NAME, "Commitment"),
                RdfTripleTO.resource(OU + "G1", RDF_TYPE, OU + "Generalization"),
                RdfTripleTO.resource(OU + "G1", ONTOUML_SPECIFIC, OU + "Commitment"),
                RdfTripleTO.resource(OU + "G1", ONTOUML_GENERAL, OU + "B2B"),
                RdfTripleTO.literal(OU + "B2B", ONTOUML_NAME, "B2B")
        ), Map.of());

        final String json = diagrams.renderDiagram(graph);

        assertThat(json).contains("\"id\":\"" + OU + "Commitment\",\"name\":\"Commitment\","
                + "\"subdomain\":\"B2B\",\"pos\":\"Class\"");
    }
}

package domox.dom.rdf;

import domox.port.RdfGraphTO;
import domox.port.RdfTripleTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level guardrail for the RDF → node indexing rules: which triples produce a node, how a node is
 * classified and grouped, and which nodes are dropped as generated-id noise.  Exercised directly so a
 * change here is caught without going through the JSON writer.
 */
class RdfGraphIndexTest {

    private static final String PS = "http://example.org/petshop#";
    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String RDFS_SUBCLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
    private static final String OWL_CLASS = "http://www.w3.org/2002/07/owl#Class";

    @Test
    void classifiesClassesIndividualsAndLiterals() {
        final Map<String, RdfDiagramNode> nodes = RdfGraphIndex.index(new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Dog", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.resource(PS + "Buddy", RDF_TYPE, PS + "Dog"),
                RdfTripleTO.literal(PS + "Buddy", RDFS_LABEL, "Buddy")
        ), Map.of()));

        assertThat(nodes.get(PS + "Dog").kind()).isEqualTo(RdfNodeKind.CLASS);
        assertThat(nodes.get(PS + "Buddy").kind()).isEqualTo(RdfNodeKind.INDIVIDUAL);
        assertThat(nodes.get("literal:Buddy").kind()).isEqualTo(RdfNodeKind.LITERAL);
    }

    @Test
    void groupsAClassUnderItsSuperClassAndLeavesARootClassUngrouped() {
        final Map<String, RdfDiagramNode> nodes = RdfGraphIndex.index(new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Dog", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.resource(PS + "Dog", RDFS_SUBCLASS_OF, PS + "Pet"),
                RdfTripleTO.resource(PS + "Pet", RDF_TYPE, OWL_CLASS)
        ), Map.of()));

        assertThat(nodes.get(PS + "Dog").typeLocalName()).as("Dog clusters under Pet").isEqualTo("Pet");
        assertThat(nodes.get(PS + "Pet").typeLocalName()).as("a root class has no group").isEmpty();
    }

    @Test
    void groupsAnIndividualUnderItsRdfType() {
        final Map<String, RdfDiagramNode> nodes = RdfGraphIndex.index(new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Buddy", RDF_TYPE, PS + "Dog")
        ), Map.of()));

        assertThat(nodes.get(PS + "Buddy").typeLocalName()).isEqualTo("Dog");
    }

    @Test
    void namesALiteralByItsPredicate() {
        final Map<String, RdfDiagramNode> nodes = RdfGraphIndex.index(new RdfGraphTO(List.of(
                RdfTripleTO.literal(PS + "Buddy", RDFS_LABEL, "Buddy")
        ), Map.of()));

        assertThat(nodes.get("literal:Buddy").typeLocalName()).isEqualTo("label");
        assertThat(nodes.get("literal:Buddy").name()).isEqualTo("Buddy");
    }

    @Test
    void fallsBackToTheLocalNameWhenThereIsNoLabel() {
        final Map<String, RdfDiagramNode> nodes = RdfGraphIndex.index(new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Dog", RDF_TYPE, OWL_CLASS)
        ), Map.of()));

        assertThat(nodes.get(PS + "Dog").name()).isEqualTo("Dog");
    }

    @Test
    void labelsANodeWithTheOntologyItCameFrom() {
        final Map<String, RdfDiagramNode> nodes = RdfGraphIndex.index(new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Dog", RDF_TYPE, OWL_CLASS)
        ), Map.of(), Map.of(PS + "Dog", "https://w3id.org/semanticarts/ontology/gistCore")));

        assertThat(nodes.get(PS + "Dog").ontology()).isEqualTo("gist");
    }

    @Test
    void dropsAnUnnamedNodeWhoseLocalNameLooksLikeAGeneratedId() {
        final Map<String, RdfDiagramNode> nodes = RdfGraphIndex.index(new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "ykj3OGFS_j2pB_5", RDFS_SUBCLASS_OF, PS + "Pet"),
                RdfTripleTO.resource(PS + "Pet", RDF_TYPE, OWL_CLASS)
        ), Map.of()));

        assertThat(nodes).doesNotContainKey(PS + "ykj3OGFS_j2pB_5");
        assertThat(nodes).containsKey(PS + "Pet");
    }

    @Test
    void keepsANamedNodeEvenWhenItsNameLooksMachineGenerated() {
        final Map<String, RdfDiagramNode> nodes = RdfGraphIndex.index(new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "x", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.literal(PS + "x", RDFS_LABEL, "B2B")
        ), Map.of()));

        assertThat(nodes.get(PS + "x").name()).isEqualTo("B2B");
    }

    @Test
    void blanksAClusterGroupThatIsOnlyAnOpaqueId() {
        final Map<String, RdfDiagramNode> nodes = RdfGraphIndex.index(new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Dog", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.resource(PS + "Dog", RDFS_SUBCLASS_OF, PS + "02kj3OGFS_j2pCNa")
        ), Map.of()));

        assertThat(nodes.get(PS + "Dog").typeLocalName()).isEmpty();
    }

    @Test
    void keepsAClusterGroupThatComesFromANamedParent() {
        final Map<String, RdfDiagramNode> nodes = RdfGraphIndex.index(new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Dog", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.resource(PS + "Dog", RDFS_SUBCLASS_OF, PS + "x"),
                RdfTripleTO.literal(PS + "x", RDFS_LABEL, "B2B")
        ), Map.of()));

        assertThat(nodes.get(PS + "Dog").typeLocalName()).isEqualTo("B2B");
    }
}

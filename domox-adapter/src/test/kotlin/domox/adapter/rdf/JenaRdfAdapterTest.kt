package domox.adapter.rdf

import domox.dom.rdf.RdfGraphDiagrams
import domox.dom.requirements.Corpora
import domox.dom.rules.TypedDependencyRule
import domox.port.RdfFormat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the RDF/OWL retrieval service against the bundled sample ontology
 * (`domox-domain/src/main/resources/petshop-ontology.ttl`): load → SPARQL → D3 JSON.
 *
 * <p>No containers and no network: everything runs against the classpath fixture, so this test is
 * part of the ordinary unit-test run.</p>
 */
class JenaRdfAdapterTest {

    private val rdf = JenaRdfAdapter()

    private companion object {
        const val SAMPLE = "petshop-ontology.ttl"
        const val PS = "http://example.org/petshop#"
    }

    @Test
    fun `loads the sample ontology with its prefixes`() {
        val graph = rdf.load(SAMPLE)

        assertTrue(graph.size() > 30, "expected a non-trivial graph, got ${graph.size()} triples")
        assertTrue(graph.prefixes().containsKey("ps"), "prefix declarations should be preserved")
        assertTrue(
            graph.triples().any { it.predicate() == "http://www.w3.org/2000/01/rdf-schema#subClassOf" },
            "the sample declares a sub-class hierarchy"
        )
        assertTrue(
            graph.triples().any { it.literal() && it.objectValue().toString().equals("Buddy the Golden Retriever") },
            "literals keep their lexical form"
        )
    }

    @Test
    fun `construct returns the requested sub-graph`() {
        val dogs = rdf.construct(
            SAMPLE,
            """
            PREFIX ps: <$PS>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            CONSTRUCT { ?dog rdf:type ps:Dog } WHERE { ?dog rdf:type ps:Dog }
            """.trimIndent()
        )

        assertEquals(1, dogs.size(), "the sample has exactly one dog")
        assertTrue(dogs.triples().single().subject().endsWith("#Buddy"))
    }

    @Test
    fun `select returns one ordered map per row`() {
        val rows = rdf.select(
            SAMPLE,
            """
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT ?pet ?label WHERE { ?pet rdfs:label ?label } ORDER BY ?label
            """.trimIndent()
        )

        assertFalse(rows.isEmpty())
        assertTrue(rows.all { it.containsKey("pet") && it.containsKey("label") })
        assertTrue(rows.any { it["label"] == "Whiskers the Siamese" })
    }

    @Test
    fun `serializes to turtle and json-ld`() {
        assertTrue(rdf.serialize(SAMPLE, RdfFormat.TURTLE).contains("subClassOf"))
        assertTrue(rdf.serialize(SAMPLE, RdfFormat.JSON_LD).trimStart().startsWith("{"))
    }

    @Test
    fun `the loaded graph renders as D3 JSON`() {
        val json = RdfGraphDiagrams().renderDiagram(rdf.load(SAMPLE))

        val root = JSONObject(json)                       // parsing proves the JSON is well-formed
        val nodes = root.getJSONArray("nodes")
        val links = root.getJSONArray("links")

        assertTrue(nodes.length() > 10, "expected many nodes, got ${nodes.length()}")
        assertTrue(links.length() > 10, "expected many links, got ${links.length()}")

        fun nodeEndingWith(suffix: String): JSONObject =
            (0 until nodes.length())
                .map { nodes.getJSONObject(it) }
                .single { it.getString("id").endsWith(suffix) }

        val buddy = nodeEndingWith("#Buddy")
        assertEquals("Buddy the Golden Retriever", buddy.getString("name"), "rdfs:label wins over the IRI")
        assertEquals("Dog", buddy.getString("subdomain"), "nodes are grouped by rdf:type")
        assertEquals("Individual", buddy.getString("pos"))

        val dog = nodeEndingWith("#Dog")
        assertEquals("Class", dog.getString("pos"))
        assertEquals("Pet", dog.getString("subdomain"), "sub-classOf makes Dog a class, grouped by type")

        // every RDF statement is an edge, labelled with the predicate's local name
        val labels = (0 until links.length()).map { links.getJSONObject(it).getString("label") }
        assertTrue(labels.contains("subClassOf"))
        assertTrue(labels.contains("owns"))

        assertEquals(3, root.getJSONArray("legend").length(),
            "the sample uses all three node kinds (Class, Individual, Literal)")
    }

    /**
     * End-to-end search: `Corpora.searchOntologyAsD3Graph` issues its two SPARQL queries through the
     * real adapter, so this proves the generated SPARQL is valid ARQ and that a match brings its
     * neighbourhood along (the individual *and* its `rdf:type` class).
     */
    @Test
    fun `search renders the matching neighbourhood as D3 JSON`() {
        val corpora = corporaWithRdf()

        val root = JSONObject(corpora.searchOntologyAsD3Graph("Buddy"))
        val nodes = root.getJSONArray("nodes")
        val ids = (0 until nodes.length()).map { nodes.getJSONObject(it).getString("id") }

        assertTrue(ids.any { it.endsWith("#Buddy") }, "the matched individual is a node: $ids")
        assertTrue(ids.any { it.endsWith("#Dog") }, "its rdf:type is one hop away and included: $ids")

        val links = root.getJSONArray("links")
        val labels = (0 until links.length()).map { links.getJSONObject(it).getString("label") }
        assertTrue(labels.contains("type"), "the rdf:type edge is kept: $labels")
    }

    /** A blank search term is the "show me everything" case. */
    @Test
    fun `blank search renders the whole ontology`() {
        val root = JSONObject(corporaWithRdf().ontologyAsD3Graph())

        assertTrue(root.getJSONArray("nodes").length() > 10,
            "expected the whole sample ontology, got ${root.getJSONArray("nodes").length()} nodes")
    }

    /**
     * A [Corpora] with only its RDF collaborators wired — enough to exercise the ontology search;
     * every other constructor argument is unused by `searchOntologyAsD3Graph`/`ontologyAsD3Graph`.
     */
    private fun corporaWithRdf(): Corpora =
        Corpora(null, null, null, null, emptyList<TypedDependencyRule>(), null, null, null, null,
            rdf, RdfGraphDiagrams())
}

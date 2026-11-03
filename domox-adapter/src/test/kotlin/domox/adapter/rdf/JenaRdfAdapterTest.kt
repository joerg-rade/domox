package domox.adapter.rdf

import com.sun.net.httpserver.HttpServer
import domox.dom.rdf.RdfGraphDiagrams
import domox.dom.rdf.RdfProperties
import domox.dom.requirements.Corpora
import domox.dom.rules.TypedDependencyRule
import domox.port.RdfFormat
import domox.port.RdfRemoteOptions
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.File
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/**
 * Guards the RDF/OWL retrieval service against the bundled sample ontology
 * (`domox-domain/src/main/resources/PetStore-ontology.ttl` — a **DBpedia export** of the
 * "Pet store" page and its incoming links): load → SPARQL → D3 JSON.
 *
 * <p>No containers and no network: everything runs against the classpath fixture, so this test is
 * part of the ordinary unit-test run.</p>
 *
 * <p>Shape of the fixture (it drives the assertions below): one hub, {@code dbr:Pet_store}, with
 * ~93 incoming {@code dbo:wikiPageWikiLink} edges, ~12 {@code owl:sameAs} translations,
 * {@code rdf:type owl:Thing}, multi-language {@code rdfs:label}s and one {@code dbo:description}
 * literal.  There is no {@code owl:Class}/{@code rdfs:subClassOf}, so only the <em>Individual</em> and
 * <em>Literal</em> node kinds occur.  {@code dbr:Fancy_rat} is a leaf: it links to the hub and nothing
 * links to it.</p>
 */
class JenaRdfAdapterTest {

    private val rdf = JenaRdfAdapter()

    private companion object {
        const val SAMPLE = "PetStore-ontology.ttl"
        const val DBR = "http://dbpedia.org/resource/"
        const val DBO = "http://dbpedia.org/ontology/"
        const val HUB = DBR + "Pet_store"
        const val LEAF = "Fancy_rat"
    }

    @Test
    fun `loads the sample ontology with its prefixes`() {
        val graph = rdf.load(SAMPLE)

        assertTrue(graph.size() > 100, "expected a non-trivial graph, got ${graph.size()} triples")
        assertTrue(graph.prefixes().containsKey("dbr"), "prefix declarations should be preserved")
        assertTrue(graph.prefixes().containsKey("dbo"), "prefix declarations should be preserved")
        assertTrue(
            graph.triples().count { it.predicate() == DBO + "wikiPageWikiLink" } > 50,
            "the export is a page-links graph: the hub has many incoming links"
        )
        assertTrue(
            graph.triples().any { it.literal() && it.predicate() == DBO + "description" },
            "literals keep their lexical form"
        )
    }

    @Test
    fun `construct returns the requested sub-graph`() {
        val incoming = rdf.construct(
            SAMPLE,
            """
            PREFIX dbo: <$DBO>
            CONSTRUCT { ?s dbo:wikiPageWikiLink <$HUB> }
            WHERE     { ?s dbo:wikiPageWikiLink <$HUB> }
            """.trimIndent()
        )

        assertTrue(incoming.size() > 50, "expected the hub's incoming links, got ${incoming.size()}")
        assertTrue(incoming.triples().all { it.objectValue() == HUB }, "only the requested sub-graph")
    }

    @Test
    fun `select returns one ordered map per row`() {
        val rows = rdf.select(
            SAMPLE,
            """
            PREFIX dbo: <$DBO>
            SELECT ?s ?o WHERE { ?s dbo:wikiPageWikiLink ?o } ORDER BY ?s
            """.trimIndent()
        )

        assertFalse(rows.isEmpty())
        assertTrue(rows.all { it.containsKey("s") && it.containsKey("o") })
        assertTrue(rows.any { it["o"] == HUB }, "the hub is among the link targets")

        val subjects = rows.mapNotNull { it["s"] }
        assertEquals(subjects.sorted(), subjects, "ORDER BY ?s is honoured")
    }

    @Test
    fun `serializes to turtle and json-ld`() {
        assertTrue(rdf.serialize(SAMPLE, RdfFormat.TURTLE).contains("wikiPageWikiLink"))
        assertTrue(rdf.serialize(SAMPLE, RdfFormat.JSON_LD).trimStart().startsWith("{"))
    }

    @Test
    fun `the loaded graph renders as D3 JSON`() {
        val json = RdfGraphDiagrams().renderDiagram(rdf.load(SAMPLE))

        val root = JSONObject(json)                       // parsing proves the JSON is well-formed
        val nodes = root.getJSONArray("nodes")
        val links = root.getJSONArray("links")

        assertTrue(nodes.length() > 50, "expected many nodes, got ${nodes.length()}")
        assertTrue(links.length() > 50, "expected many links, got ${links.length()}")

        fun nodeWithId(id: String): JSONObject =
            (0 until nodes.length())
                .map { nodes.getJSONObject(it) }
                .single { it.getString("id") == id }

        // the hub is an individual (rdf:type owl:Thing), so it is grouped by that type
        val hub = nodeWithId(HUB)
        assertEquals("Individual", hub.getString("pos"))
        assertEquals("Thing", hub.getString("subdomain"))

        // every RDF statement is an edge, labelled with the predicate's local name
        val labels = (0 until links.length()).map { links.getJSONObject(it).getString("label") }
        assertTrue(labels.contains("wikiPageWikiLink"))
        assertTrue(labels.contains("sameAs"))

        // the export declares no owl:Class / rdfs:subClassOf, so only two node kinds occur
        val legend = (0 until root.getJSONArray("legend").length())
            .map { root.getJSONArray("legend").getJSONObject(it).getString("label") }
        assertTrue(legend.contains("Individual"), "legend: $legend")
        assertTrue(legend.contains("Literal"), "legend: $legend")
    }

    /**
     * End-to-end search: `Corpora.ontologyAsD3Graph` issues its SPARQL through the real adapter, so
     * this proves the generated query is valid ARQ and that a match brings its neighbourhood along.
     * <p>{@code Fancy_rat} is a <em>leaf</em>: it links to the hub and nothing links to it, so its
     * neighbourhood is exactly one edge.</p>
     */
    @Test
    fun `search renders the matching neighbourhood as D3 JSON`() {
        val root = JSONObject(corporaWithRdf().ontologyAsD3Graph(LEAF))
        val ids = nodeIds(root)

        assertTrue(ids.contains(LEAF), "the matched resource is a node: $ids")
        assertTrue(ids.contains("Pet_store"), "the resource it links to is one hop away: $ids")

        val links = root.getJSONArray("links")
        val labels = (0 until links.length()).map { links.getJSONObject(it).getString("label") }
        assertTrue(labels.contains("wikiPageWikiLink"), "the edge is kept: $labels")
    }

    /**
     * A search must return ONLY the matched resource's neighbourhood — not the whole export.
     * <p>The export has ~120 nodes; searching the leaf {@code Fancy_rat} must yield that resource and
     * the hub it links to, and nothing else.</p>
     */
    @Test
    fun `search restricts the graph to the matching neighbourhood`() {
        val ids = nodeIds(JSONObject(corporaWithRdf().ontologyAsD3Graph(LEAF)))

        assertTrue(ids.contains(LEAF), "the match itself: $ids")
        assertTrue(ids.contains("Pet_store"), "the one hop it reaches: $ids")

        assertFalse(ids.contains("Tony_Silva"), "an unrelated page leaked in: $ids")
        assertFalse(ids.contains("Pixel_Chix"), "an unrelated page leaked in: $ids")
        assertTrue(ids.size <= 4, "expected a one-edge neighbourhood, got ${ids.size}: $ids")
    }

    /**
     * The focus query must resolve the **concept**, not every IRI/label *containing* the term.
     * Substring matching is what made a search for "Pet" also return "Petit" (and "PetShow",
     * "competition", …) — noise that buries the concept the user asked for.
     * <p>The word-boundary <em>fallback</em> is checked here too: it may match a multi-word label
     * ("Golden Retriever"), but still not "Petit" — there is no word boundary between "pet" and "it".</p>
     */
    @Test
    fun `focus query resolves the concept exactly, ignoring substring decoys`() {
        val decoys = """
            @prefix ps:   <http://example.org/petshop#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix owl:  <http://www.w3.org/2002/07/owl#> .

            ps:Pet           a owl:Class ; rdfs:label "Pet" .
            ps:Petit         a owl:Class ; rdfs:label "Petit" .
            ps:petit         a owl:Class ; rdfs:label "petit" .
            ps:PetShow       a owl:Class ; rdfs:label "Pet Show" .
            ps:GoldRetriever a owl:Class ; rdfs:label "Golden Retriever" .
        """.trimIndent()
        val file = File.createTempFile("focus-decoys-", ".ttl")
        file.writeText(decoys)
        try {
            assertEquals(listOf("http://example.org/petshop#Pet"), focusOf(file, Corpora.focusQuery("Pet")),
                "only the exact concept must resolve")

            val word = focusOf(file, Corpora.wordMatchQuery("Pet", ""))
            assertTrue(word.contains("http://example.org/petshop#Pet"), "$word")
            assertFalse(word.contains("http://example.org/petshop#Petit"),
                "the fallback must not match 'Petit' — there is no word boundary between pet/it: $word")
            assertFalse(word.contains("http://example.org/petshop#petit"), "$word")
            // NOTE: 'PetShow' (label "Pet Show") legitimately matches the *word* "Pet" — that is the
            // intended, forgiving behaviour of the fallback; it only runs when nothing matches exactly.

            assertEquals(
                listOf("http://example.org/petshop#GoldRetriever"),
                focusOf(file, Corpora.wordMatchQuery("Golden Retriever", "")),
                "a partial phrase resolves on word boundaries")
        } finally {
            file.delete()
        }
    }

    /** Runs a focus query against a local ontology and returns the distinct IRIs it resolved. */
    private fun focusOf(file: File, query: String): List<String> =
        rdf.select(file.absolutePath, query).mapNotNull { it["focus"] }.distinct()

    /** A blank search term is the "show me everything" case. */
    @Test
    fun `blank search renders the whole ontology`() {
        val root = JSONObject(corporaWithRdf().ontologyAsD3Graph())

        assertTrue(root.getJSONArray("nodes").length() > 50,
            "expected the whole export, got ${root.getJSONArray("nodes").length()} nodes")
    }

    /**
     * The *action* path: it must return a view model whose `diagram` property already carries the
     * rendered graph — that is what the Wicket viewer draws (an action returning a plain String
     * would be shown as text).
     */
    @Test
    fun `the search action returns a view model that renders the graph`() {
        val viewModel = corporaWithRdf().searchOntologyAsD3Graph(LEAF)

        assertEquals(LEAF, viewModel.searchTerm)
        assertTrue(viewModel.title().startsWith("Ontology:"), viewModel.title())

        assertTrue(nodeIds(JSONObject(viewModel.diagram)).contains(LEAF),
            "the graph is pre-rendered: ${viewModel.diagram.take(200)}")
    }

    /**
     * A [Corpora] with only its RDF collaborators wired — enough to exercise the ontology search;
     * every other constructor argument is unused by `searchOntologyAsD3Graph`/`ontologyAsD3Graph`.
     * The default [RdfProperties] keeps it in local (bundled ontology) mode.
     */
    private fun corporaWithRdf(properties: RdfProperties = RdfProperties()): Corpora =
        Corpora(null, null, null, null, emptyList<TypedDependencyRule>(), null, null, null, null,
            rdf, RdfGraphDiagrams(), properties)

    /**
     * `domox.rdf.local-resource` may point at a **file on disk**, so a deployment can swap ontologies
     * without rebuilding.  The adapter tries the classpath first and falls back to the file system.
     */
    @Test
    fun `local-resource may point at a file on disk`() {
        val custom = """
            @prefix dbr: <http://dbpedia.org/resource/> .
            @prefix dbo: <http://dbpedia.org/ontology/> .
            dbr:Custom_shop dbo:wikiPageWikiLink dbr:Custom_thing .
        """.trimIndent()
        val file = File.createTempFile("custom-ontology-", ".ttl")
        file.writeText(custom)
        try {
            val properties = RdfProperties().apply { localResource = file.absolutePath }

            val ids = nodeIds(JSONObject(corporaWithRdf(properties).ontologyAsD3Graph("Custom_shop")))

            assertTrue(ids.contains("Custom_shop"), "the file on disk is the source: $ids")
            assertTrue(ids.contains("Custom_thing"), "its neighbourhood comes along: $ids")
        } finally {
            file.delete()
        }
    }

    /**
     * `selectRemote` against a **local stub SPARQL endpoint** (JDK HttpServer): proves the adapter
     * really issues a SPARQL-protocol request and parses the result set.  No network, no container —
     * everything stays on 127.0.0.1.
     */
    @Test
    fun `selectRemote issues a SPARQL protocol request and parses the result set`() {
        val seen = AtomicReference("")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/sparql") { exchange ->
            val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            seen.set(if (body.isNotBlank()) body else exchange.requestURI.rawQuery.orEmpty())
            val payload = """
                {"head":{"vars":["focus"]},
                 "results":{"bindings":[
                   {"focus":{"type":"uri","value":"http://example.org/petshop#Buddy"}}]}}
            """.trimIndent().toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/sparql-results+json")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        try {
            val endpoint = "http://127.0.0.1:${server.address.port}/sparql"

            val rows = rdf.selectRemote(endpoint, "SELECT ?focus WHERE { ?focus a <http://x/Cat> }")

            assertEquals(1, rows.size)
            assertEquals("http://example.org/petshop#Buddy", rows.single()["focus"])
            assertTrue(seen.get().contains("SELECT"), "a SPARQL query must be sent: ${seen.get()}")
        } finally {
            server.stop(0)
        }
    }

    /** The configured `User-Agent` must actually be sent — Wikidata rejects anonymous requests. */
    @Test
    fun `selectRemote sends the configured user agent`() {
        val seenUserAgent = AtomicReference("")
        val server = stubEndpoint(delayMillis = 0, seenUserAgent = seenUserAgent)
        server.start()
        try {
            rdf.selectRemote(
                "http://127.0.0.1:${server.address.port}/sparql",
                "SELECT ?focus WHERE { ?focus ?p ?o }",
                RdfRemoteOptions(Duration.ofSeconds(5), "domox-test/1"))

            assertTrue(seenUserAgent.get().contains("domox-test/1"),
                "User-Agent must be sent, was '${seenUserAgent.get()}'")
        } finally {
            server.stop(0)
        }
    }

    /**
     * A slow endpoint must be abandoned at the configured timeout — without one, an overloaded
     * public endpoint (DBpedia regularly takes >60s) blocks the request thread indefinitely.
     */
    @Test
    fun `selectRemote honours the configured timeout`() {
        val server = stubEndpoint(delayMillis = 2_000, seenUserAgent = AtomicReference(""))
        server.start()
        try {
            val startedAt = System.nanoTime()
            try {
                rdf.selectRemote(
                    "http://127.0.0.1:${server.address.port}/sparql",
                    "SELECT ?focus WHERE { ?focus ?p ?o }",
                    RdfRemoteOptions(Duration.ofMillis(300), "domox-test/1"))
                fail("expected the request to be abandoned at the timeout")
            } catch (expected: RuntimeException) {
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                assertTrue(elapsedMs < 1_500, "must abort at the timeout, but took ${elapsedMs}ms")
            }
        } finally {
            server.stop(0)
        }
    }

    /** The local part of every node id (works for both `#fragment` and `/path` IRIs). */
    private fun nodeIds(root: JSONObject): List<String> {
        val nodes = root.getJSONArray("nodes")
        return (0 until nodes.length())
            .map { nodes.getJSONObject(it).getString("id") }
            .map { it.substringAfterLast('#').substringAfterLast('/') }
    }

    /** A SPARQL-protocol stub endpoint; optionally slow, and records the `User-Agent` it received. */
    private fun stubEndpoint(delayMillis: Long, seenUserAgent: AtomicReference<String>): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/sparql") { exchange ->
            seenUserAgent.set(exchange.requestHeaders.getFirst("User-Agent").orEmpty())
            if (delayMillis > 0) Thread.sleep(delayMillis)
            val payload = """{"head":{"vars":["focus"]},"results":{"bindings":[]}}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/sparql-results+json")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        return server
    }
}

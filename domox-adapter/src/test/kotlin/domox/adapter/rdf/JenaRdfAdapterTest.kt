package domox.adapter.rdf

import com.sun.net.httpserver.HttpServer
import domox.dom.rdf.OntologyQueries
import domox.dom.rdf.RdfGraphDiagrams
import domox.dom.rdf.RdfProperties
import domox.dom.rdf.UmlLayoutDiagrams
import domox.dom.requirements.OntologyDiagrams
import domox.port.RdfFormat
import domox.port.RdfRemoteOptions
import org.json.JSONObject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.File
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/**
 * Guards the RDF/OWL retrieval service against the bundled classpath fixtures
 * (`domox-domain/src/main/resources/`): load → SPARQL → D3 JSON.
 *
 * <p>No containers and no network: everything runs against the classpath, so this test is part of
 * the ordinary unit-test run.</p>
 *
 * <p><b>Two fixtures, one per class vocabulary the mapper understands</b> — the diagram has to work
 * for both a knowledge graph and a UML model:</p>
 *
 * <ul>
 *   <li>[SAMPLE] — `PetStore-ontology.ttl`, a **DBpedia export** of the "Pet store" page and its
 *       incoming links.  One hub, `dbr:Pet_store`, with ~93 incoming `dbo:wikiPageWikiLink` edges,
 *       ~12 `owl:sameAs` translations, `rdf:type owl:Thing`, multi-language `rdfs:label`s and one
 *       `dbo:description` literal.  There is no `owl:Class`/`rdfs:subClassOf`, so only the
 *       *Individual* and *Literal* node kinds occur.  `dbr:Fancy_rat` is a leaf: it links to the hub
 *       and nothing links to it.</li>
 *   <li>[ONTOUML] — `ontology.ttl`, a **UML/OntoUML** model export: classes typed `ontouml:Class`,
 *       named with `ontouml:name` (it carries no `rdfs:label` at all), generalization reified as an
 *       `ontouml:Generalization` resource (`ontouml:general` → parent, `ontouml:specific` → child),
 *       plus diagram *layout* statements (`ontouml:Point`/`Rectangle`/`Path` and coordinates).</li>
 * </ul>
 */
class JenaRdfAdapterTest {

    private val rdf = JenaRdfAdapter()

    private companion object {
        /** The DBpedia export: individuals and literals only, no classes. */
        const val SAMPLE = "PetStore-ontology.ttl"

        /** The UML/OntoUML model export: `ontouml:Class` + `ontouml:name`, reified generalization. */
        const val ONTOUML = "ontology.ttl"

        const val DBR = "http://dbpedia.org/resource/"
        const val DBO = "http://dbpedia.org/ontology/"
        const val HUB = DBR + "Pet_store"
        const val LEAF = "Fancy_rat"

        const val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        const val OU = "https://w3id.org/ontouml#"

        /**
         * A concept of the OntoUML fixture, resolvable only through `ontouml:name` — exactly one
         * resource carries it, so its neighbourhood is small and its assertions are stable.
         */
        const val ONTOUML_CONCEPT = "Commitment"
    }

    @Test
    fun `loads the sample ontology with its prefixes`() {
        val graph = rdf.load(SAMPLE)

        assertThat(graph.size() > 100).describedAs("expected a non-trivial graph, got ${graph.size()} triples").isTrue()
        assertThat(graph.prefixes().containsKey("dbr")).describedAs("prefix declarations should be preserved").isTrue()
        assertThat(graph.prefixes().containsKey("dbo")).describedAs("prefix declarations should be preserved").isTrue()
        assertThat(graph.triples().count { it.predicate() == DBO + "wikiPageWikiLink" } > 50)
            .describedAs("the export is a page-links graph: the hub has many incoming links").isTrue()
        assertThat(graph.triples().any { it.literal() && it.predicate() == DBO + "description" })
            .describedAs("literals keep their lexical form").isTrue()
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

        assertThat(incoming.size() > 50).describedAs("expected the hub's incoming links, got ${incoming.size()}").isTrue()
        assertThat(incoming.triples().all { it.objectValue() == HUB }).describedAs("only the requested sub-graph").isTrue()
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

        assertThat(rows.isEmpty()).isFalse()
        assertThat(rows.all { it.containsKey("s") && it.containsKey("o") }).isTrue()
        assertThat(rows.any { it["o"] == HUB }).describedAs("the hub is among the link targets").isTrue()

        val subjects = rows.mapNotNull { it["s"] }
        assertThat(subjects).describedAs("ORDER BY ?s is honoured").isEqualTo(subjects.sorted())
    }

    @Test
    fun `serializes to turtle and json-ld`() {
        assertThat(rdf.serialize(SAMPLE, RdfFormat.TURTLE).contains("wikiPageWikiLink")).isTrue()
        assertThat(rdf.serialize(SAMPLE, RdfFormat.JSON_LD).trimStart().startsWith("{")).isTrue()
    }

    @Test
    fun `the loaded graph renders as D3 JSON`() {
        val json = RdfGraphDiagrams().renderDiagram(rdf.load(SAMPLE))

        val root = JSONObject(json)                       // parsing proves the JSON is well-formed
        val nodes = root.getJSONArray("nodes")
        val links = root.getJSONArray("links")

        assertThat(nodes.length() > 50).describedAs("expected many nodes, got ${nodes.length()}").isTrue()
        assertThat(links.length() > 50).describedAs("expected many links, got ${links.length()}").isTrue()

        fun nodeWithId(id: String): JSONObject =
            (0 until nodes.length())
                .map { nodes.getJSONObject(it) }
                .single { it.getString("id") == id }

        // the hub is an individual (rdf:type owl:Thing), so it is grouped by that type
        val hub = nodeWithId(HUB)
        assertThat(hub.getString("pos")).isEqualTo("Individual")
        assertThat(hub.getString("subdomain")).isEqualTo("Thing")

        // every RDF statement is an edge, labelled with the predicate's local name
        val labels = (0 until links.length()).map { links.getJSONObject(it).getString("label") }
        assertThat(labels.contains("wikiPageWikiLink")).isTrue()
        assertThat(labels.contains("sameAs")).isTrue()

        // the export declares no owl:Class / rdfs:subClassOf, so only two node kinds occur
        val legend = (0 until root.getJSONArray("legend").length())
            .map { root.getJSONArray("legend").getJSONObject(it).getString("label") }
        assertThat(legend.contains("Individual")).describedAs("legend: $legend").isTrue()
        assertThat(legend.contains("Literal")).describedAs("legend: $legend").isTrue()
    }

    /**
     * End-to-end search: `OntologyDiagrams.ontologyAsD3Graph` issues its SPARQL through the real adapter, so
     * this proves the generated query is valid ARQ and that a match brings its neighbourhood along.
     * <p>{@code Fancy_rat} is a <em>leaf</em>: it links to the hub and nothing links to it, so its
     * neighbourhood is exactly one edge.</p>
     */
    @Test
    fun `search renders the matching neighbourhood as D3 JSON`() {
        val root = JSONObject(ontologyDiagramsWith(dbpediaFixture()).ontologyAsD3Graph(LEAF))
        val ids = nodeIds(root)

        assertThat(ids.contains(LEAF)).describedAs("the matched resource is a node: $ids").isTrue()
        assertThat(ids.contains("Pet_store")).describedAs("the resource it links to is one hop away: $ids").isTrue()

        val links = root.getJSONArray("links")
        val labels = (0 until links.length()).map { links.getJSONObject(it).getString("label") }
        assertThat(labels.contains("wikiPageWikiLink")).describedAs("the edge is kept: $labels").isTrue()
    }

    /**
     * A search must return ONLY the matched resource's neighbourhood — not the whole export.
     * <p>The export has ~120 nodes; searching the leaf {@code Fancy_rat} must yield that resource and
     * the hub it links to, and nothing else.</p>
     */
    @Test
    fun `search restricts the graph to the matching neighbourhood`() {
        val ids = nodeIds(JSONObject(ontologyDiagramsWith(dbpediaFixture()).ontologyAsD3Graph(LEAF)))

        assertThat(ids.contains(LEAF)).describedAs("the match itself: $ids").isTrue()
        assertThat(ids.contains("Pet_store")).describedAs("the one hop it reaches: $ids").isTrue()

        assertThat(ids.contains("Tony_Silva")).describedAs("an unrelated page leaked in: $ids").isFalse()
        assertThat(ids.contains("Pixel_Chix")).describedAs("an unrelated page leaked in: $ids").isFalse()
        assertThat(ids.size <= 4).describedAs("expected a one-edge neighbourhood, got ${ids.size}: $ids").isTrue()
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
            assertThat(focusOf(file, OntologyQueries.focusQuery("Pet")))
                .describedAs("only the exact concept must resolve")
                .isEqualTo(listOf("http://example.org/petshop#Pet"))

            val word = focusOf(file, OntologyQueries.wordMatchQuery("Pet", ""))
            assertThat(word.contains("http://example.org/petshop#Pet")).describedAs("$word").isTrue()
            assertThat(word.contains("http://example.org/petshop#Petit"))
                .describedAs("the fallback must not match 'Petit' — there is no word boundary between pet/it: $word")
                .isFalse()
            assertThat(word.contains("http://example.org/petshop#petit")).describedAs("$word").isFalse()
            // NOTE: 'PetShow' (label "Pet Show") legitimately matches the *word* "Pet" — that is the
            // intended, forgiving behaviour of the fallback; it only runs when nothing matches exactly.

            assertThat(focusOf(file, OntologyQueries.wordMatchQuery("Golden Retriever", "")))
                .describedAs("a partial phrase resolves on word boundaries")
                .isEqualTo(listOf("http://example.org/petshop#GoldRetriever"))
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
        val root = JSONObject(ontologyDiagramsWith().ontologyAsD3Graph())

        assertThat(root.getJSONArray("nodes").length() > 50)
            .describedAs("expected the whole export, got ${root.getJSONArray("nodes").length()} nodes").isTrue()
    }

    /**
     * The *action* path: it must return a view model whose `diagram` property already carries the
     * rendered graph — that is what the Wicket viewer draws (an action returning a plain String
     * would be shown as text).
     */
    @Test
    fun `the search action returns a view model that renders the graph`() {
        val viewModel = ontologyDiagramsWith(dbpediaFixture()).searchOntologyAsD3Graph(LEAF)

        assertThat(viewModel.searchTerm).isEqualTo(LEAF)
        assertThat(viewModel.title().startsWith("Ontology:")).describedAs(viewModel.title()).isTrue()

        assertThat(nodeIds(JSONObject(viewModel.diagram)).contains(LEAF))
            .describedAs("the graph is pre-rendered: ${viewModel.diagram.take(200)}").isTrue()
    }

    // -- the UML/OntoUML vocabulary -------------------------------------------

    @Test
    fun `loads the OntoUML model with its classes`() {
        val graph = rdf.load(ONTOUML)

        assertThat(graph.size() > 1_000).describedAs("expected the whole model, got ${graph.size()} triples").isTrue()
        assertThat(graph.prefixes().containsKey("ontouml")).describedAs("prefix declarations should be preserved").isTrue()
        assertThat(graph.triples().count { it.predicate() == RDF_TYPE && it.objectValue() == OU + "Class" } > 100)
            .describedAs("a UML model types its classes as ontouml:Class").isTrue()
        assertThat(graph.triples().none {
            it.predicate() == RDF_TYPE && it.objectValue() == "http://www.w3.org/2002/07/owl#Class"
        }).describedAs("…and declares no owl:Class at all — which is exactly why the mapper needs the ontouml: rules").isTrue()
    }

    /**
     * The point of the OntoUML support: `ontouml:Class` becomes a **Class** node labelled with its
     * `ontouml:name`.  Without those rules this model renders as opaque tool IDs (`:0Ckj3OGFS_j2pB5A`)
     * with no class kind at all.
     */
    @Test
    fun `an OntoUML model renders classes with their human names`() {
        val root = JSONObject(RdfGraphDiagrams().renderDiagram(rdf.load(ONTOUML)))

        val concept = nodesNamed(root, ONTOUML_CONCEPT)
        assertThat(concept.isNotEmpty()).describedAs("ontouml:name must become the node label").isTrue()
        // the concept is a *Class* node carrying its human name and clustering under its parent's name
        // (the same name also appears as a Literal node — every statement becomes an edge)
        val klass = concept.singleOrNull { it.getString("pos") == "Class" }
        assertThat(klass != null).describedAs("an ontouml:Class must be a Class node: $concept").isTrue()
        assertThat(klass!!.getString("subdomain").isNotBlank())
            .describedAs("it clusters under the name of its super-class: $klass").isTrue()

        val legend = (0 until root.getJSONArray("legend").length())
            .map { root.getJSONArray("legend").getJSONObject(it).getString("label") }
        assertThat(legend.contains("Class")).describedAs("legend: $legend").isTrue()
    }

    /**
     * End-to-end over the UML fixture: `OntologyDiagrams.ontologyAsD3Graph` must resolve a concept named
     * *only* by `ontouml:name` (the file carries no `rdfs:label`), and bring its neighbourhood along
     * — but only its neighbourhood, not the whole 15 000-statement model.
     */
    @Test
    fun `search resolves an OntoUML concept by its ontouml name`() {
        val root = JSONObject(ontologyDiagramsWith().ontologyAsD3Graph(ONTOUML_CONCEPT))
        val names = nodeNames(root)

        assertThat(names.contains(ONTOUML_CONCEPT)).describedAs("the named concept is a node: $names").isTrue()
        assertThat(root.getJSONArray("links").length() > 0).describedAs("its neighbourhood must bring edges along").isTrue()
        assertThat(names.size < 100).describedAs("a one-concept neighbourhood, not the whole model: ${names.size}").isTrue()
    }

    /**
     * The DBpedia fixture as the configured local source.  The *default* local source is the
     * OntoUML model (`ontology.ttl`), so the page-links assertions must opt in to their own fixture.
     */
    private fun dbpediaFixture(): RdfProperties = RdfProperties().apply { localResource = SAMPLE }

    /**
     * An [OntologyDiagrams] with its RDF collaborators wired — enough to exercise the ontology search.
     * The default [RdfProperties] keeps it in local (bundled ontology) mode.
     */
    private fun ontologyDiagramsWith(properties: RdfProperties = RdfProperties()): OntologyDiagrams =
        OntologyDiagrams(rdf, RdfGraphDiagrams(), UmlLayoutDiagrams(), properties)

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

            val ids = nodeIds(JSONObject(ontologyDiagramsWith(properties).ontologyAsD3Graph("Custom_shop")))

            assertThat(ids.contains("Custom_shop")).describedAs("the file on disk is the source: $ids").isTrue()
            assertThat(ids.contains("Custom_thing")).describedAs("its neighbourhood comes along: $ids").isTrue()
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

            assertThat(rows.size).isEqualTo(1)
            assertThat(rows.single()["focus"]).isEqualTo("http://example.org/petshop#Buddy")
            assertThat(seen.get().contains("SELECT")).describedAs("a SPARQL query must be sent: ${seen.get()}").isTrue()
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

            assertThat(seenUserAgent.get().contains("domox-test/1"))
                .describedAs("User-Agent must be sent, was '${seenUserAgent.get()}'").isTrue()
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
                assertThat(elapsedMs < 1_500).describedAs("must abort at the timeout, but took ${elapsedMs}ms").isTrue()
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

    /** Every node, for the name/kind assertions — an OntoUML model's ids are opaque tool IDs. */
    private fun nodeObjects(root: JSONObject): List<JSONObject> {
        val nodes = root.getJSONArray("nodes")
        return (0 until nodes.length()).map { nodes.getJSONObject(it) }
    }

    /** The `name` of every node — the human label the client draws. */
    private fun nodeNames(root: JSONObject): List<String> =
        nodeObjects(root).map { it.getString("name") }

    /** The nodes carrying `name`; an OntoUML model has no readable id to match on instead. */
    private fun nodesNamed(root: JSONObject, name: String): List<JSONObject> =
        nodeObjects(root).filter { it.getString("name") == name }

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

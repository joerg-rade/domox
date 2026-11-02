package domox.adapter.rdf

import domox.port.RdfFormat
import domox.port.RdfGraphTO
import domox.port.RdfPort
import domox.port.RdfTripleTO
import org.apache.jena.query.QueryExecution
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFLanguages
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Adapter implementation of [RdfPort] backed by **Apache Jena**.
 *
 * <p>Retrieves RDF/OWL data from a classpath resource, a file, or a remote SPARQL endpoint, runs
 * SPARQL over it, and maps the result into the domain-safe [RdfGraphTO] — no Jena type ever
 * crosses the port boundary.</p>
 *
 * <p><b>Typical use</b> (ontology exploration, feeding the D3 diagram):</p>
 * <pre>
 *   val graph  = rdfPort.load("petshop-ontology.ttl")                        // the whole ontology
 *   val dogs   = rdfPort.construct("petshop-ontology.ttl", constructQuery)   // just the dogs
 *   val rows   = rdfPort.select("petshop-ontology.ttl", selectQuery)         // tabular output
 *   val turtle = rdfPort.serialize("petshop-ontology.ttl", RdfFormat.TURTLE) // re-serialize
 * </pre>
 *
 * <p>Registered as a `@Component`: `DomainModule` component-scans package `domox`, so the adapter
 * beans are picked up without further wiring (guarded by `HexagonalPortWiringIntegTest`).</p>
 */
@Component
class JenaRdfAdapter : RdfPort {

    override fun load(location: String): RdfGraphTO =
        readModel(location).toGraphTO()

    override fun construct(location: String, sparql: String): RdfGraphTO =
        QueryExecution.create()
            .query(sparql)
            .model(readModel(location))
            .build()
            .use { it.execConstruct().toGraphTO() }

    override fun select(location: String, sparql: String): List<Map<String, String>> =
        runSelect(
            QueryExecution.create()
                .query(sparql)
                .model(readModel(location))
                .build()
        )

    override fun selectRemote(endpointUrl: String, sparql: String): List<Map<String, String>> =
        runSelect(
            QueryExecution.service(endpointUrl)
                .query(sparql)
                .build()
        )

    override fun serialize(location: String, format: RdfFormat): String {
        val out = ByteArrayOutputStream()
        RDFDataMgr.write(out, readModel(location), langOf(format))
        return out.toString(StandardCharsets.UTF_8)
    }

    // -- helpers -------------------------------------------------------------

    /**
     * Reads an RDF document into a Jena [Model].  `location` is either a classpath resource
     * (optionally prefixed with `classpath:`) or a file path; the syntax is derived from the
     * file extension, defaulting to Turtle.
     */
    private fun readModel(location: String): Model {
        val name = location.removePrefix("classpath:")
        val lang = RDFLanguages.filenameToLang(name) ?: Lang.TURTLE
        val model = ModelFactory.createDefaultModel()
        resourceStream(name).use { RDFDataMgr.read(model, it, lang) }
        return model
    }

    private fun resourceStream(name: String): InputStream {
        val loader = javaClass.classLoader
        (loader.getResourceAsStream(name) ?: loader.getResourceAsStream("/$name"))?.let { return it }
        val file = File(name)
        if (file.isFile) return FileInputStream(file)
        throw IllegalArgumentException(
            "RDF source not found on the classpath or as a file: $name"
        )
    }

    private fun Model.toGraphTO(): RdfGraphTO {
        val triples = ArrayList<RdfTripleTO>()
        val statements = listStatements()
        while (statements.hasNext()) {
            val st = statements.next()
            val obj = st.`object`
            triples += if (obj.isLiteral) {
                RdfTripleTO.literal(st.subject.term(), st.predicate.uri, obj.asLiteral().lexicalForm)
            } else {
                RdfTripleTO.resource(st.subject.term(), st.predicate.uri, obj.asResource().term())
            }
        }
        return RdfGraphTO(triples, nsPrefixMap)
    }

    private fun runSelect(qexec: QueryExecution): List<Map<String, String>> =
        qexec.use { q ->
            val rows = ArrayList<Map<String, String>>()
            val resultSet = q.execSelect()
            while (resultSet.hasNext()) {
                val solution = resultSet.next()
                val row = LinkedHashMap<String, String>()
                for (varName in solution.varNames()) {
                    solution.get(varName)?.let { row[varName] = lexical(it) }
                }
                rows += row
            }
            rows
        }

    /** IRI when the node is a resource, the lexical form when it is a literal. */
    private fun lexical(node: RDFNode): String =
        if (node.isLiteral) node.asLiteral().lexicalForm else node.asResource().term()

    /** IRI of a resource, or a synthetic `_:label` for a blank node. */
    private fun Resource.term(): String =
        if (isURIResource) uri else "_:${id.labelString}"

    private fun langOf(format: RdfFormat): Lang = when (format) {
        RdfFormat.TURTLE -> Lang.TURTLE
        RdfFormat.RDF_XML -> Lang.RDFXML
        RdfFormat.JSON_LD -> Lang.JSONLD
        RdfFormat.N_TRIPLES -> Lang.NTRIPLES
    }
}

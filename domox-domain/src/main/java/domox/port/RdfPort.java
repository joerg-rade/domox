package domox.port;

import java.util.List;
import java.util.Map;

/**
 * Port for retrieving <b>RDF/OWL</b> data and turning it into something the domain (and, via
 * {@code RdfGraphDiagrams}, the D3 diagram) can consume.
 *
 * <p>Implemented in the adapter module by Apache Jena ({@code domox.adapter.rdf.JenaRdfAdapter}).
 * The domain must never see Jena types: the port speaks in {@link RdfGraphTO} /
 * {@link RdfTripleTO} / {@link RdfFormat} only.</p>
 *
 * <p><b>Sources.</b> {@code location} is a classpath resource ({@code classpath:petshop-ontology.ttl})
 * or a file path; the RDF syntax is taken from the extension ({@code .ttl}, {@code .owl},
 * {@code .rdf}, {@code .nt}, {@code .jsonld}).  Remote graphs are addressed by SPARQL endpoint URL.</p>
 *
 * <p><b>Why {@code construct} matters here.</b> A SPARQL {@code CONSTRUCT} returns an RDF
 * <em>graph</em> rather than a result table, so it is the natural way to say "show me this part of
 * the ontology" — the returned triples map one-to-one onto the D3 node/link structure.</p>
 */
public interface RdfPort {

    /**
     * Loads an RDF graph from a classpath resource or file.
     *
     * @param location classpath resource (with or without a {@code classpath:} prefix) or file path
     */
    RdfGraphTO load(String location);

    /**
     * Runs a SPARQL {@code CONSTRUCT} (or {@code DESCRIBE}) over the graph at {@code location}
     * and returns the resulting sub-graph.
     */
    RdfGraphTO construct(String location, String sparql);

    /**
     * Runs a SPARQL {@code SELECT} over the graph at {@code location}.
     *
     * @return one ordered map per row: variable name → lexical value (missing bindings omitted)
     */
    List<Map<String, String>> select(String location, String sparql);

    /**
     * Runs a SPARQL {@code SELECT} against a remote SPARQL endpoint (e.g. DBpedia, Wikidata).
     */
    List<Map<String, String>> selectRemote(String endpointUrl, String sparql);

    /**
     * Serializes the graph at {@code location} in the requested syntax.
     */
    String serialize(String location, RdfFormat format);
}

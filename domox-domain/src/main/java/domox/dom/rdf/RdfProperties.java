package domox.dom.rdf;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the RDF/OWL graph source behind {@code Corpora}'s ontology diagram.
 *
 * <pre>
 * domox:
 *   rdf:
 *     endpoint-url: https://dbpedia.org/sparql   # blank (default) = bundled classpath ontology
 *     query-limit: 200
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "domox.rdf")
@Getter
@Setter
public class RdfProperties {

    /**
     * SPARQL endpoint the ontology diagram is queried from.
     * <p>
     * Blank (the default) keeps everything local: the bundled classpath ontology
     * ({@code ontology.ttl}) is queried through {@code RdfPort#select}/{@code construct}.
     * When set, the focus query and the triple fetch are issued against this endpoint through
     * {@code RdfPort#selectRemote} instead.
     */
    private String endpointUrl = "";

    /**
     * Local graph source used while {@link #getEndpointUrl() endpoint-url} is blank: a classpath
     * resource name (e.g. {@code ontology.ttl}) or a filesystem path (e.g.
     * {@code /opt/ontologies/petshop.ttl}).
     * <p>
     * Blank (the default) uses the ontology bundled in {@code domox-domain}'s resources.  Override it
     * to point at your own ontology without rebuilding — the adapter tries the classpath first and
     * falls back to the file system, so both forms work with the same setting.
     */
    private String localResource = "";

    /**
     * Row cap applied to the queries sent to {@link #getEndpointUrl() the endpoint}.
     * <p>
     * A public endpoint (DBpedia, Wikidata, …) holds billions of triples, so an unbounded
     * "give me everything" query would neither finish nor be polite.  Ignored in local mode.
     */
    private int queryLimit = 200;

    /**
     * Per-request timeout for endpoint queries, in seconds.
     * <p>
     * Without one, a slow or overloaded endpoint blocks the request thread for as long as it feels
     * like taking (DBpedia is regularly unreachable for a minute or more).  Ignored in local mode.
     */
    private int queryTimeoutSeconds = 20;

    /**
     * Value of the {@code User-Agent} header sent to the endpoint.
     * <p>
     * Several public endpoints — Wikidata in particular — reject requests that do not identify
     * themselves.  Blank leaves Jena's default.
     */
    private String userAgent = "domox-ontology-diagram/0.1";

    /**
     * Language tag that {@code rdfs:label} / {@code skos:prefLabel} matches must carry, e.g. {@code en}.
     * <p>
     * Blank (the default) accepts any label.  When set, labels in that language <em>or untagged</em>
     * labels are accepted, so an ontology with mixed tagging stays searchable.
     */
    private String labelLanguage = "";

    /** Whether a remote endpoint is configured. */
    public boolean isRemote() {
        return endpointUrl != null && !endpointUrl.isBlank();
    }
}

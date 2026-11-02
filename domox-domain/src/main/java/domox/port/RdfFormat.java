package domox.port;

/**
 * RDF serialization syntaxes the {@link RdfPort} can write.
 */
public enum RdfFormat {

    /** Turtle — the most readable RDF syntax (Jena's {@code "TURTLE"}). */
    TURTLE("TURTLE", "ttl"),

    /** RDF/XML — the original W3C syntax (Jena's {@code "RDF/XML"}). */
    RDF_XML("RDF/XML", "rdf"),

    /** JSON-LD — JSON-friendly RDF (Jena's {@code "JSON-LD"}). */
    JSON_LD("JSON-LD", "jsonld"),

    /** N-Triples — one statement per line (Jena's {@code "N-TRIPLES"}). */
    N_TRIPLES("N-TRIPLES", "nt");

    /** The language name Jena's {@code RDFDataMgr}/{@code Model#write} expects. */
    public final String jenaLang;

    /** Conventional file extension, e.g. for a {@code Blob} download. */
    public final String fileExtension;

    RdfFormat(final String jenaLang, final String fileExtension) {
        this.jenaLang = jenaLang;
        this.fileExtension = fileExtension;
    }
}

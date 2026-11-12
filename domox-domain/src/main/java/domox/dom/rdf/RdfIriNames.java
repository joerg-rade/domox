package domox.dom.rdf;

/**
 * Naming helpers for RDF/OWL IRIs and the human-readable names a diagram prints for them.
 * <p>
 * Split out of {@code RdfGraphDiagrams}, which had grown to own the IRI naming rules, the node
 * indexing <em>and</em> the JSON emission; the three are used from several places
 * ({@link DiagramIndex}, {@link OntologyTerms}, the RDF diagram itself), so they belong to a class of
 * their own rather than to whichever class happened to define them first.
 * <p>
 * Static and free of any framework dependency, so it is unit-testable on its own.
 */
public final class RdfIriNames {

    private RdfIriNames() {
    }

    /**
     * A short, human-friendly name for the reference ontology a graph IRI denotes — the label the
     * diagram prints on a node's fourth line ({@code gist}, {@code BFO}, {@code Schema.org}, …).
     *
     * <p>Falls back to the graph IRI's local name for an ontology we do not know, and to an empty
     * string when there is no attribution at all (a local file, or a single-default-graph endpoint) —
     * in which case the client simply omits the line.</p>
     */
    public static String ontologyLabel(final String graphIri) {
        if (graphIri == null || graphIri.isBlank()) {
            return "";
        }
        return switch (graphIri) {
            case "https://schema.org/" -> "Schema.org";
            case "http://purl.obolibrary.org/obo/bfo.owl" -> "BFO";
            case "https://w3id.org/semanticarts/ontology/gistCore" -> "gist";
            case "http://BPMNbasedOntology" -> "BBO";
            case "https://ntimes404.github.io/sBPMN/" -> "sBPMN";
            default -> localName(graphIri);
        };
    }

    /** Local name of an IRI: after {@code #}, else after the last {@code /}. */
    public static String localName(final String iri) {
        if (iri == null || iri.isEmpty()) {
            return "";
        }
        final int hash = iri.lastIndexOf('#');
        if (hash >= 0 && hash + 1 < iri.length()) {
            return iri.substring(hash + 1);
        }
        final int slash = iri.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < iri.length()) {
            return iri.substring(slash + 1);
        }
        return iri;
    }

    /**
     * Whether {@code label} is an opaque machine identifier rather than a human-readable name — the
     * discriminator the diagram uses to drop the noise nodes a UML/OntoUML tool leaves behind
     * (e.g. {@code ykj3OGFS_j2pB_5}, {@code 02kj3OGFS_j2pCNa_path_point_0}, a UUID).
     *
     * <p>A name is considered human when it <b>contains whitespace</b> ({@code "Feather Cat Toy"},
     * {@code "Sale 4711"}, {@code "Social Relator"}) or when letters and digits are <b>not
     * interleaved</b>: a plain word ({@code Pet}, {@code actions}, {@code cl_hiredprovider}), a
     * leading/trailing number ({@code "1st Order Entities"}, {@code AssociationClass1}) or a pure
     * number ({@code 507}, {@code 24.90}) all stay.  It is opaque when <b>letters and digits alternate
     * at least twice</b> — the signature of a generated id — which every example above shares, while
     * the human names do not.</p>
     *
     * <p>Consulted only for resources that have <em>no</em> name of their own: a resource with an
     * {@code rdfs:label} / {@code ontouml:name} is kept whatever that name looks like, so a real
     * {@code B2B} or {@code H1N1} is never mistaken for a generated id.</p>
     */
    public static boolean looksLikeOpaqueId(final String label) {
        if (label == null) {
            return true;
        }
        final String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (trimmed.chars().anyMatch(Character::isWhitespace)) {
            return false;   // a phrase of words, however short ("A", "Based on")
        }
        int alternations = 0;
        for (int i = 1; i < trimmed.length(); i++) {
            final char previous = trimmed.charAt(i - 1);
            final char current = trimmed.charAt(i);
            if (Character.isLetterOrDigit(previous) && Character.isLetterOrDigit(current)
                    && Character.isDigit(previous) != Character.isDigit(current)) {
                alternations++;
            }
        }
        return alternations >= 2;
    }
}

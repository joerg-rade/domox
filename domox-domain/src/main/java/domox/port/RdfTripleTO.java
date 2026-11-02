package domox.port;

/**
 * One RDF statement — subject → predicate → object.
 *
 * @param subject     the subject IRI (an RDF resource)
 * @param predicate   the predicate IRI
 * @param objectValue the object: an IRI when {@code literal} is {@code false}, otherwise the
 *                    literal's lexical form (e.g. {@code "Buddy the Golden Retriever"})
 * @param literal     {@code true} when the object is an RDF literal rather than a resource
 *
 * <p>Named {@code objectValue} rather than {@code object} deliberately: RDF calls the third term
 * the <i>object</i>, but {@code object} is a Kotlin keyword, so {@code triple.object} cannot even
 * be written in Kotlin (the adapter module's language).</p>
 */
public record RdfTripleTO(String subject, String predicate, String objectValue, boolean literal) {

    /** Convenience factory for a resource-to-resource statement. */
    public static RdfTripleTO resource(final String subject, final String predicate, final String objectIri) {
        return new RdfTripleTO(subject, predicate, objectIri, false);
    }

    /** Convenience factory for a statement whose object is a literal. */
    public static RdfTripleTO literal(final String subject, final String predicate, final String lexicalForm) {
        return new RdfTripleTO(subject, predicate, lexicalForm, true);
    }
}

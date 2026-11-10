package domox.webapp.mcp;

import domox.dom.rdf.OntologyTerm;

import java.util.List;

/**
 * A serializable view of one ontology term for an LLM reviewer.
 *
 * @param graph      the named graph (vocabulary) the term was found in, e.g. {@code https://schema.org/}
 * @param iri        the term's IRI
 * @param label      the human label
 * @param kind       {@code CLASS}, {@code PROPERTY} or {@code INDIVIDUAL}
 * @param superClass local name of the term's super-class, or {@code null}
 * @param definition the ontology's own description of the term, or {@code null}
 * @param synonyms   alternative labels / equivalent classes
 */
public record OntologyTermView(
        String graph,
        String iri,
        String label,
        String kind,
        String superClass,
        String definition,
        List<String> synonyms) {

    public static OntologyTermView from(final OntologyTerm term) {
        return new OntologyTermView(
                term.graph(),
                term.iri(),
                term.label(),
                String.valueOf(term.kind()),
                term.superClass(),
                term.definition(),
                term.synonyms());
    }
}

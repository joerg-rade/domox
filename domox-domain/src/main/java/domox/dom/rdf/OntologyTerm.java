package domox.dom.rdf;

import java.util.List;

/**
 * One ontology term found by {@link OntologyTerms#lookup(String)} — a domain-safe, Jena-free view of
 * a resource in the configured RDF/OWL graph.
 *
 * <p>Deliberately flat and small: it carries exactly what a reviewer needs to judge whether a
 * candidate name is a real concept — which <em>graph</em> it came from (Schema.org, BFO, …), its IRI
 * and human label, its {@link Kind}, its super-class and its definition — so the evidence can be
 * quoted back to an LLM without leaking triples.</p>
 *
 * @param graph      the named graph the descriptive triples were found in (a graph may <em>reference</em>
 *                   a term whose home vocabulary is elsewhere — read it together with {@code iri})
 * @param iri        the term's IRI
 * @param label      {@code rdfs:label} / {@code skos:prefLabel}, else the IRI's local name
 * @param kind       what the term <em>is</em> in the ontology's own vocabulary
 * @param superClass local name of the term's {@code rdfs:subClassOf} target, or {@code null}
 * @param definition {@code rdfs:comment} / {@code skos:definition}, or {@code null}
 * @param synonyms   {@code skos:altLabel} values and local names of {@code owl:equivalentClass}
 */
public record OntologyTerm(
        String graph,
        String iri,
        String label,
        Kind kind,
        String superClass,
        String definition,
        List<String> synonyms) {

    public OntologyTerm {
        synonyms = List.copyOf(synonyms);
    }

    /** What the term is, in the ontology's own vocabulary. */
    public enum Kind {
        /** {@code owl:Class} / {@code rdfs:Class} / {@code ontouml:Class} — a concept. */
        CLASS,
        /** An OWL/RDF property: {@code owl:ObjectProperty}, {@code owl:DatatypeProperty}, {@code rdf:Property}. */
        PROPERTY,
        /** Anything else — an instance of some class. */
        INDIVIDUAL
    }
}

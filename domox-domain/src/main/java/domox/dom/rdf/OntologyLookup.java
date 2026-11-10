package domox.dom.rdf;

import java.util.List;

/**
 * Result of {@link OntologyTerms#lookup(String)}.
 *
 * <p>Carries the <em>reason</em> alongside the matches, because "no matches" and "the endpoint could
 * not be queried" are very different answers — and a reviewer must never read the second as evidence
 * against a candidate.  Mirrors the diagram's graceful degradation: {@code OntologyTerms} never
 * throws.</p>
 *
 * @param term    the term that was looked up
 * @param source  what was queried (the endpoint URL, or the local resource name)
 * @param terms   the matching terms, in the order the focus query returned them
 * @param sourceAvailable {@code false} when the source could not be queried at all — the caller must
 *                        then treat {@code terms} as "unknown", not as "no such term"
 * @param message why there are no matches (or why the source failed), otherwise {@code null}
 */
public record OntologyLookup(
        String term,
        String source,
        List<OntologyTerm> terms,
        boolean sourceAvailable,
        String message) {

    public OntologyLookup {
        terms = List.copyOf(terms);
    }

    public boolean hasMatches() {
        return !terms.isEmpty();
    }
}

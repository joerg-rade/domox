package domox.webapp.mcp;

import domox.dom.rdf.OntologyLookup;

import java.util.List;

/**
 * Result of the {@code ontologySearch} MCP tool: what the configured reference vocabularies know about
 * a term.
 *
 * @param term       the term that was looked up
 * @param source     what was queried (the SPARQL endpoint, or the local ontology resource)
 * @param matchCount how many terms matched
 * @param matches    the matching terms
 * @param message    why there are no matches — {@code null} when there are some
 */
public record OntologySearchResult(
        String term,
        String source,
        int matchCount,
        List<OntologyTermView> matches,
        String message) {

    public static OntologySearchResult of(final OntologyLookup lookup) {
        final List<OntologyTermView> matches = lookup.terms().stream()
                .map(OntologyTermView::from)
                .toList();
        return new OntologySearchResult(
                lookup.term(),
                lookup.source(),
                matches.size(),
                matches,
                lookup.message());
    }
}

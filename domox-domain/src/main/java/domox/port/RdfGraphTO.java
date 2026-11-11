package domox.port;

import java.util.List;
import java.util.Map;

/**
 * A domain-safe view of an RDF graph: its triples plus the prefix declarations of the document.
 *
 * <p>Deliberately free of any Jena type — the adapter maps Jena's {@code Model} into this record,
 * so the domain (and the diagram builder) never depend on the Semantic Web libraries.</p>
 *
 * @param triples    every statement of the graph (subject, predicate, object)
 * @param prefixes   prefix → namespace IRI, as declared in the source document (e.g. {@code ps})
 * @param graphByIri the named graph each IRI was read from — IRI → source graph IRI.  Empty when the
 *                   source has no named graphs (a single file, or a single-default-graph endpoint);
 *                   the diagram uses it to show which reference ontology an entry came from.
 */
public record RdfGraphTO(List<RdfTripleTO> triples, Map<String, String> prefixes,
                         Map<String, String> graphByIri) {

    public RdfGraphTO {
        triples = List.copyOf(triples);
        prefixes = Map.copyOf(prefixes);
        graphByIri = Map.copyOf(graphByIri);
    }

    /** A graph with no per-IRI graph attribution — a local file, or a single-default-graph endpoint. */
    public RdfGraphTO(final List<RdfTripleTO> triples, final Map<String, String> prefixes) {
        this(triples, prefixes, Map.of());
    }

    public int size() {
        return triples.size();
    }

    public boolean isEmpty() {
        return triples.isEmpty();
    }
}

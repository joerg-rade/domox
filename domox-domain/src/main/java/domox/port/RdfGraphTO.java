package domox.port;

import java.util.List;
import java.util.Map;

/**
 * A domain-safe view of an RDF graph: its triples plus the prefix declarations of the document.
 *
 * <p>Deliberately free of any Jena type — the adapter maps Jena's {@code Model} into this record,
 * so the domain (and the diagram builder) never depend on the Semantic Web libraries.</p>
 *
 * @param triples  every statement of the graph (subject, predicate, object)
 * @param prefixes prefix → namespace IRI, as declared in the source document (e.g. {@code ps})
 */
public record RdfGraphTO(List<RdfTripleTO> triples, Map<String, String> prefixes) {

    public RdfGraphTO {
        triples = List.copyOf(triples);
        prefixes = Map.copyOf(prefixes);
    }

    public int size() {
        return triples.size();
    }

    public boolean isEmpty() {
        return triples.isEmpty();
    }
}

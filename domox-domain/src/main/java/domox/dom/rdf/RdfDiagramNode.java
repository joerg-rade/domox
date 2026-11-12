package domox.dom.rdf;

/**
 * One node of an RDF diagram, as the JSON writer needs it: its IRI ({@code id}), the human name to
 * print, the cluster group it belongs to ({@code typeLocalName}), its {@link RdfNodeKind} and the
 * reference ontology it came from.
 */
record RdfDiagramNode(String id, String name, String typeLocalName, RdfNodeKind kind, String ontology) {
}

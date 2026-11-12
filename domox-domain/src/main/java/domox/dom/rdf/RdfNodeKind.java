package domox.dom.rdf;

/**
 * The three node kinds of an RDF graph, rendered in the {@code <<…>>} label line and used as the
 * legend's categories.  The colour and radius are carried here so the node declaration and the legend
 * entry can never disagree.
 */
enum RdfNodeKind {
    CLASS("Class", "#3498DB", 14, "owl:Class / rdfs:Class - a concept"),
    INDIVIDUAL("Individual", "#2ECC71", 11, "An instance (rdf:type of a class)"),
    LITERAL("Literal", "#AAB7B8", 8, "A data value");

    final String label;
    final String color;
    final int radius;
    final String description;

    RdfNodeKind(final String label, final String color, final int radius, final String description) {
        this.label = label;
        this.color = color;
        this.radius = radius;
        this.description = description;
    }
}

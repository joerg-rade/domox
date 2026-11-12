package domox.dom.rdf.geometry;

/**
 * A class box, in the export's own coordinate system: its top-left corner and its size.
 *
 * <p>A plain value carrier of the UML-layout reconstruction, extracted from
 * {@code UmlLayoutDiagrams} so that the router no longer depends on the layout class just to name
 * the boxes it routes between.</p>
 */
public record Box(double x, double y, double w, double h) {
}

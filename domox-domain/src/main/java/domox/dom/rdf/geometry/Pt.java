package domox.dom.rdf.geometry;

/**
 * One point of a connector's polyline, in the export's own coordinate system.
 *
 * <p>A plain value carrier of the UML-layout reconstruction, extracted from
 * {@code UmlLayoutDiagrams} so that the router no longer depends on the layout class just to name
 * the points it works with.</p>
 */
public record Pt(double x, double y) {
}

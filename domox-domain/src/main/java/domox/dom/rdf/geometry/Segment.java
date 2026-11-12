package domox.dom.rdf.geometry;

/**
 * One segment of an already-placed route — the unit the crossing count and the router work with.
 *
 * <p>A plain value carrier of the UML-layout reconstruction, extracted from
 * {@code UmlLayoutDiagrams} so that the router no longer depends on the layout class just to name
 * the segments it avoids.</p>
 */
public record Segment(Pt from, Pt to) {
}

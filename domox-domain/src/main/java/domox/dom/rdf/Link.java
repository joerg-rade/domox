package domox.dom.rdf;

import java.util.List;

import domox.dom.rdf.geometry.Pt;

/**
 * A connector: the two views it joins, what it means, the polyline it follows, and whether it carries
 * the arrowhead.  Generalizations that share a super-class merge into one trunk there, and only the
 * first of them draws the arrow.
 *
 * <p>Promoted out of {@code UmlLayoutDiagrams}: it is the value the routing stage shares — the export
 * parsing builds it ({@link UmlLayoutDiagrams#renderLayout}), {@link PortLayout} reads the kind to
 * place its ends, and the JSON assembly writes it back out.</p>
 */
record Link(String source, String target, String kind, String label, List<Pt> points,
            boolean arrow) {

    /** The link kinds; the client picks the stroke colour and the arrowhead from these. */
    static final String ASSOCIATION = "association";

    static final String GENERALIZATION = "generalization";
}

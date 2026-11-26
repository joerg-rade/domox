package domox.dom.rdf;

import java.util.List;

import domox.dom.rdf.geometry.Pt;

/**
 * A connector: the two views it joins, what it means, the polyline it follows, and whether it carries
 * the arrowhead.  Generalizations that share a super-class merge into one trunk there, and only the
 * first of them draws the arrow.
 *
 * <p>{@code members} is what the connector <em>stands for</em>, one line each, and is empty for every
 * connector but the sub-domain overview's.  There one edge aggregates many collaborations and its label is
 * only the count of them, so the collaborations themselves travel with it — they are what the reader gets
 * by pointing at the line (see {@code ClassDiagram#overviewOf}).</p>
 *
 * <p>Promoted out of {@code UmlLayoutDiagrams}: it is the value the routing stage shares — the export
 * parsing builds it ({@link UmlLayoutDiagrams#renderLayout}), {@link PortLayout} reads the kind to
 * place its ends, and the JSON assembly writes it back out.</p>
 */
record Link(String source, String target, String kind, String label, List<Pt> points,
            boolean arrow, List<String> members) {

    /** The link kinds; the client picks the stroke colour and the arrowhead from these. */
    static final String ASSOCIATION = "association";

    static final String GENERALIZATION = "generalization";
}

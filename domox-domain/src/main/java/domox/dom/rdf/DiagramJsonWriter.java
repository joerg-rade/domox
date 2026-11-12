package domox.dom.rdf;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Bounds;
import domox.dom.rdf.geometry.Pt;

/**
 * <b>Serialises a reconstructed UML diagram to the D3 payload</b> the client draws statically.
 *
 * <p>The payload carries its own geometry, so the client needs no layout engine:</p>
 * <pre>
 *   { "layout":"uml", "title":"…", "canvas":{"width":…,"height":…},
 *     "nodes":[{"id","name","kind","x","y","w","h"}],
 *     "links":[{"source","target","kind","label","points":[[x,y],…]}],
 *     "legend":[{"color","label","description"}] }
 * </pre>
 *
 * <p>The export's coordinates are absolute within a much larger sheet, so every coordinate is translated
 * to start at {@link #PADDING} and the canvas is the drawing's bounding box plus that margin.</p>
 *
 * <p>Extracted from {@code UmlLayoutDiagrams}; it is handed the boxes, the routed links and the bounds
 * that class computed, and does nothing but turn them into JSON.</p>
 */
final class DiagramJsonWriter {

    /** Margin drawn around the reconstructed picture, in model units. */
    private static final int PADDING = 24;

    /** The model element kinds the reconstructed nodes carry. */
    private static final String NODE_KIND = "Class";

    private DiagramJsonWriter() {
    }

    /**
     * Renders the diagram — its canvas, its nodes, its links and the legend — as one JSON object.
     *
     * @param title   the diagram's display name, drawn as the title
     * @param bounds  the drawing's bounding box, before the margin is added
     * @param nodeIds the boxes to draw, in the order they are emitted
     * @param boxes   the box rectangles, keyed by view id
     * @param nameOf  the class name a view draws, for the node label
     * @param links   the connectors, already routed and clipped
     */
    static String write(final String title, final Bounds bounds, final List<String> nodeIds,
                        final Map<String, Box> boxes, final Function<String, String> nameOf,
                        final List<Link> links) {
        final double dx = PADDING - bounds.minX();
        final double dy = PADDING - bounds.minY();

        final StringBuilder json = new StringBuilder(1024);
        json.append('{')
                .append("\"layout\":\"uml\",")
                .append("\"title\":\"").append(escape(title)).append("\",")
                .append("\"canvas\":{\"width\":").append(num(bounds.width() + 2 * PADDING))
                .append(",\"height\":").append(num(bounds.height() + 2 * PADDING)).append("},");

        json.append("\"nodes\":[");
        boolean first = true;
        for (final String id : nodeIds) {
            final Box box = boxes.get(id);
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"id\":\"").append(escape(id)).append('"')
                    .append(",\"name\":\"").append(escape(nameOf.apply(id))).append('"')
                    .append(",\"kind\":\"").append(NODE_KIND).append('"')
                    .append(",\"x\":").append(num(box.x() + dx))
                    .append(",\"y\":").append(num(box.y() + dy))
                    .append(",\"w\":").append(num(box.w()))
                    .append(",\"h\":").append(num(box.h()))
                    .append('}');
        }
        json.append("],");

        json.append("\"links\":[");
        first = true;
        for (final Link link : links) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"source\":\"").append(escape(link.source())).append('"')
                    .append(",\"target\":\"").append(escape(link.target())).append('"')
                    .append(",\"kind\":\"").append(link.kind()).append('"')
                    .append(",\"label\":\"").append(escape(link.label())).append('"')
                    .append(",\"arrow\":").append(link.arrow())
                    .append(",\"points\":[");
            boolean firstPoint = true;
            for (final Pt point : link.points()) {
                if (!firstPoint) {
                    json.append(',');
                }
                firstPoint = false;
                json.append('[').append(num(point.x() + dx)).append(',').append(num(point.y() + dy)).append(']');
            }
            json.append("]}");
        }
        json.append("],");

        // The legend explains the two line kinds (and the box).  Each entry carries the `kind` the
        // client uses to draw the matching swatch — a line, an arrow, or a box — so the legend can
        // never drift from what the drawing actually looks like.
        json.append("\"legend\":[")
                .append("{\"kind\":\"node\",\"color\":\"#3498DB\",\"label\":\"Class\",")
                .append("\"description\":\"a class box, where the model puts it\"},")
                .append("{\"kind\":\"association\",\"color\":\"#7f8c8d\",\"label\":\"Association\",")
                .append("\"description\":\"a plain line between two classes\"},")
                .append("{\"kind\":\"generalization\",\"color\":\"#e67e22\",\"label\":\"Generalization\",")
                .append("\"description\":\"the arrow points at the super-class (is-a)\"}")
                .append(']');

        json.append('}');
        return json.toString();
    }

    /** Integral values print without a {@code .0} — the JSON is read by humans as well as by D3. */
    private static String num(final double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? Long.toString((long) value)
                : Double.toString(value);
    }

    private static String escape(final String value) {
        return RdfGraphDiagrams.escapeJson(value);
    }
}

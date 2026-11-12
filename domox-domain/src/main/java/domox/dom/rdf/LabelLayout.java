package domox.dom.rdf;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;

import domox.dom.rdf.geometry.Box;

/**
 * <b>Label layout</b> for the UML reconstruction: growing a class box until its label fits, so the whole
 * diagram keeps <b>one font size</b>.
 *
 * <p>A model's box is only as wide as its own layout says, and over a third of them are narrower than the
 * class name they carry.  Shrinking those labels would make one diagram use two font sizes, so instead the
 * box is widened to the width {@link LabelMetrics} estimates for its name — to the right when there is
 * room, otherwise to the left.  Only when a neighbouring box leaves room in neither direction is the width
 * left alone and the client shrinks that single label.</p>
 *
 * <p>Extracted from {@code UmlLayoutDiagrams}; the width estimate it fits to lives in
 * {@link LabelMetrics}, and the boxes it widens are those {@code UmlLayoutDiagrams} reconstructed.</p>
 */
final class LabelLayout {

    private LabelLayout() {
    }

    /**
     * Widens a box so its class name fits, which keeps <b>one font size across the whole diagram</b> — a
     * shrunk label is the fallback, not the rule.
     * <p>
     * A model's box is only as wide as its own layout says, and over a third of them are narrower than
     * their class name.  The box grows to the right when there is room, otherwise to the left; when a
     * neighbouring box leaves no room either way the width is left alone and the client shrinks that one
     * label instead.  Measured on the bundled model: 103 boxes grow right, 16 grow left, 7 have no room.
     *
     * @param boxes  the boxes to widen <em>in place</em>, keyed by view id
     * @param nameOf the class name a view draws — the text the box must fit
     */
    static void widenToFitLabels(final Map<String, Box> boxes, final Function<String, String> nameOf) {
        for (final String id : new ArrayList<>(boxes.keySet())) {
            final Box box = boxes.get(id);
            final double shortfall = LabelMetrics.estimatedLabelWidth(nameOf.apply(id))
                    + LabelMetrics.LABEL_PADDING - box.w();
            if (shortfall <= 0) {
                continue;
            }
            if (room(boxes, id, box, true) >= shortfall) {
                boxes.put(id, new Box(box.x(), box.y(), box.w() + shortfall, box.h()));
            } else if (room(boxes, id, box, false) >= shortfall) {
                boxes.put(id, new Box(box.x() - shortfall, box.y(), box.w() + shortfall, box.h()));
            }
        }
    }

    /**
     * Free space beside {@code box} before the nearest box that overlaps it vertically — to the right
     * ({@code rightwards}) or to the left.
     *
     * @return {@link Double#MAX_VALUE} when nothing is in the way, {@code 0} when a neighbour already
     *         reaches the box
     */
    private static double room(final Map<String, Box> boxes, final String id, final Box box,
                               final boolean rightwards) {
        double best = Double.MAX_VALUE;
        for (final Map.Entry<String, Box> other : boxes.entrySet()) {
            if (other.getKey().equals(id)) {
                continue;
            }
            final Box b = other.getValue();
            if (b.y() >= box.y() + box.h() || box.y() >= b.y() + b.h()) {
                continue;                                   // no vertical overlap: not in the way
            }
            if (rightwards) {
                if (b.x() + b.w() <= box.x() + box.w()) {
                    continue;                               // it ends before our right edge
                }
                best = Math.clamp(b.x() - LabelMetrics.LABEL_GAP - (box.x() + box.w()), 0, best);
            } else {
                if (b.x() >= box.x()) {
                    continue;                               // it starts after our left edge
                }
                best = Math.clamp(box.x() - LabelMetrics.LABEL_GAP - (b.x() + b.w()), 0, best);
            }
        }
        return best;
    }
}

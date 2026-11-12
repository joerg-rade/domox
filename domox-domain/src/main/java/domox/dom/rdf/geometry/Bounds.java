package domox.dom.rdf.geometry;

import java.util.Collection;

/**
 * The drawing's bounding box — what the padded canvas is computed from.
 *
 * <p>A plain value carrier of the UML-layout reconstruction, extracted from
 * {@code UmlLayoutDiagrams}.  It is built from the boxes and the surviving connector points rather
 * than from the layout class's private link type, so it depends only on {@link Box} and
 * {@link Pt}.</p>
 */
public record Bounds(double minX, double minY, double maxX, double maxY) {

    /** The bounding box of the boxes alone: the area a route's bends must stay within. */
    public static Bounds of(final Collection<Box> boxes) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (final Box box : boxes) {
            minX = Math.min(minX, box.x());
            minY = Math.min(minY, box.y());
            maxX = Math.max(maxX, box.x() + box.w());
            maxY = Math.max(maxY, box.y() + box.h());
        }
        return minX > maxX ? new Bounds(0, 0, 0, 0) : new Bounds(minX, minY, maxX, maxY);
    }

    /** The bounding box of the whole picture: the boxes plus every surviving connector point. */
    public static Bounds of(final Collection<Box> boxes, final Collection<Pt> points) {
        final Bounds base = of(boxes);
        double minX = base.minX();
        double minY = base.minY();
        double maxX = base.maxX();
        double maxY = base.maxY();
        for (final Pt point : points) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    /** The width of the bounding box. */
    public double width() {
        return maxX - minX;
    }

    /** The height of the bounding box. */
    public double height() {
        return maxY - minY;
    }
}

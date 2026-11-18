package domox.dom.rdf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeSet;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Pt;
import domox.dom.rdf.geometry.Segment;

import static domox.dom.rdf.PortGeometry.DEPARTURE_STUB;
import static domox.dom.rdf.PortGeometry.DOWN;
import static domox.dom.rdf.PortGeometry.LEFT;
import static domox.dom.rdf.PortGeometry.NONE;
import static domox.dom.rdf.PortGeometry.RIGHT;
import static domox.dom.rdf.PortGeometry.UP;

/**
 * An <b>orthogonal connector router</b>: the axis-aligned route between two class boxes that never runs
 * through a box and crosses as few already-placed routes as it can.
 *
 * <p>This is the piece the reconstruction used to do without.  It reused the bends the export stored,
 * which is where a dense diagram's crossings come from: the model's author drew each connector on its
 * own, so no route was ever aware of another.  Here every connector is routed afresh — that trades the
 * model's own routing for a picture whose lines keep out of each other's way.</p>
 *
 * <p>The search runs over the <b>Hanan grid</b> of the coordinates that matter — the left/right/top/bottom
 * edges and the centres of the boxes in play, plus the two attachment points.  A shortest rectilinear
 * route between two points among rectilinear obstacles always exists on that grid, so A* over it is both
 * correct and cheap.  Each move is priced at its length, plus {@link #BEND_COST} for turning a corner and
 * {@link #CROSS_COST} for crossing a route already placed; a move that would pass through a box is not a
 * legal move at all.</p>
 *
 * <p>Blocked moves and crossing counts are precomputed per grid line, so the search itself costs O(1) per
 * neighbour and a diagram of a hundred connectors routes in a fraction of a second.</p>
 *
 * <p>Where a connector <em>attaches</em> — a box's four edges, their midpoints, and the inward normal a
 * generalization arrives along — is {@link PortGeometry}, which this class and {@link PortLayout} share.</p>
 */
final class OrthogonalRouter {

    /** Cost of turning a corner — a route with fewer bends reads better than a marginally shorter one. */
    private static final double BEND_COST = 20.0;

    /** Cost of crossing a route already placed — a route may detour this far to avoid one. */
    private static final double CROSS_COST = 250.0;

    /** How far outside the two boxes the search region reaches, so a route can get around an obstacle. */
    private static final double REGION_MARGIN = 160.0;

    /** Tolerance on coordinates that ought to be equal. */
    private static final double EPS = 1e-6;

    /** Above this the grid is too big to search; the caller's plain route is used instead. */
    private static final long MAX_GRID = 250_000L;

    /** How many directions a search state can be in — the direction vocabulary itself is {@link PortGeometry}. */
    private static final int DIRECTIONS = 5;

    private OrthogonalRouter() {
    }

    /**
     * Routes a connector from {@code start} to {@code end}, avoiding every box and preferring a path that
     * crosses few of the routes already placed.
     *
     * @param startPort the point on {@code start} the route must leave from, or {@code null} to let the
     *                  search pick the cheapest of the box's four edge midpoints
     * @param endPort   likewise for {@code end}
     * @param departure the direction the route must first travel in from {@code startPort}, or
     *                  {@link PortGeometry#NONE} for any — a generalization asks for {@link PortGeometry#UP},
     *                  so it leaves the middle of its sub-class's top edge going straight up
     * @param arrival   the direction the route must be travelling in when it reaches {@code endPort}, or
     *                  {@link PortGeometry#NONE} for any — a generalization asks for the inward normal, so
     *                  that every connector into one super-class arrives along the same line and they merge
     * @return the route's points, or an empty list when no route was found (the caller then falls back)
     */
    static List<Pt> route(final Box start, final Box end, final Pt startPort, final Pt endPort,
                          final int departure, final int arrival,
                          final List<Box> boxes, final List<Segment> placed) {
        final List<Pt> froms = PortGeometry.ports(start, startPort);
        final List<Pt> tos = PortGeometry.ports(end, endPort);
        if (froms.isEmpty() || tos.isEmpty()) {
            return List.of();
        }

        // -- the region the search may use: both boxes, plus a margin -----------------------------
        final double minX = Math.min(start.x(), end.x()) - REGION_MARGIN;
        final double maxX = Math.max(start.x() + start.w(), end.x() + end.w()) + REGION_MARGIN;
        final double minY = Math.min(start.y(), end.y()) - REGION_MARGIN;
        final double maxY = Math.max(start.y() + start.h(), end.y() + end.h()) + REGION_MARGIN;

        // -- the Hanan grid: the coordinates that matter ------------------------------------------
        final TreeSet<Double> xs = new TreeSet<>();
        final TreeSet<Double> ys = new TreeSet<>();
        xs.add(minX);
        xs.add(maxX);
        ys.add(minY);
        ys.add(maxY);

        final List<Box> obstacles = new ArrayList<>();
        for (final Box box : boxes) {
            if (box.x() > maxX || box.x() + box.w() < minX || box.y() > maxY || box.y() + box.h() < minY) {
                continue;
            }
            obstacles.add(box);
            xs.add(box.x());
            xs.add(box.x() + box.w());
            xs.add(box.x() + box.w() / 2);
            ys.add(box.y());
            ys.add(box.y() + box.h());
            ys.add(box.y() + box.h() / 2);
        }
        for (final Pt port : froms) {
            xs.add(port.x());
            ys.add(port.y());
        }
        for (final Pt port : tos) {
            xs.add(port.x());
            ys.add(port.y());
        }
        // A forced departure must run straight out of the box, but only a short way before it may turn —
        // otherwise the nearest grid line it could reach is the edge of the search region, and the route
        // would leave a long stub in the air above the box.
        if (departure != NONE) {
            for (final Pt port : froms) {
                switch (departure) {
                    case UP -> ys.add(port.y() - DEPARTURE_STUB);
                    case DOWN -> ys.add(port.y() + DEPARTURE_STUB);
                    case LEFT -> xs.add(port.x() - DEPARTURE_STUB);
                    case RIGHT -> xs.add(port.x() + DEPARTURE_STUB);
                    default -> { }
                }
            }
        }

        final double[] xa = toArray(xs);
        final double[] ya = toArray(ys);
        final int nx = xa.length;
        final int ny = ya.length;
        if (nx < 2 || ny < 2 || (long) nx * ny > MAX_GRID) {
            return List.of();
        }

        // -- what each move costs, precomputed per grid line --------------------------------------
        final boolean[] blockedH = new boolean[(nx - 1) * ny];
        final boolean[] blockedV = new boolean[nx * (ny - 1)];
        for (final Box box : obstacles) {
            markBlocked(box, xa, ya, blockedH, blockedV);
        }
        final int[] crossH = new int[(nx - 1) * ny];
        final int[] crossV = new int[nx * (ny - 1)];
        for (final Segment segment : placed) {
            markCrossings(segment, xa, ya, crossH, crossV);
        }

        return search(xa, ya, froms, tos, departure, arrival, blockedH, blockedV, crossH, crossV);
    }

    /**
     * A* over the grid: from any of the {@code froms}, to any of the {@code tos}.
     * <p>
     * The state is a grid node <em>plus</em> the direction the route arrived in, so turning a corner can
     * be priced and a required arrival direction can be enforced; a move that would pass through a box was
     * already marked, and never enters the search.
     */
    private static List<Pt> search(final double[] xa, final double[] ya,
                                   final List<Pt> froms, final List<Pt> tos,
                                   final int departure, final int arrival,
                                   final boolean[] blockedH, final boolean[] blockedV,
                                   final int[] crossH, final int[] crossV) {
        final int nx = xa.length;
        final int ny = ya.length;
        final int stride = nx * ny;
        final double[] g = new double[DIRECTIONS * stride];
        Arrays.fill(g, Double.POSITIVE_INFINITY);
        final int[] back = new int[DIRECTIONS * stride];
        Arrays.fill(back, -1);

        final boolean[] goal = new boolean[stride];
        for (final Pt port : tos) {
            final int i = indexOf(xa, port.x());
            final int j = indexOf(ya, port.y());
            if (i >= 0 && j >= 0) {
                goal[i * ny + j] = true;
            }
        }

        final PriorityQueue<Step> open = new PriorityQueue<>(
                Comparator.comparingDouble(Step::f).thenComparingLong(Step::seq));
        long seq = 0;
        Pt forcedFrom = null;
        for (final Pt port : froms) {
            final int i = indexOf(xa, port.x());
            final int j = indexOf(ya, port.y());
            if (i < 0 || j < 0) {
                continue;
            }
            if (departure == NONE) {
                final int state = i * ny + j;                   // direction NONE
                if (g[state] > 0) {
                    g[state] = 0;
                    open.add(new Step(i, j, NONE, 0, heuristic(xa, ya, i, j, tos), seq++));
                }
                continue;
            }
            // A forced departure: the route's first move leaves the port in that one direction, so the
            // search starts at the next grid node along it and the port is put back in front afterwards.
            final int ni = departure == RIGHT ? i + 1 : departure == LEFT ? i - 1 : i;
            final int nj = departure == DOWN ? j + 1 : departure == UP ? j - 1 : j;
            if (ni < 0 || ni >= nx || nj < 0 || nj >= ny) {
                continue;
            }
            final boolean blocked = departure == RIGHT || departure == LEFT
                    ? blockedH[Math.min(i, ni) * ny + j]
                    : blockedV[i * (ny - 1) + Math.min(j, nj)];
            if (blocked) {
                continue;
            }
            final double step = Math.abs(xa[ni] - xa[i]) + Math.abs(ya[nj] - ya[j]);
            final int state = departure * stride + ni * ny + nj;
            if (g[state] > step) {
                g[state] = step;
                open.add(new Step(ni, nj, departure, step,
                        step + heuristic(xa, ya, ni, nj, tos), seq++));
                forcedFrom = port;
            }
        }

        int goalState = -1;
        while (!open.isEmpty()) {
            final Step cur = open.poll();
            final int state = cur.dir() * stride + cur.i() * ny + cur.j();
            if (cur.g() > g[state] + EPS) {
                continue;                                       // a cheaper way to this state won
            }
            if (goal[cur.i() * ny + cur.j()] && (arrival == NONE || cur.dir() == arrival)) {
                goalState = state;
                break;
            }
            final int i = cur.i();
            final int j = cur.j();
            for (int di = -1; di <= 1; di += 2) {
                final int ni = i + di;
                if (ni < 0 || ni >= nx) {
                    continue;
                }
                final int hi = Math.min(i, ni);
                if (blockedH[hi * ny + j]) {
                    continue;
                }
                final int dir = di > 0 ? RIGHT : LEFT;
                if (dir == opposite(cur.dir())) {
                    continue;                                   // never double straight back
                }
                final double step = Math.abs(xa[ni] - xa[i])
                        + (turns(cur.dir(), dir) ? BEND_COST : 0)
                        + CROSS_COST * crossH[hi * ny + j];
                relax(open, g, back, xa, ya, tos, state, g[state] + step,
                        dir * stride + ni * ny + j, ni, j, dir, seq++);
            }
            for (int dj = -1; dj <= 1; dj += 2) {
                final int nj = j + dj;
                if (nj < 0 || nj >= ny) {
                    continue;
                }
                final int vj = Math.min(j, nj);
                if (blockedV[i * (ny - 1) + vj]) {
                    continue;
                }
                final int dir = dj > 0 ? DOWN : UP;
                if (dir == opposite(cur.dir())) {
                    continue;                                   // never double straight back
                }
                final double step = Math.abs(ya[nj] - ya[j])
                        + (turns(cur.dir(), dir) ? BEND_COST : 0)
                        + CROSS_COST * crossV[i * (ny - 1) + vj];
                relax(open, g, back, xa, ya, tos, state, g[state] + step,
                        dir * stride + i * ny + nj, i, nj, dir, seq++);
            }
        }

        if (goalState < 0) {
            return List.of();
        }
        final List<Pt> points = new ArrayList<>();
        for (int state = goalState; state >= 0; state = back[state]) {
            final int rest = state % stride;
            points.add(new Pt(xa[rest / ny], ya[rest % ny]));
        }
        Collections.reverse(points);
        if (forcedFrom != null) {
            points.addFirst(forcedFrom);
        }
        return simplify(points);
    }

    private static void relax(final PriorityQueue<Step> open, final double[] g, final int[] back,
                              final double[] xa, final double[] ya, final List<Pt> goals,
                              final int from, final double cost, final int next,
                              final int i, final int j, final int dir, final long seq) {
        if (cost + EPS < g[next]) {
            g[next] = cost;
            back[next] = from;
            open.add(new Step(i, j, dir, cost, cost + heuristic(xa, ya, i, j, goals), seq));
        }
    }

    /**
     * Marks every move that would pass through {@code box}.
     * <p>
     * A box's edges are themselves grid lines, so no move straddles one: a move is inside the box exactly
     * when its fixed coordinate lies strictly between the two parallel edges and its span lies within the
     * other two.  Running <em>along</em> an edge is therefore still legal — routes often do.
     */
    private static void markBlocked(final Box box, final double[] xa, final double[] ya,
                                    final boolean[] blockedH, final boolean[] blockedV) {
        final int nx = xa.length;
        final int ny = ya.length;
        final double left = box.x();
        final double right = box.x() + box.w();
        final double top = box.y();
        final double bottom = box.y() + box.h();
        if (right <= left || bottom <= top) {
            return;
        }
        for (int j = upperIndex(ya, top); j < ny && ya[j] < bottom; j++) {
            for (int i = lowerIndex(xa, left); i < nx - 1 && xa[i + 1] <= right; i++) {
                blockedH[i * ny + j] = true;
            }
        }
        for (int i = upperIndex(xa, left); i < nx && xa[i] < right; i++) {
            for (int j = lowerIndex(ya, top); j < ny - 1 && ya[j + 1] <= bottom; j++) {
                blockedV[i * (ny - 1) + j] = true;
            }
        }
    }

    /** Counts, per move, how many already-placed routes it crosses transversally. */
    private static void markCrossings(final Segment segment, final double[] xa, final double[] ya,
                                      final int[] crossH, final int[] crossV) {
        final int nx = xa.length;
        final int ny = ya.length;
        if (Math.abs(segment.from().y() - segment.to().y()) <= EPS) {
            final double y = segment.from().y();                // a horizontal placed route
            final int j = upperIndex(ya, y) - 1;
            if (j < 0 || j >= ny - 1 || ya[j] >= y || y >= ya[j + 1]) {
                return;                                         // no vertical move straddles it
            }
            final double x1 = Math.min(segment.from().x(), segment.to().x());
            final double x2 = Math.max(segment.from().x(), segment.to().x());
            for (int i = upperIndex(xa, x1); i < nx && xa[i] < x2; i++) {
                crossV[i * (ny - 1) + j]++;
            }
        } else if (Math.abs(segment.from().x() - segment.to().x()) <= EPS) {
            final double x = segment.from().x();                // a vertical placed route
            final int i = upperIndex(xa, x) - 1;
            if (i < 0 || i >= nx - 1 || xa[i] >= x || x >= xa[i + 1]) {
                return;
            }
            final double y1 = Math.min(segment.from().y(), segment.to().y());
            final double y2 = Math.max(segment.from().y(), segment.to().y());
            for (int j = upperIndex(ya, y1); j < ny && ya[j] < y2; j++) {
                crossH[i * ny + j]++;
            }
        }
    }


    /** Manhattan distance to the nearest goal — an admissible lower bound on the cost still to come. */
    private static double heuristic(final double[] xa, final double[] ya, final int i, final int j,
                                    final List<Pt> goals) {
        double best = Double.POSITIVE_INFINITY;
        for (final Pt goal : goals) {
            best = Math.min(best, Math.abs(xa[i] - goal.x()) + Math.abs(ya[j] - goal.y()));
        }
        return best;
    }

    /** Whether moving in {@code next} straight after {@code previous} is a corner. */
    private static boolean turns(final int previous, final int next) {
        return previous != NONE && axis(previous) != axis(next);
    }

    /**
     * The direction that doubles straight back.
     * <p>
     * A move that reverses is never worth taking — it adds length <em>and</em> two corners, so a route
     * containing one is always beaten by the route that skips it.  Forbidding it also keeps a forced
     * departure honest: without this the search could step out of the port and immediately step back in,
     * which the simplification then collapses — undoing the departure entirely.
     */
    private static int opposite(final int direction) {
        return switch (direction) {
            case RIGHT -> LEFT;
            case LEFT -> RIGHT;
            case DOWN -> UP;
            case UP -> DOWN;
            default -> NONE;
        };
    }

    /** The axis a direction runs along: {@code 0} none, {@code 1} horizontal, {@code 2} vertical. */
    private static int axis(final int direction) {
        return switch (direction) {
            case RIGHT, LEFT -> 1;
            case DOWN, UP -> 2;
            default -> 0;
        };
    }

    /** Drops the points a straight run does not need, and any point repeated. */
    private static List<Pt> simplify(final List<Pt> points) {
        final List<Pt> out = new ArrayList<>();
        for (final Pt point : points) {
            while (out.size() >= 2 && collinear(out.get(out.size() - 2), out.getLast(), point)) {
                out.removeLast();
            }
            if (!out.isEmpty() && same(out.getLast(), point)) {
                continue;
            }
            out.add(point);
        }
        return out;
    }

    private static boolean collinear(final Pt a, final Pt b, final Pt c) {
        return (Math.abs(a.x() - b.x()) <= EPS && Math.abs(b.x() - c.x()) <= EPS)
                || (Math.abs(a.y() - b.y()) <= EPS && Math.abs(b.y() - c.y()) <= EPS);
    }

    private static boolean same(final Pt a, final Pt b) {
        return Math.abs(a.x() - b.x()) <= EPS && Math.abs(a.y() - b.y()) <= EPS;
    }

    private static double[] toArray(final TreeSet<Double> values) {
        final double[] out = new double[values.size()];
        int i = 0;
        for (final double value : values) {
            out[i++] = value;
        }
        return out;
    }


    /** The index of {@code value} in the sorted array, or {@code -1} when it is not exactly present. */
    private static int indexOf(final double[] sorted, final double value) {
        final int i = lowerIndex(sorted, value);
        return i < sorted.length && sorted[i] == value ? i : -1;
    }

    /** The first index whose value is {@code >= value}. */
    private static int lowerIndex(final double[] sorted, final double value) {
        int low = 0;
        int high = sorted.length;
        while (low < high) {
            final int mid = (low + high) >>> 1;
            if (sorted[mid] < value) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /** The first index whose value is {@code > value}. */
    private static int upperIndex(final double[] sorted, final double value) {
        int low = 0;
        int high = sorted.length;
        while (low < high) {
            final int mid = (low + high) >>> 1;
            if (sorted[mid] <= value) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /** One entry of the search's open set: a grid node, the axis the route arrived on, and its costs. */
    private record Step(int i, int j, int dir, double g, double f, long seq) { }
}


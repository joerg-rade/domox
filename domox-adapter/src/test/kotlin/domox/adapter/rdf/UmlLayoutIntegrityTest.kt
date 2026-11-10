package domox.adapter.rdf

import domox.dom.rdf.UmlLayoutDiagrams
import org.json.JSONObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Guards the **geometry** of the reconstructed UML diagrams against the bundled OntoUML export.
 *
 * <p>Every rendering defect found in this feature so far lived in the mapping, not in the client:
 * bends stated out of order (a zig-zag that seems to stop in mid-air), paths built from `(0,0)`
 * sentinels, points leaked from another connector, arrow tips left *inside* the box drawn on top of
 * them, connectors attaching at a box **corner**, two connectors attaching at the **same point** of
 * one edge, and inheritance trunks that enter their super-class from the **side**.  All of them were
 * invisible to the unit tests — those use two-box synthetic graphs — and every one was found by
 * looking at the picture.  This asserts the invariants that would have failed for each of them, over
 * the **real** model, so the next such defect fails the build instead of needing an eye.</p>
 *
 * <p>Container-free and network-free (it reads the classpath fixture), so it belongs in the ordinary
 * unit run — wired into `ci.yml` and `scripts/check.sh` next to [JenaRdfAdapterTest].  The same
 * invariants are reported for a live instance by `scripts/uml-diagram-report.py`.</p>
 */
class UmlLayoutIntegrityTest {

    private val rdf = JenaRdfAdapter()
    private val diagrams = UmlLayoutDiagrams()

    /** Every diagram of the bundled export must render and satisfy the geometry invariants. */
    @Test
    fun `the bundled export renders every diagram without geometry violations`() {
        val problems = renderAll().flatMap { (name, payload) -> problemsIn(name, payload) }

        assertThat(problems).describedAs("UML layout invariants violated:\n  " + problems.joinToString("\n  ")).isEmpty()
    }

    /**
     * Writes every diagram's payload to {@link #DUMP_DIR}, so measuring the geometry needs **no running
     * instance**: `python3 scripts/uml-diagram-report.py --payload $DUMP_DIR` analyses them all offline.
     *
     * <p>Without this, every measurement meant `install` + boot + a live SPARQL query — a 35-second cycle
     * with a database and an endpoint attached, repeated dozens of times while tuning the router.  The
     * renderer is already offline here, so the dump is free.</p>
     */
    @Test
    fun `dumps every diagram payload for offline analysis`() {
        val directory = File(DUMP_DIR)
        directory.mkdirs()
        renderAll().forEach { (name, payload) ->
            File(directory, slug(name) + ".json").writeText(payload.toString(2))
        }
    }

    /** The payload of every diagram of the bundled export, rendered from the classpath fixture. */
    private fun renderAll(): Map<String, JSONObject> {
        val graph = rdf.load(ONTOUML)
        val names = diagrams.diagramNames(graph)
        assertThat(names).describedAs("the bundled export ships no diagrams at all").isNotEmpty()
        return names.associateWith { JSONObject(diagrams.renderLayout(graph, it)) }
    }

    /** A diagram name as a file name. */
    private fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private companion object {
        const val ONTOUML = "ontology.ttl"

        /** Mirrors `UmlLayoutDiagrams.ROUTE_SLACK`. */
        const val SLACK = 60.0

        /** A tip this far inside its box is painted over; this far outside it is floating. */
        const val ON_EDGE = 1.5
        const val FLOATING = 30.0

        /** How many boxes per diagram may stay narrower than their label (no room to widen). */
        const val NARROW_LIMIT = 3

        /** A segment this close to an axis counts as orthogonal (mirrors UmlLayoutDiagrams.ORTHO_TOL). */
        const val ORTHO_TOL = 1.0

        /** An inheritance trunk shorter than this reads as a sideways entry, not a merged trunk. */
        const val TRUNK_MIN = 8.0

        /** Where the offline payload dump is written (surefire's working directory is the module root). */
        const val DUMP_DIR = "target/uml-payloads"
    }

    /** One connector end: the box it attaches to, where, and whether it is an inheritance edge. */
    private class End(val box: String, val point: DoubleArray, val arrow: Boolean, val what: String)

    // ---- invariants ---------------------------------------------------------------------------

    private fun problemsIn(title: String, root: JSONObject): List<String> {
        val problems = mutableListOf<String>()
        val boxes = boxesOf(root)
        val names = namesOf(root)
        assertThat(boxes).describedAs("$title has no boxes").isNotEmpty()

        // Every box is widened to fit its label, so the whole diagram keeps one font size.  A box with no
        // room to grow — a neighbour on either side — keeps its width, and the client shrinks that one
        // label; the measured model has four such boxes in total, so a handful is expected while a
        // regression that stops the widening altogether (99 boxes) is not.
        val narrow = boxes.filter { (id, box) ->
            val label = names[id]
            label != null && box[2] + 0.5 < UmlLayoutDiagrams.estimatedLabelWidth(label)
        }
        if (narrow.size > NARROW_LIMIT) {
            problems += "$title: ${narrow.size} boxes are narrower than their label (at most " +
                "$NARROW_LIMIT expected, for boxes with no room to grow): " +
                narrow.keys.take(3).joinToString { names[it] ?: it }
        }

        val links = root.getJSONArray("links")
        val ends = mutableListOf<End>()
        for (i in 0 until links.length()) {
            val link = links.getJSONObject(i)
            val source = link.getString("source")
            val target = link.getString("target")
            val sourceBox = boxes[source]
            val targetBox = boxes[target]
            if (sourceBox == null || targetBox == null) {
                problems += "$title: a connector has a dangling endpoint"
                continue
            }
            val path = pointsOf(link)
            if (path.size < 2) continue
            val generalization = link.getString("kind") == "generalization"
            val what = "'${names[target]}' -> '${names[source]}'"
            ends += End(target, path.first(), generalization, "$what (start)")
            ends += End(source, path.last(), generalization, "$what (end)")

            if (generalization) {
                // The arrow lands on `source` — the super-class end of the path.
                val tip = path.last()
                val offBy = edgeDistance(tip, sourceBox)
                if (inside(tip, sourceBox)) {
                    if (offBy > ON_EDGE) {
                        problems += "$title: the arrow into '${names[source]}' ends " +
                            "${round(offBy)} units INSIDE its box — the box paints the arrowhead over"
                    }
                } else if (offBy > FLOATING) {
                    problems += "$title: the arrow into '${names[source]}' ends " +
                        "${round(offBy)} units OUTSIDE its box — it points at empty space"
                }

                // An inheritance edge leaves the middle of its sub-class's TOP edge...
                val topMiddle = doubleArrayOf(targetBox[0] + targetBox[2] / 2, targetBox[1])
                if (!samePoint(path.first(), topMiddle)) {
                    problems += "$title: $what leaves '${names[target]}' at " +
                        "${point(path.first())}, not the middle of its top edge"
                }

                // ...and runs into the super-class along its edge's normal, for long enough to read as a
                // merged trunk rather than a line that arrives sideways and turns in the last unit or two.
                val edge = edgeOf(sourceBox, tip)
                val previous = path[path.size - 2]
                val along = if (edge <= 1) abs(previous[1] - tip[1]) else abs(previous[0] - tip[0])
                val across = if (edge <= 1) abs(previous[0] - tip[0]) else abs(previous[1] - tip[1])
                if (across > ORTHO_TOL) {
                    problems += "$title: the arrow into '${names[source]}' enters from the SIDE — " +
                        "the trunk must run along its edge's normal"
                } else if (along < TRUNK_MIN) {
                    problems += "$title: the trunk into '${names[source]}' is only ${round(along)} " +
                        "units long (at least ${round(TRUNK_MIN)} expected), so it reads as a sideways entry"
                }
            }

            problems += backtracks(title, path)

            // Every segment must run horizontally or vertically, as a UML tool draws its connectors.
            for (step in 1 until path.size) {
                if (abs(path[step][0] - path[step - 1][0]) > ORTHO_TOL &&
                    abs(path[step][1] - path[step - 1][1]) > ORTHO_TOL) {
                    problems += "$title: '${names[target]}' -> '${names[source]}' has a diagonal " +
                        "segment — routes must be orthogonal"
                    break
                }
            }

            val limit = max(SLACK, centreDistance(sourceBox, targetBox))
            val leaked = path.count {
                edgeDistance(it, sourceBox) > limit && edgeDistance(it, targetBox) > limit
            }
            if (leaked > 0) {
                problems += "$title: '${names[target]}' -> '${names[source]}' carries $leaked " +
                    "bend(s) far from both boxes — a point leaked from another connector"
            }
        }

        problems += attachmentProblems(title, ends, boxes, names)
        return problems
    }

    /**
     * Where connectors attach: never at a box **corner**, and never two of them at the same point of one
     * edge — either way they cannot be told apart at the box, which is how these defects were found (by
     * eye, one round-trip at a time) before they were asserted here.
     */
    private fun attachmentProblems(title: String, ends: List<End>, boxes: Map<String, DoubleArray>,
                                   names: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        for (end in ends) {
            val box = boxes[end.box] ?: continue
            if (onCorner(end.point, box)) {
                problems += "$title: ${end.what} attaches at the CORNER of '${names[end.box]}' — " +
                    "connectors attach at the middle of an edge"
            }
        }
        ends.groupBy { it.box to edgeOf(boxes.getValue(it.box), it.point) }
            .forEach { (edge, group) ->
                for (i in group.indices) {
                    for (j in i + 1 until group.size) {
                        val a = group[i]
                        val b = group[j]
                        if (a.arrow && b.arrow) continue          // one shared trunk, deliberately
                        if (samePoint(a.point, b.point)) {
                            problems += "$title: ${a.what} and ${b.what} attach at the same point on " +
                                "the ${edgeName(edge.second)} edge of '${names[edge.first]}'"
                        }
                    }
                }
            }
        return problems
    }

    // ---- helpers ------------------------------------------------------------------------------

    private fun boxesOf(root: JSONObject): Map<String, DoubleArray> {
        val nodes = root.getJSONArray("nodes")
        return (0 until nodes.length()).associate {
            val node = nodes.getJSONObject(it)
            node.getString("id") to doubleArrayOf(
                node.getDouble("x"), node.getDouble("y"), node.getDouble("w"), node.getDouble("h"))
        }
    }

    private fun namesOf(root: JSONObject): Map<String, String> {
        val nodes = root.getJSONArray("nodes")
        return (0 until nodes.length()).associate {
            val node = nodes.getJSONObject(it)
            node.getString("id") to node.getString("name")
        }
    }

    private fun pointsOf(link: JSONObject): List<DoubleArray> {
        val points = link.getJSONArray("points")
        return (0 until points.length()).map {
            val point = points.getJSONArray(it)
            doubleArrayOf(point.getDouble(0), point.getDouble(1))
        }
    }

    private fun inside(point: DoubleArray, box: DoubleArray, tol: Double = ON_EDGE): Boolean =
        point[0] >= box[0] - tol && point[0] <= box[0] + box[2] + tol &&
            point[1] >= box[1] - tol && point[1] <= box[1] + box[3] + tol

    /** Distance from a point to the nearest edge of a box — zero when it is inside. */
    private fun edgeDistance(point: DoubleArray, box: DoubleArray): Double {
        val dx = max(max(box[0] - point[0], 0.0), point[0] - (box[0] + box[2]))
        val dy = max(max(box[1] - point[1], 0.0), point[1] - (box[1] + box[3]))
        return hypot(dx, dy)
    }

    private fun centreDistance(a: DoubleArray, b: DoubleArray): Double =
        hypot((a[0] + a[2] / 2) - (b[0] + b[2] / 2), (a[1] + a[3] / 2) - (b[1] + b[3] / 2))

    /** The box edge a point attaches to: 0 top, 1 bottom, 2 left, 3 right (mirrors `OrthogonalRouter`). */
    private fun edgeOf(box: DoubleArray, point: DoubleArray): Int {
        val left = abs(point[0] - box[0])
        val right = abs(point[0] - (box[0] + box[2]))
        val top = abs(point[1] - box[1])
        val bottom = abs(point[1] - (box[1] + box[3]))
        val nearest = minOf(left, right, top, bottom)
        return when (nearest) {
            left -> 2
            right -> 3
            top -> 0
            else -> 1
        }
    }

    private fun edgeName(edge: Int): String = listOf("top", "bottom", "left", "right")[edge]

    /** Whether a point sits where two edges of the box meet. */
    private fun onCorner(point: DoubleArray, box: DoubleArray): Boolean {
        if (!inside(point, box)) return false
        val onVertical = abs(point[0] - box[0]) <= ON_EDGE || abs(point[0] - (box[0] + box[2])) <= ON_EDGE
        val onHorizontal = abs(point[1] - box[1]) <= ON_EDGE || abs(point[1] - (box[1] + box[3])) <= ON_EDGE
        return onVertical && onHorizontal
    }

    private fun samePoint(a: DoubleArray, b: DoubleArray, tol: Double = 1.0): Boolean =
        abs(a[0] - b[0]) <= tol && abs(a[1] - b[1]) <= tol

    private fun point(p: DoubleArray): String = "(${round(p[0])},${round(p[1])})"

    /** Exact 180-degree reversals — a segment immediately retraced, which no real route does. */
    private fun backtracks(title: String, path: List<DoubleArray>): List<String> {
        for (i in 2 until path.size) {
            val ax = path[i - 1][0] - path[i - 2][0]
            val ay = path[i - 1][1] - path[i - 2][1]
            val bx = path[i][0] - path[i - 1][0]
            val by = path[i][1] - path[i - 1][1]
            if (ax * bx + ay * by < 0 && abs(ax * by - ay * bx) < 1e-9) {
                return listOf("$title: a connector backtracks on itself — the export states " +
                    "its bends out of order")
            }
        }
        return emptyList()
    }

    private fun round(value: Double): String = "%.0f".format(value)
}

package domox.adapter.rdf

import domox.dom.rdf.UmlLayoutDiagrams
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Guards the **geometry** of the reconstructed UML diagrams against the bundled OntoUML export.
 *
 * <p>Every rendering defect found in this feature so far lived in the mapping, not in the client:
 * bends stated out of order (a zig-zag that seems to stop in mid-air), paths built from `(0,0)`
 * sentinels, points leaked from another connector, and arrow tips left *inside* the box drawn on top
 * of them.  All of them were invisible to the unit tests — those use two-box synthetic graphs — and
 * were only ever found by looking at the picture.  This asserts the invariants that would have failed
 * for each of them, over the **real** model, so the next such defect fails the build instead of
 * needing an eye.</p>
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
        val graph = rdf.load(ONTOUML)
        val names = diagrams.diagramNames(graph)
        assertTrue(names.isNotEmpty(), "the bundled export ships no diagrams at all")

        val problems = names.flatMap { problemsIn(it, JSONObject(diagrams.renderLayout(graph, it))) }

        assertTrue(problems.isEmpty(),
            "UML layout invariants violated:\n  " + problems.joinToString("\n  "))
    }

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
    }

    // ---- invariants ---------------------------------------------------------------------------

    private fun problemsIn(title: String, root: JSONObject): List<String> {
        val problems = mutableListOf<String>()
        val boxes = boxesOf(root)
        val names = namesOf(root)
        assertTrue(boxes.isNotEmpty(), "$title has no boxes")

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

            if (link.getString("kind") == "generalization") {
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

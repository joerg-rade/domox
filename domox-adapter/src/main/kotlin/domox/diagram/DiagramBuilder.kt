package domox.diagram

import domox.HttpRequest
import domox.nlp.ExtendedDependencyFactory
import domox.nlp.ExtendedDependencyTO
import domox.nlp.SentenceTO
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.fop.svg.PDFTranscoder
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.io.StringWriter
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** The SVG namespace used by Graphviz/Kroki output. */
private const val SVG_NS = "http://www.w3.org/2000/svg"

/** The DOT node name the [domox.dom.nlp.LexicalGraphGenerator] gives to the legend. */
private const val LEGEND_NODE_NAME = "Legend"

/** Padding (SVG points) kept between the legend and the diagram's top-left edge. */
private const val LEGEND_CORNER_PADDING = 12.0

/** SVG shape elements whose broad fills could occlude geometry behind them. */
private val SHAPE_ELEMENTS = setOf("polygon", "path", "rect", "ellipse", "circle", "line", "polyline")

/** Graphviz SVGs declare an external DTD (svg11.dtd) that we never need; it is stripped before
 *  parsing so the parser never sees (and never tries to fetch) an external DTD reference. */
private val SVG_DOCTYPE = Regex("""<!DOCTYPE[^>]*>""", setOf(RegexOption.DOT_MATCHES_ALL))

@Component
class DiagramBuilder @JvmOverloads constructor(
    private val httpRequest: HttpRequest = HttpRequest(),
) {

    /**
     * Rewrites a Kroki-rendered Graphviz SVG so the legend node sits in the top-left corner.
     *
     * Why this is needed: the lexical graph selects the force-directed `neato` engine via its DOT
     * `layout` attribute (which Kroki honours over the renderer's own request options). neato
     * cannot park a disconnected node in a corner — the dot-only `rank = sink` idiom is ignored,
     * and a pinned `pos="x,y!"` has no idea where the final bounding box will be. So placement is
     * done here, deterministically: find the legend group (the `<g>` whose `<title>` is
     * [LEGEND_NODE_NAME]), measure the legend and the graph's content bounding box (the
     * `fill="white"` background polygon), and add a `translate(dx dy)` that pins the legend to the
     * top-left corner with [LEGEND_CORNER_PADDING] of padding. The legend's broad background fills
     * are then made transparent so the legend can never occlude a node if it happens to overlap
     * one. All other SVG is left untouched. If the legend (or the background polygon) cannot be
     * found, the SVG is returned unchanged.
     */
    fun relocateLegendToTopLeft(svg: String): String {
        val doc = parseSvg(svg) ?: return svg
        val legendGroup = findLegendGroup(doc) ?: return svg
        val graphBackground = findGraphBackground(doc) ?: return svg

        val graph = boundingBox(graphBackground)
        val legend = boundingBox(legendGroup)
        // SVG y grows downward, so the top edge of the graph is its smallest y.
        val dx = (graph.minX + LEGEND_CORNER_PADDING) - legend.minX
        val dy = (graph.minY + LEGEND_CORNER_PADDING) - legend.minY
        legendGroup.setAttribute("transform", "translate(${formatNumber(dx)} ${formatNumber(dy)})")
        makeBackgroundTransparent(legendGroup, legend)
        return serialize(doc)
    }

    /**
     * Drops the fill of the legend's broad (near full-width) background shapes so an overlapping
     * node stays visible. The small colour-key swatches and the cell borders are left intact.
     */
    private fun makeBackgroundTransparent(legendGroup: Element, legend: Bounds) {
        val legendWidth = legend.maxX - legend.minX
        if (legendWidth <= 0.0) return
        val shapes = legendGroup.getElementsByTagName("*")
        for (i in 0 until shapes.length) {
            val element = shapes.item(i) as? Element ?: continue
            if (element.localName !in SHAPE_ELEMENTS) continue
            val fill = element.getAttribute("fill")
            if (fill.isEmpty() || fill.equals("none", ignoreCase = true)) continue
            val box = boundingBox(element)
            if (box.maxX - box.minX >= legendWidth * 0.8) element.setAttribute("fill", "none")
        }
    }

    /** Parses an SVG defensively: no external entities/DTDs are ever fetched (the declaration is
     *  stripped first, and loading is disabled as belt-and-braces). */
    private fun parseSvg(svg: String): Document? = try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        listOf(
            "http://apache.org/xml/features/nonvalidating/load-external-dtd",
            "http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities",
            XMLConstants.FEATURE_SECURE_PROCESSING
        ).forEach { feature -> try { factory.setFeature(feature, true) } catch (_: Exception) { } }
        factory.newDocumentBuilder().parse(InputSource(StringReader(SVG_DOCTYPE.replace(svg, ""))))
    } catch (_: Exception) {
        null
    }

    /** The legend's `<g>` group (the one whose `<title>` child equals [LEGEND_NODE_NAME]). */
    private fun findLegendGroup(doc: Document): Element? {
        val groups = doc.getElementsByTagNameNS(SVG_NS, "g")
        for (i in 0 until groups.length) {
            val group = groups.item(i) as? Element ?: continue
            if (titleOf(group) == LEGEND_NODE_NAME) return group
        }
        return null
    }

    /** The `<title>` text of a group, trimmed, or null when absent. */
    private fun titleOf(group: Element): String? {
        val children = group.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.localName == "title") return node.textContent.trim()
        }
        return null
    }

    /** The graph's background: the largest `fill="white"` polygon (its content bounding box). */
    private fun findGraphBackground(doc: Document): Element? {
        val polygons = doc.getElementsByTagNameNS(SVG_NS, "polygon")
        var best: Element? = null
        var bestArea = Double.NEGATIVE_INFINITY
        for (i in 0 until polygons.length) {
            val polygon = polygons.item(i) as? Element ?: continue
            if (polygon.getAttribute("fill") != "white") continue
            val box = boundingBox(polygon)
            val area = (box.maxX - box.minX) * (box.maxY - box.minY)
            if (area > bestArea) {
                bestArea = area
                best = polygon
            }
        }
        return best
    }

    /** Tight bounding box of every piece of geometry under [root], in the SVG coordinate space. */
    private fun boundingBox(root: Element): Bounds {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        fun account(x: Double, y: Double) {
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
        fun visit(element: Element) {
            when (element.localName) {
                "polygon" -> parsePoints(element.getAttribute("points")).forEach { account(it.first, it.second) }
                "ellipse" -> {
                    val cx = element.getAttribute("cx").toDoubleOrNull() ?: 0.0
                    val cy = element.getAttribute("cy").toDoubleOrNull() ?: 0.0
                    val rx = element.getAttribute("rx").toDoubleOrNull() ?: 0.0
                    val ry = element.getAttribute("ry").toDoubleOrNull() ?: 0.0
                    account(cx - rx, cy - ry)
                    account(cx + rx, cy + ry)
                }
                "text" -> {
                    val x = element.getAttribute("x").toDoubleOrNull()
                    val y = element.getAttribute("y").toDoubleOrNull()
                    when {
                        x != null -> account(x, y ?: 0.0)
                        y != null -> account(0.0, y)
                    }
                }
            }
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element) visit(child)
            }
        }
        visit(root)
        if (minX.isInfinite()) return Bounds(0.0, 0.0, 0.0, 0.0)
        return Bounds(minX, minY, maxX, maxY)
    }

    /** Parses an SVG `points="x1,y1 x2,y2 ..."` attribute into coordinate pairs. */
    private fun parsePoints(points: String): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        val tokens = points.trim().split(Regex("[\\s,]+"))
        var i = 0
        while (i + 1 < tokens.size) {
            val x = tokens[i].toDoubleOrNull()
            val y = tokens[i + 1].toDoubleOrNull()
            if (x != null && y != null) out.add(x to y)
            i += 2
        }
        return out
    }

    /** Serialises a (possibly mutated) DOM back to a standalone SVG XML string. */
    private fun serialize(doc: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        transformer.setOutputProperty(OutputKeys.INDENT, "no")
        val writer = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(writer))
        return writer.toString()
    }

    private fun formatNumber(value: Double): String = String.format(Locale.ROOT, "%.4f", value)

    private data class Bounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double)

    /**
     * Builds a typed-dependency syntax diagram (PDF) for a [SentenceTO].
     *
     * Kept for callers that still hold a [SentenceTO] (e.g. during NLP analysis).
     */
    fun buildTypedDependencyDiagram(sentence: SentenceTO): ByteArray {
        val dependencies = ExtendedDependencyFactory(sentence).getDependencies()
        return buildTypedDependencyDiagram(dependencies)
    }

    /**
     * Builds a typed-dependency syntax diagram (PDF) directly from the dependency list.
     *
     * Allows the diagram to be reconstructed lazily from persisted domain state
     * e.g. a Sentence's typed dependencies, without requiring the
     * original [SentenceTO] (and thus re-running the NLP pipeline).
     */
    fun buildTypedDependencyDiagram(dependencies: List<ExtendedDependencyTO>): ByteArray {
        val pumlCode = ColoredPlantUmlMindmapGenerator(dependencies).generateMindmap()
        val svgDiagram = httpRequest.invokePlantUML(pumlCode)
        return convertSvgToPdf(svgDiagram)
    }

    /**
     * Builds a document-wide lexical <em>component</em> diagram (PDF) from already
     * generated Graphviz DOT code.
     *
     * The DOT itself is produced by the domain module's `LexicalGraphGenerator`, which
     * draws edges directly from the persisted `TypedDependency`s of a document's
     * sentences — so the caller does not need to reconstruct `SentenceTO`s (or re-run
     * the NLP pipeline). Because the source is DOT (not PlantUML), it is rendered via
     * Kroki's `/graphviz` endpoint.
     */
    fun buildLexicalGraphDiagram(dotCode: String): ByteArray {
        // The Kroki SVG is rewritten before PDF conversion so the legend always lands in the
        // top-left corner (with transparent backgrounds) — Graphviz' force-directed engine
        // (selected by the DOT source and honoured by Kroki) cannot place a node there from
        // inside DOT (see relocateLegendToTopLeft).
        val svgDiagram = relocateLegendToTopLeft(httpRequest.invokeGraphviz(dotCode))
        return convertSvgToPdf(svgDiagram)
    }

    private fun convertSvgToPdf(svgContent: String): ByteArray {
        try {
            val input = TranscoderInput(StringReader(svgContent))
            val outputStream = ByteArrayOutputStream()
            val output = TranscoderOutput(outputStream)

            PDFTranscoder().transcode(input, output)

            return outputStream.toByteArray()
        } catch (e: Exception) {
            throw RuntimeException("Failed to convert SVG to PDF", e)
        }
    }
}
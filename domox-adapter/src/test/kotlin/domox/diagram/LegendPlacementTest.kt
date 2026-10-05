package domox.diagram

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * Unit tests for [DiagramBuilder.relocateLegendToTopLeft] — the SVG post-processing that pins
 * the legend to the top-left corner of a Kroki-rendered graphviz diagram (without a running
 * Kroki container) and makes its broad background fills transparent. Graphviz' neato engine
 * cannot place a node in a corner from within DOT, so the adapter does it deterministically by
 * translating the legend `<g>` group.
 */
internal class LegendPlacementTest {

    /** Reproduces the geometry of real graphviz/Kroki output: a white background polygon spanning
     *  x∈[-4, 378.16], y∈[-184.68, 4], and a legend table group spanning x∈[0,231], y∈[-81.75, 0]
     *  with full-width backgrounds, a full-width header, and a small (20px) colour swatch. */
    private val svg = """
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
         "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
        <svg width="382pt" height="189pt" viewBox="0 0 382.00 189.00" xmlns="http://www.w3.org/2000/svg">
        <g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(4 184.68)">
        <title>LexicalDependencyGraph</title>
        <polygon fill="white" stroke="none" points="-4,4 -4,-184.68 378.16,-184.68 378.16,4 -4,4"/>
        <g id="node1" class="node">
        <title>shop_nn</title>
        <ellipse fill="none" stroke="black" cx="234.95" cy="-150.62" rx="39.95" ry="30.05"/>
        </g>
        <!-- Legend -->
        <g id="node3" class="node">
        <title>Legend</title>
        <polygon fill="#ffffff" stroke="none" points="0,0 0,-81.75 231,-81.75 231,0 0,0"/>
        <polygon fill="#e2e8f0" stroke="none" points="0,-54.5 0,-81.75 231,-81.75 231,-54.5 0,-54.5"/>
        <polygon fill="none" stroke="#333333" points="0,-54.5 0,-81.75 231,-81.75 231,-54.5 0,-54.5"/>
        <polygon fill="#3498db" stroke="none" points="0,-27.25 0,-54.5 20,-54.5 20,-27.25 0,-27.25"/>
        <text xml:space="preserve" text-anchor="start" x="25" y="-40">Noun</text>
        </g>
        </g>
        </svg>
    """.trimIndent()

    @Test
    fun `moves legend group to top-left corner`() {
        val out = DiagramBuilder().relocateLegendToTopLeft(svg)

        // dx = (background.minX + 12) - legend.minX = (-4 + 12) - 0 = 8
        // dy = (background.minY + 12) - legend.minY = (-184.68 + 12) - (-81.75) = -90.93
        val transform = Regex("""id="node3"[^>]*transform="translate\(([0-9.-]+) ([0-9.-]+)\)"""")
            .find(out)?.groupValues
        assertTrue(transform != null, "legend group must carry a translate transform:\n$out")
        assertEquals(8.0, transform!![1].toDouble(), 0.001)
        assertEquals(-90.93, transform[2].toDouble(), 0.001)

        // everything else survives the DOM round-trip untouched
        assertTrue(out.contains("""<title>shop_nn</title>"""), "graph nodes must be preserved:\n$out")
        assertTrue(out.contains("cx=\"234.95\""), "graph node geometry must be preserved:\n$out")
        assertTrue(out.contains("Noun"), "legend content must be preserved:\n$out")

        // and the result is still well-formed XML
        assertTrue(parseable(out), "output must be valid XML")
    }

    @Test
    fun `broad legend backgrounds are transparent but colour swatches survive`() {
        val out = DiagramBuilder().relocateLegendToTopLeft(svg)

        // full-width backgrounds (box + header) become fill="none" so an overlapped node shows through
        assertTrue(!out.contains("""fill="#ffffff""""), "box background must be transparent:\n$out")
        assertTrue(!out.contains("""fill="#e2e8f0""""), "header background must be transparent:\n$out")

        // the small colour-key swatch keeps its fill
        assertTrue(out.contains("""fill="#3498db""""), "colour swatch must keep its fill:\n$out")
    }

    @Test
    fun `returns input unchanged when there is no legend`() {
        val noLegend = svg.replace("""<title>Legend</title>""", """<title>NotALegend</title>""")
        assertEquals(noLegend, DiagramBuilder().relocateLegendToTopLeft(noLegend))
    }

    @Test
    fun `returns input unchanged when background polygon is missing`() {
        val noBackground = svg.replace("""fill="white"""", """fill="white2"""")
        assertNotEquals(svg, noBackground) // guard: the fixture actually changed
        assertEquals(noBackground, DiagramBuilder().relocateLegendToTopLeft(noBackground))
    }

    private fun parseable(xml: String): Boolean = try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        true
    } catch (_: Exception) {
        false
    }
}
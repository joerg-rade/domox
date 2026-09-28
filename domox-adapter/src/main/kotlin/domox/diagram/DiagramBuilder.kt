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

@Component
class DiagramBuilder @JvmOverloads constructor(
    private val httpRequest: HttpRequest = HttpRequest(),
) {

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
package domox.diagram

import domox.HttpRequest
import domox.nlp.SentenceTO
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.fop.svg.PDFTranscoder
import java.io.ByteArrayOutputStream
import java.io.StringReader

class DiagramBuilder {

    fun buildTypedDependencyDiagram(sentence: SentenceTO): ByteArray {
        val pumlCode = ColoredPlantUmlMindmapGenerator(sentence).generateMindmap()
        val svgDiagram = HttpRequest().invokePlantUML(pumlCode)
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
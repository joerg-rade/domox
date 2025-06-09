package domox.diagram

import domox.Constants
import domox.nlp.DocumentTO
import domox.nlp.StanfordCoreNlpAPI
import junit.framework.TestCase.assertEquals
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class TdDiagramTest {
    @Test
    fun testBuild() {
        //given
        val scheme = Constants.coreNlpScheme
        val host = Constants.coreNlpHost
        val port = Constants.coreNlpPort
        val text = "A language tape has a title language and level."
        val coreDocument: DocumentTO = StanfordCoreNlpAPI(scheme, host, port).annotate(text)
        assertNotNull(coreDocument)
        val coreSentence = coreDocument.sentences.first()
        //when
        val pumlCode = TdDiagram.build(coreSentence)
        //then
        assertTrue(pumlCode.contains("rectangle \"A\" as W1"))
        assertTrue(pumlCode.contains("card \"DT\" as D1"))
        assertTrue(pumlCode.contains("D1 -d-( W1"))
        assertTrue(pumlCode.contains("D9 -r-> D10"))
        assertEquals(4, countOccurrencesOfIn("D1 ", pumlCode))
        assertEquals(4, countOccurrencesOfIn("D10 ", pumlCode))
        assertEquals(5, countOccurrencesOfIn("NN", pumlCode))
        assertEquals(2, countOccurrencesOfIn("compound", pumlCode))
        System.out.println(pumlCode)
    }

    private fun countOccurrencesOfIn(needle: String, haystack: String): Int {
        return haystack.windowed(needle.length).filter { it == needle }.count()
    }

}
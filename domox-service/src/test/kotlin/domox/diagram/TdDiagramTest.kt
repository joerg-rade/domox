package domox.diagram

import domox.StanfordNLP
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TdDiagramTest {
    @Test
    fun testBuild() {
        //given
        val text = "A language tape has a title language and level."
        val coreDocument = StanfordNLP().annotate(text)
        val coreSentence = coreDocument.sentences().first()
        //when
        val pumlCode = TdDiagram.build(coreSentence)
        //then
        assertTrue(pumlCode.contains("p1 -> w1"))
        assertTrue(pumlCode.contains("p10 -> w10"))
        assertTrue(pumlCode.contains("rank=same {p1,p2,p3,p4,p5,p6,p7,p8,p9,p10}"))
        assertEquals(1, countOccurrencesOfIn("CHARTREUSE", pumlCode))
        assertEquals(1, countOccurrencesOfIn("WHITE", pumlCode))
        assertEquals(2, countOccurrencesOfIn("MAGENTA", pumlCode))
        assertEquals(5, countOccurrencesOfIn("CYAN", pumlCode))
        assertEquals(1, countOccurrencesOfIn("LIGHTGREY", pumlCode))
    }

    private fun countOccurrencesOfIn(needle: String, haystack: String): Int {
        return haystack.windowed(needle.length).filter { it == needle }.count()
    }
}
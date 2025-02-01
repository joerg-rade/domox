package domox.diagram

import domox.UmlUtils
import junit.framework.TestCase.assertTrue
import org.junit.Test

internal class UmlUtilsTest {

    @Test //IntegrationTest
    fun testGenerateDiagram() {
        //given
        val pumlCode = "\"" +
                "participant BOB [[https://en.wiktionary.org/wiki/best_of_breed]]\\n" +
                "participant PITA [[https://en.wiktionary.org/wiki/PITA]]\\n" +
                "BOB -> PITA: sometimes is a" +
                "\""
        //when
        val svg = UmlUtils().generateDiagram(pumlCode)
        //then
        assertTrue(svg.contains("BOB"))
        assertTrue(svg.contains("PITA"))
    }

    private fun countOccurrencesOfIn(needle: String, haystack: String): Int {
        return haystack.windowed(needle.length).filter { it == needle }.count()
    }
}
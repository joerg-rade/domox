package domox.diagram

import domox.UmlUtils
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

@Disabled("Requires external diagram rendering service")
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
        val svg: String = UmlUtils().generateDiagram(pumlCode)
        //then
        assertTrue(svg.contains("BOB"))
        assertTrue(svg.contains("PITA"))
    }

}

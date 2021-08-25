package domox

import junit.framework.TestCase
import org.junit.Test
import kotlin.test.assertNotEquals

class HttpRequestTest : TestCase() {

    @Test // IntegrationTest !
    fun testInvokeCoreNLP() {
        // given
        val arg = "The quick brown fox jumped over the lazy dog?"
        val parameters =
            "{\"annotators\":\"tokenize,ssplit,pos,lemma,ner,parse\",\"outputFormat\":\"json\"}"
        // when
        val responseStr = HttpRequest().invokeCoreNLP_Fuel(arg, parameters)
        //then
        assertNotEquals("", responseStr)
        System.out.println(responseStr)
    }
}
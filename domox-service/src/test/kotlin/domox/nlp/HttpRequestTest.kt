package domox.nlp

import domox.HttpRequest
import junit.framework.TestCase
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HttpRequestTest : TestCase() {

    @Test // IntegrationTest !
    fun testInvokeCoreNLP() {
        val parameters = "{\"annotators\":\"depparse\",\"outputFormat\":\"json\"}"
        // when
        val responseStr = HttpRequest().invokeCoreNLP_Fuel(SampleText.QBF, parameters)
        //then
        assertNotEquals("", responseStr)
        System.out.println(responseStr)
    }

}
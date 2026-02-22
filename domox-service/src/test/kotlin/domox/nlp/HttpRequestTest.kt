package domox.nlp

import domox.HttpRequest
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class HttpRequestTest {

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

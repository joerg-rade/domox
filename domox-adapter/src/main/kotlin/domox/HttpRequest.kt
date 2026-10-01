package domox

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.json.responseJson
import com.github.kittinunf.result.Result
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
@EnableConfigurationProperties(KrokiProperties::class)
class HttpRequest(
    private val krokiProperties: KrokiProperties = KrokiProperties(),
) {

    /*
    https://www.url-encode-decode.com/
    {"annotators":"tokenize, ssplit, pos, lemma, ner, parse, sentiment","outputFormat":"json"}
    %7B%22annotators%22%3A%22tokenize%2C+ssplit%2C+pos%2C+lemma%2C+ner%2C+parse%2C+sentiment%22%2C%22outputFormat%22%3A%22json%22%7D
     */
    fun invokeCoreNLP_Fuel(arg: String, parameters: String, host: String = Constants.coreNlpHost, port: Int = Constants.coreNlpPort): String {
        System.out.println("[invokeCoreNLP] " + parameters)
        val query = listOf("properties" to parameters)
        val coreNlpUrl = Constants.coreNlpScheme + "://" + host + ":" + port
        Thread.sleep(10000)
        FuelManager.instance.timeoutInMillisecond = TimeUnit.SECONDS.toMillis(20).toInt()    // overall request timeout
        FuelManager.instance.timeoutReadInMillisecond = TimeUnit.SECONDS.toMillis(60).toInt() // socket read timeout

        val (request, response, result) = coreNlpUrl
            .httpPost(query)
            .header(mapOf("Accept" to "application/json, text/plain, */*"))
            .header(mapOf("DNT" to "1"))
            .header(mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36"))
            .header(mapOf("Content-Type" to "application/json;charset=UTF-8"))
            .header(mapOf("Accept-Encoding" to "gzip, deflate, br"))
            .header(mapOf("Origin" to "chrome-extension://ehafadccdcdedbhcbddihehiodgcddpl"))
            .header(mapOf("Sec-Fetch-Site" to "localhost:9000"))
            .header(mapOf("Sec-Fetch-Mode" to "same-origin"))
            .header(mapOf("Sec-Fetch-Dest" to "empty"))
            .header(mapOf("Accept-Language" to "en,de-DE;q=0.9,de;q=0.8,en-US;q=0.7"))
            .header(mapOf("Transfer-Encoding" to "chunked"))
            .body(arg)
            .allowRedirects(true)
            .responseJson()
        when (result) {
            is Result.Failure -> {
                val ex = result.getException()
                println(ex)
                return ""
            }
            is Result.Success -> {
                val data = result.value.content
                return data
            }
        }
    }

    @JvmOverloads
    fun invokePlantUML(arg: String, host: String = "", port: Int = 0): String {
        System.out.println("[invokePlantUML] " + arg)
        val krokiHost = if (host.isEmpty())
            getSystemProperty("kroki.host", krokiProperties.host)
        else host
        val krokiPort = if (port == 0)
            getSystemProperty("kroki.port", krokiProperties.port.toString()).toInt()
        else port
        val endpoint = "http://" + krokiHost + ":" + krokiPort + "/plantuml/svg"
        val (request, response, result) = endpoint
            .httpPost()
            .set("Accept", Constants.svgMimeType)
            .set("Content-Type", Constants.stdMimeType)
            .body(arg)
            .responseString()
        return result.get()
    }

    /**
     * Renders a Graphviz/DOT diagram as SVG via Kroki's `/graphviz/svg` endpoint.
     *
     * Unlike [invokePlantUML] (which POSTs PlantUML source as a raw text body to
     * `/plantuml/svg`), DOT source must be sent as the JSON
     * `{"diagram_source": ..., "diagram_options": {...}}` payload that Kroki's POST
     * API expects — the same format used by [domox.GraphvizUtils.generateDiagram].
     *
     * Uses the configured Kroki host/port unless explicit [host]/[port] are provided.
     */
    @JvmOverloads
    fun invokeGraphviz(dotCode: String, host: String = "", port: Int = 0): String {
        System.out.println("[invokeGraphviz] " + dotCode)
        val krokiHost = if (host.isEmpty())
            getSystemProperty("kroki.host", krokiProperties.host)
        else host
        val krokiPort = if (port == 0)
            getSystemProperty("kroki.port", krokiProperties.port.toString()).toInt()
        else port
        val endpoint = "http://" + krokiHost + ":" + krokiPort + "/graphviz/svg"
        val jsonPayload = "{\"diagram_source\": " + escapeJsonString(dotCode) +
                ",\"diagram_options\": {\"layout\": \"dot\"}}"
        val (request, response, result) = endpoint
            .httpPost()
            .set("Accept", Constants.svgMimeType)
            .set("Content-Type", Constants.jsonMimeType)
            .body(jsonPayload)
            .responseString()
        return result.get()
    }

    private fun escapeJsonString(input: String): String {
        val escaped = input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\u0008", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun getSystemProperty(key: String, defaultValue: String): String {
        return System.getProperty(key) ?: defaultValue
    }

    fun invokeAnonymous(url: String, arg: String): String {
        System.out.println(arg)
        val (request, response, result) = url
            .httpPost()
            .body(arg)
            .responseString()
        val answer = result.get()
        return answer
    }

}
package domox

import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.json.responseJson
import com.github.kittinunf.result.Result
import com.mashape.unirest.http.HttpResponse
import com.mashape.unirest.http.JsonNode
import com.mashape.unirest.http.Unirest

class HttpRequest {

    /*
    https://www.url-encode-decode.com/
    {"annotators":"tokenize, ssplit, pos, lemma, ner, parse, sentiment","outputFormat":"json"}
    %7B%22annotators%22%3A%22tokenize%2C+ssplit%2C+pos%2C+lemma%2C+ner%2C+parse%2C+sentiment%22%2C%22outputFormat%22%3A%22json%22%7D
     */
    fun invokeCoreNLP_Fuel(arg: String, parameters: String): String {
        System.out.println("[invokeCoreNLP] " + parameters)
        val query = listOf("properties" to parameters)
        val (request, response, result) = Constants.coreNlpUrl
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

    fun invokePlantUML(arg: String): String {
        System.out.println("[invokePlantUML] " + arg)
        val endpoint = Constants.plantUmlUrl + "/plantuml"
        val (request, response, result) = endpoint
            .httpPost()
            .set("Accept", Constants.svgMimeType)
            .set("Content-Type", Constants.stdMimeType)
            .body(arg)
            .responseString()
        return result.get()
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
package domox

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class StanfordCoreNlpAPI(
    val scheme: String,
    val host: String,
    val port: Int
) {

    fun annotate(text: String): StanfordCoreNlpTO {
        //val annotators = encodeQuery("{'annotators':'tokenize,ssplit,pos,lemma,ner,parse,depparse,coref'}")
        val annotators = encodeQuery("{'annotators':'depparse,coref'}")
        val url = "${scheme}://${host}:${port}/?properties=${annotators}"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody: RequestBody = RequestBody.create(mediaType, text)
        val request: Request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val client = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val response = client
            .newCall(request)
            .execute()

        if (!response.isSuccessful) {
            println("Request failed with status code: ${response.code}")
        }
        val responseBody = response.body
        val responseBodyString = responseBody?.string() //as Map<*, *>
        val results = JSONObject(responseBodyString)
        return createTransferObject(results.toString())
    }

    /**
     *     Replace special characters with percent-encoded values
     */
    private fun encodeQuery(query: String): String {
        return query.replace(" ".toRegex(), "%20").replace("\"".toRegex(), "%22") // Encode double quotes
            .replace(",".toRegex(), "%2C") // Encode commas
            .replace(":".toRegex(), "%3A") // Encode colons
            .replace("\\{".toRegex(), "%7B") // Encode left brace
            .replace("\\}".toRegex(), "%7D") // Encode right brace
            .replace("&".toRegex(), "%26") // Encode ampersands
    }

    companion object Factory {
        fun createTransferObject(content: String?): StanfordCoreNlpTO {
            val transferObject: StanfordCoreNlpTO
            try {
                val objectMapper = ObjectMapper()
                transferObject = objectMapper.readValue(content, StanfordCoreNlpTO::class.java)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
            return transferObject
        }
    }

}
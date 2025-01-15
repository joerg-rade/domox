package domox

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class StanfordCoreNlpAPI {

    fun annotate(text: String) {
        val annotators = "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref"
        val url = "http://localhost:8090/?properties={\"annotators\":\"$annotators\"}"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody: RequestBody = RequestBody.create(mediaType, text)
        val request: Request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        val response = client.newCall(request).execute()

        println("Url: " + url)
        println("Text: " + text)
        if (response.isSuccessful) {
            val responseBody = response.body?.string() //as Map<*, *>
            val results = JSONObject(responseBody)

            // Process the results as needed
            val sentences = results.getJSONArray("sentences")
            for (i in 0 until sentences.length()) {
                val sentence = sentences.getJSONObject(i)
                val tokens = sentence.getJSONArray("tokens")
                for (j in 0 until tokens.length()) {
                    val token = tokens.getJSONObject(j)
                    val originalText = token.getString("originalText")
                    val pos = token.getString("pos")
                    println("Token: $originalText, POS: $pos")
                }
            }
        } else {
            println("Request failed with status code: ${response.code}")
        }
    }

}
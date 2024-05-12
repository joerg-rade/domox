package domox

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class SparkNlpAPI {

    fun annotate(text: String?) {
        // Define the annotators to be used
        val annotators = listOf("token", "lemma")

        // Create the request payload
        val payload = JSONObject()
        payload.put("text", text)
        payload.put("annotators", annotators)

        // Make the REST call to the Spark NLP server
        val client = OkHttpClient()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("http://localhost:9000/annotate")
            .post(requestBody)
            .build()
        val response = client.newCall(request).execute()

        // Parse the response
        val responseBody = response.body as Map<*, *>
        val annotatedData = JSONObject(responseBody) as JSONArray

        if (response.isSuccessful) {
            val responseBody = response.body as Map<*, *>
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
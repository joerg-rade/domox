package domox

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.simple.JSONArray
import org.json.simple.JSONObject

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

        // Access the annotated results
//        val sentences = annotatedData.get("sentences")
        //       val tokens = sentences.getJSONObject(0).getJSONArray("tokens")
        //     val lemmas = (0 until tokens.length()).map { tokens.getJSONObject(it).getString("lemma") }

        // Print the lemmas
        //   println(lemmas)
    }

}
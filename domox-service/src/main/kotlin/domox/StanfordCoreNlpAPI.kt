package domox

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class StanfordCoreNlpAPI {
//    var pipeline:StanfordCoreNLP
    init {
 /*       try {
            val props = Properties()
            //            props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref,kbp,quote");
            props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref")
            props.setProperty("coref.algorithm", "neural")
            pipeline = StanfordCoreNLP(props)
        } catch (e: Exception) {
            throw RuntimeException("Exception occurred in creating NLP pipeline")
        }*/
    }

    fun annotate(text: String?) {
        // Define the annotators to be used
        val annotators = "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref"

        // Create the request payload
        val payload = JSONObject()
        payload.put("text", text)
        payload.put("annotators", annotators)

        // Make the REST call to the Spark NLP server
        val client = OkHttpClient()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("http://localhost:8090/annotate")
            .post(requestBody)
            .build()
        val response = client.newCall(request).execute()

        if (response.isSuccessful) {
            val responseBody = response.body?.string() //as Map<*, *>
            println("ResponseBody")
            println(responseBody)
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
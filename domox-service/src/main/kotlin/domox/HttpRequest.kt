package domox

import com.github.kittinunf.fuel.httpPost

class HttpRequest {

    fun invokeAnonymous(url: String, arg: String): String {
        val (request, response, result) = url
                .httpPost()
                .body(arg)
                .responseString()
        val answer = result.get()
        return answer
    }

}
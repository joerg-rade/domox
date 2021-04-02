package domox

import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*

class HtmlReader {

    fun extractContentFromUrl(url: String): String? {
        val rawContent = readStringFromURL(url)
        return html2text(rawContent)
    }

    private fun readStringFromURL(requestURL: String): String {
        var stream: InputStream? = null
        try {
            stream = URL(requestURL).openStream()
        } catch (exception: IOException) {
            exception.printStackTrace()
        }
        val csName = StandardCharsets.UTF_8.toString()
        Scanner(stream, csName).use { scanner ->
            scanner.useDelimiter("\\A")
            return if (scanner.hasNext()) scanner.next() else ""
        }
    }

    private fun html2text(html: String): String? {
        var out = html
        out = out.replace("<h2>Table of Contents[\\s\\S]*?Introduction</h1></a>".toRegex(), "")
        out = out.replace("<a [\\s\\S]*?</a>".toRegex(), "")
        // out = out.replaceAll("<li>Section[\\s\\S]*?<br>", "");
        out = out.replace("<h1>[\\s\\S]*?</h1>".toRegex(), "")
        out = out.replace("<h2>[\\s\\S]*?</h2>".toRegex(), "")
        out = out.replace("<h3>[\\s\\S]*?</h3>".toRegex(), "")
        out = out.replace("<dt>[\\s\\S]*?</dt>".toRegex(), "")
        out = out.replace("<title>[\\s\\S]*?</title>".toRegex(), "")
        //       out = out.replaceAll("<[^>]+>", "");
        return out.replace("&nbsp;".toRegex(), " ")
    }

}
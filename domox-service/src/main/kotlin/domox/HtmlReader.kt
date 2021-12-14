package domox

import org.apache.tika.exception.TikaException
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.BodyContentHandler
import org.xml.sax.SAXException
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

    @Throws(IOException::class, SAXException::class, TikaException::class)
    fun parseToPlainText(url: String): String? {
        val handler = BodyContentHandler()
        val parser = AutoDetectParser()
        val metadata = Metadata()
        URL(url).openStream().use { stream ->
            parser.parse(stream, handler, metadata)
            return handler.toString()
        }
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
        out = out.replace("<!DOCTYPE[\\s\\S]*?>".toRegex(), "")
        out = out.replace("<head>[\\s\\S]*?</head>".toRegex(), "")
        out = out.replace("<BODY[\\s\\S]*?>".toRegex(), "")
        out = out.replace("<h2>Table of Contents[\\s\\S]*?Introduction</h1></a>".toRegex(), "")
        out = out.replace("<a [\\s\\S]*?</a>".toRegex(), "")
        // out = out.replaceAll("<li>Section[\\s\\S]*?<br>", "");
        out = out.replace("<h1>[\\s\\S]*?</h1>".toRegex(), "")
        out = out.replace("<h2>[\\s\\S]*?</h2>".toRegex(), "")
        out = out.replace("<h3>[\\s\\S]*?</h3>".toRegex(), "")
        out = out.replace("<dt>[\\s\\S]*?</dt>".toRegex(), "")
        out = out.replace("<title>[\\s\\S]*?</title>".toRegex(), "")
        //       out = out.replaceAll("<[^>]+>", "");
        out = out.replace("<li>".toRegex(), ".")
        out = out.replace("<br>".toRegex(), ".")
        out = out.replace("<p>".toRegex(), "")

        out = out.replace("<ul>".toRegex(), ".")
        out = out.replace("</ul>".toRegex(), ".")
        out = out.replace("<dl>".toRegex(), "")
        out = out.replace("</dl>".toRegex(), "")
        out = out.replace("<dd>".toRegex(), ".")
        out = out.replace("</dd>".toRegex(), ".")

        out = out.replace("<table>".toRegex(), "")
        out = out.replace("</table>".toRegex(), "")
        out = out.replace("<th [\\s\\S]*?>".toRegex(), "")
        out = out.replace("</th>".toRegex(), ".")
        out = out.replace("<tr>".toRegex(), "")
        out = out.replace("</tr>".toRegex(), "")
        out = out.replace("<td>".toRegex(), "")
        out = out.replace("</td>".toRegex(), ".")

        out = out.replace("<html>".toRegex(), "")
        out = out.replace("</html>".toRegex(), "")
        out = out.replace("</body>".toRegex(), "")
        out = out.replace("..", ".")

        return out.replace("&nbsp;".toRegex(), " ")
    }

}
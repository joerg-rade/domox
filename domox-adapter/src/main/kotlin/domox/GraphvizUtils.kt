package domox

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class GraphvizUtils {
    /**
     * Generates an SVG diagram from Graphviz/DOT source using Kroki.
     *
     * @param dotCode The raw Graphviz DOT source code.
     * @param layout The Graphviz layout engine ("dot", "fdp", "neato", "circo", "twopi", "sfdp").
     * @param host The Kroki server host.
     * @param port The Kroki server port.
     * @return The rendered SVG output as a String.
     */
    fun generateDiagram(
        dotCode: String,
        layout: String = "fdp",
        host: String = "localhost",
        port: Int = 8800
    ): String {
        val url = "http://$host:$port/graphviz/svg"

        // Sanitize JSON values (escapes quotes, backslashes, and newlines)
        val jsonPayload = """
        {
          "diagram_source": ${escapeJsonString(dotCode)},
          "diagram_options": {
            "layout": ${escapeJsonString(layout)}
          }
        }
    """.trimIndent()

        val client = HttpClient.newBuilder().build()
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw RuntimeException("Kroki rendering failed [HTTP ${response.statusCode()}]: ${response.body()}")
        }

        return response.body()
    }

    /**
     * Helper function to properly escape string literals for JSON formatting.
     */
    private fun escapeJsonString(input: String): String {
        val escaped = input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
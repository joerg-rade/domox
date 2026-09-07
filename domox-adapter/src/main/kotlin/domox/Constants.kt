package domox

object Constants {
    const val pdfMimeType = "application/pdf"
    const val stdMimeType = "text/plain"
    const val svgMimeType = "image/svg+xml"
    const val pngMimeType = "image/png"
    const val jsonMimeType = "application/json"
    const val actionSeparator = "\n"
    const val subTypeJson = "json"
    const val subTypeXml = "xml"

    const val plantUmlUrl = "http://localhost:8001"
    //host:port depend on how docker is started
    // docker compose: -p "8001:8000" yuzutech/kroki
    // docker run:     -p 8001:8000 yuzutech/kroki
    const val coreNlpPort = 8999
    const val coreNlpHost = "localhost"
    const val coreNlpScheme = "http"

}

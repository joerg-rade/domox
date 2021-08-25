package domox

object Constants {
    const val stdMimeType = "text/plain"
    const val svgMimeType = "image/svg+xml"
    const val jsonMimeType = "application/json"
    const val actionSeparator = "\n"
    const val subTypeJson = "json"
    const val subTypeXml = "xml"

    const val plantUmlUrl = "http://localhost:8000"
    //host:port depend on how docker is started
    // docker run -d --name kroki -p 8080:8000 yuzutech/kroki
    const val coreNlpUrl = "http://localhost:9000"

}

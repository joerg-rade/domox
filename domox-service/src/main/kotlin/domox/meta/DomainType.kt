package domox.meta

data class DomainType(
        val links: List<Link>,
        val canonicalName: String,
        val members: List<Link>,
        val typeActions: List<Link>,
//        val extensions: Extensions
)

data class Link(val rel: String = "",
                val method: String = "Method.GET.operation",
                val href: String,
                val type: String = "",
        //IMPROVE RO SPEC? "args" should be changed to "arguments" - RO spec or SimpleApp?
                val args: Map<String, String> = emptyMap(),
        /* arguments can either be:
         * -> empty Map {}
         * -> Map with "value": null (cf. SO_PROPERTY)
         * -> Map with empty key "" (cf. ACTIONS_DOWNLOAD_META_MODEL)
         * -> Map with key,<VALUE> (cf. ACTIONS_RUN_FIXTURE_SCRIPT, ACTIONS_FIND_BY_NAME, ACTIONS_CREATE) */
                val arguments: Map<String, String?> = emptyMap(),
                val title: String = "")

data class LogEntry(val url: String)

data class DiagramDM(val classes: Set<DomainType>)

class ResourceSpecification(
        val url: String,
        val subType: String)

data class Argument(var key: String = "",
                    var value: String? = null,
                    val potFileName: String = "",
                    val href: String? = null) {
    init {
        if (value == null) {
            value = ""
        }
    }

}

object Constants {

    const val stdMimeType = "text/plain"
    const val svgMimeType = "image/svg+xml"
    const val calcHeight = "calc(100vh - 88px)"
    const val actionSeparator = "\n"
    const val subTypeJson = "json"
    const val subTypeXml = "xml"

    const val plantUmlUrl = "https://kroki.io/" //see: https://github.com/yuzutech/kroki
    //const val plantUmlUrl = "http://localhost:8080/"
    //host:port depend on how docker is started
    // docker run -d --name kroki -p 8080:8000 yuzutech/kroki

}

enum class Method(val operation: String) {
    GET("GET"),
    PUT("PUT"),
    POST("POST"),
//    DELETE("DELETE")  not used - Apache Isis defines delete operations on DomainObjects
}

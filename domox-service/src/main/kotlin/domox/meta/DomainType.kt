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

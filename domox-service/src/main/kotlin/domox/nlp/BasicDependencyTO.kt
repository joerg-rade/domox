package domox.nlp

data class BasicDependencyTO(
    val dep: String = "",
    val governor: Long = 0L,
    val governorGloss: String = "",
    val dependent: Long = 0L,
    val dependentGloss: String = ""
)

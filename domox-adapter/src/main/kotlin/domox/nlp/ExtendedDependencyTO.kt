package domox.nlp

data class ExtendedDependencyTO(
    val dep: String = "",
    val governor: Long = 0L,
    val governorGloss: String = "",
    val governorPos: String = "",
    val governorLemma: String = "",
    val dependent: Long = 0L,
    val dependentGloss: String = "",
    val dependentPos: String = "",
    val dependentLemma: String = ""
)
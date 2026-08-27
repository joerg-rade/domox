package domox.nlp

/**
 * A factory for creating extended dependency objects.
 * Amends the basic dependency objects with additional information such as part-of-speech tags.
 * @param sentenceTO The sentence transfer object containing the basic dependencies and tokens.
 */
class ExtendedDependencyFactory(sentenceTO: SentenceTO) {
    private val dependencies: List<ExtendedDependencyTO>

    init {
        val eppDeps = sentenceTO.enhancedPlusPlusDependencies
        val toks = sentenceTO.tokens
        if (eppDeps.isEmpty() || toks.isEmpty()) {
            throw IllegalArgumentException("The sentence must contain at least one dependency.")
        }
        dependencies = eppDeps.map { dep ->
            val dependentToken = toks.find { it.index == dep.dependent }
            ExtendedDependencyTO(
                dep = dep.dep,
                governor = dep.governor,
                governorGloss = dep.governorGloss,
                governorPos = toks.find { it.index == dep.governor }?.pos ?: "",
                dependent = dep.dependent,
                dependentGloss = dep.dependentGloss,
                pos = dependentToken?.pos ?: ""
            )
        }
    }

    fun getDependencies(): List<ExtendedDependencyTO> {
        return dependencies
    }
}
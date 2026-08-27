package domox.nlp

import com.fasterxml.jackson.annotation.JsonIgnore

data class SentenceTO(
    val index: Long = 0L,
    val parse: String = "",
    val basicDependencies: List<BasicDependencyTO> = emptyList(),
    val enhancedDependencies: List<BasicDependencyTO> = emptyList(),
    val enhancedPlusPlusDependencies: List<BasicDependencyTO> = emptyList(),
    val sentimentValue: String = "",
    val sentiment: String = "",
    val sentimentDistribution: List<Long> = emptyList(),
    val sentimentTree: String = "",
    @JsonIgnore
    val entitymentions: List<EntityMentionTO>? = null,
    val tokens: List<TokenTO> = emptyList()
)

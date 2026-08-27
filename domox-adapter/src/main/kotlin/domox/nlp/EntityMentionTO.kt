package domox.nlp

import com.fasterxml.jackson.annotation.JsonIgnore

data class EntityMentionTO(
    val characterOffsetBegin: Long = 0L,
    val characterOffsetEnd: Long = 0L,
    val tokenEnd: String = "",
    val ner: String = "",
    val text: String = "",
    val normalizedNER: String? = null,
    val docTokenBegin: String? = null,
    val tokenBegin: String? = null,
    val docTokenEnd: String? = null,
    @JsonIgnore
    val nerConfidences: NerConfidenceTO? = null
)

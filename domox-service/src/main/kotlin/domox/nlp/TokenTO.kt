package domox.nlp

import com.fasterxml.jackson.annotation.JsonIgnore

data class TokenTO(
    val index: Long = 0L,
    val word: String = "",
    val originalText: String = "",
    val lemma: String = "",
    val characterOffsetBegin: Long = 0L,
    val characterOffsetEnd: Long = 0L,
    val pos: String = "",
    val ner: String = "",
    val before: String = "",
    val after: String = "",
    val speaker: String = "",
    val normalizedNER: String? = null,
    @JsonIgnore
    val timex: TimexTO? = null
)

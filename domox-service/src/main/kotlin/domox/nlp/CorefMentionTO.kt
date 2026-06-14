package domox.nlp

data class CorefMentionTO(
    val number: String? = null,
    val startIndex: Int = 0,
    val gender: String? = null,
    val sentNum: Int = 0,
    val endIndex: Int = 0,
    val isRepresentativeMention: Boolean = false,
    val animacy: String? = null,
    val id: Int = 0,
    val text: String? = null,
    val position: List<Int>? = null,
    val type: String? = null,
    val headIndex: Int = 0
)


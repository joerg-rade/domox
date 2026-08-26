package domox.diagram

import domox.nlp.ExtendedDependencyTO
import domox.nlp.SentenceTO
import kotlin.collections.sortedBy

class ColoredPlantUmlMindmapGenerator(private val sentence: SentenceTO) {
    private data class TreeNode(
        val index: Int,
        val word: String,
        val depRelation: String,
        val pos: String,
        val children: MutableList<TreeNode> = mutableListOf()
    )

    private var dependencies: List<ExtendedDependencyTO>

    init {
        val eppDeps = sentence.enhancedPlusPlusDependencies
        val toks = sentence.tokens
        if (eppDeps.isEmpty() || toks.isEmpty()) {
            throw IllegalArgumentException("The sentence must contain at least one dependency.")
        }
        dependencies = eppDeps.map { dep ->
            val dependentToken = toks.find { it.index == dep.dependent }
            ExtendedDependencyTO(
                dep = dep.dep,
                governor = dep.governor,
                governorGloss = dep.governorGloss,
                dependent = dep.dependent,
                dependentGloss = dep.dependentGloss,
                pos = dependentToken?.pos ?: ""
            )
        }
    }

    fun generateMindmap(): String {
        val rootDep = dependencies.firstOrNull { it.governor == 0.toLong() }
            ?: return "@startmindmap\n* Error: No Root Found\n@endmindmap"

        val childrenMap = dependencies.groupBy { it.governor }

        fun buildTree(currentDep: ExtendedDependencyTO, visited: MutableSet<Long> = mutableSetOf()): TreeNode {
            val node = TreeNode(
                index = currentDep.dependent.toInt(),
                word = currentDep.dependentGloss,
                depRelation = currentDep.dep,
                pos = currentDep.pos
            )

            // Detect cycles to prevent infinite recursion
            if (visited.contains(currentDep.dependent)) {
                return node
            }
            visited.add(currentDep.dependent)

            val childDeps = childrenMap[currentDep.dependent] ?: emptyList()
            for (childDep in childDeps.sortedBy { it.dependent }) {
                // Skip if this child was already processed as a different dependency type
                if (!visited.contains(childDep.dependent)) {
                    node.children.add(buildTree(childDep, visited))
                }
            }
            return node
        }

        val treeRoot = buildTree(rootDep)

        val builder = StringBuilder()
        builder.append(header())

        fun reconstructText(): String {
            val uniqueWords = mutableMapOf<Int, String>()
            for (dep in dependencies.sortedBy { it.dependent }) {
                uniqueWords[dep.dependent.toInt()] = dep.dependentGloss
            }
            return uniqueWords.toSortedMap().values.joinToString(" ")
        }
        builder.appendLine("caption: ${reconstructText()}")

        fun renderNode(node: TreeNode, depth: Int) {
            val stars = "*".repeat(depth)
            val posColor = getPosColor(node.pos)
            val depIcon = getDepIcon(node.depRelation)

            // Use POS color for background and dependency icon for visual encoding
            builder.appendLine("$stars[$posColor]:<U+0023>${node.index} / <i>${node.pos}")
            builder.appendLine("<size:16><b>${node.word}</b></size>")
            builder.appendLine("----")
            builder.appendLine("$depIcon <i>${node.depRelation}</i>;")

            for (child in node.children) {
                renderNode(child, depth + 1)
            }
        }

        renderNode(treeRoot, 1)
        builder.appendLine(legend())
        builder.appendLine("@endmindmap")

        return builder.toString()
    }

    private fun header(): String {
        val builder = StringBuilder()
        builder.appendLine("@startmindmap")
        builder.appendLine("!include <tupadr3/font-awesome/square>")
        builder.appendLine("!include <tupadr3/font-awesome/th>")
        builder.appendLine("!include <tupadr3/font-awesome/play>")
        builder.appendLine("!include <tupadr3/font-awesome/plus>")
        builder.appendLine("!include <tupadr3/font-awesome/flag>")
        builder.appendLine("!include <tupadr3/font-awesome/user>")
        builder.appendLine("!include <tupadr3/font-awesome/cube>")
        builder.appendLine("!include <tupadr3/font-awesome/cubes>")
        builder.appendLine("!include <tupadr3/font-awesome/sliders>")
        builder.appendLine("!include <tupadr3/font-awesome/tag>")
        builder.appendLine("!include <tupadr3/font-awesome/link>")
        builder.appendLine("!include <tupadr3/font-awesome/circle>")
        builder.appendLine("!include <tupadr3/font-awesome/question>")
        builder.appendLine("!include <tupadr3/font-awesome/sitemap>")
        builder.appendLine("!include <tupadr3/font-awesome/crosshairs>")
        return builder.toString()
    }

    /**
     * Maps dependency relations to intuitive Font Awesome icons.
     */
    fun getDepIcon(depRelation: String): String = when (depRelation) {
        "ROOT" -> "<\$flag{scale=0.5}>"          // Flag for root
        "nsubj" -> "<\$user{scale=0.5}>"         // User for subject
        "dobj", "obj", "iobj" -> "<\$cube{scale=0.5}>"  // Cube for objects
        "amod", "advmod" -> "<\$sliders{scale=0.5}>"  // Sliders for modifiers
        "compound" -> "<\$cubes{scale=0.5}>"     // Cubes for compounds
        "det" -> "<\$tag{scale=0.5}>"           // Tag for determiners
        "case" -> "<\$sitemap{scale=0.5}>"         // Sitemap for prepositions/cases
        "punct" -> "<\$circle{scale=0.5}>"       // Circle for punctuation
        "nmod:of", "nmod:about", "nmod:for", "nmod:with", "nmod:from", "nmod:as" -> "<\$link{scale=0.5}>"       // Road for nmod:of
        else -> "<\$question{scale=0.5}>"       // Question for others
    }

    private fun legend(): String {
        return """
legend left
  | Icon  | Dependency | | Color | PartOfSpeech |
  | <${'$'}flag{scale=0.5}> | ROOT | | <#3498DB> Nouns | NN, NNS |
  | <${'$'}user{scale=0.5}> | nsubj | | <#E74C3C> Verbs | VB, VBZ, VBD, VBG, VBN, VBP |
  | <${'$'}cube{scale=0.5}> | dobj, obj, iobj | | <#2ECC71> Adjectives | JJ, JJR, JJS |
  | <${'$'}sliders{scale=0.5}> | amod, advmod | | | |
  | <${'$'}link{scale=0.5}> | nmod:* | | | |
  | <${'$'}cubes{scale=0.5}> | compound | | <#9B59B6> Pronouns | PRP, PRP$ |
  | <${'$'}tag{scale=0.5}> | det | | <#F39C12> Determiners | DT |
  | <${'$'}sitemap{scale=0.5}> | case | | <#17A2B8> Prepositions | IN |
  | <${'$'}circle{scale=0.5}> | punct | | <#FFFFFF> Punctuation | , . ! ? ; : |
  | | | | <#FFBB28> adverbs | RB |
  | | | | <#F1C40F> numerals| CD |
  | | | | <#00C49F> coordinating conjunctions | CC |
  | | | | <#9B59B6> wh-determiners, adverbs, pronouns | WDT, WRB, WP, WP$, WRB |
  | | | | <#2C3E50> proper nouns | NNP, NNPS |
  | <${'$'}question{scale=0.5}> | others | | <#AAB7B8> Others |  |
endlegend
        """.trimIndent()
    }

    /**
     * Maps Part-of-Speech (POS) tags to standard color hex codes.
     * Uses Universal Dependencies color conventions for grammatical categories.
     */
    fun getPosColor(pos: String): String = when (pos) {
        "NN", "NNS" -> "#3498DB"     // Blue for nouns
        "VB", "VBZ", "VBD", "VBG", "VBN", "VBP" -> "#E74C3C"  // Red for verbs
        "JJ", "JJR", "JJS" -> "#2ECC71"  // Green for adjectives
        "DT" -> "#F39C12"          // Orange for determiners
        "PRP", "PRP$" -> "#9B59B6"  // Purple for pronouns
        "IN" -> "#17A2B8"          // Gray for prepositions
        "RB" -> "#FFBB28"          // Amber for adverbs
        "CD" -> "#F1C40F"      // Yellow for numerals
        "CC" -> "#00C49F"          // Teal for coordinating conjunctions
        "WDT", "WP", "WP$", "WRB" -> "#9B59B6"          // Soft Purple for wh-determiners, adverbs, pronouns
        "NNP", "NNPS" -> "#2C3E50"     // Deep Blue for proper nouns
        ",", ".", "!", "?", ";", ":" -> "#FFFFFF"  // White for punctuation
        else -> "#AAB7B8"          // Neutral gray for others
    }

}
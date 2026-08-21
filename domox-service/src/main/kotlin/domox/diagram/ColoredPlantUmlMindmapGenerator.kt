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

    /**
     * Maps Universal Dependency relations to standard color hex codes.
     */
    fun getDepColor(depRelation: String): String = when (depRelation) {
        "ROOT", "root" -> "#E74C3C"                                  // Crimson (Root)
        "nsubj", "dobj", "obj", "iobj", "csubj" -> "#3498DB"         // Blue (Core Arguments)
        "amod", "advmod", "compound", "nummod" -> "#2ECC71"         // Green (Modifiers)
        "det", "case", "mark", "cc", "aux", "cop" -> "#F39C12"       // Amber (Function Words)
        "parataxis", "advcl", "advcl:to", "xcomp" -> "#9B59B6"      // Purple (Clauses)
        "punct" -> "#BDC3C7"                                         // Grey (Punctuation)
        else -> "#AAB7B8"                                           // Fallback Neutral
    }

    /**
     * Maps Universal Dependency relations to standard color hex codes.
     */
    fun getPosSymbol(pos: String): String = when (pos) {
        "NN" -> "<\$square{scale=0.3}>"
        "NNS" -> "<\$th{scale=0.3}>"
        "JJ" -> "<\$plus{scale=0.3}>"
        "VB", "VBZ" -> "<\$play{scale=0.3}>"
        else -> "<\$question{scale=0.3}>"
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
                node.children.add(buildTree(childDep, visited))
            }
            return node
        }

        val treeRoot = buildTree(rootDep)

        val builder = StringBuilder()
        builder.appendLine("@startmindmap")
        builder.appendLine("!include <tupadr3/font-awesome/square>")
        builder.appendLine("!include <tupadr3/font-awesome/th>")
        builder.appendLine("!include <tupadr3/font-awesome/play>")
        builder.appendLine("!include <tupadr3/font-awesome/plus>")

        fun reconstructText(): String {
            val answer = StringBuilder()
            for (dep in dependencies.sortedBy { it.dependent }) {
                answer.append(dep.dependentGloss).append(" ")
            }
            return answer.toString().trim()
        }
        builder.appendLine("caption: ${reconstructText()}")

        fun renderNode(node: TreeNode, depth: Int) {
            val stars = "*".repeat(depth)
            val colorHex = getDepColor(node.depRelation)
            val posSymbol = getPosSymbol(node.pos)

            // Appends [#HEX] to style the background box of the mindmap node
            builder.appendLine("$stars[$colorHex]:<U+0023>${node.index} / <i>${node.depRelation}</i>")
            builder.appendLine("$posSymbol <b>${node.word}</b>")
            builder.appendLine("${node.pos};")

            for (child in node.children) {
                renderNode(child, depth + 1)
            }
        }

        renderNode(treeRoot, 1)
        builder.appendLine("@endmindmap")

        return builder.toString()
    }


}

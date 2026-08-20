data class BasicDependencyTO(
    val dep: String,
    val governor: Int,
    val governorGloss: String,
    val dependent: Int,
    val dependentGloss: String
)

class ColoredPlantUmlMindmapGenerator(private val dependencies: List<BasicDependencyTO>) {

    private data class TreeNode(
        val index: Int,
        val word: String,
        val depRelation: String,
        val children: MutableList<TreeNode> = mutableListOf()
    )

    /**
     * Maps Universal Dependency relations to standard color hex codes.
     */
    private fun getDepColor(depRelation: String): String = when (depRelation) {
        "ROOT", "root" -> "#E74C3C"                                  // Crimson (Root)
        "nsubj", "dobj", "obj", "iobj", "csubj" -> "#3498DB"         // Blue (Core Arguments)
        "amod", "advmod", "compound", "nummod" -> "#2ECC71"         // Green (Modifiers)
        "det", "case", "mark", "cc", "aux", "cop" -> "#F39C12"       // Amber (Function Words)
        "parataxis", "advcl", "advcl:to", "xcomp" -> "#9B59B6"      // Purple (Clauses)
        "punct" -> "#BDC3C7"                                         // Grey (Punctuation)
        else -> "#AAB7B8"                                           // Fallback Neutral
    }

    fun generateMindmap(): String {
        val rootDep = dependencies.firstOrNull { it.governor == 0 }
            ?: return "@startmindmap\n* Error: No Root Found\n@endmindmap"

        val childrenMap = dependencies.groupBy { it.governor }

        fun buildTree(currentDep: BasicDependencyTO): TreeNode {
            val node = TreeNode(
                index = currentDep.dependent,
                word = currentDep.dependentGloss,
                depRelation = currentDep.dep
            )
            val childDeps = childrenMap[currentDep.dependent] ?: emptyList()
            for (childDep in childDeps.sortedBy { it.dependent }) {
                node.children.add(buildTree(childDep))
            }
            return node
        }

        val treeRoot = buildTree(rootDep)

        val builder = StringBuilder()
        builder.appendLine("@startmindmap")
        builder.appendLine("caption Color-Coded Dependency Syntax Tree")

        fun renderNode(node: TreeNode, depth: Int) {
            val stars = "*".repeat(depth)
            val colorHex = getDepColor(node.depRelation)

            // Appends [#HEX] to style the background box of the mindmap node
            builder.appendLine("$stars[$colorHex]:<b>${node.word}</b>")
            builder.appendLine("dep: ${node.depRelation}")
            builder.appendLine("dependent: ${node.index};")

            for (child in node.children) {
                renderNode(child, depth + 1)
            }
        }

        renderNode(treeRoot, 1)
        builder.appendLine("@endmindmap")

        return builder.toString()
    }
}

fun main() {
    val dependencies = listOf(
        BasicDependencyTO("ROOT", 0, "ROOT", 4, "photography"),
        BasicDependencyTO("nummod", 4, "photography", 1, "11"),
        BasicDependencyTO("punct", 4, "photography", 2, "-RRB-"),
        BasicDependencyTO("compound", 4, "photography", 3, "Pet"),
        BasicDependencyTO("punct", 4, "photography", 5, ":"),
        BasicDependencyTO("det", 8, "shops", 6, "Some"),
        BasicDependencyTO("compound", 8, "shops", 7, "pet"),
        BasicDependencyTO("nsubj", 9, "offer", 8, "shops"),
        BasicDependencyTO("parataxis", 4, "photography", 9, "offer"),
        BasicDependencyTO("amod", 12, "services", 10, "pet"),
        BasicDependencyTO("compound", 12, "services", 11, "photography"),
        BasicDependencyTO("dobj", 9, "offer", 12, "services"),
        BasicDependencyTO("mark", 14, "capture", 13, "to"),
        BasicDependencyTO("advcl:to", 9, "offer", 14, "capture"),
        BasicDependencyTO("amod", 16, "moments", 15, "memorable"),
        BasicDependencyTO("dobj", 14, "capture", 16, "moments"),
        BasicDependencyTO("case", 18, "pets", 17, "with"),
        BasicDependencyTO("nmod:with", 16, "moments", 18, "pets"),
        BasicDependencyTO("punct", 4, "photography", 19, ".")
    )

    val generator = ColoredPlantUmlMindmapGenerator(dependencies)
    println(generator.generateMindmap())
}
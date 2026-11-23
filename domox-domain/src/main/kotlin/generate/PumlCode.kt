package generate

class PumlCode() : BaseCode() {

    fun add(s: String): PumlCode {
        code += s
        return this
    }

    /**
     * Appends {@code s} as a complete line — the trailing newline [add] deliberately leaves to its
     * caller (the class body is assembled from [add]/[addBegin]/[addEnd], so only the callers that
     * emit a standalone line need this).
     */
    fun addLine(s: String): PumlCode {
        code += s + NL
        return this
    }

    fun addBegin(): PumlCode {
        code += " {" + NL
        return this
    }

    fun addEnd(): PumlCode {
        code += "}" + NL
        return this
    }

    fun addClass(s: String): PumlCode {
        val result = ("class \"$s\" as $s")
        code += result
        return this
    }

    fun addProperty(s: String): PumlCode {
        code += TAB + s + NL
        return this
    }

    fun addResponsibility(s: String): PumlCode {
        val result = ("$s")
        code += TAB + result + NL
        return this
    }

    fun addCollaborator(s: String): PumlCode {
        return addLine(s)
    }

    fun asUml(): PumlCode {
        code = "@startuml$NL$code@enduml"
        return this
    }

    fun trim(): PumlCode {
        if (code.endsWith(NL)) {
            code = code.dropLast(1)
        }
        return this
    }

}

package diagram

class PumlCode() {

    private val NL = "\n"
    private val TAB = "\t"

    var code = ""

    fun add(s: String): PumlCode {
        code += s
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

    fun addAction(s: String): PumlCode {
        val result = ("$s")
        code += TAB + result + NL
        return this
    }

    fun addAssociation(s: String): PumlCode {
        code += s + NL
        return this
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

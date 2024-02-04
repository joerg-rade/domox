/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */package diagram

class PumlCode() {

    private val NL = "\n"

    var code = ""

    fun add(s: String): PumlCode {
        code += s
        return this
    }

    fun addBegin(): PumlCode {
        code += "{" + NL
        return this
    }

    fun addEnd(): PumlCode {
        code += "}" + NL
        return this
    }

    fun addClass(s: String): PumlCode {
        val result = ("class \"(C) $s\" as $s")
        code += result + NL
        return this
    }

    fun addProperty(s: String): PumlCode {
        code += s + NL
        return this
    }

    fun addAction(s: String): PumlCode {
        val result = ("$s()")
        code += result + NL
        return this
    }

    fun addAssociation(className: String, ascName: String): PumlCode {
        val result = className + " -> " + ascName
        code += result + NL
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

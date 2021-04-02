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
 */
package domox.diagram

import domox.meta.*
import org.apache.isis.client.kroviz.core.model.meta.MetaClass
import org.apache.isis.client.kroviz.core.model.meta.MetaPackage

class PumlBuilder {

    private val Q = "\""
    private val NL = "\\n"

    fun with(pkgList: Set<MetaPackage>): String {
        var code = "$Q@startuml$NL"
        pkgList.forEach { p ->
            code += amendByPackage(p)
        }
        code += "@enduml$Q"
        return code
    }

    private fun amendByPackage(pkg: MetaPackage): String {
        val packageName = pkg.name
        var code = "package $packageName {$NL"
        pkg.classes.forEach { cls ->
            code += amendByClass(cls)
        }
        code += "}$NL"
        return code
    }

    private fun amendByClass(cls: MetaClass): String {
        val className = cls.name
        var code = "class $className$NL"
        cls.properties.forEach { p ->
            code += "$className : ${p.name}$NL"
        }
        cls.actions.forEach { a ->
            code += "$className : ${a.name}()$NL"
        }
        return code
    }

    fun with(domainType: DomainType): String {
        val cn = domainType.canonicalName
        val cls = cn.split(".").last()
        val pkg = cn.replace(".$cls", "")
        var pumlCode = "$Q@startuml$NL package $pkg {$NL" +
                "class $cls$NL"
        domainType.members.forEach { m ->
/*            val memberName = m.name()
            pumlCode += when {
                m.isProperty() -> "$cls : $memberName$NL"
                else -> {
                    "$cls : $memberName()$NL"
                }
            }*/
        }
        pumlCode += "}$NL@enduml$Q"
        return pumlCode
    }

    fun withLogEntry(rootLE: LogEntry): String {
        var code = "$Q@startuml$NL"
//        code += iterateOverChildren(rootLE)
        code += "@enduml$Q"
        return code
    }

    private fun iterateOverChildren(logEntry: LogEntry): String {
        var code = ""
/*        val tObj = logEntry.obj
        val parentUrl = logEntry.url
        if (tObj is HasLinks) {
            tObj.getLinks().forEach { l ->
                val rel = l.relation()
                if (rel != Relation.UP && rel != Relation.SELF) {
                    code += amendWithChild(parentUrl, l)
                }
            }
        }*/
        return code
    }

    private fun amendWithChild(parentUrl: String, child: Link): String {
        // kroki.io can not handle / (slash) in strings; escaping doesn't work either
        val baseUrl = "UiManager.getUrl()"
        var source = parentUrl.replace(baseUrl, "")
        source = source.replace("/" , "_")
        val childUrl = "child.href"
        var target = childUrl.replace(baseUrl, "")
        target = target.replace("/" , "_")
        var code = "$source -> $target $NL"

        val rs = ResourceSpecification(childUrl, "")
        val childLE = LogEntry(baseUrl) //"EventStore.find(rs)"
        if (childLE != null) {
            code += iterateOverChildren(childLE)
        }
        return code
    }

}


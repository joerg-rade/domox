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

import domox.HttpRequest
import domox.meta.Constants

object UmlUtils {

    const val sampleCode = "\"" +
            "participant BOB [[https://en.wiktionary.org/wiki/best_of_breed]]\\n" +
            "participant PITA [[https://en.wiktionary.org/wiki/PITA]]\\n" +
            "BOB -> PITA: sometimes is a" +
            "\""

    fun sampleDiagram(): String {
        return generateDiagram(sampleCode)
    }

    fun generateDiagram(pumlCode: String): String {
        var arg = "{"
        arg += "\"diagram_source\":" + pumlCode + ","
        arg += "\"diagram_type\":" + "\"plantuml\","
        arg += "\"output_format\":" + "\"svg\""
        arg += "}"

        return HttpRequest().invokeAnonymous(Constants.plantUmlUrl, arg)
    }

}

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

import domox.nlp.SentenceTO
import domox.nlp.TokenTO

/**
 * Generates PlantUML code for a TypedDependency diagram in the style of a mindmap.
 * Inspired by mindmap.puml.
 */
object TdDiagramMindmap : PlantUmlDiagram() {

    private const val header = """
@startmindmap
top to bottom direction
"""

    fun build(sentenceTO: SentenceTO): String {
        var answer = header
        answer += "+ S\n"

        val phrases = groupTokensIntoPhrases(sentenceTO)

        phrases["NP_SUBJ"]?.let { tokens ->
            answer += "++ NP\n"
            tokens.sortedBy { it.index }.forEach { token ->
                answer += buildMindmapNode(token, 3)
            }
        }

        phrases["VP"]?.let { tokens ->
            answer += "++[#CHARTREUSE] VP\n"
            tokens.sortedBy { it.index }.forEach { token ->
                answer += buildMindmapNode(token, 3)
            }
        }

        phrases["NP_OBJ"]?.let { tokens ->
            answer += "++ NP\n"
            tokens.sortedBy { it.index }.forEach { token ->
                answer += buildMindmapNode(token, 3)
            }
        }

        phrases["CC"]?.let { tokens ->
            answer += "++[#WHITE] CC\n"
            tokens.sortedBy { it.index }.forEach { token ->
                answer += "+++_ ${token.word}\n"
            }
        }

        phrases["NN"]?.let { tokens ->
            tokens.sortedBy { it.index }.forEach { token ->
                answer += "++[#CYAN] NN\n"
                answer += "+++_ ${token.word}\n"
            }
        }

        phrases["."]?.let { tokens ->
            answer += "++[#LIGHTGREY] .\n"
            tokens.sortedBy { it.index }.forEach { token ->
                answer += "+++_ ${token.word}\n"
            }
        }

        return answer + "@endmindmap\n"
    }

    private fun groupTokensIntoPhrases(sentenceTO: SentenceTO): Map<String, List<TokenTO>> {
        val dependencies = sentenceTO.enhancedPlusPlusDependencies
        val tokens = sentenceTO.tokens
        val phrases = mutableMapOf<String, MutableList<TokenTO>>()
        val assignedTokens = mutableSetOf<Long>()

        val npSubjTokens = mutableListOf<TokenTO>()
        val npObjTokens = mutableListOf<TokenTO>()

        // Pass 0: conj:and becomes standalone N
        dependencies.forEach { dependency ->
            if (dependency.dep != "conj:and") return@forEach
            val dependentToken = tokens.find { it.index == dependency.dependent } ?: return@forEach
            if (dependentToken.pos.startsWith("NN")) {
                phrases.getOrPut("NN") { mutableListOf() }.add(dependentToken)
                assignedTokens.add(dependentToken.index)
            }
        }

        // Pass 1: Heads (nsubj, doj, cc, punct, ROOT
        dependencies.forEach { dependency ->
            if (dependency.dep == "ROOT") {
                val rootToken = tokens.firstOrNull { it.index == dependency.dependent } ?: return@forEach
                if (!assignedTokens.contains(rootToken.index)) {
                    phrases.getOrPut("VP") { mutableListOf() }.add(rootToken)
                    assignedTokens.add(rootToken.index)
                }
                return@forEach
            }

            val dependentToken = tokens.find { it.index == dependency.dependent } ?: return@forEach
            val governorToken = tokens.find { it.index == dependency.governor } ?: return@forEach

            if (assignedTokens.contains(dependentToken.index)) {
                return@forEach
            }

            when {
                dependency.dep == "nsubj" -> {
                    if (governorToken.pos.startsWith("V")) {
                        npSubjTokens.add(dependentToken)
                        assignedTokens.add(dependentToken.index)
                    }
                }
                dependency.dep == "dobj" || dependency.dep.startsWith("nmod") -> {
                    if (governorToken.pos.startsWith("V")) {
                        npObjTokens.add(dependentToken)
                        assignedTokens.add(dependentToken.index)
                    }
                }
                dependency.dep == "cc" -> {
                    phrases.getOrPut("CC") { mutableListOf() }.add(dependentToken)
                    assignedTokens.add(dependentToken.index)
                }
                dependency.dep == "punct" -> {
                    phrases.getOrPut(".") { mutableListOf() }.add(dependentToken)
                    assignedTokens.add(dependentToken.index)
                }
            }
        }

        // Pass 2: Modifiers (det, compound, amod)
        dependencies.forEach { dependency ->
            val dependentToken = tokens.find { it.index == dependency.dependent } ?: return@forEach
            val governorToken = tokens.find { it.index == dependency.governor } ?: return@forEach

            if (assignedTokens.contains(dependentToken.index)) {
                return@forEach
            }

            when (dependency.dep) {
                "det", "compound", "amod", "case" -> {
                    if (governorToken.pos.startsWith("N")) {
                        if (npSubjTokens.any{ it.index == governorToken.index }) {
                            npSubjTokens.add(dependentToken)
                            assignedTokens.add(dependentToken.index)
                        } else if (npObjTokens.any { it.index == governorToken.index }) {
                            npObjTokens.add(dependentToken)
                            assignedTokens.add(dependentToken.index)
                        }
                    }
                }
                else -> {
                    if (!assignedTokens.contains(dependentToken.index)) {
                        assignedTokens.add(dependentToken.index)
                    }
                }
            }
        }

        if (npSubjTokens.isNotEmpty()) {
            phrases["NP_SUBJ"] = npSubjTokens.sortedBy { it.index } as MutableList<TokenTO>
        }
        if (npObjTokens.isNotEmpty()) {
            phrases["NP_OBJ"] = npObjTokens.sortedBy { it.index } as MutableList<TokenTO>
        }

        return phrases
    }

    private fun buildMindmapNode(token: TokenTO, depth: Int): String {
        val pos = token.pos
        val word = token.word
        val color = findColor(pos)
        val indent = "+".repeat(depth)

        return "$indent[$color] $pos\n$indent+_ $word\n"
    }

    override fun findColor(posTag: String): String {
        return when {
            posTag.startsWith("VB") -> "#CHARTREUSE"
            posTag.equals("CC") -> "#WHITE"
            posTag.endsWith("DT") -> "#MAGENTA"
            posTag.startsWith("N") -> "#CYAN"
            posTag.equals(".") -> "#LIGHTGREY"
            else -> "#e3e3e3"
        }
    }
}
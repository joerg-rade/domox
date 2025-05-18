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

import domox.nlp.BasicDependencyTO
import domox.nlp.SentenceTO
import domox.nlp.TokenTO
import java.awt.Color

/**
 * Generates PlantUML code for a TypedDependency diagram.
 * see: /docs/gv2.puml for sample diagram
 */
object TdDiagramPuml {
    private const val header = """
@startuml
skinparam rectangleBackgroundColor white
skinparam rectangleFontSize 18
skinparam rectangleBorderColor transparent
skinparam ArrowFontSize 12
skinparam cardFontSize 14

skinparam ranksep 64
skinparam nodesep 20
skinparam linetype ortho
    """
    private const val wordTemplate =
        "rectangle \"$2\" as W$1\n" +
                "card \"$4\" as D$1 $3\n" +
                "D$1 -d-( W$1\n\n"
    private const val footer = "@enduml\n"
    private const val glue = "D$1 -r-> D$2 #transparent\n"

    fun build(sentenceTO: SentenceTO): String {
        var answer = header
        val tokenList = sentenceTO.tokens
        answer += buildWords(tokenList);
        answer += buildGlue(tokenList);
        answer += buildTypedDependencies(sentenceTO);
        return answer + footer
    }

    private fun buildWords(tokenList: List<TokenTO>): String {
        var answer = ""
        tokenList.forEachIndexed() { index, token ->
            val pos = token.pos
            val word = token.word
            answer += buildWord(index + 1, pos, word)
        }
        return answer
    }

    private fun buildGlue(tokenList: List<TokenTO>): String {
        var answer = ""
        tokenList.forEachIndexed() { index, _ ->
            if (index > 0) {
                val current = index.toString()
                val next = (index + 1).toString()
                var template = glue
                template = template.replace("$1", current)
                template = template.replace("$2", next)
                answer += template
            }
        }
        return answer
    }

    private fun buildTypedDependencies(sentenceTO: SentenceTO): String {
        var answer = "\n"
        val rawDependencyList: List<BasicDependencyTO> = sentenceTO.enhancedPlusPlusDependencies
        rawDependencyList.forEachIndexed() { index, dependency ->
            if (index > 0) {
                val depCode = dependency.dep
                val govIndex = dependency.governor.toInt()
                val depIndex = dependency.dependent.toInt()
                val direction = when {
                    govIndex > depIndex -> "l"
                    else -> "r"
                }
                val color = determineColor(sentenceTO.tokens, depIndex)
                answer = answer + "D$govIndex -[$color]$direction-> D$depIndex : \"$depCode\" \n"
            }
        }
        return answer
    }

    private fun determineColor(rawTokenList: List<TokenTO>, index: Int): String {
        val token = rawTokenList.get(index - 1)
        val baseColor = findColor(token.pos)
        return darkenColor(baseColor, 0.25f)
    }

    private fun buildWord(index: Int, pos: String, word: String): String {
        val template = wordTemplate + ""
        var answer = template.replace("$1", index.toString())
        answer = answer.replace("$2", word)
        val color = findColor(pos)
        answer = answer.replace("$3", color)
        answer = answer.replace("$4", pos)
        return answer;
    }

    // for posTags: https://en.wikipedia.org/wiki/Brown_Corpus//Part-of-speech_tags_used
    // for colors: https://github.com/nlplab/brat/blob/master/configurations/Stanford-CoreNLP/visual.conf
    private fun findColor(posTag: String): String {
        return when {
            posTag.equals("CC") -> "#fefefe" //Coordination off-white due to: https://forum.plantuml.net/8356/white-backgrounds-are-rendered-transparent-in-svg
            arrayOf("-LRB-", "-RRB-").contains(posTag) -> "#e3e3e3" //Punctuation, light grey
            posTag.startsWith("JJ") -> "#fffda8" // Adjectives, yellowish
            posTag.startsWith("RB") -> "#fffda8" // Adverbs, yellowish
            posTag.equals("WRB") -> "#fffda8" // Adverbs, yellowish
            posTag.endsWith("DT") -> "#ccadf6" //Determiners, greyish blue
            posTag.equals("CD") -> "#ccdaf6" //Number, greyish blue
            posTag.startsWith("NN") -> "#a4bced" // Nouns, blue
            posTag.startsWith("PRP") -> "#a4bced" // Pronoun, greyish blue
            posTag.startsWith("WP") -> "#a4bced" // Pronoun, greyish blue
            arrayOf("IN", "TO").contains(posTag) -> "#ffe8be" // Prepositions, brownish
            posTag.startsWith("VB") -> "#adf6a2" // Verbs, green
            posTag.equals("MD") -> "#adf6a2" // Verbs, green
            arrayOf("EX", "FW", "LS", "POS", "RP", "SYM", "UH").contains(posTag) -> "#e4cbf6" // Misc., violet
            // Named Entities
            arrayOf("DATE", "DURATION", "TIME").contains(posTag) -> "#9affe6" // Time entities
            posTag.equals("LOCATION") -> "#95dfff"
            posTag.equals("MISC") -> "#f1f447"
            posTag.equals("NUMBER") -> "#df99ff"
            posTag.equals("ORGANIZATION") -> "#8fb2ff"
            posTag.equals("PERCENT") -> "#ffa22b"
            posTag.equals("PERSON") -> "#ffccaa"
            posTag.equals("SET") -> "#ff7c95"
            posTag.equals("Mention") -> "#ffe000"
            else -> "#e3e3e3"
        }
    }

    fun lightenColor(hex: String, factor: Float): String {
        val color = Color.decode(hex)
        val r = (color.red + (255 - color.red) * factor).toInt().coerceIn(0, 255)
        val g = (color.green + (255 - color.green) * factor).toInt().coerceIn(0, 255)
        val b = (color.blue + (255 - color.blue) * factor).toInt().coerceIn(0, 255)

        return String.format("#%02X%02X%02X", r, g, b)
    }

    fun darkenColor(hex: String, factor: Float): String {
        val color = Color.decode(hex)
        val r = (color.red * (1 - factor)).toInt().coerceIn(0, 255)
        val g = (color.green * (1 - factor)).toInt().coerceIn(0, 255)
        val b = (color.blue * (1 - factor)).toInt().coerceIn(0, 255)

        return String.format("#%02X%02X%02X", r, g, b)
    }
}

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

/**
 * Generates graphviz code for a TypedDependency diagram.
 * see: /docs/SentenceAnalysis.puml for sample diagram
 */
object TdDiagram {
    private const val header = "digraph g {\n" +
            "    graph [nodesep=0.2 ranksep=0.1]\n" +
            "    node [shape=none fontname=arial fontsize=12 style=filled fillcolor=\"#fefefe\" width=0.1 height=0]\n" +
            "    edge [style=invis]\n" +
            "    splines=true; esep=1;\n"
    private const val footer = "}"

    fun build(sentence: SentenceTO, compact: Boolean = false): String {
        var answer = header
        val wordList = sentence.tokens
//FIXME        val posTagList = sentence.posTags()
//        answer += buildNodes(wordList, posTagList)

        val wordCount = wordList.size
        answer += buildGlue(wordCount, compact)
        answer += "edge [style=normal fontsize=10 fontname=arial color=GREY]\n"

//FIXME        val dependencyList = sentence.dependencyParse().typedDependencies()
//        answer += buildEdges(dependencyList)
        return answer + footer
    }

    private fun buildNodes(wordList: List<TokenTO>, posTagList: List<String>): String {
        var answer = ""
        wordList.forEachIndexed() { index, token ->
            val posTag = posTagList.get(index)
            answer += buildWord(index + 1, posTag, token.word)
        }
        return answer
    }

    private fun buildEdges(dependencyList: List<BasicDependencyTO>): String {
        var answer = ""
        dependencyList.forEach() { td ->
            td
//FIXME
            /*val rel = td.dep.//shortName
            val gov = td.gov().index()
            val dep = td.dep.index()
            when (rel) {
                "root" -> {
                }
                else -> answer += "p$gov -> p$dep [label=\"$rel\"]\n"
            }*/
        }
        return answer
    }

    private fun buildGlue(wordCount: Int, compact: Boolean): String {
        val wc = wordCount - 1
        val lastWord = wordCount
        var answer = ""
        (1..wc).forEach { i ->
            answer += "w$i -> "
        }
        answer += "w$lastWord\n"

        (1..wc).forEach { i ->
            answer += "p$i -> "
        }
        answer += "p$lastWord\n"

        if (compact) {
            answer += "rank=same {"
            (1..wc).forEach { i ->
                answer += "w$i,"
            }
            answer += "w$lastWord}\n"

            answer += "rank=same {"
            (1..wc).forEach { i ->
                answer += "p$i,"
            }
            answer += "p$lastWord}\n"
        }
        return answer
    }

    private fun buildWord(index: Int, posTag: String, text: String): String {
        val color = findColor(posTag)
        return "subgraph cluster$index {\n" +
                "p$index [label=\"$posTag\" shape=box fontsize=10 fillcolor=\"$color\"]\n" +
                "w$index [label=\"$text\"]\n" +
                "p$index -> w$index\n" +
                "}\n"
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

}

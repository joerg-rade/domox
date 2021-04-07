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

import edu.stanford.nlp.ling.IndexedWord
import edu.stanford.nlp.pipeline.CoreSentence
import edu.stanford.nlp.trees.TypedDependency

/**
 * see: /docs/SentenceAnalysis.adoc for sa sample diagram
 */
object TdDiagram {
    const val header = "digraph g {\n" +
            "    graph [nodesep=0.2 ranksep=0.1]\n" +
            "    node [shape=none fontname=arial fontsize=12 style=filled fillcolor=white width=0.1 height=0]\n" +
            "    edge [style=invis]\n"
    const val footer = "}"

    fun build(sentence: CoreSentence): String {
        var answer = header
        val wordList = sentence.tokensAsStrings()
        val posTagList = sentence.posTags()
        answer += buildNodes(wordList, posTagList)

        val wordCount = wordList.size
        answer += buildGlue(wordCount)
        answer += "edge [style=normal fontsize=10 fontname=arial color=GREY]\n"

        val dependencyList = sentence.dependencyParse().typedDependencies()
        answer += buildEdges(dependencyList)
        return answer + footer
    }

    private fun buildNodes(wordList: List<String>, posTagList: List<String>): String {
        var answer = ""
        wordList.forEachIndexed() { index, word ->
            val posTag = posTagList.get(index)
            answer += buildWord(index + 1, posTag, word)
        }
        return answer
    }

    /*    p1 -> p3 [label=det dir=back]
        p2 -> p3 [label=compound dir=back]
        p3 -> p4 [label=nsubj dir=back]
        p4 -> p7 [label=dobj]
        p5 -> p7 [label=det dir=back]
        p6 -> p7 [label=compound dir=back]
        p7 -> p8 [label=cc]
        p7 -> p9 [label="conj:and"]*/
    private fun buildEdges(dependencyList: Collection<TypedDependency>): String {
        var answer = ""
        dependencyList.forEach() { td ->
            val rel = td.reln().shortName
            val gov = td.gov().index()
            val dep = td.dep().index()
            when (rel) {
                "root" -> {}
                "punct" -> {}
                else -> answer += "p$gov -> p$dep [label=\"$rel\"]\n"
            }
        }
        return answer
    }

    private fun buildGlue(wordCount: Int): String {
        val wc = wordCount - 1
        val lastWord = wordCount
        var answer = ""
        (1..wc).forEach { i ->
            answer += "w$i -> "
        }
        answer += "w$lastWord\n"

        answer += "rank=same {"
        (1..wc).forEach { i ->
            answer += "w$i,"
        }
        answer += "w$lastWord}\n"

        (1..wc).forEach { i ->
            answer += "p$i -> "
        }
        answer += "p$lastWord\n"

        answer += "rank=same {"
        (1..wc).forEach { i ->
            answer += "p$i,"
        }
        answer += "p$lastWord}\n"
        return answer
    }

    private fun buildWord(index: Int, posTag: String, text: String): String {
        val color = findColor(posTag)
        return "subgraph _$index {\n" +
                "p$index [label=\"$posTag\" shape=box fontsize=10 fillcolor=$color]\n" +
                "w$index [label=\"$text\"]\n" +
                "p$index -> w$index\n" +
                "}\n"
    }

    private fun findColor(posTag: String): String {
        return when (posTag) {
            "CC" -> "WHITE"
            "DT" -> "MAGENTA"
            "NN" -> "CYAN"
            "VBZ" -> "CHARTREUSE"
            else -> "LIGHTGREY"
        }
    }

}

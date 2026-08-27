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

import java.awt.Color

/**
 * Base class for PlantUML diagram generators.
 * Contains shared utilities for color manipulation and common dependencies.
 */
abstract class PlantUmlDiagram {

    // for posTags: https://en.wikipedia.org/wiki/Brown_Corpus//Part-of-speech_tags_used
    // for colors: https://github.com/nlplab/brat/blob/master/configurations/Stanford-CoreNLP/visual.conf
    open fun findColor(posTag: String): String {
        return when {
            posTag.equals("CC") -> "#fefefe" // Coordination (off-white)
            arrayOf("-LRB-", "-RRB-").contains(posTag) -> "#e3e3e3" // Punctuation (light grey)
            posTag.startsWith("JJ") -> "#fffda8" // Adjectives (yellowish)
            posTag.startsWith("RB") -> "#fffda8" // Adverbs (yellowish)
            posTag.equals("WRB") -> "#fffda8" // Adverbs (yellowish)
            posTag.endsWith("DT") -> "#ccadf6" // Determiners (greyish blue)
            posTag.equals("CD") -> "#ccdaf6" // Numbers (greyish blue)
            posTag.startsWith("NN") -> "#a4bced" // Nouns (blue)
            posTag.startsWith("PRP") -> "#a4bced" // Pronouns (greyish blue)
            posTag.startsWith("WP") -> "#a4bced" // Pronouns (greyish blue)
            arrayOf("IN", "TO").contains(posTag) -> "#ffe8be" // Prepositions (brownish)
            posTag.startsWith("VB") -> "#adf6a2" // Verbs (green)
            posTag.equals("MD") -> "#adf6a2" // Modal verbs (green)
            arrayOf("EX", "FW", "LS", "POS", "RP", "SYM", "UH").contains(posTag) -> "#e4cbf6" // Misc. (violet)
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
            else -> "#e3e3e3" // Default (light grey)
        }
    }

    protected fun lightenColor(hex: String, factor: Float): String {
        val color = Color.decode(hex)
        val r = (color.red + (255 - color.red) * factor).toInt().coerceIn(0, 255)
        val g = (color.green + (255 - color.green) * factor).toInt().coerceIn(0, 255)
        val b = (color.blue + (255 - color.blue) * factor).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    protected fun darkenColor(hex: String, factor: Float): String {
        val color = Color.decode(hex)
        val r = (color.red * (1 - factor)).toInt().coerceIn(0, 255)
        val g = (color.green * (1 - factor)).toInt().coerceIn(0, 255)
        val b = (color.blue * (1 - factor)).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }
}

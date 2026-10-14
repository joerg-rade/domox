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
package domox

class MarkdownFilter {

    fun stripMarkdownRegex(md: String): String {
        return md
            // Remove code blocks
            .replace(Regex("```[\\s\\S]*?```"), "")
            // Remove headers
            .replace(Regex("(?m)^#{1,6}\\s+"), "")
            // Remove images ![alt](url) -> ""
            .replace(Regex("!\\[.*?\\]\\(.*?\\)"), "")
            // Remove links [text](url) -> text
            .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")
            // Remove bold/italic emphasis
            .replace(Regex("[*_]{1,3}(.*?)[*_]{1,3}"), "$1")
            // Remove blockquotes and list markers
            .replace(Regex("(?m)^\\s*[*\\-+]+\\s+"), "")
            .replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
            // Remove table structure lines (|---|---|)
            .replace(Regex("(?m)^\\|?[\\s\\-:|]*\\|?$"), "")
            // Replace remaining table pipes with space
            .replace("|", " ")
            // Collapse spaces and line breaks
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n\\s*\\n"), "\n")
            .trim()
    }

}

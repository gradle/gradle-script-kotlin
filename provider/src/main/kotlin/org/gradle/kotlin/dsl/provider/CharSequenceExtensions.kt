/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.provider

import kotlin.coroutines.experimental.buildSequence

internal
fun CharSequence.linePreservingSubstring(range: IntRange): String =
    linePreservingSubstring_(range).second

internal
fun CharSequence.linePreservingSubstring_(range: IntRange): Pair<Int, String> {
    val lineCount = take(range.start).count { it == '\n' }
    return lineCount to "\n".repeat(lineCount) + substring(range)
}

/**
 * Computes the 1-based line and column numbers from the given [range].
 */
internal
fun CharSequence.lineAndColumnFromRange(range: IntRange): Pair<Int, Int> {
    require(range.endInclusive <= lastIndex)
    val prefix = take(range.start)
    val lineCountBefore = prefix.count { it == '\n' }
    val lastNewLineIndex = prefix.lastIndexOf('\n')
    return (lineCountBefore + 1) to (range.start - lastNewLineIndex)
}

internal
fun CharSequence.splitIncluding(delimiter: Char) = buildSequence {
    var startIndex = 0
    while (true) {
        val endIndex = indexOf(delimiter, startIndex) + 1
        if (endIndex == 0) break
        yield(substring(startIndex, endIndex))
        startIndex = endIndex
    }
    if (startIndex <= lastIndex) {
        yield(substring(startIndex))
    }
}

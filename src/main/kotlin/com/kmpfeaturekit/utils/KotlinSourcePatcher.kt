package com.kmpfeaturekit.utils

object KotlinSourcePatcher {
    fun addImport(content: String, importFqName: String): String {
        val importLine = "import $importFqName"
        if (content.lines().any { it.trim() == importLine }) return content

        val lines = content.lines().toMutableList()
        val importIndexes = lines.indices.filter { lines[it].startsWith("import ") }
        if (importIndexes.isNotEmpty()) {
            val firstImportIndex = importIndexes.first()
            val lastImportIndex = importIndexes.last()
            val imports = (lines.subList(firstImportIndex, lastImportIndex + 1) + importLine).sorted()
            repeat(lastImportIndex - firstImportIndex + 1) { lines.removeAt(firstImportIndex) }
            lines.addAll(firstImportIndex, imports)
            return lines.joinToString("\n")
        }

        val packageIndex = lines.indexOfFirst { it.startsWith("package ") }
        return if (packageIndex >= 0) {
            lines.add(packageIndex + 1, "")
            lines.add(packageIndex + 2, importLine)
            lines.joinToString("\n")
        } else {
            "$importLine\n$content"
        }
    }

    fun appendArgumentToCall(
        content: String,
        callName: String,
        argument: String,
        importFqName: String? = null,
        rejectWhenArgumentContains: Set<Char> = setOf('{')
    ): String? {
        if (argument in content) return null

        val call = findCall(content, callName) ?: return null
        val existingArguments = content.substring(call.openParen + 1, call.closeParen)
        if (rejectWhenArgumentContains.any { it in existingArguments }) return null

        val replacement = if (existingArguments.trim().startsWith("listOf(")) {
            appendArgumentToNestedList(content, call, argument) ?: return null
        } else {
            replaceCallArguments(content, call, appendToArguments(existingArguments, argument))
        }

        return importFqName?.let { addImport(replacement, it) } ?: replacement
    }

    fun addSuperTypeToAnnotatedDeclaration(
        content: String,
        annotationName: String,
        superTypeName: String,
        importFqName: String
    ): String? {
        if (superTypeName in content) return null

        val lines = content.lines().toMutableList()
        val annotationIndex = lines.indexOfFirst { it.contains("@$annotationName") }
        if (annotationIndex < 0) return null

        val declarationIndex = ((annotationIndex + 1) until lines.size).firstOrNull { index ->
            lines[index].contains(" class ") ||
                lines[index].trimStart().startsWith("class ") ||
                lines[index].contains(" interface ") ||
                lines[index].trimStart().startsWith("interface ") ||
                lines[index].contains(" abstract class ") ||
                lines[index].trimStart().startsWith("abstract class ")
        } ?: return null

        val original = lines[declarationIndex]
        val braceIndex = original.indexOf('{').takeIf { it >= 0 }
        val header = braceIndex?.let { original.substring(0, it).trimEnd() } ?: original.trimEnd()
        val tail = braceIndex?.let { original.substring(it) }.orEmpty()
        val updatedHeader = if (":" in header) "$header, $superTypeName" else "$header : $superTypeName"
        lines[declarationIndex] = if (tail.isEmpty()) updatedHeader else "$updatedHeader $tail"

        return addImport(lines.joinToString("\n"), importFqName)
    }

    fun insertInsideAnnotatedDeclaration(
        content: String,
        annotationName: String,
        memberLine: String,
        importFqName: String
    ): String? {
        if (memberLine.trim() in content) return null

        val lines = content.lines().toMutableList()
        val annotationIndex = lines.indexOfFirst { it.contains("@$annotationName") }
        if (annotationIndex < 0) return null

        val declarationIndex = ((annotationIndex + 1) until lines.size).firstOrNull { index ->
            lines[index].contains(" class ") ||
                lines[index].trimStart().startsWith("class ") ||
                lines[index].contains(" interface ") ||
                lines[index].trimStart().startsWith("interface ") ||
                lines[index].contains(" abstract class ") ||
                lines[index].trimStart().startsWith("abstract class ")
        } ?: return null
        val openingBrace = findOpeningBrace(lines, declarationIndex) ?: return null
        val indent = lines[openingBrace].takeWhile { it.isWhitespace() } + "    "
        lines.add(openingBrace + 1, "$indent${memberLine.trim()}")

        return addImport(lines.joinToString("\n"), importFqName)
    }

    fun insertInsideCallBlock(
        content: String,
        callName: String,
        line: String,
        imports: List<String>
    ): String? {
        if (line.trim() in content) return null

        val call = findCall(content, callName) ?: return null
        val openBrace = content.indexOf('{', startIndex = call.closeParen).takeIf { it >= 0 } ?: return null
        val closeBrace = findMatching(content, openBrace, '{', '}') ?: return null
        val lineStart = content.lastIndexOf('\n', openBrace).let { if (it < 0) 0 else it + 1 }
        val indent = content.substring(lineStart, openBrace).takeWhile { it.isWhitespace() } + "    "
        val updated = content.replaceRange(openBrace + 1, openBrace + 1, "\n$indent${line.trim()}")
        return imports.fold(updated) { text, importFqName -> addImport(text, importFqName) }
    }

    fun appendEntryToNamedList(
        content: String,
        names: List<String>,
        entry: String,
        importFqName: String
    ): String? {
        if (entry in content) return null

        val target = names.firstNotNullOfOrNull { name -> findAssignmentList(content, name) } ?: return null
        val current = content.substring(target.openParen + 1, target.closeParen)
        val replacement = replaceRangeInside(content, target.openParen + 1, target.closeParen, appendToArguments(current, entry))
        return addImport(replacement, importFqName)
    }

    private fun appendArgumentToNestedList(content: String, call: CallRange, argument: String): String? {
        val listStart = content.indexOf("listOf(", startIndex = call.openParen + 1)
            .takeIf { it >= 0 && it < call.closeParen } ?: return null
        val listOpenParen = content.indexOf('(', startIndex = listStart)
        val listCloseParen = findMatching(content, listOpenParen, '(', ')') ?: return null
        val current = content.substring(listOpenParen + 1, listCloseParen)
        return replaceRangeInside(content, listOpenParen + 1, listCloseParen, appendToArguments(current, argument))
    }

    private fun appendToArguments(existing: String, argument: String): String {
        if (existing.isBlank()) return argument
        if ('\n' !in existing) return "${existing.trimEnd()}, $argument"

        val indent = existing.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.takeWhile { it.isWhitespace() }
            ?: "    "
        val trimmed = existing.trimEnd()
        val separator = if (trimmed.endsWith(",")) "" else ","
        return "$trimmed$separator\n$indent$argument,"
    }

    private fun replaceCallArguments(content: String, call: CallRange, arguments: String): String =
        replaceRangeInside(content, call.openParen + 1, call.closeParen, arguments)

    private fun replaceRangeInside(content: String, start: Int, end: Int, replacement: String): String =
        content.substring(0, start) + replacement + content.substring(end)

    private fun findAssignmentList(content: String, name: String): CallRange? {
        val assignment = Regex("""\b(?:val|var)?\s*$name\s*=""").find(content) ?: return null
        val listStart = content.indexOf("listOf(", startIndex = assignment.range.last + 1).takeIf { it >= 0 } ?: return null
        val openParen = content.indexOf('(', startIndex = listStart)
        val closeParen = findMatching(content, openParen, '(', ')') ?: return null
        return CallRange(openParen, closeParen)
    }

    private fun findCall(content: String, callName: String): CallRange? {
        var index = content.indexOf("$callName(")
        while (index >= 0) {
            val previous = content.getOrNull(index - 1)
            if (previous == null || !previous.isLetterOrDigit() && previous != '_') {
                val openParen = content.indexOf('(', startIndex = index)
                val closeParen = findMatching(content, openParen, '(', ')') ?: return null
                return CallRange(openParen, closeParen)
            }
            index = content.indexOf("$callName(", startIndex = index + callName.length)
        }
        return null
    }

    private fun findOpeningBrace(lines: List<String>, declarationIndex: Int): Int? {
        for (index in declarationIndex until minOf(lines.size, declarationIndex + 10)) {
            if ("{" in lines[index]) return index
        }
        return null
    }

    private fun findMatching(content: String, openIndex: Int, open: Char, close: Char): Int? {
        if (openIndex < 0 || content.getOrNull(openIndex) != open) return null
        var depth = 0
        var index = openIndex
        var inLineComment = false
        var inBlockComment = false
        var inString = false
        var inTripleString = false

        while (index < content.length) {
            val char = content[index]
            val next = content.getOrNull(index + 1)
            val triple = content.startsWith("\"\"\"", index)

            when {
                inLineComment && char == '\n' -> inLineComment = false
                inBlockComment && char == '*' && next == '/' -> {
                    inBlockComment = false
                    index++
                }
                inString && char == '\\' -> index++
                inString && char == '"' -> inString = false
                inTripleString && triple -> {
                    inTripleString = false
                    index += 2
                }
                !inLineComment && !inBlockComment && !inString && !inTripleString && char == '/' && next == '/' -> {
                    inLineComment = true
                    index++
                }
                !inLineComment && !inBlockComment && !inString && !inTripleString && char == '/' && next == '*' -> {
                    inBlockComment = true
                    index++
                }
                !inLineComment && !inBlockComment && !inString && !inTripleString && triple -> {
                    inTripleString = true
                    index += 2
                }
                !inLineComment && !inBlockComment && !inString && !inTripleString && char == '"' -> inString = true
                !inLineComment && !inBlockComment && !inString && !inTripleString && char == open -> depth++
                !inLineComment && !inBlockComment && !inString && !inTripleString && char == close -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return null
    }

    private data class CallRange(val openParen: Int, val closeParen: Int)
}

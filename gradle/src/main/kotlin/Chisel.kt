/**
 * Minimal, deterministic processor for the `Stonecutter`-style version directives used in this
 * project's shared Kotlin source.
 *
 * Conditions use the Stonecutter shape already present in source:
 *
 * ```
 * //? if >=26 {
 * active branch
 * //?} else
 * /*fallback branch*/
 * ```
 *
 * The checked-in branch (between `{` and `//?}`) is the true branch for the newest target; the else
 * branch is stored in a block comment. Unlike a plain "comment the if / uncomment the else" flip,
 * this evaluates the directive's condition (`>=26`, `>=1.21.11`, ...) against the target Minecraft
 * version, so more than two targets can be expressed by stacking or nesting directives. For the
 * newest target every condition holds and the output equals the checked-in source verbatim.
 *
 * `Stonecutter` cannot process these comments here because the plugin is only applied to the root
 * project while the code lives in subprojects; this helper performs the equivalent transform into
 * a generated source directory that the Kotlin source set points at.
 */
fun chiselSource(lines: List<String>, minecraftVersion: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.trimStart().startsWith("//? if")) {
            // Keep the marker line (it is a harmless // comment)
            out.appendLine(line)
            val keepIfBranch = evaluateCondition(line, minecraftVersion)
            i++
            // Collect the if-branch up to the matching "//?}" marker, tracking nested directives
            val ifBranch = mutableListOf<String>()
            var nestedDepth = 0
            while (i < lines.size) {
                val branchLine = lines[i]
                val trimmed = branchLine.trimStart()
                if (trimmed.startsWith("//? if")) {
                    nestedDepth++
                    ifBranch.add(branchLine)
                    i++
                    continue
                }
                if (trimmed.startsWith("//?}")) {
                    if (nestedDepth == 0) break
                    nestedDepth--
                    ifBranch.add(branchLine)
                    i++
                    continue
                }
                ifBranch.add(branchLine)
                i++
            }
            if (keepIfBranch) {
                // Recurse so nested directives inside the kept branch are resolved too
                out.append(chiselSource(ifBranch, minecraftVersion))
            } else {
                // Comment the inactive branch line-by-line (robust against nested block comments)
                ifBranch.forEach { out.appendLine("//$$ $it") }
            }
            // Handle the closing "//?}" or "//?} else" marker line
            var hasElse = false
            if (i < lines.size) {
                hasElse = lines[i].trimStart().startsWith("//?} else")
                out.appendLine(lines[i])
                i++
            }
            if (hasElse && i < lines.size) {
                // The else-branch is a single /* ... */ block
                var j = i
                while (j < lines.size && !lines[j].contains("*/")) j++
                val block = lines.subList(i, minOf(j + 1, lines.size)).toMutableList()
                if (keepIfBranch) {
                    // If-branch wins: keep the else block commented out verbatim
                    block.forEach { out.appendLine(it) }
                } else if (block.isNotEmpty()) {
                    // Else-branch wins: strip the surrounding /* */ and resolve any nested directives
                    val firstIdx = block[0].indexOf("/*")
                    if (firstIdx >= 0) {
                        block[0] = block[0].removeRange(firstIdx, firstIdx + 2)
                    }
                    val lastLine = block[block.size - 1]
                    val lastIdx = lastLine.lastIndexOf("*/")
                    if (lastIdx >= 0) {
                        block[block.size - 1] = lastLine.removeRange(lastIdx, lastIdx + 2)
                    }
                    out.append(chiselSource(block, minecraftVersion))
                }
                i = j + 1
            }
        } else {
            out.appendLine(line)
            i++
        }
    }
    return out.toString()
}

/** Evaluates a `//? if <op><version> {` directive against the target [minecraftVersion]. */
private fun evaluateCondition(marker: String, minecraftVersion: String): Boolean {
    val condition = marker.substringAfter("//? if", "").substringBefore("{").trim()
    val operator = listOf(">=", "<=", ">", "<", "==").firstOrNull { condition.startsWith(it) }
        ?: error("Unsupported chisel condition '$condition'.")
    val target = condition.removePrefix(operator).trim()
    val comparison = compareVersions(minecraftVersion, target)
    return when (operator) {
        ">=" -> comparison >= 0
        "<=" -> comparison <= 0
        ">" -> comparison > 0
        "<" -> comparison < 0
        "==" -> comparison == 0
        else -> error("Unsupported chisel operator '$operator'.")
    }
}

/** Compares two dotted version strings numerically, segment by segment. */
private fun compareVersions(left: String, right: String): Int {
    val leftParts = versionParts(left)
    val rightParts = versionParts(right)
    val size = maxOf(leftParts.size, rightParts.size)
    for (idx in 0 until size) {
        val l = leftParts.getOrElse(idx) { 0 }
        val r = rightParts.getOrElse(idx) { 0 }
        if (l != r) return l.compareTo(r)
    }
    return 0
}

/** Extracts the numeric segments of a version string (e.g. `"1.21.11"` -> `[1, 21, 11]`). */
private fun versionParts(version: String): List<Int> =
    Regex("\\d+").findAll(version).map { it.value.toInt() }.toList()

package cc.unitmesh.devins.ui.compose.sketch

/**
 * Diff 数据模型
 */
data class DiffHunk(
    val oldStartLine: Int,
    val oldLineCount: Int,
    val newStartLine: Int,
    val newLineCount: Int,
    val lines: List<DiffLine>,
    val header: String
)

data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNumber: Int? = null,
    val newLineNumber: Int? = null
)

enum class DiffLineType {
    CONTEXT,    // 上下文行（未修改）
    ADDED,      // 添加的行
    DELETED,    // 删除的行
    HEADER      // 头部信息（如 @@ -1,5 +1,6 @@）
}

data class FileDiff(
    val oldPath: String?,
    val newPath: String?,
    val hunks: List<DiffHunk>,
    val isNewFile: Boolean = false,
    val isDeletedFile: Boolean = false
)

/**
 * Unified Diff 解析器
 */
object DiffParser {
    
    /**
     * 解析统一格式的 diff 内容
     */
    fun parse(diffContent: String): List<FileDiff> {
        val fileDiffs = mutableListOf<FileDiff>()
        val lines = diffContent.lines()
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            
            // 检测文件头（--- 和 +++）
            if (line.startsWith("---")) {
                val oldPath = extractPath(line)
                i++
                
                if (i >= lines.size) break
                val newLine = lines[i]
                val newPath = if (newLine.startsWith("+++")) {
                    extractPath(newLine)
                } else {
                    oldPath
                }
                
                // 解析此文件的所有 hunks
                val hunks = mutableListOf<DiffHunk>()
                i++
                
                while (i < lines.size && !lines[i].startsWith("---")) {
                    if (lines[i].startsWith("@@")) {
                        val hunk = parseHunk(lines, i)
                        hunks.add(hunk.first)
                        i = hunk.second
                    } else {
                        i++
                    }
                }
                
                val isNewFile = oldPath == "/dev/null" || oldPath == null
                val isDeletedFile = newPath == "/dev/null" || newPath == null
                
                fileDiffs.add(FileDiff(oldPath, newPath, hunks, isNewFile, isDeletedFile))
            } else {
                i++
            }
        }
        
        // 如果没有标准的 --- +++ 头部，尝试解析整个内容作为一个 diff
        if (fileDiffs.isEmpty() && diffContent.contains("@@")) {
            val hunks = mutableListOf<DiffHunk>()
            var lineIndex = 0
            val contentLines = lines
            
            while (lineIndex < contentLines.size) {
                if (contentLines[lineIndex].startsWith("@@")) {
                    val hunk = parseHunk(contentLines, lineIndex)
                    hunks.add(hunk.first)
                    lineIndex = hunk.second
                } else {
                    lineIndex++
                }
            }
            
            if (hunks.isNotEmpty()) {
                fileDiffs.add(FileDiff(null, null, hunks))
            }
        }
        
        return fileDiffs
    }
    
    private fun extractPath(line: String): String? {
        val parts = line.split(Regex("\\s+"), 2)
        if (parts.size < 2) return null
        
        var path = parts[1]
        // 移除 a/ 或 b/ 前缀
        if (path.startsWith("a/") || path.startsWith("b/")) {
            path = path.substring(2)
        }
        
        return if (path == "/dev/null") null else path
    }
    
    private fun parseHunk(lines: List<String>, startIndex: Int): Pair<DiffHunk, Int> {
        var i = startIndex
        val headerLine = lines[i]
        
        // 解析 @@ -oldStart,oldCount +newStart,newCount @@ 格式
        val hunkHeaderRegex = Regex("""@@\s+-(\d+)(?:,(\d+))?\s+\+(\d+)(?:,(\d+))?\s+@@(.*)""")
        val match = hunkHeaderRegex.find(headerLine)
        
        val (oldStart, oldCount, newStart, newCount) = if (match != null) {
            val oldStart = match.groupValues[1].toInt()
            val oldCount = match.groupValues[2].toIntOrNull() ?: 1
            val newStart = match.groupValues[3].toInt()
            val newCount = match.groupValues[4].toIntOrNull() ?: 1
            listOf(oldStart, oldCount, newStart, newCount)
        } else {
            listOf(1, 0, 1, 0)
        }
        
        val diffLines = mutableListOf<DiffLine>()
        var oldLineNum = oldStart
        var newLineNum = newStart
        i++
        
        // 解析 hunk 内容
        while (i < lines.size) {
            val line = lines[i]
            
            when {
                line.startsWith("@@") -> break  // 下一个 hunk
                line.startsWith("---") -> break // 下一个文件
                line.startsWith(" ") -> {
                    // 上下文行
                    diffLines.add(DiffLine(
                        DiffLineType.CONTEXT,
                        line.substring(1),
                        oldLineNum++,
                        newLineNum++
                    ))
                }
                line.startsWith("+") -> {
                    // 添加的行
                    diffLines.add(DiffLine(
                        DiffLineType.ADDED,
                        line.substring(1),
                        null,
                        newLineNum++
                    ))
                }
                line.startsWith("-") -> {
                    // 删除的行
                    diffLines.add(DiffLine(
                        DiffLineType.DELETED,
                        line.substring(1),
                        oldLineNum++,
                        null
                    ))
                }
                line.isEmpty() -> {
                    // 空行作为上下文
                    diffLines.add(DiffLine(
                        DiffLineType.CONTEXT,
                        "",
                        oldLineNum++,
                        newLineNum++
                    ))
                }
                else -> {
                    // 其他行也作为上下文处理
                    diffLines.add(DiffLine(
                        DiffLineType.CONTEXT,
                        line,
                        oldLineNum++,
                        newLineNum++
                    ))
                }
            }
            i++
        }
        
        val hunk = DiffHunk(oldStart, oldCount, newStart, newCount, diffLines, headerLine)
        return Pair(hunk, i)
    }
}


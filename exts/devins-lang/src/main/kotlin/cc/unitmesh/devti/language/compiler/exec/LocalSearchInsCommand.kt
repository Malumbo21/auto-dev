package cc.unitmesh.devti.language.compiler.exec

import cc.unitmesh.devti.language.utils.canBeAdded
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile


/**
 * Todo: Spike different search API in Intellij
 * - [com.intellij.util.indexing.FileBasedIndex]
 * - [com.intellij.find.FindManager] or [com.intellij.find.impl.FindInProjectUtil]
 * - [com.intellij.psi.search.PsiSearchHelper]
 * - [com.intellij.structuralsearch.StructuralSearchUtil] (Structural search API)
 * - [com.intellij.find.EditorSearchSession]
 *
 * ```java
 * EditorSearchSession.start(editor,project).setTextInField("Your Text to search");
 * ```
 *
 */
class LocalSearchInsCommand(val myProject: Project, private val scope: String, val text: String?) : InsCommand {
    override suspend fun execute(): String {
        val text = (text ?: scope).trim()
        /// check text length if less then 3 return alert slowly
        if (text.length < 3) {
            throw IllegalArgumentException("Text length should be more than 5")
        }

        val textSearch = search(myProject, text, 5)
        return textSearch.map { (file, lines) ->
            val filePath = file.path
            val linesWithContext = lines.joinToString("\n")
            "$filePath\n$linesWithContext"
        }.joinToString("\n")
    }

    /**
     * Searches for occurrences of a specified keyword within the content of files in the project.
     * For each occurrence, it retrieves a specified number of lines before and after the matched line,
     * providing context around the keyword. The results are grouped by the file in which the keyword was found.
     *
     * @param project The project in which to search for the keyword. This is used to access the project's file index.
     * @param keyword The keyword to search for within the files. The search is case-sensitive.
     * @param overlap The number of lines to retrieve before and after each matched line. This determines the context size around the keyword.
     * @return A map where each key is a `VirtualFile` containing the keyword, and the value is a list of strings representing
     *         the lines of context around the keyword in that file. The context includes the matched line and the specified
     *         number of lines before and after it.
     */
    private fun search(project: Project, keyword: String, overlap: Int): Map<VirtualFile, List<String>> {
        val result = mutableMapOf<VirtualFile, List<String>>()

        ProjectFileIndex.getInstance(project).iterateContent { file ->
            if (!file.canBeAdded() || !ProjectFileIndex.getInstance(project).isInContent(file)) {
                return@iterateContent true
            }

            val content = file.contentsToByteArray().toString(Charsets.UTF_8).lines()
            val matchedIndices = content.withIndex()
                .filter { (_, line) -> line.contains(keyword) }
                .map { it.index }

            if (matchedIndices.isNotEmpty()) {
                val linesWithContext = matchedIndices.flatMap { index ->
                    val start = (index - overlap).coerceAtLeast(0)
                    val end = (index + overlap).coerceAtMost(content.size - 1)
                    content.subList(start, end + 1)
                }.distinct()

                result[file] = linesWithContext
            }
            true
        }
        return result
    }
}
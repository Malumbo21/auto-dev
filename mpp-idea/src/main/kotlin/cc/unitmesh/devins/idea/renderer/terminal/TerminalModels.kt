package cc.unitmesh.devins.idea.renderer.terminal

import androidx.compose.ui.graphics.Color

/**
 * Terminal cell representing a single character with formatting
 */
data class TerminalCell(
    val char: Char = ' ',
    val foregroundColor: Color = Color.White,
    val backgroundColor: Color = Color.Black,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false
)

/**
 * Terminal line containing multiple cells
 */
data class TerminalLine(
    val cells: List<TerminalCell> = emptyList()
)

/**
 * Terminal state containing all lines and cursor position
 */
data class TerminalState(
    val lines: List<TerminalLine> = listOf(TerminalLine()),
    val cursorX: Int = 0,
    val cursorY: Int = 0
) {
    fun clearScreen(): TerminalState {
        return copy(
            lines = listOf(TerminalLine()),
            cursorX = 0,
            cursorY = 0
        )
    }

    fun getVisibleLines(maxLines: Int = 50): List<TerminalLine> {
        return lines.takeLast(maxLines)
    }
}

/**
 * ANSI parser for terminal output
 */
object AnsiParser {
    fun parse(text: String, state: TerminalState = TerminalState()): TerminalState {
        // Simplified ANSI parsing - just convert text to terminal cells
        val lines = text.split("\n")
        val terminalLines = lines.map { line ->
            TerminalLine(
                cells = line.map { char ->
                    TerminalCell(
                        char = char,
                        foregroundColor = Color.White,
                        backgroundColor = Color.Black
                    )
                }
            )
        }
        
        return state.copy(lines = terminalLines)
    }
}


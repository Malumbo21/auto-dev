package cc.unitmesh.devins.ui.compose.sketch

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * NanoDSL Block Renderer - Cross-platform component for rendering NanoDSL code blocks.
 *
 * On JVM platforms: Renders live UI preview using StatefulNanoRenderer when parsing succeeds,
 * with fallback to syntax-highlighted code display.
 *
 * On non-JVM platforms (JS, WASM, iOS, Android): Displays syntax-highlighted NanoDSL code only,
 * as the full parser (xuiper-ui) is JVM-only.
 *
 * Usage in SketchRenderer:
 * ```kotlin
 * "nanodsl", "nano" -> {
 *     NanoDSLBlockRenderer(
 *         nanodslCode = fence.text,
 *         isComplete = blockIsComplete,
 *         modifier = Modifier.fillMaxWidth()
 *     )
 * }
 * ```
 *
 * @param nanodslCode The NanoDSL source code to render
 * @param isComplete Whether the code block streaming is complete
 * @param modifier Compose modifier for the component
 */
@Composable
expect fun NanoDSLBlockRenderer(
    nanodslCode: String,
    isComplete: Boolean = true,
    modifier: Modifier = Modifier
)


package cc.unitmesh.devins.ui.nano.cli

import cc.unitmesh.devins.ui.nano.NanoRenderUtils
import cc.unitmesh.xuiper.dsl.NanoDSL
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.state.NanoStateManager
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File

/**
 * A JVM-only CLI helper to debug NanoDSL text interpolation.
 *
 * It parses NanoDSL to NanoIR, initializes state from the IR state block,
 * then walks the IR tree and prints string props as:
 *   raw => rendered
 * and also prints per-template expression evaluations.
 */
object NanoDslTextRenderCli {

    @JvmStatic
    fun main(args: Array<String>) {
        val parsed = Args.parse(args.toList())
        if (parsed == null) {
            Args.printUsage()
            return
        }

        val input = File(parsed.path)
        if (!input.exists()) {
            System.err.println("Input path does not exist: ${input.absolutePath}")
            return
        }

        val dslFiles = when {
            input.isFile -> listOf(input)
            else -> input
                .walkTopDown()
                .filter { it.isFile && it.name == "generated.nanodsl" }
                .toList()
        }

        if (dslFiles.isEmpty()) {
            System.err.println("No generated.nanodsl found under: ${input.absolutePath}")
            return
        }

        var anyFailed = false
        dslFiles.sortedBy { it.absolutePath }.forEach { file ->
            val report = renderReportForFile(file, failOnUnresolved = parsed.failOnUnresolved)
            val outFile = if (parsed.writeReportFile) File(file.parentFile, "render-report.txt") else null

            if (outFile != null) {
                outFile.writeText(report.text)
            }

            print(report.text)

            if (parsed.failOnUnresolved && report.unresolvedCount > 0) {
                anyFailed = true
            }
        }

        if (anyFailed) {
            System.err.println("\nFAILED: unresolved template expressions found")
            kotlin.system.exitProcess(2)
        }
    }

    private data class Report(val text: String, val unresolvedCount: Int)

    private fun renderReportForFile(file: File, failOnUnresolved: Boolean): Report {
        val source = file.readText()
        val ir = try {
            NanoDSL.toIR(source)
        } catch (e: Exception) {
            return Report(
                text = buildString {
                    appendLine("== ${file.absolutePath} ==")
                    appendLine("PARSE ERROR: ${e.message}")
                    appendLine()
                },
                unresolvedCount = if (failOnUnresolved) 1 else 0
            )
        }

        val stateManager = NanoStateManager().apply { initFromComponent(ir) }
        val stateSnapshot: Map<String, Any> = stateManager.getState().snapshot().mapValues { (_, v) -> v ?: "" }

        var unresolved = 0
        val text = buildString {
            appendLine("== ${file.absolutePath} ==")
            appendLine("rootType: ${ir.type}")
            appendLine("stateKeys: ${stateSnapshot.keys.sorted().joinToString(", ")}")
            appendLine()

            unresolved += walk(ir, stateSnapshot, path = "root") { line ->
                appendLine(line)
            }

            appendLine()
            appendLine("summary.unresolvedCount: $unresolved")
            appendLine()
        }

        return Report(text = text, unresolvedCount = unresolved)
    }

    private fun walk(
        ir: NanoIR,
        state: Map<String, Any>,
        path: String,
        emit: (String) -> Unit
    ): Int {
        var unresolved = 0

        // String props
        if (ir.props.isNotEmpty()) {
            ir.props.entries.sortedBy { it.key }.forEach { (key, value) ->
                val primitive = value as? JsonPrimitive
                val stringRaw = primitive?.contentOrNull() ?: return@forEach

                val rendered = NanoRenderUtils.interpolateText(stringRaw, state)
                val templates = extractTemplates(stringRaw)
                if (templates.isNotEmpty()) {
                    val results = templates.map { expr ->
                        val result = NanoRenderUtils.evaluateExpression(expr, NanoRenderUtils.getBuiltInVariables() + state)
                        TemplateEval(expr = expr, result = result)
                    }

                    val unresolvedExprs = results.filter { it.result.isBlank() }
                    if (unresolvedExprs.isNotEmpty()) {
                        unresolved += unresolvedExprs.size
                    }

                    emit(
                        "$path.${ir.type}.props.$key\n" +
                            "  raw     : ${quote(stringRaw)}\n" +
                            "  rendered: ${quote(rendered)}\n" +
                            "  eval    : ${results.joinToString("; ") { it.toShortString() }}\n" +
                            (if (unresolvedExprs.isNotEmpty()) "  status  : UNRESOLVED(${unresolvedExprs.size})" else "  status  : OK")
                    )
                }
            }
        }

        // Bindings (show resolved values for quick inspection)
        if (!ir.bindings.isNullOrEmpty()) {
            ir.bindings!!.entries.sortedBy { it.key }.forEach { (key, binding) ->
                val expr = binding.expression
                val resolved = when {
                    expr.startsWith("state.") -> {
                        val k = expr.removePrefix("state.")
                        state[k]?.toString() ?: ""
                    }
                    else -> expr
                }
                emit(
                    "$path.${ir.type}.bindings.$key\n" +
                        "  expr    : ${quote(expr)}\n" +
                        "  resolved: ${quote(resolved)}"
                )
            }
        }

        // Children
        val children = ir.children.orEmpty()
        children.forEachIndexed { idx, child ->
            unresolved += walk(child, state, path = "$path/${ir.type}[$idx]", emit = emit)
        }

        return unresolved
    }

    private data class TemplateEval(val expr: String, val result: String) {
        fun toShortString(): String = "${expr.trim()} => ${quote(result)}"
    }

    private fun quote(s: String): String {
        val normalized = s.replace("\n", "\\n")
        return "\"$normalized\""
    }

    private fun JsonPrimitive.contentOrNull(): String? {
        // Only treat JSON string primitives as "string props".
        // Other primitives (int/bool/double) are ignored here.
        if (this.intOrNull != null) return null
        if (this.doubleOrNull != null) return null
        if (this.booleanOrNull != null) return null
        return this.content
    }

    private fun extractTemplates(text: String): List<String> {
        val pattern = Regex("""\\$\\{([^}]+)\\}|\\{([^}]+)\\}""")
        return pattern.findAll(text)
            .mapNotNull { mr ->
                (mr.groups[1]?.value ?: mr.groups[2]?.value)?.trim()
            }
            .filter { it.isNotBlank() }
            .toList()
    }

    private data class Args(
        val path: String,
        val failOnUnresolved: Boolean,
        val writeReportFile: Boolean
    ) {
        companion object {
            fun parse(args: List<String>): Args? {
                if (args.isEmpty()) return null

                var path: String? = null
                var failOnUnresolved = true
                var writeReportFile = true

                var i = 0
                while (i < args.size) {
                    when (val a = args[i]) {
                        "--path" -> {
                            path = args.getOrNull(i + 1)
                            i += 2
                        }
                        "--failOnUnresolved" -> {
                            failOnUnresolved = (args.getOrNull(i + 1) ?: "true").toBooleanStrictOrNull() ?: true
                            i += 2
                        }
                        "--writeReportFile" -> {
                            writeReportFile = (args.getOrNull(i + 1) ?: "true").toBooleanStrictOrNull() ?: true
                            i += 2
                        }
                        "--help", "-h" -> return null
                        else -> {
                            // Back-compat: allow positional path
                            if (path == null) {
                                path = a
                                i += 1
                            } else {
                                return null
                            }
                        }
                    }
                }

                val p = path?.trim().orEmpty()
                if (p.isBlank()) return null
                return Args(path = p, failOnUnresolved = failOnUnresolved, writeReportFile = writeReportFile)
            }

            fun printUsage() {
                println(
                    """
Usage:
  ./gradlew :mpp-ui:runNanoDslTextRenderCli --args='--path <FILE_OR_DIR> [--failOnUnresolved true|false] [--writeReportFile true|false]'

Notes:
  - If --path is a directory, it will search for files named 'generated.nanodsl' recursively.
  - When --writeReportFile=true, it writes 'render-report.txt' next to each 'generated.nanodsl'.
""".trimIndent()
                )
            }
        }
    }
}

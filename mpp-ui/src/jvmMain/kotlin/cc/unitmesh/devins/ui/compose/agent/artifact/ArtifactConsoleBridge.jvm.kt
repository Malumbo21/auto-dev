package cc.unitmesh.devins.ui.compose.agent.artifact

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * JVM-only helper for Artifact WebView <-> Kotlin console bridge.
 *
 * Why this exists:
 * - Streaming uses repeated HTML injections; naive console patching will wrap console.log multiple times,
 *   causing duplicate log lines. This bridge is idempotent (stores original console once on window).
 * - compose-webview-multiplatform has a known callbackId = -1 spam; we suppress it (same as WebEdit).
 */
internal object ArtifactConsoleBridgeJvm {
    const val METHOD_NAME: String = "artifactConsole"

    /**
     * Inject console capture script into HTML.
     *
     * The injected JS:
     * - Installs a single console wrapper (idempotent via window.__autodevArtifactConsoleBridgeState)
     * - Buffers logs until kmpJsBridge is ready, then flushes
     * - Suppresses noisy kmpJsBridge.onCallback(-1, ...) spam
     */
    fun injectConsoleCapture(html: String): String {
        val consoleScript = """
            <script>
            (function() {
                // ====== Idempotent state stored on window (prevents double-wrapping) ======
                const STATE_KEY = '__autodevArtifactConsoleBridgeState';
                const state = window[STATE_KEY] || (window[STATE_KEY] = {});
                
                // Capture original console functions ONCE.
                // If we re-inject (streaming), reset console.* back to original before wrapping again.
                if (!state.originalConsole) {
                    state.originalConsole = {
                        log: console.log.bind(console),
                        info: console.info.bind(console),
                        warn: console.warn.bind(console),
                        error: console.error.bind(console)
                    };
                } else {
                    console.log = state.originalConsole.log;
                    console.info = state.originalConsole.info;
                    console.warn = state.originalConsole.warn;
                    console.error = state.originalConsole.error;
                }
                
                state.pending = state.pending || [];
                state.flushTimer = state.flushTimer || null;
                state.patchTimer = state.patchTimer || null;

                function bridgeReady() {
                    return (window.kmpJsBridge && typeof window.kmpJsBridge.callNative === 'function');
                }

                // Fix for compose-webview-multiplatform library bug:
                // When callbackId is -1, the library still calls onCallback and logs it,
                // causing excessive console spam like:
                //   "onCallback: -1, {}, undefined"
                function patchOnCallback() {
                    if (!window.kmpJsBridge || !window.kmpJsBridge.onCallback) return;
                    if (window.kmpJsBridge.__autodevOnCallbackPatched) return;
                    const originalOnCallback = window.kmpJsBridge.onCallback;
                    window.kmpJsBridge.onCallback = function(callbackId, data) {
                        if (callbackId === -1 || callbackId === '-1') return;
                        return originalOnCallback.call(window.kmpJsBridge, callbackId, data);
                    };
                    window.kmpJsBridge.__autodevOnCallbackPatched = true;
                    if (state.patchTimer) { clearInterval(state.patchTimer); state.patchTimer = null; }
                }

                function sendPayload(payload) {
                    try {
                        // Fire-and-forget: passing a callback can trigger verbose internal logs.
                        window.kmpJsBridge.callNative('${METHOD_NAME}', JSON.stringify(payload));
                        return true;
                    } catch (e) {
                        return false;
                    }
                }

                function flushPending() {
                    patchOnCallback();
                    if (!bridgeReady()) return;
                    while (state.pending.length > 0) {
                        const payload = state.pending.shift();
                        if (!sendPayload(payload)) break;
                    }
                    if (state.pending.length === 0 && state.flushTimer) {
                        clearInterval(state.flushTimer);
                        state.flushTimer = null;
                    }
                }

                function sendToKotlin(level, args) {
                    const message = Array.from(args).map(arg => {
                        if (typeof arg === 'object') {
                            try { return JSON.stringify(arg); }
                            catch { return String(arg); }
                        }
                        return String(arg);
                    }).join(' ');

                    const payload = { level: level, message: message };
                    if (bridgeReady()) {
                        patchOnCallback();
                        if (!sendPayload(payload)) {
                            state.pending.push(payload);
                        }
                    } else {
                        state.pending.push(payload);
                        if (!state.flushTimer) state.flushTimer = setInterval(flushPending, 100);
                        if (!state.patchTimer) state.patchTimer = setInterval(patchOnCallback, 100);
                    }
                }

                // Install wrappers exactly once per (current) console originals.
                console.log = function() { sendToKotlin('log', arguments); state.originalConsole.log.apply(console, arguments); };
                console.info = function() { sendToKotlin('info', arguments); state.originalConsole.info.apply(console, arguments); };
                console.warn = function() { sendToKotlin('warn', arguments); state.originalConsole.warn.apply(console, arguments); };
                console.error = function() { sendToKotlin('error', arguments); state.originalConsole.error.apply(console, arguments); };

                window.onerror = function(msg, url, line, col, error) {
                    sendToKotlin('error', ['Uncaught error: ' + msg + ' at ' + url + ':' + line]);
                };

                // Try to patch/flush immediately (if bridge already exists)
                patchOnCallback();
                flushPending();
            })();
            </script>
        """.trimIndent()

        return when {
            html.contains("<head>", ignoreCase = true) -> {
                html.replaceFirst(Regex("<head>", RegexOption.IGNORE_CASE), "<head>\n$consoleScript\n")
            }
            html.contains("<body", ignoreCase = true) -> {
                val bodyRegex = Regex("<body[^>]*>", RegexOption.IGNORE_CASE)
                val match = bodyRegex.find(html)
                if (match != null) {
                    html.replaceFirst(bodyRegex, "${match.value}\n$consoleScript\n")
                } else {
                    "$consoleScript\n$html"
                }
            }
            html.contains("<html", ignoreCase = true) -> {
                val htmlRegex = Regex("<html[^>]*>", RegexOption.IGNORE_CASE)
                val match = htmlRegex.find(html)
                if (match != null) {
                    html.replaceFirst(htmlRegex, "${match.value}\n$consoleScript\n")
                } else {
                    "$consoleScript\n$html"
                }
            }
            else -> {
                "$consoleScript\n$html"
            }
        }
    }

    /**
     * Parse params from JsMessage to (level, message).
     * Supports:
     * - JSON object string: {"level":"log","message":"..."}
     * - JSON primitive containing JSON string (double encoded)
     * - Plain string
     */
    fun parseConsoleParams(params: String): Pair<String, String> {
        val element = runCatching { Json.parseToJsonElement(params) }.getOrNull()
            ?: return "log" to params

        if (element is JsonObject) {
            val level = element["level"]?.jsonPrimitive?.content ?: "log"
            val msg = element["message"]?.jsonPrimitive?.content ?: ""
            return level to msg
        }

        val primitiveContent: String? = runCatching { element.jsonPrimitive.content }.getOrNull()
        if (primitiveContent.isNullOrBlank()) return "log" to params

        val nested = runCatching { Json.parseToJsonElement(primitiveContent) }.getOrNull()
        if (nested is JsonObject) {
            val level = nested["level"]?.jsonPrimitive?.content ?: "log"
            val msg = nested["message"]?.jsonPrimitive?.content ?: primitiveContent
            return level to msg
        }
        return "log" to primitiveContent
    }

    fun escapeForTemplateLiteral(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")
    }
}



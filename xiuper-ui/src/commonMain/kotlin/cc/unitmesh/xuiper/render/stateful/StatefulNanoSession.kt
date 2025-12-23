package cc.unitmesh.xuiper.render.stateful

import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.state.NanoStateRuntime
import kotlinx.coroutines.flow.StateFlow

/**
 * StatefulNanoSession
 *
 * A small, platform-agnostic wrapper around [NanoStateRuntime] that:
 * - Initializes runtime from a [NanoIR]
 * - Exposes a stable observed-keys list for UI frameworks to subscribe
 * - Applies [NanoActionIR] mutations
 *
 * This is designed to be reused by Compose (mpp-ui), IDEA renderers (Jewel/Swing),
 * and any other platform renderer.
 */
class StatefulNanoSession private constructor(
    private val runtime: NanoStateRuntime
) {
    /** Stable key order for deterministic subscription. */
    val observedKeys: List<String> = runtime.declaredKeys.toList().sorted()

    fun snapshot(): Map<String, Any> = runtime.snapshot()

    fun apply(action: NanoActionIR) {
        runtime.apply(action)
    }

    fun flow(path: String): StateFlow<Any?> = runtime.state.flow(path)

    companion object {
        fun create(ir: NanoIR): Result<StatefulNanoSession> {
            return runCatching { StatefulNanoSession(NanoStateRuntime(ir)) }
        }
    }
}

package cc.unitmesh.xuiper.render.stateful

import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR

/**
 * Optional shared recursion skeleton for stateful renderers.
 *
 * Why it exists:
 * - Compose / Jewel / HTML renderers all need the same pattern:
 *   snapshot + action dispatcher + recursive node traversal.
 * - UI-specific concerns (e.g., Compose Modifier) are modeled as [P] (payload).
 */
class StatefulNanoTreeDispatcher<P, T>(
    private val dispatch: (
        node: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        payload: P,
        renderChild: (NanoIR, P) -> T
    ) -> T
) {
    fun render(session: StatefulNanoSession, root: NanoIR, payload: P): T {
        val stateSnapshot = session.snapshot()
        fun renderNode(node: NanoIR, p: P): T {
            return dispatch(node, stateSnapshot, session::apply, p, ::renderNode)
        }
        return renderNode(root, payload)
    }
}

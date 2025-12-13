package cc.unitmesh.viewer.web.automation

/**
 * Test result data class
 */
data class TestResult(
    val name: String,
    val category: TestCategory,
    val passed: Boolean,
    val duration: Long,
    val message: String = if (passed) "Test completed successfully" else "Test assertion failed"
)

/**
 * Test categories for grouping related tests
 */
enum class TestCategory {
    BRIDGE_COMMUNICATION,
    DOM_INSPECTION,
    SHADOW_DOM,
    USER_INTERACTION,
    MUTATION_OBSERVER,
    GENERAL
}

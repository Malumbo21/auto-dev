package cc.unitmesh.agent.subagent

import cc.unitmesh.llm.PromptStreamingService
import cc.unitmesh.devins.filesystem.EmptyFileSystem
import cc.unitmesh.devins.filesystem.ProjectFileSystem
import cc.unitmesh.devins.llm.Message
import cc.unitmesh.llm.compression.TokenInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test


class NanoDSLAgentScenarioCompileTest {

    private class FakePromptStreamingService(
        private val response: String
    ) : PromptStreamingService {
        override fun streamPrompt(
            userPrompt: String,
            fileSystem: ProjectFileSystem,
            historyMessages: List<Message>,
            compileDevIns: Boolean,
            onTokenUpdate: ((TokenInfo) -> Unit)?,
            onCompressionNeeded: ((Int, Int) -> Unit)?
        ): Flow<String> = flowOf(response)
    }

    @Test
    fun `should generate valid NanoDSL for scenarios with badge variables`() = runTest {
        val scenarios = listOf(
            "Trip budget badge",
            "Traveler count badge",
            "List length badge"
        )

        val expectedSnippets = listOf(
            "SGD {state.budget}",
            "{state.travelers} people",
            "{len(state.activities)} selected"
        )

        val dslVariants = listOf(
            """
```nanodsl
component BudgetDemo:
    state:
        budget: int = 2000

    HStack(spacing="md"):
        Badge("SGD {state.budget}", color="blue")
```
""".trim(),
            """
```nanodsl
component TravelersDemo:
    state:
        travelers: int = 2

    HStack(spacing="md"):
        Badge("{state.travelers} people", color="green")
```
""".trim(),
            """
```nanodsl
component ActivitiesDemo:
    state:
        activities: list = ["a", "b", "c"]

    HStack(spacing="md"):
        Badge("{len(state.activities)} selected", color="purple")
```
""".trim()
        )

        scenarios.zip(dslVariants).zip(expectedSnippets).forEach { (pair, expectedSnippet) ->
            val (description, response) = pair
            val agent = NanoDSLAgent(
                llmService = FakePromptStreamingService(response),
                maxRetries = 1
            )

            val result = agent.execute(
                NanoDSLContext(description = description),
                onProgress = {}
            )

            if (!result.success) {
                throw IllegalStateException(
                    "Scenario '$description' should succeed. metadata=${result.metadata} content='${result.content.take(1200)}'"
                )
            }

            val isValid = result.metadata["isValid"]
            if (isValid != "true") {
                throw IllegalStateException(
                    "Scenario '$description' should be valid. isValid=$isValid metadata=${result.metadata}"
                )
            }

            val irJson = result.metadata["irJson"]
                ?: throw IllegalStateException(
                    "Scenario '$description' should include irJson metadata. metadata=${result.metadata}"
                )

            if (!irJson.contains("Badge(")) {
                throw IllegalStateException(
                    "Scenario '$description' IR should contain Badge(...) in source. irJson='${irJson.take(1200)}'"
                )
            }

            if (!irJson.contains(expectedSnippet)) {
                throw IllegalStateException(
                    "Scenario '$description' IR should preserve template snippet '$expectedSnippet'. irJson='${irJson.take(1200)}'"
                )
            }
        }
    }
}

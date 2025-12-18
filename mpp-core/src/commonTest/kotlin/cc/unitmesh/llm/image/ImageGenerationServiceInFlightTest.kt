package cc.unitmesh.llm.image

import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.ModelConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageGenerationServiceInFlightTest {

    @Test
    fun shouldDeduplicateInFlightRequestsForSamePromptAndSize() = runTest {
        var requestCount = 0

        val engine = MockEngine { _ ->
            requestCount += 1
            // Ensure callers overlap in time.
            delay(50)
            respond(
                content = """
                {"data":[{"url":"https://example.com/generated.png"}]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(engine)
        val service = ImageGenerationService(
            config = ModelConfig(
                provider = LLMProviderType.GLM,
                modelName = "cogview-3-flash",
                apiKey = "test-key",
                baseUrl = "https://example.com/"
            ),
            client = client
        )

        val results = (1..10).map {
            async { service.generateImage(prompt = "a cat", size = "1024x512") }
        }.awaitAll()

        assertEquals(1, requestCount, "Expected a single HTTP request for identical in-flight calls")
        assertTrue(results.all { it is ImageGenerationResult.Success })
        results.forEach { r ->
            assertEquals(
                "https://example.com/generated.png",
                (r as ImageGenerationResult.Success).imageUrl
            )
        }

        service.close()
    }
}

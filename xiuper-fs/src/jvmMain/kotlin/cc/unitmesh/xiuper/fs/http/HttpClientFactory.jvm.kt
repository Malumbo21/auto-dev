package cc.unitmesh.xiuper.fs.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual object HttpClientFactory {
    actual fun create(service: RestServiceConfig): HttpClient {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        return HttpClient(CIO) {
            expectSuccess = false

            install(ContentNegotiation) {
                json(json)
            }

            defaultRequest {
                url.takeFrom(service.baseUrl)
                service.defaultHeaders.forEach { (k, v) -> header(k, v) }
                header(HttpHeaders.UserAgent, "XiuperFs/1.0")
            }
        }
    }
}

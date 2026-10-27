package dev.pocketbucket.data

import dev.pocketbucket.auth.Connection
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Bitbucket Data Center REST 1.0 adapter. No cookies, logging, retries or redirects. */
class BitbucketApi(engine: HttpClientEngine, private val connection: Connection) : AutoCloseable {
    private val client = HttpClient(engine) {
        expectSuccess = false
        followRedirects = false
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    internal suspend inline fun <reified T> get(
        segments: List<String>, start: Int? = null, limit: Int = 25, state: String? = null,
    ): T {
        require(start == null || start >= 0)
        require(limit in 1..100)
        return safeRequest {
            val response = client.get {
                url {
                    takeFrom(connection.server.value)
                    appendPathSegments("rest", "api", "1.0")
                    segments.forEach { appendPathSegments(it, encodeSlash = true) }
                    if (start != null) {
                        parameters.append("start", start.toString())
                        parameters.append("limit", limit.toString())
                    }
                    state?.let { parameters.append("state", it) }
                }
                header(HttpHeaders.Authorization, connection.credentials.authorizationHeader())
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                header(HttpHeaders.CacheControl, "no-store")
            }
            when (response.status.value) {
                in 200..299 -> response.body<T>()
                401 -> throw ApiFailure.Unauthorized()
                403 -> throw ApiFailure.Forbidden()
                404 -> throw ApiFailure.NotFound()
                in 300..399 -> throw ApiFailure.InvalidResponse()
                else -> throw ApiFailure.Http(response.status.value)
            }
        }
    }

    private suspend inline fun <T> safeRequest(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiFailure) {
        throw e
    } catch (_: HttpRequestTimeoutException) {
        throw ApiFailure.Timeout()
    } catch (_: ConnectTimeoutException) {
        throw ApiFailure.Timeout()
    } catch (_: SocketTimeoutException) {
        throw ApiFailure.Timeout()
    } catch (_: SerializationException) {
        throw ApiFailure.InvalidResponse()
    } catch (_: io.ktor.serialization.ContentConvertException) {
        throw ApiFailure.InvalidResponse()
    } catch (_: io.ktor.client.call.NoTransformationFoundException) {
        throw ApiFailure.InvalidResponse()
    } catch (_: Exception) {
        // Never expose a transport exception's URL, headers, response or nested causes.
        throw ApiFailure.Connection()
    }

    override fun close() { client.close() }
}

package dev.pocketbucket.auth

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.util.encodeBase64

/** Secrets deliberately have no generated toString/equals/component/copy methods. */
sealed class Credentials {
    class Basic(val username: String, val password: String) : Credentials() {
        init {
            require(username.isNotBlank() && ':' !in username && username.none { it.isISOControl() }) {
                "Enter a username without colons or control characters."
            }
            require(password.isNotEmpty()) { "Enter a password." }
        }
        override fun toString() = "Basic([redacted])"
    }
    class Token(val token: String) : Credentials() {
        init {
            require(token.isNotBlank() && token.none { it.isWhitespace() || it.isISOControl() }) {
                "Enter a token without whitespace or control characters."
            }
        }
        override fun toString() = "Token([redacted])"
    }

    fun authorizationHeader(): String = when (this) {
        is Basic -> "Basic " + "$username:$password".encodeToByteArray().encodeBase64()
        is Token -> "Bearer $token"
    }
}

class ServerUrl private constructor(val value: String) {
    companion object {
        fun parse(input: String): ServerUrl {
            val message = "Use an HTTPS server URL, optionally with a context path; omit credentials, query and fragment."
            val url = try { Url(input.trim()) } catch (_: Exception) { throw IllegalArgumentException(message) }
            require(url.protocol == URLProtocol.HTTPS && url.host.isNotBlank() &&
                url.user == null && url.password == null && url.parameters.isEmpty() &&
                url.fragment.isEmpty() && '?' !in input && '#' !in input &&
                input.trim().startsWith("https://", ignoreCase = true)) { message }
            return ServerUrl(url.toString().trimEnd('/'))
        }
    }
    override fun toString() = value
}

class Connection(val server: ServerUrl, val credentials: Credentials) {
    override fun toString() = "Connection([redacted])"
}

/** Implementations encrypt at rest using platform facilities; never plain preferences. */
interface CredentialStore {
    suspend fun load(): Connection?
    suspend fun save(connection: Connection)
    suspend fun clear()
}

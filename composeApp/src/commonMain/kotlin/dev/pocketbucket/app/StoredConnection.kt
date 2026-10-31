package dev.pocketbucket.app

import dev.pocketbucket.auth.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class StoredConnection(
    val server: String, val method: String, val username: String = "", val secret: String,
) {
    override fun toString() = "StoredConnection([redacted])"
}
internal fun Connection.encode(): String {
    val record = when (val auth = credentials) {
        is Credentials.Basic -> StoredConnection(server.value, "basic", auth.username, auth.password)
        is Credentials.Token -> StoredConnection(server.value, "token", secret = auth.token)
    }
    return Json.encodeToString(record)
}
internal fun decodeConnection(value: String): Connection {
    val record = Json.decodeFromString<StoredConnection>(value)
    val credentials = when (record.method) {
        "basic" -> Credentials.Basic(record.username, record.secret)
        "token" -> Credentials.Token(record.secret)
        else -> error("Unsupported saved authentication method")
    }
    return Connection(ServerUrl.parse(record.server), credentials)
}

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("CAST_NEVER_SUCCEEDS")
package dev.pocketbucket.app

import dev.pocketbucket.auth.Connection
import dev.pocketbucket.auth.CredentialStore
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

/** A single device-local Keychain item, accessible only while the device is unlocked. */
class IosCredentialStore : CredentialStore {
    private val mutex = Mutex()

    // Security constants are CF pointers, not Kotlin objects to put in an NSDictionary.
    // The pointed-to values stay retained for the duration of each synchronous call.
    private inline fun <T> withDictionary(
        entries: Map<CFStringRef?, CFTypeRef?>, action: (CFDictionaryRef) -> T,
    ): T = memScoped {
        val keys = allocArrayOf(*entries.keys.toTypedArray())
        val values = allocArrayOf(*entries.values.toTypedArray())
        val dictionary = CFDictionaryCreate(kCFAllocatorDefault, keys.reinterpret(), values.reinterpret(),
            entries.size.convert(), null, null) ?: error("Keychain query allocation failed")
        try { action(dictionary) } finally { CFRelease(dictionary) }
    }

    private inline fun <T> withQuery(
        extra: Map<CFStringRef?, CFTypeRef?> = emptyMap(), action: (CFDictionaryRef) -> T,
    ): T {
        val service = CFBridgingRetain("dev.pocketbucket.connection.v1")
        val account = CFBridgingRetain("connection")
        return try {
            withDictionary(mapOf(kSecClass to kSecClassGenericPassword,
                kSecAttrService to service, kSecAttrAccount to account) + extra, action)
        } finally { CFBridgingRelease(service); CFBridgingRelease(account) }
    }

    override suspend fun load(): Connection? = withContext(Dispatchers.Default) {
        mutex.withLock {
            withQuery(mapOf(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne)) { query ->
                memScoped {
                    val result = alloc<CFTypeRefVar>()
                    val status = SecItemCopyMatching(query, result.ptr)
                    if (status == errSecItemNotFound) null
                    else {
                        check(status == errSecSuccess) { "Keychain read failed" }
                        val data = CFBridgingRelease(result.value) as NSData
                        val value = NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
                            ?: error("Invalid Keychain encoding")
                        decodeConnection(value)
                    }
                }
            }
        }
    }
    override suspend fun save(connection: Connection) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val data = (connection.encode() as NSString).dataUsingEncoding(NSUTF8StringEncoding)!!
            val retainedData = CFBridgingRetain(data)
            try {
                val attributes = mapOf(kSecValueData to retainedData,
                    kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
                val status = withQuery { query ->
                    withDictionary(attributes) { changes -> SecItemUpdate(query, changes) }
                }
                if (status == errSecItemNotFound) {
                    withQuery(attributes) { query ->
                        check(SecItemAdd(query, null) == errSecSuccess) { "Keychain save failed" }
                    }
                } else { check(status == errSecSuccess) { "Keychain update failed" } }
            } finally { CFBridgingRelease(retainedData) }
        }
    }
    override suspend fun clear() = withContext(Dispatchers.Default) {
        mutex.withLock {
            withQuery { query ->
                val status = SecItemDelete(query)
                check(status == errSecSuccess || status == errSecItemNotFound) { "Keychain delete failed" }
            }
        }
    }
}

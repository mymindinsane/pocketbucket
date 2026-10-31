package dev.pocketbucket.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.pocketbucket.auth.Connection
import dev.pocketbucket.auth.CredentialStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Preferences contain only AES-GCM ciphertext + IV; the key stays in Android Keystore. */
class AndroidCredentialStore(context: Context) : CredentialStore {
    private val preferences = context.applicationContext.getSharedPreferences("secure_session", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    override suspend fun load(): Connection? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ciphertext = preferences.getString("payload", null) ?: return@withLock null
            val iv = preferences.getString("iv", null) ?: error("Missing secure storage IV")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            decodeConnection(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)).decodeToString())
        }
    }
    override suspend fun save(connection: Connection) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val bytes = connection.encode().encodeToByteArray()
            val encrypted = try { cipher.doFinal(bytes) } finally { bytes.fill(0) }
            // Keep the commit result: KTX edit returns Unit and would hide disk-write failure.
            check(preferences.edit()
                .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).commit())
        }
    }
    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Keep the commit result: KTX edit returns Unit and would hide disk-write failure.
            check(preferences.edit().clear().commit())
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(ALIAS) }
            Unit
        }
    }
    private companion object { const val ALIAS = "dev.pocketbucket.connection.v1" }
}

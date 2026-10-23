package com.will.mvvmbase.feature.auth.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import com.will.mvvmbase.feature.auth.domain.AuthSessionTokens
import com.will.mvvmbase.core.apm.ApmMonitoredMethod
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : SessionStore {
    private val preferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

    @Synchronized
    @ApmMonitoredMethod(slowThresholdMillis = 25)
    override fun readSession(): AuthSessionTokens? {
        val encodedIv = preferences.getString(SESSION_IV_KEY, null)
        val encodedCiphertext = preferences.getString(SESSION_CIPHERTEXT_KEY, null)
        if (encodedIv == null || encodedCiphertext == null) {
            if (encodedIv != null || encodedCiphertext != null) clear()
            return readLegacyAccessToken()
        }
        return runCatching {
            val plaintext = decrypt(encodedIv, encodedCiphertext)
            val json = org.json.JSONObject(String(plaintext, StandardCharsets.UTF_8))
            val accessToken = json.getString(ACCESS_TOKEN_FIELD)
                .takeIf(String::isNotBlank)
                ?: error("Stored access token is blank.")
            AuthSessionTokens(
                accessToken = accessToken,
                refreshToken = json.optString(REFRESH_TOKEN_FIELD).takeIf(String::isNotBlank),
            )
        }.getOrElse {
            clear()
            null
        }
    }

    @Synchronized
    @ApmMonitoredMethod(slowThresholdMillis = 25)
    override fun saveSession(tokens: AuthSessionTokens) {
        require(tokens.accessToken.isNotBlank()) { "Access token must not be blank." }
        val json = org.json.JSONObject().put(ACCESS_TOKEN_FIELD, tokens.accessToken).apply {
            tokens.refreshToken?.takeIf(String::isNotBlank)?.let {
                put(REFRESH_TOKEN_FIELD, it)
            }
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(json.toString().toByteArray(StandardCharsets.UTF_8))
        val saved = preferences.edit()
            .remove(LEGACY_IV_KEY)
            .remove(LEGACY_CIPHERTEXT_KEY)
            .putString(SESSION_IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(SESSION_CIPHERTEXT_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .commit()
        check(saved) { "Could not persist the authentication session." }
    }

    @Synchronized
    @ApmMonitoredMethod(slowThresholdMillis = 25)
    override fun clear() {
        // Make any leftover ciphertext unreadable if SharedPreferences cannot commit the removal.
        val keyDeletionFailure = runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }.exceptionOrNull()
        val cleared = preferences.edit()
            .remove(SESSION_IV_KEY)
            .remove(SESSION_CIPHERTEXT_KEY)
            .remove(LEGACY_IV_KEY)
            .remove(LEGACY_CIPHERTEXT_KEY)
            .commit()
        if (!cleared) {
            throw IllegalStateException(
                "Could not clear the authentication session.",
                keyDeletionFailure,
            )
        }
    }

    private fun readLegacyAccessToken(): AuthSessionTokens? {
        val encodedIv = preferences.getString(LEGACY_IV_KEY, null) ?: return null
        val encodedCiphertext = preferences.getString(LEGACY_CIPHERTEXT_KEY, null) ?: return null
        return runCatching {
            val token = String(decrypt(encodedIv, encodedCiphertext), StandardCharsets.UTF_8)
            require(token.isNotBlank()) { "Stored access token is blank." }
            AuthSessionTokens(token)
        }.getOrElse {
            clear()
            null
        }
    }

    private fun decrypt(encodedIv: String, encodedCiphertext: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(encodedIv, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP))
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mvvm_base_auth_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val SESSION_IV_KEY = "session_iv"
        const val SESSION_CIPHERTEXT_KEY = "session_ciphertext"
        const val LEGACY_IV_KEY = "token_iv"
        const val LEGACY_CIPHERTEXT_KEY = "token_ciphertext"
        const val ACCESS_TOKEN_FIELD = "accessToken"
        const val REFRESH_TOKEN_FIELD = "refreshToken"
    }
}

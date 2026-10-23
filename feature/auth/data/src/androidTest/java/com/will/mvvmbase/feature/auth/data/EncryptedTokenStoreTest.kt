package com.will.mvvmbase.feature.auth.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.will.mvvmbase.feature.auth.domain.AuthSessionTokens
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedTokenStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @After fun cleanup() { EncryptedTokenStore(context).clear() }
    @Test fun encryptedSessionSurvivesReopenAndLogoutRemovesIt() {
        val store = EncryptedTokenStore(context)
        val tokens = AuthSessionTokens("private-access-value", "private-refresh-value")
        store.saveSession(tokens)
        val raw = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(raw.contains(tokens.accessToken)); assertFalse(raw.contains(tokens.refreshToken!!))
        assertEquals(tokens, EncryptedTokenStore(context).readSession())
        store.clear(); assertNull(EncryptedTokenStore(context).readSession())
    }
    @Test fun corruptedCiphertextSafelyInvalidatesSession() {
        val store = EncryptedTokenStore(context)
        store.saveSession(AuthSessionTokens("access", "refresh"))
        context.getSharedPreferences("auth_session", Context.MODE_PRIVATE).edit()
            .putString("session_ciphertext", "invalid-ciphertext").commit()
        assertNull(store.readSession())
    }
}

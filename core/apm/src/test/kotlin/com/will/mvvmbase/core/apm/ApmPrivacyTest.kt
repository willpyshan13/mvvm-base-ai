package com.will.mvvmbase.core.apm

import io.opentelemetry.api.common.AttributeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApmPrivacyTest {
    @Test
    fun sensitiveAttributesAreExcludedWhileOperationalContextRemains() {
        val attributes = ApmPrivacy.attributes(mapOf(
            "feature" to "checkout",
            "authorization" to "opaque-secret",
            "api_key" to "opaque-secret",
            "contact" to "person@example.com",
            "destination" to "https://example.com/private?token=secret",
            "credential" to "Bearer secret",
            "identifier" to "13800138000",
        ))
        assertEquals("checkout", attributes.get(AttributeKey.stringKey("feature")))
        assertEquals(1, attributes.size())
    }

    @Test
    fun attributeBudgetAndValueLengthAreEnforced() {
        val attributes = ApmPrivacy.attributes((0..30).associate { "field.$it" to "safe" })
        assertEquals(16, attributes.size())
        assertNull(ApmPrivacy.attributes(mapOf("feature" to "x".repeat(97)))
            .get(AttributeKey.stringKey("feature")))
    }

    @Test
    fun logMessagesRemoveSensitiveValues() {
        val message = ApmPrivacy.safeLogMessage(
            "person@example.com https://example.com/private Bearer secret 13800138000",
        )
        listOf("person@example.com", "https://example.com", "secret", "13800138000").forEach {
            assertFalse(message.contains(it))
        }
        assertTrue(ApmPrivacy.safeLogMessage("a@b.com ".repeat(100)).length <= 512)
    }

    @Test
    fun dynamicNamesContainingPathsAreRejected() {
        assertFalse(ApmPrivacy.isSafeName("GET /users/123?token=secret"))
    }
}

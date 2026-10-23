package com.will.mvvmbase.core.apm

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

/** Central attribute policy: bounded cardinality, safe names and key-based PII exclusions. */
internal object ApmPrivacy {
    private const val MAX_CUSTOM_ATTRIBUTES = 16
    private const val MAX_CUSTOM_VALUE_LENGTH = 96
    private val emailPattern = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    private val urlPattern = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val bearerPattern = Regex("\\bBearer\\s+\\S+", RegexOption.IGNORE_CASE)
    private val longNumberPattern = Regex("(?<!\\d)\\d{10,}(?!\\d)")
    private val sensitiveTerms = listOf(
        "password", "secret", "token", "authorization", "email", "phone", "user", "account", "uid",
        "cookie", "url", "path", "query", "api_key", "apikey", "credential",
    )

    fun attributes(values: Map<String, String>): Attributes {
        val builder = Attributes.builder()
        values.entries
            .asSequence()
            .mapNotNull { (key, value) ->
                if (!key.hasSafeSyntax() || key.containsSensitiveTerm() || value.length > MAX_CUSTOM_VALUE_LENGTH) {
                    return@mapNotNull null
                }
                if (emailPattern.containsMatchIn(value) || urlPattern.containsMatchIn(value) ||
                    bearerPattern.containsMatchIn(value) || longNumberPattern.containsMatchIn(value)
                ) {
                    return@mapNotNull null
                }
                key to value
            }
            .take(MAX_CUSTOM_ATTRIBUTES)
            .forEach { (key, value) -> builder.put(AttributeKey.stringKey(key), value) }
        return builder.build()
    }

    fun isSafeName(value: String): Boolean = value.hasSafeSyntax()

    fun safeLogMessage(value: String): String {
        val bounded = value.take(512)
        return bounded
            .replace(bearerPattern, "Bearer [redacted]")
            .replace(emailPattern, "[redacted-email]")
            .replace(urlPattern, "[redacted-url]")
            .replace(longNumberPattern, "[redacted-number]")
            .take(512)
    }

    private fun String.hasSafeSyntax(): Boolean =
        length in 1..80 && all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }

    private fun String.containsSensitiveTerm(): Boolean {
        val normalized = lowercase()
        return sensitiveTerms.any(normalized::contains)
    }
}

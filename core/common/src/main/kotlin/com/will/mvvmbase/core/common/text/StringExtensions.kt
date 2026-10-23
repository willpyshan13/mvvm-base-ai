package com.will.mvvmbase.core.common.text

/** Returns a trimmed string, or null when this string is null or blank. */
fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)

/** Truncates by Unicode code point, keeping the complete result within [maxLength]. */
fun String.truncate(maxLength: Int, ellipsis: String = "…"): String {
    require(maxLength >= 0) { "maxLength must be non-negative" }

    val lengthInCodePoints = codePointCount(0, length)
    if (lengthInCodePoints <= maxLength) return this
    if (maxLength == 0) return ""

    val ellipsisLength = ellipsis.codePointCount(0, ellipsis.length)
    if (ellipsisLength >= maxLength) {
        val end = ellipsis.offsetByCodePoints(0, maxLength)
        return ellipsis.substring(0, end)
    }

    val prefixLength = maxLength - ellipsisLength
    val prefixEnd = offsetByCodePoints(0, prefixLength)
    return substring(0, prefixEnd) + ellipsis
}

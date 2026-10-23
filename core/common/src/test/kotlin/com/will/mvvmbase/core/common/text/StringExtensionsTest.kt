package com.will.mvvmbase.core.common.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StringExtensionsTest {
    @Test
    fun trimmedOrNullTrimsTextAndRejectsBlankValues() {
        assertEquals("hello", "  hello  ".trimmedOrNull())
        assertNull("  \n".trimmedOrNull())
        assertNull(null.trimmedOrNull())
    }

    @Test
    fun truncateKeepsTheRequestedLengthWithoutSplittingEmoji() {
        assertEquals("A😀…", "A😀BC".truncate(maxLength = 3))
        assertEquals("…", "hello".truncate(maxLength = 1))
        assertEquals("", "hello".truncate(maxLength = 0))
    }
}

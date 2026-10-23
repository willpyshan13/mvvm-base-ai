package com.will.mvvmbase.core.common.format

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ByteSizeFormatterTest {
    @Test
    fun formatsByteBoundariesAndLargeValues() {
        assertEquals("0 B", 0L.toReadableByteSize(Locale.US))
        assertEquals("1023 B", 1023L.toReadableByteSize(Locale.US))
        assertEquals("1.5 KiB", 1536L.toReadableByteSize(Locale.US))
        assertEquals("-1.5 KiB", (-1536L).toReadableByteSize(Locale.US))
    }
}

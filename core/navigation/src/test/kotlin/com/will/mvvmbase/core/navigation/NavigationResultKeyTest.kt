package com.will.mvvmbase.core.navigation

import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationResultKeyTest {
    @Test fun roundTripsTypedValueAndRejectsInvalidPayload() {
        val key = NavigationResultKey("toolbox.selection", String.serializer())
        assertEquals("A", key.decode(key.encode("A")))
        assertNull(key.decode("not json"))
    }
}

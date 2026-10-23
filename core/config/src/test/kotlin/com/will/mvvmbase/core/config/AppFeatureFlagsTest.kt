package com.will.mvvmbase.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppFeatureFlagsTest {
    @Test fun parsesOnlyKnownBooleanFlagsAndUsesDefaults() {
        val values = AppFeatureFlags.parse("""{"features":{"feed.sync":false,"unknown":false}}""")
        assertEquals(false, values?.get(FeatureFlag.FEED_SYNC))
        assertEquals(true, values?.get(FeatureFlag.VIDEO_PLAYER))
    }

    @Test fun rejectsMalformedConfiguration() {
        assertNull(AppFeatureFlags.parse("""{"features":[]}"""))
    }
}

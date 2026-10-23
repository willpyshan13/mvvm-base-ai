package com.will.mvvmbase.benchmark

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val baseline = BaselineProfileRule()
    @Test fun generate() = baseline.collect(packageName = BuildConfig.TARGET_PACKAGE) {
        pressHome(); startActivityAndWait()
        startActivityAndWait(Intent(Intent.ACTION_VIEW, Uri.parse("wbase://open/toolbox"))
            .setPackage(BuildConfig.TARGET_PACKAGE))
        device.waitForIdle()
    }
}

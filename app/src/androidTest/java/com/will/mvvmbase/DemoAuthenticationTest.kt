package com.will.mvvmbase

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoAuthenticationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun text(id: Int) = rule.activity.getString(id)
    @Test fun demoLoginReturnsToProtectedTargetAndLogoutRestoresGate() {
        assumeTrue(BuildConfig.DEMO_AUTH)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(text(com.will.mvvmbase.feature.home.R.string.ui_homescreen_1)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(text(com.will.mvvmbase.feature.home.R.string.ui_homescreen_4)).performClick()
        rule.onNodeWithText(text(com.will.mvvmbase.feature.auth.R.string.ui_loginscreen_3)).performTextInput("demo")
        rule.onNodeWithText(text(com.will.mvvmbase.feature.auth.R.string.ui_loginscreen_4)).performTextInput("demo1234")
        rule.onAllNodesWithText(text(com.will.mvvmbase.feature.auth.R.string.ui_loginscreen_6))
            .filter(hasClickAction()).onFirst().performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(text(com.will.mvvmbase.feature.home.R.string.ui_summaryscreen_2)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(text(com.will.mvvmbase.feature.home.R.string.ui_summaryscreen_1)).performClick()
        rule.onNodeWithText(text(com.will.mvvmbase.feature.home.R.string.ui_homescreen_2)).performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(text(com.will.mvvmbase.feature.home.R.string.ui_homescreen_3)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

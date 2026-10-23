package com.will.mvvmbase

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private fun text(id: Int, vararg args: Any) = composeRule.activity.getString(id, *args)
    private fun ready() {
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(text(com.will.mvvmbase.feature.home.R.string.ui_homescreen_1)).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun wirelessScreenRetainsModeAndUuidAndReturnsToToolbox() {
        ready()
        composeRule.onNodeWithTag("tab-1").performClick()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.toolbox.R.string.connectivity_entry))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.connectivity.R.string.classic)).performScrollTo().performClick()
        val uuid = hasSetTextAction() and hasText(text(com.will.mvvmbase.feature.connectivity.R.string.service_uuid))
        composeRule.onNode(uuid).performTextClearance()
        composeRule.onNode(uuid).performTextInput("00001102-0000-1000-8000-00805f9b34fb")
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNode(uuid).assertTextContains("00001102-0000-1000-8000-00805f9b34fb")
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.connectivity.R.string.refresh_wifi)).performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.toolbox.R.string.connectivity_entry)).assertExists()
    }

    @Test fun homeCanOpenSettingsAndNetworkStatusThenReturn() {
        ready()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.home.R.string.ui_homescreen_7)).performClick()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.media.R.string.ui_videoplayerscreen_2)).assertIsDisplayed()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.media.R.string.ui_videoplayerscreen_7)).assertIsDisplayed()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.media.R.string.ui_videoplayerscreen_1)).performClick()
        composeRule.onNodeWithTag("tab-2").performClick()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.settings.R.string.ui_settingsscreen_3)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("tab-0").performClick()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.home.R.string.ui_homescreen_6)).performClick()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.status.R.string.ui_statusscreen_9)).assertIsDisplayed()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.status.R.string.ui_statusscreen_1)).performClick()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.home.R.string.ui_homescreen_1)).assertIsDisplayed()
    }
    @Test fun independentTabsAndActivityRecreationPreserveDraft() {
        ready()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.home.R.string.ui_homescreen_8)).performTextInput("restored draft")
        composeRule.onNodeWithTag("tab-1").performClick()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.toolbox.R.string.ui_toolboxscreen_4)).assertIsDisplayed()
        composeRule.onNodeWithTag("tab-0").performClick()
        composeRule.onNodeWithText("restored draft").assertExists()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("restored draft").assertExists()
    }
    @Test fun protectedDeepLinkRetainsTargetWhileAuthGateRuns() {
        ready()
        composeRule.runOnIdle { composeRule.activity.openDeepLink(Uri.parse("wbase://open/summary")) }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(text(com.will.mvvmbase.feature.auth.R.string.ui_loginscreen_3)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.auth.R.string.ui_loginscreen_3)).assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.auth.R.string.ui_loginscreen_3)).assertIsDisplayed()
    }
    @Test fun notificationDeepLinkAndResultDelivery() {
        ready()
        composeRule.runOnIdle { composeRule.activity.openDeepLink(Uri.parse("wbase://open/toolbox")) }
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.toolbox.R.string.ui_toolboxscreen_9)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.toolbox.R.string.option_a)).performClick()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.toolbox.R.string.result_value, "A")).assertExists()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.toolbox.R.string.ui_toolboxscreen_10)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.toolbox.R.string.result_value, "A")).assertDoesNotExist()
    }
    @Test fun personalCenterRetainsDraftOnRecreationAndSystemBackReturnsToSettings() {
        ready()
        composeRule.onNodeWithTag("tab-2").performClick()
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.settings.R.string.account_open_profile))
            .performScrollTo().performClick()
        val field = hasSetTextAction() and hasText(text(com.will.mvvmbase.feature.settings.R.string.profile_nickname_label))
        composeRule.onNode(field).performTextClearance()
        composeRule.onNode(field).performTextInput("😀unsaved profile")
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNode(field).assertTextContains("😀unsaved profile")
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText(text(com.will.mvvmbase.feature.settings.R.string.account_open_profile))
            .assertExists()
    }
}

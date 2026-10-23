package com.will.mvvmbase

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import com.will.mvvmbase.R
import com.will.mvvmbase.core.designsystem.adaptive.AppWindowInfo
import com.will.mvvmbase.core.designsystem.adaptive.LocalAppWindowInfo

@Composable
internal fun AppNavigationShell(selected: Int, select: (Int) -> Unit, content: @Composable () -> Unit) {
    val pip by com.will.mvvmbase.core.media.AppPictureInPicture.active.collectAsStateWithLifecycle()
    val fullscreen by com.will.mvvmbase.core.media.AppPictureInPicture.fullscreen.collectAsStateWithLifecycle()
    val labels = listOf(
        stringResource(R.string.ui_appnavigationshell_1),
        stringResource(R.string.ui_appnavigationshell_2),
        stringResource(R.string.ui_appnavigationshell_3),
        stringResource(R.string.ui_appnavigationshell_4),
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowInfo = AppWindowInfo.from(maxWidth, maxHeight)
        val rail = windowInfo.useNavigationRail
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        CompositionLocalProvider(LocalAppWindowInfo provides windowInfo) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                bottomBar = {
                    if (!rail && !pip && !fullscreen && !imeVisible) NavigationBar {
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = selected == index,
                                onClick = { select(index) },
                                icon = { Text(listOf("✓", "▦", "⚙", "☷")[index]) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("tab-$index"),
                            )
                        }
                    }
                },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (rail && !pip && !fullscreen) NavigationRail {
                        labels.forEachIndexed { index, label ->
                            NavigationRailItem(
                                selected = selected == index,
                                onClick = { select(index) },
                                icon = { Text(listOf("✓", "▦", "⚙", "☷")[index]) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("tab-$index"),
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        if (pip || fullscreen) content()
                        else Box(Modifier.fillMaxHeight().widthIn(max = windowInfo.contentMaxWidth).fillMaxWidth()) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

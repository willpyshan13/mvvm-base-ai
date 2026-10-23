package com.will.mvvmbase.core.designsystem.adaptive

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppWidthClass { Compact, Medium, Expanded }
enum class AppHeightClass { Compact, Medium, Expanded }

/** Derived from the current app window; it can change after rotation, resizing, or folding. */
data class AppWindowInfo(val width: AppWidthClass, val height: AppHeightClass) {
    val useNavigationRail: Boolean
        get() = width != AppWidthClass.Compact && height != AppHeightClass.Compact

    val contentMaxWidth: Dp
        get() = if (width == AppWidthClass.Expanded) 960.dp else 840.dp

    companion object {
        fun from(width: Dp, height: Dp) = AppWindowInfo(
            width = when {
                width < 600.dp -> AppWidthClass.Compact
                width < 840.dp -> AppWidthClass.Medium
                else -> AppWidthClass.Expanded
            },
            height = when {
                height < 480.dp -> AppHeightClass.Compact
                height < 900.dp -> AppHeightClass.Medium
                else -> AppHeightClass.Expanded
            },
        )
    }
}

val LocalAppWindowInfo = compositionLocalOf {
    AppWindowInfo(AppWidthClass.Compact, AppHeightClass.Medium)
}

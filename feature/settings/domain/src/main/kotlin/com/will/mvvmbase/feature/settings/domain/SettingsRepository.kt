package com.will.mvvmbase.feature.settings.domain

import kotlinx.coroutines.flow.Flow

enum class ThemeMode { System, Light, Dark }
enum class ThemePalette { Blue, Green, Purple }

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
    val themePalette: Flow<ThemePalette>
    suspend fun setThemePalette(palette: ThemePalette)
    val darkTheme: Flow<Boolean>
    val videoCacheSizeMb: Flow<Int>
    val profileNickname: Flow<String>

    suspend fun setDarkTheme(enabled: Boolean)

    suspend fun setVideoCacheSizeMb(sizeMb: Int)

    suspend fun setProfileNickname(nickname: String)

    suspend fun resetPreferences()

    companion object {
        const val MAX_PROFILE_NICKNAME_LENGTH = 24
        const val DEFAULT_VIDEO_CACHE_SIZE_MB = 512
        const val MIN_CUSTOM_VIDEO_CACHE_SIZE_MB = 16
        const val MAX_CUSTOM_VIDEO_CACHE_SIZE_MB = 16_384
        val VIDEO_CACHE_PRESET_OPTIONS_MB = listOf(0, 128, 256, 512, 1024)

        fun isSupportedVideoCacheSizeMb(sizeMb: Int): Boolean =
            sizeMb in VIDEO_CACHE_PRESET_OPTIONS_MB ||
                sizeMb in MIN_CUSTOM_VIDEO_CACHE_SIZE_MB..MAX_CUSTOM_VIDEO_CACHE_SIZE_MB

        fun isValidProfileNickname(nickname: String): Boolean {
            val normalized = nickname.trim()
            return normalized.isNotEmpty() &&
                normalized.codePointCount(0, normalized.length) <= MAX_PROFILE_NICKNAME_LENGTH &&
                normalized.none { Character.isISOControl(it) }
        }
    }
}


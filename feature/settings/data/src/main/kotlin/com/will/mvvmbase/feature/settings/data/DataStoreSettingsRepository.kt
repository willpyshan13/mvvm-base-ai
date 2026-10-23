package com.will.mvvmbase.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.will.mvvmbase.feature.settings.domain.ThemeMode
import com.will.mvvmbase.feature.settings.domain.ThemePalette
import com.will.mvvmbase.feature.settings.domain.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val themePaletteKey = stringPreferencesKey("theme_palette")
    override val themeMode = store.data.map { preferences ->
        preferences[themeModeKey]?.let { value -> ThemeMode.entries.find { it.name == value } }
            ?: preferences[booleanPreferencesKey("dark_theme")]?.let { if (it) ThemeMode.Dark else ThemeMode.Light }
            ?: ThemeMode.System
    }
    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[themeModeKey] = mode.name; it[darkThemeKey] = mode == ThemeMode.Dark }
    }
    override val themePalette = store.data.map { preferences ->
        preferences[themePaletteKey]?.let { value -> ThemePalette.entries.find { it.name == value } }
            ?: ThemePalette.Blue
    }
    override suspend fun setThemePalette(palette: ThemePalette) {
        store.edit { it[themePaletteKey] = palette.name }
    }
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val videoCacheSizeMbKey = intPreferencesKey("video_cache_size_mb")
    private val profileNicknameKey = stringPreferencesKey("profile_nickname")

    override val darkTheme = store.data.map { preferences -> preferences[darkThemeKey] ?: false }
    override val profileNickname = store.data.map { preferences -> preferences[profileNicknameKey].orEmpty() }
    override val videoCacheSizeMb = store.data.map { preferences ->
        preferences[videoCacheSizeMbKey]
            ?.takeIf(SettingsRepository::isSupportedVideoCacheSizeMb)
            ?: SettingsRepository.DEFAULT_VIDEO_CACHE_SIZE_MB
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        store.edit { preferences -> preferences[darkThemeKey] = enabled
            preferences[themeModeKey] = if (enabled) ThemeMode.Dark.name else ThemeMode.Light.name }
    }

    override suspend fun setVideoCacheSizeMb(sizeMb: Int) {
        require(SettingsRepository.isSupportedVideoCacheSizeMb(sizeMb)) {
            "Unsupported video cache size: $sizeMb MB."
        }
        store.edit { preferences -> preferences[videoCacheSizeMbKey] = sizeMb }
    }

    override suspend fun setProfileNickname(nickname: String) {
        val normalized = nickname.trim()
        require(SettingsRepository.isValidProfileNickname(normalized)) { "Invalid profile nickname." }
        store.edit { preferences -> preferences[profileNicknameKey] = normalized }
    }

    override suspend fun resetPreferences() {
        store.edit { preferences ->
            preferences[themeModeKey] = ThemeMode.System.name
            preferences[themePaletteKey] = ThemePalette.Blue.name
            preferences[darkThemeKey] = false
            preferences[videoCacheSizeMbKey] = SettingsRepository.DEFAULT_VIDEO_CACHE_SIZE_MB
        }
    }
}


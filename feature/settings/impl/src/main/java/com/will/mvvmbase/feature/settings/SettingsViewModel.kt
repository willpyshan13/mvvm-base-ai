package com.will.mvvmbase.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.will.mvvmbase.feature.settings.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val operationError = MutableStateFlow(false)
    val themeMode = repository.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
        com.will.mvvmbase.feature.settings.domain.ThemeMode.System)
    val themePalette = repository.themePalette.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
        com.will.mvvmbase.feature.settings.domain.ThemePalette.Blue)
    fun setThemeMode(mode: com.will.mvvmbase.feature.settings.domain.ThemeMode) {
        save { repository.setThemeMode(mode) }
    }
    fun setThemePalette(palette: com.will.mvvmbase.feature.settings.domain.ThemePalette) {
        save { repository.setThemePalette(palette) }
    }
    val darkTheme: StateFlow<Boolean> = repository.darkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val videoCacheSizeMb: StateFlow<Int> = repository.videoCacheSizeMb
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsRepository.DEFAULT_VIDEO_CACHE_SIZE_MB,
        )
    val profileNickname: StateFlow<String> = repository.profileNickname
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setDarkTheme(enabled: Boolean) {
        save { repository.setDarkTheme(enabled) }
    }

    fun setVideoCacheSizeMb(sizeMb: Int, onSuccess: () -> Unit = {}) {
        save(onSuccess) { repository.setVideoCacheSizeMb(sizeMb) }
    }

    fun setProfileNickname(nickname: String) {
        save { repository.setProfileNickname(nickname) }
    }
    fun resetPreferences(onSuccess: () -> Unit) {
        save(onSuccess) { repository.resetPreferences() }
    }

    private fun save(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); operationError.value = false; onSuccess() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { operationError.value = true }
        }
    }
}


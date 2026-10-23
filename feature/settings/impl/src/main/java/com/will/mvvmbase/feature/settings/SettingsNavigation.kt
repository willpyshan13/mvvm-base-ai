package com.will.mvvmbase.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.will.mvvmbase.core.navigation.FeatureEntryBuilder
import com.will.mvvmbase.core.navigation.Navigator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

internal fun EntryProviderScope<NavKey>.settingsEntries(
    isAuthenticated: kotlinx.coroutines.flow.StateFlow<Boolean>,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    entry<SettingsKey> {
        SettingsRoute(
            isAuthenticated = isAuthenticated,
            onLogin = onLogin,
            onLogout = onLogout,
            onBack = onBack,
        )
    }
}

class SettingsEntryBuilder @Inject constructor() : FeatureEntryBuilder {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: Navigator) {
        with(scope) {
            settingsEntries(
                isAuthenticated = navigator.authentication,
                onLogin = navigator::login,
                onLogout = navigator::logout,
                onBack = navigator::back,
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsNavigationBindings {
    @Binds
    @IntoSet
    abstract fun bindSettingsEntryBuilder(implementation: SettingsEntryBuilder): FeatureEntryBuilder
}

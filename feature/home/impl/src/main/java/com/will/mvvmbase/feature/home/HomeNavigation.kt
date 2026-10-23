package com.will.mvvmbase.feature.home

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.will.mvvmbase.core.navigation.FeatureEntryBuilder
import com.will.mvvmbase.core.navigation.Navigator
import com.will.mvvmbase.feature.settings.SettingsKey
import com.will.mvvmbase.feature.status.StatusKey
import com.will.mvvmbase.feature.media.VideoPlayerKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

internal fun EntryProviderScope<NavKey>.homeEntries(
    isAuthenticated: kotlinx.coroutines.flow.StateFlow<Boolean>,
    onOpenSummary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenVideoPlayer: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    entry<TasksKey> {
        HomeRoute(
            isAuthenticated = isAuthenticated,
            onOpenSummary = onOpenSummary,
            onOpenSettings = onOpenSettings,
            onOpenStatus = onOpenStatus,
            onOpenVideoPlayer = onOpenVideoPlayer,
            onLogin = onLogin,
            onLogout = onLogout,
        )
    }
    entry<SummaryKey> {
        SummaryRoute(onBack = onBack)
    }
}

class HomeEntryBuilder @Inject constructor() : FeatureEntryBuilder {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: Navigator) {
        with(scope) {
            homeEntries(
                isAuthenticated = navigator.authentication,
                onOpenSummary = { navigator.navigate(SummaryKey) },
                onOpenSettings = { navigator.navigate(SettingsKey) },
                onOpenStatus = { navigator.navigate(StatusKey) },
                onOpenVideoPlayer = { navigator.navigate(VideoPlayerKey) },
                onLogin = navigator::login,
                onLogout = navigator::logout,
                onBack = navigator::back,
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeNavigationBindings {
    @Binds
    @IntoSet
    abstract fun bindHomeEntryBuilder(implementation: HomeEntryBuilder): FeatureEntryBuilder
}

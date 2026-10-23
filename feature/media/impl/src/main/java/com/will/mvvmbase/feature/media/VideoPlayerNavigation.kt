package com.will.mvvmbase.feature.media

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import com.will.mvvmbase.core.config.AppFeatureFlags
import com.will.mvvmbase.core.config.FeatureFlag
import com.will.mvvmbase.core.navigation.FeatureEntryBuilder
import com.will.mvvmbase.core.navigation.Navigator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

class VideoPlayerEntryBuilder @Inject constructor() : FeatureEntryBuilder {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: Navigator) {
        with(scope) {
            entry<VideoPlayerKey> {
                val flags by AppFeatureFlags.values.collectAsState()
                if (flags[FeatureFlag.VIDEO_PLAYER] == false) {
                    Text(stringResource(R.string.player_temporarily_unavailable))
                } else {
                    VideoPlayerScreen(onBack = navigator::back)
                }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VideoPlayerNavigationBindings {
    @Binds
    @IntoSet
    abstract fun bindVideoPlayerEntryBuilder(
        implementation: VideoPlayerEntryBuilder,
    ): FeatureEntryBuilder
}

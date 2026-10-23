package com.will.mvvmbase.feature.feed

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

class FeedEntryBuilder @Inject constructor() : FeatureEntryBuilder {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: Navigator) {
        with(scope) {
            entry<FeedKey> {
                FeedScreen(onBack = navigator::back)
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FeedNavigationBindings {
    @Binds
    @IntoSet
    abstract fun bindFeedEntryBuilder(
        implementation: FeedEntryBuilder,
    ): FeatureEntryBuilder
}

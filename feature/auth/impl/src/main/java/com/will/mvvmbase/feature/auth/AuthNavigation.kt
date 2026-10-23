package com.will.mvvmbase.feature.auth

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

class AuthEntryBuilder @Inject constructor() : FeatureEntryBuilder {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: Navigator) {
        with(scope) {
            entry<LoginKey> {
                LoginRoute(
                    onLoginSuccess = navigator::onLoginSuccess,
                    onBack = navigator::back,
                )
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthNavigationBindings {
    @Binds
    @IntoSet
    abstract fun bindAuthEntryBuilder(implementation: AuthEntryBuilder): FeatureEntryBuilder
}

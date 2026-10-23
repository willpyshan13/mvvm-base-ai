package com.will.mvvmbase.feature.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.will.mvvmbase.feature.settings.domain.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsRepositoryBindings {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(implementation: DataStoreSettingsRepository): SettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object SettingsDataModule {
    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore
}

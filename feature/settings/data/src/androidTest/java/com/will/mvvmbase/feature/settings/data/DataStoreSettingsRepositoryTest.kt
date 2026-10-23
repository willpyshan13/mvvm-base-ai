package com.will.mvvmbase.feature.settings.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import com.will.mvvmbase.feature.settings.domain.SettingsRepository
import com.will.mvvmbase.feature.settings.domain.ThemeMode
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreSettingsRepositoryTest {
    @Test fun preferencesAndUnicodeNicknamePersistAndResetPreservesProfile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "theme-test.preferences_pb")
        file.delete()

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val firstStore = PreferenceDataStoreFactory.create(
                scope = firstScope,
                produceFile = { file },
            )
            val repository = DataStoreSettingsRepository(firstStore)
            repository.setDarkTheme(true)
            repository.setVideoCacheSizeMb(128)
            repository.setProfileNickname("  😀昵称  ")
            for (invalid in listOf("   ", "a\nb", "😀".repeat(25))) {
                try {
                    repository.setProfileNickname(invalid)
                    fail("Invalid nickname must not be saved")
                } catch (_: IllegalArgumentException) { }
            }
        } finally {
            firstScope.cancel()
        }

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopenedStore = PreferenceDataStoreFactory.create(
                scope = secondScope,
                produceFile = { file },
            )
            val repository = DataStoreSettingsRepository(reopenedStore)
            assertTrue(repository.darkTheme.first())
            assertEquals(128, repository.videoCacheSizeMb.first())
            assertEquals("😀昵称", repository.profileNickname.first())
            repository.setProfileNickname("😀".repeat(24))
            assertEquals("😀".repeat(24), repository.profileNickname.first())
            repository.resetPreferences()
            assertEquals(ThemeMode.System, repository.themeMode.first())
            assertEquals(false, repository.darkTheme.first())
            assertEquals(SettingsRepository.DEFAULT_VIDEO_CACHE_SIZE_MB, repository.videoCacheSizeMb.first())
            assertEquals("😀".repeat(24), repository.profileNickname.first())
        } finally {
            secondScope.cancel()
            file.delete()
        }
    }
}

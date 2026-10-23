package com.will.mvvmbase.feature.feed.data

import android.content.Context
import androidx.room.Room
import androidx.work.*
import com.will.mvvmbase.core.network.NetworkConfiguration
import com.will.mvvmbase.feature.feed.domain.FeedRepository
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton
import retrofit2.Retrofit
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class FeedSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        fun enqueue(context: Context) {
            if (!com.will.mvvmbase.core.config.AppFeatureFlags.enabled(
                    com.will.mvvmbase.core.config.FeatureFlag.FEED_SYNC,
                )) return
            WorkManager.getInstance(context).enqueueUniqueWork("feed-sync", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<FeedSyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build())
        }
    }
    override suspend fun doWork(): Result {
        if (!com.will.mvvmbase.core.config.AppFeatureFlags.enabled(
                com.will.mvvmbase.core.config.FeatureFlag.FEED_SYNC,
            )) return Result.success()
        return try {
            EntryPointAccessors.fromApplication(applicationContext, FeedWorkerDependencies::class.java).repository().sync()
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (runAttemptCount < 5) Result.retry() else Result.failure() }
    }
}

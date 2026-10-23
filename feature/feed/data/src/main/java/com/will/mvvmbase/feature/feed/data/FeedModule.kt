package com.will.mvvmbase.feature.feed.data

import android.content.Context
import androidx.room.Room
import androidx.work.*
import com.will.mvvmbase.core.network.NetworkConfiguration
import com.will.mvvmbase.core.apm.AppApm
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

@Module @InstallIn(SingletonComponent::class)
object FeedModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): FeedDatabase =
        Room.databaseBuilder(context, FeedDatabase::class.java, "feed.db")
            .setQueryCallback(AppApm.roomQueryCallback(), AppApm.roomQueryExecutor())
            .build()
    @Provides @Singleton fun repository(@ApplicationContext context: Context, db: FeedDatabase,
        config: NetworkConfiguration, retrofit: Provider<Retrofit>): FeedRepository {
        val backend = if (config.isConfigured) HttpFeedBackend(retrofit.get().create(FeedService::class.java))
            else DemoFeedBackend(context)
        return OfflineFeedRepository(db, backend) {
            FeedSyncWorker.enqueue(context)
        }
    }
}

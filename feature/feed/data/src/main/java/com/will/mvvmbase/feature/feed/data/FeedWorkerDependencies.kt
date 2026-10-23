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

@EntryPoint @InstallIn(SingletonComponent::class)
interface FeedWorkerDependencies { fun repository(): FeedRepository }

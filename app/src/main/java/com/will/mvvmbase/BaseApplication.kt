package com.will.mvvmbase

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.will.mvvmbase.core.config.AppFeatureFlags.initialize(this, BuildConfig.FEATURE_FLAGS_URL)
        if (BuildConfig.DEBUG) {
            com.will.mvvmbase.core.apm.AppApm.installDebugResourceLeakChecks()
        }
        com.will.mvvmbase.core.apm.AppApm.initialize(
            context = this,
            configuration = com.will.mvvmbase.core.apm.ApmConfiguration(
                endpoint = BuildConfig.APM_ENDPOINT,
                enabled = BuildConfig.APM_ENABLED,
                appVersion = BuildConfig.VERSION_NAME,
                environment = BuildConfig.APP_ENVIRONMENT,
            ),
        )
        com.will.mvvmbase.core.designsystem.image.AppImages.install(
            apmInterceptor = com.will.mvvmbase.core.apm.AppApm.imageNetworkInterceptor(),
            callFactoryDecorator = { callFactory ->
                com.will.mvvmbase.core.apm.AppApm.instrumentCallFactory(callFactory)
            },
        )
        com.will.mvvmbase.core.platform.AppTransfers.configureHttpClientHooks(
            networkInterceptor = com.will.mvvmbase.core.apm.AppApm.externalHttpsNetworkInterceptor("file_transfer"),
            callFactoryDecorator = { callFactory ->
                com.will.mvvmbase.core.apm.AppApm.instrumentCallFactory(callFactory)
            },
        )
        com.will.mvvmbase.core.media.AppVideoCacheSettings.configureHttpClientHooks(
            networkInterceptor = com.will.mvvmbase.core.apm.AppApm.externalHttpsNetworkInterceptor("media"),
            callFactoryDecorator = { factory -> com.will.mvvmbase.core.apm.AppApm.instrumentCallFactory(factory) },
        )
        com.will.mvvmbase.core.platform.AppDiagnostics.install(this)
        com.will.mvvmbase.feature.feed.data.FeedSyncWorker.enqueue(this)
    }
}



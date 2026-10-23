package com.will.mvvmbase

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.will.mvvmbase.core.network.CommonHeadersProvider
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Public API metadata only. Keep tokens in SessionTokenProvider and avoid persistent device IDs. */
@Singleton
class AppCommonHeadersProvider @Inject constructor() : CommonHeadersProvider {
    override fun headers(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Accept-Language" to (AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()
            ?: Locale.getDefault().toLanguageTag()),
        "X-App-Version" to BuildConfig.VERSION_NAME,
        "X-App-Version-Code" to BuildConfig.VERSION_CODE.toString(),
        "X-App-Platform" to "android",
        "X-Android-Api" to Build.VERSION.SDK_INT.toString(),
        "X-App-Environment" to BuildConfig.APP_ENVIRONMENT,
    )
}

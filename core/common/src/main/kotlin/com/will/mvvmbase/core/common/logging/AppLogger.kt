package com.will.mvvmbase.core.common.logging

import android.util.Log
import com.will.mvvmbase.core.common.BuildConfig

/** Android log helpers. Calls are ignored in release builds to avoid leaking diagnostic data. */
object AppLogger {
    fun verbose(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.v(tag, message)
    }

    fun debug(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun info(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(tag, message, throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(tag, message, throwable)
    }
}

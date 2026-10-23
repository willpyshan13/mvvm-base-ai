package com.will.mvvmbase.core.apm

import android.os.StrictMode

/** Debug-only resource leak checks that preserve the process's existing VM policy. */
internal object ApmResourceLeakDiagnostics {
    fun install() {
        val builder = StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
            .detectLeakedClosableObjects()
            .detectLeakedSqlLiteObjects()
            .penaltyLog()
        StrictMode.setVmPolicy(builder.build())
    }
}

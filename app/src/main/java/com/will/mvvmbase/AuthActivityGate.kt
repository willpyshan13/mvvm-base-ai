package com.will.mvvmbase

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.will.mvvmbase.feature.auth.domain.AuthRepository
import com.will.mvvmbase.feature.auth.domain.AuthState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Shared authentication checks for Activity-to-Activity navigation. */
object AuthActivityGate {
    const val EXTRA_REQUIRE_LOGIN = "com.will.mvvmbase.extra.REQUIRE_LOGIN"
    const val EXTRA_PENDING_ACTIVITY_INTENT = "com.will.mvvmbase.extra.PENDING_ACTIVITY_INTENT"
    const val STATE_REQUIRE_LOGIN = "com.will.mvvmbase.state.REQUIRE_LOGIN"
    const val STATE_PENDING_ACTIVITY_INTENT = "com.will.mvvmbase.state.PENDING_ACTIVITY_INTENT"

    /**
     * Use before opening a protected Activity. The target Activity should also call
     * [requireAuthentication] before showing protected content.
     */
    fun launchWhenAuthenticated(
        activity: ComponentActivity,
        target: Intent,
        authRepository: AuthRepository,
    ) {
        activity.lifecycleScope.launch {
            when (authRepository.state.first { it !is AuthState.Loading }) {
                AuthState.SignedIn -> activity.startActivity(target)
                AuthState.SignedOut -> routeToLogin(activity, target)
                AuthState.Loading -> Unit
            }
        }
    }

    /**
     * Call from a protected Activity before setting its content. This also watches for
     * logout or session expiry and returns to the public MainActivity when that happens.
     */
    fun requireAuthentication(
        activity: ComponentActivity,
        authRepository: AuthRepository,
        onAuthenticated: () -> Unit,
    ) {
        activity.lifecycleScope.launch {
            when (authRepository.state.first { it !is AuthState.Loading }) {
                AuthState.SignedOut -> {
                    val target = Intent(activity.intent).setClass(activity, activity.javaClass)
                    routeToLogin(activity, target)
                    activity.finish()
                }
                AuthState.SignedIn -> {
                    onAuthenticated()
                    authRepository.state.first { it is AuthState.SignedOut }
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        val target = Intent(activity.intent).setClass(activity, activity.javaClass)
                        routeToLogin(activity, target)
                        activity.finish()
                    }
                }
                AuthState.Loading -> Unit
            }
        }
    }

    private fun routeToLogin(activity: ComponentActivity, target: Intent) {
        routeToMain(activity) {
            putExtra(EXTRA_REQUIRE_LOGIN, true)
            putExtra(EXTRA_PENDING_ACTIVITY_INTENT, target)
        }
    }

    private fun routeToMain(
        activity: ComponentActivity,
        configure: Intent.() -> Unit = {},
    ) {
        activity.startActivity(
            Intent(activity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply(configure),
        )
    }

}

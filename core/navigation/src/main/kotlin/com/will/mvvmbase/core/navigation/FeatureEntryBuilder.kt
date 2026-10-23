package com.will.mvvmbase.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface Navigator {
    val isAuthenticated: Boolean
    val authentication: kotlinx.coroutines.flow.StateFlow<Boolean>
        get() = kotlinx.coroutines.flow.MutableStateFlow(isAuthenticated)

    fun navigate(destination: NavKey)

    fun resultPayload(id: String): kotlinx.coroutines.flow.StateFlow<String?>
    fun consumeResult(id: String)
    fun returnResultPayload(id: String, value: String)

    fun <T : Any> result(key: NavigationResultKey<T>): Flow<T?> =
        resultPayload(key.id).map { value -> value?.let(key::decode) }

    fun <T : Any> consumeResult(key: NavigationResultKey<T>) = consumeResult(key.id)

    fun <T : Any> returnResult(key: NavigationResultKey<T>, value: T) =
        returnResultPayload(key.id, key.encode(value))

    fun back()

    fun login()

    fun logout()

    fun onLoginSuccess()
}

/** A destination that must only be opened while the user has an active session. */
interface AuthRequiredNavKey : NavKey

interface FeatureEntryBuilder {
    fun register(scope: EntryProviderScope<NavKey>, navigator: Navigator)
}

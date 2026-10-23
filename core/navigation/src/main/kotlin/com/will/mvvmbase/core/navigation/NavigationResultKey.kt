package com.will.mvvmbase.core.navigation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** A stable, namespaced key with an explicit wire format for saved navigation results. */
class NavigationResultKey<T : Any>(
    val id: String,
    private val serializer: KSerializer<T>,
) {
    init {
        require(id.contains('.')) { "Navigation result IDs must be namespaced: $id" }
    }

    internal fun encode(value: T): String = Json.encodeToString(serializer, value)

    internal fun decode(value: String): T? =
        runCatching { Json.decodeFromString(serializer, value) }.getOrNull()
}

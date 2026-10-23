package com.will.mvvmbase.feature.feed.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import retrofit2.Response
import retrofit2.http.*

/** Separate persisted fake server so the demo exercises disk cache and a real pending-write queue. */
class DemoFeedBackend(context: Context) : FeedBackend {
    private val prefs = context.getSharedPreferences("demo-feed-server", Context.MODE_PRIVATE)
    @Volatile var offline = false
    private fun rows(): MutableList<RemoteItem> = prefs.getString("rows", null)?.let {
        Json.decodeFromString<List<RemoteItem>>(it).toMutableList()
    } ?: (1..65).map { RemoteItem("item-%03d".format(it), "演示条目 $it", 1) }.toMutableList()
    fun changeOnServer(id: String) {
        val all = rows()
        val old = requireNotNull(all.find { it.id == id })
        all.removeAll { it.id == id }; all.add(old.copy(title = old.title + " (server)", version = old.version + 1))
        check(prefs.edit().putString("rows", Json.encodeToString(all)).commit())
    }
    override suspend fun page(offset: Int, limit: Int): RemotePage {
        if (offline) throw IOException("演示离线模式")
        val all = rows().sortedBy { it.id }
        val page = all.drop(offset).take(limit)
        return RemotePage(page, (offset + page.size).takeIf { it < all.size })
    }
    override suspend fun save(item: RemoteItem): Pair<Boolean, RemoteItem> {
        if (offline) throw IOException("演示离线模式")
        val all = rows()
        val existing = all.find { it.id == item.id }
        if (existing != null && existing.version != item.version) return false to existing
        val updated = item.copy(version = (existing?.version ?: 0) + 1)
        all.removeAll { it.id == item.id }; all.add(updated)
        check(prefs.edit().putString("rows", Json.encodeToString(all)).commit())
        return true to updated
    }
}

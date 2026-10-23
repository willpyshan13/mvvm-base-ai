package com.will.mvvmbase.feature.feed.data

import androidx.room.withTransaction
import com.will.mvvmbase.feature.feed.domain.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class OfflineFeedRepository(private val db: FeedDatabase, private val backend: FeedBackend,
    private val scheduleSync: () -> Unit) : FeedRepository {
    private val dao = db.feedDao()
    private val lock = Mutex()
    override val isDemo = backend is DemoFeedBackend
    override val items = dao.observe().map { rows -> rows.map { FeedItem(it.id, it.title, it.version, it.pending, it.conflict) } }
    override suspend fun refresh() = lock.withLock { fetch(0, true); Unit }
    override suspend fun loadMore(): Boolean = lock.withLock {
        val cursor = dao.cursor() ?: FeedCursor()
        if (cursor.end) false else fetch(cursor.offset, false)
    }
    private suspend fun fetch(offset: Int, refresh: Boolean): Boolean {
        val page = withContext(Dispatchers.IO) { backend.page(offset, 20) }
        require(page.nextOffset == null || page.nextOffset > offset) { "Invalid pagination cursor" }
        db.withTransaction {
            if (refresh) dao.clearClean()
            page.items.forEach { item ->
                if (dao.find(item.id)?.pending != true) dao.put(FeedRow(item.id, item.title, item.version))
            }
            dao.cursor(FeedCursor(offset = page.nextOffset ?: offset + page.items.size, end = page.nextOffset == null))
        }
        return page.nextOffset != null
    }
    override suspend fun add(title: String) {
        require(title.isNotBlank())
        dao.put(FeedRow(UUID.randomUUID().toString(), title.trim(), 0, pending = true))
        scheduleSync()
    }
    override suspend fun rename(id: String, title: String) = lock.withLock {
        require(title.isNotBlank())
        val row = requireNotNull(dao.find(id))
        dao.put(row.copy(title = title.trim(), pending = true))
        scheduleSync()
    }
    override suspend fun sync() = lock.withLock {
        if (!com.will.mvvmbase.core.config.AppFeatureFlags.enabled(
                com.will.mvvmbase.core.config.FeatureFlag.FEED_SYNC,
            )) return@withLock
        dao.pending().forEach { row ->
            if (!com.will.mvvmbase.core.config.AppFeatureFlags.enabled(
                    com.will.mvvmbase.core.config.FeatureFlag.FEED_SYNC,
                )) return@withLock
            val (saved, remote) = withContext(Dispatchers.IO) { backend.save(RemoteItem(row.id, row.title, row.version)) }
            dao.put(if (saved) FeedRow(remote.id, remote.title, remote.version)
                else row.copy(conflict = true, remoteTitle = remote.title, remoteVersion = remote.version))
        }
    }
    override suspend fun resolve(id: String, keepLocal: Boolean) = lock.withLock {
        val row = requireNotNull(dao.find(id))
        require(row.conflict)
        dao.put(row.copy(title = if (keepLocal) row.title else requireNotNull(row.remoteTitle),
            version = requireNotNull(row.remoteVersion), pending = keepLocal, conflict = false,
            remoteTitle = null, remoteVersion = null))
        if (keepLocal) scheduleSync()
    }
    override suspend fun simulateConflict(id: String) = lock.withLock {
        val demo = backend as? DemoFeedBackend ?: error("Demo only")
        val row = requireNotNull(dao.find(id))
        require(!row.pending) { "Sync the item before testing a conflict" }
        dao.put(row.copy(title = row.title + " (local)", pending = true))
        withContext(Dispatchers.IO) { demo.changeOnServer(id) }
        scheduleSync()
    }
    override fun simulateOffline(offline: Boolean) { (backend as? DemoFeedBackend)?.offline = offline }
}

package com.will.mvvmbase.feature.feed.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class OfflineFeedRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: FeedDatabase
    private class Server : FeedBackend {
        val rows = (1..45).map { RemoteItem("%03d".format(it), "item $it", 1) }.toMutableList()
        var offline = false
        override suspend fun page(offset: Int, limit: Int): RemotePage {
            if (offline) throw IOException("offline")
            val page = rows.drop(offset).take(limit)
            return RemotePage(page, (offset + page.size).takeIf { it < rows.size })
        }
        override suspend fun save(item: RemoteItem): Pair<Boolean, RemoteItem> {
            if (offline) throw IOException("offline")
            val old = rows.find { it.id == item.id }
            if (old != null && old.version != item.version) return false to old
            val updated = item.copy(version = item.version + 1)
            rows.removeAll { it.id == item.id }; rows.add(updated)
            return true to updated
        }
    }
    @Before fun open() {
        context.deleteDatabase("feed-test.db")
        db = Room.databaseBuilder(context, FeedDatabase::class.java, "feed-test.db").build()
    }
    @After fun close() { db.close(); context.deleteDatabase("feed-test.db") }
    @Test fun queuedChangesSurviveReopenAndSyncWithoutDuplicates() = runBlocking {
        val server = Server().apply { offline = true }
        var repo = OfflineFeedRepository(db, server) {}
        repo.add("offline draft")
        val id = repo.items.first().single().id
        try { repo.sync(); fail() } catch (_: IOException) {}
        db.close()
        db = Room.databaseBuilder(context, FeedDatabase::class.java, "feed-test.db").build()
        repo = OfflineFeedRepository(db, server) {}
        assertTrue(repo.items.first().single().pending)
        server.offline = false
        repo.sync(); repo.sync()
        assertFalse(repo.items.first().single().pending)
        assertEquals(1, server.rows.count { it.id == id })
    }
    @Test fun paginationCursorPersistsAndFailedRefreshKeepsCache() = runBlocking {
        val server = Server(); var repo = OfflineFeedRepository(db, server) {}
        repo.refresh(); assertEquals(20, repo.items.first().size)
        db.close(); db = Room.databaseBuilder(context, FeedDatabase::class.java, "feed-test.db").build()
        repo = OfflineFeedRepository(db, server) {}
        assertTrue(repo.loadMore()); assertEquals(40, repo.items.first().size)
        assertFalse(repo.loadMore()); assertEquals(45, repo.items.first().size)
        server.offline = true
        try { repo.refresh(); fail() } catch (_: IOException) {}
        assertEquals(45, repo.items.first().size)
    }
    @Test fun conflictsPreserveLocalEditsUntilUserChooses() = runBlocking {
        val server = Server(); val repo = OfflineFeedRepository(db, server) {}
        repo.refresh(); repo.rename("001", "local")
        server.rows[0] = RemoteItem("001", "server", 2)
        repo.sync()
        val conflict = repo.items.first().first { it.id == "001" }
        assertTrue(conflict.conflict); assertEquals("local", conflict.title)
        repo.resolve("001", true); repo.sync()
        assertEquals("local", server.rows.first { it.id == "001" }.title)
        assertEquals(3, server.rows.first { it.id == "001" }.version.toInt())
        repo.rename("001", "another local")
        server.rows.removeAll { it.id == "001" }; server.rows.add(RemoteItem("001", "new server", 4))
        repo.sync(); repo.resolve("001", false)
        val resolved = repo.items.first().first { it.id == "001" }
        assertEquals("new server", resolved.title); assertFalse(resolved.pending); assertFalse(resolved.conflict)
    }
}

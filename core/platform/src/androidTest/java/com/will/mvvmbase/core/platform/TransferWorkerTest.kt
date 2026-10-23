package com.will.mvvmbase.core.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class TransferWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun worker(interceptor: Interceptor): TransferWorker {
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        return TestListenableWorkerBuilder<TransferWorker>(context)
            .setInputData(workDataOf("url" to "https://transfer.test/file"))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters) =
                    TransferWorker(appContext, params, client)
            }).build()
    }
    @Test fun successfulDownloadCommitsFileAndRemovesPartialData() = runBlocking {
        val worker = worker(Interceptor { chain -> Response.Builder().request(chain.request())
            .protocol(Protocol.HTTP_1_1).code(200).message("OK").body("download payload".toResponseBody()).build() })
        val result = worker.doWork() as ListenableWorker.Result.Success
        val file = File(requireNotNull(result.outputData.getString("file")))
        try {
            assertEquals("download payload", file.readText())
            assertFalse(File(file.parentFile, "${worker.id}.part").exists())
        } finally { file.delete() }
    }
    @Test fun transientFailureRetriesWithoutLeavingPartialFile() = runBlocking {
        val worker = worker(Interceptor { throw IOException("offline") })
        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
        assertFalse(File(context.filesDir, "downloads/${worker.id}.part").exists())
    }
    @Test fun cancellingCoroutineCancelsTheHttpCall() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val worker = worker(Interceptor { chain ->
            entered.complete(Unit)
            while (!chain.call().isCanceled()) Thread.sleep(5)
            cancelled.complete(Unit)
            throw IOException("cancelled")
        })
        val work = launch(Dispatchers.Default) { worker.doWork() }
        withTimeout(3_000) { entered.await(); work.cancel(); cancelled.await(); work.join() }
    }
}

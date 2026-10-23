package com.will.mvvmbase.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class ApiErrorTest {
    @Test fun `error envelope preserves business code and useful message`() = runBlocking {
        val result = apiCall<String> { Response.error(422, """{"code":"duplicate","message":"Already exists"}""".toResponseBody()) }
        assertEquals(ApiResult.HttpError(422, "Already exists", "duplicate"), result)
    }
    @Test fun `empty successful response is legal for bodyless endpoints`() = runBlocking {
        assertEquals(ApiResult.Success(Unit), apiCallEmpty { Response.success<Void>(204, null) })
    }
    @Test fun `malformed proxy error retains HTTP status without leaking body`() = runBlocking {
        val result = apiCall<String> { Response.error(502, "<html>private upstream information</html>".toResponseBody()) }
        assertEquals(502, (result as ApiResult.HttpError).statusCode)
        assertFalse(result.message.contains("private"))
    }
    @Test fun `HTTP success can still represent business failure`() {
        val result = ApiResult.Success("rejected").mapBusiness<String, Unit> { ApiResult.BusinessError("quota", "Quota exceeded") }
        assertEquals(ApiResult.BusinessError("quota", "Quota exceeded"), result)
    }
}

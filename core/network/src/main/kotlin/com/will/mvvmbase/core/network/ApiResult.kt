package com.will.mvvmbase.core.network

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class HttpError(val statusCode: Int, val message: String, val businessCode: String? = null) : ApiResult<Nothing>
    data class BusinessError(val code: String, val message: String) : ApiResult<Nothing>
    data class NetworkError(val cause: IOException) : ApiResult<Nothing>
    data class UnexpectedError(val cause: Throwable) : ApiResult<Nothing>
}

data class ApiError(val code: String?, val message: String?)

/** Replace for backends with a different error envelope. Raw bodies are never exposed to UI. */
fun interface ApiErrorDecoder {
    fun decode(body: String): ApiError

    companion object {
        val Default = ApiErrorDecoder { body ->
            val value = Json.parseToJsonElement(body).jsonObject
            ApiError(value["code"]?.jsonPrimitive?.content, value["message"]?.jsonPrimitive?.content)
        }
    }
}

private fun <T> Response<T>.httpError(decoder: ApiErrorDecoder): ApiResult.HttpError {
    val error = errorBody()?.use { body ->
        // Bound memory even if a proxy returns a large HTML error page.
        val text = body.charStream().use { reader ->
            val buffer = CharArray(8192)
            val count = reader.read(buffer)
            if (count > 0) String(buffer, 0, count) else ""
        }
        try { decoder.decode(text) } catch (_: Exception) { null }
    }
    return ApiResult.HttpError(code(), error?.message?.takeIf { it.isNotBlank() } ?: message(), error?.code)
}

/** Cancellation always propagates. Use apiCallEmpty for 204/205 or other bodyless success. */
suspend fun <T : Any> apiCall(
    errorDecoder: ApiErrorDecoder = ApiErrorDecoder.Default,
    request: suspend () -> Response<T>,
): ApiResult<T> = executeApi {
    val response = request()
    if (!response.isSuccessful) response.httpError(errorDecoder)
    else response.body()?.let { ApiResult.Success(it) }
        ?: ApiResult.UnexpectedError(IllegalStateException("Empty response body"))
}

suspend fun apiCallEmpty(
    errorDecoder: ApiErrorDecoder = ApiErrorDecoder.Default,
    request: suspend () -> Response<*>,
): ApiResult<Unit> = executeApi {
    val response = request()
    if (!response.isSuccessful) response.httpError(errorDecoder)
    else {
        (response.body() as? java.io.Closeable)?.close()
        ApiResult.Success(Unit)
    }
}

/** Maps a successful HTTP envelope to a business result without assuming a global success code. */
fun <T, R> ApiResult<T>.mapBusiness(
    transform: (T) -> ApiResult<R>,
): ApiResult<R> = when (this) {
    is ApiResult.Success -> transform(value)
    is ApiResult.HttpError -> this
    is ApiResult.BusinessError -> this
    is ApiResult.NetworkError -> this
    is ApiResult.UnexpectedError -> this
}

private suspend fun <T> executeApi(block: suspend () -> ApiResult<T>): ApiResult<T> = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (network: IOException) {
    ApiResult.NetworkError(network)
} catch (unexpected: Exception) {
    ApiResult.UnexpectedError(unexpected)
}

package com.will.mvvmbase.feature.status.data

import com.will.mvvmbase.core.network.ApiResult
import com.will.mvvmbase.core.network.NetworkConfiguration
import com.will.mvvmbase.core.network.apiCall
import com.will.mvvmbase.feature.status.domain.StatusRepository
import com.will.mvvmbase.feature.status.domain.StatusResult
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Provider

class NetworkStatusRepository @Inject constructor(
    private val serviceProvider: Provider<StatusService>,
    private val configuration: NetworkConfiguration,
) : StatusRepository {
    override suspend fun check(): StatusResult {
        if (!configuration.isConfigured) return StatusResult.NotConfigured
        return when (val result = apiCall { serviceProvider.get().status() }) {
            is ApiResult.Success -> StatusResult.Available(result.value.status)
            is ApiResult.HttpError -> StatusResult.HttpFailure(result.statusCode)
            is ApiResult.NetworkError -> {
                if (result.cause.isTimeout()) StatusResult.Timeout else StatusResult.NetworkFailure
            }
            is ApiResult.BusinessError -> StatusResult.UnexpectedFailure
            is ApiResult.UnexpectedError -> StatusResult.UnexpectedFailure
        }
    }
}

private fun Throwable.isTimeout(): Boolean {
    var current: Throwable? = this
    repeat(8) {
        if (current is SocketTimeoutException) return true
        current = current?.cause
    }
    return false
}

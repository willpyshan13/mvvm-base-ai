package com.will.mvvmbase.feature.status.domain

sealed interface StatusResult {
    data class Available(val status: String) : StatusResult
    data class HttpFailure(val statusCode: Int) : StatusResult
    data object NetworkFailure : StatusResult
    data object Timeout : StatusResult
    data object UnexpectedFailure : StatusResult
    data object NotConfigured : StatusResult
}

interface StatusRepository {
    suspend fun check(): StatusResult
}

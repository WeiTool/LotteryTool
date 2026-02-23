package com.lotterytool.utils

sealed class FetchResult<out T> {
    data class Success<T>(val data: T? = null) : FetchResult<T>()
    data class Error(val message: String, val code: Int? = null) : FetchResult<Nothing>()
}

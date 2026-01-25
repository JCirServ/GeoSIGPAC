
package com.geosigpac.cirserv.utils

/**
 * Encapsula el resultado de una operación de red o asíncrona.
 */
sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val code: Int, val message: String) : NetworkResult<Nothing>()
    data class NetworkError(val exception: Throwable) : NetworkResult<Nothing>()
    data class Timeout(val exception: Throwable) : NetworkResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    
    fun getOrNull(): T? = if (this is Success) data else null
}

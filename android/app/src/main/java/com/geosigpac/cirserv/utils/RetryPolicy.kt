
package com.geosigpac.cirserv.utils

import android.util.Log
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException

object RetryPolicy {
    private const val TAG = "RetryPolicy"

    /**
     * Ejecuta un bloque suspendido con reintentos automáticos.
     * @param times Número máximo de reintentos (defecto 3).
     * @param initialDelay Retardo inicial en ms (defecto 1000ms).
     * @param maxDelay Retardo máximo permitido entre intentos (defecto 4000ms).
     * @param factor Factor de multiplicación para backoff exponencial (defecto 2.0).
     */
    suspend fun <T> executeWithRetry(
        times: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 4000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (!shouldRetry(e)) {
                    throw e
                }
                Log.w(TAG, "Intento ${attempt + 1} fallido: ${e.message}. Reintentando en ${currentDelay}ms...")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
        return block() // Último intento, si falla lanza la excepción
    }

    private fun shouldRetry(e: Exception): Boolean {
        return when (e) {
            is SocketTimeoutException -> true
            is IOException -> true // Errores generales de red
            is HttpException -> {
                // Reintentar errores de servidor (5xx) o Rate Limit (429)
                // NO reintentar errores de cliente (400, 401, 403, 404, etc)
                e.code in 500..599 || e.code == 429
            }
            else -> false // Errores lógicos o de parseo no se reintentan
        }
    }
}

class HttpException(val code: Int, message: String) : IOException("HTTP $code: $message")

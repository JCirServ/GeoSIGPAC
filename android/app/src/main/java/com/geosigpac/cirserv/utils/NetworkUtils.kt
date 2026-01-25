
package com.geosigpac.cirserv.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

object NetworkUtils {
    private const val TAG = "NetworkUtils"
    private const val CONNECT_TIMEOUT = 10000 // 10s
    private const val READ_TIMEOUT = 30000    // 30s
    private const val USER_AGENT = "GeoSIGPAC-Mobile/1.0 (Android)"

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    suspend fun fetchUrl(urlString: String): NetworkResult<String> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            
            // Configuración robusta
            connection.apply {
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Connection", "close") // Evitar problemas de pool en conexiones inestables
            }

            val code = connection.responseCode
            if (code in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                NetworkResult.Success(response)
            } else {
                Log.e(TAG, "Error HTTP $code para URL: $urlString")
                // Lanzamos HttpException para que RetryPolicy pueda evaluarlo
                throw HttpException(code, connection.responseMessage ?: "Error desconocido")
            }

        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout conectando a: $urlString")
            throw e // RetryPolicy capturará esto
        } catch (e: Exception) {
            Log.e(TAG, "Excepción de red: ${e.message}")
            throw e // RetryPolicy evaluará si es IOException
        } finally {
            connection?.disconnect()
        }
    }
}

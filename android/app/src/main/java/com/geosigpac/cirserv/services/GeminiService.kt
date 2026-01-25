
package com.geosigpac.cirserv.services

import com.geosigpac.cirserv.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.utils.RetryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import android.util.Log

object GeminiService {
    
    private const val TAG = "GeminiService"
    private const val GEMINI_TIMEOUT_MS = 60000L // 60s timeout

    private val model = GenerativeModel(
        modelName = "gemini-3-flash-preview",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    /**
     * Analiza la coherencia técnica de un recinto basado en su uso y metadatos.
     */
    suspend fun analyzeParcela(parcela: NativeParcela): String = withContext(Dispatchers.IO) {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return@withContext "API Key no configurada. Revise GEMINI_API_KEY."
        }

        val prompt = """
            Eres un inspector experto de la PAC en España. Analiza este recinto:
            Referencia SIGPAC: ${parcela.referencia}
            Uso Declarado: ${parcela.uso}
            Superficie: ${parcela.area} hectáreas
            Datos técnicos: ${parcela.metadata.entries.joinToString { "${it.key}: ${it.value}" }}
            
            Evalúa si hay alguna incoherencia técnica. Responde en menos de 15 palabras.
        """.trimIndent()

        try {
            // Envolvemos en Timeout global y Política de Reintentos
            withTimeout(GEMINI_TIMEOUT_MS) {
                RetryPolicy.executeWithRetry(times = 3, initialDelay = 1500) {
                    val response = model.generateContent(prompt)
                    response.text?.trim() ?: "Sin respuesta del inspector IA."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error Gemini: ${e.message}")
            if (e.message?.contains("429") == true) {
                "IA sobrecargada. Intente más tarde."
            } else {
                "IA no disponible temporalmente."
            }
        }
    }
}

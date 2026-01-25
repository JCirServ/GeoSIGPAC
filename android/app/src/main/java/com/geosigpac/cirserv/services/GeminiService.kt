
package com.geosigpac.cirserv.services

import com.geosigpac.cirserv.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.geosigpac.cirserv.model.NativeParcela
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiService {
    
    // Inicialización segura usando BuildConfig generado por Gradle
    private val model = GenerativeModel(
        modelName = "gemini-3-flash-preview",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    /**
     * Analiza la coherencia técnica de un recinto basado en su uso y metadatos.
     */
    suspend fun analyzeParcela(parcela: NativeParcela): String = withContext(Dispatchers.IO) {
        // Validación básica antes de llamar a la API
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
            val response = model.generateContent(prompt)
            response.text?.trim() ?: "Sin respuesta del inspector IA."
        } catch (e: Exception) {
            "IA no disponible temporalmente."
        }
    }
}

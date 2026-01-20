
package com.geosigpac.cirserv.services

import com.google.ai.client.generativeai.GenerativeModel
import com.geosigpac.cirserv.model.NativeParcela
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiService {
    // API_KEY gestionada por el entorno
    private const val API_KEY = "TU_API_KEY_ENV" 

    private val model = GenerativeModel(
        modelName = "gemini-3-flash-preview",
        apiKey = System.getenv("API_KEY") ?: "" // Fallback a env var
    )

    /**
     * Analiza la coherencia técnica de un recinto basado en su uso y metadatos.
     */
    suspend fun analyzeParcela(parcela: NativeParcela): String = withContext(Dispatchers.IO) {
        val prompt = """
            Eres un inspector experto de la PAC en España. Analiza este recinto:
            Referencia SIGPAC: ${parcela.referencia}
            Uso Declarado: ${parcela.uso}
            Superficie: ${parcela.area} hectáreas
            Datos técnicos: ${parcela.metadata.entries.joinToString { "${it.key}: ${it.value}" }}
            
            Evalúa si hay alguna incoherencia técnica. Responde en menos de 15 palabras.
        """.trimIndent()

        try {
            // Se asume que la API_KEY se inyecta correctamente en tiempo de ejecución
            val response = model.generateContent(prompt)
            response.text?.trim() ?: "Sin respuesta del inspector IA."
        } catch (e: Exception) {
            "IA no disponible temporalmente."
        }
    }
}

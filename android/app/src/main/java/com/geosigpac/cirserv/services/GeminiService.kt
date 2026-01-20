
package com.geosigpac.cirserv.services

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.geosigpac.cirserv.model.NativeParcela
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiService {
    // Nota: En producción, la API_KEY debería manejarse de forma segura (Secrets Gradle Plugin)
    private val apiKey = "TU_API_KEY_AQUI" // El sistema inyectará la real en el entorno

    private val model = GenerativeModel(
        modelName = "gemini-3-flash-preview",
        apiKey = apiKey
    )

    suspend fun analyzeParcela(parcela: NativeParcela): String = withContext(Dispatchers.IO) {
        val prompt = """
            Actúa como inspector de la PAC. Analiza la coherencia de este recinto:
            Referencia: ${parcela.referencia}
            Uso SIGPAC: ${parcela.uso}
            Superficie: ${parcela.area} ha
            Metadatos extra: ${parcela.metadata}
            Responde brevemente si el uso es conforme o si hay incidencia. Máximo 20 palabras.
        """.trimIndent()

        try {
            val response = model.generateContent(prompt)
            response.text ?: "No se pudo obtener respuesta de la IA."
        } catch (e: Exception) {
            "Error en análisis: ${e.message}"
        }
    }
}

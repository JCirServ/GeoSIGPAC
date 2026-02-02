
package com.geosigpac.cirserv.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.geosigpac.cirserv.BuildConfig
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.utils.RetryPolicy
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream

object GeminiService {
    
    private const val TAG = "GeminiService"
    private const val GEMINI_TIMEOUT_MS = 90000L 

    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash", // Cambiado a 1.5-flash para soporte multimodal robusto
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    /**
     * Analiza la coherencia técnica de un recinto basado en su uso y metadatos.
     */
    suspend fun analyzeParcela(parcela: NativeParcela): String = withContext(Dispatchers.IO) {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) return@withContext "API Key no configurada."

        val prompt = """
            Eres un inspector experto de la PAC. Analiza:
            Ref: ${parcela.referencia} | Uso: ${parcela.uso}
            Superficie: ${parcela.area} ha
            Datos: ${parcela.metadata.entries.joinToString { "${it.key}: ${it.value}" }}
            Evalúa incoherencias. Máx 15 palabras.
        """.trimIndent()

        try {
            withTimeout(GEMINI_TIMEOUT_MS) {
                RetryPolicy.executeWithRetry {
                    val response = model.generateContent(prompt)
                    response.text?.trim() ?: "Sin respuesta."
                }
            }
        } catch (e: Exception) { "IA no disponible." }
    }

    /**
     * MEJORA: Análisis Multimodal. Compara la foto real con el cultivo declarado.
     */
    suspend fun analyzePhotoConsistency(
        context: Context,
        photoUri: Uri,
        declaracion: String,
        usoSigpac: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(photoUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) return@withContext "Error: No se pudo procesar la imagen."

            val prompt = """
                Como agrónomo experto, inspecciona esta foto de campo.
                Cultivo Declarado por el agricultor: $declaracion
                Uso SIGPAC Oficial: $usoSigpac
                
                Instrucciones:
                1. ¿Es la vegetación de la foto consistente con lo declarado? 
                2. ¿Identificas el cultivo?
                3. ¿Ves anomalías (sequía, maleza excesiva, plagas)?
                Responde de forma técnica y muy breve (máx 25 palabras).
            """.trimIndent()

            val inputContent = content {
                image(bitmap)
                text(prompt)
            }

            val response = model.generateContent(inputContent)
            bitmap.recycle()
            response.text?.trim() ?: "Análisis visual no concluyente."

        } catch (e: Exception) {
            Log.e(TAG, "Error en visión IA: ${e.message}")
            "Error al analizar evidencia visual."
        }
    }
}

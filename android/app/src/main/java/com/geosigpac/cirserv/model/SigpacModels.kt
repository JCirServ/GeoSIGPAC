
package com.geosigpac.cirserv.model

data class SigpacData(
    val superficie: Double? = null,
    val pendienteMedia: Double? = null,
    val coefRegadio: Double? = null,
    val admisibilidad: Double? = null,
    val incidencias: String? = null,
    val usoSigpac: String? = null,
    val region: String? = null,
    val altitud: Int? = null
)

data class CultivoData(
    val expNum: String? = null,
    val parcProducto: Int? = null,
    val parcSistexp: String? = null,
    val parcSupcult: Double? = null,
    val parcAyudasol: String? = null,
    val pdrRec: String? = null,
    val cultsecunProducto: Int? = null,
    val cultsecunAyudasol: String? = null,
    val parcIndcultapro: Int? = null,
    val tipoAprovecha: String? = null
)

data class NativeParcela(
    val id: String,
    val referencia: String, 
    val uso: String,
    val lat: Double,
    val lng: Double,
    val area: Double,
    val metadata: Map<String, String>,
    val geometryRaw: String? = null, // Raw KML coordinates string
    val sigpacInfo: SigpacData? = null,
    val cultivoInfo: CultivoData? = null,
    val informeIA: String? = null,
    val isHydrated: Boolean = false,
    val centroidLat: Double? = null,
    val centroidLng: Double? = null,
    // Nuevos campos para el Checklist
    val verifiedUso: String? = null,
    val completedChecks: List<String> = emptyList(),
    val finalVerdict: String? = null, // "CUMPLE" | "NO_CUMPLE"
    val photos: List<String> = emptyList() // Lista de URIs de fotos
)

data class NativeExpediente(
    val id: String,
    val titular: String,
    val fechaImportacion: String,
    val parcelas: List<NativeParcela>
)

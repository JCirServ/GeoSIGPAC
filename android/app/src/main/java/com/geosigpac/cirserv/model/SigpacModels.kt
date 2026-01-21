
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
    val lat: Double, // Centroide para zoom
    val lng: Double, // Centroide para zoom
    val geometry: String? = null, // GeoJSON String (Polygon)
    val area: Double,
    val metadata: Map<String, String>,
    val sigpacInfo: SigpacData? = null,
    val cultivoInfo: CultivoData? = null,
    val informeIA: String? = null,
    val isHydrated: Boolean = false
)

data class NativeExpediente(
    val id: String,
    val titular: String,
    val fechaImportacion: String,
    val parcelas: List<NativeParcela>
)


package com.geosigpac.cirserv.model

data class SigpacData(
    val provincia: Int? = null,
    val municipio: Int? = null,
    val agregado: Int? = null,
    val zona: Int? = null,
    val poligono: Int? = null,
    val parcela: Int? = null,
    val recinto: Int? = null,
    val superficie: Double? = null,
    val pendienteMedia: Double? = null,
    val coefRegadio: Double? = null,
    val admisibilidad: Double? = null,
    val incidencias: String? = null,
    val usoSigpac: String? = null,
    val region: String? = null,
    val altitud: Int? = null,
    val srid: Int? = null
)

data class CultivoData(
    val expNum: String? = null,
    val producto: Int? = null,
    val sistExp: String? = null,
    val supCult: Double? = null,
    val ayudaSol: String? = null,
    val pdrRec: String? = null,
    val cultSecunProducto: Int? = null,
    val cultSecunAyudaSol: String? = null,
    val indCultApro: Int? = null,
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

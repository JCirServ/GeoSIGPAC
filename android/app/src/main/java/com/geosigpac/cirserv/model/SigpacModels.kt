
package com.geosigpac.cirserv.model

data class SigpacData(
    val pendiente: Double? = null,
    val altitud: Double? = null,
    val municipio: String? = null,
    val poligono: String? = null,
    val parcela: String? = null,
    val recinto: String? = null,
    val provincia: String? = null
)

data class CultivoData(
    val expNum: String? = null,
    val producto: String? = null,
    val sistExp: String? = null,
    val ayudaSol: String? = null,
    val superficie: Double? = null
)

data class NativeParcela(
    val id: String,
    val referencia: String, // Ref_SigPac
    val uso: String,
    val lat: Double,
    val lng: Double,
    val area: Double,
    val metadata: Map<String, String>,
    var sigpacInfo: SigpacData? = null,
    var cultivoInfo: CultivoData? = null,
    var isHydrated: Boolean = false
)

data class NativeExpediente(
    val id: String,
    val titular: String,
    val fechaImportacion: String,
    val parcelas: List<NativeParcela>
)

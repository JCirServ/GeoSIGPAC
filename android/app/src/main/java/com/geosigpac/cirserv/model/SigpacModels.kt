
package com.geosigpac.cirserv.model

data class SigpacData(
    val provincia: Int? = null,
    val municipio: Int? = null,
    val agregado: Int? = null,
    val zona: Int? = null,
    val poligono: Int? = null,
    val parcela: Int? = null,
    val recinto: Int? = null,
    val pendiente: Double? = null,
    val altitud: Double? = null
)

data class CultivoData(
    val expNum: String? = null,
    val producto: Int? = null,
    val sistExp: String? = null,
    val supCult: Double? = null,
    val ayudaSol: String? = null,
    val pdrRec: String? = null,
    val indCultApro: Int? = null,
    val tipoAprovecha: String? = null
)

data class NativeParcela(
    val id: String,
    val referencia: String, // Prov:Mun:Pol:Parc:Rec
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

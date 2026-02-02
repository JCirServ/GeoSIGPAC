
package com.geosigpac.cirserv.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Ignore

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

@Entity(
    tableName = "parcelas",
    foreignKeys = [
        ForeignKey(
            entity = NativeExpediente::class,
            parentColumns = ["id"],
            childColumns = ["parentExpedienteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["parentExpedienteId"])]
)
data class NativeParcela(
    @PrimaryKey val id: String,
    val parentExpedienteId: String,
    val referencia: String, 
    val uso: String,
    val lat: Double,
    val lng: Double,
    val area: Double,
    val metadata: Map<String, String>,
    val geometryRaw: String? = null,
    val sigpacInfo: SigpacData? = null,
    val cultivoInfo: CultivoData? = null,
    val informeIA: String? = null,
    val isHydrated: Boolean = false,
    val centroidLat: Double? = null,
    val centroidLng: Double? = null,
    val verifiedUso: String? = null,
    val verifiedProductoCode: Int? = null,
    val completedChecks: List<String> = emptyList(),
    val finalVerdict: String? = null,
    val photos: List<String> = emptyList(),
    val photoLocations: Map<String, String> = emptyMap()
)

@Entity(tableName = "expedientes")
data class NativeExpediente(
    @PrimaryKey val id: String,
    val titular: String,
    val fechaImportacion: String,
    // Room ignorará este campo, lo llenamos nosotros al mapear desde la relación
    @Ignore val parcelas: List<NativeParcela> = emptyList()
) {
    // Constructor necesario para Room ya que ignoramos campos en el principal
    constructor(id: String, titular: String, fechaImportacion: String) : this(id, titular, fechaImportacion, emptyList())
}

package com.geosigpac.cirserv.model

import android.net.Uri

data class NativeParcela(
    val id: String,
    val referencia: String, // Ref_SigPac
    val uso: String,
    val lat: Double,
    val lng: Double,
    val area: Double,
    val metadata: Map<String, String>
)

data class NativeExpediente(
    val id: String,
    val titular: String,
    val fechaImportacion: String,
    val parcelas: List<NativeParcela>
)
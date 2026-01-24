// Crea: Application.kt
package com.geosigpac.cirserv

import android.app.Application
import org.acra.ACRA
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat

class GeoSigpacApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        
        ACRA.init(this) {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            
            mailSender {
                mailTo = "tu@email.com"
                subject = "GeoSIGPAC Crash Report"
                reportAsFile = true
            }
            
            dialog {
                text = "La app ha tenido un error. ¿Enviar reporte?"
                positiveButtonText = "Enviar"
                negativeButtonText = "Cancelar"
            }
        }
    }
}
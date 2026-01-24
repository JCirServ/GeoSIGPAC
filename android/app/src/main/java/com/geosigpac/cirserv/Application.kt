package com.geosigpac.cirserv

import android.app.Application
import android.content.Context
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra // Asegúrate de tener esta dependencia

class GeoSigpacApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        // Usamos initAcra para habilitar el DSL de Kotlin correctamente
        initAcra {
            reportFormat = StringFormat.JSON
            
            mailSender {
                mailTo = "tu@email.com"
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
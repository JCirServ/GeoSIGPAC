package com.geosigpac.cirserv.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.geosigpac.cirserv.bridge.WebAppInterface

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebProjectManager(
    webAppInterface: WebAppInterface,
    onWebViewCreated: (WebView) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // Configuración esencial para apps híbridas modernas
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true // Necesario para cargar assets locales y fotos de la cámara
                    allowContentAccess = true
                }

                // Inyectar el puente JS
                addJavascriptInterface(webAppInterface, "Android")

                // Clientes para manejo de errores y consola
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient() // Habilita alerts y logs de JS en Logcat

                // Cargar la Single Page Application (SPA) desde assets
                loadUrl("file:///android_assets/index.html")
                
                // Callback para guardar referencia si es necesario
                onWebViewCreated(this)
            }
        }
    )
}
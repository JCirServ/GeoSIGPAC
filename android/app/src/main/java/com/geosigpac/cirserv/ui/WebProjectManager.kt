package com.geosigpac.cirserv.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.geosigpac.cirserv.bridge.WebAppInterface

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebProjectManager(
    webAppInterface: WebAppInterface,
    onWebViewCreated: (WebView) -> Unit,
    onNavigateToMap: () -> Unit,
    onOpenCamera: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                // Botón: Ir al Mapa
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToMap,
                    icon = { 
                        Icon(
                            imageVector = Icons.Default.Map, 
                            contentDescription = "Mapa"
                        ) 
                    },
                    label = { Text("Mapa") }
                )
                
                // Botón: Abrir Cámara (Icono Extended)
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenCamera,
                    icon = { 
                        Icon(
                            imageVector = Icons.Default.CameraAlt, 
                            contentDescription = "Cámara"
                        ) 
                    },
                    label = { Text("Cámara") }
                )
            }
        }
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding), // Evita que la barra tape el contenido web
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
                    loadUrl("file:///android_asset/index.html")
                    
                    // Callback para guardar referencia si es necesario
                    onWebViewCreated(this)
                }
            }
        )
    }
}
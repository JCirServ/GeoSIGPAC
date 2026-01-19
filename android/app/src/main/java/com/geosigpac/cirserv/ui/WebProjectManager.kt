
package com.geosigpac.cirserv.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.geosigpac.cirserv.bridge.WebAppInterface

/**
 * Componente que gestiona el WebView híbrido utilizando WebViewAssetLoader.
 * Esto permite cargar la webapp local bajo un contexto HTTPS seguro, evitando
 * problemas de CORS y Mixed Content.
 */
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
                .padding(innerPadding),
            factory = { context ->
                // 1. Configuramos el AssetLoader
                // El dominio 'appassets.androidplatform.net' es el estándar recomendado por Google.
                val assetLoader = WebViewAssetLoader.Builder()
                    .setDomain("appassets.androidplatform.net")
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(context))
                    .build()

                WebView(context).apply {
                    settings.apply {
                        // Seguridad y Funcionalidad
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        
                        // Desactivamos acceso a archivos para forzar el uso del AssetLoader (más seguro)
                        allowFileAccess = false
                        allowContentAccess = false
                        
                        // Optimizaciones de visualización
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(false)
                        
                        // User Agent personalizado para detectar en Web si estamos en la App
                        userAgentString = "$userAgentString GeoSIGPAC/1.0"
                    }

                    // Inyectamos el puente nativo
                    addJavascriptInterface(webAppInterface, "Android")

                    webViewClient = object : WebViewClient() {
                        // Interceptamos las peticiones para que pasen por el AssetLoader
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            return assetLoader.shouldInterceptRequest(request.url)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                        }

                        // Manejo de errores de carga
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                        }
                    }
                    
                    webChromeClient = WebChromeClient()

                    // Importante: Cargamos la URL a través del dominio virtual HTTPS
                    // Dado que Vite construye en 'assets/', el index.html está en la raíz del handler /assets/
                    loadUrl("https://appassets.androidplatform.net/assets/index.html")
                    
                    onWebViewCreated(this)
                }
            },
            update = { webView ->
                // Aquí se podrían manejar actualizaciones del estado si fuera necesario
            }
        )
    }
}

package com.geosigpac.cirserv.ui

import android.annotation.SuppressLint
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
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
                // Configuramos el AssetLoader para mapear el dominio virtual a la carpeta assets
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()

                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        // Ya no dependemos críticamente de estas gracias al AssetLoader,
                        // pero las mantenemos para compatibilidad con el bridge.
                        allowFileAccessFromFileURLs = false
                        allowUniversalAccessFromFileURLs = false
                    }

                    addJavascriptInterface(webAppInterface, "Android")

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            // Interceptamos peticiones a appassets.androidplatform.net
                            return assetLoader.shouldInterceptRequest(request.url)
                        }
                    }
                    
                    webChromeClient = WebChromeClient()

                    // Cargamos a través del dominio virtual seguro
                    loadUrl("https://appassets.androidplatform.net/assets/index.html")
                    
                    onWebViewCreated(this)
                }
            }
        )
    }
}
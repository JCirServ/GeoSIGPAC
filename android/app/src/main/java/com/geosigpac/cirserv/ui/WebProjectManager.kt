package com.geosigpac.cirserv.ui

import android.annotation.SuppressLint
import android.view.View
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
                    icon = { Icon(Icons.Default.Map, "Mapa") },
                    label = { Text("Mapa") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenCamera,
                    icon = { Icon(Icons.Default.CameraAlt, "Cámara") },
                    label = { Text("Cámara") }
                )
            }
        }
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            factory = { context ->
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()

                WebView(context).apply {
                    // Optimizaciones de rendimiento y estabilidad
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        databaseEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }

                    addJavascriptInterface(webAppInterface, "Android")

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            return assetLoader.shouldInterceptRequest(request.url)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Forzamos un render para evitar el parpadeo blanco inicial
                            view?.visibility = View.VISIBLE
                        }
                    }
                    
                    webChromeClient = WebChromeClient()
                    loadUrl("https://appassets.androidplatform.net/assets/index.html")
                    onWebViewCreated(this)
                }
            },
            update = { /* El WebView se mantiene persistente si es necesario */ }
        )
    }
}
package com.geosigpac.cirserv.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.geosigpac.cirserv.bridge.WebAppInterface

/**
 * Componente que gestiona el WebView híbrido utilizando WebViewAssetLoader.
 * Optimizado con colores de fondo idénticos para una integración seamless.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebProjectManager(
    webAppInterface: WebAppInterface,
    onWebViewCreated: (WebView) -> Unit,
    onNavigateToMap: () -> Unit,
    onOpenCamera: () -> Unit
) {
    // Colores de la marca GeoSIGPAC para la interfaz nativa
    val bgDark = Color(0xFF07080D) // Negro profundo idéntico al CSS
    val accentNeon = Color(0xFF5C60F5)
    val textGray = Color(0xFF94A3B8)

    // Variable para retener el callback del archivo seleccionado
    var uploadMessage by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    // Launcher para abrir el selector de archivos del sistema
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            var results: Array<Uri>? = null
            
            if (data != null) {
                val dataString = data.dataString
                val clipData = data.clipData
                
                if (clipData != null) {
                    results = Array(clipData.itemCount) { i ->
                        clipData.getItemAt(i).uri
                    }
                } else if (dataString != null) {
                    results = arrayOf(Uri.parse(dataString))
                }
            }
            uploadMessage?.onReceiveValue(results)
        } else {
            uploadMessage?.onReceiveValue(null)
        }
        uploadMessage = null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bgDark,
        bottomBar = {
            NavigationBar(
                // CAMBIO: Ahora el fondo es bgDark para fusionarse con la webview
                containerColor = bgDark, 
                contentColor = Color.White,
                tonalElevation = 0.dp
            ) {
                // Cámara
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenCamera,
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = textGray,
                        unselectedTextColor = textGray,
                        indicatorColor = accentNeon.copy(alpha = 0.12f)
                    ),
                    icon = { 
                        Icon(
                            imageVector = Icons.Default.CameraAlt, 
                            contentDescription = "Cámara"
                        ) 
                    },
                    label = { Text("Cámara") }
                )

                // Mapa
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToMap,
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = textGray,
                        unselectedTextColor = textGray,
                        indicatorColor = accentNeon.copy(alpha = 0.12f)
                    ),
                    icon = { 
                        Icon(
                            imageVector = Icons.Default.Map, 
                            contentDescription = "Mapa"
                        ) 
                    },
                    label = { Text("Mapa") }
                )
            }
        }
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            factory = { context ->
                val assetLoader = WebViewAssetLoader.Builder()
                    .setDomain("appassets.androidplatform.net")
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(context))
                    .build()

                WebView(context).apply {
                    // Evita el parpadeo blanco al iniciar la carga
                    setBackgroundColor(0xFF07080D.toInt()) 
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = false
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(false)
                        userAgentString = "$userAgentString GeoSIGPAC/1.0"
                    }

                    addJavascriptInterface(webAppInterface, "Android")

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            return assetLoader.shouldInterceptRequest(request.url)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            // Reforzamos el fondo negro
                            view?.setBackgroundColor(0xFF07080D.toInt())
                        }
                    }
                    
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            if (uploadMessage != null) {
                                uploadMessage?.onReceiveValue(null)
                                uploadMessage = null
                            }
                            uploadMessage = filePathCallback
                            try {
                                val intent = Intent(Intent.ACTION_GET_CONTENT)
                                intent.addCategory(Intent.CATEGORY_OPENABLE)
                                intent.type = "*/*"
                                val mimeTypes = arrayOf(
                                    "application/vnd.google-earth.kml+xml",
                                    "application/vnd.google-earth.kmz",
                                    "application/zip"
                                )
                                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                                filePickerLauncher.launch(intent)
                            } catch (e: Exception) {
                                uploadMessage?.onReceiveValue(null)
                                uploadMessage = null
                                return false
                            }
                            return true
                        }
                    }

                    loadUrl("https://appassets.androidplatform.net/assets/index.html")
                    onWebViewCreated(this)
                }
            }
        )
    }
}
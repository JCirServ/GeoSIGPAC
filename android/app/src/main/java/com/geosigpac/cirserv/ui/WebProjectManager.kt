
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            // Importante: Si se cancela, debemos devolver null para reiniciar el input del WebView
            uploadMessage?.onReceiveValue(null)
        }
        uploadMessage = null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                // Cámara (Izquierda)
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

                // Mapa (Derecha)
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
            }
        }
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            factory = { context ->
                // 1. Configuramos el AssetLoader
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
                        
                        // Desactivamos acceso a archivos directos pero habilitamos acceso a contenido
                        allowFileAccess = false
                        allowContentAccess = true
                        
                        // Optimizaciones de visualización
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(false)
                        
                        userAgentString = "$userAgentString GeoSIGPAC/1.0"
                    }

                    // Inyectamos el puente nativo
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
                        }
                    }
                    
                    webChromeClient = object : WebChromeClient() {
                        // CRÍTICO: Sobrescribir este método para manejar <input type="file"> de forma robusta en Android
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            // Cancelar callback anterior si existe
                            if (uploadMessage != null) {
                                uploadMessage?.onReceiveValue(null)
                                uploadMessage = null
                            }

                            uploadMessage = filePathCallback

                            try {
                                // En lugar de usar fileChooserParams.createIntent() que puede ser muy restrictivo con KML,
                                // creamos un Intent manual amplio.
                                val intent = Intent(Intent.ACTION_GET_CONTENT)
                                intent.addCategory(Intent.CATEGORY_OPENABLE)
                                intent.type = "*/*" // Permitir todo para evitar que Android deshabilite archivos KML
                                
                                // Sugerir tipos MIME correctos
                                val mimeTypes = arrayOf(
                                    "application/vnd.google-earth.kml+xml",
                                    "application/vnd.google-earth.kmz",
                                    "application/xml",
                                    "text/xml",
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream"
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

                    // Cargamos la URL
                    loadUrl("https://appassets.androidplatform.net/assets/index.html")
                    
                    onWebViewCreated(this)
                }
            },
            update = { webView ->
                // Actualizaciones de estado si fueran necesarias
            }
        )
    }
}

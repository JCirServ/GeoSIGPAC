
package com.geosigpac.cirserv.bridge

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "GeoSIGPAC_LOG_Bridge"

data class NativeProject(
    val id: String,
    val name: String,
    val description: String,
    val lat: Double,
    val lng: Double,
    val date: String,
    val status: String,
    var imageUrl: String? = null
)

class WebAppInterface(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onCameraRequested: (String) -> Unit,
    private val onMapFocusRequested: (Double, Double) -> Unit
) {
    private val mockProjects = listOf(
        NativeProject("p1", "Parcela 102 - Olivos", "Revisión nativa.", 37.3891, -5.9845, "2023-10-25", "pending")
    )

    @JavascriptInterface
    fun openCamera(projectId: String) {
        Log.i(TAG, "openCamera llamado desde JS. ProjectID: $projectId")
        scope.launch(Dispatchers.Main) {
            onCameraRequested(projectId)
        }
    }

    @JavascriptInterface
    fun onProjectSelected(lat: Double, lng: Double) {
        Log.i(TAG, "onProjectSelected llamado desde JS. Coordenadas: $lat, $lng")
        scope.launch(Dispatchers.Main) {
            onMapFocusRequested(lat, lng)
        }
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Log.d(TAG, "showToast llamado desde JS: $message")
        Toast.makeText(context, "GeoSIGPAC: $message", Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun getProjects(): String {
        Log.d(TAG, "getProjects llamado desde JS. Enviando datos mock.")
        return Gson().toJson(mockProjects)
    }
}

package com.geosigpac.cirserv.bridge

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Data model matching the TypeScript 'Project' interface
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
    // Single Source of Truth: Data resides in Android
    private val mockProjects = listOf(
        NativeProject(
            "p1", "Parcela 102 - Olivos", 
            "Revisión de sistema de riego y conteo de árboles (Nativo).", 
            37.3891, -5.9845, "2023-10-25", "pending"
        ),
        NativeProject(
            "p2", "Parcela 45 - Viñedos", 
            "Inspección fitosanitaria trimestral (Nativo).", 
            42.4285, -2.6280, "2023-10-26", "verified"
        ),
        NativeProject(
            "p3", "Zona Reforestación Norte", 
            "Seguimiento de crecimiento de plantones.", 
            43.2630, -2.9350, "2023-10-27", "completed"
        )
    )

    /**
     * Triggered by Web to open Native Camera
     */
    @JavascriptInterface
    fun openCamera(projectId: String) {
        scope.launch(Dispatchers.Main) {
            onCameraRequested(projectId)
        }
    }

    /**
     * Triggered by Web when a user clicks 'Localizar'
     */
    @JavascriptInterface
    fun onProjectSelected(lat: Double, lng: Double) {
        scope.launch(Dispatchers.Main) {
            onMapFocusRequested(lat, lng)
        }
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, "Native: $message", Toast.LENGTH_SHORT).show()
    }

    /**
     * Synchronous data fetch called by React's useEffect on mount
     */
    @JavascriptInterface
    fun getProjects(): String {
        return Gson().toJson(mockProjects)
    }
}
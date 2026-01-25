
package com.geosigpac.cirserv.ui.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LocationOverlay(
    isLandscape: Boolean,
    locationText: String,
    activeRef: String?,
    sigpacUso: String?,
    matchedParcelInfo: Any?,
    showNoDataMessage: Boolean,
    onClearManual: (() -> Unit)?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val alignment = if (isLandscape) Alignment.TopEnd else Alignment.TopEnd
        val modifier = if (isLandscape) {
            Modifier.align(alignment).padding(16.dp)
        } else {
            Modifier.align(alignment).padding(top = 40.dp, end = 16.dp)
        }

        Box(modifier = modifier) {
            InfoBox(
                locationText = locationText,
                sigpacRef = activeRef,
                sigpacUso = sigpacUso,
                matchedParcelInfo = matchedParcelInfo,
                showNoDataMessage = showNoDataMessage,
                onClearManual = onClearManual
            )
        }
    }
}

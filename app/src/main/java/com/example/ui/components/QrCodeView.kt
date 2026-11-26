package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.totp.QrCodeGenerator
import com.example.ui.theme.LocalInstrumentColors

/**
 * Renders a locally generated QR code onto Compose Canvas with quiet zone and crisp contrast
 * for scanning into any authenticator app offline.
 */
@Composable
fun QrCodeView(
    content: String,
    size: Dp = 200.dp,
    modifier: Modifier = Modifier
) {
    val colors = LocalInstrumentColors.current
    val matrix = remember(content) { QrCodeGenerator.encode(content) }

    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .padding(14.dp)
            .semantics { contentDescription = "Authenticator setup QR code" }
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val quiet = 2
            val totalCells = matrix.size + quiet * 2
            val cellPx = this.size.minDimension / totalCells.toFloat()

            for (r in 0 until matrix.size) {
                for (c in 0 until matrix.size) {
                    if (matrix.modules[r][c]) {
                        drawRect(
                            color = Color(0xFF13112A),
                            topLeft = Offset((c + quiet) * cellPx, (r + quiet) * cellPx),
                            size = Size(cellPx + 0.5f, cellPx + 0.5f)
                        )
                    }
                }
            }
        }
    }
}

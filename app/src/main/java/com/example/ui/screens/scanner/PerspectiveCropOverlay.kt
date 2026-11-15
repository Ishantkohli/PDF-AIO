package com.example.ui.screens.scanner

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.scan.DocumentEdgeDetector
import com.example.ui.theme.AdfCoral
import com.example.ui.theme.AdfElectricCyan
import kotlin.math.hypot

/**
 * Interactive 4-Corner Perspective Crop Editor Overlay.
 * Users can drag TL, TR, BR, and BL handles directly over the captured image
 * to fine-tune document borders before applying Matrix.setPolyToPoly perspective warp.
 */
@Composable
fun PerspectiveCropOverlay(
    initialQuad: DocumentEdgeDetector.NormalizedQuad?,
    onApplyQuadCrop: (DocumentEdgeDetector.NormalizedQuad) -> Unit,
    modifier: Modifier = Modifier
) {
    var tl by remember(initialQuad) { mutableStateOf(initialQuad?.topLeft ?: PointF(0.08f, 0.08f)) }
    var tr by remember(initialQuad) { mutableStateOf(initialQuad?.topRight ?: PointF(0.92f, 0.08f)) }
    var br by remember(initialQuad) { mutableStateOf(initialQuad?.bottomRight ?: PointF(0.92f, 0.92f)) }
    var bl by remember(initialQuad) { mutableStateOf(initialQuad?.bottomLeft ?: PointF(0.08f, 0.92f)) }

    var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
    var activeCornerIdx by remember { mutableIntStateOf(-1) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(boxSize) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val w = boxSize.width.toFloat().coerceAtLeast(1f)
                        val h = boxSize.height.toFloat().coerceAtLeast(1f)
                        val pts = listOf(
                            Offset(tl.x * w, tl.y * h),
                            Offset(tr.x * w, tr.y * h),
                            Offset(br.x * w, br.y * h),
                            Offset(bl.x * w, bl.y * h)
                        )
                        val nearest = pts.withIndex().minByOrNull { (_, p) ->
                            hypot(p.x - startOffset.x, p.y - startOffset.y)
                        }
                        activeCornerIdx = if (nearest != null && hypot(
                                nearest.value.x - startOffset.x,
                                nearest.value.y - startOffset.y
                            ) <= 110f
                        ) nearest.index else -1
                    },
                    onDragEnd = { activeCornerIdx = -1 },
                    onDragCancel = { activeCornerIdx = -1 },
                    onDrag = { change, dragAmount ->
                        if (activeCornerIdx in 0..3) {
                            change.consume()
                            val w = boxSize.width.toFloat().coerceAtLeast(1f)
                            val h = boxSize.height.toFloat().coerceAtLeast(1f)
                            val dx = dragAmount.x / w
                            val dy = dragAmount.y / h
                            when (activeCornerIdx) {
                                0 -> tl = PointF((tl.x + dx).coerceIn(0.02f, 0.92f), (tl.y + dy).coerceIn(0.02f, 0.92f))
                                1 -> tr = PointF((tr.x + dx).coerceIn(0.08f, 0.98f), (tr.y + dy).coerceIn(0.02f, 0.92f))
                                2 -> br = PointF((br.x + dx).coerceIn(0.08f, 0.98f), (br.y + dy).coerceIn(0.08f, 0.98f))
                                3 -> bl = PointF((bl.x + dx).coerceIn(0.02f, 0.92f), (bl.y + dy).coerceIn(0.08f, 0.98f))
                            }
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pTl = Offset(tl.x * w, tl.y * h)
            val pTr = Offset(tr.x * w, tr.y * h)
            val pBr = Offset(br.x * w, br.y * h)
            val pBl = Offset(bl.x * w, bl.y * h)

            val path = Path().apply {
                moveTo(pTl.x, pTl.y)
                lineTo(pTr.x, pTr.y)
                lineTo(pBr.x, pBr.y)
                lineTo(pBl.x, pBl.y)
                close()
            }
            drawPath(path, color = AdfElectricCyan.copy(alpha = 0.14f))
            drawPath(
                path = path,
                color = AdfElectricCyan,
                style = Stroke(
                    width = 5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                )
            )

            listOf(pTl, pTr, pBr, pBl).forEachIndexed { idx, pt ->
                val isSelected = idx == activeCornerIdx
                drawCircle(Color.White, radius = if (isSelected) 22f else 17f, center = pt)
                drawCircle(AdfCoral, radius = if (isSelected) 16f else 12f, center = pt)
            }
        }

        Button(
            onClick = {
                onApplyQuadCrop(DocumentEdgeDetector.NormalizedQuad(tl, tr, br, bl))
            },
            colors = ButtonDefaults.buttonColors(containerColor = AdfCoral),
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .testTag("scanner_apply_crop_button")
        ) {
            Icon(Icons.Default.Crop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Apply Perspective Crop", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

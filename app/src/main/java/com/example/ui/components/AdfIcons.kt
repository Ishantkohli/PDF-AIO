package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

/**
 * ADF AIO Bespoke Custom Icon System
 * High-precision two-tone vector icons engineered specifically for ADF AIO
 * with clean geometry, subtle depth accents, and optional micro-animations.
 */
object AdfIcon {

    @Composable
    fun Conversion(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = AdfElectricCyan,
        secondaryColor: Color = AdfVibrantBlue,
        isAnimated: Boolean = true
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "ConversionPulse")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = if (isAnimated) 360f else 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ConversionRotation"
        )

        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.09f

            rotate(rotation) {
                // Secondary translucent orbital ring
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.25f),
                    radius = w * 0.42f,
                    style = Stroke(width = strokeW * 0.7f)
                )

                // Top Arc Arrow
                val topPath = Path().apply {
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f),
                        startAngleDegrees = 200f,
                        sweepAngleDegrees = 140f,
                        forceMoveTo = true
                    )
                }
                drawPath(topPath, color = primaryColor, style = Stroke(width = strokeW, cap = StrokeCap.Round))

                // Top Arrowhead
                val arrowPath1 = Path().apply {
                    moveTo(w * 0.78f, h * 0.18f)
                    lineTo(w * 0.88f, h * 0.28f)
                    lineTo(w * 0.75f, h * 0.36f)
                }
                drawPath(arrowPath1, color = primaryColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // Bottom Arc Arrow
                val bottomPath = Path().apply {
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f),
                        startAngleDegrees = 20f,
                        sweepAngleDegrees = 140f,
                        forceMoveTo = true
                    )
                }
                drawPath(bottomPath, color = secondaryColor, style = Stroke(width = strokeW, cap = StrokeCap.Round))

                // Bottom Arrowhead
                val arrowPath2 = Path().apply {
                    moveTo(w * 0.22f, h * 0.82f)
                    lineTo(w * 0.12f, h * 0.72f)
                    lineTo(w * 0.25f, h * 0.64f)
                }
                drawPath(arrowPath2, color = secondaryColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // Glowing Central Fusion Core
                drawCircle(color = primaryColor, radius = w * 0.08f)
                drawCircle(color = secondaryColor.copy(alpha = 0.4f), radius = w * 0.15f)
            }
        }
    }

    @Composable
    fun FpsSpeed(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = AdfElectricCyan,
        accentColor: Color = Color(0xFF10B981)
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Outer Tachometer Arc
            val arcPath = Path().apply {
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(w * 0.12f, h * 0.12f, w * 0.88f, h * 0.88f),
                    startAngleDegrees = 135f,
                    sweepAngleDegrees = 270f,
                    forceMoveTo = false
                )
            }
            drawPath(
                path = arcPath,
                color = primaryColor.copy(alpha = 0.25f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // High-speed Accent Arc
            val highSpeedPath = Path().apply {
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(w * 0.12f, h * 0.12f, w * 0.88f, h * 0.88f),
                    startAngleDegrees = 315f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
            }
            drawPath(
                path = highSpeedPath,
                color = accentColor,
                style = Stroke(width = strokeW * 1.1f, cap = StrokeCap.Round)
            )

            // Center Pivot Hub
            drawCircle(color = primaryColor, radius = w * 0.12f)
            drawCircle(color = AdfMidnightBackground, radius = w * 0.05f)

            // Dynamic Needle pointed to 120/165 high-zone
            drawLine(
                color = accentColor,
                start = Offset(w * 0.5f, h * 0.5f),
                end = Offset(w * 0.76f, h * 0.28f),
                strokeWidth = strokeW * 1.1f,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun PureViewer(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = Color(0xFF10B981),
        accentColor: Color = AdfElectricCyan
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Clean Optical Eye Contour
            val eyePath = Path().apply {
                moveTo(w * 0.08f, h * 0.5f)
                cubicTo(w * 0.25f, h * 0.22f, w * 0.75f, h * 0.22f, w * 0.92f, h * 0.5f)
                cubicTo(w * 0.75f, h * 0.78f, w * 0.25f, h * 0.78f, w * 0.08f, h * 0.5f)
                close()
            }
            drawPath(
                path = eyePath,
                color = primaryColor.copy(alpha = 0.2f),
                style = Fill
            )
            drawPath(
                path = eyePath,
                color = primaryColor,
                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Central Focus Lens & Iris
            drawCircle(
                color = primaryColor,
                radius = w * 0.18f,
                style = Stroke(width = strokeW * 0.9f)
            )
            drawCircle(color = accentColor, radius = w * 0.09f)

            // Subtle Horizon Reading Lines
            drawLine(
                color = primaryColor.copy(alpha = 0.4f),
                start = Offset(w * 0.35f, h * 0.88f),
                end = Offset(w * 0.65f, h * 0.88f),
                strokeWidth = strokeW * 0.8f,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun SignatureVault(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = AdfCyberViolet,
        accentColor: Color = AdfElectricCyan
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Stylus Pen Barrel (Angled)
            val penPath = Path().apply {
                moveTo(w * 0.75f, h * 0.15f)
                lineTo(w * 0.85f, h * 0.25f)
                lineTo(w * 0.45f, h * 0.65f)
                lineTo(w * 0.32f, h * 0.68f)
                lineTo(w * 0.35f, h * 0.55f)
                close()
            }
            drawPath(penPath, color = primaryColor.copy(alpha = 0.3f), style = Fill)
            drawPath(penPath, color = primaryColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))

            // Nib tip point
            drawLine(
                color = accentColor,
                start = Offset(w * 0.32f, h * 0.68f),
                end = Offset(w * 0.26f, h * 0.74f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )

            // Fluid Vector Signature Flourish
            val sigPath = Path().apply {
                moveTo(w * 0.12f, h * 0.84f)
                cubicTo(w * 0.25f, h * 0.74f, w * 0.35f, h * 0.92f, w * 0.55f, h * 0.82f)
                cubicTo(w * 0.7f, h * 0.72f, w * 0.82f, h * 0.86f, w * 0.92f, h * 0.78f)
            }
            drawPath(sigPath, color = accentColor, style = Stroke(width = strokeW * 0.9f, cap = StrokeCap.Round))
        }
    }

    @Composable
    fun FileOpen(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = Color(0xFFF59E0B),
        accentColor: Color = AdfElectricCyan
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Back Folder Body
            val backFolder = Path().apply {
                moveTo(w * 0.12f, h * 0.28f)
                lineTo(w * 0.38f, h * 0.28f)
                lineTo(w * 0.48f, h * 0.38f)
                lineTo(w * 0.88f, h * 0.38f)
                lineTo(w * 0.88f, h * 0.75f)
                lineTo(w * 0.12f, h * 0.75f)
                close()
            }
            drawPath(backFolder, color = primaryColor.copy(alpha = 0.3f), style = Fill)
            drawPath(backFolder, color = primaryColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))

            // Glowing Data Sheet Rising Out
            val sheet = Path().apply {
                moveTo(w * 0.32f, h * 0.18f)
                lineTo(w * 0.68f, h * 0.18f)
                lineTo(w * 0.68f, h * 0.58f)
                lineTo(w * 0.32f, h * 0.58f)
                close()
            }
            drawPath(sheet, color = accentColor.copy(alpha = 0.25f), style = Fill)
            drawPath(sheet, color = accentColor, style = Stroke(width = strokeW * 0.8f))

            // Front Folder Flap (Angled 3D)
            val frontFlap = Path().apply {
                moveTo(w * 0.08f, h * 0.82f)
                lineTo(w * 0.28f, h * 0.5f)
                lineTo(w * 0.92f, h * 0.5f)
                lineTo(w * 0.82f, h * 0.82f)
                close()
            }
            drawPath(frontFlap, color = primaryColor.copy(alpha = 0.5f), style = Fill)
            drawPath(frontFlap, color = primaryColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun PdfDoc(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = Color(0xFFEF4444),
        accentColor: Color = Color.White
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Document Body with Folded Top-Right Corner
            val docPath = Path().apply {
                moveTo(w * 0.18f, h * 0.12f)
                lineTo(w * 0.62f, h * 0.12f)
                lineTo(w * 0.82f, h * 0.32f)
                lineTo(w * 0.82f, h * 0.88f)
                lineTo(w * 0.18f, h * 0.88f)
                close()
            }
            drawPath(docPath, color = primaryColor.copy(alpha = 0.18f), style = Fill)
            drawPath(docPath, color = primaryColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))

            // Corner Fold Triangle
            val fold = Path().apply {
                moveTo(w * 0.62f, h * 0.12f)
                lineTo(w * 0.62f, h * 0.32f)
                lineTo(w * 0.82f, h * 0.32f)
                close()
            }
            drawPath(fold, color = primaryColor.copy(alpha = 0.45f), style = Fill)
            drawPath(fold, color = primaryColor, style = Stroke(width = strokeW * 0.8f))

            // Red Header Badge / Text Bars
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(w * 0.28f, h * 0.44f),
                size = Size(w * 0.44f, h * 0.10f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            drawLine(
                color = accentColor.copy(alpha = 0.7f),
                start = Offset(w * 0.28f, h * 0.64f),
                end = Offset(w * 0.68f, h * 0.64f),
                strokeWidth = strokeW * 0.8f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = accentColor.copy(alpha = 0.5f),
                start = Offset(w * 0.28f, h * 0.74f),
                end = Offset(w * 0.54f, h * 0.74f),
                strokeWidth = strokeW * 0.8f,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun WordDoc(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = Color(0xFF2563EB),
        accentColor: Color = Color.White
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Document Shell
            val docPath = Path().apply {
                moveTo(w * 0.18f, h * 0.12f)
                lineTo(w * 0.62f, h * 0.12f)
                lineTo(w * 0.82f, h * 0.32f)
                lineTo(w * 0.82f, h * 0.88f)
                lineTo(w * 0.18f, h * 0.88f)
                close()
            }
            drawPath(docPath, color = primaryColor.copy(alpha = 0.2f), style = Fill)
            drawPath(docPath, color = primaryColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))

            // Stylized 'W' Glyph inside
            val wPath = Path().apply {
                moveTo(w * 0.28f, h * 0.45f)
                lineTo(w * 0.36f, h * 0.76f)
                lineTo(w * 0.50f, h * 0.55f)
                lineTo(w * 0.64f, h * 0.76f)
                lineTo(w * 0.72f, h * 0.45f)
            }
            drawPath(wPath, color = accentColor, style = Stroke(width = strokeW * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Watermark(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = AdfCyberViolet,
        accentColor: Color = AdfElectricCyan
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Concentric Protection Rings
            drawCircle(color = primaryColor.copy(alpha = 0.2f), radius = w * 0.40f)
            drawCircle(color = primaryColor, radius = w * 0.40f, style = Stroke(width = strokeW, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))

            // Inner Diagonal Seal Ribbon
            rotate(-45f) {
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.4f),
                    topLeft = Offset(w * 0.2f, h * 0.38f),
                    size = Size(w * 0.6f, h * 0.24f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawLine(
                    color = accentColor,
                    start = Offset(w * 0.28f, h * 0.5f),
                    end = Offset(w * 0.72f, h * 0.5f),
                    strokeWidth = strokeW * 1.1f,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    @Composable
    fun Merge(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = Color(0xFFF59E0B),
        accentColor: Color = AdfElectricCyan
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Left Input Stream
            val leftStream = Path().apply {
                moveTo(w * 0.22f, h * 0.18f)
                lineTo(w * 0.45f, h * 0.45f)
                lineTo(w * 0.45f, h * 0.82f)
            }
            drawPath(leftStream, color = primaryColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Right Input Stream
            val rightStream = Path().apply {
                moveTo(w * 0.78f, h * 0.18f)
                lineTo(w * 0.55f, h * 0.45f)
                lineTo(w * 0.55f, h * 0.82f)
            }
            drawPath(rightStream, color = accentColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Merging Nexus Diamond
            val diamond = Path().apply {
                moveTo(w * 0.5f, h * 0.35f)
                lineTo(w * 0.65f, h * 0.5f)
                lineTo(w * 0.5f, h * 0.65f)
                lineTo(w * 0.35f, h * 0.5f)
                close()
            }
            drawPath(diamond, color = Color.White, style = Fill)
            drawPath(diamond, color = primaryColor, style = Stroke(width = strokeW * 0.8f))
        }
    }

    @Composable
    fun Crown(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        color: Color = AdfElectricCyan
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            val crown = Path().apply {
                moveTo(w * 0.15f, h * 0.75f)
                lineTo(w * 0.12f, h * 0.35f)
                lineTo(w * 0.35f, h * 0.55f)
                lineTo(w * 0.50f, h * 0.25f)
                lineTo(w * 0.65f, h * 0.55f)
                lineTo(w * 0.88f, h * 0.35f)
                lineTo(w * 0.85f, h * 0.75f)
                close()
            }
            drawPath(crown, color = color.copy(alpha = 0.25f), style = Fill)
            drawPath(crown, color = color, style = Stroke(width = strokeW, join = StrokeJoin.Round, cap = StrokeCap.Round))

            // Three crown jewel spheres
            drawCircle(color = Color.White, radius = w * 0.04f, center = Offset(w * 0.12f, h * 0.35f))
            drawCircle(color = Color.White, radius = w * 0.05f, center = Offset(w * 0.50f, h * 0.25f))
            drawCircle(color = Color.White, radius = w * 0.04f, center = Offset(w * 0.88f, h * 0.35f))
        }
    }

    @Composable
    fun Tools(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = AdfElectricCyan,
        accentColor: Color = AdfVibrantBlue
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Wrench/Caliper angled shaft
            val wrench = Path().apply {
                moveTo(w * 0.28f, h * 0.82f)
                lineTo(w * 0.58f, h * 0.52f)
                lineTo(w * 0.48f, h * 0.42f)
                lineTo(w * 0.18f, h * 0.72f)
                close()
            }
            drawPath(wrench, color = primaryColor.copy(alpha = 0.25f), style = Fill)
            drawPath(wrench, color = primaryColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))

            // Precision Gear ring in top right
            drawCircle(
                color = accentColor.copy(alpha = 0.2f),
                radius = w * 0.26f,
                center = Offset(w * 0.68f, h * 0.32f),
                style = Fill
            )
            drawCircle(
                color = accentColor,
                radius = w * 0.26f,
                center = Offset(w * 0.68f, h * 0.32f),
                style = Stroke(width = strokeW)
            )
            drawCircle(
                color = primaryColor,
                radius = w * 0.10f,
                center = Offset(w * 0.68f, h * 0.32f)
            )

            // Gear Cogs
            for (i in 0 until 6) {
                val angle = i * 60f * (Math.PI / 180.0)
                val cx = w * 0.68f + (w * 0.26f * Math.cos(angle)).toFloat()
                val cy = h * 0.32f + (w * 0.26f * Math.sin(angle)).toFloat()
                drawCircle(color = accentColor, radius = strokeW * 0.75f, center = Offset(cx, cy))
            }
        }
    }

    @Composable
    fun Download(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = AdfElectricCyan,
        accentColor: Color = AdfVibrantBlue
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "DownloadPulse")
        val pulseOffset by infiniteTransition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "DownloadYOffset"
        )

        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // U-shaped receiver cradle
            val cradle = Path().apply {
                moveTo(w * 0.15f, h * 0.62f)
                lineTo(w * 0.15f, h * 0.82f)
                lineTo(w * 0.85f, h * 0.82f)
                lineTo(w * 0.85f, h * 0.62f)
            }
            drawPath(cradle, color = accentColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Arrow shaft with micro animated downward glide
            val arrowY = h * 0.18f + (pulseOffset * 1.5f)
            drawLine(
                color = primaryColor,
                start = Offset(w * 0.5f, arrowY),
                end = Offset(w * 0.5f, arrowY + h * 0.40f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )

            // Arrow head
            val head = Path().apply {
                moveTo(w * 0.32f, arrowY + h * 0.26f)
                lineTo(w * 0.50f, arrowY + h * 0.44f)
                lineTo(w * 0.68f, arrowY + h * 0.26f)
            }
            drawPath(head, color = primaryColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun SecurityShield(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = AdfElectricCyan,
        accentColor: Color = AdfCyberViolet
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Defensive Shield Contour
            val shield = Path().apply {
                moveTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.85f, h * 0.24f)
                cubicTo(w * 0.85f, h * 0.60f, w * 0.65f, h * 0.82f, w * 0.50f, h * 0.90f)
                cubicTo(w * 0.35f, h * 0.82f, w * 0.15f, h * 0.60f, w * 0.15f, h * 0.24f)
                close()
            }
            drawPath(shield, color = primaryColor.copy(alpha = 0.20f), style = Fill)
            drawPath(shield, color = primaryColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))

            // Inner Cyber Core Checkmark/Emblem
            val check = Path().apply {
                moveTo(w * 0.34f, h * 0.48f)
                lineTo(w * 0.46f, h * 0.60f)
                lineTo(w * 0.66f, h * 0.38f)
            }
            drawPath(check, color = accentColor, style = Stroke(width = strokeW * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Search(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = AdfElectricCyan,
        accentColor: Color = AdfVibrantBlue
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Optical Sensor Lens
            drawCircle(
                color = primaryColor.copy(alpha = 0.20f),
                radius = w * 0.28f,
                center = Offset(w * 0.42f, h * 0.42f),
                style = Fill
            )
            drawCircle(
                color = primaryColor,
                radius = w * 0.28f,
                center = Offset(w * 0.42f, h * 0.42f),
                style = Stroke(width = strokeW)
            )

            // Scanning reticle center dot
            drawCircle(color = accentColor, radius = w * 0.07f, center = Offset(w * 0.42f, h * 0.42f))

            // Angled Grip Handle
            drawLine(
                color = primaryColor,
                start = Offset(w * 0.63f, h * 0.63f),
                end = Offset(w * 0.86f, h * 0.86f),
                strokeWidth = strokeW * 1.2f,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun SettingsSliders(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = AdfElectricCyan,
        accentColor: Color = AdfCyberViolet
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.08f

            // Track 1
            drawLine(
                color = primaryColor.copy(alpha = 0.35f),
                start = Offset(w * 0.15f, h * 0.32f),
                end = Offset(w * 0.85f, h * 0.32f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            drawCircle(color = primaryColor, radius = w * 0.12f, center = Offset(w * 0.62f, h * 0.32f))
            drawCircle(color = Color.White, radius = w * 0.05f, center = Offset(w * 0.62f, h * 0.32f))

            // Track 2
            drawLine(
                color = accentColor.copy(alpha = 0.35f),
                start = Offset(w * 0.15f, h * 0.68f),
                end = Offset(w * 0.85f, h * 0.68f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            drawCircle(color = accentColor, radius = w * 0.12f, center = Offset(w * 0.38f, h * 0.68f))
            drawCircle(color = Color.White, radius = w * 0.05f, center = Offset(w * 0.38f, h * 0.68f))
        }
    }

    @Composable
    fun Close(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        color: Color = AdfTextSecondary
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.09f

            drawLine(
                color = color,
                start = Offset(w * 0.24f, h * 0.24f),
                end = Offset(w * 0.76f, h * 0.76f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(w * 0.76f, h * 0.24f),
                end = Offset(w * 0.24f, h * 0.76f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun ChevronLeft(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        color: Color = AdfElectricCyan
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.09f

            val path = Path().apply {
                moveTo(w * 0.62f, h * 0.22f)
                lineTo(w * 0.34f, h * 0.50f)
                lineTo(w * 0.62f, h * 0.78f)
            }
            drawPath(path, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun ChevronRight(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        color: Color = AdfElectricCyan
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.09f

            val path = Path().apply {
                moveTo(w * 0.38f, h * 0.22f)
                lineTo(w * 0.66f, h * 0.50f)
                lineTo(w * 0.38f, h * 0.78f)
            }
            drawPath(path, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun ChevronDown(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        color: Color = AdfElectricCyan
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.09f

            val path = Path().apply {
                moveTo(w * 0.22f, h * 0.38f)
                lineTo(w * 0.50f, h * 0.66f)
                lineTo(w * 0.78f, h * 0.38f)
            }
            drawPath(path, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Star(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        isFilled: Boolean = true,
        fillColor: Color = Color(0xFFFBBF24),
        outlineColor: Color = Color(0xFFFBBF24)
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.08f

            val starPath = Path().apply {
                val cx = w * 0.5f
                val cy = h * 0.52f
                val outerR = w * 0.42f
                val innerR = w * 0.19f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outerR else innerR
                    val angle = (i * 36 - 90) * (Math.PI / 180.0)
                    val x = cx + (r * Math.cos(angle)).toFloat()
                    val y = cy + (r * Math.sin(angle)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }

            if (isFilled) {
                drawPath(starPath, color = fillColor.copy(alpha = 0.95f), style = Fill)
            }
            drawPath(starPath, color = outlineColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Delete(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        color: Color = AdfCoral
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Lid & Handle
            drawLine(
                color = color,
                start = Offset(w * 0.20f, h * 0.30f),
                end = Offset(w * 0.80f, h * 0.30f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(w * 0.40f, h * 0.20f),
                end = Offset(w * 0.60f, h * 0.20f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )

            // Canister Body
            val can = Path().apply {
                moveTo(w * 0.26f, h * 0.30f)
                lineTo(w * 0.32f, h * 0.84f)
                lineTo(w * 0.68f, h * 0.84f)
                lineTo(w * 0.74f, h * 0.30f)
            }
            drawPath(can, color = color.copy(alpha = 0.2f), style = Fill)
            drawPath(can, color = color, style = Stroke(width = strokeW, join = StrokeJoin.Round))

            // Vertical vents
            drawLine(color = color, start = Offset(w * 0.42f, h * 0.42f), end = Offset(w * 0.42f, h * 0.72f), strokeWidth = strokeW * 0.8f, cap = StrokeCap.Round)
            drawLine(color = color, start = Offset(w * 0.58f, h * 0.42f), end = Offset(w * 0.58f, h * 0.72f), strokeWidth = strokeW * 0.8f, cap = StrokeCap.Round)
        }
    }

    @Composable
    fun Layers(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = AdfElectricCyan,
        accentColor: Color = AdfCyberViolet
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.08f

            // Top rhombus
            val topLayer = Path().apply {
                moveTo(w * 0.50f, h * 0.15f)
                lineTo(w * 0.85f, h * 0.33f)
                lineTo(w * 0.50f, h * 0.51f)
                lineTo(w * 0.15f, h * 0.33f)
                close()
            }
            drawPath(topLayer, color = primaryColor.copy(alpha = 0.35f), style = Fill)
            drawPath(topLayer, color = primaryColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))

            // Mid Chevron
            val midLayer = Path().apply {
                moveTo(w * 0.15f, h * 0.53f)
                lineTo(w * 0.50f, h * 0.71f)
                lineTo(w * 0.85f, h * 0.53f)
            }
            drawPath(midLayer, color = accentColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))

            // Bottom Chevron
            val botLayer = Path().apply {
                moveTo(w * 0.15f, h * 0.73f)
                lineTo(w * 0.50f, h * 0.91f)
                lineTo(w * 0.85f, h * 0.73f)
            }
            drawPath(botLayer, color = primaryColor.copy(alpha = 0.6f), style = Stroke(width = strokeW, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Camera(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = AdfElectricCyan,
        accentColor: Color = AdfCoral
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Camera Body
            val body = Path().apply {
                moveTo(w * 0.12f, h * 0.32f)
                lineTo(w * 0.32f, h * 0.32f)
                lineTo(w * 0.38f, h * 0.20f)
                lineTo(w * 0.62f, h * 0.20f)
                lineTo(w * 0.68f, h * 0.32f)
                lineTo(w * 0.88f, h * 0.32f)
                lineTo(w * 0.88f, h * 0.82f)
                lineTo(w * 0.12f, h * 0.82f)
                close()
            }
            drawPath(body, color = primaryColor.copy(alpha = 0.2f), style = Fill)
            drawPath(body, color = primaryColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))

            // Center Aperture Lens
            drawCircle(
                color = primaryColor,
                radius = w * 0.18f,
                center = Offset(w * 0.50f, h * 0.55f),
                style = Stroke(width = strokeW)
            )
            drawCircle(
                color = accentColor,
                radius = w * 0.07f,
                center = Offset(w * 0.50f, h * 0.55f)
            )

            // Flash sensor
            drawCircle(color = Color.White, radius = w * 0.04f, center = Offset(w * 0.76f, h * 0.42f))
        }
    }

    @Composable
    fun Whiteout(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = AdfCoral,
        accentColor: Color = Color.White
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Angled Eraser Brick
            val eraser = Path().apply {
                moveTo(w * 0.35f, h * 0.15f)
                lineTo(w * 0.85f, h * 0.45f)
                lineTo(w * 0.65f, h * 0.85f)
                lineTo(w * 0.15f, h * 0.55f)
                close()
            }
            drawPath(eraser, color = primaryColor.copy(alpha = 0.3f), style = Fill)
            drawPath(eraser, color = primaryColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))

            // Eraser Active Sweep Band
            val sweep = Path().apply {
                moveTo(w * 0.25f, h * 0.35f)
                lineTo(w * 0.75f, h * 0.65f)
            }
            drawPath(sweep, color = accentColor, style = Stroke(width = strokeW * 1.2f, cap = StrokeCap.Round))

            // Horizontal wiped underline
            drawLine(
                color = accentColor.copy(alpha = 0.5f),
                start = Offset(w * 0.10f, h * 0.88f),
                end = Offset(w * 0.90f, h * 0.88f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun MenuDots(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        color: Color = AdfTextSecondary
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val r = w * 0.08f

            drawCircle(color = color, radius = r, center = Offset(w * 0.5f, h * 0.25f))
            drawCircle(color = color, radius = r, center = Offset(w * 0.5f, h * 0.50f))
            drawCircle(color = color, radius = r, center = Offset(w * 0.5f, h * 0.75f))
        }
    }

    @Composable
    fun MenuThreeLines(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        color: Color = AdfElectricCyan
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.09f

            // Top line
            drawLine(
                color = color,
                start = Offset(w * 0.16f, h * 0.28f),
                end = Offset(w * 0.84f, h * 0.28f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            // Middle line (styled with subtle cyber step)
            drawLine(
                color = color,
                start = Offset(w * 0.16f, h * 0.50f),
                end = Offset(w * 0.68f, h * 0.50f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            // Bottom line
            drawLine(
                color = color,
                start = Offset(w * 0.16f, h * 0.72f),
                end = Offset(w * 0.84f, h * 0.72f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun Refresh(
        modifier: Modifier = Modifier,
        size: Dp = 20.dp,
        color: Color = AdfElectricCyan
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.10f
            val arcRect = androidx.compose.ui.geometry.Rect(w * 0.18f, h * 0.18f, w * 0.82f, h * 0.82f)
            val arcPath = Path().apply {
                arcTo(arcRect, 45f, 280f, false)
            }
            drawPath(arcPath, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round))
            val arrow = Path().apply {
                moveTo(w * 0.70f, h * 0.10f)
                lineTo(w * 0.88f, h * 0.22f)
                lineTo(w * 0.72f, h * 0.38f)
            }
            drawPath(arrow, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun FolderEmpty(
        modifier: Modifier = Modifier,
        size: Dp = 48.dp,
        primaryColor: Color = AdfTextMuted,
        accentColor: Color = AdfElectricCyan
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.06f

            // Folder contour
            val folder = Path().apply {
                moveTo(w * 0.15f, h * 0.30f)
                lineTo(w * 0.38f, h * 0.30f)
                lineTo(w * 0.48f, h * 0.40f)
                lineTo(w * 0.85f, h * 0.40f)
                lineTo(w * 0.85f, h * 0.78f)
                lineTo(w * 0.15f, h * 0.78f)
                close()
            }
            drawPath(
                folder,
                color = primaryColor,
                style = Stroke(
                    width = strokeW,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            )

            // Central minimal slash or zero
            drawCircle(color = accentColor.copy(alpha = 0.4f), radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.58f))
        }
    }

    @Composable
    fun Lightning(
        modifier: Modifier = Modifier,
        size: Dp = 20.dp,
        color: Color = Color(0xFFD946EF)
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val bolt = Path().apply {
                moveTo(w * 0.56f, h * 0.08f)
                lineTo(w * 0.22f, h * 0.54f)
                lineTo(w * 0.48f, h * 0.54f)
                lineTo(w * 0.42f, h * 0.92f)
                lineTo(w * 0.78f, h * 0.44f)
                lineTo(w * 0.52f, h * 0.44f)
                close()
            }
            drawPath(bolt, color = color, style = Fill)
        }
    }

    @Composable
    fun DeveloperBadge(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        primaryColor: Color = AdfElectricCyan,
        accentColor: Color = AdfCyberViolet
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.09f

            // Left angle bracket <
            val leftBracket = Path().apply {
                moveTo(w * 0.32f, h * 0.28f)
                lineTo(w * 0.14f, h * 0.50f)
                lineTo(w * 0.32f, h * 0.72f)
            }
            drawPath(leftBracket, color = primaryColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Right angle bracket >
            val rightBracket = Path().apply {
                moveTo(w * 0.68f, h * 0.28f)
                lineTo(w * 0.86f, h * 0.50f)
                lineTo(w * 0.68f, h * 0.72f)
            }
            drawPath(rightBracket, color = primaryColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Forward slash /
            drawLine(
                color = accentColor,
                start = Offset(w * 0.58f, h * 0.22f),
                end = Offset(w * 0.42f, h * 0.78f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }

    // ==========================================
    // 8 Bespoke Social Platform Vector Icons
    // ==========================================

    @Composable
    fun Instagram(
        modifier: Modifier = Modifier,
        size: Dp = 32.dp,
        primaryColor: Color = Color(0xFFFF2E93),
        accentColor: Color = Color(0xFFD946EF)
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Outer squircle container
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(w * 0.12f, h * 0.12f),
                size = Size(w * 0.76f, h * 0.76f),
                cornerRadius = CornerRadius(w * 0.24f, h * 0.24f),
                style = Stroke(width = strokeW)
            )

            // Inner camera lens circle
            drawCircle(
                color = accentColor,
                radius = w * 0.20f,
                center = Offset(w * 0.50f, h * 0.50f),
                style = Stroke(width = strokeW)
            )

            // Flash sensor dot
            drawCircle(
                color = Color.White,
                radius = w * 0.045f,
                center = Offset(w * 0.68f, h * 0.32f)
            )
        }
    }

    @Composable
    fun GitHub(
        modifier: Modifier = Modifier,
        size: Dp = 32.dp,
        primaryColor: Color = Color(0xFFF0F6FC),
        accentColor: Color = AdfElectricCyan
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.08f

            // Head & cat silhouette
            val head = Path().apply {
                moveTo(w * 0.20f, h * 0.50f)
                cubicTo(w * 0.20f, h * 0.30f, w * 0.34f, h * 0.16f, w * 0.50f, h * 0.16f)
                cubicTo(w * 0.66f, h * 0.16f, w * 0.80f, h * 0.30f, w * 0.80f, h * 0.50f)
                cubicTo(w * 0.80f, h * 0.72f, w * 0.66f, h * 0.84f, w * 0.50f, h * 0.84f)
                cubicTo(w * 0.34f, h * 0.84f, w * 0.20f, h * 0.72f, w * 0.20f, h * 0.50f)
                close()
            }
            drawPath(head, color = primaryColor.copy(alpha = 0.15f), style = Fill)
            drawPath(head, color = primaryColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))

            // Cat Ears
            val earL = Path().apply {
                moveTo(w * 0.28f, h * 0.34f)
                lineTo(w * 0.22f, h * 0.18f)
                lineTo(w * 0.38f, h * 0.22f)
            }
            drawPath(earL, color = primaryColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

            val earR = Path().apply {
                moveTo(w * 0.72f, h * 0.34f)
                lineTo(w * 0.78f, h * 0.18f)
                lineTo(w * 0.62f, h * 0.22f)
            }
            drawPath(earR, color = primaryColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Inner fork/branch anchor
            drawLine(
                color = accentColor,
                start = Offset(w * 0.50f, h * 0.40f),
                end = Offset(w * 0.50f, h * 0.72f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            drawCircle(color = accentColor, radius = w * 0.05f, center = Offset(w * 0.50f, h * 0.40f))
            drawCircle(color = primaryColor, radius = w * 0.05f, center = Offset(w * 0.50f, h * 0.72f))
        }
    }

    @Composable
    fun LinkedIn(
        modifier: Modifier = Modifier,
        size: Dp = 32.dp,
        primaryColor: Color = Color(0xFF00E5FF),
        accentColor: Color = Color(0xFF2563EB)
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Outer rounded tile
            drawRoundRect(
                color = accentColor.copy(alpha = 0.25f),
                topLeft = Offset(w * 0.12f, h * 0.12f),
                size = Size(w * 0.76f, h * 0.76f),
                cornerRadius = CornerRadius(w * 0.20f, h * 0.20f),
                style = Fill
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(w * 0.12f, h * 0.12f),
                size = Size(w * 0.76f, h * 0.76f),
                cornerRadius = CornerRadius(w * 0.20f, h * 0.20f),
                style = Stroke(width = strokeW)
            )

            // Letter 'i'
            drawCircle(color = Color.White, radius = w * 0.045f, center = Offset(w * 0.32f, h * 0.34f))
            drawLine(
                color = Color.White,
                start = Offset(w * 0.32f, h * 0.46f),
                end = Offset(w * 0.32f, h * 0.70f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )

            // Letter 'n'
            val nPath = Path().apply {
                moveTo(w * 0.48f, h * 0.70f)
                lineTo(w * 0.48f, h * 0.46f)
                cubicTo(w * 0.48f, h * 0.40f, w * 0.56f, h * 0.40f, w * 0.62f, h * 0.46f)
                lineTo(w * 0.62f, h * 0.70f)
            }
            drawPath(nPath, color = Color.White, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun XTwitter(
        modifier: Modifier = Modifier,
        size: Dp = 32.dp,
        primaryColor: Color = Color.White,
        accentColor: Color = AdfElectricCyan
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.09f

            // Primary diagonal bar
            drawLine(
                color = primaryColor,
                start = Offset(w * 0.18f, h * 0.18f),
                end = Offset(w * 0.82f, h * 0.82f),
                strokeWidth = strokeW * 1.2f,
                cap = StrokeCap.Round
            )

            // Crossing thin diagonal bar
            drawLine(
                color = accentColor,
                start = Offset(w * 0.82f, h * 0.18f),
                end = Offset(w * 0.18f, h * 0.82f),
                strokeWidth = strokeW * 0.9f,
                cap = StrokeCap.Round
            )

            // Center fusion core
            drawCircle(color = primaryColor, radius = w * 0.06f, center = Offset(w * 0.50f, h * 0.50f))
        }
    }

    @Composable
    fun YouTube(
        modifier: Modifier = Modifier,
        size: Dp = 32.dp,
        primaryColor: Color = Color(0xFFEF4444),
        accentColor: Color = Color.White
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Screen frame
            drawRoundRect(
                color = primaryColor.copy(alpha = 0.25f),
                topLeft = Offset(w * 0.12f, h * 0.22f),
                size = Size(w * 0.76f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.18f, h * 0.18f),
                style = Fill
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(w * 0.12f, h * 0.22f),
                size = Size(w * 0.76f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.18f, h * 0.18f),
                style = Stroke(width = strokeW)
            )

            // Play triangle
            val play = Path().apply {
                moveTo(w * 0.44f, h * 0.38f)
                lineTo(w * 0.62f, h * 0.50f)
                lineTo(w * 0.44f, h * 0.62f)
                close()
            }
            drawPath(play, color = accentColor, style = Fill)
        }
    }

    @Composable
    fun WhatsApp(
        modifier: Modifier = Modifier,
        size: Dp = 32.dp,
        primaryColor: Color = Color(0xFF10B981),
        accentColor: Color = Color.White
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Speech bubble outline with tail
            val bubble = Path().apply {
                moveTo(w * 0.50f, h * 0.14f)
                cubicTo(w * 0.72f, h * 0.14f, w * 0.86f, h * 0.28f, w * 0.86f, h * 0.48f)
                cubicTo(w * 0.86f, h * 0.68f, w * 0.72f, h * 0.82f, w * 0.50f, h * 0.82f)
                lineTo(w * 0.26f, h * 0.88f)
                lineTo(w * 0.30f, h * 0.72f)
                cubicTo(w * 0.18f, h * 0.66f, w * 0.14f, h * 0.58f, w * 0.14f, h * 0.48f)
                cubicTo(w * 0.14f, h * 0.28f, w * 0.28f, h * 0.14f, w * 0.50f, h * 0.14f)
                close()
            }
            drawPath(bubble, color = primaryColor.copy(alpha = 0.2f), style = Fill)
            drawPath(bubble, color = primaryColor, style = Stroke(width = strokeW, join = StrokeJoin.Round))

            // Telephone handset arc
            val phone = Path().apply {
                moveTo(w * 0.38f, h * 0.38f)
                cubicTo(w * 0.42f, h * 0.46f, w * 0.54f, h * 0.58f, w * 0.62f, h * 0.62f)
            }
            drawPath(phone, color = accentColor, style = Stroke(width = strokeW * 1.2f, cap = StrokeCap.Round))
            drawCircle(color = accentColor, radius = w * 0.05f, center = Offset(w * 0.38f, h * 0.38f))
            drawCircle(color = accentColor, radius = w * 0.05f, center = Offset(w * 0.62f, h * 0.62f))
        }
    }

    @Composable
    fun Facebook(
        modifier: Modifier = Modifier,
        size: Dp = 32.dp,
        primaryColor: Color = Color(0xFF3B82F6),
        accentColor: Color = Color.White
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f

            // Outer rounded tile
            drawRoundRect(
                color = primaryColor.copy(alpha = 0.25f),
                topLeft = Offset(w * 0.12f, h * 0.12f),
                size = Size(w * 0.76f, h * 0.76f),
                cornerRadius = CornerRadius(w * 0.22f, h * 0.22f),
                style = Fill
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(w * 0.12f, h * 0.12f),
                size = Size(w * 0.76f, h * 0.76f),
                cornerRadius = CornerRadius(w * 0.22f, h * 0.22f),
                style = Stroke(width = strokeW)
            )

            // Letter 'f'
            val fPath = Path().apply {
                moveTo(w * 0.64f, h * 0.32f)
                cubicTo(w * 0.56f, h * 0.32f, w * 0.50f, h * 0.36f, w * 0.50f, h * 0.44f)
                lineTo(w * 0.50f, h * 0.76f)
            }
            drawPath(fPath, color = accentColor, style = Stroke(width = strokeW * 1.1f, cap = StrokeCap.Round))
            drawLine(
                color = accentColor,
                start = Offset(w * 0.38f, h * 0.48f),
                end = Offset(w * 0.62f, h * 0.48f),
                strokeWidth = strokeW * 1.1f,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun Reddit(
        modifier: Modifier = Modifier,
        size: Dp = 32.dp,
        primaryColor: Color = Color(0xFFFF5722),
        accentColor: Color = Color.White
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.08f

            // Snoo oval head
            drawOval(
                color = primaryColor.copy(alpha = 0.2f),
                topLeft = Offset(w * 0.20f, h * 0.34f),
                size = Size(w * 0.60f, h * 0.44f),
                style = Fill
            )
            drawOval(
                color = primaryColor,
                topLeft = Offset(w * 0.20f, h * 0.34f),
                size = Size(w * 0.60f, h * 0.44f),
                style = Stroke(width = strokeW)
            )

            // Left & Right ears
            drawCircle(color = primaryColor, radius = w * 0.07f, center = Offset(w * 0.18f, h * 0.54f))
            drawCircle(color = primaryColor, radius = w * 0.07f, center = Offset(w * 0.82f, h * 0.54f))

            // Antenna
            val ant = Path().apply {
                moveTo(w * 0.50f, h * 0.34f)
                lineTo(w * 0.56f, h * 0.18f)
                lineTo(w * 0.68f, h * 0.22f)
            }
            drawPath(ant, color = primaryColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color = primaryColor, radius = w * 0.05f, center = Offset(w * 0.70f, h * 0.22f))

            // Eyes
            drawCircle(color = primaryColor, radius = w * 0.05f, center = Offset(w * 0.38f, h * 0.54f))
            drawCircle(color = primaryColor, radius = w * 0.05f, center = Offset(w * 0.62f, h * 0.54f))

            // Smile
            val smile = Path().apply {
                moveTo(w * 0.40f, h * 0.66f)
                cubicTo(w * 0.46f, h * 0.72f, w * 0.54f, h * 0.72f, w * 0.60f, h * 0.66f)
            }
            drawPath(smile, color = accentColor, style = Stroke(width = strokeW, cap = StrokeCap.Round))
        }
    }
}

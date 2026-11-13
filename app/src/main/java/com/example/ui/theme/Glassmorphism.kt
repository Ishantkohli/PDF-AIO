package com.example.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==========================================
// Liquid Glass Aesthetic System Design Tokens
// ==========================================

// Translucent Frosted Glass Base Layers
val GlassSurfaceDark = Color(0x2E0D162B) // 18% translucent glass
val GlassSurfaceElevated = Color(0x4A131F3B) // 29% translucent glass
val GlassSurfaceLight = Color(0x1F203050) // 12% translucent white-blue sheen

// Specular Glass Border Highlights
val SpecularHighlightTop = Color(0x80FFFFFF) // Top specular reflection shine
val SpecularHighlightBottom = Color(0x2200E5FF) // Subtle cyan bottom glow

val LiquidGlassBorderGradient = Brush.linearGradient(
    colors = listOf(
        Color(0x80FFFFFF), // Top specular highlight
        Color(0x3800E5FF), // Cyan transition
        Color(0x228B5CF6), // Violet transition
        Color(0x10000000)  // Subtle bottom
    ),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

val LiquidGlassActiveBorder = Brush.linearGradient(
    colors = listOf(
        Color(0xFF00E5FF),
        Color(0xFF38BDF8),
        Color(0xFF818CF8)
    )
)

/**
 * Animated Ambient Liquid Fluid Mesh Background
 * Draws glowing, organic fluid orbs slowly breathing in the background
 */
@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidGlassFluid")
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050814)) // Deep liquid abyss
    ) {
        // Fluid GPU Canvas drawing soft ambient glowing fluid spheres
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Orb 1: Cyan / Electric Blue liquid blob top-left
            val rad1 = width * 0.55f
            val rad1Rad = Math.toRadians(phase1.toDouble())
            val orb1X = width * 0.25f + Math.sin(rad1Rad).toFloat() * 50f
            val orb1Y = height * 0.2f + Math.cos(rad1Rad).toFloat() * 50f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x3A00E5FF),
                        Color(0x180284C7),
                        Color(0x00050814)
                    ),
                    center = Offset(orb1X, orb1Y),
                    radius = rad1
                ),
                center = Offset(orb1X, orb1Y),
                radius = rad1
            )

            // Orb 2: Glass Violet / Iris liquid blob middle-right
            val rad2 = width * 0.65f
            val rad2Rad = Math.toRadians(phase2.toDouble())
            val orb2X = width * 0.75f + Math.cos(rad2Rad).toFloat() * 70f
            val orb2Y = height * 0.55f + Math.sin(rad2Rad).toFloat() * 70f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x30818CF8),
                        Color(0x156366F1),
                        Color(0x00050814)
                    ),
                    center = Offset(orb2X, orb2Y),
                    radius = rad2
                ),
                center = Offset(orb2X, orb2Y),
                radius = rad2
            )

            // Orb 3: Translucent Coral / Pink liquid accent bottom-left
            val rad3 = width * 0.50f
            val orb3X = width * 0.2f + Math.cos(rad1Rad * 0.5).toFloat() * 40f
            val orb3Y = height * 0.85f + Math.sin(rad1Rad * 0.5).toFloat() * 40f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x22EC4899),
                        Color(0x0E3B82F6),
                        Color(0x00050814)
                    ),
                    center = Offset(orb3X, orb3Y),
                    radius = rad3
                ),
                center = Offset(orb3X, orb3Y),
                radius = rad3
            )
        }

        // Overlay Content
        content()
    }
}

/**
 * Reusable Liquid Glass Container Card with glossy highlight border
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color = GlassSurfaceDark,
    borderBrush: Brush = LiquidGlassBorderGradient,
    borderWidth: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = CardDefaults.outlinedCardBorder().copy(brush = borderBrush, width = borderWidth),
        modifier = cardModifier
    ) {
        Column {
            content()
        }
    }
}

/**
 * Reusable Liquid Glass Surface
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = GlassSurfaceDark,
    borderBrush: Brush = LiquidGlassBorderGradient,
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit
) {
    Surface(
        shape = shape,
        color = backgroundColor,
        border = CardDefaults.outlinedCardBorder().copy(brush = borderBrush, width = borderWidth),
        modifier = modifier
    ) {
        content()
    }
}

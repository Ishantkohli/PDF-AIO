package com.example.ui.components

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sin

/**
 * 100% Offline Text-To-Speech Reader Control Card with Speed Selectors
 * and Real-Time Audio Frequency Waveform Visualizer.
 */
@Composable
fun OfflineTtsVisualizer(
    textToRead: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    // Initialize local offline TTS engine
    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsEngine = tts
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // Waveform Animation Loop
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(50)
            tick = (tick + 1) % 360
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = AdfObsidianSurface,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
        ),
        tonalElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("offline_tts_visualizer_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AdfElectricCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = AdfElectricCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Offline Read Aloud (TTS)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = AdfTextPrimary
                        )
                        Text(
                            text = "100% Local Device Speech Engine",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = AdfTextSecondary
                        )
                    }
                }

                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AdfTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real-Time Animated Waveform Canvas (12 Frequency Bars)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AdfMidnightBackground)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barCount = 18
                    val gap = 6.dp.toPx()
                    val barWidth = (size.width - (gap * (barCount - 1))) / barCount
                    val maxHeight = size.height

                    for (i in 0 until barCount) {
                        val amplitude = if (isPlaying) {
                            val factor = sin((tick * 0.15f) + (i * 0.4f)).coerceAtLeast(0.1f)
                            maxHeight * factor * 0.85f
                        } else {
                            maxHeight * 0.15f
                        }

                        val left = i * (barWidth + gap)
                        val top = (maxHeight - amplitude) / 2f

                        drawRoundRect(
                            color = if (isPlaying) AdfElectricCyan else AdfTextMuted,
                            topLeft = Offset(left, top),
                            size = Size(barWidth, amplitude),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Speed Selector Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Speed:", fontSize = 12.sp, color = AdfTextSecondary, fontWeight = FontWeight.SemiBold)

                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    speeds.forEach { speed ->
                        val isSelected = currentSpeed == speed
                        Surface(
                            onClick = { currentSpeed = speed },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) CrimsonPrimary else AdfMidnightSurface,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = if (isSelected) Brush.horizontalGradient(listOf(CrimsonPrimary, CrimsonPrimary))
                                else Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle))
                            ),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = "${speed}x",
                                color = if (isSelected) Color.White else AdfTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                    .testTag("tts_speed_${speed}x")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        ttsEngine?.stop()
                        isPlaying = false
                    }
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.Red, modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(16.dp))

                FloatingActionButton(
                    onClick = {
                        if (isPlaying) {
                            ttsEngine?.stop()
                            isPlaying = false
                        } else {
                            ttsEngine?.setSpeechRate(currentSpeed)
                            ttsEngine?.speak(
                                textToRead.ifBlank { "PDF AIO 100% offline text to speech reader." },
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "tts_read_id"
                            )
                            isPlaying = true
                        }
                    },
                    containerColor = CrimsonPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.size(52.dp).testTag("tts_play_pause_fab")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

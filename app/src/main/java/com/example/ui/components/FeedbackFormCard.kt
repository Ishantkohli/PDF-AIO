package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FeedbackDispatcher
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun FeedbackFormCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var problemsFaced by remember { mutableStateOf("") }
    var updateSuggestion by remember { mutableStateOf("") }
    var appSuggestion by remember { mutableStateOf("") }

    var isSubmitting by remember { mutableStateOf(false) }
    var isSubmitted by remember { mutableStateOf(false) }

    val activeThemeStyle by ThemeManager.currentTheme.collectAsStateWithLifecycle()
    val isLiquidTheme = activeThemeStyle == AppThemeStyle.LIQUID_GLASS || activeThemeStyle == AppThemeStyle.FROSTED_CRYSTAL

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = if (isLiquidTheme) LiquidGlassBorderGradient else Brush.horizontalGradient(
                listOf(AdfElectricCyan.copy(alpha = 0.5f), AdfVibrantBlue.copy(alpha = 0.5f))
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("feedback_form_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AdfElectricCyan.copy(alpha = 0.15f))
                        .border(1.dp, AdfElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Feedback,
                        contentDescription = "Feedback",
                        tint = AdfElectricCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Feedback Form",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                    Text(
                        text = "Send feedback directly to developer (ishantkohlikohli@gmail.com)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = AdfTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedContent(
                targetState = isSubmitted,
                label = "FeedbackFormTransition"
            ) { submitted ->
                if (submitted) {
                    // ==============================================================
                    // POST-SUBMISSION STATE: EXACT REQUESTED CONFIRMATION TEXT
                    // ==============================================================
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AdfEmeraldGreen.copy(alpha = 0.2f))
                                .border(2.dp, AdfEmeraldGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = AdfEmeraldGreen,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Your FeedBack is recorded",
                            color = AdfElectricCyan,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Thank You For Feedback",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Thank you for using our App",
                            color = AdfTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedButton(
                            onClick = {
                                name = ""
                                problemsFaced = ""
                                updateSuggestion = ""
                                appSuggestion = ""
                                isSubmitted = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("submit_another_feedback_button")
                        ) {
                            Text("Submit Another Response", color = AdfElectricCyan, fontSize = 12.sp)
                        }
                    }
                } else {
                    // ==============================================================
                    // FEEDBACK FORM INPUT FIELDS
                    // ==============================================================
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Field 1: Name
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Name :", fontSize = 12.sp, color = AdfElectricCyan) },
                            placeholder = { Text("Enter your name...", fontSize = 12.sp, color = AdfTextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AdfTextPrimary,
                                unfocusedTextColor = AdfTextPrimary,
                                focusedBorderColor = AdfElectricCyan,
                                unfocusedBorderColor = AdfBorderSubtle,
                                focusedContainerColor = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface,
                                unfocusedContainerColor = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("feedback_input_name")
                        )

                        // Field 2: Any Problems Faced
                        OutlinedTextField(
                            value = problemsFaced,
                            onValueChange = { problemsFaced = it },
                            label = { Text("Any Problems Faced :", fontSize = 12.sp, color = AdfElectricCyan) },
                            placeholder = { Text("Describe any problems or bugs faced...", fontSize = 12.sp, color = AdfTextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AdfTextPrimary,
                                unfocusedTextColor = AdfTextPrimary,
                                focusedBorderColor = AdfElectricCyan,
                                unfocusedBorderColor = AdfBorderSubtle,
                                focusedContainerColor = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface,
                                unfocusedContainerColor = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(90.dp).testTag("feedback_input_problems")
                        )

                        // Field 3: Suggestion for new update
                        OutlinedTextField(
                            value = updateSuggestion,
                            onValueChange = { updateSuggestion = it },
                            label = { Text("Suggestion for new update :", fontSize = 12.sp, color = AdfElectricCyan) },
                            placeholder = { Text("Features you want in next update...", fontSize = 12.sp, color = AdfTextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AdfTextPrimary,
                                unfocusedTextColor = AdfTextPrimary,
                                focusedBorderColor = AdfElectricCyan,
                                unfocusedBorderColor = AdfBorderSubtle,
                                focusedContainerColor = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface,
                                unfocusedContainerColor = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(90.dp).testTag("feedback_input_update_suggestion")
                        )

                        // Field 4: Suggestion on the app
                        OutlinedTextField(
                            value = appSuggestion,
                            onValueChange = { appSuggestion = it },
                            label = { Text("Suggestion on the app :", fontSize = 12.sp, color = AdfElectricCyan) },
                            placeholder = { Text("General thoughts or design feedback...", fontSize = 12.sp, color = AdfTextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AdfTextPrimary,
                                unfocusedTextColor = AdfTextPrimary,
                                focusedBorderColor = AdfElectricCyan,
                                unfocusedBorderColor = AdfBorderSubtle,
                                focusedContainerColor = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface,
                                unfocusedContainerColor = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(90.dp).testTag("feedback_input_app_suggestion")
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Submit Button
                        Button(
                            enabled = !isSubmitting,
                            onClick = {
                                if (isSubmitting) return@Button
                                isSubmitting = true
                                val currentName = name
                                val currentProblems = problemsFaced
                                val currentUpdate = updateSuggestion
                                val currentApp = appSuggestion

                                coroutineScope.launch {
                                    try {
                                        FeedbackDispatcher.dispatchFeedback(
                                            context = context,
                                            name = currentName,
                                            problemsFaced = currentProblems,
                                            updateSuggestion = currentUpdate,
                                            appSuggestion = currentApp
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("FeedbackForm", "Dispatch failed: ${e.message}")
                                    } finally {
                                        isSubmitting = false
                                        isSubmitted = true
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("submit_feedback_button")
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = AdfMidnightBackground,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Sending to ishantkohlikohli@gmail.com...",
                                    color = AdfMidnightBackground,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            } else {
                                Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = AdfMidnightBackground, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Submit Feedback to Developer",
                                    color = AdfMidnightBackground,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

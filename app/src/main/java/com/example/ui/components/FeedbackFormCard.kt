package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FeedbackDispatcher
import com.example.data.FeedbackDraft
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface FeedbackSubmitState {
    data object Idle : FeedbackSubmitState
    data object Submitting : FeedbackSubmitState
    data class DeliveredRemote(val draft: FeedbackDraft?) : FeedbackSubmitState
    data class SavedOfflineFailed(val draft: FeedbackDraft?, val reason: String) : FeedbackSubmitState
}

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
    var activeDraftId by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var submitState by remember { mutableStateOf<FeedbackSubmitState>(FeedbackSubmitState.Idle) }
    var savedDrafts by remember { mutableStateOf<List<FeedbackDraft>>(emptyList()) }
    var showDrafts by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        savedDrafts = FeedbackDispatcher.loadDrafts(context)
    }

    val activeThemeStyle by ThemeManager.currentTheme.collectAsStateWithLifecycle()
    val isLiquidTheme = activeThemeStyle == AppThemeStyle.LIQUID_GLASS || activeThemeStyle == AppThemeStyle.FROSTED_CRYSTAL

    fun performSubmit() {
        if (submitState is FeedbackSubmitState.Submitting) return
        if (problemsFaced.isBlank() && updateSuggestion.isBlank() && appSuggestion.isBlank()) {
            validationError = "Please enter a problem, update suggestion, or feedback message first."
            return
        }
        validationError = null
        submitState = FeedbackSubmitState.Submitting
        val currentName = name
        val currentProblems = problemsFaced
        val currentUpdate = updateSuggestion
        val currentApp = appSuggestion
        val draftId = activeDraftId

        coroutineScope.launch {
            val result = FeedbackDispatcher.dispatchFeedbackWithResult(
                context = context,
                name = currentName,
                problemsFaced = currentProblems,
                updateSuggestion = currentUpdate,
                appSuggestion = currentApp,
                existingDraftId = draftId
            )
            savedDrafts = FeedbackDispatcher.loadDrafts(context)
            activeDraftId = result.draft?.id
            submitState = if (result.remoteDelivered) {
                FeedbackSubmitState.DeliveredRemote(result.draft)
            } else {
                FeedbackSubmitState.SavedOfflineFailed(
                    draft = result.draft,
                    reason = result.errorMessage ?: "Could not reach the feedback server."
                )
            }
        }
    }

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
                        text = "Send feedback directly to developer (${FeedbackDispatcher.DEVELOPER_EMAIL})",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = AdfTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedContent(
                targetState = submitState,
                label = "FeedbackFormTransition"
            ) { state ->
                when (state) {
                    is FeedbackSubmitState.DeliveredRemote -> {
                        // Confirmed HTTP 2xx delivery
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                                .testTag("feedback_delivered_state")
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
                                    activeDraftId = null
                                    submitState = FeedbackSubmitState.Idle
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("submit_another_feedback_button")
                            ) {
                                Text("Submit Another Response", color = AdfElectricCyan, fontSize = 12.sp)
                            }
                        }
                    }

                    is FeedbackSubmitState.SavedOfflineFailed -> {
                        // Honest failure disclosure + local draft options
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .testTag("feedback_failed_state")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(AdfAmber.copy(alpha = 0.18f))
                                    .border(2.dp, AdfAmber, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Remote delivery failed",
                                    tint = AdfAmber,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Remote Send Unavailable — Saved Locally",
                                color = AdfAmber,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Could not deliver online (${state.reason}). Your feedback was saved safely as a local draft on this device.",
                                color = AdfTextSecondary,
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { performSubmit() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("feedback_retry_send_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AdfMidnightBackground, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry Send", color = AdfMidnightBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                state.draft?.let { draft ->
                                    OutlinedButton(
                                        onClick = { launchIntentSafely(context, FeedbackDispatcher.buildEmailIntent(draft), "Open Email App") },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("feedback_send_email_button")
                                    ) {
                                        Icon(Icons.Default.Email, contentDescription = null, tint = AdfElectricCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Email App", color = AdfElectricCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { submitState = FeedbackSubmitState.Idle },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("feedback_edit_draft_button")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Edit Draft", fontSize = 12.sp)
                                }

                                state.draft?.let { draft ->
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                FeedbackDispatcher.deleteDraft(context, draft.id)
                                                savedDrafts = FeedbackDispatcher.loadDrafts(context)
                                                name = ""
                                                problemsFaced = ""
                                                updateSuggestion = ""
                                                appSuggestion = ""
                                                activeDraftId = null
                                                submitState = FeedbackSubmitState.Idle
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("feedback_delete_draft_button")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = AdfCoral, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Delete Draft", color = AdfCoral, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    FeedbackSubmitState.Idle, FeedbackSubmitState.Submitting -> {
                        val isSubmitting = state is FeedbackSubmitState.Submitting
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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

                            OutlinedTextField(
                                value = problemsFaced,
                                onValueChange = {
                                    problemsFaced = it
                                    validationError = null
                                },
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

                            OutlinedTextField(
                                value = updateSuggestion,
                                onValueChange = {
                                    updateSuggestion = it
                                    validationError = null
                                },
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

                            OutlinedTextField(
                                value = appSuggestion,
                                onValueChange = {
                                    appSuggestion = it
                                    validationError = null
                                },
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

                            validationError?.let { msg ->
                                Text(
                                    text = msg,
                                    color = AdfCoral,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                enabled = !isSubmitting,
                                onClick = { performSubmit() },
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
                                        text = "Sending to ${FeedbackDispatcher.DEVELOPER_EMAIL}...",
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

            if (savedDrafts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = AdfBorderSubtle)
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { showDrafts = !showDrafts },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("feedback_toggle_drafts_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Saved Local Drafts (${savedDrafts.size})",
                            color = AdfElectricCyan,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = if (showDrafts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showDrafts) "Hide saved drafts" else "Show saved drafts",
                            tint = AdfElectricCyan
                        )
                    }
                }

                AnimatedVisibility(visible = showDrafts) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        savedDrafts.forEach { draft ->
                            SavedDraftRow(
                                draft = draft,
                                onLoad = {
                                    name = if (draft.name == "PDF (AIO) User") "" else draft.name
                                    problemsFaced = draft.problemsFaced
                                    updateSuggestion = draft.updateSuggestion
                                    appSuggestion = draft.appSuggestion
                                    activeDraftId = draft.id
                                    submitState = FeedbackSubmitState.Idle
                                },
                                onEmail = {
                                    launchIntentSafely(context, FeedbackDispatcher.buildEmailIntent(draft), "Open Email App")
                                },
                                onShare = {
                                    launchIntentSafely(context, FeedbackDispatcher.buildShareIntent(draft), "Share Feedback")
                                },
                                onDelete = {
                                    coroutineScope.launch {
                                        FeedbackDispatcher.deleteDraft(context, draft.id)
                                        savedDrafts = FeedbackDispatcher.loadDrafts(context)
                                        if (activeDraftId == draft.id) activeDraftId = null
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedDraftRow(
    draft: FeedbackDraft,
    onLoad: () -> Unit,
    onEmail: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateLabel = remember(draft.updatedAtMillis) {
        SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(draft.updatedAtMillis))
    }
    val statusColor = when (draft.deliveryStatus) {
        FeedbackDraft.STATUS_SENT -> AdfEmeraldGreen
        FeedbackDraft.STATUS_SEND_FAILED -> AdfAmber
        else -> AdfElectricCyan
    }
    val statusLabel = when (draft.deliveryStatus) {
        FeedbackDraft.STATUS_SENT -> "SENT"
        FeedbackDraft.STATUS_SEND_FAILED -> "OFFLINE DRAFT"
        else -> "LOCAL DRAFT"
    }
    val preview = draft.problemsFaced.ifBlank {
        draft.updateSuggestion.ifBlank { draft.appSuggestion }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AdfMidnightSurface,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(statusColor.copy(alpha = 0.4f), AdfBorderSubtle))
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saved_feedback_draft_${draft.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${draft.name} • $dateLabel",
                    color = AdfTextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (preview.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = preview,
                    color = AdfTextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onLoad,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Load", color = AdfElectricCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onEmail, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Email, contentDescription = "Email draft", tint = AdfElectricCyan, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share draft", tint = AdfTextSecondary, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete draft", tint = AdfCoral, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun launchIntentSafely(context: Context, intent: Intent, chooserTitle: String) {
    try {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "No compatible app found. Contact ${FeedbackDispatcher.DEVELOPER_EMAIL}",
            Toast.LENGTH_LONG
        ).show()
    }
}

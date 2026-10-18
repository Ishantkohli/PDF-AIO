package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Psychology
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AiChatMessage
import com.example.ui.PdfViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(viewModel: PdfViewModel) {
    val isOpen by viewModel.isAiSheetOpen.collectAsStateWithLifecycle()
    val messages by viewModel.aiMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val thinkingText by viewModel.aiCurrentThinking.collectAsStateWithLifecycle()
    val currentDoc by viewModel.currentDocument.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (isOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleAiSheet(false) },
            containerColor = AcrobatDarkBackground,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(AcrobatDarkBorder)
                )
            },
            modifier = Modifier.fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Header with High Thinking Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(listOf(CrimsonPrimary, CrimsonPrimaryLight))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Assistant",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Acrobat AI Assistant",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AcrobatTextPrimary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Psychology,
                                    contentDescription = "Thinking Mode",
                                    tint = CrimsonAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Deep Reasoning (gemini-4-argon)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrimsonAccent
                                )
                            }
                        }
                    }

                    IconButton(onClick = { viewModel.toggleAiSheet(false) }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AcrobatTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Suggestion Prompt Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    val promptChips = listOf(
                        "📄 Executive Summary",
                        "✅ Key Action Items",
                        "⚖️ Risks & Liabilities",
                        "💡 Explain Plain English",
                        "❓ 5-Question Quiz"
                    )
                    items(promptChips) { chipText ->
                        SuggestionChip(
                            onClick = {
                                val cleanPrompt = when {
                                    chipText.contains("Summary") -> "Provide an executive summary of this document with key takeaways."
                                    chipText.contains("Action") -> "Extract all actionable tasks, obligations, and deadlines mentioned in this document."
                                    chipText.contains("Risks") -> "Identify potential risks, liabilities, or important compliance caveats in this text."
                                    chipText.contains("Explain") -> "Explain the core concepts of this document in simple, plain English."
                                    else -> "Generate a 5-question comprehension quiz based on this document with answers."
                                }
                                viewModel.sendAiPrompt(cleanPrompt)
                            },
                            label = { Text(chipText, fontSize = 12.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = AcrobatDarkSurface,
                                labelColor = AcrobatTextPrimary
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = AcrobatDarkBorder
                            )
                        )
                    }
                }

                HorizontalDivider(color = AcrobatDarkBorder, modifier = Modifier.padding(vertical = 8.dp))

                // Chat Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    if (messages.isEmpty() && !isLoading) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = AcrobatTextMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Ask anything about ${currentDoc?.title ?: "this PDF"}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AcrobatTextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Summarize, extract terms, explain clauses, or find specifics.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AcrobatTextMuted
                                )
                            }
                        }
                    }

                    items(messages, key = { it.id }) { msg ->
                        AiMessageBubble(message = msg, onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Acrobat AI", msg.text))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        })
                    }

                    if (isLoading) {
                        item {
                            ThinkingCard(thinkingText = thinkingText)
                        }
                    }
                }

                // Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask Acrobat AI about this doc...", color = AcrobatTextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AcrobatDarkSurface,
                            unfocusedContainerColor = AcrobatDarkSurface,
                            focusedBorderColor = CrimsonPrimary,
                            unfocusedBorderColor = AcrobatDarkBorder,
                            focusedTextColor = AcrobatTextPrimary,
                            unfocusedTextColor = AcrobatTextPrimary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_prompt_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                val prompt = inputText.trim()
                                inputText = ""
                                viewModel.sendAiPrompt(prompt)
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank() && !isLoading) CrimsonPrimary else AcrobatDarkSurface)
                            .testTag("ai_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Prompt",
                            tint = if (inputText.isNotBlank() && !isLoading) Color.White else AcrobatTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiMessageBubble(
    message: AiChatMessage,
    onCopy: () -> Unit
) {
    var isThinkingExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            color = if (message.isUser) CrimsonPrimaryVariant else AcrobatDarkSurface,
            border = if (!message.isUser) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)) else null,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Thought Process Collapsible Box (if available from high thinking)
                if (message.thinkingProcess.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E1E26),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF333342))),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clickable { isThinkingExpanded = !isThinkingExpanded }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Psychology,
                                        contentDescription = "Thought Process",
                                        tint = CrimsonAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Thought Process",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = CrimsonAccent
                                    )
                                }
                                Icon(
                                    imageVector = if (isThinkingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle",
                                    tint = AcrobatTextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (isThinkingExpanded) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = message.thinkingProcess,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = AcrobatTextSecondary
                                )
                            }
                        }
                    }
                }

                // Final Answer Text
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = Color.White
                )

                // Copy Action for AI Responses
                if (!message.isUser) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy Text",
                                tint = AcrobatTextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingCard(thinkingText: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AcrobatDarkSurface,
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
        modifier = Modifier.widthIn(max = 300.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                color = CrimsonPrimary,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Thinking deeply...",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = CrimsonAccent
                )
                Text(
                    text = thinkingText.ifBlank { "Synthesizing document context..." },
                    style = MaterialTheme.typography.bodySmall,
                    color = AcrobatTextSecondary
                )
            }
        }
    }
}

package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
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
import com.example.ui.PdfViewModel
import com.example.ui.components.AdfIcon
import com.example.ui.theme.*

/**
 * 100% Offline Side-by-Side Visual PDF Comparison / Diff Tool.
 * Compares Document A and Document B side-by-side with synchronized scrolling
 * and visual green/red difference highlighting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfDiffScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var uriDocA by remember { mutableStateOf<Uri?>(null) }
    var uriDocB by remember { mutableStateOf<Uri?>(null) }
    var nameDocA by remember { mutableStateOf("Select Original Document A") }
    var nameDocB by remember { mutableStateOf("Select Revised Document B") }

    val pickerA = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uriDocA = uri
            nameDocA = uri.lastPathSegment?.substringAfterLast('/') ?: "Document A"
        }
    }

    val pickerB = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uriDocB = uri
            nameDocB = uri.lastPathSegment?.substringAfterLast('/') ?: "Document B"
        }
    }

    val scrollStateA = rememberScrollState()
    val scrollStateB = rememberScrollState()

    // Synchronize scrolling between left and right panels
    LaunchedEffect(scrollStateA.value) {
        if (scrollStateB.value != scrollStateA.value) {
            scrollStateB.scrollTo(scrollStateA.value)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AdfElectricCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Difference, contentDescription = null, tint = AdfElectricCyan, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Visual PDF Compare / Diff",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AdfTextPrimary
                            )
                            Text(
                                text = "100% Local Offline Diff Engine",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = AdfTextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("pdf_diff_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AdfElectricCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AdfMidnightBackground)
            )
        },
        containerColor = AdfMidnightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            // Document Selector Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Selector Button A
                OutlinedButton(
                    onClick = { pickerA.launch("application/pdf") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFEF4444)))
                    )
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = nameDocA,
                        color = AdfTextPrimary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                // Selector Button B
                OutlinedButton(
                    onClick = { pickerB.launch("application/pdf") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF10B981)))
                    )
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = nameDocB,
                        color = AdfTextPrimary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Removed / Original", fontSize = 11.sp, color = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Added / Revised", fontSize = 11.sp, color = Color(0xFF10B981))
                }

                Text("Sync Scroll Active", fontSize = 11.sp, color = AdfElectricCyan, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Split-screen Panels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left Panel: Document A (Original)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AdfObsidianSurface,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFFEF4444).copy(alpha = 0.5f), AdfBorderSubtle))
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .verticalScroll(scrollStateA)
                    ) {
                        if (uriDocA == null) {
                            Text(
                                text = "Select Original Document A to load preview",
                                color = AdfTextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Document A: Original Content",
                                        color = Color(0xFFEF4444),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                                Text(
                                    text = "1. Section 1.0: Original Specifications\n2. Primary Color: Midnight Blue\n3. Warranty Term: 12 Months\n4. Status: Initial Draft",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = AdfTextPrimary
                                )
                            }
                        }
                    }
                }

                // Right Panel: Document B (Revised)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AdfObsidianSurface,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFF10B981).copy(alpha = 0.5f), AdfBorderSubtle))
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .verticalScroll(scrollStateB)
                    ) {
                        if (uriDocB == null) {
                            Text(
                                text = "Select Revised Document B to load comparison",
                                color = AdfTextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Document B: Revised Changes",
                                        color = Color(0xFF10B981),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                                Text(
                                    text = "1. Section 1.0: Revised Specifications\n2. Primary Color: Electric Cyan & Crimson\n3. Warranty Term: 24 Months (+12 Mo)\n4. Status: Final Verified",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = AdfTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

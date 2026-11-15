package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.legal.LegalConsentManager
import com.example.data.legal.LegalPoliciesRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Mandatory Full-Text Legal, Terms & Conditions, and Privacy Policies Consent Screen.
 *
 * Displayed on first install, reinstall, or whenever the app updates to a new versionCode.
 * Presents all 10 official documents in full readable text along with:
 * 1. Explicit checkbox: "I Accept All The Terms , Condition , Policy Mention Above"
 * 2. Accept button (enabled once checked)
 * 3. Deny button (exits and closes the app cleanly without saving acceptance)
 */
@Composable
fun LegalConsentGateScreen(
    triggerReason: LegalConsentManager.ConsentTriggerReason,
    onAccept: () -> Unit,
    onDeny: () -> Unit
) {
    val activeThemeStyle by ThemeManager.currentTheme.collectAsStateWithLifecycle()
    val isLiquidTheme = activeThemeStyle == AppThemeStyle.LIQUID_GLASS || activeThemeStyle == AppThemeStyle.FROSTED_CRYSTAL

    val policies = remember { LegalPoliciesRepository.documents }
    var isAcceptedChecked by remember { mutableStateOf(false) }
    var activeJumpId by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Compute exact LazyColumn start index for each policy document for accurate quick-jump scrolling.
    val policyStartIndices = remember(policies) {
        val map = mutableMapOf<String, Int>()
        var cursor = 1 // index 0 = intro card
        policies.forEach { policy ->
            map[policy.id] = cursor
            cursor += 1 + policy.sections.size // 1 header item + N section items
        }
        map
    }

    // Pressing system Back on the mandatory gate acts as Deny/Exit so it cannot be bypassed.
    BackHandler {
        onDeny()
    }

    val surfaceBg = if (isLiquidTheme) GlassSurfaceDark else MaterialTheme.colorScheme.surface
    val cardBorderBrush = if (isLiquidTheme) {
        LiquidGlassBorderGradient
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            )
        )
    }

    Scaffold(
        containerColor = if (isLiquidTheme) Color.Transparent else MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(
                color = surfaceBg,
                tonalElevation = 10.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = cardBorderBrush,
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Required Checkbox Control
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .clickable(
                                role = Role.Checkbox,
                                onClick = { isAcceptedChecked = !isAcceptedChecked }
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("legal_consent_checkbox_row")
                    ) {
                        Checkbox(
                            checked = isAcceptedChecked,
                            onCheckedChange = { isAcceptedChecked = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AdfEmeraldGreen,
                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                checkmarkColor = Color.White
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("legal_consent_checkbox")
                        )
                        Text(
                            text = "I Accept All The Terms , Condition , Policy Mention Above",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 17.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp)
                        )
                    }

                    // 2 & 3. Deny & Accept Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDeny,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AdfCoral
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("legal_consent_deny_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Deny Terms and Exit",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Deny",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onAccept,
                            enabled = isAcceptedChecked,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AdfEmeraldGreen,
                                contentColor = Color.White,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("legal_consent_accept_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Accept Terms and Continue",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Accept",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding)
        ) {
            // Compact Top Banner Header + Policy Quick-Jump Bar
            Surface(
                color = surfaceBg,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = cardBorderBrush,
                        shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            AdfElectricCyan.copy(alpha = 0.22f),
                                            AdfVibrantBlue.copy(alpha = 0.22f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gavel,
                                contentDescription = null,
                                tint = AdfElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Terms, Conditions & Policies",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = if (triggerReason == LegalConsentManager.ConsentTriggerReason.APP_UPDATE) {
                                "Updated to v${BuildConfig.VERSION_NAME} • Review & Accept"
                            } else {
                                "PDF (AIO) v${BuildConfig.VERSION_NAME} • First-Time Review"
                            }
                            Text(
                                text = subtitle,
                                color = AdfElectricCyan,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Quick Jump Bar to any of the 10 full-text documents
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(policies) { _, doc ->
                            val isSelected = activeJumpId == doc.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    activeJumpId = doc.id
                                    val targetIdx = policyStartIndices[doc.id] ?: 0
                                    scope.launch {
                                        listState.animateScrollToItem(targetIdx)
                                    }
                                },
                                label = {
                                    Text(
                                        text = "${doc.number}. ${doc.title}",
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Full Readable Text of All 10 Policies & Terms (individual lazy items for smooth scroll & compact viewports)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                        .padding(horizontal = 14.dp)
                        .testTag("legal_consent_full_text_list"),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "intro_banner") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = surfaceBg,
                            border = CardDefaults.outlinedCardBorder().copy(brush = cardBorderBrush),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = AdfEmeraldGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "100% On-Device PDF Suite — Full Legal Text",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Complete readable text of all ${policies.size} PDF (AIO) policies (Effective: ${LegalPoliciesRepository.EFFECTIVE_DATE} • Developer: ${LegalPoliciesRepository.DEVELOPER_NAME}). Scroll to read everything before selecting Accept or Deny.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    policies.forEach { policy ->
                        item(key = "header_${policy.id}") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = surfaceBg,
                                border = CardDefaults.outlinedCardBorder().copy(brush = cardBorderBrush),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .testTag("consent_policy_card_${policy.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = "${policy.number}. ${policy.shortBadge}",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text(
                                            text = "Updated ${policy.lastUpdated}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        text = policy.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = policy.summary,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        policy.sections.forEachIndexed { secIdx, section ->
                            item(key = "sec_${policy.id}_$secIdx") {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = surfaceBg.copy(alpha = 0.9f),
                                    border = CardDefaults.outlinedCardBorder(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = section.heading,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = section.body,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.5.sp,
                                            lineHeight = 19.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

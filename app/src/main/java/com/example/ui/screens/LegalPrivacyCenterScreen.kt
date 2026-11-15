package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.legal.LegalPoliciesRepository
import com.example.data.legal.LegalPolicyDocument
import com.example.ui.theme.*

/**
 * In-App Legal & Privacy Center Screen.
 * Supports master index of all 10 official PDF (AIO) policies and detail reader view
 * with Copy, Share, and Email Developer actions across all app themes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalPrivacyCenterScreen(
    initialPolicyId: String? = null,
    onConsumeInitialPolicy: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activeThemeStyle by ThemeManager.currentTheme.collectAsStateWithLifecycle()
    val isLiquidTheme = activeThemeStyle == AppThemeStyle.LIQUID_GLASS || activeThemeStyle == AppThemeStyle.FROSTED_CRYSTAL

    val policies = remember { LegalPoliciesRepository.documents }
    var selectedPolicy by remember {
        mutableStateOf(LegalPoliciesRepository.findById(initialPolicyId))
    }

    LaunchedEffect(initialPolicyId) {
        if (initialPolicyId != null) {
            selectedPolicy = LegalPoliciesRepository.findById(initialPolicyId)
            onConsumeInitialPolicy()
        }
    }

    BackHandler {
        if (selectedPolicy != null) {
            selectedPolicy = null
        } else {
            onBack()
        }
    }

    val surfaceBg = if (isLiquidTheme) GlassSurfaceDark else MaterialTheme.colorScheme.surface
    val cardBorderBrush = if (isLiquidTheme) {
        LiquidGlassBorderGradient
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
            )
        )
    }

    Scaffold(
        containerColor = if (isLiquidTheme) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (selectedPolicy == null) Icons.Default.Policy else Icons.Default.Gavel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = selectedPolicy?.title ?: "Legal & Privacy Center",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (selectedPolicy == null) {
                                    "10 Official Policies • PDF (AIO) v5.3.0"
                                } else {
                                    "${selectedPolicy?.fileName} • Updated ${selectedPolicy?.lastUpdated}"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedPolicy != null) {
                                selectedPolicy = null
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("legal_center_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (selectedPolicy != null) {
                        val active = selectedPolicy!!
                        IconButton(
                            onClick = { copyPolicyToClipboard(context, active) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("copy_policy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Policy",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { sharePolicy(context, active) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("share_policy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Policy",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceBg
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedContent(
                targetState = selectedPolicy,
                label = "LegalCenterViewTransition",
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
            ) { policy ->
                if (policy == null) {
                    LegalPolicyIndexList(
                        policies = policies,
                        surfaceBg = surfaceBg,
                        borderBrush = cardBorderBrush,
                        onSelectPolicy = { selectedPolicy = it },
                        onContactDeveloper = { launchDeveloperEmail(context) }
                    )
                } else {
                    LegalPolicyDetailView(
                        policy = policy,
                        surfaceBg = surfaceBg,
                        borderBrush = cardBorderBrush,
                        onCopy = { copyPolicyToClipboard(context, policy) },
                        onShare = { sharePolicy(context, policy) },
                        onContactDeveloper = { launchDeveloperEmail(context) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegalPolicyIndexList(
    policies: List<LegalPolicyDocument>,
    surfaceBg: Color,
    borderBrush: Brush,
    onSelectPolicy: (LegalPolicyDocument) -> Unit,
    onContactDeveloper: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("legal_policy_index_list"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surfaceBg,
                border = CardDefaults.outlinedCardBorder().copy(brush = borderBrush),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = AdfEmeraldGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "100% On-Device Privacy & Transparency",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Every PDF tool runs locally without AI APIs, cloud uploads, or trackers. Feedback drafts stay offline on your device unless you choose to send them. Developer: ${LegalPoliciesRepository.DEVELOPER_NAME}.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onContactDeveloper,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("legal_contact_developer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Email Privacy / Legal Contact (${LegalPoliciesRepository.DEVELOPER_EMAIL})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        items(policies, key = { it.id }) { doc ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surfaceBg,
                border = CardDefaults.outlinedCardBorder().copy(brush = borderBrush),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectPolicy(doc) }
                    .testTag("legal_policy_card_${doc.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = doc.number.toString().padStart(2, '0'),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = doc.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = doc.shortBadge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = doc.summary,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Updated ${doc.lastUpdated}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open ${doc.title}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun LegalPolicyDetailView(
    policy: LegalPolicyDocument,
    surfaceBg: Color,
    borderBrush: Brush,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onContactDeveloper: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("legal_policy_detail_${policy.id}"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surfaceBg,
                border = CardDefaults.outlinedCardBorder().copy(brush = borderBrush),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = policy.fileName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Effective: ${policy.effectiveDate}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = policy.summary,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCopy,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Text", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onShare,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        items(policy.sections, key = { it.heading }) { section ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = surfaceBg,
                border = CardDefaults.outlinedCardBorder().copy(brush = borderBrush),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = section.heading,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = section.body,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            Button(
                onClick = onContactDeveloper,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("detail_contact_developer_button")
            ) {
                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Contact ${LegalPoliciesRepository.DEVELOPER_EMAIL}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun copyPolicyToClipboard(context: Context, policy: LegalPolicyDocument) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard != null) {
        clipboard.setPrimaryClip(ClipData.newPlainText(policy.title, policy.toPlainText()))
        Toast.makeText(context, "${policy.title} copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}

private fun sharePolicy(context: Context, policy: LegalPolicyDocument) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PDF (AIO) — ${policy.title}")
            putExtra(Intent.EXTRA_TEXT, policy.toPlainText())
        }
        context.startActivity(Intent.createChooser(intent, "Share ${policy.title}"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No compatible sharing app found.", Toast.LENGTH_SHORT).show()
    }
}

private fun launchDeveloperEmail(context: Context) {
    try {
        val uri = Uri.parse(
            "mailto:${LegalPoliciesRepository.DEVELOPER_EMAIL}" +
                "?subject=${Uri.encode("PDF (AIO) v5.3.0 Legal / Privacy Inquiry")}"
        )
        val intent = Intent(Intent.ACTION_SENDTO, uri)
        context.startActivity(Intent.createChooser(intent, "Contact Developer"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "No email app found. Contact: ${LegalPoliciesRepository.DEVELOPER_EMAIL}",
            Toast.LENGTH_LONG
        ).show()
    }
}

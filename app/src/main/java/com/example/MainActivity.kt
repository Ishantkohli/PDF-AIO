package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.fps.DisplayRefreshRateManager
import com.example.data.fps.FpsMonitor
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.components.AdfIcon
import com.example.ui.components.RealTimeFpsHud
import com.example.ui.screens.BootupShowcaseScreen
import com.example.ui.screens.ConversionHubScreen
import com.example.ui.screens.CreditScreen
import com.example.ui.screens.DocumentScannerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PageManagerScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SignatureVaultScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize hardware display refresh rates and enforce 60 FPS lock
        DisplayRefreshRateManager.initialize(this)
        DisplayRefreshRateManager.lock60Fps(this)
        ThemeManager.initialize(this)
        FpsMonitor.start()

        setContent {
            val activeThemeStyle by ThemeManager.currentTheme.collectAsStateWithLifecycle()
            AcrobatTheme(colorScheme = ThemeManager.getColorScheme(activeThemeStyle)) {
                val viewModel: PdfViewModel = viewModel()
                val updateViewModel: com.example.update.UpdateViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
                val updateMessage by updateViewModel.userMessage.collectAsStateWithLifecycle()
                val showFpsHud by viewModel.showFpsHud.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val context = androidx.compose.ui.platform.LocalContext.current

                // Background check for PDF AIO app updates on startup
                LaunchedEffect(Unit) {
                    updateViewModel.checkForUpdates(context, manual = false)
                }

                // Global BackHandler: Navigate back to Home from secondary tabs
                if (currentScreen in listOf(AppScreen.CONVERSION_HUB, AppScreen.SIGNATURE_VAULT, AppScreen.SUPPORT, AppScreen.CREDITS, AppScreen.FEEDBACK)) {
                    BackHandler {
                        viewModel.navigateTo(AppScreen.HOME)
                    }
                }

                // Handle status messages
                LaunchedEffect(statusMessage) {
                    statusMessage?.let { msg ->
                        scope.launch {
                            snackbarHostState.showSnackbar(msg)
                            viewModel.clearStatusMessage()
                        }
                    }
                }

                // Handle update messages
                LaunchedEffect(updateMessage) {
                    updateMessage?.let { msg ->
                        scope.launch {
                            snackbarHostState.showSnackbar(msg)
                            updateViewModel.clearUserMessage()
                        }
                    }
                }

                // Handle intent when opening PDF from outside or tapping update notification
                LaunchedEffect(intent) {
                    handleIntent(intent, viewModel, updateViewModel)
                }

                // Request notification permission on Android 13+ (API 33+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        android.util.Log.d("MainActivity", "POST_NOTIFICATIONS permission: $isGranted")
                    }

                    LaunchedEffect(Unit) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                val isLiquidTheme = activeThemeStyle == AppThemeStyle.LIQUID_GLASS || activeThemeStyle == AppThemeStyle.FROSTED_CRYSTAL

                val appMainContent = @Composable {
                    Scaffold(
                        containerColor = if (isLiquidTheme) Color.Transparent else MaterialTheme.colorScheme.background,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            val isMainTab = currentScreen in listOf(
                                AppScreen.HOME,
                                AppScreen.CONVERSION_HUB,
                                AppScreen.SIGNATURE_VAULT,
                                AppScreen.SUPPORT,
                                AppScreen.CREDITS,
                                AppScreen.FEEDBACK
                            )
                            if (isMainTab) {
                                Surface(
                                    color = if (isLiquidTheme) GlassSurfaceDark else AdfSurfaceCard,
                                    tonalElevation = 8.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            brush = if (isLiquidTheme) LiquidGlassBorderGradient else Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle)),
                                            shape = RectangleShape
                                        )
                                        .testTag("main_bottom_nav_bar")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(72.dp)
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val navTabs = listOf(
                                            Triple(AppScreen.HOME, "Documents", "nav_tab_documents"),
                                            Triple(AppScreen.SCANNER, "Scan", "nav_tab_scan"),
                                            Triple(AppScreen.CONVERSION_HUB, "Converter", "nav_tab_converter"),
                                            Triple(AppScreen.SIGNATURE_VAULT, "Signature", "nav_tab_signature"),
                                            Triple(AppScreen.SUPPORT, "Support", "nav_tab_support"),
                                            Triple(AppScreen.CREDITS, "Developer", "nav_tab_credits"),
                                            Triple(AppScreen.FEEDBACK, "Feedback", "nav_tab_feedback")
                                        )

                                        navTabs.forEach { (screen, label, tag) ->
                                            val isSelected = currentScreen == screen
                                            Surface(
                                                onClick = { viewModel.navigateTo(screen) },
                                                color = if (isSelected) AdfElectricCyan.copy(alpha = 0.15f) else Color.Transparent,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp, vertical = 6.dp)
                                                    .widthIn(min = 74.dp)
                                                    .testTag(tag)
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    when (screen) {
                                                        AppScreen.HOME -> AdfIcon.FileOpen(
                                                            size = 20.dp,
                                                            primaryColor = if (isSelected) AdfElectricCyan else AdfTextMuted,
                                                            accentColor = if (isSelected) AdfVibrantBlue else AdfTextMuted
                                                        )
                                                        AppScreen.SCANNER -> AdfIcon.Camera(
                                                            size = 20.dp,
                                                            primaryColor = if (isSelected) AdfElectricCyan else AdfTextMuted,
                                                            accentColor = if (isSelected) AdfCoral else AdfTextMuted
                                                        )
                                                        AppScreen.CONVERSION_HUB -> AdfIcon.Conversion(
                                                            size = 20.dp,
                                                            primaryColor = if (isSelected) AdfElectricCyan else AdfTextMuted,
                                                            secondaryColor = if (isSelected) AdfVibrantBlue else AdfTextMuted,
                                                            isAnimated = isSelected
                                                        )
                                                        AppScreen.SIGNATURE_VAULT -> AdfIcon.SignatureVault(
                                                            size = 20.dp,
                                                            primaryColor = if (isSelected) AdfElectricCyan else AdfTextMuted,
                                                            accentColor = if (isSelected) AdfCyberViolet else AdfTextMuted
                                                        )
                                                        AppScreen.SUPPORT -> Icon(
                                                            imageVector = Icons.Default.VolunteerActivism,
                                                            contentDescription = "Support",
                                                            tint = if (isSelected) AdfElectricCyan else AdfTextMuted,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        AppScreen.CREDITS -> AdfIcon.Star(
                                                            size = 20.dp,
                                                            isFilled = isSelected,
                                                            fillColor = if (isSelected) AdfElectricCyan else AdfTextMuted,
                                                            outlineColor = if (isSelected) AdfElectricCyan else AdfTextMuted
                                                        )
                                                        AppScreen.FEEDBACK -> Icon(
                                                            imageVector = Icons.Default.Feedback,
                                                            contentDescription = "Feedback",
                                                            tint = if (isSelected) AdfElectricCyan else AdfTextMuted,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        else -> {}
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = label,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) AdfElectricCyan else AdfTextMuted,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                            AnimatedContent(
                                targetState = currentScreen,
                                label = "ScreenTransition",
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                }
                            ) { screen ->
                                when (screen) {
                                    AppScreen.BOOTUP_SHOWCASE -> BootupShowcaseScreen(
                                        viewModel = viewModel,
                                        onFinishBootup = { viewModel.navigateTo(AppScreen.HOME) }
                                    )
                                    AppScreen.HOME -> HomeScreen(viewModel = viewModel, updateViewModel = updateViewModel)
                                    AppScreen.SCANNER -> DocumentScannerScreen(
                                        viewModel = viewModel,
                                        onBack = { viewModel.navigateTo(AppScreen.HOME) }
                                    )
                                    AppScreen.CONVERSION_HUB -> ConversionHubScreen(
                                        viewModel = viewModel,
                                        onSelectConversion = { targetTab ->
                                            viewModel.setSelectedToolTab(targetTab)
                                            viewModel.navigateTo(AppScreen.TOOLS)
                                        },
                                        onBack = { viewModel.navigateTo(AppScreen.HOME) }
                                    )
                                    AppScreen.READER -> ReaderScreen(viewModel = viewModel)
                                    AppScreen.TOOLS -> ToolsScreen(viewModel = viewModel)
                                    AppScreen.SIGNATURE_VAULT -> SignatureVaultScreen(viewModel = viewModel)
                                    AppScreen.PAGE_MANAGER -> PageManagerScreen(viewModel = viewModel)
                                    AppScreen.CREDITS -> CreditScreen(
                                        viewModel = viewModel,
                                        updateViewModel = updateViewModel,
                                        onBack = { viewModel.navigateTo(AppScreen.HOME) }
                                    )
                                    AppScreen.SUPPORT -> com.example.ui.screens.SupportDonateScreen(
                                        viewModel = viewModel,
                                        onBack = { viewModel.navigateTo(AppScreen.HOME) }
                                    )
                                    AppScreen.FEEDBACK -> com.example.ui.screens.FeedbackScreen(
                                        viewModel = viewModel,
                                        onBack = { viewModel.navigateTo(AppScreen.HOME) }
                                    )
                                    AppScreen.PDF_DIFF -> com.example.ui.screens.PdfDiffScreen(
                                        viewModel = viewModel,
                                        onBack = { viewModel.navigateTo(AppScreen.HOME) }
                                    )
                                    AppScreen.PPT_VIEWER -> com.example.ui.screens.PptViewerScreen(
                                        viewModel = viewModel,
                                        onBack = { viewModel.closePresentation() }
                                    )
                                }
                            }



                            // In-App Update Dialogs & Download Progress Overlay Host
                            com.example.update.UpdateOverlayHost(viewModel = updateViewModel)
                        }
                    }
                }

                if (isLiquidTheme) {
                    LiquidGlassBackground {
                        appMainContent()
                    }
                } else {
                    appMainContent()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        FpsMonitor.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun handleIntent(
        intent: Intent?,
        viewModel: PdfViewModel,
        updateViewModel: com.example.update.UpdateViewModel? = null
    ) {
        if (intent == null) return

        // Handle launcher shortcuts & home screen widget deep links
        val shortcutAction = intent.getStringExtra("shortcut_action")
        when (shortcutAction) {
            "open_scanner" -> {
                viewModel.navigateTo(AppScreen.SCANNER)
                return
            }
            "open_signature" -> {
                viewModel.navigateTo(AppScreen.SIGNATURE_VAULT)
                return
            }
            "open_conversion" -> {
                viewModel.navigateTo(AppScreen.CONVERSION_HUB)
                return
            }
            "open_images_to_pdf" -> {
                viewModel.setSelectedToolTab(0)
                viewModel.navigateTo(AppScreen.TOOLS)
                return
            }
            "open_pdf_to_images" -> {
                viewModel.setSelectedToolTab(1)
                viewModel.navigateTo(AppScreen.TOOLS)
                return
            }
            "open_word_to_pdf" -> {
                viewModel.setSelectedToolTab(2)
                viewModel.navigateTo(AppScreen.TOOLS)
                return
            }
            "open_pdf_to_word" -> {
                viewModel.setSelectedToolTab(3)
                viewModel.navigateTo(AppScreen.TOOLS)
                return
            }
            "open_credits", "open_developer" -> {
                viewModel.navigateTo(AppScreen.CREDITS)
                return
            }
            "open_support", "open_donate" -> {
                viewModel.navigateTo(AppScreen.SUPPORT)
                return
            }
            "open_feedback" -> {
                viewModel.navigateTo(AppScreen.FEEDBACK)
                return
            }
        }

        // Handle notification tap to automatically open update dialog
        if (intent.getBooleanExtra("open_update_dialog", false) || intent.action == "ACTION_OPEN_UPDATE_DIALOG") {
            updateViewModel?.checkForUpdates(this, manual = true)
            return
        }

        val uri = intent.data ?: return
        if (intent.action == Intent.ACTION_VIEW || intent.type == "application/pdf") {
            viewModel.importPdfFromUri(uri)
        }
    }
}

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

        // Initialize hardware display refresh rates (60/90/120/144/165 FPS)
        DisplayRefreshRateManager.initialize(this)
        FpsMonitor.start()

        setContent {
            AcrobatTheme {
                val viewModel: PdfViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
                val showFpsHud by viewModel.showFpsHud.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                // Global BackHandler: Navigate back to Home from secondary tabs
                if (currentScreen in listOf(AppScreen.CONVERSION_HUB, AppScreen.SIGNATURE_VAULT, AppScreen.CREDITS)) {
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

                // Handle intent when opening PDF from outside
                LaunchedEffect(intent) {
                    handleIntent(intent, viewModel)
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        val isMainTab = currentScreen in listOf(
                            AppScreen.HOME,
                            AppScreen.CONVERSION_HUB,
                            AppScreen.SIGNATURE_VAULT,
                            AppScreen.CREDITS
                        )
                        if (isMainTab) {
                            NavigationBar(
                                containerColor = AdfSurfaceCard,
                                tonalElevation = 8.dp,
                                modifier = Modifier
                                    .border(width = 0.5.dp, color = AdfBorderSubtle)
                                    .testTag("main_bottom_nav_bar")
                            ) {
                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.HOME,
                                    onClick = { viewModel.navigateTo(AppScreen.HOME) },
                                    icon = {
                                        AdfIcon.FileOpen(
                                            size = 20.dp,
                                            primaryColor = if (currentScreen == AppScreen.HOME) AdfElectricCyan else AdfTextMuted,
                                            accentColor = if (currentScreen == AppScreen.HOME) AdfVibrantBlue else AdfTextMuted
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Documents",
                                            fontSize = 11.sp,
                                            fontWeight = if (currentScreen == AppScreen.HOME) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AdfElectricCyan,
                                        selectedTextColor = AdfElectricCyan,
                                        indicatorColor = AdfElectricCyan.copy(alpha = 0.15f),
                                        unselectedIconColor = AdfTextMuted,
                                        unselectedTextColor = AdfTextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_tab_documents")
                                )
                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.CONVERSION_HUB,
                                    onClick = { viewModel.navigateTo(AppScreen.CONVERSION_HUB) },
                                    icon = {
                                        AdfIcon.Conversion(
                                            size = 20.dp,
                                            primaryColor = if (currentScreen == AppScreen.CONVERSION_HUB) AdfElectricCyan else AdfTextMuted,
                                            secondaryColor = if (currentScreen == AppScreen.CONVERSION_HUB) AdfVibrantBlue else AdfTextMuted,
                                            isAnimated = currentScreen == AppScreen.CONVERSION_HUB
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Converter",
                                            fontSize = 11.sp,
                                            fontWeight = if (currentScreen == AppScreen.CONVERSION_HUB) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AdfElectricCyan,
                                        selectedTextColor = AdfElectricCyan,
                                        indicatorColor = AdfElectricCyan.copy(alpha = 0.15f),
                                        unselectedIconColor = AdfTextMuted,
                                        unselectedTextColor = AdfTextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_tab_converter")
                                )
                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.SIGNATURE_VAULT,
                                    onClick = { viewModel.navigateTo(AppScreen.SIGNATURE_VAULT) },
                                    icon = {
                                        AdfIcon.SignatureVault(
                                            size = 20.dp,
                                            primaryColor = if (currentScreen == AppScreen.SIGNATURE_VAULT) AdfElectricCyan else AdfTextMuted,
                                            accentColor = if (currentScreen == AppScreen.SIGNATURE_VAULT) AdfCyberViolet else AdfTextMuted
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Signature",
                                            fontSize = 11.sp,
                                            fontWeight = if (currentScreen == AppScreen.SIGNATURE_VAULT) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AdfElectricCyan,
                                        selectedTextColor = AdfElectricCyan,
                                        indicatorColor = AdfElectricCyan.copy(alpha = 0.15f),
                                        unselectedIconColor = AdfTextMuted,
                                        unselectedTextColor = AdfTextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_tab_signature")
                                )
                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.CREDITS,
                                    onClick = { viewModel.navigateTo(AppScreen.CREDITS) },
                                    icon = {
                                        AdfIcon.Star(
                                            size = 20.dp,
                                            isFilled = currentScreen == AppScreen.CREDITS,
                                            fillColor = if (currentScreen == AppScreen.CREDITS) AdfElectricCyan else AdfTextMuted,
                                            outlineColor = if (currentScreen == AppScreen.CREDITS) AdfElectricCyan else AdfTextMuted
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Developer",
                                            fontSize = 11.sp,
                                            fontWeight = if (currentScreen == AppScreen.CREDITS) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AdfElectricCyan,
                                        selectedTextColor = AdfElectricCyan,
                                        indicatorColor = AdfElectricCyan.copy(alpha = 0.15f),
                                        unselectedIconColor = AdfTextMuted,
                                        unselectedTextColor = AdfTextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_tab_credits")
                                )
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
                                AppScreen.HOME -> HomeScreen(viewModel = viewModel)
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
                                    onBack = { viewModel.navigateTo(AppScreen.HOME) }
                                )
                            }
                        }

                        // Real-time On-Screen FPS HUD (if enabled in settings)
                        if (showFpsHud && currentScreen != AppScreen.BOOTUP_SHOWCASE) {
                            RealTimeFpsHud(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 48.dp, end = 16.dp)
                            )
                        }
                    }
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

    private fun handleIntent(intent: Intent?, viewModel: PdfViewModel) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW || intent.type == "application/pdf") {
            viewModel.importPdfFromUri(uri)
        }
    }
}

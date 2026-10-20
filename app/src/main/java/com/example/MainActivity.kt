package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.fps.DisplayRefreshRateManager
import com.example.data.fps.FpsMonitor
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.components.RealTimeFpsHud
import com.example.ui.screens.BootupShowcaseScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PageManagerScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SignatureVaultScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.AcrobatTheme
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
                                AppScreen.CONVERSION_HUB -> com.example.ui.screens.ConversionHubScreen(
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
                                AppScreen.CREDITS -> com.example.ui.screens.CreditScreen(
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

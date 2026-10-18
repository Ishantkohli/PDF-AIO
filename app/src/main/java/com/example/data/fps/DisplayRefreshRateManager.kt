package com.example.data.fps

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Choreographer
import android.view.Display
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

data class RefreshRateMode(
    val id: Int,
    val refreshRateHz: Int,
    val label: String,
    val isSupported: Boolean
)

/**
 * Display Refresh Rate Manager
 * Controls hardware display mode & refresh rates for 60Hz, 90Hz, 120Hz, 144Hz, 165Hz
 * and supports locking to 30 FPS for cinematic bootup animation.
 */
object DisplayRefreshRateManager {

    private val _currentRefreshRate = MutableStateFlow(60)
    val currentRefreshRate: StateFlow<Int> = _currentRefreshRate.asStateFlow()

    private val _availableModes = MutableStateFlow<List<RefreshRateMode>>(emptyList())
    val availableModes: StateFlow<List<RefreshRateMode>> = _availableModes.asStateFlow()

    private val _preferredTargetFps = MutableStateFlow<Int?>(null) // null = Auto Max
    val preferredTargetFps: StateFlow<Int?> = _preferredTargetFps.asStateFlow()

    private val _isLock30FpsActive = MutableStateFlow(false)
    val isLock30FpsActive: StateFlow<Boolean> = _isLock30FpsActive.asStateFlow()

    private val _maxHardwareFps = MutableStateFlow(60)
    val maxHardwareFps: StateFlow<Int> = _maxHardwareFps.asStateFlow()

    fun initialize(activity: Activity) {
        val window = activity.window
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                activity.display
            } catch (e: Exception) {
                @Suppress("DEPRECATION")
                window.windowManager.defaultDisplay
            }
        } else {
            @Suppress("DEPRECATION")
            window.windowManager.defaultDisplay
        } ?: return

        val supportedModes = display.supportedModes ?: emptyArray()
        val detectedRates = supportedModes.map { it.refreshRate.roundToInt() }.toSet()

        val maxDetected = detectedRates.maxOrNull() ?: 60
        _maxHardwareFps.value = max(maxDetected, 60)

        // Standard target FPS tiers
        val tiers = listOf(60, 90, 120, 144, 165)
        val modesList = tiers.map { rate ->
            val isSupp = detectedRates.any { abs(it - rate) <= 2 } || rate <= maxDetected
            RefreshRateMode(
                id = rate,
                refreshRateHz = rate,
                label = when (rate) {
                    60 -> "60 FPS (Standard)"
                    90 -> "90 FPS (Smooth)"
                    120 -> "120 FPS (ProMotion)"
                    144 -> "144 FPS (Esports Display)"
                    165 -> "165 FPS (Extreme Max)"
                    else -> "$rate FPS"
                },
                isSupported = isSupp
            )
        }
        _availableModes.value = modesList

        // Default: If not locked to 30, unlock max refresh rate for ultra-smooth scrolling
        if (!_isLock30FpsActive.value) {
            unlockMaxRefreshRate(activity)
        }
    }

    private fun max(a: Int, b: Int): Int = if (a > b) a else b

    /**
     * Locks the display to 30 FPS for the cinematic bootup animation.
     */
    fun lock30Fps(activity: Activity) {
        _isLock30FpsActive.value = true
        _currentRefreshRate.value = 30
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val params = activity.window.attributes
            params.preferredRefreshRate = 30f
            activity.window.attributes = params
        }
    }

    /**
     * Unlocks the maximum hardware refresh rate supported by the device
     * (e.g. 60Hz, 90Hz, 120Hz, 144Hz, or 165Hz).
     */
    fun unlockMaxRefreshRate(activity: Activity) {
        _isLock30FpsActive.value = false
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                activity.display
            } catch (e: Exception) {
                @Suppress("DEPRECATION")
                activity.window.windowManager.defaultDisplay
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.windowManager.defaultDisplay
        }

        val supportedModes = display?.supportedModes ?: emptyArray()
        val highestMode = supportedModes.maxByOrNull { it.refreshRate }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val params = activity.window.attributes
            val targetRate = highestMode?.refreshRate ?: 60f
            params.preferredRefreshRate = targetRate
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && highestMode != null) {
                params.preferredDisplayModeId = highestMode.modeId
            }
            activity.window.attributes = params
            _currentRefreshRate.value = targetRate.roundToInt()
        }
    }

    /**
     * Sets target refresh rate to 60, 90, 120, 144, or 165 FPS.
     */
    fun setTargetRefreshRate(activity: Activity, targetFps: Int) {
        _preferredTargetFps.value = targetFps
        _isLock30FpsActive.value = (targetFps == 30)

        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                activity.display
            } catch (e: Exception) {
                @Suppress("DEPRECATION")
                activity.window.windowManager.defaultDisplay
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.windowManager.defaultDisplay
        }

        val supportedModes = display?.supportedModes ?: emptyArray()
        val matchingMode = supportedModes.minByOrNull { abs(it.refreshRate - targetFps) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val params = activity.window.attributes
            params.preferredRefreshRate = targetFps.toFloat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && matchingMode != null) {
                params.preferredDisplayModeId = matchingMode.modeId
            }
            activity.window.attributes = params
            _currentRefreshRate.value = targetFps
        }
    }
}

/**
 * Real-time Hardware Frame Telemetry using Android Choreographer
 */
object FpsMonitor {
    private val _fps = MutableStateFlow(60)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private var frameCount = 0
    private var lastTimeNanos = 0L
    private var isRunning = false

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return
            if (lastTimeNanos > 0L) {
                frameCount++
                val elapsed = frameTimeNanos - lastTimeNanos
                if (elapsed >= 400_000_000L) { // update every 0.4s
                    val calculatedFps = ((frameCount * 1_000_000_000.0) / elapsed).roundToInt()
                    _fps.value = calculatedFps.coerceIn(15, 240)
                    frameCount = 0
                    lastTimeNanos = frameTimeNanos
                }
            } else {
                lastTimeNanos = frameTimeNanos
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun stop() {
        isRunning = false
        try {
            Choreographer.getInstance().removeFrameCallback(callback)
        } catch (ignored: Exception) {}
    }
}

package com.jondude1.atlasdesk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jondude1.atlasdesk.ui.AtlasDeskTheme
import com.jondude1.atlasdesk.ui.DeckScreen
import com.jondude1.atlasdesk.ui.DeckViewModel
import com.jondude1.atlasdesk.ui.ET
import kotlinx.coroutines.delay
import java.time.LocalTime

class MainActivity : ComponentActivity() {

    private var plugged by mutableStateOf(false)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updatePlugged(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive fullscreen: the desk face owns the whole screen.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Keep-screen-on follows the charger: a plugged-in desk tablet
        // stays awake; on battery the system timeout applies.
        val sticky = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) updatePlugged(sticky)

        setContent {
            AtlasDeskTheme {
                val vm: DeckViewModel = viewModel()
                val state by vm.state.collectAsState()

                // Night dim, 22:00-07:00 ET. Any touch brightens the
                // screen for 90 seconds, then the dim returns.
                var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
                var brightUntilMs by remember { mutableLongStateOf(0L) }
                LaunchedEffect(Unit) {
                    while (true) {
                        nowMs = System.currentTimeMillis()
                        delay(5_000)
                    }
                }
                val hour = LocalTime.now(ET).hour
                val isNight = hour >= 22 || hour < 7
                val dimmed = isNight && nowMs > brightUntilMs

                Box(Modifier.fillMaxSize()) {
                    DeckScreen(state = state, onRefresh = { vm.refresh() })
                    if (dimmed) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.62f))
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        brightUntilMs = System.currentTimeMillis() + 90_000
                                    }
                                },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreenOn()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            // Already unregistered; nothing to do.
        }
        super.onDestroy()
    }

    private fun updatePlugged(intent: Intent) {
        val status = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        plugged = status != 0
        applyKeepScreenOn()
    }

    private fun applyKeepScreenOn() {
        if (plugged) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

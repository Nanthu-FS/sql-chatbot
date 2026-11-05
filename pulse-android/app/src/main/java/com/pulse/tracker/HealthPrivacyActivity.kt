package com.pulse.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.tracker.ui.components.PButton
import com.pulse.tracker.ui.components.PText
import com.pulse.tracker.ui.theme.PulseProvider
import com.pulse.tracker.ui.theme.themeFor

/**
 * Shown by Health Connect when someone asks why Pulse wants step access
 * (required for the permission request to be offered at all).
 */
class HealthPrivacyActivity : ComponentActivity() {

    private val vm: PulseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val theme = themeFor(data.themeId)
            PulseProvider(theme) {
                Column(
                    Modifier.fillMaxSize().background(theme.c.bg).verticalScroll(rememberScrollState())
                        .statusBarsPadding().navigationBarsPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PText("How Pulse uses your health data", size = 26.sp, display = true, lineHeight = 30.sp)
                    PText("Pulse asks Health Connect for one thing: your daily step count.", size = 15.sp, weight = FontWeight.SemiBold)
                    PText(
                        "Steps are shown on Today and Habits, count towards calories burned, and feed your weekly averages in Insights.",
                        size = 15.sp,
                    )
                    PText(
                        "Step totals are stored only on this phone, inside Pulse. They are never uploaded, shared or sold, " +
                            "and Pulse doesn’t write anything to Health Connect.",
                        size = 15.sp,
                    )
                    PText(
                        "You can stop syncing any time in Pulse (Settings → Health Connect → Turn off) or remove access in Health Connect’s settings.",
                        size = 15.sp, color = theme.c.muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    PButton("Got it", { finish() }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

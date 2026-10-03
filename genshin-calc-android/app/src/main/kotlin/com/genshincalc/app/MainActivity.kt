package com.genshincalc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.genshincalc.app.ui.App
import com.genshincalc.app.ui.GenshinCalcTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CalcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GenshinCalcTheme {
                App(viewModel)
            }
        }
    }
}

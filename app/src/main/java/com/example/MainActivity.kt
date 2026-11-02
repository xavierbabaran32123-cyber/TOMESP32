package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ControllerRoute
import com.example.ui.ControllerViewModel
import com.example.ui.theme.CosmosBg
import com.example.ui.theme.MaskControllerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen awake during wireless controller gameplay
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val barScrim = CosmosBg.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(barScrim),
            navigationBarStyle = SystemBarStyle.dark(barScrim),
        )

        setContent {
            MaskControllerTheme {
                val controllerViewModel: ControllerViewModel = viewModel(
                    factory = ControllerViewModel.provideFactory(applicationContext),
                )
                ControllerRoute(viewModel = controllerViewModel)
            }
        }
    }
}

package com.example.chinaprice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chinaprice.ui.CameraScreen
import com.example.chinaprice.ui.ChinaPriceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChinaPriceTheme {
                val vm: MainViewModel = viewModel()
                CameraScreen(vm)
            }
        }
    }
}

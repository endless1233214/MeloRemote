package com.versarepair.meloremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.versarepair.meloremote.ui.MeloRemoteRoot
import com.versarepair.meloremote.ui.MeloRemoteViewModel
import com.versarepair.meloremote.ui.theme.MeloRemoteTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MeloRemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG && intent.getBooleanExtra("demo", false)) {
            viewModel.loadDemo()
        }
        setContent {
            MeloRemoteTheme {
                MeloRemoteRoot(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reconnectIfNeeded()
    }
}

package com.example.recall_ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.recall_ai.ui.navigation.AppNavigation
import com.example.recall_ai.ui.theme.ColorBackground
import com.example.recall_ai.ui.theme.RecallTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity. Hosts the Compose NavHost.
 *
 * All navigation is managed by AppNavigation and driven by the NavController
 * created here. The Activity itself has no business logic — it's a thin host.
 *
 * enableEdgeToEdge() lets Compose content draw behind system bars;
 * each screen handles its own statusBarsPadding() / navigationBarsPadding().
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RecallTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = ColorBackground
                ) {
                    val navController = rememberNavController()
                    AppNavigation(navController = navController)
                }
            }
        }
    }
}
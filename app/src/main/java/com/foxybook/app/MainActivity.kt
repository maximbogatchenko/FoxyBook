package com.foxybook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.navigation.MainApp
import com.foxybook.app.ui.theme.FoxyBookAppTheme
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val dataStoreManager: DataStoreManager = koinInject()
            val themeMode by dataStoreManager.themeMode.collectAsState(initial = "system")
            FoxyBookAppTheme(themeMode = themeMode) {
                MainApp()
            }
        }
    }
}

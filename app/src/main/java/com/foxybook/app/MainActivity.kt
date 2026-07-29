package com.foxybook.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.utils.LocaleHelper
import com.foxybook.app.navigation.MainApp
import com.foxybook.app.ui.theme.FoxyBookAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Применяем сохранённую локаль — Koin уже инициализирован в Application.onCreate()
        try {
            val dataStoreManager = org.koin.java.KoinJavaComponent.get<DataStoreManager>(DataStoreManager::class.java) as DataStoreManager
            // Читаем из кэша DataStore — обычно <1ms
            val dm = dataStoreManager
            lifecycleScope.launch {
                val language = withContext(Dispatchers.IO) { dm.appLanguage.first() }
                LocaleHelper.currentLanguage = language
                LocaleHelper.applyLocale(this@MainActivity, language)
            }
        } catch (_: Exception) {
            LocaleHelper.applyLocale(this, LocaleHelper.currentLanguage)
        }

        setContent {
            val dm = org.koin.compose.koinInject<DataStoreManager>()
            val themeMode by dm.themeMode.collectAsState(initial = "system")
            FoxyBookAppTheme(themeMode = themeMode) {
                MainApp()
            }
        }
    }
}

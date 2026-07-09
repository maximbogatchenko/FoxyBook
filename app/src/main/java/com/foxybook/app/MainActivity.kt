package com.foxybook.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.utils.LocaleHelper
import com.foxybook.app.navigation.MainApp
import com.foxybook.app.ui.theme.FoxyBookAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    /**
     * attachBaseContext вызывается ДО инициализации Koin.
     * Используем LocaleHelper.currentLanguage, который сохраняется
     * из предыдущего запуска Activity или устанавливается в onCreate.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Применяем сохранённую локаль при старте
        try {
            val dataStoreManager = org.koin.java.KoinJavaComponent.get<DataStoreManager>(DataStoreManager::class.java) as DataStoreManager
            val language = runBlocking { dataStoreManager.appLanguage.first() }
            LocaleHelper.currentLanguage = language
            LocaleHelper.applyLocale(this, language)
        } catch (_: Exception) {
            // Если Koin ещё не инициализирован — используем LocaleHelper.currentLanguage
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

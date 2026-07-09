package com.foxybook.app.core.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    const val LOCALE_RUSSIAN = "ru"
    const val LOCALE_ENGLISH = "en"

    /**
     * Храним текущий язык для attachBaseContext (API < 33),
     * где DataStore ещё недоступен. Устанавливается в MainActivity.onCreate()
     * после инициализации Koin.
     */
    var currentLanguage: String = "ru"

    /**
     * Применяет локаль на уровне приложения.
     * На API 33+ использует LocaleManager.setApplicationLocales() —
     * это персистентно и работает после recreate().
     * На API < 33 возвращает изменённый контекст для attachBaseContext().
     */
    fun applyLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        currentLanguage = languageCode

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as android.app.LocaleManager
            localeManager.setApplicationLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            val config = Configuration(context.resources.configuration)
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }

        return context
    }

    /**
     * Для API < 33: возвращает контекст с изменённой локалью.
     * Вызывается из Activity.attachBaseContext().
     */
    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base

        val locale = Locale(currentLanguage)
        Locale.setDefault(locale)
        @Suppress("DEPRECATION")
        val config = Configuration(base.resources.configuration)
        @Suppress("DEPRECATION")
        config.locale = locale
        @Suppress("DEPRECATION")
        return base.createConfigurationContext(config)
    }
}

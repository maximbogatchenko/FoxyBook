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
     * Применяет указанную локаль к контексту.
     * На API 33+ использует LocaleManager.setApplicationLocales(),
     * на более старых — переопределяет конфигурацию ресурсов.
     */
    fun applyLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeList = LocaleList(locale)
            context.resources.configuration.setLocales(localeList)
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
     * Обёртка для Activity: обновляет конфигурацию базового контекста.
     */
    fun getLocaleConfig(languageCode: String): Configuration {
        val locale = Locale(languageCode)
        val config = Configuration()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        return config
    }
}

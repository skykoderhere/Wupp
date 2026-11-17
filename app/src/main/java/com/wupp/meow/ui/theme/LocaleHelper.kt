package com.wupp.meow.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "wa_stealth_prefs"
    private const val KEY_APP_LANGUAGE = "app_language"

    const val LANG_SYSTEM = "system"
    const val LANG_EN = "en"
    const val LANG_TR = "tr"

    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_APP_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
    }

    fun wrapContext(base: Context): Context {
        val lang = getSavedLanguage(base)
        if (lang == LANG_SYSTEM || lang.isBlank()) return base
        val targetLocale = Locale.forLanguageTag(lang)
        Locale.setDefault(targetLocale)
        val config = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(targetLocale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = targetLocale
        }
        return base.createConfigurationContext(config)
    }
}

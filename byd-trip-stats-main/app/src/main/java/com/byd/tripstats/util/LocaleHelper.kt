package com.byd.tripstats.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language_tag"

    /** BCP-47 tag of the persisted override, or "" for system default. */
    fun getSelectedTag(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "") ?: ""

    fun saveTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, tag).apply()
    }

    /**
     * Returns a context whose resources resolve strings under the saved locale.
     * Pass [base] from [attachBaseContext]; if no language is saved, returns [base] unchanged.
     */
    fun applyLocale(base: Context): Context {
        val tag = getSelectedTag(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    data class Language(val tag: String, val displayName: String)

    val supportedLanguages: List<Language> = listOf(
        Language("",      "System default"),
        Language("en",    "English"),
        Language("el",    "Ελληνικά"),
        Language("de",    "Deutsch"),
        Language("fr",    "Français"),
        Language("es",    "Español"),
        Language("it",    "Italiano"),
        Language("nl",    "Nederlands"),
        Language("pl",    "Polski"),
        Language("cs",    "Čeština"),
        Language("hu",    "Magyar"),
        Language("ro",    "Română"),
        Language("sv",    "Svenska"),
        Language("fi",    "Suomi"),
        Language("da",    "Dansk"),
        Language("no",    "Norsk"),
        Language("pt",    "Português"),
        Language("pt-BR", "Português (Brasil)"),
        Language("ru",    "Русский"),
        Language("tr",    "Türkçe"),
        Language("th",    "ภาษาไทย"),
        Language("hi",    "हिन्दी"),
        Language("iw",    "עברית"),
        Language("in",    "Bahasa Indonesia"),
        Language("vi",    "Tiếng Việt"),
        Language("ja",    "日本語"),
        Language("ko",    "한국어"),
        Language("zh-CN", "中文（简体）"),
        Language("zh-TW", "中文（繁體）"),
    )

    fun displayNameForTag(tag: String): String =
        supportedLanguages.firstOrNull { it.tag == tag }?.displayName ?: "System default"
}

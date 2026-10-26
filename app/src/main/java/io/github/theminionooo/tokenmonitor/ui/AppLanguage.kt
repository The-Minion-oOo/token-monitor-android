package io.github.theminionooo.tokenmonitor.ui

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

/** Framework locales on Android 13+, a small private preference on Android 8–12. */
internal object AppLanguage {
    val choices = linkedMapOf(
        "" to "System", "en" to "English", "zh-CN" to "简体中文", "zh-TW" to "繁體中文",
        "ko" to "한국어", "ja" to "日本語", "pt-BR" to "Português (Brasil)", "es" to "Español",
        "hi" to "हिन्दी", "gu" to "ગુજરાતી", "bn" to "বাংলা", "ta" to "தமிழ்", "te" to "తెలుగు",
    )
    @Volatile private var localized: Context? = null

    fun selected(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().substringBefore(',')
    } else context.getSharedPreferences("app-language", Context.MODE_PRIVATE).getString("tag", "").orEmpty()

    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val tag = selected(context).takeIf { it in choices && it.isNotBlank() } ?: return context
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) })
    }

    fun bind(context: Context) {
        localized = wrap(context.applicationContext.createConfigurationContext(Configuration(context.resources.configuration)))
    }

    fun choose(context: Context, tag: String) {
        require(tag in choices)
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            context.getSharedPreferences("app-language", Context.MODE_PRIVATE).edit().putString("tag", tag).apply()
            bind(context.applicationContext)
            kotlin.concurrent.thread(name = "widget-language") {
                io.github.theminionooo.tokenmonitor.widget.WidgetUpdateCoordinator.refresh(context.applicationContext)
            }
            context.findActivity()?.recreate()
        }
    }

    fun locale(): Locale = localized?.resources?.configuration?.locales?.get(0) ?: Locale.US

    fun text(@StringRes id: Int, fallback: String, vararg arguments: Any?): String {
        val context = localized
        val pattern = context?.getString(id) ?: fallback
        return if (arguments.isEmpty()) pattern else String.format(locale(), pattern, *arguments)
    }
}

/** Explicit resource IDs keep protocol fields, saved keys and Hub-supplied content untouched. */
internal fun appText(@StringRes id: Int, fallback: String, vararg arguments: Any?): String = AppLanguage.text(id, fallback, *arguments)

package dev.sannydroid

import android.content.Context
import androidx.compose.ui.text.font.FontFamily

const val DEFAULT_THEME = "gta-sa"
const val DEFAULT_FONT = "dejavu"
const val DEFAULT_FONT_SIZE = 14f

class AppPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("sannydroid", Context.MODE_PRIVATE)

    var themeId: String
        get() = sp.getString("theme", DEFAULT_THEME) ?: DEFAULT_THEME
        set(v) = sp.edit().putString("theme", v).apply()

    var fontKey: String
        get() = sp.getString("font", DEFAULT_FONT) ?: DEFAULT_FONT
        set(v) = sp.edit().putString("font", v).apply()

    var fontSize: Float
        get() = sp.getFloat("fontSize", DEFAULT_FONT_SIZE)
        set(v) = sp.edit().putFloat("fontSize", v).apply()
}

val FONT_OPTIONS = listOf(
    "dejavu" to "DejaVu Sans Mono (Android)",
    "liberation" to "Liberation Mono (Android)",
    "mono" to "Monospace Android",
    "sans" to "Sans-serif",
    "serif" to "Serif",
    "cursive" to "Cursive",
)

fun fontFor(key: String): FontFamily = when (key) {
    "sans" -> FontFamily.SansSerif
    "serif" -> FontFamily.Serif
    "cursive" -> FontFamily.Cursive
    "dejavu", "liberation", "mono" -> FontFamily.Monospace
    else -> FontFamily.Monospace
}

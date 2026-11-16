package dev.sannydroid

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

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

/** key -> nama tampilan */
val FONT_OPTIONS = listOf(
    "dejavu" to "DejaVu Sans Mono",
    "liberation" to "Liberation Mono",
    "mono" to "Monospace bawaan Android",
    "sans" to "Sans-serif",
    "serif" to "Serif",
    "cursive" to "Cursive",
)

private val DejaVuMono = FontFamily(
    Font(R.font.dejavu_sans_mono, FontWeight.Normal),
    Font(R.font.dejavu_sans_mono_bold, FontWeight.Bold),
)

private val LiberationMono = FontFamily(
    Font(R.font.liberation_mono, FontWeight.Normal),
    Font(R.font.liberation_mono_bold, FontWeight.Bold),
)

fun fontFor(key: String): FontFamily = when (key) {
    "dejavu" -> DejaVuMono
    "liberation" -> LiberationMono
    "sans" -> FontFamily.SansSerif
    "serif" -> FontFamily.Serif
    "cursive" -> FontFamily.Cursive
    else -> FontFamily.Monospace
}

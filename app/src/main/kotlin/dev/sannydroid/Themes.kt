package dev.sannydroid

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

data class TokenStyle(val color: Color?, val bold: Boolean, val italic: Boolean, val underline: Boolean)

class EditorTheme(
    val id: String,
    val name: String,
    val background: Color,
    val foreground: Color,
    val gutter: Color,
    val tokens: Map<String, TokenStyle>,
    val accent: Color? = null,
) {
    val isDark: Boolean get() = background.luminance() < 0.5f

    fun span(token: String): SpanStyle {
        val t = tokens[token] ?: return SpanStyle()
        return SpanStyle(
            color = t.color ?: Color.Unspecified,
            fontWeight = if (t.bold) FontWeight.Bold else null,
            fontStyle = if (t.italic) FontStyle.Italic else null,
            textDecoration = if (t.underline) TextDecoration.Underline else null,
        )
    }

    companion object {
        fun gtaSa(): EditorTheme {
            fun t(c: Long, bold: Boolean = false, italic: Boolean = false) = TokenStyle(Color(c), bold, italic, false)
            return EditorTheme(
                id = "gta-sa",
                name = "GTA San Andreas",
                background = Color(0xFF0B0710),
                foreground = Color(0xFFF5ECD7),
                gutter = Color(0xFF8A7F96),
                tokens = mapOf(
                    "comments" to t(0xFF6FBF73, italic = true),
                    "labels" to t(0xFFFFA64D, bold = true),
                    "variables" to t(0xFFFF6FA8),
                    "keywords" to t(0xFFB38CFF, bold = true),
                    "numbers" to t(0xFFFFD166),
                    "strings" to t(0xFFFF9E80),
                    "commands" to t(0xFF7CE0FF),
                    "directives" to t(0xFFB38CFF, italic = true),
                    "constants" to t(0xFFFFD166, bold = true),
                ),
                accent = Color(0xFFFFA64D),
            )
        }

        fun fallback() = EditorTheme(
            id = "fallback",
            name = "Bawaan",
            background = Color(0xFF1E1E1E),
            foreground = Color(0xFFD4D4D4),
            gutter = Color(0xFF858585),
            tokens = emptyMap(),
        )
    }
}

object ThemeLoader {
    private val TOKENS = listOf(
        "comments", "labels", "variables", "keywords", "numbers", "strings",
        "models", "classes", "commands", "directives", "constants",
    )

    fun loadAll(ctx: Context): List<EditorTheme> {
        val files = ctx.assets.list("themes")?.filter { it.endsWith(".ini") } ?: emptyList()
        val out = ArrayList<EditorTheme>()
        for (f in files) {
            try {
                val text = ctx.assets.open("themes/$f").bufferedReader().use { it.readText() }
                out.add(parse(text, f.removeSuffix(".ini")))
            } catch (e: Exception) {
                // lewati tema yang rusak
            }
        }
        return listOf(EditorTheme.gtaSa()) + out.sortedBy { it.name.lowercase() }
    }

    fun parse(text: String, id: String): EditorTheme {
        val sections = HashMap<String, MutableMap<String, String>>()
        var cur = ""
        for (raw in text.lines()) {
            val l = raw.trim()
            if (l.isEmpty() || l.startsWith(";")) continue
            if (l.startsWith("[") && l.endsWith("]")) {
                cur = l.substring(1, l.length - 1).lowercase()
                sections.getOrPut(cur) { LinkedHashMap() }
                continue
            }
            val i = l.indexOf('=')
            if (i < 0) continue
            sections.getOrPut(cur) { LinkedHashMap() }[l.substring(0, i).trim()] = l.substring(i + 1).trim()
        }
        val vars = sections["variables"] ?: emptyMap<String, String>()
        val gui = sections["gui"] ?: emptyMap<String, String>()
        val syn = sections["syntax"] ?: emptyMap<String, String>()

        fun color(v: String?, depth: Int = 0): Color? {
            if (v == null || depth > 5) return null
            val s = v.trim()
            val ref = vars[s]
            if (ref != null) return color(ref, depth + 1)
            if (s.startsWith("#") && s.length == 7) {
                val n = s.substring(1).toLongOrNull(16) ?: return null
                return Color(0xFF000000L or n)
            }
            val n = s.toLongOrNull() ?: return null
            if (n < 0 || n > 0xFFFFFF) return null // 536870911 = "tanpa warna"
            // warna Delphi (TColor) berformat BGR
            return Color((n and 0xFF).toInt(), ((n shr 8) and 0xFF).toInt(), ((n shr 16) and 0xFF).toInt())
        }

        val tokens = HashMap<String, TokenStyle>()
        for (t in TOKENS) {
            tokens[t] = TokenStyle(
                color = color(syn["$t.color"]),
                bold = syn["$t.style.bold"] == "1",
                italic = syn["$t.style.italic"] == "1",
                underline = syn["$t.style.underline"] == "1",
            )
        }
        val name = sections["meta"]?.get("name")?.ifBlank { null } ?: id
        return EditorTheme(
            id = id,
            name = name,
            background = color(gui["editor.background"]) ?: Color(0xFF1E1E1E),
            foreground = color(gui["editor.color"]) ?: Color(0xFFD4D4D4),
            gutter = color(gui["gutter.color"]) ?: Color(0xFF858585),
            tokens = tokens,
        )
    }
}

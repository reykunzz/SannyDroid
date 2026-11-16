package dev.sannydroid

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class Highlighter(
    private val keywords: Set<String>,
    private val constants: Set<String>,
    private val theme: EditorTheme,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlight(text.text), OffsetMapping.Identity)

    fun highlight(s: String): AnnotatedString = buildAnnotatedString {
        append(s)
        fun paint(re: Regex, token: String) {
            val st = theme.span(token)
            for (m in re.findAll(s)) addStyle(st, m.range.first, m.range.last + 1)
        }
        paint(NUMBER, "numbers")
        paint(VARIABLE, "variables")
        paint(LABEL_REF, "labels")
        paint(LABEL_DEF, "labels")
        paint(OPCODE_ID, "keywords")
        val cmd = theme.span("commands")
        val const = theme.span("constants")
        for (m in WORD.findAll(s)) {
            val w = m.value.lowercase()
            if (w in keywords) addStyle(cmd, m.range.first, m.range.last + 1)
            else if (w in constants) addStyle(const, m.range.first, m.range.last + 1)
        }
        paint(STRING, "strings")
        paint(DIRECTIVE, "directives")
        paint(COMMENT, "comments")
    }

    private companion object {
        val NUMBER = Regex("\\b-?\\d+(\\.\\d+)?\\b")
        val VARIABLE = Regex("\\$\\w+|\\b\\d+@")
        val LABEL_REF = Regex("@\\w+")
        val LABEL_DEF = Regex("^\\s*:\\w+", RegexOption.MULTILINE)
        val OPCODE_ID = Regex("^\\s*[0-9A-Fa-f]{4}:", RegexOption.MULTILINE)
        val WORD = Regex("\\b[A-Za-z_]\\w*\\b")
        val STRING = Regex("'[^'\\n]*'|\"[^\"\\n]*\"")
        val DIRECTIVE = Regex("\\{[^}\\n]*\\}")
        val COMMENT = Regex("//.*")
    }
}

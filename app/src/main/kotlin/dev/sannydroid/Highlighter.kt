package dev.sannydroid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class Highlighter(private val keywords: Set<String>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlight(text.text), OffsetMapping.Identity)

    private fun highlight(s: String): AnnotatedString = buildAnnotatedString {
        append(s)
        fun paint(re: Regex, color: Color) {
            for (m in re.findAll(s)) addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1)
        }
        paint(NUMBER, Color(0xFFB5CEA8))
        paint(VARIABLE, Color(0xFF9CDCFE))
        paint(LABEL_REF, Color(0xFFDCDCAA))
        paint(LABEL_DEF, Color(0xFFDCDCAA))
        paint(OPCODE_ID, Color(0xFFC586C0))
        for (m in WORD.findAll(s)) {
            if (m.value.lowercase() in keywords) {
                addStyle(SpanStyle(color = Color(0xFF569CD6)), m.range.first, m.range.last + 1)
            }
        }
        paint(STRING, Color(0xFFCE9178))
        paint(DIRECTIVE, Color(0xFFC586C0))
        paint(COMMENT, Color(0xFF6A9955))
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

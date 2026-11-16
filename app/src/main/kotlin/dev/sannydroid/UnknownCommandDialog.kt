package dev.sannydroid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sb.CompileError
import sb.OpcodeDb
import sb.Suggestion

class UnknownItem(
    val line: Int,
    val text: String,
    val reason: String,
    val suggestions: List<Suggestion>,
)

private val ID_PREFIX = Regex("^([0-9A-Fa-f]{4}):\\s*")
private val HIGH_LEVEL = Regex(
    "(?i)^(while|for|repeat|until|end|else|elseif|break|continue|while)\\b|\\bthen\\b|^\\w+\\.\\w+",
)

private fun commandWord(line: String): String {
    var s = ID_PREFIX.replace(line.trim(), "")
    if (s.startsWith("not ", ignoreCase = true)) s = s.substring(4).trim()
    return s.takeWhile { it.isLetterOrDigit() || it == '_' }
}

fun explainUnknown(db: OpcodeDb, e: CompileError): UnknownItem {
    val idMatch = ID_PREFIX.find(e.text.trim())
    val word = commandWord(e.text)
    val reason = when {
        idMatch != null && e.msg.contains("tidak dikenal") -> {
            val id = idMatch.groupValues[1].uppercase()
            val extra = if (id == "0A93") {
                " Di Android, akhiri script dengan terminate_this_custom_script (opcode 004E)."
            } else {
                ""
            }
            "Opcode $id tidak ada di daftar GTA SA Mobile. Mungkin opcode itu hanya ada di versi PC " +
                "atau di versi CLEO lain.$extra"
        }
        HIGH_LEVEL.containsMatchIn(e.text.trim()) ->
            "Ini sintaks tingkat tinggi (if...then, while, Player.Xxx, dan sejenisnya) yang belum didukung. " +
                "Tulis dalam bentuk opcode, mis. if 0, lalu kondisinya, lalu jf @label."
        else ->
            "Tidak ada perintah bernama \"$word\" di database GTA SA Mobile. Biasanya karena salah ketik, " +
                "atau perintah itu bukan milik GTA SA Mobile."
    }
    return UnknownItem(e.line, e.text, reason, db.suggest(word))
}

@Composable
fun UnknownCommandDialog(items: List<UnknownItem>, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shown = items.take(8)
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = cs.surface,
        titleContentColor = cs.primary,
        textContentColor = cs.onSurface,
        title = { GtaText("Perintah tidak dikenali", fontSize = 22.sp, color = cs.primary) },
        confirmButton = { TextButton(onClick = onClose) { GtaText("Mengerti", color = cs.primary) } },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (items.size == 1) {
                        "Compiler menemukan 1 perintah yang tidak dikenal, jadi script belum bisa dibuat."
                    } else {
                        "Compiler menemukan ${items.size} perintah yang tidak dikenal, jadi script belum bisa dibuat."
                    },
                    fontSize = 13.sp,
                )
                for (item in shown) {
                    Text(
                        "Baris ${item.line}",
                        color = cs.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        item.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(cs.onSurface.copy(alpha = 0.10f))
                            .padding(6.dp),
                    )
                    Text(item.reason, fontSize = 12.sp)
                    if (item.suggestions.isNotEmpty()) {
                        Text("Mungkin maksud Anda:", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        for (s in item.suggestions) {
                            Text("- ${s.usage}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
                if (items.size > shown.size) {
                    Text(
                        "...dan ${items.size - shown.size} lainnya (lihat daftar kesalahan di bawah editor).",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    "Cara memperbaiki",
                    color = cs.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "- Periksa ejaan perintah (huruf besar/kecil tidak berpengaruh).\n" +
                        "- Pastikan perintah ada di GTA SA Mobile; sebagian opcode hanya ada di PC.\n" +
                        "- Tulis dalam bentuk opcode, mis. 0001: wait 250 ms, atau keyword seperti wait 250.\n" +
                        "- Akhiri script dengan terminate_this_custom_script.",
                    fontSize = 12.sp,
                )
            }
        },
    )
}

package dev.sannydroid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PREVIEW = "{\$CLEO .csa}\n0000:\n:LOOP\nwait 250 // contoh\n0@ += 1\n\$12 = 'ABC'\njump @LOOP"

@Composable
fun AppearanceDialog(
    themes: List<EditorTheme>,
    theme: EditorTheme,
    keywords: Set<String>,
    constants: Set<String>,
    fontKey: String,
    fontSize: Float,
    onTheme: (String) -> Unit,
    onFont: (String) -> Unit,
    onSize: (Float) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = cs.surface,
        titleContentColor = cs.onSurface,
        textContentColor = cs.onSurface,
        title = { GtaText("Tampilan", fontSize = 22.sp, color = cs.primary) },
        confirmButton = { TextButton(onClick = onClose) { GtaText("Selesai", color = cs.primary) } },
        dismissButton = { TextButton(onClick = onReset) { GtaText("Atur ulang", color = cs.primary) } },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                // pratinjau
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(theme.background)
                        .padding(8.dp),
                ) {
                    Text(
                        text = Highlighter(keywords, constants, theme).highlight(PREVIEW),
                        color = theme.foreground,
                        fontFamily = fontFor(fontKey),
                        fontSize = fontSize.sp,
                    )
                }

                Text("Ukuran font: ${fontSize.toInt()}", modifier = Modifier.padding(top = 12.dp))
                Slider(
                    value = fontSize,
                    onValueChange = onSize,
                    valueRange = 10f..24f,
                    steps = 13,
                )

                Text("Font", modifier = Modifier.padding(top = 4.dp))
                for ((key, label) in FONT_OPTIONS) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onFont(key) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = key == fontKey, onClick = { onFont(key) })
                        Text(label, fontFamily = fontFor(key))
                    }
                }

                Text("Tema warna", modifier = Modifier.padding(top = 12.dp))
                for (t in themes) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onTheme(t.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = t.id == theme.id, onClick = { onTheme(t.id) })
                        Box(
                            Modifier.size(width = 36.dp, height = 24.dp).background(t.background),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Aa", color = t.foreground, fontSize = 12.sp)
                        }
                        Text(t.name, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        },
    )
}

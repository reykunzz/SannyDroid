from pathlib import Path

p = Path("app/src/main/kotlin/dev/sannydroid/MainActivity.kt")
s = p.read_text()

def replace_once(old, new, name):
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"GAGAL: {name}, ditemukan {count} kali")
    s = s.replace(old, new, 1)

replace_once(
    "import androidx.compose.runtime.mutableStateOf\n",
    "import androidx.compose.runtime.mutableStateOf\n"
    "import androidx.compose.runtime.mutableStateListOf\n",
    "import state"
)

replace_once(
    "import androidx.compose.ui.text.input.TextFieldValue\n",
    "import androidx.compose.ui.text.input.TextFieldValue\n"
    "import androidx.compose.ui.input.key.Key\n"
    "import androidx.compose.ui.input.key.KeyEventType\n"
    "import androidx.compose.ui.input.key.isCtrlPressed\n"
    "import androidx.compose.ui.input.key.onPreviewKeyEvent\n"
    "import androidx.compose.ui.input.key.type\n",
    "import keyboard"
)

replace_once(
    "    var text by remember { mutableStateOf(TextFieldValue(SAMPLE)) }\n",
    """    var text by remember { mutableStateOf(TextFieldValue(SAMPLE)) }

    val history = remember {
        mutableStateListOf(TextFieldValue(SAMPLE))
    }
    var historyIndex by remember { mutableStateOf(0) }

    val undo: () -> Unit = {
        if (historyIndex > 0) {
            historyIndex--
            text = history[historyIndex]
        }
    }

    val redo: () -> Unit = {
        if (historyIndex < history.lastIndex) {
            historyIndex++
            text = history[historyIndex]
        }
    }

    val resetHistory: (TextFieldValue) -> Unit = { value ->
        history.clear()
        history.add(value)
        historyIndex = 0
        text = value
    }
""",
    "history state"
)

replace_once(
    "text = TextFieldValue(s)",
    "resetHistory(TextFieldValue(s))",
    "open file history reset"
)

replace_once(
    '            GtaButton(\n                "Compile",',
    """            GtaButton(
                "Undo",
                onClick = { undo() },
                modifier = Modifier.padding(start = 6.dp),
            )
            GtaButton(
                "Redo",
                onClick = { redo() },
                modifier = Modifier.padding(start = 6.dp),
            )
            GtaButton(
                "Compile",""",
    "toolbar buttons"
)

replace_once(
    """            .weight(1f)""",
    """            .weight(1f)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        event.isCtrlPressed
                    ) {
                        when (event.key) {
                            Key.Z -> {
                                undo()
                                true
                            }
                            Key.Y -> {
                                redo()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }""",
    "keyboard shortcuts"
)

replace_once(
    "            onValueChange = { text = it },",
    """            onValueChange = { value ->
                if (value.text != text.text) {
                    while (history.size > historyIndex + 1) {
                        history.removeAt(history.lastIndex)
                    }

                    history.add(value)

                    if (history.size > 100) {
                        history.removeAt(0)
                    }

                    historyIndex = history.lastIndex
                } else if (history.isNotEmpty()) {
                    history[historyIndex] = value
                }

                text = value
            },""",
    "text history"
)

p.write_text(s)
print("BERHASIL: Undo/Redo berhasil ditambahkan.")

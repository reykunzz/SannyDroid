package dev.sannydroid

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sb.Compiler
import sb.ErrorKind
import sb.OpcodeDb
import sb.OpcodeDbLoader

private const val SAMPLE = "{\$CLEO .csa}\n" +
    "0000:\n" +
    "// contoh script CLEO Android\n" +
    ":LOOP\n" +
    "wait 250\n" +
    "0@ += 1\n" +
    "jump @LOOP\n"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

private fun readAsset(ctx: Context, path: String): String =
    ctx.assets.open(path).bufferedReader().use { it.readText() }

private fun loadDb(ctx: Context): OpcodeDb = OpcodeDbLoader.load(
    listOf(
        readAsset(ctx, "sa/SASCM.INI"),
        readAsset(ctx, "sa_mobile/SASCM.Mobile.ini"),
        readAsset(ctx, "sa_mobile/SASCM.CLEO.ini"),
        readAsset(ctx, "sa_mobile/SASCM.ini"),
    ),
    readAsset(ctx, "sa_mobile/keywords.txt"),
    readAsset(ctx, "sa_mobile/constants.txt"),
    readAsset(ctx, "sa_mobile/sa_mobile.json"),
)

private fun schemeFor(t: EditorTheme): ColorScheme {
    val accent = t.accent ?: t.tokens["keywords"]?.color ?: t.tokens["commands"]?.color ?: t.foreground
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val panel = lerp(t.background, if (t.isDark) Color.White else Color.Black, 0.07f)
    return if (t.isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            background = panel,
            onBackground = t.foreground,
            surface = panel,
            onSurface = t.foreground,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            background = panel,
            onBackground = t.foreground,
            surface = panel,
            onSurface = t.foreground,
        )
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { AppPrefs(ctx) }
    val themes = remember { ThemeLoader.loadAll(ctx) }
    val gta = remember { loadGtaFont(ctx) }
    var themeId by remember { mutableStateOf(prefs.themeId) }
    var fontKey by remember { mutableStateOf(prefs.fontKey) }
    var fontSize by remember { mutableStateOf(prefs.fontSize) }

    val theme = themes.firstOrNull { it.id == themeId }
        ?: themes.firstOrNull { it.id == DEFAULT_THEME }
        ?: EditorTheme.fallback()

    CompositionLocalProvider(LocalGtaFont provides gta) {
    MaterialTheme(colorScheme = schemeFor(theme)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            EditorScreen(
                themes = themes,
                theme = theme,
                fontKey = fontKey,
                fontSize = fontSize,
                onTheme = {
                    themeId = it
                    prefs.themeId = it
                },
                onFont = {
                    fontKey = it
                    prefs.fontKey = it
                },
                onSize = {
                    fontSize = it
                    prefs.fontSize = it
                },
                onReset = {
                    themeId = DEFAULT_THEME
                    fontKey = DEFAULT_FONT
                    fontSize = DEFAULT_FONT_SIZE
                    prefs.themeId = DEFAULT_THEME
                    prefs.fontKey = DEFAULT_FONT
                    prefs.fontSize = DEFAULT_FONT_SIZE
                },
            )
        }
    }
    }
}

@Composable
fun EditorScreen(
    themes: List<EditorTheme>,
    theme: EditorTheme,
    fontKey: String,
    fontSize: Float,
    onTheme: (String) -> Unit,
    onFont: (String) -> Unit,
    onSize: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(TextFieldValue(SAMPLE)) }
    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var matchIndex by remember { mutableStateOf(0) }

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
    var errors by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf("Memuat database opcode...") }
    var db by remember { mutableStateOf<OpcodeDb?>(null) }
    var compiled by remember { mutableStateOf<ByteArray?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var unknownItems by remember { mutableStateOf<List<UnknownItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val loaded = withContext(Dispatchers.IO) { loadDb(ctx) }
            db = loaded
            status = "Siap: ${loaded.opcodes.size} opcode dimuat (GTA SA Mobile)"
        } catch (e: Exception) {
            status = "Gagal memuat database: ${e.message}"
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val s = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                resetHistory(TextFieldValue(s))
                errors = emptyList()
            } catch (e: Exception) {
                status = "Gagal membuka file: ${e.message}"
            }
        }
    }
    val saveSource = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.text.toByteArray()) }
                status = "Sumber disimpan"
            } catch (e: Exception) {
                status = "Gagal menyimpan: ${e.message}"
            }
        }
    }
    val saveBinary = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val b = compiled
        if (uri != null && b != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(b) }
                status = "Script hasil compile disimpan (${b.size} byte)"
            } catch (e: Exception) {
                status = "Gagal menyimpan: ${e.message}"
            }
        }
    }

    val keywords = remember(db) { db?.keywords?.keys ?: emptySet() }
    val constants = remember(db) { db?.constants?.keys?.map { it.lowercase() }?.toSet() ?: emptySet() }
    val btnPad = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val errorColor = if (theme.isDark) Color(0xFFF48771) else Color(0xFFC62828)

    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp),
    ) {
        // Header IDE
        Row(
            Modifier
                .fillMaxWidth()
                .background(cs.surface, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("REY XSA", color = cs.primary, fontSize = 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(APP_NAME, color = cs.onSurface, fontSize = 23.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("CLEO SCRIPT EDITOR",
                    color = cs.onSurface.copy(alpha = 0.60f), fontSize = 10.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) {
                    Text("Buka", fontSize = 12.sp, color = cs.onSurface)
                }
                TextButton(onClick = { saveSource.launch("script.txt") }) {
                    Text("Simpan", fontSize = 12.sp, color = cs.onSurface)
                }
                TextButton(onClick = { showAppearance = true }) {
                    Text("⚙", fontSize = 20.sp, color = cs.primary)
                }
            }
        }

        // Tab file dan kontrol editor
        Row(
            Modifier.fillMaxWidth()
                .background(cs.surfaceVariant)
                .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("</>", color = cs.primary, fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("main.csa", color = cs.onSurface, fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                modifier = Modifier.weight(1f).padding(start = 9.dp))
            TextButton(onClick = { undo() }) {
                Text("↶", color = cs.onSurface, fontSize = 20.sp)
            }
            TextButton(onClick = { redo() }) {
                Text("↻", color = cs.onSurface, fontSize = 20.sp)
            }
        }

        // Find & Replace
        Row(
            Modifier
                .fillMaxWidth()
                .background(cs.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                showFind = !showFind
                if (!showFind) {
                    findQuery = ""
                    replaceQuery = ""
                    matchIndex = 0
                }
            }) {
                Text(if (showFind) "Tutup Cari" else "⌕ Cari", color = cs.primary)
            }
        }

        val findMatches = remember(text.text, findQuery) {
            if (findQuery.isBlank()) {
                emptyList()
            } else {
                val found = mutableListOf<Int>()
                var position = 0
                while (position <= text.text.length - findQuery.length) {
                    val index = text.text.indexOf(findQuery, position, ignoreCase = true)
                    if (index < 0) break
                    found.add(index)
                    position = index + findQuery.length
                }
                found
            }
        }

        if (showFind) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceVariant)
                    .padding(8.dp),
            ) {
                BasicTextField(
                    value = findQuery,
                    onValueChange = {
                        findQuery = it
                        matchIndex = 0
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cs.surface, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    singleLine = true,
                    textStyle = TextStyle(color = cs.onSurface, fontSize = 14.sp),
                    decorationBox = { inner ->
                        if (findQuery.isEmpty()) {
                            Text("Cari teks atau opcode...", color = cs.onSurface.copy(alpha = 0.5f))
                        }
                        inner()
                    },
                )

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (findMatches.isEmpty()) "0 hasil"
                        else "${matchIndex + 1} / ${findMatches.size}",
                        color = cs.onSurface,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        if (findMatches.isNotEmpty()) {
                            matchIndex = (matchIndex - 1 + findMatches.size) % findMatches.size
                            val start = findMatches[matchIndex]
                            text = text.copy(
                                selection = androidx.compose.ui.text.TextRange(
                                    start, start + findQuery.length
                                )
                            )
                        }
                    }) { Text("↑") }
                    TextButton(onClick = {
                        if (findMatches.isNotEmpty()) {
                            matchIndex = (matchIndex + 1) % findMatches.size
                            val start = findMatches[matchIndex]
                            text = text.copy(
                                selection = androidx.compose.ui.text.TextRange(
                                    start, start + findQuery.length
                                )
                            )
                        }
                    }) { Text("↓") }
                }

                BasicTextField(
                    value = replaceQuery,
                    onValueChange = { replaceQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cs.surface, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    singleLine = true,
                    textStyle = TextStyle(color = cs.onSurface, fontSize = 14.sp),
                    decorationBox = { inner ->
                        if (replaceQuery.isEmpty()) {
                            Text("Ganti dengan...", color = cs.onSurface.copy(alpha = 0.5f))
                        }
                        inner()
                    },
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        if (findMatches.isNotEmpty()) {
                            val index = matchIndex.coerceIn(0, findMatches.lastIndex)
                            val start = findMatches[index]
                            val end = start + findQuery.length
                            val updated = TextFieldValue(
                                text.text.replaceRange(start, end, replaceQuery),
                                androidx.compose.ui.text.TextRange(start + replaceQuery.length)
                            )
                            while (history.size > historyIndex + 1) {
                                history.removeAt(history.lastIndex)
                            }
                            history.add(updated)
                            if (history.size > 100) history.removeAt(0)
                            historyIndex = history.lastIndex
                            text = updated
                            matchIndex = 0
                        }
                    }) { Text("Ganti") }

                    TextButton(onClick = {
                        if (findQuery.isNotEmpty() && findMatches.isNotEmpty()) {
                            val updatedText = text.text.replace(
                                findQuery, replaceQuery, ignoreCase = true
                            )
                            val updated = TextFieldValue(updatedText)
                            while (history.size > historyIndex + 1) {
                                history.removeAt(history.lastIndex)
                            }
                            history.add(updated)
                            if (history.size > 100) history.removeAt(0)
                            historyIndex = history.lastIndex
                            text = updated
                            matchIndex = 0
                        }
                    }) { Text("Ganti Semua") }
                }
            }
        }

        val autocompleteSuggestions = remember(text, db) {
            val cursor = text.selection.start.coerceIn(0, text.text.length)
            val lineStart = text.text.substring(0, cursor).lastIndexOf('\n') + 1
            val linePrefix = text.text.substring(lineStart, cursor)
            val match = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)$""").matchEntire(linePrefix)
            val prefix = match?.groupValues?.get(1)?.lowercase()

            if (prefix == null || prefix.length < 2) {
                emptyList()
            } else {
                db?.let { database ->
                    database.keywords.entries
                        .asSequence()
                        .filter { it.key.startsWith(prefix) }
                        .mapNotNull { entry ->
                            database.opcodes[entry.value]?.let { entry.key to it }
                        }
                        .sortedBy { it.first }
                        .take(6)
                        .toList()
                } ?: emptyList()
            }
        }

        if (autocompleteSuggestions.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                autocompleteSuggestions.forEach { suggestion ->
                    val word = suggestion.first
                    val opcode = suggestion.second

                    TextButton(
                        onClick = {
                            val cursor = text.selection.start.coerceIn(0, text.text.length)
                            val lineStart = text.text.substring(0, cursor).lastIndexOf('\n') + 1
                            val linePrefix = text.text.substring(lineStart, cursor)
                            val tokenStart = lineStart +
                                linePrefix.takeWhile { it.isWhitespace() }.length

                            val newText = text.text.replaceRange(tokenStart, cursor, "$word ")
                            val newCursor = tokenStart + word.length + 1
                            val updated = TextFieldValue(
                                newText,
                                androidx.compose.ui.text.TextRange(newCursor),
                            )

                            while (history.size > historyIndex + 1) {
                                history.removeAt(history.lastIndex)
                            }
                            history.add(updated)
                            if (history.size > 100) history.removeAt(0)
                            historyIndex = history.lastIndex
                            text = updated
                        },
                    ) {
                        Column {
                            Text(
                                "${opcode.name.ifBlank { word }}  ${opcode.id.toString(16).uppercase().padStart(4, '0')}",
                                color = cs.primary,
                                fontSize = 12.sp,
                            )
                            if (opcode.description.isNotBlank()) {
                                Text(
                                    opcode.description,
                                    color = cs.onSurface.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                )
                            }
                            if (opcode.specs.isNotEmpty()) {
                                Text(
                                    opcode.specs.joinToString(", ") {
                                        "${it.type} (${it.source.name.lowercase()})"
                                    },
                                    color = cs.onSurface.copy(alpha = 0.6f),
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }

        val editorScrollState = rememberScrollState()
        val lineCount = text.text.count { it == '\n' } + 1
        val lineNumbers = (1..lineCount).joinToString("\n") { it.toString() }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(theme.background)
                .verticalScroll(editorScrollState),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = lineNumbers,
                modifier = Modifier
                    .background(cs.surfaceVariant)
                    .padding(start = 8.dp, end = 10.dp, top = 8.dp),
                color = theme.foreground.copy(alpha = 0.45f),
                fontFamily = fontFor(fontKey),
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.4f).sp,
            )

            BasicTextField(
                value = text,
                onValueChange = { value ->
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
                },
                modifier = Modifier
                    .weight(1f)
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
                    }
                    .fillMaxWidth()
                    .padding(8.dp),
                textStyle = TextStyle(
                    fontFamily = fontFor(fontKey),
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.4f).sp,
                    color = theme.foreground,
                ),
                cursorBrush = SolidColor(theme.foreground),
                visualTransformation = remember(keywords, constants, theme) {
                    Highlighter(keywords, constants, theme)
                },
            )
        }

        // Status bar
        Row(
            Modifier.fillMaxWidth()
                .background(cs.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(status, color = cs.onSurface.copy(alpha = 0.75f),
                fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(
                "${errors.size} errors",
                color = if (errors.isEmpty()) Color(0xFF81C995) else errorColor,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // Toolbar bawah
        Row(
            Modifier.fillMaxWidth()
                .background(Color(0xFF302D38), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            GtaButton(
                "▶  COMPILE",
                primary = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val d = db
                    if (d == null) {
                        status = "Database belum siap"
                    } else {
                        val res = Compiler(d).compile(text.text)
                        errors = res.errors.map { it.toString() }
                        val b = res.bytes
                        compiled = b
                        val unknown = res.errors.filter { it.kind == ErrorKind.UNKNOWN_COMMAND }
                        if (unknown.isNotEmpty()) unknownItems = unknown.map { explainUnknown(d, it) }
                        if (b != null) {
                            status = "Compile berhasil: ${b.size} byte"
                            saveBinary.launch("script${res.extension}")
                        } else {
                            status = "Compile gagal: ${res.errors.size} kesalahan"
                        }
                    }
                },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            for (e in errors) Text(e, color = errorColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }

    if (unknownItems.isNotEmpty()) {
        UnknownCommandDialog(unknownItems) { unknownItems = emptyList() }
    }

    if (showAppearance) {
        AppearanceDialog(
            themes = themes,
            theme = theme,
            keywords = keywords,
            constants = constants,
            fontKey = fontKey,
            fontSize = fontSize,
            onTheme = onTheme,
            onFont = onFont,
            onSize = onSize,
            onReset = onReset,
            onClose = { showAppearance = false },
            onAbout = {
                showAppearance = false
                showAbout = true
            },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = cs.surface,
            titleContentColor = cs.onSurface,
            textContentColor = cs.onSurface,
            confirmButton = { TextButton(onClick = { showAbout = false }) { GtaText("Tutup", color = cs.primary) } },
            dismissButton = {
                TextButton(onClick = { uriHandler.openUri(SANNY_URL) }) { GtaText("Situs Sanny Builder", color = cs.primary) }
            },
            icon = {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
            },
            title = { GtaText(APP_NAME, fontSize = 24.sp) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("Versi $APP_VERSION")
                    Text("Pengembang: Rey xsa")
                    Text("Sumber kode: $REPO_URL", fontSize = 12.sp)
                    GtaText("\nSumber dan kredit", fontSize = 16.sp, color = cs.primary, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        "Data opcode, daftar perintah, konstanta, template, dan tema warna " +
                            "berasal dari Sanny Builder 4.2.0 (sannybuilder.com), karya Seemann dan " +
                            "para kontributornya. Hak cipta data tersebut tetap milik pembuat aslinya.",
                        fontSize = 12.sp,
                    )
                    Text(
                        "\nFont DejaVu Sans Mono (lisensi Bitstream Vera/DejaVu) dan Liberation Mono " +
                            "(SIL Open Font License) disertakan beserta berkas lisensinya.",
                        fontSize = 12.sp,
                    )
                    Text(
                        "\nSannyDroid adalah proyek tidak resmi dan tidak berafiliasi dengan " +
                            "Sanny Builder, Rockstar Games, maupun Take-Two Interactive.",
                        fontSize = 12.sp,
                    )
                }
            },
        )
    }
}

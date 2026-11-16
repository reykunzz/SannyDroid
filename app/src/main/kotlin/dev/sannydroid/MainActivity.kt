package dev.sannydroid

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sb.Compiler
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
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                EditorScreen()
            }
        }
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

@Composable
fun EditorScreen() {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var text by remember { mutableStateOf(TextFieldValue(SAMPLE)) }
    var errors by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf("Memuat database opcode...") }
    var db by remember { mutableStateOf<OpcodeDb?>(null) }
    var compiled by remember { mutableStateOf<ByteArray?>(null) }
    var showAbout by remember { mutableStateOf(false) }

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
                text = TextFieldValue(s)
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

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "Logo $APP_NAME",
                modifier = Modifier.size(40.dp),
            )
            Column(Modifier.padding(start = 10.dp)) {
                Text(APP_NAME, color = Color.White, fontSize = 18.sp)
                Text("oleh $DEVELOPER_NAME", color = Color(0xFF9CDCFE), fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            OutlinedButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) { Text("Buka") }
            OutlinedButton(
                onClick = { saveSource.launch("script.txt") },
                modifier = Modifier.padding(start = 6.dp),
            ) { Text("Simpan") }
            Button(
                onClick = {
                    val d = db
                    if (d == null) {
                        status = "Database belum siap"
                    } else {
                        val res = Compiler(d).compile(text.text)
                        errors = res.errors.map { it.toString() }
                        val b = res.bytes
                        compiled = b
                        if (b != null) {
                            status = "Compile berhasil: ${b.size} byte"
                            saveBinary.launch("script${res.extension}")
                        } else {
                            status = "Compile gagal: ${res.errors.size} kesalahan"
                        }
                    }
                },
                modifier = Modifier.padding(start = 6.dp),
            ) { Text("Compile") }
            TextButton(onClick = { showAbout = true }, modifier = Modifier.padding(start = 6.dp)) { Text("Tentang") }
        }

        val keywords = remember(db) { db?.keywords?.keys ?: emptySet() }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF252526))
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Color(0xFFD4D4D4)),
            cursorBrush = SolidColor(Color.White),
            visualTransformation = remember(keywords) { Highlighter(keywords) },
        )

        Text(status, color = Color(0xFF9CDCFE), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            for (e in errors) Text(e, color = Color(0xFFF48771), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Tutup") } },
            dismissButton = {
                TextButton(onClick = { uriHandler.openUri(SANNY_URL) }) { Text("Situs Sanny Builder") }
            },
            icon = {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
            },
            title = { Text(APP_NAME) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Versi $APP_VERSION")
                    Text("Pengembang: $DEVELOPER_NAME")
                    Text("Sumber kode: $REPO_URL", fontSize = 12.sp)
                    Text(
                        "\nSumber dan kredit",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "Data opcode, daftar perintah, konstanta, template, dan tema warna " +
                            "berasal dari Sanny Builder 4.2.0 (sannybuilder.com), karya Seemann dan " +
                            "para kontributornya. Hak cipta data tersebut tetap milik pembuat aslinya.",
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

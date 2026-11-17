package dev.sannydroid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sb.Opcode
import sb.OpcodeDb

@Composable
fun OpcodeExplorerDialog(
    db: OpcodeDb,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme

    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Opcode?>(null) }

    val results = remember(db, query) {
        db.opcodes.values
            .filter { op ->
                query.isBlank() ||
                    op.id.toString().contains(query, true) ||
                    "%04X".format(op.id).contains(query, true) ||
                    op.name.contains(query, true) ||
                    op.description.contains(query, true) ||
                    op.pattern.contains(query, true) ||
                    op.friendly().contains(query, true)
            }
            .sortedBy { it.id }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        titleContentColor = cs.onSurface,
        textContentColor = cs.onSurface,
        title = {
            Text("Opcode Explorer", color = cs.primary)
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Cari nama, ID, atau format") },
                )

                Text(
                    "${results.size} opcode ditemukan",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = cs.primary,
                )

                if (selected != null) {
                    val op = selected!!

                    TextButton(
                        onClick = { selected = null }
                    ) {
                        Text("← Kembali ke daftar")
                    }

                    Text(
                        "Opcode %04X".format(op.id),
                        color = cs.primary,
                        fontSize = 19.sp,
                    )

                    Text(
                        op.name.ifBlank { "Tanpa nama" },
                        fontSize = 17.sp,
                    )

                    if (op.description.isNotBlank()) {
                        Text(
                            op.description,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }

                    Text(
                        "Format:",
                        color = cs.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    Text(
                        op.friendly(),
                        fontSize = 13.sp,
                    )

                    Text(
                        "Jumlah parameter: ${
                            if (op.variadic) "Variadic" else op.argc
                        }",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    if (op.specs.isNotEmpty()) {
                        Text(
                            "Detail parameter:",
                            color = cs.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )

                        op.specs.forEachIndexed { index, spec ->
                            Text(
                                "${index + 1}. ${spec.type} — ${spec.source}",
                                fontSize = 12.sp,
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(op.friendly()))
                        }
                    ) {
                        Text("Salin format opcode")
                    }
                } else {
                    results.take(150).forEach { op ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = op }
                                .padding(vertical = 7.dp)
                        ) {
                            Text(
                                "%04X — %s".format(
                                    op.id,
                                    op.name.ifBlank { op.firstWord ?: "Opcode" }
                                ),
                                color = cs.primary,
                                fontSize = 14.sp,
                            )

                            Text(
                                op.friendly(),
                                fontSize = 12.sp,
                                color = cs.onSurface,
                            )
                        }
                    }

                    if (results.size > 150) {
                        Text(
                            "Ketik pencarian untuk mempersempit hasil.",
                            fontSize = 12.sp,
                            color = cs.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        },
    )
}

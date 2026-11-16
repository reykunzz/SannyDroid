package sb

import java.io.ByteArrayOutputStream

class CompileError(val line: Int, val msg: String) {
    override fun toString() = "baris $line: $msg"
}

class CompileResult(val bytes: ByteArray?, val extension: String, val errors: List<CompileError>)

/**
 * Compiler teks -> bytecode script CLEO (GTA SA Mobile / CLEO Android).
 * Mendukung sintaks opcode klasik Sanny Builder (bukan blok if/while/for tingkat tinggi).
 */
class Compiler(private val db: OpcodeDb) {
    fun compile(source: String): CompileResult = Session(db).run(source)
}

private class Err(message: String) : Exception(message)

private enum class K { INT, FLOAT, GLOBAL, LOCAL, LABEL, SQ, DQ, IDENT, OTHER }

private class Operand(val text: String, val kind: K)

private class Hit(val op: Opcode, val params: List<String>, val extras: List<String>)

private class Fixup(val pos: Int, val label: String, val line: Int)

private val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
private val BRACES = Regex("\\{[^}]*\\}")
private val CLEO_DIRECTIVE = Regex("^\\{\\s*\\\$CLEO\\s*(\\.\\w+)?\\s*\\}$", RegexOption.IGNORE_CASE)
private val LABEL_DEF = Regex("^:(\\w+)$")
private val ID_PREFIX = Regex("^([0-9A-Fa-f]{4}):\\s*(.*)$")
private val RE_INT = Regex("^-?(\\d+|0[xX][0-9a-fA-F]+)$")
private val RE_FLOAT = Regex("^-?(\\d*\\.\\d+|\\d+\\.\\d*)[fF]?$")
private val RE_GLOBAL = Regex("^\\$(\\w+)$")
private val RE_LOCAL = Regex("^(\\d+)@$")
private val RE_LABEL = Regex("^@(\\w+)$")
private val RE_IDENT = Regex("^[A-Za-z_]\\w*$")

private class Session(private val db: OpcodeDb) {
    private val out = ByteArrayOutputStream()
    private val labels = HashMap<String, Int>()
    private val fixups = ArrayList<Fixup>()
    private val errors = ArrayList<CompileError>()
    private var ext = ".csa"
    private var curLine = 0

    fun run(source: String): CompileResult {
        val clean = BLOCK_COMMENT.replace(source) { m -> "\n".repeat(m.value.count { it == '\n' }) }
        for ((i, raw) in clean.split("\n").withIndex()) {
            curLine = i + 1
            try {
                handleLine(raw)
            } catch (e: Err) {
                errors.add(CompileError(curLine, e.message ?: "kesalahan"))
            }
        }
        val bytes = out.toByteArray()
        for (f in fixups) {
            val off = labels[f.label]
            when {
                off == null -> errors.add(CompileError(f.line, "label @${f.label} tidak ditemukan"))
                off == 0 -> errors.add(CompileError(f.line, "lompat ke offset 0 tidak mungkin; awali script dengan 0000:"))
                else -> {
                    val v = -off
                    bytes[f.pos] = v.toByte()
                    bytes[f.pos + 1] = (v shr 8).toByte()
                    bytes[f.pos + 2] = (v shr 16).toByte()
                    bytes[f.pos + 3] = (v shr 24).toByte()
                }
            }
        }
        return CompileResult(if (errors.isEmpty()) bytes else null, ext, errors)
    }

    private fun stripLineComment(s: String): String {
        var q = '\u0000'
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (q != '\u0000') {
                if (c == q) q = '\u0000'
            } else if (c == '\'' || c == '"') {
                q = c
            } else if (c == '/' && i + 1 < s.length && s[i + 1] == '/') {
                return s.substring(0, i)
            }
            i++
        }
        return s
    }

    private fun handleLine(raw: String) {
        var line = stripLineComment(raw).trim()
        if (line.isEmpty()) return
        val d = CLEO_DIRECTIVE.matchEntire(line)
        if (d != null) {
            ext = d.groupValues[1].ifEmpty { ".csa" }
            return
        }
        line = BRACES.replace(line, " ").trim()
        if (line.isEmpty()) return

        val ld = LABEL_DEF.matchEntire(line)
        if (ld != null) {
            val name = ld.groupValues[1].uppercase()
            if (labels.containsKey(name)) throw Err("label :$name sudah didefinisikan")
            labels[name] = out.size()
            return
        }
        compileInstruction(line)
    }

    private fun splitOperands(s: String): List<String> {
        val res = ArrayList<String>()
        val cur = StringBuilder()
        var q = '\u0000'
        for (c in s) {
            if (q != '\u0000') {
                cur.append(c)
                if (c == q) q = '\u0000'
            } else if (c == '\'' || c == '"') {
                q = c
                cur.append(c)
            } else if (c.isWhitespace() || c == ',') {
                if (cur.isNotEmpty()) {
                    res.add(cur.toString())
                    cur.setLength(0)
                }
            } else cur.append(c)
        }
        if (cur.isNotEmpty()) res.add(cur.toString())
        return res
    }

    private fun tryMatch(op: Opcode, text: String): Hit? {
        val m = op.regex.matchEntire(text) ?: return null
        val n = op.groupToParam.size
        val params = arrayOfNulls<String>(n)
        for (g in 0 until n) {
            val pn = op.groupToParam[g]
            if (pn in 1..n) params[pn - 1] = m.groupValues[g + 1]
        }
        val extras = if (op.variadic) splitOperands(m.groupValues.getOrNull(n + 1) ?: "") else emptyList()
        return Hit(op, params.map { it ?: "" }, extras)
    }

    private fun operand(t: String): Operand {
        var s = t.trim()
        if (RE_IDENT.matches(s)) {
            val c = db.constants[s.uppercase()]
            if (c != null) s = c.trim()
        }
        val kind = when {
            RE_INT.matches(s) -> K.INT
            RE_FLOAT.matches(s) -> K.FLOAT
            RE_GLOBAL.matches(s) -> K.GLOBAL
            RE_LOCAL.matches(s) -> K.LOCAL
            RE_LABEL.matches(s) -> K.LABEL
            s.length >= 2 && s.startsWith("'") && s.endsWith("'") -> K.SQ
            s.length >= 2 && s.startsWith("\"") && s.endsWith("\"") -> K.DQ
            RE_IDENT.matches(s) -> K.IDENT
            else -> K.OTHER
        }
        return Operand(s, kind)
    }

    private fun compatible(h: Hit): Boolean {
        for ((i, p) in h.params.withIndex()) {
            val spec = h.op.specs.getOrNull(i) ?: continue
            val o = operand(p)
            when (spec.source) {
                Src.GLOBAL -> if (o.kind != K.GLOBAL) return false
                Src.LOCAL -> if (o.kind != K.LOCAL) return false
                Src.LITERAL -> {
                    if (o.kind == K.GLOBAL || o.kind == K.LOCAL) return false
                    if (spec.type == "int" && o.kind == K.FLOAT) return false
                    if (spec.type == "float" && o.kind == K.INT) return false
                }
                Src.ANY -> {}
            }
        }
        return true
    }

    private fun compileInstruction(line: String) {
        var rest = line
        var not = false
        var forced: Int? = null
        val pm = ID_PREFIX.matchEntire(rest)
        if (pm != null) {
            var id = pm.groupValues[1].toInt(16)
            if (id and 0x8000 != 0) {
                not = true
                id = id and 0x7FFF
            }
            forced = id
            rest = pm.groupValues[2].trim()
        }
        if (rest.startsWith("not ", ignoreCase = true)) {
            not = true
            rest = rest.substring(4).trim()
        }

        val hit: Hit
        if (forced != null) {
            val op = db.opcodes[forced] ?: throw Err("opcode %04X tidak dikenal".format(forced))
            hit = when {
                rest.isEmpty() -> Hit(op, emptyList(), emptyList())
                else -> tryMatch(op, rest) ?: positional(op, splitOperands(rest))
            }
        } else {
            val word = rest.takeWhile { it.isLetterOrDigit() || it == '_' }.lowercase()
            val hits = ArrayList<Hit>()
            for (op in db.candidates(word)) {
                val h = tryMatch(op, rest)
                if (h != null) hits.add(h)
            }
            val ok = hits.firstOrNull { compatible(it) }
            if (ok != null) {
                hit = ok
            } else if (hits.isNotEmpty()) {
                throw Err("tipe operand tidak cocok dengan varian opcode mana pun: $rest")
            } else {
                val kwId = db.keywords[word] ?: throw Err("perintah tidak dikenali: $rest")
                val op = db.opcodes[kwId] ?: throw Err("keyword '$word' mengacu ke opcode %04X yang tidak ada".format(kwId))
                hit = positional(op, splitOperands(rest.substring(word.length)))
            }
        }
        emit(hit, not)
    }

    private fun positional(op: Opcode, ops: List<String>): Hit {
        if (op.variadic) {
            val fixed = op.groupToParam.size.coerceAtMost(ops.size)
            return Hit(op, ops.take(fixed), ops.drop(fixed))
        }
        if (ops.size != op.argc) {
            throw Err("opcode %04X butuh %d parameter, ditemukan %d".format(op.id, op.argc, ops.size))
        }
        return Hit(op, ops, emptyList())
    }

    private fun emit(h: Hit, not: Boolean) {
        val op = h.op
        if (!op.variadic && h.params.size != op.argc) {
            throw Err("pola opcode %04X tidak konsisten dengan jumlah parameter (%d)".format(op.id, op.argc))
        }
        var code = op.id
        if (not) code = code or 0x8000
        write16(code)
        for ((i, p) in h.params.withIndex()) encode(operand(p), op.specs.getOrNull(i), op.letters[i + 1])
        if (op.variadic) {
            for (e in h.extras) encode(operand(e), null, null)
            out.write(0)
        }
    }

    private fun write16(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private fun write32(v: Int) {
        write16(v and 0xFFFF)
        write16((v shr 16) and 0xFFFF)
    }

    private fun writeFloat(f: Float) = write32(java.lang.Float.floatToRawIntBits(f))

    private fun parseInt(t: String): Long {
        val neg = t.startsWith("-")
        val body = t.removePrefix("-")
        val v = if (body.startsWith("0x", true)) body.substring(2).toLong(16) else body.toLong()
        val r = if (neg) -v else v
        if (r < Int.MIN_VALUE || r > 0xFFFFFFFFL) throw Err("angka di luar jangkauan 32-bit: $t")
        return r
    }

    private fun shortString(s: String) {
        if (s.length > 8) throw Err("string pendek maksimal 8 karakter: '$s'")
        out.write(0x09)
        val b = s.toByteArray(Charsets.ISO_8859_1)
        for (i in 0 until 8) out.write(if (i < b.size) b[i].toInt() else 0)
    }

    private fun encode(o: Operand, spec: ParamSpec?, letter: Char?) {
        when (o.kind) {
            K.INT -> {
                val v = parseInt(o.text)
                if (spec?.type == "float") {
                    out.write(0x06)
                    writeFloat(v.toFloat())
                } else if (v in -128..127) {
                    out.write(0x04)
                    out.write(v.toInt() and 0xFF)
                } else if (v in -32768..32767) {
                    out.write(0x05)
                    write16(v.toInt())
                } else {
                    out.write(0x01)
                    write32(v.toInt())
                }
            }
            K.FLOAT -> {
                out.write(0x06)
                writeFloat(o.text.removeSuffix("f").removeSuffix("F").toFloat())
            }
            K.GLOBAL -> {
                val n = RE_GLOBAL.matchEntire(o.text)!!.groupValues[1]
                val idx = n.toIntOrNull() ?: throw Err("variabel global bernama \$$n belum didukung; pakai nomor, mis. \$12")
                if (idx * 4 > 0xFFFF) throw Err("indeks variabel global terlalu besar: \$$idx")
                out.write(0x02)
                write16(idx * 4)
            }
            K.LOCAL -> {
                val idx = RE_LOCAL.matchEntire(o.text)!!.groupValues[1].toInt()
                if (idx > 0xFFFF) throw Err("indeks variabel lokal terlalu besar: $idx@")
                out.write(0x03)
                write16(idx)
            }
            K.LABEL -> labelRef(RE_LABEL.matchEntire(o.text)!!.groupValues[1])
            K.SQ -> shortString(o.text.substring(1, o.text.length - 1))
            K.DQ -> {
                val b = o.text.substring(1, o.text.length - 1).toByteArray(Charsets.ISO_8859_1)
                if (b.size > 255) throw Err("string terlalu panjang (maks 255)")
                out.write(0x0E)
                out.write(b.size)
                out.write(b)
            }
            K.IDENT -> when (letter) {
                'g' -> shortString(o.text)
                'p' -> labelRef(o.text)
                else -> throw Err("pengenal tidak dikenal: ${o.text}")
            }
            K.OTHER -> throw Err("operand tidak didukung: ${o.text}")
        }
    }

    private fun labelRef(name: String) {
        out.write(0x01)
        fixups.add(Fixup(out.size(), name.uppercase(), curLine))
        write32(0)
    }
}
